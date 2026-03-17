package mofo.com.pestscout.auth.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Mock
    private CorsConfigurationSource corsConfigurationSource;

    @Mock
    private AuthenticationConfiguration authenticationConfiguration;

    @Mock
    private ObjectProvider<EdgeSyncAuthenticationFilter> edgeSyncAuthenticationFilterProvider;

    @InjectMocks
    private SecurityConfig securityConfig;

    @Test
    void passwordEncoder_usesBCrypt() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(encoder.matches("secret", encoder.encode("secret"))).isTrue();
    }

    @Test
    void authenticationManager_isRetrievedFromConfiguration() throws Exception {
        AuthenticationManager manager = authentication -> null;
        when(authenticationConfiguration.getAuthenticationManager()).thenReturn(manager);

        assertThat(securityConfig.authenticationManager(authenticationConfiguration)).isEqualTo(manager);
    }
}
