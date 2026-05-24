# TEAM — BioSecure

Referencia interna del equipo: errores conocidos, dependencias exactas, estado del proyecto, prompts, decisiones de arquitectura y convenciones.

---

## Errores conocidos y soluciones

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

### `LocalLifecycleOwner` deprecated en CameraPreview

**Síntoma:** Warning `'val LocalLifecycleOwner: ProvidableCompositionLocal<LifecycleOwner>' is deprecated`.

**Causa:** En Compose reciente se movió a `androidx.lifecycle.compose`.

**Estado:** Warning no-bloqueante. La solución es agregar `androidx.lifecycle:lifecycle-runtime-compose` y cambiar el import. Pendiente para siguiente iteración.

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
- [ ] Conectar `LoginScreen` con `loginAsAdmin()` / `loginAsEmployee()`
- [ ] Reemplazar listas mock en `HistoryScreen` con `viewModel.attendances`
- [ ] Llamar `loadUsers()` al iniciar y mostrar en Dashboard
- [ ] Conectar `registerAttendance()` al flujo de escaneo exitoso
- [ ] Mostrar `getStats()` en `DashboardScreen`
- [ ] Manejar estados `isLoading` y `error` en la UI
- [x] CRUD completo de empleados: `createEmployee`, `updateEmployee`, `deleteEmployee`
- [x] `RegisterEmployeeScreen` conectada a `BioSecureViewModel` con Snackbar de confirmación
- [x] `EmployeeListScreen` con edición (lápiz) y eliminación (basura + AlertDialog)
- [x] `EditEmployeeScreen` con formulario prellenado y Snackbar al guardar
- [x] Agregar entrada en `DashboardScreen` para navegar a `EmployeeListScreen`

### Iteracion 3 — Cambios adicionales post-CRUD

- [x] `EditEmployeeScreen` migrado a `RetrofitInstance.api.updateDjangoEmployee()` directo (sin ViewModel)
  - Pre-carga datos vía `getDjangoEmployees()` filtrado por `userId` en `LaunchedEffect`
  - Estado `isLoading` muestra `CircularProgressIndicator` mientras se obtiene el empleado
  - Estado `isSaving` desactiva el botón y muestra spinner durante el PUT
  - Navega atrás automáticamente tras éxito; muestra Snackbar de error si falla
  - `BUILD SUCCESSFUL` verificado (solo warning no-bloqueante: `centerAlignedTopAppBarColors` deprecated)

---

### Iteracion 4 — Pulido y entrega (pendiente)
- [ ] Migrar `LocalLifecycleOwner` a `lifecycle-runtime-compose`
- [ ] Reemplazar `centerAlignedTopAppBarColors` deprecated
- [ ] Agregar pantalla de splash / onboarding
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
