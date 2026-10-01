# Mail.ai Monorepo Instructions

- This repository is a monorepo. Keep separately deployable applications under `mail.ai/` (for example, `schedule-service/` and `email-worker/`).
- Keep shared architecture decisions and project progress in root `plan.md` and `progress.md`.
- Each service owns its build file, runtime configuration, and service-specific `AGENTS.md` where useful. Services may be deployed independently.
- Current backend stack: Java 21 and Spring Boot. Prefer the owner's familiar stack unless a concrete integration blocker requires otherwise.
- Never commit OAuth tokens, API keys, passwords, or secret-bearing environment files. Keep secrets in deployment secret stores and provide only `.env.example` placeholders when needed.
- Keep service-to-service boundaries explicit and use authenticated HTTP calls/verified callbacks. User-owned Gmail tokens must remain server-side and be associated with the correct application user.
