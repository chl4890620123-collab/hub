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
}
