// File/manual import deduplicates by content hash and versions stable source identities before AI analysis.
package com.hub.service;

import com.hub.dto.AiDtos;
import com.hub.model.User;
import com.hub.repository.DocumentRepository;
import com.hub.repository.TimelineRepository;
import com.hub.util.Hashing;
import com.hub.util.UnicodeText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final int MAX_TEXT_LENGTH = 2_000_000;
    private static final long MAX_FILE_BYTES = 100L * 1024L * 1024L;

    private final DocumentRepository documents;
    private final DocumentParserService parser;
    private final ParagraphChunker chunker;
    private final FileStorageService storage;
    private final AiClient ai;
    private final TimelineRepository timeline;
    private final VectorIndexService vectorIndex;
    private final TransactionTemplate transaction;

    public DocumentService(DocumentRepository documents,
                           DocumentParserService parser,
                           ParagraphChunker chunker,
                           FileStorageService storage,
                           AiClient ai,
                           TimelineRepository timeline,
                           VectorIndexService vectorIndex,
                           org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.documents = documents;
        this.parser = parser;
        this.chunker = chunker;
        this.storage = storage;
        this.ai = ai;
        this.timeline = timeline;
        this.vectorIndex = vectorIndex;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Parses/OCRs before writing the source file or opening a DB transaction. A failed parser therefore
     * does not leave a new database row, and external API latency never holds DB locks.
     */
    public long upload(long projectId, MultipartFile file, User user) {
        try {
            if (file == null || file.isEmpty()) throw new IllegalArgumentException("빈 파일은 가져올 수 없습니다.");
            if (file.getSize() > MAX_FILE_BYTES) throw new IllegalArgumentException("파일은 100MB 이하로 올려 주세요.");
            byte[] bytes = file.getBytes();
            String name = safeTitle(file.getOriginalFilename(), "upload.bin");
            String hash = Hashing.sha256(bytes);
            var duplicate = documents.findVersionByHash(projectId, "FILE", hash);
            if (duplicate.isPresent()) return duplicate.get();

            String text = extractText(name, file.getContentType(), bytes);
            validateExtractedText(text);
            String stored = storage.save(projectId, name, bytes);
            String sourceIdentifier = "file:" + normalizeSourceName(name);
            try {
                return saveText(projectId, "FILE", sourceIdentifier, name, stored, text, bytes, user);
            } catch (RuntimeException e) {
                storage.deleteQuietly(stored);
                throw e;
            }
        } catch (IOException e) {
            throw new IllegalStateException("File read failed", e);
        }
    }

    public long manualText(long projectId, String title, String text, User user) {
        String safeTitle = safeTitle(title, "Manual text");
        validateExtractedText(text);
        byte[] bytes = text.strip().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = Hashing.sha256(bytes);
        var duplicate = documents.findVersionByHash(projectId, "MANUAL_TEXT", hash);
        if (duplicate.isPresent()) return duplicate.get();
        return saveText(projectId, "MANUAL_TEXT", "manual:" + normalizeSourceName(safeTitle), safeTitle, null, text.strip(), bytes, user);
    }

    /**
     * Browser recordings keep their original meeting/evidence rows, but also create one searchable
     * transcript snapshot in the shared document index. This makes a recorded meeting discoverable
     * from the same unified search without running document analysis a second time.
     */
    public long importMeetingTranscript(long projectId, long meetingId, String title, String transcript, long createdBy) {
        String safeTitle = safeTitle(title, "회의 기록");
        String safeText = UnicodeText.nfc(transcript == null ? "" : transcript).strip();
        validateExtractedText(safeText);
        byte[] bytes = safeText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return saveText(projectId, "MEETING_TRANSCRIPT", "meeting:" + meetingId, safeTitle, null, safeText, bytes, createdBy);
    }

    public void updateSummary(long versionId, String summary) {
        documents.updateSummary(versionId, UnicodeText.nfc(summary == null ? "" : summary));
    }

    public long importExternalText(long projectId,
                                   String sourceType,
                                   String sourceIdentifier,
                                   String title,
                                   String text,
                                   User user) {
        validateExternalIdentity(sourceType, sourceIdentifier);
        validateExtractedText(text);
        String safeTitle = safeTitle(title, sourceType + " item");
        return saveText(
                projectId,
                sourceType,
                sourceIdentifier,
                safeTitle,
                null,
                text.strip(),
                text.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                user
        );
    }

    public long importExternalFile(long projectId,
                                   String sourceType,
                                   String sourceIdentifier,
                                   String filename,
                                   String contentType,
                                   byte[] bytes,
                                   User user) {
        validateExternalIdentity(sourceType, sourceIdentifier);
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("External file is empty");

        String safeName = safeTitle(filename, "external-file.bin");
        String hash = Hashing.sha256(bytes);
        var existing = documents.findDocumentId(projectId, sourceType, sourceIdentifier);
        if (existing.isPresent()) {
            var latest = documents.latestVersion(existing.get());
            if (latest.isPresent() && latest.get().sha256().equals(hash)) return latest.get().id();
        }

        String text = extractText(safeName, contentType, bytes);
        validateExtractedText(text);
        String stored = storage.save(projectId, safeName, bytes);
        try {
            return saveText(projectId, sourceType, sourceIdentifier, safeName, stored, text, bytes, user);
        } catch (RuntimeException e) {
            storage.deleteQuietly(stored);
            throw e;
        }
    }

    private String extractText(String filename, String contentType, byte[] bytes) {

        String parsed = "";
        try {
            parsed = parser.parse(filename, bytes);
            if (!needsOcrFallback(filename, contentType, bytes, parsed)) return parsed;
            if (!ocrEligible(filename, contentType)) return parsed;
            log.info("Parser text for {} looks sparse/noisy; trying OCR quality fallback", filename);
        } catch (IllegalArgumentException parserFailure) {
            if (!ocrEligible(filename, contentType)) throw parserFailure;
            log.info("Parser could not extract {}; trying OCR fallback", filename);
        }

        if (!ocrEligible(filename, contentType)) return parsed;
        try {
            String ocr = ai.ocr(filename, contentType, bytes);
            return textQualityScore(ocr) >= textQualityScore(parsed) ? ocr : parsed;
        } catch (RuntimeException ocrFailure) {
            if (parsed != null && !parsed.isBlank()) {
                log.warn("OCR fallback failed for {}; keeping parser text: {}", filename, ocrFailure.getMessage());
                return parsed;
            }
            throw ocrFailure;
        }
    }

    /**
     * Some scanned PDFs expose only a title or a few broken glyphs to Tika. Treating any non-empty
     * string as successful parsing makes those documents effectively unsearchable, so OCR is tried
     * when the extracted text is implausibly sparse compared with the source size or mostly noise.
     */
    private boolean needsOcrFallback(String filename, String contentType, byte[] bytes, String text) {
        if (!ocrEligible(filename, contentType)) return false;
        String normalized = text == null ? "" : text.replaceAll("\\s+", "").trim();
        if (normalized.isBlank() || normalized.length() < 40) return true;
        if (textQualityScore(text) < 0.45d) return true;
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        boolean pdf = type.contains("pdf") || name.endsWith(".pdf");
        return pdf && bytes != null && bytes.length > 50_000 && normalized.length() < 180;
    }

    private static double textQualityScore(String text) {
        if (text == null || text.isBlank()) return 0.0d;
        int useful = 0;
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) continue;
            visible++;
            if (Character.isLetterOrDigit(c) || (c >= 0xAC00 && c <= 0xD7A3)) useful++;
        }
        if (visible == 0) return 0.0d;
        double ratio = useful / (double) visible;
        double lengthFactor = Math.min(1.0d, useful / 120.0d);
        return ratio * 0.75d + lengthFactor * 0.25d;
    }

    private boolean ocrEligible(String filename, String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return type.startsWith("image/") || type.contains("pdf") || name.endsWith(".pdf")
                || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".webp");
    }

    private long saveText(long projectId,
                          String sourceType,
                          String sourceIdentifier,
                          String title,
                          String storagePath,
                          String text,
                          byte[] hashBytes,
                          User user) {
        return saveText(projectId, sourceType, sourceIdentifier, title, storagePath, text, hashBytes, user.id());
    }

    private long saveText(long projectId,
                          String sourceType,
                          String sourceIdentifier,
                          String title,
                          String storagePath,
                          String text,
                          byte[] hashBytes,
                          long userId) {
        final String normalizedTitle = UnicodeText.nfc(title);
        final String normalizedText = UnicodeText.nfc(text);
        List<String> chunks = chunker.chunk(normalizedText);
        if (chunks.isEmpty()) throw new IllegalArgumentException("No searchable text was produced");
        EmbeddingAttempt embedding = embedBestEffort(chunks);
        String hash = Hashing.sha256(hashBytes);

        Long versionId = transaction.execute(status -> {
            // A project-level row lock is intentionally brief and database-portable. It closes the
            // "first upload" race before a document exists, while parsing/OCR/embedding remain outside
            // the transaction so long-running AI work never holds this lock.
            documents.lockProject(projectId);
            long documentId = documents.findDocumentId(projectId, sourceType, sourceIdentifier)
                    .orElseGet(() -> documents.createDocument(
                            projectId, sourceType, sourceIdentifier, normalizedTitle, storagePath, userId
                    ));

            // Prevent two sync requests from allocating the same version number.
            documents.lockDocument(documentId);
            var latest = documents.latestVersion(documentId);
            if (latest.isPresent() && latest.get().sha256().equals(hash)) {
                documents.updateSourceMetadata(documentId, normalizedTitle, storagePath);
                return latest.get().id();
            }

            documents.updateSourceMetadata(documentId, normalizedTitle, storagePath);
            long version = documents.createVersion(documentId, hash, normalizedText, "READY");
            List<Long> chunkIds = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                chunkIds.add(documents.createChunk(
                        version,
                        i,
                        "P-" + String.format("%04d", i + 1),
                        chunks.get(i)
                ));
            }
            if (embedding.ready() && embedding.vectors().size() == chunkIds.size()) {
                try {
                    for (int i = 0; i < chunkIds.size(); i++) {
                        vectorIndex.store(chunkIds.get(i), embedding.vectors().get(i));
                    }
                    documents.markEmbeddingStatus(version, "READY", null, true);
                } catch (RuntimeException storeFailure) {
                    log.warn("Embedding storage failed; queued for retry: {}", storeFailure.getMessage());
                    documents.markEmbeddingStatus(version, "FAILED", storeFailure.getMessage(), true);
                }
            } else {
                documents.markEmbeddingStatus(version, "FAILED", embedding.error(), true);
            }

            timeline.append(
                    projectId,
                    latest.isPresent() ? "DOCUMENT_UPDATED" : "DOCUMENT_IMPORTED",
                    normalizedTitle,
                    sourceType,
                    LocalDateTime.now(),
                    "DOCUMENT_VERSION",
                    version
            );
            return version;
        });
        if (versionId == null) throw new IllegalStateException("Document transaction returned no version id");
        return versionId;
    }

    private EmbeddingAttempt embedBestEffort(List<String> chunks) {
        final int batchSize = 64;
        List<List<Float>> vectors = new ArrayList<>(chunks.size());
        try {
            for (int start = 0; start < chunks.size(); start += batchSize) {
                int end = Math.min(start + batchSize, chunks.size());
                List<String> batch = chunks.subList(start, end);
                AiDtos.EmbedResponse embedded = ai.embed(batch, "passage");
                if (embedded == null || embedded.dimensions() != 768 || embedded.vectors() == null
                        || embedded.vectors().size() != batch.size()) {
                    throw new IllegalStateException("Embedding response shape does not match vector(768)");
                }
                vectors.addAll(embedded.vectors());
            }
            return new EmbeddingAttempt(List.copyOf(vectors), true, null);
        } catch (Exception e) {
            log.warn("Embedding failed; continuing with text-search fallback: {}", e.getMessage());
            return new EmbeddingAttempt(List.of(), false, e.getMessage());
        }
    }

    private record EmbeddingAttempt(List<List<Float>> vectors, boolean ready, String error) {}

    private static void validateExternalIdentity(String sourceType, String sourceIdentifier) {
        if (sourceType == null || sourceType.isBlank()) throw new IllegalArgumentException("Source type is required");
        if (sourceIdentifier == null || sourceIdentifier.isBlank()) {
            throw new IllegalArgumentException("Source identifier is required");
        }
    }

    private static void validateExtractedText(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("No text could be extracted");
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Extracted text is too large; split the source into smaller files");
        }
    }


    private static String normalizeSourceName(String value) {
        String normalized = UnicodeText.nfc(value == null ? "source" : value).trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-")
                .replaceAll("[^\\p{L}\\p{N}._-]+", "-");
        if (normalized.isBlank()) normalized = "source";
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
    }

    private static String safeTitle(String value, String fallback) {
        String title = UnicodeText.nfc(value == null ? "" : value).trim();
        if (title.isBlank()) title = fallback;
        return title.length() <= 500 ? title : title.substring(0, 500);
    }
}
