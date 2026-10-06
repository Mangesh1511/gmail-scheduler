# Mail.ai Monorepo Instructions

- This repository is a monorepo. The prototype currently has one Spring Boot deployment under `mail.ai/server/`; preserve logical package boundaries for schedule APIs and internal send-job handling.
- Keep shareable architecture and interview-facing design documentation in `README.md` and `docs/design.md`. Do not add private progress logs or planning notes to the repository unless requested.
- Current backend stack: Java 21 and Spring Boot. Prefer the owner's familiar stack unless a concrete integration blocker requires otherwise.
- Never commit OAuth tokens, API keys, passwords, or secret-bearing environment files. Keep secrets in deployment secret stores and provide only `.env.example` placeholders when needed.
- Keep service-to-service boundaries explicit and use authenticated HTTP calls/verified callbacks. User-owned Gmail tokens must remain server-side and be associated with the correct application user.
