# BioSecure
> Sistema de asistencia biométrica empresarial multi-empresa (SaaS)

---

## Demo en video

[![BioSecure Demo](https://img.youtube.com/vi/nNoXH3v_XNI/0.jpg)](https://youtu.be/nNoXH3v_XNI)

> Ver demo completo en YouTube: https://youtu.be/nNoXH3v_XNI

---

## Capturas de pantalla

Las capturas de pantalla del proyecto se encuentran en la carpeta [`screenshots/`](screenshots/).

---

## Índice
1. [¿Qué es BioSecure?](#1-qué-es-biosecure)
2. [¿Cómo funciona?](#2-cómo-funciona)
3. [Requisitos](#3-requisitos)
4. [Instalación](#4-instalación)
5. [Tecnologías usadas](#5-tecnologías-usadas)
6. [Funcionalidades actuales](#6-funcionalidades-actuales)
7. [Widgets nuevos](#7-widgets-nuevos)
8. [Estructura del proyecto](#8-estructura-del-proyecto)
9. [Flujo completo de la app](#9-flujo-completo-de-la-app)
10. [Base de datos Firestore](#10-base-de-datos-firestore)
11. [Próximamente](#11-próximamente)
12. [Equipo](#12-equipo)

---

## 1. ¿Qué es BioSecure?

BioSecure es una aplicación Android nativa para el registro de asistencia biométrica empresarial. Permite a las empresas controlar la entrada de sus empleados mediante tres métodos de verificación: **huella dactilar**, **reconocimiento facial con ML Kit** y **códigos QR vinculados al UID de Firebase**.

El sistema opera con dos roles diferenciados — Admin y Empleado — cuyo acceso es determinado automáticamente desde Firestore al iniciar sesión. Toda la información se guarda en tiempo real en Firebase Firestore. La arquitectura está diseñada para escalar a múltiples empresas con aislamiento completo de datos por `companyId` (modelo SaaS multi-tenant).

**Stack principal:** Kotlin · Jetpack Compose · MVVM · Firebase Auth + Firestore · CameraX · ML Kit · ZXing · Lottie · Google Maps

---

## 2. ¿Cómo funciona?

### Admin
1. Inicia sesión — Firestore detecta el rol `"admin"` automáticamente
2. Accede al Dashboard con métricas en tiempo real, historial y accesos rápidos
3. **Gestiona turnos:** crea, edita y elimina turnos con nombre y rango horario (`ShiftSettingsScreen`); asigna empleados a turnos individualmente (`ShiftManagerScreen`)
4. **Gestiona sedes:** define ubicaciones en mapa interactivo con Google Maps, geocerca visual y slider de radio (`SedesScreen`)
5. Registra nuevos empleados: crea la cuenta en Firebase Auth (secondary app) y guarda el perfil en Firestore incluyendo el turno asignado
6. Cuando un empleado marca asistencia y queda en estado **PENDIENTE**, el admin escanea su QR con `QRScannerScreen` → el registro pasa automáticamente a **EXITOSO**
7. Exporta el historial filtrado como CSV desde `HistoryScreen`
8. También puede registrar asistencias manualmente desde el Dashboard

### Empleado
1. Inicia sesión — Firestore detecta el rol `"employee"` automáticamente
2. La sesión se mantiene activa entre reinicios (sesión persistente via Splash)
3. Desde `ScanScreen` elige el método de marcación:
   - **Huella dactilar:** activa `BiometricPrompt`; al autenticarse registra asistencia en estado PENDIENTE
   - **Reconocimiento facial:** ML Kit analiza los frames de la cámara frontal; el borde se vuelve verde al detectar rostro; al confirmar registra asistencia en estado PENDIENTE
4. Navega automáticamente a **`EmployeeDashboard`** donde ve su estado del día (PENDIENTE / EXITOSO / TARDANZA / sin registro)
5. En estado PENDIENTE: espera que el admin escanee su QR; puede ver su QR desde `AttendanceCard` en el historial
6. Pulsa "Actualizar estado" para refrescar sin salir de la pantalla
7. Puede editar su nombre de perfil desde `SettingsScreen`

---

## 3. Requisitos

| Requisito | Detalle |
|---|---|
| Android Studio | Narwhal 2025 o superior (recomendado) |
| JDK | 19 o superior (`JAVA_HOME` configurado) |
| Android mínimo | API 26 — Android 8.0 Oreo |
| Android objetivo | API 36 |
| `google-services.json` | Colocar en `app/` — solicitarlo al líder del equipo |
| Firebase Console | Authentication (Email/Password) + Firestore habilitados |
| Google Maps | Clave API con "Maps SDK for Android" habilitado en Google Cloud Console |
| Dispositivo | Con sensor de huella dactilar para el flujo biométrico |

> **Nota JAVA_HOME:** Si el build falla con `ERROR: JAVA_HOME is set to an invalid directory`, usa la terminal integrada de Android Studio o fija manualmente:
> ```powershell
> $env:JAVA_HOME = "C:\Program Files\Java\jdk-19"
> .\gradlew.bat assembleDebug
> ```

> **Nota build:** Usar siempre `./gradlew assembleDebug`. El comando `gradlew build` falla en Windows por una ruta con backslash en `gradle.properties` (bug pre-existente, no bloqueante).

---

## 4. Instalación

1. **Clonar el repositorio**
   ```bash
   git clone <url-del-repo>
   cd BioSecure
   ```

2. **Abrir en Android Studio**
   Abre la carpeta `BioSecure/` directamente como proyecto existente.

3. **Agregar `google-services.json`**
   Coloca el archivo en `app/google-services.json`. Solicítalo al líder técnico.
   Verifica que `package_name` sea `com.biosecure.app`.

4. **Sincronizar Gradle**
   Haz clic en *Sync Project with Gradle Files* o ejecuta:
   ```bash
   ./gradlew assembleDebug
   ```

5. **Configurar Firebase Console**
   - **Authentication:** habilitar proveedor `Email/Password`
   - **Firestore:** crear colección `users` con al menos un documento admin:
     ```
     users/{uid}
       email: "admin@empresa.com"
       role: "admin"
       name: "Nombre Admin"
       companyId: "MiEmpresa"
     ```
   - Crear documento de empresa en colección `companies`:
     ```
     companies/MiEmpresa
       name: "Mi Empresa"
       checkInStart: "08:00"
       checkInEnd: "09:00"
       shifts: []
     ```
   - Reglas mínimas de Firestore:
     ```
     rules_version = '2';
     service cloud.firestore {
       match /databases/{database}/documents {
         match /{document=**} {
           allow read, write: if request.auth != null;
         }
       }
     }
     ```

6. **Instalar en dispositivo**
   ```powershell
   $adb = "C:\Users\<usuario>\AppData\Local\Android\Sdk\platform-tools\adb.exe"
   & $adb uninstall com.biosecure.app
   & $adb install app\build\outputs\apk\debug\app-debug.apk
   ```

---

## 5. Tecnologías usadas

### Lenguaje y UI
| Tecnología | Versión |
|---|---|
| Kotlin | `2.2.10` |
| Jetpack Compose BOM | `2026.02.01` |
| Material 3 | gestionado por BOM |
| Navigation Compose | `2.9.0` |
| Lottie Compose | `6.4.0` |

### Android y Gradle
| Componente | Versión |
|---|---|
| Android Gradle Plugin (AGP) | `9.1.1` |
| `core-ktx` | `1.18.0` |
| `activity-compose` | `1.13.0` |
| `lifecycle-runtime-ktx` | `2.10.0` |
| `lifecycle-runtime-compose` | `2.10.0` |
| `lifecycle-viewmodel-compose` | `2.10.0` |
| `fragment-ktx` | `1.8.5` |

### Firebase
| Librería | Versión |
|---|---|
| Firebase BOM | `33.1.0` |
| Firebase Authentication (KTX) | gestionado por BOM |
| Firebase Firestore (KTX) | gestionado por BOM |
| Firebase Messaging (KTX) | gestionado por BOM |
| Google Services Plugin | `4.4.2` |

### Biometría y Cámara
| Librería | Versión |
|---|---|
| `androidx-biometric` | `1.2.0-alpha05` |
| CameraX core / camera2 / lifecycle / view | `1.3.1` |
| ML Kit face-detection | `16.1.7` |

### Mapas y Ubicación
| Librería | Versión |
|---|---|
| Maps Compose | `4.4.1` |
| Play Services Maps | `18.2.0` |
| Play Services Location | `21.2.0` |

### QR y Red
| Librería | Versión |
|---|---|
| ZXing core | `3.5.2` |
| ZXing Android Embedded | `4.3.0` |
| Retrofit | `2.9.0` |
| Retrofit Gson Converter | `2.9.0` |
| Coil Compose | `2.6.0` |

### Patrones y Arquitectura
- **MVVM** con `StateFlow` + `viewModelScope` (sin LiveData)
- `BioSecureViewModel` + `ScanViewModel`
- `BioSecureViewModelFactory` con inyección manual (sin Hilt/Koin)
- `AuthRepository`, `AttendanceRepository` y `CompanyRepository` como capa de datos
- **Multi-tenant:** campo `companyId` como clave de partición en `users/`, `attendances/` y `sedes/`
- `FragmentActivity` en `MainActivity` (requerido por `BiometricPrompt`)

---

## 6. Funcionalidades actuales

### Admin
- Splash screen con sesión persistente y navegación automática por rol
- Login con detección automática de rol desde Firestore (`users/{uid}.role`)
- Dashboard con métricas reales (empleados, asistencias, tardanzas), estadísticas y accesos rápidos con animaciones Lottie
- Registrar empleados: crea cuenta Firebase Auth (secondary app) + perfil Firestore `users/{uid}` con turno asignado
- Lista de empleados con edición (formulario prellenado) y eliminación (con `AlertDialog` de confirmación)
- **Configurar turnos:** lista, añade, edita y elimina turnos con nombre y rango horario por empresa (`ShiftSettingsScreen`)
- **Gestor de turnos:** asigna y desasigna empleados a turnos con `AssignShiftBottomSheet` (`ShiftManagerScreen`)
- **Sedes:** mapa Google Maps interactivo, geocerca visual con `Circle`, buscador de direcciones con `Geocoder`, slider de radio 20m–500m (`SedesScreen`)
- Escanear QR del empleado con cámara trasera (CameraX + ZXing): confirma registros PENDIENTE → EXITOSO
- Historial de asistencias en tiempo real desde Firestore filtrado por empresa
- Exportar historial como CSV respetando búsqueda y filtros activos (`Intent.ACTION_SEND`)
- Registro manual de asistencia desde Dashboard (`ManualAttendanceDialog`)
- Soporte de idioma EN / ES completo en todas las pantallas

### Empleado
- Splash screen con sesión persistente y navegación automática por rol
- Login con detección automática de rol desde Firestore
- Pantalla de escaneo con dos tabs:
  - **Huella Dactilar:** activa `BiometricPrompt`; al verificarse crea registro PENDIENTE y navega a `EmployeeDashboard`
  - **Escaneo Facial:** ML Kit analiza frames en tiempo real; borde verde al detectar rostro, rojo si no; botón habilitado solo con rostro presente; crea registro PENDIENTE al confirmar
- **`EmployeeDashboard`:** muestra estado de asistencia del día (PENDIENTE / EXITOSO / PUNTUAL / TARDANZA / sin registro); botón "Actualizar estado" con ícono Refresh; acceso directo al historial
- Historial de asistencias propias filtrado por UID desde Firestore (tiempo real)
- QR personal visible por registro en `AttendanceCard` del historial
- Editar nombre de perfil desde `SettingsScreen` (lápiz → `AlertDialog` → Firestore)
- Reloj en tiempo real en `ScanScreen` (formato 12h AM/PM)

### Ambos roles
- Animaciones Lottie en todas las pantallas principales (fingerprint, face scan, check success, empty state, etc.)
- Tema Material 3 completo — modo oscuro y claro con tokens globales en `Theme.kt`
- Soporte de idioma EN / ES mediante `LocalAppLanguage`
- Notificaciones push via Firebase Cloud Messaging (FCM) al registrar asistencias

---

## 7. Widgets nuevos

Esta versión Beta04 incorpora nuevos componentes y widgets de UI que mejoran la experiencia del usuario:

### `PullToRefreshBox` — Dashboard Admin
El `DashboardScreen` ahora envuelve su contenido en un `PullToRefreshBox` (Material3 Experimental). Deslizar hacia abajo recarga empleados, asistencias y configuración de empresa sin salir de la pantalla.

### `WeeklyChartCard` — Gráfica semanal con Vico
Se integró la librería **Vico** (`com.patrykandpatrick.vico`) para renderizar un gráfico de líneas (`CartesianChartHost` + `LineCartesianLayer`) con los datos de asistencia de la semana. Aparece en la sección "Estadísticas Semanales" del Dashboard Admin.

### `DashboardSkeleton` — Efecto shimmer de carga
Mientras los datos del dashboard se cargan por primera vez, se muestra un skeleton animado usando el modifier `shimmerEffect()` (definido en `ui/theme/Shimmer.kt`). El efecto es un gradiente lineal animado con `LinearEasing` a 1200ms.

### `Today's Status Card` — EmployeeDashboard
Tarjeta que muestra el estado de asistencia del día con íconos contextuales:
- 📋 Sin registro — indica que el empleado aún no ha marcado
- ⏳ Pendiente de confirmación — registro creado, esperando al admin
- 🚫 Inasistencia registrada — estado FALLIDO o INASISTENCIA
- ✅ Asistencia confirmada — EXITOSO o PUNTUAL con hora de entrada

### `Weekly Performance Card` — EmployeeDashboard
Tarjeta con siete círculos (L M X J V S D / M T W T F S S) que representan cada día de la semana actual. El color del círculo indica el estado:
- **Verde** (✓) — EXITOSO o PUNTUAL
- **Naranja** (!) — TARDANZA
- **Rojo** (✗) — FALLIDO o INASISTENCIA
- **Gris** — sin registro o día futuro

Incluye contador `N / 7 días con asistencia confirmada`.

### `swipeToNavigate` — Navegación por gestos
Modifier extension (`ui/components/SwipeNavigation.kt`) que detecta arrastres horizontales con `detectHorizontalDragGestures`. Threshold de 80 px. Implementado en `DashboardScreen`, `EmployeeDashboard` y `ScanScreen` para navegar entre tabs sin tocar la barra inferior.

### `FilterChip` — Selector de sedes
Fila de chips horizontales desplazables en el Dashboard Admin que permiten filtrar las métricas y el historial por sede. Incluye chip "Todas las Sedes" que resetea el filtro.

### `AnimatedVisibility` — Entrada animada de métricas
Las `MetricCard` del Dashboard Admin aparecen con `fadeIn() + slideInVertically` al cargar los datos, usando `AnimatedVisibility` de Compose Animation.

### `AI Analysis Card` — Análisis inteligente
Tarjeta en la sección "Análisis IA" del Dashboard Admin que muestra recomendaciones generadas por el ViewModel basadas en los patrones de asistencia de la empresa.

---

## 8. Estructura del proyecto

```
app/src/main/java/com/biosecure/app/
│
├── MainActivity.kt
├── BioSecureMessagingService.kt       # FCM: canal de notificaciones, onMessageReceived, onNewToken
│
├── data/
│   ├── model/
│   │   ├── Attendance.kt              # Attendance, ConfirmationData, ScanType, AttendanceStatus
│   │   ├── User.kt                    # Modelo de usuario con companyId, shiftId, shiftName
│   │   ├── Shift.kt                   # Modelo de turno: name, checkInStart, checkInEnd, toleranceMin
│   │   ├── Sede.kt                    # Modelo de sede: id, name, lat, lng, radioMetros, activa
│   │   └── UserRequests.kt            # DTOs para Retrofit (temporal)
│   ├── network/
│   │   ├── ApiService.kt              # Interfaz Retrofit (DummyJSON — temporal)
│   │   └── RetrofitInstance.kt        # Singleton Retrofit
│   └── repository/
│       ├── AuthRepository.kt          # Firebase Auth: login, logout, getRoleForCurrentUser()
│       ├── AttendanceRepository.kt    # Firestore: asistencias, empleados, observadores en tiempo real
│       └── CompanyRepository.kt       # Firestore: empresa, turnos, sedes
│
└── ui/
    ├── components/
    │   ├── CameraPreview.kt           # CameraX + ML Kit ImageAnalysis + onFaceDetected callback
    │   └── LottieIcon.kt              # Composable genérico para animaciones Lottie desde assets/
    ├── navigation/
    │   └── NavGraph.kt                # Rutas: Splash, Login, Dashboard, Scan, History, Settings,
    │                                  #   Confirmation, QRScanner, EmployeeQR, AdminEmployeeQR,
    │                                  #   EmployeeList, EditEmployee, RegisterEmployee,
    │                                  #   ShiftSettings, ShiftManager, Sedes, EmployeeDashboard
    ├── screens/
    │   ├── admin/
    │   │   ├── AdminEmployeeQRScreen.kt   # QR del empleado recién registrado + compartir
    │   │   ├── EditEmployeeScreen.kt      # Formulario de edición con precarga desde Firestore
    │   │   ├── EmployeeList.kt            # Lista de empleados con editar/eliminar
    │   │   ├── QRScannerScreen.kt         # Escáner QR admin (CameraX + ZXing): PENDIENTE → EXITOSO
    │   │   ├── RegisterEmployeeScreen.kt  # Registro con Firebase Auth secondary app + turno
    │   │   ├── SedesScreen.kt             # Google Maps + Geocoder + Circle geocerca + slider radio
    │   │   ├── ShiftManagerScreen.kt      # Asignación de empleados a turnos (AssignShiftBottomSheet)
    │   │   └── ShiftSettingsScreen.kt     # CRUD de turnos con TimePickerModal
    │   ├── confirmation/
    │   │   └── ConfirmationScreen.kt      # Modo empleado (QR) / Modo admin (Lottie check animado)
    │   ├── dashboard/
    │   │   └── DashboardScreen.kt         # Métricas reales, accesos rápidos, Lottie icons, registro manual
    │   ├── employee/
    │   │   ├── EmployeeDashboard.kt       # Estado de asistencia del día + Refresh + historial
    │   │   └── QRScreen.kt               # QR personal del empleado 512×512
    │   ├── history/
    │   │   └── HistoryScreen.kt           # Historial desde Firestore, búsqueda, filtros, export CSV
    │   ├── login/
    │   │   └── LoginScreen.kt             # Login único con detección automática de rol
    │   ├── scan/
    │   │   └── ScanScreen.kt              # Tabs Huella / Facial + ML Kit + BiometricPrompt
    │   ├── settings/
    │   │   └── SettingsScreen.kt          # Toggle dark mode, editar nombre de perfil
    │   └── splash/
    │       └── SplashScreen.kt            # Logo penguin animación spring + sesión persistente
    ├── theme/
    │   ├── Color.kt
    │   ├── Theme.kt                       # LightColorScheme + DarkColorScheme con tokens Material 3
    │   └── Type.kt
    └── viewmodel/
        ├── BioSecureViewModel.kt          # Estado global: auth, users, attendances, shifts, sedes
        └── ScanViewModel.kt               # Reloj en tiempo real (formato hh:mm a)
```

---

## 9. Flujo completo de la app

### Inicio de sesión y detección de rol
```
App abre → SplashScreen (logo penguin + animación spring)
    ↓
¿Hay sesión activa? (FirebaseAuth.currentUser)
    ├── Sí → getRoleForCurrentUser() consulta users/{uid}.role en Firestore
    │       ├── "admin"    → Dashboard
    │       └── "employee" → EmployeeDashboard
    └── No → LoginScreen

LoginScreen → usuario ingresa email + contraseña → "Iniciar Sesión"
    → loginWithRole() autentica Firebase Auth + consulta users/{uid}
    → guarda role, name, uid, companyId en BioSecureViewModel
    → navega a Dashboard (admin) o EmployeeDashboard (employee)
```

### Flujo de asistencia: Empleado → Admin (QR obligatorio)
```
Empleado en ScanScreen
    ├── Tab Huella → BiometricPrompt.authenticate()
    │   └── Éxito → registerAttendance(HUELLA) → Firestore: status "PENDIENTE"
    └── Tab Facial → ML Kit detecta rostro en tiempo real
        ├── Sin rostro → borde rojo, botón deshabilitado
        └── Rostro detectado → borde verde, botón habilitado
            └── "REGISTRAR ENTRADA" → registerAttendance(FACIAL) → Firestore: status "PENDIENTE"

    ↓ (ambos flujos)
EmployeeDashboard:
    ├── Estado PENDIENTE → ícono ⏳ + "Esperando confirmación del administrador"
    │                    + hora de registro + botón "Ver mi historial"
    ├── Estado EXITOSO/PUNTUAL/TARDANZA → ✅ mensaje con detalle
    └── Sin registro hoy → PulsingBiometricIcon (animación ondas)

Admin en QRScannerScreen → apunta cámara al QR del empleado (desde AttendanceCard del historial)
    → ZXing MultiFormatReader lee el UID del QR
    → Busca registro PENDIENTE de hoy para ese UID
    → confirmAttendance() → doc se actualiza a "EXITOSO"
    → navega a ConfirmationScreen (modo admin)

ConfirmationScreen (modo admin):
    - Lottie animación check (iterations=1)
    - "Asistencia registrada exitosamente"
    - Card: Empleado | Hora de ingreso | Fecha
    - Botón "Cerrar"

Empleado pulsa "Actualizar estado" → EmployeeDashboard muestra ✅
```

### Flujo de registro de empleado (Admin)
```
Dashboard → "Registrar Empleado" → RegisterEmployeeScreen
    → Admin llena: nombre, apellido, email, contraseña, cargo, departamento, turno
    → FirebaseApp.getInstance("secondary") crea cuenta Auth sin cerrar sesión del admin
    → Guarda users/{uid} en Firestore: {email, role:"employee", name, department, cargo, companyId, shiftName}
    → secondaryAuth.signOut()
    → Navega a AdminEmployeeQRScreen con el UID

AdminEmployeeQRScreen:
    - QR 512×512 generado con ZXing QRCodeWriter
    - Botón "Compartir QR" → FileProvider + Intent.ACTION_SEND
    - Botón "Volver al Dashboard"
```

---

## 10. Base de datos Firestore

### Colección `companies/{companyId}`

| Campo | Tipo | Descripción |
|---|---|---|
| `name` | String | Nombre visible de la empresa |
| `checkInStart` | String | Hora de inicio de jornada (`HH:mm`) |
| `checkInEnd` | String | Hora límite para marcar PUNTUAL (`HH:mm`) — fallback si `shifts` está vacío |
| `shifts` | List | Lista de turnos (ver estructura abajo) |

```
companies/MiEmpresa
  ├── name: "Mi Empresa"
  ├── checkInStart: "08:00"
  ├── checkInEnd: "09:00"
  └── shifts: [
        { name: "Turno Mañana", checkInStart: "07:00", checkInEnd: "08:30", toleranceMin: 10 },
        { name: "Turno Tarde",  checkInStart: "13:00", checkInEnd: "14:00", toleranceMin: 5 }
      ]
```

> La lógica de detección busca el turno cuyo `checkInStart` es más cercano a la hora actual. Si `shifts` está vacío, usa `checkInEnd` del documento raíz como fallback.

---

### Colección `sedes/{sedeId}`

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | String | ID de la sede |
| `name` | String | Nombre de la sede |
| `lat` | Double | Latitud del centro del geocerca |
| `lng` | Double | Longitud del centro del geocerca |
| `radioMetros` | Int | Radio de la geocerca en metros (20–500) |
| `companyId` | String | ID del tenant |
| `activa` | Boolean | Si la sede está activa |
| `creadaEn` | Timestamp | Fecha de creación |

---

### Colección `users/{uid}`

| Campo | Tipo | Descripción |
|---|---|---|
| `email` | String | Correo del usuario |
| `role` | String | `"admin"` o `"employee"` |
| `name` | String | Nombre completo — fuente de verdad para toda la UI |
| `department` | String | Departamento del empleado |
| `cargo` | String | Cargo/título del empleado |
| `companyId` | String | ID del tenant — aísla datos entre empresas |
| `shiftName` | String | Nombre del turno asignado (opcional) |
| `fcmToken` | String | Token FCM para notificaciones push |

```
users/{uid}
  ├── email: string
  ├── role: string         ("admin" | "employee")
  ├── name: string
  ├── department: string
  ├── cargo: string
  ├── companyId: string    ← clave de partición multi-tenant
  ├── shiftName: string    ← turno asignado al empleado
  └── fcmToken: string     ← actualizado en cada sesión
```

> **Migración:** Los admins existentes deben tener `companyId` agregado manualmente en Firestore Console. Los empleados nuevos lo heredan automáticamente del admin que los registra.

---

### Colección `attendances/{docId}`

| Campo | Tipo | Descripción |
|---|---|---|
| `uid` | String | Firebase UID del empleado |
| `userName` | String | Nombre completo tomado de `users/{uid}.name` |
| `date` | String | Fecha en formato `dd/MM/yyyy` |
| `checkIn` | String | Hora en formato `HH:mm` |
| `status` | String | `PUNTUAL` · `TARDANZA` · `EXITOSO` · `FALLIDO` · `INASISTENCIA` · `PENDIENTE` |
| `type` | String | `HUELLA` · `FACIAL` · `QR` |
| `location` | String | `"Sede Central"` por defecto |
| `companyId` | String | ID del tenant |

```
attendances/{docId}
  ├── uid: string
  ├── userName: string
  ├── date: string          (dd/MM/yyyy)
  ├── checkIn: string       (HH:mm)
  ├── type: string          (HUELLA | FACIAL | QR)
  ├── location: string
  ├── status: string        (PUNTUAL | TARDANZA | EXITOSO | FALLIDO | INASISTENCIA | PENDIENTE)
  └── companyId: string     ← clave de partición multi-tenant
```

> El empleado crea el registro en estado `PENDIENTE`. El admin al escanear el QR llama `confirmAttendance()` que actualiza el status a `EXITOSO`.

---

### Colección `notifications/{id}`

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | String | `"attendance"` |
| `userName` | String | Nombre del empleado |
| `checkIn` | String | Hora de marcación |
| `timestamp` | Timestamp | `Timestamp.now()` |
| `read` | Boolean | `false` al crear |

---

### Reglas de seguridad (producción mínima)
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

---

## 11. Próximamente

| Funcionalidad | Prioridad | Descripción |
|---|---|---|
| **APK release firmado** | ALTA | Generar keystore + `signingConfigs` en `build.gradle.kts` + `assembleRelease` |
| **Verificar Maps API key** | ALTA | Confirmar que la clave en `AndroidManifest.xml` tiene "Maps SDK for Android" habilitado en Google Cloud Console |
| **Tests unitarios `BioSecureViewModel`** | MEDIA | Cubrir `login()`, `checkAttendanceStatus()`, `loadTodayAttendance()`, `assignShiftToEmployee()` con `kotlinx-coroutines-test` |
| **`LocalClipboardManager` deprecated** | MEDIA | Migrar a `LocalClipboard.current` (Compose 1.5+) |
| **Firebase Functions** | BAJA | Validación JWT del QR efímero + geofencing server-side |
| **Navegación Sede-First para admin** | BAJA | Admin selecciona sede → dashboard filtrado por esa sede |

---

## 12. Equipo

| Nombre | Rol |
|---|---|
| Nicolas Carrillo Chambi | Líder técnico / Arquitectura |
| Sergio Ponce Moron | UI / Jetpack Compose |
| Marco Figueroa Cordova | Integración biométrica / CameraX |
| Carlos Moran Guillen | Capa de datos / Firebase |
| Adrian Flores Cama | Navegación / ViewModel |

**Institución:** TECSUP
