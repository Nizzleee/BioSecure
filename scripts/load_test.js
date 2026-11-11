#!/usr/bin/env node
/*
 * Staged load test against the Firebase Emulator Suite: 1 -> 10 -> 50 -> 100 -> 200.
 *
 * Each stage uses a DISJOINT block of seeded employees, so every request is a
 * real first check-in and the latency series stays comparable across stages.
 * Tokens come from the Auth emulator; the call goes through the HTTP callable
 * endpoint of the Functions emulator (same path the Android app uses).
 *
 * What this measures: server logic, transaction cost, duplicate prevention and
 * idempotent replays inside the emulator.
 * What this does NOT measure: production capacity, real network latency or
 * Firestore contention under multi-region load.
 */

const fs = require("node:fs");
const path = require("node:path");

const {
  admin,
  getAdmin,
  getDb,
  projectId,
  signIn,
  callRegisterAttendance,
  isCallableSuccess,
} = require("./lib/firebase_emulator");
const { summarizeLatencies, sleep, chunk } = require("./lib/helpers");

const adminSdk = getAdmin();
const db = getDb();

const STAGES = (process.env.LOADTEST_STAGES || "1,10,50,100,200")
  .split(",")
  .map((value) => Number.parseInt(value.trim(), 10))
  .filter((value) => Number.isFinite(value) && value > 0);

const EMPLOYEE_START = Number.parseInt(process.env.LOADTEST_EMPLOYEE_START || "1", 10);
const EMPLOYEE_DOMAIN =
  process.env.SEED_EMPLOYEE_EMAIL_DOMAIN || "demo.biosecure.test";
const PASSWORD = process.env.SEED_PASSWORD || "Test1234!";
const TIMEZONE = process.env.SEED_TIMEZONE || "America/Lima";
const SEDE_LAT = Number(process.env.SEED_SEDE_LAT || "-12.0464");
const SEDE_LNG = Number(process.env.SEED_SEDE_LNG || "-77.0429");
const JITTER_DEGREES = Number(process.env.LOADTEST_JITTER_DEGREES || "0.0005");
const ACCURACY_METERS = Number(process.env.LOADTEST_ACCURACY || "12");
const AUTH_CONCURRENCY = Number.parseInt(process.env.LOADTEST_AUTH_CONCURRENCY || "16", 10);
const COOLDOWN_MS = Number.parseInt(process.env.LOADTEST_COOLDOWN_MS || "1500", 10);
const OUTPUT_PATH =
  process.env.LOADTEST_OUTPUT || path.join("loadtest", "results", "loadtest-latest.json");

const EXPECTED_REJECTIONS = new Set(["TOO_SOON", "ALREADY_COMPLETE"]);

function employeeUid(index) {
  return `emp${String(index).padStart(21, "0")}`;
}

function employeeEmail(index) {
  return `employee${String(index).padStart(3, "0")}@${EMPLOYEE_DOMAIN}`;
}

function jitter() {
  return (Math.random() * 2 - 1) * JITTER_DEGREES;
}

async function authenticateAll(users) {
  const startedAt = performance.now();
  let done = 0;
  const failures = [];

  for (const group of chunk(users, AUTH_CONCURRENCY)) {
    await Promise.all(
      group.map(async (user) => {
        try {
          user.token = await signIn(user.email, PASSWORD);
        } catch (error) {
          failures.push({ index: user.index, email: user.email, error: error.message });
        } finally {
          done += 1;
          if (done % 50 === 0 || done === users.length) {
            console.log(`  auth ${done}/${users.length}`);
          }
        }
      })
    );
  }

  return { durationMs: performance.now() - startedAt, failures };
}

async function runStage(stage, users) {
  const jobs = users.map((user) => ({
    user,
    payload: {
      tipo: "HUELLA",
      lat: SEDE_LAT + jitter(),
      lng: SEDE_LNG + jitter(),
      accuracy: ACCURACY_METERS,
      isMock: false,
      requestId: crypto.randomUUID(),
    },
  }));

  const wallStart = performance.now();
  const attempts = await Promise.all(
    jobs.map(async ({ user, payload }) => {
      const callStart = performance.now();
      try {
        const response = await callRegisterAttendance(user.token, payload);
        return {
          index: user.index,
          uid: user.uid,
          requestId: payload.requestId,
          payload,
          durationMs: performance.now() - callStart,
          ok: isCallableSuccess(response),
          code: isCallableSuccess(response) ? "OK" : response.errorCode || "UNKNOWN_ERROR",
          httpStatus: response.httpStatus,
          result: response.body?.result ?? null,
        };
      } catch (error) {
        return {
          index: user.index,
          uid: user.uid,
          requestId: payload.requestId,
          payload,
          durationMs: performance.now() - callStart,
          ok: false,
          code: "NETWORK_ERROR",
          httpStatus: 0,
          result: null,
          error: error.message,
        };
      }
    })
  );
  const wallMs = performance.now() - wallStart;

  const codes = {};
  for (const attempt of attempts) {
    codes[attempt.code] = (codes[attempt.code] || 0) + 1;
  }

  const successes = attempts.filter((attempt) => attempt.ok);
  return {
    users: stage,
    wallMs,
    throughputPerSecond: successes.length / (wallMs / 1000),
    codes,
    successes: successes.length,
    latencyMs: summarizeLatencies(attempts.map((a) => a.durationMs)),
    successLatencyMs: summarizeLatencies(successes.map((a) => a.durationMs)),
    attempts,
  };
}

