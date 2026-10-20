import * as admin from "firebase-admin";
import functionsTest from "firebase-functions-test";
import { HttpsError } from "firebase-functions/v2/https";
import { randomUUID } from "crypto";

// ---------------------------------------------------------------------------
// RELOJ CONTROLADO POR INYECCIÓN:
// Mockeamos formatServerTime para que SIEMPRE formatee el instante mockClock.iso.
// No usamos fake timers de Jest porque interfieren con los deadlines de gRPC
// del emulador de Firestore.
// ---------------------------------------------------------------------------
const mockClock = { iso: "2026-06-24T13:15:00.000Z" }; // 08:15 en America/Lima (UTC-5)

jest.mock("../src/logic/timeUtils", () => {
  const actual = jest.requireActual("../src/logic/timeUtils");
  return {
    ...actual,
    formatServerTime: (_date: Date, tz: string) =>
      actual.formatServerTime(new Date(mockClock.iso), tz),
  };
});

// Importar DESPUÉS del mock (ts-jest eleva jest.mock por encima de los imports).
import { registerAttendance } from "../src/index";

jest.setTimeout(180000);

const testEnv = functionsTest({ projectId: "demo-biosecure-test" });
const wrapped = testEnv.wrap(registerAttendance as any) as (event: any) => Promise<any>;

const db = admin.firestore();

// ---------------------------------------------------------------------------
// IDs de 28 caracteres alfanuméricos
// ---------------------------------------------------------------------------
const COMPANY_A = "companyA";
const COMPANY_B = "companyB";

const SEDE_A = "sedeA";
const SEDE_A2 = "sedeA2";
const SEDE_B = "sedeB";
const SHIFT_A = "shiftA";
const SHIFT_A_TOL0 = "shiftATol0";
const SHIFT_B = "shiftB";

const UID = {
  adminA: "adminA" + "0".repeat(22),          // 28
  adminA2: "adminA2" + "1".repeat(21),        // 28
  adminB: "adminB" + "2".repeat(22),          // 28
  empA1: "empA1" + "3".repeat(23),            // 28
  empA2: "empA2" + "4".repeat(23),            // 28
  empB1: "empB1" + "5".repeat(23),            // 28
  inactive: "inactive" + "6".repeat(20),      // 28
  noCompany: "noCompany" + "7".repeat(19),    // 28
  noSede: "noSede" + "8".repeat(22),          // 28
  noShift: "noShift" + "9".repeat(21),        // 28
  shiftMissing: "shiftMissing" + "a".repeat(16), // 28
  corruptEmp: "corruptEmp" + "b".repeat(18),  // 28
  ghost: "ghost" + "c".repeat(23),            // 28 (no existe)
};

const SEDE_A_LAT = -12.046374;
const SEDE_A_LNG = -77.042793;
const SEDE_B_LAT = -12.1;
const SEDE_B_LNG = -77.05;

const DATE_YYYYMMDD = "20260624";
const DATE_DDMMYYYY = "24/06/2026";

function docId(uid: string): string {
  return `${uid}_${DATE_YYYYMMDD}`;
}

function basePayload(overrides: Record<string, any> = {}): Record<string, any> {
  return {
    tipo: "HUELLA",
    lat: SEDE_A_LAT,
    lng: SEDE_A_LNG,
    accuracy: 10,
    requestId: randomUUID(),
    ...overrides,
  };
}

function callAs(uid: string, data: any, token: any = {}): Promise<any> {
  return wrapped({ data, auth: { uid, token } });
}

function callNoAuth(data: any): Promise<any> {
  return wrapped({ data });
}

async function expectError(promise: Promise<any>, expectedCode: string): Promise<HttpsError> {
  let caught: any;
  try {
    await promise;
  } catch (e) {
    caught = e;
  }
  if (!caught) {
    throw new Error(`Se esperaba error ${expectedCode}, pero la llamada resolvió`);
  }
  expect(caught).toBeInstanceOf(HttpsError);
  expect(caught.details?.code).toBe(expectedCode);
  return caught as HttpsError;
}

