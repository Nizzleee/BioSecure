/*
 * Helpers shared by the local emulator scripts.
 *
 * These scripts are intentionally restricted to the Firebase Emulator Suite:
 * FIRESTORE_EMULATOR_HOST and FIREBASE_AUTH_EMULATOR_HOST are set before
 * firebase-admin is loaded, so a missing emulator cannot silently hit
 * production.
 */

process.env.FIRESTORE_EMULATOR_HOST =
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST =
  process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099";

const admin = require("firebase-admin");

const projectId =
  process.env.GCLOUD_PROJECT ||
  process.env.FIREBASE_PROJECT_ID ||
  "demo-biosecure";

const region = process.env.FUNCTIONS_REGION || "us-central1";
const functionsBaseUrl =
  process.env.FUNCTIONS_EMULATOR_URL ||
  `http://${process.env.FUNCTIONS_EMULATOR_HOST || "127.0.0.1"}:${
    process.env.FUNCTIONS_EMULATOR_PORT || "5001"
  }`;
const authBaseUrl =
  process.env.AUTH_EMULATOR_URL ||
  `http://${process.env.AUTH_EMULATOR_HOST || "127.0.0.1"}:${
    process.env.AUTH_EMULATOR_PORT || "9099"
  }`;
const apiKey = process.env.FIREBASE_API_KEY || "fake-api-key";

function getAdmin() {
  if (!admin.apps.length) {
    admin.initializeApp({ projectId });
  }
  return admin;
}

function getDb() {
  return getAdmin().firestore();
}

async function signIn(email, password) {
  const response = await fetch(
    `${authBaseUrl}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${encodeURIComponent(
      apiKey
    )}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email,
        password,
        returnSecureToken: true,
      }),
    }
  );

  const text = await response.text();
  let body;
  try {
    body = text ? JSON.parse(text) : {};
  } catch (_) {
    body = { raw: text };
  }

  if (!response.ok || !body.idToken) {
    throw new Error(
      `Auth sign-in failed for ${email}: HTTP ${response.status} ${text}`
    );
  }

  return body.idToken;
}

async function callRegisterAttendance(token, payload) {
  const response = await fetch(
    `${functionsBaseUrl}/${projectId}/${region}/registerAttendance`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ data: payload }),
    }
  );

  const text = await response.text();
  let body;
  try {
    body = text ? JSON.parse(text) : {};
  } catch (_) {
    body = { raw: text };
  }

  return {
    httpStatus: response.status,
    ok: response.ok,
    body,
    errorCode: extractErrorCode(body),
  };
}

function extractErrorCode(body) {
  if (!body || typeof body !== "object") return null;
  if (body.error && typeof body.error === "object") {
    const details = body.error.details;
    if (details && typeof details === "object" && details.code) {
      return String(details.code);
    }
    if (body.error.status) return String(body.error.status);
    if (body.error.message) return String(body.error.message);
  }
  return null;
}

function isCallableSuccess(response) {
  return Boolean(
    response &&
      response.ok &&
      response.body &&
      Object.prototype.hasOwnProperty.call(response.body, "result")
  );
}

module.exports = {
  admin,
  getAdmin,
  getDb,
  projectId,
  region,
  functionsBaseUrl,
  authBaseUrl,
  signIn,
  callRegisterAttendance,
  extractErrorCode,
  isCallableSuccess,
};
