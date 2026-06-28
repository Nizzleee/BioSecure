# TEAM — BioSecure

Referencia interna del equipo: errores conocidos, dependencias exactas, estado del proyecto, prompts, decisiones de arquitectura y convenciones.

---

## Estado actual — 2026-06-25 (arrancar desde aquí)

### Qué está funcionando

| Área | Estado | Notas |
|---|---|---|
| Build `assembleDebug` | ✅ OK | Usar `./gradlew assembleDebug`, NO `build` (lint falla en gradle.properties por ruta Windows con backslash) |
| Firebase Auth + Firestore | ✅ OK | Multi-tenant por `companyId`; aislamiento completo por empresa |
| Login por rol automático | ✅ OK | `loginWithRole()` → detecta "admin" / "employee" desde `users/{uid}.role` |
| Sesión persistente (Splash) | ✅ OK | `initializeSessionIfLoggedIn()` en MainActivity |
| Dashboard admin | ✅ OK | Métricas reales, navegación completa, todas las cards con LottieIcon |
| Flujo PENDIENTE empleado | ✅ OK | Scan biométrico → PENDIENTE en Firestore → admin escanea QR → EXITOSO |
| EmployeeDashboard | ✅ OK | Estado de hoy, semana, botón Refresh con ícono |
| ShiftSettingsScreen | ✅ OK | Lista, añade y elimina turnos por empresa |
| ShiftManagerScreen | ✅ OK | Asigna/desasigna empleados a turnos con `AssignShiftBottomSheet` |
| SedesScreen | ✅ OK | Google Maps interactivo + `Geocoder` + `Circle` de geocerca + slider de radio |
| Lottie animations | ✅ OK | Todos los assets en `assets/` son animaciones reales con keyframes |
| Dark/light theme | ✅ OK | Tokens Material 3 completos en `Theme.kt`; sin `isSystemInDarkTheme()` en pantallas |
| Idioma EN/ES | ✅ OK | Todas las pantallas con `LocalAppLanguage` |
| Export CSV | ✅ OK | Desde `HistoryScreen`, respeta filtros activos, comparte vía `Intent.ACTION_SEND` |
| Edición de perfil empleado | ✅ OK | Lápiz en `SettingsScreen` → `AlertDialog` → `viewModel.updateUserName()` → Firestore |
| `EditEmployeeScreen` uid | ✅ **YA CORREGIDO** | Parámetro es `userId: String`; NavGraph usa `NavType.StringType`; `EmployeeList` llama `Screen.AdminEditEmployee.route(it.uid)` |
| Historial tiempo real | ✅ **CORREGIDO IT.12** | `observeUserAttendances` / `observeAllAttendances` / `observeTodayAttendances` ya NO usan `orderBy` — se ordena en memoria, sin índice compuesto Firestore |
| Splash logo | ✅ **CORREGIDO IT.12** | Reemplazado emoji 🔒 por `penguin_splash.png` (160dp, animación spring) |
| Maps API key en Manifest | ⚠️ SIN VERIFICAR | Clave `AIzaSyDZVVrZS3V-m9I6zHlEmubUSfm_EqxdvCw` presente en `AndroidManifest.xml`. Falta confirmar que "Maps SDK for Android" esté habilitado para esa clave en Google Cloud Console |
| `LocalClipboardManager` | ⚠️ Warning | API deprecated; compila sin error pero genera warning |

### Qué está roto

| Problema | Impacto | Acción requerida |
|---|---|---|
| Maps no muestra tiles (posible) | Alto si `SedesScreen` muestra mapa en blanco | Ir a Google Cloud Console → Credentials → habilitar "Maps SDK for Android" para la clave `AIzaSyDZVVrZS3V-m9I6zHlEmubUSfm_EqxdvCw`. Si la clave no tiene restricciones de app, puede que ya funcione |
| APK release sin generar | Alto para entrega | Ver tarea #1 de pendientes |
| `gradlew build` con lint | Bajo | Pre-existente. Usar siempre `gradlew assembleDebug` |

### Pendientes priorizados

> Ordenados de mayor a menor urgencia para la próxima sesión.

**#1 — APK release firmado** (ALTA)
- Crear keystore: `keytool -genkeypair -v -keystore biosecure.jks -alias biosecure -keyalg RSA -keysize 2048 -validity 10000`
- Añadir `signingConfigs { release { ... } }` en `app/build.gradle.kts`
- Ejecutar `./gradlew assembleRelease`
- APK de salida: `app/build/outputs/apk/release/app-release.apk`

