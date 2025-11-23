# PestScout Backend

PestScout is a Spring Boot service for managing scouting sessions, observations, and farm insights. The codebase includes modules for authentication, farm management, analytics, and scouting workflows.

## Offline-first scaffolding

Recent changes introduce offline-ready behaviors for scouting data:

- **Versioned entities**: Entities include optimistic locking via JPA `@Version` plus `updatedAt` timestamps for change detection.
- **Soft deletion**: Records are marked as deleted instead of being removed, enabling clients to reconcile deletions during sync.
- **Idempotent writes**: Observation upserts accept client-generated request IDs to safely retry offline submissions.
- **Change feed**: The scouting service exposes a sync endpoint to return sessions and observations updated since a timestamp, with an option to include soft-deleted rows.

See `docs/offline-sync.md` for endpoint shapes and usage examples.

## Local development

1. Ensure Java 17 and Gradle are available.
2. Run the test suite:

   ```bash
   ./gradlew test
   ```

3. Start the application:

   ```bash
   ./gradlew bootRun
   ```

## Project structure

- `src/main/java/mofo/com/pestscout` – Application source organized by domain (auth, farm, scouting, analytics).
- `src/test/java/mofo/com/pestscout` – Unit tests grouped by domain.
- `docs/` – Design notes, explanations, and operational guidance.

## Contributing

Please add or update tests alongside code changes. For offline-first flows, prefer idempotent operations, clear conflict responses, and time-based sync APIs.
