# Prueba de carga con Docker — `registerAttendance`

Pipeline reproducible que ejercita el **camino HTTP real** de la Cloud Function
`registerAttendance` (token del emulador de Auth → callable del emulador de
Functions → transacción de Firestore) y luego replica los datos a PostgreSQL.

No usa la app Android: el contrato se prueba llamando al mismo endpoint que
consume `FirebaseFunctionsRepository`.

---

## 1. Qué prueba y qué NO prueba

**Sí prueba**

- Que el emulador de Auth emite un ID token y que el emulador de Functions lo acepta.
- Que el payload se serializa igual que en la app (`tipo`, `lat`, `lng`,
  `accuracy`, `isMock`, `requestId`, `qrToken`).
- Que los `defineInt()` se leen del entorno del emulador. El smoke test envía
  `accuracy = 47` y exige `ACCURACY_TOO_HIGH`; eso solo ocurre si
  `MAX_ACCURACY_METERS=45` se cargó desde `docker/firebase-emulator/functions.env`.
  Con el default hardcodeado (50) la llamada habría pasado.
- Que la geocerca de la sede rechaza coordenadas fuera de radio.
- Que el check-in escribe el documento determinista `{uid}_{yyyyMMdd}`.
- Que repetir el mismo `requestId` devuelve la misma respuesta y **no** crea otro documento.
- Latencia p50/p95/p99 por nivel de concurrencia (1, 10, 50, 100, 200).
- Errores agrupados por `code`.
- Documentos creados y duplicados reales por `userId + date`.

**NO prueba**

- Capacidad de producción. El emulador no reproduce contención de Firestore,
  latencia multi-región ni límites de cuota.
- CORS de navegador: el cliente es Node, no un navegador.
- La app Android en el celular.
- `registerManualAttendance` (sigue `unimplemented`).

> Frase segura para el informe: «La prueba mide lógica de negocio, idempotencia y
> duplicados sobre el emulador con 200 llamadas concurrentes. No mide capacidad
> de producción.»

---

## 2. «Alerta por duplicados»: qué se cuenta realmente

El ID de asistencia es determinista (`{uid}_{yyyyMMdd}`), así que Firestore
**no puede** tener dos documentos del mismo usuario el mismo día. Por eso la
alerta no se construye contando documentos duplicados, sino contando:

| Señal | Significado |
|---|---|
| `ALREADY_COMPLETE` | El usuario ya tenía entrada y salida; el sistema rechazó la tercera marcación. |
| `TOO_SOON` | Segundo intento antes de `MIN_MINUTES_BEFORE_CHECKOUT`. |
| Réplica idempotente | Mismo `requestId` → misma respuesta, cero escrituras nuevas. |
| `duplicateDocuments` | Documentos que comparten `userId + date`. Debe ser **0**. |

El informe entrega ambos números por separado: `duplicateDocuments` (invariante
de datos) y los códigos de rechazo (comportamiento esperado).

---

## 3. Arquitectura del compose

```
emulator  (Java 21 + Node 20 + firebase-tools 15.30.2)
  ├─ auth:9099  firestore:8080  functions:5001  hub:4400  ui:4000
  │     ▲             ▲               ▲
  │     │             │               │
  ├─ seed    → crea empresa, sede, turno, admin y 400 empleados
  ├─ smoke   → 1 llamada real + 2 negativas + replay idempotente
  └─ loadtest→ 1 → 10 → 50 → 100 → 200 usuarios, tokens distintos
                         │
                         ▼
                postgres:5432 (host 5433)
                         ▲
                    worker → upsert + verificación de duplicados
```

Cada servicio espera a que el anterior termine **con éxito**
(`depends_on: condition: service_completed_successfully`). Si el smoke falla, la
carga no se ejecuta: el riesgo de la Function se detecta antes de medir.

---

## 4. Cómo ejecutarla

Requisitos: Docker Desktop encendido, ~4 GB libres.

