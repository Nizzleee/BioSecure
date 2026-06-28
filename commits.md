# Commits — BioSecure

Prefijo base omitido: `app/src/main/java/com/biosecure/app/` → se escribe como `src/`

---

## Nicolas Carrillo Chambi

1. `chore(project): inicializar proyecto android con kotlin y jetpack compose`
   📁 `build.gradle.kts` · `settings.gradle.kts` · `gradle.properties`

2. `chore(build): configurar agp 9.1.1 con kotlin 2.2.10 y compose bom`
   📁 `gradle/libs.versions.toml` · `app/build.gradle.kts`

3. `chore(manifest): declarar permisos internet, use_biometric, camera y post_notifications`
   📁 `app/src/main/AndroidManifest.xml`

4. `feat(viewmodel): crear biosecureviewmodel con stateflows base para ui reactiva`
   📁 `src/ui/viewmodel/BioSecureViewModel.kt` · `src/MainActivity.kt`

5. `feat(viewmodel): crear scanviewmodel con timer en tiempo real formato hh:mm a`
   📁 `src/ui/viewmodel/ScanViewModel.kt`

6. `feat(viewmodel): configurar biosecureviewmodelfactory con inyección manual de repositorios`
   📁 `src/ui/viewmodel/BioSecureViewModel.kt` · `src/MainActivity.kt`

7. `feat(viewmodel): implementar login() con detección automática de rol desde firestore`
   📁 `src/ui/viewmodel/BioSecureViewModel.kt`

8. `feat(viewmodel): agregar _currentusername y _currentuseruid stateflows`
   📁 `src/ui/viewmodel/BioSecureViewModel.kt`

9. `feat(viewmodel): implementar setconfirmationdata() y model confirmationdata`
   📁 `src/ui/viewmodel/BioSecureViewModel.kt` · `src/data/model/Attendance.kt`

10. `feat(register): reescribir registeremployeescreen con firebase secondary app pattern`
    📁 `src/ui/screens/admin/RegisterEmployeeScreen.kt`

11. `feat(register): crear cuenta firebase auth del empleado sin cerrar sesión del admin`
    📁 `src/ui/screens/admin/RegisterEmployeeScreen.kt`

12. `feat(viewmodel): agregar _firestoreemployees stateflow y loadfirestoreemployees()`
    📁 `src/ui/viewmodel/BioSecureViewModel.kt`

13. `chore(deps): agregar lifecycle-runtime-compose para migrar locallifecycleowner`
    📁 `gradle/libs.versions.toml` · `app/build.gradle.kts`

14. `feat(viewmodel): agregar _currentcompanyid stateflow y computependingstatus()`
    📁 `src/ui/viewmodel/BioSecureViewModel.kt`

15. `feat(viewmodel): implementar initializesessionifloggedin() para recuperar sesión activa`
    📁 `src/ui/viewmodel/BioSecureViewModel.kt` · `src/MainActivity.kt`

16. `refactor(viewmodel): extraer checkattendancestatus() como suspend fun reutilizable`
    📁 `src/ui/viewmodel/BioSecureViewModel.kt`

17. `feat(viewmodel): agregar _shifts stateflow y saveshifts() para guardar turnos por empresa`
    📁 `src/ui/viewmodel/BioSecureViewModel.kt`

18. `feat(viewmodel): reemplazar checkinhour único por lista de turnos con fallback legado`
    📁 `src/ui/viewmodel/BioSecureViewModel.kt`

19. `feat(viewmodel): agregar stateflows todayattendance y todayattendanceid para empleado`
    📁 `src/ui/viewmodel/BioSecureViewModel.kt`

20. `feat(viewmodel): agregar updateusername() que actualiza firestore y stateflow local`
    📁 `src/ui/viewmodel/BioSecureViewModel.kt`

21. `feat(viewmodel): agregar exportattendancestocsv() con intent.action_send para compartir`
    📁 `src/ui/viewmodel/BioSecureViewModel.kt`