**#2 — Verificar Maps API key** (ALTA si `SedesScreen` no muestra el mapa)
- Abrir [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → Credentials
- Confirmar que la clave `AIzaSyDZVVrZS3V-m9I6zHlEmubUSfm_EqxdvCw` tiene habilitada la API "Maps SDK for Android"
- Si el mapa muestra tiles grises o error de autenticación en logcat → este es el problema

**#3 — `LocalClipboardManager` deprecated** (MEDIA)
- Archivo afectado: buscar con `grep -r "LocalClipboardManager" app/src/`
- Reemplazar por `LocalClipboard.current` (disponible desde Compose 1.5+)

**#4 — Tests unitarios `BioSecureViewModel`** (MEDIA)
- Probar: `login()`, `checkAttendanceStatus()`, `loadTodayAttendance()`, `assignShiftToEmployee()`
- Usar `kotlinx-coroutines-test` + `mockk` o `FakeRepository` manual

**#5 — Firebase Functions** (BAJA — solo si se necesita seguridad server-side)
- Validación JWT de QR efímero
- Geofencing server-side (evitar que empleados falsifiquen ubicación)

**#6 — Navegación Sede-First para admin** (BAJA — decisión arquitectural pendiente)
- Admin selecciona sede → dashboard filtrado por esa sede
- Requiere refactorizar `NavGraph`, `DashboardScreen` y flujo de login

### Contexto de retoma rápida

```
Proyecto: BioSecure — Android biométrico
Ruta:     C:\Users\User\Desktop\BioSecure
Stack:    AGP 9.1.1 · Kotlin 2.2.10 · Compose BOM 2026.02.01
Backend:  Firebase Auth + Firestore (proyecto activo, google-services.json en app/)
Último build exitoso: assembleDebug, 0 errores

Comando de compilación:    ./gradlew assembleDebug
APK debug salida:          app/build/outputs/apk/debug/app-debug.apk
Instalar en dispositivo:   adb uninstall com.biosecure.app && adb install app-debug.apk
(adb path: C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe)
```

---

## Iteración 12 — Firestore index fix, splash logo, Lottie icons, Login visibility, QR cleanup (COMPLETADO)

### Resumen

Corrección crítica de un bug silencioso en Firestore (asistencias no aparecían en historial ni en home), reemplazo del logo de splash, mejoras visuales en Login y Dashboard, y limpieza de botones QR redundantes.

### Fix crítico — `AttendanceRepository.kt`: índices compuestos Firestore

**Raíz del problema:** Las tres funciones observadoras usaban `.whereEqualTo()` combinado con `.orderBy()` sobre un campo diferente. Firestore exige un índice compuesto para esa combinación; sin el índice, el `callbackFlow` llama `close(error)` en silencio, el `collectLatest` absorbe la excepción y `_attendanceHistoryFlow` queda con datos vacíos/obsoletos. Los registros nuevos nunca actualizan la UI.

**Funciones afectadas:**

| Función | `orderBy` eliminado | Ordenamiento nuevo |
|---|---|---|
| `observeUserAttendances(uid)` | `.orderBy("checkIn", DESCENDING)` | `.sortedWith(compareByDescending { dateSortKey(it.date) }.thenByDescending { it.checkIn })` |
| `observeAllAttendances(companyId)` | `.orderBy("timestamp", DESCENDING)` | mismo sort compuesto |
| `observeTodayAttendances(companyId)` | `.orderBy("checkIn", DESCENDING)` | `.sortedByDescending { it.checkIn }` |
| `getAttendanceByUser(uid)` | `.orderBy("checkIn", DESCENDING)` | sin sort (one-shot call) |

**Helper añadido:**
```kotlin
private fun dateSortKey(date: String): String {
    val parts = date.split("/")
    return if (parts.size == 3) "${parts[2]}${parts[1].padStart(2,'0')}${parts[0].padStart(2,'0')}" else date
}
```
Convierte `"dd/MM/yyyy"` → `"yyyyMMdd"` para orden lexicográfico correcto.

**Nota:** `import com.google.firebase.firestore.Query` se mantiene porque `getAllAttendances()` usa `Query.Direction.DESCENDING` en un `orderBy` de campo único (sin `whereEqualTo`) que no requiere índice compuesto.

### Fix — `SplashScreen.kt`: logo penguin

- Reemplazado emoji `🔒` (72sp) por `Image(painterResource(R.drawable.penguin_splash), size = 160.dp)`.
- Animación spring mantenida (`.scale(scale.value)`).
- Imports añadidos: `androidx.compose.foundation.Image`, `layout.size`, `res.painterResource`, `com.biosecure.app.R`.

### Fix — `DashboardScreen.kt`: tamaño Lottie QuickActionCard

- `LottieIcon` dentro de `QuickActionCard` cambiado de `Modifier.size(32.dp)` → `Modifier.size(36.dp)`.
- `MetricCard` de asistencia ya usaba `lottieAsset = "attendance.json"` con `48.dp` (sin cambios).

### Fix — `LoginScreen.kt`: visibilidad de texto

- Subtítulo "Sistema de Asistencia Biométrica": color cambiado de `TealLight` → `Color.White.copy(alpha = 0.85f)`.
- `textFieldColors`: añadidos `focusedPlaceholderColor` y `unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f)`.

### Fix — `EmployeeDashboard.kt`: botón de actualizar

- Contenido del `OutlinedButton` "Actualizar estado": reemplazado `Text(...)` por `Icon(Icons.Default.Refresh, contentDescription = "Actualizar estado", tint = MaterialTheme.colorScheme.primary)`.
- Imports añadidos: `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.filled.Refresh`.

### Fix — Limpieza QR

- **`HistoryScreen.kt`**: eliminado el bloque global "Ver mi QR" (estado `showMyQR`, `OutlinedButton`, `AnimatedVisibility` con `HistoryQRCard`) que estaba encima de la lista de registros. El QR por-card en `AttendanceCard` se mantiene.
- **`EmployeeDashboard.kt`**: eliminado el botón "Ver mi QR" de la card "Estado de Hoy". Imports no usados eliminados: `android.graphics.Bitmap`, `AnimatedVisibility`, `expandVertically`, `fadeIn`, `fadeOut`, `shrinkVertically`, `Image`, `asImageBitmap`, `FirebaseAuth`, `QRCodeWriter`, `BarcodeFormat`, `Dispatchers`, `withContext`, varios `MutableStateFlow` específicos de QR.

### Error encontrado y resuelto

| Error | Causa | Fix |
|---|---|---|
| `Unresolved reference 'Query'` | Al limpiar imports tras quitar `orderBy`, se eliminó `Query` pero `getAllAttendances()` aún usa `Query.Direction.DESCENDING` | Re-añadir `import com.google.firebase.firestore.Query` |

### Warnings pre-existentes (no introducidos en esta iteración)

- `CartesianChartModelProducer.build()` deprecated — biblioteca Vico en `DashboardScreen`
- `tryRunTransaction` deprecated — biblioteca Vico en `DashboardScreen`
- `Locale(String)` deprecated — Java interop en `EmployeeDashboard`

### BUILD

`BUILD SUCCESSFUL` — `assembleDebug` — 3 warnings (todos pre-existentes), 0 errores.

```
> Task :app:compileDebugKotlin
w: DashboardScreen.kt:582:64 CartesianChartModelProducer.build() deprecated
w: DashboardScreen.kt:585:23 tryRunTransaction deprecated
w: EmployeeDashboard.kt:70:47 Locale(String) deprecated
> Task :app:assembleDebug
BUILD SUCCESSFUL in 3s
36 actionable tasks: 9 executed, 27 up-to-date
```

### Archivos modificados en Iteración 12

| Archivo | Cambio |
|---|---|
| `data/repository/AttendanceRepository.kt` | Eliminado `orderBy` de 4 funciones; añadido `dateSortKey()`; orden en memoria |
| `ui/screens/splash/SplashScreen.kt` | Emoji 🔒 → `Image(penguin_splash.png, 160dp)` |
| `ui/screens/dashboard/DashboardScreen.kt` | `QuickActionCard` Lottie size: 32dp → 36dp |
| `ui/screens/login/LoginScreen.kt` | Subtítulo color: `TealLight` → `White.copy(0.85f)`; placeholder colors añadidos |
| `ui/screens/employee/EmployeeDashboard.kt` | Botón refresh: `Text` → `Icon(Refresh)`; QR button eliminado; imports limpiados |
| `ui/screens/history/HistoryScreen.kt` | Bloque "Ver mi QR" global eliminado |

---

## Iteración 11 — Merge alpha v0.2: ShiftManager, SedesScreen con Maps, modelos enterprise (COMPLETADO)

### Resumen

Merge selectivo del alpha v0.2 (rama enterprise/geofencing) hacia la rama principal. Se adoptaron las mejoras de arquitectura sin eliminar nada de la versión actual.

### Archivos sin cambios (ya eran idénticos al alpha)

Los modelos `Shift.kt`, `User.kt` y `Sede.kt` ya contenían todos los campos del alpha (`id`, `toleranceMin`, `shiftId`, `sedeId`, `creadaEn`, `activa`). No se requirió ningún cambio en los modelos.

### Cambios — ShiftManagerScreen.kt (NUEVO)

**Nuevo archivo:** `app/src/main/java/com/biosecure/app/ui/screens/admin/ShiftManagerScreen.kt`

Pantalla de gestión avanzada de turnos. Reemplaza la UX básica de `ShiftSettingsScreen` con una interfaz más completa:

- **`ShiftItemCard`**: Muestra nombre, rango horario, badge de tolerancia y contador de empleados asignados. Botón de edición inline.
- **`AddShiftBottomSheet`**: Crea o edita turnos con chips de presets (`Tiempo Completo`, `Medio Tiempo`, `Personalizado`), campos de hora y campo de tolerancia en minutos.
- **`AssignShiftBottomSheet`**: Lista de empleados con `Checkbox` para asignar/desasignar turnos individualmente. Llama `viewModel.assignShiftToEmployee(uid, shiftId)`.
- Llama `viewModel.saveShift(name, startTime, toleranceMin, endTime)` (ya existente en el ViewModel).
- `ShiftSettingsScreen` NO fue eliminada — ambas pantallas coexisten.

### Cambios — SedesScreen.kt (MEJORADO con Google Maps)

El `SedeEditorSheet` (hoja de creación/edición de sedes) fue completamente reemplazado con una versión que incluye:

- **Google Maps interactivo**: `GoogleMap` composable con toque para mover el pin.
- **Geocerca visual**: `Circle` dibujada alrededor del pin con radio configurable.
- **Buscador de direcciones**: `OutlinedTextField` + `Geocoder` en `Dispatchers.IO` para buscar por nombre de dirección.
- **Slider de radio**: Reemplaza el campo de texto. Rango: 20m–500m.
- **`LaunchedEffect(location)`**: Anima la cámara del mapa cuando cambia la ubicación.
- Lista principal y `SedeCard` sin cambios.
- Items de la lista ahora envueltos en `AnimatedVisibility(fadeIn + expandVertically)`.

Dependencias Maps ya estaban presentes: `maps-compose = "4.4.1"`, `play-services-maps = "18.2.0"`, `play-services-location = "21.2.0"`.

### Cambios — NavGraph.kt

- Nuevo `Screen.AdminShiftManager : Screen("admin/shift-manager")`
- Nuevo `composable(Screen.AdminShiftManager.route)` con `AdminRouteGuard`
- `AdminShiftSettings` existente sin cambios

### Cambios — DashboardScreen.kt

- Nuevo `QuickActionCard` "Gestor de Turnos" → navega a `Screen.AdminShiftManager.route`
- Aparece debajo del card existente "Configurar Horarios"
- Ningún botón o card existente fue eliminado

### Qué NO se adoptó del alpha (decidido intencionalmente)

| Característica alpha | Razón de exclusión |
|---|---|
| Navegación Sede-First (admin selecciona sede antes de entrar) | Requiere refactorizar Dashboard, NavGraph y flujo de login completo |
| QR con token JWT efímero | Requiere Firebase Functions con Anthropic SDK configurado |
| Registro via Cloud Functions | Rompe el flujo actual de registro directo desde cliente |
| Historial con Snapshot Listeners | Refactorización mayor del ViewModel y AttendanceRepository |
| Bottom bar en SedesScreen | El `BioSecureBottomBar` actual no acepta parámetro `viewModel` |
| "VER DASHBOARD DE LA SEDE" en SedeCard | Requiere `Screen.Dashboard.route(sedeId)` y multi-sede dashboard |

### BUILD

`BUILD SUCCESSFUL` — `assembleDebug` — 0 errores, warnings pre-existentes sin cambios.

### Archivos creados/modificados en Iteración 11

| Archivo | Cambio |
|---|---|
| `ui/screens/admin/ShiftManagerScreen.kt` | **NUEVO** — gestor de turnos con asignación de empleados |
| `ui/screens/admin/SedesScreen.kt` | `SedeEditorSheet` mejorado con Google Maps + Geocoder + Slider |
| `ui/navigation/NavGraph.kt` | + `Screen.AdminShiftManager` y composable |
| `ui/screens/dashboard/DashboardScreen.kt` | + QuickActionCard "Gestor de Turnos" |

### Pendiente para próxima iteración

- [ ] Reemplazar 8 archivos placeholder en `app/src/main/assets/` con animaciones reales de lottiefiles.com
- [ ] Fix `EditEmployeeScreen` + `NavGraph` para usar `uid: String` en lugar de `userId: Int`
- [ ] Integración real con Firebase Functions (validación JWT, geofencing server-side)
- [ ] Navegación Sede-First para admin (seleccionar sede → dashboard filtrado)
- [ ] `LocalClipboardManager` deprecated → migrar a `LocalClipboard`
- [ ] Tests unitarios para `BioSecureViewModel`
- [ ] APK release firmado para producción

---

## Iteración 10 — Lottie animations, QR fix empleado, PENDIENTE UX (COMPLETADO)

### Resumen

Se integró la biblioteca Lottie Compose para animaciones, se eliminó el QR automático del dashboard de empleados y se rediseñó el estado PENDIENTE para no mostrar QR sino una pantalla de espera con acceso al historial.

### Cambios — Dependencia Lottie

- **`gradle/libs.versions.toml`**: añadida versión `lottie = "6.4.0"` y biblioteca `lottie-compose = { group = "com.airbnb.android", name = "lottie-compose", version.ref = "lottie" }`.
- **`app/build.gradle.kts`**: añadida `implementation(libs.lottie.compose)` en bloque de dependencias.

### Cambios — Componente LottieIcon

**Nuevo archivo:** `app/src/main/java/com/biosecure/app/ui/components/LottieIcon.kt`

```kotlin
@Composable
fun LottieIcon(
    assetName: String,
    modifier: Modifier = Modifier,
    iterations: Int = LottieConstants.IterateForever,
    isPlaying: Boolean = true
)
```

Carga animaciones desde `app/src/main/assets/` vía `LottieCompositionSpec.Asset(assetName)`.

### Cambios — Assets Lottie (placeholders)

**Nuevo directorio:** `app/src/main/assets/` con 8 archivos JSON de placeholder (animación vacía válida).

| Archivo | Pantalla / uso |
|---|---|
| `lock.json` | LoginScreen — icono de cabecera |
| `fingerprint_scan.json` | ScanScreen tab 0 — área de huella dactilar |
| `face_scan.json` | ScanScreen tab 1 — sin permiso de cámara |
| `success_check.json` | ConfirmationScreen admin — check de confirmación (iterations=1) |
| `empty_state.json` | HistoryScreen — estado vacío sin registros |
| `alert.json` | DashboardScreen — MetricCard de tardanzas |
| `qr_scan.json` | DashboardScreen — QuickActionCard escanear QR |
| `team.json` | DashboardScreen — QuickActionCard ver empleados |

**IMPORTANTE:** Los 8 archivos son placeholders vacíos. El usuario debe reemplazarlos con animaciones reales descargadas de lottiefiles.com.

### Cambios — Pantallas con Lottie

| Pantalla | Cambio |
|---|---|
| `LoginScreen.kt` | `Text("🔒", fontSize=48.sp)` → `LottieIcon("lock.json", Modifier.size(80.dp))` |
| `ScanScreen.kt` | Tab 0 sin scan: emoji ☝️ → `LottieIcon("fingerprint_scan.json")`; Tab 1 sin permiso: texto → `LottieIcon("face_scan.json")` |
| `ConfirmationScreen.kt` | Box+Icon(CheckCircle) → `LottieIcon("success_check.json", iterations=1)` con `.scale(checkScale)` |
| `HistoryScreen.kt` | Empty state texto → Column con `LottieIcon("empty_state.json")` + texto debajo |
| `DashboardScreen.kt` | `MetricCard` y `QuickActionCard` ahora aceptan `lottieAsset: String? = null`; si no null muestra LottieIcon en vez de emoji |

### Cambios — MetricCard y QuickActionCard (DashboardScreen)

Nuevos parámetros agregados:
```kotlin
fun MetricCard(icon: String = "", lottieAsset: String? = null, ...)
fun QuickActionCard(icon: String = "", lottieAsset: String? = null, ...)
```
Lógica: `if (lottieAsset != null) LottieIcon(lottieAsset, Modifier.size(48.dp)) else Text(icon, ...)`

Call sites actualizados:
- MetricCard tardanzas: `lottieAsset = "alert.json"` (antes emoji ⚠️)
- QuickActionCard Escanear QR: `lottieAsset = "qr_scan.json"` (antes emoji 📲)
- QuickActionCard Ver empleados: `lottieAsset = "team.json"` (antes emoji 👥)

### Cambios — Fix QR automático en EmployeeDashboard

**Problema raíz:** `loadTodayAttendance()` en el ViewModel llamaba `autoCreateTodayPendiente()` que creaba un registro PENDIENTE en Firestore inmediatamente al abrir la app, saltándose la rama `null` del dashboard y mostrando el QR siempre.

**Fix en `BioSecureViewModel.kt`:**
```kotlin
fun loadTodayAttendance() {
    viewModelScope.launch {
        val result = attendanceRepository.getTodayAttendanceByUser(uid, date)
        if (result != null) {
            _todayAttendanceId.value = result.first
            _todayAttendance.value = result.second
        } else {
            _todayAttendance.value = null    // ANTES: llamaba autoCreateTodayPendiente()
            _todayAttendanceId.value = null
        }
    }
}
```
`autoCreateTodayPendiente()` sigue existiendo pero ya NO se llama automáticamente. Solo `registerAttendance(scanType)` crea registros.

### Cambios — Rediseño UX PENDIENTE (EmployeeDashboard)

**Antes:** rama PENDIENTE mostraba el QR del empleado con botón "Visualizar QR".

**Ahora:** rama PENDIENTE muestra:
- Icono ⏳ grande
- Texto "Asistencia registrada" (título bold)
- Texto "Esperando confirmación del administrador"
- Hora de registro
- `OutlinedButton "📋 Ver mi historial"` → navega a `Screen.EmployeeHistory.route`

**Rama null (sin registro hoy):** muestra `PulsingBiometricIcon()` — nuevo composable con animación de pulso y ondas usando `rememberInfiniteTransition`. El QR sigue accesible como `TextButton` oculto debajo para emergencias.

### Errores conocidos y estado

| Error | Estado | Solución |
|---|---|---|
| Errores rojos en DashboardScreen (Android Studio) | No son errores de compilación — `compileDebugKotlin` da BUILD SUCCESSFUL | Hacer **File → Sync Project with Gradle Files** en Android Studio |
| `gradlew build` falla con lint en `gradle.properties` | Pre-existente, ruta Windows con backslash no escapado | Usar `gradlew assembleDebug` en lugar de `build` |
| Animaciones Lottie muestran nada (pantalla vacía) | Los archivos en `assets/` son placeholders | Reemplazar con JSON reales desde lottiefiles.com |

### Archivos creados/modificados en Iteración 10

| Archivo | Cambio |
|---|---|
| `gradle/libs.versions.toml` | + `lottie = "6.4.0"`, `lottie-compose` library |
| `app/build.gradle.kts` | + `implementation(libs.lottie.compose)` |
| `ui/components/LottieIcon.kt` | **NUEVO** — composable genérico para animaciones Lottie |
| `app/src/main/assets/*.json` | **NUEVO** — 8 placeholders: lock, fingerprint_scan, face_scan, success_check, empty_state, alert, qr_scan, team |
| `ui/viewmodel/BioSecureViewModel.kt` | Fix `loadTodayAttendance()` — eliminada llamada a `autoCreateTodayPendiente()` |
| `ui/screens/employee/EmployeeDashboard.kt` | Rama PENDIENTE rediseñada (⏳ + historial); rama null con `PulsingBiometricIcon()`; QR oculto detrás de TextButton |
| `ui/screens/login/LoginScreen.kt` | Icono 🔒 → `LottieIcon("lock.json")` |
| `ui/screens/scan/ScanScreen.kt` | Emojis de escaneo → `LottieIcon` |
| `ui/screens/confirmation/ConfirmationScreen.kt` | CheckCircle icon → `LottieIcon("success_check.json", iterations=1)` |
| `ui/screens/history/HistoryScreen.kt` | Empty state → `LottieIcon("empty_state.json")` |
| `ui/screens/dashboard/DashboardScreen.kt` | `MetricCard` + `QuickActionCard` con soporte `lottieAsset`; 3 call sites actualizados |

### Pendiente para próxima iteración

- [ ] Reemplazar 8 archivos placeholder en `app/src/main/assets/` con animaciones reales de lottiefiles.com
- [ ] Fix `EditEmployeeScreen` + `NavGraph` para usar `uid: String` en lugar de `userId: Int` (plan existente: `delegated-roaming-floyd.md`)
- [ ] Claude/Gemini API para análisis IA en DashboardScreen (sección "Análisis IA" solo toca `viewModel?.getAiAnalysis()`)
- [ ] Geofencing activo — validar ubicación antes de permitir marcación
- [ ] Diálogo explicativo de permisos de cámara
- [ ] `LocalClipboardManager` deprecated → migrar a `LocalClipboard`
- [ ] Tests unitarios para `BioSecureViewModel`
- [ ] APK release firmado para producción

---

## Iteración 9 — Mejoras UX, idioma completo, APIs deprecadas y exportar CSV (COMPLETADO)

### Resumen

Iteración de calidad y completitud: se cerraron todos los warnings de APIs deprecadas, se añadió soporte de idioma EN/ES a las pantallas restantes, se implementó exportar CSV de asistencias, edición de perfil de empleado y se corrigió el fondo de los campos de login en modo claro.

### Cambios — APIs deprecadas (todas resueltas)

| Archivo(s) | API antigua | API nueva |
|---|---|---|
| `ScanScreen`, `QRScannerScreen`, `EditEmployeeScreen`, `RegisterEmployeeScreen`, `EmployeeList`, `AdminEmployeeQRScreen`, `ShiftSettingsScreen`, `QRScreen` (employee) | `TopAppBarDefaults.centerAlignedTopAppBarColors()` | `TopAppBarDefaults.topAppBarColors()` |
| `CameraPreview`, `QRScannerScreen` | `import androidx.compose.ui.platform.LocalLifecycleOwner` | `import androidx.lifecycle.compose.LocalLifecycleOwner` |
| `SettingsScreen` | `Icons.Default.KeyboardArrowRight` | `Icons.AutoMirrored.Filled.KeyboardArrowRight` |

- Dependencia añadida: `androidx.lifecycle:lifecycle-runtime-compose:2.10.0` en `libs.versions.toml` + `app/build.gradle.kts`.

### Cambios — LoginScreen: fondo de campos visible en modo claro

- **Problema:** `surfaceVariant = White` en `BioSecureLightColorScheme` → los campos se fundían con el Card blanco en modo claro.
- **Solución:** Reemplazado `MaterialTheme.colorScheme.surfaceVariant` por `MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)`. Produce un fondo levemente grisáceo en modo claro y funciona igual de bien en modo oscuro, sin depender de `isSystemInDarkTheme()`.

### Cambios — Exportar CSV (HistoryScreen)

- El texto "Exportar CSV" (solo visible para admin) es ahora clickable con `Modifier.clickable {}`.
- Al pulsarlo genera un CSV con cabecera (`Nombre,Fecha,Hora Entrada,Estado,Tipo,Ubicación` / EN) y una fila por registro visible en ese momento (respeta búsqueda y filtros activos).
- Comparte vía `Intent.ACTION_SEND` (type `text/plain`) usando el chooser nativo de Android.

### Cambios — Edición de perfil de empleado (SettingsScreen)

- Empleados ven un icono de lápiz (`Icons.Outlined.Edit`, 16dp) junto a su nombre en la cabecera de ajustes.
- Al pulsar abre un `AlertDialog` con `OutlinedTextField` pre-poblado con el nombre actual.
- Guardar llama `viewModel.updateUserName(newName)` → actualiza `users/{uid}.name` en Firestore y `_currentUserName` en el ViewModel.
- Admins no ven el botón de edición.
- Nuevo método en `BioSecureViewModel`:
```kotlin
fun updateUserName(newName: String) {
    val uid = _currentUserUid.value ?: return
    viewModelScope.launch {
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .update("name", newName).await()
        _currentUserName.value = newName
    }
}
```

### Cambios — Soporte de idioma EN/ES completo

**`ConfirmationScreen.kt`** — Ahora importa `LocalAppLanguage` y pasa `lang` a ambas funciones privadas:
- `AdminConfirmedContent`: "Attendance recorded successfully" / "Asistencia registrada exitosamente"; labels "Employee/Empleado", "Check-in time/Hora de ingreso", "Date/Fecha", "Close/Cerrar".
- `EmployeeQRWaitingContent`: "Hello/Hola", "Have the Admin scan your QR.../Escanea con el Admin...", "[name]'s QR / QR de [nombre]", "Cancel/Cancelar".

**`AttendanceCard` (HistoryScreen.kt)** — Añadido parámetro `lang: String = "es"` y traducciones:
- Subtítulos de empleado: "No records for this day / Sin registros para este día", "Registered at HH:mm — pending / Registrado a las HH:mm — pendiente", "Entry: HH:mm / Entrada: HH:mm".
- Badges de estado: On Time/Puntual, Successful/Exitoso, Absent/Inasistencia, Out of Shift/Fuera de Horario, Late/Tardanza, Pending/Pendiente.
- Sección QR: "Show QR / Visualizar QR", "Hide QR / Ocultar QR", saludos (Good morning/Buenos días, Good afternoon/Buenas tardes, Good evening/Buenas noches), instrucción "Show this QR to the administrator.../Muestra este QR al administrador...".

### Archivos modificados en Iteración 9

| Archivo | Cambio |
|---|---|
| `gradle/libs.versions.toml` | + `lifecycle-runtime-compose` library |
| `app/build.gradle.kts` | + `implementation(libs.androidx.lifecycle.runtime.compose)` |
| `ui/components/CameraPreview.kt` | Fix import `LocalLifecycleOwner` |
| `ui/screens/scan/ScanScreen.kt` | Fix `topAppBarColors` |
| `ui/screens/admin/QRScannerScreen.kt` | Fix `LocalLifecycleOwner` + `topAppBarColors` |
| `ui/screens/admin/EditEmployeeScreen.kt` | Fix `topAppBarColors` |
| `ui/screens/admin/RegisterEmployeeScreen.kt` | Fix `topAppBarColors` |
| `ui/screens/admin/EmployeeList.kt` | Fix `topAppBarColors` |
| `ui/screens/admin/AdminEmployeeQRScreen.kt` | Fix `topAppBarColors` |
| `ui/screens/admin/ShiftSettingsScreen.kt` | Fix `topAppBarColors` |
| `ui/screens/employee/QRScreen.kt` | Fix `topAppBarColors` |
| `ui/screens/settings/SettingsScreen.kt` | Fix `AutoMirrored.KeyboardArrowRight` + icono Edit + dialog editar nombre |
| `ui/screens/login/LoginScreen.kt` | Fix fondo campos (`onBackground.copy(alpha=0.06f)`) |
| `ui/screens/history/HistoryScreen.kt` | Export CSV funcional + `AttendanceCard` con `lang` + traducciones |
| `ui/screens/confirmation/ConfirmationScreen.kt` | Soporte completo EN/ES en ambas sub-pantallas |
| `ui/viewmodel/BioSecureViewModel.kt` | + `updateUserName(newName)` |

---

## Iteración 8 — Soporte de idioma en ScanScreen y barra de navegación (COMPLETADO)

### Cambios

- **`ScanScreen.kt`**: Todos los textos ahora respetan `LocalAppLanguage.current`. Traducidos: título (`Registro` / `Check-in`), heading (`Registro de Asistencia` / `Attendance Check-in`), saludo (`Bienvenido` / `Welcome`), tabs (`Huella Dactilar` / `Fingerprint`, `Escaneo Facial` / `Facial Scan`), instrucciones de rostro/huella, estado (`Reconocimiento requerido` / `Authentication required`) y botón (`REGISTRAR ENTRADA` / `REGISTER CHECK-IN`). Import `LocalAppLanguage` añadido.
- **`BioSecureBottomBar`** (en `ScanScreen.kt`): Etiquetas traducidas según idioma. ES: `INICIO / ESCANEO / HISTORIAL / AJUSTES / PANEL`. EN: `HOME / SCAN / HISTORY / SETTINGS / DASHBOARD`. La barra es compartida por todas las pantallas.

### Fix de despliegue descubierto en esta sesión

`adb install -r` no siempre reemplaza correctamente una versión anterior instalada manualmente. La solución fiable es:
```
adb uninstall com.biosecure.app
adb install app-debug.apk
```
APK compilado en: `app/build/outputs/apk/debug/app-debug.apk`

---

## Iteración 7 — PENDIENTE status, EmployeeDashboard, turnos por empleado, edición inline de turnos (COMPLETADO)

### Nuevas funcionalidades

- **`AttendanceStatus.PENDIENTE`**: Nuevo estado añadido al enum en `Attendance.kt`. El registro biométrico del empleado queda en PENDIENTE hasta que el admin escanee su QR.
- **`AttendanceRepository`**: Tres nuevas funciones — `confirmAttendance(attendanceId)`, `getTodayPendingAttendance(uid, date)`, `getTodayAttendanceByUser(uid, date)`.
- **`BioSecureViewModel`**: Nuevos StateFlows `_currentShiftName`, `_todayAttendanceId`, `_todayAttendance`. Nuevas funciones: `loadEmployeeShiftName()`, `loadTodayAttendance()`, `findTodayPendingAttendance()`, `confirmAttendance()`. `registerAttendance()` guarda `PENDIENTE` en lugar de calcular el status. `checkAttendanceStatus()` acepta `shiftName?` para buscar el turno del empleado específico.
- **`EmployeeDashboard.kt`** (NUEVO): Pantalla de inicio para empleados. Muestra QR del empleado (generado con ZXing desde su UID). Con `AnimatedContent` según el estado de hoy: ✅ + mensaje si EXITOSO/PUNTUAL/TARDANZA; QR + "⏳ Pendiente de confirmación" si PENDIENTE; QR + botón biométrico si sin registro. Botón "🔄 Actualizar estado".
- **`QRScannerScreen`**: Al escanear un QR, busca registro PENDIENTE de hoy para ese UID. Si existe → `confirmAttendance` → EXITOSO. Si no → crea nuevo registro con status calculado normalmente.
- **`RegisterEmployeeScreen`**: Dropdown `ExposedDropdownMenuBox` para seleccionar turno de `viewModel.shifts`. Guarda `shiftName` en Firestore si se selecciona uno.
- **`ShiftSettingsScreen`**: Icono de edición (lápiz) por cada `ShiftCard`. Al pulsar, abre `EditShiftDialog` con `TimePickerModal` pre-poblado para modificar `checkInStart` y `checkInEnd` sin eliminar el turno.
- **`ScanScreen`**: Flujo de empleado (biométrico o facial) llama `registerAttendance(ScanType.HUELLA/FACIAL)` y navega a `Screen.EmployeeDashboard.route` en lugar de Confirmation. Admin sigue navegando a Confirmation. `BioSecureBottomBar` añade "INICIO 🏠" al principio para empleados.
- **`NavGraph`**: Nuevo `Screen.EmployeeDashboard : Screen("employee/dashboard")`. Composable registrado. `AdminRouteGuard` redirige a `EmployeeDashboard` en vez de `EmployeeScan`. Login navega a `EmployeeDashboard` para rol employee.
- **`HistoryScreen`**: `AttendanceCard` incluye `AttendanceStatus.PENDIENTE -> "Pendiente"` en el `when` del label de estado (necesario para compilación tras añadir el enum).

### Flujo PENDIENTE — resumen
1. Empleado hace scan biométrico → `registerAttendance()` → Firestore: `status: "PENDIENTE"` → navega a `EmployeeDashboard`
2. `EmployeeDashboard` muestra el QR del empleado con badge "⏳ Pendiente"
3. Admin abre `QRScannerScreen`, escanea el QR → encuentra el doc PENDIENTE → llama `confirmAttendance()` → doc se actualiza a `EXITOSO`
4. Empleado pulsa "🔄 Actualizar estado" → `EmployeeDashboard` muestra ✅

---

## Iteración 6b — Fixes de visibilidad, debug logs, LoginScreen y animaciones (COMPLETADO)

### Cambios

- **DashboardScreen:** Card "Configurar Horarios" movida a posición 5 (justo después de los botones Scan + Historial, antes de "Registrar nuevo empleado"). Ahora visible sin necesidad de hacer scroll. Color de fondo cambiado a `primaryContainer` para distinguirla visualmente.
- **BioSecureViewModel:** `loadAttendanceHistory()` añade `Log.d("HISTORY_DEBUG", "UID: $uid, Role: $role, CompanyId: $companyId")` para depurar el historial por rol. `registerAttendance()` catch añade `Log.e("ATTENDANCE_DEBUG", "Error: ${e.message}")`.
- **LoginScreen:** `OutlinedTextField` → `TextField` (filled style) con `TextFieldDefaults.colors()` adaptado a tema oscuro/claro vía `isSystemInDarkTheme()`. El indicador inferior es `#1ED9C5` al enfocar y transparente en reposo; los contenedores cambian entre `#1E2D2A` (dark) y `#F0F4F3` (light).
- **HistoryScreen:** `AttendanceCard` en el bloque `items(records)` ahora envuelto en `AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically())`. Importaciones añadidas: `AnimatedVisibility`, `fadeIn`, `slideInVertically`.

---

## Iteración 6 — Turnos múltiples, modo oscuro mejorado, animaciones y bug fixes (COMPLETADO)

### Nuevas funcionalidades

#### Configuración de turnos por empresa

Se reemplazó el modelo de un único `checkInStart/checkInEnd` por una lista de turnos por empresa. Cada empresa puede tener N turnos con nombre y rango horario propio.

**Nuevo modelo:** `data/model/Shift.kt`
```kotlin
data class Shift(name: String, checkInStart: String, checkInEnd: String)
```

**Almacenamiento Firestore:** campo `shifts` en `companies/{companyId}` como lista de mapas.
```
companies/Pizzeria
  └── shifts: [
        { name: "Turno Mañana", checkInStart: "07:00", checkInEnd: "08:30" },
        { name: "Turno Tarde",  checkInStart: "13:00", checkInEnd: "14:00" }
      ]
```

**Lógica de detección:** `checkAttendanceStatus()` encuentra el turno cuyo `checkInStart` es más cercano a la hora actual y evalúa PUNTUAL/TARDANZA contra ese turno. Si `shifts` está vacío, usa el campo legado `checkInEnd`.

#### Nueva pantalla: ShiftSettingsScreen

- Accesible desde Dashboard → "Configurar Horarios"
- Lista turnos actuales con nombre y rango horario en Cards con `AnimatedVisibility`
- Botón `+` en la AppBar abre diálogo para agregar turno (nombre + 2 TimePickerModal en formato 24h)
- Botón eliminar por turno (ícono `Icons.Outlined.Delete`)
- Botón "Guardar cambios" llama `viewModel.saveShifts()`
- Ruta: `admin/shift-settings`

### Bug fix — hora de entrada no aparece en historial

**Causa:** `registerAttendance()` y `registerManualAttendance()` guardaban en Firestore pero no actualizaban `_attendanceHistory` (StateFlow que alimenta HistoryScreen). El historial solo se refrescaba al llamar `loadAttendanceHistory()` explícitamente.

**Solución:** Después de `attendanceRepository.saveAttendance()`, se añade el nuevo registro directamente a `_attendanceHistory` con todos los campos que HistoryScreen necesita. El empleado ve su hora de entrada inmediatamente sin necesidad de recargar la pantalla.

### Modo oscuro mejorado (Theme.kt)

`DarkColorScheme` reemplazado con paleta más vibrante y de mayor contraste:

| Token | Antes | Ahora |
|---|---|---|
| `primary` | `#00B4A6` | `#1ED9C5` |
| `onPrimary` | `White` | `#003731` |
| `background` | `#121212` | `#101512` |
| `surface` | `#1A1A1A` | `#1A2420` |
| `surfaceVariant` | `#2A2A2A` | `#2A3530` |
| `onSurfaceVariant` | `#AAAAAA` | `#C0CCC6` |
| `outline` | `#00B4A6` | `#8A9590` |

### Animaciones

- `DashboardScreen`: MetricCards y StatItem Card envueltos en `AnimatedVisibility(fadeIn + slideInVertically)` al cargar
- `ShiftSettingsScreen`: items de turno con `AnimatedVisibility(fadeIn + slideInVertically)`
- Botones principales (`REGISTRAR ENTRADA`, Scan, Historial): `.animateContentSize()` para transiciones suaves de contenido

### Íconos mejorados (Icons.Outlined)

- `Icons.Default.CheckCircle` → `Icons.Outlined.CheckCircle` en ScanScreen y ConfirmationScreen
- `Icons.Outlined.Add` y `Icons.Outlined.Delete` en ShiftSettingsScreen
- Dependencia `material-icons-extended` ya estaba presente en BOM

### Archivos creados/modificados en Iteración 6

| Archivo | Cambio |
|---|---|
| `data/model/Shift.kt` | **NUEVO** — data class con `name`, `checkInStart`, `checkInEnd` |
| `data/repository/CompanyRepository.kt` | + `getShifts()`, `saveShifts()` |
| `ui/viewmodel/BioSecureViewModel.kt` | + `_shifts` StateFlow; `loadShifts()`, `saveShifts()`; `checkAttendanceStatus()` usa turnos múltiples con fallback; `timeToMinutes()` helper; `registerAttendance/registerManualAttendance` actualizan `_attendanceHistory` tras guardar; `logout()` limpia `_shifts` |
| `ui/screens/admin/ShiftSettingsScreen.kt` | **NUEVO** — lista de turnos, dialog con TimePicker, guardar en Firestore |
| `ui/screens/dashboard/DashboardScreen.kt` | + AnimatedVisibility en MetricCards y StatCard; + animateContentSize en botones; + Card "Configurar Horarios" → `AdminShiftSettings` |
| `ui/screens/scan/ScanScreen.kt` | `Icons.Default.CheckCircle` → `Icons.Outlined.CheckCircle`; + `animateContentSize()` en botón principal |
| `ui/screens/confirmation/ConfirmationScreen.kt` | `Icons.Default.CheckCircle` → `Icons.Outlined.CheckCircle` |
| `ui/theme/Theme.kt` | `DarkColorScheme` reemplazado con paleta más vibrante y legible |
| `ui/navigation/NavGraph.kt` | + `Screen.AdminShiftSettings`; + composable para `ShiftSettingsScreen` |

---

## Iteración 5 — Multi-tenant: horarios por empresa + aislamiento de datos (COMPLETADO)

### Arquitectura multi-tenant

Cada empresa es un documento en la colección `companies/`. El campo `companyId` en `users/` y `attendances/` actúa como clave de partición: todas las consultas que emiten admins o empleados se filtran por ese valor, garantizando que los datos de una empresa nunca sean visibles para otra.

**Colección nueva:** `companies/{companyId}`

| Campo | Tipo | Descripción |
|---|---|---|
| `name` | String | Nombre visible de la empresa |
| `checkInStart` | String | Hora de inicio de jornada (`HH:mm`) |
| `checkInEnd` | String | Límite para marcar PUNTUAL (`HH:mm`) |

Ejemplo — crear manualmente en Firestore Console:
```
companies/Pizzeria
  ├── name: "Pizzería"
  ├── checkInStart: "08:00"
  └── checkInEnd: "09:00"
```

**Campo `companyId` en `users/{uid}`:**
- Los admins deben tener `companyId: "Pizzeria"` (o el id de su empresa) en su documento.
- Los empleados reciben automáticamente el `companyId` del admin que los registra (`RegisterEmployeeScreen` lo hereda de `viewModel.currentCompanyId`).

**Campo `companyId` en `attendances/{docId}`:**
- Se guarda automáticamente en cada asistencia desde `AttendanceRepository.saveAttendance()` y desde `QRScannerScreen`.

---

### Flujo de companyId de extremo a extremo

```
Login → loginWithRole() lee companyId desde users/{uid}
      → BioSecureViewModel._currentCompanyId poblado

MainActivity.onCreate()
      → viewModel.initializeSessionIfLoggedIn()  (sesión persistente)
      → también popula _currentCompanyId desde Firestore

ScanScreen (empleado hace scan biométrico o facial)
      → viewModel.computePendingStatus()
         └── checkAttendanceStatus(companyId)
              └── companies/{companyId}.checkInEnd vs hora actual
              → PUNTUAL si hora ≤ checkInEnd, TARDANZA si no

QRScannerScreen (admin escanea QR)
      → lee viewModel.currentCompanyId
      → llama viewModel.checkAttendanceStatus(companyId) (suspend)
      → guarda companyId + status en attendances/

RegisterEmployeeScreen
      → toma viewModel.currentCompanyId al guardar users/{uid}
      → garantiza que empleados hereden el tenant del admin

loadFirestoreEmployees()
      → si companyId != null → getEmployeesByCompany(companyId)
      → si companyId == null → getEmployees() (fallback sin filtro)

loadAttendanceHistory() — admin
      → si companyId != null → getAttendancesByCompany(companyId)
      → si companyId == null → getAllAttendances()
```

---

### Regla de índices Firestore

| Consulta | Tipo de índice | Creación |
|---|---|---|
| `users WHERE companyId = X` | Single-field | Automático |
| `attendances WHERE companyId = X` | Single-field | Automático |
| `users WHERE companyId = X AND role = "employee"` | Filtro en memoria (evita índice compuesto) | N/A |

`getEmployeesByCompany()` filtra `role == "employee"` en memoria después de la consulta por `companyId` para no requerir un índice compuesto manual.

---

### Archivos creados/modificados en esta iteración

| Archivo | Cambio |
|---|---|
| `data/repository/CompanyRepository.kt` | **NUEVO** — `getCompany()`, `createCompany()` |
| `data/model/Attendance.kt` | + campo `companyId: String = ""` |
| `data/repository/AuthRepository.kt` | `UserLoginResult` + `companyId`; `loginWithRole` lee `companyId`; `getUserData()` nuevo |
| `data/repository/AttendanceRepository.kt` | `saveAttendance` guarda `companyId`; nuevos `getEmployeesByCompany()`, `getAttendancesByCompany()` |
| `ui/viewmodel/BioSecureViewModel.kt` | + `companyRepository`; `_currentCompanyId`; `_pendingAttendanceStatus`; `checkAttendanceStatus()`; `computePendingStatus()`; `initializeSessionIfLoggedIn()`; `registerAttendance/registerManualAttendance` usan `checkAttendanceStatus`; `loadFirestoreEmployees/loadAttendanceHistory` filtran por companyId; `BioSecureViewModelFactory` actualizado |
| `ui/screens/scan/ScanScreen.kt` | `computePendingStatus()` antes de navegar (biométrico + facial) |
| `ui/screens/admin/QRScannerScreen.kt` | Lee `companyId` del ViewModel; `checkAttendanceStatus()`; guarda `companyId` + `status` real |
| `ui/screens/admin/RegisterEmployeeScreen.kt` | Hereda `companyId` del admin al crear empleado |
| `MainActivity.kt` | + `CompanyRepository()`; `initializeSessionIfLoggedIn()` en `onCreate()` |

---

### Próxima iteración 6

- [ ] Agregar campo `companyId` a documentos admin existentes en Firestore Console (migración manual)
- [ ] Pantalla de configuración de empresa (Admin puede ver/editar checkInStart / checkInEnd)
- [ ] Dashboard muestra horario de empresa ("Horario: 08:00–09:00")
- [ ] Claude API para consultas RRHH en Dashboard
- [ ] Geofencing por empresa
- [ ] Migrar `LocalLifecycleOwner` a `lifecycle-runtime-compose`

---

## Contexto para próxima sesión

Proyecto: BioSecure - Android biométrico en Kotlin + Compose + MVVM
Ruta: C:\Users\User\Desktop\BioSecure
Stack: AGP 9.1.1, Kotlin 2.2.10, Firebase Auth + Firestore, ZXing QR, CameraX, BiometricPrompt

**Iteración 2 COMPLETA:**
- Login automático por rol desde Firestore (`users/{uid}.role`)
- Nombre real desde Firestore (`users/{uid}.name`) guardado en `BioSecureViewModel._currentUserName`
- UID guardado en `BioSecureViewModel._currentUserUid` al login
- Scan biométrico (huella + facial) vinculado al usuario — NO registra en Firestore al escanear
- Flujo QR completo: empleado hace scan → `ConfirmationScreen` muestra QR con su UID → Admin abre `QRScannerScreen` → escanea → registra en `attendances/` → `ConfirmationScreen` modo admin con ✅ animado
- Registro de empleados: Admin crea cuenta Firebase Auth via secondary app → guarda en `users/{uid}` → navega a `AdminEmployeeQRScreen` con el UID
- Splash screen con sesión persistente (`getRoleForCurrentUser()`)

**Flujo de datos clave:**
```
Login → loginWithRole() → users/{uid} → role + name + uid guardados en ViewModel
Empleado scan → ConfirmationScreen (employee mode: QR 260dp con uid)
Admin QRScanner → lee uid del QR → users/{uid}.name → attendances/ → setConfirmationData()
Admin ConfirmationScreen → lastConfirmation (userName, checkIn, date)
```

**Próxima iteración 3:**
1. Historial real desde Firestore (`attendances/` filtrado por `uid`)
2. Claude API para consultas RRHH en Dashboard
3. ML Kit reconocimiento facial real
4. Geofencing
5. Notificaciones FCM

---

## Errores conocidos y soluciones

### Bug crítico resuelto — INTERNET permission faltante

- **Problema:** Historial y asistencias no se guardaban/cargaban (0 documentos en UI)
- **Causa:** Faltaba `<uses-permission android:name="android.permission.INTERNET"/>` en `AndroidManifest.xml`
- **Firebase** entraba en modo offline silencioso sin lanzar excepciones; escrituras devolvían éxito falso desde caché local
- **Solución:** Agregar el permiso en `AndroidManifest.xml` antes de `<application>`
- **Detectado por:** `DNS isBlocked=true` en logcat; `CACHE=0 SERVER=0` en streams de Firestore
- **Estado:** ✅ RESUELTO — Iteración 7

---

### CRÍTICO — Permiso INTERNET faltante en AndroidManifest.xml (Iteración 7)

**Síntoma:** Firestore no lee nada (0 documentos en historial, colecciones vacías), registros parecen guardarse (no da error) pero no aparecen nunca en la UI. Logcat muestra `DNS isBlocked=true`, streams `UNAVAILABLE`, lecturas devuelven `CACHE=0 SERVER=0`.

**Causa raíz:** Falta `<uses-permission android:name="android.permission.INTERNET" />` en `AndroidManifest.xml`. Sin ese permiso, Android bloquea toda conexión de red en Android 6+. Firebase SDK entra en modo offline: las escrituras se encolan en caché local (devuelven éxito falso), todas las lecturas devuelven la caché (vacía). No se lanza ninguna excepción.

**Por qué ocurrió:** El permiso INTERNET **no** se agrega automáticamente por el SDK de Firebase ni por dependencias de Gradle. Debe declararse manualmente. Al agregar Firebase a un proyecto existente es fácil olvidarlo.

**Solución:** Agregar en `AndroidManifest.xml` antes de `<application>`:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

**Regla para el futuro:** Verificar este permiso PRIMERO ante cualquier síntoma de Firestore vacío o sin conexión. Sin INTERNET, Firebase produce fallos silenciosos imposibles de detectar sin logcat.

**Estado:** Resuelto en Iteración 7 — confirmado por usuario: "Perfecto ahora si funciona".

---

### Campo role null en Firestore

- Problema: loginWithRole retorna "Usuario sin rol asignado" aunque el documento existe
- Causa: Campo role creado con espacio en blanco o valor vacío en Firebase Console
- Solución: Borrar y recrear el documento manualmente en Firestore
  asegurándose que el campo role tenga exactamente "admin" o "employee" sin espacios
- Diagnóstico: Agregar Source.SERVER y logs AUTH_DEBUG para verificar

---

### AGP 9.2.1 — Build falla al actualizar el plugin

**Síntoma:** Al cambiar `agp = "9.2.1"` en `libs.versions.toml` el build rompe con errores relacionados a variantes de compilación o flags de namespace obligatorio.

**Causa:** AGP 9.2.x introduce cambios breaking en cómo se resuelven las variantes de build y requiere `namespace` declarado explícitamente en todos los módulos, además de ajustes en las opciones de compilación Java.

**Solución:**
1. Mantener `agp = "9.1.1"` hasta confirmar compatibilidad.
2. Si se necesita actualizar, agregar en `app/build.gradle.kts`:
   ```kotlin
   android {
       namespace = "com.biosecure.app"   // ya presente — verificar que esté
       compileOptions {
           sourceCompatibility = JavaVersion.VERSION_17
           targetCompatibility = JavaVersion.VERSION_17
       }
   }
   ```
3. Actualizar también el wrapper: `.\gradlew.bat wrapper --gradle-version 9.5`.
4. Sincronizar y limpiar: `.\gradlew.bat clean build`.

---

### JAVA_HOME apunta a ruta inválida

**Síntoma:** `ERROR: JAVA_HOME is set to an invalid directory`.

**Solución:** Usar siempre la terminal integrada de Android Studio, o fijar manualmente:
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-19"
.\gradlew.bat build
```

---

### BiometricPrompt — `FragmentActivity` requerido

**Síntoma:** `ClassCastException` al intentar mostrar el prompt biométrico.

**Causa:** `BiometricPrompt` requiere que la Activity sea `FragmentActivity`, no `ComponentActivity`.

**Solución:** `MainActivity` ya extiende `FragmentActivity`. No cambiar la herencia.

---

### `isSystemInDarkTheme()` no coincide con el modo oscuro controlado por la app (RESUELTO — Iteración 8)

**Síntoma:** Los campos de texto de `LoginScreen` aparecen verde oscuro / colores incorrectos en modo claro, pero solo en el dispositivo físico. En el emulador se ven bien.

**Causa raíz:** La app controla el modo oscuro internamente via `SharedPreferences` (clave `dark_mode`) y lo pasa como parámetro `isDarkMode` al tema `BioSecureTheme`. Pero `isSystemInDarkTheme()` lee el modo del **sistema operativo**, no el de la app. Si el sistema está en modo claro y la app en oscuro (o viceversa), `isSystemInDarkTheme()` devuelve el valor incorrecto y aplica colores contradictorios.

**Solución definitiva:** No usar `isSystemInDarkTheme()` en ninguna pantalla. Usar exclusivamente tokens de `MaterialTheme.colorScheme.*` que ya reflejan el tema activo de la app:
```kotlin
// MAL — depende del tema del sistema, no del tema de la app:
val isDarkTheme = isSystemInDarkTheme()
containerColor = if (isDarkTheme) Color(0xFF2A2A2A) else Color.White

// BIEN — sigue el tema real de la app:
containerColor = MaterialTheme.colorScheme.surfaceVariant
```
Si el token del tema no da el resultado visual correcto, el problema está en `Theme.kt` (definir el token allí), no en cada pantalla.

**Regla:** Toda diferenciación de colores por tema debe vivir en `Theme.kt` → `DarkColorScheme` / `BioSecureLightColorScheme`. Las pantallas solo consumen tokens del tema.

---

### Campos de `TextField` invisibles sobre Card en modo claro — `surfaceVariant = White` (RESUELTO — Iteración 9)

**Síntoma:** Los campos de login (y similares) no tienen fondo visible en modo claro; se funden con el Card blanco que los contiene.

**Causa:** `BioSecureLightColorScheme` define `surfaceVariant = White`. Al usar `containerColor = MaterialTheme.colorScheme.surfaceVariant` el campo queda blanco sobre fondo blanco → indistinguible.

**Solución:** Usar un color de fondo derivado del texto del fondo, no de `surfaceVariant`:
```kotlin
val fieldBg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
TextFieldDefaults.colors(
    focusedContainerColor = fieldBg,
    unfocusedContainerColor = fieldBg,
    ...
)
```
`onBackground` es blanco en oscuro y negro en claro. Al 6% de opacidad produce un gris muy sutil en claro y un blanco levemente traslúcido en oscuro. Funciona en ambos temas sin condicionales.

**Dónde aplicar:** Cualquier `TextField` o `OutlinedTextField` dentro de una superficie blanca (`Card`, `Surface`) en modo claro.

---

### `adb install -r` no actualiza la app en Samsung (RESUELTO — Iteración 8)

**Síntoma:** El APK se construye correctamente pero el dispositivo físico Samsung sigue mostrando la versión anterior. `adb install -r` devuelve `Success` pero la app no cambia.

**Causa:** En algunos Samsung con One UI, `install -r` (reemplazar) puede fallar silenciosamente cuando la versión nueva tiene la misma `versionCode` que la instalada, o cuando hay algún conflicto de firma de debug.

**Solución fiable — siempre usar uninstall + install:**
```powershell
$adb = "C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apk = "C:\Users\User\Desktop\BioSecure\app\build\outputs\apk\debug\app-debug.apk"
& $adb -s <device_id> uninstall com.biosecure.app
& $adb -s <device_id> install $apk
& $adb -s <device_id> shell am start -n com.biosecure.app/.MainActivity
```
Obtener `<device_id>` con `& $adb devices`.

**Nota:** `adb` no está en el PATH del sistema. Siempre usar la ruta completa: `C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

---

### `gradlew.bat` falla en PowerShell (RESUELTO — Iteración 8)

**Síntoma:** `.\gradlew.bat assembleDebug` falla en PowerShell con errores de classpath o no encuentra el wrapper.

**Causa:** PowerShell tiene problemas con `gradlew.bat` en ciertos contextos por cómo resuelve el PATH y los argumentos en `.bat`.

**Solución:** Usar el Bash tool de Claude Code con `./gradlew assembleDebug` (sin `.bat`). El Bash tool ejecuta Git Bash (POSIX), no PowerShell, y el wrapper de Gradle funciona correctamente ahí.

---

### APIs deprecadas de Material3 TopAppBar y Icons (RESUELTO — Iteración 9)

**Síntoma:** Warnings de compilación en múltiples pantallas:
- `centerAlignedTopAppBarColors is deprecated. Use topAppBarColors instead.`
- `Icons.Filled.KeyboardArrowRight is deprecated. Use AutoMirrored version instead.`

**Causa:** Material3 consolidó las funciones de colores de TopAppBar y marcó `centerAlignedTopAppBarColors` como obsoleta. `KeyboardArrowRight` debe ser `AutoMirrored` para soporte RTL correcto.

**Solución:**
```kotlin
// centerAlignedTopAppBarColors:
TopAppBarDefaults.centerAlignedTopAppBarColors(...)  // deprecado
TopAppBarDefaults.topAppBarColors(...)                // correcto

// KeyboardArrowRight:
Icons.Default.KeyboardArrowRight          // deprecado
Icons.AutoMirrored.Filled.KeyboardArrowRight  // correcto
// Agregar import: androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
```
**Archivos afectados:** ScanScreen, QRScannerScreen, EditEmployeeScreen, RegisterEmployeeScreen, EmployeeList, AdminEmployeeQRScreen, ShiftSettingsScreen, QRScreen (employee), SettingsScreen.

---

### `LocalLifecycleOwner` deprecated en CameraPreview y QRScannerScreen (RESUELTO — Iteración 9)

**Síntoma:** Warning `'val LocalLifecycleOwner: ProvidableCompositionLocal<LifecycleOwner>' is deprecated`.

**Causa:** En Compose reciente `LocalLifecycleOwner` se movió de `androidx.compose.ui.platform` a `androidx.lifecycle.compose`. El import viejo sigue compilando pero genera warning.

**Solución:**
1. Agregar dependencia en `libs.versions.toml`:
   ```toml
   androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
   ```
2. Agregar en `app/build.gradle.kts`:
   ```kotlin
   implementation(libs.androidx.lifecycle.runtime.compose)
   ```
3. Cambiar el import en cada archivo afectado:
   ```kotlin
   // Antes (deprecated):
   import androidx.compose.ui.platform.LocalLifecycleOwner
   // Después:
   import androidx.lifecycle.compose.LocalLifecycleOwner
   ```
   Archivos afectados: `CameraPreview.kt`, `QRScannerScreen.kt`.

**Estado:** ✅ RESUELTO — Iteración 9.

---

### Modo oscuro — campos de texto invisibles (RESUELTO)

**Síntoma:** Los `OutlinedTextField` muestran texto invisible (negro sobre negro) al activar el modo oscuro.

**Problema:** Material 3 sobreescribe colores en modo oscuro con sus defaults internos cuando el `darkColorScheme` no está definido explícitamente.

**Causa raíz:** `darkColorScheme` no estaba definido con todos los tokens necesarios (`onSurface`, `outline`, `surfaceVariant`, etc.). Material 3 infería colores que resultaban en texto ilegible sobre superficies oscuras.

**Solución 1 — Global (Theme.kt):** Definir `darkColorScheme` completo con todos los tokens explícitos:
```kotlin
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00B4A6),
    onPrimary = Color.White,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    outline = Color(0xFF00B4A6),
    // ... resto de tokens
)
```

**Solución 2 — Por pantalla:** Agregar `OutlinedTextFieldDefaults.colors()` con colores explícitos en cada pantalla que tenga campos de texto:
```kotlin
val isDarkTheme = isSystemInDarkTheme()

OutlinedTextFieldDefaults.colors(
    focusedTextColor = if (isDarkTheme) Color.White else Color.Black,
    unfocusedTextColor = if (isDarkTheme) Color.White else Color.Black,
    focusedBorderColor = Color(0xFF00B4A6),
    unfocusedBorderColor = Color(0xFF00B4A6),
    focusedLabelColor = Color(0xFF00B4A6),
    unfocusedLabelColor = if (isDarkTheme) Color(0xFFCCCCCC) else Color.Gray,
    cursorColor = Color(0xFF00B4A6)
)
```

**Colores correctos modo oscuro:**
- `background` = `#121212`
- `surface` = `#1E1E1E`
- `onBackground` = `White`
- `onSurface` = `White`
- `primary` = `#00B4A6`
- `outline` = `#00B4A6`

---

### Regla general para nuevas pantallas con campos de texto

- Siempre agregar `isSystemInDarkTheme()` y definir colores explícitos en los `OutlinedTextField`.
- Nunca dejar que Material 3 infiera los colores en modo oscuro.
- Usar `import androidx.compose.foundation.isSystemInDarkTheme` y `import androidx.compose.ui.graphics.Color` en cada pantalla con campos.

---

### Regla definitiva para campos de texto en modo oscuro (Card + TextField)

**Problema raíz:** La combinación `Card(containerColor = surface)` + `TextField(containerColor = surface)` produce colores casi idénticos en modo oscuro, haciendo el texto invisible. Los valores deben diferenciarse intencionalmente.

**Valores correctos garantizados:**

| Elemento | Modo oscuro | Modo claro |
|---|---|---|
| `Card containerColor` | `Color(0xFF1A1A1A)` | `Color.White` |
| `TextField focusedContainerColor` | `Color(0xFF2A2A2A)` | `Color.White` |
| `TextField unfocusedContainerColor` | `Color(0xFF2A2A2A)` | `Color.White` |
| `TextField focusedTextColor` | `Color.White` | `Color.Black` |
| `TextField unfocusedTextColor` | `Color.White` | `Color.Black` |
| `TextField focusedBorderColor` | `Color(0xFF00B4A6)` | `Color(0xFF00B4A6)` |
| `TextField unfocusedBorderColor` | `Color(0xFF00B4A6)` | `Color(0xFF00B4A6)` |

**Patrón completo para cualquier pantalla con Card + TextField:**
```kotlin
val isDarkTheme = isSystemInDarkTheme()

// Card que envuelve los campos:
colors = CardDefaults.cardColors(
    containerColor = if (isDarkTheme) Color(0xFF1A1A1A) else Color.White
)

// Cada OutlinedTextField dentro:
colors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = if (isDarkTheme) Color.White else Color.Black,
    unfocusedTextColor = if (isDarkTheme) Color.White else Color.Black,
    focusedBorderColor = Color(0xFF00B4A6),
    unfocusedBorderColor = Color(0xFF00B4A6),
    focusedContainerColor = if (isDarkTheme) Color(0xFF2A2A2A) else Color.White,
    unfocusedContainerColor = if (isDarkTheme) Color(0xFF2A2A2A) else Color.White,
    focusedLabelColor = Color(0xFF00B4A6),
    unfocusedLabelColor = if (isDarkTheme) Color(0xFFCCCCCC) else Color.Gray,
    cursorColor = Color(0xFF00B4A6)
)
```

---

### LoginScreen — fondo de campos de texto incorrecto en modo oscuro (RESUELTO)

**Síntoma:** Los `OutlinedTextField` en `LoginScreen` mostraban fondo blanco sobre superficie oscura, haciendo el campo visible pero con fondo incorrecto.

**Causa:** `OutlinedTextFieldDefaults.colors()` no incluía `focusedContainerColor` ni `unfocusedContainerColor`, por lo que Material 3 aplicaba su default blanco también en modo oscuro.

**Solución (LoginScreen.kt):** Agregar las dos propiedades al bloque `textFieldColors`:
```kotlin
focusedContainerColor = if (isDarkTheme) Color(0xFF2C2C2C) else Color.White,
unfocusedContainerColor = if (isDarkTheme) Color(0xFF2C2C2C) else Color.White
```

---

### Tabs de ScanScreen invisibles / colores incorrectos en modo oscuro (RESUELTO)

**Síntoma:** Los botones de tab "Huella Dactilar" / "Escaneo Facial" en `ScanScreen` mostraban colores erróneos en modo oscuro porque `MaterialTheme.colorScheme.primary` y `.surface` son sobreescritos por el sistema.

**Causa:** `ButtonDefaults.buttonColors()` usaba tokens del tema (`primary`, `surface`, `onPrimary`, `onSurface`) que en modo oscuro podían resultar en bajo contraste o colores inesperados según el `DarkColorScheme` activo.

**Solución (ScanScreen.kt):** Usar literales explícitos con distinción de tema para el estado no seleccionado:
```kotlin
val isDarkTheme = isSystemInDarkTheme()
// ...
colors = ButtonDefaults.buttonColors(
    containerColor = if (isSelected) Color(0xFF0D3B35)
                     else if (isDarkTheme) Color(0xFF2C2C2C)
                     else Color(0xFFF0F0F0),
    contentColor = if (isSelected) Color.White else Color(0xFF00B4A6)
)
```
Agregar `import androidx.compose.foundation.isSystemInDarkTheme` y `import androidx.compose.ui.graphics.Color` si no están presentes.

---

### ScanScreen — hora hardcodeada reemplazada por ScanViewModel.currentTime (RESUELTO)

**Síntoma:** El texto `"08:45 AM"` en `ScanScreen` era estático y no reflejaba la hora real.

**Solución (ScanScreen.kt):**
1. Agregar `scanViewModel: ScanViewModel? = null` como parámetro opcional de `ScanScreen`.
2. Reemplazar el `Text` hardcodeado:
```kotlin
val currentTime by (scanViewModel?.currentTime ?: kotlinx.coroutines.flow.MutableStateFlow("")).collectAsState()
Text(text = currentTime.ifEmpty { "08:45 AM" }, ...)
```
El fallback `"08:45 AM"` garantiza que la pantalla muestre algo si `scanViewModel` es null (preview, tests).

---

### Botón "Entrar como Admin" — borde y texto incorrectos en modo oscuro (RESUELTO)

**Síntoma:** El `OutlinedButton` de admin en `LoginScreen` usaba `MaterialTheme.colorScheme.primary` para borde y contenido, lo que resultaba en colores inconsistentes según el tema activo.

**Causa:** Material 3 mapea `primary` a distintos valores entre `lightColorScheme` y `darkColorScheme`, produciendo resultados visuales diferentes al esperado.

**Solución (LoginScreen.kt):** Usar literales explícitos:
```kotlin
colors = ButtonDefaults.outlinedButtonColors(
    contentColor = if (isDarkTheme) Color.White else Color(0xFF0D3B35)
),
border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00B4A6))
```
`isDarkTheme` ya está definido como `val isDarkTheme = isSystemInDarkTheme()` al inicio del composable.

---

## Versiones exactas de dependencias

Extraídas de `gradle/libs.versions.toml` — última sincronización exitosa con `BUILD SUCCESSFUL`.

### Plugins / AGP

| Clave | Versión |
|---|---|
| `agp` (Android Gradle Plugin) | `9.1.1` |
| `kotlin` | `2.2.10` |

### AndroidX Core

| Biblioteca | Versión |
|---|---|
| `core-ktx` | `1.18.0` |
| `lifecycle-runtime-ktx` | `2.10.0` |
| `lifecycle-viewmodel-compose` | `2.10.0` |
| `activity-compose` | `1.13.0` |
| `fragment-ktx` | `1.8.5` |

### Compose

| Biblioteca | Versión |
|---|---|
| `compose-bom` | `2026.02.01` |
| `navigation-compose` | `2.9.0` |
| material3, ui, ui-graphics, ui-tooling | gestionadas por BOM |

### Red e imágenes

| Biblioteca | Versión |
|---|---|
| `retrofit` | `2.9.0` |
| `converter-gson` | `2.9.0` |
| `coil-compose` | `2.6.0` |

### Biometría y cámara

| Biblioteca | Versión |
|---|---|
| `androidx-biometric` | `1.2.0-alpha05` |
| `camera-core` | `1.3.1` |
| `camera-camera2` | `1.3.1` |
| `camera-lifecycle` | `1.3.1` |
| `camera-view` | `1.3.1` |
| `mlkit face-detection` | `16.1.5` |

### Testing

| Biblioteca | Versión |
|---|---|
| `junit` | `4.13.2` |
| `androidx-junit` | `1.3.0` |
| `espresso-core` | `3.7.0` |

---

## Estado actual — Iteraciones

### Iteración 1 — Base funcional ✅
- [x] App base Login, Scan, History, Settings, Dashboard
- [x] BioSecureViewModel + ScanViewModel
- [x] Navegación separada por rol (`isAdmin` en rutas)
- [x] CRUD empleados (crear, editar, eliminar)
- [x] Modo oscuro/claro con tokens Material 3 completos
- [x] Tema `Theme.kt` con `LightColorScheme` y `DarkColorScheme` completos

### Iteración 2 — Firebase + Flujo QR ✅
- [x] Firebase Authentication real (email/password)
- [x] Firestore para asistencias (`attendances/`) y usuarios (`users/`)
- [x] Validación de roles desde Firestore (`users/{uid}.role`)
- [x] Login único con detección automática de rol — un solo botón "Iniciar Sesión"
- [x] Nombre real desde Firestore (`users/{uid}.name`) en `currentUserName` StateFlow
- [x] UID del usuario en `currentUserUid` StateFlow (fuente del QR)
- [x] Splash screen con sesión persistente (`getRoleForCurrentUser()`)
- [x] `ScanScreen` — biométrico (huella/facial) navega a ConfirmationScreen sin registrar
- [x] `ConfirmationScreen` — modo empleado: QR 260dp con UID + "Escanea con el Admin"
- [x] `ConfirmationScreen` — modo admin: ✅ animado spring + datos de `lastConfirmation`
- [x] `QRScannerScreen` — lee QR, consulta `users/{uid}.name`, guarda en `attendances/`, llama `setConfirmationData()`
- [x] `RegisterEmployeeScreen` — crea cuenta Firebase Auth via secondary app + guarda en `users/{uid}`
- [x] `AdminEmployeeQRScreen` — muestra QR del nuevo empleado con botón "Compartir QR"
- [x] `ConfirmationData` model con `userName`, `checkIn`, `date`, `scanType`
- [x] `AttendanceRepository.saveAttendance()` incluye campo `uid`
- [x] Registro manual por Admin desde Dashboard

### Iteración 3 — IA, ML Kit y extras ⏳
- [x] Historial real desde Firestore (`attendances/` filtrado por `uid`)
- [x] Dashboard con datos reales: `loadFirestoreEmployees()` + `loadUsers()` al entrar; nombre real del admin; conteo real de empleados (`firestoreEmployees.size`)
- [x] `EmployeeListScreen` desde Firestore (`firestoreEmployees`, elimina DummyJSON)
- [x] `ConfirmationScreen` — modo empleado muestra "QR de [nombre]" con `currentUserName`
- [x] `ScanScreen` — eliminado botón "Mostrar QR" (QR aparece solo en ConfirmationScreen)
- [ ] Claude API para consultas RRHH en Dashboard
- [x] ML Kit reconocimiento facial real (reemplazar CameraPreview placeholder)
- [x] Geofencing — verificar ubicación antes de permitir marcación
- [x] Notificaciones push FCM
- [ ] Migrar `LocalLifecycleOwner` a `lifecycle-runtime-compose`
- [ ] Reemplazar `centerAlignedTopAppBarColors` deprecated por `topAppBarColors`
- [ ] Optimización APK release firmado

---

### Iteracion 1 — Estructura base y UI estática
- [x] Crear proyecto Android con Kotlin + Compose
- [x] Configurar paleta de colores y tema Material 3
- [x] Implementar `LoginScreen` con campos email/contraseña
- [x] Implementar `ScanScreen` con tabs Huella / Facial
- [x] Implementar `HistoryScreen` con lista de asistencias mock
- [x] Implementar `DashboardScreen` admin con métricas estáticas
- [x] Implementar `SettingsScreen` con toggle dark mode
- [x] Configurar `NavGraph` con rutas y paso de `isAdmin`
- [x] Integrar `CameraPreview` con CameraX
- [x] Conectar `BiometricPrompt` en `ScanScreen`

### Iteracion 2 — Capa de datos y ViewModels
- [x] Crear modelos `User`, `Attendance`, `AttendanceStatus`, `ScanType`
- [x] Configurar Retrofit + `ApiService` apuntando a DummyJSON
- [x] Implementar `UserRepository`
- [x] Crear `BioSecureViewModel` con StateFlows y `BioSecureViewModelFactory`
- [x] Crear `ScanViewModel` con timer en tiempo real y `ScanState`
- [x] Inicializar ViewModel en `MainActivity` con `ViewModelProvider`
- [x] Pasar `viewModel` a todas las pantallas como parámetro opcional
- [x] `BUILD SUCCESSFUL` verificado post-iteración 2

### Iteracion 3 — Integración real de datos (en progreso)
- [x] `LoginScreen` conectada a `login()` unificado con detección automática de rol
- [x] `HistoryScreen` — historial real desde `attendanceHistory` (Firestore)
- [x] `DashboardScreen` — llama `loadFirestoreEmployees()` + `loadUsers()` en `LaunchedEffect`; nombre real del admin desde `currentUserName`; conteo real de empleados desde `firestoreEmployees.size`
- [x] `EmployeeListScreen` — datos desde Firestore (`firestoreEmployees`), elimina DummyJSON
- [ ] Conectar `registerAttendance()` al flujo de escaneo exitoso (actualmente navega directo a Confirmation sin guardar en Firestore desde ScanScreen)
- [ ] Manejar estados `isLoading` y `error` en todas las pantallas pendientes
- [x] CRUD completo de empleados: `createEmployee`, `updateEmployee`, `deleteEmployee`
- [x] `RegisterEmployeeScreen` conectada a Firebase Auth (secondary app) + Firestore
- [x] `EmployeeListScreen` con eliminación (basura + AlertDialog) desde Firestore
- [x] `EditEmployeeScreen` con formulario prellenado y Snackbar al guardar
- [x] Agregar entrada en `DashboardScreen` para navegar a `EmployeeListScreen`

### Iteracion 3 — Dark mode: regla de colores explícitos en todas las pantallas

**Regla definitiva aplicada a todas las pantallas:**

| Token | Modo oscuro | Modo claro |
|---|---|---|
| Card / contenedor | `Color(0xFF1A1A1A)` | `Color.White` |
| TextField container | `Color(0xFF2A2A2A)` | `Color.White` |
| Texto principal | `Color.White` | `MaterialTheme.colorScheme.onSurface` |
| Texto secundario | `Color(0xFFAAAAAA)` | `.onSurface.copy(alpha = 0.7f)` |
| Borde / acento | `Color(0xFF00B4A6)` | `Color(0xFF00B4A6)` |

**Pantallas actualizadas:**
- [x] `LoginScreen` — ya correcto desde iteraciones anteriores
- [x] `EditEmployeeScreen` — ya correcto desde iteraciones anteriores
- [x] `RegisterEmployeeScreen` — crítico: `fieldColors` sin texto/container → texto invisible; corregido con `isDarkTheme` + colores completos
- [x] `DashboardScreen` — reescrito: `isDarkTheme`, `cardColor` variable, todos los Cards y textos con colores explícitos en `MetricCard`, `StatItem`, `AuditItem`, `PreventiveCard`
- [x] `HistoryScreen` — stat Cards y `AttendanceCard` con `if (isDarkTheme) Color(0xFF1A1A1A) else Color.White`
- [x] `SettingsScreen` — 2 Cards principales con `if (isDarkMode) Color(0xFF1A1A1A) else Color.White` (usa parámetro `isDarkMode` existente)
- [x] `EmployeeList` — `isDarkTheme` añadido; AlertDialog + user Cards con colores explícitos
- [x] `ScanScreen` — `isDarkTheme` añadido; tab Card y scan circle con colores explícitos
- [x] `BUILD SUCCESSFUL` verificado (0 errores)

---

### Iteracion 3 — LoginScreen + ScanViewModel + HistoryScreen fixes

- [x] `LoginScreen` — texto "Iniciar Sesión": `color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface` (evita negro sobre negro en modo oscuro)
- [x] `LoginScreen` — botón "Entrar como Admin": `contentColor = Color.White` siempre (el card ya es oscuro `0xFF1A1A1A`, no hay razón para condicional)
- [x] `ScanViewModel` — reloj cambiado de `"HH:mm:ss"` (24h) a `"hh:mm a"` (12h con AM/PM)
- [x] `HistoryScreen` — buscador ya tenía `focusedContainerColor`, `unfocusedContainerColor`, `focusedTextColor`, `unfocusedTextColor` correctos desde sesión anterior — sin cambio necesario
- [x] `BUILD SUCCESSFUL` verificado

---

### Iteracion 3 — ScanScreen: reloj real + colores de tabs + limpieza

- [x] `ScanScreen` — eliminado parámetro `scanViewModel: ScanViewModel? = null`; ahora obtiene el ViewModel internamente con `val scanViewModel: ScanViewModel = viewModel()`
- [x] `currentTime` enlazado directamente: `val currentTime by scanViewModel.currentTime.collectAsState()` (sin null-safety, sin fallback hardcodeado)
- [x] Colores de tabs simplificados: `containerColor = if (isSelected) Color(0xFF00B4A6) else Color(0xFF2C2C2C)`, `contentColor = Color.White` (mismo resultado dark/light)
- [x] Verificado: Tab 0 = "Huella Dactilar" → "☝️" + BiometricPrompt; Tab 1 = "Escaneo Facial" → CameraPreview (orden correcto, sin inversión)
- [x] Eliminado import `isSystemInDarkTheme` de ScanScreen (ya no necesario)
- [x] `BUILD SUCCESSFUL` verificado

---

### Iteracion 3 — Cambios adicionales post-CRUD

- [x] `EditEmployeeScreen` migrado a `RetrofitInstance.api.updateDjangoEmployee()` directo (sin ViewModel)
  - Pre-carga datos vía `getDjangoEmployees()` filtrado por `userId` en `LaunchedEffect`
  - Estado `isLoading` muestra `CircularProgressIndicator` mientras se obtiene el empleado
  - Estado `isSaving` desactiva el botón y muestra spinner durante el PUT
  - Navega atrás automáticamente tras éxito; muestra Snackbar de error si falla
  - `BUILD SUCCESSFUL` verificado (solo warning no-bloqueante: `centerAlignedTopAppBarColors` deprecated)

---

### Iteracion 3 — Tema Material 3 correcto: Now in Android pattern (COMPLETADO)

**Objetivo:** Eliminar todos los `if (isDarkTheme)` hardcodeados en pantallas. El tema se encarga automáticamente del cambio de colores entre modo claro y oscuro.

**Cambios en `Theme.kt`:**
- `BioSecureLightColorScheme` ahora tiene tokens completos: `secondary`, `onSecondary`, `secondaryContainer`, `onSecondaryContainer`, `primaryContainer`, `onPrimaryContainer`, `tertiary`, `onTertiary`, `surfaceVariant`, `onSurfaceVariant`, `outline`, `outlineVariant`, `error`, `onError`, `inverseSurface`, `inverseOnSurface`, `scrim`
- `DarkColorScheme` actualizado:
  - `surface = Color(0xFF1A1A1A)` (era `0xFF1E1E1E`) → coincide con lo que los Cards esperaban
  - `surfaceVariant = Color(0xFF2A2A2A)` (era `0xFF2C2C2C`) → TextField containers en oscuro
  - `onSurfaceVariant = Color(0xFFAAAAAA)` (era `0xFFCCCCCC`) → texto secundario correcto
  - Agregados tokens `secondary`, `tertiary`, `error`, `scrim`, etc.

**Mappeo de tokens para BioSecure:**

| Token | Light | Dark | Uso |
|---|---|---|---|
| `surface` | `White` | `Color(0xFF1A1A1A)` | Cards, contenedores |
| `surfaceVariant` | `White` | `Color(0xFF2A2A2A)` | TextField backgrounds |
| `onSurface` | `TextPrimary` | `Color.White` | Texto principal |
| `onSurfaceVariant` | `TextSecondary` | `Color(0xFFAAAAAA)` | Texto secundario/muted |
| `outline` | `Teal` | `Color(0xFF00B4A6)` | Bordes, cursores, acentos |
| `onBackground` | `TextPrimary` | `Color.White` | Texto sobre fondo |

**Regla nueva para todas las pantallas:**
```kotlin
// Cards:
colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)

// TextFields:
OutlinedTextFieldDefaults.colors(
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

// Texto secundario:
color = MaterialTheme.colorScheme.onSurfaceVariant

// Texto principal sobre surface:
color = MaterialTheme.colorScheme.onSurface
```

**Pantallas refactorizadas (eliminado `isSystemInDarkTheme()` / `isDarkTheme`):**
- [x] `LoginScreen` — card, title text, textFields usan tokens; border usa `outline`
- [x] `HistoryScreen` — stat cards, search field, `AttendanceCard` todos con tokens
- [x] `ScanScreen` — tab card y scan circle usan `surface`; tabs botones mantienen `Color(0xFF00B4A6)` / `Color(0xFF2C2C2C)` (diseño intencional, no condicional de tema)
- [x] `DashboardScreen` — todos los Cards, `MetricCard`, `StatItem`, `AuditItem`, `PreventiveCard` sin `isDarkTheme`
- [x] `SettingsScreen` — Cards usan `surface`; helpers `SectionLabel`, `BiometricItem`, `SettingsItem`, `SettingsItemWithValue` eliminaron parámetro `isDarkMode: Boolean` (no se usaba en sus cuerpos); el `isDarkMode: Boolean` del composable principal se conserva solo para el Switch
- [x] `EmployeeList` — Card, AlertDialog usan `surface` / `MaterialTheme.colorScheme.*`
- [x] `RegisterEmployeeScreen` — fieldColors y Cards con tokens; removido `isDarkTheme` e `isSystemInDarkTheme`
- [x] `EditEmployeeScreen` — fieldColors con tokens; removido `isDarkTheme` e `isSystemInDarkTheme`
- [x] `BUILD SUCCESSFUL` verificado (0 errores, solo warnings pre-existentes)

---

### Iteracion 3 — Integración Firebase Auth + Firestore (COMPLETADO)

- [x] Agregar `firebase-bom = "33.1.0"` en `libs.versions.toml` con libraries `firebase-auth`, `firebase-firestore` y plugin `google-services = "4.4.2"`
- [x] Aplicar `google-services` plugin en `build.gradle.kts` raíz y `app/build.gradle.kts`
- [x] Crear `data/repository/AuthRepository.kt` — wrappea `FirebaseAuth` con `suspend fun login/register`, `fun logout/isLoggedIn`
- [x] Crear `data/repository/AttendanceRepository.kt` — guarda asistencias en colección `"attendances"` de Firestore
- [x] Actualizar `BioSecureViewModel`: `loginAsAdmin(email, password)` y `loginAsEmployee(email, password)` usan Firebase Auth; `_authError` y `_authSuccess` StateFlows para manejo de errores y navegación; `registerAttendance()` guarda en Firestore vía `AttendanceRepository`
- [x] Actualizar `LoginScreen`: botones conectados a email/password reales; `LaunchedEffect(authSuccess)` navega al destino correcto; muestra error de autenticación; spinner durante carga; botones deshabilitados si campos vacíos o loading
- [x] Actualizar `BioSecureViewModelFactory`: acepta `AuthRepository` y `AttendanceRepository` como parámetros
- [x] Actualizar `MainActivity`: inicializa `AuthRepository` y `AttendanceRepository`; si `isLoggedIn()` navega directo a Dashboard con rol "admin"
- [x] Actualizar `NavGraph`: acepta `startDestination: String` como parámetro para soportar navegación condicional al inicio
- [x] Validar rol en Firestore tras login: `loginWithRole()` en `AuthRepository` consulta `users/{uid}`, retorna `UserLoginResult(user, role, name)`; si el rol no coincide con el botón pulsado el ViewModel hace `logout()` y emite error de permisos

**Arquitectura transitional:**
- Firebase Auth → controla quién puede acceder (credenciales reales requeridas)
- DummyJSON → sigue proveyendo datos de perfil de empleado (temporal, hasta migrar a Firestore)
- Firestore → almacena registros de asistencia en colección `"attendances"`

---

### Firebase — Estructura de Firestore

**Colección:** `users/{uid}`

| Campo | Tipo | Descripción |
|---|---|---|
| `email` | String | Correo del usuario (coincide con Firebase Auth) |
| `role` | String | `"admin"` o `"employee"` — define el acceso |
| `name` | String | Nombre completo (fuente de verdad para UI) |
| `department` | String | Departamento del empleado (opcional en admins) |
| `cargo` | String | Cargo/título del empleado (opcional en admins) |

```
users/{uid}
  ├── email: string
  ├── role: string         ("admin" | "employee")
  ├── name: string
  ├── department: string
  └── cargo: string
```

**Flujo de validación de rol:**
1. `login()` en ViewModel llama `AuthRepository.loginWithRole()`
2. `loginWithRole()` autentica con Firebase Auth, luego consulta `users/{uid}`
3. Si el documento no tiene campo `role` → hace `signOut()` y retorna error
4. El nombre del campo `name` se guarda en `BioSecureViewModel._currentUserName`
5. `_currentUserName` es la fuente de verdad para mostrar el nombre en ScanScreen y ConfirmationScreen

**Mensajes de error de rol:**
- Sin rol en Firestore: `"Usuario no registrado"`
- Error de credenciales: `"Credenciales inválidas"`

---

**Colección:** `attendances/{id}`

| Campo | Tipo | Descripción |
|---|---|---|
| `uid` | String | Firebase UID del empleado (vincula con `users/`) |
| `userName` | String | Nombre completo del empleado |
| `date` | String | Fecha en formato `dd/MM/yyyy` |
| `checkIn` | String | Hora en formato `HH:mm` |
| `status` | String | `EXITOSO` \| `FALLIDO` \| `PUNTUAL` \| `TARDANZA` \| `INASISTENCIA` |
| `type` | String | `HUELLA` \| `FACIAL` \| `QR` |
| `location` | String | `"Sede Central"` por defecto |

```
attendances/{id}
  ├── uid: string
  ├── userName: string
  ├── checkIn: string
  ├── type: string         (HUELLA/FACIAL/QR)
  ├── location: string
  ├── date: string
  └── status: string       (EXITOSO/FALLIDO)
```

---

### Firebase — Configuración en Firebase Console

1. Ir a [Firebase Console](https://console.firebase.google.com/) y seleccionar el proyecto BioSecure.
2. **Authentication:** Habilitar proveedor `Email/Password`. Crear usuarios de prueba: uno admin y uno empleado.
3. **Firestore Database:** Crear base de datos en modo producción (o modo test para desarrollo). Agregar reglas:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /attendances/{doc} {
         allow read, write: if request.auth != null;
       }
     }
   }
   ```
4. El `google-services.json` ya está en `app/` — no moverlo ni regenerarlo salvo que se cambie de proyecto Firebase.
5. Verificar que el `package_name` en `google-services.json` coincida con `com.biosecure.app`.

---

### Iteracion 4 — Pulido y entrega

- [x] Pantalla Splash (`ui/screens/splash/SplashScreen.kt`):
  - Fondo `#0D3B35`, emoji 🔒 con animación `spring` (scale 0.3 → 1.0, `DampingRatioMediumBouncy`)
  - "BioSecure" en blanco bold 36sp, "Sistema de Asistencia Biométrica" en `#00B4A6` 14sp
  - `delay(2000L)` mínimo; si `isLoggedIn()` consulta `getRoleForCurrentUser()` y navega a Dashboard o EmployeeScan según rol; si no → Login
  - Elimina Splash del back stack con `popUpTo(Screen.Splash.route) { inclusive = true }`
