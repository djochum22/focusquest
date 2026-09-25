package com.example.focusquest.auth;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.auth.ExtensionCredentialService;
import com.example.focusquest.security.JwtService;
import com.example.focusquest.support.WithRealSecurityConfig;
import com.example.focusquest.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WithRealSecurityConfig
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private ExtensionCredentialService extensionCredentialService;

    @Test
    void setupStatusReportsSetupRequiredWhileNoUserExistsWithoutAuthentication() throws Exception {
        when(userService.hasUser()).thenReturn(false);

        mockMvc.perform(get("/api/auth/setup-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(true));
    }

    @Test
    void setupStatusReportsSetupCompleteOnceAUserExists() throws Exception {
        when(userService.hasUser()).thenReturn(true);

        mockMvc.perform(get("/api/auth/setup-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(false));
    }

    @Test
    void otherAuthEndpointsStillRequireAToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
