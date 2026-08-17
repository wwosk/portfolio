package org.example.mypaintings.sale;

import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.PaintingQueryService;
import org.example.mypaintings.painting.attachedimages.FileUrlService;
import org.example.mypaintings.painting.placement.PaintingPlacement;
import org.example.mypaintings.painting.placement.PaintingPlacementQueryService;
import org.example.mypaintings.painting.placement.PlacementStatus;
import org.example.mypaintings.sale.dto.CreateSaleRequest;
import org.example.mypaintings.sale.dto.SaleOfferResponse;
import org.example.mypaintings.sale.event.PaintingSoldEvent;
import org.example.mypaintings.sale.financial.CostStructure;
import org.example.mypaintings.sale.financial.Money;
import org.example.mypaintings.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SaleService Test Suite")
class SaleServiceTest {

    @Mock SaleRepository saleRepository;
    @Mock FileUrlService fileUrlService;
    @Mock PaintingQueryService paintingQueryService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock PaintingPlacementQueryService placementQueryService;

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    @InjectMocks SaleService saleService;

    private static final long PAINTING_ID = 42L;
    private static final String IDEMPOTENCY_KEY = "test-idempotency-uuid-123";
    private static final BigDecimal COMMISSION = new BigDecimal("0.10");
    private static final BigDecimal VAT = new BigDecimal("0.23");
    private static final BigDecimal SHIPPING = new BigDecimal("25.00");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(saleService, "maxActiveLocksPerIp", 2);
        ReflectionTestUtils.setField(saleService, "lockExpireTimeInSeconds", 300);
        ReflectionTestUtils.setField(saleService, "commissionRate", COMMISSION);
        ReflectionTestUtils.setField(saleService, "vatRate", VAT);
        ReflectionTestUtils.setField(saleService, "shippingCost", SHIPPING);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private Painting mockPainting(BigDecimal amount, String currency) {
        Painting p = mock(Painting.class);
        when(p.getId()).thenReturn(PAINTING_ID);
        when(p.getPrice()).thenReturn(new Money(amount, currency));
        return p;
    }

    private PaintingPlacement mockPlacement(PlacementStatus status) {
        PaintingPlacement pl = mock(PaintingPlacement.class);
        when(pl.getPlacementStatus()).thenReturn(status);
        return pl;
    }

    // ------------------------------------------------------------------
    // calculateCostStructure
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("calculateCostStructure")
    class CalculateCostStructure {

        @Test
        @DisplayName("computes all components including shipping when delivery is needed")
        void calculateCostStructure_shouldComputeAllComponents_whenDeliveryNeeded() {
            // given
            Painting painting = mockPainting(new BigDecimal("100.00"), "PLN");

            // when
            CostStructure cost = saleService.calculateCostStructure(painting, true);

            // then
            assertThat(cost.getBasePrice().getAmount()).isEqualByComparingTo("100.00");
            assertThat(cost.getPlatformFee().getAmount()).isEqualByComparingTo("10.00");
            assertThat(cost.getPlatformTax().getAmount()).isEqualByComparingTo("2.30");
            assertThat(cost.getShippingFee().getAmount()).isEqualByComparingTo("25.00");
            assertThat(cost.getTotalPrice().getAmount()).isEqualByComparingTo("137.30");
            assertThat(cost.getTotalPrice().getCurrency()).isEqualTo("PLN");
        }

        @Test
        @DisplayName("sets shipping to zero when self-reception is selected")
        void calculateCostStructure_shouldSetZeroShipping_whenSelfReception() {
            // given
            Painting painting = mockPainting(new BigDecimal("100.00"), "PLN");

            // when
            CostStructure cost = saleService.calculateCostStructure(painting, false);

            // then
            assertThat(cost.getShippingFee().getAmount()).isEqualByComparingTo("0");
            assertThat(cost.getTotalPrice().getAmount()).isEqualByComparingTo("112.30");
        }

        @Test
        @DisplayName("rounds values using HALF_UP to 2 decimal places")
        void calculateCostStructure_shouldRoundHalfUp() {
            // given
            Painting painting = mockPainting(new BigDecimal("33.33"), "EUR");

            // when
            CostStructure cost = saleService.calculateCostStructure(painting, false);

            // then
            assertThat(cost.getPlatformFee().getAmount()).isEqualByComparingTo("3.33");
            assertThat(cost.getPlatformTax().getAmount()).isEqualByComparingTo("0.77");
        }
    }

    // ------------------------------------------------------------------
    // createSaleEntry & Idempotency
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("createSaleEntry and Idempotency")
    class CreateSaleEntry {

