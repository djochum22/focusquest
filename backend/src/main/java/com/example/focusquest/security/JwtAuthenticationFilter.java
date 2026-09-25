package com.example.focusquest.security;

import com.example.focusquest.auth.ExtensionCredentialService;
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

    public JwtAuthenticationFilter(JwtService jwtService,
                                    UserDetailsService userDetailsService,
                                    ExtensionCredentialService extensionCredentialService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.extensionCredentialService = extensionCredentialService;
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
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }
        extensionCredentialService.findUsername(token).ifPresent(username -> {
            UserDetails principal = User.withUsername(username)
                    .password("")
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_EXTENSION")))
                    .build();
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        });
    }
}
