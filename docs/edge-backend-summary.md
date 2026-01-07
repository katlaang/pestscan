# Edge-ready backend checklist

This guide summarizes the minimum backend changes required to support edge deployments (offline-first runtimes) while keeping the existing cloud REST APIs. Use it alongside `docs/offline-sync.md` for field data sync details.

## Runtime modes
- Introduce an application runtime flag, e.g. `app.runtime.mode` with values `CLOUD` (default) or `EDGE`.
- Provide a small helper (e.g. `RuntimeMode` component) so services can branch on cloud-only behavior without failing at the edge.
- Edge deployments run per customer with their own DB; no multi-tenant switching logic is needed at the edge.

## Persistence and sync status
- Keep one database per edge site (configured via Spring datasource). The cloud continues using the central database.
- Add a `syncStatus` (enum) field to syncable entities (sessions, observations, photos) to track `LOCAL_ONLY`, `PENDING_UPLOAD`, `SYNCED`, or `CONFLICT` state.
- Preserve existing optimistic concurrency/version fields so cloud merges remain safe.

## Cache behavior
- Do **not** fail startup if Redis is absent. Use `spring.cache.type=simple` for edge and `spring.cache.type=redis` for cloud.
- Remove startup-wide cache eviction that assumes Redis. Instead, evict on domain events (e.g., photo confirmed/uploaded).
- Scope cache keys by tenant/farm/user context to avoid leaks in the cloud runtime.

## Photo lifecycle (cloud + edge)
- Add a dedicated `ScoutingPhoto` entity with: company/farm IDs, session ID, optional observation ID, `localPhotoId` (UUID from device), `status` (`LOCAL_ONLY`, `UPLOADED`), and `objectKey` (storage path).
- Decouple photos from session completion so observations can finalize while offline.
- Provide both metadata and binary flows:
  - **Metadata registration (edge-safe):** `POST /api/scouting/photos/register` stores metadata only.
  - **Binary upload (cloud):** clients upload the image to a presigned URL; backend validates ownership and links the object.
  - **Upload confirmation:** `POST /api/scouting/photos/confirm` marks the photo as uploaded and triggers cache eviction/processing.

## Edge-to-cloud sync endpoints
- Expose cloud-only sync endpoints that accept idempotent, tenant-scoped payloads:
  - `POST /api/cloud/sync/sessions`
  - `POST /api/cloud/sync/photos/register`
  - `POST /api/cloud/sync/photos/confirm`
- Ensure idempotency (reject or no-op on duplicates) and verify company ownership on every call.

## Security and isolation
- In the cloud runtime, enforce tenant isolation end to end: JWT must carry `company_id`; every query and cache key must filter/include that context.
- Edge → cloud sync must **not** depend on a user JWT. Issue a dedicated service token (e.g., `X-Edge-Sync-Token`) scoped to the company and edge node, and authenticate `/api/cloud/sync/**` with it.
- At the edge, isolation is achieved by per-customer deployment; no dynamic tenant switching is required.

## Edge sync worker
- Run a scheduled job only in `EDGE` mode that checks for `PENDING_UPLOAD` sessions/photos and pushes them to the cloud sync endpoints. Keep the interval configurable (e.g., `app.edge.sync.interval-ms`).

## What stays the same
- Existing session/observation controllers remain mostly unchanged—just ensure they tolerate missing Redis and do not block on photo uploads.
- Offline observation sync continues to follow `docs/offline-sync.md` for versioning and soft-delete rules.
- For concrete edge sync payloads, conflict walkthroughs, and React Native queue guidance, see `docs/edge-sync-guide.md`.
