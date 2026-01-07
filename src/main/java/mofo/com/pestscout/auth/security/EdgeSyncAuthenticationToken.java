package mofo.com.pestscout.auth.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class EdgeSyncAuthenticationToken extends AbstractAuthenticationToken {

    private final EdgeSyncPrincipal principal;

    public EdgeSyncAuthenticationToken(EdgeSyncPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_EDGE_SYNC")));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "edge-sync-token";
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.email();
    }

    public EdgeSyncPrincipal getEdgePrincipal() {
        return principal;
    }
}
