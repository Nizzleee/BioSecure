# BioSecure

Sistema de control de asistencia biométrica para Android. Permite registrar entradas y salidas del personal mediante huella dactilar y reconocimiento facial, con roles diferenciados para empleados y administradores.

---

## Requisitos mínimos

| Requisito | Versión |
|---|---|
| Android Studio | Ladybug 2024.2 o superior |
| JDK | 19 (incluido en Android Studio) |
| Android SDK | API 36 (compileSdk) |
| Android mínimo en dispositivo | API 26 — Android 8.0 Oreo |
| Gradle | 9.4.1 (wrapper incluido) |
| Kotlin | 2.2.10 |
| AGP | 9.1.1 |

El dispositivo o emulador debe tener **sensor de huella dactilar registrado** para probar biometría. Para reconocimiento facial se necesita **cámara frontal**.

---

## Setup

```bash
# 1. Clonar el repositorio
git clone <url-del-repo>
cd BioSecure

# 2. Abrir en Android Studio
#    File > Open > seleccionar la carpeta BioSecure

# 3. Sincronizar Gradle
#    Android Studio lo hace automáticamente al abrir
#    O desde terminal:
.\gradlew.bat build          # Windows
./gradlew build              # macOS / Linux

# 4. Correr en dispositivo / emulador
#    Run > Run 'app'  (Shift+F10)
#    O desde terminal:
.\gradlew.bat installDebug
```

> **Nota:** Si el build falla con un error de `JAVA_HOME`, abre Android Studio y usa su terminal integrada — ya tiene el JDK configurado correctamente.

---

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Lenguaje | Kotlin 2.2.10 |
| UI | Jetpack Compose + Material 3 |
| Arquitectura | MVVM (ViewModel + StateFlow) |
| Navegación | Navigation Compose 2.9.0 |
| Red | Retrofit 2.9.0 + Gson |
| Imágenes | Coil 2.6.0 |
| Biometría | AndroidX Biometric 1.2.0-alpha05 |
| Cámara | CameraX 1.3.1 |
| Reconocimiento facial | ML Kit Face Detection 16.1.5 |
| DI | Manual (ViewModelFactory) |
| Fuente de datos externa | DummyJSON API |

---

## Equipo

| Nombre | Rol |
|---|---|
| Nicolas Carrillo Chambi | Líder técnico / Arquitectura |
| Sergio Ponce Moron | UI / Jetpack Compose |
| Marco Figueroa Cordova | Integración biométrica / CameraX |
| Carlos Moran Guillen | Capa de datos / Retrofit |
| Adrian Flores Cama | Navegación / ViewModel |

Institución: **TECSUP**
