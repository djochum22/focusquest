package com.example.focusquest.security;

import com.example.focusquest.shared.exception.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                          HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // Same {code, message} shape as every other error. This runs in the security filter chain,
        // before Spring MVC, so GlobalExceptionHandler cannot produce it.
        ErrorResponse body = new ErrorResponse("UNAUTHORIZED", "Authentication is required to access this resource");

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
