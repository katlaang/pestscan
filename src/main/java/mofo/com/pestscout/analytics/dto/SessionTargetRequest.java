package mofo.com.pestscout.analytics.dto;

import java.util.List;
import java.util.UUID;

/**
 * Declares which structure(s) the session will cover and whether all bays/benches
 * should be included or only specific tagged ones.
 */
 public record SessionTargetRequest(
         UUID greenhouseId,
         UUID fieldBlockId,
         Boolean includeAllBays,
         Boolean includeAllBenches,
         List<String> bayTags,
         List<String> benchTags
 ) {
     public SessionTargetRequest {
         if (greenhouseId == null && fieldBlockId == null) {
             throw new IllegalArgumentException("Target must reference a greenhouse or a field block");
         }
         if (greenhouseId != null && fieldBlockId != null) {
             throw new IllegalArgumentException("Target cannot reference both greenhouse and field block");
         }
     }
 }
