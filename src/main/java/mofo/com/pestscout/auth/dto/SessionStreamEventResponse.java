package mofo.com.pestscout.auth.dto;

import java.time.Instant;

public record SessionStreamEventResponse(
        String eventType,
        String message,
        String activeClientSessionId,
        Instant timestamp
) {
}
