import { hhMmToMinutes } from "./timeUtils";

export type AttendanceStatusType = "PUNTUAL" | "TARDANZA";

export interface ShiftConfig {
  startTime: string;    // "08:00"
  endTime: string;      // "17:00"
  toleranceMin: number; // 0 o mayor (acepta 0)
}

/**
 * Evalúa el estado de asistencia (PUNTUAL o TARDANZA) comparando la hora del servidor
 * con la configuración del turno del usuario.
 *
 * Reglas estrictas:
 * - NO existe estado FALLIDO. Si la hora está fuera de la ventana del turno, lanza OUT_OF_WINDOW.
 * - Turnos nocturnos o de duración cero (endTime <= startTime) no están soportados: lanza UNSUPPORTED_SHIFT.
 * - toleranceMin acepta 0 de forma estricta (no usa fallbacks or || 15).
 */
export function calculateAttendanceStatus(
  currentHhMm: string,
  shift: ShiftConfig,
  earlyCheckInMinutes: number = 60
): AttendanceStatusType {
  const startMinutes = hhMmToMinutes(shift.startTime);
  const endMinutes = hhMmToMinutes(shift.endTime);

  // Validación de Turno Nocturno o inviable
  if (endMinutes <= startMinutes) {
    throw new Error("UNSUPPORTED_SHIFT: Turnos nocturnos o de duración cero no están soportados");
  }

  const currentMinutes = hhMmToMinutes(currentHhMm);

  // Acepta 0 estrictamente
  const tolerance = Number.isFinite(shift.toleranceMin) && shift.toleranceMin >= 0
    ? Math.min(shift.toleranceMin, 120)
    : 0;

  const minAllowedMinutes = startMinutes - earlyCheckInMinutes;
  const punctualLimitMinutes = startMinutes + tolerance;

  // Validación estricta de ventana horaria
  if (currentMinutes < minAllowedMinutes || currentMinutes > endMinutes) {
    throw new Error("OUT_OF_WINDOW: Marcación fuera de la ventana horaria del turno");
  }

  if (currentMinutes <= punctualLimitMinutes) {
    return "PUNTUAL";
  } else {
    return "TARDANZA";
  }
}
