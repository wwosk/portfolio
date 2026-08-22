package org.example.mypaintings.feed;

import org.example.mypaintings.feed.dto.PaintingDetailsResponse;
import org.example.mypaintings.feed.dto.PaintingFeedItemResponse;
import org.example.mypaintings.feed.dto.PaintingFilterCriteria;
import org.example.mypaintings.feed.social.PaintingLike;
import org.example.mypaintings.feed.social.PaintingLikeRepository;
import org.example.mypaintings.geolocation.UserProximity;
import org.example.mypaintings.geolocation.UserProximityRepository;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.PaintingRepository;
import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.painting.features.Orientation;
import org.example.mypaintings.painting.features.PaintingMedium;
import org.example.mypaintings.painting.features.SizeCategory;
import org.example.mypaintings.painting.features.Unit;
import org.example.mypaintings.painting.styles.PaintingStyle;
import org.example.mypaintings.painting.styles.PaintingStyleRepository;
import org.example.mypaintings.reservation.ReservationOffer;
import org.example.mypaintings.reservation.ReservationOfferRepository;
import org.example.mypaintings.reservation.ReservationStatus;
import org.example.mypaintings.sale.financial.Money;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserRepository;
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("PaintingFeedService Test Suite")
class PaintingFeedServiceTest {

    @Autowired private PaintingFeedService paintingFeedService;
    @Autowired private PaintingRepository paintingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PaintingStyleRepository paintingStyleRepository;
    @Autowired private UserProximityRepository userProximityRepository;
    @Autowired private ReservationOfferRepository reservationOfferRepository;
    @Autowired private PaintingLikeRepository likeRepository;

    private User artist1;
    private User viewer;
    private User viewerFar;

    private Painting painting1;
    private Painting painting2;
    private Painting painting3;
    private Painting paintingWithAcceptedReservation;

    private PaintingStyle styleModern;
    private String uniqueSuffix;

    @BeforeEach
    void setUp() {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        artist1 = userRepository.save(new User(UserRole.ARTIST, "vanGogh" + uniqueSuffix, "Some Street 1", 52.0, 21.0));
        User artist2 = userRepository.save(new User(UserRole.ARTIST, "Monet" + uniqueSuffix, "Some Street 2", 53.0, 22.0));
        viewer = userRepository.save(new User(UserRole.HOTEL, "Hotel Grand" + uniqueSuffix, "Warszawa", 52.2, 21.0));
        viewerFar = userRepository.save(new User(UserRole.HOTEL, "Hotel Far" + uniqueSuffix, "Gdansk", 54.35, 18.65));

        styleModern = paintingStyleRepository.save(new PaintingStyle("Modern_" + uniqueSuffix));

        painting1 = paintingRepository.save(Painting.builder()
                .artist(artist1)
                .title("Starry Night " + uniqueSuffix)
                .description("A famous masterpiece")
                .price(new Money(new BigDecimal("1500"), "EUR"))
                .dimensions(new Dimensions(60, 120, Unit.CM))
                .medium(PaintingMedium.OIL)
                .styles(Set.of(styleModern))
                .build());

        painting2 = paintingRepository.save(Painting.builder()
                .artist(artist1)
                .title("Sunflowers " + uniqueSuffix)
                .description("Bright flowers")
                .price(new Money(new BigDecimal("800"), "EUR"))
                .dimensions(new Dimensions(30, 20, Unit.CM))
                .medium(PaintingMedium.ACRYLIC)
                .build());

        painting3 = paintingRepository.save(Painting.builder()
                .artist(artist2)
                .title("Water Lilies " + uniqueSuffix)
                .description("Calm water")
                .price(new Money(new BigDecimal("2200"), "EUR"))
                .dimensions(new Dimensions(50, 50, Unit.CM))
                .medium(PaintingMedium.WATERCOLOR)
                .build());

        paintingWithAcceptedReservation = paintingRepository.save(Painting.builder()
                .artist(artist2)
                .title("Reserved " + uniqueSuffix)
                .description("Already sold")
                .price(new Money(new BigDecimal("500"), "EUR"))
                .dimensions(new Dimensions(40, 40, Unit.CM))
                .medium(PaintingMedium.OIL)
                .build());
        
        reservationOfferRepository.save(ReservationOffer.builder()
                .hotel(viewer)
                .painting(paintingWithAcceptedReservation)
                .status(ReservationStatus.ACCEPTED)
                .build());

        userProximityRepository.save(new UserProximity(viewer, artist1, 25.0));
        userProximityRepository.save(new UserProximity(viewer, artist2, 145.0));
        userProximityRepository.save(new UserProximity(viewerFar, artist1, 350.0));
        userProximityRepository.save(new UserProximity(viewerFar, artist2, 400.0));

        likeRepository.save(new PaintingLike(viewer, painting1));
    }