```powershell
# construir y ejecutar todo
docker compose -f docker-compose.loadtest.yml up --build

# ver el resultado del informe
Get-Content loadtest\results\loadtest-latest.json

# limpiar contenedores y volumen de PostgreSQL
docker compose -f docker-compose.loadtest.yml down -v
```

Servicios expuestos en el host:

| Puerto | Servicio |
|---|---|
| 4000 | Emulator UI |
| 5001 | Functions (callable) |
| 8080 | Firestore |
| 9099 | Auth |
| 5433 | PostgreSQL |

Las imágenes copian el código dentro de la imagen (no hay bind mount del repo),
así que `node_modules` de Windows nunca se mezcla con binarios Linux. Para
repetir con cambios de código hay que reconstruir (`--build`).

### Datos del seed

| Elemento | Valor |
|---|---|
| Proyecto | `demo-biosecure` |
| Empresa | `demo-company` (`timezone: America/Lima`) |
| Sede | `demo-sede` — lat `-12.0464`, lng `-77.0429`, radio `300 m` |
| Turno | `demo-shift` — `00:00`–`23:59`, tolerancia `0` |
| Admin | `admin@demo.biosecure.test` |
| Empleados | `employee001@…` … `employee400@…` |
| Contraseña | `Test1234!` |
| UID empleado *n* | `emp` + 21 dígitos (`emp000000000000000000001`) |

El turno `00:00–23:59` evita `OUT_OF_SCHEDULE_WINDOW` durante la prueba.

### Reparto de usuarios (sin solapamiento)

| Fase | Rango | Motivo |
|---|---|---|
| Smoke | 400 | No contamina la escalera |
| Carga 1 | 1 | |
| Carga 10 | 2–11 | |
| Carga 50 | 12–61 | |
| Carga 100 | 62–161 | |
| Carga 200 | 162–361 | |

Cada usuario hace **un solo check-in**, así que la latencia de cada nivel mide la
misma transacción completa y no un rechazo rápido por duplicado.

---

## 5. Resultados

Corrida de referencia (Docker Desktop, emulador, 26/10/2026):

| Usuarios | wall (ms) | p50 (ms) | p95 (ms) | p99 (ms) | códigos |
|---:|---:|---:|---:|---:|---|
| 1 | 55 | 55 | 55 | 55 | `OK: 1` |
| 10 | 2 420 | 1 985 | 2 417 | 2 418 | `OK: 10` |
| 50 | 20 693 | 17 508 | 20 609 | 20 672 | `OK: 50` |
| 100 | 1 110 | 630 | 984 | 1 008 | `OK: 100` |
| 200 | 5 247 | 2 560 | 5 196 | 5 211 | `OK: 200` |

Totales: `361/361` con `OK`, `0` errores, `0` documentos duplicados,
`361` documentos nuevos, `362` notificaciones y `362` claves de idempotencia.

Las etapas de 10 y 50 usuarios son las más lentas porque el emulador de Functions
levanta workers nuevos en frío (uno por instancia); a partir de 100 el pool ya
está caliente y los números bajan. **No** interpretar esa curva como capacidad:
mide el arranque del emulador, no la Function.

`loadtest/results/loadtest-latest.json` contiene:

```jsonc
{
  "environment": "firebase-emulator-suite",
  "auth":     { "users": 361, "durationMs": 0, "loginsPerSecond": 0 },
  "stages":   [ { "users": 1, "latencyMs": { "p50": 0, "p95": 0, "p99": 0 }, "codes": {} } ],
  "totals":   { "requests": 361, "successes": 361, "duplicateDocuments": 0, "notifications": 361 },
  "probes":   { "replay": { "sameCheckIn": true }, "newDocuments": 0 },
  "notes":    [ "Measured against the Firebase Emulator Suite, not production." ]
}
```

Los percentiles se calculan con interpolación lineal sobre todas las llamadas de
la etapa (incluidos errores, si los hay). `successLatencyMs` excluye los rechazos.

---

## 6. Fallos esperables y cómo leerlos

