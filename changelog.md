# Changelog

## [Unreleased]
### Added
- Documented service-wide error handling approach that keeps domain exceptions transport-agnostic while still returning consistent API payloads.
- Introduced OpenAPI/Swagger support (springdoc) to describe controller responses using shared `ErrorResponse` payloads.
- Added global exception handler returning consistent error payloads with timestamps, request paths, and optional validation details.
- Added heatmap layer modes (`all`, `pests`, `diseases`) across farm heatmap, heatmap timeline, and GIS heatmap
  endpoints.
- Added observation-model enrichment for scouting observations and drafts:
    - `localObservationId`
    - `observationType`
    - `lifecycleStatus`
    - `latitude`
    - `longitude`
    - `geometry`
- Added support for type-only suspicious observations when a scout has not yet identified the species.
- Added repeatable Flyway migration `R__scouting_observation_enrichment.sql` to backfill and persist the new observation
  fields.
- Added Swagger/OpenAPI documentation for scouting observation capture, session detail, session sync, and edge sync
  payloads.

### Changed
- Updated build configuration to include SpringDoc dependency for Swagger UI generation.
- Removed the `BusinessException` base class in favour of focused runtime exceptions that expose only an error code.
- Simplified documentation comments by removing HTML paragraph tags and restored the base entity to its pre-logging implementation.
- Enabled these optional capabilities by default for tier-eligible farms:
    - `ai-pest-identification`
    - `drone-image-processing`
    - `predictive-modeling`
    - `gis-heatmaps`
    - `automated-treatment-recommendations`
- Updated `README.md` and frontend-facing docs to cover the milestone 0 heatmap layer API changes and milestone 1
  observation payload changes.
- Extended frontend-facing docs to explain geometry capture during scouting sessions, including field GPS capture,
  greenhouse grid-first behavior, and GeoJSON coordinate order.
