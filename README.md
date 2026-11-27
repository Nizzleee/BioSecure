# BioSecure — Sistema de Asistencia Biométrica Empresarial (SaaS Multi-Tenant)

> **Estado del Proyecto:** Beta 03 | **Compilación:** `assembleDebug` BUILD SUCCESSFUL  
> **Plataforma:** Android Nativo (Kotlin + Jetpack Compose Material 3)  
> **Backend:** Node.js propio + MongoDB Atlas (sin Firebase)

---

## 📋 Índice
1. [¿Qué es BioSecure?](#1-qué-es-biosecure)
2. [Situación Actual de la Aplicación](#2-situación-actual-de-la-aplicación)
3. [Funcionalidades Actuales (Lo que TIENE)](#3-funcionalidades-actuales-lo-que-tiene)
4. [Cosas de las que CARECE (Pendientes y Limitaciones)](#4-cosas-de-las-que-carece-pendientes-y-limitaciones)
5. [Vulnerabilidades y Riesgos de Seguridad](#5-vulnerabilidades-y-riesgos-de-seguridad)
6. [Arquitectura y Stack Tecnológico](#6-arquitectura-y-stack-tecnológico)
7. [Base de Datos MongoDB Atlas](#7-base-de-datos-mongodb-atlas)
8. [Estructura del Proyecto](#8-estructura-del-proyecto)
9. [Instalación y Configuración](#9-instalación-y-configuración)
10. [Equipo de Desarrollo](#10-equipo-de-desarrollo)

---

## 1. ¿Qué es BioSecure?

**BioSecure** es una solución móvil empresarial en la nube para el control y registro de asistencia laboral mediante verificación biométrica híbrida y validación en tiempo real.

Diseñado bajo un modelo **SaaS Multi-tenant**, permite a múltiples empresas gestionar de forma independiente sus empleados, sedes, turnos horarios y registros de entrada/salida. El sistema utiliza un mecanismo de **doble factor de verificación (2FA)**:
1. El empleado valida su identidad en su smartphone mediante **huella dactilar (BiometricPrompt)** o **reconocimiento facial real con plantilla local cifrada** (MobileFaceNet TFLite + ML Kit para detectar el rostro).
2. La marcación queda en estado **PENDIENTE** generando un código **QR**.
3. El administrador o supervisor en el punto de control escanea dicho QR para confirmar la asistencia presencial, actualizando el estado a **EXITOSO / PUNTUAL / TARDANZA**.

---

## 2. Situación Actual de la Aplicación

Actualmente el proyecto se encuentra en la versión **Beta 03**, completamente funcional y estable para pruebas operativas en dispositivos Android físicos.

* **Estado de Compilación:** 100% libre de errores de compilación (`./gradlew assembleDebug` ejecutable).
* **Base de Datos:** MongoDB Atlas, accedida únicamente desde el backend Node propio (auth scrypt + tokens HMAC).
* **Procesamiento On-Device:** ML Kit localiza el rostro y MobileFaceNet TFLite genera el embedding; la plantilla se cifra en Android Keystore.
* **Multi-tenant:** Aislamiento lógico de datos por empresa (`companyId`) activo en todas las colecciones.

---

## 3. Funcionalidades Actuales (Lo que TIENE)

### 👨‍💼 Módulo Administrador
* **Autenticación y Sesión Persistente:** Detección automática de rol (`admin`) al iniciar sesión o reabrir la aplicación.
* **Dashboard con Métricas en Tiempo Real:** Visualización de total de empleados, asistencias del día, tardanzas, accesos rápidos y animaciones Lottie.
* **Escáner de Validación QR:** Integración de CameraX + ZXing para leer el QR del empleado y cambiar el estado de la asistencia de `PENDIENTE` a `EXITOSO` de forma instantánea.
* **Gestión de Empleados (CRUD):** Registro de empleados en MongoDB mediante el backend, edición de datos y eliminación con diálogo de confirmación.
* **Gestión de Turnos Personalizados:** Creación, edición y eliminación de turnos horarios por empresa con configuración de hora de entrada, hora de salida y margen de tolerancia (`ShiftSettingsScreen`).
* **Asignador de Turnos:** Interfaz interactiva (`ShiftManagerScreen`) para asignar o desasignar turnos a empleados específicos.
* **Gestión de Sedes y Geocercas (Google Maps):** Configuración visual de sedes en mapa interactivo con búsqueda de direcciones por Geocoder y ajuste de radio de geocerca (20m a 500m).
* **Historial Global y Reportes CSV:** Filtros avanzados por fecha, empleado, estado y exportación directa a archivo `.csv` mediante `Intent.ACTION_SEND`.
* **Marcación Manual de Emergencia:** Registro manual directo desde el Dashboard para contingencias.

### 👷‍♂️ Módulo Empleado
* **Marcación Biométrica Híbrida (`ScanScreen`):**
  * **Tab Huella Dactilar:** Integración nativa con `BiometricPrompt` del sistema Android.
  * **Tab Reconocimiento Facial:** Inscripción de plantilla facial (3 muestras), comparación por distancia euclidiana y almacenamiento cifrado en Android Keystore. ML Kit localiza el rostro y MobileFaceNet TFLite genera el embedding; la asistencia solo se registra si el rostro coincide con la plantilla del usuario.
* **Panel del Empleado (`EmployeeDashboard`):** Muestra el estado actual de la asistencia del día (⏳ Pendiente / ✅ Exitoso / ⏱️ Tardanza / ⚠️ Sin registro).
* **Carnet QR Personal:** Visualización del código QR en alta definición para ser escaneado por el administrador.
* **Historial Personal:** Consulta de registros históricos propios filtrados por su UID local.
* **Edición de Perfil:** Permite actualizar el nombre visible del usuario.

### 🌐 Funcionalidades Globales
* **Soporte Bilingüe (Español / Inglés):** Cambio dinámico de idioma en toda la interfaz mediante `CompositionLocal`.
* **Soporte para Modo Oscuro / Claro:** Paleta de colores adaptativa en Material 3 con tokens globales en `Theme.kt`.
* **Animaciones Lottie:** 8 animaciones dinámicas reales integradas en assets para mejorar la experiencia de usuario.
* **Notificaciones:** Consulta REST al backend (polling cada 4 s); no depende de Firebase Cloud Messaging.

---

## 4. Cosas de las que CARECE (Pendientes y Limitaciones)

| Área | Carencia / Limitación Actual | Impacto |
|---|---|---|
| **Build de Producción** | No cuenta con Keystore de firma de release ni reglas de optimización / ofuscación ProGuard / R8 en `build.gradle.kts`. | Imposibilita la publicación directa en Google Play Store en su estado actual. |
| **Pruebas Automatizadas** | Falta de suite de pruebas unitarias (`BioSecureViewModelTest`), pruebas de interfaz (Compose UI Tests) e integración. | Riesgo de regresiones al agregar nuevas funcionalidades. |
| **Detección de Vida Facial (Liveness Detection)** | ML Kit detecta contornos y presencia de rostro, pero **no valida parpadeo ni movimiento dinámico**. | Posibilidad de suplantación mediante fotos impresas o pantallas de alta resolución. |
| **Validación Servidor de Geocerca** | La geocerca se visualiza en Google Maps y el backend recalcula la distancia, pero un GPS simulado con `accuracy` baja aún podría falsear la ubicación. | Un usuario con conocimientos técnicos podría falsear sus coordenadas GPS. |
| **Sincronización Offline Completa** | La app usa REST contra el backend; sin conectividad no se puede validar turnos ni registrar asistencia. | Dificultad para operar en zonas sin ninguna conectividad a internet. |
| **Filtro Sede-First en Admin** | El administrador ve el total global de la empresa, pero no puede filtrar todo el Dashboard dinámicamente por una Sede específica seleccionada en un desplegable. | Menor practicidad para administradores con decenas de sedes simultáneas. |

---

## 5. Vulnerabilidades y Riesgos de Seguridad

> [!CAUTION]
> **Atención:** Antes de desplegar este sistema en un entorno de producción real, deben corregirse las siguientes vulnerabilidades identificadas durante la fase Beta 03.

### 🔴 1. Código QR Estático (Riesgo de Reutilización / Replay Attack)
* **Vulnerabilidad:** El código QR generado para el empleado contiene únicamente el `uid` en texto plano sin firmar.
* **Riesgo:** Un empleado podría tomar una captura de pantalla a su QR y enviársela a un compañero para que este lo muestre al administrador, marcando asistencia sin estar físicamente en el lugar si el supervisor no verifica el rostro del portador.
* **Solución Recomendada:** Implementar QRs efímeros con expiración (ej. TOTP o Tokens JWT firmados que duren 30 segundos) generados en el backend Node propio.

### 🔴 2. Secreto de firma de sesión por defecto
* **Vulnerabilidad:** `JWT_SECRET` tiene un valor por defecto en `server.js` si no se define `AUTH_SECRET` en el entorno.
* **Riesgo:** Cualquiera que conozca ese valor podría firmar tokens falsos y suplantar a un administrador.
* **Solución Recomendada:** Definir `AUTH_SECRET` con un valor aleatorio largo en el servidor antes de publicarlo. El backend ya valida roles, empresa (`companyId`) y expiración de tokens.

### 🟡 3. Clave API de Google Maps Expuesta en `AndroidManifest.xml`
* **Vulnerabilidad:** La API Key de Google Maps está escrita en texto plano dentro del manifiesto de la aplicación.
* **Riesgo:** La clave podría ser extraída mediante descompilación del APK y ser utilizada por terceros no autorizados.
* **Solución Recomendada:** Restringir la clave API en Google Cloud Console mediante **Restricciones de Aplicación Android** (asociando la huella SHA-1 del certificado y el nombre del paquete `com.biosecure.app`).

### 🟡 4. Falta de Algoritmo Anti-Spoofing (Liveness Test)
* **Vulnerabilidad:** El detector facial de ML Kit confirma un rostro válido con puntos clave (ojos, nariz, boca), pero no exige acciones de prueba de vida.
* **Riesgo:** Se podría engañar a la cámara frontal mostrando una fotografía estática en alta resolución de la cara del empleado.
* **Solución Recomendada:** Incorporar verificación de parpadeo (`smilingProbability` / `leftEyeOpenProbability`) o movimientos de cabeza solicitados de forma aleatoria antes de habilitar el botón de envío.

---

## 6. Arquitectura y Stack Tecnológico

```
                          ┌──────────────────────────┐
                          │   Jetpack Compose UI     │
                          └────────────┬─────────────┘
                                       │
                          ┌────────────▼─────────────┐
                          │    BioSecureViewModel    │
                          └────────────┬─────────────┘
                                       │
          ┌────────────────────────────┼────────────────────────────┐
          ▼                            ▼                            ▼
┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐
│  AuthRepository  │         │AttendanceRepo    │         │ CompanyRepo      │
└─────────┬────────┘         └─────────┬────────┘         └─────────┬────────┘
          │                            │                            │
          └────────────────────────────┼────────────────────────────┘
                                       │
                         ┌─────────────▼─────────────┐
                         │  Backend Node (REST/HTTP) │
                          └────────────┬──────────────┘
                                       │
                          ┌────────────▼──────────────┐
                          │      MongoDB Atlas        │
                         └───────────────────────────┘
```

* **Lenguaje:** Kotlin `2.2.10`
* **UI Framework:** Jetpack Compose BOM `2026.02.01` + Material 3
* **Min SDK / Target SDK:** API 26 (Android 8.0 Oreo) / API 36
* **Arquitectura:** MVVM + Clean Architecture parcial con Repository Pattern
* **Gestión de Estado:** `StateFlow` + `viewModelScope`
* **Inyección de Dependencias:** `BioSecureViewModelFactory` manual
* **Librerías Clave:**
  * Retrofit + OkHttp (REST al backend Node)
  * TensorFlow Lite `2.17.0` (MobileFaceNet) + ML Kit Face Detection `16.1.7`
  * CameraX `1.3.1`
  * Google ML Kit Face Detection `16.1.7`
  * AndroidX Biometric `1.2.0-alpha05`
  * ZXing Android Embedded `4.3.0`
  * Google Maps Compose `4.4.1`
  * Lottie Compose `6.4.0`

---

## 7. Base de Datos MongoDB Atlas

### Colecciones Principales:
* **`users/{uid}`**: Perfil del usuario, rol (`admin` / `employee`), empresa asignada (`companyId`), turno (`shiftId`), sede (`sedeId`) y token FCM.
* **`attendances/{docId}`**: Historial de marcaciones con estado (`PENDIENTE`, `EXITOSO`, `PUNTUAL`, `TARDANZA`), tipo (`HUELLA`, `FACIAL`, `QR`), fecha, hora y empresa.
* **`companies/{companyId}`**: Configuración de la empresa, hora de entrada por defecto, tolerancia y lista de turnos configurados.
* **`sedes/{sedeId}`**: Configuración de ubicaciones geográficas con coordenadas lat/lng y radio de geocerca en metros.

---

## 8. Estructura del Proyecto

```
com.biosecure.app/
├── MainActivity.kt                      # FragmentActivity para soporte de BiometricPrompt
├── data/network/ApiConfig.kt           # URL pública del backend
├── data/
│   ├── model/                          # Data Classes (User, Attendance, Shift, Sede, Company)
│   ├── prefs/                          # Jetpack DataStore para preferencias de tema
│   └── repository/                     # Repositorios de datos desacoplados
└── ui/
    ├── components/                     # Componentes reutilizables (CameraPreview, LottieIcon)
    ├── navigation/                     # NavGraph.kt + AdminRouteGuard
    ├── screens/                        # Pantallas separadas por módulos (admin, employee, scan, etc.)
    ├── theme/                          # Material 3 Theme, Color.kt, Type.kt, LocalAppLanguage.kt
    └── viewmodel/                      # BioSecureViewModel y ScanViewModel
```

---

## 9. Instalación y Configuración

### Requisitos Previos:
* Android Studio Narwhal (2025+) o posterior.
* JDK 19 configurado (`JAVA_HOME`).
* Dispositivo móvil físico con Android 8.0+ y sensor de huella dactilar.

### Pasos para Ejecutar:
1. Clonar el repositorio.
2. Arrancar el backend: `server\start.cmd` (o `server\start-public.cmd` para exponerlo con Cloudflare Tunnel).
3. Sincronizar Gradle en Android Studio.
4. Compilar e instalar en el dispositivo físico mediante:
   ```bash
   ./gradlew assembleDebug
   ```

### Red / URL del backend
* La app resuelve la URL pública desde `api-url.txt` del repositorio (raw de GitHub), con caché local y valor por defecto.
* Si el túnel se reinicia y cambia la URL, actualiza `api-url.txt` y haz push: la app la recoge al abrir, sin recompilar.
* Para pruebas locales por USB: `adb reverse tcp:3001 tcp:3001` y `http://127.0.0.1:3001/` en `ApiConfig.DEFAULT_BASE_URL`.
* Para WiFi local: usa la IP LAN del PC, por ejemplo `http://192.168.18.5:3001/`.

---

## 10. Equipo de Desarrollo

| Integrante | Rol en el Proyecto | Institución |
|---|---|---|
| **Nicolas Carrillo Chambi** | Líder Técnico & Arquitectura | TECSUP |
| **Sergio Ponce Moron** | UI/UX Developer & Jetpack Compose | TECSUP |
| **Marco Figueroa Cordova** | Integración Biométrica & ML Kit | TECSUP |
| **Carlos Moran Guillen** | Backend Node & MongoDB Atlas | TECSUP |
| **Adrian Flores Cama** | ViewModel & Navigation Compose | TECSUP |