22. `feat(viewmodel): agregar _sedes stateflow, savesede() y loadcompanyconfig()`
    📁 `src/ui/viewmodel/BioSecureViewModel.kt` · `src/data/model/Sede.kt` · `src/data/model/Company.kt`

23. `feat(viewmodel): agregar saveshifts() para guardar batch de turnos en firestore`
    📁 `src/ui/viewmodel/BioSecureViewModel.kt`

24. `feat(viewmodel): refactorizar factory a 4 parámetros con firebasefunctionsrepository`
    📁 `src/ui/viewmodel/BioSecureViewModel.kt` · `src/MainActivity.kt`

25. `feat(viewmodel): implementar observeattendancehistory() con combine de tres stateflows`
    📁 `src/ui/viewmodel/BioSecureViewModel.kt`

26. `feat(viewmodel): agregar registerattendance() con token qr, coordenadas gps y fallback directo`
    📁 `src/ui/viewmodel/BioSecureViewModel.kt`

27. `feat(viewmodel): implementar refreshsession() y setselectedsede() para flujo sede-first`
    📁 `src/ui/viewmodel/BioSecureViewModel.kt`

28. `fix(sedes): eliminar parámetro viewmodel inexistente en llamada a biosecurebottombar`
    📁 `src/ui/screens/admin/SedesScreen.kt`

29. `fix(compile): eliminar employeedashboard obsoleto con referencias a métodos removidos`
    📁 `src/ui/screens/employee/` *(archivo eliminado)*

30. `docs(readme): actualizar sección equipo y descripción de arquitectura multi-sede`
    📁 `README.md`

---

## Sergio Ponce Moron

1. `feat(theme): implementar biosecuretheme con paleta teal y tokens material3 completos`
   📁 `src/ui/theme/Theme.kt` · `src/ui/theme/Color.kt` · `src/ui/theme/Type.kt`

2. `feat(theme): definir darkcolorscheme con surface #1A2420 y primary #1ED9C5`
   📁 `src/ui/theme/Theme.kt` · `src/ui/theme/Color.kt`

3. `feat(login): implementar loginscreen con campos email, contraseña y botón de acceso`
   📁 `src/ui/screens/login/LoginScreen.kt`

4. `feat(scan): crear scanscreen con tabs huella dactilar y escaneo facial`
   📁 `src/ui/screens/scan/ScanScreen.kt`

5. `feat(history): implementar historyscreen con lista de asistencias y filtro de búsqueda`
   📁 `src/ui/screens/history/HistoryScreen.kt`

6. `feat(dashboard): crear dashboardscreen con metriccards y quickactioncards`
   📁 `src/ui/screens/dashboard/DashboardScreen.kt`

7. `feat(settings): implementar settingsscreen con toggle dark mode y perfil de usuario`
   📁 `src/ui/screens/settings/SettingsScreen.kt`

8. `feat(confirmation): rediseñar confirmationscreen con modo admin y modo empleado`
   📁 `src/ui/screens/confirmation/ConfirmationScreen.kt`

9. `feat(confirmation): animar icono check con animatable spring de 0 a 1 en modo admin`
   📁 `src/ui/screens/confirmation/ConfirmationScreen.kt`

10. `feat(employeelist): migrar employeelistscreen a datos reales de firestore`
    📁 `src/ui/screens/admin/EmployeeList.kt`

11. `feat(splash): crear splashscreen con animación spring y navegación condicional por rol`
    📁 `src/ui/screens/splash/SplashScreen.kt`

12. `feat(theme): mejorar darkcolorscheme con paleta más vibrante y mayor contraste`
    📁 `src/ui/theme/Theme.kt` · `src/ui/theme/Color.kt`

13. `feat(dashboard): envolver metriccards en animatedvisibility con fadein y slideinvertically`
    📁 `src/ui/screens/dashboard/DashboardScreen.kt`

14. `feat(shifts): crear shiftsettingsscreen con lista de turnos, timepicker y edición inline`
    📁 `src/ui/screens/admin/ShiftSettingsScreen.kt`

