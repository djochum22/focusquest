package com.example.focusquest.export;

import com.example.focusquest.user.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExportController {

    private final ExportService exportService;
    private final UserService userService;

    public ExportController(ExportService exportService, UserService userService) {
        this.exportService = exportService;
        this.userService = userService;
    }

    @GetMapping("/api/export")
    public LocalDataExportDto exportLocalData(@AuthenticationPrincipal UserDetails principal) {
        return exportService.exportLocalData(userService.getByUsername(principal.getUsername()));
    }
}
