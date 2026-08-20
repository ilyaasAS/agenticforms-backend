#!/bin/sh
set -e

MEDIA_DIR="${MEDIA_STORAGE_DIR:-/data/media}"
mkdir -p "$MEDIA_DIR" || true

# Volume Docker souvent root:root + cap_drop ALL → chown peut échouer.
chown -R spring:spring "$MEDIA_DIR" 2>/dev/null || true
chmod -R u+rwX "$MEDIA_DIR" 2>/dev/null || true

# Si toujours pas writable pour spring, bascule sur /tmp (world-writable).
if ! runuser -u spring -- sh -c "test -w '$MEDIA_DIR'" 2>/dev/null; then
  MEDIA_DIR="/tmp/agenticform-media"
  mkdir -p "$MEDIA_DIR"
  chmod 1777 "$MEDIA_DIR" 2>/dev/null || chmod 777 "$MEDIA_DIR" 2>/dev/null || true
  export MEDIA_STORAGE_DIR="$MEDIA_DIR"
  echo "WARN: media storage fallback -> $MEDIA_STORAGE_DIR" >&2
fi

exec runuser -u spring -- java -jar /app/app.jar
