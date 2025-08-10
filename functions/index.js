const logger = require("firebase-functions/logger");
const axios = require("axios");
/* eslint-disable camelcase */
const functions  = require("firebase-functions");
const admin      = require("firebase-admin");
const OpenAI     = require("openai").default;
const crypto  = require("crypto");

const fetch = require("node-fetch");

const openai = new OpenAI({
  apiKey: "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"   // make sure this env var is set
});

admin.initializeApp({
  databaseURL: "https://kupidxdefault.asia-southeast1.firebasedatabase.app"
});
const db     = admin.database();
const USERS  = db.ref('users');
const now    = () => Date.now();
const BOOST_DURATION_MS = 1 * 60 * 60 * 1_000;   // 1 h
const PAGE_SIZE = 50;

// ── Your Razorpay secret (the one you pasted: 27346b6a8…1c01) ──
const RAZORPAY_SECRET = '27346b6a824152fe1d0404a56f7d587b326fcb7e4bfd287225188bd25c771c01';
// Hardcoded Razorpay webhook secret used by kupidxPlusWebhook
const RZP_WEBHOOK_SECRET = 'o58jBDJ32C0FujiSTKABOfUKneDAlLz5Vo5x3m0HatbS0ZpV';


/* LIVE keys (hard-coded for now) */
const RZP_KEY_ID     = "rzp_live_DsoxJLeiCw940M";
const RZP_KEY_SECRET = "AjQhp4QXqa6XmUJmabpxHEuo";

/* ───────────────────────────── Razorpay callable ───────────────────────────── */

const Razorpay = require("razorpay");
const razorpay = new Razorpay({
  key_id:     RZP_KEY_ID,
  key_secret: RZP_KEY_SECRET,
});

exports.verifyPayment = functions
.region("asia-south1")
.https.onCall(async (data, context) => {
  try {
    const { paymentId } = data;
    if (!paymentId) {
      throw new functions.https.HttpsError("invalid-argument", "Payment ID required");
    }
    const payment  = await razorpay.payments.fetch(paymentId);
    const captured = payment.status === "captured";
    console.log(`[verifyPayment] ${paymentId} → ${payment.status}`);
    return captured;
  } catch (err) {
    console.error("verifyPayment error:", err);
    throw new functions.https.HttpsError("internal", err.message);
  }
});

// New: create one-time order
exports.createOneTimeOrder = functions
  .region("asia-south1")
  .https.onCall(async (data, context) => {
    const { type, quantity } = data;
    const pricing = {
      swipes:      { amount: quantity * 100,  receipt: `swipes_${quantity}` },
      compliments: { amount: quantity * 150,  receipt: `compliments_${quantity}` },
      boosts:      { amount: quantity * 200,  receipt: `boosts_${quantity}` },
      aiMessages:  { amount: quantity * 200,  receipt: `aiMessages_${quantity}` },
    };
    const p = pricing[type];
    if (!p) throw new functions.https.HttpsError("invalid-argument", "Unknown purchase type");
    const order = await razorpay.orders.create({
      amount: p.amount,
      currency: "INR",
      receipt: p.receipt,
    });
    return { id: order.id, key: razorpay.key_id };
  });

/* ───────────────────────────── Chat suggestions ───────────────────────────── */

/* maps “hi”, “bn”, … → prompt fragment */
const LANG = { hi: "Hindi", bn: "Bengali", en: "English", ta: "Tamil", kn: "Kannada", te: "Telugu" };

exports.chatSuggestions = functions
  .region("asia-south1")
  .runWith({ timeoutSeconds: 120, memory: "512MB" })
  .https.onRequest(async (req, res) => {
    /* CORS */
    if (req.method === "OPTIONS") {
      return res
        .set({
          "Access-Control-Allow-Origin":  "*",
          "Access-Control-Allow-Methods": "POST",
          "Access-Control-Allow-Headers": "Content-Type",
        })
        .status(204).send("");
    }
    if (req.method !== "POST") return res.status(405).send("POST only");

    try {
      const {
        messages       = [],
        lang            // 🆕 preferred
      } = req.body || {};

    console.log("[chatSuggestions] got lang:", lang);

      const code     = (lang).toLowerCase().slice(0, 2);
      const language = LANG[code] || "English";

      /* ─── build GPT messages ─── */
      const gptMsgs = [
        {
          role: "system",
          content:
            `You are a “Chat-Suggestion Engine” for a dating app.\n` +
            `⚠️  ALWAYS reply *exclusively* in ${language}.\n\n` +
            `Return **JSON only** in this exact schema (no other text):\n` +
            `{"topics":[],"activities":[{"placeName":"","integration":""}],"integrationTips":[]}`,
        },
        {
          role: "user",
          content: [
            {
              type: "text",
              text:
                `Recent messages:\n` +
                messages
                  .slice(-10)
                  .map((m) => `${m.role}: ${m.text ?? "[image]"}`)
            },
            /* ≤3 pictures */
            ...messages
              .filter((m) => m.imageUrl)
              .slice(-3)
              .map((m) => ({ type: "image_url", image_url: { url: m.imageUrl } })),
          ],
        }
      ];

      const completion = await openai.chat.completions.create({
        model: "gpt-4.1",
        messages: gptMsgs,
        temperature: 0.7,
        max_tokens: 400,
        /* NEW: ask the API itself to enforce JSON */
        response_format: { type: "json_object" },
      });

      const json = completion.choices[0].message.content.trim();   // already pure JSON

      return res.set("Access-Control-Allow-Origin", "*").send(json);
    } catch (err) {
      console.error("chatSuggestions error:", err);
      return res.status(500).send(err.message || "internal error");
    }
  });

const {
    geohashQueryBounds,
    distanceBetween
  } = require('geofire-common');

const COUNTRY_WHITELIST = new Set([
  'United States',
  'India',
  'South Korea',
  'Ghana',
  'Nigeria',
  'Japan',
  'China',
  'Taiwan',
  'Russia',
  'Canada',
  'United Kingdom',
  'Australia',
  'New Zealand',
  'France',
  'Germany',
  'Spain',
  'Italy',
  'Netherlands',
  'Belgium',
  'Sweden',
  'Switzerland',
  'Norway',
  'Denmark',
  'Finland',
  'Ireland',
  'Portugal',
  'Austria'
]);

