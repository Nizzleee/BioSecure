import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineInt } from "firebase-functions/params";
import * as admin from "firebase-admin";
import { createHash } from "crypto";
import { calculateHaversineDistance } from "./logic/haversine";
import { formatServerTime, hhMmToMinutes } from "./logic/timeUtils";
import { calculateAttendanceStatus, ShiftConfig, AttendanceLogicError } from "./logic/attendanceCalculator";

admin.initializeApp();
const db = admin.firestore();

// --- PARAMETRIZACIÓN CON DEFINES DE FIREBASE FUNCTIONS PARAMS (Sin literales) ---
const paramMaxAccuracyMeters = defineInt("MAX_ACCURACY_METERS", { default: 50 });
const paramEarlyCheckinMinutes = defineInt("EARLY_CHECKIN_MINUTES", { default: 60 });
const paramMinMinutesBeforeCheckout = defineInt("MIN_MINUTES_BEFORE_CHECKOUT", { default: 5 });
const paramMaxCheckoutOvertimeMinutes = defineInt("MAX_CHECKOUT_OVERTIME_MINUTES", { default: 120 });
const paramIdempotencyTtlDays = defineInt("IDEMPOTENCY_TTL_DAYS", { default: 7 });

export interface RegisterAttendancePayload {
  tipo?: "HUELLA" | "FACIAL" | "QR";
  qrToken?: string; // Token opaco resuelto a effectiveUid
  lat: number;
  lng: number;
  accuracy: number;
  isMock?: boolean; // Nota: clientMockFlag se guarda en el documento como señal de telemetría no confiable
  requestId: string;
}

/**
 * Cloud Function v2 'registerAttendance': Única vía autorizada de escritura de asistencias.
 */
