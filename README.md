# PestScout Backend

PestScout is a Spring Boot 3 backend for greenhouse and field scouting operations. It manages authentication, farm
tenancy, hectare-based licensing, scouting sessions, analytics, offline-first sync, and a set of optional capabilities
that can be enabled globally or per farm.

This README is intended to help a new engineer, operator, or technical stakeholder understand:

- what the system does
- how the main domains fit together
- how licensing and feature flags work
- which endpoints exist and what they are for
- how data moves through the platform
- how to run and modify the project locally

## Table of contents

1. Project summary
2. Who uses the system
3. High-level architecture
4. Core domain model
5. Licensing and commercial rules
6. Optional capabilities and feature flags
7. Main workflows
8. API map
9. Configuration
10. Local development
11. Docker
12. Repository map
13. Supporting docs
14. Known limitations and current implementation notes

## Project summary

At a high level, PestScout supports the full flow of a licensed scouting product:

- a `SUPER_ADMIN` creates and licenses farms
- a farm owner or manager configures structures such as greenhouses and field blocks
- a manager creates scouting sessions and assigns scouts
- scouts collect observations and photos, including offline-friendly updates
- the backend aggregates those records into dashboards, heatmaps, trends, and reports
- optional advanced modules can be turned on per deployment and per farm

The backend is organized into these top-level domains:

- `auth`: login, JWT, user administration, password reset, membership
- `farm`: farms, licensing, structures, farm-level settings
- `scouting`: sessions, observations, photos, audit events, sync
- `analytics`: dashboards, trends, heatmaps, reports
- `optional`: AI/drone/prediction/recommendation/order features
- `common`: shared config, caching, errors, utilities

## Who uses the system

### `SUPER_ADMIN`

System-level operator. This role can:

- create farms
- generate and update licenses
- inspect license history
- manage per-farm feature overrides
- see all farms regardless of normal license visibility rules

### `FARM_ADMIN`

Farm-level administrator. This role can:

- manage operational farm data
- access dashboards and analytics while the farm is licensed or in the dashboard-only post-expiry window
- use optional capabilities when both the feature flag and active license allow it
- download retained raw data after expiry

### `MANAGER`

Operational farm user. This role can:

- manage sessions and review scouting data
- access analytics under the same license rules as `FARM_ADMIN`
- use optional capabilities when enabled and licensed
- receive expiry reminder notifications for retained data access

### `SCOUT`

Field data-collection user. This role can:

- start, submit, and edit assigned sessions
- create or update observations
- work with offline-friendly sync data

Scouts do not use the manager analytics/dashboard views.

### `EDGE_SYNC`

Headless service role used for edge-to-cloud sync endpoints. This is not an interactive user role.

## High-level architecture

### Runtime stack

- Java 25
- Spring Boot 3.5
- Spring Security with JWT-based stateless authentication
- Spring Data JPA with PostgreSQL
- Flyway for schema migrations
- Spring Cache with either in-memory cache or Redis-backed caching
- SpringDoc / Swagger UI for API discovery

### Storage and infrastructure

- PostgreSQL stores the system of record
- Redis is optional and is used as a cache backend, not as a Spring Data repository store
- S3-compatible storage or MinIO can be used for photo upload flows
- scheduled jobs are enabled and currently used for edge-sync detection and license-expiry notification queuing

### Request flow

For most business operations, the request path is:

1. Controller receives an authenticated request.
2. Service layer resolves the acting user and farm access.
3. Licensing and feature-gate checks are applied where needed.
4. Domain repositories load or persist entities.
5. Cache eviction happens after mutating operations where needed.
6. DTOs are returned to the client.

### Main enforcement layers

- `SecurityConfig`: global authentication and route protection
- farm/analytics/optional access services: tenant and role enforcement
- `LicenseService`: commercial access rules
- `FeatureAccessService`: optional capability rollout rules

## Core domain model

### Farm

`Farm` is the main tenant boundary. It stores:

- identity and contact metadata
- owner and assigned scout references
- subscription and license fields
- structure defaults
- location metadata
- commercial visibility and archival state

Important farm-related ideas:

- licensing is hectare-based
- structures must carry real `areaHectares` values for correct license enforcement
- a farm can outlive license expiry; data is retained even when dashboards are hidden

### Greenhouse and FieldBlock

These represent physical target structures inside a farm.

They store:

- name and descriptive metadata
- bay/bench structure counts
- normalized tag lists
- `areaHectares`

`areaHectares` is operationally important because session area is validated against licensed hectares.

### User and UserFarmMembership

