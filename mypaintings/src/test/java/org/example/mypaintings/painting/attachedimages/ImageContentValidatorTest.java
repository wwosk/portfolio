package org.example.mypaintings.painting.attachedimages;

import org.example.mypaintings.exception.files.InvalidPhotoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("ImageContentValidator Test Suite")
class ImageContentValidatorTest {

    private ImageContentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ImageContentValidator();
    }

    // ------------------------------------------------------------------
    // validate
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("accepts valid JPEG file")
        void shouldAcceptValidJpeg() {
            // given
            byte[] jpegBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
            MultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", jpegBytes);

            // when & then
            assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("accepts valid PNG file")
        void shouldAcceptValidPng() {
            // given
            byte[] pngBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            MultipartFile file = new MockMultipartFile(
                    "file", "photo.png", "image/png", pngBytes);

            // when & then
            assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects empty file")
        void shouldRejectEmptyFile() {
            // given
            MultipartFile file = new MockMultipartFile(
                    "file", "empty.jpg", "image/jpeg", new byte[0]);

            // when & then
            assertThatThrownBy(() -> validator.validate(file))
                    .isInstanceOf(InvalidPhotoException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("rejects file larger than 10MB limit")
        void shouldRejectFileLargerThan10MB() {
            // given
            byte[] bigContent = new byte[10 * 1024 * 1024 + 1];
            MultipartFile file = new MockMultipartFile(
                    "file", "big.jpg", "image/jpeg", bigContent);

            // when & then
            assertThatThrownBy(() -> validator.validate(file))
                    .isInstanceOf(InvalidPhotoException.class)
                    .hasMessageContaining("max size");
        }

        @ParameterizedTest
        @ValueSource(strings = {"image/gif", "application/pdf", "text/plain", "image/webp"})
        @DisplayName("rejects disallowed content types")
        void shouldRejectDisallowedContentType(String contentType) {
            // given
            MultipartFile file = new MockMultipartFile(
                    "file", "file.bin", contentType, new byte[]{1, 2, 3});

            // when & then
            assertThatThrownBy(() -> validator.validate(file))
                    .isInstanceOf(InvalidPhotoException.class)
                    .hasMessageContaining("Content-Type must be");
        }

        @Test
        @DisplayName("rejects null content type")
        void shouldRejectNullContentType() {
            // given
            MultipartFile file = new MockMultipartFile(
                    "file", "file.bin", null, new byte[]{1, 2, 3});

            // when & then
            assertThatThrownBy(() -> validator.validate(file))
                    .isInstanceOf(InvalidPhotoException.class);
        }

        @Test
        @DisplayName("rejects JPEG with PNG magic bytes")
        void shouldRejectJpegWithPngMagicBytes() {
            // given
            byte[] pngBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            MultipartFile file = new MockMultipartFile(
                    "file", "fake.jpg", "image/jpeg", pngBytes);

            // when & then
            assertThatThrownBy(() -> validator.validate(file))
                    .isInstanceOf(InvalidPhotoException.class)
                    .hasMessageContaining("does not match");
        }

        @Test
        @DisplayName("rejects PNG with JPEG magic bytes")
        void shouldRejectPngWithJpegMagicBytes() {
            // given
            byte[] jpegBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
            MultipartFile file = new MockMultipartFile(
                    "file", "fake.png", "image/png", jpegBytes);

            // when & then
            assertThatThrownBy(() -> validator.validate(file))
                    .isInstanceOf(InvalidPhotoException.class);
        }

        @Test
        @DisplayName("rejects file with too short header")
        void shouldRejectTooShortHeader() {
            // given
            MultipartFile file = new MockMultipartFile(
                    "file", "short.png", "image/png", new byte[]{1, 2});

            // when & then
            assertThatThrownBy(() -> validator.validate(file))
                    .isInstanceOf(InvalidPhotoException.class);
        }

        @Test
        @DisplayName("throws InvalidPhotoException when input stream fails with IOException")
        void shouldReturnFalseWhenInputStreamFails() throws IOException {
            // given
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getSize()).thenReturn(10L);
            when(file.getContentType()).thenReturn("image/png");
            when(file.getOriginalFilename()).thenReturn("bad.png");
            when(file.getInputStream()).thenThrow(new IOException("boom"));

            // when & then
            assertThatThrownBy(() -> validator.validate(file))
                    .isInstanceOf(InvalidPhotoException.class);
        }
    }
}
