# BioSecure — Migración Firebase → MongoDB Atlas

## Estado
- ✅ Base de datos `biosecure` en MongoDB Atlas (6 colecciones: users, companies, sedes, shifts, attendances, notifications).
- ✅ Backend API Node en `D:\BioSecure Beta03\server\server.js` (puerto 3001, sin dependencias externas salvo mongoose).
- ✅ App Android compilada e instalada **sin Firebase** (Auth/Firestore/Functions/Messaging/Storage eliminados).
- ✅ Login, empleados, sedes, turnos, asistencias y notificaciones ahora vienen de Mongo.

## Cómo ejecutar
1. Iniciar API: doble clic en `server\start.cmd` (o `node server.js`). Debe mostrar "MongoDB conectado: biosecure" y "escuchando en http://127.0.0.1:3001".
2. Con el celular por USB:
   ```
   adb reverse tcp:3001 tcp:3001
   ```
3. Abrir la app BioSecure en el celular.

Si usas WiFi en vez de USB, cambia `BASE_URL` en
`BioSecure/app/src/main/java/com/biosecure/app/data/network/RetrofitInstance.kt`
por `http://<IP_DEL_PC>:3001/` (ej. `http://192.168.18.5:3001/`) y recompila.

## Credenciales de prueba
| Rol | Correo | Contraseña |
|---|---|---|
| Admin | sapm3006@gmail.com | sapm1234 |
| Empleado | empleado@pizzeria.com | empleado1234 |

## URI de Atlas
```
mongodb+srv://sapm3006_db_user:sergioponce1234@cluster0.kssppg3.mongodb.net/biosecure?retryWrites=true&w=majority
```

## Cambios principales en el app
- `AuthRepository` → token JWT guardado en SharedPreferences (antes Firebase Auth).
- `AttendanceRepository` → Retrofit a la API (antes Firestore, con polling de 4 s).
- `FunctionsRepository` → endpoints REST (antes Cloud Functions).
- `CompanyRepository` → REST.
- Widget → lee estado desde la API.
- Notificaciones → locales (FCM eliminado).
- Subida de foto de perfil → imagen comprimida y guardada en `users.image` (MongoDB), sin Firebase Storage.
- Errores REST → el cliente muestra el `code`/`error` real del backend, no mensajes genéricos.
- Turnos → se leen/escriben en la colección `shifts` y se sincronizan con `company.shifts`; IDs generados siempre.

## Endpoints
```
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
GET    /api/health
```
