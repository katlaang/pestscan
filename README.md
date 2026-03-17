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
7. Image analysis and review
8. Main workflows
9. API map
10. Configuration
11. Local development
12. Docker
13. Repository map
14. Supporting docs
15. Known limitations and current implementation notes

## Project summary

At a high level, PestScout supports the full flow of a licensed scouting product:

- the platform is bootstrapped with exactly one initial `SUPER_ADMIN`
- a `SUPER_ADMIN` creates and licenses farms
- a farm owner or manager configures structures such as greenhouses and field blocks
- a manager creates scouting sessions and assigns scouts
- scouts collect observations and photos, including offline-friendly updates
- scouting photos can be analyzed as a core workflow with manual review and accuracy tracking
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

- create the very first super admin through the one-time bootstrap endpoint when the system has none
- create additional `SUPER_ADMIN` profiles after logging in as an existing super admin
- create any user with a temporary password that remains valid for 5 days
- trigger the password-setup email that tells the invited user to replace that temporary password
- reactivate invited users whose temporary password window expired
- create farms
- generate licenses
- activate licenses by setting `subscriptionStatus`
- set license activation dates through `licenseStartDate`
- update license schedules, extensions, expiry dates, grace periods, archive dates, and billing metadata
- inspect license history
- manage per-farm feature overrides
- view, search, update, and disable users across all farms
- access all farm, scouting, analytics, image-analysis, and optional-capability data in the application
- see all farms regardless of normal license visibility rules
- clear and inspect caches through the admin cache endpoints

This is the top interactive role in the platform. In practical terms, `SUPER_ADMIN` is the only role that can issue or
activate commercial licensing, and it has global visibility into application data across all farms.

### Super-admin bootstrap and gating

The system enforces a two-stage super-admin creation model:

1. before any super admin exists, the public bootstrap endpoint may create exactly one initial `SUPER_ADMIN`
2. after that first super admin exists, the bootstrap endpoint is permanently closed and additional super admins can
   only be created by an authenticated existing super admin

The relevant routes are:

- `GET /api/auth/bootstrap/super-admin/status`
- `POST /api/auth/bootstrap/super-admin`
- `POST /api/auth/users`

Important rules:

- `POST /api/auth/bootstrap/super-admin` succeeds only when no super admin exists yet
- public self-registration through `POST /api/auth/register` does not allow `SUPER_ADMIN`
- once the first super admin is created, only a logged-in super admin may create more super admins
- when a super admin creates any user through `POST /api/auth/users`, the submitted password is treated as a temporary
  password
- temporary passwords are valid for 5 days and the backend also queues an account-setup email with a password-reset
  token
- invited users are expected to reset their own password through `POST /api/auth/reset-password`
- if the user does not reset that password within the 5-day window, the profile is soft-deleted and marked for
  reactivation
- only a logged-in `SUPER_ADMIN` may restore that profile through `POST /api/auth/users/{userId}/reactivate`
- super admins are global users and are not farm-scoped

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
- session-level default photo source selection, with per-photo override when needed
- core pest/disease image analysis for registered photos
- explicit AI-versus-manual comparison for each analyzed photo
- manual review of model output and farm-level accuracy metrics
- explicit photo source tracking, including scout handheld and drone captures

Detailed behavior for source selection, analysis execution, manual review, and accuracy reporting is described in the
`Image analysis and review` section below.

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

### Who controls license generation and activation

License generation and license activation are deliberately centralized under `SUPER_ADMIN`.

That means:

- only `SUPER_ADMIN` can generate a license reference
- only `SUPER_ADMIN` can move a farm from `PENDING_ACTIVATION` to `ACTIVE`
- only `SUPER_ADMIN` can set or change the activation date through `licenseStartDate`
- only `SUPER_ADMIN` can change license type, extension months, expiry dates, grace-period dates, archive dates, and
  other commercial schedule fields

Operationally, a farm manager or farm admin may manage normal farm data, but they do not own the commercial lifecycle
of the license.

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