- [x] `AuthRepository.getRoleForCurrentUser()` — consulta `users/{uid}` en Firestore para la sesión activa; retorna `null` si no hay usuario o falla la red
- [x] `NavGraph`: `Screen.Splash` agregado; `startDestination` cambiado a `Screen.Splash.route`; acepta `authRepository: AuthRepository?` y lo pasa a `SplashScreen`
- [x] `MainActivity`: eliminada lógica de `startDestination` y `setRole("admin")` hardcodeado; pasa `authRepository` a `NavGraph`
- [x] `LoginScreen` — botón único "Iniciar Sesión"; llama `viewModel.login()` que consulta Firestore y navega por rol automáticamente
- [x] `AuthRepository.loginAndGetRole()` — wrappea `loginWithRole()` retornando `Pair<FirebaseUser, String>`; lanza excepción en vez de `Result`
- [x] `BioSecureViewModel.login()` — unifica `loginAsAdmin` + `loginAsEmployee`; mapea errores Firebase → `"Credenciales inválidas"`, errores de rol → `"Usuario no registrado"`
- [x] `BioSecureViewModel.registerManualAttendance(user)` — registra asistencia `ScanType.QR` para un usuario dado; guarda en Firestore vía `AttendanceRepository`
- [x] `ScanType.QR` — nuevo valor en el enum `ScanType` para registros por QR y manuales
- [x] `ui/screens/employee/QRScreen.kt` — genera QR de 512×512 con UID de Firebase usando `zxing-core`; muestra en `Image`; botón "Compartir QR" usa `FileProvider` + `Intent.ACTION_SEND`
- [x] `ui/screens/admin/QRScannerScreen.kt` — CameraX back camera + `ImageAnalysis` + `MultiFormatReader` (zxing-core); `AtomicBoolean` para procesar sólo el primer QR; consulta `users/{uid}` y guarda en `attendances/`; navega a Confirmation
- [x] `NavGraph.kt` — `Screen.AdminQRScanner` (`admin/qr-scanner`), `Screen.EmployeeQR` (`employee/qr`); ambas conectadas y pasando `authRepository` donde corresponde
- [x] `DashboardScreen.kt` — tarjetas "Escanear QR" → `AdminQRScanner` y "Registro manual" → `ManualAttendanceDialog` (busca en `viewModel.users`, registra con `registerManualAttendance`)
- [x] `ScanScreen.kt` — botón "Mi QR de Asistencia" visible solo para empleados (`!isAdmin`); navega a `EmployeeQR`
- [x] `AndroidManifest.xml` — `FileProvider` declarado para compartir QR como imagen desde caché
- [x] `res/xml/file_paths.xml` — rutas de caché para `FileProvider`
- [x] `libs.versions.toml` + `build.gradle.kts` — `zxing-core 3.5.2` y `zxing-android-embedded 4.3.0` agregados