15. `feat(theme): agregar localapplanguage compositionlocal para soporte en/es`
    📁 `src/ui/theme/LocalAppLanguage.kt`

16. `feat(scan): traducir todos los textos a inglés y español con localapplanguage.current`
    📁 `src/ui/screens/scan/ScanScreen.kt`

17. `feat(bottombar): implementar biosecurebottombar con etiquetas bilingüe según rol`
    📁 `src/ui/screens/scan/ScanScreen.kt`

18. `feat(confirmation): añadir soporte completo en/es en admincontent y employeecontent`
    📁 `src/ui/screens/confirmation/ConfirmationScreen.kt`

19. `feat(settings): añadir icono lápiz para editar nombre de empleado con alertdialog`
    📁 `src/ui/screens/settings/SettingsScreen.kt`

20. `feat(history): implementar export csv funcional que respeta búsqueda y filtros activos`
    📁 `src/ui/screens/history/HistoryScreen.kt`

21. `fix(login): corregir fondo de campos usando onbackground.copy(alpha 0.06f)`
    📁 `src/ui/screens/login/LoginScreen.kt`

22. `feat(history): añadir traducciones en/es en attendancecard y badges de estado`
    📁 `src/ui/screens/history/HistoryScreen.kt`

23. `chore(deps): integrar lottie-compose 6.4.0 para animaciones json desde assets`
    📁 `gradle/libs.versions.toml` · `app/build.gradle.kts`

24. `feat(components): crear lottieicon composable genérico con parámetro iterations`
    📁 `src/ui/components/LottieIcon.kt`

25. `feat(assets): agregar archivos json lottie para lock, fingerprint, face, success, empty, alert, qr y team`
    📁 `app/src/main/assets/`

26. `feat(login): reemplazar emoji lock estático por lottieicon animado de 80dp`
    📁 `src/ui/screens/login/LoginScreen.kt`

27. `feat(scan): reemplazar emojis de huella y rostro por animaciones lottie`
    📁 `src/ui/screens/scan/ScanScreen.kt`

28. `feat(confirmation): reemplazar icono checkcircle por lottie success con iterations 1`
    📁 `src/ui/screens/confirmation/ConfirmationScreen.kt`

29. `feat(history): añadir lottie empty state cuando historial no tiene registros`
    📁 `src/ui/screens/history/HistoryScreen.kt`

30. `feat(shiftmanager): crear shiftmanagerscreen con addshiftbottomsheet y edición inline`
    📁 `src/ui/screens/admin/ShiftManagerScreen.kt`

31. `feat(shiftmanager): implementar assignshiftbottomsheet con checkbox por empleado`
    📁 `src/ui/screens/admin/ShiftManagerScreen.kt`

32. `feat(theme): agregar shimmereffect modifier extension para skeleton loading`
    📁 `src/ui/theme/Shimmer.kt`

---

## Marco Figueroa Cordova

1. `feat(biometric): integrar biometricprompt en scanscreen tab de huella dactilar`
   📁 `src/ui/screens/scan/ScanScreen.kt`

2. `fix(biometric): requerir fragmentactivity para biometricprompt en mainactivity`
   📁 `src/MainActivity.kt`

3. `feat(qrscanner): implementar qrscannerscreen con camerax e imageanalysis multiformatreader`
   📁 `src/ui/screens/admin/QRScannerScreen.kt`

4. `feat(qrscanner): añadir atomicboolean para procesar solo el primer qr escaneado`
   📁 `src/ui/screens/admin/QRScannerScreen.kt`

5. `feat(qr): crear adminemployeeqrscreen con qr 512x512 del uid y botón compartir`
   📁 `src/ui/screens/admin/AdminEmployeeQRScreen.kt`

6. `feat(camera): integrar ml kit facedetector en camerapreview con strategy_keep_only_latest`
   📁 `src/ui/components/CameraPreview.kt` · `gradle/libs.versions.toml` · `app/build.gradle.kts`

7. `feat(scan): mostrar borde verde y habilitar botón solo cuando ml kit detecta rostro`
   📁 `src/ui/screens/scan/ScanScreen.kt` · `src/ui/components/CameraPreview.kt`