Users are global identities. Membership rows attach users to farms with farm-specific roles where needed.

This supports:

- farm managers belonging to one or more farms
- per-farm access checks
- manager/admin recipient resolution for license expiry reminders

### ScoutingSession

A scouting session is the operational unit of work. It stores:

- farm, manager, and scout references
- target structures
- crop and environmental metadata
- status and timestamps
- recommendations
- sync metadata

Current session lifecycle:

- `DRAFT`
- `IN_PROGRESS`
- `SUBMITTED`
- `COMPLETED`
- `REOPENED`
- `INCOMPLETE`
- `CANCELLED` exists for locking logic, though the primary workflow is draft -> in progress -> submitted -> completed

### ScoutingObservation

An observation is a row-level record of what was seen at a specific grid cell.

It stores:

- species code
- derived category
- bay, bench, and spot coordinates
- count and notes
- optimistic locking version
- optional `clientRequestId` for idempotent sync

### ScoutingPhoto

Photos are attached to sessions and optionally to observations. The system supports:

- metadata registration
- upload via external object storage
- confirmation after upload

### Heatmaps and analytics DTOs

Analytics are computed from stored sessions and observations. The main analytics surfaces are:

- dashboard summary
- full dashboard aggregation
- weekly and monthly heatmaps
- pest and severity trends
- distribution summaries
- recommendations and alerts
- raw-data export PDF

### Feature entitlements

Optional capabilities are governed by:

- deployment-level config in `app.features`
- default allowed subscription tiers
- optional per-farm overrides in `farm_feature_entitlements`

### License history

Every license generation, update, and expiry-notice queue event is recorded in `farm_license_history`.

This exists so operators can answer:

- what the farm was licensed for
- when it changed
- who changed it
- when the expiry reminder workflow ran

## Licensing and commercial rules

### Why licensing is separate from feature flags

Feature flags answer: "Should this capability be available for this farm from a product rollout perspective?"

Licensing answers: "Is this farm commercially entitled to use operational features right now?"

A farm needs both when a capability is license-sensitive.

### License fields that matter

The commercial lifecycle is based on:

- `licenseType`: `TRIAL` or `PAID`
- `licenseStartDate`
- `licenseExtensionMonths`
- `licenseExpiryDate`
- `licenseGracePeriodEnd`
- `licenseArchivedDate`
- `licenseExpiryNotificationSentAt`

The practical "license end date" is `licenseExpiryDate`.

### Trial and paid terms

The implemented defaults are:

- trial: 3 months
- maximum trial extension: 3 more months
- paid: 12 months
- maximum paid extension: 6 more months

These values are configurable under `app.licensing`.

### What happens at expiry

When a farm reaches `licenseExpiryDate`:

- session creation and other operationally licensed features stop
- optional capabilities that depend on active licensing stop
- dashboards remain visible to `FARM_ADMIN` and `MANAGER` during a limited post-expiry window
- raw data is retained

When the dashboard visibility window ends:

- dashboards are hidden from farm managers/admins
- the farm data is still not deleted
- super admins can still inspect farm state for administration

### Hectare enforcement

License enforcement is done using real area values.

For a session:

1. selected structures are resolved
2. the requested area is calculated from structure `areaHectares`
3. if only some bays are selected, the area is allocated proportionally
4. the result is checked against licensed hectares

Session creation fails if:

- the license is expired or archived
- the farm is not commercially active
- the requested area exceeds the licensed area
- a targeted greenhouse or field block does not have `areaHectares`

### Dashboards versus operational access

This distinction matters:

- operational access requires an active license
- dashboard access may continue briefly after expiry

That is why the code has separate policy methods for:

- operational access
- dashboard access

### Expiry reminders and offboarding

An expiry reminder workflow exists for farms that have expired but are still inside the dashboard-only visibility
window.

Current behavior:

- the scheduler scans farms
- if a farm is expired and the reminder has not been queued yet, the system records a reminder event
- recipient emails are resolved from the owner plus active farm admin/manager memberships
- a raw-data PDF download URL is included in the queued reminder

Important limitation:

- the system currently queues and logs the reminder flow
- it does not yet send real outbound email through SMTP, SES, or SendGrid

### Raw-data PDF export

The retained data export endpoint is:

- `GET /api/farms/{farmId}/license/raw-data-export.pdf`

This export is intentionally raw-data focused:

- session metadata
- observation rows
- license metadata

It does not contain dashboard charts or graph renderings.

## Optional capabilities and feature flags

The optional capability framework supports deployment-wide rollout plus per-farm control.

### Implemented optional capabilities

