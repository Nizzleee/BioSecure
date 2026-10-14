export interface FormattedTimeResult {
  dateYyyyMmDd: string; // "20260624"
  timeHhMm: string;     // "08:15"
  minutesFromMidnight: number; // 495
}

export function formatServerTime(
  date: Date = new Date(),
  timeZone: string = "America/Lima"
): FormattedTimeResult {
  const sanitizedTz = sanitizeTimeZone(timeZone);

  let dateFormatter: Intl.DateTimeFormat;
  let timeFormatter: Intl.DateTimeFormat;

  try {
    dateFormatter = new Intl.DateTimeFormat("en-US", {
      timeZone: sanitizedTz,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hourCycle: "h23",
    });

    timeFormatter = new Intl.DateTimeFormat("en-GB", {
      timeZone: sanitizedTz,
      hour: "2-digit",
      minute: "2-digit",
      hourCycle: "h23",
    });
  } catch (e) {
    throw new Error("INVALID_TIMEZONE: Zona horaria inválida en la configuración de la empresa");
  }

  const dateParts = dateFormatter.formatToParts(date);
  const year = dateParts.find((p) => p.type === "year")?.value || "1970";
  const month = dateParts.find((p) => p.type === "month")?.value || "01";
  const day = dateParts.find((p) => p.type === "day")?.value || "01";
  const dateYyyyMmDd = `${year}${month}${day}`;

  const timeParts = timeFormatter.formatToParts(date);
  const hourStr = timeParts.find((p) => p.type === "hour")?.value || "00";
  const minuteStr = timeParts.find((p) => p.type === "minute")?.value || "00";

  const hour = parseInt(hourStr, 10) % 24;
  const minute = parseInt(minuteStr, 10) % 60;

  const timeHhMm = `${hour.toString().padStart(2, "0")}:${minute.toString().padStart(2, "0")}`;
  const minutesFromMidnight = hour * 60 + minute;

  return {
    dateYyyyMmDd,
    timeHhMm,
    minutesFromMidnight,
  };
}

export function hhMmToMinutes(hhMm: string): number {
  if (typeof hhMm !== "string") {
    throw new Error("CORRUPT_CHECKIN_DATA: Formato de hora no es una cadena válida");
  }
  const regex = /^([01][0-9]|2[0-3]):[0-5][0-9]$/;
  if (!regex.test(hhMm.trim())) {
    throw new Error("CORRUPT_CHECKIN_DATA: Formato de hora corrupto o inválido (esperado HH:mm)");
  }
  const parts = hhMm.trim().split(":").map((p) => parseInt(p, 10));
  return parts[0] * 60 + parts[1];
}

function sanitizeTimeZone(tz: string): string {
  if (!tz || typeof tz !== "string" || tz.trim() === "") {
    return "America/Lima";
  }
  if (tz === "UTC-5") {
    return "America/Lima";
  }
  return tz.trim();
}
