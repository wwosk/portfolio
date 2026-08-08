package org.example.mypaintings.painting;

import org.example.mypaintings.painting.attachedimages.AttachedImage;
import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.painting.features.Orientation;
import org.example.mypaintings.painting.features.PaintingMedium;
import org.example.mypaintings.painting.features.SizeCategory;
import org.example.mypaintings.painting.features.Unit;
import org.example.mypaintings.painting.styles.PaintingStyle;
import org.example.mypaintings.sale.financial.Money;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaintingTest {

    private static final String VALID_TITLE = "Starry Night";
    private static final String VALID_DESCRIPTION = "A masterpiece";
    private static final Dimensions VALID_DIMENSIONS = new Dimensions(73.7, 92.1, Unit.CM);
    private static final Money VALID_PRICE = new Money(new BigDecimal("1000.00"), "EUR");
    private static final PaintingMedium VALID_MEDIUM = PaintingMedium.OIL;

    private User artist;

    @BeforeEach
    void setUp() {
        artist = new User(UserRole.ARTIST, "vanGogh", "Amsterdam", 52.3676, 4.9041);
    }

    private Painting.PaintingBuilder validBuilder() {
        return Painting.builder()
                .artist(artist)
                .title(VALID_TITLE)
                .dimensions(VALID_DIMENSIONS)
                .description(VALID_DESCRIPTION)
                .price(VALID_PRICE)
                .medium(VALID_MEDIUM);
    }

    private AttachedImage attachedImage(String fileKey) {
        AttachedImage image = new AttachedImage();
        image.setFileKey(fileKey);
        image.setOriginalFileName(fileKey + ".jpg");
        image.setContentType("image/jpeg");
        image.setFileSize(1024L);
        return image;
    }

    private static PaintingStyle style(String name) {
        return new PaintingStyle(name);
    }

    @Nested
    @DisplayName("Business Constructor (@Builder)")
    class BusinessConstructor {

        @Test
        @DisplayName("Should create painting when all data is valid")
        void shouldCreatePainting_whenAllDataValid() {
            // when
            Painting painting = validBuilder().build();

            // then
            assertThat(painting.getArtist()).isEqualTo(artist);
            assertThat(painting.getTitle()).isEqualTo(VALID_TITLE);
            assertThat(painting.getDescription()).isEqualTo(VALID_DESCRIPTION);
            assertThat(painting.getPrice()).isSameAs(VALID_PRICE);
            assertThat(painting.getMedium()).isEqualTo(VALID_MEDIUM);
            assertThat(painting.getDimensions()).isEqualTo(VALID_DIMENSIONS);
            assertThat(painting.isArchived()).isFalse();
        }

        @Test
        @DisplayName("Should initialize empty collections when attachments and styles are null")
        void shouldInitializeEmptyCollections_whenNullsProvided() {
            // when
            Painting painting = validBuilder()
                    .attachments(null)
                    .styles(null)
                    .build();

            // then
            assertThat(painting.getAttachments()).isNotNull().isEmpty();
            assertThat(painting.getStyles()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("Should keep provided attachments and styles collections")
        void shouldKeepProvidedCollections() {
            // given
            AttachedImage image = attachedImage("key-1");
            PaintingStyle style = style("Impressionism");

            // when
            Painting painting = validBuilder()
                    .attachments(Set.of(image))
                    .styles(Set.of(style))
                    .build();

            // then
            assertThat(painting.getAttachments()).containsExactly(image);
            assertThat(painting.getStyles()).containsExactly(style);
        }

        @Test
        @DisplayName("Should calculate sizeCategory and orientation from dimensions automatically")
        void shouldCalculateMetadata_fromDimensions() {
            // when
            Painting painting = validBuilder().build();

            // then
            assertThat(painting.getSizeCategory()).isEqualTo(SizeCategory.MEDIUM);
            assertThat(painting.getOrientation()).isEqualTo(Orientation.PORTRAIT);
        }

        @Test
        @DisplayName("Should not calculate sizeCategory or orientation when dimensions are null")
        void shouldNotCalculateMetadata_whenDimensionsNull() {
            // when
            Painting painting = validBuilder().dimensions(null).build();

            // then
            assertThat(painting.getDimensions()).isNull();
            assertThat(painting.getSizeCategory()).isNull();
            assertThat(painting.getOrientation()).isNull();
        }
    }

    @Nested
    @DisplayName("setDimensions")
    class SetDimensions {

        @Test
        @DisplayName("Should set dimensions and recalculate metadata")
        void shouldSetDimensionsAndRecalculateMetadata() {
            // given
            Painting painting = validBuilder().build();
            Dimensions newDimensions = new Dimensions(200.0, 150.0, Unit.CM);

            // when
            painting.setDimensions(newDimensions);

            // then
            assertThat(painting.getDimensions()).isEqualTo(newDimensions);
            assertThat(painting.getSizeCategory()).isEqualTo(SizeCategory.HUGE);
            assertThat(painting.getOrientation()).isEqualTo(Orientation.LANDSCAPE);
        }

        @Test
        @DisplayName("Should clear metadata when dimensions are null")
        void shouldClearMetadata_whenDimensionsNull() {
            // given
            Painting painting = validBuilder().build();

            // when
            painting.setDimensions(null);

            // then
            assertThat(painting.getDimensions()).isNull();
            assertThat(painting.getSizeCategory()).isNull();
            assertThat(painting.getOrientation()).isNull();
        }
    }

    @Nested
    @DisplayName("addStyle")
    class AddStyle {

        @Test
        @DisplayName("Should add style when total styles count is below limit")
        void shouldAddStyle_whenBelowLimit() {
            // given
            Painting painting = validBuilder().build();
            PaintingStyle style = style("Impressionism");

            // when
            painting.addStyle(style);

            // then
            assertThat(painting.getStyles()).containsExactly(style);
        }

        @Test
        @DisplayName("Should allow adding exactly three distinct styles")
        void shouldAllowExactlyThreeStyles() {
            // given
            Painting painting = validBuilder().build();

            // when
            painting.addStyle(style("A"));
            painting.addStyle(style("B"));
            painting.addStyle(style("C"));

            // then
            assertThat(painting.getStyles()).hasSize(3);
        }

        @Test
        @DisplayName("Should throw IllegalStateException when attempting to add a 4th style")
        void shouldThrow_whenStyleLimitExceeded() {
            // given
            Painting painting = validBuilder().build();
            painting.addStyle(style("A"));
            painting.addStyle(style("B"));
            painting.addStyle(style("C"));

            // when & then
            assertThatThrownBy(() -> painting.addStyle(style("D")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Maximum amount of styles achieved (3).");
        }

        @Test
        @DisplayName("Should not increase set size when duplicate style with same normalized name is added")
        void shouldNotIncreaseSize_whenDuplicateStyleAdded() {
            // given
            Painting painting = validBuilder().build();

            // when
            painting.addStyle(style("Impressionism"));
            painting.addStyle(style("IMPRESSIONISM"));

            // then
            assertThat(painting.getStyles()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("addAttachment")
    class AddAttachment {

        @Test
        @DisplayName("Should add attachment and set back-reference on image")
        void shouldAddAttachmentAndSetBackReference() {
            // given
            Painting painting = validBuilder().build();
            AttachedImage image = attachedImage("key-1");

            // when
            painting.addAttachment(image);

            // then
            assertThat(painting.getAttachments()).containsExactly(image);
            assertThat(image.getPainting()).isSameAs(painting);
        }

        @Test
        @DisplayName("Should allow adding exactly three attachments")
        void shouldAllowExactlyThreeAttachments() {
            // given
            Painting painting = validBuilder().build();

            // when
            painting.addAttachment(attachedImage("a"));
            painting.addAttachment(attachedImage("b"));
            painting.addAttachment(attachedImage("c"));

            // then
            assertThat(painting.getAttachments()).hasSize(3);
        }

        @Test
        @DisplayName("Should throw IllegalStateException when attempting to add a 4th attachment")
        void shouldThrow_whenAttachmentLimitExceeded() {
            // given
            Painting painting = validBuilder().build();
            painting.addAttachment(attachedImage("a"));
            painting.addAttachment(attachedImage("b"));
            painting.addAttachment(attachedImage("c"));

            // when & then
            assertThatThrownBy(() -> painting.addAttachment(attachedImage("d")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Maximum amount of attached images achieved (3).");
        }
    }

    @Nested
    @DisplayName("Image Keys Read Operations")
    class ImageKeys {

        @Test
        @DisplayName("Should return empty list when no attachments are present")
        void shouldReturnEmptyList_whenNoAttachments() {
            // given
            Painting painting = validBuilder().build();

            // when & then
            assertThat(painting.getImagesAsFileKeys()).isEmpty();
        }

        @Test
        @DisplayName("Should return all file keys when attachments exist")
        void shouldReturnAllFileKeys() {
            // given
            Painting painting = validBuilder().build();
            painting.addAttachment(attachedImage("a"));
            painting.addAttachment(attachedImage("b"));

            // when & then
            assertThat(painting.getImagesAsFileKeys())
                    .containsExactlyInAnyOrder("a", "b");
        }

        @Test
        @DisplayName("Should return null for first image file key when attachments are empty")
        void firstKey_shouldReturnNull_whenEmpty() {
            // given
            Painting painting = validBuilder().build();

            // when & then
            assertThat(painting.getFirstImageFileKey()).isNull();
        }

        @Test
        @DisplayName("Should return single file key for first image when attachments exist")
        void firstKey_shouldReturnSingleKey() {
            // given
            Painting painting = validBuilder().build();
            painting.addAttachment(attachedImage("only"));

            // when & then
            assertThat(painting.getFirstImageFileKey()).isEqualTo("only");
        }

        @Test
        @DisplayName("Should return empty list when attachments collection is null")
        void shouldReturnEmpty_whenAttachmentsNull() throws Exception {
            // given
            Painting painting = new Painting();
            var field = Painting.class.getDeclaredField("attachments");
            field.setAccessible(true);
            field.set(painting, null);

            // when & then
            assertThat(painting.getImagesAsFileKeys()).isEmpty();
        }
    }

    @Nested
    @DisplayName("isOwnedBy")
    class IsOwnedBy {

        @Test
        @DisplayName("Should return true when provided user id matches artist id")
        void shouldReturnTrue_whenSameArtistId() {
            // given
            Painting painting = validBuilder().build();
            setId(artist, 42L);

            // when & then
            assertThat(painting.isOwnedBy(42L)).isTrue();
        }

        @Test
        @DisplayName("Should return false when provided user id differs from artist id")
        void shouldReturnFalse_whenDifferentArtistId() {
            // given
            Painting painting = validBuilder().build();
            setId(artist, 42L);

            // when & then
            assertThat(painting.isOwnedBy(99L)).isFalse();
        }

        @Test
        @DisplayName("Should return false when artist is null")
        void shouldReturnFalse_whenArtistNull() {
            // given
            Painting painting = validBuilder().artist(null).build();

            // when & then
            assertThat(painting.isOwnedBy(1L)).isFalse();
        }
    }

    @Nested
    @DisplayName("archive")
    class Archive {

        @Test
        @DisplayName("Should set archived flag to true when archive is invoked")
        void archive_shouldSetArchivedTrue() {
            // given
            Painting painting = validBuilder().build();

            // when
            painting.archive();

            // then
            assertThat(painting.isArchived()).isTrue();
        }
    }

    private static void setId(Object target, Long id) {
        try {
            var field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}