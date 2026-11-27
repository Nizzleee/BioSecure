# TEAM — BioSecure Beta 03

> Documento actualizado tras la migración a MongoDB Atlas.
> El detalle operativo está en `README.md` y `MIGRACION-MONGO.md`.

## Estado actual

| Área | Estado |
|---|---|
| Build Android | ✅ `.\gradlew.bat assembleDebug` correcto |
| Backend | ✅ Node `http` + mongoose en puerto 3001 |
| Base de datos | ✅ Atlas, base `biosecure` |
| Firebase en app | ❌ Eliminado (Auth/Firestore/Functions/Messaging/Storage) |
| Login correo | ✅ scrypt + token HMAC |
| Login Google | ✅ `idToken` verificado con Google; usuario en Mongo |
| Sesión persistente | ✅ SharedPreferences (`SessionStore`) |
| Empleados / sedes / turnos | ✅ REST a MongoDB |
| Asistencia | ✅ geovalla, turno, horario, duplicados e idempotencia |
| Fotos de perfil | ✅ data URI en `users.image` |
| Notificaciones | ✅ locales, sin FCM |
| Escaneo facial | ⚠️ Detecta rostro; no valida identidad (usar huella) |

## Arranque rápido

```bat
node server\server.js
adb reverse tcp:3001 tcp:3001
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Credenciales de prueba

| Rol | Correo | Contraseña |
|---|---|---|
| Admin | sapm3006@gmail.com | sapm1234 |
| Empleado | empleado@pizzeria.com | empleado1234 |
