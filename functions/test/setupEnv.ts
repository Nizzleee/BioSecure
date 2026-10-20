// Se ejecuta antes de cargar cualquier módulo de test (setupFiles de Jest).
// Debe fijar el entorno del emulador ANTES de importar firebase-admin / src/index.
process.env.GCLOUD_PROJECT = "demo-biosecure-test";
process.env.FIREBASE_CONFIG = JSON.stringify({ projectId: "demo-biosecure-test" });
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";

// firebase-functions/params resolve sus valores SOLO desde process.env en runtime.
// El emulador de functions los inyectaria; aqui invocamos la funcion con
// firebase-functions-test, asi que replicamos los defaults declarados en src/index.ts.
process.env.MAX_ACCURACY_METERS = "50";
process.env.EARLY_CHECKIN_MINUTES = "60";
process.env.MIN_MINUTES_BEFORE_CHECKOUT = "5";
process.env.MAX_CHECKOUT_OVERTIME_MINUTES = "120";
process.env.IDEMPOTENCY_TTL_DAYS = "7";
