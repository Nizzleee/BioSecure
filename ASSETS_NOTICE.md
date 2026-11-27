# Notas de assets de terceros

## `app/src/main/assets/mobilefacenet.tflite`

Modelo MobileFaceNet usado para generar embeddings faciales en el dispositivo.

- Fuente: https://github.com/MCarlomagno/FaceRecognitionAuth
- Archivo: `assets/mobilefacenet.tflite`
- Licencia del repositorio: BSD-3-Clause
- Uso en BioSecure: inferencia local (112x112 RGB -> vector de 192 floats). No se envían imágenes al backend.
