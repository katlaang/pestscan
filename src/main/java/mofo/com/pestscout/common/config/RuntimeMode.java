package mofo.com.pestscout.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RuntimeMode {

    @Value("${app.runtime.mode:CLOUD}")
    private String mode;

    public boolean isEdge() {
        return "EDGE".equalsIgnoreCase(mode);
    }

    public boolean isCloud() {
        return !isEdge();
    }

    public String getMode() {
        return mode;
    }
}