exports.getNearbyProfiles = functions
  .region('asia-south1')
  .runWith({ timeoutSeconds: 120, memory: '1GB' })
  .https.onCall(async (data, _ctx) => {

    const { uid, maxDistance } = data || {};
    if (!uid)
      throw new functions.https.HttpsError('invalid-argument', 'uid required');

    /* ── caller’s location & country ─────────────────────────────── */
    const [locSnap, countrySnap] = await Promise.all([
      db.ref(`geoFireLocations/${uid}/l`).get(),   // [lat,lng] | null
      db.ref(`users/${uid}/country`).get(),        // string | null
    ]);

    const myLoc     = locSnap.val();
    const myCountry = countrySnap.val() || null;
    const cutoff    = Date.now() - 30 * 24 * 60 * 60 * 1000;

    const distLimit = Number(maxDistance);
    const useDist   =
      Array.isArray(myLoc) && myLoc.length === 2 &&
      Number.isFinite(distLimit) && distLimit <= 65;   // 65-km rule

    /* ── STEP 1: collect candidate UIDs ──────────────────────────── */
    let candidateIds = [];

    if (useDist) {
      /* distance path – query by geohash */
            const bounds = geohashQueryBounds(myLoc, distLimit);
            const snaps = await Promise.all(
              bounds.map(([start, end]) =>
                db.ref('geoFireLocations')
                  .orderByChild('g')
                  .startAt(start)
                  .endAt(end)
                  .get()
              )
            );

            const seen = new Set();
            snaps.forEach(snap => {
              snap.forEach(loc => {
                const id = loc.key;
                if (id !== uid && !seen.has(id)) {
                  seen.add(id);
                  candidateIds.push(id);
                }
              });
            });
    } else {
      /* country path – same-country only (if country known) */
      if (!myCountry) return { profiles: [] };
      const snap = await db
        .ref('users')
        .orderByChild('lastActive')
        .startAt(cutoff)
        .get();
      snap.forEach(s => { if (s.key !== uid) candidateIds.push(s.key); });
    }

    if (candidateIds.length === 0) return { profiles: [] };

    /* ── STEP 2: optional distance filter & ordering ─────────────── */
    const pairs = [];
    if (useDist) {
      const { distanceBetween } = require('geofire-common');
      const chunk = 400;

      for (let i = 0; i < candidateIds.length; i += chunk) {
        const ids   = candidateIds.slice(i, i + chunk);
        const locSnaps = await Promise.all(
          ids.map(id => db.ref(`geoFireLocations/${id}/l`).get())
        );

        locSnaps.forEach((snap, idx) => {
          const loc = snap.val();
          const id  = ids[idx];
          let dist  = Infinity;
          if (Array.isArray(loc) && loc.length === 2) {
            dist = distanceBetween(loc, myLoc);
          }
          if (dist <= distLimit) pairs.push({ id, dist });
        });
      }

      /* order by distance, then uid */
      pairs.sort((a, b) => a.dist - b.dist || a.id.localeCompare(b.id));
      candidateIds = pairs.map(p => p.id);
    } else {
      /* just deterministic uid-ordering */
       /* compute distances for ordering when distance filter not used */
            if (Array.isArray(myLoc) && myLoc.length === 2) {
              const { distanceBetween } = require('geofire-common');
              const chunk = 400;

              for (let i = 0; i < candidateIds.length; i += chunk) {
                const ids = candidateIds.slice(i, i + chunk);
                const locSnaps = await Promise.all(
                  ids.map(id => db.ref(`geoFireLocations/${id}/l`).get())
                );

                locSnaps.forEach((snap, idx) => {
                  const loc = snap.val();
                  const id = ids[idx];
                  let dist = Infinity;
                  if (Array.isArray(loc) && loc.length === 2) {
                    dist = distanceBetween(loc, myLoc);
                  }
                  pairs.push({ id, dist });
                });
              }

              pairs.sort((a, b) => a.dist - b.dist || a.id.localeCompare(b.id));
              candidateIds = pairs.map(p => p.id);
            } else {
              /* fallback deterministic ordering if caller has no location */
              candidateIds.sort();
            }
    }

    /* ── STEP 3: fetch the profiles ──────────────────────────────── */
    const profileSnaps = await Promise.all(
      candidateIds.map(id => db.ref(`users/${id}`).get())
    );

    const profiles = profileSnaps
      .map(s => (s.val() ? { ...s.val(), userId: s.key } : null))
      .filter(p => p && p.lastActive >= cutoff && p.country === myCountry);

    return { profiles };
  });

exports.getGlobalBoostedUsers = functions
  .region('asia-south1')
  .runWith({ timeoutSeconds: 60, memory: '256MB' })
  .https.onCall(async () => {
    const cutOff = now() - BOOST_DURATION_MS;

    const snap = await USERS
      .orderByChild('isBoosted').equalTo(true).get();

    const profiles = [];
    snap.forEach(s => {
      const u = s.val();
      if (u?.boostedAt >= cutOff) profiles.push(toJson(s));
    });
    return { profiles };
  });

exports.getGlobalPremiumUsers = functions
  .region('asia-south1')
  .runWith({ timeoutSeconds: 60, memory: '256MB' })
  .https.onCall(async () => {
    const snap = await USERS.get();

    const profiles = [];
    snap.forEach(s => {
      const u = s.val();
      if (u?.isPremium || u?.isPlus) {
        // include the UID so the app can map it back
        profiles.push({ userId: s.key, ...u });
      }
    });

    return { profiles };
  });
  /* ──────────────────────── getGlobalComplimenters ──────────────────────────── */
  /** uid → list every user who has *ever* sent that uid a compliment. */
exports.getGlobalComplimenters = functions
  .region('asia-south1')
  .https.onCall(async ({ uid }) => {
    if (!uid) {
      throw new functions.https.HttpsError('invalid-argument', 'uid required');
    }

    /* 1️⃣  read sender-ids once */
    const sendersSnap = await db.ref(`complimentsReceived/${uid}`).get();
    const ids         = Object.keys(sendersSnap.val() || {});

    if (ids.length === 0) return { profiles: [] };

    /* 2️⃣  batch-fetch their profiles */
    const profSnaps = await Promise.all(
      ids.map(id => USERS.child(id).get())
    );

    const profiles = profSnaps.map(toJson).filter(Boolean);
    return { profiles };
  });

// Near the top, replace your dummy VALID_PLANS with the real ones:
const VALID_PLANS = new Set([
  "plan_QjnGf6wdQAmyi2",    // ₹9 / week       (Plus)
  "plan_QjpkdErsuewaUJ",    // ₹29 / week      (Premium)
  "plan_QjpkKQ5S3ur64Q",    // ₹39 / month     (Plus)
  "plan_QjplxIqveB0BVS",    // ₹99 / month     (Premium)
  "plan_QjpmNjEkEPlObK",    // ₹399 / year     (Plus)
  "plan_QjmpS4xg31rg"       // ₹999 / year     (Premium)
]);

