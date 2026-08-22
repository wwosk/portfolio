package org.example.mypaintings.painting.styles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:style_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false",
        "spring.test.database.replace=none"
})
@DisplayName("PaintingStyleRepository Test Suite")
class PaintingStyleRepositoryTest {

    @Autowired
    private PaintingStyleRepository paintingStyleRepository;

    @Autowired
    private TestEntityManager tem;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setUp() {
        em.createNativeQuery("DELETE FROM painting_painting_styles").executeUpdate();
        em.createNativeQuery("DELETE FROM painting_styles").executeUpdate();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private PaintingStyle newStyle(String name, long usageCount, boolean official) {
        PaintingStyle style = new PaintingStyle(name);
        tem.persistAndFlush(style);

        for (long i = 0; i < usageCount; i++) {
            style.incrementUsageCount(Long.MAX_VALUE);
        }
        if (official) {
            em.createNativeQuery(
                            "UPDATE painting_styles SET is_official = true WHERE id = :id")
                    .setParameter("id", style.getId())
                    .executeUpdate();
        }
        tem.flush();
        tem.clear();
        return style;
    }

    // ---------------------------------------------------------------------
    // findByNormalizedName
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("findByNormalizedName")
    class FindByNormalizedName {

        @Test
        @DisplayName("returns style when normalized name matches")
        void findByNormalizedName_returnsStyle() {
            // given
            PaintingStyle saved = newStyle("Oil Painting", 0, false);

            // when
            Optional<PaintingStyle> result =
                    paintingStyleRepository.findByNormalizedName("oil painting");

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(saved.getId());
            assertThat(result.get().getName()).isEqualTo("Oil Painting");
        }

        @Test
        @DisplayName("is case sensitive because input must already be normalized")
        void findByNormalizedName_isCaseSensitive() {
            // given
            newStyle("Oil Painting", 0, false);

            // when
            Optional<PaintingStyle> result = paintingStyleRepository.findByNormalizedName("OIL PAINTING");

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when normalized name does not exist")
        void findByNormalizedName_emptyWhenMissing() {
            // when
            Optional<PaintingStyle> result = paintingStyleRepository.findByNormalizedName("does not exist");

            // then
            assertThat(result).isEmpty();
        }
    }

    // ---------------------------------------------------------------------
    // autocomplete
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("autocomplete")
    class Autocomplete {

        @Test
        @DisplayName("performs prefix match case-insensitively")
        void autocomplete_prefixMatchCaseInsensitive() {
            // given
            newStyle("Oil", 0, false);
            newStyle("Oil Pastel", 0, false);
            newStyle("Acrylic", 0, false);

            // when
            List<PaintingStyle> result = paintingStyleRepository.autocomplete(
                    "oil", PageRequest.of(0, 10));

            // then
            assertThat(result)
                    .extracting(PaintingStyle::getName)
                    .containsExactlyInAnyOrder("Oil", "Oil Pastel");
        }

        @Test
        @DisplayName("sorts by official DESC, then usageCount DESC, then name ASC")
        void autocomplete_sortsByOfficialThenUsageThenName() {
            // given
            newStyle("Watercolor A", 10, false);
            newStyle("Watercolor B", 50, true);
            newStyle("Watercolor C", 100, false);
            newStyle("Watercolor D", 5, true);

            // when
            List<PaintingStyle> result = paintingStyleRepository.autocomplete(
                    "watercolor", PageRequest.of(0, 10));

            // then
            assertThat(result)
                    .extracting(PaintingStyle::getName)
                    .containsExactly(
                            "Watercolor B",
                            "Watercolor D",
                            "Watercolor C",
                            "Watercolor A"
                    );
        }

        @Test
        @DisplayName("sorts by name ASC when official flag and usageCount tie")
        void autocomplete_tiesSortedByNameAsc() {
            // given
            newStyle("Style B", 10, false);
            newStyle("Style A", 10, false);
            newStyle("Style C", 10, false);

            // when
            List<PaintingStyle> result = paintingStyleRepository.autocomplete(
                    "style", PageRequest.of(0, 10));

            // then
            assertThat(result)
                    .extracting(PaintingStyle::getName)
                    .containsExactly("Style A", "Style B", "Style C");
        }

        @Test
        @DisplayName("respects Pageable limit")
        void autocomplete_respectsPageable() {
            // given
            for (int i = 0; i < 5; i++) {
                newStyle("Style " + i, i, false);
            }

            // when
            List<PaintingStyle> result = paintingStyleRepository.autocomplete(
                    "style", PageRequest.of(0, 2));

            // then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("returns all items when query is empty")
        void autocomplete_emptyQueryReturnsAll() {
            // given
            newStyle("Oil", 0, false);
            newStyle("Acrylic", 0, false);

            // when
            List<PaintingStyle> result = paintingStyleRepository.autocomplete(
                    "", PageRequest.of(0, 10));

            // then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("returns empty list when there are no matches")
        void autocomplete_noMatches() {
            // given
            newStyle("Oil", 0, false);

            // when
            List<PaintingStyle> result = paintingStyleRepository.autocomplete(
                    "xyz", PageRequest.of(0, 10));

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("treats percent sign as a wildcard in LIKE clause")
        void autocomplete_percentIsWildcard() {
            // given
            newStyle("Oil", 0, false);
            newStyle("Acrylic", 0, false);

            // when
            List<PaintingStyle> result = paintingStyleRepository.autocomplete(
                    "O%", PageRequest.of(0, 10));

            // then
            assertThat(result)
                    .extracting(PaintingStyle::getName)
                    .containsExactly("Oil");
        }
    }

    // ---------------------------------------------------------------------
    // findByIsOfficialTrueOrderByUsageCountDesc
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("findByIsOfficialTrueOrderByUsageCountDesc")
    class FindByIsOfficialTrue {

        @Test
        @DisplayName("returns only official styles sorted by usageCount DESC")
        void findByIsOfficialTrue_returnsOnlyOfficialSortedByUsage() {
            // given
            newStyle("Official Low", 5, true);
            newStyle("Official High", 100, true);
            newStyle("Unofficial", 1000, false);

            // when
            List<PaintingStyle> result = paintingStyleRepository
                    .findByIsOfficialTrueOrderByUsageCountDesc(PageRequest.of(0, 10));

            // then
            assertThat(result)
                    .extracting(PaintingStyle::getName)
                    .containsExactly("Official High", "Official Low");
        }

        @Test
        @DisplayName("respects Pageable for official styles query")
        void findByIsOfficialTrue_respectsPageable() {
            // given
            newStyle("Official 1", 10, true);
            newStyle("Official 2", 20, true);
            newStyle("Official 3", 30, true);

            // when
            List<PaintingStyle> result = paintingStyleRepository
                    .findByIsOfficialTrueOrderByUsageCountDesc(PageRequest.of(0, 2));

            // then
            assertThat(result)
                    .extracting(PaintingStyle::getName)
                    .containsExactly("Official 3", "Official 2");
        }

        @Test
        @DisplayName("returns empty list when no official styles exist")
        void findByIsOfficialTrue_emptyWhenNoOfficial() {
            // given
            newStyle("Unofficial", 10, false);

            // when
            List<PaintingStyle> result = paintingStyleRepository
                    .findByIsOfficialTrueOrderByUsageCountDesc(PageRequest.of(0, 10));

            // then
            assertThat(result).isEmpty();
        }
    }

    // ---------------------------------------------------------------------
    // Domain Rules (incrementUsageCount)
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Domain Rules - incrementUsageCount")
    class DomainRules {

        @Test
        @DisplayName("promotes style to official when usage threshold is exceeded")
        void incrementUsageCount_promotesToOfficial() {
            // given
            PaintingStyle style = new PaintingStyle("Oil");
            tem.persistAndFlush(style);

            // when & then
            style.incrementUsageCount(3);
            assertThat(style.isOfficial()).isFalse();
            style.incrementUsageCount(3);
            assertThat(style.isOfficial()).isFalse();
            style.incrementUsageCount(3);
            assertThat(style.isOfficial()).isFalse();
            style.incrementUsageCount(3);
            assertThat(style.isOfficial()).isTrue();

            tem.flush();
            tem.clear();

            PaintingStyle reloaded = paintingStyleRepository
                    .findByNormalizedName("oil").orElseThrow();
            assertThat(reloaded.isOfficial()).isTrue();
            assertThat(reloaded.getUsageCount()).isEqualTo(4L);
        }
    }

    // ---------------------------------------------------------------------
    // Uniqueness Constraints
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Uniqueness Constraints")
    class UniquenessConstraints {

        @Test
        @DisplayName("throws DataIntegrityViolationException on duplicate normalizedName")
        void normalizedName_isUnique() {
            // given
            newStyle("Oil", 0, false);

            PaintingStyle duplicate = new PaintingStyle("OIL");

            // when & then
            assertThatThrownBy(() -> paintingStyleRepository.saveAndFlush(duplicate))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("throws DataIntegrityViolationException on duplicate name")
        void name_isUnique() {
            // given
            newStyle("Oil", 0, false);

            PaintingStyle duplicate = new PaintingStyle("Oil");

            // when & then
            assertThatThrownBy(() -> paintingStyleRepository.saveAndFlush(duplicate))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }

}
