package com.example.focusquest.export;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.auth.ExtensionCredentialService;
import com.example.focusquest.security.JwtService;
import com.example.focusquest.support.WithRealSecurityConfig;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserDto;
import com.example.focusquest.user.UserService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Slice test for ExportController under the real security configuration. */
@WithRealSecurityConfig
@WebMvcTest(ExportController.class)
class ExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExportService exportService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private ExtensionCredentialService extensionCredentialService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("doug", "hash", "Doug", "UTC");
        when(userService.getByUsername("doug")).thenReturn(user);
    }

    @Test
    @WithMockUser(username = "doug")
    void exportReturnsTheCallersDataAsJson() throws Exception {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        when(exportService.exportLocalData(user)).thenReturn(new LocalDataExportDto(now, "1.2",
                UserDto.from(user), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportedAt", notNullValue()))
                .andExpect(jsonPath("$.schemaVersion").value("1.2"))
                .andExpect(jsonPath("$.user.username").value("doug"))
                .andExpect(jsonPath("$.focusSessions").isArray())
                .andExpect(jsonPath("$.experienceTransactions").isArray())
                .andExpect(jsonPath("$.gemTransactions").isArray());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/export"))
                .andExpect(status().isUnauthorized());
    }
}
