# Firebase Emulator Suite: Java 21 (Firestore emulator) + Node 20 (Functions/CLI).
# Java 21 comes from Temurin; the Node runtime is copied from the official image
# so the two runtimes share one container without a distro upgrade.
FROM node:20-bookworm-slim AS nodebin

FROM eclipse-temurin:21-jre-jammy

ENV DEBIAN_FRONTEND=noninteractive \
    FIREBASE_CLI_DISABLE_UPDATE_CHECK=true \
    NO_UPDATE_NOTIFIER=1

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates procps \
 && rm -rf /var/lib/apt/lists/*

COPY --from=nodebin /usr/local/bin/node /usr/local/bin/node
COPY --from=nodebin /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -sf /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
 && ln -sf /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx

RUN node --version && npm --version && java -version

ARG FIREBASE_TOOLS_VERSION=15.30.2
RUN npm install -g firebase-tools@${FIREBASE_TOOLS_VERSION}

# Keep admin.firestore.FieldValue/Timestamp available inside the emulator, as in
# production. See patch-admin-namespace.js for the full explanation.
COPY docker/firebase-emulator/patch-admin-namespace.js /usr/local/bin/patch-admin-namespace.js
RUN node /usr/local/bin/patch-admin-namespace.js

WORKDIR /workspace

# Functions dependencies first so code changes do not invalidate the npm layer.
COPY functions/package.json functions/package-lock.json functions/
RUN npm --prefix functions ci

COPY firebase.json firestore.rules ./
COPY functions functions
COPY docker/firebase-emulator/functions.env functions/.env
RUN npm --prefix functions run build && test -f functions/lib/index.js

COPY docker/firebase-emulator/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

EXPOSE 4000 4400 4500 5001 8080 9099

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
