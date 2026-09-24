// one trusted local-storage boundary; all reads/deletes are constrained under HUB_STORAGE_ROOT.
package com.hub.service;

import com.hub.config.HubProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class FileStorageService {
    private final Path root;

    public FileStorageService(HubProperties props) {
        this.root = Path.of(props.storageRoot()).toAbsolutePath().normalize();
    }

    public String save(long projectId, String originalName, byte[] data) {
        try {
            Path project = projectDirectory(projectId);
            Files.createDirectories(project);
            String safeName = Path.of(originalName == null ? "upload.bin" : originalName).getFileName().toString();
            Path target = project.resolve(UUID.randomUUID() + "-" + safeName).normalize();
            if (!target.startsWith(project)) throw new IllegalArgumentException("안전하지 않은 파일 경로입니다.");
            Files.write(target, data);
            return target.toString();
        } catch (IOException e) {
            throw new IllegalStateException("파일을 저장하지 못했습니다.", e);
        }
    }

    public byte[] readTrusted(String storagePath) {
        try {
            Path candidate = trustedPath(storagePath);
            return Files.readAllBytes(candidate);
        } catch (IOException e) {
            throw new IllegalStateException("저장된 파일을 읽지 못했습니다.", e);
        }
    }

    public void deleteQuietly(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) return;
        try { Files.deleteIfExists(trustedPath(storagePath)); } catch (Exception ignored) { /* best-effort cleanup only */ }
    }

    /**
     * Permanent-delete paths must not report success while the original file still exists.
     * Missing files are already deleted and therefore count as success; any real filesystem failure
     * is surfaced to the caller instead of being silently ignored.
     */
    public void deleteStrict(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) return;
        try {
            Files.deleteIfExists(trustedPath(storagePath));
        } catch (IOException e) {
            throw new IllegalStateException("원본 파일을 영구 삭제하지 못했습니다. 저장소 권한과 파일 사용 상태를 확인해 주세요.", e);
        }
    }

    private Path trustedPath(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) throw new IllegalArgumentException("저장 파일 경로가 없습니다.");
        Path candidate = Path.of(storagePath).toAbsolutePath().normalize();
        if (!candidate.startsWith(root)) throw new IllegalArgumentException("허용되지 않은 저장 파일 경로입니다.");
        return candidate;
    }

    private Path projectDirectory(long projectId) {
        Path project = root.resolve("project-" + projectId).normalize();
        if (!project.startsWith(root)) throw new IllegalArgumentException("허용되지 않은 프로젝트 저장 경로입니다.");
        return project;
    }
}
