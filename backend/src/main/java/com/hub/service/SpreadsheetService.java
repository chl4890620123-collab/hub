package com.hub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.model.SpreadsheetColumn;
import com.hub.model.SpreadsheetFile;
import com.hub.model.SpreadsheetRow;
import com.hub.model.User;
import com.hub.repository.SpreadsheetRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "자료표" - a per-project Excel-like CRUD data grid. User-defined columns, ordered rows, plain
 * add/edit/delete on both, XLSX import/export, and an optional per-file password (with a hint shown
 * back on a wrong/missing attempt - see SpreadsheetRepository's migration comment for why that's fine).
 * No formulas or cell references: this mirrors what the user actually asked for ("CRUD 구성"), not a
 * spreadsheet engine.
 */
@Service
public class SpreadsheetService {
    private static final int MAX_COLUMNS = 60;
    private static final int MAX_ROWS = 5000;
    private static final int MIN_SHEET_PASSWORD_LEN = 4;

    private final SpreadsheetRepository repo;
    private final ProjectAccessService access;
    private final ObjectMapper json;
    private final PasswordEncoder passwordEncoder;

    public SpreadsheetService(SpreadsheetRepository repo, ProjectAccessService access, ObjectMapper json,
                              PasswordEncoder passwordEncoder) {
        this.repo = repo; this.access = access; this.json = json; this.passwordEncoder = passwordEncoder;
    }

    public List<SpreadsheetFile> list(long projectId, User actor) {
        access.requireAccess(projectId, actor);
        return repo.listForProject(projectId).stream().map(this::toFileView).toList();
    }

    public record Unlocked(SpreadsheetFile file, List<SpreadsheetRow> rows) {}

    public Unlocked open(long fileId, String password, User actor) {
        var raw = requireFile(fileId, actor);
        requirePassword(raw, password);
        var rows = repo.listRows(fileId).stream().map(this::toRowView).toList();
        return new Unlocked(toFileView(raw), rows);
    }

    public long create(long projectId, String name, List<String> columnLabels, String password, String hint, User actor) {
        access.requireAccess(projectId, actor);
        String safeName = requireName(name);
        List<SpreadsheetColumn> columns = buildColumns(columnLabels);
        String hash = encodeSheetPassword(password);
        return repo.createFile(projectId, safeName, actor.id(), writeColumns(columns), hash, hash == null ? null : blankToNull(hint));
    }

    public void rename(long fileId, String name, String password, User actor) {
        var raw = requireOwnerOrAdmin(fileId, actor);
        requirePasswordUnlessAdmin(raw, password, actor);
        repo.renameFile(fileId, requireName(name));
    }

    public void setColumns(long fileId, List<String> columnLabels, String password, User actor) {
        var raw = requireOwnerOrAdmin(fileId, actor);
        requirePasswordUnlessAdmin(raw, password, actor);
        repo.updateColumns(fileId, writeColumns(buildColumns(columnLabels)));
        repo.touch(fileId);
    }

    /** currentPassword must match the existing password (null/blank ok when none is set yet), unless
     * the caller is a company admin - otherwise a lost password plus a hint nobody can guess would
     * permanently strand the file with no recovery path at all. */
    public void setSecurity(long fileId, String currentPassword, String newPassword, String hint, User actor) {
        var raw = requireOwnerOrAdmin(fileId, actor);
        requirePasswordUnlessAdmin(raw, currentPassword, actor);
        String hash = encodeSheetPassword(newPassword);
        repo.updateSecurity(fileId, hash, hash == null ? null : blankToNull(hint));
    }

    public void delete(long fileId, String password, User actor) {
        var raw = requireOwnerOrAdmin(fileId, actor);
        requirePasswordUnlessAdmin(raw, password, actor);
        repo.deleteFile(fileId);
    }

    public SpreadsheetRow addRow(long fileId, String password, Map<String, String> cells, User actor) {
        var raw = requireFile(fileId, actor);
        requirePassword(raw, password);
        if (repo.listRows(fileId).size() >= MAX_ROWS) throw new IllegalArgumentException("행은 최대 " + MAX_ROWS + "개까지 만들 수 있습니다.");
        int position = repo.nextRowPosition(fileId);
        long id = repo.createRow(fileId, position, writeCells(cleanCells(raw, cells)));
        repo.touch(fileId);
        return new SpreadsheetRow(id, fileId, position, cleanCells(raw, cells), java.time.LocalDateTime.now());
    }

