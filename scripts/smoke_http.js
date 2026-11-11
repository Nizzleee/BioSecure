#!/usr/bin/env node
/*
 * Smoke test of the REAL HTTP path, before any load is generated.
 *
 * It proves, against the Firebase Emulator Suite:
 *   1. the callable endpoint is up and rejects unauthenticated calls,
 *   2. an Auth emulator ID token is accepted by the callable,
 *   3. defineInt() params are read from the emulator environment (see
 *      MAX_ACCURACY_METERS=45 in docker/firebase-emulator/functions.env:
 *      accuracy 47 must be rejected, which would NOT happen with the
 *      hardcoded default of 50),
 *   4. the geofence is evaluated against the seeded sede,
 *   5. a check-in is written to Firestore with the deterministic doc id,
 *   6. replaying the same requestId returns the same payload and does not
 *      create a second document.
 */

const {
  admin,
  getAdmin,
  getDb,
  projectId,
  region,
  functionsBaseUrl,
  signIn,
  callRegisterAttendance,
  isCallableSuccess,
} = require("./lib/firebase_emulator");
const { dateYmdInTimeZone } = require("./lib/helpers");

const adminSdk = getAdmin();
const db = getDb();

const TIMEZONE = process.env.SEED_TIMEZONE || "America/Lima";
const SEDE_LAT = Number(process.env.SEED_SEDE_LAT || "-12.0464");
const SEDE_LNG = Number(process.env.SEED_SEDE_LNG || "-77.0429");
const EMPLOYEE_COUNT = Number.parseInt(process.env.SEED_EMPLOYEES || "400", 10);
const SMOKE_INDEX = Number.parseInt(
  process.env.SMOKE_EMPLOYEE_INDEX || String(EMPLOYEE_COUNT),
  10
);
const EMPLOYEE_DOMAIN =
  process.env.SEED_EMPLOYEE_EMAIL_DOMAIN || "demo.biosecure.test";
const PASSWORD = process.env.SEED_PASSWORD || "Test1234!";
const ACCURACY_LIMIT = Number.parseInt(
  process.env.SMOKE_EXPECTED_MAX_ACCURACY || "45",
  10
);
const COMPANY_ID = process.env.SEED_COMPANY_ID || "demo-company";
const CALLABLE_URL = `${functionsBaseUrl}/${projectId}/${region}/registerAttendance`;

const employeeUid = `emp${String(SMOKE_INDEX).padStart(21, "0")}`;
const employeeEmail = `employee${String(SMOKE_INDEX).padStart(3, "0")}@${EMPLOYEE_DOMAIN}`;

const checks = [];

function check(name, ok, detail) {
  checks.push({ name, ok: Boolean(ok), detail });
  const label = ok ? "PASS" : "FAIL";
  console.log(`[${label}] ${name}${detail === undefined ? "" : ` -> ${JSON.stringify(detail)}`}`);
  return ok;
}

async function checkEndpoints() {
  // The hub binds to 127.0.0.1 inside the emulator container, so it is not a
  // valid readiness probe from another container. Probe the callable itself:
  // an unauthenticated POST must answer 401 UNAUTHENTICATED, which proves the
  // HTTP endpoint is up and enforcing auth.
  let callableOk = false;
  let callableDetail = {};
  try {
    const response = await fetch(CALLABLE_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ data: {} }),
    });
    const body = await response.text();
    callableOk = !response.ok && /UNAUTHENTICATED/i.test(body);
    callableDetail = { httpStatus: response.status, body: body.slice(0, 200) };
  } catch (error) {
    callableDetail = { error: error.message };
  }
  check("functions endpoint answers and enforces auth", callableOk, callableDetail);

  const companyDoc = await db.collection("companies").doc(COMPANY_ID).get();
  check("firestore emulator reachable and seeded", companyDoc.exists, {
    companyId: COMPANY_ID,
    timezone: companyDoc.data()?.timezone ?? null,
  });

  return callableOk && companyDoc.exists;
}

async function resetSmokeUserState() {
  // The smoke always uses the same employee, and the backend rejects a second
  // check-in on the same day (ALREADY_COMPLETE / TOO_SOON). A fresh run must
  // therefore clear only this employee's documents. Load-test users (1..361)
  // are never touched, so the split asserted by the load test still holds.
  const attendances = await db
    .collection("attendances")
    .where("userId", "==", employeeUid)
    .get();
  const keys = await db
    .collection("idempotency_keys")
    .where("uid", "==", employeeUid)
    .get();

  if (attendances.size > 0 || keys.size > 0) {
    const batch = db.batch();
    for (const doc of attendances.docs) batch.delete(doc.ref);
    for (const doc of keys.docs) batch.delete(doc.ref);
    await batch.commit();
  }

  return {
    removedAttendances: attendances.size,
    removedIdempotencyKeys: keys.size,
  };
}

function basePayload(overrides = {}) {
  return {
    tipo: "HUELLA",
    lat: SEDE_LAT,
    lng: SEDE_LNG,
    accuracy: 12,
    isMock: false,
    requestId: crypto.randomUUID(),
    ...overrides,
  };
}

