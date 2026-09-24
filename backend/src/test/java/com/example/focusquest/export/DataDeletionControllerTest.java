package com.example.focusquest.export;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/** Slice test for DataDeletionController under the real security configuration. */
@WithRealSecurityConfig
@WebMvcTest(DataDeletionController.class)
class DataDeletionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DataDeletionService dataDeletionService;

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
    void deletesTheCallersDataAndReturns204() throws Exception {
        mockMvc.perform(delete("/api/me/data")).andExpect(status().isNoContent());

        verify(dataDeletionService).deleteAllData(user);
    }

    @Test
    @WithMockUser(username = "doug")
    void reportsAConflictWhileBlockingIsActive() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Data cannot be deleted while website blocking is active"))
                .when(dataDeletionService).deleteAllData(user);

        mockMvc.perform(delete("/api/me/data"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(delete("/api/me/data")).andExpect(status().isUnauthorized());
    }
}
