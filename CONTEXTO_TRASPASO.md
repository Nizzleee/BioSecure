# BioSecure — Contexto de Traspaso

> Documento autónomo. Si solo puedes leer un archivo antes de retomar el proyecto, lee este.
> Última actualización: 2026-06-24.

---

## Qué es el proyecto

**BioSecure** es una app Android de registro de asistencia biométrica para empresas. Los empleados marcan su entrada con huella dactilar o reconocimiento facial; el administrador confirma escaneando el QR del empleado. Todo se persiste en Firebase Firestore con aislamiento multi-tenant por empresa (`companyId`).

- **Plataforma:** Android (Kotlin + Jetpack Compose)
- **Arquitectura:** MVVM, un ViewModel global (`BioSecureViewModel`) compartido por todas las pantallas
- **Backend:** Firebase Auth + Firestore (no hay servidor propio)
- **Package:** `com.biosecure.app`
- **Estado del build:** `assembleDebug` BUILD SUCCESSFUL — 0 errores

---

## Stack técnico — versiones exactas

### Android / Build

| Parámetro | Valor |
|---|---|
| `compileSdk` | 36 |
| `targetSdk` | 36 |
| `minSdk` | 26 (Android 8.0) |
| `versionCode` | 1 |
| `versionName` | 1.0 |
| Android Gradle Plugin | 9.1.1 |
| Kotlin | 2.2.10 |
| Java compatibility | VERSION_11 |

### Dependencias principales

| Librería | Versión |
|---|---|
| Compose BOM | 2026.02.01 |
| Navigation Compose | 2.9.0 |
| Lifecycle / ViewModel Compose | 2.10.0 |
| Activity Compose | 1.13.0 |
| Core KTX | 1.18.0 |
| Fragment KTX | 1.8.5 |
| Firebase BOM | 33.1.0 (gestiona auth, firestore, messaging, functions, storage) |
| Google Services plugin | 4.4.2 |
| CameraX (core / camera2 / lifecycle / view) | 1.3.1 |
| ML Kit Face Detection | 16.1.7 |
| BiometricPrompt | 1.2.0-alpha05 |
| ZXing Core (QR generación) | 3.5.2 |
| ZXing Android Embedded (QR scan) | 4.3.0 |
| Coil Compose (imágenes) | 2.6.0 |
| Retrofit + Gson | 2.9.0 |
| Maps Compose | 4.4.1 |
| Play Services Maps | 18.2.0 |
| Play Services Location | 21.2.0 |
| Lottie Compose | 6.4.0 |
| Vico Charts (compose-m3) | 2.0.0-alpha.22 |
| WorkManager | 2.9.0 |
| DataStore Preferences | 1.1.2 |

---

## Comandos esenciales

```powershell
# Compilar (SIEMPRE usar assembleDebug, no build — build falla con lint)
./gradlew assembleDebug

# APK de salida
app/build/outputs/apk/debug/app-debug.apk

# Instalar en dispositivo físico (adb NO está en PATH — usar ruta completa)
$adb = "C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apk = "C:\Users\User\Desktop\BioSecure\app\build\outputs\apk\debug\app-debug.apk"
& $adb uninstall com.biosecure.app   # desinstalar primero (adb install -r no es fiable en Samsung)
& $adb install $apk
& $adb shell am start -n com.biosecure.app/.MainActivity
# ID del dispositivo: & $adb devices
```

---

## Estructura de carpetas

