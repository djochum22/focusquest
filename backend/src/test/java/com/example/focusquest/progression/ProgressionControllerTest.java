package com.example.focusquest.progression;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.security.JwtService;
import com.example.focusquest.support.WithRealSecurityConfig;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Slice test for ProgressionController under the real security configuration. */
@WithRealSecurityConfig
@WebMvcTest(ProgressionController.class)
class ProgressionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProgressionService progressionService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("doug", "hash", "Doug", "UTC");
        when(userService.getByUsername("doug")).thenReturn(user);
    }

    @Test
    @WithMockUser(username = "doug")
    void returnsTheProgression() throws Exception {
        when(progressionService.getSummary(user))
                .thenReturn(new ProgressionService.ProgressionSummary(300, 3, 250, 450, 12));

        mockMvc.perform(get("/api/me/progression"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalXp").value(300))
                .andExpect(jsonPath("$.level").value(3))
                .andExpect(jsonPath("$.levelStartXp").value(250))
                .andExpect(jsonPath("$.nextLevelXp").value(450))
                .andExpect(jsonPath("$.gems").value(12));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/me/progression")).andExpect(status().isUnauthorized());
    }
}
