# mail.ai

Cloud-hosted prototype for creating scheduled Gmail messages from a user request.

## Monorepo layout

- `mail.ai/schedule-service/` — Spring Boot API for creating and listing schedules and jobs.
- `mail.ai/email-worker/` — planned independent Spring Boot service for sending due jobs through Gmail.
- `plan.md` — architecture and scope decisions.
- `progress.md` — implementation status and next steps.

Each service is built and deployed independently. See the service-specific `AGENTS.md` for local conventions.
