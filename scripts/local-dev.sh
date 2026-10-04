#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
QSTASH_ENV_FILE="$ROOT_DIR/.local/qstash.env"

usage() {
  cat <<'EOF'
Usage: ./scripts/local-dev.sh [up|down|logs]

  up    Start PostgreSQL, MongoDB, local QStash, and the Spring app (default)
  down  Stop local containers while preserving database volumes
  logs  Follow the Spring app logs
EOF
}

fail() {
  printf 'Local setup: %s\n' "$1" >&2
  exit 1
}

load_env() {
  if [[ ! -f "$ENV_FILE" ]]; then
    cp "$ROOT_DIR/.env.example" "$ENV_FILE"
    chmod 600 "$ENV_FILE"
    fail "Created .env from .env.example. Fill in Google OAuth and AWS Bedrock bearer token, then run this script again."
  fi
  chmod 600 "$ENV_FILE"
  set -a
  # .env is a shell-compatible local file and is gitignored.
  source "$ENV_FILE"
  set +a
}

require_value() {
  local name="$1"
  local value="${!name:-}"
  [[ -n "$value" ]] || fail "$name is empty in .env. Set it before starting the local stack."
}

reject_placeholder() {
  local name="$1"
  local value="${!name:-}"
  case "$value" in
    your-*|change-this*|replace-*) fail "$name still has a placeholder value in .env." ;;
  esac
}

check_port_available() {
  local port="$1"
  local service="$2"
  command -v nc >/dev/null 2>&1 || return 0
  if nc -z 127.0.0.1 "$port" >/dev/null 2>&1; then
    local running_services
    running_services="$(docker compose ps --services --status running 2>/dev/null || true)"
    if ! grep -qx "$service" <<< "$running_services"; then
      fail "Port $port is already in use. Stop the existing process using it, then retry."
    fi
  fi
}

prepare_encryption_key() {
  if [[ -z "${TOKEN_ENCRYPTION_KEY:-}" ]]; then
    python3 - "$ENV_FILE" <<'PY'
import base64
import os
import secrets
import sys
from pathlib import Path

path = Path(sys.argv[1])
key = base64.b64encode(secrets.token_bytes(32)).decode("ascii")
lines = path.read_text().splitlines()
for index, line in enumerate(lines):
    if line.split("=", 1)[0].strip() == "TOKEN_ENCRYPTION_KEY":
        lines[index] = f"TOKEN_ENCRYPTION_KEY={key}"
        break
else:
    lines.append(f"TOKEN_ENCRYPTION_KEY={key}")
path.write_text("\n".join(lines).rstrip() + "\n")
os.chmod(path, 0o600)
PY
    load_env
    printf 'Generated and saved TOKEN_ENCRYPTION_KEY in .env.\n'
  fi

  python3 - "$TOKEN_ENCRYPTION_KEY" <<'PY'
import base64
import sys
try:
    valid = len(base64.b64decode(sys.argv[1], validate=True)) == 32
except Exception:
    valid = False
if not valid:
    raise SystemExit("TOKEN_ENCRYPTION_KEY must be Base64 encoding of exactly 32 bytes.")
PY
}

write_qstash_env() {
  local logs="$1"
  QSTASH_LOGS="$logs" python3 - "$QSTASH_ENV_FILE" <<'PY'
import os
import re
import sys
from pathlib import Path

log = os.environ["QSTASH_LOGS"]
keys = ("QSTASH_TOKEN", "QSTASH_CURRENT_SIGNING_KEY", "QSTASH_NEXT_SIGNING_KEY")
values = {}
for key in keys:
    pattern = re.compile(rf"\b{key}\s*=\s*(?:\"([^\"]+)\"|'([^']+)'|([^\s]+))")
    matches = list(pattern.finditer(log))
    if matches:
        match = matches[-1]
        values[key] = next(group for group in match.groups() if group is not None)
if len(values) != len(keys):
    raise SystemExit(1)

path = Path(sys.argv[1])
path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
old_umask = os.umask(0o077)
try:
    path.write_text("".join(f"{key}={values[key]}\n" for key in keys))
finally:
    os.umask(old_umask)
os.chmod(path, 0o600)
PY
}

