# Source this file from Bash or Zsh, then run start_app.
# It defines helpers only; starting services is explicit.

if [[ -n "${ZSH_VERSION:-}" ]]; then
  _MAIL_AI_SOURCE_FILE="${(%):-%N}"
else
  _MAIL_AI_SOURCE_FILE="${BASH_SOURCE[0]}"
fi
_MAIL_AI_REPO_ROOT="$(cd -- "$(dirname -- "$_MAIL_AI_SOURCE_FILE")" && pwd -P)"

start_app() {
  "$_MAIL_AI_REPO_ROOT/scripts/local-dev.sh" up
}

# Run Spring Boot on the host against the external development services in .env.
start_dev_stack() {
  "$_MAIL_AI_REPO_ROOT/scripts/dev-stack-app.sh"
}

stop_app() {
  "$_MAIL_AI_REPO_ROOT/scripts/local-dev.sh" down
}

app_logs() {
  "$_MAIL_AI_REPO_ROOT/scripts/local-dev.sh" logs
}
