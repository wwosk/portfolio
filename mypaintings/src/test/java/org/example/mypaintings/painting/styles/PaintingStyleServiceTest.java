package org.example.mypaintings.painting.styles;

import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.PaintingQueryService;
import org.example.mypaintings.painting.event.PaintingCreatedEvent;
import org.example.mypaintings.painting.styles.dto.StyleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaintingStyleService Test Suite")
class PaintingStyleServiceTest {

    @Mock
    private PaintingStyleRepository styleRepository;
    @Mock
    private PaintingQueryService paintingQueryService;

    @InjectMocks
    private PaintingStyleService service;

    private static final long PAINTING_ID = 7L;
    private static final long OFFICIAL_THRESHOLD = 5L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "officialThreshold", OFFICIAL_THRESHOLD);
    }

    private PaintingStyle newStyle(String name) {
        return new PaintingStyle(name);
    }

    private PaintingStyle mockStyle(String name, long usageCount, boolean official) {
        PaintingStyle s = mock(PaintingStyle.class);
        when(s.getName()).thenReturn(name);
        when(s.getUsageCount()).thenReturn(usageCount);
        when(s.isOfficial()).thenReturn(official);
        return s;
    }

    // ------------------------------------------------------------------
    // getOrCreateStyle
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getOrCreateStyle")
    class GetOrCreateStyle {

        @Test
        @DisplayName("returns existing style without saving when found by normalized name")
        void shouldReturnExistingWhenFound() {
            // given
            PaintingStyle existing = newStyle("Impressionism");
            when(styleRepository.findByNormalizedName("impressionism")).thenReturn(Optional.of(existing));

            // when
            StyleResponse resp = service.getOrCreateStyle("  Impressionism  ");

            // then
            assertThat(resp.name()).isEqualTo("Impressionism");
            assertThat(resp.isOfficial()).isFalse();
            assertThat(resp.usageCount()).isZero();
            verify(styleRepository, never()).save(any());
        }

        @Test
        @DisplayName("creates and saves new style when not found")
        void shouldCreateNewWhenNotFound() {
            // given
            when(styleRepository.findByNormalizedName("baroque")).thenReturn(Optional.empty());
            when(styleRepository.save(any(PaintingStyle.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            StyleResponse resp = service.getOrCreateStyle("  Baroque  ");

            // then
            assertThat(resp.name()).isEqualTo("Baroque");
            assertThat(resp.isOfficial()).isFalse();
            assertThat(resp.usageCount()).isZero();

            ArgumentCaptor<PaintingStyle> captor = ArgumentCaptor.forClass(PaintingStyle.class);
            verify(styleRepository).save(captor.capture());
            PaintingStyle saved = captor.getValue();
            assertThat(saved.getName()).isEqualTo("Baroque");
            assertThat(saved.getNormalizedName()).isEqualTo("baroque");
            assertThat(saved.getUsageCount()).isZero();
            assertThat(saved.isOfficial()).isFalse();
        }

        @Test
        @DisplayName("recovers from data integrity violation by re-reading existing style")
        void shouldRecoverFromDataIntegrityViolation() {
            // given
            PaintingStyle winner = newStyle("Baroque");
            when(styleRepository.findByNormalizedName("baroque"))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(winner));
            when(styleRepository.save(any(PaintingStyle.class)))
                    .thenThrow(new DataIntegrityViolationException("dup"));

            // when
            StyleResponse resp = service.getOrCreateStyle("Baroque");

            // then
            assertThat(resp.name()).isEqualTo("Baroque");
        }

        @Test
        @DisplayName("rethrows DataIntegrityViolationException when conflict occurs and style is still not found")
        void shouldRethrowWhenConflictAndStillNotFound() {
            // given
            DataIntegrityViolationException original = new DataIntegrityViolationException("dup");
            when(styleRepository.findByNormalizedName("baroque")).thenReturn(Optional.empty());
            when(styleRepository.save(any(PaintingStyle.class))).thenThrow(original);

            // when & then
            assertThatThrownBy(() -> service.getOrCreateStyle("Baroque"))
                    .isSameAs(original);
        }
    }

    // ------------------------------------------------------------------
    // autocomplete
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("autocomplete")
    class Autocomplete {

        @Test
        @DisplayName("maps entities to DTOs and passes page request with limit")
        void shouldMapAndPassLimit() {
            // given
            List<PaintingStyle> found = List.of(
                    newStyle("Baroque"),
                    newStyle("Baroquely")
            );
            when(styleRepository.autocomplete(eq("bar"), any(PageRequest.class))).thenReturn(found);

            // when
            List<StyleResponse> result = service.autocomplete("bar", 10);

            // then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(StyleResponse::name)
                    .containsExactly("Baroque", "Baroquely");

            ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
            verify(styleRepository).autocomplete(eq("bar"), pageCaptor.capture());
            assertThat(pageCaptor.getValue().getPageNumber()).isZero();
            assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("returns empty list when nothing is found")
        void shouldReturnEmpty() {
            // given
            when(styleRepository.autocomplete(eq("xyz"), any(PageRequest.class))).thenReturn(List.of());

            // when
            List<StyleResponse> result = service.autocomplete("xyz", 5);

            // then
            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // getPopularOfficialStyles
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getPopularOfficialStyles")
    class GetPopularOfficialStyles {

        @Test
        @DisplayName("maps official styles and passes limit to page request")
        void shouldReturnMappedList() {
            // given
            List<PaintingStyle> found = List.of(
                    mockStyle("Baroque", 100L, true),
                    mockStyle("Impressionism", 80L, true)
            );
            when(styleRepository.findByIsOfficialTrueOrderByUsageCountDesc(any(PageRequest.class)))
                    .thenReturn(found);

            // when
            List<StyleResponse> result = service.getPopularOfficialStyles(2);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.getFirst().name()).isEqualTo("Baroque");
            assertThat(result.get(0).isOfficial()).isTrue();
            assertThat(result.get(0).usageCount()).isEqualTo(100L);
            assertThat(result.get(1).name()).isEqualTo("Impressionism");

            ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
            verify(styleRepository).findByIsOfficialTrueOrderByUsageCountDesc(pageCaptor.capture());
            assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(2);
        }
    }

    // ------------------------------------------------------------------
    // resolveStyles
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("resolveStyles")
    class ResolveStyles {

        @Test
        @DisplayName("returns empty list when style IDs collection is null")
        void shouldReturnEmptyWhenNull() {
            // when & then
            assertThat(service.resolveStyles(null)).isEmpty();
            verifyNoInteractions(styleRepository);
        }

        @Test
        @DisplayName("returns empty list when style IDs collection is empty")
        void shouldReturnEmptyWhenEmpty() {
            // when & then
            assertThat(service.resolveStyles(List.of())).isEmpty();
            verifyNoInteractions(styleRepository);
        }

        @Test
        @DisplayName("returns all styles when all IDs exist")
        void shouldReturnAllWhenAllFound() {
            // given
            PaintingStyle a = newStyle("Baroque");
            PaintingStyle b = newStyle("Gothic");
            when(styleRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(a, b));

            // when
            List<PaintingStyle> result = service.resolveStyles(List.of(1L, 2L));

            // then
            assertThat(result).containsExactly(a, b);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when some style IDs are missing")
        void shouldThrowWhenSomeMissing() {
            // given
            PaintingStyle a = newStyle("Baroque");
            when(styleRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(a));

            // when & then
            assertThatThrownBy(() -> service.resolveStyles(List.of(1L, 2L, 3L)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------
    // handlePaintingCreated
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("handlePaintingCreated")
    class HandlePaintingCreated {

        @Test
        @DisplayName("increments usage count for each style associated with the painting")
        void shouldIncrementEachStyle() {
            // given
            PaintingStyle s1 = spy(newStyle("Baroque"));
            PaintingStyle s2 = spy(newStyle("Gothic"));
            Set<PaintingStyle> styles = new HashSet<>(Set.of(s1, s2));

            Painting painting = mock(Painting.class);
            when(painting.getStyles()).thenReturn(styles);
            when(paintingQueryService.getActiveWithDetailsOrThrow(PAINTING_ID)).thenReturn(painting);

            // when
            service.handlePaintingCreated(new PaintingCreatedEvent(PAINTING_ID));

            // then
            verify(s1).incrementUsageCount(OFFICIAL_THRESHOLD);
            verify(s2).incrementUsageCount(OFFICIAL_THRESHOLD);
            verify(paintingQueryService).getActiveWithDetailsOrThrow(PAINTING_ID);
        }

        @Test
        @DisplayName("does nothing when painting has no styles")
        void shouldDoNothingWhenNoStyles() {
            // given
            Painting painting = mock(Painting.class);
            when(painting.getStyles()).thenReturn(Set.of());
            when(paintingQueryService.getActiveWithDetailsOrThrow(PAINTING_ID)).thenReturn(painting);

            // when
            service.handlePaintingCreated(new PaintingCreatedEvent(PAINTING_ID));

            // then
            verifyNoInteractions(styleRepository);
        }

        @Test
        @DisplayName("propagates exception from painting query service")
        void shouldPropagateException() {
            // given
            when(paintingQueryService.getActiveWithDetailsOrThrow(PAINTING_ID))
                    .thenThrow(new IllegalStateException("not found"));

            // when & then
            assertThatThrownBy(() -> service.handlePaintingCreated(new PaintingCreatedEvent(PAINTING_ID)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("not found");
        }
    }
}