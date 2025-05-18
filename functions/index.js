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
const LANG = { hi: "Hindi", bn: "Bengali", en: "English" };

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

  /**
   * Callable: getNearbyProfiles
   *   data = { uid, maxDistance, minRows }
   *
   * • Looks up the caller’s saved lat/lng in /geoFireLocations/{uid}/l
   * • Runs one geo-hash sweep at maxDistance km
   * • If we still have < minRows ⇒ does one extra world-wide sweep
   * • Pulls each profile from /users/* and returns JSON
   *
   * Notes
   *   – `WORLDWIDE_DISTANCE` (101 km in the app) means “no distance filter”.
   *   – Designed for asia-south1; tweak memory / timeout if you like.
   */
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
