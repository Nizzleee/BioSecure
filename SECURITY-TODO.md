# Tareas de seguridad pendientes

## 1. Enrolamiento facial 100 % en el servidor (BLOQUEANTE PARA RELEASE)

**Estado:** pendiente (decisión tomada el 2026-11-09: no cerrarlo todavía).

**Problema actual**

`firestore.rules` permite que el propio usuario escriba
`users/{uid}/faceEmbedding/current`. Desde la migración a arquitectura 100 %
local, el cliente también decide la coincidencia facial
(`FaceRecognitionRepository.verify()`), así que un APK modificado podría
sustituir su plantilla por la cara de otra persona y habilitar un marcaje por
proxy sin que ningún servidor lo detecte.

**Por qué se aplaza**

- No hay Blaze: `registerAttendance`, `enrollFace` y el resto de Cloud
  Functions no se pueden desplegar en `bio-f146c`. El código servidor queda en
  `functions/` pendiente de activar facturación.
- Primero hay que validar el umbral `FACE_MATCH_THRESHOLD` con caras reales
  (genuinas vs. impostor) en el dispositivo.
- El riesgo es inherente a la Opción A (decisión y escritura en el cliente).

**Limitaciones que hay que asumir mientras no haya Blaze**

1. Un cliente modificado puede escribir su propia asistencia sin verificación
   facial real. Las reglas solo validan UID, schema, tipos y la transición de
   check-out; no pueden recalcular Haversine, turno ni similitud facial.
2. La asistencia se calcula con el reloj del dispositivo y la zona horaria de
   la empresa; no es una fuente de tiempo confiable.
3. No se escriben notificaciones de check-in (el servidor sí lo hacía).

**Arreglo requerido antes de generar el release**

1. Crear una callable `enrollFace` (Admin SDK) que:
   - reciba `uid`, `model`, `dim` y `vectors`;
   - valide dimensiones, finitud y número de vectores;
   - escriba la plantilla con Admin SDK (ignorando las reglas de cliente).
2. Cambiar `FaceRecognitionRepository.enrollFace()` para llamar a esa callable
   en lugar de `FaceEmbeddingRepository.save()`.
3. En `firestore.rules`, cambiar la regla de `faceEmbedding` a:
   - `allow read: if request.auth.uid == userId;`
   - `allow write: if false;` (solo Admin SDK).
4. Redesplegar functions + rules y volver a enrolar a los usuarios de prueba.

**Criterio de salida**

Ningún cliente puede crear, actualizar ni borrar su plantilla facial; solo el
servidor puede hacerlo. Hasta entonces, **no publicar el release**.