function formatTable(rows) {
  const header = [
    "users".padStart(6),
    "wall(ms)".padStart(10),
    "ok".padStart(5),
    "req/s".padStart(8),
    "p50".padStart(8),
    "p95".padStart(8),
    "p99".padStart(8),
    "codes".padStart(28),
  ].join(" | ");
  const lines = [header, "-".repeat(header.length)];
  for (const row of rows) {
    lines.push(
      [
        String(row.users).padStart(6),
        String(Math.round(row.wallMs)).padStart(10),
        String(row.successes).padStart(5),
        row.throughputPerSecond.toFixed(2).padStart(8),
        String(row.latencyMs.p50 ?? "-").padStart(8),
        String(row.latencyMs.p95 ?? "-").padStart(8),
        String(row.latencyMs.p99 ?? "-").padStart(8),
        JSON.stringify(row.codes).padStart(28),
      ].join(" | ")
    );
  }
  return lines.join("\n");
}

async function countCollection(name) {
  const snapshot = await db.collection(name).get();
  return snapshot.size;
}

async function attendanceStats() {
  const snapshot = await db.collection("attendances").get();
  const perKey = new Map();
  let withoutCheckIn = 0;

  for (const doc of snapshot.docs) {
    const data = doc.data();
    const key = `${data.userId || "?"}|${data.date || "?"}`;
    perKey.set(key, (perKey.get(key) || 0) + 1);
    if (!data.checkIn) withoutCheckIn += 1;
  }

  const duplicates = [...perKey.entries()]
    .filter(([, count]) => count > 1)
    .map(([key, count]) => ({ key, count }));

  return {
    total: snapshot.size,
    uniqueUserDateKeys: perKey.size,
    duplicateCount: duplicates.length,
    duplicates,
    withoutCheckIn,
  };
}

async function runDuplicateProbes(stageRecords) {
  const probe = stageRecords.find((record) => record.ok);
  if (!probe) {
    return { skipped: true, reason: "no successful request to replay" };
  }

  const dbBefore = await countCollection("attendances");

  // 1) Same requestId, same payload -> idempotent replay, no new document.
  const replay = await callRegisterAttendance(
    (await signIn(employeeEmail(probe.index), PASSWORD)),
    probe.payload
  );
  const replayResult = replay.body?.result;

  // 2) New requestId for a user who already checked in -> logical rejection
  //    (or a CHECK_OUT if more than MIN_MINUTES_BEFORE_CHECKOUT have passed).
  const secondCall = await callRegisterAttendance(
    await signIn(employeeEmail(probe.index), PASSWORD),
    {
      tipo: "HUELLA",
      lat: SEDE_LAT,
      lng: SEDE_LNG,
      accuracy: ACCURACY_METERS,
      isMock: false,
      requestId: crypto.randomUUID(),
    }
  );

  const dbAfter = await countCollection("attendances");

  return {
    employeeIndex: probe.index,
    replay: {
      ok: isCallableSuccess(replay),
      sameCheckIn: replayResult?.checkIn === probe.result?.checkIn,
      errorCode: replay.errorCode,
    },
    secondCall: {
      httpStatus: secondCall.httpStatus,
      errorCode: secondCall.errorCode,
      type: secondCall.body?.result?.type ?? null,
      expected: Boolean(
        EXPECTED_REJECTIONS.has(secondCall.errorCode) ||
          secondCall.body?.result?.type === "CHECK_OUT"
      ),
    },
    documentsBefore: dbBefore,
    documentsAfter: dbAfter,
    newDocuments: dbAfter - dbBefore,
  };
}