export const registerAttendance = onCall(
  { region: "us-central1" },
  async (request) => {
    // 1. Autenticación del Solicitante
    const auth = request.auth;
    if (!auth || !auth.uid) {
      throw new HttpsError("unauthenticated", "Usuario no autenticado", { code: "UNAUTHENTICATED" });
    }
    const callerUid = auth.uid;

    const data = request.data as RegisterAttendancePayload;
    if (!data) {
      throw new HttpsError("invalid-argument", "Payload ausente", { code: "INVALID_ARGUMENT" });
    }

    // 2. Validación de Tipo de Marcación
    if (!data.tipo || !["HUELLA", "FACIAL", "QR"].includes(data.tipo)) {
      throw new HttpsError("invalid-argument", "Tipo de marcación inválido o ausente", { code: "INVALID_TYPE" });
    }

    // 3. Validación de Coordenadas y Accuracy
    if (!Number.isFinite(data.lat) || data.lat < -90.0 || data.lat > 90.0 ||
        !Number.isFinite(data.lng) || data.lng < -180.0 || data.lng > 180.0) {
      throw new HttpsError("invalid-argument", "Coordenadas lat/lng fuera de rango válido", { code: "INVALID_COORDINATES" });
    }

    if (!Number.isFinite(data.accuracy) || data.accuracy < 0) {
      throw new HttpsError("invalid-argument", "Precisión de GPS inválida", { code: "INVALID_ACCURACY" });
    }

    const maxAccuracy = paramMaxAccuracyMeters.value();
    if (data.accuracy > maxAccuracy) {
      throw new HttpsError("invalid-argument", `Accuracy excesivamente alta (${data.accuracy.toFixed(1)}m > ${maxAccuracy}m)`, { code: "ACCURACY_TOO_HIGH" });
    }

    // 4. Validación de requestId con Regex
    const requestId = (data.requestId || "").trim();
    const requestIdRegex = /^[A-Za-z0-9-]{8,64}$/;
    if (!requestIdRegex.test(requestId)) {
      throw new HttpsError("invalid-argument", "requestId con formato inválido", { code: "INVALID_REQUEST_ID" });
    }

    // 5. Validación Estricta de qrToken (si está presente)
    const qrToken = (data.qrToken || "").trim();
    if (qrToken.length > 0) {
      const qrRegex = /^[A-Za-z0-9]{20,128}$/;
      if (!qrRegex.test(qrToken)) {
        throw new HttpsError("invalid-argument", "INVALID_QR_TOKEN: Formato de token QR inválido", { code: "INVALID_QR_TOKEN" });
      }
    }

    const effectiveUid = qrToken ? qrToken : callerUid;

    // Cálculo de Payload Hash para Idempotencia (Sin lat/lng)
    const payloadString = `${effectiveUid}|${data.tipo}`;
    const payloadHash = createHash("sha256").update(payloadString).digest("hex");

    let logCompanyId = "";

    // --- TRANSACCIÓN FIRESTORE ATÓMICA ---
    const resultPayload = await db.runTransaction(async (transaction) => {

      // PASO 1: LECTURAS FIRESTORE DENTRO DE LA TRANSACCIÓN

      // 1.1 Idempotencia por requestId y payloadHash
      const idempotencyRef = db.collection("idempotency_keys").doc(requestId);
      const idempotencyDoc = await transaction.get(idempotencyRef);

      if (idempotencyDoc.exists) {
        const idData = idempotencyDoc.data();
        if (idData?.uid !== callerUid) {
          throw new HttpsError("permission-denied", "requestId ya fue utilizado por otro usuario", { code: "REQUEST_ID_USED" });
        }
        if (idData?.payloadHash && idData.payloadHash !== payloadHash) {
          throw new HttpsError("failed-precondition", "requestId reutilizado con un payload diferente", { code: "REQUEST_ID_MISMATCH" });
        }
        return idData?.response;
      }

      // 1.2 Lectura y Validación Perfil Solicitante (callerUid)
      const callerRef = db.collection("users").doc(callerUid);
      const callerDoc = await transaction.get(callerRef);
      if (!callerDoc.exists) {
        throw new HttpsError("permission-denied", "Perfil del solicitante no existe", { code: "CALLER_NOT_FOUND" });
      }
      const callerData = callerDoc.data() || {};
      if (callerData.isActive === false) {
        throw new HttpsError("permission-denied", "El perfil del solicitante se encuentra inactivo", { code: "CALLER_INACTIVE" });
      }
      const callerRole = callerData.role || "employee";
      const callerCompanyId = callerData.companyId || "";

      // 1.3 Regla Estricta: Administrador NUNCA puede marcar asistencia para sí mismo
      if (effectiveUid === callerUid && callerRole === "admin") {
        throw new HttpsError("permission-denied", "ADMIN_CANNOT_MARK_SELF: Los administradores no marcan asistencia", { code: "ADMIN_CANNOT_MARK_SELF" });
      }

      // 1.4 Lectura Perfil Empleado Objetivo (effectiveUid)
      let targetData = callerData;
      if (effectiveUid !== callerUid) {
        const targetRef = db.collection("users").doc(effectiveUid);
        const targetDoc = await transaction.get(targetRef);
        if (!targetDoc.exists) {
          throw new HttpsError("not-found", "El perfil del empleado objetivo no existe", { code: "TARGET_NOT_FOUND" });
        }
        targetData = targetDoc.data() || {};
      }

      // Regla Estricta: El objetivo DEBE ser ROL EMPLEADO
      if (targetData.role !== "employee") {
        throw new HttpsError("permission-denied", "TARGET_NOT_EMPLOYEE: El objetivo debe ser un empleado", { code: "TARGET_NOT_EMPLOYEE" });
      }

      if (targetData.isActive === false) {
        throw new HttpsError("failed-precondition", "El empleado se encuentra inactivo", { code: "EMPLOYEE_INACTIVE" });
      }

      const companyId = targetData.companyId || "";
      if (!companyId) {
        throw new HttpsError("failed-precondition", "El empleado no tiene empresa asignada", { code: "NO_COMPANY_ASSIGNED" });
      }
      logCompanyId = companyId;

      // 1.5 Autorización y Tipo de Marcación
      let determinedType: "HUELLA" | "FACIAL" | "QR";

      if (effectiveUid === callerUid) {
        if (data.tipo === "QR") {
          throw new HttpsError("invalid-argument", "Tipo QR no permitido para automarcación", { code: "INVALID_SELF_CHECKIN_TYPE" });
        }
        if (data.tipo !== "HUELLA" && data.tipo !== "FACIAL") {
          throw new HttpsError("invalid-argument", "Automarcación requiere tipo HUELLA o FACIAL", { code: "INVALID_TYPE" });
        }
        determinedType = data.tipo;
      } else {
        if (callerRole !== "admin") {
          throw new HttpsError("permission-denied", "Solo un administrador puede escanear a un empleado", { code: "ADMIN_ONLY" });
        }
        if (callerCompanyId !== companyId) {
          throw new HttpsError("permission-denied", "No tienes permisos sobre empleados de otra empresa", { code: "COMPANY_MISMATCH" });
        }
        determinedType = "QR";
      }

      // 1.6 Lectura Empresa (Sin Fallback)
      const companyRef = db.collection("companies").doc(companyId);
      const companyDoc = await transaction.get(companyRef);
      if (!companyDoc.exists) {
        throw new HttpsError("failed-precondition", "La empresa asignada no existe", { code: "COMPANY_NOT_FOUND" });
      }
      const companyData = companyDoc.data() || {};
      const timezone = companyData.timezone;
      if (typeof timezone !== "string" || timezone.trim() === "") {
        throw new HttpsError("failed-precondition", "INVALID_TIMEZONE: La empresa no tiene una zona horaria válida configurada", { code: "INVALID_TIMEZONE" });
      }

      // 1.7 Lectura Sede Asignada y Validación Estricta
      const sedeId = targetData.sedeId || "";
      if (!sedeId) {
        throw new HttpsError("failed-precondition", "FAILED_PRECONDITION: El empleado no tiene sede asignada", { code: "NO_SEDE_ASSIGNED" });
      }
      const sedeRef = db.collection("companies").doc(companyId).collection("sedes").doc(sedeId);
      const sedeDoc = await transaction.get(sedeRef);
      if (!sedeDoc.exists) {
        throw new HttpsError("failed-precondition", "INVALID_SEDE_CONFIG: La sede asignada no existe", { code: "INVALID_SEDE_CONFIG" });
      }
      const sedeData = sedeDoc.data() || {};
      if (sedeData.activa === false) {
        throw new HttpsError("failed-precondition", "INVALID_SEDE_CONFIG: La sede asignada se encuentra inactiva", { code: "INVALID_SEDE_CONFIG" });
      }

      if (typeof sedeData.lat !== "number" || !Number.isFinite(sedeData.lat) ||
          typeof sedeData.lng !== "number" || !Number.isFinite(sedeData.lng) ||
          typeof sedeData.radioMetros !== "number" || !Number.isFinite(sedeData.radioMetros)) {
        throw new HttpsError("failed-precondition", "INVALID_SEDE_CONFIG: Configuración geográfica de la sede corrupta", { code: "INVALID_SEDE_CONFIG" });
      }

      const sedeLat = sedeData.lat;
      const sedeLng = sedeData.lng;
      const rawRadio = sedeData.radioMetros;

      if (rawRadio < 20 || rawRadio > 500) {
        throw new HttpsError("failed-precondition", "INVALID_SEDE_CONFIG: radioMetros fuera de rango permitido (20-500m)", { code: "INVALID_SEDE_CONFIG" });
      }
      const radioMetros = rawRadio;

      // 1.8 Lectura Estricta de Turno (SIN FALLBACKS a 08:00-17:00 ni a horaEntrada de empresa)
      const shiftId = targetData.shiftId || "";
      if (!shiftId) {
        throw new HttpsError("failed-precondition", "NO_SHIFT_ASSIGNED: El empleado no tiene turno asignado", { code: "NO_SHIFT_ASSIGNED" });
      }

      const shiftRef = db.collection("companies").doc(companyId).collection("shifts").doc(shiftId);
      const shiftDoc = await transaction.get(shiftRef);
      if (!shiftDoc.exists) {
        throw new HttpsError("failed-precondition", "NO_SHIFT_ASSIGNED: El documento del turno asignado no existe", { code: "NO_SHIFT_ASSIGNED" });
      }

      const sData = shiftDoc.data() || {};
      const shiftStartTime = sData.startTime;
      const shiftEndTime = sData.endTime;
      const shiftTolerance = sData.toleranceMin;

      if (typeof shiftStartTime !== "string" || typeof shiftEndTime !== "string" ||
          typeof shiftTolerance !== "number" || !Number.isFinite(shiftTolerance)) {
        throw new HttpsError("failed-precondition", "INVALID_SHIFT_CONFIG: Configuración de turno corrupta", { code: "INVALID_SHIFT_CONFIG" });
      }

      const trimmedStart = shiftStartTime.trim();
      const trimmedEnd = shiftEndTime.trim();

      const timeRegex = /^([01][0-9]|2[0-3]):[0-5][0-9]$/;
      if (!timeRegex.test(trimmedStart) || !timeRegex.test(trimmedEnd)) {
        throw new HttpsError("failed-precondition", "INVALID_SHIFT_CONFIG: Configuración de horario de turno con formato inválido (esperado HH:mm)", { code: "INVALID_SHIFT_CONFIG" });
      }

      let startMinutes: number;
      let endMinutes: number;
      try {
        startMinutes = hhMmToMinutes(trimmedStart);
        endMinutes = hhMmToMinutes(trimmedEnd);
      } catch (e) {
        throw new HttpsError("failed-precondition", "INVALID_SHIFT_CONFIG: Configuración de horario de turno corrupta", { code: "INVALID_SHIFT_CONFIG" });
      }

      if (endMinutes <= startMinutes) {
        throw new HttpsError("failed-precondition", "UNSUPPORTED_SHIFT: Turnos nocturnos o de duración cero no están soportados", { code: "UNSUPPORTED_SHIFT" });
      }

      const shiftConfig: ShiftConfig = {
        startTime: trimmedStart,
        endTime: trimmedEnd,
        toleranceMin: shiftTolerance
      };

      // 1.9 Fecha y Hora Servidor
      let formattedTime;
      try {
        formattedTime = formatServerTime(new Date(), timezone);
      } catch (e: any) {
        const msg = String(e?.message || "");
        if (msg.includes("INVALID_TIMEZONE")) {
          throw new HttpsError("failed-precondition", "INVALID_TIMEZONE: Zona horaria inválida en la configuración de la empresa", { code: "INVALID_TIMEZONE" });
        }
        throw new HttpsError("internal", "Error interno al procesar hora del servidor");
      }

      const dateYyyyMmDd = formattedTime.dateYyyyMmDd;
      const currentHhMm = formattedTime.timeHhMm;

      // 1.10 Asistencia del Día (docId = {effectiveUid}_{yyyyMMdd})
      const docId = `${effectiveUid}_${dateYyyyMmDd}`;
      const attendanceRef = db.collection("attendances").doc(docId);
      const attendanceDoc = await transaction.get(attendanceRef);

      // PASO 2: VALIDACIÓN DE GEOCERCA (Sin sumar accuracy al radio)
      const distance = calculateHaversineDistance(data.lat, data.lng, sedeLat, sedeLng);

      if (distance > radioMetros) {
        throw new HttpsError(
          "failed-precondition",
          `OUT_OF_RANGE_GEOFENCE: Distancia ${distance.toFixed(1)}m supera el radio de la sede (${radioMetros}m)`,
          { code: "OUT_OF_RANGE_GEOFENCE" }
        );
      }

      let responsePayload: any = {};

      if (!attendanceDoc.exists) {
        // --- CASO CHECK-IN ---
        let finalStatus: string;
        try {
          finalStatus = calculateAttendanceStatus(currentHhMm, shiftConfig, paramEarlyCheckinMinutes.value());
        } catch (e: any) {
          if (e instanceof AttendanceLogicError) {
            if (e.code === "UNSUPPORTED_SHIFT") {
              throw new HttpsError("failed-precondition", "UNSUPPORTED_SHIFT: Turnos nocturnos o de duración cero no están soportados", { code: "UNSUPPORTED_SHIFT" });
            }
            if (e.code === "OUT_OF_WINDOW") {
              throw new HttpsError("failed-precondition", "OUT_OF_SCHEDULE_WINDOW: Marcación fuera de la ventana horaria del turno", { code: "OUT_OF_SCHEDULE_WINDOW" });
            }
          }
          throw new HttpsError("internal", "Error interno al calcular estado de asistencia");
        }

        const newAttendanceData = {
          userId: effectiveUid,
          companyId: companyId,
          sedeId: sedeId,
          userName: targetData.name || "Empleado",
          date: `${dateYyyyMmDd.substring(6,8)}/${dateYyyyMmDd.substring(4,6)}/${dateYyyyMmDd.substring(0,4)}`,
          checkIn: currentHhMm,
          checkOut: "",
          status: finalStatus,
          type: determinedType,
          location: sedeData.nombre || "Sede",
          latitude: data.lat,
          longitude: data.lng,
          accuracy: data.accuracy,
          distanceMeters: Math.round(distance),
          requestId: requestId,
          marcadoPor: callerUid,
          // clientMockFlag es recibido del cliente únicamente para registro/telemetría (no es 100% confiable)
          clientMockFlag: Boolean(data.isMock),
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          updatedAt: admin.firestore.FieldValue.serverTimestamp()
        };

        // PASO 3: ESCRITURAS DE TRANSACCIÓN
        transaction.set(attendanceRef, newAttendanceData);

        // Notificación Servidor (Solo en CheckIn para informar la llegada)
        const notifRef = db.collection("notifications").doc();
        transaction.set(notifRef, {
          companyId: companyId,
          type: "attendance",
          userName: targetData.name || "Empleado",
          checkIn: currentHhMm,
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
          read: false
        });

        responsePayload = {
          status: finalStatus,
          checkIn: currentHhMm,
          date: newAttendanceData.date,
          userName: targetData.name || "Empleado",
          type: "CHECK_IN"
        };

      } else {
        // --- CASO CHECK-OUT ---
        const existingData = attendanceDoc.data() || {};
        if (existingData.checkOut && existingData.checkOut !== "") {
          throw new HttpsError("failed-precondition", "ALREADY_COMPLETE: Entrada y salida ya registradas para hoy", { code: "ALREADY_COMPLETE" });
        }

        if (typeof existingData.status !== "string" || !existingData.status) {
          throw new HttpsError("failed-precondition", "CORRUPT_CHECKIN_DATA: Registro de asistencia no contiene un estado válido", { code: "CORRUPT_CHECKIN_DATA" });
        }

        let checkInMinutes: number;
        try {
          checkInMinutes = hhMmToMinutes(existingData.checkIn);
        } catch (e) {
          throw new HttpsError("failed-precondition", "CORRUPT_CHECKIN_DATA: Formato de hora en checkIn corrupto", { code: "CORRUPT_CHECKIN_DATA" });
        }

        const currentMinutes = formattedTime.minutesFromMidnight;
        const minCheckoutTime = checkInMinutes + paramMinMinutesBeforeCheckout.value();

        if (currentMinutes < minCheckoutTime) {
          throw new HttpsError(
            "failed-precondition",
            `TOO_SOON: Deben transcurrir al menos ${paramMinMinutesBeforeCheckout.value()} minutos desde el checkIn para registrar salida`,
            { code: "TOO_SOON" }
          );
        }

        // Validación de tope superior de hora para checkOut (No más allá del fin de turno + horas extra permitidas)
        let shiftEndMinutes: number;
        try {
          shiftEndMinutes = hhMmToMinutes(shiftConfig.endTime);
        } catch (e) {
          throw new HttpsError("failed-precondition", "INVALID_SHIFT_CONFIG: Configuración del fin de turno corrupta", { code: "INVALID_SHIFT_CONFIG" });
        }

        const maxCheckoutAllowedMinutes = shiftEndMinutes + paramMaxCheckoutOvertimeMinutes.value();
        if (currentMinutes > maxCheckoutAllowedMinutes) {
          throw new HttpsError(
            "failed-precondition",
            `OUT_OF_WINDOW: Hora de salida excede el tiempo límite permitido de horas extra`,
            { code: "OUT_OF_WINDOW" }
          );
        }

        transaction.update(attendanceRef, {
          checkOut: currentHhMm,
          updatedAt: admin.firestore.FieldValue.serverTimestamp()
        });

        responsePayload = {
          status: existingData.status,
          checkIn: existingData.checkIn,
          checkOut: currentHhMm,
          date: existingData.date,
          userName: targetData.name || "Empleado",
          type: "CHECK_OUT"
        };
      }

      // Guardar Idempotencia con Timestamp explícito y payloadHash
      const expiresAtDate = new Date();
      expiresAtDate.setDate(expiresAtDate.getDate() + paramIdempotencyTtlDays.value());
      const expiresAtTimestamp = admin.firestore.Timestamp.fromDate(expiresAtDate);

      transaction.set(idempotencyRef, {
        uid: callerUid,
        payloadHash: payloadHash,
        response: responsePayload,
        expiresAt: expiresAtTimestamp,
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });

      return responsePayload;
    });

    console.log(JSON.stringify({
      event: "ATTENDANCE_PROCESSED",
      companyId: logCompanyId,
      type: resultPayload.type,
      status: resultPayload.status,
      requestId: requestId
    }));

    return resultPayload;
  }
);

export const registerManualAttendance = onCall(
  { region: "us-central1" },
  async (request) => {
    const auth = request.auth;
    if (!auth || !auth.uid) {
      throw new HttpsError("unauthenticated", "Usuario no autenticado", { code: "UNAUTHENTICATED" });
    }
    throw new HttpsError("unimplemented", "registerManualAttendance aún no está implementada", { code: "UNIMPLEMENTED" });
  }
);