```
BioSecure/
├── app/
│   ├── google-services.json           ← Firebase config (NO mover, NO regenerar salvo cambio de proyecto)
│   └── src/main/
│       ├── AndroidManifest.xml        ← Permisos + Maps API key + FileProvider + FCM service
│       ├── assets/                    ← 8 animaciones Lottie reales (lock, fingerprint_scan, face_scan,
│       │                                 success_check, empty_state, alert, qr_scan, team)
│       └── java/com/biosecure/app/
│           ├── MainActivity.kt        ← FragmentActivity (requerido por BiometricPrompt)
│           ├── BioSecureMessagingService.kt  ← FCM push notifications
│           ├── data/
│           │   ├── model/
│           │   │   ├── User.kt        ← data class con uid, role, companyId, shiftId, sedeId
│           │   │   ├── Attendance.kt  ← Attendance + ConfirmationData + AttendanceStatus + ScanType
│           │   │   ├── Shift.kt       ← id, name, startTime, endTime, toleranceMin
│           │   │   ├── Sede.kt        ← id, nombre, lat, lng, radioMetros, activa
│           │   │   ├── Company.kt     ← id, horaEntrada, toleranciaMin, geoFence
│           │   │   └── UserRequests.kt
│           │   ├── network/
│           │   │   ├── ApiService.kt        ← Retrofit (DummyJSON, legado)
│           │   │   └── RetrofitInstance.kt
│           │   ├── prefs/
│           │   │   └── ThemePreferences.kt  ← DataStore para dark mode
│           │   └── repository/
│           │       ├── AuthRepository.kt         ← Firebase Auth + lectura users/{uid}
│           │       ├── AttendanceRepository.kt   ← CRUD attendances/, users/ (getEmployees)
│           │       ├── CompanyRepository.kt      ← CRUD companies/ y sedes/
│           │       ├── UserRepository.kt         ← Retrofit DummyJSON (legado)
│           │       └── FirebaseFunctionsRepository.kt
│           ├── ui/
│           │   ├── components/
│           │   │   ├── CameraPreview.kt    ← CameraX + ML Kit face detection
│           │   │   └── LottieIcon.kt       ← Composable genérico: LottieIcon(assetName)
│           │   ├── navigation/
│           │   │   └── NavGraph.kt         ← Sealed class Screen + AdminRouteGuard + NavHost
│           │   ├── screens/
│           │   │   ├── splash/SplashScreen.kt
│           │   │   ├── login/LoginScreen.kt
│           │   │   ├── dashboard/DashboardScreen.kt    ← Solo admin
│           │   │   ├── scan/ScanScreen.kt              ← Admin + empleado
│           │   │   ├── history/HistoryScreen.kt        ← Admin + empleado
│           │   │   ├── settings/SettingsScreen.kt
│           │   │   ├── confirmation/ConfirmationScreen.kt
│           │   │   ├── admin/
│           │   │   │   ├── AdminEmployeeQRScreen.kt
│           │   │   │   ├── EditEmployeeScreen.kt   ← userId: String
│           │   │   │   ├── EmployeeList.kt
│           │   │   │   ├── QRScannerScreen.kt      ← CameraX + ZXing lector QR
│           │   │   │   ├── RegisterEmployeeScreen.kt
│           │   │   │   ├── SedesScreen.kt          ← Google Maps + Geocoder + Circle
│           │   │   │   ├── ShiftManagerScreen.kt   ← Asignación empleado↔turno
│           │   │   │   └── ShiftSettingsScreen.kt  ← CRUD turnos de empresa
│           │   │   └── employee/
│           │   │       ├── EmployeeDashboard.kt    ← Estado del día (null/PENDIENTE/EXITOSO)
│           │   │       └── QRScreen.kt
│           │   ├── theme/
│           │   │   ├── Theme.kt          ← BioSecureTheme + Light/DarkColorScheme completos
│           │   │   ├── Color.kt
│           │   │   ├── Type.kt
│           │   │   └── LocalAppLanguage.kt  ← CompositionLocal<String> para EN/ES
│           │   └── viewmodel/
│           │       ├── BioSecureViewModel.kt       ← ViewModel principal
│           │       └── ScanViewModel.kt            ← Reloj en tiempo real (hh:mm a)
│           └── workers/
│               ├── ShiftReminderWorker.kt
│               └── ShiftNotificationScheduler.kt
├── gradle/
│   └── libs.versions.toml   ← Catálogo de dependencias (fuente de verdad de versiones)
├── TEAM.md                  ← Historial completo de iteraciones, errores y arquitectura
└── CONTEXTO_TRASPASO.md     ← Este archivo
```

---

## Estructura de Firestore

### `users/{uid}`

