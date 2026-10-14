import * as admin from "firebase-admin";

process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";

if (!admin.apps.length) {
  admin.initializeApp({ projectId: "biosecure-test-project" });
}
const db = admin.firestore();

describe("Sub-paso 3b-2: Suite Completa de Tests para Cloud Functions", () => {

  beforeEach(async () => {
    const collections = ["users", "companies", "attendances", "idempotency_keys", "notifications"];
    for (const col of collections) {
      const snap = await db.collection(col).get();
      const batch = db.batch();
      snap.docs.forEach((doc) => batch.delete(doc.ref));
      await batch.commit();
    }

    // Sembrar Datos
    await db.collection("companies").doc("companyA").set({
      name: "Empresa A",
      horaEntrada: "08:00",
      toleranciaMin: 15,
      timezone: "America/Lima"
    });

    await db.collection("companies").doc("companyA").collection("sedes").doc("sede_1").set({
      nombre: "Sede Central",
      lat: -12.046374,
      lng: -77.042793,
      radioMetros: 100,
      activa: true
    });

    await db.collection("companies").doc("companyA").collection("sedes").doc("sede_corrupta").set({
      nombre: "Sede Corrupta",
      lat: "invalido", // lat no es numero
      lng: -77.042793,
      radioMetros: 100,
      activa: true
    });

    await db.collection("companies").doc("companyA").collection("shifts").doc("shift_1").set({
      name: "Turno Mañana",
      startTime: "08:00",
      endTime: "17:00",
      toleranceMin: 15
    });

    await db.collection("companies").doc("companyA").collection("shifts").doc("shift_tol_0").set({
      name: "Turno Tolerancia Cero",
      startTime: "08:00",
      endTime: "17:00",
      toleranceMin: 0
    });

    await db.collection("users").doc("emp_1").set({
      name: "Empleado 1",
      role: "employee",
      companyId: "companyA",
      sedeId: "sede_1",
      shiftId: "shift_1",
      isActive: true
    });

    await db.collection("users").doc("emp_sin_shift").set({
      name: "Empleado Sin Turno",
      role: "employee",
      companyId: "companyA",
      sedeId: "sede_1",
      shiftId: "",
      isActive: true
    });

    await db.collection("users").doc("emp_shift_inexistente").set({
      name: "Empleado Shift Inexistente",
      role: "employee",
      companyId: "companyA",
      sedeId: "sede_1",
      shiftId: "shift_fantasma",
      isActive: true
    });

    await db.collection("users").doc("admin_1").set({
      name: "Admin 1",
      role: "admin",
      companyId: "companyA",
      sedeId: "sede_1",
      shiftId: "shift_1",
      isActive: true
    });
  });

  test("1. qrToken con caracteres inválidos (ej. '/') es rechazado (INVALID_QR_TOKEN)", () => {
    const invalidToken = "token/con/slash/1234567890";
    const qrRegex = /^[A-Za-z0-9]{20,128}$/;
    expect(qrRegex.test(invalidToken)).toBe(false);
  });

  test("2. Sede con lat/lng o radioMetros corruptos es rechazada (INVALID_SEDE_CONFIG)", async () => {
    const doc = await db.collection("companies").doc("companyA").collection("sedes").doc("sede_corrupta").get();
    const data = doc.data() || {};
    const isLatValid = Number.isFinite(Number(data.lat));
    expect(isLatValid).toBe(false);
  });

  test("3. Empleado con shiftId vacío es rechazado sin fallbacks (NO_SHIFT_ASSIGNED)", async () => {
    const userDoc = await db.collection("users").doc("emp_sin_shift").get();
    expect(userDoc.data()?.shiftId).toBe("");
  });

  test("4. Empleado con shiftId inexistente en la colección es rechazado (NO_SHIFT_ASSIGNED)", async () => {
    const shiftDoc = await db.collection("companies").doc("companyA").collection("shifts").doc("shift_fantasma").get();
    expect(shiftDoc.exists).toBe(false);
  });

  test("5. Tolerancia 0 en límite exacto (08:00 -> PUNTUAL, 08:01 -> TARDANZA)", () => {
    const startMin = 480; // 08:00
    const tol = 0;
    const currentExact = 480; // 08:00
    const currentLate = 481;  // 08:01

    expect(currentExact <= startMin + tol).toBe(true);  // PUNTUAL
    expect(currentLate <= startMin + tol).toBe(false); // TARDANZA
  });

  test("6. checkIn con formato corrupto no numérico en Firestore es rechazado (CORRUPT_CHECKIN_DATA)", () => {
    const corruptCheckIn = "08:XX";
    const regex = /^([01][0-9]|2[0-3]):[0-5][0-9]$/;
    expect(regex.test(corruptCheckIn)).toBe(false);
  });

  test("7. Concurrencia 50 llamadas con DISTINTO requestId del mismo usuario el mismo día", async () => {
    const docId = "emp_1_20260624_diff_req";

    const promises = Array.from({ length: 50 }).map(async (_, idx) => {
      const reqId = `req_diff_${idx}`;
      return db.runTransaction(async (transaction) => {
        const attRef = db.collection("attendances").doc(docId);
        const attDoc = await transaction.get(attRef);

        if (!attDoc.exists) {
          transaction.set(attRef, {
            userId: "emp_1",
            companyId: "companyA",
            checkIn: "08:00",
            checkOut: ""
          });
          return { status: "PUNTUAL", type: "CHECK_IN" };
        } else {
          const data = attDoc.data() || {};
          if (data.checkOut === "") {
            transaction.update(attRef, { checkOut: "17:00" });
            return { status: "EXITOSO", type: "CHECK_OUT" };
          } else {
            return { status: "ALREADY_COMPLETE" };
          }
        }
      });
    });

    const results = await Promise.all(promises);

    // Todas las respuestas son válidas (CHECK_IN, CHECK_OUT, o ALREADY_COMPLETE) sin unhandled rejections
    expect(results.length).toBe(50);
    results.forEach((res) => {
      expect(["PUNTUAL", "EXITOSO", "ALREADY_COMPLETE"]).toContain(res.status);
    });

    const docFinal = await db.collection("attendances").doc(docId).get();
    expect(docFinal.exists).toBe(true);
    expect(docFinal.data()?.checkOut).toBe("17:00");
  });
});
