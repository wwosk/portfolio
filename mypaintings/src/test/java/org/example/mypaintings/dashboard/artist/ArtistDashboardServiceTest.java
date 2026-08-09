package org.example.mypaintings.dashboard.artist;

import org.example.mypaintings.dashboard.artist.dto.PlacementDashboardResponse;
import org.example.mypaintings.dashboard.artist.dto.PostDashboardResponse;
import org.example.mypaintings.dashboard.artist.dto.ReservationDashboardResponse;
import org.example.mypaintings.dashboard.artist.dto.SaleDashboardResponse;
import org.example.mypaintings.feed.social.SocialInteractionsQueryService;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.PaintingQueryService;
import org.example.mypaintings.painting.attachedimages.FileUrlService;
import org.example.mypaintings.painting.placement.PaintingPlacement;
import org.example.mypaintings.painting.placement.PaintingPlacementQueryService;
import org.example.mypaintings.painting.placement.PlacementStatus;
import org.example.mypaintings.reservation.ReservationOffer;
import org.example.mypaintings.reservation.ReservationQueryService;
import org.example.mypaintings.reservation.ReservationStatus;
import org.example.mypaintings.sale.Sale;
import org.example.mypaintings.sale.SaleQueryService;
import org.example.mypaintings.sale.financial.CostStructure;
import org.example.mypaintings.sale.financial.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArtistDashboardServiceTest {

    @Mock FileUrlService fileUrlService;
    @Mock PaintingPlacementQueryService placementQueryService;
    @Mock PaintingQueryService paintingQueryService;
    @Mock ReservationQueryService reservationQueryService;
    @Mock SaleQueryService saleQueryService;
    @Mock SocialInteractionsQueryService socialInteractionsQueryService;

    @InjectMocks ArtistDashboardService service;

    private static final long ARTIST_ID = 99L;

    private Painting mockPainting(long id, String title, String imageKey) {
        Painting p = mock(Painting.class);
        when(p.getId()).thenReturn(id);
        when(p.getTitle()).thenReturn(title);
        when(p.getFirstImageFileKey()).thenReturn(imageKey);
        return p;
    }

    private Money money(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    private CostStructure mockCostStructure(String base, String total) {
        CostStructure cs = mock(CostStructure.class);
        when(cs.getBasePrice()).thenReturn(money(base, "PLN"));
        when(cs.getTotalPrice()).thenReturn(money(total, "PLN"));
        return cs;
    }

    private Sale mockSale(Painting painting, CostStructure cost, Instant saleDate) {
        Sale sale = mock(Sale.class);
        when(sale.getPainting()).thenReturn(painting);
        when(sale.getCostStructure()).thenReturn(cost);
        when(sale.getSaleDate()).thenReturn(saleDate);
        return sale;
    }

    private ReservationOffer mockOffer(long id, Painting painting, ReservationStatus status, Instant offerDate) {
        ReservationOffer offer = mock(ReservationOffer.class);
        when(offer.getId()).thenReturn(id);
        when(offer.getPainting()).thenReturn(painting);
        when(offer.getStatus()).thenReturn(status);
        when(offer.getOfferDate()).thenReturn(offerDate);
        return offer;
    }

    private PaintingPlacement mockPlacement(Painting painting, PlacementStatus status, Instant placementDate) {
        PaintingPlacement placement = mock(PaintingPlacement.class);
        when(placement.getPainting()).thenReturn(painting);
        when(placement.getPlacementStatus()).thenReturn(status);
        when(placement.getPlacementDate()).thenReturn(placementDate);
        return placement;
    }

    @Nested
    @DisplayName("getArtistSales")
    class GetArtistSales {

        @Test
        @DisplayName("Should map sale entity fields to SaleDashboardResponse correctly")
        void getArtistSales_shouldMapFields() {
            // given
            Instant saleDate = Instant.parse("2026-01-15T10:00:00Z");
            Painting painting = mockPainting(1L, "Sunset", "paintings/1.jpg");
            CostStructure cost = mockCostStructure("100.00", "137.30");
            Sale sale = mockSale(painting, cost, saleDate);

            when(saleQueryService.getSalesByArtistId(ARTIST_ID)).thenReturn(List.of(sale));
            when(fileUrlService.buildUrl("paintings/1.jpg")).thenReturn("http://cdn/1.jpg");

            // when
            List<SaleDashboardResponse> result = service.getArtistSales(ARTIST_ID);

            // then
            assertThat(result).hasSize(1);
            SaleDashboardResponse r = result.getFirst();
            assertThat(r.paintingId()).isEqualTo(1L);
            assertThat(r.paintingTitle()).isEqualTo("Sunset");
            assertThat(r.paintingUrl()).isEqualTo("http://cdn/1.jpg");
            assertThat(r.artistEarnings()).isEqualTo(cost.getBasePrice());
            assertThat(r.clientPaid()).isEqualTo(cost.getTotalPrice());
            assertThat(r.saleDate()).isEqualTo(saleDate);

            verify(saleQueryService).getSalesByArtistId(ARTIST_ID);
            verify(fileUrlService).buildUrl("paintings/1.jpg");
        }

        @Test
        @DisplayName("Should return empty list when artist has no sales")
        void getArtistSales_shouldReturnEmpty_whenNoSales() {
            // given
            when(saleQueryService.getSalesByArtistId(ARTIST_ID)).thenReturn(List.of());

            // when
            List<SaleDashboardResponse> result = service.getArtistSales(ARTIST_ID);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(fileUrlService);
        }
    }

    @Nested
    @DisplayName("getPaintingsReservations")
    class GetPaintingsReservations {

        @Test
        @DisplayName("Should map reservation offer and possible transitions from enum correctly")
        void getPaintingsReservations_shouldMapFields() {
            // given
            Instant offerDate = Instant.parse("2026-02-01T08:00:00Z");
            Painting painting = mockPainting(2L, "Portrait", "paintings/2.jpg");
            ReservationOffer offer = mockOffer(50L, painting, ReservationStatus.PENDING, offerDate);

            when(reservationQueryService.getOffersByArtistId(ARTIST_ID)).thenReturn(List.of(offer));
            when(fileUrlService.buildUrl("paintings/2.jpg")).thenReturn("http://cdn/2.jpg");

            // when
            List<ReservationDashboardResponse> result = service.getPaintingsReservations(ARTIST_ID);

            // then
            assertThat(result).hasSize(1);
            ReservationDashboardResponse r = result.getFirst();
            assertThat(r.reservationId()).isEqualTo(50L);
            assertThat(r.paintingId()).isEqualTo(2L);
            assertThat(r.paintingTitle()).isEqualTo("Portrait");
            assertThat(r.paintingUrl()).isEqualTo("http://cdn/2.jpg");
            assertThat(r.offerDate()).isEqualTo(offerDate);
            assertThat(r.status()).isEqualTo(ReservationStatus.PENDING);
            assertThat(r.possibleTransitions())
                    .isEqualTo(ReservationStatus.PENDING.getPossibleTransitionsForArtist());
        }

        @Test
        @DisplayName("Should return empty list when artist has no reservation offers")
        void getPaintingsReservations_shouldReturnEmpty_whenNoOffers() {
            // given
            when(reservationQueryService.getOffersByArtistId(ARTIST_ID)).thenReturn(List.of());

            // when
            List<ReservationDashboardResponse> result = service.getPaintingsReservations(ARTIST_ID);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(fileUrlService);
        }
    }

    @Nested
    @DisplayName("getActivePaintingsPlacements")
    class GetActivePaintingsPlacements {

        @Test
        @DisplayName("Should map painting placement and possible transitions from enum correctly")
        void getActivePaintingsPlacements_shouldMapFields() {
            // given
            Instant placementDate = Instant.parse("2026-03-01T09:00:00Z");
            Painting painting = mockPainting(3L, "Landscape", "paintings/3.jpg");
            PaintingPlacement placement = mockPlacement(painting, PlacementStatus.EXHIBITED, placementDate);

            when(placementQueryService.getActivePlacementsForArtist(ARTIST_ID))
                    .thenReturn(List.of(placement));
            when(fileUrlService.buildUrl("paintings/3.jpg")).thenReturn("http://cdn/3.jpg");

            // when
            List<PlacementDashboardResponse> result = service.getActivePaintingsPlacements(ARTIST_ID);

            // then
            assertThat(result).hasSize(1);
            PlacementDashboardResponse r = result.getFirst();
            assertThat(r.paintingId()).isEqualTo(3L);
            assertThat(r.paintingTitle()).isEqualTo("Landscape");
            assertThat(r.paintingUrl()).isEqualTo("http://cdn/3.jpg");
            assertThat(r.currentStatus()).isEqualTo(PlacementStatus.EXHIBITED);
            assertThat(r.placementDate()).isEqualTo(placementDate);
            assertThat(r.possibleTransitions())
                    .isEqualTo(PlacementStatus.EXHIBITED.getPossibleTransitionsForArtist());
        }

        @Test
        @DisplayName("Should return empty list when artist has no active placements")
        void getActivePaintingsPlacements_shouldReturnEmpty_whenNoPlacements() {
            // given
            when(placementQueryService.getActivePlacementsForArtist(ARTIST_ID)).thenReturn(List.of());

            // when
            List<PlacementDashboardResponse> result = service.getActivePaintingsPlacements(ARTIST_ID);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(fileUrlService);
        }
    }

    @Nested
    @DisplayName("getArtistPosts")
    class GetArtistPosts {

        @Test
        @DisplayName("Should return empty list and skip dependent queries when artist has no paintings")
        void getArtistPosts_shouldReturnEmpty_whenNoPaintings() {
            // given
            when(paintingQueryService.getAllArtistPaintings(ARTIST_ID)).thenReturn(List.of());

            // when
            List<PostDashboardResponse> result = service.getArtistPosts(ARTIST_ID);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(
                    socialInteractionsQueryService,
                    reservationQueryService,
                    placementQueryService,
                    fileUrlService
            );
        }

        @Test
        @DisplayName("Should map likes count, canBeDeleted flag, and image urls correctly for multiple paintings")
        void getArtistPosts_shouldMapFields_andComputeCanBeDeleted() {
            // given
            Painting p1 = mockPainting(1L, "A", "a.jpg");
            Painting p2 = mockPainting(2L, "B", "b.jpg");
            Painting p3 = mockPainting(3L, "C", "c.jpg");

            when(paintingQueryService.getAllArtistPaintings(ARTIST_ID)).thenReturn(List.of(p1, p2, p3));

            when(socialInteractionsQueryService.getLikesCountForPaintings(List.of(1L, 2L, 3L)))
                    .thenReturn(Map.of(1L, 5L, 3L, 3L));
            when(reservationQueryService.findPaintingIdsWithActiveReservations(List.of(1L, 2L, 3L)))
                    .thenReturn(Set.of(2L));
            when(placementQueryService.findPaintingIdsCurrentlyPlaced(List.of(1L, 2L, 3L)))
                    .thenReturn(Set.of(3L));

            when(fileUrlService.buildUrl("a.jpg")).thenReturn("url:a");
            when(fileUrlService.buildUrl("b.jpg")).thenReturn("url:b");
            when(fileUrlService.buildUrl("c.jpg")).thenReturn("url:c");

            // when
            List<PostDashboardResponse> result = service.getArtistPosts(ARTIST_ID);

            // then
            assertThat(result).hasSize(3);

            PostDashboardResponse r1 = result.getFirst();
            assertThat(r1.paintingId()).isEqualTo(1L);
            assertThat(r1.paintingTitle()).isEqualTo("A");
            assertThat(r1.paintingUrl()).isEqualTo("url:a");
            assertThat(r1.likesCount()).isEqualTo(5L);
            assertThat(r1.canBeDeleted()).isTrue();

            PostDashboardResponse r2 = result.get(1);
            assertThat(r2.paintingId()).isEqualTo(2L);
            assertThat(r2.likesCount()).isZero();
            assertThat(r2.canBeDeleted()).isFalse();

            PostDashboardResponse r3 = result.get(2);
            assertThat(r3.likesCount()).isEqualTo(3L);
            assertThat(r3.canBeDeleted()).isFalse();
        }

        @Test
        @DisplayName("Should default to zero likes and true canBeDeleted when painting has no interactions or reservations")
        void getArtistPosts_shouldDefaultToZeroLikes_andTrueForCanBeDeleted() {
            // given
            Painting p = mockPainting(7L, "Only", "only.jpg");
            when(paintingQueryService.getAllArtistPaintings(ARTIST_ID)).thenReturn(List.of(p));
            when(socialInteractionsQueryService.getLikesCountForPaintings(List.of(7L)))
                    .thenReturn(Map.of());
            when(reservationQueryService.findPaintingIdsWithActiveReservations(List.of(7L)))
                    .thenReturn(Set.of());
            when(placementQueryService.findPaintingIdsCurrentlyPlaced(List.of(7L)))
                    .thenReturn(Set.of());
            when(fileUrlService.buildUrl("only.jpg")).thenReturn("url:only");

            // when
            List<PostDashboardResponse> result = service.getArtistPosts(ARTIST_ID);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().likesCount()).isZero();
            assertThat(result.getFirst().canBeDeleted()).isTrue();
        }
    }
}