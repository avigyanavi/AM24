/* eslint-disable camelcase */
// ────────────────────────────────────────────────────────────────
// index.js — ALL 9 HTTPS/Callable functions migrated to GCF Gen-2
//  (verifyPayment, createOneTimeOrder, chatSuggestions, getNearbyProfiles,
//   createKupidxPlusSub, verifyKupidxPlusPayment, createManualSubscriptionOrder,
//   cancelKupidxPlusSub, kupidxPlusWebhook)
//  Other RTDB/Firestore/PubSub triggers remain Gen-1.
// ────────────────────────────────────────────────────────────────

// Gen-2 builders
const { onRequest, onCall }  = require("firebase-functions/v2/https");
// Gen-1 import kept for HttpsError, config, and non-HTTP triggers
const functions               = require("firebase-functions");
const logger                  = require("firebase-functions/logger");

const admin   = require("firebase-admin");
const axios   = require("axios");
const OpenAI  = require("openai").default;
const Busboy  = require("busboy");
const { v4: uuidv4 } = require("uuid");
const crypto  = require("crypto");
const fetch   = require("node-fetch");
const Razorpay = require("razorpay");
const {
  geohashQueryBounds,
  distanceBetween,
} = require("geofire-common");

/* ─────────────────────── ENV / INITIALISATION ─────────────────────── */
admin.initializeApp({
  databaseURL:
    "https://kupidxdefault.asia-southeast1.firebasedatabase.app/",
});

// Keep hard-coded keys for now (consider functions:secrets:set in prod)
const openai = new OpenAI({
  apiKey:
    "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",
});

const RAZORPAY_SECRET =
  "27346b6a824152fe1d0404a56f7d587b326fcb7e4bfd287225188bd25c771c01";
const RZP_KEY_ID     = "rzp_live_DsoxJLeiCw940M";
const RZP_KEY_SECRET = "AjQhp4QXqa6XmUJmabpxHEuo";

const razorpay = new Razorpay({ key_id: RZP_KEY_ID, key_secret: RZP_KEY_SECRET });

/* Shared options for Gen-2 HTTPS/Callable */
const defaultOpts = {
  region: "asia-south1",
  memory: "1GiB",
  cpu: 1,
  timeoutSeconds: 540,
};

exports.verifyPayment = onCall({ ...defaultOpts }, async ({ paymentId }) => {
  if (!paymentId) throw new functions.https.HttpsError("invalid-argument", "Payment ID required");
  const payment  = await razorpay.payments.fetch(paymentId);
  return payment.status === "captured";
});

exports.createOneTimeOrder = onCall({ ...defaultOpts }, async ({ type, quantity }) => {
  const pricing = {
    swipes:      { amount: quantity * 100, receipt: `swipes_${quantity}` },
    compliments: { amount: quantity * 150, receipt: `compliments_${quantity}` },
    boosts:      { amount: quantity * 200, receipt: `boosts_${quantity}` },
  };
  const p = pricing[type];
  if (!p) throw new functions.https.HttpsError("invalid-argument", "Unknown type");
  const order = await razorpay.orders.create({ amount: p.amount, currency: "INR", receipt: p.receipt });
  return { id: order.id, key: RZP_KEY_ID };
});

/* ───────────────────────────── Chat suggestions ───────────────────────────── */

/* maps “hi”, “bn”, … → prompt fragment */
const LANG = { hi: "Hindi", bn: "Bengali", en: "English", ta: "Tamil", kn: "Kannada", te: "Telegu" };

exports.chatSuggestions = onRequest({ ...defaultOpts, cpu: 2 }, async (req, res) => {
  if (req.method === "OPTIONS") return res.set({ "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Methods": "POST", "Access-Control-Allow-Headers": "Content-Type" }).status(204).send("");
  if (req.method !== "POST") return res.status(405).send("POST only");
  try {
    const { messages = [], lang } = req.body || {};
    const language = LANG[(lang || "en").slice(0,2).toLowerCase()] || "English";
    const gptMsgs = [
      { role: "system", content: `You are a Chat‑Suggestion Engine; ALWAYS reply in ${language}. Return JSON only.` },
      { role: "user",   content: messages.slice(-10).map(m => `${m.role}: ${m.text ?? "[image]"}`).join("\n") },
    ];
    const completion = await openai.chat.completions.create({ model: "gpt-4.1", messages: gptMsgs, temperature: 0.7, max_tokens: 400, response_format: { type: "json_object" } });
    return res.set("Access-Control-Allow-Origin", "*").send(completion.choices[0].message.content.trim());
  } catch (e) { return res.status(500).send(e.message); }
});

