package org.example.mypaintings.painting.attachedimages;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AttachedImageTransactionService Test Suite")
class AttachedImageTransactionServiceTest {

    @Mock
    private AttachedImageRepository repository;

    @InjectMocks
    private AttachedImageTransactionService service;

    // ------------------------------------------------------------------
    // saveMetadata
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("saveMetadata")
    class SaveMetadata {

        @Test
        @DisplayName("saves metadata successfully and returns generated identifier")
        void shouldSaveMetadataAndReturnId() {
            // given
            AttachedImage saved = new AttachedImage();
            saved.setId(42L);
            when(repository.save(any(AttachedImage.class))).thenReturn(saved);

            // when
            Long id = service.saveMetadata(
                    "key_photo.jpg", "image/jpeg", "photo.jpg", 1234L);

            // then
            assertThat(id).isEqualTo(42L);
            verify(repository).save(any(AttachedImage.class));
        }

        @Test
        @DisplayName("populates all entity fields correctly before repository save")
        void shouldPopulateAllFieldsBeforeSave() {
            // given
            AttachedImage saved = new AttachedImage();
            saved.setId(1L);
            when(repository.save(any(AttachedImage.class))).thenReturn(saved);

            // when
            service.saveMetadata("k1", "image/png", "orig.png", 999L);

            // then
            ArgumentCaptor<AttachedImage> captor = ArgumentCaptor.forClass(AttachedImage.class);
            verify(repository).save(captor.capture());
            AttachedImage toSave = captor.getValue();

            assertThat(toSave.getFileKey()).isEqualTo("k1");
            assertThat(toSave.getContentType()).isEqualTo("image/png");
            assertThat(toSave.getOriginalFileName()).isEqualTo("orig.png");
            assertThat(toSave.getFileSize()).isEqualTo(999L);
            assertThat(toSave.getPainting()).isNull();
        }
    }
}
