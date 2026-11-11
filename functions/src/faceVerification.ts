/**
 * PENDIENTE DE BLAZE — NO DESPLEGADO.
 *
 * Se conserva como referencia del umbral y de la verificación autoritativa en
 * servidor. La app usa ahora FaceRecognitionRepository.verify() con el mismo
 * umbral (0.70) y la misma similitud coseno, en el dispositivo.
 *
 * No borrar. Ver functions/PENDING-BLAZE.md.
 */
import { HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";

/**
 * Umbral de similitud coseno para aceptar una cara.
 *
 * 0.70 es el valor acordado para producción. No bajar de 0.65: por debajo
 * de ese punto dos personas distintas pueden superar el umbral con FaceNet.
 */
export const FACE_MATCH_THRESHOLD = 0.70;
const MIN_ALLOWED_THRESHOLD = 0.65;

if (FACE_MATCH_THRESHOLD < MIN_ALLOWED_THRESHOLD) {
  throw new Error("FACE_MATCH_THRESHOLD no puede ser menor que 0.65");
}

const MAX_EMBEDDING_DIM = 4096;
const MAX_VECTORS = 5;

/**
 * Similitud coseno real entre dos vectores.
 *
 * Devuelve -1 si las dimensiones no coinciden o si algún vector es nulo.
 * El cliente ya normaliza L2, pero calculamos la norma igualmente para no
 * confiar en que la plantilla almacenada esté normalizada.
 */
export function cosineSimilarity(a: number[], b: number[]): number {
  if (!Array.isArray(a) || !Array.isArray(b) || a.length === 0 || a.length !== b.length) {
    return -1;
  }

  let dot = 0;
  let normA = 0;
  let normB = 0;
  for (let i = 0; i < a.length; i++) {
    const va = a[i];
    const vb = b[i];
    if (!Number.isFinite(va) || !Number.isFinite(vb)) {
      return -1;
    }
    dot += va * vb;
    normA += va * va;
    normB += vb * vb;
  }

  if (normA === 0 || normB === 0) {
    return -1;
  }
  return dot / Math.sqrt(normA * normB);
}

interface StoredFaceEmbedding {
  model: string;
  dim: number;
  vectors: number[][];
  updatedAt?: admin.firestore.Timestamp;
}

/**
 * Verifica la cara en el servidor, que es la única fuente de verdad.
 *
 * El cliente solo envía la plantilla generada en el dispositivo; nunca
 * decide si la cara coincide. Si la verificación falla, se lanza un
 * HttpsError cuyo `details.code` es uno de:
 *   - FACE_NOT_ENROLLED
 *   - FACE_MODEL_MISMATCH
 *   - FACE_MISMATCH   (incluye `details.score`)
 */
export async function verifyFaceOrThrow(
  uid: string,
  liveEmbedding: number[],
  liveModel: string
): Promise<number> {
  if (typeof uid !== "string" || uid.trim() === "") {
    throw new HttpsError("unauthenticated", "Usuario no autenticado", { code: "UNAUTHENTICATED" });
  }

  if (
    !Array.isArray(liveEmbedding) ||
    liveEmbedding.length === 0 ||
    liveEmbedding.length > MAX_EMBEDDING_DIM ||
    !liveEmbedding.every((value) => Number.isFinite(value))
  ) {
    throw new HttpsError(
      "invalid-argument",
      "La plantilla facial en vivo es inválida",
      { code: "FACE_EMBEDDING_INVALID" }
    );
  }

  if (typeof liveModel !== "string" || liveModel.trim() === "" || liveModel.length > 128) {
    throw new HttpsError(
      "invalid-argument",
      "El identificador del modelo facial es inválido",
      { code: "FACE_EMBEDDING_INVALID" }
    );
  }

  const db = admin.firestore();
  const snap = await db
    .collection("users")
    .doc(uid)
    .collection("faceEmbedding")
    .doc("current")
    .get();

  if (!snap.exists) {
    throw new HttpsError(
      "failed-precondition",
      "El usuario no tiene una plantilla facial enrolada",
      { code: "FACE_NOT_ENROLLED" }
    );
  }

  const data = snap.data() as Partial<StoredFaceEmbedding> | undefined;
  const storedModel = data?.model;
  const storedDim = data?.dim;
  const storedVectors = data?.vectors;

  if (
    typeof storedModel !== "string" ||
    typeof storedDim !== "number" ||
    !Number.isInteger(storedDim) ||
    !Array.isArray(storedVectors) ||
    storedVectors.length === 0
  ) {
    throw new HttpsError(
      "failed-precondition",
      "El enrolamiento facial almacenado está incompleto",
      { code: "FACE_NOT_ENROLLED" }
    );
  }

  if (storedModel !== liveModel || storedDim !== liveEmbedding.length) {
    throw new HttpsError(
      "failed-precondition",
      "La plantilla facial no corresponde al modelo actual",
      {
        code: "FACE_MODEL_MISMATCH",
        expectedModel: storedModel,
        expectedDim: storedDim,
        receivedModel: liveModel,
        receivedDim: liveEmbedding.length,
      }
    );
  }

  const validVectors = storedVectors.filter(
    (vector) =>
      Array.isArray(vector) &&
      vector.length === storedDim &&
      vector.every((value) => Number.isFinite(value))
  );

  if (validVectors.length === 0 || validVectors.length > MAX_VECTORS) {
    throw new HttpsError(
      "failed-precondition",
      "El enrolamiento facial almacenado está corrupto",
      { code: "FACE_NOT_ENROLLED" }
    );
  }

  const scores = validVectors.map((vector) => cosineSimilarity(liveEmbedding, vector));
  if (scores.some((score) => !Number.isFinite(score) || score < 0)) {
    throw new HttpsError(
      "failed-precondition",
      "El enrolamiento facial almacenado está corrupto",
      { code: "FACE_NOT_ENROLLED" }
    );
  }

  const bestScore = Math.max(...scores);

  if (bestScore < FACE_MATCH_THRESHOLD) {
    throw new HttpsError(
      "failed-precondition",
      "El rostro no coincide con el enrolamiento",
      { code: "FACE_MISMATCH", score: Number(bestScore.toFixed(4)) }
    );
  }

  return bestScore;
}