### Iteracion 3 — ML Kit detección facial real (COMPLETADO)

- [x] `gradle/libs.versions.toml` — versión renombrada a `mlkit-face = "16.1.7"`; librería renombrada a `mlkit-face-detection` con `version.ref = "mlkit-face"`
- [x] `app/build.gradle.kts` — dependencia actualizada a `libs.mlkit.face.detection`
- [x] `CameraPreview.kt` — reescrito con `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`; `FaceDetectorOptions.PERFORMANCE_MODE_FAST` + `minFaceSize = 0.15f`; callback `onFaceDetected: (Boolean) -> Unit`; función `analyzeImageProxy` anotada con `@OptIn(ExperimentalGetImage::class)` para acceder a `imageProxy.image`; `faceDetector` en `remember` para evitar recreación
- [x] `ScanScreen.kt` — `faceDetected: Boolean` state; reset a `false` al cambiar de tab; borde verde (`0xFF4CAF50`) con rostro / rojo (`error`) sin rostro; texto "Rostro detectado ✅" / "Coloque su rostro frente a la cámara"; botón `enabled = selectedTab != 1 || faceDetected`

**Flujo ML Kit:**
```
CameraX ImageAnalysis → analysisExecutor (background) → ML Kit FaceDetector
    → addOnSuccessListener (main thread) → onFaceDetected(faces.isNotEmpty())
    → ScanScreen faceDetected state → borde + texto + botón reactivos
```

