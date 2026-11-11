# Cloud Functions pendientes de Blaze

**Estado:** no desplegadas. El proyecto `bio-f146c` está en plan Spark y no
permite desplegar Cloud Functions.

## Qué se conserva

- `functions/src/index.ts`
  - `registerAttendance`: validación autoritativa de usuario, empresa, sede,
    turno, geocerca, ventana horaria, check-in/check-out e idempotencia.
  - `registerManualAttendance`: stub sin implementar.
- `functions/src/faceVerification.ts`
  - `FACE_MATCH_THRESHOLD = 0.70` (nunca menor que 0.65).
  - Similitud coseno y validación de la plantilla almacenada.
- `functions/src/logic/`
  - `haversine.ts`, `timeUtils.ts`, `attendanceCalculator.ts`.

No borrar este código: sigue siendo la referencia para volver al modelo
servidor si en el futuro se activa facturación.

## Qué usa hoy la app

La app móvil funciona sin Cloud Functions:

- **Verificación facial:** `FaceRecognitionRepository.verify()` carga
  `users/{uid}/faceEmbedding/current`, genera la plantilla en vivo, calcula la
  similitud coseno y decide con el mismo umbral de 0.70.
- **Asistencia:** `AttendanceRepository.registerLocalAttendance()` escribe
  directamente en `attendances/{uid}_{yyyyMMdd}` con el mismo schema que
  escribía `registerAttendance` (sin `shiftId`/`turno`).
- **Check-out:** única actualización permitida por reglas: `checkOut` de `""`
  a `HH:mm` + `updatedAt`.
- **Notificaciones:** el servidor creaba un documento en `notifications` en el
  check-in. El cliente no puede hacerlo con las reglas actuales; queda
  pendiente para cuando exista Blaze.

## Limitaciones conocidas de la arquitectura local

1. La decisión de coincidencia facial vive en el cliente. Un APK modificado
   podría saltarse la verificación. Es inherente a la Opción A sin backend.
2. La plantilla facial sigue siendo escribible por el propio usuario
   (`users/{uid}/faceEmbedding/current`), por lo que un cliente modificado
   podría sustituirla. Ver `SECURITY-TODO.md`.
3. La geocerca, la ventana horaria y el estado (`PUNTUAL`/`TARDANZA`) se
   calculan en el cliente. Las reglas validan tipos, UID y schema, pero no
   pueden recalcular Haversine ni el turno.
4. La fecha/hora usa la zona horaria de la empresa, pero el reloj del
   dispositivo no es una fuente confiable.
5. El QR de admin sigue llamando a la Cloud Function y no funcionará hasta
   activar Blaze. El marcaje propio FACIAL/HUELLA sí es local.
