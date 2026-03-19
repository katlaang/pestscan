package mofo.com.pestscout.common.exception;

import mofo.com.pestscout.common.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsSpringSecurityAccessDeniedToForbidden() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/scouting/sessions/123");

        var response = handler.handleSpringSecurityAccessDenied(new AccessDeniedException("Access Denied"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(body.getErrorCode()).isEqualTo("FORBIDDEN");
        assertThat(body.getMessage()).isEqualTo("Access Denied");
        assertThat(body.getPath()).isEqualTo("/api/scouting/sessions/123");
    }

    @Test
    void mapsUnsupportedMethodToMethodNotAllowed() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/scouting/sessions/123/observations/456");

        var response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PUT"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
        assertThat(body.getErrorCode()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(body.getPath()).isEqualTo("/api/scouting/sessions/123/observations/456");
    }
}
