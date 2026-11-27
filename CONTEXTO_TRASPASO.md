# Contexto de traspaso — BioSecure Beta 03

La versión actual **ya no usa Firebase**. Todo el estado está en MongoDB Atlas y se accede mediante el backend REST propio.

## Puntos clave para continuar

1. **Backend:** `server/server.js`. Arranca con `node server.js` o `server/start.cmd` en el puerto 3001.
2. **Base de datos:** Atlas, base `biosecure`. Colecciones: `users`, `companies`, `sedes`, `shifts`, `attendances`, `notifications`, `idempotency_keys`.
3. **App Android:** `app/src/main/java/com/biosecure/app`. Red en `data/network/RetrofitInstance.kt` (`BASE_URL`).
4. **Sesión:** `data/prefs/SessionStore.kt` guarda el token HMAC del backend.
5. **Repositorios:** `AuthRepository`, `AttendanceRepository`, `FunctionsRepository`, `CompanyRepository`.
6. **Tiempo real:** polling REST de 4 s en los flows de asistencias.
7. **Google Sign-In:** `GOOGLE_CLIENT_ID` en `server/start.cmd` y `google_web_client_id` en `app/src/main/res/values/strings.xml`.
8. **Fotos:** se comprimen y guardan como data URI en `users.image`.
9. **Ubicación:** `data/location/LocationProvider.kt` pide permiso, usa ubicación actual y hace fallback a última conocida.
10. **Errores:** el backend responde `{ ok:false, error, code }`; el cliente los muestra tal cual.

## Flujo de asistencia

```text
Empleado: huella / facial → POST /api/attendances/register (self)
Admin: QR scanner → POST /api/attendances/register (qrToken = uid empleado)

Validaciones servidor:
  - geovalla de la sede asignada (20–500 m)
  - turno asignado existente
  - ventana horaria del turno
  - entrada duplicada → ALREADY_COMPLETE
  - salida antes de 5 min → TOO_SOON
  - idempotencia por requestId
```

## Limitación conocida

El modo facial solo detecta rostro con ML Kit; la verificación de identidad real es la huella (BiometricPrompt).

## Documentación relacionada

- `README.md` — visión general, endpoints y ejecución.
- `MIGRACION-MONGO.md` — detalle de la migración y configuración de Atlas.
- `TEAM.md` — estado rápido del equipo.
