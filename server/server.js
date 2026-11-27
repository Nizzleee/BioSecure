/**
 * BioSecure API - backend puro MongoDB (sin Firebase).
 * Node.js nativo (http) + mongoose + crypto (scrypt/HMAC).
 */
import { createRequire } from "node:module";
import crypto from "node:crypto";

const require = createRequire("D:/TRabajo-web-main/package.json");
const mongoose = require("mongoose");

const PORT = Number(process.env.PORT || 3001);
const MONGO_URI = process.env.MONGODB_URI ||
  "mongodb+srv://sapm3006_db_user:sergioponce1234@cluster0.kssppg3.mongodb.net/biosecure?retryWrites=true&w=majority";
const JWT_SECRET = process.env.AUTH_SECRET || "biosecure-dev-secret-cambiar-en-produccion-2026";

// ---------------------------------------------------------------- auth utils
const b64u = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
const sign = (payload) => {
  const body = b64u(payload);
  const sig = crypto.createHmac("sha256", JWT_SECRET).update(body).digest("base64url");
  return `${body}.${sig}`;
};
const verifyToken = (token) => {
  if (!token || typeof token !== "string") return null;
  const [body, sig] = token.split(".");
  if (!body || !sig) return null;
  const expected = crypto.createHmac("sha256", JWT_SECRET).update(body).digest("base64url");
  const a = Buffer.from(sig), b = Buffer.from(expected);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return null;
  try {
    const payload = JSON.parse(Buffer.from(body, "base64url").toString());
    if (payload.exp && Date.now() > payload.exp) return null;
    return payload;
  } catch { return null; }
};
const hashPassword = (password) => {
  const salt = crypto.randomBytes(16).toString("hex");
  const hash = crypto.scryptSync(String(password), salt, 64).toString("hex");
  return `${salt}:${hash}`;
};
const checkPassword = (password, stored) => {
  if (!stored || !stored.includes(":")) return false;
  const [salt, hex] = stored.split(":");
  const a = Buffer.from(hex, "hex");
  const b = crypto.scryptSync(String(password), salt, 64);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
};

// ---------------------------------------------------------------- helpers
const haversine = (lat1, lng1, lat2, lng2) => {
  const R = 6371000, rad = (d) => (d * Math.PI) / 180;
  const dLat = rad(lat2 - lat1), dLng = rad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
};
const hhMmToMinutes = (hhmm) => {
  if (typeof hhmm !== "string" || !/^\d{2}:\d{2}$/.test(hhmm)) throw new Error("Formato de hora inválido");
  const [h, m] = hhmm.split(":").map(Number);
  if (h > 23 || m > 59) throw new Error("Hora fuera de rango");
  return h * 60 + m;
};
const serverTime = (timeZone = "America/Lima") => {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone, year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", hour12: false,
  }).formatToParts(new Date());
  const p = Object.fromEntries(parts.filter(x => x.type !== "literal").map(x => [x.type, x.value]));
  const yyyyMmDd = `${p.year}${p.month}${p.day}`;
  return {
    yyyyMmDd,
    date: `${p.day}/${p.month}/${p.year}`,
    hhMm: `${p.hour}:${p.minute}`,
    minutes: Number(p.hour) * 60 + Number(p.minute),
  };
};
const attendanceStatus = (hhMm, shift, earlyMinutes = 60) => {
  const now = hhMmToMinutes(hhMm);
  const start = hhMmToMinutes(shift.startTime);
  const end = hhMmToMinutes(shift.endTime);
  const tol = Number(shift.toleranceMin ?? 15);
  if (now < start - earlyMinutes) throw new Error("Fuera de ventana: demasiado temprano");
  if (now > end + tol) return "TARDANZA";
  return "PUNTUAL";
};

// ---------------------------------------------------------------- mongoose schemas
const Mixed = mongoose.Schema.Types.Mixed;
const col = (name) => new mongoose.Schema({ _id: Mixed }, { strict: false, collection: name });
const Users = mongoose.model("UserBs", col("users"));
const Companies = mongoose.model("CompanyBs", col("companies"));
const Sedes = mongoose.model("SedeBs", col("sedes"));
const Shifts = mongoose.model("ShiftBs", col("shifts"));
const Attendances = mongoose.model("AttendanceBs", col("attendances"));
const Notifications = mongoose.model("NotificationBs", col("notifications"));
const Idempotency = mongoose.model("IdempotencyBs", col("idempotency_keys"));