async function clearDatabase(): Promise<void> {
  for (const col of ["users", "attendances", "idempotency_keys", "notifications"]) {
    const snap = await db.collection(col).get();
    await Promise.all(snap.docs.map((d) => d.ref.delete()));
  }
  const companies = await db.collection("companies").get();
  await Promise.all(companies.docs.map((d) => db.recursiveDelete(d.ref)));
}

async function seed(): Promise<void> {
  await clearDatabase();

  await db.collection("companies").doc(COMPANY_A).set({
    name: "Empresa A",
    timezone: "America/Lima",
  });
  await db.collection("companies").doc(COMPANY_B).set({
    name: "Empresa B",
    timezone: "America/Lima",
  });

  await db.doc(`companies/${COMPANY_A}/sedes/${SEDE_A}`).set({
    nombre: "Sede A",
    lat: SEDE_A_LAT,
    lng: SEDE_A_LNG,
    radioMetros: 100,
    activa: true,
  });
  await db.doc(`companies/${COMPANY_A}/sedes/${SEDE_A2}`).set({
    nombre: "Sede A2",
    lat: SEDE_A_LAT,
    lng: SEDE_A_LNG,
    radioMetros: 100,
    activa: true,
  });
  await db.doc(`companies/${COMPANY_B}/sedes/${SEDE_B}`).set({
    nombre: "Sede B",
    lat: SEDE_B_LAT,
    lng: SEDE_B_LNG,
    radioMetros: 100,
    activa: true,
  });

  await db.doc(`companies/${COMPANY_A}/shifts/${SHIFT_A}`).set({
    startTime: "08:00",
    endTime: "17:00",
    toleranceMin: 15,
  });
  await db.doc(`companies/${COMPANY_A}/shifts/${SHIFT_A_TOL0}`).set({
    startTime: "08:00",
    endTime: "17:00",
    toleranceMin: 0,
  });
  await db.doc(`companies/${COMPANY_B}/shifts/${SHIFT_B}`).set({
    startTime: "08:00",
    endTime: "17:00",
    toleranceMin: 15,
  });

  const users: Array<[string, Record<string, any>]> = [
    [UID.adminA, { name: "Admin A", role: "admin", companyId: COMPANY_A, sedeId: SEDE_A, shiftId: SHIFT_A, isActive: true }],
    [UID.adminA2, { name: "Admin A2", role: "admin", companyId: COMPANY_A, sedeId: SEDE_A, shiftId: SHIFT_A, isActive: true }],
    [UID.adminB, { name: "Admin B", role: "admin", companyId: COMPANY_B, sedeId: SEDE_B, shiftId: SHIFT_B, isActive: true }],
    [UID.empA1, { name: "Empleado A1", role: "employee", companyId: COMPANY_A, sedeId: SEDE_A, shiftId: SHIFT_A, isActive: true }],
    [UID.empA2, { name: "Empleado A2", role: "employee", companyId: COMPANY_A, sedeId: SEDE_A2, shiftId: SHIFT_A, isActive: true }],
    [UID.empB1, { name: "Empleado B1", role: "employee", companyId: COMPANY_B, sedeId: SEDE_B, shiftId: SHIFT_B, isActive: true }],
    [UID.inactive, { name: "Inactivo", role: "employee", companyId: COMPANY_A, sedeId: SEDE_A, shiftId: SHIFT_A, isActive: false }],
    [UID.noCompany, { name: "Sin Empresa", role: "employee", companyId: "", sedeId: SEDE_A, shiftId: SHIFT_A, isActive: true }],
    [UID.noSede, { name: "Sin Sede", role: "employee", companyId: COMPANY_A, sedeId: "", shiftId: SHIFT_A, isActive: true }],
    [UID.noShift, { name: "Sin Turno", role: "employee", companyId: COMPANY_A, sedeId: SEDE_A, shiftId: "", isActive: true }],
    [UID.shiftMissing, { name: "Turno Fantasma", role: "employee", companyId: COMPANY_A, sedeId: SEDE_A, shiftId: "shiftNope", isActive: true }],
    [UID.corruptEmp, { name: "Corrupto", role: "employee", companyId: COMPANY_A, sedeId: SEDE_A, shiftId: SHIFT_A, isActive: true }],
  ];
  for (const [uid, data] of users) {
    await db.collection("users").doc(uid).set(data);
  }
}