```
uid           : String   — Firebase UID (es el id del documento)
email         : String   — Correo del usuario
role          : String   — "admin" | "employee"  (determina toda la navegación)
name          : String   — Nombre completo (fuente de verdad para la UI)
firstName     : String
lastName      : String
department    : String
cargo         : String
companyId     : String   — ID de la empresa (ej: "Pizzeria") — clave de partición multi-tenant
shiftId       : String   — ID del turno asignado
sedeId        : String   — ID de la sede asignada
fcmToken      : String   — Token FCM actualizado en cada inicio de sesión
isActive      : Boolean
```

> **Importante:** El campo `role` debe ser exactamente `"admin"` o `"employee"` sin espacios. Si falta o está mal, el login devuelve "Usuario no registrado".

### `attendances/{docId}`

```
uid           : String           — Firebase UID del empleado
userId        : String
userName      : String
companyId     : String           — Hereda del empleado (clave de partición)
date          : String           — "dd/MM/yyyy"
checkIn       : String           — "HH:mm"
checkOut      : String
status        : String           — "PUNTUAL" | "TARDANZA" | "INASISTENCIA" | "EXITOSO" | "FALLIDO" | "PENDIENTE"
type          : String           — "HUELLA" | "FACIAL" | "QR"
location      : String           — "Sede Central" (default)
latitude      : Double?
longitude     : Double?
```

### `companies/{companyId}`

```
name          : String   — Nombre visible
horaEntrada   : String   — "HH:mm" hora de inicio de jornada
toleranciaMin : Int      — Minutos de tolerancia para PUNTUAL
timezone      : String   — "UTC-5"
geoFence: {
  latitude    : Double
  longitude   : Double
  radius      : Double   — en metros
}
shifts        : Array    — Lista de turnos (ver abajo)
```

Ejemplo de creación manual en Firestore Console:
```
companies/Pizzeria
  ├── name: "Pizzería El Buen Sabor"
  ├── horaEntrada: "08:00"
  ├── toleranciaMin: 15
  └── shifts: [
        { id: "t1", name: "Turno Mañana", startTime: "07:00", endTime: "09:00", toleranceMin: 15 },
        { id: "t2", name: "Turno Tarde",  startTime: "13:00", endTime: "14:30", toleranceMin: 10 }
      ]
```

### `sedes/{sedeId}`

```
id            : String
nombre        : String
lat           : Double
lng           : Double
radioMetros   : Int      — Radio de geocerca en metros (20–500)
activa        : Boolean
creadaEn      : Timestamp
```

### `notifications/{id}`

```
type          : String      — "attendance"
userName      : String
checkIn       : String
timestamp     : Timestamp
read          : Boolean     — false al crear
```

---

## Flujos principales

### 1. Login por rol

```
LoginScreen (email + password)
  ↓ viewModel.login()
  ↓ AuthRepository.loginWithRole()
    → FirebaseAuth.signInWithEmailAndPassword()
    → Firestore: users/{uid} → lee role, name, companyId
  ↓ BioSecureViewModel popula:
      _currentRole, _currentUserName, _currentUserUid, _currentCompanyId
  ↓ NavGraph navega según rol:
      "admin"    → Screen.Dashboard
      "employee" → Screen.EmployeeDashboard

Sesión persistente (al reabrir la app):
  SplashScreen → viewModel.loadSessionBlocking()
    → AuthRepository.getUserData() — consulta Firestore con el usuario Firebase activo
    → navega a Dashboard o EmployeeDashboard según rol sin pasar por Login
```

### 2. Flujo biométrico → PENDIENTE → confirmación QR

