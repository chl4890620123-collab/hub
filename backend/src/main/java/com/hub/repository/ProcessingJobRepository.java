// single persistence boundary for queued AI work; request_key prevents duplicate jobs from retries/double-clicks.
package com.hub.repository;

import com.hub.model.ProcessingJob;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class ProcessingJobRepository {
    public record Lease(long id, boolean shouldRun) {}

    private static final String SELECT =
            "SELECT id,project_id,job_type,target_type,target_id,requester_user_id,status,progress,error_code,error_message,result_json,created_at,updated_at FROM processing_job";

    private final JdbcTemplate jdbc;
    public ProcessingJobRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Lease createOrReuse(long projectId, String jobType, String targetType, long targetId, String requestKey) {
        return createOrReuse(projectId, jobType, targetType, targetId, requestKey, null, false);
    }

    public Lease createOrReuseForUser(long projectId, String jobType, String targetType, long targetId,
                                      String requestKey, long requesterUserId) {
        return createOrReuse(projectId, jobType, targetType, targetId, requestKey, requesterUserId, false);
    }

    public Lease createOrReuseRerunnable(long projectId, String jobType, String targetType, long targetId, String requestKey) {
        return createOrReuse(projectId, jobType, targetType, targetId, requestKey, null, true);
    }

    public Lease createOrReuseRerunnableForUser(long projectId, String jobType, String targetType, long targetId,
                                                String requestKey, long requesterUserId) {
        return createOrReuse(projectId, jobType, targetType, targetId, requestKey, requesterUserId, true);
    }

    private Lease createOrReuse(long projectId, String jobType, String targetType, long targetId,
                                String requestKey, Long requesterUserId, boolean rerunnable) {
        Optional<ProcessingJob> existing = findByRequestKey(requestKey);
        if (existing.isPresent()) return rerunnable ? reviveIfTerminal(existing.get()) : reviveIfFailed(existing.get());
        try {
            KeyHolder key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO processing_job(project_id,job_type,target_type,target_id,requester_user_id,status,request_key,progress,updated_at) VALUES(?,?,?,?,?,'PENDING',?,0,CURRENT_TIMESTAMP)",
                        new String[]{"id"});
                ps.setLong(1, projectId);
                ps.setString(2, jobType);
                ps.setString(3, targetType);
                ps.setLong(4, targetId);
                if (requesterUserId == null) ps.setNull(5, Types.BIGINT); else ps.setLong(5, requesterUserId);
                ps.setString(6, requestKey);
                return ps;
            }, key);
            if (key.getKey() == null) throw new IllegalStateException("Processing job id was not generated");
            return new Lease(key.getKey().longValue(), true);
        } catch (DuplicateKeyException race) {
            ProcessingJob found = findByRequestKey(requestKey).orElseThrow();
            return rerunnable ? reviveIfTerminal(found) : reviveIfFailed(found);
        }
    }

    private Lease reviveIfFailed(ProcessingJob existing) {
        if (!"FAILED".equals(existing.status())) return new Lease(existing.id(), false);
        int changed = jdbc.update("UPDATE processing_job SET status='PENDING',progress=0,error_code=NULL,error_message=NULL,result_json=NULL,started_at=NULL,finished_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='FAILED'", existing.id());
        return new Lease(existing.id(), changed == 1);
    }

    private Lease reviveIfTerminal(ProcessingJob existing) {
        if (!"FAILED".equals(existing.status()) && !"SUCCESS".equals(existing.status())) return new Lease(existing.id(), false);
        int changed = jdbc.update("UPDATE processing_job SET status='PENDING',progress=0,error_code=NULL,error_message=NULL,result_json=NULL,started_at=NULL,finished_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status IN ('FAILED','SUCCESS')", existing.id());
        return new Lease(existing.id(), changed == 1);
    }

    public int failInterruptedJobs() {
        return jdbc.update("UPDATE processing_job SET status='FAILED',error_code='INTERRUPTED',error_message='Server restarted before this job finished. Retry the same source to resume safely.',finished_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE status IN ('PENDING','PROCESSING')");
    }

    public void start(long id) {
        jdbc.update("UPDATE processing_job SET status='PROCESSING',progress=5,started_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'", id);
    }

    public void progress(long id, int progress) {
        jdbc.update("UPDATE processing_job SET progress=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PROCESSING'", Math.max(0, Math.min(progress, 99)), id);
    }

    public void success(long id, String resultJson) {
        jdbc.update("UPDATE processing_job SET status='SUCCESS',progress=100,result_json=?,error_code=NULL,error_message=NULL,finished_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?", resultJson, id);
    }

    public void fail(long id, String code, String message) {
        jdbc.update("UPDATE processing_job SET status='FAILED',error_code=?,error_message=?,finished_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?", code, safe(message), id);
    }

    public ProcessingJob find(long id) {
        return rows(SELECT + " WHERE id=?", id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("처리 작업을 찾을 수 없습니다."));
    }

    public List<ProcessingJob> recent(long projectId) {
        return rows(SELECT + " WHERE project_id=? ORDER BY updated_at DESC,id DESC LIMIT 20", projectId);
    }

    public List<ProcessingJob> recentVisible(long projectId, long userId) {
        return rows(SELECT + " WHERE project_id=? AND (requester_user_id IS NULL OR requester_user_id=?) ORDER BY updated_at DESC,id DESC LIMIT 20",
                projectId, userId);
    }

    public Optional<ProcessingJob> findByRequestKey(String requestKey) {
        return rows(SELECT + " WHERE request_key=?", requestKey).stream().findFirst();
    }

    private List<ProcessingJob> rows(String sql, Object... args) {
        return jdbc.query(sql, (rs, n) -> new ProcessingJob(
                rs.getLong("id"), rs.getLong("project_id"), rs.getString("job_type"), rs.getString("target_type"),
                rs.getObject("target_id", Long.class), rs.getObject("requester_user_id", Long.class),
                rs.getString("status"), rs.getInt("progress"), rs.getString("error_code"),
                rs.getString("error_message"), rs.getString("result_json"),
                rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("updated_at").toLocalDateTime()), args);
    }

    private static String safe(String message) {
        if (message == null || message.isBlank()) return "Background processing failed";
        String clean = message.replaceAll("(?i)(token|secret|password)=[^\\s&]+", "$1=[REDACTED]");
        return clean.length() > 1000 ? clean.substring(0, 1000) : clean;
    }
}
