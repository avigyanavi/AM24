const logger = require("firebase-functions/logger");
const axios = require("axios");
/* eslint-disable camelcase */
const functions  = require("firebase-functions");
const admin      = require("firebase-admin");
const OpenAI     = require("openai").default;
const crypto  = require("crypto");

const fetch = require("node-fetch");
const {google} = require("googleapis");

const openai = new OpenAI({
 apiKey: "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"   // make sure this env var is set
});

const RAW_STORAGE_BUCKET_NAMES = [
  "am-twentyfour.appspot.com",
  "am-twentyfour",
];

const DATABASE_CONFIGS = [
  {
    name: "kupidxdefault",
    url: "https://kupidxdefault.asia-southeast1.firebasedatabase.app",
  },
  {
    name: "am-twentyfour",
    url: "https://am-twentyfour.firebaseio.com",
  },
  {
    name: "kupidx",
    url: "https://kupidx.asia-southeast1.firebasedatabase.app",
  },
];

const bucketCache = new Map();
let DEFAULT_STORAGE_BUCKETS = [];

function sanitizeBucketName(name) {
  if (!name) return null;
  return name.replace(/^gs:\/\//, "").replace(/\/+$/, "");
}

function detectDefaultStorageBucket() {
  const hasDefaultApp = admin.apps.length > 0;
  const fromOptions = hasDefaultApp ? sanitizeBucketName(admin.app().options?.storageBucket) : null;
  if (fromOptions) return fromOptions;

  try {
    if (process.env.FIREBASE_CONFIG) {
      const parsed = JSON.parse(process.env.FIREBASE_CONFIG);
      if (parsed.storageBucket) {
        const fromConfig = sanitizeBucketName(parsed.storageBucket);
        if (fromConfig) return fromConfig;
      }
    }
  } catch (err) {
    functions.logger.warn("Failed to parse FIREBASE_CONFIG for storage bucket", err);
  }

  if (process.env.GCLOUD_PROJECT) {
    return sanitizeBucketName(`${process.env.GCLOUD_PROJECT}.appspot.com`);
  }

  return sanitizeBucketName("am-twentyfour.appspot.com");
}

const CONFIGURED_STORAGE_BUCKET_NAMES = Array.from(
  new Set(RAW_STORAGE_BUCKET_NAMES.map(sanitizeBucketName).filter(Boolean)),
);

const primaryAppOptions = {
  databaseURL: DATABASE_CONFIGS[0].url,
};

if (CONFIGURED_STORAGE_BUCKET_NAMES[0]) {
  primaryAppOptions.storageBucket = CONFIGURED_STORAGE_BUCKET_NAMES[0];
}

const primaryApp = admin.initializeApp(primaryAppOptions);

const databaseTargets = DATABASE_CONFIGS.map((config, index) => {
  if (index === 0) {
    return { ...config, app: primaryApp, db: primaryApp.database() };
  }
  const app = admin.initializeApp({ databaseURL: config.url }, `db-${config.name}`);
  return { ...config, app, db: app.database() };
});

let activeDatabaseIndex = 0;
let db     = databaseTargets[activeDatabaseIndex]?.db;

const getUsersRef = () => db.ref('users');
const getChatId = (uid1, uid2) => (uid1 < uid2 ? `${uid1}_${uid2}` : `${uid2}_${uid1}`);

function snapshotToMessage(child) {
  if (!child || typeof child.val !== 'function') return null;
  const val = child.val() || {};
  if (typeof val !== 'object') return null;
  return {
    id: val.id || child.key || '',
    senderId: val.senderId || '',
    receiverId: val.receiverId || '',
    text: val.text || '',
    timestamp: Number(val.timestamp) || Date.now(),
    read: Boolean(val.read),
    mediaType: val.mediaType || null,
    mediaUrl: val.mediaUrl || null,
    processed: Boolean(val.processed),
    isPost: Boolean(val.isPost),
  };
}

function toJson(snap) {
  if (!snap || !snap.exists || !snap.exists()) return null;
  const v = snap.val() || null;
  return v ? { ...v, userId: snap.key } : null;
}

const AVAILABLE_DATABASE_NAMES = databaseTargets.map((target) => target.name);

const FAILOVER_TIMEOUT_MS = 5_000;
const FAILOVER_CHECK_INTERVAL_MS = 5 * 60 * 1_000;
let lastHealthCheckTs = 0;

function setActiveDatabase(index, reason = "") {
  if (index < 0 || index >= databaseTargets.length) return;
  if (activeDatabaseIndex === index) return;

  activeDatabaseIndex = index;
  db = databaseTargets[activeDatabaseIndex].db;
  functions.logger.warn("Switched RTDB target", {
    activeDatabase: databaseTargets[activeDatabaseIndex].name,
    reason,
  });
}

function findFallbackIndex() {
  if (databaseTargets.length <= 1) return -1;
  for (let i = 1; i < databaseTargets.length; i += 1) {
    if (databaseTargets[i] && databaseTargets[i].db) return i;
  }
  return -1;
}

function withTimeout(promise, timeoutMs) {
  return Promise.race([
    promise,
    new Promise((_, reject) => {
      setTimeout(() => reject(new Error("timeout")), timeoutMs).unref?.();
    }),
  ]);
}

async function runPrimaryHealthCheck(force = false) {
  const primary = databaseTargets[0];
  if (!primary || !primary.db) return;

  const nowTs = Date.now();
  if (!force && nowTs - lastHealthCheckTs < FAILOVER_CHECK_INTERVAL_MS) return;
  lastHealthCheckTs = nowTs;

  try {
    await withTimeout(primary.db.ref(".info/serverTimeOffset").get(), FAILOVER_TIMEOUT_MS);
    if (activeDatabaseIndex !== 0) {
      setActiveDatabase(0, "primary healthy");
    }
  } catch (err) {
    const fallbackIndex = findFallbackIndex();
    if (fallbackIndex !== -1) {
      setActiveDatabase(fallbackIndex, `primary health check failed: ${err?.message || err}`);
    } else {
      functions.logger.error("Primary database health check failed and no fallback configured", err);
    }
  }
}

if (databaseTargets.length > 1) {
  runPrimaryHealthCheck(true).catch((err) => {
    functions.logger.error("Initial database health check failed", err);
  });

  setInterval(() => {
    runPrimaryHealthCheck().catch((err) => {
      functions.logger.error("Periodic database health check failed", err);
    });
  }, FAILOVER_CHECK_INTERVAL_MS).unref();
}

DEFAULT_STORAGE_BUCKETS = CONFIGURED_STORAGE_BUCKET_NAMES.slice();
const detectedBucket = detectDefaultStorageBucket();
if (detectedBucket && !DEFAULT_STORAGE_BUCKETS.includes(detectedBucket)) {
  DEFAULT_STORAGE_BUCKETS.push(detectedBucket);
}

const now    = () => Date.now();
const BOOST_DURATION_MS = 1 * 60 * 60 * 1_000;   // 1 h
const PAGE_SIZE = 50;
const PHONE_REGISTRATION_LIMIT = 300;
const PHONE_REG_COUNTER_ROOT   = "metrics/phoneRegistrations";
const PHONE_AUTH_CONFIG_PATH   = "config/phoneAuth";

const utcDateKey = (date = new Date()) => date.toISOString().slice(0, 10);
// Accent/case-insensitive string normalizer for country fields.
function normalizeCountry(name = '') {
  return String(name)
    .normalize('NFD')                      // split accents
    .replace(/[\u0300-\u036f]/g, '')      // strip accents
    .trim()
    .toLowerCase();
}

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
// ---------- Buckets + Storage helpers ----------
function getBucket(bucketName) {
  const sanitized = sanitizeBucketName(bucketName);
  const targetBucket = sanitized || DEFAULT_STORAGE_BUCKETS[0] || null;
  if (!targetBucket) return admin.storage().bucket();
  if (!bucketCache.has(targetBucket)) {
    bucketCache.set(targetBucket, admin.storage().bucket(targetBucket));
  }
  return bucketCache.get(targetBucket);
}

function gatherStorageUrls(data = {}) {
  const urls = new Set();
  const add = (v) => { if (typeof v === "string" && v.trim()) urls.add(v.trim()); };

  add(data.profilepicUrl);
  add(data.profilepicThumbnailUrl);
  add(data.voiceNoteUrl);
  if (Array.isArray(data.optionalPhotoUrls))  data.optionalPhotoUrls.forEach(add);
  if (Array.isArray(data.privateAlbumUrls))   data.privateAlbumUrls.forEach(add);

  return Array.from(urls);
}

const ACTIVE_FEED_PATH = 'feedIndex/ACTIVE';
const REVERSE_EPOCH = 9999999999999; // far future; invert current millis against this
function makeActiveRankKey(ts) {
  const t = Number(ts) || 0;
  const inv = REVERSE_EPOCH - t;
  return inv.toString().padStart(13, '0'); // keep fixed width for lexicographic sort
}
exports.cleanOrphanedStorage = functions
  .region("asia-south1")
  .runWith({ timeoutSeconds: 540, memory: "2GB" })
  .https.onRequest(async (req, res) => {
    const confirm = req.query.confirm === "true"; // dry-run by default
    const summaries = [];

    try {
      // Collect all URLs currently referenced in DB
      const referencedUrls = new Set();

      for (const target of databaseTargets) {
        const dbName = target.name;
        const usersSnap = await target.db.ref("users").once("value");
        let count = 0;

        usersSnap.forEach(userSnap => {
          const data = userSnap.val() || {};
          const urls = gatherStorageUrls(data);
          urls.forEach(u => referencedUrls.add(u));
          count++;
        });

        summaries.push({
          database: dbName,
          usersScanned: count,
          uniqueUrlsFound: referencedUrls.size
        });
      }

      // Normalize gs:// and HTTPS to direct gs:// format
      const normalizeUrl = (url) => {
        try {
          if (url.startsWith("gs://")) return url;
          const u = new URL(url);
          if (u.hostname.endsWith("googleapis.com")) {
            const parts = u.pathname.split("/");
            const bIndex = parts.indexOf("b");
            const oIndex = parts.indexOf("o");
            if (bIndex !== -1 && oIndex !== -1) {
              const bucket = parts[bIndex + 1];
              const objectPath = decodeURIComponent(parts[oIndex + 1]);
              return `gs://${bucket}/${objectPath}`;
            }
          }
        } catch {}
        return null;
      };

      const normalizedRefs = new Set();
      for (const url of referencedUrls) {
        const n = normalizeUrl(url);
        if (n) normalizedRefs.add(n);
      }

      // Scan all buckets
      for (const bucketName of DEFAULT_STORAGE_BUCKETS) {
        const bucket = getBucket(bucketName);
        const [files] = await bucket.getFiles({ autoPaginate: true });

        let orphaned = [];
        for (const file of files) {
          const gsUrl = `gs://${bucketName}/${file.name}`;
          if (!normalizedRefs.has(gsUrl)) {
            orphaned.push(gsUrl);
            if (confirm) await file.delete({ ignoreNotFound: true });
          }
        }

        summaries.push({
          bucket: bucketName,
          totalFiles: files.length,
          orphanedFiles: orphaned.length,
          deleted: confirm ? orphaned.length : 0
        });
      }

      return res.status(200).json({
        mode: confirm ? "DELETE CONFIRMED" : "DRY-RUN (no deletions)",
        summaries
      });
    } catch (err) {
      console.error("cleanOrphanedStorage error:", err);
      return res.status(500).json({ error: err.message });
    }
  });

exports.onLastActiveWrite = functions
  .region('asia-south1')
  .database.instance('kupidxdefault')           // match your default DB subdomain
  .ref('/users/{uid}/lastActive')
  .onWrite(async (change, ctx) => {
    const uid = ctx.params.uid;

    // If deleted/missing, remove any previous index row and exit.
    if (!change.after.exists()) {
      const oldKeySnap = await admin.database()
        .ref(`${ACTIVE_FEED_PATH}/byUser/${uid}/key`).get();
      const oldKey = oldKeySnap.val();
      if (oldKey) {
        const updates = {};
        updates[`${ACTIVE_FEED_PATH}/list/${oldKey}`] = null;
        updates[`${ACTIVE_FEED_PATH}/byUser/${uid}`] = null;
        await admin.database().ref().update(updates);
      }
      return null;
    }

    const ts = Number(change.after.val()) || 0;
    if (ts <= 0) return null;

    const dbRoot = admin.database().ref();

    // 1) remove previous key (if any)
    const prevKeySnap = await dbRoot.child(`${ACTIVE_FEED_PATH}/byUser/${uid}/key`).get();
    const prevKey = prevKeySnap.val();

    // 2) write new row
    const rankKey = makeActiveRankKey(ts);
    const updates = {};
    updates[`${ACTIVE_FEED_PATH}/list/${rankKey}`] = { uid, lastActive: ts };
    updates[`${ACTIVE_FEED_PATH}/byUser/${uid}`]   = { key: rankKey, lastActive: ts };

    // 3) delete old row atomically (if it differs)
    if (prevKey && prevKey !== rankKey) {
      updates[`${ACTIVE_FEED_PATH}/list/${prevKey}`] = null;
    }

    await dbRoot.update(updates);
    return null;
  });

// ───────────────────────── Trim the global ACTIVE feed to a bounded size ───────────────────────
exports.trimActiveFeed = functions
   .region('asia-south1')
   .pubsub.schedule('every 60 minutes')
   .timeZone('Asia/Kolkata')
   .onRun(async () => {
    const KEEP = 20000; // keep newest 20k rows; tune as you grow
    const listQuery = admin.database().ref(`${ACTIVE_FEED_PATH}/list`).orderByKey();
    const snap = await listQuery.once('value');
    if (!snap.exists()) return null;

    // Keys are iterated in key order (ASC). Our inverted key makes ASC == NEWEST first.
    const keys = [];
    snap.forEach(child => { keys.push(child.key); });
    if (keys.length <= KEEP) return null;

    const toDelete = keys.slice(KEEP); // drop everything after KEEP
    const updates = {};
    toDelete.forEach(k => { updates[`${ACTIVE_FEED_PATH}/list/${k}`] = null; });

    // Best-effort cleanup: remove byUser entries pointing to missing list keys.
    const byUserSnap = await admin.database().ref(`${ACTIVE_FEED_PATH}/byUser`).once('value');
    if (byUserSnap.exists()) {
      byUserSnap.forEach(child => {
        const v = child.val() || {};
        const key = v.key;
        if (key && !snap.child(key).exists()) {
          updates[`${ACTIVE_FEED_PATH}/byUser/${child.key}`] = null;
        }
      });
    }

    if (Object.keys(updates).length) {
      await admin.database().ref().update(updates);
    }
    return null;
  });

exports.rebuildActiveFeed = functions
  .region('asia-south1')
  .runWith({ timeoutSeconds: 540, memory: '1GB' })
  .https.onRequest(async (_req, res) => {
    try {
      const cutoff = Date.now() - 30*24*60*60*1000; // only last 30 days
      const usersSnap = await admin.database().ref('users')
        .orderByChild('lastActive').startAt(cutoff).once('value');

      const updates = {};
      const byUser  = {};
      usersSnap.forEach(u => {
        const uid = u.key;
        const ts  = Number(u.child('lastActive').val()) || 0;
        if (!uid || ts <= 0) return;
        const key = makeActiveRankKey(ts);
        updates[`${ACTIVE_FEED_PATH}/list/${key}`] = { uid, lastActive: ts };
        byUser[uid] = { key, lastActive: ts };
      });
      updates[`${ACTIVE_FEED_PATH}/byUser`] = byUser;

      await admin.database().ref().update(updates);
      res.status(200).send(`Rebuilt ACTIVE with ${Object.keys(byUser).length} rows`);
    } catch (e) {
      console.error('rebuildActiveFeed', e);
      res.status(500).send(e.message || 'error');
    }
  });

const chunk = (arr, size) => {
  const out = []; for (let i=0;i<arr.length;i+=size) out.push(arr.slice(i,i+size)); return out;
};

/**
 * GET / POST (https) function
 * Query params:
 *   - enforce=true  -> will cap availableAiMessages > 25 to 25 (writes back to DB)
 *
 * Response JSON:
 *  {
 *    histogram: { "0-5": 123, "6-10": 45, ... , ">25": 3 },
 *    totalUsersScanned: N,
 *    usersAbove25: [ { uid, value }, ... ],
 *    changesApplied: { updated: M }   // only when enforce=true
 *  }
 */
exports.reportAiMessagesDistribution = functions
  .region("asia-south1")
  .runWith({ timeoutSeconds: 540, memory: "512MB" })
  .https.onRequest(async (req, res) => {
    try {
      const enforce = String(req.query.enforce || req.body?.enforce || "").toLowerCase() === "true";

      const usersSnap = await admin.database().ref("users").get();
      if (!usersSnap.exists()) {
        return res.status(200).json({ histogram: {}, totalUsersScanned: 0, usersAbove25: [] });
      }

      // histogram buckets
      const buckets = {
        "0-5": 0,
        "6-10": 0,
        "11-15": 0,
        "16-20": 0,
        "21-25": 0,
        ">25": 0
      };

      const usersAbove25 = [];
      const updates = {}; // multi-path updates if enforce=true
      let total = 0;

      usersSnap.forEach(child => {
        total++;
        const uid = child.key;
        const data = child.val() || {};
        const raw = data.availableAiMessages;
        // coerce to integer, treat missing/non-number as 0
        const val = (Number.isFinite(Number(raw)) ? Math.floor(Number(raw)) : 0);

        if (val <= 5) buckets["0-5"]++;
        else if (val <= 10) buckets["6-10"]++;
        else if (val <= 15) buckets["11-15"]++;
        else if (val <= 20) buckets["16-20"]++;
        else if (val <= 25) buckets["21-25"]++;
        else {
          buckets[">25"]++;
          usersAbove25.push({ uid, value: val });

          if (enforce) {
            // cap down to 25
            updates[`users/${uid}/availableAiMessages`] = 25;
          }
        }
      });

      let applied = { updated: 0 };
      if (enforce && Object.keys(updates).length) {
        // perform batched updates (avoid huge single update if you have many users)
        const entries = Object.entries(updates);
        const CHUNK = 400; // safe size
        const groups = chunk(entries, CHUNK);

        for (const g of groups) {
          const batch = {};
          for (const [path, val] of g) batch[path] = val;
          await admin.database().ref().update(batch);
          applied.updated += Object.keys(batch).length;
        }
      }

      return res.status(200).json({
        histogram: buckets,
        totalUsersScanned: total,
        usersAbove25,
        changesApplied: enforce ? applied : undefined,
        note: enforce ? "Values >25 were capped to 25." : "Run with ?enforce=true to cap values >25 to 25."
      });
    } catch (err) {
      console.error("reportAiMessagesDistribution error:", err);
      return res.status(500).json({ error: String(err) });
    }
  });


exports.refreshNearbyIndexes = functions
  .region('asia-south1')
  .pubsub.schedule('every 5 minutes')
  .timeZone('Asia/Kolkata')
  .onRun(async () => {
    const db = admin.database();
    const NOW = Date.now();
    const ACTIVE_WINDOW_MS = 30 * 60 * 1000;
    const MAX_PER_RUN = 400;
    const CHUNK = 100;

    const listSnap = await db.ref('feedIndex/ACTIVE/list')
      .orderByKey()
      .limitToFirst(2000)
      .get();

    const work = [];
    listSnap.forEach(child => {
      const row = child.val() || {};
      if (NOW - (row.lastActive || 0) <= ACTIVE_WINDOW_MS) work.push(row.uid);
    });

    const uids = work.slice(0, MAX_PER_RUN);

    for (let i = 0; i < uids.length; i += CHUNK) {
      await Promise.all(uids.slice(i, i + CHUNK).map(buildNearbyIndexFor));
    }
    return null;
  });

exports.onAuthUserDeleteCleanupNotifications = functions
  .region('asia-south1')
  .auth.user()
  .onDelete(async (user) => {
    try {
      const deletedUid = user.uid;
      const root = admin.database().ref('notifications');
      const snap = await root.get();
      if (!snap.exists()) return null;

      const updates = {};
      snap.forEach(recipientSnap => {
        const recipientId = recipientSnap.key;
        recipientSnap.forEach(nSnap => {
          const nid = nSnap.key;
          const nVal = nSnap.val() || {};
          if (nVal.senderId === deletedUid) {
            updates[`/notifications/${recipientId}/${nid}`] = null;
          }
        });
      });

      if (Object.keys(updates).length === 0) return null;

      // apply deletions in one atomic update
      await admin.database().ref().update(updates);
      console.log(`[onAuthUserDeleteCleanupNotifications] removed ${Object.keys(updates).length} notifications from deleted user ${deletedUid}`);
      return null;
    } catch (err) {
      console.error('onAuthUserDeleteCleanupNotifications error', err);
      return null;
    }
  });

exports.forceDeleteAccount = functions.region('asia-southeast1').https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Sign in required');
  const uid = context.auth.uid;
  await admin.auth().deleteUser(uid);
  await admin.auth().revokeRefreshTokens(uid);  // optional
  return { ok: true };
});