Super admins also continue to have global access to application data for administration, support, compliance, and
commercial operations. They are not limited by normal farm membership rules when reading platform data.

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
| Drone or aerial image processing         | `drone-image-processing`              | Off            | `PREMIUM`                      | `POST /api/optional-capabilities/drone-image-processing/analyze`                                                                                                          |
| Predictive modeling                      | `predictive-modeling`                 | Off            | `PREMIUM`                      | `GET /api/optional-capabilities/predictive-modeling/forecast`                                                                                                             |
| Automated PDF reports                    | `automated-pdf-reports`               | On             | `BASIC`, `STANDARD`, `PREMIUM` | `POST /api/analytics/reports/export`                                                                                                                                      |
| GIS heatmaps or advanced mapping layers  | `gis-heatmaps`                        | Off            | `PREMIUM`                      | `GET /api/optional-capabilities/gis-heatmaps/layers`                                                                                                                      |
| Automated treatment recommendations      | `automated-treatment-recommendations` | Off            | `STANDARD`, `PREMIUM`          | `GET /api/optional-capabilities/automated-treatment-recommendations`                                                                                                      |
| Integrated purchasing or supply ordering | `supply-ordering`                     | Off            | `PREMIUM`                      | `GET /api/optional-capabilities/supply-ordering/draft`, `POST /api/optional-capabilities/supply-ordering/orders`, `GET /api/optional-capabilities/supply-ordering/orders` |

General scouting photo analysis is part of the core scouting workflow and is exposed under `/api/scouting/photos`.
The legacy optional AI-photo route remains only as a compatibility shim.

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
    drone-image-processing:
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
curl -X PUT "http://localhost:8080/api/farms/{farmId}/features/drone-image-processing" \
  -H "Authorization: Bearer <super-admin-jwt>" \
  -H "Content-Type: application/json" \
  -d "{\"enabled\":true}"