```
[EMPLEADO]
ScanScreen (Tab 0: Huella, Tab 1: Facial)
  ↓ Tab 0: BiometricPrompt.authenticate() → onAuthenticationSucceeded
  ↓ Tab 1: ML Kit detecta rostro → botón se habilita → onClick
  ↓ viewModel.registerAttendance(ScanType.HUELLA | FACIAL)
    → Firestore attendances/: status = "PENDIENTE"
  ↓ navega a Screen.EmployeeDashboard

EmployeeDashboard (rama PENDIENTE)
  → Muestra: icono ⏳ + "Asistencia registrada" + "Esperando confirmación del administrador"
  → Botón "Ver mi historial" → EmployeeHistory
  → El QR del empleado sigue accesible como TextButton de emergencia

[ADMIN]
DashboardScreen → "Escanear QR"
  ↓ QRScannerScreen (CameraX + ZXing MultiFormatReader)
    → Lee UID del QR del empleado
    → Busca attendances/ donde uid=X, status="PENDIENTE", date=hoy
    → Si existe: confirmAttendance(attendanceId) → status = "EXITOSO"
    → Si no existe: crea nuevo registro con checkAttendanceStatus() → PUNTUAL | TARDANZA
    → viewModel.setConfirmationData(...)
    → navega a Screen.Confirmation

ConfirmationScreen (rol admin)
  → Muestra: ✅ animado + datos del empleado (nombre, hora, fecha)
  → Botón "Cerrar" → AdminScan

[EMPLEADO — actualiza estado]
EmployeeDashboard → "Actualizar estado"
  → viewModel.loadTodayAttendance()
  → Si status = "EXITOSO" → rama EXITOSO (✅ + mensaje)
```

### 3. Cálculo de estado de asistencia (PUNTUAL / TARDANZA)

```kotlin
// checkAttendanceStatus() en BioSecureViewModel
// 1. Obtiene los turnos de companies/{companyId}/shifts
// 2. Encuentra el turno cuyo startTime es más cercano a la hora actual
// 3. Si hora_actual <= turno.endTime + toleranceMin → PUNTUAL
//    Si hora_actual > turno.endTime + toleranceMin → TARDANZA
// 4. Fallback si no hay turnos: usa companies/{companyId}.horaEntrada directamente
```

### 4. Registro de empleado nuevo (Admin)

```
RegisterEmployeeScreen
  ↓ Admin llena: nombre, apellido, email, contraseña (min 6 chars), cargo, departamento, turno
  ↓ FirebaseApp.getInstance("secondary")  ← secondary app para NO cerrar sesión del admin
    → secondaryAuth.createUserWithEmailAndPassword(email, password)
    → uid = secondaryAuth.currentUser?.uid
    → secondaryAuth.signOut()
  ↓ Firestore users/{uid}: { role:"employee", name, email, companyId, cargo, department, shiftId }
  ↓ navega a AdminEmployeeQRScreen(uid)
    → Genera QR 512×512 con ZXing (encode del UID)
    → Botón "Compartir QR" → FileProvider + Intent.ACTION_SEND
```

### 5. Turnos (Shifts)

```
ShiftSettingsScreen  → CRUD de turnos (nombre + startTime/endTime + toleranceMin)
  Guarda en: companies/{companyId}/shifts (array de mapas)

ShiftManagerScreen   → Asignar/desasignar empleados a un turno
  Lee: firestoreEmployees (users/ donde role="employee" y companyId=X)
  Escribe: users/{uid}.shiftId = shift.id

checkAttendanceStatus() usa los turnos de la empresa para calcular PUNTUAL/TARDANZA
```

### 6. Sedes con Google Maps

```
SedesScreen → SedeEditorSheet
  → GoogleMap composable (maps-compose 4.4.1)
  → Toque en mapa → mueve pin (lat/lng)
  → OutlinedTextField + Geocoder (Dispatchers.IO) → busca dirección por texto
  → Slider 20m–500m → Circle de geocerca visual
  → Guardar → Firestore sedes/{id}
  
Maps API key: AIzaSyDZVVrZS3V-m9I6zHlEmubUSfm_EqxdvCw (en AndroidManifest.xml)
PENDIENTE: verificar que esta clave tenga "Maps SDK for Android" habilitado
           en Google Cloud Console → APIs & Services → Credentials
```

---

## Arquitectura — puntos clave

### MainActivity extiende FragmentActivity

```kotlin
// NO cambiar a ComponentActivity — BiometricPrompt requiere FragmentManager
class MainActivity : FragmentActivity() { ... }
```

### BioSecureViewModel — dependencias

```kotlin
BioSecureViewModelFactory(
    userRepository,          // Retrofit DummyJSON (legado, poco usado)
    authRepository,          // Firebase Auth
    attendanceRepository,    // Firestore attendances/ y users/
    companyRepository,       // Firestore companies/ y sedes/
    functionsRepository,     // Firebase Functions (no activo aún)
    themePreferences         // DataStore dark mode
)
```