async function buildNearbyIndexFor(uid) {
  const db = admin.database();
  const [locSnap, uSnap] = await Promise.all([
    db.ref(`geoFireLocations/${uid}/l`).get(),
    db.ref(`users/${uid}`).get()
  ]);

  const center = locSnap.val();
  const u = uSnap.val() || {};
  if (!Array.isArray(center) || center.length !== 2) return;
  if (u.isPrivate === true) return;
  if (u.allowLocationForMatches === false) return;

  const maxKm = Math.min(Number(u.datingDistancePreference) || 30);
  const limit = 200;

  const { geohashQueryBounds, distanceBetween } = require('geofire-common');
  const bounds = geohashQueryBounds(center, maxKm);

  const snaps = await Promise.all(bounds.map(([s, e]) =>
    db.ref('geoFireLocations').orderByChild('g').startAt(s).endAt(e).get()
  ));

  const seen = new Set(), pairs = [];
  snaps.forEach(s => s.forEach(c => {
    const other = c.key;
    if (other === uid || seen.has(other)) return;
    seen.add(other);
    const loc = c.child('l').val();
    if (Array.isArray(loc) && loc.length === 2) {
      const dKm = distanceBetween([loc[0], loc[1]], center);
      if (dKm <= maxKm) pairs.push({ id: other, distM: Math.round(dKm * 1000) });
    }
  }));

  pairs.sort((a,b) => a.distM - b.distM);
  const top = pairs.slice(0, limit);

  const base = db.ref(`userFeedIndex/${uid}/NEARBY`);
  const updates = {};
  top.forEach((p,i) => {
    updates[String(i).padStart(5,'0')] = { uid: p.id, distanceM: p.distM };
  });
  await base.set(updates);   // overwrite atomically
}

