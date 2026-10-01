# Schedule Service Instructions

- Java 21 and Spring Boot 3.5.x.
- Keep HTTP request/response types in `api`, persistence models and repositories in `domain`, and orchestration in `service`.
- Validate every request on the server. Never trust an owner ID supplied in the request body; replace the current development-only `X-User-Id` header with authenticated principal identity before exposing the service.
- Persist recurrence timestamps as UTC instants; recurrence intervals are elapsed seconds, with a 7-day maximum horizon and a minimum 3600-second interval.
- QStash and Gmail integrations are not implemented yet. Do not put credentials into request or job payloads.
- Add focused tests when behavior changes.