| Síntoma | Causa probable |
|---|---|
| Smoke: `ACCURACY_TOO_HIGH` no aparece | `functions/.env` no se cargó → los `defineInt` usan defaults. Revisar el log del emulador al arrancar. |
| `NO_SHIFT_ASSIGNED` / `NO_SEDE_ASSIGNED` | El seed no terminó o el usuario no tiene `shiftId`/`sedeId`. |
| `OUT_OF_RANGE_GEOFENCE` | Coordenadas fuera de 300 m (`LOADTEST_JITTER_DEGREES` demasiado alto). |
| `UNAUTHENTICATED` | El emulador de Functions no está validando contra el de Auth; revisar que ambos estén en el mismo `--project`. |
| `RETRY` masivo | Contención real en el emulador. Es la señal que en producción importa. |
| `TOO_SOON` en una etapa | Se reutilizó un usuario ya marcado; revisar `LOADTEST_EMPLOYEE_START`. |
| Smoke: check-in responde `500 INTERNAL` y el log del emulador muestra `Cannot read properties of undefined (reading 'serverTimestamp')` | Falta el parche al namespace de `firebase-admin` (ver §8). Reconstruir el emulador con `--build`. |
| `TOO_SOON` al repetir el smoke | Corrida anterior dejó la asistencia del usuario 400. El smoke limpia su propio estado al iniciar; si aparece, la imagen del smoke es vieja (`--build`). |

Los tests de la Function (`78/78`) y de reglas (`25/25`) no cubren este camino:
se ejecutan con `firebase-functions-test` (wrap), no contra el emulador. Por eso
el smoke test HTTP existe y bloquea la carga si falla.

---

## 7. PostgreSQL

`docker/postgres/init.sql` define el esquema espejo. `scripts/postgres_worker.js`:

1. aplica el esquema (idempotente),
2. lee todas las asistencias de Firestore,
3. hace `INSERT … ON CONFLICT (attendance_id) DO UPDATE`,
4. registra la corrida en `sync_runs`,
5. verifica duplicados por `user_id + attendance_date` y falla si encuentra alguno.

La tabla **no** tiene unique en `(user_id, attendance_date)` a propósito: si
Firestore llegara a producir dos documentos para el mismo día, el worker debe
poder mostrarlo como violación en lugar de fusionarlo silenciosamente.

---

## 8. Parche necesario al emulador (`admin.firestore.FieldValue`)

**El código de `functions/src/index.ts` está correcto.** El problema es del
emulador, no de la Function:

`firebase-tools` envuelve el módulo `firebase-admin` del proyecto en un `Proxied`
que, para cualquier propiedad que sea función y no constructora, devuelve
`value.bind(target)`. `admin.firestore` es justamente eso: un getter que devuelve
una arrow function con los estáticos del SDK pegados
(`Object.assign(fn, require("@google-cloud/firestore").Firestore)`). Al hacer
`.bind()` se pierden esos estáticos, así que dentro del emulador:

```js
admin.firestore()              // OK
admin.firestore.FieldValue     // undefined  ← rompe serverTimestamp()
admin.firestore.Timestamp      // undefined  ← rompe fromDate()
```

En Cloud Functions real el namespace no pasa por ese proxy y ambas propiedades
existen. Consecuencia: el check-in respondía `500 INTERNAL` **solo** en el
emulador, con `TypeError: Cannot read properties of undefined (reading
'serverTimestamp')` en el log del emulador.

Solución aplicada en la imagen (`docker/firebase-emulator/patch-admin-namespace.js`,
ejecutado desde el `Dockerfile`): se conserva el `bind` pero se copian los
descriptores originales al resultado, restaurando `FieldValue`, `Timestamp`,
`v1` y `v1beta1`. El parche es estricto: si una versión nueva de `firebase-tools`
cambia esa función, **el build falla** en lugar de degradar en silencio.

Esto **no** modifica `functions/`: el código que se prueba es exactamente el que
se despliega. Solo se corrige el emulador para que se comporte como producción.

> Si en el futuro se actualiza `FIREBASE_TOOLS_VERSION`, revisar
> `patch-admin-namespace.js` y el log del build del servicio `emulator`.