exports.activateEntryFeePlusUsers = functions
  .region('asia-south1')
  .https.onRequest(async (_req, res) => {
    try {
      const nowTs = Date.now();
      const oneMonthMs = 30 * 24 * 60 * 60 * 1000;
      const targetRenewalTs = nowTs + oneMonthMs;

      const usersRef = admin.database().ref('users');
      const snap = await usersRef.once('value');

      if (!snap.exists()) {
        return res.status(200).send('No users found.');
      }

      const updates = {};
      const touchedUsers = new Set();
      let totalUsers = 0;
      let freeUsers = 0;
      let alreadyPlus = 0;
      let flippedToPlus = 0;
      let renewalSynced = 0;
      let statusActivated = 0;

      snap.forEach((userSnap) => {
        const key = userSnap.key;
        if (!key) return;

        totalUsers += 1;
        const data = userSnap.val() || {};

        if (data.isPlus === true) {
          alreadyPlus += 1;
          return;
        }

        freeUsers += 1;

        updates[`${key}/isPlus`] = true;
        updates[`${key}/nextRenewal`] = targetRenewalTs;
        touchedUsers.add(key);
        flippedToPlus += 1;
        renewalSynced += 1;

        const subscriptionStatus =
          typeof data.subscriptionStatus === 'string'
            ? data.subscriptionStatus.toLowerCase()
            : '';
        if (subscriptionStatus !== 'active') {
          updates[`${key}/subscriptionStatus`] = 'active';
          statusActivated += 1;
        }
      });

      const updateCount = Object.keys(updates).length;

      const summaryDetails = [
        `total users scanned: ${totalUsers}`,
        `already plus: ${alreadyPlus}`,
        `free users detected: ${freeUsers}`,
        `free users flipped: ${flippedToPlus}`,
        `renewals synced: ${renewalSynced}`,
        `renewal set to ${new Date(targetRenewalTs).toISOString()}`,
        `status activated: ${statusActivated}`,
      ].join(', ');

      if (updateCount === 0) {
        return res.status(200).send(`No updates required. ${summaryDetails}.`);
      }

      await usersRef.update(updates);

      res
        .status(200)
        .send(
          `Updated ${touchedUsers.size} user(s) across ${updateCount} field(s). ${summaryDetails}.`
        );
    } catch (err) {
      console.error('activateEntryFeePlusUsers error:', err);
      res.status(500).send(err.message);
    }
  });


exports.capPhoneRegistrations = functions
  .region('asia-south1')
  .auth.user()
  .onCreate(async (user) => {
    const isPhoneSignup = Boolean(user.phoneNumber) ||
      (Array.isArray(user.providerData) &&
        user.providerData.some((info) => info && info.providerId === 'phone'));

    if (!isPhoneSignup) return null;

    try {
      const dayKey = utcDateKey();
      const counterRef = db.ref(PHONE_REG_COUNTER_ROOT).child(dayKey);
      const result = await counterRef.transaction((current) => (current || 0) + 1);

      if (!result.committed) return null;

      const total = result.snapshot.val() || 0;
      if (total >= PHONE_REGISTRATION_LIMIT) {
        await db.ref(PHONE_AUTH_CONFIG_PATH).update({
          enabled: false,
          disabledAt: admin.database.ServerValue.TIMESTAMP,
          dayKey,
        });
      }
    } catch (err) {
      logger.error('Failed to enforce phone registration cap', err);
    }

    return null;
  });

exports.resetDailyPhoneRegistrationCap = functions
  .region('asia-south1')
  .pubsub.schedule('every day 00:05')
  .timeZone('UTC')
  .onRun(async () => {
    const dayKey = utcDateKey();

    await Promise.all([
      db.ref(PHONE_REG_COUNTER_ROOT).child(dayKey).set(0),
      db.ref(PHONE_AUTH_CONFIG_PATH).update({
        enabled: true,
        updatedAt: admin.database.ServerValue.TIMESTAMP,
      }),
    ]);

    return null;
  });

