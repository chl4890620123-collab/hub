package com.hub.service;

import com.hub.model.User;
import com.hub.repository.AdminSubmissionRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@Service
public class AdminSubmissionService {
    private static final long MAX_FILE_BYTES = 100L * 1024L * 1024L;

    private final AdminSubmissionRepository submissions;
    private final ProjectAccessService access;
    private final FileStorageService storage;

    public AdminSubmissionService(AdminSubmissionRepository submissions,
                                  ProjectAccessService access,
                                  FileStorageService storage) {
        this.submissions = submissions;
        this.access = access;
        this.storage = storage;
    }

    public long submit(long projectId, String title, String note, String url, MultipartFile file, User actor) {
        access.requireAccess(projectId, actor);
        if (actor.isAdmin()) throw new AccessDeniedException("관리자는 제출자가 아니라 검토자입니다.");
        String cleanTitle = requireTitle(title);
        String cleanNote = blankToNull(note);
        String cleanUrl = normalizeUrl(url);
        boolean hasFile = file != null && !file.isEmpty();
        if (!hasFile && cleanUrl == null) {
            throw new IllegalArgumentException("파일 또는 URL 중 하나는 제출해 주세요.");
        }

        String fileName = null;
        String contentType = null;
        Long sizeBytes = null;
        String storagePath = null;
        if (hasFile) {
            if (file.getSize() > MAX_FILE_BYTES) throw new IllegalArgumentException("파일은 100MB 이하로 보내 주세요.");
            fileName = safeName(file);
            contentType = file.getContentType();
            sizeBytes = file.getSize();
            try {
                storagePath = storage.save(projectId, fileName, file.getBytes());
            } catch (IOException e) {
                throw new IllegalStateException("파일을 읽는 데 실패했습니다.", e);
            }
        }

        return submissions.create(projectId, actor.id(), cleanTitle, cleanNote, cleanUrl,
                fileName, contentType, sizeBytes, storagePath);
    }

    public List<AdminSubmissionRepository.Submission> list(long projectId, User actor) {
        access.requireAccess(projectId, actor);
        return actor.isAdmin()
                ? submissions.listForProject(projectId)
                : submissions.listForSender(projectId, actor.id());
    }

    public record Download(byte[] data, String fileName, String contentType) {}

    public Download download(long submissionId, User actor) {
        var submission = submissions.find(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("제출 자료를 찾을 수 없습니다."));
        access.requireAccess(submission.projectId(), actor);
        if (!actor.isAdmin() && submission.senderId() != actor.id()) {
            throw new AccessDeniedException("본인이 제출한 자료만 볼 수 있습니다.");
        }
        if (submission.storagePath() == null || submission.fileName() == null) {
            throw new IllegalArgumentException("이 제출에는 다운로드할 파일이 없습니다.");
        }
        return new Download(storage.readTrusted(submission.storagePath()), submission.fileName(), submission.contentType());
    }

    public void delete(long submissionId, User actor) {
        var submission = submissions.find(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("제출 자료를 찾을 수 없습니다."));
        access.requireAdmin(submission.projectId(), actor);
        if (submission.storagePath() != null) storage.deleteStrict(submission.storagePath());
        if (!submissions.delete(submissionId)) throw new IllegalArgumentException("삭제할 제출 자료를 찾을 수 없습니다.");
    }

    private static String requireTitle(String value) {
        String title = value == null ? "" : value.trim();
        if (title.isBlank()) throw new IllegalArgumentException("제출 제목을 입력해 주세요.");
        if (title.length() > 500) throw new IllegalArgumentException("제출 제목은 500자 이하여야 합니다.");
        return title;
    }

    private static String normalizeUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String url = value.trim();
        if (url.length() > 2000) throw new IllegalArgumentException("URL은 2000자 이하여야 합니다.");
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) || uri.getHost() == null) {
                throw new IllegalArgumentException("URL은 http:// 또는 https:// 주소여야 합니다.");
            }
            return uri.toString();
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("URL")) throw e;
            throw new IllegalArgumentException("올바른 URL을 입력해 주세요.");
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (clean.length() > 1000) throw new IllegalArgumentException("메모는 1000자 이하여야 합니다.");
        return clean;
    }

    private static String safeName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return (name == null || name.isBlank()) ? "attachment.bin" : java.nio.file.Path.of(name).getFileName().toString();
    }
}
