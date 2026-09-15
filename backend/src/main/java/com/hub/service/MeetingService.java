// web meeting upload is fast; queued STT/AI work reads the stored audio and persists grounded TODO candidates.
package com.hub.service;

import com.hub.dto.AiDtos;
import com.hub.model.User;
import com.hub.repository.MeetingRepository;
import com.hub.repository.TimelineRepository;
import com.hub.util.Hashing;
import com.hub.util.UnicodeText;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.function.IntConsumer;

@Service
public class MeetingService {
    private static final long MAX_AUDIO_BYTES = 100L * 1024L * 1024L;

    public record UploadResult(long meetingId) {}
    public record MeetingResult(long meetingId, AiDtos.AnalyzeResponse analysis) {}

    private final MeetingRepository meetings;
    private final FileStorageService storage;
    private final AiClient ai;
    private final AnalysisService analysis;
    private final TimelineRepository timeline;
    private final DocumentService documents;
    private final SensitiveDataMaskingService piiMasking;

    public MeetingService(MeetingRepository meetings, FileStorageService storage, AiClient ai,
                          AnalysisService analysis, TimelineRepository timeline, DocumentService documents,
                          SensitiveDataMaskingService piiMasking) {
        this.meetings = meetings; this.storage = storage; this.ai = ai; this.analysis = analysis; this.timeline = timeline; this.documents = documents;
        this.piiMasking = piiMasking;
    }

    public UploadResult createUpload(long projectId, String title, OffsetDateTime meetingAt, MultipartFile audio, User user) {
        validateInput(title, audio);
        title = UnicodeText.nfc(title).trim();
        String storedPath = null;
        try {
            byte[] bytes = audio.getBytes();
            String audioHash = Hashing.sha256(bytes);
            var duplicate = meetings.findByAudioHash(projectId, audioHash);
            if (duplicate.isPresent()) return new UploadResult(duplicate.get());
            String filename = safeAudioName(audio.getOriginalFilename());
            String contentType = audio.getContentType() == null ? "application/octet-stream" : audio.getContentType();
            storedPath = storage.save(projectId, filename, bytes);
            LocalDate sourceDate = meetingAt == null ? LocalDate.now() : meetingAt.toLocalDate();
            long meetingId;
            try {
                meetingId = meetings.create(projectId, title.trim(), storedPath, filename, contentType, audioHash, meetingAt, sourceDate, user.id());
            } catch (DuplicateKeyException duplicateRace) {
                // Two browser retries can pass the pre-check at the same time. The DB hash index is the final guard.
                storage.deleteQuietly(storedPath);
                storedPath = null;
                long existingId = meetings.findByAudioHash(projectId, audioHash)
                        .orElseThrow(() -> new IllegalStateException("Duplicate meeting was detected but the existing record could not be resolved", duplicateRace));
                return new UploadResult(existingId);
            }
            timeline.append(projectId, "MEETING_UPLOADED", title.trim(), "웹 회의 음성 업로드 완료 · STT 대기",
                    LocalDateTime.now(), "MEETING", meetingId);
            return new UploadResult(meetingId);
        } catch (Exception error) {
            if (storedPath != null) storage.deleteQuietly(storedPath);
            throw new IllegalStateException("회의 음성을 저장하지 못했습니다. 다시 시도해 주세요.", error);
        }
    }

    public MeetingResult processStored(long meetingId, IntConsumer progress) {
        MeetingRepository.ProcessingInput input = meetings.processingInput(meetingId);
        if ("READY".equals(input.status())) {
            String transcript = UnicodeText.nfc(meetings.transcript(meetingId));
            long transcriptVersion = documents.importMeetingTranscript(
                    input.projectId(), meetingId, input.title(), transcript, input.createdBy());
            AiDtos.AnalyzeResponse analyzed = analysis.analyzeMeeting(input.projectId(), meetingId, input.sourceDate());
            documents.updateSummary(transcriptVersion, analyzed.summary());
            return new MeetingResult(meetingId, analyzed);
        }
        meetings.markProcessing(meetingId);
        progress.accept(20);
        byte[] bytes = storage.readTrusted(input.audioPath());
        AiDtos.SttResponse result = ai.stt(input.audioFileName(), input.audioContentType(), bytes);
        if (result == null || result.text() == null || result.text().isBlank()) {
            throw new IllegalStateException("회의 음성에서 내용을 확인하지 못했습니다. 녹음 상태를 확인해 다시 시도해 주세요.");
        }
        // Masked immediately after STT returns: the resident-number/card/phone/email plaintext
        // never reaches transcript storage, search indexing, or the AI analysis call below.
        String transcript = piiMasking.mask(UnicodeText.nfc(result.text()).strip());

        progress.accept(55);
        meetings.deleteSegments(meetingId); // retry-safe: never duplicate transcript segments.
        if (result.segments() == null || result.segments().isEmpty()) {
            meetings.createSegment(meetingId, 0, null, null, "화자 미확인", transcript);
        } else {
            int index = 0;
            for (AiDtos.SttSegment segment : result.segments()) {
                if (segment.text() == null || segment.text().isBlank()) continue;
                meetings.createSegment(meetingId, index++, segment.startMs(), segment.endMs(),
                        UnicodeText.nfc(segment.speaker()), piiMasking.mask(UnicodeText.nfc(segment.text()).trim()));
            }
        }
        meetings.completeTranscript(meetingId, transcript);
        timeline.append(input.projectId(), "MEETING_TRANSCRIBED", input.title(),
                transcript.substring(0, Math.min(300, transcript.length())), LocalDateTime.now(), "MEETING", meetingId);

        // Put the transcript into the same searchable document index used by file imports.
        // Analysis remains meeting-grounded, so TODO/decision candidates are not duplicated.
        long transcriptVersion = documents.importMeetingTranscript(
                input.projectId(), meetingId, input.title(), transcript, input.createdBy());
        progress.accept(75);
        AiDtos.AnalyzeResponse analyzed = analysis.analyzeMeeting(input.projectId(), meetingId, input.sourceDate());
        documents.updateSummary(transcriptVersion, analyzed.summary());
        return new MeetingResult(meetingId, analyzed);
    }

    public void markFailed(long meetingId) { meetings.fail(meetingId); }

    private void validateInput(String title, MultipartFile audio) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("회의 제목을 입력해 주세요.");
        if (title.trim().length() > 500) throw new IllegalArgumentException("회의 제목은 500자 이하로 입력해 주세요.");
        if (audio == null || audio.isEmpty()) throw new IllegalArgumentException("녹음 또는 음성 파일을 선택해 주세요.");
        if (audio.getSize() > MAX_AUDIO_BYTES) throw new IllegalArgumentException("음성 파일은 100MB 이하로 올려 주세요.");
        String type = audio.getContentType();
        if (type != null && !type.isBlank() && !type.startsWith("audio/") && !"application/octet-stream".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("음성 파일만 올릴 수 있습니다.");
        }
    }

    private String safeAudioName(String originalName) {
        if (originalName == null || originalName.isBlank()) return "meeting.wav";
        String base = java.nio.file.Path.of(originalName).getFileName().toString();
        return base.isBlank() ? "meeting.wav" : base;
    }
}
