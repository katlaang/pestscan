package mofo.com.pestscout.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.auth.service.UserSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private ObjectProvider<UserRepository> userRepositoryProvider;

    @Mock
    private ObjectProvider<UserSessionService> userSessionServiceProvider;

    @Mock
    private FilterChain filterChain;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(tokenProvider, userDetailsService, userRepositoryProvider, userSessionServiceProvider);
    }

    @AfterEach
    void cleanContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_setsAuthenticationWhenTokenValid() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        UUID farmId = UUID.randomUUID();
        String token = "token";
        UserDetails userDetails = User.withUsername("user@example.com").password("pass").authorities("ROLE_MANAGER").build();
        mofo.com.pestscout.auth.model.User domainUser = mofo.com.pestscout.auth.model.User.builder()
                .id(userId)
                .email(userDetails.getUsername())
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getRequestURI()).thenReturn("/api/farms");
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getEmailFromToken(token)).thenReturn(userDetails.getUsername());
        when(tokenProvider.getUserIdFromToken(token)).thenReturn(userId);
        when(tokenProvider.getFarmIdFromToken(token)).thenReturn(farmId);
        when(tokenProvider.getRoleFromToken(token)).thenReturn("MANAGER");
        when(userDetailsService.loadUserByUsername(userDetails.getUsername())).thenReturn(userDetails);
        UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
        UserSessionService userSessionService = org.mockito.Mockito.mock(UserSessionService.class);
        when(userRepositoryProvider.getIfAvailable()).thenReturn(userRepository);
        when(userSessionServiceProvider.getIfAvailable()).thenReturn(userSessionService);
        when(userRepository.findById(userId)).thenReturn(Optional.of(domainUser));
        when(userSessionService.isIdleExpired(domainUser)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(UsernamePasswordAuthenticationToken.class);
        verify(userSessionService).recordActivity(domainUser);
        verify(request).setAttribute("userId", userId);
        verify(request).setAttribute("farmId", farmId);
        verify(request).setAttribute("userEmail", userDetails.getUsername());
        verify(request).setAttribute("userRole", "MANAGER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_skipsAuthenticationWhenHeaderMissing() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_skipsAuthenticationWhenUserNoLongerExists() throws ServletException, IOException {
        String token = "token";

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getEmailFromToken(token)).thenReturn("missing@example.com");
        when(userDetailsService.loadUserByUsername("missing@example.com"))
                .thenThrow(new UsernameNotFoundException("missing"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_skipsAuthenticationWhenUserDisabled() throws ServletException, IOException {
        String token = "token";
        UserDetails disabledUser = User.withUsername("disabled@example.com")
                .password("pass")
                .authorities("ROLE_MANAGER")
                .disabled(true)
                .build();
        mofo.com.pestscout.auth.model.User domainUser = mofo.com.pestscout.auth.model.User.builder()
                .email("disabled@example.com")
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getEmailFromToken(token)).thenReturn("disabled@example.com");
        when(userDetailsService.loadUserByUsername("disabled@example.com")).thenReturn(disabledUser);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_skipsAuthenticationWhenIdleExpired() throws ServletException, IOException {
        String token = "token";
        UUID userId = UUID.randomUUID();
        UserDetails userDetails = User.withUsername("idle@example.com").password("pass").authorities("ROLE_MANAGER").build();
        mofo.com.pestscout.auth.model.User domainUser = mofo.com.pestscout.auth.model.User.builder()
                .id(userId)
                .email("idle@example.com")
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getEmailFromToken(token)).thenReturn("idle@example.com");
        when(tokenProvider.getUserIdFromToken(token)).thenReturn(userId);
        when(tokenProvider.getFarmIdFromToken(token)).thenReturn(UUID.randomUUID());
        when(tokenProvider.getRoleFromToken(token)).thenReturn("MANAGER");
        when(userDetailsService.loadUserByUsername("idle@example.com")).thenReturn(userDetails);
        UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
        UserSessionService userSessionService = org.mockito.Mockito.mock(UserSessionService.class);
        when(userRepositoryProvider.getIfAvailable()).thenReturn(userRepository);
        when(userSessionServiceProvider.getIfAvailable()).thenReturn(userSessionService);
        when(userRepository.findById(userId)).thenReturn(Optional.of(domainUser));
        when(userSessionService.isIdleExpired(domainUser)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(request).setAttribute(JwtAuthenticationFilter.AUTH_FAILURE_CODE_ATTR, "SESSION_EXPIRED");
        verify(request).setAttribute(JwtAuthenticationFilter.AUTH_FAILURE_MESSAGE_ATTR, "Your session expired due to inactivity. Please log in again.");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_marksAuthenticationFailureWhenTokenInvalid() throws ServletException, IOException {
        String token = "token";

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenProvider.validateToken(token)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(request).setAttribute(JwtAuthenticationFilter.AUTH_FAILURE_CODE_ATTR, "SESSION_EXPIRED");
        verify(request).setAttribute(JwtAuthenticationFilter.AUTH_FAILURE_MESSAGE_ATTR, "Your session has expired. Please log in again.");
        verify(filterChain).doFilter(request, response);
    }
}
