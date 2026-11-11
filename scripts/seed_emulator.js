#!/usr/bin/env node
/*
 * Seed the Firebase Emulator Suite with one company, one sede, one open shift,
 * one admin and N employees.
 *
 * Usage:
 *   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
 *   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 \
 *   SEED_EMPLOYEES=200 node scripts/seed_emulator.js
 *
 * The script refuses to run without the emulator hosts. It never targets a
 * production project by accident.
 */

const { getAdmin, getDb, projectId } = require("./lib/firebase_emulator");

const admin = getAdmin();
const db = getDb();

const COMPANY_ID = process.env.SEED_COMPANY_ID || "demo-company";
const SEDE_ID = process.env.SEED_SEDE_ID || "demo-sede";
const SHIFT_ID = process.env.SEED_SHIFT_ID || "demo-shift";
const EMPLOYEE_COUNT = Math.max(
  1,
  Number.parseInt(process.env.SEED_EMPLOYEES || "200", 10)
);
const PASSWORD = process.env.SEED_PASSWORD || "Test1234!";
const ADMIN_EMAIL = process.env.SEED_ADMIN_EMAIL || "admin@demo.biosecure.test";
const EMPLOYEE_EMAIL_DOMAIN =
  process.env.SEED_EMPLOYEE_EMAIL_DOMAIN || "demo.biosecure.test";
const TIMEZONE = process.env.SEED_TIMEZONE || "America/Lima";
const SEDE_LAT = Number(process.env.SEED_SEDE_LAT || "-12.0464");
const SEDE_LNG = Number(process.env.SEED_SEDE_LNG || "-77.0429");
const SEDE_RADIO_METROS = Number(process.env.SEED_SEDE_RADIO_METROS || "300");
const SHIFT_START = process.env.SEED_SHIFT_START || "00:00";
const SHIFT_END = process.env.SEED_SHIFT_END || "23:59";
const SHIFT_TOLERANCE_MIN = Number(
  process.env.SEED_SHIFT_TOLERANCE_MIN || "0"
);
const RESET = process.env.SEED_RESET !== "false";

function employeeUid(index) {
  return `emp${String(index).padStart(21, "0")}`;
}

function adminUid() {
  return `admin${String(1).padStart(21, "0")}`;
}

function employeeEmail(index) {
  return `employee${String(index).padStart(3, "0")}@${EMPLOYEE_EMAIL_DOMAIN}`;
}

async function upsertAuthUser(uid, email, password, displayName) {
  try {
    await admin.auth().getUser(uid);
    await admin.auth().updateUser(uid, {
      email,
      password,
      displayName,
      emailVerified: true,
      disabled: false,
    });
  } catch (error) {
    if (error.code !== "auth/user-not-found") throw error;
    await admin.auth().createUser({
      uid,
      email,
      password,
      displayName,
      emailVerified: true,
      disabled: false,
    });
  }
}

async function clearCollection(name) {
  if (typeof db.recursiveDelete === "function") {
    await db.recursiveDelete(db.collection(name));
    return;
  }

  const snapshot = await db.collection(name).get();
  let batch = db.batch();
  let count = 0;
  for (const doc of snapshot.docs) {
    batch.delete(doc.ref);
    count += 1;
    if (count % 450 === 0) {
      await batch.commit();
      batch = db.batch();
    }
  }
  await batch.commit();
}

async function main() {
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error("FIRESTORE_EMULATOR_HOST is required (emulator only)");
  }
  if (!process.env.FIREBASE_AUTH_EMULATOR_HOST) {
    throw new Error("FIREBASE_AUTH_EMULATOR_HOST is required (emulator only)");
  }

  console.log(
    `Seeding project=${projectId} company=${COMPANY_ID} employees=${EMPLOYEE_COUNT}`
  );

  if (RESET) {
    for (const collection of [
      "attendances",
      "idempotency_keys",
      "notifications",
    ]) {
      await clearCollection(collection);
    }
    console.log("Cleared attendances, idempotency_keys and notifications");
  }

  await db
    .collection("companies")
    .doc(COMPANY_ID)
    .set(
      {
        nombre: "Empresa Demo",
        timezone: TIMEZONE,
        activa: true,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

  await db
    .collection("companies")
    .doc(COMPANY_ID)
    .collection("sedes")
    .doc(SEDE_ID)
    .set(
      {
        nombre: "Sede Demo",
        lat: SEDE_LAT,
        lng: SEDE_LNG,
        radioMetros: SEDE_RADIO_METROS,
        activa: true,
      },
      { merge: true }
    );

  await db
    .collection("companies")
    .doc(COMPANY_ID)
    .collection("shifts")
    .doc(SHIFT_ID)
    .set(
      {
        nombre: "Turno Demo 24h",
        startTime: SHIFT_START,
        endTime: SHIFT_END,
        toleranceMin: SHIFT_TOLERANCE_MIN,
      },
      { merge: true }
    );

  const adminId = adminUid();
  await upsertAuthUser(adminId, ADMIN_EMAIL, PASSWORD, "Admin Demo");
  await db
    .collection("users")
    .doc(adminId)
    .set(
      {
        uid: adminId,
        name: "Admin Demo",
        firstName: "Admin",
        lastName: "Demo",
        email: ADMIN_EMAIL,
        role: "admin",
        companyId: COMPANY_ID,
        isActive: true,
      },
      { merge: true }
    );

  const batch = db.batch();
  for (let index = 1; index <= EMPLOYEE_COUNT; index += 1) {
    const uid = employeeUid(index);
    const email = employeeEmail(index);
    const name = `Empleado Demo ${String(index).padStart(3, "0")}`;

    await upsertAuthUser(uid, email, PASSWORD, name);

    batch.set(
      db.collection("users").doc(uid),
      {
        uid,
        name,
        firstName: "Empleado",
        lastName: `Demo ${String(index).padStart(3, "0")}`,
        email,
        role: "employee",
        companyId: COMPANY_ID,
        sedeId: SEDE_ID,
        shiftId: SHIFT_ID,
        isActive: true,
      },
      { merge: true }
    );
  }
  await batch.commit();

  console.log("Seed complete");
  console.log(
    JSON.stringify(
      {
        projectId,
        companyId: COMPANY_ID,
        sedeId: SEDE_ID,
        shiftId: SHIFT_ID,
        timezone: TIMEZONE,
        sede: { lat: SEDE_LAT, lng: SEDE_LNG, radioMetros: SEDE_RADIO_METROS },
        shift: {
          startTime: SHIFT_START,
          endTime: SHIFT_END,
          toleranceMin: SHIFT_TOLERANCE_MIN,
        },
        admin: { uid: adminId, email: ADMIN_EMAIL, password: PASSWORD },
        employees: {
          count: EMPLOYEE_COUNT,
          uidPrefix: "emp",
          firstUid: employeeUid(1),
          lastUid: employeeUid(EMPLOYEE_COUNT),
          firstEmail: employeeEmail(1),
          lastEmail: employeeEmail(EMPLOYEE_COUNT),
          password: PASSWORD,
        },
      },
      null,
      2
    )
  );
}

main()
  .catch((error) => {
    console.error("Seed failed:", error);
    process.exitCode = 1;
  })
  .finally(async () => {
    await admin.app().delete().catch(() => {});
  });
