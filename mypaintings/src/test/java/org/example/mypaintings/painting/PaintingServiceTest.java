package org.example.mypaintings.painting;

import org.example.mypaintings.exception.access.UnauthorizedAccessException;
import org.example.mypaintings.painting.attachedimages.AttachedImage;
import org.example.mypaintings.painting.attachedimages.AttachedImageService;
import org.example.mypaintings.painting.event.PaintingArchivedEvent;
import org.example.mypaintings.painting.event.PaintingCreatedEvent;
import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.painting.features.PaintingMedium;
import org.example.mypaintings.painting.features.Unit;
import org.example.mypaintings.painting.placement.PaintingPlacementQueryService;
import org.example.mypaintings.painting.styles.PaintingStyle;
import org.example.mypaintings.painting.styles.PaintingStyleService;
import org.example.mypaintings.reservation.ReservationQueryService;
import org.example.mypaintings.sale.financial.Money;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserQueryService;
import org.example.mypaintings.user.UserRole;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaintingServiceTest {

    @Mock private PaintingRepository paintingRepository;
    @Mock private UserQueryService userQueryService;
    @Mock private PaintingStyleService paintingStyleService;
    @Mock private AttachedImageService attachedImageService;
    @Mock private PaintingPlacementQueryService placementQueryService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ReservationQueryService reservationQueryService;

    @InjectMocks private PaintingService paintingService;

    private User artist;

    @BeforeEach
    void setUp() {
        artist = new User(UserRole.ARTIST, "artistNick", "Main St 1", 10.0, 20.0);
        setId(artist, 42L);
    }

    @Nested
    @DisplayName("createPainting")
    class CreatePainting {

        @Test
        @DisplayName("should create painting, save it and publish PaintingCreatedEvent")
        void shouldCreatePaintingSaveAndPublishEvent() {
            // given
            long artistId = 42L;
            PaintingRegistryRequest request = sampleRequest(List.of(1L, 2L), List.of(10L, 11L));

            PaintingStyle style1 = styleWithId(1L, "abstract");
            PaintingStyle style2 = styleWithId(2L, "modern");
            AttachedImage img1 = imageWithId(10L);
            AttachedImage img2 = imageWithId(11L);

            when(userQueryService.getValidArtistOrThrow(artistId)).thenReturn(artist);
            when(paintingStyleService.resolveStyles(request.styleIds()))
                    .thenReturn(List.of(style1, style2));
            when(attachedImageService.resolveAndValidateAttachments(request.attachedImageIds()))
                    .thenReturn(List.of(img1, img2));
            when(paintingRepository.saveAndFlush(any(Painting.class)))
                    .thenAnswer(inv -> {
                        Painting p = inv.getArgument(0);
                        setId(p, 999L);
                        return p;
                    });

            // when
            paintingService.createPainting(request, artistId);

            // then
            ArgumentCaptor<Painting> paintingCaptor = ArgumentCaptor.forClass(Painting.class);
            verify(paintingRepository).saveAndFlush(paintingCaptor.capture());
            Painting saved = paintingCaptor.getValue();

            assertThat(saved.getArtist()).isSameAs(artist);
            assertThat(saved.getTitle()).isEqualTo(request.title());
            assertThat(saved.getPrice()).isSameAs(request.price());
            assertThat(saved.getDescription()).isEqualTo(request.description());
            assertThat(saved.getMedium()).isEqualTo(request.medium());
            assertThat(saved.getDimensions()).isEqualTo(
                    new Dimensions(request.width(), request.height(), request.unit()));
            assertThat(saved.getStyles()).containsExactlyInAnyOrder(style1, style2);
            assertThat(saved.getAttachments()).containsExactlyInAnyOrder(img1, img2);

            assertThat(img1.getPainting()).isSameAs(saved);
            assertThat(img2.getPainting()).isSameAs(saved);

            ArgumentCaptor<PaintingCreatedEvent> eventCaptor =
                    ArgumentCaptor.forClass(PaintingCreatedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().paintingId()).isEqualTo(999L);
        }

        @Test
        @DisplayName("should pass correct artistId to userQueryService")
        void shouldPassArtistIdToUserQuery() {
            // given
            long artistId = 42L;
            PaintingRegistryRequest request = sampleRequest(List.of(1L), List.of(10L));
            mockHappyPathResolvers(request);
            when(paintingRepository.saveAndFlush(any())).thenAnswer(inv -> {
                Painting p = inv.getArgument(0);
                setId(p, 1L);
                return p;
            });

            // when
            paintingService.createPainting(request, artistId);

            // then
            verify(userQueryService).getValidArtistOrThrow(artistId);
        }

        @Test
        @DisplayName("should not persist nor publish event when artist resolution fails")
        void shouldNotPersistWhenArtistResolutionFails() {
            // given
            long artistId = 42L;
            PaintingRegistryRequest request = sampleRequest(List.of(1L), List.of(10L));
            when(userQueryService.getValidArtistOrThrow(artistId))
                    .thenThrow(new IllegalArgumentException("bad artist"));

            // when / then
            assertThatThrownBy(() -> paintingService.createPainting(request, artistId))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(paintingRepository);
            verifyNoInteractions(eventPublisher);
            verifyNoInteractions(paintingStyleService);
            verifyNoInteractions(attachedImageService);
        }

        @Test
        @DisplayName("should not persist nor publish event when attachment resolution fails")
        void shouldNotPersistWhenAttachmentResolutionFails() {
            // given
            long artistId = 42L;
            PaintingRegistryRequest request = sampleRequest(List.of(1L), List.of(10L));
            when(userQueryService.getValidArtistOrThrow(artistId)).thenReturn(artist);
            when(paintingStyleService.resolveStyles(request.styleIds()))
                    .thenReturn(List.of(styleWithId(1L, "abstract")));
            when(attachedImageService.resolveAndValidateAttachments(request.attachedImageIds()))
                    .thenThrow(new IllegalArgumentException("bad images"));

            // when / then
            assertThatThrownBy(() -> paintingService.createPainting(request, artistId))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(paintingRepository, never()).saveAndFlush(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("should build Dimensions using width, height and unit from request")
        void shouldBuildDimensionsFromRequest() {
            // given
            long artistId = 42L;
            PaintingRegistryRequest request = sampleRequest(List.of(1L), List.of(10L));
            mockHappyPathResolvers(request);
            when(paintingRepository.saveAndFlush(any())).thenAnswer(inv -> {
                Painting p = inv.getArgument(0);
                setId(p, 1L);
                return p;
            });

            // when
            paintingService.createPainting(request, artistId);

            // then
            ArgumentCaptor<Painting> captor = ArgumentCaptor.forClass(Painting.class);
            verify(paintingRepository).saveAndFlush(captor.capture());
            Dimensions dims = captor.getValue().getDimensions();
            assertThat(dims.getWidth()).isEqualTo(request.width());
            assertThat(dims.getHeight()).isEqualTo(request.height());
            assertThat(dims.getUnit()).isEqualTo(request.unit());
        }
    }

    @Nested
    @DisplayName("archivePainting")
    class ArchivePainting {

        @Test
        @DisplayName("should archive painting and publish PaintingArchivedEvent on happy path")
        void shouldArchiveAndPublishEvent() {
            // given
            long paintingId = 100L;
            long artistId = 42L;
            Painting painting = ownedPainting(paintingId, artistId);

            when(paintingRepository.findByIdWithLock(paintingId))
                    .thenReturn(Optional.of(painting));
            when(reservationQueryService.hasActiveReservation(paintingId)).thenReturn(false);
            when(placementQueryService.isSold(paintingId)).thenReturn(false);
            when(placementQueryService.isCurrentlyPlaced(paintingId)).thenReturn(false);

            // when
            paintingService.archivePainting(paintingId, artistId);

            // then
            assertThat(painting.isArchived()).isTrue();

            ArgumentCaptor<PaintingArchivedEvent> eventCaptor =
                    ArgumentCaptor.forClass(PaintingArchivedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().paintingId()).isEqualTo(paintingId);
        }

        @Test
        @DisplayName("should throw EntityNotFoundException and publish no event when painting missing")
        void shouldThrowWhenPaintingMissing() {
            // given
            long paintingId = 100L;
            long artistId = 42L;
            when(paintingRepository.findByIdWithLock(paintingId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> paintingService.archivePainting(paintingId, artistId))
                    .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
                    .hasMessage("Painting not found");

            verifyNoInteractions(eventPublisher);
            verifyNoInteractions(reservationQueryService);
            verifyNoInteractions(placementQueryService);
        }

        @Test
        @DisplayName("should throw UnauthorizedAccessException when artist is not owner")
        void shouldThrowWhenNotOwner() {
            // given
            long paintingId = 100L;
            long ownerId = 42L;
            long attackerId = 999L;
            Painting painting = ownedPainting(paintingId, ownerId);

            when(paintingRepository.findByIdWithLock(paintingId))
                    .thenReturn(Optional.of(painting));

            // when / then
            assertThatThrownBy(() -> paintingService.archivePainting(paintingId, attackerId))
                    .isInstanceOf(UnauthorizedAccessException.class);

            assertThat(painting.isArchived()).isFalse();
            verifyNoInteractions(eventPublisher);
            verifyNoInteractions(reservationQueryService);
            verifyNoInteractions(placementQueryService);
        }

        @Test
        @DisplayName("should throw IllegalStateException when painting has active reservation")
        void shouldThrowWhenActiveReservation() {
            // given
            long paintingId = 100L;
            long artistId = 42L;
            Painting painting = ownedPainting(paintingId, artistId);

            when(paintingRepository.findByIdWithLock(paintingId))
                    .thenReturn(Optional.of(painting));
            when(reservationQueryService.hasActiveReservation(paintingId)).thenReturn(true);

            // when / then
            assertThatThrownBy(() -> paintingService.archivePainting(paintingId, artistId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Painting is already reserved");

            assertThat(painting.isArchived()).isFalse();
            verifyNoInteractions(eventPublisher);
            verify(placementQueryService, never()).isSold(anyLong());
            verify(placementQueryService, never()).isCurrentlyPlaced(anyLong());
        }

        @Test
        @DisplayName("should throw IllegalStateException when painting is sold")
        void shouldThrowWhenSold() {
            // given
            long paintingId = 100L;
            long artistId = 42L;
            Painting painting = ownedPainting(paintingId, artistId);

            when(paintingRepository.findByIdWithLock(paintingId))
                    .thenReturn(Optional.of(painting));
            when(reservationQueryService.hasActiveReservation(paintingId)).thenReturn(false);
            when(placementQueryService.isSold(paintingId)).thenReturn(true);

            // when / then
            assertThatThrownBy(() -> paintingService.archivePainting(paintingId, artistId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Painting is already sold");

            assertThat(painting.isArchived()).isFalse();
            verifyNoInteractions(eventPublisher);
            verify(placementQueryService, never()).isCurrentlyPlaced(anyLong());
        }

        @Test
        @DisplayName("should throw IllegalStateException when painting is currently placed")
        void shouldThrowWhenCurrentlyPlaced() {
            // given
            long paintingId = 100L;
            long artistId = 42L;
            Painting painting = ownedPainting(paintingId, artistId);

            when(paintingRepository.findByIdWithLock(paintingId))
                    .thenReturn(Optional.of(painting));
            when(reservationQueryService.hasActiveReservation(paintingId)).thenReturn(false);
            when(placementQueryService.isSold(paintingId)).thenReturn(false);
            when(placementQueryService.isCurrentlyPlaced(paintingId)).thenReturn(true);

            // when / then
            assertThatThrownBy(() -> paintingService.archivePainting(paintingId, artistId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Painting is currently in tracked placement state");

            assertThat(painting.isArchived()).isFalse();
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("should verify ownership before checking reservations")
        void shouldCheckOwnershipBeforeReservation() {
            // given
            long paintingId = 100L;
            long ownerId = 42L;
            long attackerId = 999L;
            Painting painting = ownedPainting(paintingId, ownerId);

            when(paintingRepository.findByIdWithLock(paintingId))
                    .thenReturn(Optional.of(painting));

            // when / then
            assertThatThrownBy(() -> paintingService.archivePainting(paintingId, attackerId))
                    .isInstanceOf(UnauthorizedAccessException.class);

            verifyNoInteractions(reservationQueryService);
            verifyNoInteractions(placementQueryService);
        }

        @Test
        @DisplayName("should use pessimistic lock (findByIdWithLock) instead of findById")
        void shouldUseLockedFind() {
            // given
            long paintingId = 100L;
            long artistId = 42L;
            Painting painting = ownedPainting(paintingId, artistId);

            when(paintingRepository.findByIdWithLock(paintingId))
                    .thenReturn(Optional.of(painting));
            when(reservationQueryService.hasActiveReservation(paintingId)).thenReturn(false);
            when(placementQueryService.isSold(paintingId)).thenReturn(false);
            when(placementQueryService.isCurrentlyPlaced(paintingId)).thenReturn(false);

            // when
            paintingService.archivePainting(paintingId, artistId);

            // then
            verify(paintingRepository).findByIdWithLock(paintingId);
            verify(paintingRepository, never()).findById(anyLong());
        }
    }

    private PaintingRegistryRequest sampleRequest(List<Long> styleIds, List<Long> imageIds) {
        return new PaintingRegistryRequest(
                "Sunset",
                "Beautiful sunset",
                imageIds,
                50.0,
                70.0,
                Unit.CM,
                mock(Money.class),
                styleIds,
                PaintingMedium.OIL
        );
    }

    private void mockHappyPathResolvers(PaintingRegistryRequest request) {
        when(userQueryService.getValidArtistOrThrow(anyLong())).thenReturn(artist);
        when(paintingStyleService.resolveStyles(request.styleIds()))
                .thenReturn(request.styleIds().stream()
                        .map(id -> styleWithId(id, "style-" + id))
                        .toList());
        when(attachedImageService.resolveAndValidateAttachments(request.attachedImageIds()))
                .thenReturn(request.attachedImageIds().stream()
                        .map(this::imageWithId)
                        .toList());
    }

    private Painting ownedPainting(long paintingId, long ownerId) {
        User owner = new User(UserRole.ARTIST, "owner", "Addr 1", 0.0, 0.0);
        setId(owner, ownerId);
        Painting p = Painting.builder()
                .artist(owner)
                .title("t")
                .price(mock(Money.class))
                .dimensions(new Dimensions(10, 10, Unit.CM))
                .medium(PaintingMedium.OIL)
                .build();
        setId(p, paintingId);
        return p;
    }

    private PaintingStyle styleWithId(long id, String name) {
        PaintingStyle style = new PaintingStyle(name);
        setId(style, id);
        return style;
    }

    private AttachedImage imageWithId(long id) {
        AttachedImage img = new AttachedImage();
        img.setFileKey("file-" + id);
        img.setOriginalFileName("file-" + id + ".jpg");
        img.setContentType("image/jpeg");
        img.setFileSize(1024L);
        setId(img, id);
        return img;
    }

    private static void setId(Object target, Long id) {
        try {
            var field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set id on " + target.getClass(), e);
        }
    }
}