    // ------------------------------------------------------------------
    // Happy Path & Basic Feed Operations
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Happy Path and Basic Operations")
    class HappyPathAndBasic {

        @Test
        @DisplayName("provides painting feed successfully with all active paintings")
        void shouldProvidePaintingFeedSuccessfully() {
            // given
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(null, null, null, null, null);

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 10));

            // then
            assertThat(feed).isNotNull();
            assertThat(feed.getContent()).hasSize(3);
            assertThat(feed.getContent()).extracting(PaintingFeedItemResponse::id)
                    .containsExactlyInAnyOrder(painting1.getId(), painting2.getId(), painting3.getId());
        }

        @Test
        @DisplayName("gets painting details successfully")
        void shouldGetPaintingDetails() {
            // when
            PaintingDetailsResponse details = paintingFeedService.getPaintingDetails(
                    painting1.getId(), viewer.getId());

            // then
            assertThat(details).isNotNull();
            assertThat(details.id()).isEqualTo(painting1.getId());
            assertThat(details.title()).isEqualTo("Starry Night " + uniqueSuffix);
            assertThat(details.artistId()).isEqualTo(artist1.getId());
            assertThat(details.artistNickname()).isEqualTo("vanGogh" + uniqueSuffix);
            assertThat(details.medium()).isEqualTo(PaintingMedium.OIL);
            assertThat(details.styles()).contains(styleModern.getName());
        }

        @Test
        @DisplayName("excludes archived paintings from feed")
        void shouldExcludeArchivedPaintingsFromFeed() {
            // given
            painting1.archive();
            paintingRepository.save(painting1);
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(null, null, null, null, null);

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 10));

            // then
            assertThat(feed.getContent()).hasSize(2);
            assertThat(feed.getContent()).extracting(PaintingFeedItemResponse::id)
                    .doesNotContain(painting1.getId());
        }
    }

    // ------------------------------------------------------------------
    // Solo Filters
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Solo Filters")
    class SoloFilters {

        @Test
        @DisplayName("filters paintings by medium")
        void shouldFilterPaintingsByMedium() {
            // given
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(
                    null, null, null, List.of(PaintingMedium.OIL), null);

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 10));

            // then
            assertThat(feed.getContent()).hasSize(1);
            assertThat(feed.getContent().getFirst().id()).isEqualTo(painting1.getId());
        }

        @Test
        @DisplayName("filters paintings by size category")
        void shouldFilterPaintingsBySizeCategory() {
            // given
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(
                    null, null, List.of(SizeCategory.SMALL), null, null);

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 10));

            // then
            assertThat(feed.getContent()).hasSize(1);
            assertThat(feed.getContent().getFirst().id()).isEqualTo(painting2.getId());
        }

        @Test
        @DisplayName("filters paintings by orientation")
        void shouldFilterPaintingsByOrientation() {
            // given
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(
                    null, null, null, null, List.of(Orientation.SQUARE));

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 10));

            // then
            assertThat(feed.getContent()).hasSize(1);
            assertThat(feed.getContent().getFirst().id()).isEqualTo(painting3.getId());
        }

        @Test
        @DisplayName("filters paintings by style")
        void shouldFilterPaintingsByStyle() {
            // given
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(
                    null, List.of(styleModern), null, null, null);

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 10));

            // then
            assertThat(feed.getContent()).hasSize(1);
            assertThat(feed.getContent().getFirst().id()).isEqualTo(painting1.getId());
        }

        @Test
        @DisplayName("filters paintings by proximity radius solo")
        void shouldFilterPaintingsByRadiusSolo() {
            // given
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(50.0, null, null, null, null);

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 10));

            // then
            assertThat(feed.getContent()).extracting(PaintingFeedItemResponse::id)
                    .containsExactlyInAnyOrder(painting1.getId(), painting2.getId())
                    .doesNotContain(painting3.getId(), paintingWithAcceptedReservation.getId());
        }
    }

    // ------------------------------------------------------------------
    // Filter Intersections
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Filter Intersections")
    class FilterIntersections {

        @Test
        @DisplayName("filters paintings by medium and size category intersection")
        void shouldFilterPaintingsByMediumAndSizeCategoryIntersection() {
            // given
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(
                    null, null, List.of(SizeCategory.LARGE), List.of(PaintingMedium.OIL), null);

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 10));

            // then
            assertThat(feed.getContent()).hasSize(1);
            assertThat(feed.getContent().getFirst().id()).isEqualTo(painting1.getId());
        }

        @Test
        @DisplayName("filters paintings by medium and style intersection")
        void shouldFilterPaintingsByMediumAndStyleIntersection() {
            // given
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(
                    null, List.of(styleModern), null, List.of(PaintingMedium.OIL), null);

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 10));

            // then
            assertThat(feed.getContent()).hasSize(1);
            assertThat(feed.getContent().getFirst().id()).isEqualTo(painting1.getId());
        }

        @Test
        @DisplayName("filters paintings by size category and orientation intersection")
        void shouldFilterPaintingsBySizeCategoryAndOrientationIntersection() {
            // given
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(
                    null, null, List.of(SizeCategory.MEDIUM), null, List.of(Orientation.SQUARE));

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 10));

            // then
            assertThat(feed.getContent()).hasSize(1);
            assertThat(feed.getContent().getFirst().id()).isEqualTo(painting3.getId());
        }

        @Test
        @DisplayName("returns empty when filter intersection does not match any painting")
        void shouldReturnEmptyWhenIntersectionOfFiltersDoesNotMatchAnyPainting() {
            // given
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(
                    null, null, List.of(SizeCategory.SMALL), List.of(PaintingMedium.OIL), null);

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 10));

            // then
            assertThat(feed.getContent()).isEmpty();
        }

        @Test
        @DisplayName("returns empty when radius excludes all matching medium")
        void shouldReturnEmptyWhenRadiusExcludesAllMatchingMedium() {
            // given
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(
                    50.0, null, null, List.of(PaintingMedium.WATERCOLOR), null);

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 10));

            // then
            assertThat(feed.getContent()).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // Full Conjunction
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Full Conjunction")
    class FullConjunction {

        @Test
        @DisplayName("applies all five filters together successfully")
        void shouldApplyAllFiveFiltersTogether() {
            // given
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(
                    50.0,
                    List.of(styleModern),
                    List.of(SizeCategory.LARGE),
                    List.of(PaintingMedium.OIL),
                    List.of(Orientation.PORTRAIT)
            );

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 10));

            // then
            assertThat(feed.getContent()).extracting(PaintingFeedItemResponse::id)
                    .containsExactly(painting1.getId());
        }
    }

    // ------------------------------------------------------------------
    // Reservation Rules
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Reservation Rules")
    class ReservationRules {

        @Test
        @DisplayName("excludes painting with accepted reservation")
        void shouldExcludePaintingWithAcceptedReservation() {
            // given
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(null, null, null, null, null);

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 10));

            // then
            assertThat(feed.getContent()).extracting(PaintingFeedItemResponse::id)
                    .doesNotContain(paintingWithAcceptedReservation.getId());
        }

        @Test
        @DisplayName("includes painting with pending reservation")
        void shouldIncludePaintingWithPendingReservation() {
            // given
            Painting pending = paintingRepository.save(Painting.builder()
                    .artist(artist1)
                    .title("Pending " + uniqueSuffix)
                    .description("pending")
                    .price(new Money(new BigDecimal("100"), "EUR"))
                    .dimensions(new Dimensions(10, 10, Unit.CM))
                    .medium(PaintingMedium.OIL)
                    .build());
            
            reservationOfferRepository.save(ReservationOffer.builder()
                    .painting(pending)
                    .hotel(viewer)
                    .status(ReservationStatus.PENDING)
                    .build());

            PaintingFilterCriteria criteria = new PaintingFilterCriteria(null, null, null, null, null);

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 10));

            // then
            assertThat(feed.getContent()).extracting(PaintingFeedItemResponse::id)
                    .contains(pending.getId());
        }
    }

    // ------------------------------------------------------------------
    // Null vs Empty Criteria & Radius Edge Cases
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Criteria and Radius Edge Cases")
    class CriteriaEdgeCases {

        @Test
        @DisplayName("treats empty lists same as null filters")
        void shouldTreatEmptyListsSameAsNullFilters() {
            // given
            PaintingFilterCriteria empty = new PaintingFilterCriteria(
                    null, List.of(), List.of(), List.of(), List.of());
            PaintingFilterCriteria nulls = new PaintingFilterCriteria(
                    null, null, null, null, null);

            // when
            var a = paintingFeedService.providePaintingFeed(viewer.getId(), empty, PageRequest.of(0, 10));
            var b = paintingFeedService.providePaintingFeed(viewer.getId(), nulls, PageRequest.of(0, 10));

            // then
            assertThat(a.getContent()).extracting(PaintingFeedItemResponse::id)
                    .containsExactlyInAnyOrderElementsOf(
                            b.getContent().stream().map(PaintingFeedItemResponse::id).toList());
        }

        @Test
        @DisplayName("ignores radius when null, zero or negative")
        void shouldIgnoreRadiusEdgeCases() {
            // given
            PaintingFilterCriteria nullRadius = new PaintingFilterCriteria(null, null, null, null, null);
            PaintingFilterCriteria zero = new PaintingFilterCriteria(0.0, null, null, null, null);
            PaintingFilterCriteria negative = new PaintingFilterCriteria(-10.0, null, null, null, null);

            // when & then
            assertThat(paintingFeedService.providePaintingFeed(viewer.getId(), nullRadius, PageRequest.of(0, 10))
                    .getContent()).hasSize(3);
            assertThat(paintingFeedService.providePaintingFeed(viewer.getId(), zero, PageRequest.of(0, 10))
                    .getContent()).hasSize(3);
            assertThat(paintingFeedService.providePaintingFeed(viewer.getId(), negative, PageRequest.of(0, 10))
                    .getContent()).hasSize(3);
        }
    }

    // ------------------------------------------------------------------
    // Social Interactions (Likes)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Social Interactions and Likes")
    class SocialInteractions {

        @Test
        @DisplayName("marks liked paintings correctly in feed")
        void shouldMarkLikedPaintingsInFeed() {
            // given
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(null, null, null, null, null);

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 10));

            // then
            Map<Long, Boolean> likedById = feed.getContent().stream()
                    .collect(Collectors.toMap(PaintingFeedItemResponse::id, PaintingFeedItemResponse::isLiked));

            assertThat(likedById.get(painting1.getId())).isTrue();
            assertThat(likedById.get(painting2.getId())).isFalse();
            assertThat(likedById.get(painting3.getId())).isFalse();
        }

        @Test
        @DisplayName("does not mark liked paintings for another viewer")
        void shouldNotMarkLikedForOtherViewer() {
            // given
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(null, null, null, null, null);

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewerFar.getId(), criteria, PageRequest.of(0, 10));

            // then
            assertThat(feed.getContent()).allMatch(item -> !item.isLiked());
        }
    }

    // ------------------------------------------------------------------
    // Pagination and Ordering
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Pagination and Ordering")
    class PaginationAndOrdering {

        @Test
        @DisplayName("paginates feed correctly")
        void shouldPaginateFeedCorrectly() {
            // given
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(null, null, null, null, null);

            // when
            Slice<PaintingFeedItemResponse> first = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 2));

            // then
            assertThat(first.getContent()).hasSize(2);
            assertThat(first.hasNext()).isTrue();
            assertThat(first.getNumber()).isZero();
            assertThat(first.getSize()).isEqualTo(2);

            Slice<PaintingFeedItemResponse> second = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(1, 2));

            assertThat(second.getContent()).hasSize(1);
            assertThat(second.hasNext()).isFalse();
        }

        @Test
        @DisplayName("returns empty slice when no paintings match criteria")
        void shouldReturnEmptySliceWhenNoPaintingsMatch() {
            // given
            PaintingFilterCriteria criteria = new PaintingFilterCriteria(
                    null, null, List.of(SizeCategory.SMALL), List.of(PaintingMedium.OIL), null);

            // when
            Slice<PaintingFeedItemResponse> feed = paintingFeedService.providePaintingFeed(
                    viewer.getId(), criteria, PageRequest.of(0, 10));

            // then
            assertThat(feed.getContent()).isEmpty();
            assertThat(feed.hasNext()).isFalse();
        }
    }

    // ------------------------------------------------------------------
    // Artist Profile
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Artist Profile")
    class ArtistProfile {

        @Test
        @DisplayName("provides artist profile paintings correctly")
        void shouldProvideArtistProfilePaintings() {
            // when
            Slice<PaintingFeedItemResponse> artistPaintings = paintingFeedService.provideArtistProfilePaintings(
                    viewer.getId(), artist1.getId(), PageRequest.of(0, 10));

            // then
            assertThat(artistPaintings).isNotNull();
            assertThat(artistPaintings.getContent()).hasSize(2);
            assertThat(artistPaintings.getContent()).extracting(PaintingFeedItemResponse::id)
                    .containsExactlyInAnyOrder(painting1.getId(), painting2.getId());
        }

        @Test
        @DisplayName("excludes archived paintings from artist profile")
        void shouldExcludeArchivedPaintingsFromArtistProfile() {
            // given
            painting1.archive();
            paintingRepository.save(painting1);

            // when
            Slice<PaintingFeedItemResponse> result = paintingFeedService.provideArtistProfilePaintings(
                    viewer.getId(), artist1.getId(), PageRequest.of(0, 10));

            // then
            assertThat(result.getContent()).extracting(PaintingFeedItemResponse::id)
                    .containsExactly(painting2.getId());
        }

        @Test
        @DisplayName("returns empty slice for unknown artist ID")
        void shouldReturnEmptyForUnknownArtist() {
            // when
            Slice<PaintingFeedItemResponse> result = paintingFeedService.provideArtistProfilePaintings(
                    viewer.getId(), 999_999L, PageRequest.of(0, 10));

            // then
            assertThat(result.getContent()).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // Painting Details Edge Cases
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Painting Details Edge Cases")
    class PaintingDetailsEdgeCases {

        @Test
        @DisplayName("throws exception when painting is archived")
        void shouldThrowWhenPaintingArchived() {
            // given
            painting1.archive();
            paintingRepository.save(painting1);

            // when & then
            assertThatThrownBy(() -> paintingFeedService.getPaintingDetails(painting1.getId(), viewer.getId()))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("throws exception when painting is not found")
        void shouldThrowWhenPaintingNotFound() {
            // when & then
            assertThatThrownBy(() -> paintingFeedService.getPaintingDetails(999_999L, viewer.getId()))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("returns empty styles list when painting has no styles")
        void shouldReturnEmptyStylesListWhenPaintingHasNoStyles() {
            // when
            PaintingDetailsResponse details = paintingFeedService.getPaintingDetails(
                    painting2.getId(), viewer.getId());

            // then
            assertThat(details.styles()).isEmpty();
        }
    }
}