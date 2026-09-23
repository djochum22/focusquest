package com.example.focusquest.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.example.focusquest.config.JwtConfig;
import com.example.focusquest.security.JwtService;
import com.example.focusquest.streak.StreakService;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private StreakService streakService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private JwtConfig jwtConfig;

    @Test
    void setupCreatesTheDefaultStreakConfigurationRightAfterTheUser() {
        User user = new User("doug", "hash", "Doug", "UTC");
        when(userService.createUser("doug", "password123", "Doug", "UTC")).thenReturn(user);
        when(jwtService.generateToken("doug")).thenReturn("token");
        when(jwtConfig.getExpirationMinutes()).thenReturn(60L);
        AuthService authService = new AuthService(userService, streakService, authenticationManager, jwtService, jwtConfig);

        LoginResponse response = authService.setup(new SetupRequest("doug", "password123", "Doug", "UTC"));

        assertThat(response.token()).isEqualTo("token");
        InOrder order = inOrder(userService, streakService);
        order.verify(userService).createUser("doug", "password123", "Doug", "UTC");
        order.verify(streakService).createDefaultConfiguration(user);
    }
}