| Capability                               | Feature key                           | Global default | Default tiers                  | Primary endpoint(s)                                                                                                                                                       |
|------------------------------------------|---------------------------------------|----------------|--------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| AI pest identification                   | `ai-pest-identification`              | Off            | `PREMIUM`                      | `GET /api/optional-capabilities/ai-pest-identification/photos/{photoId}`                                                                                                  |
| Drone or aerial image processing         | `drone-image-processing`              | Off            | `PREMIUM`                      | `POST /api/optional-capabilities/drone-image-processing/analyze`                                                                                                          |
| Predictive modeling                      | `predictive-modeling`                 | Off            | `PREMIUM`                      | `GET /api/optional-capabilities/predictive-modeling/forecast`                                                                                                             |
| Automated PDF reports                    | `automated-pdf-reports`               | On             | `BASIC`, `STANDARD`, `PREMIUM` | `POST /api/analytics/reports/export`                                                                                                                                      |
| GIS heatmaps or advanced mapping layers  | `gis-heatmaps`                        | Off            | `PREMIUM`                      | `GET /api/optional-capabilities/gis-heatmaps/layers`                                                                                                                      |
| Automated treatment recommendations      | `automated-treatment-recommendations` | Off            | `STANDARD`, `PREMIUM`          | `GET /api/optional-capabilities/automated-treatment-recommendations`                                                                                                      |
| Integrated purchasing or supply ordering | `supply-ordering`                     | Off            | `PREMIUM`                      | `GET /api/optional-capabilities/supply-ordering/draft`, `POST /api/optional-capabilities/supply-ordering/orders`, `GET /api/optional-capabilities/supply-ordering/orders` |

### How feature resolution works

Feature availability is resolved in three layers:

1. global config in `application.yml`
2. farm-level override in the database
3. farm subscription tier when no override exists

The effective rule is:

1. if `app.features.<feature>.enabled` is `false`, the feature is off for every farm
2. if the feature is globally enabled and a farm override exists, the override wins
3. if the feature is globally enabled and no farm override exists, `allowed-tiers` decides access

### Feature flags and licensing together

Feature flags are not enough on their own.

For most advanced capabilities:

- the feature must be enabled
- the farm must be in an allowed tier
- the farm must be operationally licensed

So a farm can still see dashboards during the post-expiry window but still be blocked from:

- AI pest identification
- drone analysis
- predictive modeling
- treatment recommendations
- supply ordering

Important exception:

- automated PDF report export is analytics-facing rather than operationally licensed
- it follows feature-flag rules plus dashboard visibility rules
- that means it can still be available during the post-expiry dashboard window if the feature is enabled

### Global enablement

Global defaults live in `src/main/resources/application.yml` under `app.features`.

Example:

```yml
app:
  features:
    ai-pest-identification:
      enabled: true
      allowed-tiers:
        - PREMIUM
    automated-treatment-recommendations:
      enabled: true
      allowed-tiers:
        - STANDARD
        - PREMIUM
```

To turn a feature off everywhere:

```yml
app:
  features:
    supply-ordering:
      enabled: false
```

### Per-farm enablement and disablement

Super admins can inspect and pin feature status per farm through:

- `GET /api/farms/{farmId}/features`
- `PUT /api/farms/{farmId}/features/{featureKey}`

Example request body:

```json
{
  "enabled": true
}
```

Example:

```bash
curl -X PUT "http://localhost:8080/api/farms/{farmId}/features/ai-pest-identification" \
  -H "Authorization: Bearer <super-admin-jwt>" \
  -H "Content-Type: application/json" \
  -d "{\"enabled\":true}"
```

The feature status response exposes:

- `globalEnabled`
- `tierAllowed`
- `overrideEnabled`
- `effectiveEnabled`

## Main workflows

### 1. Super admin creates and licenses a farm

Typical flow:

1. create the farm
2. create or update structures
3. set license type, start date, and extension months
4. generate a license reference if needed
5. optionally override per-farm feature flags

Key endpoints:

- `POST /api/farms`
- `PUT /api/farms/{farmId}`
- `POST /api/farms/{farmId}/license/generate`
- `PUT /api/farms/{farmId}/license`
- `PUT /api/farms/{farmId}/features/{featureKey}`

### 2. Manager prepares operational data

Typical flow:

1. view farm metadata
2. create greenhouses or field blocks
3. ensure `areaHectares` is populated on structures
4. configure scouting targets and team memberships

Why `areaHectares` matters:

- without it, session license validation cannot determine requested area

### 3. Manager creates a scouting session

Typical flow:

1. select the farm
2. select structures and optional bay/bench subsets
3. assign a scout
4. save the session as `DRAFT` or `NEW`

At creation time the backend validates:

- farm admin/manager access
- active commercial license
- hectare entitlement

### 4. Scout collects observations

Typical flow:

1. start session
2. upsert single observations or bulk upload them
3. attach or confirm photos
4. submit session

The system supports:

- optimistic locking
- offline-safe updates
- idempotent client request keys
- soft-delete semantics for sync

### 5. Manager reviews and completes the session

Typical flow:

1. review a submitted session
2. complete it after confirmation
3. reopen it if corrections are needed

### 6. Analytics consumption

Managers and farm admins can:

- view summary dashboards
- view full aggregated dashboards
- fetch trends and heatmaps
- view monthly reports
- export reports

These analytics are license-aware:

- active license: full analytics access
- expired but within dashboard window: dashboards still visible
- beyond visibility window: dashboards hidden for farm users

### 7. Edge/offline sync

The platform includes sync-specific support for mobile and edge deployments:

- changes since timestamp queries
- idempotent observation writes
- dedicated cloud sync endpoints
- edge runtime mode configuration

The scheduler currently detects pending edge items and logs the need for transport; it does not yet implement a full
transport client.

### 8. Expired farm offboarding

Current intended offboarding behavior:

1. the license expires
2. operational features stop
3. dashboards remain visible for a limited period
4. a reminder workflow is queued for owner/manager recipients
5. recipients are directed to a raw-data PDF export
6. after the visibility window, dashboards are hidden but data remains stored

## API map

This is the practical API grouping a new engineer should know.

### Authentication and users

Base path: `/api/auth`

Examples:

- `/login`
- `/register`
- `/refresh`
- `/forgot-password`
- `/reset-password`
- `/me`
- `/users`

### Farms, structures, and licensing

Base paths:

- `/api/farms`
- `/api/farms/{farmId}/license`
- `/api/farms/{farmId}/features`
- `/api/farms/{farmId}/heatmap`
- `/api/farms/{farmId}/greenhouses`
- `/api/farms/{farmId}/field-blocks`

### Scouting

Base paths:

- `/api/scouting/sessions`
- `/api/scouting/photos`

Important session operations:

- create
- update
- start
- submit
- complete
- reopen
- add observations
- bulk add observations
- delete observations
- list sessions
- sync sessions
- fetch audits

### Analytics

Base paths:

- `/api/analytics/dashboard`
- `/api/analytics/dashboard/full`
- `/api/analytics/heatmap`
- `/api/analytics/trend`
- `/api/analytics/reports`

### Optional capabilities

Base path:

- `/api/optional-capabilities`

### Edge sync

Base path:

- `/api/cloud/sync`

### Cache admin

Base path:

- `/api/admin/cache`

Swagger UI is available at:

- `http://localhost:8080/swagger-ui.html`

OpenAPI JSON is exposed at:

- `/api-docs`

## Configuration

### Database

Configured under `spring.datasource`.

Default local values:

- host: `localhost`
- port: `5433`
- database: `pestscan_scouting`
- username: `postgres`
- password: `admin`

### Cache mode

Configured under `spring.cache.type`.

Useful modes:

- `simple`: in-memory cache, easiest for local development
- `redis`: Redis-backed cache

Redis repository scanning is disabled intentionally because Redis is only being used as a cache backend here.

### Runtime mode

Configured through:

- `app.runtime.mode`
- `APP_RUNTIME_MODE`

Supported modes:

- `CLOUD` (default)
- `EDGE`

### Licensing policy

Configured under `app.licensing`.

Important keys:

- `trial-months`
- `max-trial-extension-months`
- `paid-months`
- `max-paid-extension-months`
- `dashboard-visibility-days-after-expiry`
- `notification-cron`
- `public-base-url`

`APP_PUBLIC_BASE_URL` should be set to the externally reachable backend URL if reminder download links need to work
outside local development.

### Feature flags

Configured under `app.features`.

Each feature has:

- `enabled`
- `allowed-tiers`

### Edge sync config

Configured under `app.edge.sync`.

Important keys:

- `enabled`
- `token`
- `company-number`
- `edge-node-id`
- `interval-ms`

### S3 / MinIO

Configured under `aws.s3`.

Used for photo upload flows when exercising storage-backed image workflows.

## Local development

### Prerequisites

- Java 25
- PostgreSQL
- optional Redis
- optional MinIO or S3-compatible storage

### Start the backend

```bash
./gradlew bootRun
```

### Run tests

```bash
./gradlew test
```

### Build the application jar

