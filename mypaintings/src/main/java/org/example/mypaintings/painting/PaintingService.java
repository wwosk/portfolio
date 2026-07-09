package org.example.mypaintings.painting;


import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.exception.access.UnauthorizedAccessException;
import org.example.mypaintings.painting.attachedimages.AttachedImageService;
import org.example.mypaintings.painting.event.PaintingCreatedEvent;
import org.example.mypaintings.painting.event.PaintingArchivedEvent;
import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.painting.attachedimages.AttachedImage;
import org.example.mypaintings.painting.placement.PaintingPlacementQueryService;
import org.example.mypaintings.painting.styles.PaintingStyle;
import org.example.mypaintings.painting.styles.PaintingStyleService;
import org.example.mypaintings.reservation.ReservationQueryService;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserQueryService;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Slf4j
@Service
@RequiredArgsConstructor


public class PaintingService {
    private final PaintingRepository paintingRepository;
    private final UserQueryService userQueryService;
    private final PaintingStyleService paintingStyleService;
    private final AttachedImageService attachedImageService;
    private final PaintingPlacementQueryService placementQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final ReservationQueryService reservationQueryService;

    @Transactional
    public void createPainting(PaintingRegistryRequest paintingDTO, long artistId) {
        log.info("Artist ID: {} is starting the registration process for a new painting with title: '{}'", artistId, paintingDTO.title());

        User artist = userQueryService.getValidArtistOrThrow(artistId);

        Dimensions dimensions = new Dimensions(
                paintingDTO.width(),
                paintingDTO.height(),
                paintingDTO.unit()
        );

        Painting painting = Painting.builder()
                .title(paintingDTO.title())
                .price(paintingDTO.price())
                .dimensions(dimensions)
                .artist(artist)
                .medium(paintingDTO.medium())
                .description(paintingDTO.description())
                .build();


        List<PaintingStyle> styles = paintingStyleService.resolveStyles(paintingDTO.styleIds());
        styles.forEach(painting::addStyle);

        List<AttachedImage> attachments = attachedImageService.resolveAndValidateAttachments(paintingDTO.attachedImageIds());
        attachments.forEach(painting::addAttachment);

        Painting savedPainting = paintingRepository.saveAndFlush(painting);
        log.info("Successfully registered a new painting with ID: {} for artist ID: {}", savedPainting.getId(), artistId);

        eventPublisher.publishEvent(new PaintingCreatedEvent(savedPainting.getId()));
    }

    @Transactional
    public void archivePainting(long paintingId, long artistId){
        log.info("Attempting to delete/archive painting ID: {} by artist ID: {}", paintingId, artistId);

        Painting painting = paintingRepository.findByIdWithLock(paintingId)
                .orElseThrow(() -> {
                    log.warn("Painting with ID: {} to delete was not found", paintingId);
                    return new EntityNotFoundException("Painting not found");
                });

        if (!painting.isOwnedBy(artistId)){
            log.warn("Unauthorized: Artist ID: {} tried to delete someone else's painting ID: {}", artistId, paintingId);
            throw new UnauthorizedAccessException("You are not the owner of this painting");
        }

        if (reservationQueryService.hasActiveReservation(paintingId)) {
            log.warn("Cannot delete painting ID: {} because it has an accepted reservation", paintingId);
            throw new IllegalStateException("Painting is already reserved");
        }

        if (placementQueryService.isSold(paintingId))
        {
            log.warn("Cannot delete painting ID: {} because it is sold", paintingId);
            throw new IllegalStateException("Painting is already sold");
        }

        boolean isCurrentlyPlaced = placementQueryService.isCurrentlyPlaced(paintingId);
        if (isCurrentlyPlaced) {
            log.warn("Cannot delete painting ID: {} because it is currently in tracked placement state", paintingId);
            throw new IllegalStateException("Painting is currently in tracked placement state");
        }
        painting.archive();

        eventPublisher.publishEvent(new PaintingArchivedEvent(paintingId));

        log.info("Successfully archived painting with ID: {}", paintingId);
    }







}
