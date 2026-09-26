package com.example.focusquest.security;

import com.example.focusquest.auth.ExtensionCredentialService;
import com.example.focusquest.vision.CompanionCredentialService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ExtensionCredentialService extensionCredentialService;
    private final CompanionCredentialService companionCredentialService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                    UserDetailsService userDetailsService,
                                    ExtensionCredentialService extensionCredentialService,
                                    CompanionCredentialService companionCredentialService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.extensionCredentialService = extensionCredentialService;
        this.companionCredentialService = companionCredentialService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (token.startsWith(ExtensionCredentialService.TOKEN_PREFIX)) {
            authenticateExtension(token, request);
            filterChain.doFilter(request, response);
            return;
        }
        if (token.startsWith(CompanionCredentialService.TOKEN_PREFIX)) {
            authenticateScoped(companionCredentialService.findUsername(token).orElse(null), "ROLE_COMPANION", request);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String username = jwtService.extractUsername(token);
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (jwtService.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /** An extension token authenticates as its user but with ROLE_EXTENSION only, never ROLE_USER. */
    private void authenticateExtension(String token, HttpServletRequest request) {
        authenticateScoped(extensionCredentialService.findUsername(token).orElse(null), "ROLE_EXTENSION", request);
    }

    /**
     * Authenticates a scoped token's user with that single role and never ROLE_USER, so it reaches only
     * the routes SecurityConfig opens to the role. Does nothing for an unknown token.
     */
    private void authenticateScoped(String username, String role, HttpServletRequest request) {
        if (username == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }
        UserDetails principal = User.withUsername(username)
                .password("")
                .authorities(List.of(new SimpleGrantedAuthority(role)))
                .build();
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