exports.createKupidxPlusSub = functions
  .region("asia-south1")
  .https.onCall(async (data) => {
    const { uid, planId } = data;               // ← pull planId from the client
    if (!uid || !planId) {
      throw new functions.https.HttpsError("invalid-argument","uid+planId required");
    }
    // create using the exact plan they selected:
    const sub = await razorpay.subscriptions.create({
      plan_id: planId,
      customer_notify: 1,
      total_count: getCountForPlan(planId),     // e.g. 52, 12, or 1
      notes: { uid, planId }
    });

    // store everything up front:
    await admin
      .database()
      .ref(`users/${uid}/subscription`)
      .set({
        id: sub.id,
        planId,
        status: "created",
        nextCharge: sub.current_end
      });

/*  👆  Don’t grant Plus/Premium yet – wait for webhook  */
   return { subscriptionId: sub.id, keyId: RZP_KEY_ID };
  });

const PLAN_CYCLES = {
  plan_QjnGf6wdQAmyi2: 52,   // weekly plus
  plan_QjpkdErsuewaUJ: 52,   // weekly premium
  plan_QjpkKQ5S3ur64Q: 12,   // monthly plus
  plan_QjplxIqveB0BVS: 12,   // monthly premium
  plan_QjpmNjEkEPlObK: 1,    // yearly plus
  plan_QjmpS4xg31rg:   1,    // yearly premium
};

function getCountForPlan(planId) {
  return PLAN_CYCLES[planId] || 1;  // sensible default
}

/* ───────── verify first-payment signature (optional client call) ──────── */
exports.verifyKupidxPlusPayment = functions
.region("asia-south1")
.https.onCall(async (data) => {
  const { paymentId, subscriptionId, signature } = data || {};
  if (!paymentId || !subscriptionId || !signature)
    throw new functions.https.HttpsError("invalid-argument", "all fields required");

  /* HMAC-SHA256(subscriptionId|paymentId, key_secret) */
  const expected = crypto
    .createHmac("sha256", RZP_KEY_SECRET)
    .update(`${subscriptionId}|${paymentId}`)
    .digest("hex");

  if (expected !== signature)
    throw new functions.https.HttpsError("permission-denied", "Bad signature");

  return { ok: true };
});

exports.createManualSubscriptionOrder = functions
  .region("asia-south1")
  .https.onCall(async (data, context) => {
    const { amount, label } = data;

    if (!amount || !label) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Amount and label are required"
      );
    }

    const order = await razorpay.orders.create({
      amount: amount * 100, // convert INR to paise
      currency: "INR",
      receipt: `manual_sub_${label}_${Date.now()}`,
    });

    return { id: order.id, key: razorpay.key_id };
  });

const PLAN_TIERS = {
  plan_QjnGf6wdQAmyi2:    { plus: true,  premium: false },  // ₹9 / week
  plan_QjpkdErsuewaUJ:    { plus: false, premium: true  },  // ₹29 / week
  plan_QjpkKQ5S3ur64Q:     { plus: true,  premium: false },  // ₹39 / month
  plan_QjplxIqveB0BVS:     { plus: false, premium: true  },  // ₹99 / month
  plan_QjpmNjEkEPlObK:     { plus: true,  premium: false },  // ₹399 / year
  plan_QjmpS4xg31rg:       { plus: false, premium: true  },  // ₹999 / year
};


const FREE_SWIPE_QUOTA = 20;
exports.checkExpiredOneTimeSubscriptions = functions.pubsub
  .schedule('every 24 hours')
  .timeZone('Asia/Kolkata')
  .onRun(async () => {
    const now = Date.now();
    const usersRef = admin.database().ref('users');

    const snapshot = await usersRef.orderByChild('nextRenewal').endAt(now).once('value');

    const updates = {};
    snapshot.forEach(userSnap => {
      const user = userSnap.val();
      if (user.isPlus || user.isPremium) {
        updates[`${userSnap.key}/isPlus`] = false;
        updates[`${userSnap.key}/isPremium`] = false;
        updates[`${userSnap.key}/swipesInfo/remainingSwipes`] = FREE_SWIPE_QUOTA;
      }
    });

    await usersRef.update(updates);
    console.log("Expired one-time subscriptions reset.");
  });

// 1️⃣ Add a cancel function
// functions/index.js  – completely replace old cancelKupidxPlusSub
exports.cancelKupidxPlusSub = functions
  .region("asia-south1")
  .https.onCall(async (_data, context) => {

    const uid = context.auth?.uid;
    if (!uid) throw new functions.https.HttpsError(
      "unauthenticated", "Sign-in required"
    );

    /* ① pull subscription id */
    const subId = (
      await admin.database().ref(`users/${uid}/subscription/id`).get()
    ).val();

    if (!subId)
      throw new functions.https.HttpsError(
        "not-found","No active subscription stored for this user"
      );

    /* ② cancel via Razorpay REST API (works for cards & UPI mandates created
           *through Razorpay*). Use explicit cancel_at_cycle_end=0 */
    const axios = require("axios");
    const BASIC = Buffer.from(
      `${RZP_KEY_ID}:${RZP_KEY_SECRET}`
    ).toString("base64");

    try {
      await axios.post(
        `https://api.razorpay.com/v1/subscriptions/${subId}/cancel`,
        { cancel_at_cycle_end: 0 },
        { headers: { Authorization: `Basic ${BASIC}` } }
      );
    } catch (err) {
      /* log and bubble up a *meaningful* message */
      console.error("[cancelKupidxPlusSub]", err?.response?.data || err);
      const msg =
        err?.response?.data?.error?.description ||
        err.message ||
        "Razorpay cancel failed";
      throw new functions.https.HttpsError("failed-precondition", msg);
    }

    /* ③ wipe perks immediately */
    await admin.database().ref(`users/${uid}`).update({
      isPlus:false, isPremium:false,
      subscriptionStatus:"cancelled",
      nextRenewal:null
    });

    return { cancelled:true };
  });

exports.verifyKupidxSubscription = functions
  .region("asia-south1")
  .https.onCall(async (data, context) => {
    const { subscriptionId } = data || {};
    const uid = context.auth?.uid;
    if (!uid || !subscriptionId) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "subscriptionId and auth required"
      );
    }

    let sub;
    try {
      sub = await razorpay.subscriptions.fetch(subscriptionId);
    } catch (err) {
      console.error("[verifyKupidxSubscription] fetch failed", err);
      throw new functions.https.HttpsError("internal", "Lookup failed");
    }

    const { status, plan_id: planId, current_end } = sub;
    const active = status === "active" && VALID_PLANS.has(planId);
    if (active) {
      const tier = PLAN_TIERS[planId] || { plus: false, premium: false };
      const updates = {
        isPlus: tier.plus,
        isPremium: tier.premium,
        subscriptionStatus: "active",
        nextRenewal: current_end,
        subscription: { id: subscriptionId, planId },
      };
      if (tier.plus || tier.premium) {
        updates["swipesInfo/remainingSwipes"] = tier.premium ? 2147483647 : 50;
        updates.availableBoosts = tier.premium ? 5 : 3;
        updates.availableCompliments = tier.premium ? 5 : 3;
        if (tier.premium) updates.availableAiMessages = 2;
      }
      await admin.database().ref(`users/${uid}`).update(updates);
    }

    return { ok: active, status, planId };
  });

