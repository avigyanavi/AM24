const { onRequest } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const axios = require("axios");
/* eslint-disable camelcase */
const functions  = require("firebase-functions");
const admin      = require("firebase-admin");
const OpenAI     = require("openai").default;
const Busboy = require("busboy");
const { v4: uuidv4 } = require("uuid");

const fetch = require("node-fetch");

const openai = new OpenAI({
  apiKey: "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"   // make sure this env var is set
});

admin.initializeApp({
  databaseURL: "https://kupidxdefault.asia-southeast1.firebasedatabase.app"
});

// ── Your Razorpay secret (the one you pasted: 27346b6a8…1c01) ──
const RAZORPAY_SECRET = '27346b6a824152fe1d0404a56f7d587b326fcb7e4bfd287225188bd25c771c01';

/* ───────────────────────────── Razorpay callable ───────────────────────────── */

const Razorpay = require("razorpay");
const razorpay = new Razorpay({
  key_id:     "rzp_test_PEBgJvcT9jIT7O",
  key_secret: "HM0OOCqESrzteQG1oRO7Lplz",
});

exports.verifyPayment = functions.https.onCall(async (data, context) => {
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
    const {
      uid,
      minRows = 50
    } = data || {};

    if (!uid)
      throw new functions.https.HttpsError('invalid-argument', 'uid is required');

    const db = admin.database();
    const locSnap = await db.ref(`geoFireLocations/${uid}/l`).get();
    const latLng = locSnap.val();  // [lat, lng]

    if (!Array.isArray(latLng) || latLng.length < 2) {
      const all = await db.ref('users').get();
      const list = [];
      all.forEach(ss => { if (ss.key !== uid) list.push(ss.val()); });
      return { profiles: list.slice(0, minRows) };
    }

    const center = { lat: latLng[0], lng: latLng[1] };
    const collectedUids = new Set();

    const sweep = async radiusKm => {
      if (radiusKm === Infinity) {
        const all = await db.ref('geoFireLocations').get();
        all.forEach(s => collectedUids.add(s.key));
        return;
      }

      const bounds = geohashQueryBounds([center.lat, center.lng], radiusKm * 1000);
      const tasks = bounds.map(b =>
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

    /* ✅ TEMP: override radius to 15000 km */
    const firstRadius = 15000;
    console.log(`[getNearbyProfiles] TEMP radius forced to ${firstRadius}km`);
    await sweep(firstRadius);

    if (collectedUids.size < minRows) {
      console.log(`[getNearbyProfiles] Fewer than ${minRows} users found, sweeping globally...`);
      await sweep(Infinity);
    }

    collectedUids.delete(uid); // drop self
    const uids = Array.from(collectedUids).slice(0, minRows);

    console.log('[getNearbyProfiles] Final UID list:', uids);

    const docs = await Promise.all(
      uids.map(id => db.ref(`users/${id}`).get())
    );

    const profiles = docs.map(s => s.val()).filter(Boolean);
    console.log('[getNearbyProfiles] returning', profiles.length, 'profiles');
    return { profiles };
  });


/* ─── razorpayWebhook (HTTP) ─── */
//exports.razorpayWebhook = functions
//  .region('asia-south1')
//  .https.onRequest(async (req, res) => {
//    const body = req.rawBody;              // keep raw for signature check
//    const sig  = req.get('X-Razorpay-Signature');
//    const crypto = require('crypto');
//
//    const expected = crypto
//      .createHmac('sha256', razorpay.key_secret)
//      .update(body)
//      .digest('hex');
//
//    if (expected !== sig) return res.status(400).send('bad sig');
//
//    const event = req.body.event;
//    const payload = req.body.payload || {};
//
//    /* handle a few key events */
//    if (event === 'subscription.charged') {
//      const subId  = payload.subscription.entity.id;
//      const userId = payload.subscription.entity.notes?.firebaseUid;     // store uid in notes when you create subs
//      const next   = payload.payment.entity.acquirer_data.next_payment_date;
//
//      if (userId) {
//        await admin.database().ref(`users/${userId}/premiumStatus/expiryDate`).set(Date.parse(next));
//      }
//    }
//
//    if (event === 'subscription.cancelled') {
//      const subId  = payload.subscription.entity.id;
//      const userId = payload.subscription.entity.notes?.firebaseUid;
//      if (userId) {
//        await admin.database().ref(`users/${userId}/premiumStatus`).update({ isPremium: false });
//      }
//    }
//
//    res.send('ok');
//  });
//
///* ─── verifySubscriptionPayment (callable) ─── */
//exports.verifySubscriptionPayment = functions
//  .region('asia-south1')
//  .https.onCall(async (data, context) => {
//    const { uid, paymentId, subscriptionId, signature } = data || {};
//    if (!uid || !paymentId || !subscriptionId || !signature)
//      throw new functions.https.HttpsError('invalid-argument', 'uid, paymentId, subscriptionId, and signature are required');
//
//    /* 1️⃣  verify HMAC (signature = HMAC_SHA256(subscriptionId|paymentId, secret)) */
//    const crypto = require('crypto');
//    const expected = crypto
//      .createHmac('sha256', razorpay.key_secret)
//      .update(`${subscriptionId}|${paymentId}`)
//      .digest('hex');
//
//    if (expected !== signature)
//      throw new functions.https.HttpsError('permission-denied', 'Invalid signature');
//
//    /* 2️⃣  fetch the payment object and ensure it is captured */
//    const payment = await razorpay.payments.fetch(paymentId);
//    if (payment.status !== 'captured')
//      throw new functions.https.HttpsError('failed-precondition', `Payment not captured (${payment.status})`);
//
//    /* 3️⃣  write premiumStatus */
//    const db   = admin.database();
//    const next = Date.parse(payment.acquirer_data?.next_payment_date) || 0;
//    await db.ref(`users/${uid}/premiumStatus`).set({
//      isPremium: true,
//      subscriptionId,
//      paymentId,
//      expiryDate: next,
//    });
//
//    return { ok: true, expiryDate: next };
//  });


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
const crypto = require('crypto');
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

exports.razorpayWebhook = functions
  .region('asia-south1')
  .https.onRequest(async (req, res) => {
    if (req.method !== 'POST') {
      return res.status(405).send('Method Not Allowed');
    }

    // 1) Verify HMAC
    const signature = req.headers['x-razorpay-signature'] || '';
    const bodyRaw   = JSON.stringify(req.body);
    const expected  = crypto
      .createHmac('sha256', RAZORPAY_SECRET)
      .update(bodyRaw)
      .digest('hex');
    if (signature !== expected) {
      console.error('❌ Invalid signature:', { expected, received: signature });
      return res.status(400).send('Invalid signature');
    }

    // 2) Dispatch
    const event   = req.body.event;
    const payment = req.body.payload?.payment?.entity;
    if (payment) {
      const uid = payment.notes?.uid;
      if (event === 'payment.captured' && uid) {
        // 👉 Write `isPremium` at users/{uid}/isPremium
        const expiry = new Date(Date.now() + 365*24*60*60*1000).toISOString();
        await admin.database()
          .ref(`users/${uid}/isPremium`)
          .set(true);
        // Optional: store expiry separately if you still want it
        await admin.database()
          .ref(`users/${uid}/expiryDate`)
          .set(expiry);

        console.log(`✅ Marked ${uid} premium until ${expiry}`);
      }
      else if (event === 'payment.failed' && uid) {
        console.warn(`❌ Payment.failed for ${uid}`);
      }
    }

    // 3) Ack
    res.json({ status: 'ok' });
  });