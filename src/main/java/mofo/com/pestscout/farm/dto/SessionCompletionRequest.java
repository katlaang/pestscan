package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionCompletionRequest {

    @AssertTrue(message = "Please confirm all information is correct before completing the session.")
    private boolean confirmationAcknowledged;
}