exports.kupidxPlusWebhook = functions
  .region("asia-south1")
  .https.onRequest(async (req, res) => {

      if (req.method === 'GET') {
        return res.status(200).send('Webhook endpoint is up');
      }
    const { event, payload } = req.body;
    const uid = payload.subscription.entity.notes?.uid;
    if (!uid) {
       logger.error('[kupidxPlusWebhook] Missing uid', { payload });
       return res.status(400).send('uid missing');
    }
    const db  = admin.database().ref(`users/${uid}`);

    switch (event) {
      case "subscription.activated":
      case "subscription.charged": {
        const planId = payload.subscription.entity.plan_id;
        const tier   = PLAN_TIERS[planId] || { plus: false, premium: false };
        const updates = {
          isPlus:            tier.plus,
          isPremium:         tier.premium,
          subscriptionStatus:"active",
          nextRenewal:       payload.subscription.entity.current_end,
        };
        if (tier.plus || tier.premium) {
                  updates["swipesInfo/remainingSwipes"] = tier.premium ? 2147483647 : 50;
                  updates.availableBoosts      = tier.premium ? 5 : 3;
                  updates.availableCompliments = tier.premium ? 5 : 3;
                  if (tier.premium) updates.availableAiMessages = 2;
                }
        await db.update(updates);
        break;
      }

      case "subscription.charged.failed":
      case "subscription.cancelled": {
        // user explicitly cancelled or failed payment
        await db.update({
          isPlus:           false,
          isPremium:        false,
          subscriptionStatus:"inactive",
          nextRenewal:      null,
        });
        break;
      }

      case "subscription.completed": {
        // subscription ran its full course (total_count reached)
        await db.update({
          isPlus:           false,
          isPremium:        false,
          subscriptionStatus:"completed",
          nextRenewal:      null,
        });
        break;
      }
    }

    res.status(200).send("ok");
  });

/* shorthand so we only spell it once */
const DB = 'kupidxdefault';          // ⇐ the sub-domain before .asia-southeast1…

/* ──────────────────────── Auto-disable after 5 reports ─────────────────────── */

async function incrementReportCount(uid) {
  const countRef = admin.database().ref(`reportCounts/${uid}`);
  const res      = await countRef.transaction(c => (c || 0) + 1);
  const total    = res.snapshot.val();

  console.log(`[incrementReportCount] ${uid} now has ${total} reports`);
  if (total >= 5) {
    await admin.auth().updateUser(uid, { disabled: true });
    console.log(`[incrementReportCount] Disabled user ${uid}`);
  }
}

/* 1️⃣ user-to-user report */
exports.onUserReport = functions
  .region('asia-south1')
  .database.instance(DB)                 // ← add .instance()
  .ref('/reports/{reportedId}/{reporterId}')
  .onCreate((_, context) =>
    incrementReportCount(context.params.reportedId)
  );

/* 2️⃣ chat message report */
exports.onChatReport = functions
  .region('asia-south1')
  .database.instance(DB)
  .ref('/reports/{reportId}')
  .onCreate(snapshot => {
    const data = snapshot.val();
    if (data?.reportedId) return incrementReportCount(data.reportedId);
    return null;
  });

/* 3️⃣ post report */
exports.onPostReport = functions
  .region('asia-south1')
  .database.instance(DB)
  .ref('/reportedPosts/{postId}/{reportId}')
  .onCreate(snapshot => {
    const data = snapshot.val();
    if (data?.reportedUser) return incrementReportCount(data.reportedUser);
    return null;
  });

exports.grantWeeklyQuotas = functions.pubsub
  .schedule('every 10080 minutes')
  .timeZone('Asia/Kolkata')
  .onRun(async () => {
    const usersRef = admin.database().ref('users');
    const snap     = await usersRef.once('value');
    const updates  = {};

    snap.forEach(userSnap => {
      const uid  = userSnap.key;
      const data = userSnap.val() || {};
      let boosts, compliments, ai;

      if (data.isPremium) {
        boosts      = 5;
        compliments = 5;
        ai          = 2;
      } else if (data.isPlus) {
        boosts      = 3;
        compliments = 3;
      } else {
        return; // skip free users
      }

      updates[`users/${uid}/availableBoosts`]      = boosts;
      updates[`users/${uid}/availableCompliments`] = compliments;
      if (ai !== undefined) {
              updates[`users/${uid}/availableAiMessages`] = ai;
            }
    });

    // perform all updates in one go
    await admin.database().ref().update(updates);
    console.log("Weekly quotas granted.");
  });

// functions/src/unread-counter.ts
// ✅ correct
exports.bumpUnreadCounter = functions
  .region('asia-south1')
  .database.instance(DB)
  .ref('/notifications/{uid}/{nid}')
  .onWrite(async (change, ctx) => {
    const uid    = ctx.params.uid;
    const before = change.before.exists() ? change.before.val() : null;
    const after  = change.after.exists()  ? change.after.val()  : null;

    // Treat anything except the literal string "true" as UNREAD
    const isUnread = obj => obj && obj.isRead !== "true";

    const delta = (() => {
      if (!before && isUnread(after))                       return +1;  // new unread row
      if ( before && isUnread(before) && !isUnread(after))  return -1;  // was unread → read
      return 0;
    })();
    if (delta === 0) return null;

    await admin.database()
      .ref(`users/${uid}/notifUnreadCount`)
      .transaction(c => {
         const next = (c || 0) + delta;   // old value ±1
         return next < 0 ? 0 : next;      // ⟵ clamp to 0
       });
  });

    /* 🔸 one extra line: a *named* secondary app for the US instance */
    const adminUS = admin.initializeApp({
      databaseURL: 'https://am-twentyfour.firebaseio.com/',
    }, 'US');

    const dbUS  = adminUS.database();