    public void updateRow(long fileId, long rowId, String password, Map<String, String> cells, User actor) {
        var raw = requireFile(fileId, actor);
        requirePassword(raw, password);
        var row = repo.findRow(rowId).filter(r -> r.fileId() == fileId)
                .orElseThrow(() -> new IllegalArgumentException("행을 찾을 수 없습니다."));
        repo.updateRow(row.id(), writeCells(cleanCells(raw, cells)));
        repo.touch(fileId);
    }

    public void deleteRow(long fileId, long rowId, String password, User actor) {
        var raw = requireFile(fileId, actor);
        requirePassword(raw, password);
        repo.findRow(rowId).filter(r -> r.fileId() == fileId)
                .orElseThrow(() -> new IllegalArgumentException("행을 찾을 수 없습니다."));
        repo.deleteRow(rowId);
        repo.touch(fileId);
    }

    public long importXlsx(long projectId, String name, String password, String hint, MultipartFile file, User actor) {
        access.requireAccess(projectId, actor);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("가져올 엑셀 파일을 선택해 주세요.");
        String safeName = requireName(name != null && !name.isBlank() ? name : baseFileName(file));
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.getBytes()))) {
            Sheet sheet = workbook.getSheetAt(0);
            var rows = sheet.iterator();
            if (!rows.hasNext()) throw new IllegalArgumentException("엑셀 파일에 내용이 없습니다.");
            Row headerRow = rows.next();
            // Indexed access, not the cell iterator: the iterator silently skips blank cells, which
            // would shift every column after a gap out of alignment with the data rows below it.
            List<String> labels = new ArrayList<>();
            int lastCol = headerRow.getLastCellNum();
            for (int i = 0; i < lastCol; i++) labels.add(cellText(headerRow.getCell(i)));
            while (!labels.isEmpty() && labels.get(labels.size() - 1).isBlank()) labels.remove(labels.size() - 1);
            if (labels.isEmpty()) throw new IllegalArgumentException("첫 줄에 열 이름이 있어야 합니다.");
            List<SpreadsheetColumn> columns = buildColumns(labels);
            String hash = encodeSheetPassword(password);
            long fileId = repo.createFile(projectId, safeName, actor.id(), writeColumns(columns), hash, hash == null ? null : blankToNull(hint));
            int position = 0, imported = 0;
            while (rows.hasNext() && imported < MAX_ROWS) {
                Row dataRow = rows.next();
                Map<String, String> cells = new LinkedHashMap<>();
                boolean anyValue = false;
                for (int i = 0; i < columns.size(); i++) {
                    String value = cellText(dataRow.getCell(i));
                    if (!value.isBlank()) anyValue = true;
                    cells.put(columns.get(i).key(), value);
                }
                if (!anyValue) continue;
                repo.createRow(fileId, position++, writeCells(cells));
                imported++;
            }
            return fileId;
        } catch (IOException e) {
            throw new IllegalArgumentException("엑셀 파일을 읽지 못했습니다. .xlsx 형식인지 확인해 주세요.");
        } catch (org.apache.poi.EmptyFileException | org.apache.poi.util.RecordFormatException e) {
            throw new IllegalArgumentException("엑셀 파일 형식을 인식하지 못했습니다.");
        }
    }

    public record Export(byte[] bytes, String fileName) {}

    public Export exportXlsx(long fileId, String password, User actor) {
        var raw = requireFile(fileId, actor);
        requirePassword(raw, password);
        List<SpreadsheetColumn> columns = readColumns(raw.columnsJson());
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("자료표");
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) header.createCell(i).setCellValue(columns.get(i).label());
            var dataRows = repo.listRows(fileId);
            for (int r = 0; r < dataRows.size(); r++) {
                Map<String, String> cells = readCells(dataRows.get(r).cellsJson());
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < columns.size(); c++) {
                    String value = cells.getOrDefault(columns.get(c).key(), "");
                    row.createCell(c).setCellValue(value);
                }
            }
            for (int i = 0; i < columns.size(); i++) sheet.autoSizeColumn(i);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new Export(out.toByteArray(), raw.name() + ".xlsx");
        } catch (IOException e) {
            throw new UncheckedIOException("엑셀 파일을 만들지 못했습니다.", e);
        }
    }

    // --- internal ---

    private SpreadsheetRepository.FileRow requireFile(long fileId, User actor) {
        var raw = repo.findFile(fileId).orElseThrow(() -> new IllegalArgumentException("자료표를 찾을 수 없습니다."));
        access.requireAccess(raw.projectId(), actor);
        return raw;
    }

    private SpreadsheetRepository.FileRow requireOwnerOrAdmin(long fileId, User actor) {
        var raw = requireFile(fileId, actor);
        if (raw.ownerId() != actor.id() && !actor.isAdmin())
            throw new AccessDeniedException("만든 사람이나 관리자만 이 자료표를 바꿀 수 있습니다.");
        return raw;
    }

    private void requirePassword(SpreadsheetRepository.FileRow raw, String provided) {
        if (raw.passwordHash() == null) return;
        if (provided == null || provided.isBlank() || !passwordEncoder.matches(provided, raw.passwordHash()))
            throw new SheetLockedException(raw.passwordHint());
    }

    private void requirePasswordUnlessAdmin(SpreadsheetRepository.FileRow raw, String provided, User actor) {
        if (actor.isAdmin()) return;
        requirePassword(raw, provided);
    }

    private String encodeSheetPassword(String password) {
        if (password == null || password.isBlank()) return null;
        if (password.length() < MIN_SHEET_PASSWORD_LEN)
            throw new IllegalArgumentException("비밀번호는 " + MIN_SHEET_PASSWORD_LEN + "자 이상으로 입력해 주세요.");
        return passwordEncoder.encode(password);
    }

    private SpreadsheetFile toFileView(SpreadsheetRepository.FileRow raw) {
        return new SpreadsheetFile(raw.id(), raw.projectId(), raw.name(), raw.ownerId(), readColumns(raw.columnsJson()),
                raw.passwordHash() != null, raw.passwordHint(), raw.rowCount(), raw.createdAt(), raw.updatedAt());
    }

    private SpreadsheetRow toRowView(SpreadsheetRepository.RowRow raw) {
        return new SpreadsheetRow(raw.id(), raw.fileId(), raw.position(), readCells(raw.cellsJson()), raw.updatedAt());
    }

    private Map<String, String> cleanCells(SpreadsheetRepository.FileRow raw, Map<String, String> cells) {
        List<SpreadsheetColumn> columns = readColumns(raw.columnsJson());
        Map<String, String> cleaned = new LinkedHashMap<>();
        for (SpreadsheetColumn column : columns) {
            String value = cells == null ? null : cells.get(column.key());
            cleaned.put(column.key(), value == null ? "" : value);
        }
        return cleaned;
    }

    private static List<SpreadsheetColumn> buildColumns(List<String> labels) {
        if (labels == null || labels.isEmpty()) throw new IllegalArgumentException("열을 하나 이상 만들어 주세요.");
        if (labels.size() > MAX_COLUMNS) throw new IllegalArgumentException("열은 최대 " + MAX_COLUMNS + "개까지 만들 수 있습니다.");
        List<SpreadsheetColumn> columns = new ArrayList<>();
        int i = 0;
        for (String label : labels) {
            String clean = label == null || label.isBlank() ? "열" + (i + 1) : label.strip();
            columns.add(new SpreadsheetColumn("c" + (i + 1), clean));
            i++;
        }
        return columns;
    }

    private String writeColumns(List<SpreadsheetColumn> columns) {
        try { return json.writeValueAsString(columns); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private List<SpreadsheetColumn> readColumns(String columnsJson) {
        try { return json.readValue(columnsJson, new TypeReference<List<SpreadsheetColumn>>() {}); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private String writeCells(Map<String, String> cells) {
        try { return json.writeValueAsString(cells); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private Map<String, String> readCells(String cellsJson) {
        try { return json.readValue(cellsJson, new TypeReference<Map<String, String>>() {}); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("이름을 입력해 주세요.");
        String trimmed = name.strip();
        if (trimmed.length() > 200) throw new IllegalArgumentException("이름은 200자 이하로 입력해 주세요.");
        return trimmed;
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.strip(); }

    private static String cellText(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().strip();
            case NUMERIC -> org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : formatNumeric(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private static String formatNumeric(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) return String.format(Locale.ROOT, "%.0f", value);
        return String.valueOf(value);
    }

    private static String baseFileName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) return "가져온 자료표";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
