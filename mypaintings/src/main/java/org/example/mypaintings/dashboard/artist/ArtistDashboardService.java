package org.example.mypaintings.dashboard.artist;


import lombok.RequiredArgsConstructor;
import org.example.mypaintings.dashboard.artist.dto.PlacementDashboardResponse;
import org.example.mypaintings.dashboard.artist.dto.PostDashboardResponse;
import org.example.mypaintings.dashboard.artist.dto.ReservationDashboardResponse;
import org.example.mypaintings.dashboard.artist.dto.SaleDashboardResponse;
import org.example.mypaintings.feed.social.SocialInteractionsQueryService;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.PaintingQueryService;
import org.example.mypaintings.painting.attachedimages.FileUrlService;
import org.example.mypaintings.painting.placement.PaintingPlacementQueryService;
import org.example.mypaintings.reservation.ReservationQueryService;
import org.example.mypaintings.sale.SaleQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;



@Service
@Transactional
@RequiredArgsConstructor
public class ArtistDashboardService {
    private final FileUrlService fileUrlService;
    private final PaintingPlacementQueryService placementQueryService;
    private final PaintingQueryService paintingQueryService;
    private final ReservationQueryService reservationQueryService;
    private final SaleQueryService saleQueryService;
    private final SocialInteractionsQueryService socialInteractionsQueryService;


    @Transactional(readOnly = true)
    public List<SaleDashboardResponse> getArtistSales(long artistId) {
        return saleQueryService.getSalesByArtistId(artistId).stream()
                .map(sale -> SaleDashboardResponse.builder()
                        .paintingId(sale.getPainting().getId())
                        .paintingTitle(sale.getPainting().getTitle())
                        .paintingUrl(fileUrlService.buildUrl(sale.getPainting().getFirstImageFileKey()))
                        .artistEarnings(sale.getCostStructure().getBasePrice())
                        .clientPaid(sale.getCostStructure().getTotalPrice())
                        .saleDate(sale.getSaleDate())
                        .build())
                .toList();
    }


    @Transactional(readOnly = true)
    public List<ReservationDashboardResponse> getPaintingsReservations(long artistId) {
        return reservationQueryService.getOffersByArtistId(artistId).stream()
                .map(offer -> ReservationDashboardResponse.builder()
                        .reservationId(offer.getId())
                        .paintingId(offer.getPainting().getId())
                        .paintingTitle(offer.getPainting().getTitle())
                        .offerDate(offer.getOfferDate())
                        .paintingUrl(fileUrlService.buildUrl(offer.getPainting().getFirstImageFileKey()))
                        .possibleTransitions(offer.getStatus().getPossibleTransitionsForArtist())
                        .status(offer.getStatus()).build())
                .toList();

    }


    @Transactional(readOnly = true)
    public List<PlacementDashboardResponse> getActivePaintingsPlacements(long artistId) {
        return placementQueryService.getActivePlacementsForArtist(artistId).stream()
                .map(placement -> PlacementDashboardResponse.builder()
                        .paintingId(placement.getPainting().getId())
                        .paintingTitle(placement.getPainting().getTitle())
                        .possibleTransitions(placement.getPlacementStatus().getPossibleTransitionsForArtist())
                        .currentStatus(placement.getPlacementStatus())
                        .paintingUrl(fileUrlService.buildUrl(placement.getPainting().getFirstImageFileKey()))
                        .placementDate(placement.getPlacementDate()).build()
        ).toList();
    }


    @Transactional(readOnly = true)
    public List<PostDashboardResponse> getArtistPosts(long artistId) {
        List<Painting> paintings = paintingQueryService.getAllArtistPaintings(artistId);
        if (paintings.isEmpty()) {
            return List.of();
        }

        List<Long> paintingIds = paintings.stream()
                .map(Painting::getId)
                .toList();

        Map<Long, Long> likesCountMap = socialInteractionsQueryService.getLikesCountForPaintings(paintingIds);
        Set<Long> activeReservationIds = reservationQueryService.findPaintingIdsWithActiveReservations(paintingIds);
        Set<Long> currentlyPlacedIds = placementQueryService.findPaintingIdsCurrentlyPlaced(paintingIds);

        return paintings.stream().map(painting -> {
            long paintingId = painting.getId();
            long likes = likesCountMap.getOrDefault(paintingId, 0L);

            boolean hasAcceptedReservation = activeReservationIds.contains(paintingId);
            boolean isCurrentlyPlaced = currentlyPlacedIds.contains(paintingId);
            boolean canBeDeleted = !hasAcceptedReservation && !isCurrentlyPlaced;

            return new PostDashboardResponse(
                    paintingId,
                    painting.getTitle(),
                    fileUrlService.buildUrl(painting.getFirstImageFileKey()),
                    likes,
                    canBeDeleted
            );
        }).toList();
    }



}



