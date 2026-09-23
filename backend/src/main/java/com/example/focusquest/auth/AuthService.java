package com.example.focusquest.auth;

import com.example.focusquest.config.JwtConfig;
import com.example.focusquest.security.JwtService;
import com.example.focusquest.streak.StreakService;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserDto;
import com.example.focusquest.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserService userService;
    private final StreakService streakService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;

    public AuthService(UserService userService,
                        StreakService streakService,
                        AuthenticationManager authenticationManager,
                        JwtService jwtService,
                        JwtConfig jwtConfig) {
        this.userService = userService;
        this.streakService = streakService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtConfig = jwtConfig;
    }

    public LoginResponse setup(SetupRequest request) {
        User user = userService.createUser(
                request.username(), request.password(), request.displayName(), request.timezone());
        streakService.createDefaultConfiguration(user);
        return buildLoginResponse(user);
    }

    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        User user = userService.getByUsername(request.username());
        return buildLoginResponse(user);
    }

    private LoginResponse buildLoginResponse(User user) {
        String token = jwtService.generateToken(user.getUsername());
        long expiresInSeconds = jwtConfig.getExpirationMinutes() * 60;
        return new LoginResponse(token, "Bearer", expiresInSeconds, UserDto.from(user));
    }
}
