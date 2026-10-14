/**
  Cálculo de distancia Haversine en metros entre dos coordenadas geográficas (lat, lng).
  @param lat1 Latitud punto 1 en grados decimales (-90 a 90)
  @param lng1 Longitud punto 1 en grados decimales (-180 a 180)
  @param lat2 Latitud punto 2 en grados decimales (-90 a 90)
  @param lng2 Longitud punto 2 en grados decimales (-180 a 180)
  @returns Distancia en metros (float)
  @throws Error con mensaje "INVALID_COORDINATES" si algún argumento no es finito o está fuera de rango
  */
export function calculateHaversineDistance(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number
): number {
  // Validar que los 4 argumentos sean finitos
  if (!Number.isFinite(lat1) || !Number.isFinite(lng1) || !Number.isFinite(lat2) || !Number.isFinite(lng2)) {
    throw new Error("INVALID_COORDINATES");
  }

  // Validar rangos: lat [-90, 90], lng [-180, 180]
  if (lat1 < -90 || lat1 > 90 || lat2 < -90 || lat2 > 90 ||
      lng1 < -180 || lng1 > 180 || lng2 < -180 || lng2 > 180) {
    throw new Error("INVALID_COORDINATES");
  }

  const EARTH_RADIUS_METERS = 6371000; // Radio medio de la Tierra en metros

  const toRadians = (degrees: number) => (degrees * Math.PI) / 180;

  const dLat = toRadians(lat2 - lat1);
  const dLng = toRadians(lng2 - lng1);

  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRadians(lat1)) *
      Math.cos(toRadians(lat2)) *
      Math.sin(dLng / 2) *
      Math.sin(dLng / 2);

  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

  const distance = EARTH_RADIUS_METERS * c;

  // Verificar que el resultado sea finito
  if (!Number.isFinite(distance)) {
    throw new Error("INVALID_COORDINATES");
  }

  return distance;
}