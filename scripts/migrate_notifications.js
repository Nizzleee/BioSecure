/**
 * Script de migración con Firebase Admin SDK para agregar el campo 'companyId'
 * a todas las notificaciones existentes en Firestore.
 *
 * Uso:
 * 1. npm install firebase-admin
 * 2. export GOOGLE_APPLICATION_CREDENTIALS="/ruta/a/serviceAccountKey.json"
 * 3. node scripts/migrate_notifications.js <DEFAULT_COMPANY_ID>
 */

const admin = require('firebase-admin');

if (!process.env.GOOGLE_APPLICATION_CREDENTIALS && !process.env.FIRESTORE_EMULATOR_HOST) {
  console.error("ERROR: Debes definir GOOGLE_APPLICATION_CREDENTIALS o FIRESTORE_EMULATOR_HOST");
  process.exit(1);
}

const defaultCompanyId = process.argv[2] || "EmpresaDefault";

admin.initializeApp();
const db = admin.firestore();

async function migrateNotifications() {
  console.log(`Iniciando migración de notificaciones a companyId = '${defaultCompanyId}'...`);

  const snapshot = await db.collection('notifications').get();
  if (snapshot.empty) {
    console.log("No hay notificaciones para migrar.");
    return;
  }

  let batch = db.batch();
  let count = 0;
  let updated = 0;

  for (const doc of snapshot.docs) {
    const data = doc.data();
    if (!data.companyId) {
      batch.update(doc.ref, { companyId: defaultCompanyId });
      count++;
      updated++;
    }

    if (count === 500) {
      await batch.commit();
      batch = db.batch();
      count = 0;
    }
  }

  if (count > 0) {
    await batch.commit();
  }

  console.log(`Migración completada con éxito. Documentos actualizados: ${updated}/${snapshot.size}`);
}

migrateNotifications().catch(console.error);
