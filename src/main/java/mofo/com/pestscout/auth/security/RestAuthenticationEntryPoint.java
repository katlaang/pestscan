package mofo.com.pestscout.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.common.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        String errorCode = resolveStringAttribute(request, JwtAuthenticationFilter.AUTH_FAILURE_CODE_ATTR, "AUTHENTICATION_REQUIRED");
        String message = resolveStringAttribute(
                request,
                JwtAuthenticationFilter.AUTH_FAILURE_MESSAGE_ATTR,
                "Authentication required. Please log in again."
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .errorCode(errorCode)
                .message(message)
                .path(request.getRequestURI())
                .build();

        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String resolveStringAttribute(HttpServletRequest request, String attributeName, String fallback) {
        Object attribute = request.getAttribute(attributeName);
        return attribute instanceof String value && !value.isBlank() ? value : fallback;
    }
}
