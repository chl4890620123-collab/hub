package com.hub.controller;

import com.hub.model.SpreadsheetColumn;
import com.hub.model.SpreadsheetFile;
import com.hub.model.SpreadsheetRow;
import com.hub.model.User;
import com.hub.service.CurrentUserService;
import com.hub.service.SpreadsheetService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** "자료표" - the Excel-like CRUD grid page. One sheet's password (when set) travels as the
 *  X-Sheet-Password header on every call that touches its content, so it never sits in a URL/query
 *  string where it could end up logged. */
@RestController
public class SpreadsheetController {
    private static final String PASSWORD_HEADER = "X-Sheet-Password";

    private final CurrentUserService currentUser;
    private final SpreadsheetService sheets;

    public SpreadsheetController(CurrentUserService currentUser, SpreadsheetService sheets) {
        this.currentUser = currentUser; this.sheets = sheets;
    }

    public record CreateRequest(String name, List<String> columns, String password, String hint) {}
    public record RenameRequest(String name, String password) {}
    public record ColumnsRequest(List<SpreadsheetColumn> columns, String password) {}
    public record SecurityRequest(String currentPassword, String newPassword, String hint) {}
    public record RowRequest(Map<String, String> cells) {}

    @GetMapping("/api/projects/{projectId}/sheets")
    public List<SpreadsheetFile> list(@PathVariable long projectId, Authentication auth) {
        User user = currentUser.requireOperational(auth);
        return sheets.list(projectId, user);
    }

    @PostMapping("/api/projects/{projectId}/sheets")
    public Map<String, Object> create(@PathVariable long projectId, @RequestBody CreateRequest body, Authentication auth) {
        User user = currentUser.requireOperational(auth);
        long id = sheets.create(projectId, body.name(), body.columns(), body.password(), body.hint(), user);
        return Map.of("id", id);
    }

    @PostMapping(value = "/api/projects/{projectId}/sheets/import", consumes = "multipart/form-data")
    public Map<String, Object> importXlsx(@PathVariable long projectId, @RequestPart("file") MultipartFile file,
                                          @RequestParam(required = false) String name,
                                          @RequestParam(required = false) String password,
                                          @RequestParam(required = false) String hint, Authentication auth) {
        User user = currentUser.requireOperational(auth);
        long id = sheets.importXlsx(projectId, name, password, hint, file, user);
        return Map.of("id", id);
    }

    @GetMapping("/api/sheets/{id}")
    public SpreadsheetService.Unlocked open(@PathVariable long id,
                                            @RequestHeader(value = PASSWORD_HEADER, required = false) String password,
                                            Authentication auth) {
        User user = currentUser.requireOperational(auth);
        return sheets.open(id, password, user);
    }

    @PutMapping("/api/sheets/{id}")
    public Map<String, Object> rename(@PathVariable long id, @RequestBody RenameRequest body, Authentication auth) {
        User user = currentUser.requireOperational(auth);
        sheets.rename(id, body.name(), body.password(), user);
        return Map.of("status", "RENAMED");
    }

    @PutMapping("/api/sheets/{id}/columns")
    public Map<String, Object> columns(@PathVariable long id, @RequestBody ColumnsRequest body, Authentication auth) {
        User user = currentUser.requireOperational(auth);
        sheets.setColumns(id, body.columns(), body.password(), user);
        return Map.of("status", "UPDATED");
    }

    @PutMapping("/api/sheets/{id}/security")
    public Map<String, Object> security(@PathVariable long id, @RequestBody SecurityRequest body, Authentication auth) {
        User user = currentUser.requireOperational(auth);
        sheets.setSecurity(id, body.currentPassword(), body.newPassword(), body.hint(), user);
        return Map.of("status", "UPDATED");
    }

    @DeleteMapping("/api/sheets/{id}")
    public Map<String, Object> delete(@PathVariable long id,
                                      @RequestHeader(value = PASSWORD_HEADER, required = false) String password,
                                      Authentication auth) {
        User user = currentUser.requireOperational(auth);
        sheets.delete(id, password, user);
        return Map.of("status", "DELETED");
    }

    @GetMapping("/api/sheets/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable long id,
                                         @RequestHeader(value = PASSWORD_HEADER, required = false) String password,
                                         Authentication auth) {
        User user = currentUser.requireOperational(auth);
        var file = sheets.exportXlsx(id, password, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }

    @PostMapping("/api/sheets/{id}/rows")
    public SpreadsheetRow addRow(@PathVariable long id,
                                 @RequestHeader(value = PASSWORD_HEADER, required = false) String password,
                                 @RequestBody RowRequest body, Authentication auth) {
        User user = currentUser.requireOperational(auth);
        return sheets.addRow(id, password, body.cells(), user);
    }

    @PutMapping("/api/sheets/{id}/rows/{rowId}")
    public Map<String, Object> updateRow(@PathVariable long id, @PathVariable long rowId,
                                         @RequestHeader(value = PASSWORD_HEADER, required = false) String password,
                                         @RequestBody RowRequest body, Authentication auth) {
        User user = currentUser.requireOperational(auth);
        sheets.updateRow(id, rowId, password, body.cells(), user);
        return Map.of("status", "UPDATED");
    }

    @DeleteMapping("/api/sheets/{id}/rows/{rowId}")
    public Map<String, Object> deleteRow(@PathVariable long id, @PathVariable long rowId,
                                         @RequestHeader(value = PASSWORD_HEADER, required = false) String password,
                                         Authentication auth) {
        User user = currentUser.requireOperational(auth);
        sheets.deleteRow(id, rowId, password, user);
        return Map.of("status", "DELETED");
    }
}