### Tema y idioma — cómo se propagan

```kotlin
// MainActivity.kt
val isDarkPref by viewModel.isDarkMode.collectAsState()   // DataStore
val isDarkMode = isDarkPref ?: prefs.getBoolean("dark_mode", systemTheme)  // SharedPrefs fallback
var language by remember { mutableStateOf(prefs.getString("language", "es") ?: "es") }

CompositionLocalProvider(LocalAppLanguage provides language) {
    BioSecureTheme(darkTheme = isDarkMode) { NavGraph(...) }
}

// En cualquier pantalla:
val lang = LocalAppLanguage.current   // "es" | "en"
```

### Regla de colores en pantallas

No usar `isSystemInDarkTheme()` en pantallas. Usar exclusivamente tokens del tema:

```kotlin
// Cards:
CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)

// TextFields:
OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.outline,     // = #1ED9C5 en dark
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    ...
)
```

### Multi-tenant — aislamiento de datos

Cada admin tiene `companyId` en su documento Firestore. Todos los empleados que registra heredan ese `companyId`. Todas las consultas de admins van filtradas por `companyId`:
- `loadFirestoreEmployees()` → `users WHERE companyId = X AND role = "employee"` (filtro en memoria)
- `loadAttendanceHistory()` → `attendances WHERE companyId = X`
- `checkAttendanceStatus()` → `companies/{companyId}/shifts`

### AdminRouteGuard

```kotlin
// En NavGraph.kt — todas las rutas admin están envueltas en:
AdminRouteGuard(viewModel, navController) {
    // Si currentRole != "admin" → redirige a EmployeeDashboard
}
```

---

## Estado de cada iteración

| Iteración | Qué se hizo | Estado |
|---|---|---|
| 1 | UI estática: Login, Scan, History, Dashboard, Settings, NavGraph, dark mode, BiometricPrompt | ✅ |
| 2 | Firebase Auth + Firestore, login por rol, sesión persistente, flujo QR completo, registro de empleado | ✅ |
| 3 | Historial real desde Firestore, ML Kit facial real, FCM push notifications, CRUD empleados, DashboardScreen con datos reales | ✅ |
| 4 | SplashScreen, login unificado, QRScreen empleado, QRScannerScreen admin, ConfirmationData, flujo biométrico→QR→confirmación | ✅ |
| 5 | Arquitectura multi-tenant: `companyId` en users y attendances, `companies/` collection, aislamiento de datos por empresa | ✅ |
| 6 | Turnos múltiples por empresa (ShiftSettingsScreen), dark mode mejorado (paleta teal), animaciones Compose, bug fix historial | ✅ |
| 6b | Fix visibilidad DashboardScreen, debug logs, LoginScreen filled style, AnimatedVisibility en HistoryScreen | ✅ |
| 7 | `AttendanceStatus.PENDIENTE`, EmployeeDashboard, flujo QR con estado PENDIENTE, edición inline de turnos, INTERNET permission fix | ✅ |
| 8 | Idioma EN/ES en ScanScreen y BioSecureBottomBar, fix adb install en Samsung | ✅ |
| 9 | APIs deprecadas resueltas, idioma EN/ES completo en todas las pantallas, export CSV, edición nombre empleado, fix fondo login | ✅ |
| 10 | Lottie integrado (8 animaciones reales en assets/), fix QR automático (loadTodayAttendance sin auto-crear PENDIENTE), rediseño UX PENDIENTE | ✅ |
| 11 | ShiftManagerScreen (asignación empleado↔turno), SedesScreen con Google Maps + Geocoder + Circle, NavGraph + Dashboard actualizados | ✅ |

---

## Pendientes priorizados

### #1 — APK release firmado (ALTA — entrega de producción)