8. `feat(qrscanner): buscar registro pendiente al escanear qr y confirmar asistencia como exitoso`
   📁 `src/ui/screens/admin/QRScannerScreen.kt`

9. `feat(scan): integrar workers para notificaciones de turno con shiftnotificationscheduler`
   📁 `src/workers/ShiftNotificationScheduler.kt` · `src/workers/ShiftReminderWorker.kt`

10. `fix(camera): migrar import locallifecycleowner a androidx.lifecycle.compose`
    📁 `src/ui/components/CameraPreview.kt` · `src/ui/screens/admin/QRScannerScreen.kt`

11. `fix(topappbar): reemplazar centeredalignedtopappbarcolors deprecated por topappbarcolors`
    📁 `src/ui/screens/admin/QRScannerScreen.kt` · `src/ui/screens/admin/EditEmployeeScreen.kt` · `src/ui/screens/admin/RegisterEmployeeScreen.kt` · `src/ui/screens/admin/EmployeeList.kt` · `src/ui/screens/admin/AdminEmployeeQRScreen.kt` · `src/ui/screens/admin/ShiftSettingsScreen.kt` · `src/ui/screens/employee/QRScreen.kt`

12. `feat(sedes): implementar sedesscreen con googlemap interactivo y búsqueda por geocoder`
    📁 `src/ui/screens/admin/SedesScreen.kt`

13. `feat(sedes): añadir circle de geocerca con slider de radio configurable de 20m a 500m`
    📁 `src/ui/screens/admin/SedesScreen.kt`

14. `feat(scan): integrar fusedlocationclient para capturar coordenadas antes de registrar`
    📁 `src/ui/screens/scan/ScanScreen.kt` · `app/src/main/AndroidManifest.xml`

15. `feat(qrscanner): agregar gps con fusedlocationclient al escanear qr de empleado`
    📁 `src/ui/screens/admin/QRScannerScreen.kt`

---

## Carlos Moran Guillen

1. `chore(firebase): agregar google-services.json y dependencias firebase bom 33.1.0`
   📁 `app/google-services.json` · `gradle/libs.versions.toml` · `app/build.gradle.kts`

2. `chore(retrofit): configurar retrofitinstance con base url de dummyjson`
   📁 `src/data/network/RetrofitInstance.kt` · `src/data/network/ApiService.kt`

3. `chore(deps): agregar zxing-core 3.5.2 y zxing-android-embedded para generación qr`
   📁 `gradle/libs.versions.toml` · `app/build.gradle.kts`

4. `feat(model): definir modelos user, attendance, attendancestatus y scantype`
   📁 `src/data/model/User.kt` · `src/data/model/Attendance.kt`

5. `feat(repository): implementar userrepository con llamadas a api rest`
   📁 `src/data/repository/UserRepository.kt`

6. `feat(repository): implementar authrepository con firebase auth email y contraseña`
   📁 `src/data/repository/AuthRepository.kt`

7. `feat(repository): crear attendancerepository para guardar asistencias en firestore`
   📁 `src/data/repository/AttendanceRepository.kt`

8. `feat(firestore): definir estructura colección users con campos role, name y companyid`
   📁 `src/data/repository/AuthRepository.kt` · `src/data/model/User.kt`

9. `feat(repository): agregar saveatendance() con uid y companyid en documento firestore`
   📁 `src/data/repository/AttendanceRepository.kt` · `src/data/model/Attendance.kt`

10. `feat(repository): implementar getemployees() y deleteemployee() desde colección users`
    📁 `src/data/repository/AttendanceRepository.kt`

11. `feat(repository): implementar getroleforcurrentuser() para sesión persistente en splash`
    📁 `src/data/repository/AuthRepository.kt`

12. `feat(firestore): agregar escritura en colección notifications tras cada asistencia`
    📁 `src/data/repository/AttendanceRepository.kt` · `src/BioSecureMessagingService.kt`

