package org.example.mypaintings.painting;

import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.painting.features.PaintingMedium;
import org.example.mypaintings.painting.features.Unit;
import org.example.mypaintings.sale.financial.Money;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserRepository;
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import(PaintingQueryService.class)
class PaintingQueryServiceTest {

    @Autowired
    private PaintingQueryService paintingQueryService;

    @Autowired
    private PaintingRepository paintingRepository;

    @Autowired
    private UserRepository userRepository;

    private User artist;

    @BeforeEach
    void setUp() {
        // given
        artist = userRepository.save(new User(
                UserRole.ARTIST, "artist-it", "Addr 1", 50.0, 19.0));
    }

    @Nested
    @DisplayName("getActiveOrThrow")
    class GetActiveOrThrow {

        @Test
        @DisplayName("returns active painting")
        void returnsActivePainting() {
            // given
            Painting painting = savePainting("Active", false);

            // when
            Painting result = paintingQueryService.getActiveOrThrow(painting.getId());

            // then
            assertThat(result.getId()).isEqualTo(painting.getId());
            assertThat(result.isArchived()).isFalse();
        }

        @Test
        @DisplayName("throws IllegalStateException for archived painting")
        void throwsForArchived() {
            // given
            Painting painting = savePainting("Archived", true);

            // when / then
            assertThatThrownBy(() -> paintingQueryService.getActiveOrThrow(painting.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("archived");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for missing painting")
        void throwsForMissing() {
            // given / when / then
            assertThatThrownBy(() -> paintingQueryService.getActiveOrThrow(999_999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getActiveWithDetailsOrThrow")
    class GetActiveWithDetailsOrThrow {

        @Test
        @DisplayName("returns painting with fetched details")
        void returnsPaintingWithDetails() {
            // given
            Painting painting = savePainting("WithDetails", false);

            // when
            Painting result = paintingQueryService.getActiveWithDetailsOrThrow(painting.getId());

            // then
            assertThat(result.getAttachments()).isNotNull();
            assertThat(result.getStyles()).isNotNull();
            assertThat(result.getArtist()).isNotNull();
            assertThat(result.getArtist().getId()).isEqualTo(artist.getId());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for missing painting")
        void throwsForMissing() {
            // given / when / then
            assertThatThrownBy(() -> paintingQueryService.getActiveWithDetailsOrThrow(999_999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("getPaintingWithLockOrThrow returns painting when present")
    void getPaintingWithLockOrThrowReturnsPainting() {
        // given
        Painting painting = savePainting("Locked", false);

        // when
        Painting result = paintingQueryService.getPaintingWithLockOrThrow(painting.getId());

        // then
        assertThat(result.getId()).isEqualTo(painting.getId());
    }

    @Test
    @DisplayName("getPaintingWithLockOrThrow throws for missing painting")
    void getPaintingWithLockOrThrowThrowsForMissing() {
        // given / when / then
        assertThatThrownBy(() -> paintingQueryService.getPaintingWithLockOrThrow(999_999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getAllArtistPaintings returns only paintings of given artist")
    void getAllArtistPaintingsReturnsOnlyOwned() {
        // given
        User otherArtist = userRepository.save(new User(
                UserRole.ARTIST, "other-artist", "Addr 2", 50.1, 19.1));

        Painting mine = savePainting("Mine", false);
        savePaintingFor(otherArtist, "Not mine");

        // when
        List<Painting> result = paintingQueryService.getAllArtistPaintings(artist.getId());

        // then
        assertThat(result).extracting(Painting::getId).containsExactly(mine.getId());
    }

    @Test
    @DisplayName("getPaintingsWithDetailsByIds returns only requested paintings")
    void getPaintingsWithDetailsByIdsReturnsOnlyRequested() {
        // given
        Painting p1 = savePainting("P1", false);
        Painting p2 = savePainting("P2", false);
        savePainting("P3", false);

        // when
        List<Painting> result = paintingQueryService.getPaintingsWithDetailsByIds(
                List.of(p1.getId(), p2.getId()));

        // then
        assertThat(result)
                .extracting(Painting::getId)
                .containsExactlyInAnyOrder(p1.getId(), p2.getId());
    }

    @Test
    @DisplayName("getPaintingSlicesBySpecification applies specification and returns slice")
    void getPaintingSlicesBySpecificationAppliesFilter() {
        // given
        savePainting("Alpha", false);
        savePainting("Beta", false);

        Specification<Painting> onlyAlpha = (root, query, cb) ->
                cb.equal(root.get("title"), "Alpha");

        // when
        Slice<Painting> result = paintingQueryService.getPaintingSlicesBySpecification(
                onlyAlpha, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent())
                .extracting(Painting::getTitle)
                .containsExactly("Alpha");
    }

    @Test
    @DisplayName("getArtistPaintingCount counts only given artist's paintings")
    void getArtistPaintingCountCountsOnlyOwned() {
        // given
        User otherArtist = userRepository.save(new User(
                UserRole.ARTIST, "other-artist-2", "Addr 3", 50.2, 19.2));

        savePainting("Mine 1", false);
        savePainting("Mine 2", false);
        savePaintingFor(otherArtist, "Not mine");

        // when
        long count = paintingQueryService.getArtistPaintingCount(artist.getId());

        // then
        assertThat(count).isEqualTo(2L);
    }

    private Painting savePainting(String title, boolean archived) {
        Painting painting = buildPainting(artist, title);
        if (archived) {
            painting.archive();
        }
        return paintingRepository.saveAndFlush(painting);
    }

    private Painting savePaintingFor(User owner, String title) {
        return paintingRepository.saveAndFlush(buildPainting(owner, title));
    }

    private Painting buildPainting(User owner, String title) {
        return Painting.builder()
                .artist(owner)
                .title(title)
                .medium(PaintingMedium.OIL)
                .price(new Money(BigDecimal.TEN, "PLN"))
                .dimensions(new Dimensions(100, 100, Unit.CM))
                .build();
    }
}