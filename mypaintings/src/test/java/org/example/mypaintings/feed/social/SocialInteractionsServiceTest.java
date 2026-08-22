package org.example.mypaintings.feed.social;

import org.example.mypaintings.exception.validation.BusinessValidationException;
import org.example.mypaintings.feed.social.dto.ArtistStatsResponse;
import org.example.mypaintings.feed.social.dto.FollowedArtistResponse;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.PaintingQueryService;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SocialInteractionsService Test Suite")
class SocialInteractionsServiceTest {

    @Mock
    private UserQueryService userQueryService;
    @Mock
    private PaintingLikeRepository paintingLikeRepository;
    @Mock
    private FollowedArtistRepository followedArtistRepository;
    @Mock
    private PaintingQueryService paintingQueryService;

    @InjectMocks
    private SocialInteractionsService service;

    private User hotel;
    private User artist;
    private Painting painting;

    @BeforeEach
    void setUp() {
        hotel = mock(User.class);
        artist = mock(User.class);
        painting = mock(Painting.class);

        lenient().when(hotel.getId()).thenReturn(10L);
        lenient().when(artist.getId()).thenReturn(20L);
        lenient().when(artist.getNickname()).thenReturn("@picasso");
    }

    // ------------------------------------------------------------------
    // likePostToggle
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("likePostToggle")
    class LikePostToggle {