13. `feat(model): agregar campo companyid a attendance para aislamiento multi-empresa`
    📁 `src/data/model/Attendance.kt`

14. `feat(repository): implementar getemployeesbycompany() con filtro role en memoria`
    📁 `src/data/repository/AttendanceRepository.kt`

15. `feat(repository): agregar getattendancesbycompany() con whereequals companyid`
    📁 `src/data/repository/AttendanceRepository.kt`

16. `feat(model): crear data class shift con name, starttime, tolerancemin y endtime`
    📁 `src/data/model/Shift.kt`

17. `feat(model): agregar attendancestatus.pendiente al enum y fromstring() companion object`
    📁 `src/data/model/Attendance.kt`

18. `feat(repository): implementar confirmattendance() y gettodaypendingattendance()`
    📁 `src/data/repository/AttendanceRepository.kt`

19. `feat(fcm): implementar biosecuremessagingservice con canal de alta importancia`
    📁 `src/BioSecureMessagingService.kt` · `app/src/main/AndroidManifest.xml`

20. `fix(manifest): agregar permiso android.permission.internet que causaba firebase en modo offline`
    📁 `app/src/main/AndroidManifest.xml`

21. `feat(repository): implementar firebasefunctionsrepository con issuecheckintoken()`
    📁 `src/data/repository/FirebaseFunctionsRepository.kt`

22. `feat(repository): implementar observeallatendances() y observeuseratendances() con callbackflow`
    📁 `src/data/repository/AttendanceRepository.kt`

23. `fix(shifts): reemplazar loadshifts() obsoleto por loadcompanyconfig() en shiftsettings`
    📁 `src/ui/screens/admin/ShiftSettingsScreen.kt`

---

## Adrian Flores Cama

1. `feat(navgraph): configurar navhost con rutas base para admin y empleado`
   📁 `src/ui/navigation/NavGraph.kt`

2. `feat(navgraph): conectar todas las pantallas con rutas separadas por rol isadmin`
   📁 `src/ui/navigation/NavGraph.kt`

3. `feat(login): conectar loginscreen con viewmodel, autherror y navegación por rol`
   📁 `src/ui/screens/login/LoginScreen.kt`

4. `feat(navgraph): agregar startdestination condicional y authrepository como parámetro`
   📁 `src/ui/navigation/NavGraph.kt` · `src/MainActivity.kt`

5. `feat(navgraph): registrar screen.adminemployeeqr y screen.employeeqr con navtype.stringtype`
   📁 `src/ui/navigation/NavGraph.kt`

6. `feat(navgraph): agregar screen.splash con poptupTo inclusivo en rutas de login`
   📁 `src/ui/navigation/NavGraph.kt`

7. `feat(register): heredar companyid del admin al registrar nuevo empleado`
   📁 `src/ui/screens/admin/RegisterEmployeeScreen.kt`

8. `feat(navgraph): registrar screen.adminshiftsettings con adminrouteguard`
   📁 `src/ui/navigation/NavGraph.kt`

9. `feat(navgraph): registrar screen.adminshiftmanager con adminrouteguard`
   📁 `src/ui/navigation/NavGraph.kt`

10. `feat(dashboard): añadir quickactioncard gestor de turnos con navegación a shiftmanager`
    📁 `src/ui/screens/dashboard/DashboardScreen.kt`

11. `feat(navgraph): parametrizar screen.dashboard con sedeid y navargument nullable`
    📁 `src/ui/navigation/NavGraph.kt`

12. `feat(navgraph): implementar adminrouteguard para proteger rutas por rol`
    📁 `src/ui/navigation/NavGraph.kt`

13. `feat(bottombar): implementar barra admin con tab sedes en lugar de scan`
    📁 `src/ui/screens/scan/ScanScreen.kt`

14. `fix(login): redirigir admin a screen.adminsedes y empleado a screen.employeescan tras login`
    📁 `src/ui/screens/login/LoginScreen.kt`

15. `docs(team): documentar iteración 12 con merge alpha, gps, callbackflow y sede-first`
    📁 `TEAM.md`
