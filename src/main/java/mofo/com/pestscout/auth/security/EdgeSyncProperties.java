package mofo.com.pestscout.auth.security;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class EdgeSyncProperties {

    @Value("${app.edge.sync.token:}")
    private String token;

    @Value("${app.edge.sync.company-number:}")
    private String companyNumber;

    @Value("${app.edge.sync.edge-node-id:edge-node}")
    private String edgeNodeId;

    @Value("${app.edge.sync.enabled:true}")
    private boolean enabled;
}
