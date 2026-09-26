package com.example.focusquest.support;

import com.example.focusquest.config.CorsConfig;
import com.example.focusquest.config.SecurityConfig;
import com.example.focusquest.security.AuthEntryPoint;
import com.example.focusquest.security.JwtAuthenticationFilter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Loads the application's real {@link SecurityConfig} into a {@code @WebMvcTest} slice. Without it
 * the slice silently falls back to Spring Boot's default security (HTTP basic, CSRF enabled), which
 * neither matches production nor lets state-changing requests through. The test class must still
 * provide {@code @MockitoBean} instances of {@code JwtService}, {@code UserDetailsService}, {@code ExtensionCredentialService} and
 * {@code CompanionCredentialService}, which
 * the JWT filter depends on.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AuthEntryPoint.class, CorsConfig.class})
public @interface WithRealSecurityConfig {
}