beforeEach(async () => {
  mockClock.iso = "2026-06-24T13:15:00.000Z"; // 08:15 Lima
  await seed();
});

afterAll(async () => {
  await clearDatabase();
  testEnv.cleanup();
});

// ===========================================================================
describe("registerAttendance — integración real contra emulador Firestore", () => {
  // -------------------------------------------------------------------------
  describe("Camino feliz", () => {
    test("éxito con huella dentro de geocerca crea exactamente 1 doc {uid}_{yyyyMMdd}", async () => {
      const res = await callAs(UID.empA1, basePayload({ tipo: "HUELLA" }));

      expect(res.type).toBe("CHECK_IN");
      expect(res.status).toBe("PUNTUAL");
      expect(res.checkIn).toBe("08:15");
      expect(res.date).toBe(DATE_DDMMYYYY);

      const doc = await db.collection("attendances").doc(docId(UID.empA1)).get();
      expect(doc.exists).toBe(true);
      const data = doc.data()!;
      expect(data.userId).toBe(UID.empA1);
      expect(data.companyId).toBe(COMPANY_A);
      expect(data.sedeId).toBe(SEDE_A);
      expect(data.checkIn).toBe("08:15");
      expect(data.checkOut).toBe("");
      expect(data.status).toBe("PUNTUAL");
      expect(data.type).toBe("HUELLA");

      const all = await db.collection("attendances").get();
      expect(all.size).toBe(1);
    });
  });

  // -------------------------------------------------------------------------
  describe("Autenticación y validación de payload", () => {
    test("sin auth → UNAUTHENTICATED", async () => {
      await expectError(callNoAuth(basePayload()), "UNAUTHENTICATED");
    });

    test("data como string → INVALID_ARGUMENT", async () => {
      await expectError(callAs(UID.empA1, "esto-no-es-un-objeto"), "INVALID_ARGUMENT");
    });

    test("tipo ausente → INVALID_TYPE", async () => {
      const data = basePayload();
      delete data.tipo;
      await expectError(callAs(UID.empA1, data), "INVALID_TYPE");
    });

    test("tipo inválido → INVALID_TYPE", async () => {
      await expectError(callAs(UID.empA1, basePayload({ tipo: "OTRO" })), "INVALID_TYPE");
    });

    test("qrToken numérico → INVALID_QR_TOKEN", async () => {
      await expectError(callAs(UID.empA1, basePayload({ qrToken: 12345 })), "INVALID_QR_TOKEN");
    });

    test("requestId con '/' → INVALID_REQUEST_ID", async () => {
      await expectError(callAs(UID.empA1, basePayload({ requestId: "req/con/slash" })), "INVALID_REQUEST_ID");
    });
  });

  // -------------------------------------------------------------------------
  describe("Autorización", () => {
    test("qrToken ajeno desde empleado → ADMIN_ONLY", async () => {
      await expectError(
        callAs(UID.empA1, basePayload({ tipo: "QR", qrToken: UID.empA2 })),
        "ADMIN_ONLY"
      );
    });

    test("admin A sobre empleado B → TARGET_UNAVAILABLE", async () => {
      await expectError(
        callAs(UID.adminA, basePayload({ tipo: "QR", qrToken: UID.empB1 })),
        "TARGET_UNAVAILABLE"
      );
    });

    test("admin A sobre uid inexistente → TARGET_UNAVAILABLE (misma respuesta)", async () => {
      const missing = await expectError(
        callAs(UID.adminA, basePayload({ tipo: "QR", qrToken: UID.ghost })),
        "TARGET_UNAVAILABLE"
      );
      const otherCompany = await expectError(
        callAs(UID.adminA, basePayload({ tipo: "QR", qrToken: UID.empB1 })),
        "TARGET_UNAVAILABLE"
      );
      expect(missing.message).toBe(otherCompany.message);
    });

    test("admin se marca a sí mismo → ADMIN_CANNOT_MARK_SELF", async () => {
      await expectError(callAs(UID.adminA, basePayload({ tipo: "HUELLA" })), "ADMIN_CANNOT_MARK_SELF");
    });

    test("admin escanea a otro admin → TARGET_NOT_EMPLOYEE", async () => {
      await expectError(
        callAs(UID.adminA, basePayload({ tipo: "QR", qrToken: UID.adminA2 })),
        "TARGET_NOT_EMPLOYEE"
      );
    });

    test("admin con tipo HUELLA y qrToken ajeno → INVALID_TYPE", async () => {
      await expectError(
        callAs(UID.adminA, basePayload({ tipo: "HUELLA", qrToken: UID.empA1 })),
        "INVALID_TYPE"
      );
    });

    test("caller inactivo → CALLER_INACTIVE", async () => {
      await expectError(callAs(UID.inactive, basePayload({ tipo: "HUELLA" })), "CALLER_INACTIVE");
    });

    test("empleado inactivo escaneado por admin → EMPLOYEE_INACTIVE", async () => {
      await expectError(
        callAs(UID.adminA, basePayload({ tipo: "QR", qrToken: UID.inactive })),
        "EMPLOYEE_INACTIVE"
      );
    });

    test("perfil sin companyId → NO_COMPANY_ASSIGNED", async () => {
      await expectError(callAs(UID.noCompany, basePayload({ tipo: "HUELLA" })), "NO_COMPANY_ASSIGNED");
    });
  });

  // -------------------------------------------------------------------------
  describe("Geocerca y accuracy", () => {
    test("fuera de geocerca → OUT_OF_RANGE_GEOFENCE", async () => {
      await expectError(
        callAs(UID.empA1, basePayload({ lat: 0, lng: 0 })),
        "OUT_OF_RANGE_GEOFENCE"
      );
      const doc = await db.collection("attendances").doc(docId(UID.empA1)).get();
      expect(doc.exists).toBe(false);
    });

    test("accuracy alta → ACCURACY_TOO_HIGH", async () => {
      await expectError(
        callAs(UID.empA1, basePayload({ accuracy: 500 })),
        "ACCURACY_TOO_HIGH"
      );
    });

    test("checkOut fuera de geocerca → OUT_OF_RANGE_GEOFENCE y no modifica el doc", async () => {
      await db.collection("attendances").doc(docId(UID.empA1)).set({
        userId: UID.empA1,
        companyId: COMPANY_A,
        sedeId: SEDE_A,
        date: DATE_DDMMYYYY,
        checkIn: "08:00",
        checkOut: "",
        status: "PUNTUAL",
      });

      await expectError(
        callAs(UID.empA1, basePayload({ lat: 0, lng: 0 })),
        "OUT_OF_RANGE_GEOFENCE"
      );

      const doc = await db.collection("attendances").doc(docId(UID.empA1)).get();
      expect(doc.data()!.checkOut).toBe("");
    });
  });

  // -------------------------------------------------------------------------
  describe("Ventana horaria", () => {
    test("OUT_OF_SCHEDULE_WINDOW no persiste documento", async () => {
      mockClock.iso = "2026-06-24T11:30:00.000Z"; // 06:30 Lima
      await expectError(
        callAs(UID.empA1, basePayload({ tipo: "HUELLA" })),
        "OUT_OF_SCHEDULE_WINDOW"
      );
      const doc = await db.collection("attendances").doc(docId(UID.empA1)).get();
      expect(doc.exists).toBe(false);
    });

    test("toleranceMin 0 en el límite exacto (08:00) → PUNTUAL", async () => {
      mockClock.iso = "2026-06-24T13:00:00.000Z"; // 08:00 Lima
      await db.collection("users").doc(UID.corruptEmp).update({ shiftId: SHIFT_A_TOL0 });

      const res = await callAs(UID.corruptEmp, basePayload({ tipo: "HUELLA" }));
      expect(res.type).toBe("CHECK_IN");
      expect(res.status).toBe("PUNTUAL");
    });
  });

  // -------------------------------------------------------------------------
  describe("Check-out", () => {
    test("ALREADY_COMPLETE", async () => {
      await db.collection("attendances").doc(docId(UID.empA1)).set({
        userId: UID.empA1,
        companyId: COMPANY_A,
        sedeId: SEDE_A,
        date: DATE_DDMMYYYY,
        checkIn: "08:00",
        checkOut: "17:00",
        status: "PUNTUAL",
      });
      await expectError(callAs(UID.empA1, basePayload()), "ALREADY_COMPLETE");
    });

    test("TOO_SOON", async () => {
      mockClock.iso = "2026-06-24T13:02:00.000Z"; // 08:02 Lima
      await db.collection("attendances").doc(docId(UID.empA1)).set({
        userId: UID.empA1,
        companyId: COMPANY_A,
        sedeId: SEDE_A,
        date: DATE_DDMMYYYY,
        checkIn: "08:00",
        checkOut: "",
        status: "PUNTUAL",
      });
      await expectError(callAs(UID.empA1, basePayload()), "TOO_SOON");
    });

    test("status ausente en doc existente → CORRUPT_CHECKIN_DATA", async () => {
      await db.collection("attendances").doc(docId(UID.corruptEmp)).set({
        userId: UID.corruptEmp,
        companyId: COMPANY_A,
        sedeId: SEDE_A,
        date: DATE_DDMMYYYY,
        checkIn: "08:00",
        checkOut: "",
      });
      await expectError(callAs(UID.corruptEmp, basePayload()), "CORRUPT_CHECKIN_DATA");
    });

    test("checkIn corrupto → CORRUPT_CHECKIN_DATA", async () => {
      await db.collection("attendances").doc(docId(UID.corruptEmp)).set({
        userId: UID.corruptEmp,
        companyId: COMPANY_A,
        sedeId: SEDE_A,
        date: DATE_DDMMYYYY,
        checkIn: "08:XX",
        checkOut: "",
        status: "PUNTUAL",
      });
      await expectError(callAs(UID.corruptEmp, basePayload()), "CORRUPT_CHECKIN_DATA");
    });
  });

  // -------------------------------------------------------------------------
  describe("Idempotencia", () => {
    test("requestId reusado por otro uid → REQUEST_ID_USED", async () => {
      const requestId = randomUUID();
      await callAs(UID.empA1, basePayload({ requestId }));
      await expectError(callAs(UID.empA2, basePayload({ requestId })), "REQUEST_ID_USED");
    });

    test("mismo requestId con otro GPS devuelve lo guardado (sin reevaluar geocerca)", async () => {
      const requestId = randomUUID();
      const first = await callAs(UID.empA1, basePayload({ requestId }));
      const replay = await callAs(UID.empA1, basePayload({ requestId, lat: 0, lng: 0 }));
      expect(replay).toEqual(first);
      const all = await db.collection("attendances").get();
      expect(all.size).toBe(1);
    });

    test("mismo requestId con otro qrToken → REQUEST_ID_MISMATCH", async () => {
      const requestId = randomUUID();
      await callAs(UID.adminA, basePayload({ tipo: "QR", qrToken: UID.empA1, requestId }));
      await expectError(
        callAs(UID.adminA, basePayload({ tipo: "QR", qrToken: UID.empA2, requestId })),
        "REQUEST_ID_MISMATCH"
      );
    });
  });

  // -------------------------------------------------------------------------
  describe("Configuración corrupta de sede/turno/empresa", () => {
    test("sede con lat null → INVALID_SEDE_CONFIG", async () => {
      await db.doc(`companies/${COMPANY_A}/sedes/${SEDE_A}`).update({ lat: null });
      await expectError(callAs(UID.empA1, basePayload()), "INVALID_SEDE_CONFIG");
    });

    test("sede con lat '' → INVALID_SEDE_CONFIG", async () => {
      await db.doc(`companies/${COMPANY_A}/sedes/${SEDE_A}`).update({ lat: "" });
      await expectError(callAs(UID.empA1, basePayload()), "INVALID_SEDE_CONFIG");
    });

    test("shiftId vacío → NO_SHIFT_ASSIGNED", async () => {
      await expectError(callAs(UID.noShift, basePayload()), "NO_SHIFT_ASSIGNED");
    });

    test("shiftId inexistente → NO_SHIFT_ASSIGNED", async () => {
      await expectError(callAs(UID.shiftMissing, basePayload()), "NO_SHIFT_ASSIGNED");
    });

    test("toleranceMin null → INVALID_SHIFT_CONFIG", async () => {
      await db.doc(`companies/${COMPANY_A}/shifts/${SHIFT_A}`).update({ toleranceMin: null });
      await expectError(callAs(UID.empA1, basePayload()), "INVALID_SHIFT_CONFIG");
    });

    test("timezone ausente → INVALID_TIMEZONE", async () => {
      await db.collection("companies").doc(COMPANY_A).update({
        timezone: admin.firestore.FieldValue.delete(),
      });
      await expectError(callAs(UID.empA1, basePayload()), "INVALID_TIMEZONE");
    });
  });

  // -------------------------------------------------------------------------
  describe("Concurrencia", () => {
    test("50 llamadas con DISTINTO requestId: exactamente 1 doc", async () => {
      const results = await Promise.allSettled(
        Array.from({ length: 50 }, () => callAs(UID.empA1, basePayload()))
      );

      const counts: Record<string, number> = {};
      for (const r of results) {
        if (r.status === "fulfilled") {
          const key = `OK:${r.value?.type ?? "?"}/${r.value?.status ?? "?"}`;
          counts[key] = (counts[key] || 0) + 1;
        } else {
          const code = (r.reason as any)?.details?.code ?? "SIN_CODIGO";
          counts[`ERR:${code}`] = (counts[`ERR:${code}`] || 0) + 1;
        }
      }
      // Reporte literal de los conteos
      console.log("[50 DISTINTO requestId] " + JSON.stringify(counts));

      const all = await db.collection("attendances").get();
      expect(all.size).toBe(1);

      const success = results.filter(
        (r) => r.status === "fulfilled" && r.value?.type === "CHECK_IN"
      ).length;
      expect(success).toBeGreaterThanOrEqual(1);

      const allowedErrCodes = ["TOO_SOON", "ALREADY_COMPLETE", "RETRY"];
      for (const r of results) {
        if (r.status === "rejected") {
          expect(allowedErrCodes).toContain((r.reason as any)?.details?.code);
        } else {
          expect(["CHECK_IN", "CHECK_OUT"]).toContain(r.value?.type);
        }
      }
    });

    test("50 llamadas con el MISMO requestId: 1 doc", async () => {
      const requestId = randomUUID();
      const results = await Promise.allSettled(
        Array.from({ length: 50 }, () => callAs(UID.empA1, basePayload({ requestId })))
      );

      const counts: Record<string, number> = {};
      for (const r of results) {
        if (r.status === "fulfilled") {
          counts["OK"] = (counts["OK"] || 0) + 1;
        } else {
          const code = (r.reason as any)?.details?.code ?? "SIN_CODIGO";
          counts[`ERR:${code}`] = (counts[`ERR:${code}`] || 0) + 1;
        }
      }
      console.log("[50 MISMO requestId] " + JSON.stringify(counts));

      const all = await db.collection("attendances").get();
      expect(all.size).toBe(1);

      const fulfilled = results.filter((r) => r.status === "fulfilled") as PromiseFulfilledResult<any>[];
      expect(fulfilled.length).toBeGreaterThanOrEqual(1);
      const first = fulfilled[0].value;
      for (const r of fulfilled) {
        expect(r.value).toEqual(first);
      }
    });
  });
});
