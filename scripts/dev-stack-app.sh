#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"

fail() {
  printf 'Dev-stack setup: %s\n' "$1" >&2
  exit 1
}

[[ -f "$ENV_FILE" ]] || fail "Create .env from .env.example and fill in the development-service settings first."

set -a
# .env is a shell-compatible local file and is gitignored.
source "$ENV_FILE"
set +a

require_value() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "$name is empty in .env."
}

for name in SPRING_PROFILES_ACTIVE DB_URL DB_USERNAME DB_PASSWORD MONGODB_URI \
  CHAT_STORE AI_PROVIDER AWS_REGION AWS_BEARER_TOKEN_BEDROCK AI_MODEL \
  TOKEN_ENCRYPTION_KEY QSTASH_ENABLED QSTASH_URL; do
  require_value "$name"
done

[[ "$SPRING_PROFILES_ACTIVE" == "postgres" ]] || fail "Set SPRING_PROFILES_ACTIVE=postgres in .env."
[[ "$CHAT_STORE" == "mongo" ]] || fail "Set CHAT_STORE=mongo in .env."
[[ "$DB_URL" == jdbc:postgresql://* ]] || fail "DB_URL must use PostgreSQL JDBC format: jdbc:postgresql://host:port/database?sslmode=... . Keep DB_USERNAME and DB_PASSWORD in their separate variables."

python3 - "$MONGODB_URI" <<'PY' || fail "MONGODB_URI must include a database name in its path, for example /mailai_chat."
import sys
from urllib.parse import urlsplit
parsed = urlsplit(sys.argv[1])
if parsed.scheme not in ("mongodb", "mongodb+srv") or not parsed.path.strip("/"):
    raise SystemExit(1)
PY

if [[ "$DB_URL" != *"sslmode="* ]]; then
  fail "DB_URL must include the SSL mode required by the PostgreSQL provider."
fi
if [[ -n "${DB_SSL_ROOT_CERT:-}" ]]; then
  [[ "$DB_SSL_ROOT_CERT" == /* ]] || fail "DB_SSL_ROOT_CERT must be an absolute path."
  [[ -r "$DB_SSL_ROOT_CERT" && -f "$DB_SSL_ROOT_CERT" ]] || fail "DB_SSL_ROOT_CERT must point to a readable CA certificate file."
  [[ "$DB_URL" == *"sslmode=verify-full"* ]] || fail "Set sslmode=verify-full in DB_URL when DB_SSL_ROOT_CERT is provided."
fi
if [[ "$DB_URL" == *"sslmode=require"* && "$DB_URL" != *"sslrootcert="* ]]; then
  if [[ -z "${DB_SSL_ROOT_CERT:-}" ]]; then
    printf 'Dev-stack setup: PostgreSQL is encrypted with sslmode=require, but the server certificate is not verified.\n' >&2
  fi
fi

command -v mvn >/dev/null 2>&1 || fail "Maven is required to run Spring Boot locally."

cd "$ROOT_DIR/mail.ai/server"
export SERVER_PORT="${SERVER_PORT:-8082}"
export PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-http://localhost:${SERVER_PORT}}"
export GOOGLE_REDIRECT_URI="${GOOGLE_REDIRECT_URI:-http://localhost:${SERVER_PORT}/auth/google/callback}"
export FRONTEND_BASE_URL="${FRONTEND_BASE_URL:-http://localhost:4201}"
export FRONTEND_ORIGIN="${FRONTEND_ORIGIN:-http://localhost:4201}"

if [[ "$QSTASH_ENABLED" == "true" && "$PUBLIC_BASE_URL" == http://localhost* ]]; then
  printf 'Dev-stack setup: QStash is enabled, but its callback URL is localhost and cannot be reached by Upstash. Use a public HTTPS tunnel and set PUBLIC_BASE_URL to its URL before testing scheduled callbacks.\n' >&2
fi

printf 'Starting Spring Boot locally with the external development services from .env.\n'
printf 'PostgreSQL, MongoDB, Bedrock, and QStash credentials are read from the environment; no local dependency containers are started.\n'
printf 'Backend: http://localhost:%s\n' "$SERVER_PORT"
printf 'Stop the app with Ctrl+C.\n\n'

mvn spring-boot:run
