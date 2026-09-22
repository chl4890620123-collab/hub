package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Raw JDBC access for "자료표" CRUD tables. Columns/cells stay as JSON text here - parsing into
 * SpreadsheetColumn/SpreadsheetRow happens in SpreadsheetService, matching how ProcessingJob keeps
 * resultJson as a plain string at the repository layer.
 */
@Repository
public class SpreadsheetRepository {
    private final JdbcTemplate jdbc;
    public SpreadsheetRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record FileRow(long id, long projectId, String name, long ownerId, String columnsJson,
                          String passwordHash, String passwordHint, int rowCount,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record RowRow(long id, long fileId, int position, String cellsJson, LocalDateTime updatedAt) {}

    public long createFile(long projectId, String name, long ownerId, String columnsJson,
                           String passwordHash, String passwordHint) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO spreadsheet_file(project_id,name,owner_id,columns_json,password_hash,password_hint)
                    VALUES(?,?,?,?,?,?)
                    """, new String[]{"id"});
            ps.setLong(1, projectId); ps.setString(2, name); ps.setLong(3, ownerId); ps.setString(4, columnsJson);
            if (passwordHash == null) ps.setNull(5, Types.VARCHAR); else ps.setString(5, passwordHash);
            if (passwordHint == null) ps.setNull(6, Types.VARCHAR); else ps.setString(6, passwordHint);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Spreadsheet file id was not generated");
        return key.getKey().longValue();
    }

    public List<FileRow> listForProject(long projectId) {
        return jdbc.query("""
                SELECT f.id,f.project_id,f.name,f.owner_id,f.columns_json,f.password_hash,f.password_hint,
                       f.created_at,f.updated_at,COALESCE(r.cnt,0) AS row_count
                FROM spreadsheet_file f
                LEFT JOIN (SELECT file_id,COUNT(*) cnt FROM spreadsheet_row GROUP BY file_id) r ON r.file_id=f.id
                WHERE f.project_id=? ORDER BY f.updated_at DESC
                """, (rs, n) -> mapFile(rs), projectId);
    }

    public Optional<FileRow> findFile(long id) {
        return jdbc.query("""
                SELECT f.id,f.project_id,f.name,f.owner_id,f.columns_json,f.password_hash,f.password_hint,
                       f.created_at,f.updated_at,COALESCE(r.cnt,0) AS row_count
                FROM spreadsheet_file f
                LEFT JOIN (SELECT file_id,COUNT(*) cnt FROM spreadsheet_row GROUP BY file_id) r ON r.file_id=f.id
                WHERE f.id=?
                """, (rs, n) -> mapFile(rs), id).stream().findFirst();
    }

    public void renameFile(long id, String name) {
        jdbc.update("UPDATE spreadsheet_file SET name=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", name, id);
    }

    public void updateColumns(long id, String columnsJson) {
        jdbc.update("UPDATE spreadsheet_file SET columns_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", columnsJson, id);
    }

    public void updateSecurity(long id, String passwordHash, String passwordHint) {
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "UPDATE spreadsheet_file SET password_hash=?,password_hint=?,updated_at=CURRENT_TIMESTAMP WHERE id=?");
            if (passwordHash == null) ps.setNull(1, Types.VARCHAR); else ps.setString(1, passwordHash);
            if (passwordHint == null) ps.setNull(2, Types.VARCHAR); else ps.setString(2, passwordHint);
            ps.setLong(3, id);
            return ps;
        });
    }

    public void touch(long id) {
        jdbc.update("UPDATE spreadsheet_file SET updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
    }

    public boolean deleteFile(long id) {
        return jdbc.update("DELETE FROM spreadsheet_file WHERE id=?", id) == 1;
    }

    public Integer nextRowPosition(long fileId) {
        Integer max = jdbc.queryForObject("SELECT MAX(row_order) FROM spreadsheet_row WHERE file_id=?", Integer.class, fileId);
        return max == null ? 0 : max + 1;
    }

    public long createRow(long fileId, int position, String cellsJson) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO spreadsheet_row(file_id,row_order,cells_json) VALUES(?,?,?)", new String[]{"id"});
            ps.setLong(1, fileId); ps.setInt(2, position); ps.setString(3, cellsJson);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Spreadsheet row id was not generated");
        return key.getKey().longValue();
    }

    public List<RowRow> listRows(long fileId) {
        return jdbc.query("SELECT id,file_id,row_order,cells_json,updated_at FROM spreadsheet_row WHERE file_id=? ORDER BY row_order",
                (rs, n) -> mapRow(rs), fileId);
    }

    public Optional<RowRow> findRow(long rowId) {
        return jdbc.query("SELECT id,file_id,row_order,cells_json,updated_at FROM spreadsheet_row WHERE id=?",
                (rs, n) -> mapRow(rs), rowId).stream().findFirst();
    }

    public void updateRow(long rowId, String cellsJson) {
        jdbc.update("UPDATE spreadsheet_row SET cells_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", cellsJson, rowId);
    }

    public boolean deleteRow(long rowId) {
        return jdbc.update("DELETE FROM spreadsheet_row WHERE id=?", rowId) == 1;
    }

    private static FileRow mapFile(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FileRow(rs.getLong("id"), rs.getLong("project_id"), rs.getString("name"), rs.getLong("owner_id"),
                rs.getString("columns_json"), rs.getString("password_hash"), rs.getString("password_hint"),
                rs.getInt("row_count"), rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("updated_at").toLocalDateTime());
    }

    private static RowRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RowRow(rs.getLong("id"), rs.getLong("file_id"), rs.getInt("row_order"), rs.getString("cells_json"),
                rs.getTimestamp("updated_at").toLocalDateTime());
    }
}
