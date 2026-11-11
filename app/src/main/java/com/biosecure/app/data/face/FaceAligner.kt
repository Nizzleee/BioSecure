package com.biosecure.app.data.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

/**
 * Alinea una cara detectada por ML Kit al tamaño de entrada del modelo
 * (160x160 para el FaceNet de shubham0204) usando ojos y nariz como
 * referencia (plantilla de 5 puntos tipo ArcFace).
 *
 * Sin alineación el embedding es poco confiable: el modelo fue entrenado
 * con caras alineadas, no con recortes aproximados del bounding box.
 */
object FaceAligner {

    /** Tamaño de salida por defecto: entrada del modelo facenet.tflite. */
    const val SIZE = 160

    private const val CANONICAL_BASE = 112f

    // Plantilla canónica ArcFace para 112x112:
    // ojo izquierdo de la imagen, ojo derecho de la imagen, base de la nariz.
    private val CANONICAL_112 = floatArrayOf(
        38.2946f, 51.6963f,
        73.5318f, 51.5014f,
        56.0252f, 71.7366f
    )

    /**
     * Devuelve un Bitmap cuadrado de [size]x[size] con la cara alineada, o
     * null si ML Kit no detectó los 3 landmarks necesarios (ojos + nariz).
     *
     * Los ojos se ordenan por su posición en la imagen (no por la etiqueta
     * LEFT/RIGHT de ML Kit), para que la transformación no refleje el rostro.
     */
    fun align(source: Bitmap, face: Face, size: Int = SIZE): Bitmap? {
        val eyeA = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val eyeB = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null

        val imageLeftEye = if (eyeA.x <= eyeB.x) eyeA else eyeB
        val imageRightEye = if (eyeA.x <= eyeB.x) eyeB else eyeA

        val scale = size.toFloat() / CANONICAL_BASE
        val canonical = FloatArray(CANONICAL_112.size) { CANONICAL_112[it] * scale }

        val src = floatArrayOf(
            imageLeftEye.x, imageLeftEye.y,
            imageRightEye.x, imageRightEye.y,
            nose.x, nose.y
        )

        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, canonical, 0, 3)) return null

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