        @Test
        @DisplayName("throws exception when painting is already sold")
        void createSaleEntry_shouldThrow_whenPaintingAlreadySold() {
            // given
            when(valueOperations.setIfAbsent(eq("idempotency:sale:" + IDEMPOTENCY_KEY), eq("PROCESSING"), any(Duration.class))).thenReturn(true);
            when(saleRepository.existsByPaintingId(PAINTING_ID)).thenReturn(true);

            CreateSaleRequest req = new CreateSaleRequest(PAINTING_ID, true, "a@b.c", "123", "addr");

            // when & then
            assertThatThrownBy(() -> saleService.createSaleEntry(req, IDEMPOTENCY_KEY))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already sold");

            verify(saleRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
            verify(redisTemplate).delete("idempotency:sale:" + IDEMPOTENCY_KEY);
        }

        @Test
        @DisplayName("throws exception when placement is not exhibited")
        void createSaleEntry_shouldThrow_whenPlacementNotExhibited() {
            // given
            Painting painting = mockPainting(new BigDecimal("100"), "PLN");
            PaintingPlacement placement = mockPlacement(PlacementStatus.IN_DELIVERY_TO_HOTEL);

            when(valueOperations.setIfAbsent(eq("idempotency:sale:" + IDEMPOTENCY_KEY), eq("PROCESSING"), any(Duration.class))).thenReturn(true);
            when(saleRepository.existsByPaintingId(PAINTING_ID)).thenReturn(false);
            when(paintingQueryService.getActiveOrThrow(PAINTING_ID)).thenReturn(painting);
            when(placementQueryService.getActivePlacement(PAINTING_ID)).thenReturn(placement);

            CreateSaleRequest req = new CreateSaleRequest(PAINTING_ID, true, "a@b.c", "123", "addr");

            // when & then
            assertThatThrownBy(() -> saleService.createSaleEntry(req, IDEMPOTENCY_KEY))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not currently exhibited");

            verify(saleRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
            verify(redisTemplate).delete("idempotency:sale:" + IDEMPOTENCY_KEY);
        }

        @Test
        @DisplayName("saves sale entry, publishes event, and sets status to COMPLETED when valid")
        void createSaleEntry_shouldSaveAndPublishEvent_whenValid() {
            // given
            Painting painting = mockPainting(new BigDecimal("100"), "PLN");
            PaintingPlacement placement = mockPlacement(PlacementStatus.EXHIBITED);

            when(valueOperations.setIfAbsent(eq("idempotency:sale:" + IDEMPOTENCY_KEY), eq("PROCESSING"), any(Duration.class))).thenReturn(true);
            when(saleRepository.existsByPaintingId(PAINTING_ID)).thenReturn(false);
            when(paintingQueryService.getActiveOrThrow(PAINTING_ID)).thenReturn(painting);
            when(placementQueryService.getActivePlacement(PAINTING_ID)).thenReturn(placement);
            when(valueOperations.get("lock:painting:" + PAINTING_ID)).thenReturn(null);

            CreateSaleRequest req = new CreateSaleRequest(PAINTING_ID, false, "a@b.c", "123", "addr");

            // when
            saleService.createSaleEntry(req, IDEMPOTENCY_KEY);

            // then
            ArgumentCaptor<Sale> saleCaptor = ArgumentCaptor.forClass(Sale.class);
            verify(saleRepository).save(saleCaptor.capture());
            Sale saved = saleCaptor.getValue();
            assertThat(saved.getPainting()).isEqualTo(painting);
            assertThat(saved.isRequiresShipping()).isFalse();
            assertThat(saved.getCostStructure().getTotalPrice().getAmount()).isEqualByComparingTo("112.30");

            ArgumentCaptor<PaintingSoldEvent> eventCaptor = ArgumentCaptor.forClass(PaintingSoldEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().paintingId()).isEqualTo(PAINTING_ID);
            assertThat(eventCaptor.getValue().deliveryNeeded()).isFalse();

            verify(valueOperations).set(eq("idempotency:sale:" + IDEMPOTENCY_KEY), eq("COMPLETED"), any(Duration.class));
        }

        @Test
        @DisplayName("returns early successfully (idempotent) when key status is COMPLETED")
        void createSaleEntry_shouldReturnEarly_whenAlreadyCompleted() {
            // given
            when(valueOperations.setIfAbsent(eq("idempotency:sale:" + IDEMPOTENCY_KEY), eq("PROCESSING"), any(Duration.class))).thenReturn(false);
            when(valueOperations.get("idempotency:sale:" + IDEMPOTENCY_KEY)).thenReturn("COMPLETED");

            CreateSaleRequest req = new CreateSaleRequest(PAINTING_ID, false, "a@b.c", "123", "addr");

            // when
            saleService.createSaleEntry(req, IDEMPOTENCY_KEY);

            // then
            verify(saleRepository, never()).existsByPaintingId(anyLong());
            verify(saleRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("throws exception when idempotency key status is PROCESSING (concurrent request)")
        void createSaleEntry_shouldThrow_whenCurrentlyProcessing() {
            // given
            when(valueOperations.setIfAbsent(eq("idempotency:sale:" + IDEMPOTENCY_KEY), eq("PROCESSING"), any(Duration.class))).thenReturn(false);
            when(valueOperations.get("idempotency:sale:" + IDEMPOTENCY_KEY)).thenReturn("PROCESSING");

            CreateSaleRequest req = new CreateSaleRequest(PAINTING_ID, false, "a@b.c", "123", "addr");

            // when & then
            assertThatThrownBy(() -> saleService.createSaleEntry(req, IDEMPOTENCY_KEY))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("currently being processed");

            verify(saleRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws exception when painting is locked by another IP")
        void createSaleEntry_shouldThrow_whenLockedByOtherIp() {
            // given
            Painting painting = mockPainting(new BigDecimal("100"), "PLN");
            PaintingPlacement placement = mockPlacement(PlacementStatus.EXHIBITED);

            when(valueOperations.setIfAbsent(eq("idempotency:sale:" + IDEMPOTENCY_KEY), eq("PROCESSING"), any(Duration.class))).thenReturn(true);
            when(saleRepository.existsByPaintingId(PAINTING_ID)).thenReturn(false);
            when(paintingQueryService.getActiveOrThrow(PAINTING_ID)).thenReturn(painting);
            when(placementQueryService.getActivePlacement(PAINTING_ID)).thenReturn(placement);
            when(valueOperations.get("lock:painting:" + PAINTING_ID)).thenReturn("10.0.0.1");

            try (MockedStatic<org.example.mypaintings.security.SecurityUtils> mocked =
                         mockStatic(org.example.mypaintings.security.SecurityUtils.class)) {
                mocked.when(org.example.mypaintings.security.SecurityUtils::getClientIP)
                        .thenReturn("192.168.1.50");

                CreateSaleRequest req = new CreateSaleRequest(PAINTING_ID, false, "a@b.c", "123", "addr");

                // when & then
                assertThatThrownBy(() -> saleService.createSaleEntry(req, IDEMPOTENCY_KEY))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("locked by another");
            }

            verify(saleRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
            verify(redisTemplate).delete("idempotency:sale:" + IDEMPOTENCY_KEY);
        }
    }

    // ------------------------------------------------------------------
    // getSaleOffer
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getSaleOffer")
    class GetSaleOffer {

        @Test
        @DisplayName("throws exception when painting is archived")
        void getSaleOffer_shouldThrow_whenArchived() {
            // given
            Painting painting = mockPainting(new BigDecimal("100"), "PLN");
            when(painting.isArchived()).thenReturn(true);
            when(paintingQueryService.getActiveWithDetailsOrThrow(PAINTING_ID)).thenReturn(painting);

            // when & then
            assertThatThrownBy(() -> saleService.getSaleOffer(PAINTING_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("archived");
        }

        @Test
        @DisplayName("throws exception when painting is not exhibited")
        void getSaleOffer_shouldThrow_whenNotExhibited() {
            // given
            Painting painting = mockPainting(new BigDecimal("100"), "PLN");
            when(painting.isArchived()).thenReturn(false);
            when(paintingQueryService.getActiveWithDetailsOrThrow(PAINTING_ID)).thenReturn(painting);

            PaintingPlacement placement = mockPlacement(PlacementStatus.IN_DELIVERY_TO_HOTEL);
            when(placementQueryService.getActivePlacement(PAINTING_ID)).thenReturn(placement);

            // when & then
            assertThatThrownBy(() -> saleService.getSaleOffer(PAINTING_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not currently exhibited");
        }

        @Test
        @DisplayName("builds response with both cost structures when unlocked")
        void getSaleOffer_shouldBuildResponse_withBothCostStructures() {
            // given
            Painting painting = mockPainting(new BigDecimal("100"), "PLN");
            User artist = mock(User.class);
            when(artist.getNickname()).thenReturn("monet");
            when(painting.getTitle()).thenReturn("Lilie");
            when(painting.getArtist()).thenReturn(artist);
            when(painting.isArchived()).thenReturn(false);
            when(painting.getImagesAsFileKeys()).thenReturn(List.of("k1"));
            when(fileUrlService.buildUrl("k1")).thenReturn("http://img/k1");

            PaintingPlacement placement = mockPlacement(PlacementStatus.EXHIBITED);

            when(paintingQueryService.getActiveWithDetailsOrThrow(PAINTING_ID)).thenReturn(painting);
            when(placementQueryService.getActivePlacement(PAINTING_ID)).thenReturn(placement);
            when(valueOperations.get("lock:painting:" + PAINTING_ID)).thenReturn(null);

            // when
            SaleOfferResponse resp = saleService.getSaleOffer(PAINTING_ID);

            // then
            assertThat(resp.title()).isEqualTo("Lilie");
            assertThat(resp.artistName()).isEqualTo("monet");
            assertThat(resp.imageUrls()).containsExactly("http://img/k1");
            assertThat(resp.costWithDelivery().getTotalPrice().getAmount()).isEqualByComparingTo("137.30");
            assertThat(resp.costWithSelfReception().getTotalPrice().getAmount()).isEqualByComparingTo("112.30");
            assertThat(resp.isLocked()).isFalse();
            assertThat(resp.lockExpiresAt()).isNull();
        }

        @Test
        @DisplayName("builds response indicating lock status and expiration when locked")
        void getSaleOffer_shouldBuildResponse_whenLocked() {
            // given
            Painting painting = mockPainting(new BigDecimal("100"), "PLN");
            User artist = mock(User.class);
            when(artist.getNickname()).thenReturn("monet");
            when(painting.getTitle()).thenReturn("Lilie");
            when(painting.getArtist()).thenReturn(artist);
            when(painting.isArchived()).thenReturn(false);
            when(painting.getImagesAsFileKeys()).thenReturn(List.of("k1"));
            when(fileUrlService.buildUrl("k1")).thenReturn("http://img/k1");

            PaintingPlacement placement = mockPlacement(PlacementStatus.EXHIBITED);

            when(paintingQueryService.getActiveWithDetailsOrThrow(PAINTING_ID)).thenReturn(painting);
            when(placementQueryService.getActivePlacement(PAINTING_ID)).thenReturn(placement);
            when(valueOperations.get("lock:painting:" + PAINTING_ID)).thenReturn("10.0.0.1");
            when(redisTemplate.getExpire("lock:painting:" + PAINTING_ID, TimeUnit.SECONDS)).thenReturn(120L);

            // when
            SaleOfferResponse resp = saleService.getSaleOffer(PAINTING_ID);

            // then
            assertThat(resp.isLocked()).isTrue();
            assertThat(resp.lockExpiresAt()).isNotNull();
        }
    }

    // ------------------------------------------------------------------
    // acquireLockForPainting
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("acquireLockForPainting")
    class AcquireLockForPainting {

        @Test
        @DisplayName("creates new lock in Redis when none exists")
        void acquireLock_shouldCreateNewLock_whenNoneExists() {
            // given
            Painting painting = mockPainting(new BigDecimal("100"), "PLN");

            when(valueOperations.get("lock:painting:" + PAINTING_ID)).thenReturn(null);
            when(valueOperations.get("locks:ip:10.0.0.1")).thenReturn("0");
            when(valueOperations.setIfAbsent(eq("lock:painting:" + PAINTING_ID), any(), any(Duration.class))).thenReturn(true);
            when(paintingQueryService.getActiveOrThrow(PAINTING_ID)).thenReturn(painting);

            try (MockedStatic<org.example.mypaintings.security.SecurityUtils> mocked =
                         mockStatic(org.example.mypaintings.security.SecurityUtils.class)) {
                mocked.when(org.example.mypaintings.security.SecurityUtils::getClientIP)
                        .thenReturn("10.0.0.1");

                // when
                boolean acquired = saleService.acquireLockForPainting(PAINTING_ID);

                // then
                assertThat(acquired).isTrue();
                verify(valueOperations).setIfAbsent(eq("lock:painting:" + PAINTING_ID), eq("10.0.0.1"), any(Duration.class));
                verify(valueOperations).increment("locks:ip:10.0.0.1");
            }
        }

        @Test
        @DisplayName("treats null IP lock count in Redis as zero")
        void acquireLock_shouldTreatNullIpCountAsZero() {
            // given
            Painting painting = mockPainting(new BigDecimal("100"), "PLN");

            when(valueOperations.get("lock:painting:" + PAINTING_ID)).thenReturn(null);
            when(valueOperations.get("locks:ip:10.0.0.1")).thenReturn(null);
            when(valueOperations.setIfAbsent(eq("lock:painting:" + PAINTING_ID), any(), any(Duration.class))).thenReturn(true);
            when(paintingQueryService.getActiveOrThrow(PAINTING_ID)).thenReturn(painting);

            try (MockedStatic<org.example.mypaintings.security.SecurityUtils> mocked =
                         mockStatic(org.example.mypaintings.security.SecurityUtils.class)) {
                mocked.when(org.example.mypaintings.security.SecurityUtils::getClientIP)
                        .thenReturn("10.0.0.1");

                // when
                boolean acquired = saleService.acquireLockForPainting(PAINTING_ID);

                // then
                assertThat(acquired).isTrue();
                verify(valueOperations).increment("locks:ip:10.0.0.1");
            }
        }

        @Test
        @DisplayName("rejects when locked by another IP address")
        void acquireLock_shouldReject_whenLockedByOtherIp() {
            // given
            when(valueOperations.get("lock:painting:" + PAINTING_ID)).thenReturn("10.0.0.1");

            try (MockedStatic<org.example.mypaintings.security.SecurityUtils> mocked =
                         mockStatic(org.example.mypaintings.security.SecurityUtils.class)) {
                mocked.when(org.example.mypaintings.security.SecurityUtils::getClientIP)
                        .thenReturn("192.168.1.50");

                // when
                boolean acquired = saleService.acquireLockForPainting(PAINTING_ID);

                // then
                assertThat(acquired).isFalse();
                verify(valueOperations, never()).setIfAbsent(any(), any(), any());
            }
        }

        @Test
        @DisplayName("refreshes lock TTL when requested by the same IP")
        void acquireLock_shouldRefresh_whenSameIp() {
            // given
            when(valueOperations.get("lock:painting:" + PAINTING_ID)).thenReturn("10.0.0.1");

            try (MockedStatic<org.example.mypaintings.security.SecurityUtils> mocked =
                     mockStatic(org.example.mypaintings.security.SecurityUtils.class)) {
                mocked.when(org.example.mypaintings.security.SecurityUtils::getClientIP)
                        .thenReturn("10.0.0.1");

                // when
                boolean acquired = saleService.acquireLockForPainting(PAINTING_ID);

                // then
                assertThat(acquired).isTrue();
                verify(redisTemplate).expire(eq("lock:painting:" + PAINTING_ID), any(Duration.class));
            }
        }

        @Test
        @DisplayName("throws exception when max active locks limit per IP is reached")
        void acquireLock_shouldThrow_whenIpLimitReached() {
            // given
            when(valueOperations.get("lock:painting:" + PAINTING_ID)).thenReturn(null);
            when(valueOperations.get("locks:ip:10.0.0.1")).thenReturn("2");

            try (MockedStatic<org.example.mypaintings.security.SecurityUtils> mocked =
                     mockStatic(org.example.mypaintings.security.SecurityUtils.class)) {
                mocked.when(org.example.mypaintings.security.SecurityUtils::getClientIP)
                        .thenReturn("10.0.0.1");

                // when & then
                assertThatThrownBy(() -> saleService.acquireLockForPainting(PAINTING_ID))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Limit of locks reached");
            }
        }

        @Test
        @DisplayName("returns false on concurrency conflict during setIfAbsent")
        void acquireLock_shouldReturnFalse_onConcurrencyConflict() {
            // given
            Painting painting = mockPainting(new BigDecimal("100"), "PLN");

            when(valueOperations.get("lock:painting:" + PAINTING_ID)).thenReturn(null);
            when(valueOperations.get("locks:ip:10.0.0.1")).thenReturn("0");
            when(valueOperations.setIfAbsent(eq("lock:painting:" + PAINTING_ID), any(), any(Duration.class))).thenReturn(false);
            when(paintingQueryService.getActiveOrThrow(PAINTING_ID)).thenReturn(painting);

            try (MockedStatic<org.example.mypaintings.security.SecurityUtils> mocked =
                     mockStatic(org.example.mypaintings.security.SecurityUtils.class)) {
                mocked.when(org.example.mypaintings.security.SecurityUtils::getClientIP)
                        .thenReturn("10.0.0.1");

                // when
                boolean acquired = saleService.acquireLockForPainting(PAINTING_ID);

                // then
                assertThat(acquired).isFalse();
            }
        }
    }
}
