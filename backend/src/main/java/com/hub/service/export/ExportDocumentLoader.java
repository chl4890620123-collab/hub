package com.hub.service.export;

import com.hub.repository.DocumentRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * "Load document + latest version text" logic shared by every write-side export service
 * (Drive/Slack/Notion). GitHubExportService keeps its own copy of this inline rather than being
 * migrated onto this helper, so the already-proven GitHub export path is left untouched.
 */
@Component
public class ExportDocumentLoader {
    private final DocumentRepository documents;

    public ExportDocumentLoader(DocumentRepository documents) {
        this.documents = documents;
    }

    public record Loaded(DocumentRepository.DocumentMeta meta, String content) {}

    public Loaded load(long projectId, long documentId) {
        var meta = documents.findMeta(documentId)
                .orElseThrow(() -> new IllegalArgumentException("내보낼 문서를 찾을 수 없습니다."));
        if (meta.projectId() != projectId) throw new IllegalArgumentException("현재 프로젝트의 문서가 아닙니다.");
        if (meta.archived() || meta.sourceDeleted()) throw new IllegalArgumentException("보관되거나 삭제된 문서는 내보낼 수 없습니다.");
        var latest = documents.latestVersion(documentId)
                .orElseThrow(() -> new IllegalArgumentException("내보낼 문서 버전을 찾을 수 없습니다."));
        if (documents.isVersionContentPurged(latest.id())) {
            throw new IllegalArgumentException("본문 보존기간이 지난 문서는 원문을 복원한 뒤 내보내 주세요.");
        }
        Map<String, Object> detail = documents.versionDetail(latest.id());
        String content = value(detail, "full_text");
        if (content.isBlank()) throw new IllegalArgumentException("내보낼 문서 본문이 비어 있습니다.");
        return new Loaded(meta, content);
    }

    private static String value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) value = row.get(key.toUpperCase(java.util.Locale.ROOT));
        return value == null ? "" : String.valueOf(value);
    }
}