async function main() {
  if (process.env.FIRESTORE_EMULATOR_HOST === undefined) {
    console.warn("FIRESTORE_EMULATOR_HOST is not set; assuming 127.0.0.1:8080");
  }
  if (STAGES.length === 0) {
    throw new Error("LOADTEST_STAGES is empty");
  }

  const totalUsers = STAGES.reduce((acc, stage) => acc + stage, 0);
  const users = Array.from({ length: totalUsers }, (_, offset) => {
    const index = EMPLOYEE_START + offset;
    return { index, uid: employeeUid(index), email: employeeEmail(index), token: null };
  });

  console.log(
    `Staged load test: project=${projectId} stages=${STAGES.join("->")} users=${totalUsers} ` +
      `(employee ${EMPLOYEE_START}..${EMPLOYEE_START + totalUsers - 1})`
  );

  const baselineAttendances = await countCollection("attendances");
  console.log(`Baseline attendances before the run: ${baselineAttendances}`);

  console.log("Authenticating users against the Auth emulator...");
  const auth = await authenticateAll(users);
  if (auth.failures.length > 0) {
    console.error("Auth failures:", JSON.stringify(auth.failures.slice(0, 5), null, 2));
    throw new Error(`${auth.failures.length} user(s) could not authenticate`);
  }
  console.log(
    `Authenticated ${users.length} users in ${Math.round(auth.durationMs)} ms ` +
      `(${(users.length / (auth.durationMs / 1000)).toFixed(1)} logins/s)`
  );

  const stageResults = [];
  let cursor = 0;

  for (const stage of STAGES) {
    const stageUsers = users.slice(cursor, cursor + stage);
    cursor += stage;

    console.log(`\nStage ${stage}: firing ${stageUsers.length} concurrent check-ins...`);
    const result = await runStage(stage, stageUsers);
    stageResults.push(result);
    console.log(
      `  ok=${result.successes}/${stage} wall=${Math.round(result.wallMs)}ms ` +
        `p50=${result.latencyMs.p50}ms p95=${result.latencyMs.p95}ms p99=${result.latencyMs.p99}ms ` +
        `codes=${JSON.stringify(result.codes)}`
    );

    if (COOLDOWN_MS > 0 && stage !== STAGES[STAGES.length - 1]) {
      await sleep(COOLDOWN_MS);
    }
  }

  const allAttempts = stageResults.flatMap((stage) => stage.attempts);
  const unexpected = allAttempts.filter(
    (attempt) => !attempt.ok && !EXPECTED_REJECTIONS.has(attempt.code)
  );

  const stats = await attendanceStats();
  const createdDuringRun = stats.total - baselineAttendances;
  const successes = allAttempts.filter((attempt) => attempt.ok).length;

  console.log("\nDuplicate and idempotency probes...");
  const probes = await runDuplicateProbes(allAttempts);

  const notifications = await countCollection("notifications");
  const idempotencyKeys = await countCollection("idempotency_keys");

  const totals = {
    requests: allAttempts.length,
    successes,
    errors: allAttempts.length - successes,
    unexpectedErrors: unexpected.length,
    attendanceDocsBefore: baselineAttendances,
    attendanceDocsAfter: stats.total,
    documentsCreatedDuringRun: createdDuringRun,
    duplicateDocuments: stats.duplicateCount,
    notifications,
    idempotencyKeys,
  };

  const codes = {};
  for (const attempt of allAttempts) {
    codes[attempt.code] = (codes[attempt.code] || 0) + 1;
  }

  const summary = {
    projectId,
    startedAt: new Date().toISOString(),
    finishedAt: new Date().toISOString(),
    environment: "firebase-emulator-suite",
    config: {
      stages: STAGES,
      employeeRange: [users[0].index, users[users.length - 1].index],
      accuracyMeters: ACCURACY_METERS,
      timezone: TIMEZONE,
      sede: { lat: SEDE_LAT, lng: SEDE_LNG, jitterDegrees: JITTER_DEGREES },
      authConcurrency: AUTH_CONCURRENCY,
    },
    auth: {
      users: users.length,
      durationMs: Math.round(auth.durationMs),
      loginsPerSecond: Number((users.length / (auth.durationMs / 1000)).toFixed(2)),
    },
    stages: stageResults.map((stage) => ({
      users: stage.users,
      wallMs: Math.round(stage.wallMs),
      throughputPerSecond: Number(stage.throughputPerSecond.toFixed(2)),
      successes: stage.successes,
      codes: stage.codes,
      latencyMs: stage.latencyMs,
      successLatencyMs: stage.successLatencyMs,
    })),
    totals: { ...totals, codes },
    probes,
    notes: [
      "Measured against the Firebase Emulator Suite, not production.",
      "The emulator does not reproduce Firestore contention or multi-region latency.",
      "Each stage used a disjoint block of employees, so every request was a first check-in.",
      "duplicateDocuments counts documents sharing userId+date, not ALREADY_COMPLETE/TOO_SOON responses.",
    ],
  };

  console.log("\n" + formatTable(stageResults));
  console.log("\nTotals:", JSON.stringify(totals, null, 2));
  console.log("Probes:", JSON.stringify(probes, null, 2));

  fs.mkdirSync(path.dirname(OUTPUT_PATH), { recursive: true });
  fs.writeFileSync(OUTPUT_PATH, JSON.stringify(summary, null, 2));
  console.log(`\nResults written to ${OUTPUT_PATH}`);

  const problems = [];
  if (unexpected.length > 0) problems.push(`${unexpected.length} unexpected error(s)`);
  if (stats.duplicateCount > 0) problems.push(`${stats.duplicateCount} duplicate document key(s)`);
  if (createdDuringRun !== successes) {
    problems.push(
      `documents created (${createdDuringRun}) do not match successful check-ins (${successes})`
    );
  }
  if (probes.replay && probes.replay.ok === false) problems.push("idempotent replay failed");
  if (probes.newDocuments > 0) problems.push("probe created unexpected documents");

  if (problems.length > 0) {
    throw new Error(problems.join("; "));
  }
}

main()
  .catch((error) => {
    console.error("Load test failed:", error.message);
    process.exitCode = 1;
  })
  .finally(async () => {
    await adminSdk.app().delete().catch(() => {});
  });