// ---------------------------------------------------------------- response helpers
const send = (res, status, body) => {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  });
  res.end(JSON.stringify(body));
};
const ok = (res, data) => send(res, 200, { ok: true, data });
const fail = (res, status, message, code) => send(res, status, { ok: false, error: message, code: code || "ERROR" });

const readBody = (req) => new Promise((resolve, reject) => {
  let raw = "";
  req.on("data", (c) => { raw += c; if (raw.length > 2e6) req.destroy(); });
  req.on("end", () => {
    if (!raw) return resolve({});
    try { resolve(JSON.parse(raw)); } catch { reject(new Error("JSON inválido")); }
  });
  req.on("error", reject);
});

const authOf = (req) => {
  const h = req.headers.authorization || "";
  const token = h.startsWith("Bearer ") ? h.slice(7) : "";
  const payload = verifyToken(token);
  return payload ? { uid: payload.uid, role: payload.role, companyId: payload.companyId } : null;
};
const requireAuth = (req, res) => {
  const a = authOf(req);
  if (!a) { fail(res, 401, "No autenticado o sesión expirada", "UNAUTHENTICATED"); return null; }
  return a;
};
const requireAdmin = (req, res) => {
  const a = requireAuth(req, res);
  if (!a) return null;
  if (a.role !== "admin") { fail(res, 403, "Requiere rol de administrador", "PERMISSION_DENIED"); return null; }
  return a;
};

const publicUser = (u) => ({
  uid: String(u._id),
  email: u.email || "",
  role: u.role || "employee",
  name: u.name || "",
  firstName: u.firstName || "",
  lastName: u.lastName || "",
  department: u.department || "",
  cargo: u.cargo || "",
  companyId: u.companyId || "",
  shiftId: u.shiftId || "",
  sedeId: u.sedeId || "",
  image: u.image || "",
  isActive: u.isActive !== false,
});