**Nota de threading:** `addOnSuccessListener` sin executor explícito corre en main thread — seguro para actualizar Compose state directamente.

---

### Iteracion 3 — Notificaciones push FCM (COMPLETADO)

- [x] `gradle/libs.versions.toml` — `firebase-messaging = { group = "com.google.firebase", name = "firebase-messaging-ktx" }` agregado (versión gestionada por Firebase BOM `33.1.0`)
- [x] `app/build.gradle.kts` — `implementation(libs.firebase.messaging)` bajo bloque Firebase
- [x] `BioSecureMessagingService.kt` — extiende `FirebaseMessagingService`; canal `"biosecure_attendance"` con `IMPORTANCE_HIGH`; `onMessageReceived()` extrae título/cuerpo de `remoteMessage.notification` con fallback a `remoteMessage.data`; `PendingIntent` hacia `MainActivity` con `FLAG_IMMUTABLE`; `onNewToken()` guarda token en `users/{uid}.fcmToken` si hay sesión activa en `FirebaseAuth`
- [x] `AndroidManifest.xml` — `<uses-permission POST_NOTIFICATIONS/>` agregado; `<service android:name=".BioSecureMessagingService" android:exported="false">` con intent-filter `com.google.firebase.MESSAGING_EVENT`
- [x] `AttendanceRepository.kt` — `import com.google.firebase.Timestamp` agregado; tras `collection.add(data).await()` escribe en `notifications/{id}`: `type`, `userName`, `checkIn`, `Timestamp.now()`, `read: false`; fallo en escritura de notificación no propaga error a la asistencia (try/catch independiente)
- [x] `MainActivity.kt` — solicita `POST_NOTIFICATIONS` en Android 13+ (`Build.VERSION_CODES.TIRAMISU`); `FirebaseMessaging.getInstance().token.addOnSuccessListener` guarda token en `users/{uid}.fcmToken` como fallback al `onNewToken` del servicio