exports.backupPrimaryDatabaseDaily = functions
  .region('asia-south1')
  .runWith({ timeoutSeconds: 540, memory: '2GB' })
  .pubsub.schedule('every 24 hours')
  .timeZone('Asia/Kolkata')
  .onRun(async () => {
    const primary = databaseTargets[0];
    const backupTarget = databaseTargets.find((target) => target.name === 'kupidx');

    if (!primary || !primary.db) {
      functions.logger.warn('Database backup skipped: primary database not configured');
      return null;
    }

    if (!backupTarget || !backupTarget.db) {
      functions.logger.warn('Database backup skipped: backup database not configured');
      return null;
    }

    try {
      await runPrimaryHealthCheck(true);
      const snapshot = await primary.db.ref().get();
      const data = snapshot.exists() ? snapshot.val() : null;
      await backupTarget.db.ref().set(data);
      functions.logger.info('Primary database backup completed', {
        source: primary.name,
        destination: backupTarget.name,
      });
    } catch (err) {
      functions.logger.error('Primary database backup failed', {
        error: err?.message || err,
        source: primary?.name,
        destination: backupTarget?.name,
      });
      throw err;
    }

    return null;
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

exports.backfillProfilepicThumbnailUrl = functions
  .region('asia-south1')
  .https.onRequest(async (_req, res) => {
    try {
      const snap = await getUsersRef().once('value');
      const updates = {};

      snap.forEach(userSnap => {
        const data = userSnap.val() || {};
        if (data.profilepicUrl && data.profilepicThumbnailUrl === undefined) {
          updates[`${userSnap.key}/profilepicThumbnailUrl`] = data.profilepicUrl;
        }
      });

      if (Object.keys(updates).length === 0) {
        return res.status(200).send('No users needed back-fill.');
      }

      await getUsersRef().update(updates);
      res
        .status(200)
        .send(`Updated ${Object.keys(updates).length} user(s).`);
    } catch (err) {
      console.error('backfillProfilepicThumbnailUrl error:', err);
      res.status(500).send(err.message);
    }
  });

exports.normalizeGender = functions
  .region('asia-south1')
  .https.onRequest(async (_req, res) => {
    try {
      const usersRef = admin.database().ref('users');
      const snap     = await usersRef.once('value');

      const updates  = {};
      const touched  = new Set();

      snap.forEach(userSnap => {
        const uid  = userSnap.key;
        const data = userSnap.val() || {};
        let gender = data.gender;

        if (typeof gender !== 'string') {
          if (gender == null) {
            gender = ''; // or "unknown" if you want to track missing
          } else if (Array.isArray(gender)) {
            gender = gender[0] || '';
          } else if (typeof gender === 'object') {
            gender = gender.value || '';
          } else {
            gender = String(gender);
          }

          updates[`${uid}/gender`] = gender;
          touched.add(uid);
        }
      });

      if (Object.keys(updates).length === 0) {
        return res.status(200).send('No users needed normalization.');
      }

      await usersRef.update(updates);
      res
        .status(200)
        .send(`Normalized gender for ${touched.size} user(s).`);
    } catch (err) {
      console.error('normalizeGender error:', err);
      res.status(500).send(err.message);
    }
  });

   exports.seedTestMujer = functions
     .region("asia-south1")
     .https.onRequest(async (_req, res) => {
       const testUid = "test_mujer_001";

       const testUser = {
         username: "test_mujer",
         name: "María Test",
         city: "",
         hometown: "CDMX",
         lastActive: Date.now(),
         dateOfJoin: Date.now(),
         gender: "Mujer",
         gclid: "TEST-1234-5678-ABCD"
       };

       try {
         // Write to kupidxdefault women node
         await admin.database().ref("women").child(testUid).set(testUser);

         // Mirror to US RTDB women node
         await dbUS.ref("women").child(testUid).set(testUser);

         res.status(200).send("Test Mujer profile created in both DBs");
       } catch (e) {
         console.error("seedTestMujer error:", e);
         res.status(500).send(e.message);
       }
     });

  exports.mirrorWomenToUS = functions
    .region("asia-south1")
    .database.instance("kupidxdefault")     // source DB
    .ref("/women/{uid}")                    // 👈 only watch the women node
    .onWrite(async (change, context) => {
      const uid = context.params.uid;

      if (!change.after.exists()) {
        // Deleted → remove from US mirror
        await dbUS.ref(`women/${uid}`).remove();
        console.log(`[mirrorWomenToUS] Removed ${uid} from US women node`);
        return null;
      }

      // Mirror the payload as-is
      const data = change.after.val() || {};
      await dbUS.ref(`women/${uid}`).set(data);

      console.log(`[mirrorWomenToUS] Mirrored ${uid} to US women node`);
      return null;
    });

exports.backfillWomenToUS = functions
  .region("asia-south1")
  .runWith({ timeoutSeconds: 540, memory: "1GB" }) // allow large scans
  .https.onRequest(async (_req, res) => {
    try {
      // Source: kupidxdefault
      const snap = await admin.database().ref("users").once("value");
      const updates = {};

      snap.forEach(userSnap => {
        const uid = userSnap.key;
        const data = userSnap.val() || {};
        const genderRaw = (data.gender || "").toString().trim();
        const gender = genderRaw.toLowerCase();

        if (["female", "f", "mujer"].includes(gender)) {
          updates[`women/${uid}`] = {
            username: data.username || "",
            name: data.name || "",
            city: data.city || "",
            hometown: data.hometown || "",
            lastActive: data.lastActive || null,
            dateOfJoin: data.dateOfJoin || null,
            gender: genderRaw,
            profilePicUrl: data.profilePicUrl || "",
            thumbnailUrl: data.thumbnailUrl || ""
          };
        }
      });

      if (Object.keys(updates).length === 0) {
        return res.status(200).send("No women found in kupidxdefault.");
      }

      // Write into US DB
      await dbUS.ref().update(updates);

      res
        .status(200)
        .send(`Backfilled ${Object.keys(updates).length} women into US RTDB.`);
    } catch (err) {
      console.error("backfillWomenToUS error:", err);
      res.status(500).send(err.message);
    }
  });

exports.syncWomenRecord = functions
  .region("asia-south1")
  .runWith({ timeoutSeconds: 300, memory: "512MB" })
  .https.onRequest(async (_req, res) => {
    try {
      const snap = await admin.database().ref("users").once("value");
      const updates = {};

      snap.forEach(userSnap => {
        const uid = userSnap.key;
        const data = userSnap.val() || {};
        const genderRaw = (data.gender || "").toString().trim();
        const gender = genderRaw.toLowerCase();

        // Match English + Spanish
        if (["female", "f", "mujer"].includes(gender)) {
          updates[`women/${uid}`] = {
            username: data.username || "",
            name: data.name || "",
            city: data.city || "",
            hometown: data.hometown || "",
            lastActive: data.lastActive || null,
            dateOfJoin: data.dateOfJoin || null,
            gender: genderRaw  // keep original case/value
          };
        }
      });

      if (Object.keys(updates).length === 0) {
        return res.status(200).send("No female/mujer users found.");
      }

      await admin.database().ref().update(updates);
      res.status(200).send(`Synced ${Object.keys(updates).length} women records.`);
    } catch (err) {
      console.error("syncWomenRecord error:", err);
      res.status(500).send(err.message);
    }
  });


exports.backfillOrientationAndKinks = functions
  .region('asia-south1')
  .https.onRequest(async (_req, res) => {
    try {
      const snap = await getUsersRef().once('value');
      const updates = {};

      snap.forEach(userSnap => {
        const data = userSnap.val() || {};
        if (data.kinks === undefined) {
          updates[`${userSnap.key}/kinks`] = [];
        }
      });

      if (Object.keys(updates).length === 0) {
        return res.status(200).send('No users needed back-fill.');
      }

      await getUsersRef().update(updates);
      res
        .status(200)
        .send(`Updated ${Object.keys(updates).length} user(s).`);
    } catch (err) {
      console.error('backfillOrientationAndKinks error:', err);
      res.status(500).send(err.message);
    }
  });

exports.backfillFreeTrialFields = functions
  .region('asia-south1')
  .runWith({ timeoutSeconds: 540, memory: '1GB' })
  .https.onRequest(async (_req, res) => {
    try {
      const snap = await getUsersRef().once('value');
      const updates = {};
      let affected = 0;

      snap.forEach((userSnap) => {
        const uid = userSnap.key;
        const data = userSnap.val() || {};
        let touched = false;

        if (typeof data.hasUsedFreeTrial !== 'boolean') {
          updates[`${uid}/hasUsedFreeTrial`] = false;
          touched = true;
        }
        if (typeof data.freeTrialCompleted !== 'boolean') {
          updates[`${uid}/freeTrialCompleted`] = false;
          touched = true;
        }
        if (typeof data.freeTrialStartedAt !== 'number') {
          updates[`${uid}/freeTrialStartedAt`] = 0;
          touched = true;
        }
        if (typeof data.freeTrialExpiry !== 'number') {
          updates[`${uid}/freeTrialExpiry`] = 0;
          touched = true;
        }

        if (touched) affected += 1;
      });

      if (Object.keys(updates).length === 0) {
        return res.status(200).send('All users already have free trial fields.');
      }

      await getUsersRef().update(updates);
      return res
        .status(200)
        .send(`Backfilled free trial fields for ${affected} user(s).`);
    } catch (err) {
      console.error('backfillFreeTrialFields error:', err);
      return res.status(500).send(err.message || 'Internal error');
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
const LANG = {
  hi: "Hindi",
  bn: "Bengali",
  en: "English",
  ta: "Tamil",
  kn: "Kannada",
  te: "Telugu",
  es: "Mexican Spanish",            // covers 'es' (and 'es-MX' after slicing)
  th: "Thai"
};

exports.chatSuggestions = functions
  .region("asia-south1")
  .runWith({ timeoutSeconds: 120, memory: "512MB" })
  .https.onRequest(async (req, res) => {
    // CORS
    if (req.method === "OPTIONS") {
      return res
        .set({
          "Access-Control-Allow-Origin":  "*",
          "Access-Control-Allow-Methods": "POST",
          "Access-Control-Allow-Headers": "Content-Type",
        })
        .status(204).send("");
    }
    if (req.method !== "POST") {
      return res.set("Access-Control-Allow-Origin", "*").status(405).send("POST only");
    }

    try {
      const { messages = [], lang } = req.body || {};
      const LANG = { hi:"Hindi", bn:"Bengali", en:"English", ta:"Tamil", kn:"Kannada", te:"Telugu" };

      const code = String(lang ?? "en").trim().slice(0, 2).toLowerCase();
      const language = LANG[code] || "English";

      // Guardrails
      const trimmed = messages.slice(-10);
      const imageParts = trimmed
        .filter(m => m && m.imageUrl)
        .slice(0, 3)
        .map(m => ({ type: "image_url", image_url: { url: m.imageUrl } }));

      const recentText = trimmed
        .map(m => `${m.role}: ${m.text ?? "[image]"}`)
        .join("\n");

      const gptMsgs = [
        {
          role: "system",
          content:
            `You are a “Chat-Suggestion Engine” for a dating app.\n` +
            `ALWAYS reply exclusively in ${language}.\n\n` +
            `Return JSON only in this schema: ` +
            `{"topics":[],"activities":[{"placeName":"","integration":""}],"integrationTips":[]}`,
        },
        {
          role: "user",
          content: [
            { type: "text", text: `Recent messages:\n${recentText}` },
            ...imageParts,
          ],
        },
      ];

      const completion = await openai.chat.completions.create({
        model: "gpt-5-mini",
        messages: gptMsgs,
        temperature: 0.7,
        max_tokens: 400,
        response_format: { type: "json_object" },
      });

      const json = completion.choices[0].message.content.trim();
      return res.set("Access-Control-Allow-Origin", "*").send(json);
    } catch (err) {
      return res
        .set("Access-Control-Allow-Origin", "*")
        .status(500)
        .send(err.message || "internal error");
    }
  });

exports.fetchChatBootstrap = functions
  .region("asia-south1")
  .https.onCall(async (data, context) => {
    const uid = context.auth?.uid;
    if (!uid) {
      throw new functions.https.HttpsError('unauthenticated', 'Authentication required');
    }

    const otherUidRaw = typeof data?.otherUid === 'string' ? data.otherUid.trim() : '';
    if (!otherUidRaw) {
      throw new functions.https.HttpsError('invalid-argument', 'otherUid is required');
    }

    if (otherUidRaw === uid) {
      return { profile: null, messages: [] };
    }

    const rawLimit = Number(data?.limit ?? 50);
    const limit = Number.isFinite(rawLimit)
      ? Math.min(Math.max(Math.floor(rawLimit), 1), 200)
      : 50;

    const chatId = getChatId(uid, otherUidRaw);

    try {
      const [profileSnap, messagesSnap] = await Promise.all([
        getUsersRef().child(otherUidRaw).get(),
        db.ref(`messages/${chatId}`)
          .orderByChild('timestamp')
          .limitToLast(limit)
          .get(),
      ]);

      const profile = toJson(profileSnap);
      const messages = [];
      if (messagesSnap && typeof messagesSnap.forEach === 'function') {
        messagesSnap.forEach((child) => {
          if (child.key === 'participants') return;
          const msg = snapshotToMessage(child);
          if (msg) messages.push(msg);
        });
        messages.sort((a, b) => a.timestamp - b.timestamp);
      }

      return { profile, messages };
    } catch (err) {
      functions.logger.error('fetchChatBootstrap failed', err);
      throw new functions.https.HttpsError('internal', err?.message || 'Failed to load chat');
    }
  });

exports.fetchDmBootstrap = functions
  .region("asia-south1")
  .https.onCall(async (data, context) => {
    const uid = context.auth?.uid;
    if (!uid) {
      throw new functions.https.HttpsError('unauthenticated', 'Authentication required');
    }

    const clamp = (value, min, max, fallback) => {
      const num = Number(value);
      if (!Number.isFinite(num)) return fallback;
      return Math.min(Math.max(Math.floor(num), min), max);
    };

    const limitMatches = clamp(data?.limitMatches, 1, 50, 30);
    const limitCompliments = clamp(data?.limitCompliments, 1, 50, 15);

    try {
      const [matchesSnap, likesSnap, complimentsSnap] = await Promise.all([
        db.ref(`matches/${uid}`).get(),
        db.ref(`likesReceived/${uid}`).get(),
        db.ref(`complimentsReceived/${uid}`).get(),
      ]);

      const matchesVal = matchesSnap.val() || {};
      const likesVal = likesSnap.val() || {};

      const matchIds = Object.keys(matchesVal).slice(0, limitMatches);
      const likeIds = Object.keys(likesVal);
      const likedCount = likeIds.filter((id) => !matchIds.includes(id)).length;

      const usersRef = getUsersRef();

      const matchSummaries = await Promise.all(
        matchIds.map(async (matchId) => {
          try {
            const profileSnap = await usersRef.child(matchId).get();
            const profile = toJson(profileSnap);
            if (!profile || !profile.username) return null;

            const chatId = getChatId(uid, matchId);
            const messageSnap = await db
              .ref(`messages/${chatId}`)
              .orderByChild('timestamp')
              .limitToLast(1)
              .get();

            let lastMessage = null;
            let hasUnread = false;

            if (messageSnap && typeof messageSnap.forEach === 'function') {
              messageSnap.forEach((child) => {
                if (child.key === 'participants') return;
                const msg = snapshotToMessage(child);
                if (msg) {
                  lastMessage = msg;
                  if (msg.senderId !== uid && !msg.read) {
                    hasUnread = true;
                  }
                }
              });
            }

            return { profile, lastMessage, hasUnread };
          } catch (err) {
            functions.logger.warn('Failed to assemble match summary', { matchId, error: err?.message });
            return null;
          }
        })
      );

      const complimentEntries = [];
      if (complimentsSnap && typeof complimentsSnap.forEach === 'function') {
        complimentsSnap.forEach((child) => {
          const senderId = child.key;
          if (!senderId) return;
          const val = child.val() || {};
          complimentEntries.push({
            senderId,
            text: val.text || '',
            timestamp: Number(val.timestamp) || 0,
            voiceUrl: val.voiceUrl || null,
          });
        });
      }

      complimentEntries.sort((a, b) => b.timestamp - a.timestamp);

      const complimentPayload = await Promise.all(
        complimentEntries.slice(0, limitCompliments).map(async (entry) => {
          try {
            const snap = await usersRef.child(entry.senderId).get();
            const profile = toJson(snap);
            if (!profile || !profile.username) return null;
            return {
              profile,
              compliment: {
                text: entry.text,
                timestamp: entry.timestamp,
                voiceUrl: entry.voiceUrl,
              },
            };
          } catch (err) {
            functions.logger.warn('Failed to load compliment sender', { senderId: entry.senderId, error: err?.message });
            return null;
          }
        })
      );

      return {
        matches: matchSummaries.filter(Boolean),
        likedCount,
        compliments: complimentPayload.filter(Boolean),
      };
    } catch (err) {
      functions.logger.error('fetchDmBootstrap failed', err);
      throw new functions.https.HttpsError('internal', err?.message || 'Failed to load DM bootstrap');
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

function normalizeLastActive(raw) {
  const n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) return null;
  // Convert seconds-based stamps to milliseconds; Firebase RTDB stores ms by default (e.g., 1752518783622)
  const ms = n < 10_000_000_000 ? n * 1000 : n;
  return ms;
}

function aiPartnerLooksLikeRefusal(text = "") {
  const t = String(text).toLowerCase();
  return [
    "i can't help with that",
    "i cannot help with that",
    "i’m not able to help with that",
    "i am not able to help with that",
    "i can't provide that",
    "i cannot provide that",
    "i can't provide explicit",
    "i cannot provide explicit",
    "not appropriate for me to",
    "as an ai",
    "violates the guidelines",
    "against my guidelines",
    "cannot engage in explicit sexual",
    "cannot engage in sexual content",
  ].some((p) => t.includes(p));
}

const AI_PARTNER_FALLBACK_TEXT =
  "Mmm… you’re getting me worked up in the best way. 😅 Come a little closer and tell me what kind of trouble you want to get into with me…";

exports.generateAIPartnerMessage = functions
  .region("asia-south1")
  .https.onCall(async (data, context) => {
    const userInput = data.userInput || "";
    const systemPrompt = data.systemPrompt || "";

    try {
      const completion = await openai.chat.completions.create({
        model: "gpt-4o-mini",
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: userInput },
        ],
        max_tokens: 200,
        temperature: 0.9,
      });

      let aiText = completion?.choices?.[0]?.message?.content?.trim() || "";

      // If OpenAI refuses, replace it with an in-character erotic fallback
      if (aiPartnerLooksLikeRefusal(aiText)) {
        logger.warn("[generateAIPartnerMessage] refusal detected, using fallback");
        aiText = AI_PARTNER_FALLBACK_TEXT;
      }

      return { ok: true, text: aiText };
    } catch (e) {
      logger.error("OpenAI error:", e);
      return {
        ok: true,
        text: AI_PARTNER_FALLBACK_TEXT,
        error: e.message,
      };
    }
  });

exports.generateAIPartnerImage = functions
  .region("asia-south1")
  .https.onCall(async (data, context) => {

    const prompt = (data.prompt || "").toString().trim();

    try {
      const resp = await openai.images.generate({
        model: "gpt-image-1-mini",
        prompt,
        size: "auto",
        n: 1
      });

      console.log("IMAGE RESP RAW:", JSON.stringify(resp, null, 2));

      const b64 = resp?.data?.[0]?.b64_json;

      if (!b64) {
        return { ok: false, error: "no-b64" };
      }

      // 🔥 Return ONLY base64
      return { ok: true, b64 };

    } catch (err) {
      console.error("IMAGE ERROR:", err);
      return { ok: false, error: err?.message || "unknown-error" };
    }
  });

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

    const distLimit = Number(maxDistance);
    const useDist   =
      Array.isArray(myLoc) && myLoc.length === 2 &&
      Number.isFinite(distLimit);   // 65-km rule

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

      const snap = await db.ref('users').get();
      snap.forEach(s => {
        if (s.key !== uid) candidateIds.push(s.key);
      });
    }

    if (candidateIds.length === 0) return { profiles: [] };

    /* ── STEP 2: optional distance ordering ──────────────────────── */
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
          pairs.push({ id, dist });
        });
      }

      /* order by distance, then uid */
      pairs.sort((a, b) => a.dist - b.dist || a.id.localeCompare(b.id));
      candidateIds = pairs.map(p => p.id);
    } else {
      /* if caller has a location, still order by distance for nicer UX;
         otherwise just sort by uid for determinism */
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
            const id  = ids[idx];
            let dist  = Infinity;
            if (Array.isArray(loc) && loc.length === 2) {
              dist = distanceBetween(loc, myLoc);
            }
            pairs.push({ id, dist });
          });
        }

        pairs.sort((a, b) => a.dist - b.dist || a.id.localeCompare(b.id));
        candidateIds = pairs.map(p => p.id);
      } else {
        candidateIds.sort();
      }
    }

    /* ── STEP 3: fetch the profiles ──────────────────────────────── */
    const profileSnaps = await Promise.all(
      candidateIds.map(id => db.ref(`users/${id}`).get())
    );

    const profiles = profileSnaps
      .map(s => (s.val() ? { ...s.val(), userId: s.key } : null))
      .filter(p => {
        if (!p) return false;
        if (!myCountry) return true; // no country on caller → accept all
        return normalizeCountry(p.country) === normalizeCountry(myCountry);
      });

    return { profiles };
  });