```bash
./gradlew bootJar
```

### Common local-development notes

- the default cache mode is `simple`, so Redis is not required for a basic local run
- Java 25 requires the Gradle 9.1 wrapper already committed in this repo
- Flyway runs migrations at startup
- Swagger is enabled locally

## Docker

The repository includes:

- `Dockerfile`
- `docker-compose.yml`

Build the image:

```bash
docker build -t pestscan-app .
```

Start the local stack:

```bash
docker compose up -d
```

Default ports:

- app: `8080`
- postgres: `5433`
- redis: `6379`

If you want Redis-backed caching in Docker, add `SPRING_CACHE_TYPE=redis` to the app container environment.

## Repository map

### Main source layout

- `src/main/java/mofo/com/pestscout/auth`
- `src/main/java/mofo/com/pestscout/farm`
- `src/main/java/mofo/com/pestscout/scouting`
- `src/main/java/mofo/com/pestscout/analytics`
- `src/main/java/mofo/com/pestscout/optional`
- `src/main/java/mofo/com/pestscout/common`
- `src/main/resources/db/migration`
- `src/test/java/mofo/com/pestscout`

### Good starting points for new engineers

If you are new to the repo, read in roughly this order:

1. `PestscoutApplication`
2. `SecurityConfig`
3. `FarmController`, `FarmLicenseController`, `FarmFeatureController`
4. `ScoutingSessionController` and `ScoutingSessionService`
5. `DashboardService`, `DashboardAggregatorService`, `ReportingService`
6. `LicenseService` and `FarmLicenseService`
7. `FeatureAccessService`
8. `OptionalCapabilityController` and optional services

### Important classes by concern

Authentication:

- `SecurityConfig`
- `JwtAuthenticationFilter`
- `JwtTokenProvider`
- `AuthController`
- `AuthService`

Licensing:

- `LicenseService`
- `FarmLicenseService`
- `FarmLicenseController`
- `LicenseExpiryNotificationService`
- `LicenseLifecycleScheduler`

Feature flags:

- `FeatureKey`
- `FeatureProperties`
- `FeatureAccessService`
- `FarmFeatureController`

Scouting:

- `ScoutingSessionService`
- `ScoutingPhotoController`
- `SessionAuditService`

Analytics:

- `AnalyticsAccessService`
- `DashboardService`
- `DashboardAggregatorService`
- `HeatmapService`
- `TrendAnalysisService`
- `ReportingService`
- `RawDataPdfExportService`

Optional capabilities:

- `OptionalCapabilityAccessService`
- `OptionalCapabilityService`
- `TreatmentRecommendationEngine`
- `SupplyOrderingService`

## Supporting docs

The `docs/` folder contains deeper design notes for specific subsystems:

- `offline-sync.md`: sync semantics and payload guidance
- `edge-sync-guide.md`: edge sync payloads and operational notes
- `edge-backend-summary.md`: edge backend checklist
- `heatmap_logic_explanation.md`: heatmap generation concepts
- `heatmap_changes.md`: heatmap implementation notes
- `exception-handling.md`: API error behavior
- `frontend-feature-guide.md`: frontend-facing expectations

## Known limitations and current implementation notes

These are important for new people so they do not assume more is implemented than actually exists.

### Email delivery is not fully integrated

The license-expiry reminder flow exists, but real outbound delivery still needs a provider integration such as:

- SMTP
- Amazon SES
- SendGrid

Today the system queues and logs the reminder intent and records it in license history.

### Raw-data export is intentionally simple

The offboarding export is a plain PDF containing raw data and metadata. It is not a chart-rich management report.

### Optional capabilities are MVP implementations

Current optional capability implementations are mostly heuristic or rule-based:

- AI identification is local heuristic logic
- drone analysis is heatmap-correlated logic
- predictive modeling is weighted trend extrapolation
- treatment recommendations are rule-based
- supply ordering is recommendation aggregation

They are structured so external AI or vendor integrations can replace the internals later.

### Edge transport is not fully implemented

The edge scheduler currently detects pending work and logs it. A full outbound transport client is still future work.

### Redis is optional

Redis warnings about repository assignment should not appear in normal configuration because Redis repository scanning
is disabled. Redis remains optional unless you explicitly choose Redis cache mode.

## Development notes

- Java toolchain is pinned to Java 25 in `build.gradle`
- Gradle wrapper is pinned to Gradle 9.1
- add or update tests alongside code changes
- keep Swagger/OpenAPI annotations and docs aligned with behavior changes
- prefer documenting business-rule changes in both code and README when they affect operators or the frontend
