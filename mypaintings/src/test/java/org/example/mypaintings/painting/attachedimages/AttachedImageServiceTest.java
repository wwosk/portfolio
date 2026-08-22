package org.example.mypaintings.painting.attachedimages;

import org.example.mypaintings.exception.duplicate.ConflictException;
import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.example.mypaintings.painting.Painting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AttachedImageService Test Suite")
class AttachedImageServiceTest {

    @Mock private AttachedImageRepository repository;
    @Mock private AttachedImageTransactionService transactionService;
    @Mock private FileStorageService fileStorageService;
    @Mock private ImageContentValidator imageContentValidator;
    @Mock private MultipartFile file;

    @InjectMocks
    private AttachedImageService service;

    // ------------------------------------------------------------------
    // store
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("store")
    class Store {

        @Test
        @DisplayName("validates file, stores it, and saves metadata successfully")
        void storeShouldValidateStoreFileAndSaveMetadata() {
            // given
            when(file.getOriginalFilename()).thenReturn("photo.jpg");
            when(file.getContentType()).thenReturn("image/jpeg");
            when(file.getSize()).thenReturn(500L);
            when(transactionService.saveMetadata(anyString(), eq("image/jpeg"), eq("photo.jpg"), eq(500L)))
                    .thenReturn(7L);

            // when
            Long id = service.store(file);

            // then
            assertThat(id).isEqualTo(7L);
            verify(imageContentValidator).validate(file);
            verify(fileStorageService).store(eq(file), argThat(key -> key.endsWith("_photo.jpg")));
            verify(transactionService).saveMetadata(
                    argThat(k -> k.endsWith("_photo.jpg")),
                    eq("image/jpeg"), eq("photo.jpg"), eq(500L));
            verify(fileStorageService, never()).delete(anyString());
        }

        @Test
        @DisplayName("uses default file name when original filename is null")
        void storeShouldUseDefaultNameWhenOriginalFilenameIsNull() {
            // given
            when(file.getOriginalFilename()).thenReturn(null);
            when(file.getContentType()).thenReturn("image/png");
            when(file.getSize()).thenReturn(10L);
            when(transactionService.saveMetadata(anyString(), anyString(), any(), anyLong()))
                    .thenReturn(1L);

            // when
            service.store(file);

            // then
            verify(fileStorageService).store(eq(file), argThat(k -> k.endsWith("_unknown_file")));
        }

        @Test
        @DisplayName("does not save metadata or store file when validation fails")
        void storeShouldNotSaveMetadataWhenValidationFails() {
            // given
            doThrow(new RuntimeException("invalid")).when(imageContentValidator).validate(file);

            // when & then
            assertThatThrownBy(() -> service.store(file))
                    .hasMessage("invalid");

            verifyNoInteractions(fileStorageService, transactionService, repository);
        }

        @Test
        @DisplayName("deletes stored file when database metadata save fails")
        void storeShouldDeleteFileWhenDbSaveFails() {
            // given
            when(file.getOriginalFilename()).thenReturn("photo.jpg");
            when(file.getContentType()).thenReturn("image/jpeg");
            when(file.getSize()).thenReturn(500L);
            when(transactionService.saveMetadata(anyString(), anyString(), anyString(), anyLong()))
                    .thenThrow(new RuntimeException("db down"));

            // when & then
            assertThatThrownBy(() -> service.store(file))
                    .hasMessage("db down");

            verify(fileStorageService).delete(argThat(k -> k.endsWith("_photo.jpg")));
        }
    }

    // ------------------------------------------------------------------
    // resolveAndValidateAttachments
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("resolveAndValidateAttachments")
    class ResolveAndValidateAttachments {

        @Test
        @DisplayName("throws IllegalArgumentException when attachments list is null")
        void resolveShouldThrowWhenListIsNull() {
            // when & then
            assertThatThrownBy(() -> service.resolveAndValidateAttachments(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be null or empty");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when attachments list is empty")
        void resolveShouldThrowWhenListIsEmpty() {
            // when & then
            assertThatThrownBy(() -> service.resolveAndValidateAttachments(List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws exception when attachment ID inside list is null")
        void resolveShouldThrowWhenIdIsNull() {
            // given
            List<Long> ids = java.util.Arrays.asList((Long) null);

            // when & then
            assertThatThrownBy(() -> service.resolveAndValidateAttachments(ids))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Attachment ID cannot be null.");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when attachment does not exist")
        void resolveShouldThrowWhenAttachmentNotFound() {
            // given
            when(repository.findById(99L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> service.resolveAndValidateAttachments(List.of(99L)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ConflictException when attachment is already assigned to a painting")
        void resolveShouldThrowConflictWhenAttachmentAlreadyAssigned() {
            // given
            AttachedImage img = new AttachedImage();
            img.setId(5L);
            img.setPainting(new Painting());
            when(repository.findById(5L)).thenReturn(Optional.of(img));

            // when & then
            assertThatThrownBy(() -> service.resolveAndValidateAttachments(List.of(5L)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already assigned");
        }

        @Test
        @DisplayName("returns resolved attachments when all IDs are valid")
        void resolveShouldReturnAttachmentsWhenAllValid() {
            // given
            AttachedImage a = new AttachedImage();
            a.setId(1L);
            AttachedImage b = new AttachedImage();
            b.setId(2L);
            when(repository.findById(1L)).thenReturn(Optional.of(a));
            when(repository.findById(2L)).thenReturn(Optional.of(b));

            // when
            List<AttachedImage> result = service.resolveAndValidateAttachments(List.of(1L, 2L));

            // then
            assertThat(result).containsExactly(a, b);
        }

        @Test
        @DisplayName("stops processing at the first error encountered")
        void resolveShouldStopAtFirstError() {
            // given
            AttachedImage a = new AttachedImage();
            a.setId(1L);
            when(repository.findById(1L)).thenReturn(Optional.of(a));
            when(repository.findById(2L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> service.resolveAndValidateAttachments(List.of(1L, 2L)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(repository).findById(1L);
            verify(repository).findById(2L);
        }
    }
}