# Changelog

## [Unreleased]
### Added
- Documented service-wide error handling approach that keeps domain exceptions transport-agnostic while still returning consistent API payloads.
- Introduced OpenAPI/Swagger support (springdoc) to describe controller responses using shared `ErrorResponse` payloads.
- Added global exception handler returning consistent error payloads with timestamps, request paths, and optional validation details.

### Changed
- Updated build configuration to include SpringDoc dependency for Swagger UI generation.
- Removed the `BusinessException` base class in favour of focused runtime exceptions that expose only an error code.
- Simplified documentation comments by removing HTML paragraph tags and restored the base entity to its pre-logging implementation.