// ---------------------------------------------------------------- routes
const routes = {
  // ---- AUTH ----
  "POST /api/auth/login": async (req, res) => {
    const { email, password } = await readBody(req);
    const u = await Users.findOne({ email: String(email || "").trim().toLowerCase() });
    if (!u || !checkPassword(password, u.passwordHash)) return fail(res, 401, "Correo o contraseña incorrectos", "INVALID_CREDENTIALS");
    if (u.isActive === false) return fail(res, 403, "Usuario inactivo", "USER_INACTIVE");
    const user = publicUser(u);
    const token = sign({ uid: user.uid, role: user.role, companyId: user.companyId, exp: Date.now() + 7 * 24 * 3600 * 1000 });
    ok(res, { token, user });
  },
  "POST /api/auth/google": async (req, res) => {
    const { idToken } = await readBody(req);
    if (!idToken) return fail(res, 400, "idToken requerido", "INVALID_ARGUMENT");
    try {
      // Verificación real del id_token de Google (caduca y firma validada por Google)
      const info = await fetch(`https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(idToken)}`);
      const claims = await info.json();
      if (!info.ok || !claims.email || claims.email_verified !== "true") {
        return fail(res, 401, "Token de Google inválido o no verificado", "INVALID_GOOGLE_TOKEN");
      }
      const expected = process.env.GOOGLE_CLIENT_ID || "";
      if (expected && claims.aud !== expected) {
        return fail(res, 401, "El token no pertenece a esta aplicación", "INVALID_GOOGLE_AUDIENCE");
      }
      const email = String(claims.email).toLowerCase();
      let u = await Users.findOne({ email });
      if (!u) {
        const uid = `g_${crypto.randomUUID()}`;
        u = {
          _id: uid, email, role: "employee",
          name: claims.name || email.substring(0, claims.email.indexOf("@")),
          firstName: claims.given_name || "", lastName: claims.family_name || "",
          image: claims.picture || "", companyId: process.env.DEFAULT_COMPANY_ID || "Pizzeria",
          shiftId: "", sedeId: "", isActive: true,
          passwordHash: hashPassword(crypto.randomBytes(24).toString("hex")),
          createdAt: new Date(), googleSub: claims.sub,
        };
        await Users.create(u);
      } else {
        // Actualiza foto/nombre si entró con Google (no pisa campos ya editados manualmente salvo imagen vacía)
        const set = {};
        if (claims.picture && !u.image) set.image = claims.picture;
        if (claims.name && !u.name) set.name = claims.name;
        if (claims.given_name && !u.firstName) set.firstName = claims.given_name;
        if (claims.family_name && !u.lastName) set.lastName = claims.family_name;
        if (Object.keys(set).length) await Users.updateOne({ _id: u._id }, { $set: set });
        u = await Users.findById(u._id).lean();
      }
      const user = publicUser(u);
      const token = sign({ uid: user.uid, role: user.role, companyId: user.companyId, exp: Date.now() + 7 * 24 * 3600 * 1000 });
      ok(res, { token, user });
    } catch (e) {
      fail(res, 401, "No se pudo verificar con Google: " + e.message, "GOOGLE_VERIFY_ERROR");
    }
  },
  "GET /api/auth/me": async (req, res) => {
    const a = requireAuth(req, res); if (!a) return;
    const u = await Users.findById(a.uid);
    if (!u) return fail(res, 404, "Usuario no encontrado", "NOT_FOUND");
    ok(res, publicUser(u));
  },
  "POST /api/auth/logout": async (req, res) => ok(res, true),

  // ---- USUARIOS / EMPLEADOS ----
  "GET /api/users": async (req, res) => {
    const a = requireAuth(req, res); if (!a) return;
    const url = new URL(req.url, "http://localhost");
    const q = {};
    if (url.searchParams.get("companyId")) q.companyId = url.searchParams.get("companyId");
    if (url.searchParams.get("role")) q.role = url.searchParams.get("role");
    if (a.role !== "admin") q._id = a.uid;
    const list = await Users.find(q).sort({ name: 1 }).lean();
    ok(res, list.map(publicUser));
  },
  "POST /api/users": async (req, res) => {
    const a = requireAdmin(req, res); if (!a) return;
    const b = await readBody(req);
    const email = String(b.email || "").trim().toLowerCase();
    if (!email || !b.password) return fail(res, 400, "Email y contraseña son obligatorios", "INVALID_ARGUMENT");
    if (String(b.password).length < 6) return fail(res, 400, "La contraseña debe tener al menos 6 caracteres", "WEAK_PASSWORD");
    if (await Users.findOne({ email })) return fail(res, 409, "Ya existe un usuario con ese correo", "EMAIL_IN_USE");
    const uid = String(b.uid || crypto.randomUUID());
    const user = {
      _id: uid, email, passwordHash: hashPassword(b.password),
      role: b.role === "admin" ? "admin" : "employee",
      name: b.name || `${b.firstName || ""} ${b.lastName || ""}`.trim() || email,
      firstName: b.firstName || "", lastName: b.lastName || "",
      department: b.department || "", cargo: b.cargo || "",
      companyId: b.companyId || a.companyId, shiftId: b.shiftId || "",
      sedeId: b.sedeId || "", image: b.image || "", isActive: b.isActive !== false,
      createdAt: new Date(),
    };
    await Users.create(user);
    ok(res, publicUser(user));
  },
  "PUT /api/users/:uid": async (req, res, uid) => {
    const a = requireAdmin(req, res); if (!a) return;
    const b = await readBody(req);
    const allowed = ["name", "firstName", "lastName", "email", "department", "cargo", "shiftId", "sedeId", "isActive", "role", "companyId"];
    const set = {};
    for (const k of allowed) if (k in b) set[k] = b[k];
    if (b.password && String(b.password).length >= 6) set.passwordHash = hashPassword(b.password);
    const u = await Users.findByIdAndUpdate(uid, { $set: set }, { new: true });
    if (!u) return fail(res, 404, "Usuario no encontrado", "NOT_FOUND");
    ok(res, publicUser(u));
  },
  "DELETE /api/users/:uid": async (req, res, uid) => {
    const a = requireAdmin(req, res); if (!a) return;
    await Users.deleteOne({ _id: uid });
    ok(res, true);
  },

  // ---- COMPANY / SHIFTS ----
  "GET /api/companies/:id": async (req, res, id) => {
    const a = requireAuth(req, res); if (!a) return;
    const c = await Companies.findById(id).lean();
    if (!c) return fail(res, 404, "Empresa no encontrada", "NOT_FOUND");
    ok(res, c);
  },
  "PUT /api/companies/:id": async (req, res, id) => {
    const a = requireAdmin(req, res); if (!a) return;
    const b = await readBody(req);
    const allowed = ["name", "horaEntrada", "toleranciaMin", "timezone", "geoFence", "checkInStart", "checkInEnd"];
    const set = {};
    for (const k of allowed) if (k in b) set[k] = b[k];
    const c = await Companies.findByIdAndUpdate(id, { $set: set }, { new: true, upsert: true });
    ok(res, c);
  },
  "PUT /api/companies/:id/shifts": async (req, res, id) => {
    const a = requireAdmin(req, res); if (!a) return;
    const { shifts } = await readBody(req);
    if (!Array.isArray(shifts)) return fail(res, 400, "shifts debe ser una lista", "INVALID_ARGUMENT");
    await Companies.updateOne({ _id: id }, { $set: { shifts } }, { upsert: true });
    await Shifts.deleteMany({ companyId: id });
    const docs = shifts.map((s, i) => ({ _id: s.id || `t${i + 1}`, ...s, companyId: id }));
    if (docs.length) await Shifts.insertMany(docs, { ordered: false }).catch(() => {});
    ok(res, shifts);
  },

  // ---- SEDES ----
  "GET /api/sedes": async (req, res) => {
    const a = requireAuth(req, res); if (!a) return;
    const url = new URL(req.url, "http://localhost");
    const q = url.searchParams.get("companyId") ? { companyId: url.searchParams.get("companyId") } : {};
    ok(res, await Sedes.find(q).lean());
  },
  "POST /api/sedes": async (req, res) => {
    const a = requireAdmin(req, res); if (!a) return;
    const b = await readBody(req);
    const id = String(b.id || b._id || crypto.randomUUID());
    const doc = { _id: id, id, companyId: b.companyId || a.companyId, nombre: b.nombre || "Sede",
      lat: Number(b.lat), lng: Number(b.lng), radioMetros: Number(b.radioMetros || 100),
      activa: b.activa !== false, creadaEn: new Date() };
    await Sedes.updateOne({ _id: id }, { $set: doc }, { upsert: true });
    ok(res, doc);
  },
  "DELETE /api/sedes/:id": async (req, res, id) => {
    const a = requireAdmin(req, res); if (!a) return;
    await Sedes.deleteOne({ _id: id });
    ok(res, true);
  },

  // ---- ATTENDANCE ----
  "GET /api/attendances": async (req, res) => {
    const a = requireAuth(req, res); if (!a) return;
    const url = new URL(req.url, "http://localhost");
    const q = {};
    if (url.searchParams.get("companyId")) q.companyId = url.searchParams.get("companyId");
    if (url.searchParams.get("uid")) q.userId = url.searchParams.get("uid");
    if (a.role !== "admin") q.userId = a.uid;
    ok(res, await Attendances.find(q).sort({ date: -1 }).limit(500).lean());
  },
  "POST /api/attendances/register": async (req, res) => {
    const a = requireAuth(req, res); if (!a) return;
    const b = await readBody(req);
    const lat = Number(b.lat), lng = Number(b.lng), accuracy = Number(b.accuracy ?? 50);
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) return fail(res, 400, "Coordenadas inválidas", "INVALID_ARGUMENT");
    if (!Number.isFinite(accuracy) || accuracy > 50) return fail(res, 400, `Accuracy excesivamente alta (${accuracy}m > 50m)`, "ACCURACY_TOO_HIGH");
    const requestId = String(b.requestId || "").trim();
    if (!requestId) return fail(res, 400, "requestId obligatorio", "INVALID_ARGUMENT");

    const idem = await Idempotency.findById(requestId).lean();
    if (idem) { if (idem.uid !== a.uid) return fail(res, 403, "requestId usado por otro usuario", "PERMISSION_DENIED"); return ok(res, idem.response); }

    //obra target: qrToken == uid del empleado (admin escanea); si no, el propio caller
    const targetUid = String(b.qrToken || "").trim() || a.uid;
    const caller = await Users.findById(a.uid).lean();
    if (!caller) return fail(res, 403, "Perfil del solicitante no existe", "PERMISSION_DENIED");
    if (targetUid === a.uid && caller.role === "admin") return fail(res, 403, "ADMIN_CANNOT_MARK_SELF", "ADMIN_CANNOT_MARK_SELF");
    const target = targetUid === a.uid ? caller : await Users.findById(targetUid).lean();
    if (!target) return fail(res, 404, "Empleado objetivo no existe", "NOT_FOUND");
    if (target.role !== "employee") return fail(res, 403, "TARGET_NOT_EMPLOYEE", "TARGET_NOT_EMPLOYEE");
    if (target.isActive === false) return fail(res, 403, "Empleado inactivo", "USER_INACTIVE");
    if (targetUid !== a.uid) {
      if (caller.role !== "admin") return fail(res, 403, "Solo un admin puede escanear empleados", "PERMISSION_DENIED");
      if (caller.companyId !== target.companyId) return fail(res, 403, "Empresa distinta", "PERMISSION_DENIED");
    }
    const companyId = target.companyId;
    if (!companyId) return fail(res, 400, "Empleado sin empresa", "FAILED_PRECONDITION");
    const company = await Companies.findById(companyId).lean();
    if (!company) return fail(res, 400, "Empresa no existe", "FAILED_PRECONDITION");

    if (!target.sedeId) return fail(res, 400, "El empleado no tiene sede asignada", "NO_SEDE_ASSIGNED");
    const sede = await Sedes.findById(target.sedeId).lean() || await Sedes.findOne({ id: target.sedeId, companyId }).lean();
    if (!sede || sede.activa === false) return fail(res, 400, "Sede inválida o inactiva", "INVALID_SEDE_CONFIG");
    const radio = Number(sede.radioMetros);
    if (!Number.isFinite(Number(sede.lat)) || !Number.isFinite(Number(sede.lng)) || radio < 20 || radio > 500)
      return fail(res, 400, "Configuración geográfica de sede corrupta", "INVALID_SEDE_CONFIG");
    const distance = haversine(lat, lng, Number(sede.lat), Number(sede.lng));
    if (distance > radio) return fail(res, 400, `OUT_OF_RANGE_GEOFENCE: Distancia ${distance.toFixed(1)}m supera el radio (${radio}m)`, "OUT_OF_RANGE_GEOFENCE");

    if (!target.shiftId) return fail(res, 400, "El empleado no tiene turno asignado", "NO_SHIFT_ASSIGNED");
    const shift = await Shifts.findById(target.shiftId).lean() || (company.shifts || []).find(s => s.id === target.shiftId);
    if (!shift) return fail(res, 400, "El turno asignado no existe", "NO_SHIFT_ASSIGNED");

    const t = serverTime(company.timezone || "America/Lima");
    const docId = `${targetUid}_${t.yyyyMmDd}`;
    const existing = await Attendances.findById(docId).lean();
    let response;

    if (!existing) {
      let status;
      try { status = attendanceStatus(t.hhMm, shift, 60); } catch (e) { return fail(res, 400, e.message, "OUT_OF_WINDOW"); }
      const type = targetUid === a.uid ? (b.tipo === "FACIAL" ? "FACIAL" : "HUELLA") : "QR";
      const doc = {
        _id: docId, uid: targetUid, userId: targetUid, userName: target.name || "Empleado",
        companyId, sedeId: target.sedeId, date: t.date, checkIn: t.hhMm, checkOut: "",
        status, type, location: sede.nombre || "Sede", latitude: lat, longitude: lng,
        accuracy, distanceMeters: Math.round(distance), requestId, marcadoPor: a.uid,
        createdAt: new Date(), updatedAt: new Date(),
      };
      await Attendances.create(doc);
      await Notifications.create({ _id: crypto.randomUUID(), companyId, type: "attendance",
        userName: target.name || "Empleado", checkIn: t.hhMm, timestamp: new Date(), read: false });
      response = { status, checkIn: t.hhMm, date: t.date, userName: target.name, type: "CHECK_IN" };
    } else {
      if (existing.checkOut) return fail(res, 400, "ALREADY_COMPLETE: Entrada y salida ya registradas hoy", "ALREADY_COMPLETE");
      const inMin = hhMmToMinutes(existing.checkIn);
      if (t.minutes < inMin + 5) return fail(res, 400, "TOO_SOON: Deben transcurrir 5 min desde el check-in", "TOO_SOON");
      const endMin = hhMmToMinutes(shift.endTime);
      if (t.minutes > endMin + 120) return fail(res, 400, "OUT_OF_WINDOW: excede horas extra permitidas", "OUT_OF_WINDOW");
      await Attendances.updateOne({ _id: docId }, { $set: { checkOut: t.hhMm, updatedAt: new Date() } });
      response = { status: existing.status || "EXITOSO", checkIn: existing.checkIn, checkOut: t.hhMm,
        date: existing.date, userName: existing.userName, type: "CHECK_OUT" };
    }
    await Idempotency.create({ _id: requestId, uid: a.uid, response, expiresAt: new Date(Date.now() + 7 * 864e5) });
    console.log(JSON.stringify({ event: "ATTENDANCE_PROCESSED", companyId, type: response.type, status: response.status, distanceMeters: Math.round(distance), requestId }));
    ok(res, response);
  },
  "PUT /api/attendances/:id/confirm": async (req, res, id) => {
    const a = requireAdmin(req, res); if (!a) return;
    const att = await Attendances.findById(id);
    if (!att) return fail(res, 404, "Asistencia no encontrada", "NOT_FOUND");
    if (att.companyId !== a.companyId) return fail(res, 403, "Empresa distinta", "PERMISSION_DENIED");
    att.status = "EXITOSO"; att.confirmedBy = a.uid; att.updatedAt = new Date();
    await att.save();
    ok(res, att);
  },

  // ---- NOTIFICACIONES ----
  "GET /api/notifications": async (req, res) => {
    const a = requireAuth(req, res); if (!a) return;
    ok(res, await Notifications.find({ companyId: a.companyId }).sort({ timestamp: -1 }).limit(100).lean());
  },
  "PUT /api/notifications/read": async (req, res) => {
    const a = requireAuth(req, res); if (!a) return;
    await Notifications.updateMany({ companyId: a.companyId }, { $set: { read: true } });
    ok(res, true);
  },

  // ---- HEALTH / SEED ----
  "GET /api/health": async (req, res) => ok(res, { status: "ok", db: mongoose.connection.readyState, time: new Date().toISOString() }),
  "POST /api/seed/passwords": async (req, res) => {
    const b = await readBody(req);
    const email = String(b.email || "").toLowerCase();
    const u = await Users.findOne({ email });
    if (!u) return fail(res, 404, "Usuario no encontrado", "NOT_FOUND");
    u.passwordHash = hashPassword(b.password || "123456");
    await u.save();
    ok(res, { uid: String(u._id), email: u.email });
  },
};

