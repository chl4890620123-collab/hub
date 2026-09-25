// meeting persistence separates upload from queued STT/analysis and stores the user's local source date explicitly.
package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Repository
public class MeetingRepository {
    public record ProcessingInput(long projectId, String title, String audioPath, String audioFileName,
                                  String audioContentType, LocalDate sourceDate, String status, long createdBy) {}
    public record DeleteInput(long projectId, String audioPath, String status, long createdBy) {}

    private final JdbcTemplate jdbc;
    public MeetingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public java.util.Optional<Long> findByAudioHash(long projectId, String audioSha256) {
        if (audioSha256 == null || audioSha256.isBlank()) return java.util.Optional.empty();
        var ids = jdbc.query("SELECT id FROM meeting WHERE project_id=? AND audio_sha256=? ORDER BY id DESC LIMIT 1", (rs,n)->rs.getLong(1), projectId, audioSha256);
        return ids.stream().findFirst();
    }

    public long create(long projectId, String title, String audioPath, String audioFileName, String audioContentType,
                       String audioSha256, OffsetDateTime meetingAt, LocalDate sourceDate, long userId) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO meeting(project_id,title,audio_path,audio_file_name,audio_content_type,audio_sha256,meeting_at,source_date,stt_status,created_by) VALUES(?,?,?,?,?,?,?,?,'PENDING',?)",
                    new String[]{"id"});
            ps.setLong(1, projectId); ps.setString(2, title); ps.setString(3, audioPath); ps.setString(4, audioFileName); ps.setString(5, audioContentType); ps.setString(6, audioSha256);
            if (meetingAt == null) ps.setNull(7, java.sql.Types.TIMESTAMP_WITH_TIMEZONE); else ps.setTimestamp(7, Timestamp.from(meetingAt.toInstant()));
            if (sourceDate == null) ps.setNull(8, java.sql.Types.DATE); else ps.setDate(8, java.sql.Date.valueOf(sourceDate));
            ps.setLong(9, userId);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Meeting id was not generated");
        return key.getKey().longValue();
    }

    public ProcessingInput processingInput(long meetingId) {
        return jdbc.queryForObject("SELECT project_id,title,audio_path,audio_file_name,audio_content_type,source_date,stt_status,created_by FROM meeting WHERE id=?",
                (rs, n) -> new ProcessingInput(rs.getLong("project_id"), rs.getString("title"), rs.getString("audio_path"),
                        rs.getString("audio_file_name"), rs.getString("audio_content_type"), rs.getObject("source_date", LocalDate.class), rs.getString("stt_status"), rs.getLong("created_by")), meetingId);
    }

    public DeleteInput deleteInput(long meetingId) {
        return jdbc.queryForObject("SELECT project_id,audio_path,stt_status,created_by FROM meeting WHERE id=?",
                (rs, n) -> new DeleteInput(rs.getLong("project_id"), rs.getString("audio_path"), rs.getString("stt_status"), rs.getLong("created_by")), meetingId);
    }

    /**
     * Removes a completed meeting while preserving already-confirmed work items. Confirmed/unconfirmed todos and
     * decisions keep their own text, but their source pointer is detached. Evidence grounded only in transcript
     * segments is removed before the meeting row so foreign keys remain valid. The searchable transcript document
     * created for this meeting is removed as part of the same transaction.
     */
    public void deleteCompleted(long projectId, long meetingId) {
        jdbc.update("DELETE FROM evidence WHERE transcript_segment_id IN (SELECT id FROM transcript_segment WHERE meeting_id=?)", meetingId);
        jdbc.update("UPDATE todo SET source_meeting_id=NULL WHERE source_meeting_id=?", meetingId);
        jdbc.update("UPDATE decision_candidate SET source_meeting_id=NULL WHERE source_meeting_id=?", meetingId);
        jdbc.update("DELETE FROM ai_run WHERE meeting_id=?", meetingId);
        jdbc.update("DELETE FROM processing_job WHERE project_id=? AND target_type='MEETING' AND target_id=?", projectId, meetingId);
        jdbc.update("DELETE FROM timeline_event WHERE project_id=? AND source_type='MEETING' AND source_id=?", projectId, meetingId);
        jdbc.update("DELETE FROM document WHERE project_id=? AND source_type='MEETING_TRANSCRIPT' AND source_identifier=?", projectId, "meeting:" + meetingId);
        int deleted = jdbc.update("DELETE FROM meeting WHERE id=? AND project_id=?", meetingId, projectId);
        if (deleted != 1) throw new IllegalArgumentException("삭제할 회의록을 찾을 수 없습니다.");
    }

    public void markProcessing(long meetingId) { jdbc.update("UPDATE meeting SET stt_status='PROCESSING' WHERE id=?", meetingId); }
    public void deleteSegments(long meetingId) { jdbc.update("DELETE FROM transcript_segment WHERE meeting_id=?", meetingId); }

    public long createSegment(long meetingId, int index, Long startMs, Long endMs, String speaker, String text) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO transcript_segment(meeting_id,segment_index,start_ms,end_ms,speaker,text) VALUES(?,?,?,?,?,?)", new String[]{"id"});
            ps.setLong(1, meetingId); ps.setInt(2, index);
            if (startMs == null) ps.setNull(3, java.sql.Types.BIGINT); else ps.setLong(3, startMs);
            if (endMs == null) ps.setNull(4, java.sql.Types.BIGINT); else ps.setLong(4, endMs);
            ps.setString(5, speaker); ps.setString(6, text);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Transcript segment id was not generated");
        return key.getKey().longValue();
    }

    public java.util.List<java.util.Map<String,Object>> segments(long meetingId) {
        return jdbc.queryForList("SELECT id,start_ms,end_ms,speaker,text FROM transcript_segment WHERE meeting_id=? ORDER BY segment_index", meetingId);
    }
    public void completeTranscript(long meetingId, String transcript) { jdbc.update("UPDATE meeting SET transcript_text=?,stt_status='READY' WHERE id=?", transcript, meetingId); }
    public void fail(long meetingId) { jdbc.update("UPDATE meeting SET stt_status='FAILED' WHERE id=?", meetingId); }
    public void lockMeeting(long meetingId) { jdbc.queryForObject("SELECT id FROM meeting WHERE id=? FOR UPDATE", Long.class, meetingId); }
    public String transcript(long meetingId) { return jdbc.queryForObject("SELECT transcript_text FROM meeting WHERE id=?", String.class, meetingId); }
    public long projectId(long meetingId) { return jdbc.queryForObject("SELECT project_id FROM meeting WHERE id=?", Long.class, meetingId); }

    private static final String STT_RETENTION_PLACEHOLDER = "[보관 기간이 지나 원문 음성 인식 결과가 정리되었습니다]";

    public int purgeTranscriptsOlderThan(LocalDate cutoff) {
        java.sql.Date cutoffDate = java.sql.Date.valueOf(cutoff);
        jdbc.update("""
                UPDATE transcript_segment SET text=?
                WHERE text<>? AND meeting_id IN (SELECT id FROM meeting WHERE created_at<?)
                """, STT_RETENTION_PLACEHOLDER, STT_RETENTION_PLACEHOLDER, cutoffDate);
        return jdbc.update(
                "UPDATE meeting SET transcript_text=? WHERE created_at<? AND transcript_text IS NOT NULL AND transcript_text<>?",
                STT_RETENTION_PLACEHOLDER, cutoffDate, STT_RETENTION_PLACEHOLDER);
    }
}