exports.replicaUnreadCounter = functions
    .region('asia-south1')
    .database.instance('kupidxdefault')
    .ref('/users/{uid}/notifUnreadCount')
    .onWrite((change, ctx) => {
      return dbUS.ref(`/users/${ctx.params.uid}/notifUnreadCount`)
        .set(Math.max(0, change.after.val() || 0));
    });

        // secondary instance

exports.pushSummary = functions.pubsub
  .schedule('every 10080 minutes')
  .timeZone('Asia/Kolkata')
  .onRun(async () => {
    const usersSnap = await dbUS
      .ref('users')
      .orderByChild('notifUnreadCount')
      .startAt(1)
      .once('value');

    const now = Date.now();
    const jobs = [];

    usersSnap.forEach(userSnap => {
      const uid    = userSnap.key;
      const user   = userSnap.val() || {};
      const unread = user.notifUnreadCount || 0;
      const last   = user.lastSummaryPush || 0;

      jobs.push((async () => {
        /* 1️⃣ fetch tokens stored in India DB */
        const tSnap  = await admin.database()
          .ref(`users/${uid}/fcmTokens`).once('value');
        const tokens = Object.keys(tSnap.val() || {});

        logger.info('pushSummary candidate', { uid, unread, tokenCount: tokens.length });

        if (tokens.length === 0) return;

        /* 2️⃣ send – note sendEachForMulticast */
        const res = await admin.messaging().sendEachForMulticast({
          tokens,
          data: { type: 'notif_summary', count: String(unread) },
          android: { priority: 'high' },
        });

        // 2a remove dead tokens so the list stays clean
        const updates = {};
        res.responses.forEach((r, i) => {
          if (!r.success &&
              r.error?.code === 'messaging/registration-token-not-registered') {   // :contentReference[oaicite:2]{index=2}
            updates[tokens[i]] = null;              // delete key
          }
        });
        if (Object.keys(updates).length) {
          await admin.database().ref(`users/${uid}/fcmTokens`).update(updates);
        }

        logger.info('pushSummary result', {
          uid,
          success: res.successCount,
          failure: res.failureCount,
        });

        /* 3️⃣ debounce timestamp in both DBs */
        await Promise.all([
          userSnap.ref.child('lastSummaryPush').set(now),               // US copy
          admin.database().ref(`users/${uid}/lastSummaryPush`).set(now) // India copy
        ]);
      })());
    });

    await Promise.all(jobs);
    logger.info(`pushSummary: processed ${jobs.length} users`);
  });

  exports.nudgeIncompleteOnboarding = functions.pubsub
    .schedule("every 120 minutes")
    .timeZone("Asia/Kolkata")
    .onRun(async () => {

      const now          = Date.now();
      const oneHour      = 60 * 60 * 1_000;
      const oneDay       = 24 * oneHour;
      const sixHours     = 6 * oneHour;

      // 1. Pull every user who hasn’t finished onboarding
      const usersSnap = await admin.database()
        .ref("users")
        .orderByChild("onboardingCompleted")
        .equalTo(false)
        .once("value");

      const jobs = [];

      usersSnap.forEach(userSnap => {
        const uid  = userSnap.key;
        const user = userSnap.val() || {};

        const created     = user.signupTimestamp   || 0;
        const lastNudge   = user.lastOnboardingNudge || 0;
        const age         = now - created;
        const sinceNudge  = now - lastNudge;

        // 2. Pick only those 1 h ≤ age < 24 h and not nudged in 6 h
        if (age >= oneHour && age < oneDay && sinceNudge >= sixHours) {
          jobs.push(async () => {

            // 3. Fetch FCM tokens
            const tSnap  = await admin.database()
              .ref(`users/${uid}/fcmTokens`)
              .once("value");

            const tokens = Object.keys(tSnap.val() || {});
            if (!tokens.length) return;

            // 4. Send multicast notification
            const payload = {
              notification: {
                title: "Finish your profile to start swiping!",
                body:   "Add a photo & pick a username – it takes 30 seconds.",
              },
              data: { type: "ONBOARDING_REMINDER" },
              tokens,
            };

            await admin.messaging().sendEachForMulticast(payload);

            // 5. Record that we nudged this user
            await admin.database()
              .ref(`users/${uid}/lastOnboardingNudge`)
              .set(now);
          });
        }
      });

      await Promise.all(jobs.map(fn => fn()));
      console.info(`Nudged ${jobs.length} incomplete users`);
      return null;
    });

    exports.flipGovtIdOnEmailVerify = functions
      .region('asia-south1')
      .https.onCall(async (_data, context) => {
        const uid = context.auth?.uid;
        if (!uid) throw new functions.https.HttpsError('unauthenticated', 'Sign-in required');

        const record = await admin.auth().getUser(uid);
        if (!record.emailVerified) {
          throw new functions.https.HttpsError('failed-precondition', 'Email not verified');
        }

        await db.ref(`verifications/${uid}/status`).set('accepted');
        await db.ref(`users/${uid}/isConsultantVerified`).set(true);
        return { ok: true };
      });

  // Push the `lastSmartMatchWeekOfYear` field to every user
exports.backfillLastSmartMatchWeekOfYear = functions
    .region('asia-south1')
    .https.onRequest(async (_req, res) => {
      try {
        const usersRef = admin.database().ref('users');
        const snap     = await usersRef.once('value');
        const updates  = {};

        snap.forEach(userSnap => {
          const data = userSnap.val() || {};
            if (data.lastSmartMatchWeekOfYear === undefined) {
            updates[`${userSnap.key}/lastSmartMatchWeekOfYear`] = null;
          }
        });

        await usersRef.update(updates);
        res.status(200).send(`updated ${Object.keys(updates).length} users`);
      } catch (err) {
        console.error('backfillLastSmartMatchWeekOfYear error:', err);
        res.status(500).send(err.message);
      }
      });

exports.backfillLocationFields = functions
  .region('asia-south1')
  .https.onRequest(async (_req, res) => {
    try {
      const usersRef = admin.database().ref('users');
      const snap     = await usersRef.once('value');

      const updates  = {};
      const touched  = new Set();

      snap.forEach(userSnap => {
        const data = userSnap.val() || {};
        const path = userSnap.key;           // userId

        // -------- non-nullable strings ----------
        if (data.country === undefined)   { updates[`${path}/country`]   = "";   touched.add(path); }
        if (data.city === undefined)      { updates[`${path}/city`]      = "";   touched.add(path); }
        if (data.hometown === undefined)  { updates[`${path}/hometown`]  = "";   touched.add(path); }

        // -------- nullable strings --------------
        if (data.customCountry === undefined)  { updates[`${path}/customCountry`]  = null; touched.add(path); }
        if (data.customCity === undefined)     { updates[`${path}/customCity`]     = null; touched.add(path); }
        if (data.customHometown === undefined) { updates[`${path}/customHometown`] = null; touched.add(path); }
      });

      if (Object.keys(updates).length === 0) {
        return res.status(200).send('No users needed back-fill.');
      }

      await usersRef.update(updates);
      res
        .status(200)
        .send(`Updated ${touched.size} user(s), wrote ${Object.keys(updates).length} field(s).`);
    } catch (err) {
      console.error('backfillLocationFields error:', err);
      res.status(500).send(err.message);
    }
  });

