# mail.ai

Cloud-hosted prototype for scheduling Gmail messages.

## Layout

- `mail.ai/server/` — single Spring Boot application for the user-facing schedule API and the protected QStash send callback.
- `plan.md` — architecture and scope decisions.
- `progress.md` — implementation status and next steps.

The user-facing API and the internal send handler are separate application components and use distinct authentication policies, but deploy together for the prototype.
