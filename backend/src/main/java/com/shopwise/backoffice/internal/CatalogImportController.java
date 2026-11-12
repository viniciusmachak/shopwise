package com.shopwise.backoffice.internal;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin-only (secured under /api/admin). CSV catalog import (RF23).
 */
@RestController
@RequestMapping("/api/admin/imports")
class CatalogImportController {

    private final CatalogImportService imports;

    CatalogImportController(CatalogImportService imports) {
        this.imports = imports;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    CatalogImportService.ImportReport upload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty.");
        }
        var name = file.getOriginalFilename() == null ? "catalog.csv" : file.getOriginalFilename();
        try (var in = file.getInputStream()) {
            return imports.run(name, in);
        }
    }

    @GetMapping
    List<CatalogImportService.ImportReport> recent() {
        return imports.recent();
    }

    @GetMapping("/{id}")
    CatalogImportService.ImportReport report(@PathVariable long id) {
        return imports.report(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import not found."));
    }

    @GetMapping(path = "/template", produces = "text/csv")
    ResponseEntity<String> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"catalog-template.csv\"")
                .body(CatalogImportService.template());
    }
}
