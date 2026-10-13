package com.smartlms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlms.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns a JSON 401 response (in ApiResponse format) when an
 * unauthenticated request hits a protected endpoint, instead of
 * Spring Security's default HTML login page.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ApiResponse<Void> body = ApiResponse.error(
                "Unauthorized: " + authException.getMessage()
        );

        new ObjectMapper()
                .findAndRegisterModules()
                .writeValue(response.getOutputStream(), body);
    }
}