```bash
# 1. Generar keystore (hacerlo UNA vez, guardar el archivo .jks de forma segura)
keytool -genkeypair -v -keystore biosecure.jks -alias biosecure \
        -keyalg RSA -keysize 2048 -validity 10000

# 2. Agregar en app/build.gradle.kts:
android {
    signingConfigs {
        create("release") {
            storeFile = file("biosecure.jks")
            storePassword = "TU_PASSWORD"
            keyAlias = "biosecure"
            keyPassword = "TU_PASSWORD"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false   // activar true + proguard para producción real
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

# 3. Compilar
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

### #2 — Verificar Maps API key (ALTA — SedesScreen puede mostrar mapa en blanco)

1. Ir a [Google Cloud Console](https://console.cloud.google.com/) → el proyecto del mismo Firebase
2. APIs & Services → Credentials → buscar clave `AIzaSyDZVVrZS3V-m9I6zHlEmubUSfm_EqxdvCw`
3. Verificar que "Maps SDK for Android" esté habilitado para esa clave
4. Si el mapa muestra tiles grises o logcat muestra `AuthFailure` → activar la API

### #3 — Migrar `LocalClipboardManager` deprecated (MEDIA)

```kotlin
// Buscar en el código:
// grep -r "LocalClipboardManager" app/src/

// Reemplazar:
// ANTES (deprecated):
val clipboard = LocalClipboardManager.current
// DESPUÉS:
val clipboard = LocalClipboard.current
```

### #4 — Tests unitarios BioSecureViewModel (MEDIA)

Casos prioritarios:
- `login()` con credenciales válidas/inválidas
- `checkAttendanceStatus()` con turnos que caen en PUNTUAL vs TARDANZA
- `loadTodayAttendance()` — que NO llame `autoCreateTodayPendiente()`
- `assignShiftToEmployee(uid, shiftId)` — que actualice Firestore

### #5 — Firebase Functions (BAJA — seguridad server-side)

- Validación JWT de QR efímero (evita falsificación de UID en QR)
- Geofencing server-side (evita que empleados falsifiquen ubicación desde app)
- Ya hay `FirebaseFunctionsRepository.kt` como stub y la dependencia `firebase-functions` declarada

### #6 — Navegación Sede-First para admin (BAJA — decisión de UX pendiente)

- Admin selecciona sede → dashboard filtrado por esa sede
- Requiere cambios en NavGraph, DashboardScreen y flujo post-login
- Decidido intencionalmente NO incluir en Iteración 11 para evitar refactorización mayor

---

## Errores conocidos activos y workarounds

### `gradlew build` falla con lint

**Síntoma:** `.\gradlew.bat build` falla con error de lint relacionado a `gradle.properties` (ruta Windows con backslash).

**Workaround:** Usar siempre `./gradlew assembleDebug` (desde Git Bash o la terminal de Android Studio). Este comando omite el lint y compila correctamente.

```bash
# Nunca usar:
.\gradlew.bat build

# Siempre usar:
./gradlew assembleDebug
```

---

### `adb install -r` no actualiza en Samsung

**Síntoma:** `adb install -r` devuelve `Success` pero el dispositivo sigue mostrando la versión anterior.

**Workaround:**
```powershell
$adb = "C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apk = "C:\Users\User\Desktop\BioSecure\app\build\outputs\apk\debug\app-debug.apk"
& $adb uninstall com.biosecure.app
& $adb install $apk
```

**Nota:** `adb` no está en el PATH del sistema en este equipo. Siempre usar la ruta completa.

---

### Campo `role` null en Firestore → "Usuario sin rol asignado"

**Síntoma:** Login falla con "Usuario no registrado" aunque el usuario existe.

**Causa:** El campo `role` fue creado con un espacio, valor vacío, o con mayúsculas en Firebase Console.

**Solución:**
1. Abrir Firestore Console → `users/{uid}` del usuario afectado
2. Eliminar el campo `role` y recrearlo con valor exacto: `admin` o `employee` (sin espacios, minúsculas)

---

### Firestore no lee nada (historial vacío, 0 documentos)

**Síntoma:** La app funciona sin errores visibles pero no muestra datos. Logcat muestra `DNS isBlocked=true` o `CACHE=0 SERVER=0`.

**Causa raíz:** Falta `<uses-permission android:name="android.permission.INTERNET"/>` en AndroidManifest.xml. Firebase entra en modo offline silencioso.

**Estado:** RESUELTO en Iteración 7. El permiso ya está declarado. Verificar si reaparece el síntoma tras cambios en el Manifest.

---

### AGP 9.2.x rompe el build

**Síntoma:** Errores de variantes de compilación al intentar actualizar el plugin.

**Workaround:** Mantener `agp = "9.1.1"` en `gradle/libs.versions.toml`. No actualizar hasta validar compatibilidad.

---

### `JAVA_HOME` inválido fuera de Android Studio

**Síntoma:** `ERROR: JAVA_HOME is set to an invalid directory` al ejecutar Gradle desde PowerShell del sistema.

**Workaround:** Usar la terminal integrada de Android Studio, o fijar la variable manualmente:
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-19"
./gradlew assembleDebug
```