exports.getMexicoUserCoords1 = functions
  .region("asia-south1")
  .runWith({ timeoutSeconds: 60, memory: "256MB" })
  .https.onRequest(async (req, res) => {
    try {
      // --- tune these if your schema differs ---
      const GEO_PATH = "geoFireLocations"; // where GeoFire keeps { g, l:[lat,lng] }
      const USER_ROOTS = ["users", "usersPublic", "profiles", "publicUsers", "userProfiles"];
      const COUNTRY_FIELDS = ["country", "country_lower", "profile.country", "location.country", "countryName"];
      const COUNTRY_WANTED = (req.query.country || "mexico").toString().toLowerCase();
      const LIMIT = Math.min(parseInt(req.query.limit) || 10000, 100000);
      // -----------------------------------------

      const geoSnap = await db.ref(GEO_PATH).get();
      if (!geoSnap.exists()) return res.status(200).json({ count: 0, users: [] });

      // Preload potential user trees once (fast join)
      const userTrees = {};
      await Promise.all(
        USER_ROOTS.map(async (root) => {
          try {
            const s = await db.ref(root).get();
            if (s.exists()) userTrees[root] = s.val();
          } catch {}
        })
      );

      const findCountry = (uid) => {
        for (const root of Object.keys(userTrees)) {
          const u = userTrees[root]?.[uid];
          if (!u) continue;
          for (const f of COUNTRY_FIELDS) {
            const v = getByPath(u, f);
            if (typeof v === "string") return v;
          }
        }
        return undefined;
      };

      const out = [];
      geoSnap.forEach((child) => {
        const uid = child.key;
        const v = child.val() || {};
        // GeoFire shapes: l: [lat, lng] (primary), sometimes location:{lat,lng}
        let lat, lng;
        if (Array.isArray(v?.l) && v.l.length === 2) {
          lat = Number(v.l[0]); lng = Number(v.l[1]);
        } else if (v?.location && typeof v.location.lat === "number" && typeof v.location.lng === "number") {
          lat = v.location.lat; lng = v.location.lng;
        }
        if (typeof lat !== "number" || typeof lng !== "number") return false;

        const country = findCountry(uid);
        if (!country) return false;
        if (country.toString().toLowerCase() !== COUNTRY_WANTED) return false;

        out.push({ uid, lat, lng });
        return out.length >= LIMIT; // stop early if limit reached
      });

      res.status(200).json({ count: out.length, users: out });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: String(e?.message || e) });
    }
  });

