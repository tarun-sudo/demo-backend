package com.example.demo.controller;

import com.example.demo.db.config.dbConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<String> get(@RequestParam("file") File file) throws IOException {
        try {
            dbConfig.uploadFile(file);
            return ResponseEntity.ok(HttpStatus.OK.toString());
        } catch (SQLException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/")
    public ResponseEntity<String> post(@RequestParam("file") File file) throws IOException {
        try {
            dbConfig.uploadFile(file);
            return ResponseEntity.ok(HttpStatus.OK.toString());
        } catch (SQLException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("A valid CSV file is required");
            }
            dbConfig.uploadStream(file.getOriginalFilename(), file.getInputStream());
            return ResponseEntity.ok(HttpStatus.OK.toString());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException | SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/tables")
    public ResponseEntity<?> tables() {
        try {
            List<String> tableNames = dbConfig.listTables();
            return ResponseEntity.ok(tableNames);
        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @DeleteMapping("/tables")
    public ResponseEntity<?> clearTables() {
        try {
            List<String> droppedTables = dbConfig.clearAllUserTables();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("droppedCount", droppedTables.size());
            response.put("droppedTables", droppedTables);
            return ResponseEntity.ok(response);
        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/table-content")
    public ResponseEntity<?> tableContent(@RequestParam("file") String fileName) {
        try {
            return ResponseEntity.ok(dbConfig.getTableContentsForFile(fileName));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PatchMapping("/table-content/cell")
    public ResponseEntity<?> updateCell(@RequestBody CellUpdateRequest request) {
        try {
            if (request == null
                    || request.fileName == null
                    || request.fileName.isBlank()
                    || request.columnName == null
                    || request.columnName.isBlank()) {
                return ResponseEntity.badRequest().body("fileName and columnName are required");
            }
            boolean updated = dbConfig.updateCellForFile(
                    request.fileName,
                    request.rowId,
                    request.columnName,
                    request.value
            );
            if (!updated) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Row not found");
            }
            return ResponseEntity.ok(Map.of("updated", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @DeleteMapping("/table-content/row")
    public ResponseEntity<?> deleteRow(
            @RequestParam("file") String fileName,
            @RequestParam("rowId") long rowId
    ) {
        try {
            if (fileName == null || fileName.isBlank()) {
                return ResponseEntity.badRequest().body("file is required");
            }
            boolean deleted = dbConfig.deleteRowForFile(fileName, rowId);
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Row not found");
            }
            return ResponseEntity.ok(Map.of("deleted", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    public static class CellUpdateRequest {
        public String fileName;
        public long rowId;
        public String columnName;
        public String value;
    }
}
