const logger = require("firebase-functions/logger");
const axios = require("axios");
/* eslint-disable camelcase */
const functions  = require("firebase-functions");
const admin      = require("firebase-admin");
const OpenAI     = require("openai").default;
const Busboy = require("busboy");
const { v4: uuidv4 } = require("uuid");
const crypto  = require("crypto");

const fetch = require("node-fetch");

const openai = new OpenAI({
  apiKey: "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"   // make sure this env var is set
});

admin.initializeApp({
  databaseURL: "https://kupidxdefault.asia-southeast1.firebasedatabase.app"
});

// ── Your Razorpay secret (the one you pasted: 27346b6a8…1c01) ──
const RAZORPAY_SECRET = '27346b6a824152fe1d0404a56f7d587b326fcb7e4bfd287225188bd25c771c01';

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
const LANG = { hi: "Hindi", bn: "Bengali", en: "English", ta: "Tamil", kn: "Kannada", te: "Telegu" };

exports.chatSuggestions = functions
  .region("asia-south1")
  .runWith({ timeoutSeconds: 540, memory: "512MB" })
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

exports.getNearbyProfiles = functions
  .region('asia-south1')
  .runWith({ timeoutSeconds: 540, memory: '1GB' })
  .https.onCall(async (data, context) => {

    /* ───────── arguments ───────── */
    const { uid, minRows = 50 } = data || {};
    console.log('[getNearbyProfiles] called by uid:', uid ?? '<none>');
    if (!uid)
      throw new functions.https.HttpsError(
        'invalid-argument',
        'uid is required'
      );

    /* ───────── helpers ─────────── */
    const db = admin.database();

    /* ───────── 1. where am I? ───── */
    const locSnap = await db.ref(`geoFireLocations/${uid}/l`).get();
    const latLng  = locSnap.val();            // [lat, lng]

    /* ──────── 2. if caller has no location, just grab N users … ─────── */
    if (!Array.isArray(latLng) || latLng.length < 2) {
      const all = await db.ref('users').get();
      const list = [];
      all.forEach(ss => {
        if (ss.key !== uid) {
          const p = ss.val();
          if (p) {
            p.userId = ss.key;                //  ← NEW (uid)
            list.push(p);
          }
        }
      });
      return { profiles: list.slice(0, minRows) };
    }

    /* ───────── 3. geo-sweep as you had it ───────── */
    const center = { lat: latLng[0], lng: latLng[1] };
    const collectedUids = new Set();

    const sweep = async radiusKm => {
      if (radiusKm === Infinity) {
        const all = await db.ref('geoFireLocations').get();
        all.forEach(s => collectedUids.add(s.key));
        return;
      }
      const bounds = geohashQueryBounds([center.lat, center.lng], radiusKm * 1000);
      const tasks  = bounds.map(b =>
        db.ref('geoFireLocations')
          .orderByChild('g').startAt(b[0]).endAt(b[1]).get()
      );
      const snaps = await Promise.all(tasks);
      snaps.forEach(snap => {
        snap.forEach(child => {
          const [lat, lng] = child.child('l').val() || [];
          if (lat == null) return;
          const dist = distanceBetween([lat, lng], [center.lat, center.lng]);
          if (dist <= radiusKm) collectedUids.add(child.key);
        });
      });
    };

    /* force-wide sweep (your TEMP line) */
    const firstRadius = 15000;
    console.log(`[getNearbyProfiles] TEMP radius forced to ${firstRadius}km`);
    await sweep(firstRadius);

    if (collectedUids.size < minRows) {
      console.log(`[getNearbyProfiles] Fewer than ${minRows} users found, sweeping globally…`);
      await sweep(Infinity);
    }

    collectedUids.delete(uid);                          // drop self
    const uids = Array.from(collectedUids).slice(0, minRows);
    console.log('[getNearbyProfiles] Final UID list:', uids);

    /* ───────── 4. fetch user docs & add uid field ───────── */
    const docs = await Promise.all(
      uids.map(id => db.ref(`users/${id}`).get())
    );

    const profiles = docs
      .map(snap => {
        const p = snap.val();
        if (!p) return null;
        p.userId = snap.key;                            //  ← NEW (uid)
        return p;
      })
      .filter(Boolean);

    console.log('[getNearbyProfiles] returning', profiles.length, 'profiles');
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


const FREE_SWIPE_QUOTA = 15;
exports.checkExpiredOneTimeSubscriptions = functions.pubsub
  .schedule('every day 00:00')
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
exports.cancelKupidxPlusSub = functions
  .region("asia-south1")
  .https.onCall(async (data, context) => {
    const uid = context.auth?.uid;
    if (!uid) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Must be signed in to cancel."
      );
    }

    // look up stored subscription ID
    const snap = await admin
      .database()
      .ref(`users/${uid}/subscription/id`)
      .get();
    const subId = snap.val();
    if (!subId) {
      throw new functions.https.HttpsError(
        "not-found",
        "No active subscription found for this user."
      );
    }

    // call Razorpay’s cancel endpoint
    await razorpay.subscriptions.cancel(subId);

    // immediately update your own DB state
    await admin
      .database()
      .ref(`users/${uid}`)
      .update({
        isPlus: false,
        isPremium: false,
        subscriptionStatus: "inactive",
      });

    return { cancelled: true };
  });


