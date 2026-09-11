package com.hub.service;

import com.hub.config.HubProperties;
import com.hub.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalPcReaderServiceTest {
    @TempDir Path temp;

    @Test
    void listsOnlySupportedRegularFilesAndDoesNotTraverseSymlinks() throws Exception {
        Files.writeString(temp.resolve("meeting.txt"), "회의 내용");
        Files.writeString(temp.resolve("ignore.exe"), "x");
        Path ssh = Files.createDirectory(temp.resolve(".ssh"));
        Files.writeString(ssh.resolve("secret.txt"), "secret");

        LocalPcReaderService service = new LocalPcReaderService(properties(temp), mock(DocumentService.class));
        var files = service.listFiles(100);

        assertEquals(1, files.size());
        assertEquals("meeting.txt", files.getFirst().relativePath());
    }

    @Test
    void importsOnlyAFileReturnedFromConfiguredRoot() throws Exception {
        Files.writeString(temp.resolve("memo.md"), "해야 할 일: 검수");
        DocumentService documents = mock(DocumentService.class);
        when(documents.importExternalFile(anyLong(), eq("LOCAL_PC"), anyString(), eq("memo.md"), any(), any(), any(User.class)))
                .thenReturn(42L);
        LocalPcReaderService service = new LocalPcReaderService(properties(temp), documents);
        User user = mock(User.class);

        var imported = service.importFile(7L, "r0", "memo.md", user);

        assertEquals(42L, imported.versionId());
        verify(documents).importExternalFile(eq(7L), eq("LOCAL_PC"), contains("memo.md"), eq("memo.md"), any(), any(), eq(user));
        assertThrows(IllegalArgumentException.class, () -> service.importFile(7L, "r0", "../outside.txt", user));
    }

    private static HubProperties properties(Path root) {
        return new HubProperties(
                "./data", "http://127.0.0.1:8000", true,
                root.toString(), "./config/search-rules.yml", 12000, 2, 3, 16, 30000,
                "", "", "", "", "", "", ""
        );
    }
}
