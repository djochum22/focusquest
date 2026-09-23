package com.example.focusquest.export;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.focusquest.security.JwtService;

/**
 * Slice test for ExportController. SecurityConfig (config package) is part of every
 * MVC slice's context, so its filter chain dependencies (JwtService, UserDetailsService)
 * are mocked here purely to satisfy wiring; this test does not exercise JWT behavior.
 */
@WebMvcTest(ExportController.class)
class ExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExportService exportService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @WithMockUser
    void exportReturnsLocalDataAsJson() throws Exception {
        when(exportService.exportLocalData())
                .thenReturn(new LocalDataExportDto(Instant.parse("2026-01-01T00:00:00Z"), "1.0"));

        mockMvc.perform(get("/api/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportedAt", notNullValue()))
                .andExpect(jsonPath("$.schemaVersion").value("1.0"));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/export"))
                .andExpect(status().isUnauthorized());
    }
}