**Flujo FCM:**
```
Empleado marca asistencia → saveAttendance() guarda en attendances/ + notifications/
Token rotado → onNewToken() → users/{uid}.fcmToken actualizado
App abre → MainActivity → FirebaseMessaging.token → users/{uid}.fcmToken actualizado
FCM backend → push llega → onMessageReceived() → canal biosecure_attendance → heads-up notification
```

**Colección `notifications/{id}` — estructura:**
```
type: "attendance"
userName: String        (nombre del empleado)
checkIn: String         (hora HH:mm)
timestamp: Timestamp    (com.google.firebase.Timestamp.now())
read: Boolean           (false al crear)
```

**Nota token:** `onNewToken` solo ejecuta si hay `currentUser` en Firebase Auth. Si el token rota antes del primer login, se sincroniza en el siguiente `onCreate` vía `FirebaseMessaging.getInstance().token`.

---

### Iteracion 4 — Biométrico con UID + Registro de empleado con Firebase Auth + QR

- [x] `data/model/Attendance.kt` — `uid: String = ""` agregado a `Attendance`; nueva data class `ConfirmationData(userName, checkIn, date, scanType, location)`
- [x] `data/repository/AttendanceRepository.kt` — `saveAttendance()` incluye campo `uid` en Firestore; nuevo `getAttendancesByUser(uid: String)` con `whereEqualTo("uid", uid)`
- [x] `BioSecureViewModel.kt` — `_lastConfirmation: MutableStateFlow<ConfirmationData?>` + `setConfirmationData()`; `registerAttendance()` toma `authRepository.currentUser?.uid` y popula `_lastConfirmation`; `registerManualAttendance()` también popula `_lastConfirmation`; `logout()` limpia `_lastConfirmation`
- [x] `ui/screens/confirmation/ConfirmationScreen.kt` — reescrita: animación `Animatable(0f→1f)` spring con `Modifier.scale()` en icono Check; lee datos reales de `viewModel.lastConfirmation` (`ConfirmationData`); muestra fila de fecha si no está vacía; mapea `"QR"` → `"Código QR"`, `"HUELLA"` → `"Huella Dactilar"`, `"FACIAL"` → `"Escaneo Facial"`
- [x] `ui/screens/admin/RegisterEmployeeScreen.kt` — reescrita: campo `password` (mín. 6 chars) agregado; usa `FirebaseApp.getInstance("secondary")` para crear cuenta Firebase Auth del empleado sin cerrar sesión del admin; guarda en Firestore `users/{uid}` con `email, role: "employee", name, department, cargo`; al éxito muestra QR de 512×512 del UID (ZXing `QRCodeWriter`); botón "Compartir QR" usa `FileProvider + Intent.ACTION_SEND`; se elimina dependencia de `UserRepository` / `EmployeeRequest` / DummyJSON
- [x] `ui/screens/admin/QRScannerScreen.kt` — acepta `viewModel: BioSecureViewModel? = null`; después de guardar en Firestore llama `viewModel?.setConfirmationData(ConfirmationData(...))` antes de navegar a Confirmation; campo `uid` también guardado en el documento de asistencia
- [x] `ui/navigation/NavGraph.kt` — `AdminQRScanner` composable ahora pasa `viewModel = viewModel` a `QRScannerScreen`