        @Test
        @DisplayName("adds like when it does not exist yet")
        void shouldAddLikeWhenNotExists() {
            // given
            when(paintingQueryService.getActiveOrThrow(1L)).thenReturn(painting);
            when(userQueryService.getValidHotelOrThrow(10L)).thenReturn(hotel);
            when(paintingLikeRepository.findByPaintingAndHotel(painting, hotel))
                    .thenReturn(Optional.empty());

            // when
            service.likePostToggle(1L, 10L);

            // then
            ArgumentCaptor<PaintingLike> captor = ArgumentCaptor.forClass(PaintingLike.class);
            verify(paintingLikeRepository).save(captor.capture());
            PaintingLike saved = captor.getValue();
            assertThat(saved.getPainting()).isSameAs(painting);
            assertThat(saved.getHotel()).isSameAs(hotel);
            verify(paintingLikeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("removes like when it already exists (toggle off)")
        void shouldRemoveLikeWhenExists() {
            // given
            PaintingLike existing = new PaintingLike();
            when(paintingQueryService.getActiveOrThrow(1L)).thenReturn(painting);
            when(userQueryService.getValidHotelOrThrow(10L)).thenReturn(hotel);
            when(paintingLikeRepository.findByPaintingAndHotel(painting, hotel))
                    .thenReturn(Optional.of(existing));

            // when
            service.likePostToggle(1L, 10L);

            // then
            verify(paintingLikeRepository).delete(existing);
            verify(paintingLikeRepository, never()).save(any());
        }

        @Test
        @DisplayName("propagates exception when painting is not found or inactive")
        void shouldPropagateWhenPaintingNotFound() {
            // given
            when(paintingQueryService.getActiveOrThrow(99L))
                    .thenThrow(new BusinessValidationException("Painting not found"));

            // when & then
            assertThatThrownBy(() -> service.likePostToggle(99L, 10L))
                    .isInstanceOf(BusinessValidationException.class);

            verifyNoInteractions(paintingLikeRepository);
        }

        @Test
        @DisplayName("propagates exception when hotel is invalid")
        void shouldPropagateWhenHotelInvalid() {
            // given
            when(paintingQueryService.getActiveOrThrow(1L)).thenReturn(painting);
            when(userQueryService.getValidHotelOrThrow(99L))
                    .thenThrow(new BusinessValidationException("Hotel not found"));

            // when & then
            assertThatThrownBy(() -> service.likePostToggle(1L, 99L))
                    .isInstanceOf(BusinessValidationException.class);

            verifyNoInteractions(paintingLikeRepository);
        }
    }

    // ------------------------------------------------------------------
    // followArtist
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("followArtist")
    class FollowArtist {

        @Test
        @DisplayName("saves follow relation when valid")
        void shouldSaveFollow() {
            // given
            when(userQueryService.getValidHotelOrThrow(10L)).thenReturn(hotel);
            when(userQueryService.getValidArtistOrThrow(20L)).thenReturn(artist);
            when(followedArtistRepository.existsByHotelAndArtist(hotel, artist)).thenReturn(false);

            // when
            service.followArtist(20L, 10L);

            // then
            ArgumentCaptor<FollowedArtist> captor = ArgumentCaptor.forClass(FollowedArtist.class);
            verify(followedArtistRepository).save(captor.capture());
            FollowedArtist saved = captor.getValue();
            assertThat(saved.getHotel()).isSameAs(hotel);
            assertThat(saved.getArtist()).isSameAs(artist);
        }

        @Test
        @DisplayName("throws exception when already following the artist")
        void shouldThrowWhenAlreadyFollowing() {
            // given
            when(userQueryService.getValidHotelOrThrow(10L)).thenReturn(hotel);
            when(userQueryService.getValidArtistOrThrow(20L)).thenReturn(artist);
            when(followedArtistRepository.existsByHotelAndArtist(hotel, artist)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> service.followArtist(20L, 10L))
                    .isInstanceOf(BusinessValidationException.class)
                    .hasMessageContaining("Already following");

            verify(followedArtistRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws exception when attempting to follow own account")
        void shouldThrowWhenFollowingSelf() {
            // given
            when(hotel.getId()).thenReturn(10L);
            when(artist.getId()).thenReturn(10L);
            when(userQueryService.getValidHotelOrThrow(10L)).thenReturn(hotel);
            when(userQueryService.getValidArtistOrThrow(10L)).thenReturn(artist);
            when(followedArtistRepository.existsByHotelAndArtist(hotel, artist)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> service.followArtist(10L, 10L))
                    .isInstanceOf(BusinessValidationException.class)
                    .hasMessageContaining("own account");

            verify(followedArtistRepository, never()).save(any());
        }

        @Test
        @DisplayName("propagates exception when hotel is invalid")
        void shouldPropagateWhenHotelInvalid() {
            // given
            when(userQueryService.getValidHotelOrThrow(99L))
                    .thenThrow(new BusinessValidationException("Hotel not found"));

            // when & then
            assertThatThrownBy(() -> service.followArtist(20L, 99L))
                    .isInstanceOf(BusinessValidationException.class);

            verifyNoInteractions(followedArtistRepository);
        }

        @Test
        @DisplayName("propagates exception when artist is invalid")
        void shouldPropagateWhenArtistInvalid() {
            // given
            when(userQueryService.getValidHotelOrThrow(10L)).thenReturn(hotel);
            when(userQueryService.getValidArtistOrThrow(99L))
                    .thenThrow(new BusinessValidationException("Artist not found"));

            // when & then
            assertThatThrownBy(() -> service.followArtist(99L, 10L))
                    .isInstanceOf(BusinessValidationException.class);

            verifyNoInteractions(followedArtistRepository);
        }
    }

    // ------------------------------------------------------------------
    // unfollowArtist
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("unfollowArtist")
    class UnfollowArtist {

        @Test
        @DisplayName("invokes deleteByHotelAndArtist successfully")
        void shouldDeleteFollow() {
            // given
            when(userQueryService.getValidHotelOrThrow(10L)).thenReturn(hotel);
            when(userQueryService.getValidArtistOrThrow(20L)).thenReturn(artist);

            // when
            service.unfollowArtist(20L, 10L);

            // then
            verify(followedArtistRepository).deleteByHotelAndArtist(hotel, artist);
        }

        @Test
        @DisplayName("propagates exception when hotel is invalid")
        void shouldPropagateWhenHotelInvalid() {
            // given
            when(userQueryService.getValidHotelOrThrow(99L))
                    .thenThrow(new BusinessValidationException("Hotel not found"));

            // when & then
            assertThatThrownBy(() -> service.unfollowArtist(20L, 99L))
                    .isInstanceOf(BusinessValidationException.class);

            verifyNoInteractions(followedArtistRepository);
        }
    }

    // ------------------------------------------------------------------
    // getArtistStats
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getArtistStats")
    class GetArtistStats {

        @Test
        @DisplayName("returns correct artist statistics")
        void shouldReturnStats() {
            // given
            when(userQueryService.getValidArtistOrThrow(20L)).thenReturn(artist);
            when(followedArtistRepository.countByArtistId(20L)).thenReturn(42L);
            when(paintingQueryService.getArtistPaintingCount(20L)).thenReturn(7L);

            // when
            ArtistStatsResponse response = service.getArtistStats(20L);

            // then
            assertThat(response.nickname()).isEqualTo("@picasso");
            assertThat(response.followersCount()).isEqualTo(42L);
            assertThat(response.paintingsCount()).isEqualTo(7L);
        }

        @Test
        @DisplayName("returns zeros when stats data is empty")
        void shouldReturnZerosWhenEmpty() {
            // given
            when(userQueryService.getValidArtistOrThrow(20L)).thenReturn(artist);
            when(followedArtistRepository.countByArtistId(20L)).thenReturn(0L);
            when(paintingQueryService.getArtistPaintingCount(20L)).thenReturn(0L);

            // when
            ArtistStatsResponse response = service.getArtistStats(20L);

            // then
            assertThat(response.followersCount()).isZero();
            assertThat(response.paintingsCount()).isZero();
        }

        @Test
        @DisplayName("propagates exception when artist is invalid")
        void shouldPropagateWhenArtistInvalid() {
            // given
            when(userQueryService.getValidArtistOrThrow(99L))
                    .thenThrow(new BusinessValidationException("Artist not found"));

            // when & then
            assertThatThrownBy(() -> service.getArtistStats(99L))
                    .isInstanceOf(BusinessValidationException.class);

            verifyNoInteractions(followedArtistRepository, paintingQueryService);
        }
    }

    // ------------------------------------------------------------------
    // getFollowedArtists
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getFollowedArtists")
    class GetFollowedArtists {

        private FollowedArtist followOf(User a) {
            FollowedArtist fa = new FollowedArtist();
            fa.setArtist(a);
            fa.setHotel(hotel);
            return fa;
        }

        @Test
        @DisplayName("maps followed artists list to DTOs with correct initials")
        void shouldMapToDtos() {
            // given
            User artist1 = mock(User.class);
            when(artist1.getId()).thenReturn(1L);
            when(artist1.getNickname()).thenReturn("@vangogh");

            User artist2 = mock(User.class);
            when(artist2.getId()).thenReturn(2L);
            when(artist2.getNickname()).thenReturn("Mo");

            when(userQueryService.getValidHotelOrThrow(10L)).thenReturn(hotel);
            when(followedArtistRepository.findAllByHotelId(10L))
                    .thenReturn(List.of(followOf(artist1), followOf(artist2)));

            // when
            List<FollowedArtistResponse> result = service.getFollowedArtists(10L);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.getFirst().id()).isEqualTo(1L);
            assertThat(result.get(0).nickname()).isEqualTo("@vangogh");
            assertThat(result.get(0).initials()).isEqualTo("VA");

            assertThat(result.get(1).id()).isEqualTo(2L);
            assertThat(result.get(1).initials()).isEqualTo("MO");
        }

        @Test
        @DisplayName("returns empty list when no followed artists exist")
        void shouldReturnEmptyList() {
            // given
            when(userQueryService.getValidHotelOrThrow(10L)).thenReturn(hotel);
            when(followedArtistRepository.findAllByHotelId(10L)).thenReturn(List.of());

            // when
            List<FollowedArtistResponse> result = service.getFollowedArtists(10L);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("propagates exception when hotel is invalid")
        void shouldPropagateWhenHotelInvalid() {
            // given
            when(userQueryService.getValidHotelOrThrow(99L))
                    .thenThrow(new BusinessValidationException("Hotel not found"));

            // when & then
            assertThatThrownBy(() -> service.getFollowedArtists(99L))
                    .isInstanceOf(BusinessValidationException.class);

            verifyNoInteractions(followedArtistRepository);
        }
    }

    // ------------------------------------------------------------------
    // generateInitials (indirect tests via getFollowedArtists)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("generateInitials (indirect)")
    class GenerateInitials {

        private String initialsFor(String nickname) {
            User a = mock(User.class);
            when(a.getId()).thenReturn(1L);
            when(a.getNickname()).thenReturn(nickname);

            FollowedArtist fa = new FollowedArtist();
            fa.setArtist(a);
            fa.setHotel(hotel);

            when(userQueryService.getValidHotelOrThrow(10L)).thenReturn(hotel);
            when(followedArtistRepository.findAllByHotelId(10L)).thenReturn(List.of(fa));

            return service.getFollowedArtists(10L).getFirst().initials();
        }

        @Test
        @DisplayName("returns 'AT' when nickname is null")
        void nullNickname() {
            assertThat(initialsFor(null)).isEqualTo("AT");
        }

        @Test
        @DisplayName("returns 'AT' when nickname is blank")
        void blankNickname() {
            assertThat(initialsFor("   ")).isEqualTo("AT");
        }

        @Test
        @DisplayName("returns single uppercase letter for single-letter nickname")
        void singleLetter() {
            assertThat(initialsFor("a")).isEqualTo("A");
        }

        @Test
        @DisplayName("strips leading '@' prefix and takes first 2 characters uppercased")
        void stripsAtPrefix() {
            assertThat(initialsFor("@picasso")).isEqualTo("PI");
        }

        @Test
        @DisplayName("takes two characters uppercased when no '@' prefix exists")
        void twoChars() {
            assertThat(initialsFor("ab")).isEqualTo("AB");
        }
    }
}