exports.backfillRewardAdFields = functions
  .region('asia-south1')
  .https.onRequest(async (_req, res) => {
    try {
      const usersRef = admin.database().ref('users');
      const snap     = await usersRef.once('value');
      const updates  = {};
      const touched  = new Set();           // how many distinct users we modify

      snap.forEach(userSnap => {
        const data = userSnap.val() || {};
        const path = userSnap.key;          // e.g. "KTvatVC19DT1aKHLTIZYzkvTT1B3"

        // Back-fill lastRewardAdDayOfYear
        if (data.lastRewardAdDayOfYear === undefined) {
          updates[`${path}/lastRewardAdDayOfYear`] = null;   // or 0 if you prefer
          touched.add(path);
        }

        // Back-fill rewardedAdsToday
        if (data.rewardedAdsToday === undefined) {
          updates[`${path}/rewardedAdsToday`] = null;        // or 0 if you prefer
          touched.add(path);
        }
      });

      // Nothing to do?
      if (Object.keys(updates).length === 0) {
        return res.status(200).send('No users needed back-fill.');
      }

      await usersRef.update(updates);
      res
        .status(200)
        .send(`Updated ${touched.size} user(s), wrote ${Object.keys(updates).length} new field(s).`);
    } catch (err) {
      console.error('backfillRewardAdFields error:', err);
      res.status(500).send(err.message);
    }
  });

  // Backfill all fields required for posting in a single function
exports.backfillProfileFieldsForPosts = functions
    .region('asia-south1')
    .https.onRequest(async (_req, res) => {
      try {
        const usersRef = admin.database().ref('users');
        const snap     = await usersRef.once('value');
        const updates  = {};

        snap.forEach(userSnap => {
          const data = userSnap.val() || {};
          if (data.lastSmartMatchWeekOfYear === undefined) {
            updates[`${userSnap.key}/lastSmartMatchWeekOfYear`] = null;
          }
          if (data.rewardedAdsToday === undefined) {
            updates[`${userSnap.key}/rewardedAdsToday`] = null;
          }
          if (data.lastRewardAdDayOfYear === undefined) {
            updates[`${userSnap.key}/lastRewardAdDayOfYear`] = null;
          }
          if (data.ethnicity === undefined) {
            updates[`${userSnap.key}/ethnicity`] = '';
          }
          if (data.incomeLevel === undefined) {
            updates[`${userSnap.key}/incomeLevel`] = '';
          }
        });

        await usersRef.update(updates);
        res.status(200).send(`updated ${Object.keys(updates).length} fields`);
      } catch (err) {
        console.error('backfillProfileFieldsForPosts error:', err);
        res.status(500).send(err.message);
      }
    });

exports.backfillProfileDefaults = functions
      .region('asia-south1')
      .https.onRequest(async (_req, res) => {
        try {
          const SKIP = new Set([
            // ── already handled in earlier jobs ──
            'lastSmartMatchWeekOfYear', 'rewardedAdsToday', 'lastRewardAdDayOfYear',
            'customLoveLanguage',
            'mediaViewsToday', 'lastMediaResetDayOfYear',
            'country', 'customCountry', 'city', 'customCity',
            'hometown', 'customHometown',
          ]);

          /** Default values lifted 1-for-1 from Profile.kt */
          const DEFAULTS = {
            email: '', password: '',

            premiumExpiryDate: null, razorpaySubscriptionId: null,

            interestedIn: [], preferredLanguage: '',
            userId: '', username: '', name: '',
            dob: '', bio: '', interests: [],

            /** gender & activity */
            gender: '', lastActive: admin.database.ServerValue.TIMESTAMP,

            badges: [], profilepicUrl: null, voiceNoteUrl: null,
            loveLanguage: '', optionalPhotoUrls: [], matches: [],

            religion: '', community: '',

            /** education */
            educationLevel: '', highSchool: '', customHighSchool: null,
            highSchoolGraduationYear: '',
            college: '', customCollege: null, collegeGraduationYear: '',
            collegeDegree: null,
            postGraduation: '', customPostGraduation: null,
            postGraduationYear: '', postGraduationDegree: null,

            ethnicity: '', incomeLevel: '',

            lifestyle: null,

            /** work & politics */
            politics: '', customPolitics: null,
            jobRole: '', customJobRole: null,
            work: '', customWork: null,

            socialCauses: [], lookingFor: '',

            likedUsers: {}, numberOfUsersWhoSwiped: 0,
            UsersWhoLikeMe: {},

            isBoosted: false, boostedAt: null,

            aiMessagesSent: 0, availableBoosts: 0,
            lastBoostTimestamp: null,

            isPremium: false, isPlus: false, isPrivate: false,

            availableCompliments: 0, lastComplimentResetDayOfYear: null,

            /** location-prefs (bools, *not* the six strings we skipped) */
            allowLocationForMatches: false, allowLocationPublic: false,

            /** AM24 ranking buckets */
            am24RankingAge: 0, am24RankingHighSchool: 0,
            am24RankingCollege: 0, am24RankingHometown: 0, am24Ranking: 0,

            /** engagement stats */
            numberOfRatings: 0, numberOfSwipeRights: 0, matchCount: 0,
            matchCountPerSwipeRight: 0.0,
            cumulativeUpvotes: 0, cumulativeDownvotes: 0,
            averageUpvoteCount: 0.0, averageDownvoteCount: 0.0,

            reportUsers: {}, blockedUsers: {},

            upvoteCount: 0, downvoteCount: 0, userTags: [],

            zodiac: null, dateOfJoin: admin.database.ServerValue.TIMESTAMP,

            /** further ranking */
            am24RankingCompositeScore: 0.0,
            am24RankingCity: 0, am24RankingCustomCity: 0,
            am24RankingCustomHometown: 0,

            /** geo */
            latitude: 0.0, longitude: 0.0,

            averageRating: 0.0,

            /** matrimony toggle + fields */
            isMatrimonyMode: false,
            marriageTimeline: null, relocationPreference: null,
            postMarriageCareerPlan: null, traditionalVsLiberal: null,

            fatherOccupation: null, motherOccupation: null,

            /** dating prefs */
            datingAgeStart: 18, datingAgeEnd: 40, datingDistancePreference: 10,
            phoneNumber: null,

            /** physical */
            height: 0, height2: [], caste: '', relationship: null,

            averageSwipeRightsOnUser: 0.0,

            /** notifications & privacy */
            notifUnreadCount: 0, lastSummaryPush: null,
            deleteTimerOverride: false, allowExplicitPics: false,
          };

          const usersRef = admin.database().ref('users');
          const snap     = await usersRef.once('value');

          const updates  = {};
          const touched  = new Set();

          snap.forEach(userSnap => {
            const data = userSnap.val() || {};
            const path = userSnap.key;   // /users/<uid>

            for (const [key, defVal] of Object.entries(DEFAULTS)) {
              if (SKIP.has(key)) continue;              // handled elsewhere
              if (data[key] === undefined) {
                updates[`${path}/${key}`] = defVal;
                touched.add(path);
              }
            }
          });

          if (Object.keys(updates).length === 0) {
            return res.status(200).send('Everything is already up-to-date ✨');
          }

          await usersRef.update(updates);
          res
            .status(200)
            .send(`Patched ${touched.size} user(s), wrote ${Object.keys(updates).length} missing field(s).`);
        } catch (err) {
          console.error('backfillProfileDefaults error:', err);
          res.status(500).send(err.message);
        }
      });

