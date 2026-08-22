package org.example.mypaintings.feed.social;

import org.example.mypaintings.feed.social.dto.PaintingLikeCountDto;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.painting.features.PaintingMedium;
import org.example.mypaintings.painting.features.Unit;
import org.example.mypaintings.sale.financial.Money;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

@DataJpaTest
@DisplayName("PaintingLikeRepository Test Suite")
class PaintingLikeRepositoryTest {

    @Autowired
    private PaintingLikeRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private User hotel1;
    private User hotel2;
    private Painting painting1;
    private Painting painting2;
    private Painting painting3;

    @BeforeEach
    void setUp() {
        hotel1 = persistUser(UserRole.HOTEL, "hotel1");
        hotel2 = persistUser(UserRole.HOTEL, "hotel2");
        User artist = persistUser(UserRole.ARTIST, "artist1");
        painting1 = persistPainting(artist, "P1");
        painting2 = persistPainting(artist, "P2");
        painting3 = persistPainting(artist, "P3");
        entityManager.flush();
        entityManager.clear();
    }

    // ------------------------------------------------------------------
    // findLikedPaintingIdsByHotelAndPaintingIds
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findLikedPaintingIdsByHotelAndPaintingIds")
    class FindLikedPaintingIds {

        @Test
        @DisplayName("returns only liked painting IDs for the given hotel")
        void findLikedPaintingIds_returnsOnlyLikedIdsForGivenHotel() {
            // given
            persistLike(hotel1, painting1);
            persistLike(hotel1, painting3);
            persistLike(hotel2, painting2);
            entityManager.flush();
            entityManager.clear();

            // when
            Set<Long> result = repository.findLikedPaintingIdsByHotelAndPaintingIds(
                    hotel1.getId(),
                    List.of(painting1.getId(), painting2.getId(), painting3.getId())
            );

            // then
            assertThat(result).containsExactlyInAnyOrder(painting1.getId(), painting3.getId());
        }

        @Test
        @DisplayName("returns empty set when hotel has not liked any of the specified paintings")
        void findLikedPaintingIds_returnsEmpty_whenHotelLikedNone() {
            // given
            persistLike(hotel2, painting1);
            entityManager.flush();
            entityManager.clear();

            // when
            Set<Long> result = repository.findLikedPaintingIdsByHotelAndPaintingIds(
                    hotel1.getId(),
                    List.of(painting1.getId(), painting2.getId())
            );

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty set when input painting IDs collection is empty")
        void findLikedPaintingIds_returnsEmpty_whenPaintingIdsIsEmpty() {
            // when
            Set<Long> result = repository.findLikedPaintingIdsByHotelAndPaintingIds(
                    hotel1.getId(),
                    List.of()
            );

            // then
            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // countLikesForPaintingIds
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("countLikesForPaintingIds")
    class CountLikesForPaintingIds {

        @Test
        @DisplayName("returns correct like count per painting")
        void countLikesForPaintingIds_returnsCountPerPainting() {
            // given
            persistLike(hotel1, painting1);
            persistLike(hotel2, painting1);
            persistLike(hotel1, painting2);
            entityManager.flush();
            entityManager.clear();

            // when
            List<PaintingLikeCountDto> result = repository.countLikesForPaintingIds(
                    List.of(painting1.getId(), painting2.getId(), painting3.getId())
            );

            // then
            assertThat(result).hasSize(2);
            assertThat(result)
                    .extracting(PaintingLikeCountDto::paintingId, PaintingLikeCountDto::likesCount)
                    .containsExactlyInAnyOrder(
                            tuple(painting1.getId(), 2L),
                            tuple(painting2.getId(), 1L)
                    );
        }

        @Test
        @DisplayName("returns empty list when there are no likes for specified paintings")
        void countLikesForPaintingIds_returnsEmpty_whenNoLikes() {
            // when
            List<PaintingLikeCountDto> result = repository.countLikesForPaintingIds(
                    List.of(painting1.getId(), painting2.getId())
            );

            // then
            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // existsByPaintingAndHotelId
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("existsByPaintingAndHotelId")
    class ExistsByPaintingAndHotelId {

        @Test
        @DisplayName("returns true when like exists and false otherwise")
        void existsByPaintingAndHotelId_returnsTrue_whenLikeExists() {
            // given
            persistLike(hotel1, painting1);
            entityManager.flush();
            entityManager.clear();

            // when & then
            assertThat(repository.existsByPaintingAndHotelId(painting1, hotel1.getId())).isTrue();
            assertThat(repository.existsByPaintingAndHotelId(painting2, hotel1.getId())).isFalse();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private User persistUser(UserRole role, String nickname) {
        return entityManager.persist(new User(role, nickname, "addr", 0.0, 0.0));
    }

    private Painting persistPainting(User artist, String title) {
        Painting p = Painting.builder()
                .artist(artist)
                .title(title)
                .dimensions(new Dimensions(10.0, 10.0, Unit.CM))
                .price(new Money(BigDecimal.TEN, "PLN"))
                .medium(PaintingMedium.OIL)
                .build();
        return entityManager.persist(p);
    }

    private void persistLike(User hotel, Painting painting) {
        entityManager.persist(new PaintingLike(hotel, painting));
    }
}
