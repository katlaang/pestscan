# Exception Handling Strategy

The service keeps domain logic free from transport concerns (HTTP, JSON, etc.) while still giving adapters enough information to build consistent API responses.

## Application-level exceptions

Domain and application services throw focused runtime exceptions such as `ConflictException` or `ResourceNotFoundException` when a rule is violated. Each exception implements `ErrorCodeCarrier`, exposing a stable `errorCode` string that mobile and web clients can react to. The exceptions themselves do **not** know anything about HTTP, which keeps them aligned with the clean architecture boundary rules.

```java
if (userRepository.existsByEmail(request.email())) {
    throw new ConflictException("A user with this email already exists");
}
```

## Global Exception Handler

`GlobalExceptionHandler` lives in the primary adapter layer (`@RestControllerAdvice`). It inspects the thrown exception, maps it to the correct HTTP status (e.g. 400, 404, 409), and returns a shared `ErrorResponse` payload:

- `timestamp` – when the error occurred
- `status` – numeric HTTP status code
- `errorCode` – machine-friendly identifier (e.g. `NOT_FOUND`)
- `message` – human readable explanation
- `path` – request URI
- `details` – optional validation messages

Validation errors aggregate field messages so clients receive actionable feedback in a single response.

## Swagger / OpenAPI documentation

The project enables [springdoc-openapi](https://springdoc.org/) so every controller can declare responses like this:

```java
@Operation(summary = "Create farm", description = "Registers a new farm tenant")
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = FarmResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(responseCode = "409", description = "Duplicate farm", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
@PostMapping("/api/farms")
public ResponseEntity<FarmResponse> createFarm(@Valid @RequestBody CreateFarmRequest request) {
    FarmResponse response = farmService.createFarm(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

Because every exception funnels through the handler and returns the shared `ErrorResponse`, controllers can confidently reference that schema in their Swagger annotations.

## Summary

- Throw expressive exceptions from the service layer; avoid building HTTP responses there.
- Let `GlobalExceptionHandler` translate exceptions into HTTP responses.
- Describe success and error payloads in controllers using springdoc.
- Clients receive consistent JSON error payloads, whether the error came from validation or application rules.
