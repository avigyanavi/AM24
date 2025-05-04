const { onRequest } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const axios = require("axios");
const functions = require("firebase-functions");
const admin = require("firebase-admin");
const Busboy = require("busboy");
const { v4: uuidv4 } = require("uuid");
const OpenAI = require("openai");
const fetch = require("node-fetch");

const openai = new OpenAI({
  apiKey: "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"   // make sure this env var is set
});


admin.initializeApp();
const db = admin.firestore();
const bucket = admin.storage().bucket();

const Razorpay = require('razorpay');

// Initialize Razorpay with your key ID and secret
const razorpay = new Razorpay({
    key_id: 'rzp_test_PEBgJvcT9jIT7O',
    key_secret: 'HM0OOCqESrzteQG1oRO7Lplz'
});

exports.verifyPayment = functions.https.onCall(async (data, context) => {
    try {
        const paymentId = data.paymentId;
        if (!paymentId) {
            throw new functions.https.HttpsError('invalid-argument', 'Payment ID is required.');
        }

        // Fetch payment details from Razorpay
        const payment = await razorpay.payments.fetch(paymentId);
        const isValid = payment.status === 'captured';

        console.log(`Payment verification for paymentId ${paymentId}: status=${payment.status}, isValid=${isValid}`);
        return isValid;
    } catch (error) {
        console.error(`Error verifying payment: ${error.message}`);
        throw new functions.https.HttpsError('internal', `Payment verification failed: ${error.message}`);
    }
});

exports.chatSuggestions = functions
  .region("asia-south1")
  .runWith({ timeoutSeconds: 540, memory: "512MB" })   // ⬅️ NEW
  .https.onRequest(async (req, res) => {
    /* ---- basic CORS ---- */
    if (req.method === "OPTIONS") {
      return res
        .set({
          "Access-Control-Allow-Origin" : "*",
          "Access-Control-Allow-Methods": "POST",
          "Access-Control-Allow-Headers": "Content-Type"
        })
        .status(204).send("");
    }
    if (req.method !== "POST") return res.status(405).send("POST only");

    try {
      const {
        messages      = [],
        currentProfile = {},
        otherProfile   = {}
      } = req.body || {};

      /* ---- build GPT-4.1 multimodal messages ---- */
      const gptMsgs = [
        {
          role: "system",
          content:
            `You are a “Chat-Suggestion Engine” for a dating app. ` +
            `Return **pure JSON** with up to 2 topics, 2 activities ` +
            `(fields: placeName, integration) and 2 integrationTips:\n` +
            `{"topics":[],"activities":[{"placeName":"","integration":""}],"integrationTips":[]}`
        },
        {
          role: "user",
          content: [
            {
              type: "text",
              text:
                `Conversation:\n` +
                messages
                  .slice(-10)
                  .map(m => `${m.role}: ${m.text ?? "[image]"}`)
                  .join(" | ") +
                `\n\nCurrent profile: ${currentProfile.name ?? "?"}` +
                ` | Other profile: ${otherProfile.name ?? "?"}`
            },
            ...messages
              .filter(m => m.imageUrl)
              .slice(-3)                   // send at most 3 images
              .map(m => ({
                type: "image_url",
                image_url: { url: m.imageUrl }
              }))
          ]
        }
      ];

      /* ---- GPT-4.1 call ---- */
      const completion = await openai.chat.completions.create({
        model: "gpt-4.1",
        messages: gptMsgs,
        temperature: 0.7,
        max_tokens: 400
      });

      let out = completion.choices?.[0]?.message?.content ?? "{}";
      out = out.replace(/```json|```/g, "").trim();  // strip fences if present

      return res
        .set("Access-Control-Allow-Origin", "*")
        .json(JSON.parse(out));
    } catch (err) {
      console.error("chatSuggestions error:", err);
      return res.status(500).send(err.message ?? "internal error");
    }
  });