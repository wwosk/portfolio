package org.example.mypaintings.painting.placement;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.example.mypaintings.exception.access.UnauthorizedAccessException;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.attachedimages.AttachedImage;
import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.painting.features.PaintingMedium;
import org.example.mypaintings.painting.features.Unit;
import org.example.mypaintings.painting.styles.PaintingStyle;
import org.example.mypaintings.sale.financial.Money;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:placement_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false",
        "spring.test.database.replace=none"
})
@DisplayName("PaintingPlacementRepository Test Suite")
class PaintingPlacementRepositoryTest {

    @Autowired
    private PaintingPlacementRepository placementRepository;

    @Autowired
    private TestEntityManager tem;

    @PersistenceContext
    private EntityManager em;

    private User artist;
    private User hotel;
    private User otherArtist;
    private User otherHotel;

    @BeforeEach
    void setUp() {
        em.createNativeQuery("DELETE FROM painting_placement").executeUpdate();
        em.createNativeQuery("DELETE FROM painting_painting_styles").executeUpdate();
        em.createNativeQuery("DELETE FROM attached_image").executeUpdate();
        em.createNativeQuery("DELETE FROM painting").executeUpdate();
        em.createNativeQuery("DELETE FROM painting_styles").executeUpdate();
        em.createNativeQuery("DELETE FROM app_users").executeUpdate();

        artist = tem.persistAndFlush(new User(UserRole.ARTIST, "artist1", "Street 1, City", 50.0, 19.0));
        hotel = tem.persistAndFlush(new User(UserRole.HOTEL, "hotel1", "Street 2, City", 51.0, 20.0));
        otherArtist = tem.persistAndFlush(new User(UserRole.ARTIST, "artist2", "Street 3, City", 52.0, 21.0));
        otherHotel = tem.persistAndFlush(new User(UserRole.HOTEL, "hotel2", "Street 4, City", 53.0, 22.0));
        tem.clear();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Painting newPainting(String title, User artist) {
        PaintingStyle style = new PaintingStyle("style-" + title);
        tem.persist(style);

        AttachedImage img = new AttachedImage();
        img.setFileKey("key-" + title);
        img.setOriginalFileName(title + ".jpg");
        img.setContentType("image/jpeg");
        img.setFileSize(1024L);
        tem.persist(img);

        Painting p = Painting.builder()
                .title(title)
                .price(new Money(new BigDecimal("1500.00"), "PLN"))
                .dimensions(new Dimensions(100.0, 80.0, Unit.CM))
                .artist(artist)
                .medium(PaintingMedium.OIL)
                .description("desc of " + title)
                .build();
        p.addStyle(style);
        p.addAttachment(img);
        return tem.persistAndFlush(p);
    }

    private PaintingPlacement newPlacement(Painting painting, User hotel, PlacementStatus target) {
        PaintingPlacement placement = PaintingPlacement.startCycle(painting, hotel);
        transitionTo(placement, target);
        return tem.persistAndFlush(placement);
    }

    private void transitionTo(PaintingPlacement placement, PlacementStatus target) {
        if (placement.getPlacementStatus() == target) {
            return;
        }
        for (PlacementStatus step : pathTo(placement.getPlacementStatus(), target)) {
            placement.transitionTo(step);
        }
    }

    private List<PlacementStatus> pathTo(PlacementStatus from, PlacementStatus to) {
        Map<PlacementStatus, PlacementStatus> prev = new EnumMap<>(PlacementStatus.class);
        Deque<PlacementStatus> queue = new ArrayDeque<>();
        queue.add(from);
        Set<PlacementStatus> visited = EnumSet.of(from);

        while (!queue.isEmpty()) {
            PlacementStatus current = queue.poll();
            if (current == to) {
                break;
            }
            for (PlacementStatus next : current.getPossibleTransitions()) {
                if (visited.add(next)) {
                    prev.put(next, current);
                    queue.add(next);
                }
            }
        }

        if (!prev.containsKey(to) && from != to) {
            throw new IllegalStateException(
                    "No legal transition path from " + from + " to " + to);
        }

        LinkedList<PlacementStatus> path = new LinkedList<>();
        PlacementStatus step = to;
        while (step != from) {
            path.addFirst(step);
            step = prev.get(step);
        }
        return path;
    }

    // ---------------------------------------------------------------------
    // findByPaintingIdAndArchivedFalse
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("findByPaintingIdAndArchivedFalse")
    class FindByPaintingIdAndArchivedFalse {

        @Test
        @DisplayName("returns active placement for painting")
        void findByPaintingIdAndArchivedFalse_returnsActive() {
            // given
            Painting p = newPainting("P1", artist);
            PaintingPlacement placement = newPlacement(p, hotel, PlacementStatus.EXHIBITED);
            tem.clear();

            // when
            Optional<PaintingPlacement> result =
                    placementRepository.findByPaintingIdAndArchivedFalse(p.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(placement.getId());
            assertThat(result.get().getPlacementStatus()).isEqualTo(PlacementStatus.EXHIBITED);
        }

        @Test
        @DisplayName("returns empty when painting has no placement")
        void findByPaintingIdAndArchivedFalse_emptyWhenNoPlacement() {
            // given
            Painting p = newPainting("P1", artist);
            tem.clear();

            // when & then
            assertThat(placementRepository.findByPaintingIdAndArchivedFalse(p.getId())).isEmpty();
        }

        @Test
        @DisplayName("skips placement with archived=true")
        void findByPaintingIdAndArchivedFalse_skipsArchived() {
            // given
            Painting p = newPainting("P1", artist);
            newPlacement(p, hotel, PlacementStatus.RETURNED_TO_ARTIST);
            tem.clear();

            // when & then
            assertThat(placementRepository.findByPaintingIdAndArchivedFalse(p.getId())).isEmpty();
        }
    }

    // ---------------------------------------------------------------------
    // existsByPaintingIdAndArchivedFalse
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("existsByPaintingIdAndArchivedFalse")
    class ExistsByPaintingIdAndArchivedFalse {

        @Test
        @DisplayName("returns true when active placement exists and false otherwise")
        void existsByPaintingIdAndArchivedFalse_works() {
            // given
            Painting activePainting = newPainting("Active", artist);
            newPlacement(activePainting, hotel, PlacementStatus.EXHIBITED);

            Painting archivedPainting = newPainting("Archived", artist);
            newPlacement(archivedPainting, hotel, PlacementStatus.RETURNED_TO_ARTIST);

            Painting noPlacement = newPainting("NoPlacement", artist);
            tem.clear();

            // when & then
            assertThat(placementRepository.existsByPaintingIdAndArchivedFalse(activePainting.getId())).isTrue();
            assertThat(placementRepository.existsByPaintingIdAndArchivedFalse(archivedPainting.getId())).isFalse();
            assertThat(placementRepository.existsByPaintingIdAndArchivedFalse(noPlacement.getId())).isFalse();
        }
    }

    // ---------------------------------------------------------------------
    // findAllActiveByPaintingArtistIdWithPaintingAndAttachments
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("findAllActiveByPaintingArtistIdWithPaintingAndAttachments")
    class FindAllActiveByPaintingArtistId {

        @Test
        @DisplayName("returns only active placements for given artist with painting and attachments fetched")
        void findAllActiveByPaintingArtistId_filtersByArtistAndArchived() {
            // given
            Painting p1 = newPainting("P1", artist);
            newPlacement(p1, hotel, PlacementStatus.EXHIBITED);

            Painting p2 = newPainting("P2", artist);
            newPlacement(p2, hotel, PlacementStatus.EXHIBITED);

            Painting p3 = newPainting("P3", artist);
            newPlacement(p3, hotel, PlacementStatus.RETURNED_TO_ARTIST);

            Painting p4 = newPainting("P4", otherArtist);
            newPlacement(p4, hotel, PlacementStatus.EXHIBITED);

            tem.clear();

            // when
            List<PaintingPlacement> result = placementRepository
                    .findAllActiveByPaintingArtistIdWithPaintingAndAttachments(
                            artist.getId(), PlacementStatus.getTerminalStatuses());

            // then
            assertThat(result).hasSize(2)
                    .extracting(pl -> pl.getPainting().getTitle())
                    .containsExactlyInAnyOrder("P1", "P2");

            assertThat(result).allSatisfy(pl ->
                    assertThat(pl.getPainting().getAttachments()).isNotEmpty());
        }

        @Test
        @DisplayName("skips placement with terminal status even if archived is false")
        void findAllActiveByPaintingArtistId_skipsTerminalStatuses() {
            // given
            Painting p = newPainting("P1", artist);
            PaintingPlacement placement = newPlacement(p, hotel, PlacementStatus.EXHIBITED);

            em.createNativeQuery(
                            "UPDATE painting_placement SET placement_status = 'RETURNED_TO_ARTIST', archived = false WHERE id = :id")
                    .setParameter("id", placement.getId())
                    .executeUpdate();
            tem.clear();

            // when
            List<PaintingPlacement> result = placementRepository
                    .findAllActiveByPaintingArtistIdWithPaintingAndAttachments(
                            artist.getId(), PlacementStatus.getTerminalStatuses());

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when artist has no placements")
        void findAllActiveByPaintingArtistId_emptyWhenNone() {
            // when
            List<PaintingPlacement> result = placementRepository
                    .findAllActiveByPaintingArtistIdWithPaintingAndAttachments(
                            artist.getId(), PlacementStatus.getTerminalStatuses());

            // then
            assertThat(result).isEmpty();
        }
    }

    // ---------------------------------------------------------------------
    // findByHotelIdAndPlacementStatusInWithPaintingAndAttachments
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("findByHotelIdAndPlacementStatusInWithPaintingAndAttachments")
    class FindByHotelIdAndPlacementStatusIn {

        @Test
        @DisplayName("filters by hotel and statuses, fetching painting and attachments")
        void findByHotelIdAndPlacementStatusIn_works() {
            // given
            Painting p1 = newPainting("P1", artist);
            newPlacement(p1, hotel, PlacementStatus.EXHIBITED);

            Painting p2 = newPainting("P2", artist);
            newPlacement(p2, hotel, PlacementStatus.IN_DELIVERY_TO_HOTEL);

            Painting p3 = newPainting("P3", artist);
            newPlacement(p3, otherHotel, PlacementStatus.EXHIBITED);

            tem.clear();

            // when
            List<PaintingPlacement> result = placementRepository
                    .findByHotelIdAndPlacementStatusInWithPaintingAndAttachments(
                            hotel.getId(), List.of(PlacementStatus.EXHIBITED));

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPainting().getTitle()).isEqualTo("P1");
            assertThat(result.get(0).getPainting().getAttachments()).isNotEmpty();
        }

        @Test
        @DisplayName("returns empty result when statuses list is empty")
        void findByHotelIdAndPlacementStatusIn_emptyStatuses() {
            // given
            Painting p = newPainting("P1", artist);
            newPlacement(p, hotel, PlacementStatus.EXHIBITED);
            tem.clear();

            // when & then
            assertThat(placementRepository.findByHotelIdAndPlacementStatusInWithPaintingAndAttachments(
                    hotel.getId(), List.of())).isEmpty();
        }
    }

    // ---------------------------------------------------------------------
    // existsByPaintingIdAndArchivedFalseAndPlacementStatusIn
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("existsByPaintingIdAndArchivedFalseAndPlacementStatusIn")
    class ExistsByPaintingIdAndStatusIn {

        @Test
        @DisplayName("returns true only for matching status")
        void existsByPaintingIdAndArchivedFalseAndStatusIn_works() {
            // given
            Painting p = newPainting("P1", artist);
            newPlacement(p, hotel, PlacementStatus.EXHIBITED);
            tem.clear();

            // when & then
            assertThat(placementRepository.existsByPaintingIdAndArchivedFalseAndPlacementStatusIn(
                    p.getId(), List.of(PlacementStatus.EXHIBITED))).isTrue();

            assertThat(placementRepository.existsByPaintingIdAndArchivedFalseAndPlacementStatusIn(
                    p.getId(), List.of(PlacementStatus.IN_DELIVERY_TO_HOTEL))).isFalse();
        }

        @Test
        @DisplayName("returns false for archived placement")
        void existsByPaintingIdAndArchivedFalseAndStatusIn_falseWhenArchived() {
            // given
            Painting p = newPainting("P1", artist);
            newPlacement(p, hotel, PlacementStatus.RETURNED_TO_ARTIST);
            tem.clear();

            // when & then
            assertThat(placementRepository.existsByPaintingIdAndArchivedFalseAndPlacementStatusIn(
                    p.getId(), List.of(PlacementStatus.RETURNED_TO_ARTIST))).isFalse();
        }
    }

    // ---------------------------------------------------------------------
    // findPaintingIdsCurrentlyPlaced
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("findPaintingIdsCurrentlyPlaced")
    class FindPaintingIdsCurrentlyPlaced {

        @Test
        @DisplayName("returns only IDs of paintings with active placement")
        void findPaintingIdsCurrentlyPlaced_works() {
            // given
            Painting active1 = newPainting("A1", artist);
            newPlacement(active1, hotel, PlacementStatus.EXHIBITED);

            Painting active2 = newPainting("A2", artist);
            newPlacement(active2, hotel, PlacementStatus.IN_DELIVERY_TO_HOTEL);

            Painting archived = newPainting("Archived", artist);
            newPlacement(archived, hotel, PlacementStatus.RETURNED_TO_ARTIST);

            Painting noPlacement = newPainting("NoPlacement", artist);
            tem.clear();

            // when
            Set<Long> result = placementRepository.findPaintingIdsCurrentlyPlaced(
                    List.of(active1.getId(), active2.getId(), archived.getId(), noPlacement.getId()));

            // then
            assertThat(result).containsExactlyInAnyOrder(active1.getId(), active2.getId());
        }

        @Test
        @DisplayName("returns empty set when input list is empty")
        void findPaintingIdsCurrentlyPlaced_emptyInput() {
            // when & then
            assertThat(placementRepository.findPaintingIdsCurrentlyPlaced(List.of())).isEmpty();
        }

        @Test
        @DisplayName("returns empty set when no placements exist")
        void findPaintingIdsCurrentlyPlaced_noPlacements() {
            // given
            Painting p = newPainting("P1", artist);
            tem.clear();

            // when & then
            assertThat(placementRepository.findPaintingIdsCurrentlyPlaced(List.of(p.getId()))).isEmpty();
        }
    }

    // ---------------------------------------------------------------------
    // Historical Placements
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Historical Placements")
    class HistoricalPlacements {

        @Test
        @DisplayName("allows multiple archived placements for the same painting")
        void multipleArchivedPlacementsAllowedForSamePainting() {
            // given
            Painting p = newPainting("P1", artist);
            newPlacement(p, hotel, PlacementStatus.RETURNED_TO_ARTIST);
            PaintingPlacement second = newPlacement(p, hotel, PlacementStatus.IN_DELIVERY_TO_HOTEL);
            tem.clear();

            // when
            Optional<PaintingPlacement> active =
                    placementRepository.findByPaintingIdAndArchivedFalse(p.getId());

            // then
            assertThat(active).isPresent();
            assertThat(active.get().getId()).isEqualTo(second.getId());
            assertThat(placementRepository.count()).isEqualTo(2L);
        }
    }

    // ---------------------------------------------------------------------
    // verifyHotelAccess Integration
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("verifyHotelAccess")
    class VerifyHotelAccessIntegration {

        @Test
        @DisplayName("succeeds for owner hotel and throws UnauthorizedAccessException for other hotel")
        void verifyHotelAccess_integration() {
            // given
            Painting p = newPainting("P1", artist);
            PaintingPlacement placement = newPlacement(p, hotel, PlacementStatus.EXHIBITED);

            // when & then
            placement.verifyHotelAccess(hotel.getId());

            assertThatThrownBy(() -> placement.verifyHotelAccess(otherHotel.getId()))
                    .isInstanceOf(UnauthorizedAccessException.class);
        }
    }
}