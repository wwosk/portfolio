package org.example.mypaintings.sale;

import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.attachedimages.AttachedImage;
import org.example.mypaintings.painting.features.*;
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

@DataJpaTest
@DisplayName("SaleRepository Test Suite")
class SaleRepositoryTest {

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User artistA;
    private User artistB;

    @BeforeEach
    void setUp() {
        artistA = entityManager.persistAndFlush(
                new User(UserRole.ARTIST, "artistA", "Addr A", 50.0, 20.0));
        artistB = entityManager.persistAndFlush(
                new User(UserRole.ARTIST, "artistB", "Addr B", 51.0, 21.0));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Painting createPainting(User artist, String title) {
        Painting painting = Painting.builder()
                .artist(artist)
                .title(title)
                .dimensions(new Dimensions(50.0, 40.0, Unit.CM))
                .description("desc")
                .price(new Money(BigDecimal.valueOf(100), "PLN"))
                .styles(Set.of())
                .medium(PaintingMedium.OIL)
                .build();
        return entityManager.persistAndFlush(painting);
    }

    private AttachedImage createAttachment(Painting painting, String fileKey) {
        AttachedImage img = new AttachedImage();
        img.setFileKey(fileKey);
        img.setOriginalFileName(fileKey + ".jpg");
        img.setContentType("image/jpeg");
        img.setFileSize(1024L);
        img.setPainting(painting);
        return entityManager.persistAndFlush(img);
    }

    private Sale createSale(Painting painting) {
        Sale sale = new Sale();
        sale.setPainting(painting);
        sale.setBuyerContactInfo(new BuyerContactInfo(
                "buyer@example.com",
                "+48123456789",
                "ul. Testowa 1, 00-001 Warszawa"
        ));
        sale.setRequiresShipping(false);
        return entityManager.persistAndFlush(sale);
    }

    // ------------------------------------------------------------------
    // existsByPaintingId
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("existsByPaintingId")
    class ExistsByPaintingId {

        @Test
        @DisplayName("returns true when sale exists for painting")
        void returnsTrueWhenSaleExists() {
            // given
            Painting painting = createPainting(artistA, "Sunset");
            createSale(painting);

            // when
            boolean exists = saleRepository.existsByPaintingId(painting.getId());

            // then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("returns false when no sale exists for painting")
        void returnsFalseWhenNoSaleExists() {
            // given
            Painting painting = createPainting(artistA, "Sunset");

            // when
            boolean exists = saleRepository.existsByPaintingId(painting.getId());

            // then
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("returns false for non-existing painting ID")
        void returnsFalseForNonExistingPaintingId() {
            // when
            boolean exists = saleRepository.existsByPaintingId(999_999L);

            // then
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("distinguishes between sales for different paintings")
        void distinguishesBetweenPaintings() {
            // given
            Painting p1 = createPainting(artistA, "P1");
            Painting p2 = createPainting(artistA, "P2");
            createSale(p1);

            // when & then
            assertThat(saleRepository.existsByPaintingId(p1.getId())).isTrue();
            assertThat(saleRepository.existsByPaintingId(p2.getId())).isFalse();
        }
    }

    // ------------------------------------------------------------------
    // findAllByPaintingArtistIdWithPaintingAndAttachments
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findAllByPaintingArtistIdWithPaintingAndAttachments")
    class FindAllByArtistIdWithDetails {

        @Test
        @DisplayName("returns only sales belonging to the given artist")
        void returnsOnlySalesOfGivenArtist() {
            // given
            Painting pA = createPainting(artistA, "ArtistA painting");
            Painting pB = createPainting(artistB, "ArtistB painting");
            createSale(pA);
            createSale(pB);

            // when
            List<Sale> result =
                    saleRepository.findAllByPaintingArtistIdWithPaintingAndAttachments(artistA.getId());

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getPainting().getId()).isEqualTo(pA.getId());
        }

        @Test
        @DisplayName("returns empty list when artist has no sales")
        void returnsEmptyWhenArtistHasNoSales() {
            // when
            List<Sale> result =
                    saleRepository.findAllByPaintingArtistIdWithPaintingAndAttachments(artistA.getId());

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list for non-existing artist ID")
        void returnsEmptyForUnknownArtist() {
            // given
            Painting p = createPainting(artistA, "P");
            createSale(p);

            // when
            List<Sale> result =
                    saleRepository.findAllByPaintingArtistIdWithPaintingAndAttachments(999_999L);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("eager fetches painting and its attachments using JOIN FETCH")
        void fetchesPaintingAndAttachments() {
            // given
            Painting p = createPainting(artistA, "With images");
            createAttachment(p, "img1");
            createAttachment(p, "img2");
            createSale(p);

            entityManager.flush();
            entityManager.clear();

            // when
            List<Sale> result =
                    saleRepository.findAllByPaintingArtistIdWithPaintingAndAttachments(artistA.getId());

            // then
            assertThat(result).hasSize(1);
            Sale sale = result.getFirst();
            assertThat(sale.getPainting().getAttachments())
                    .extracting(AttachedImage::getFileKey)
                    .containsExactlyInAnyOrder("img1", "img2");
        }

        @Test
        @DisplayName("works correctly when painting has no attachments using LEFT JOIN FETCH")
        void worksWhenPaintingHasNoAttachments() {
            // given
            Painting p = createPainting(artistA, "No images");
            createSale(p);

            entityManager.flush();
            entityManager.clear();

            // when
            List<Sale> result =
                    saleRepository.findAllByPaintingArtistIdWithPaintingAndAttachments(artistA.getId());

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getPainting().getAttachments()).isEmpty();
        }

        @Test
        @DisplayName("returns multiple sales for the same artist")
        void returnsMultipleSalesForSameArtist() {
            // given
            Painting p1 = createPainting(artistA, "P1");
            Painting p2 = createPainting(artistA, "P2");
            createSale(p1);
            createSale(p2);

            // when
            List<Sale> result =
                    saleRepository.findAllByPaintingArtistIdWithPaintingAndAttachments(artistA.getId());

            // then
            assertThat(result).hasSize(2);
        }
    }

    // ------------------------------------------------------------------
    // findAllByPaintingIdInWithDetails
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findAllByPaintingIdInWithDetails")
    class FindAllByPaintingIdInWithDetails {

        @Test
        @DisplayName("returns sales only for given painting IDs")
        void returnsOnlyForGivenPaintingIds() {
            // given
            Painting p1 = createPainting(artistA, "P1");
            Painting p2 = createPainting(artistA, "P2");
            Painting p3 = createPainting(artistA, "P3");
            createSale(p1);
            createSale(p2);
            createSale(p3);

            // when
            List<Sale> result = saleRepository.findAllByPaintingIdInWithDetails(
                    List.of(p1.getId(), p2.getId()));

            // then
            assertThat(result)
                    .extracting(s -> s.getPainting().getId())
                    .containsExactlyInAnyOrder(p1.getId(), p2.getId());
        }

        @Test
        @DisplayName("returns empty list for empty ID collection")
        void returnsEmptyForEmptyIdCollection() {
            // given
            Painting p = createPainting(artistA, "P");
            createSale(p);

            // when
            List<Sale> result = saleRepository.findAllByPaintingIdInWithDetails(List.of());

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list for non-existing IDs")
        void returnsEmptyForNonExistingIds() {
            // given
            Painting p = createPainting(artistA, "P");
            createSale(p);

            // when
            List<Sale> result = saleRepository.findAllByPaintingIdInWithDetails(List.of(999_999L));

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("skips paintings without associated sales")
        void skipsPaintingsWithoutSale() {
            // given
            Painting withSale = createPainting(artistA, "withSale");
            Painting withoutSale = createPainting(artistA, "withoutSale");
            createSale(withSale);

            // when
            List<Sale> result = saleRepository.findAllByPaintingIdInWithDetails(
                    List.of(withSale.getId(), withoutSale.getId()));

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getPainting().getId()).isEqualTo(withSale.getId());
        }

        @Test
        @DisplayName("eager fetches painting details after clearing persistence context")
        void fetchesPainting() {
            // given
            Painting p = createPainting(artistA, "P");
            createSale(p);

            entityManager.flush();
            entityManager.clear();

            // when
            List<Sale> result = saleRepository.findAllByPaintingIdInWithDetails(List.of(p.getId()));

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getPainting().getTitle()).isEqualTo("P");
        }
    }
}