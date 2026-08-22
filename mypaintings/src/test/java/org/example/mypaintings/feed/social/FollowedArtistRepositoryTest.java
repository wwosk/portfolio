package org.example.mypaintings.feed.social;

import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("FollowedArtistRepository Test Suite")
class FollowedArtistRepositoryTest {

    @Autowired
    private FollowedArtistRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private User hotel1;
    private User hotel2;
    private User artist1;
    private User artist2;

    @BeforeEach
    void setUp() {
        hotel1 = persistUser(UserRole.HOTEL, "hotel1");
        hotel2 = persistUser(UserRole.HOTEL, "hotel2");
        artist1 = persistUser(UserRole.ARTIST, "artist1");
        artist2 = persistUser(UserRole.ARTIST, "artist2");
        entityManager.flush();
        entityManager.clear();
    }

    // ------------------------------------------------------------------
    // existsByHotelAndArtist
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("existsByHotelAndArtist")
    class ExistsByHotelAndArtist {

        @Test
        @DisplayName("reflects follow relation accurately")
        void existsByHotelAndArtist_reflectsFollowRelation() {
            // given
            persistFollow(hotel1, artist1);
            entityManager.flush();
            entityManager.clear();

            // when & then
            assertThat(repository.existsByHotelAndArtist(hotel1, artist1)).isTrue();
            assertThat(repository.existsByHotelAndArtist(hotel1, artist2)).isFalse();
            assertThat(repository.existsByHotelAndArtist(hotel2, artist1)).isFalse();
        }
    }

    // ------------------------------------------------------------------
    // deleteByHotelAndArtist
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("deleteByHotelAndArtist")
    class DeleteByHotelAndArtist {

        @Test
        @DisplayName("removes only the matching follow row")
        void deleteByHotelAndArtist_removesOnlyMatchingRow() {
            // given
            persistFollow(hotel1, artist1);
            persistFollow(hotel1, artist2);
            persistFollow(hotel2, artist1);
            entityManager.flush();
            entityManager.clear();

            // when
            repository.deleteByHotelAndArtist(hotel1, artist1);
            entityManager.flush();
            entityManager.clear();

            // then
            assertThat(repository.existsByHotelAndArtist(hotel1, artist1)).isFalse();
            assertThat(repository.existsByHotelAndArtist(hotel1, artist2)).isTrue();
            assertThat(repository.existsByHotelAndArtist(hotel2, artist1)).isTrue();
        }
    }

    // ------------------------------------------------------------------
    // countByArtistId
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("countByArtistId")
    class CountByArtistId {

        @Test
        @DisplayName("counts followers of the given artist only")
        void countByArtistId_countsFollowersOfGivenArtistOnly() {
            // given
            persistFollow(hotel1, artist1);
            persistFollow(hotel2, artist1);
            persistFollow(hotel1, artist2);
            entityManager.flush();
            entityManager.clear();

            // when & then
            assertThat(repository.countByArtistId(artist1.getId())).isEqualTo(2);
            assertThat(repository.countByArtistId(artist2.getId())).isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------
    // findAllByHotelId
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findAllByHotelId")
    class FindAllByHotelId {

        @Test
        @DisplayName("returns follows of the given hotel only")
        void findAllByHotelId_returnsFollowsOfGivenHotelOnly() {
            // given
            persistFollow(hotel1, artist1);
            persistFollow(hotel1, artist2);
            persistFollow(hotel2, artist1);
            entityManager.flush();
            entityManager.clear();

            // when
            List<FollowedArtist> result = repository.findAllByHotelId(hotel1.getId());

            // then
            assertThat(result).hasSize(2);
            assertThat(result)
                    .extracting(f -> f.getArtist().getId())
                    .containsExactlyInAnyOrder(artist1.getId(), artist2.getId());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private User persistUser(UserRole role, String nickname) {
        return entityManager.persist(new User(role, nickname, "addr", 0.0, 0.0));
    }

    private void persistFollow(User hotel, User artist) {
        FollowedArtist follow = new FollowedArtist();
        follow.setHotel(hotel);
        follow.setArtist(artist);
        entityManager.persist(follow);
    }
}