// ---------------------------------------------------------------- server
const server = require("node:http").createServer(async (req, res) => {
  if (req.method === "OPTIONS") return send(res, 204, {});
  const url = new URL(req.url, "http://localhost");
  const path = url.pathname.replace(/\/+$/, "") || "/";
  for (const [key, handler] of Object.entries(routes)) {
    const [method, pattern] = key.split(" ");
    if (method !== req.method) continue;
    const params = {};
    const pParts = pattern.split("/");
    const rParts = path.split("/");
    if (pParts.length !== rParts.length) continue;
    let match = true;
    for (let i = 0; i < pParts.length; i++) {
      if (pParts[i].startsWith(":")) params[pParts[i].slice(1)] = decodeURIComponent(rParts[i]);
      else if (pParts[i] !== rParts[i]) { match = false; break; }
    }
    if (!match) continue;
    try { await handler(req, res, ...Object.values(params)); }
    catch (e) { console.error("ERROR", key, e); fail(res, 500, e.message || "Error interno", "INTERNAL"); }
    return;
  }
  fail(res, 404, `Ruta no encontrada: ${req.method} ${path}`, "NOT_FOUND");
});

(async () => {
  await mongoose.connect(MONGO_URI, { bufferCommands: false });
  console.log("MongoDB conectado:", mongoose.connection.name);
  server.listen(PORT, () => console.log(`BioSecure API escuchando en http://127.0.0.1:${PORT}`));
})().catch((e) => { console.error("Fatal:", e.message); process.exit(1); });
