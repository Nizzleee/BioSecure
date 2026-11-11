/*
 * firebase-tools emulator compatibility patch.
 *
 * Problem
 * -------
 * `functionsEmulatorRuntime` wraps the developer's `firebase-admin` module in a
 * `Proxied` object. For every property it hands back
 *
 *     Proxied.getOriginal(target, key) -> value.bind(target)
 *
 * whenever the value is a *function that is not a constructor*. `admin.firestore`
 * is exactly that: firebase-admin exposes it as a getter returning an arrow
 * function with the Firestore statics attached
 * (`Object.assign(fn, require("@google-cloud/firestore").Firestore)`).
 *
 * `.bind()` returns a new function that only keeps `length` and `name`, so inside
 * the emulator
 *
 *     admin.firestore()                 // works
 *     admin.firestore.FieldValue       // undefined  <-- wrong
 *     admin.firestore.Timestamp        // undefined  <-- wrong
 *
 * In production (Cloud Functions) the namespace is the real one and both statics
 * exist, so code like `admin.firestore.FieldValue.serverTimestamp()` works there
 * and fails only under the emulator, which makes the load-test pipeline fail
 * before it can validate anything.
 *
 * Fix
 * ---
 * Keep the bound function (so `this` stays correct) but copy the original
 * property descriptors onto it, restoring FieldValue, Timestamp, v1/v1beta1 and
 * any other static the production namespace exposes. This makes the emulator
 * behave like production instead of changing the application code.
 *
 * The patch is intentionally strict: if firebase-tools changes the shape of
 * `getOriginal`, the build fails here instead of silently regressing.
 */

const fs = require("fs");
const path = require("path");

const TARGET = process.argv[2] ||
  "/usr/local/lib/node_modules/firebase-tools/lib/emulator/functionsEmulatorRuntime.js";
const MARKER = "BioSecure emulator compat";
const ORIGINAL = "            return value.bind(target);";
const PATCHED = [
  "            // BioSecure emulator compat: binding the namespace drops its statics",
  "            // (FieldValue, Timestamp, v1, v1beta1); carry them over so the emulated",
  "            // `firebase-admin` namespace matches production.",
  "            const bound = value.bind(target);",
  "            return Object.defineProperties(bound, Object.getOwnPropertyDescriptors(value));",
].join("\n");

function main() {
  if (!fs.existsSync(TARGET)) {
    throw new Error(`firebase-tools runtime not found at ${TARGET}`);
  }

  const source = fs.readFileSync(TARGET, "utf8");
  if (source.includes(MARKER)) {
    console.log(`[patch-admin-namespace] already patched: ${path.basename(TARGET)}`);
    return;
  }

  if (!source.includes(ORIGINAL)) {
    throw new Error(
      "firebase-tools changed Proxied.getOriginal; review " +
        `${TARGET} before bumping FIREBASE_TOOLS_VERSION`
    );
  }

  fs.writeFileSync(TARGET, source.replace(ORIGINAL, PATCHED));

  const patched = fs.readFileSync(TARGET, "utf8");
  if (!patched.includes(MARKER)) {
    throw new Error("patch did not apply");
  }

  console.log(`[patch-admin-namespace] patched ${TARGET}`);
}

try {
  main();
} catch (error) {
  console.error(`[patch-admin-namespace] FAILED: ${error.message}`);
  process.exit(1);
}