up() {
  load_env
  require_value DB_NAME
  require_value DB_USERNAME
  require_value DB_PASSWORD
  require_value GOOGLE_CLIENT_ID
  require_value GOOGLE_CLIENT_SECRET
  require_value AWS_BEARER_TOKEN_BEDROCK
  [[ "${AI_PROVIDER:-bedrock-converse}" == 'bedrock-converse' ]] || fail "Set AI_PROVIDER=bedrock-converse in .env for Amazon Bedrock Converse."
  reject_placeholder GOOGLE_CLIENT_ID
  reject_placeholder GOOGLE_CLIENT_SECRET
  reject_placeholder AWS_BEARER_TOKEN_BEDROCK

  command -v docker >/dev/null 2>&1 || fail "Docker is required. Install Docker Desktop and try again."
  docker info >/dev/null 2>&1 || fail "Docker Desktop is not running. Start it, then retry."
  prepare_encryption_key

  cd "$ROOT_DIR"
  check_port_available 5432 postgres
  check_port_available 27017 mongodb
  check_port_available 8080 qstash
  check_port_available 8081 qstash
  check_port_available 8082 app

  mkdir -p "$ROOT_DIR/.local"
  chmod 700 "$ROOT_DIR/.local"
  if [[ ! -f "$QSTASH_ENV_FILE" ]]; then
    printf 'QSTASH_TOKEN=\nQSTASH_CURRENT_SIGNING_KEY=\nQSTASH_NEXT_SIGNING_KEY=\n' > "$QSTASH_ENV_FILE"
    chmod 600 "$QSTASH_ENV_FILE"
  fi

  printf 'Starting PostgreSQL and waiting for its health check...\n'
  docker compose up -d --wait postgres mongodb
  printf 'Starting local QStash...\n'
  docker compose up -d qstash

  local qstash_logs=''
  local attempt
  for attempt in {1..60}; do
    qstash_logs="$(docker compose logs --no-color --tail=100 qstash 2>/dev/null || true)"
    if printf '%s' "$qstash_logs" | grep -q 'QSTASH_TOKEN=' \
      && printf '%s' "$qstash_logs" | grep -q 'QSTASH_CURRENT_SIGNING_KEY=' \
      && printf '%s' "$qstash_logs" | grep -q 'QSTASH_NEXT_SIGNING_KEY='; then
      if write_qstash_env "$qstash_logs"; then
        break
      fi
    fi
    if [[ "$attempt" -eq 60 ]]; then
      fail "Local QStash did not publish its development credentials. Check it with: docker compose logs qstash"
    fi
    sleep 1
  done

  printf 'MongoDB and PostgreSQL are healthy; starting Spring Boot in Docker...\n'
  docker compose up -d --build --wait app

  local status=''
  for attempt in {1..30}; do
    status="$(curl --silent --show-error --max-time 2 -o /dev/null -w '%{http_code}' http://127.0.0.1:8082/api/health 2>/dev/null || true)"
    [[ "$status" == '200' ]] && break
    sleep 2
  done
  [[ "$status" == '200' ]] || fail "The app did not become healthy. Inspect it with: docker compose logs --tail=100 app"

  printf '\nLocal services are ready:\n'
  printf '  Frontend API: http://localhost:8082\n'
  printf '  OAuth callback: http://localhost:8082/auth/google/callback\n'
  printf '  MongoDB: localhost:27017 (container health checked before app startup)\n'
  printf '  QStash: localhost:8080\n'
  printf '\nStart the UI separately from mail-ai-web with: npm start\n'
  printf 'Stop containers with: docker compose down\n'
}

down() {
  cd "$ROOT_DIR"
  docker compose down
  rm -f "$QSTASH_ENV_FILE"
  printf 'Local containers stopped. Database volumes are preserved.\n'
}

logs() {
  cd "$ROOT_DIR"
  docker compose logs --follow app
}

case "${1:-up}" in
  up) up ;;
  down) down ;;
  logs) logs ;;
  -h|--help|help) usage ;;
  *) usage >&2; exit 2 ;;
esac
