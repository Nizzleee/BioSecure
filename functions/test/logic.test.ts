import { calculateHaversineDistance } from "../src/logic/haversine";
import { formatServerTime, hhMmToMinutes } from "../src/logic/timeUtils";
import { calculateAttendanceStatus, ShiftConfig } from "../src/logic/attendanceCalculator";

describe("Sub-paso 3b-1: Tests Unitarios de Lógica Pura (Haversine, Tiempo y Estado)", () => {

  describe("1. Cálculo de Distancia Haversine", () => {
    test("Distancia entre el mismo punto debe ser 0 metros", () => {
      const dist = calculateHaversineDistance(-12.046374, -77.042793, -12.046374, -77.042793);
      expect(dist).toBeCloseTo(0, 1);
    });

    test("Distancia conocida: Plaza Mayor de Lima a Parque de la Reserva (~2.5 km)", () => {
      // Plaza Mayor: -12.046374, -77.042793
      // Parque de la Reserva: -12.069123, -77.035312
      const dist = calculateHaversineDistance(-12.046374, -77.042793, -12.069123, -77.035312);
      expect(dist).toBeGreaterThan(2400);
      expect(dist).toBeLessThan(2700);
    });

    test("Distancia corta (~50 metros)", () => {
      const lat1 = -12.046374;
      const lng1 = -77.042793;
      const lat2 = -12.046824; // ~50m al sur
      const lng2 = -77.042793;
      const dist = calculateHaversineDistance(lat1, lng1, lat2, lng2);
      expect(dist).toBeGreaterThan(45);
      expect(dist).toBeLessThan(55);
    });

    // --- Nuevos tests de validación Haversine ---
    test("lat NaN lanza error", () => {
      expect(() => calculateHaversineDistance(NaN, -77.042793, -12.046374, -77.042793)).toThrow();
    });

    test("lng NaN lanza error", () => {
      expect(() => calculateHaversineDistance(-12.046374, NaN, -12.046374, -77.042793)).toThrow();
    });

    test("lat Infinity lanza error", () => {
      expect(() => calculateHaversineDistance(Infinity, -77.042793, -12.046374, -77.042793)).toThrow();
    });

    test("lng -Infinity lanza error", () => {
      expect(() => calculateHaversineDistance(-12.046374, -Infinity, -12.046374, -77.042793)).toThrow();
    });

    test("lat fuera de rango (>90) lanza error", () => {
      expect(() => calculateHaversineDistance(91, -77.042793, -12.046374, -77.042793)).toThrow();
    });

    test("lat fuera de rango (<-90) lanza error", () => {
      expect(() => calculateHaversineDistance(-91, -77.042793, -12.046374, -77.042793)).toThrow();
    });

    test("lng fuera de rango (>180) lanza error", () => {
      expect(() => calculateHaversineDistance(-12.046374, 181, -12.046374, -77.042793)).toThrow();
    });

    test("lng fuera de rango (<-180) lanza error", () => {
      expect(() => calculateHaversineDistance(-12.046374, -181, -12.046374, -77.042793)).toThrow();
    });
  });

  describe("2. Utilidades de Fecha y Hora (timeUtils)", () => {
    test("Conversión hhMmToMinutes", () => {
      expect(hhMmToMinutes("00:00")).toBe(0);
      expect(hhMmToMinutes("08:15")).toBe(495); // 8*60 + 15
      expect(hhMmToMinutes("23:59")).toBe(1439);
    });

    test("Formato de fecha y hora del servidor para timezone America/Lima", () => {
      const fixedDate = new Date("2026-06-24T13:15:00.000Z"); // 13:15 UTC -> 08:15 Lima (UTC-5)
      const formatted = formatServerTime(fixedDate, "America/Lima");

      expect(formatted.dateYyyyMmDd).toBe("20260624");
      expect(formatted.timeHhMm).toBe("08:15");
      expect(formatted.minutesFromMidnight).toBe(495);
    });

    // --- Nuevos tests timeUtils ---
    test("timezone inválida lanza error controlado (no RangeError crudo)", () => {
      const fixedDate = new Date("2026-06-24T13:15:00.000Z");
      expect(() => formatServerTime(fixedDate, "Invalid/Timezone")).toThrow();
    });

    test("hhMmToMinutes: marcación a 00:00 devuelve 0 (nunca 24)", () => {
      expect(hhMmToMinutes("00:00")).toBe(0);
    });

    test("hhMmToMinutes: marcación a 00:30 devuelve 30 (nunca 24:30)", () => {
      expect(hhMmToMinutes("00:30")).toBe(30);
    });

    test("hhMmToMinutes: formato inválido '8:00' (falta cero) lanza error", () => {
      expect(() => hhMmToMinutes("8:00")).toThrow();
    });

    test("hhMmToMinutes: formato inválido '24:00' lanza error", () => {
      expect(() => hhMmToMinutes("24:00")).toThrow();
    });

    test("hhMmToMinutes: formato inválido 'ab:cd' lanza error", () => {
      expect(() => hhMmToMinutes("ab:cd")).toThrow();
    });
  });

  describe("3. Cálculo de Estado de Asistencia (PUNTUAL, TARDANZA, FALLIDO)", () => {
    const standardShift: ShiftConfig = {
      startTime: "08:00",
      endTime: "17:00",
      toleranceMin: 15,
    };

    test("Marcación dentro del horario + tolerancia es PUNTUAL (08:10)", () => {
      const status = calculateAttendanceStatus("08:10", standardShift);
      expect(status).toBe("PUNTUAL");
    });

    test("Marcación en la tolerancia límite exacta es PUNTUAL (08:15)", () => {
      const status = calculateAttendanceStatus("08:15", standardShift);
      expect(status).toBe("PUNTUAL");
    });

    test("Marcación posterior a la tolerancia es TARDANZA (08:16)", () => {
      const status = calculateAttendanceStatus("08:16", standardShift);
      expect(status).toBe("TARDANZA");
    });

    test("Marcación dentro del turno pero tarde es TARDANZA (12:00)", () => {
      const status = calculateAttendanceStatus("12:00", standardShift);
      expect(status).toBe("TARDANZA");
    });

    // --- Tests reescritos: ahora afirman rechazo (throw) ---
    test("Marcación después del fin de turno lanza OUT_OF_WINDOW (17:01)", () => {
      expect(() => calculateAttendanceStatus("17:01", standardShift)).toThrow(/OUT_OF_WINDOW/);
    });

    test("Marcación con más de 1 hora de anticipación lanza OUT_OF_WINDOW (06:50)", () => {
      expect(() => calculateAttendanceStatus("06:50", standardShift)).toThrow(/OUT_OF_WINDOW/);
    });

    // Turno Nocturno (22:00 a 06:00, tolerancia 10 min) -> endTime <= startTime
    const overnightShift: ShiftConfig = {
      startTime: "22:00",
      endTime: "06:00",
      toleranceMin: 10,
    };

    test("Turno Nocturno (endTime 06:00 <= startTime 22:00) lanza UNSUPPORTED_SHIFT", () => {
      expect(() => calculateAttendanceStatus("22:05", overnightShift)).toThrow(/UNSUPPORTED_SHIFT/);
    });

    test("Turno Nocturno: Marcación a las 22:15 lanza UNSUPPORTED_SHIFT", () => {
      expect(() => calculateAttendanceStatus("22:15", overnightShift)).toThrow(/UNSUPPORTED_SHIFT/);
    });

    test("Turno Nocturno: Marcación a la 01:00 am lanza UNSUPPORTED_SHIFT", () => {
      expect(() => calculateAttendanceStatus("01:00", overnightShift)).toThrow(/UNSUPPORTED_SHIFT/);
    });

    test("Turno Nocturno: Marcación a las 06:05 am lanza UNSUPPORTED_SHIFT", () => {
      expect(() => calculateAttendanceStatus("06:05", overnightShift)).toThrow(/UNSUPPORTED_SHIFT/);
    });

    // Test adicional: endTime == startTime también lanza UNSUPPORTED_SHIFT
    test("Turno con endTime == startTime lanza UNSUPPORTED_SHIFT", () => {
      const zeroDurationShift: ShiftConfig = {
        startTime: "08:00",
        endTime: "08:00",
        toleranceMin: 15,
      };
      expect(() => calculateAttendanceStatus("08:00", zeroDurationShift)).toThrow(/UNSUPPORTED_SHIFT/);
    });

    // --- Nuevos tests de lógica pura attendanceCalculator ---
    test("Marcación exactamente 60 min antes del inicio es PUNTUAL (07:00 con start 08:00, earlyCheckInMinutes=60)", () => {
      const status = calculateAttendanceStatus("07:00", standardShift, 60);
      expect(status).toBe("PUNTUAL");
    });

    test("Marcación 61 min antes del inicio lanza OUT_OF_WINDOW (06:59 con start 08:00, earlyCheckInMinutes=60)", () => {
      expect(() => calculateAttendanceStatus("06:59", standardShift, 60)).toThrow(/OUT_OF_WINDOW/);
    });

    test("Límite exacto de tolerancia 0: 08:00 -> PUNTUAL", () => {
      const shiftTol0: ShiftConfig = { startTime: "08:00", endTime: "17:00", toleranceMin: 0 };
      const status = calculateAttendanceStatus("08:00", shiftTol0);
      expect(status).toBe("PUNTUAL");
    });

    test("Límite exacto de tolerancia 0: 08:01 -> TARDANZA", () => {
      const shiftTol0: ShiftConfig = { startTime: "08:00", endTime: "17:00", toleranceMin: 0 };
      const status = calculateAttendanceStatus("08:01", shiftTol0);
      expect(status).toBe("TARDANZA");
    });

    test("toleranceMin 0 se acepta estrictamente (no fallback a 15)", () => {
      const shiftTol0: ShiftConfig = { startTime: "08:00", endTime: "17:00", toleranceMin: 0 };
      // 08:10 debería ser TARDANZA porque tolerancia es 0, no 15
      const status = calculateAttendanceStatus("08:10", shiftTol0);
      expect(status).toBe("TARDANZA");
    });

    test("startTime inválido '8:00' (falta cero) lanza error", () => {
      const invalidShift: ShiftConfig = { startTime: "8:00", endTime: "17:00", toleranceMin: 15 };
      expect(() => calculateAttendanceStatus("08:00", invalidShift)).toThrow();
    });

    test("startTime inválido '24:00' lanza error", () => {
      const invalidShift: ShiftConfig = { startTime: "24:00", endTime: "17:00", toleranceMin: 15 };
      expect(() => calculateAttendanceStatus("08:00", invalidShift)).toThrow();
    });

    test("startTime inválido 'ab:cd' lanza error", () => {
      const invalidShift: ShiftConfig = { startTime: "ab:cd", endTime: "17:00", toleranceMin: 15 };
      expect(() => calculateAttendanceStatus("08:00", invalidShift)).toThrow();
    });
  });
});