const { onRequest } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const axios = require("axios");
const functions = require("firebase-functions");
const admin = require("firebase-admin");
const Busboy = require("busboy");
const { v4: uuidv4 } = require("uuid");
const OpenAI = require("openai");
const fetch = require("node-fetch");

admin.initializeApp();
const db = admin.firestore();
const bucket = admin.storage().bucket();

// GroqCloud-compatible OpenAI settings
const GROQ_API_KEY = "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";
const GROQ_API_BASE = "https://api.groq.com/openai/v1";

// Utility to clean messages like in the Python version
function cleanMessages(messages) {
  return messages.map((m) => {
    const cleaned = {
      role: m.role,
      content: m.content,
    };
    if (m.name) cleaned.name = m.name;
    return cleaned;
  });
}

exports.chat = onRequest(async (req, res) => {
  try {
    const data = req.body;
    logger.debug("Received request data:", data);

    const model = data.model || "llama3-70b-8192";
    const rawMessages = data.messages || [];
    const max_tokens = data.max_tokens || 8000;

    const messages = cleanMessages(rawMessages);
    logger.debug("Cleaned messages:", messages);

    const response = await axios.post(
      `${GROQ_API_BASE}/chat/completions`,
      {
        model,
        messages,
        max_tokens,
        stream: false,
      },
      {
        headers: {
          Authorization: `Bearer ${GROQ_API_KEY}`,
          "Content-Type": "application/json",
        },
      }
    );

    const result = response.data.choices[0].message;
    logger.debug("Final generated text:", result.content);

    res.status(200).json({
      choices: [{ message: result }],
    });
  } catch (error) {
    logger.error("An error occurred:", error);
    res.status(500).json({ error: error.message });
  }
});

// Avatar Generator using OpenAI
const openai = new OpenAI({ apiKey: "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" });

exports.generateGhibliAvatar = functions.https.onRequest(async (req, res) => {
  if (req.method !== "POST") return res.status(405).send("Only POST allowed");

  const uid        = req.headers["uid"];
  const isPaidUser = req.headers["ispaiduser"] === "true";
  if (!uid || !isPaidUser) return res.status(403).send("Access denied");

  const imageBuffers = [];
  const busboy = new Busboy({ headers: req.headers });

  busboy.on("file", (fieldname, file) => {
    const buf = [];
    file.on("data", (data) => buf.push(data));
    file.on("end", () => imageBuffers.push(Buffer.concat(buf)));
  });

  busboy.on("finish", async () => {
    try {
      const base64Images = imageBuffers.map(b =>
        `data:image/jpeg;base64,${b.toString("base64")}`
      );

      const response = await openai.images.generate({
        model: "gpt-image-1",
        prompt: "Create a Studio Ghibli-style portrait based on the provided user images.",
        n: 1,
        size: "1024x1024",
        response_format: "url",
        image: base64Images,
        moderation: "low"
      });

      const imageUrl = response.data?.[0]?.url;
      if (!imageUrl) throw new Error("Avatar generation failed");

      const imgResp = await fetch(imageUrl);
      const avatarBuffer = await imgResp.buffer();

      const filename = `avatars/${uid}/ghibli_${uuidv4()}.png`;
      const file = bucket.file(filename);
      const stream = file.createWriteStream({ metadata: { contentType: "image/png" } });

      stream.end(avatarBuffer);
      stream.on("finish", async () => {
        const downloadURL = `https://storage.googleapis.com/${bucket.name}/${filename}`;
        const userRef = db.collection("users").doc(uid);
        const userDoc = await userRef.get();
        const data    = userDoc.data() || {};
        const pics    = data.pictures || [];

        // Keep up to 6 pictures
        if (pics.length >= 5) pics.push(downloadURL);
        else pics[0] = downloadURL;

        await userRef.update({
          profilePic: downloadURL,
          pictures: pics,
        });
        res.status(200).json({ success: true, avatarUrl: downloadURL });
      });

      stream.on("error", (err) => {
        console.error("Upload error:", err);
        res.status(500).send("Failed to upload avatar");
      });
    } catch (err) {
      console.error("Avatar generation error:", err);
      res.status(500).send("Avatar generation failed");
    }
  });

  req.pipe(busboy);
});

// ——————————————————————————————————————————————
// 2) New: AI‐Character image endpoint (always Ghibli style + low moderation)
// ——————————————————————————————————————————————
exports.generateAICharacterAvatar = functions.https.onRequest(async (req, res) => {
  if (req.method !== "POST") return res.status(405).send("Only POST allowed");

  // Identify which character this is for:
  const charId = req.headers["character-id"];
  if (!charId) return res.status(400).send("Missing character-id header");

  // Expect a JSON body: { prompt: "some scene or style description", size?: "512x512" }
  const { prompt = "", size = "1024x1024" } = req.body;
  if (!prompt.trim()) return res.status(400).send("Missing prompt in request body");

  try {
    // Prepend “Studio Ghibli style” to every prompt
    const fullPrompt = `Studio Ghibli-style illustration: ${prompt}`;

    const response = await openai.images.generate({
      model: "gpt-image-1",
      prompt: fullPrompt,
      n: 1,
      size,
      response_format: "url",
      moderation: "low"
    });

    const imageUrl = response.data?.[0]?.url;
    if (!imageUrl) throw new Error("Image generation failed");

    const imgResp = await fetch(imageUrl);
    const imgBuffer = await imgResp.buffer();

    const filename = `ai_characters/${charId}/${uuidv4()}.png`;
    const file = bucket.file(filename);
    const stream = file.createWriteStream({ metadata: { contentType: "image/png" } });

    stream.end(imgBuffer);
    stream.on("finish", async () => {
      const downloadURL = `https://storage.googleapis.com/${bucket.name}/${filename}`;

      // Append this image into a Firestore array on the character doc
      await db.collection("ai_characters")
              .doc(charId)
              .set({
                images: FieldValue.arrayUnion(downloadURL)
              }, { merge: true });

      res.status(200).json({ success: true, imageUrl: downloadURL });
    });

    stream.on("error", (err) => {
      console.error("Upload error:", err);
      res.status(500).send("Failed to upload AI character image");
    });

  } catch (err) {
    console.error("AI Character image error:", err);
    res.status(500).send("AI character image generation failed");
  }
});