exports.getNearbyProfiles = onCall({ ...defaultOpts, memory: "2GiB", cpu: 2 }, async ({ uid, minRows = 50 }) => {
  if (!uid) throw new functions.https.HttpsError("invalid-argument", "uid required");
  const db = admin.database();
  const locSnap = await db.ref(`geoFireLocations/${uid}/l`).get();
  const latLng = locSnap.val();
  if (!Array.isArray(latLng)) {
    const all = await db.ref("users").get();
    const list = [];
    all.forEach(s => { if (s.key !== uid) { const p = s.val(); if (p) { p.userId = s.key; list.push(p); } } });
    return { profiles: list.slice(0, minRows) };
  }
  const center = { lat: latLng[0], lng: latLng[1] };
  const collected = new Set();
  const sweep = async rKm => {
    if (rKm === Infinity) { (await db.ref("geoFireLocations").get()).forEach(s => collected.add(s.key)); return; }
    const bounds = geohashQueryBounds([center.lat, center.lng], rKm*1000);
    await Promise.all(bounds.map(async b => {
      const snap = await db.ref("geoFireLocations").orderByChild("g").startAt(b[0]).endAt(b[1]).get();
      snap.forEach(c => {
        const [lat,lng] = c.child("l").val() || [];
        if (lat == null) return;
        if (distanceBetween([lat,lng],[center.lat,center.lng]) <= rKm) collected.add(c.key);
      });
    }));
  };
  await sweep(15000);
  if (collected.size < minRows) await sweep(Infinity);
  collected.delete(uid);
  const profiles = (await Promise.all(Array.from(collected).slice(0,minRows).map(id => db.ref(`users/${id}`).get())))
    .map(s => { const p = s.val(); if (p) { p.userId = s.key; return p; } return null; })
    .filter(Boolean);
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

exports.createKupidxPlusSub = onCall({ ...defaultOpts }, async ({ uid, planId }) => {
  if (!uid || !planId) throw new functions.https.HttpsError("invalid-argument", "uid+planId required");
  const sub = await razorpay.subscriptions.create({ plan_id: planId, customer_notify: 1, total_count: PLAN_CYCLES[planId]||1, notes: { uid, planId } });
  await admin.database().ref(`users/${uid}/subscription`).set({ id: sub.id, planId, status: "created", nextCharge: sub.current_end });
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
exports.verifyKupidxPlusPayment = onCall({ ...defaultOpts }, async ({ paymentId, subscriptionId, signature }) => {
  if (!paymentId || !subscriptionId || !signature) throw new functions.https.HttpsError("invalid-argument", "all fields required");
  const expected = crypto.createHmac("sha256", RZP_KEY_SECRET).update(`${subscriptionId}|${paymentId}`).digest("hex");
  if (expected !== signature) throw new functions.https.HttpsError("permission-denied", "Bad signature");
  return { ok: true };
});

exports.createManualSubscriptionOrder = onCall({ ...defaultOpts }, async ({ amount, label }) => {
  if (!amount || !label) throw new functions.https.HttpsError("invalid-argument", "amount & label required");
  const order = await razorpay.orders.create({ amount: amount*100, currency: "INR", receipt: `manual_sub_${label}_${Date.now()}` });
  return { id: order.id, key: RZP_KEY_ID };
});

const PLAN_TIERS = {
  plan_QjnGf6wdQAmyi2:    { plus: true,  premium: false },  // ₹9 / week
  plan_QjpkdErsuewaUJ:    { plus: false, premium: true  },  // ₹29 / week
  plan_QjpkKQ5S3ur64Q:     { plus: true,  premium: false },  // ₹39 / month
  plan_QjplxIqveB0BVS:     { plus: false, premium: true  },  // ₹99 / month
  plan_QjpmNjEkEPlObK:     { plus: true,  premium: false },  // ₹399 / year
  plan_QjmpS4xg31rg:       { plus: false, premium: true  },  // ₹999 / year
};

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
      }
    });

    await usersRef.update(updates);
    console.log("Expired one-time subscriptions reset.");
  });

exports.cancelKupidxPlusSub = onCall({ ...defaultOpts }, async (_data, ctx) => {
  const uid = ctx.auth?.uid;
  if (!uid) throw new functions.https.HttpsError("unauthenticated", "login required");
  const subId = (await admin.database().ref(`users/${uid}/subscription/id`).get()).val();
  if (!subId) throw new functions.https.HttpsError("not-found", "No subscription");
  await razorpay.subscriptions.cancel(subId);
  await admin.database().ref(`users/${uid}`).update({ isPlus:false, isPremium:false, subscriptionStatus:"inactive" });
  return { cancelled:true };
});

exports.kupidxPlusWebhook = onRequest({ ...defaultOpts, memory:'512MiB', cpu:0.25, maxInstances:5 }, async (req, res) => {
  const sig = req.headers["x-razorpay-signature"];
  try { razorpay.webhooks.verify(req.rawBody, sig, RAZORPAY_SECRET); }
  catch { return res.status(400).send("fail"); }

  const { event, payload } = req.body;
  const uid = payload.subscription.entity.customer_id;
  const db  = admin.database().ref(`users/${uid}`);
  const PLAN_TIERS = {
    plan_QjnGf6wdQAmyi2:{plus:true, premium:false}, plan_QjpkdErsuewaUJ:{plus:false,premium:true},
    plan_QjpkKQ5S3ur64Q:{plus:true, premium:false}, plan_QjplxIqveB0BVS:{plus:false,premium:true},
    plan_QjpmNjEkEPlObK:{plus:true, premium:false}, plan_QjmpS4xg31rg:{plus:false,premium:true},
  };
  switch (event) {
    case "subscription.activated":
    case "subscription.charged": {
      const planId = payload.subscription.entity.plan_id;
      const tier = PLAN_TIERS[planId] || { plus:false, premium:false };
      await db.update({ isPlus:tier.plus, isPremium:tier.premium, subscriptionStatus:"active", nextRenewal:payload.subscription.entity.current_end });
      break;
    }
    case "subscription.charged.failed":
    case "subscription.cancelled":
    case "subscription.completed": {
      await db.update({ isPlus:false, isPremium:false, subscriptionStatus:event.includes("failed")?"payment_failed":"inactive", nextRenewal:null });
      break;
    }
  }
  return res.status(200).send("ok");
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
      let boosts, compliments;

      if (data.isPremium) {
        boosts      = 5;
        compliments = 5;
      } else if (data.isPlus) {
        boosts      = 3;
        compliments = 3;
      } else {
        return; // skip free users
      }

      updates[`users/${uid}/availableBoosts`]      = boosts;
      updates[`users/${uid}/availableCompliments`] = compliments;
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
      .transaction(c => (c || 0) + delta);
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
                 .set(change.after.val());
    });

        // secondary instance

exports.pushSummary = functions.pubsub
  .schedule('every 15 minutes')
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
