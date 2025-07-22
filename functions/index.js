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

const CURSOR_REF = uid =>
  admin.database().ref(`paging/nearbyCursor/${uid}`);   // ⇦ stores last UID sent

exports.getNearbyProfiles = functions
  .region('asia-south1')
  .runWith({ timeoutSeconds: 540, memory: '1GB' })
  .https.onCall(async (data, context) => {

    /* ───────── arguments ───────── */
    const { uid, minRows = 200, afterId } = data || {};
    if (!uid) throw new functions.https.HttpsError('invalid-argument', 'uid required');

    const db = admin.database();

    /* ───────── find caller’s location ─────── */
    const locSnap = await db.ref(`geoFireLocations/${uid}/l`).get();
    const latLng  = locSnap.val();               // [lat,lng]

    /* ──────── fallback: no location ⇒ random users ─────── */
    if (!Array.isArray(latLng) || latLng.length < 2) {
      const allSnap = await db.ref('users').get();
      const list = [];
      allSnap.forEach(s => {
        if (s.key !== uid && s.val()) {
          const p = s.val();
          p.userId = s.key;
          list.push(p);
        }
      });
      return { profiles: list.slice(0, minRows) };
    }

    /* ───────── collect all candidate UIDs (same as before) ───────── */
    const center = { lat: latLng[0], lng: latLng[1] };
    const collected = new Set();

    const sweep = async radiusKm => {
      if (radiusKm === Infinity) {
        const all = await db.ref('geoFireLocations').get();
        all.forEach(s => collected.add(s.key));
        return;
      }
      const bounds = geohashQueryBounds([center.lat, center.lng], radiusKm * 1_000);
      const tasks  = bounds.map(b =>
        db.ref('geoFireLocations')
          .orderByChild('g').startAt(b[0]).endAt(b[1]).get());
      (await Promise.all(tasks)).forEach(snap => {
        snap.forEach(child => {
          const [lat, lng] = child.child('l').val() || [];
          if (lat == null) return;
          const dist = distanceBetween([lat, lng], [center.lat, center.lng]);
          if (dist <= radiusKm) collected.add(child.key);
        });
      });
    };

    await sweep(15_000);                 // TEMP: wide sweep
    if (collected.size < minRows) await sweep(Infinity);

    collected.delete(uid);               // don’t show self
    let ordered = Array.from(collected).sort();

    /* ───────── cursor handling ───────── */
    let cursor = afterId;
    if (!cursor) {
      const snap = await CURSOR_REF(uid).get();   // may be null on first ever call
      cursor = snap.val() || null;
    }
    if (cursor) {
      const idx = ordered.indexOf(cursor);
           if (idx >= 0) {
             ordered = ordered.slice(idx + 1);
           } else {
             cursor = null; // invalid cursor – restart
           }
    }

    const pageUids = ordered.slice(0, minRows);

    /* store cursor for NEXT call (null if no more pages) */
    const nextCursor = pageUids.length ? pageUids[pageUids.length - 1] : null;
    await CURSOR_REF(uid).set(nextCursor);

    /* ───────── fetch user docs ───────── */
    const profiles = (await Promise.all(
      pageUids.map(id => db.ref(`users/${id}`).get())
    ))
      .map(snap => {
        const p = snap.val();
        if (!p) return null;
        p.userId = snap.key;
        return p;
      })
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

    /* ---------- PayPal plans ---------- */
    'P-1M705186NH640511WNBUUE3Q': { plus:true,  premium:false },
    'P-1DH49334GG657434NMBUUGDQ': { plus:true,  premium:false },
    'P-0MX08011N33928941NBUUIEY': { plus:false, premium:true  },
    'P-58J68335TFT149934NBUUHPA': { plus:false, premium:true  },
};


const FREE_SWIPE_QUOTA = 20;
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
const PAYPAL_PLANS = new Set([
  'P-1M705186NH640511WNBUUE3Q',   // Plus  – Monthly  $4.99
  'P-0MX08011N33928941NBUUIEY',   // Premium – Monthly $9.99
  'P-1DH49334GG657434NMBUUGDQ',   // Plus  – Annual   $49.99
  'P-58J68335TFT149934NBUUHPA',   // Premium – Annual  $99.99
]);

const PAYPAL_ENV   = (functions.config().paypal.environment || 'sandbox').toLowerCase();
const PAYPAL_API   = PAYPAL_ENV === 'live'
                       ? 'https://api-m.paypal.com'
                       : 'https://api-m.sandbox.paypal.com';
const PAYPAL_ID    = functions.config().paypal.client_id;
const PAYPAL_SECRET= functions.config().paypal.client_secret;

async function paypalToken () {
  const r = await fetch(`${PAYPAL_API}/v1/oauth2/token`, {
    method : 'POST',
    headers: { 'Content-Type':'application/x-www-form-urlencoded',
               'Authorization':'Basic ' + Buffer.from(`${PAYPAL_ID}:${PAYPAL_SECRET}`).toString('base64') },
    body   : 'grant_type=client_credentials'
  });
  const { access_token } = await r.json();
  return access_token;
}

/* ① create an order – called from Android for *non-IN* users */
exports.createPaypalOrder = functions
  .region('asia-south1')
  .https.onCall(async (data, _ctx) => {
    const { amountUsd, label } = data || {};
    if (!amountUsd) throw new functions.https.HttpsError('invalid-argument','amountUsd missing');

    const token  = await paypalToken();
    const res    = await fetch(`${PAYPAL_API}/v2/checkout/orders`, {
      method : 'POST',
      headers: { 'Content-Type':'application/json', 'Authorization':`Bearer ${token}` },
      body   : JSON.stringify({
        intent: 'CAPTURE',
        purchase_units: [{
          amount: { currency_code:'USD', value: amountUsd.toFixed(2) },
          custom_id: label                       // e.g.  "swipes_5"
        }],
        application_context: {
          return_url: functions.config().paypal.return_url + '?oneTime=true',   // deep-links back
          cancel_url: functions.config().paypal.cancel_url
        }
      })
    });
    const json   = await res.json();
    const approve = json.links
       .find(l => l.rel === 'payer-action' || l.rel === 'approve')?.href;
    return { id: json.id, approve };           // Android opens `approve` WebView
  });

/* ② after the user is sent back, Android → this callable to capture & credit */
exports.capturePaypalOrder = functions
  .region('asia-south1')
  .https.onCall(async (data, _ctx) => {
    const { orderId } = data || {};
    if (!orderId) throw new functions.https.HttpsError('invalid-argument','orderId missing');

    const token  = await paypalToken();
    const res    = await fetch(`${PAYPAL_API}/v2/checkout/orders/${orderId}/capture`, {
      method : 'POST',
      headers: { 'Content-Type':'application/json', 'Authorization':`Bearer ${token}` }
    });
    const json   = await res.json();
    const status = json.status;                       // COMPLETED ?

    if (status !== 'COMPLETED') return { ok:false };

    /* pull what the Android labelled it with                        *
     * custom_id format = "<apiType>_<qty>"  → e.g. "swipes_10"     */
    const custom = json.purchase_units?.[0]?.custom_id || '';
    const [apiType, qtyStr] = custom.split('_');
    const qty = Number(qtyStr || 0);

    /* credit the user exactly like you do in OneTimePurchaseScreen */
    const uid = _ctx.auth?.uid || json.payer?.payer_id;   // fallback
    if (uid && qty > 0) {
      const ref = admin.database().ref(`users/${uid}`);
      const field = {
        swipes      :'swipesInfo/remainingSwipes',
        compliments :'availableCompliments',
        boosts      :'availableBoosts',
        aiMessages  :'availableAiMessages'
      }[apiType];

      if (field) {
        await ref.child(field).transaction(v => (v || 0) + qty);
      }
    }

    return { ok:true };
  });

/**
 * Callable ⇢ verifyPaypalSubscription({ subscriptionId: "I-XXXX" }) → { valid:Boolean, status:String }
 */
exports.verifyPaypalSubscription = functions
  .region('asia-south1')              // 👈 added
  .https.onCall(async (data, context) => {
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

  const access_token = await paypalToken();

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

  const valid = status === "ACTIVE" && PAYPAL_PLANS.has(planId);

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

exports.paypalWebhook = functions
  .region('asia-south1')
  .https.onRequest(async (req, res) => {
    const cfg      = functions.config().paypal;
    const env      = (cfg.environment || 'sandbox').toLowerCase();
    const apiBase  = env === 'live'
                       ? 'https://api-m.paypal.com'
                       : 'https://api-m.sandbox.paypal.com';

    /* 1️⃣ Verify the signature */
    const verifyRes = await fetch(`${PAYPAL_API}/v1/notifications/verify-webhook-signature`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Basic ' + Buffer.from(
          `${cfg.client_id}:${cfg.client_secret}`
        ).toString('base64')
      },
      body: JSON.stringify({
        auth_algo:          req.headers['paypal-auth-algo'],
        cert_url:           req.headers['paypal-cert-url'],
        transmission_id:    req.headers['paypal-transmission-id'],
        transmission_sig:   req.headers['paypal-transmission-sig'],
        transmission_time:  req.headers['paypal-transmission-time'],
        webhook_id:         cfg.webhook_id,          // 👈 from PayPal dashboard
        webhook_event:      req.body
      })
    });
    const { verification_status } = await verifyRes.json();
    if (verification_status !== 'SUCCESS') {
      console.error('[paypalWebhook] bad sig');
      return res.status(400).send('bad signature');
    }

    /* 2️⃣ Process the event */
    const ev   = req.body.event_type;               // e.g. BILLING.SUBSCRIPTION.CANCELLED
    const sub  = req.body.resource;
    const plan = sub.plan_id;
    const uid  = sub.custom_id || sub.id;           // ↙︎ see note A

    const tier = PLAN_TIERS[plan] || { plus:false, premium:false };
    const db   = admin.database().ref(`users/${uid}`);

    switch (ev) {
      case 'BILLING.SUBSCRIPTION.ACTIVATED':
      case 'PAYMENT.SALE.COMPLETED':
        await db.update({
          isPlus:     tier.plus,
          isPremium:  tier.premium,
          subscriptionStatus: 'active',
          nextRenewal: new Date(sub.billing_info.next_billing_time).getTime(),
          swipesInfo: { remainingSwipes: tier.premium ? 2147483647 : 50 },
          availableBoosts:      tier.premium ? 5 : 3,
          availableCompliments: tier.premium ? 5 : 3,
          ...(tier.premium && { availableAiMessages: 2 }),
        });
        break;

      case 'BILLING.SUBSCRIPTION.CANCELLED':
      case 'BILLING.SUBSCRIPTION.SUSPENDED':
      case 'BILLING.SUBSCRIPTION.EXPIRED':
        await db.update({
          isPlus:false, isPremium:false,
          subscriptionStatus: ev.split('.').pop().toLowerCase(),
          nextRenewal:null,
        });
        break;
    }

    res.status(200).send('ok');
  });

  // Push the `lastLotteryDayOfYear` field to every user
  exports.backfillLastLotteryDayOfYear = functions
    .region('asia-south1')
    .https.onRequest(async (_req, res) => {
      try {
        const usersRef = admin.database().ref('users');
        const snap     = await usersRef.once('value');
        const updates  = {};

        snap.forEach(userSnap => {
          const data = userSnap.val() || {};
          if (data.lastLotteryDayOfYear === undefined) {
            updates[`${userSnap.key}/lastLotteryDayOfYear`] = null;
          }
        });

        await usersRef.update(updates);
        res.status(200).send(`updated ${Object.keys(updates).length} users`);
      } catch (err) {
        console.error('backfillLastLotteryDayOfYear error:', err);
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