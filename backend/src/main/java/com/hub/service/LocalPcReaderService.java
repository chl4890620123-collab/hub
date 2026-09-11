package com.hub.service;

import com.hub.config.HubProperties;
import com.hub.model.User;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Read-only local-folder ingestion boundary. The browser never submits arbitrary absolute paths;
 * it selects a server-configured root id and a relative path returned by {@link #listFiles(int)}.
 */
@Service
public class LocalPcReaderService {
    private static final long MAX_FILE_BYTES = 100L * 1024 * 1024;
    private static final int MAX_DEPTH = 6;
    private static final int MAX_LIST_LIMIT = 500;
    private static final Set<String> EXTENSIONS = Set.of("pdf", "docx", "txt", "md", "png", "jpg", "jpeg", "webp");
    private static final Set<String> BLOCKED_SEGMENTS = Set.of(
            ".ssh", ".aws", "appdata", "credentials", "credential", "cookies", "browser", "system32", "windows"
    );

    private final HubProperties props;
    private final DocumentService documents;

    public LocalPcReaderService(HubProperties props, DocumentService documents) {
        this.props = props;
        this.documents = documents;
    }

    public List<FileView> listFiles(int requestedLimit) {
        int limit = Math.max(1, Math.min(MAX_LIST_LIMIT, requestedLimit));
        List<ConfiguredRoot> roots = configuredRoots();
        List<FileView> result = new ArrayList<>();
        for (ConfiguredRoot root : roots) {
            if (result.size() >= limit || !Files.isDirectory(root.path(), LinkOption.NOFOLLOW_LINKS)) continue;
            try (var paths = Files.walk(root.path(), MAX_DEPTH)) {
                paths.filter(path -> isImportable(root.path(), path))
                        .sorted(Comparator.comparing(path -> root.path().relativize(path).toString().toLowerCase(Locale.ROOT)))
                        .limit(limit - result.size())
                        .forEach(path -> result.add(toView(root, path)));
            } catch (IOException e) {
                throw new IllegalStateException("허용된 PC 폴더를 읽을 수 없습니다: " + root.displayPath(), e);
            }
        }
        return result;
    }

    public ImportedFile importFile(long projectId, String rootId, String relativePath, User user) {
        ConfiguredRoot root = configuredRoots().stream()
                .filter(value -> value.id().equals(rootId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("허용되지 않은 PC 폴더입니다."));
        if (relativePath == null || relativePath.isBlank()) throw new IllegalArgumentException("가져올 파일을 선택해 주세요.");
        try {
            Path realRoot = root.path().toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path candidate = realRoot.resolve(relativePath).normalize();
            if (!candidate.startsWith(realRoot)) throw new IllegalArgumentException("허용 폴더 밖의 파일에는 접근할 수 없습니다.");
            if (!isImportable(realRoot, candidate)) throw new IllegalArgumentException("지원하지 않거나 접근할 수 없는 파일입니다.");
            Path realFile = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!realFile.startsWith(realRoot)) throw new IllegalArgumentException("허용 폴더 밖의 파일에는 접근할 수 없습니다.");
            long size = Files.size(realFile);
            if (size > MAX_FILE_BYTES) throw new IllegalArgumentException("파일이 너무 큽니다. 100MB 이하 파일을 사용해 주세요.");
            byte[] bytes = Files.readAllBytes(realFile);
            String normalizedRelative = realRoot.relativize(realFile).toString().replace('\\', '/');
            String contentType = Files.probeContentType(realFile);
            long versionId = documents.importExternalFile(
                    projectId,
                    "LOCAL_PC",
                    "local-pc:" + root.id() + ":" + normalizedRelative,
                    realFile.getFileName().toString(),
                    contentType,
                    bytes,
                    user
            );
            return new ImportedFile(versionId, root.id(), normalizedRelative, realFile.getFileName().toString());
        } catch (IOException e) {
            throw new IllegalStateException("PC 파일을 읽는 중 오류가 발생했습니다.", e);
        }
    }

    public List<RootView> roots() {
        return configuredRoots().stream()
                .map(root -> new RootView(root.id(), root.displayPath(), Files.isDirectory(root.path(), LinkOption.NOFOLLOW_LINKS)))
                .toList();
    }

    private List<ConfiguredRoot> configuredRoots() {
        String raw = props.localReaderRoots();
        if (raw == null || raw.isBlank()) return List.of();
        List<ConfiguredRoot> roots = new ArrayList<>();
        String[] values = raw.split("\\|");
        for (String value : values) {
            String clean = value.trim();
            if (clean.isBlank()) continue;
            Path path = Paths.get(clean).toAbsolutePath().normalize();
            if (containsBlockedSegment(path)) continue;
            roots.add(new ConfiguredRoot("r" + roots.size(), path, path.toString()));
        }
        return List.copyOf(roots);
    }

    private static boolean isImportable(Path root, Path path) {
        try {
            if (!path.normalize().startsWith(root.normalize())) return false;
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false;
            if (containsBlockedSegment(root.relativize(path))) return false;
            if (!supported(path)) return false;
            long size = Files.size(path);
            return size > 0 && size <= MAX_FILE_BYTES;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static FileView toView(ConfiguredRoot root, Path path) {
        try {
            String relative = root.path().relativize(path).toString().replace('\\', '/');
            return new FileView(
                    root.id(),
                    relative,
                    path.getFileName().toString(),
                    Files.size(path),
                    Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant()
            );
        } catch (IOException e) {
            throw new IllegalStateException("PC 파일 정보를 읽을 수 없습니다.", e);
        }
    }

    private static boolean supported(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return false;
        return EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static boolean containsBlockedSegment(Path path) {
        for (Path segment : path) {
            if (BLOCKED_SEGMENTS.contains(segment.toString().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private record ConfiguredRoot(String id, Path path, String displayPath) {}
    public record RootView(String id, String path, boolean available) {}
    public record FileView(String rootId, String relativePath, String name, long size, Instant lastModified) {}
    public record ImportedFile(long versionId, String rootId, String relativePath, String name) {}
}
