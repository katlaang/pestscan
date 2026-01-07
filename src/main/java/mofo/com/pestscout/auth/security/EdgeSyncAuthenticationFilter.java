package mofo.com.pestscout.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Lightweight guard that authenticates edge sync calls using a shared token instead of a user JWT.
 * This enables headless background sync jobs to call the cloud endpoints without a human session.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EdgeSyncAuthenticationFilter extends OncePerRequestFilter {

    public static final String EDGE_SYNC_HEADER = "X-Edge-Sync-Token";

    private final EdgeSyncProperties edgeSyncProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/cloud/sync");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!edgeSyncProperties.isEnabled() || edgeSyncProperties.getToken() == null || edgeSyncProperties.getToken().isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(EDGE_SYNC_HEADER);
        if (token == null) {
            token = extractBearer(request.getHeader(HttpHeaders.AUTHORIZATION));
        }

        if (token != null && token.equals(edgeSyncProperties.getToken())) {
            EdgeSyncPrincipal principal = new EdgeSyncPrincipal(
                    edgeSyncProperties.getCompanyNumber(),
                    edgeSyncProperties.getEdgeNodeId()
            );
            EdgeSyncAuthenticationToken authentication = new EdgeSyncAuthenticationToken(principal);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated edge sync request for company {} via node {}", principal.companyNumber(), principal.edgeNodeId());
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length());
    }
}
