package org.example.mypaintings.painting.attachedimages;

import org.example.mypaintings.exception.files.FileStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("FileStorageService Test Suite")
class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService storageService;
    private Path root;

    @BeforeEach
    void setUp() {
        storageService = new FileStorageService(tempDir.toString());
        storageService.init();
        root = tempDir.toAbsolutePath().normalize();
    }

    // ------------------------------------------------------------------
    // store
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("store")
    class Store {

        @Test
        @DisplayName("stores file successfully when valid")
        void shouldStoreFile() throws IOException {
            // given
            byte[] content = "hello".getBytes();
            MultipartFile file = new MockMultipartFile(
                    "file", "test.txt", "text/plain", content);

            // when
            storageService.store(file, "abc_test.txt");

            // then
            Path stored = root.resolve("abc_test.txt");
            assertThat(stored).exists();
            assertThat(Files.readAllBytes(stored)).isEqualTo(content);
        }

        @Test
        @DisplayName("overwrites existing file when storing with same key")
        void shouldOverwriteExistingFile() throws IOException {
            // given
            storageService.store(new MockMultipartFile("f", "a", "text/plain", "v1".getBytes()), "k.txt");

            // when
            storageService.store(new MockMultipartFile("f", "a", "text/plain", "v2".getBytes()), "k.txt");

            // then
            assertThat(Files.readString(root.resolve("k.txt"))).isEqualTo("v2");
        }

        @Test
        @DisplayName("rejects empty file by throwing FileStorageException")
        void shouldRejectEmptyFile() {
            // given
            MultipartFile empty = new MockMultipartFile(
                    "file", "empty.jpg", "image/jpeg", new byte[0]);

            // when & then
            assertThatThrownBy(() -> storageService.store(empty, "key.jpg"))
                    .isInstanceOf(FileStorageException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("rejects null file by throwing FileStorageException")
        void shouldRejectNullFile() {
            // when & then
            assertThatThrownBy(() -> storageService.store(null, "key.jpg"))
                    .isInstanceOf(FileStorageException.class);
        }

        @Test
        @DisplayName("rejects path traversal in key by throwing FileStorageException")
        void shouldRejectPathTraversal() {
            // given
            MultipartFile file = new MockMultipartFile(
                    "file", "x", "text/plain", "data".getBytes());

            // when & then
            assertThatThrownBy(() -> storageService.store(file, "../escape.txt"))
                    .isInstanceOf(FileStorageException.class)
                    .hasMessageContaining("path traversal");
        }

        @Test
        @DisplayName("rejects absolute path outside root directory")
        void shouldRejectAbsolutePathOutsideRoot() {
            // given
            MultipartFile file = new MockMultipartFile(
                    "file", "x", "text/plain", "data".getBytes());

            // when & then
            assertThatThrownBy(() -> storageService.store(file, "/etc/passwd"))
                    .isInstanceOf(FileStorageException.class)
                    .hasMessageContaining("path traversal");
        }

        @Test
        @DisplayName("wraps IOException on store failure into FileStorageException")
        void shouldWrapIOExceptionOnStore() throws IOException {
            // given
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getInputStream()).thenThrow(new IOException("disk error"));
            when(file.getSize()).thenReturn(5L);

            // when & then
            assertThatThrownBy(() -> storageService.store(file, "fail.txt"))
                    .isInstanceOf(FileStorageException.class)
                    .hasMessageContaining("Failed to store");
        }

        @Test
        @DisplayName("cleans up temporary file when store fails")
        void shouldCleanupTempFileOnFailure() throws IOException {
            // given
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getInputStream()).thenThrow(new IOException("boom"));
            when(file.getSize()).thenReturn(5L);

            // when & then
            assertThatThrownBy(() -> storageService.store(file, "x.tmp"))
                    .isInstanceOf(FileStorageException.class);

            try (var stream = Files.list(root)) {
                assertThat(stream.map(p -> p.getFileName().toString()))
                        .noneMatch(n -> n.endsWith(".tmp"));
            }
        }
    }

    // ------------------------------------------------------------------
    // delete
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deletes existing file successfully")
        void shouldDeleteExistingFile() throws IOException {
            // given
            Path target = root.resolve("del.txt");
            Files.writeString(target, "bye");

            // when
            storageService.delete("del.txt");

            // then
            assertThat(target).doesNotExist();
        }

        @Test
        @DisplayName("does not throw exception when deleting non-existent file")
        void shouldNotThrowWhenDeletingNonExistentFile() {
            // when & then
            assertThatCode(() -> storageService.delete("nope.txt"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws FileStorageException when delete key contains path traversal")
        void shouldNotThrowWhenDeletingInvalidKey() {
            // when & then
            assertThatThrownBy(() -> storageService.delete("../evil"))
                    .isInstanceOf(FileStorageException.class);
        }
    }
}