---

### Errores rojos en Android Studio pero build exitoso

**Síntoma:** Android Studio marca errores rojos en DashboardScreen u otras pantallas, pero `assembleDebug` da BUILD SUCCESSFUL.

**Solución:** `File → Sync Project with Gradle Files` en Android Studio.

---

## Configuración de Firebase — pasos para nuevo entorno

Si alguien clona el proyecto en un nuevo equipo o crea un nuevo proyecto Firebase:

1. **Firebase Console** → Seleccionar proyecto (o crear nuevo) → Authentication → habilitar `Email/Password`
2. **Firestore** → Crear base de datos → Modo producción → Reglas mínimas:
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
3. **google-services.json** → Descargar desde Firebase Console → reemplazar `app/google-services.json`
4. **Crear admin en Firestore manualmente:**
   ```
   users/{uid_del_admin_en_firebase_auth}
     ├── email: "admin@empresa.com"
     ├── role: "admin"
     ├── name: "Nombre Admin"
     └── companyId: "MiEmpresa"
   
   companies/MiEmpresa
     ├── name: "Mi Empresa S.A."
     ├── horaEntrada: "08:00"
     └── toleranciaMin: 15
   ```

---

## Animaciones Lottie

Los 8 archivos en `app/src/main/assets/` son animaciones Lottie **reales y funcionales** (no placeholders vacíos):

| Archivo | Pantalla | Comportamiento |
|---|---|---|
| `lock.json` | LoginScreen — cabecera | Loop infinito |
| `fingerprint_scan.json` | ScanScreen — tab Huella | Loop infinito |
| `face_scan.json` | ScanScreen — tab Facial sin permiso | Loop infinito |
| `success_check.json` | ConfirmationScreen admin — check | `iterations=1` (una vez) |
| `empty_state.json` | HistoryScreen — sin registros | Loop infinito |
| `alert.json` | DashboardScreen — MetricCard tardanzas | Loop infinito |
| `qr_scan.json` | DashboardScreen — QuickAction escanear | Loop infinito |
| `team.json` | DashboardScreen — QuickAction empleados | Loop infinito |

Uso del componente:
```kotlin
// ui/components/LottieIcon.kt
LottieIcon(
    assetName = "lock.json",
    modifier = Modifier.size(80.dp),
    iterations = LottieConstants.IterateForever,  // o 1 para reproducir una vez
    isPlaying = true
)
```

---

## Convenciones del proyecto

### Commits

```
feat(scan): descripción en imperativo
fix(navgraph): descripción
chore(deps): actualizar compose-bom
refactor(viewmodel): descripción
docs(team): descripción
```

### Patrón de colores en nuevas pantallas

```kotlin
// Cards
colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)

// TextFields
colors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.outline,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedLabelColor = MaterialTheme.colorScheme.outline,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.outline
)

// Texto principal
color = MaterialTheme.colorScheme.onSurface

// Texto secundario
color = MaterialTheme.colorScheme.onSurfaceVariant
```

### Agregar una nueva pantalla admin

1. Añadir `object NuevaPantalla : Screen("admin/nueva-pantalla")` en `NavGraph.kt`
2. Añadir `composable(Screen.NuevaPantalla.route)` envuelto en `AdminRouteGuard`
3. Agregar un `QuickActionCard` o navegación en `DashboardScreen.kt`
4. La pantalla recibe `navController: NavController` y `viewModel: BioSecureViewModel? = null`

---

*Para historial completo de decisiones, errores y prompts útiles, ver `TEAM.md`.*