**Firestore — estructura actualizada:**

`users/{uid}`:
```
email: String
role: "admin" | "employee"
name: String
department: String     ← nuevo (empleados creados desde app)
cargo: String          ← nuevo (empleados creados desde app)
```

`attendances/{docId}`:
```
uid: String            ← nuevo: Firebase UID del empleado (vincula con users/)
userId: String | Int
userName: String
date: String           (dd/MM/yyyy)
checkIn: String        (HH:mm)
status: String         ("EXITOSO", etc.)
type: String           ("HUELLA", "FACIAL", "QR")
location: String       ("Sede Central")
```

**Patrón secondary FirebaseApp (preserva sesión admin):**
```kotlin
val mainApp = FirebaseApp.getInstance()
val secondaryApp = try {
    FirebaseApp.getInstance("secondary")
} catch (e: IllegalStateException) {
    FirebaseApp.initializeApp(context.applicationContext, mainApp.options, "secondary")!!
}
val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
secondaryAuth.createUserWithEmailAndPassword(email, password).await()
val uid = secondaryAuth.currentUser?.uid ?: throw Exception("No UID")
secondaryAuth.signOut()  // admin sigue logueado en FirebaseAuth.getInstance()
```

### Iteracion 4 — Fixes: nombre Firestore, flujo facial, QR de empleado

- [x] `BioSecureViewModel` — `_currentUserName: MutableStateFlow<String>` y `currentUserName: StateFlow<String>` agregados; `login()` ahora llama `loginWithRole()` directamente (en vez de `loginAndGetRole()`) y guarda `result.name` en `_currentUserName`; `registerAttendance()` usa `_currentUserName.value` como `userName` (fuente de verdad = Firestore, no `displayName` de Firebase); `logout()` limpia `_currentUserName`
- [x] `ScanScreen` — botón "REGISTRAR ENTRADA" ahora bifurca: tab 0 (Huella) → `biometricPrompt.authenticate()`, tab 1 (Facial) → `registerAttendance(FACIAL)` + navega directo sin BiometricPrompt; callback `onAuthenticationSucceeded` usa `ScanType.HUELLA` directamente; `promptInfo` subtitle fijo a "Use su huella dactilar"; muestra "Bienvenido, [nombre]" cuando empleado está logueado
- [x] `RegisterEmployeeScreen` — simplificado: al éxito navega a `Screen.AdminEmployeeQR.route(uid)` con `popUpTo(AdminRegisterEmployee) { inclusive = true }` en lugar de mostrar QR inline; eliminado todo el estado de éxito, `qrBitmap`, `SummaryRow`, y dependencias ZXing/FileProvider
- [x] `AdminEmployeeQRScreen.kt` (nuevo) — recibe `uid: String` como argumento de ruta; genera QR 512×512 con ZXing; botón "Compartir QR" usa `FileProvider + Intent.ACTION_SEND`; botón "Volver al Dashboard" navega a `Screen.Dashboard`
- [x] `NavGraph` — `Screen.AdminEmployeeQR("admin/employee-qr/{uid}")` agregado; composable con `NavType.StringType`; import `AdminEmployeeQRScreen` agregado

**Flujo de nombre correcto:**
1. Login → `loginWithRole()` consulta Firestore `users/{uid}.name`
2. Nombre guardado en `_currentUserName` en el ViewModel
3. `registerAttendance()` usa `_currentUserName.value` para `ConfirmationData.userName`
4. `ConfirmationScreen` muestra el nombre real de Firestore

**Flujo registro de empleado:**
1. Admin llena formulario (nombre, apellido, email, contraseña, cargo, departamento)
2. App crea cuenta Firebase Auth via secondary app → obtiene `uid`
3. Guarda `users/{uid}` en Firestore con `role: "employee"`, `name`, `department`, `cargo`
4. Navega a `AdminEmployeeQRScreen` con el UID
5. Admin comparte QR con el empleado
6. Empleado presenta QR → admin escanea → asistencia registrada

### Iteracion 4 — EmployeeList desde Firestore + QR personalizado