exports.kupidxPlusWebhook = functions
  .region("asia-south1")
  .https.onRequest(async (req, res) => {
    const sig = req.headers["x-razorpay-signature"];
    let ev;
    try {
      ev = razorpay.webhooks.verify(
        req.rawBody,
        sig,
        functions.config().razorpay.webhook_secret
      );
    } catch (err) {
      logger.error("Webhook signature mismatch", err);
      return res.status(400).send("fail");
    }

    const { event, payload } = req.body;
    const uid = payload.subscription.entity.customer_id;
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

/** Plan you expect the user to subscribe to */
const EXPECTED_PLAN = "P-8EV86494EK6312239NBCUGVI";

/**
 * Callable ⇢ verifyPaypalSubscription({ subscriptionId: "I-XXXX" }) → { valid:Boolean, status:String }
 */
exports.verifyPaypalSubscription = functions.https.onCall(async (data, context) => {
  const subId = data?.subscriptionId;
  if (!subId) {
    throw new functions.https.HttpsError("invalid-argument", "subscriptionId missing");
  }

  /* ── PayPal credentials from firebase functions:config:set ── */
  const cfg         = functions.config().paypal;
  const clientId    = cfg.client_id;
  const clientSecret= cfg.client_secret;
  const env         = (cfg.environment || "sandbox").toLowerCase();
  const apiBase     = env === "live"
                        ? "https://api-m.paypal.com"
                        : "https://api-m.sandbox.paypal.com";

  /* ── 1) OAuth2 token ── */
  const tokenRes = await fetch(`${apiBase}/v1/oauth2/token`, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      "Authorization": "Basic " + Buffer.from(`${clientId}:${clientSecret}`).toString("base64")
    },
    body: "grant_type=client_credentials"
  });

  if (!tokenRes.ok) {
    throw new functions.https.HttpsError("internal", "PayPal auth failed");
  }
  const { access_token } = await tokenRes.json();

  /* ── 2) Subscription details ── */
  const subRes = await fetch(`${apiBase}/v1/billing/subscriptions/${subId}`, {
    headers: { Authorization: `Bearer ${access_token}` }
  });

  if (!subRes.ok) {
    throw new functions.https.HttpsError("internal", "Subscription lookup failed");
  }

  const subJson = await subRes.json();
  const status  = subJson.status;    // ACTIVE | APPROVAL_PENDING | CANCELLED …
  const planId  = subJson.plan_id;

  const valid = status === "ACTIVE" && planId === EXPECTED_PLAN;

  return { valid, status, planId };   // your Android code can check .valid === true
});

exports.grantWeeklyQuotas = functions.pubsub
  .schedule('every monday 00:00')
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
  .schedule('every 120 minutes')
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

exports.pushUpgradePrompt = functions.pubsub
    .schedule('every 24 hours')
    .timeZone('Asia/Kolkata')
    .onRun(async () => {
      const usersSnap = await admin.database()
        .ref('users')
        .orderByChild('isPlus')
        .equalTo(false)
        .once('value');

      const now = Date.now();
      const day = 24 * 60 * 60 * 1000;
      const jobs = [];

      usersSnap.forEach(userSnap => {
        const uid  = userSnap.key;
        const user = userSnap.val() || {};
        const last = user.lastUpgradePush || 0;

        if (user.isPremium === true) return;
        if (now - last < day) return;         // skip if pushed < 24h ago

        jobs.push((async () => {
          const tSnap  = await admin.database()
            .ref(`users/${uid}/fcmTokens`).once('value');
          const tokens = Object.keys(tSnap.val() || {});

          logger.info('pushUpgradePrompt candidate', {
            uid,
            tokenCount: tokens.length,
          });

          if (tokens.length === 0) return;

          const res = await admin.messaging().sendEachForMulticast({
            tokens,
            notification: {
              body: 'Upgrade for unlimited swipes and an ad-free experience',
            },
            data: { type: 'upgrade_prompt' },
            android: { priority: 'high' },
          });

          const updates = {};
          res.responses.forEach((r, i) => {
            if (!r.success &&
                r.error?.code === 'messaging/registration-token-not-registered') {
              updates[tokens[i]] = null;
            }
          });
          if (Object.keys(updates).length) {
            await admin.database().ref(`users/${uid}/fcmTokens`).update(updates);
          }

          logger.info('pushUpgradePrompt result', {
            uid,
            success: res.successCount,
            failure: res.failureCount,
          });

          await Promise.all([
            userSnap.ref.child('lastUpgradePush').set(now),             // India copy
            dbUS.ref(`users/${uid}/lastUpgradePush`).set(now),           // US copy
          ]);
        })());
      });

      await Promise.all(jobs);
      logger.info(`pushUpgradePrompt: processed ${jobs.length} users`);
    });