exports.backfillMediaViewFields = functions
  .region('asia-south1')
  .https.onRequest(async (_req, res) => {
    try {
      const usersRef = admin.database().ref('users');
      const snap     = await usersRef.once('value');

      const updates  = {};
      const touched  = new Set();

      snap.forEach(userSnap => {
        const data = userSnap.val() || {};
        const path = userSnap.key;                   // userId

        // mediaViewsToday
        if (data.mediaViewsToday === undefined) {
          updates[`${path}/mediaViewsToday`] = null; // or 0 if you prefer
          touched.add(path);
        }

        // lastMediaResetDayOfYear
        if (data.lastMediaResetDayOfYear === undefined) {
          updates[`${path}/lastMediaResetDayOfYear`] = null; // or 0
          touched.add(path);
        }
      });

      if (Object.keys(updates).length === 0) {
        return res.status(200).send('No users needed back-fill.');
      }

      await usersRef.update(updates);
      res
        .status(200)
        .send(`Updated ${touched.size} user(s), wrote ${Object.keys(updates).length} field(s).`);
    } catch (err) {
      console.error('backfillMediaViewFields error:', err);
      res.status(500).send(err.message);
    }
  });

exports.backfillCustomLoveLanguage = functions
  .region('asia-south1')
  .https.onRequest(async (_req, res) => {
    try {
      const usersRef = admin.database().ref('users');
      const snap     = await usersRef.once('value');

      const updates  = {};
      snap.forEach(userSnap => {
        const data = userSnap.val() || {};
        if (data.customLoveLanguage === undefined) {
          updates[`${userSnap.key}/customLoveLanguage`] = null; // or "" if you prefer
        }
      });

      if (Object.keys(updates).length === 0) {
        return res.status(200).send('No users needed back-fill.');
      }

      await usersRef.update(updates);
      res
        .status(200)
        .send(`Updated ${Object.keys(updates).length} user node(s).`);
    } catch (err) {
      console.error('backfillCustomLoveLanguage error:', err);
      res.status(500).send(err.message);
    }
  });

function calcAge(dob) {
  if (!dob) return 0;
  const parts = dob.split('/');
  if (parts.length !== 3) return 0;
  const [dd, mm, yy] = parts.map(Number);
  const birth = new Date(yy, mm - 1, dd);
  if (isNaN(birth.getTime())) return 0;
  const today = new Date();
  let age = today.getFullYear() - birth.getFullYear();
  const m = today.getMonth() - birth.getMonth();
  if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) age--;
  return age;
}

exports.recomputeLeaderboard = functions.pubsub
  .schedule('every 120 minutes')
  .onRun(async () => {
    const usersSnap = await admin.database().ref('users').once('value');
    const profiles = [];
    usersSnap.forEach(child => {
      const data = child.val() || {};
      const numRatings = data.numberOfRatings || 0;
      const avgRating  = data.averageRating || 0;
      const composite  = (avgRating / 5) * (numRatings / (numRatings + 20));
      profiles.push({
        userId: child.key,
        username: data.username || '',
        name: data.name || '',
        gender: data.gender || '',
        country: data.country || '',
        city: data.city || '',
        customCity: data.customCity || null,
        hometown: data.hometown || '',
        customHometown: data.customHometown || null,
        highSchool: data.highSchool || '',
        customHighSchool: data.customHighSchool || null,
        college: data.college || '',
        customCollege: data.customCollege || null,
        dob: data.dob || '',
        profilepicUrl: data.profilepicUrl || null,
        matchCount: data.matchCount || 0,
        numberOfSwipeRights: data.numberOfSwipeRights || 0,
        numberOfUsersWhoSwiped: data.numberOfUsersWhoSwiped || 0,
        averageRating: avgRating,
        numberOfRatings: numRatings,
        compositeScore: composite,
        age: calcAge(data.dob)
      });
    });

    const rank = arr => {
      const map = {};
      arr.forEach((p,i) => { map[p.userId] = i+1; });
      return map;
    };

    profiles.sort((a,b) => b.compositeScore - a.compositeScore);
    const compRank = rank(profiles);

    const ageRank = rank([...profiles].sort((a,b) => b.age - a.age));

    const rankGroup = (items, keyFn) => {
      const m = {};
      const groups = {};
      items.forEach(p => {
        const key = keyFn(p) || 'Other';
        (groups[key] = groups[key] || []).push(p);
      });
      for (const k in groups) {
        groups[k].sort((a,b) => b.compositeScore - a.compositeScore)
          .forEach((p,i) => { m[p.userId] = i+1; });
      }
      return m;
    };

    const cityRank = rankGroup(profiles, p => p.city);
    const customCityRank = rankGroup(profiles.filter(p => p.city === 'Other'), p => p.customCity);
    const homeRank = rankGroup(profiles, p => p.hometown);
    const customHomeRank = rankGroup(profiles.filter(p => p.hometown === 'Other'), p => p.customHometown);
    const hsRank = rankGroup(profiles, p => p.highSchool || p.customHighSchool);
    const colRank = rankGroup(profiles, p => p.college || p.customCollege);

    const updates = {};
    profiles.forEach(p => {
      const avgSwipe = p.numberOfUsersWhoSwiped > 0 ?
        p.numberOfSwipeRights / p.numberOfUsersWhoSwiped : 0;
      const matchPct = p.numberOfSwipeRights > 0 ?
        p.matchCount / p.numberOfSwipeRights : 0;
      updates[p.userId] = {
        userId: p.userId,
        username: p.username,
        name: p.name,
        gender: p.gender,
        country: p.country,
        city: p.city,
        customCity: p.customCity,
        hometown: p.hometown,
        customHometown: p.customHometown,
        highSchool: p.highSchool,
        customHighSchool: p.customHighSchool,
        college: p.college,
        customCollege: p.customCollege,
        dob: p.dob,
        profilepicUrl: p.profilepicUrl,
        matchCount: p.matchCount,
        numberOfSwipeRights: p.numberOfSwipeRights,
        numberOfUsersWhoSwiped: p.numberOfUsersWhoSwiped,
        averageRating: p.averageRating,
        numberOfRatings: p.numberOfRatings,
        compositeScore: p.compositeScore,
        am24Ranking: compRank[p.userId] || 0,
        am24RankingAge: ageRank[p.userId] || 0,
        am24RankingCity: cityRank[p.userId] || 0,
        am24RankingCustomCity: customCityRank[p.userId] || 0,
        am24RankingHometown: homeRank[p.userId] || 0,
        am24RankingCustomHometown: customHomeRank[p.userId] || 0,
        am24RankingHighSchool: hsRank[p.userId] || 0,
        am24RankingCollege: colRank[p.userId] || 0,
        averageSwipeRightsOnUser: avgSwipe,
        matchCountPerSwipeRight: matchPct
      };
    });

    await admin.database().ref('leaderboard').set(updates);
    console.log(`recomputed leaderboard with ${profiles.length} profiles`);
  });