async function main() {
  console.log(`Smoke test against project=${projectId} user=${employeeEmail}`);

  const endpointsOk = await checkEndpoints();
  if (!endpointsOk) {
    throw new Error("emulator endpoints are not ready; aborting smoke test");
  }

  const reset = await resetSmokeUserState();
  check("smoke user starts from a clean state (repeatable run)", true, reset);

  const token = await signIn(employeeEmail, PASSWORD);
  check("auth emulator returns an ID token", typeof token === "string" && token.length > 0, {
    tokenLength: token.length,
  });

  // --- Negative 1: accuracy above the emulator-provided MAX_ACCURACY_METERS ---
  const highAccuracy = ACCURACY_LIMIT + 2;
  const accuracyResponse = await callRegisterAttendance(
    token,
    basePayload({ accuracy: highAccuracy })
  );
  check(
    `accuracy ${highAccuracy}m is rejected (proves MAX_ACCURACY_METERS=${ACCURACY_LIMIT})`,
    accuracyResponse.errorCode === "ACCURACY_TOO_HIGH",
    { httpStatus: accuracyResponse.httpStatus, code: accuracyResponse.errorCode }
  );

  // --- Negative 2: coordinates outside the seeded geofence ---
  const geofenceResponse = await callRegisterAttendance(
    token,
    basePayload({ lat: SEDE_LAT + 0.02, lng: SEDE_LNG + 0.02 })
  );
  check(
    "coordinates outside the sede are rejected",
    geofenceResponse.errorCode === "OUT_OF_RANGE_GEOFENCE",
    { httpStatus: geofenceResponse.httpStatus, code: geofenceResponse.errorCode }
  );

  // --- Positive: real check-in ---
  const requestId = crypto.randomUUID();
  const payload = basePayload({ requestId });
  const startedAt = performance.now();
  const successResponse = await callRegisterAttendance(token, payload);
  const latencyMs = Math.round(performance.now() - startedAt);

  const result = successResponse.body?.result;
  check("check-in returns HTTP 200 with a callable result", isCallableSuccess(successResponse), {
    httpStatus: successResponse.httpStatus,
    latencyMs,
  });
  if (!isCallableSuccess(successResponse)) {
    console.error(JSON.stringify(successResponse.body, null, 2));
    throw new Error("check-in did not return a result");
  }

  check(
    "response shape carries the server contract",
    result.type === "CHECK_IN" &&
      typeof result.checkIn === "string" &&
      /^\d{2}:\d{2}$/.test(result.checkIn) &&
      typeof result.date === "string" &&
      /^\d{2}\/\d{2}\/\d{4}$/.test(result.date) &&
      typeof result.userName === "string" &&
      ["PUNTUAL", "TARDANZA"].includes(result.status),
    result
  );

  const expectedYmd = dateYmdInTimeZone(TIMEZONE);
  const expectedDocId = `${employeeUid}_${expectedYmd}`;
  const doc = await db.collection("attendances").doc(expectedDocId).get();
  check("attendance document exists with the deterministic id", doc.exists, {
    docId: expectedDocId,
  });

  if (doc.exists) {
    const data = doc.data();
    check(
      "stored document matches the response",
      data.userId === employeeUid &&
        data.type === "HUELLA" &&
        data.checkIn === result.checkIn &&
        data.date === result.date &&
        data.requestId === requestId &&
        data.marcadoPor === employeeUid &&
        data.clientMockFlag === false &&
        data.status === result.status,
      {
        userId: data.userId,
        type: data.type,
        status: data.status,
        distanceMeters: data.distanceMeters,
        checkOut: data.checkOut,
      }
    );
    check("check-in leaves checkOut empty", data.checkOut === "", data.checkOut);
  }

  // --- Idempotent replay: same requestId, same payload ---
  const replayResponse = await callRegisterAttendance(token, payload);
  const replayResult = replayResponse.body?.result;
  check(
    "replaying the same requestId returns the original response",
    isCallableSuccess(replayResponse) &&
      replayResult?.checkIn === result.checkIn &&
      replayResult?.type === result.type &&
      replayResult?.status === result.status,
    { httpStatus: replayResponse.httpStatus }
  );

  const snapshot = await db
    .collection("attendances")
    .where("userId", "==", employeeUid)
    .get();
  check("replay does not create a second attendance document", snapshot.size === 1, {
    documentsForUser: snapshot.size,
  });

  const idempotencyDoc = await db.collection("idempotency_keys").doc(requestId).get();
  check("idempotency key is persisted", idempotencyDoc.exists, {
    requestId,
    hasResponse: Boolean(idempotencyDoc.data()?.response),
  });

  const failed = checks.filter((entry) => !entry.ok);
  const summary = {
    ok: failed.length === 0,
    projectId,
    employee: { uid: employeeUid, email: employeeEmail },
    attendanceDocId: expectedDocId,
    latencyMs,
    checkInLatencyMs: latencyMs,
    checks,
  };

  console.log(JSON.stringify(summary, null, 2));
  if (failed.length > 0) {
    throw new Error(`${failed.length} smoke check(s) failed`);
  }
}

main()
  .catch((error) => {
    console.error("Smoke test failed:", error.message);
    process.exitCode = 1;
  })
  .finally(async () => {
    await adminSdk.app().delete().catch(() => {});
  });
