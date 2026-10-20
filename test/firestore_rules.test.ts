import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  RulesTestEnvironment
} from '@firebase/rules-unit-testing';
import * as fs from 'fs';

let testEnv: RulesTestEnvironment;

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'biosecure-test-project',
    firestore: {
      rules: fs.readFileSync('firestore.rules', 'utf8'),
      host: '127.0.0.1',
      port: 8080
    }
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();

  // Seed datos iniciales con Admin Context (bypass de reglas)
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();

    // Usuario Admin de Empresa A
    await db.collection('users').doc('admin_A').set({
      role: 'admin',
      companyId: 'companyA',
      name: 'Admin A'
    });

    // Usuario Empleado de Empresa A
    await db.collection('users').doc('employee_A').set({
      role: 'employee',
      companyId: 'companyA',
      name: 'Empleado A',
      sedeId: 'sede_1',
      phone: '999888777',
      email: 'emp_a@test.com'
    });

    // Empresas A y B
    await db.collection('companies').doc('companyA').set({ name: 'Empresa A', horaEntrada: '08:00', toleranciaMin: 15, timezone: 'America/Lima' });
    await db.collection('companies').doc('companyB').set({ name: 'Empresa B', horaEntrada: '08:00', toleranciaMin: 15, timezone: 'America/Lima' });

    // Sede en Empresa A
    await db.collection('companies').doc('companyA').collection('sedes').doc('sede_1').set({
      nombre: 'Sede Principal',
      lat: -12.0463,
      lng: -77.0427,
      radioMetros: 100,
      activa: true
    });

    // Notificación en Empresa A
    await db.collection('notifications').doc('notif_1').set({
      companyId: 'companyA',
      text: 'Marcación realizada'
    });

    // Asistencia en Empresa A
    await db.collection('attendances').doc('employee_A_20260624').set({
      userId: 'employee_A',
      companyId: 'companyA',
      status: 'PUNTUAL'
    });
  });
});

describe('BioSecure Firestore Security Rules - Tests de Ataque y Negativos', () => {

  test('Ataque 1: Empleado no puede crear users/{su_uid} con role=admin', async () => {
    const context = testEnv.authenticatedContext('hacker_uid');
    const db = context.firestore();

    await assertFails(
      db.collection('users').doc('hacker_uid').set({
        role: 'admin',
        companyId: 'companyA',
        name: 'Hacker Admin'
      })
    );
  });

  test('Ataque 2: Empleado no puede modificar su propio sedeId', async () => {
    const context = testEnv.authenticatedContext('employee_A');
    const db = context.firestore();

    await assertFails(
      db.collection('users').doc('employee_A').update({
        sedeId: 'nueva_sede_hacker'
      })
    );
  });

  test('Ataque 3: Admin de Empresa A no puede mover un usuario a companyId B', async () => {
    const context = testEnv.authenticatedContext('admin_A');
    const db = context.firestore();

    await assertFails(
      db.collection('users').doc('employee_A').update({
        companyId: 'companyB'
      })
    );
  });

  test('Ataque 4: Admin de Empresa A no puede escribir en sedes de Empresa B', async () => {
    const context = testEnv.authenticatedContext('admin_A');
    const db = context.firestore();

    await assertFails(
      db.collection('companies').doc('companyB').collection('sedes').doc('sede_x').set({
        nombre: 'Sede Hack',
        lat: -12.0,
        lng: -77.0,
        radioMetros: 50,
        activa: true
      })
    );
  });

  test('Ataque 5: Empleado no puede leer notificaciones', async () => {
    const context = testEnv.authenticatedContext('employee_A');
    const db = context.firestore();

    await assertFails(
      db.collection('notifications').doc('notif_1').get()
    );
  });

  test('Ataque 6: Usuario no registrado en /users no puede leer ninguna colección', async () => {
    const context = testEnv.authenticatedContext('ghost_uid');
    const db = context.firestore();

    await assertFails(
      db.collection('attendances').doc('employee_A_20260624').get()
    );
  });

  test('Ataque 7: Consulta sobre attendances sin filtro de companyId falla', async () => {
    const context = testEnv.authenticatedContext('admin_A');
    const db = context.firestore();

    await assertFails(
      db.collection('attendances').get()
    );
  });

  test('Ataque 8: Modificar timezone de empresa desde el cliente falla', async () => {
    const context = testEnv.authenticatedContext('admin_A');
    const db = context.firestore();

    await assertFails(
      db.collection('companies').doc('companyA').update({
        timezone: 'Asia/Tokyo'
      })
    );
  });
});