exports.backfillEthnicityIncome = functions
      .region('asia-south1')
      .https.onRequest(async (_req, res) => {
        try {
          const usersRef = admin.database().ref('users');
          const snap     = await usersRef.once('value');
          const updates  = {};

          snap.forEach(userSnap => {
            const data = userSnap.val() || {};
            if (data.ethnicity === undefined) {
              updates[`${userSnap.key}/ethnicity`] = '';
            }
            if (data.incomeLevel === undefined) {
              updates[`${userSnap.key}/incomeLevel`] = '';
            }
          });

          await usersRef.update(updates);
          res.status(200).send(`updated ${Object.keys(updates).length} fields`);
        } catch (err) {
          console.error('backfillEthnicityIncome error:', err);
          res.status(500).send(err.message);
        }
        });

exports.sortDisplayedProfiles = functions
          .region('asia-south1')
          .https.onCall(async (data, _context) => {
            const { uid, ids } = data || {};
            if (!uid || !Array.isArray(ids))
              throw new functions.https.HttpsError('invalid-argument', 'uid and ids required');

            const db = admin.database();
            const center = (await db.ref(`geoFireLocations/${uid}/l`).get()).val();
            if (!Array.isArray(center) || center.length < 2) {
              return { ids };
            }

            const snaps = await Promise.all(ids.map(id =>
              db.ref(`geoFireLocations/${id}/l`).get()
            ));

            const pairs = ids.map((id, i) => {
              const loc = snaps[i].val();
              if (!Array.isArray(loc) || loc.length < 2) {
                return { id, dist: Infinity };
              }
              const dist = distanceBetween([loc[0], loc[1]], [center[0], center[1]]);
              return { id, dist };
            });

            pairs.sort((a, b) => a.dist - b.dist);
            return { ids: pairs.map(p => p.id) };
          });
          // functions/index.js
exports.userActivityReport = functions
            .region('asia-south1')
            .https.onRequest(async (req, res) => {
              try {
                const usersSnap = await admin.database().ref('users').once('value');
                const table = [];
                const startOfToday = new Date().setHours(0, 0, 0, 0);
                let activeToday = 0;

                usersSnap.forEach(snap => {
                  const user = snap.val();
                  const lastActive = user?.lastActive ?? 0;
                  if (lastActive >= startOfToday) activeToday++;
                  table.push({
                    uid: snap.key,
                    username: user?.username ?? '',
                    lastActive,            // milliseconds since epoch
                  });
                });

                res.set('Access-Control-Allow-Origin', '*')    // allow running via a link
                   .json({ activeToday, users: table });
              } catch (err) {
                console.error('userActivityReport error:', err);
                res.status(500).send(err.message);
              }
            });

exports.listUsaUsers = functions
              .region('asia-south1')
              .https.onRequest(async (_req, res) => {
                try {
                  const snap = await admin
                    .database()
                    .ref('users')
                    .orderByChild('country')
                    .equalTo('United States')
                    .once('value');
                  const users = [];
                  snap.forEach(child => {
                    const u = child.val() || {};
                    users.push({
                      uid: child.key,
                      username: u.username || '',
                      name: u.name || ''
                    });
                  });
                  res.set('Access-Control-Allow-Origin', '*').json({ users });
                } catch (err) {
                  console.error('listUsaUsers error:', err);
                  res.status(500).send(err.message);
                }
              });

exports.listWomenInUsa = functions
                .region('asia-south1')
                .https.onRequest(async (_req, res) => {
                  try {
                    const snap = await admin
                      .database()
                      .ref('users')
                      .orderByChild('country')
                      .equalTo('United States')
                      .once('value');

                    const users = [];
                    snap.forEach(child => {
                      const u = child.val() || {};
                      if ((u.gender || '').toLowerCase() === 'female') {
                        users.push({
                          uid: child.key,
                          username: u.username || '',
                          name: u.name || '',
                          dateOfJoin: u.dateOfJoin || null,
                          lastActive: u.lastActive || null,
                          city: u.city || '',
                          hometown: u.hometown || ''
                        });
                      }
                    });

                    res
                      .set('Access-Control-Allow-Origin', '*')
                      .json({ count: users.length, users });
                  } catch (err) {
                    console.error('listWomenInUsa error:', err);
                    res.status(500).send(err.message);
                  }
                });

exports.backfillSwipeCounts = functions
                  .region('asia-south1')
                  .https.onRequest(async (_req, res) => {
                    try {
                      const db = admin.database();
                      const swipesSnap = await db.ref('swipes').once('value');
                      const updates = {};

                      swipesSnap.forEach(userSnap => {
                        const uid = userSnap.key;
                        userSnap.forEach(otherSnap => {
                          updates[`users/${uid}/swipeCounts/${otherSnap.key}`] = 1;
                        });
                      });

                      await db.ref().update(updates);
                      res.status(200).send(`Updated ${Object.keys(updates).length} swipe count entries`);
                    } catch (err) {
                      console.error('backfillSwipeCounts error:', err);
                      res.status(500).send(err.message);
                    }
                  });
