# BioSecure — Asistencia biométrica empresarial

Sistema Android nativo (Kotlin + Jetpack Compose) para registro de asistencia por **huella**, **QR** y **detección facial**, con panel de administración multi-empresa.

> **Estado actual:** Beta 03 migrada a **100% MongoDB Atlas + backend Node propio**.
> Firebase Auth, Firestore, Functions, Messaging y Storage **no se usan**.

---

## Arquitectura

| Capa | Tecnología |
|---|---|
| App | Kotlin, Jetpack Compose Material 3, MVVM, Coroutines/Flow |
| Red | Retrofit + OkHttp (REST JSON) |
| Backend | Node.js `http` + mongoose, puerto `3001` |
| Base de datos | MongoDB Atlas, base `biosecure` |
| Auth | hash `scrypt` + token HMAC guardado en SharedPreferences |
| Google | Google Sign-In (`idToken`) verificado por Google y usuario creado/buscado en Mongo |
| Tiempo real | polling REST cada 4 s (reemplaza listeners de Firestore) |
| Notificaciones | notificaciones locales del sistema (sin FCM) |
| Fotos | imagen comprimida como data URI en `users.image` (sin Storage) |

El código fuente Android está en `app/src/main/java/com/biosecure/app`.
El backend está en `server/server.js`; arranque rápido: `server/start.cmd`.

---

## Ejecutar el backend

1. Verifica que `server/start.cmd` tenga configuradas `MONGODB_URI`, `AUTH_SECRET` y `GOOGLE_CLIENT_ID`.
2. Ejecuta:

```bat
node server\server.js
```

Debe imprimir:

```text
MongoDB conectado: biosecure
BioSecure API escuchando en http://127.0.0.1:3001
```

Comprueba salud con:

```bat
curl http://127.0.0.1:3001/api/health
```

---

## Ejecutar la app

Con el teléfono conectado por USB:

```bat
adb reverse tcp:3001 tcp:3001
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Compilación:

```bat
.\gradlew.bat assembleDebug
```

Si usas WiFi en lugar de USB, cambia `BASE_URL` en
`app/src/main/java/com/biosecure/app/data/network/RetrofitInstance.kt`
a `http://<IP_DEL_PC>:3001/` y recompila.

### Credenciales de prueba

| Rol | Correo | Contraseña |
|---|---|---|
| Admin | `sapm3006@gmail.com` | `sapm1234` |
| Empleado | `empleado@pizzeria.com` | `empleado1234` |

El botón **Continuar con Google** crea o reutiliza el usuario en MongoDB y guarda su foto de perfil.

---

## Endpoints principales

```text
POST   /api/auth/login
POST   /api/auth/google
GET    /api/auth/me
POST   /api/auth/logout

GET    /api/users?companyId=&role=
POST   /api/users
PUT    /api/users/:uid
DELETE /api/users/:uid

GET    /api/companies/:id
PUT    /api/companies/:id
GET    /api/companies/:id/shifts
PUT    /api/companies/:id/shifts

GET    /api/sedes?companyId=
POST   /api/sedes
DELETE /api/sedes/:id

GET    /api/attendances?companyId=&uid=
POST   /api/attendances/register
PUT    /api/attendances/:id/confirm

GET    /api/notifications
PUT    /api/notifications/read
GET    /api/health
```

Los errores del servidor se devuelven como:

```json
{ "ok": false, "error": "mensaje", "code": "CODIGO_REAL" }
```

Códigos habituales de asistencia:
`ALREADY_COMPLETE`, `TOO_SOON`, `NO_SHIFT_ASSIGNED`, `NO_SEDE_ASSIGNED`,
`OUT_OF_RANGE_GEOFENCE`, `OUT_OF_WINDOW`, `ACCURACY_TOO_HIGH`,
`ADMIN_CANNOT_MARK_SELF`, `TARGET_NOT_EMPLOYEE`.

---

## Funcionalidades

- Login por correo/contraseña y login con Google.
- Roles automáticos Admin / Empleado desde MongoDB.
- Dashboard admin con métricas del día, filtro por sede y exportación CSV.
- Gestión de empleados, turnos y sedes con geocerca (20–500 m).
- Registro de entrada/salida con validación de distancia, turno, horario y duplicados.
- Scanner QR para que el admin registre asistencias de empleados.
- Perfil editable: nombre y foto de perfil persistida en Mongo.
- Widget y notificaciones locales alimentados por la API.
- Tema claro/oscuro e idioma ES/EN.

### Limitación conocida

La pestaña de **escaneo facial** detecta un rostro con ML Kit, pero **no verifica la identidad** del empleado. La verificación biométrica real se hace con huella/BiometricPrompt.

---

## Documentación adicional

- `MIGRACION-MONGO.md` — detalle de la migración y configuración de Atlas.
- `server/start.cmd` — variables de entorno del backend.
