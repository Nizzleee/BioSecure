#!/usr/bin/env node
/*
 * PostgreSQL worker: copies the attendances produced by the load test from
 * Firestore (emulator) into PostgreSQL and verifies the invariants.
 *
 * The schema lives in docker/postgres/init.sql and is applied idempotently here,
 * so the worker also works against a database created outside Compose.
 *
 * Upsert key: attendance_id (the Firestore document id). If two documents ever
 * shared userId+date, both rows are kept and reported as a duplicate violation
 * instead of being silently merged.
 */

const fs = require("node:fs");
const path = require("node:path");

const { admin, getAdmin, getDb, projectId } = require("./lib/firebase_emulator");
const { ddMmYyyyToIso } = require("./lib/helpers");

const adminSdk = getAdmin();
const db = getDb();

const DATABASE_URL =
  process.env.DATABASE_URL ||
  "postgres://biosecure:biosecure@127.0.0.1:5432/biosecure";
const SCHEMA_FILE = process.env.PG_SCHEMA_FILE || path.join("docker", "postgres", "init.sql");

function requireSchema() {
  const candidates = [
    SCHEMA_FILE,
    path.join("/app", SCHEMA_FILE),
    path.join("..", SCHEMA_FILE),
  ];
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) return fs.readFileSync(candidate, "utf8");
  }
  throw new Error(`schema file not found (looked for ${SCHEMA_FILE})`);
}

function toTimestamp(value) {
  if (!value) return null;
  if (typeof value.toDate === "function") {
    try {
      return value.toDate();
    } catch (_) {
      return null;
    }
  }
  if (value instanceof Date) return value;
  return null;
}

function normalize(doc) {
  const data = doc.data();
  const attendanceDate = ddMmYyyyToIso(data.date);
  if (!attendanceDate) return { error: `invalid date "${data.date}"` };
  if (typeof data.checkIn !== "string" || !/^\d{2}:\d{2}$/.test(data.checkIn)) {
    return { error: `invalid checkIn "${data.checkIn}"` };
  }
  if (!data.userId || !data.companyId) {
    return { error: "missing userId/companyId" };
  }

  const checkOut =
    typeof data.checkOut === "string" && /^\d{2}:\d{2}$/.test(data.checkOut)
      ? data.checkOut
      : null;

  return {
    row: {
      attendance_id: doc.id,
      user_id: data.userId,
      company_id: data.companyId,
      sede_id: data.sedeId || "",
      user_name: data.userName || "Empleado",
      attendance_date: attendanceDate,
      check_in: data.checkIn,
      check_out: checkOut,
      status: data.status || "DESCONOCIDO",
      scan_type: data.type || "DESCONOCIDO",
      location: data.location || null,
      latitude: typeof data.latitude === "number" ? data.latitude : null,
      longitude: typeof data.longitude === "number" ? data.longitude : null,
      accuracy: typeof data.accuracy === "number" ? data.accuracy : null,
      distance_meters:
        typeof data.distanceMeters === "number" ? Math.round(data.distanceMeters) : null,
      request_id: data.requestId || null,
      marked_by: data.marcadoPor || null,
      client_mock_flag: Boolean(data.clientMockFlag),
      created_at: toTimestamp(data.createdAt),
      updated_at: toTimestamp(data.updatedAt),
    },
  };
}

