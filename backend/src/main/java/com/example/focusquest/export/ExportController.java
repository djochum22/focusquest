package com.example.focusquest.export;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/api/export")
    public LocalDataExportDto exportLocalData() {
        return exportService.exportLocalData();
    }
}
