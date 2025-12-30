# PestScout Backend

PestScout is a Spring Boot 3 service for managing farms, user access, scouting sessions, and analytics for greenhouse/field operations. The codebase is organized by domain (auth, farm, scouting, analytics, common infrastructure) and ships with offline-first sync semantics so mobile clients can keep working without connectivity.

## Features at a glance
- **Authentication & authorization**: JWT-based login/refresh, role-aware access (super admin, manager/owner, scout), and method-level security enforced by `SecurityConfig`.
- **Farm & licensing**: CRUD for farms with licensing/subscription metadata (licensed area/quota, discounts, expiry, grace/archive periods, Stripe IDs) and controls over who can change those fields.
- **Scouting sessions & observations**: Session targets map to greenhouses/field blocks, observations support optimistic locking, idempotent writes, and soft deletes, and sync endpoints expose changes since a timestamp.
- **Analytics**: Heatmap generation over weekly windows plus severity legends; sessions filtered by farm access and ISO week boundaries.
- **Caching & infrastructure**: Redis-backed caching, Flyway migrations, Swagger UI, actuator health metrics, and file upload support.

## Domain modules
- **Auth (`src/main/java/mofo/com/pestscout/auth`)**
  - `AuthController` exposes login, register, refresh, and current-user endpoints backed by `AuthService` and `UserService`.
  - `SecurityConfig` wires JWT filters, stateless sessions, and public vs. secured routes.
- **Farm (`src/main/java/mofo/com/pestscout/farm`)**
  - `Farm` entity tracks general details plus licensed area/quota, discount percentages, expiry/grace/archive dates, Stripe IDs, and default structure configuration (bay/bench/spot-check counts).
  - Services and DTOs handle creation/updating with role checks (super admins manage licensing/billing fields; managers edit operational metadata).
- **Scouting (`src/main/java/mofo/com/pestscout/scouting`)**
  - Sessions and targets link farms to greenhouses/field blocks, and observations carry severity/count/location details.
  - Offline-ready behaviors: versioned entities, `clientRequestId` idempotency keys, soft deletes, and sync endpoints that return changes since a timestamp (see `docs/offline-sync.md`).
- **Analytics (`src/main/java/mofo/com/pestscout/analytics`)**
  - Heatmap services aggregate observations per session target and farm overview while enforcing farm access rules; docs in `docs/heatmap_logic_explanation.md` and `docs/heatmap_changes.md` explain the grid and severity legend.
- **Common (`src/main/java/mofo/com/pestscout/common`)**
  - Shared configuration (caching), error handling, DTOs, and utilities. `CacheAdminController` provides endpoints to clear farm/user caches.

## Architecture notes
- **Spring Boot** 3.5 with Java 21 and Spring Data JPA for persistence; Flyway handles schema migrations under `src/main/resources/db/migration`.
- **Security**: JWT authentication with BCrypt password hashing and stateless sessions. Public routes: `/api/auth/login`, `/api/auth/register`, `/api/auth/refresh`, `/api-docs/**`, `/swagger-ui/**`, `/actuator/**`.
- **Data model**: Base entities include audit columns and optimistic locking. Farms own greenhouses/field blocks; sessions attach to farms and targets; observations reference session targets.
- **Caching**: Redis caching via `RedisCacheConfig` for farm metadata, users, and permissions.
- **Validation**: Jakarta Bean Validation on DTOs; global error responses use `ErrorResponse`.

## Running locally
1. **Prerequisites**: Java 21, Gradle (wrapper included), PostgreSQL (defaults to `localhost:5433/pestscan_scouting`), and Redis (`localhost:6379`). Override credentials via environment variables in `src/main/resources/application.yml`.
2. **Database**: Ensure the database exists and run Flyway migrations automatically on startup.
3. **Tests**: Run the suite with:
   ```bash
   ./gradlew test
   ```
4. **Start the API**:
   ```bash
   ./gradlew bootRun
   ```
5. **API docs**: Swagger UI is available at `http://localhost:8080/swagger-ui.html` with OpenAPI JSON at `/api-docs`.

## Offline sync summary
- Observations support idempotent upserts via `clientRequestId`, optimistic locking with `version`, and soft deletion.
- Sync endpoint: `GET /scouting/farms/{farmId}/sync?since=<ISO timestamp>&includeDeleted=<boolean>` returns sessions/observations updated since the timestamp, optionally including deletions.
- Conflict responses use HTTP 409 for stale versions or duplicate `clientRequestId` across sessions. See `docs/offline-sync.md` for request/response shapes and client recommendations.

## Repository layout
- `src/main/java/mofo/com/pestscout` – Domain packages (`auth`, `farm`, `scouting`, `analytics`, `common`).
- `src/test/java/mofo/com/pestscout` – Unit and integration tests parallel to main packages.
- `docs/` – Design notes (offline sync, heatmap logic/changes, exception handling).
- `build.gradle` – Dependencies (Spring Boot, Security, JPA, Redis, Swagger/OpenAPI, Flyway, PostgreSQL driver) and Java toolchain settings.

## Contributing
- Add or update tests alongside code changes.
- Prefer idempotent operations and explicit conflict responses for offline scenarios.
- Keep Swagger annotations up to date so the API surface remains discoverable.