describe('BioSecure Firestore Security Rules - Tests Positivos y Operaciones Válidas', () => {

  test('Positivo 1: Admin crea un empleado en su propia empresa', async () => {
    const context = testEnv.authenticatedContext('admin_A');
    const db = context.firestore();

    await assertSucceeds(
      db.collection('users').doc('nuevo_empleado').set({
        role: 'employee',
        companyId: 'companyA',
        name: 'Nuevo Empleado'
      })
    );
  });

  test('Positivo 2: Admin edita una sede de su propia empresa con datos válidos', async () => {
    const context = testEnv.authenticatedContext('admin_A');
    const db = context.firestore();

    await assertSucceeds(
      db.collection('companies').doc('companyA').collection('sedes').doc('sede_1').update({
        nombre: 'Sede Central Actualizada',
        lat: -12.0500,
        lng: -77.0400,
        radioMetros: 150,
        activa: true
      })
    );
  });

  test('Positivo 3: Empleado lee su propia asistencia', async () => {
    const context = testEnv.authenticatedContext('employee_A');
    const db = context.firestore();

    await assertSucceeds(
      db.collection('attendances').doc('employee_A_20260624').get()
    );
  });

  test('Positivo 4: Admin lee asistencias de su empresa con filtro companyId', async () => {
    const context = testEnv.authenticatedContext('admin_A');
    const db = context.firestore();

    await assertSucceeds(
      db.collection('attendances').where('companyId', '==', 'companyA').get()
    );
  });
});

describe('BioSecure Firestore Security Rules - Casos adicionales de escalada y validación', () => {

  test('Ataque 9: Admin A no puede cambiar el role de employee_A a admin', async () => {
    const context = testEnv.authenticatedContext('admin_A');
    const db = context.firestore();

    await assertFails(
      db.collection('users').doc('employee_A').update({
        role: 'admin'
      })
    );
  });

  test('Ataque 10: Usuario autenticado sin doc en users no puede leer companies ni attendances', async () => {
    const context = testEnv.authenticatedContext('ghost_uid');
    const db = context.firestore();

    await assertFails(
      db.collection('companies').doc('companyA').get()
    );

    await assertFails(
      db.collection('attendances').doc('employee_A_20260624').get()
    );
  });

  test('Ataque 11: Empleado no puede cambiar su propio isActive', async () => {
    const context = testEnv.authenticatedContext('employee_A');
    const db = context.firestore();

    await assertFails(
      db.collection('users').doc('employee_A').update({
        isActive: false
      })
    );
  });

  test('Validación de sede: radioMetros 10 y 600 fallan; 100 pasa', async () => {
    const context = testEnv.authenticatedContext('admin_A');
    const db = context.firestore();
    const sedeBase = {
      nombre: 'Sede Validada',
      lat: -12.0463,
      lng: -77.0427,
      activa: true
    };

    await assertFails(
      db.collection('companies').doc('companyA').collection('sedes').doc('sede_10').set({
        ...sedeBase,
        radioMetros: 10
      })
    );

    await assertFails(
      db.collection('companies').doc('companyA').collection('sedes').doc('sede_600').set({
        ...sedeBase,
        radioMetros: 600
      })
    );

    await assertSucceeds(
      db.collection('companies').doc('companyA').collection('sedes').doc('sede_100').set({
        ...sedeBase,
        radioMetros: 100
      })
    );
  });

  test('Ataque 12: Cliente no puede crear, actualizar ni borrar attendances e idempotency_keys', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection('idempotency_keys').doc('idem_1').set({
        uid: 'admin_A',
        response: { ok: true }
      });
    });

    const context = testEnv.authenticatedContext('admin_A');
    const db = context.firestore();

    await assertFails(
      db.collection('attendances').doc('nueva_asistencia').set({
        userId: 'employee_A',
        companyId: 'companyA',
        status: 'PUNTUAL'
      })
    );

    await assertFails(
      db.collection('attendances').doc('employee_A_20260624').update({
        status: 'TARDANZA'
      })
    );

    await assertFails(
      db.collection('attendances').doc('employee_A_20260624').delete()
    );

    await assertFails(
      db.collection('idempotency_keys').doc('idem_nuevo').set({
        uid: 'admin_A',
        response: { ok: true }
      })
    );

    await assertFails(
      db.collection('idempotency_keys').doc('idem_1').update({
        uid: 'employee_A'
      })
    );

    await assertFails(
      db.collection('idempotency_keys').doc('idem_1').delete()
    );
  });
});