```

The feature status response exposes:

- `globalEnabled`
- `tierAllowed`
- `overrideEnabled`
- `effectiveEnabled`

## Image analysis and review

This section is intentionally detailed because image analysis now spans core scouting behavior, manual QA, and one
optional drone-specific path. New engineers should read this section before changing photo registration, mobile sync,
or AI-related endpoints.

### Core versus optional image analysis

The system now treats image analysis in two different ways:

- core scouting photo analysis:
  standard pest and disease analysis for normal scouting photos is part of the core scouting workflow
- optional drone image analysis:
  aerial or drone processing remains an optional capability controlled through feature flags and licensing

This split is deliberate.

Core scouting photo analysis exists because a normal scout taking close-up or handheld pictures is a standard product
workflow, not an upsell. The user should not lose the ability to analyze normal scouting photos because a feature flag
was turned off.

Drone image analysis remains optional because it usually implies:

- premium capture hardware
- different operating workflows
- potentially different licensing or commercial packaging
- broader-area correlation rather than single-leaf close-up diagnosis

### The system does not infer photo source from the image pixels

This is the most important design point.

The backend does **not** currently inspect EXIF metadata, camera hardware signatures, or the image content itself to
guess whether a photo came from a drone or from a scout's phone.

Instead, the source type is provided as metadata by the client application and persisted in two layers:

1. session-level default source
2. photo-level source

The effective rule is:

1. when a session is created, the client may set `defaultPhotoSourceType`
2. when a photo is registered, the client may set `sourceType`
3. if the photo request contains `sourceType`, that value wins
4. if the photo request omits `sourceType`, the backend uses the session's `defaultPhotoSourceType`
5. if neither value is present, the backend falls back to `SCOUT_HANDHELD`

This means the expected UX is:

- if the whole session is a normal scout walkthrough, set `defaultPhotoSourceType=SCOUT_HANDHELD` once at session start
- if the whole session is a drone capture session, set `defaultPhotoSourceType=DRONE` once at session start
- if most images are handheld but one or two are drone-derived, register those photos with `sourceType=DRONE`
- if most images are drone-derived but one close-up was captured manually, override just that photo

This design keeps the backend deterministic. It avoids hidden "magic" behavior where the source type silently changes
because some heuristic guessed wrong.

### Source types

Current source types are:

- `SCOUT_HANDHELD`
- `DRONE`
- `OTHER`

Operational meaning:

- `SCOUT_HANDHELD`: normal close-up or mid-range scouting photos captured by a human using a phone, tablet, or similar
  device
- `DRONE`: aerial, ortho, canopy, or other drone/UAV-derived captures
- `OTHER`: edge cases where the client knows the image is neither standard handheld nor drone, but still wants to
  register it

### Data model for image analysis

Image analysis spans three related storage concepts.

#### 1. `ScoutingSession`

The session now carries `defaultPhotoSourceType`.

Purpose:

- define the expected capture mode at the beginning of the session
- reduce repeated client metadata submission for every photo
- make UI behavior simpler for mobile/edge clients

Practical effect:

- if a scout starts a normal field session, the client can set the session default to `SCOUT_HANDHELD`
- if a manager starts an aerial imagery session, the client can set the session default to `DRONE`

#### 2. `ScoutingPhoto`

Each photo stores `sourceType`.

Purpose:

- persist the actual source assigned to that photo
- allow per-photo override when the session default is not enough
- let downstream analysis behave differently for handheld versus drone imagery

Important distinction:

- `defaultPhotoSourceType` is session intent
- `sourceType` is the effective source of the specific stored photo

#### 3. `ScoutingPhotoAnalysis`

This is the persisted analysis record for one photo.

It stores:

- which photo was analyzed
- which provider produced the result
- which model version was used
- a textual summary
- whether manual review is required
- the top predicted species code and confidence
- the review status
- the manually reviewed or corrected species code
- reviewer identity and notes

This record exists because the system must preserve both:

- what the AI predicted
- what the human reviewer later confirmed or corrected

Without that persistence, there is no reliable way to calculate model accuracy over time.

#### 4. `scouting_photo_analysis_candidates`

This table stores the ranked candidate predictions for a single analysis run.

Purpose:

- preserve the top few model options, not just the winner
- expose rationale to the UI
- support later auditing of "the model almost chose X but human reviewers consistently picked Y"

### End-to-end photo analysis flow

The full handheld-photo flow is:

1. create or update a scouting session with `defaultPhotoSourceType`
2. register a photo for that session
3. upload the actual file to object storage
4. confirm the upload with the resulting object key
5. run photo analysis for that registered photo
6. optionally review and correct the result
7. inspect farm-level accuracy metrics across reviewed photos

Each phase is separate on purpose.

The system does not analyze a photo before it has a persisted photo record. Analysis is tied to the `ScoutingPhoto`
entity rather than to an anonymous binary upload.

### Session creation and source defaults

When a session is created, the client should set `defaultPhotoSourceType` according to the expected capture mode.

Example handheld session:

```json
{
  "farmId": "0f1d7dd7-6a8a-4c8a-9f31-9d0d4fef2c1a",
  "scoutId": "04c9a9db-c9e6-4ef6-a2b7-2f8a0bcf07ef",
  "targets": [
    {
      "greenhouseId": "1b6a3d3f-bd7a-4572-9e8d-6d90a4b4a21c",
      "fieldBlockId": null,
      "includeAllBays": true,
      "includeAllBenches": true,
      "bayTags": [],
      "benchTags": []
    }
  ],
  "sessionDate": "2026-03-16",
  "weekNumber": 12,
  "crop": "Tomato",
  "variety": "Cherry",
  "temperatureCelsius": 22.5,
  "relativeHumidityPercent": 65.0,
  "observationTime": "10:30:00",
  "weatherNotes": "Sunny and warm",
  "notes": "Routine scouting round",
  "defaultPhotoSourceType": "SCOUT_HANDHELD",
  "status": "DRAFT"
}
```

Example drone-oriented session:

```json
{
  "farmId": "0f1d7dd7-6a8a-4c8a-9f31-9d0d4fef2c1a",
  "scoutId": "04c9a9db-c9e6-4ef6-a2b7-2f8a0bcf07ef",
  "targets": [
    {
      "greenhouseId": "1b6a3d3f-bd7a-4572-9e8d-6d90a4b4a21c",
      "fieldBlockId": null,
      "includeAllBays": true,
      "includeAllBenches": true,
      "bayTags": [],
      "benchTags": []
    }
  ],
  "sessionDate": "2026-03-16",
  "weekNumber": 12,
  "notes": "Aerial mapping pass",
  "defaultPhotoSourceType": "DRONE",
  "status": "DRAFT"
}
```

Recommendation for client teams:

- always send `defaultPhotoSourceType`
- do not rely on the backend fallback except as a safety net

### Photo registration and source override

Photo registration still happens before upload confirmation.

Handheld photo inheriting the session default:

```json
{
  "sessionId": "7604d577-5eb1-4d9f-8c32-8d7806d7b8be",
  "observationId": "a91df441-2c52-4630-8aa7-4e2b39849f9d",
  "localPhotoId": "leaf-closeup-001",
  "purpose": "Close-up on leaf damage",
  "capturedAt": "2026-03-16T10:35:00Z"
}
```

Drone photo explicitly overriding a handheld session default:

```json
{
  "sessionId": "7604d577-5eb1-4d9f-8c32-8d7806d7b8be",
  "localPhotoId": "uav-grid-pass-004",
  "purpose": "Drone canopy sweep north quadrant",
  "sourceType": "DRONE",
  "capturedAt": "2026-03-16T10:40:00Z"
}
```

Important backend behavior:

- `purpose` is descriptive metadata only
- `purpose` may help the heuristic classifier, but it does not define the authoritative source type
- `sourceType` is the authoritative source for that photo record

### Upload confirmation

After the client uploads the image to object storage, it confirms the final object key.

Example:

```json
{
  "sessionId": "7604d577-5eb1-4d9f-8c32-8d7806d7b8be",
  "localPhotoId": "leaf-closeup-001",
  "objectKey": "farms/0f1d7dd7/photos/2026/03/16/leaf-closeup-001.jpg"
}
```

Only after registration and confirmation does the photo have:

- a stable backend photo ID
- the effective source type
- the object storage pointer

Those are the minimum pieces required for reliable analysis and audit.

### Core photo analysis endpoints

The core endpoints are:

- `POST /api/scouting/photos/{photoId}/analysis`
- `GET /api/scouting/photos/{photoId}/analysis`
- `PUT /api/scouting/photos/{photoId}/analysis/review`
- `GET /api/scouting/photos/analysis/accuracy`

#### Run analysis

Request:

```json
{
  "farmId": "0f1d7dd7-6a8a-4c8a-9f31-9d0d4fef2c1a"
}
```

Purpose:

- run or refresh the current stored AI analysis for one photo
- persist the top predicted label, confidence, summary, and ranked candidates

Current implementation note:

- the provider is currently heuristic and local
- this is intentionally persisted behind a provider and model-version abstraction so it can later be replaced with a
  real external or embedded model

#### Get analysis

`GET /api/scouting/photos/{photoId}/analysis?farmId={farmId}`

Purpose:

- read the most recent stored analysis
- lazily generate one if none exists yet

#### Manual review

Request:

```json
{
  "farmId": "0f1d7dd7-6a8a-4c8a-9f31-9d0d4fef2c1a",
  "speciesCode": "WHITEFLIES",
  "reviewNotes": "Manager review changed diagnosis after inspecting full plant context"
}
```

Purpose:

- confirm the AI prediction if it was correct
- correct the prediction if a human reviewer disagrees
- create an auditable record for accuracy reporting

Who should use this endpoint:

- managers
- farm admins
- super admins

Scouts can run analysis, but the review step is intentionally restricted to roles responsible for validation.

#### Accuracy reporting

`GET /api/scouting/photos/analysis/accuracy?farmId={farmId}`

Purpose:

- summarize how often the model matched human review
- show pending review volume
- provide per-species breakdowns

This endpoint is about monitored quality, not real-time prediction.

### Response model: explicit AI-versus-manual comparison

The response intentionally contains both flattened summary fields and structured comparison blocks.

Important fields:

- `predictedSpeciesCode`: top AI label
- `reviewedSpeciesCode`: final human-reviewed label, if one exists
- `reviewStatus`: current human-review state
- `candidates`: ranked AI options
- `aiAnalysis`: structured AI snapshot
- `manualAnalysis`: structured manual review snapshot
- `comparison`: normalized AI-versus-manual comparison result

Example response:

```json
{
  "farmId": "0f1d7dd7-6a8a-4c8a-9f31-9d0d4fef2c1a",
  "photoId": "c3b4586b-1808-4562-80e5-6ecce388ce9d",
  "photoSourceType": "SCOUT_HANDHELD",
  "provider": "heuristic-local-v1",
  "modelVersion": "heuristic-local-v1",
  "summary": "Most likely thrips based on photo metadata and recent session observations.",
  "reviewRequired": false,
  "reviewStatus": "CORRECTED",
  "predictedSpeciesCode": "THRIPS",
  "predictedDisplayName": "Thrips",
  "predictedCategory": "PEST",
  "predictedConfidence": 0.91,
  "reviewedSpeciesCode": "WHITEFLIES",
  "reviewedDisplayName": "Whiteflies",
  "reviewedCategory": "PEST",
  "reviewNotes": "Manager review changed diagnosis after inspecting full plant context",
  "reviewerName": "Mia Manager",
  "candidates": [
    {
      "speciesCode": "THRIPS",
      "displayName": "Thrips",
      "category": "PEST",
      "confidence": 0.91,
      "rationale": "Linked observation"
    }
  ],
  "aiAnalysis": {
    "provider": "heuristic-local-v1",
    "modelVersion": "heuristic-local-v1",
    "summary": "Most likely thrips based on photo metadata and recent session observations.",
    "speciesCode": "THRIPS",
    "displayName": "Thrips",
    "category": "PEST",
    "confidence": 0.91,
    "candidates": [
      {
        "speciesCode": "THRIPS",
        "displayName": "Thrips",
        "category": "PEST",
        "confidence": 0.91,
        "rationale": "Linked observation"
      }
    ]
  },
  "manualAnalysis": {
    "reviewStatus": "CORRECTED",
    "speciesCode": "WHITEFLIES",
    "displayName": "Whiteflies",
    "category": "PEST",
    "reviewNotes": "Manager review changed diagnosis after inspecting full plant context",
    "reviewedAt": "2026-03-16T16:11:32Z",
    "reviewerName": "Mia Manager"
  },
  "comparison": {
    "status": "CATEGORY_MATCH",
    "exactMatch": false,
    "sameCategory": true
  }
}
```

### Comparison status semantics

Current comparison statuses are:

- `PENDING_MANUAL_REVIEW`
- `EXACT_MATCH`
- `CATEGORY_MATCH`
- `MISMATCH`

Meaning:

- `PENDING_MANUAL_REVIEW`: no human has reviewed the photo yet
- `EXACT_MATCH`: AI label and manual label are the same species code
- `CATEGORY_MATCH`: AI and manual labels differ, but both belong to the same category such as `PEST`
- `MISMATCH`: AI and manual labels differ across categories or otherwise do not match

Why `CATEGORY_MATCH` exists:

- it distinguishes "the AI missed the exact pest but stayed in the right family of problem" from a total miss such as
  pest versus disease
- it gives product and ML teams a more useful signal than a strict binary correct/incorrect metric

### Review status semantics

Current review statuses are:

- `PENDING_REVIEW`
- `CONFIRMED`
- `CORRECTED`

Meaning:

- `PENDING_REVIEW`: no reviewer has finalized the result
- `CONFIRMED`: a human reviewed the photo and agreed with the AI
- `CORRECTED`: a human reviewed the photo and chose a different final label

### Accuracy metrics

Accuracy is calculated from reviewed photos only.

This is important.

Unreviewed photos are not treated as correct and are not treated as wrong. They are simply pending. Otherwise the
reported metric would mix unknown truth with known truth and become misleading.

The farm-level accuracy response includes:

- `totalAnalyses`: all persisted analyses for the farm
- `pendingReviewCount`: analyses not yet manually reviewed
- `reviewedCount`: analyses with either `CONFIRMED` or `CORRECTED`
- `exactMatchCount`: reviewed cases where AI and manual labels are the same
- `correctedCount`: reviewed cases where human reviewers changed the label
- `accuracyRate`: `exactMatchCount / reviewedCount`
- `averagePredictedConfidence`: mean of stored top-prediction confidence over reviewed cases
- `speciesBreakdown`: per-species reviewed counts and exact-match rates

### How drone analysis fits into the picture

Drone image analysis is optional and remains under:

- `POST /api/optional-capabilities/drone-image-processing/analyze`

The drone feature uses explicit photo source metadata when selecting drone images. In other words:

- if a photo has `sourceType=DRONE`, the optional drone processor treats it as drone imagery
- if the source type is not explicitly set, the optional processor may still fall back to metadata text heuristics
- but the preferred and authoritative signal is the persisted `sourceType`

This makes the drone path more reliable without turning it into a mandatory capability.

### Legacy compatibility route

For compatibility with existing clients, the old optional AI-photo route still exists:

- `GET /api/optional-capabilities/ai-pest-identification/photos/{photoId}`

But that route is now only a shim over the core scouting photo-analysis service. It should not be treated as the main
integration path for new frontend or mobile work.

Use the core scouting endpoints instead.

### Persistence and migration policy

The project currently keeps the baseline schema in:

- `src/main/resources/db/migration/R__initial_schema.sql`

The image-analysis schema changes are folded into that file rather than being split into a separate versioned migration.

That includes:

- `scouting_sessions.default_photo_source_type`
- `scouting_photos.source_type`
- `scouting_photo_analyses`
- `scouting_photo_analysis_candidates`

When updating this schema area, keep it aligned with the repeatable baseline unless the migration strategy of the
project changes.

### Current limitations

These points are intentional and should not be glossed over:

- source type is not auto-detected from image pixels, EXIF, or device hardware
- source type depends on client-supplied metadata
- the current core photo-analysis provider is heuristic, not a full production vision model
- drone analysis remains optional and follows separate commercial controls
- accuracy metrics are only as good as the manual review discipline applied by managers and admins

If the team later adds true model inference, this section should be updated to clearly state:

- how inference is executed
- whether inference is local or remote
- whether source type can be auto-suggested
- whether manual review remains mandatory or only recommended

## Main workflows

### 0. Initial super-admin bootstrap

Before the rest of the platform can be administered, the deployment needs its first `SUPER_ADMIN`.

Typical flow:

1. check whether bootstrap is still open
2. create the first super admin if none exists
3. log in as that super admin
4. use authenticated admin endpoints for any additional privileged profile creation
5. when creating other users, provide a temporary password and let the backend queue the password-setup email

Key endpoints:

- `GET /api/auth/bootstrap/super-admin/status`
- `POST /api/auth/bootstrap/super-admin`
- `POST /api/auth/login`
- `POST /api/auth/users`
- `POST /api/auth/users/{userId}/reactivate`

### 1. Super admin creates and licenses a farm

Typical flow:

1. create the farm
2. create or update structures
3. set license type, activation date, and extension months
4. activate the farm by setting `subscriptionStatus=ACTIVE` when commercially ready
5. generate a license reference if needed
6. optionally override per-farm feature flags

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

- `/bootstrap/super-admin/status`
- `/bootstrap/super-admin`
- `/login`
- `/register`
- `/refresh`
- `/forgot-password`
- `/reset-password`
- `/me`
- `/users`
- `/users/{userId}/reactivate`

Key intent split:

- `/bootstrap/super-admin` is a one-time public bootstrap route for the first super admin only
- `/register` is self-service registration for non-super-admin profiles
- `/users` is the authenticated admin-managed profile creation route and is where existing super admins create other
  super admins
- `/users/{userId}/reactivate` is the super-admin-only recovery route for profiles that were soft-deleted after the
  temporary-password deadline elapsed

Admin-created user lifecycle:

- a super admin submits the new profile, including a temporary password
- the backend stores that profile with `passwordChangeRequired=true` and a 5-day `temporaryPasswordExpiresAt`
- the backend queues an account-setup email that points the user at the reset-password flow
- the user may sign in with the temporary password during that 5-day window, but the frontend should prompt for an
  immediate password change because the profile is still in onboarding state
- once the user completes `POST /api/auth/reset-password`, the onboarding flags are cleared
- if the 5-day window elapses first, the backend soft-deletes the profile, disables access, invalidates open reset
  tokens, and requires a `SUPER_ADMIN` reactivation

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
- set the default photo source type for the session
- run photo analysis
- review photo analysis
- inspect photo-analysis accuracy
- list sessions
- sync sessions
- fetch audits

Important photo operations:

- `POST /api/scouting/photos/register`
- `POST /api/scouting/photos/confirm`
- `POST /api/scouting/photos/{photoId}/analysis`
- `GET /api/scouting/photos/{photoId}/analysis`
- `PUT /api/scouting/photos/{photoId}/analysis/review`
- `GET /api/scouting/photos/analysis/accuracy`

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

- core scouting photo analysis is local heuristic logic with manual review support
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
