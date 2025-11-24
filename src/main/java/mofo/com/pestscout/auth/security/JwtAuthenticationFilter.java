package mofo.com.pestscout.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * JWT Authentication Filter.
 * <p>
 * This filter runs once per request and performs the following:
 * - Extracts the JWT token from the Authorization header (Bearer ...).
 * - Validates the token using JwtTokenProvider.
 * - Loads the corresponding UserDetails from the database.
 * - Populates the Spring SecurityContext with an authenticated user.
 * - Attaches user-related attributes (userId, farmId, userEmail, userRole) to the request
 * so controllers/services can access them easily.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;

    /**
     * Core filter logic.
     * <p>
     * If a valid JWT is present, this method:
     * - extracts claims (email, userId, farmId, role),
     * - loads UserDetails,
     * - creates an authenticated UsernamePasswordAuthenticationToken,
     * - sets it into the SecurityContext.
     * <p>
     * If anything fails, the request is still allowed to proceed, but without
     * an authenticated SecurityContext (downstream handlers can decide what to do).
     */
    @Override
    public void doFilterInternal(@NonNull HttpServletRequest request,
                                 @NonNull HttpServletResponse response,
                                 @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                // Extract claims from token
                String email = tokenProvider.getEmailFromToken(jwt);
                UUID userId = tokenProvider.getUserIdFromToken(jwt);
                UUID farmId = tokenProvider.getFarmIdFromToken(jwt);
                String role = tokenProvider.getRoleFromToken(jwt);

                // Load user details from our UserDetailsService
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // Build an authentication object for Spring Security
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Store authentication in the security context for this request thread
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Attach useful attributes for downstream layers (controllers/services)
                request.setAttribute("userId", userId);
                request.setAttribute("farmId", farmId);
                request.setAttribute("userEmail", email);
                request.setAttribute("userRole", role);

                LOGGER.debug("Set authentication for user '{}' (farmId: {}, role: {})",
                        email, farmId, role);
            } else if (StringUtils.hasText(jwt)) {
                LOGGER.debug("JWT present but invalid or expired");
            }
        } catch (Exception ex) {
            // We log the error but do not block the request pipeline here.
            LOGGER.error("Could not set user authentication in security context", ex);
        }

        // Continue the filter chain regardless of authentication outcome
        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from the Authorization header.
     * <p>
     * Expected format:
     * Authorization: Bearer <token>
     *
     * @param request incoming HTTP request
     * @return the raw JWT string, or null if header is missing/incorrect
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // Strip "Bearer "
        }

        return null;
    }
}