exports.getGlobalBoostedUsers = functions
  .region('asia-south1')
  .runWith({ timeoutSeconds: 60, memory: '256MB' })
  .https.onCall(async () => {
    const cutOff = now() - BOOST_DURATION_MS;

    const snap = await getUsersRef()
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
  .https.onCall(async ({ uid }) => {
    const cutoff = now() - 7 * 24 * 60 * 60 * 1000; // 7 days – exclude users inactive for ≥1 week

    const snap = await getUsersRef()
      .orderByChild('lastActive')
      .startAt(cutoff)
      .get();

    const profiles = [];
    snap.forEach(s => {
    if (s.key === uid) return; // exclude caller
      const u = s.val();
      const lastActive = normalizeLastActive(u?.lastActive);
      if (u?.priority && Number.isFinite(lastActive) && lastActive >= cutoff) {
              // include the UID so the app can map it back
        profiles.push({ userId: s.key, ...u, lastActive });
      }
    });

    profiles.sort((a, b) => {
      const tierA = a.priority ? 1 : 0;
      const tierB = b.priority ? 1 : 0;
      if (tierA !== tierB) return tierB - tierA; // priority first
      return a.userId.localeCompare(b.userId);
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
      ids.map(id => getUsersRef().child(id).get())
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

function resolveSubscriptionTier(user = {}) {
  const subscription = typeof user.subscription === "object" && user.subscription
    ? user.subscription
    : {};

  const planCandidates = [
    subscription.planId,
    subscription.plan_id,
    subscription.plan?.id,
    subscription.notes?.planId,
    user.subscriptionPlanId,
    user.planId,
  ];

  for (const candidate of planCandidates) {
    if (typeof candidate === "string" && candidate.trim()) {
      const planId = candidate.trim();
      return { planId, tier: PLAN_TIERS[planId] || null };
    }
  }

  return { planId: null, tier: null };
}

const FREE_SWIPE_QUOTA = 20;
const PREMIUM_ACTIVE_STATUSES = new Set([
  'active',
  'trialing',
  'in_trial',
  'authorised',
  'authorized',
  'authenticated',
  'pending',
  'auto_renewing',
  'on_hold',
]);
const PREMIUM_CANCELLED_STATUSES = new Set([
  'inactive',
  'cancelled',
  'canceled',
  'completed',
  'expired',
  'halted',
  'paused',
]);
const ENTRY_FEE_SUCCESS_STATUSES = new Set([
  'paid',
  'success',
  'successful',
  'completed',
]);
exports.checkExpiredOneTimeSubscriptions = functions.pubsub
  .schedule('every 24 hours')
  .timeZone('Asia/Kolkata')
  .onRun(async () => {
    const now = Date.now();
    const usersRef = admin.database().ref('users');
    const snapshot = await usersRef.once('value');
    const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;

    const updates = {};
    let deactivated = 0;
    let activated = 0;
    let syncedRenewal = 0;

    snapshot.forEach(userSnap => {
      const uid = userSnap.key;
      const u = userSnap.val() || {};

      const isPlus         = !!u.isPlus;
      const isPremium      = !!u.isPremium;
      const nextRenewal    = Number(u.nextRenewal || 0);
      const loginPlusExpiry= Number(u.loginPlusExpiry || 0);
      const entryFeePaidAt = Number(u.entryFeePaidAt || 0);
      const entryFeeOfferExpiry = Number(u.entryFeeOfferExpiry || 0);
      const premiumExpiryDate = Number(u.premiumExpiryDate || 0);
      const subscription      = typeof u.subscription === 'object' && u.subscription
        ? u.subscription
        : {};
      const subscriptionCurrentEnd = Number(
        subscription.current_end ??
        subscription.currentEnd ??
        subscription.currentPeriodEnd ??
        subscription.expiryTimeMillis ??
        subscription.expiry_time_millis ??
        0
      );
      const subscriptionNextBillingAt = Number(
        subscription.next_billing_at ??
        subscription.nextBillingAt ??
        subscription.renewalTimeMillis ??
        subscription.renewal_time_millis ??
        0
      );
      const { tier: subscriptionTier } = resolveSubscriptionTier(u);
      const subscriptionIsPremiumPlan = subscriptionTier?.premium === true;
      const subscriptionStatusRaw = typeof u.subscriptionStatus === 'string'
        ? u.subscriptionStatus.toLowerCase()
        : '';
      const subscriptionStatusFromObject = typeof subscription.status === 'string'
        ? subscription.status.toLowerCase()
        : '';
      const activePremiumStatus =
       subscriptionIsPremiumPlan && (
        PREMIUM_ACTIVE_STATUSES.has(subscriptionStatusRaw) ||
        PREMIUM_ACTIVE_STATUSES.has(subscriptionStatusFromObject)
       );
      const cancelledPremiumStatus =
        subscriptionIsPremiumPlan && (
          PREMIUM_CANCELLED_STATUSES.has(subscriptionStatusRaw) ||
          PREMIUM_CANCELLED_STATUSES.has(subscriptionStatusFromObject)
        );
      const subscriptionIdentifiers = [
        typeof u.subscriptionId === 'string' ? u.subscriptionId.trim() : '',
        typeof subscription.id === 'string' ? subscription.id.trim() : '',
        typeof subscription.subscriptionId === 'string' ? subscription.subscriptionId.trim() : '',
        typeof subscription.productId === 'string' ? subscription.productId.trim() : '',
        typeof subscription.planId === 'string' ? subscription.planId.trim() : '',
        typeof subscription.plan_id === 'string' ? subscription.plan_id.trim() : '',
      ].filter(Boolean);
      const hasPremiumIdentifier = subscriptionIsPremiumPlan && subscriptionIdentifiers.length > 0;
      const fromEntryFee   = entryFeePaidAt > 0 ? entryFeePaidAt + THIRTY_DAYS_MS : 0;
      const desiredRenewal = Math.max(
        Number.isFinite(nextRenewal) ? nextRenewal : 0,
        Number.isFinite(loginPlusExpiry) ? loginPlusExpiry : 0,
        Number.isFinite(fromEntryFee) ? fromEntryFee : 0,
        Number.isFinite(entryFeeOfferExpiry) ? entryFeeOfferExpiry : 0,
        Number.isFinite(premiumExpiryDate) ? premiumExpiryDate : 0,
        Number.isFinite(subscriptionCurrentEnd) ? subscriptionCurrentEnd : 0,
        Number.isFinite(subscriptionNextBillingAt) ? subscriptionNextBillingAt : 0
      );

      const premiumRenewalSources = [0];
      if (Number.isFinite(premiumExpiryDate)) {
        premiumRenewalSources.push(premiumExpiryDate);
      }
      if (subscriptionIsPremiumPlan) {
        if (Number.isFinite(subscriptionCurrentEnd)) {
           premiumRenewalSources.push(subscriptionCurrentEnd);
      }
        if (Number.isFinite(subscriptionNextBillingAt)) {
           premiumRenewalSources.push(subscriptionNextBillingAt);
      }
        if (Number.isFinite(nextRenewal)) {
           premiumRenewalSources.push(nextRenewal);
        }
      }
      const premiumRenewal = Math.max(...premiumRenewalSources);
      const premiumFlagged = isPremium && !cancelledPremiumStatus;
      const hasPremiumEntitlement =
      (Number.isFinite(premiumRenewal) && premiumRenewal > now) ||
      activePremiumStatus ||
       (premiumFlagged && hasPremiumIdentifier);
       const entryFeePaymentStatus = typeof u.entryFeePaymentStatus === 'string'
       ? u.entryFeePaymentStatus.toLowerCase()
       : '';
       const isEntryFeePaid =
       u.isEntryFeePaid === true || ENTRY_FEE_SUCCESS_STATUSES.has(entryFeePaymentStatus);
       const entryFeeAccessActive =
       (Number.isFinite(fromEntryFee) && fromEntryFee > now) ||
       (Number.isFinite(entryFeeOfferExpiry) && entryFeeOfferExpiry > now) ||
       (isEntryFeePaid && fromEntryFee === 0 && entryFeeOfferExpiry === 0);

       const hasActiveEntitlement =
       desiredRenewal > now ||
       hasPremiumEntitlement ||
       premiumFlagged ||
       entryFeeAccessActive;
      if (!hasActiveEntitlement) {
        // Entitlement finished → deactivate both tiers (keep old behavior)
          if (
                  isPlus ||
                  isPremium ||
                  nextRenewal ||
                  loginPlusExpiry ||
                  premiumExpiryDate ||
                  entryFeeOfferExpiry ||
                  entryFeePaidAt ||
                  hasPremiumIdentifier
                ) {
          updates[`${uid}/isPlus`] = false;
          updates[`${uid}/isPremium`] = false;
          updates[`${uid}/subscriptionStatus`] = 'inactive';
          updates[`${uid}/nextRenewal`] = null;
          updates[`${uid}/entryFeeOfferExpiry`] = null;
          updates[`${uid}/swipesInfo/remainingSwipes`] = FREE_SWIPE_QUOTA;
          deactivated += 1;
        }
        return; // done with this user
      }

      // Entitlement active → ensure Plus is on & renewal is synced
      // (Don’t force Premium true here—Premium stays driven by webhooks/billing)
      const userUpdates = {};
      if (!isPlus) {
        userUpdates.isPlus = true;
        activated += 1;
      }
       const shouldHavePremiumFlag = hasPremiumEntitlement || premiumFlagged;
            if (shouldHavePremiumFlag !== isPremium) {
              userUpdates.isPremium = shouldHavePremiumFlag;
            }
      if (desiredRenewal !== nextRenewal) {
        userUpdates.nextRenewal = desiredRenewal;
        syncedRenewal += 1;
      }
      const shouldSyncEntryFee = entryFeePaidAt > 0 || entryFeeOfferExpiry > 0;
            if (
              shouldSyncEntryFee &&
              desiredRenewal > 0 &&
              desiredRenewal !== entryFeeOfferExpiry
            ) {
              userUpdates.entryFeeOfferExpiry = desiredRenewal;
            }
      if (Object.keys(userUpdates).length) {
        userUpdates.lastEntitlementSyncAt = now;
        Object.entries(userUpdates).forEach(([k, v]) => {
          updates[`${uid}/${k}`] = v;
        });
      }
    });

    if (Object.keys(updates).length > 0) {
      await usersRef.update(updates);
    }
    console.log(
      `[checkExpiredOneTimeSubscriptions] deactivated=${deactivated}, ` +
      `activated=${activated}, renewalSynced=${syncedRenewal}`
    );
  });

exports.loginEntitlementSweep = functions
  .region('asia-south1')
  .https.onCall(async (_data, context) => {
    const uid = context.auth?.uid;
    if (!uid) {
      throw new functions.https.HttpsError('unauthenticated', 'Sign-in required');
    }

    const snap = await db.ref(`users/${uid}`).get();
    const u = snap.val() || {};

    const now = Date.now();
    const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;

    const isPlus = !!u.isPlus;
    const isPremium = !!u.isPremium;
    const entryFeePaidAt = Number(u.entryFeePaidAt || 0);
    const loginPlusExpiry = Number(u.loginPlusExpiry || 0);
    const nextRenewal = Number(u.nextRenewal || 0);
    const entryFeeOfferExpiry = Number(u.entryFeeOfferExpiry || 0);
    const premiumExpiryDate = Number(u.premiumExpiryDate || 0);
    const subscription = typeof u.subscription === 'object' && u.subscription ? u.subscription : {};
    const subscriptionCurrentEnd = Number(
      subscription.current_end ??
        subscription.currentEnd ??
        subscription.currentPeriodEnd ??
        subscription.expiryTimeMillis ??
        subscription.expiry_time_millis ??
        0,
    );
    const subscriptionNextBillingAt = Number(
      subscription.next_billing_at ??
        subscription.nextBillingAt ??
        subscription.renewalTimeMillis ??
        subscription.renewal_time_millis ??
        0,
    );
    const { tier: subscriptionTier } = resolveSubscriptionTier(u);
    const subscriptionIsPremiumPlan = subscriptionTier?.premium === true;
    const subscriptionStatusRaw = typeof u.subscriptionStatus === 'string' ? u.subscriptionStatus.toLowerCase() : '';
    const subscriptionStatusFromObject = typeof subscription.status === 'string' ? subscription.status.toLowerCase() : '';
    const activePremiumStatus =
      subscriptionIsPremiumPlan &&
        (PREMIUM_ACTIVE_STATUSES.has(subscriptionStatusRaw) || PREMIUM_ACTIVE_STATUSES.has(subscriptionStatusFromObject));    const cancelledPremiumStatus =
      subscriptionIsPremiumPlan &&
        (PREMIUM_CANCELLED_STATUSES.has(subscriptionStatusRaw) || PREMIUM_CANCELLED_STATUSES.has(subscriptionStatusFromObject));    const subscriptionIdentifiers = [
      typeof u.subscriptionId === 'string' ? u.subscriptionId.trim() : '',
      typeof subscription.id === 'string' ? subscription.id.trim() : '',
      typeof subscription.subscriptionId === 'string' ? subscription.subscriptionId.trim() : '',
      typeof subscription.productId === 'string' ? subscription.productId.trim() : '',
      typeof subscription.planId === 'string' ? subscription.planId.trim() : '',
      typeof subscription.plan_id === 'string' ? subscription.plan_id.trim() : '',
    ].filter(Boolean);
    const hasPremiumIdentifier = subscriptionIsPremiumPlan && subscriptionIdentifiers.length > 0;

    const entryFeePaymentStatus = typeof u.entryFeePaymentStatus === 'string' ? u.entryFeePaymentStatus.toLowerCase() : '';
    const isEntryFeePaid = u.isEntryFeePaid === true || ENTRY_FEE_SUCCESS_STATUSES.has(entryFeePaymentStatus);

    const fromEntryFee = entryFeePaidAt > 0 ? entryFeePaidAt + THIRTY_DAYS_MS : 0;
    const desiredRenewal = Math.max(
      Number.isFinite(nextRenewal) ? nextRenewal : 0,
      Number.isFinite(loginPlusExpiry) ? loginPlusExpiry : 0,
      Number.isFinite(fromEntryFee) ? fromEntryFee : 0,
      Number.isFinite(entryFeeOfferExpiry) ? entryFeeOfferExpiry : 0,
      Number.isFinite(premiumExpiryDate) ? premiumExpiryDate : 0,
      Number.isFinite(subscriptionCurrentEnd) ? subscriptionCurrentEnd : 0,
      Number.isFinite(subscriptionNextBillingAt) ? subscriptionNextBillingAt : 0,
    );
    const availableAiMessages = Number(u.availableAiMessages || 0);
    const hasEntryAiBonus     = !!u.hasEntryAiBonus;

     const premiumRenewalSources = [0];
      if (Number.isFinite(premiumExpiryDate)) {
      premiumRenewalSources.push(premiumExpiryDate);
     }
    if (subscriptionIsPremiumPlan) {
      if (Number.isFinite(subscriptionCurrentEnd)) {
        premiumRenewalSources.push(subscriptionCurrentEnd);
      }
      if (Number.isFinite(subscriptionNextBillingAt)) {
        premiumRenewalSources.push(subscriptionNextBillingAt);
      }
      if (Number.isFinite(nextRenewal)) {
        premiumRenewalSources.push(nextRenewal);
      }
    }
    const premiumRenewal = Math.max(...premiumRenewalSources);
    const premiumFlagged = isPremium && !cancelledPremiumStatus;
    const hasPremiumEntitlement =
      (Number.isFinite(premiumRenewal) && premiumRenewal > now) || activePremiumStatus || (premiumFlagged && hasPremiumIdentifier);
    const entryFeeAccessActive =
      (Number.isFinite(fromEntryFee) && fromEntryFee > now) ||
      (Number.isFinite(entryFeeOfferExpiry) && entryFeeOfferExpiry > now) ||
      (isEntryFeePaid && fromEntryFee === 0 && entryFeeOfferExpiry === 0);

    const hasActiveEntitlement = desiredRenewal > now || hasPremiumEntitlement || premiumFlagged || entryFeeAccessActive;

    const updates = {};

    if (hasActiveEntitlement && !isPlus) {
      updates.isPlus = true;
    }
    const shouldHavePremiumFlag = hasPremiumEntitlement || premiumFlagged;
    if (shouldHavePremiumFlag !== isPremium) {
      updates.isPremium = shouldHavePremiumFlag;
    }

    if (desiredRenewal > 0 && desiredRenewal !== nextRenewal) {
      updates.nextRenewal = desiredRenewal;
    }

    if (
      isEntryFeePaid &&
      desiredRenewal > now &&
      desiredRenewal > fromEntryFee &&
      desiredRenewal - THIRTY_DAYS_MS > entryFeePaidAt
    ) {
      updates.entryFeePaidAt = desiredRenewal - THIRTY_DAYS_MS;
    }

    if (desiredRenewal > 0 && desiredRenewal !== entryFeeOfferExpiry) {
      updates.entryFeeOfferExpiry = desiredRenewal;
    }

    if (Object.keys(updates).length) {
      updates.lastEntitlementSyncAt = now;
      await db.ref(`users/${uid}`).update(updates);
    }

    return { ok: true, applied: updates };
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

exports.searchUsernames = functions
  .region("asia-south1")
  .https.onCall(async (data, context) => {
    const rawQuery = typeof data?.query === "string" ? data.query : "";
    const normalized = rawQuery.trim();
    if (normalized.length < 2) {
      return { profiles: [] };
    }

    const lookupKeys = Array.from(new Set([normalized, normalized.toLowerCase()]))
      .filter((value) => value && value.length >= 2)
      .slice(0, 2);

    const usernameEntries = new Map();

    for (const key of lookupKeys) {
      try {
        const snapshot = await db
          .ref("usernames")
          .orderByKey()
          .startAt(key)
          .endAt(`${key}\uf8ff`)
          .limitToFirst(25)
          .get();

        snapshot.forEach((child) => {
          const username = child.key || "";
          const userId = child.val();
          if (!username || typeof userId !== "string") {
            return;
          }
          if (!usernameEntries.has(userId)) {
            usernameEntries.set(userId, { userId, username });
          }
        });
      } catch (err) {
        logger.error("Failed username lookup", { key, error: err?.message || err });
      }
    }

    const limitedEntries = Array.from(usernameEntries.values()).slice(0, 25);

    const profiles = await Promise.all(
      limitedEntries.map(async ({ userId, username }) => {
        try {
          const snap = await db.ref("users").child(userId).get();
          if (!snap.exists()) {
            return null;
          }
          const profile = snap.val() || {};
          return {
            ...profile,
            userId,
            username: profile.username || username,
          };
        } catch (err) {
          logger.error("Failed to load profile for username search", {
            userId,
            error: err?.message || err,
          });
          return null;
        }
      })
    );

    return {
      profiles: profiles.filter(Boolean),
    };
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
          updates.availableBoosts      = tier.premium ? 5 : 3;
          updates.availableCompliments = tier.premium ? 5 : 3;

          // ⭐ AI messages: Plus = 25 / month, Premium = 50 / month
          updates.availableAiMessages = tier.premium ? 50 : 25;
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

          // ⭐ AI messages: Plus = 25 / month, Premium = 50 / month
          updates.availableAiMessages = tier.premium ? 50 : 25;
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

exports.processLikesCleanupRequest = functions
  .region('asia-south1')
  .database.instance(DB)
  .ref('/likesReceivedCleanupRequests/{owner}/{target}')
  .onCreate(async (snap, context) => {
    const owner = context.params.owner;
    const target = context.params.target;
    const rootRef = admin.database().ref();

    try {
      // 1) Fast-path: does the auth user exist?
      try {
        await admin.auth().getUser(target);
      } catch (authErr) {
        // If auth.user not found -> safe to delete the like immediately
        if (authErr.code === 'auth/user-not-found') {
          const updates = {};
          updates[`likesReceived/${owner}/${target}`] = null;
          updates[`likesReceivedCleanupRequests/${owner}/${target}`] = null;
          updates[`likesDeletesLog/${owner}/${target}`] = {
            deletedBy: 'cloudfunc',
            reason: 'auth-user-not-found',
            timestamp: admin.database.ServerValue.TIMESTAMP
          };
          await rootRef.update(updates);
          return null;
        }
        // For other auth errors, log and continue to profile check below
        console.warn(`[processLikesCleanupRequest] auth.getUser error for ${target}`, authErr);
      }

      // 2) If auth user exists, verify profile presence (users/<target>)
      const userSnap = await admin.database().ref(`users/${target}`).get();
      if (!userSnap.exists()) {
        const updates = {};
        updates[`likesReceived/${owner}/${target}`] = null;
        updates[`likesReceivedCleanupRequests/${owner}/${target}`] = null;
        updates[`likesDeletesLog/${owner}/${target}`] = {
          deletedBy: 'cloudfunc',
          reason: 'profile-missing',
          timestamp: admin.database.ServerValue.TIMESTAMP
        };
        await rootRef.update(updates);
      } else {
        // Target looks valid — do nothing and leave the cleanup request in place
        // (a scheduled job or manual review can remove it later if needed)
        return null;
      }
    } catch (err) {
      console.error(`[processLikesCleanupRequest] failed for ${owner}/${target}`, err);
      // Don't delete anything on unexpected errors — leaving the request intact is safer.
    }
    return null;
  });


exports.processLikesCleanupRequest = functions
  .region('asia-south1')
  .database.instance(DB)
  .ref('/likesReceivedCleanupRequests/{owner}/{target}')
  .onCreate(async (snap, context) => {
    const owner = context.params.owner;
    const target = context.params.target;
    const rootRef = admin.database().ref();

    try {
      // 1) Fast-path: does the auth user exist?
      try {
        await admin.auth().getUser(target);
      } catch (authErr) {
        // If auth.user not found -> safe to delete the like immediately
        if (authErr.code === 'auth/user-not-found') {
          const updates = {};
          updates[`likesReceived/${owner}/${target}`] = null;
          updates[`likesReceivedCleanupRequests/${owner}/${target}`] = null;
          updates[`likesDeletesLog/${owner}/${target}`] = {
            deletedBy: 'cloudfunc',
            reason: 'auth-user-not-found',
            timestamp: admin.database.ServerValue.TIMESTAMP
          };
          await rootRef.update(updates);
          return null;
        }
        // For other auth errors, log and continue to profile check below
        console.warn(`[processLikesCleanupRequest] auth.getUser error for ${target}`, authErr);
      }

      // 2) If auth user exists, verify profile presence (users/<target>)
      const userSnap = await admin.database().ref(`users/${target}`).get();
      if (!userSnap.exists()) {
        const updates = {};
        updates[`likesReceived/${owner}/${target}`] = null;
        updates[`likesReceivedCleanupRequests/${owner}/${target}`] = null;
        updates[`likesDeletesLog/${owner}/${target}`] = {
          deletedBy: 'cloudfunc',
          reason: 'profile-missing',
          timestamp: admin.database.ServerValue.TIMESTAMP
        };
        await rootRef.update(updates);
      } else {
        // Target looks valid — do nothing and leave the cleanup request in place
        // (a scheduled job or manual review can remove it later if needed)
        return null;
      }
    } catch (err) {
      console.error(`[processLikesCleanupRequest] failed for ${owner}/${target}`, err);
      // Don't delete anything on unexpected errors — leaving the request intact is safer.
    }
    return null;
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

exports.listMexicanUsersByGender = functions
  .region('asia-south1')
  .https.onRequest(async (_req, res) => {
    try {
      const usersRef = admin.database().ref('users');

      // Handle both "Mexico" and "México" spellings
      const [mxSnap1, mxSnap2] = await Promise.all([
        usersRef.orderByChild('country').equalTo('Mexico').once('value'),
        usersRef.orderByChild('country').equalTo('México').once('value'),
      ]);

      const men = [];
      const women = [];
      const other = [];
      const seen = new Set();

      const pushUser = (child) => {
        const u = child.val() || {};
        if (normalizeCountry(u.country) !== 'mexico') return;
        const g = (u.gender || '').toString().trim().toLowerCase();

        const payload = {
          uid: child.key,
          username: u.username || '',
          name: u.name || '',
          city: u.city || '',
          hometown: u.hometown || '',
          dateOfJoin: u.dateOfJoin || null,
          lastActive: u.lastActive || null,
        };

        if (g === 'male' || g === 'm') {
          men.push(payload);
        } else if (g === 'female' || g === 'f') {
          women.push(payload);
        } else {
          other.push(payload);
        }
      };

      [mxSnap1, mxSnap2].forEach(snap => {
        snap.forEach(child => {
          if (seen.has(child.key)) return;
          seen.add(child.key);
          pushUser(child);
        });
      });

      // Most-recent active first
      const byLastActiveDesc = (a, b) => (b.lastActive || 0) - (a.lastActive || 0);
      men.sort(byLastActiveDesc);
      women.sort(byLastActiveDesc);
      other.sort(byLastActiveDesc);

      res
        .set('Access-Control-Allow-Origin', '*')
        .json({
          count: {
            men: men.length,
            women: women.length,
            other: other.length,
            total: men.length + women.length + other.length,
          },
          men,
          women,
          other,
        });
    } catch (err) {
      console.error('listMexicanUsersByGender error:', err);
      res.status(500).send(err.message);
    }
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

exports.verifyPlayPurchase = functions
  .region('asia-south1')
  .https.onCall(async (data, _ctx) => {
    try {
      const {purchaseToken, productId, packageName, productType} = data || {};
      if (!purchaseToken || !productId || !packageName || !productType) {
        throw new functions.https.HttpsError('invalid-argument', 'Missing parameters');
      }

      const auth = new google.auth.GoogleAuth({
        scopes: ['https://www.googleapis.com/auth/androidpublisher'],
      });
      const client = await auth.getClient();
      const api = google.androidpublisher({version: 'v3', auth: client});

      if (productType === 'inapp') {
        const res = await api.purchases.products.get({
          packageName,
          productId,
          token: purchaseToken,
        });
        return res.data;
      } else {
        const res = await api.purchases.subscriptions.get({
          packageName,
          subscriptionId: productId,
          token: purchaseToken,
        });
        return res.data;
      }
    } catch (err) {
      console.error('verifyPlayPurchase error:', err);
      throw new functions.https.HttpsError('internal', err.message);
    }
  });

/**
 * Handle Google Play Real-Time Developer Notifications (RTDN) for
 * subscription status changes. Requires a Pub/Sub topic `play-subs` to be
 * configured in the Play Console.
 */
exports.onPlaySubscriptionNotification = functions
  .region('asia-south1')
  .pubsub.topic('play-subs')
  .onPublish(async message => {
    try {
      const data = JSON.parse(Buffer.from(message.data, 'base64').toString());
      const sn = data.subscriptionNotification;
      if (!sn) return;

      const { purchaseToken, subscriptionId, notificationType } = sn;
      const packageName = data.packageName;

      const auth = new google.auth.GoogleAuth({
        scopes: ['https://www.googleapis.com/auth/androidpublisher'],
      });
      const client = await auth.getClient();
      const api = google.androidpublisher({ version: 'v3', auth: client });
      const res = await api.purchases.subscriptions.get({
        packageName,
        subscriptionId,
        token: purchaseToken,
      });

      const purchase = res.data;
      const uid = purchase.obfuscatedExternalAccountId;
      if (!uid) {
        console.warn('[onPlaySubscriptionNotification] Missing uid', purchase);
        return;
      }

      const ref = admin.database().ref(`users/${uid}`);
      const cancelTypes = [3, 12, 13]; // CANCELLED, REVOKED, EXPIRED
      if (cancelTypes.includes(notificationType)) {
        await ref.update({
          isPlus: false,
          isPremium: false,
          subscriptionStatus: 'inactive',
          nextRenewal: null,
        });
      } else {
        await ref.update({
          subscriptionStatus: 'active',
          nextRenewal: purchase.expiryTimeMillis
            ? Number(purchase.expiryTimeMillis)
            : null,
        });
      }
    } catch (err) {
      console.error('onPlaySubscriptionNotification error:', err);
    }
  });

/**
 * Callable used by the client to force a subscription status check. This can
 * be invoked on app startup to ensure server state matches Play Billing.
 */
exports.syncPlaySubscription = functions
  .region('asia-south1')
  .https.onCall(async (data, _ctx) => {
    try {
      const { purchaseToken, productId, packageName } = data || {};
      if (!purchaseToken || !productId || !packageName) {
        throw new functions.https.HttpsError('invalid-argument', 'Missing parameters');
      }

      const auth = new google.auth.GoogleAuth({
        scopes: ['https://www.googleapis.com/auth/androidpublisher'],
      });
      const client = await auth.getClient();
      const api = google.androidpublisher({ version: 'v3', auth: client });
      const res = await api.purchases.subscriptions.get({
        packageName,
        subscriptionId: productId,
        token: purchaseToken,
      });

      const purchase = res.data;
      const uid = purchase.obfuscatedExternalAccountId;
      if (!uid) {
        throw new functions.https.HttpsError(
          'internal',
          'Missing obfuscatedExternalAccountId'
        );
      }

      const ref = admin.database().ref(`users/${uid}`);
      const cancelReason = purchase.cancelReason;
      const expired =
        purchase.expiryTimeMillis &&
        Number(purchase.expiryTimeMillis) < Date.now();
      if (cancelReason != null || expired) {
        await ref.update({
          isPlus: false,
          isPremium: false,
          subscriptionStatus: 'inactive',
          nextRenewal: null,
        });
      } else {
        await ref.update({
          subscriptionStatus: 'active',
          nextRenewal: purchase.expiryTimeMillis
            ? Number(purchase.expiryTimeMillis)
            : null,
        });
      }

      return purchase;
    } catch (err) {
      console.error('syncPlaySubscription error:', err);
      throw new functions.https.HttpsError('internal', err.message);
    }
  });

// ✅ single, final definition
exports.notifyOmegleInvite = functions
  .region('asia-south1')                     // match your other functions
  .database.instance('kupidxdefault')        // the subdomain BEFORE .asia-southeast1...
  .ref('/omegleInvites/{partnerId}/{chatId}')
  .onCreate(async (snapshot, context) => {
    const partnerId = context.params.partnerId;
    const chatId    = context.params.chatId;
    const otherUid  = snapshot.val();

    const tokenSnap = await admin.database()
      .ref(`users/${partnerId}/fcmTokens`)
      .once('value');
    const tokens = tokenSnap.exists() ? Object.keys(tokenSnap.val()) : [];
    if (!tokens.length) return null;

    const payload = {
      data: { type: 'omegle_invite', chatId, otherUid },
      tokens,
    };

    await admin.messaging().sendEachForMulticast(payload);
    return null;
  });

exports.grantAiMessagesLast90Days = functions
  .region("asia-south1")
  .runWith({ timeoutSeconds: 540, memory: "1GB" })
  .https.onRequest(async (req, res) => {
    try {
      const db = admin.database();

      // ─────────────────────────────────────────────
      // SAFETY: run-once guard
      // ─────────────────────────────────────────────
      const lockRef = db.ref("adminJobs/grantAiMessagesLast90Days");
      const lockSnap = await lockRef.get();

      if (lockSnap.exists()) {
        return res.status(200).send("Already executed. No action taken.");
      }

      // ─────────────────────────────────────────────
      // Time window: last 90 days
      // ─────────────────────────────────────────────
      const NOW = Date.now();
      const DAYS_90_MS = 90 * 24 * 60 * 60 * 1000;
      const cutoff = NOW - DAYS_90_MS;

      const usersSnap = await db.ref("users").get();
      if (!usersSnap.exists()) {
        return res.status(200).send("No users found.");
      }

      const updates = {};
      let eligible = 0;

      usersSnap.forEach(userSnap => {
        const uid = userSnap.key;
        const lastActive = Number(userSnap.child("lastActive").val() || 0);

        if (lastActive >= cutoff) {
          const current = Number(
            userSnap.child("availableAiMessages").val() || 0
          );
          updates[`users/${uid}/availableAiMessages`] = current + 5;
          eligible++;
        }
      });

      // Mark job as completed (idempotency)
      updates["adminJobs/grantAiMessagesLast90Days"] = {
        executedAt: NOW,
        cutoff,
        addedPerUser: 5,
        affectedUsers: eligible
      };

      if (eligible === 0) {
        await lockRef.set({
          executedAt: NOW,
          affectedUsers: 0
        });
        return res.status(200).send("No eligible users in last 90 days.");
      }

      await db.ref().update(updates);

      return res.status(200).send(
        `SUCCESS: +5 AI messages granted to ${eligible} users (active ≤90 days)`
      );

    } catch (err) {
      console.error("grantAiMessagesLast90Days failed", err);
      return res.status(500).send("Internal error");
    }
  });