- [x] `AttendanceRepository.kt` — `getEmployees()`: consulta `users/` donde `role == "employee"`, añade `uid` al mapa de cada documento; `deleteEmployee(uid)`: elimina documento `users/{uid}` de Firestore
- [x] `BioSecureViewModel.kt` — `_firestoreEmployees: MutableStateFlow<List<Map<String, Any>>>` + `firestoreEmployees` StateFlow; `loadFirestoreEmployees()` llama `attendanceRepository.getEmployees()`; `deleteFirestoreEmployee(uid)` llama `attendanceRepository.deleteEmployee(uid)` y filtra el StateFlow local
- [x] `EmployeeList.kt` — reescrita: carga desde `viewModel.firestoreEmployees` (Firestore) en lugar de `viewModel.users` (DummyJSON); muestra `name`, `cargo`, `department`, `email` por empleado; botón eliminar llama `deleteFirestoreEmployee(uid)`; se elimina botón editar (EditEmployeeScreen aún usa DummyJSON)
- [x] `ConfirmationScreen.kt` — `EmployeeQRWaitingContent`: agrega `Text("QR de $currentUserName")` encima del Card con el QR; `contentDescription` del QR actualizado a `"QR de $currentUserName"`
- [x] `ScanScreen.kt` — eliminado bloque `if (!isAdmin)` con `OutlinedButton "📲 Mi QR de Asistencia"`; el QR solo aparece en `ConfirmationScreen` tras scan exitoso

**Estructura Firestore `users/{uid}` campos usados en EmployeeListScreen:**
```
name:       String   — nombre completo del empleado
email:      String   — correo electrónico
cargo:      String   — título/cargo
department: String   — departamento
role:       "employee"
uid:        String   — añadido al mapa desde doc.id (no almacenado en el documento)
```

---

### Iteracion 4 — Flujo revisado: QR como confirmación obligatoria

**Flujo definitivo empleado → admin:**
```
Empleado hace scan (huella o facial)
    ↓  NO registra en Firestore todavía
ConfirmationScreen (modo empleado)
    - Nombre: "Hola, [nombre]"
    - Texto: "Escanea con el Admin para confirmar tu entrada"
    - QR 260dp con UID del empleado
    - Botón "Cancelar" → vuelve a ScanScreen sin registrar

Admin abre QRScannerScreen → apunta al QR del empleado
    ↓  Lee UID → consulta users/{uid}.name → guarda en attendances/
ConfirmationScreen (modo admin)
    - ✅ Check animado
    - "Asistencia registrada exitosamente"
    - Card: Empleado, Hora de ingreso, Fecha
    - Botón "Cerrar" → vuelve a AdminScan
```

- [x] `ScanScreen` — eliminadas las llamadas `viewModel?.registerAttendance()` de: biometric callback `onAuthenticationSucceeded` y facial button onClick; ambos flujos simplemente navegan a `Screen.Confirmation.route` sin registrar
- [x] `ConfirmationScreen` — reescrita con dos modos diferenciados por `isAdmin` (derivado de `viewModel.currentRole`):
  - **`EmployeeQRWaitingContent`** (employee): nombre, texto "Escanea con el Admin...", QR 260dp en Card, `OutlinedButton "Cancelar"` → `Screen.scan(false)` con `popUpTo(Confirmation) { inclusive = true }`
  - **`AdminConfirmedContent`** (admin): check animado spring 0→1, "Asistencia registrada exitosamente", Card con Empleado + Hora + Fecha desde `lastConfirmation`, `Button "Cerrar"` → `Screen.scan(true)`
  - `LaunchedEffect(isAdmin)` — solo anima el check si `isAdmin = true`; QR se genera con `remember(currentUserUid)` independientemente del modo
- [x] `QRScannerScreen` — sin cambios; ya registra correctamente: lee UID, consulta `users/{uid}.name`, guarda en `attendances/` con todos los campos, llama `setConfirmationData()`, navega a Confirmation

### Iteracion 4 — ConfirmationScreen con QR del empleado

- [x] `BioSecureViewModel` — `_currentUserUid: MutableStateFlow<String?>` + `currentUserUid: StateFlow<String?>` agregados; se popula con `result.user.uid` en `login()` junto a `_currentUserName`; se limpia en `logout()`
- [x] `ConfirmationScreen` — reescrita con scroll (`verticalScroll + rememberScrollState`) para soportar contenido adicional; lee `currentUserUid` del ViewModel; genera QR 512×512 con ZXing `QRCodeWriter` via `remember(currentUserUid)` (solo cuando `!isAdmin && uid != null`); muestra QR en Card 200dp centrado; texto "Muestra este QR al administrador" encima del QR; botones reemplazados por un solo botón "Cerrar" → `Screen.scan(isAdmin)` con `popUpTo(Confirmation) { inclusive = true }`
- [x] `QRScannerScreen` — sin cambios; ya lee `users/{uid}.name` correctamente, guarda `uid` en `attendances/`, llama `viewModel?.setConfirmationData()` antes de navegar

**Flujo completo empleado → admin:**
1. Empleado abre ScanScreen → marca huella o facial
2. `registerAttendance(HUELLA|FACIAL)` guarda en Firestore con `uid`
3. Navega a ConfirmationScreen → muestra datos + QR (200dp) generado con UID
4. Empleado muestra QR al admin
5. Admin abre QRScannerScreen → apunta cámara al QR
6. App lee UID del QR, busca `users/{uid}.name`, guarda en `attendances/`
7. Navega a ConfirmationScreen (rol admin) → muestra nombre real del empleado, sin QR

- [ ] Migrar `LocalLifecycleOwner` a `lifecycle-runtime-compose`
- [ ] Reemplazar `centerAlignedTopAppBarColors` deprecated (usar `topAppBarColors`)
- [ ] Reemplazar `Icons.Filled.KeyboardArrowRight` deprecated (usar `AutoMirrored`)
- [ ] Reemplazar `LocalClipboardManager` deprecated (usar `LocalClipboard`)
- [ ] Manejo de permisos de cámara con diálogo explicativo
- [ ] Tests unitarios para `BioSecureViewModel`
- [ ] Generar APK release firmado

---

## Prompts utiles para Claude Code

Los siguientes prompts funcionaron bien en este proyecto. Adaptarlos según la tarea.

**Crear un ViewModel desde cero:**
```
Crea ui/viewmodel/NombreViewModel.kt con:
- StateFlow para [listar estados]
- Métodos [listar métodos]
Sin borrar archivos existentes. Ejecuta .\gradlew.bat build al final.
```

**Agregar un parámetro a una pantalla sin romper nada:**
```
Agrega el parámetro `viewModel: BioSecureViewModel? = null` a [Pantalla]Screen.
Solo agregar la firma, no cambiar la lógica interna. Verificar que NavGraph lo pase.
```

**Depurar un error de compilación:**
```
El build falla con este error: [pegar error completo].
Lee los archivos afectados y explica la causa raíz antes de modificar nada.
```

**Revisar compatibilidad de dependencias:**
```
Quiero actualizar [librería] de [versión actual] a [versión nueva].
Revisa libs.versions.toml y app/build.gradle.kts. Lista posibles breaking changes
antes de hacer cualquier cambio.
```

**Conectar ViewModel a una pantalla existente:**
```
Conecta BioSecureViewModel a [Pantalla]Screen.
Reemplaza los datos mock con los StateFlows correspondientes.
No modificar el diseño visual. Ejecuta el build al terminar.
```

**Arreglar campos de texto invisibles en modo oscuro:**
```
Lee Theme.kt y la pantalla afectada. Agrega isSystemInDarkTheme()
y OutlinedTextFieldDefaults.colors() con colores explícitos en cada
OutlinedTextField. Usa focusedTextColor/unfocusedTextColor = White
en modo oscuro y borderColor = #00B4A6 siempre.
```

**Arreglar colores de tabs o botones que usan tokens del tema:**
```
Lee la pantalla afectada. Reemplaza ButtonDefaults.buttonColors() que
use MaterialTheme.colorScheme.* por literales explícitos:
  containerColor = if (isSelected) Color(0xFF0D3B35) else Color(0xFF1E1E1E)
  contentColor   = if (isSelected) Color.White       else Color(0xFF00B4A6)
Agrega import androidx.compose.ui.graphics.Color si no está presente.
```

**Arreglar OutlinedButton con borde/texto dependiente del tema:**
```
Lee la pantalla afectada. Reemplaza el border y contentColor del
OutlinedButton por literales explícitos:
  border = BorderStroke(1.dp, Color(0xFF00B4A6))
  contentColor = if (isDarkTheme) Color.White else Color(0xFF0D3B35)
Asegurarse de que isDarkTheme = isSystemInDarkTheme() esté definido.
```

**Arreglar fondo de OutlinedTextField invisible en modo oscuro:**
```
Agrega focusedContainerColor y unfocusedContainerColor al bloque de
OutlinedTextFieldDefaults.colors():
  focusedContainerColor = if (isDarkTheme) Color(0xFF2C2C2C) else Color.White,
  unfocusedContainerColor = if (isDarkTheme) Color(0xFF2C2C2C) else Color.White
```

**Migrar una pantalla de edición para usar RetrofitInstance directamente (sin ViewModel):**
```
Reescribe [Pantalla]Screen para que:
1. Acepte un id: Int como parámetro de ruta.
2. En LaunchedEffect(id) llame RetrofitInstance.api.getAll(), filtre por id y
   pre-rellene los campos del formulario. Muestra CircularProgressIndicator mientras carga.
3. El botón "Guardar" use scope.launch { RetrofitInstance.api.update(id, datos) }
   con try/catch mostrando Snackbar de éxito o error.
4. Navegue atrás con navController.navigateUp() tras éxito.
Manejar isLoading e isSaving como estados separados.
```

**Conectar hora real de ScanViewModel a ScanScreen:**
```
Agrega scanViewModel: ScanViewModel? = null como parámetro a ScanScreen.
Reemplaza el Text hardcodeado con:
  val currentTime by (scanViewModel?.currentTime
      ?: kotlinx.coroutines.flow.MutableStateFlow("")).collectAsState()
  Text(text = currentTime.ifEmpty { "08:45 AM" }, ...)
Agregar import com.biosecure.app.ui.viewmodel.ScanViewModel.
```

---

## Decisiones de arquitectura

### MVVM con StateFlow en lugar de LiveData

**Decision:** Usar `StateFlow` + `viewModelScope` para todo el estado reactivo.

**Por que:** `StateFlow` es idiomatic en proyectos Compose + Kotlin Coroutines. `LiveData` requiere observar desde el ciclo de vida de la Activity/Fragment, lo que agrega complejidad innecesaria en Compose donde se usa `collectAsState()`. `StateFlow` es type-safe y facilita testing sin dependencias de Android.

---

### ViewModelFactory manual en lugar de Hilt/Koin

**Decision:** Inyección manual con `BioSecureViewModelFactory`.

**Por que:** El proyecto es académico y agregar Hilt suma ~200 líneas de boilerplate de setup (módulos, componentes, anotaciones). Con una sola dependencia real (`UserRepository`), el factory manual es suficiente y más legible para el equipo. Si el proyecto escala, migrar a Hilt es directo.

---

### FragmentActivity en lugar de ComponentActivity

**Decision:** `MainActivity` extiende `FragmentActivity`.

**Por que:** `BiometricPrompt` de AndroidX requiere un `FragmentManager`, disponible solo en `FragmentActivity`. `ComponentActivity` no lo provee. Cambiar esto rompería la biometría.

---

### Navegacion con paso de isAdmin por ruta en lugar de shared state

**Decision:** `isAdmin: Boolean` viaja como argumento de ruta (`scan/true`, `history/false`).

**Por que:** En el momento de implementar NavGraph (Iteracion 1) no existía un ViewModel global. Para Iteracion 3 el `currentRole` en `BioSecureViewModel` puede reemplazar este mecanismo, pero se mantiene por retrocompatibilidad mientras las pantallas no consumen el ViewModel todavia.

---

### Fuente de datos DummyJSON para prototipo

**Decision:** API base `https://dummyjson.com/users` como backend temporal.

**Por que:** Permite desarrollar y mostrar el flujo completo (red → repository → ViewModel → UI) sin necesitar un backend propio. Los modelos `User` ya mapean los campos reales que un backend de producción tendría. Cambiar a un endpoint real solo requiere modificar la `BASE_URL` en `RetrofitInstance`.

---

## Convenciones de commits

Formato: `tipo(scope): descripción en imperativo, minúsculas, sin punto final`

| Tipo | Cuando usarlo |
|---|---|
| `feat` | Nueva funcionalidad visible para el usuario |
| `fix` | Corrección de bug |
| `refactor` | Cambio de código sin cambio de comportamiento |
| `style` | Cambios de formato, espaciado, nombres (sin lógica) |
| `chore` | Dependencias, configuración de build, Gradle |
| `docs` | README, TEAM, comentarios |
| `test` | Agregar o corregir tests |

**Ejemplos:**
```
feat(scan): integrar BiometricPrompt con ScanViewModel
fix(navgraph): corregir paso de isAdmin en ruta settings
chore(deps): actualizar compose-bom a 2026.02.01
refactor(viewmodel): extraer lógica de stats a función separada
docs(team): agregar error conocido de AGP 9.2.1
```

**Reglas:**
- Un commit = un cambio cohesivo. No mezclar feat + refactor.
- El scope es el módulo o pantalla afectada: `scan`, `login`, `viewmodel`, `navgraph`, `deps`, `build`.
- Si el commit rompe algo existente (breaking change), agregar `!` después del scope: `feat(navgraph)!: cambiar firma de NavGraph`.
- Los commits de trabajo en progreso van en ramas feature, no en main.

---

## Repositorios de referencia

### 1. Firebase Auth + Firestore
- URL: https://github.com/alexmamo/FirestoreCleanArchitectureApp
- Uso: Reemplazar DummyJSON por Firebase, manejo de roles Admin/Empleado
- Archivos clave: data/repository/, domain/usecase/
- Impacto tokens: Bajo

### 2. BiometricPrompt + Compose
- URL: https://github.com/dhruvsh-dev/FingerPrint-App-Jetpack-Compose
- Uso: Conectar huella dactilar con ViewModel para registrar asistencia
- Impacto tokens: Muy bajo

### 3. ML Kit + CameraX
- URL: https://github.com/ahmedelshaikh20/camerax_with_compose
- Uso: Detección facial real antes de permitir marcación
- Impacto tokens: Medio-Alto

### 4. IA para consultas RRHH
- URL: https://github.com/skydoves/gemini-android
- Uso: Arquitectura de chat con IA, adaptable a Claude API via Retrofit
- Impacto tokens: Bajo

### 5. Geofencing
- URL: https://github.com/kibotu/geofencer
- Uso: Bloquear marcación si empleado está fuera del perímetro
- Impacto tokens: Medio

## Orden de implementación recomendado
1. Firebase Auth + Firestore (repositorio 1)
2. BiometricPrompt mejorado (repositorio 2)
3. IA con Claude API (repositorio 4)
4. ML Kit facial (repositorio 3)
5. Geofencing (repositorio 5)
