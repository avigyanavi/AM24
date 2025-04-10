const { onRequest } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const axios = require("axios");

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

    // Call the Groq-compatible API
    const response = await axios.post(
      `${GROQ_API_BASE}/chat/completions`,
      {
        model,
        messages,
        max_tokens,
        stream: false // using non-streaming for easier Firebase compatibility
      },
      {
        headers: {
          "Authorization": `Bearer ${GROQ_API_KEY}`,
          "Content-Type": "application/json"
        }
      }
    );

    const result = response.data.choices[0].message;
    logger.debug("Final generated text:", result.content);

    res.status(200).json({
      choices: [
        { message: result }
      ]
    });
  } catch (error) {
    logger.error("An error occurred:", error);
    res.status(500).json({ error: error.message });
  }
});