async function main() {
  const { Client } = require("pg");
  const schemaSql = requireSchema();

  console.log(`Syncing Firestore project=${projectId} -> PostgreSQL ${DATABASE_URL}`);
  const client = new Client({ connectionString: DATABASE_URL });
  await client.connect();

  try {
    await client.query(schemaSql);

    const snapshot = await db.collection("attendances").get();
    console.log(`Read ${snapshot.size} attendance document(s) from Firestore`);

    const skipped = [];
    const rows = [];
    for (const doc of snapshot.docs) {
      const { row, error } = normalize(doc);
      if (error) {
        skipped.push({ id: doc.id, error });
        continue;
      }
      rows.push(row);
    }

    let inserted = 0;
    let updated = 0;

    await client.query("BEGIN");
    for (const row of rows) {
      const result = await client.query(
        `INSERT INTO attendances (
           attendance_id, user_id, company_id, sede_id, user_name, attendance_date,
           check_in, check_out, status, scan_type, location, latitude, longitude,
           accuracy, distance_meters, request_id, marked_by, client_mock_flag,
           created_at, updated_at, synced_at
         ) VALUES (
           $1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20, now()
         )
         ON CONFLICT (attendance_id) DO UPDATE SET
           user_id = EXCLUDED.user_id,
           company_id = EXCLUDED.company_id,
           sede_id = EXCLUDED.sede_id,
           user_name = EXCLUDED.user_name,
           attendance_date = EXCLUDED.attendance_date,
           check_in = EXCLUDED.check_in,
           check_out = EXCLUDED.check_out,
           status = EXCLUDED.status,
           scan_type = EXCLUDED.scan_type,
           location = EXCLUDED.location,
           latitude = EXCLUDED.latitude,
           longitude = EXCLUDED.longitude,
           accuracy = EXCLUDED.accuracy,
           distance_meters = EXCLUDED.distance_meters,
           request_id = EXCLUDED.request_id,
           marked_by = EXCLUDED.marked_by,
           client_mock_flag = EXCLUDED.client_mock_flag,
           created_at = EXCLUDED.created_at,
           updated_at = EXCLUDED.updated_at,
           synced_at = now()
         RETURNING (xmax = 0) AS inserted`,
        [
          row.attendance_id,
          row.user_id,
          row.company_id,
          row.sede_id,
          row.user_name,
          row.attendance_date,
          row.check_in,
          row.check_out,
          row.status,
          row.scan_type,
          row.location,
          row.latitude,
          row.longitude,
          row.accuracy,
          row.distance_meters,
          row.request_id,
          row.marked_by,
          row.client_mock_flag,
          row.created_at,
          row.updated_at,
        ]
      );
      if (result.rows[0].inserted) inserted += 1;
      else updated += 1;
    }

    const total = await client.query("SELECT count(*)::int AS total FROM attendances");
    const duplicates = await client.query(
      `SELECT user_id, attendance_date, count(*)::int AS count
         FROM attendances
        GROUP BY user_id, attendance_date
       HAVING count(*) > 1
        ORDER BY count DESC
        LIMIT 20`
    );
    const byStatus = await client.query(
      `SELECT status, count(*)::int AS count FROM attendances GROUP BY status ORDER BY status`
    );
    const byType = await client.query(
      `SELECT scan_type, count(*)::int AS count FROM attendances GROUP BY scan_type ORDER BY scan_type`
    );
    const checkoutSummary = await client.query(
      `SELECT count(*) FILTER (WHERE check_out IS NOT NULL)::int AS with_checkout,
              count(*) FILTER (WHERE check_out IS NULL)::int AS without_checkout
         FROM attendances`
    );

    const summary = {
      firestoreDocuments: snapshot.size,
      upserted: inserted + updated,
      inserted,
      updated,
      skipped: skipped.length,
      skippedSamples: skipped.slice(0, 5),
      postgresRows: total.rows[0].total,
      duplicateUserDateKeys: duplicates.rowCount,
      duplicateSamples: duplicates.rows,
      byStatus: byStatus.rows,
      byType: byType.rows,
      checkouts: checkoutSummary.rows[0],
    };

    await client.query(
      `INSERT INTO sync_runs (
         source, firestore_documents, inserted, updated, skipped, postgres_rows, duplicate_keys
       ) VALUES ($1,$2,$3,$4,$5,$6,$7)`,
      [
        "firestore-emulator",
        snapshot.size,
        inserted,
        updated,
        skipped.length,
        total.rows[0].total,
        duplicates.rowCount,
      ]
    );
    await client.query("COMMIT");

    console.log(JSON.stringify(summary, null, 2));

    if (skipped.length > 0) {
      throw new Error(`${skipped.length} document(s) could not be normalized`);
    }
    if (duplicates.rowCount > 0) {
      throw new Error(`${duplicates.rowCount} duplicate userId+date group(s) in PostgreSQL`);
    }
  } catch (error) {
    await client.query("ROLLBACK").catch(() => {});
    throw error;
  } finally {
    await client.end().catch(() => {});
  }
}

main()
  .catch((error) => {
    console.error("PostgreSQL worker failed:", error.message);
    process.exitCode = 1;
  })
  .finally(async () => {
    await adminSdk.app().delete().catch(() => {});
  });
