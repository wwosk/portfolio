package org.example.mypaintings.sale;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.PaintingQueryService;
import org.example.mypaintings.painting.placement.PaintingPlacement;
import org.example.mypaintings.painting.placement.PaintingPlacementQueryService;
import org.example.mypaintings.painting.attachedimages.FileUrlService;
import org.example.mypaintings.painting.placement.PlacementStatus;
import org.example.mypaintings.sale.dto.CreateSaleRequest;
import org.example.mypaintings.sale.dto.SaleOfferResponse;
import org.example.mypaintings.sale.event.PaintingSoldEvent;
import org.example.mypaintings.sale.financial.CostStructure;
import org.example.mypaintings.sale.financial.Money;
import org.example.mypaintings.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SaleService {
    private final SaleRepository saleRepository;
    private final FileUrlService fileUrlService;
    private final PaintingQueryService paintingQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final PaintingPlacementQueryService placementQueryService;

    private final StringRedisTemplate redisTemplate;

    @Value("${app.sale.max-active-locks-per-ip:2}")
    private int maxActiveLocksPerIp;

    @Value("${app.sale.lock-expire-seconds:300}")
    private int lockExpireTimeInSeconds;

    @Value("${app.sale.vat-rate}")
    private BigDecimal vatRate;

    @Value("${app.sale.commission-rate}")
    private BigDecimal commissionRate;

    @Value("${app.sale.shipping-cost}")
    private BigDecimal shippingCost;

    private static final String LOCK_PREFIX = "lock:painting:";
    private static final String IP_LOCK_COUNT_PREFIX = "locks:ip:";
    private static final String IDEMPOTENCY_PREFIX = "idempotency:sale:";

    public void createSaleEntry(CreateSaleRequest request, String idempotencyKey) {
        log.info("Starting sale entry creation for painting ID: {} with idempotency key: {}", request.paintingId(), idempotencyKey);

        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;

        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", Duration.ofHours(24));

        if (Boolean.FALSE.equals(isNew)) {
            String status = redisTemplate.opsForValue().get(redisKey);
            if ("COMPLETED".equals(status)) {
                log.info("Duplicate idempotent request detected for key: {}. Sale was already successfully processed.", idempotencyKey);
                return;
            } else {
                log.warn("Concurrent sale request detected for idempotency key: {} which is currently processing.", idempotencyKey);
                throw new IllegalStateException("Sale request with this idempotency key is currently being processed.");
            }
        }

        try {
            if (saleRepository.existsByPaintingId(request.paintingId())) {
                log.warn("Attempt to re-sell painting ID: {}, which has already been sold", request.paintingId());
                throw new IllegalStateException(String.format("Painting with id '%s' is already sold", request.paintingId()));
            }

            Painting painting = paintingQueryService.getActiveOrThrow(request.paintingId());
            PaintingPlacement placement = placementQueryService.getActivePlacement(request.paintingId());

            if (placement.getPlacementStatus() != PlacementStatus.EXHIBITED) {
                log.warn("Attempt to sell painting ID: {}, which is not exhibited (current status: {})", request.paintingId(), placement.getPlacementStatus());
                throw new IllegalStateException("Painting is not currently exhibited!");
            }

            CostStructure costStructure = calculateCostStructure(painting, request.deliveryNeeded());

            Sale sale = new Sale();
            sale.setPainting(painting);
            sale.setCostStructure(costStructure);
            sale.setRequiresShipping(request.deliveryNeeded());
            sale.setBuyerContactInfo(
                    new BuyerContactInfo(request.email(), request.phoneNumber(), request.fullAddress())
            );

            String lockKey = LOCK_PREFIX + request.paintingId();
            String lockedByIp = redisTemplate.opsForValue().get(lockKey);

            if (lockedByIp != null) {
                String clientIp = SecurityUtils.getClientIP();
                if (!lockedByIp.equals(clientIp)) {
                    log.warn("Attempt to sell painting ID: {}, which is currently locked by another IP", request.paintingId());
                    throw new IllegalStateException("Painting is currently locked by another user/session.");
                }
            }

            saleRepository.save(sale);

            eventPublisher.publishEvent(new PaintingSoldEvent(request.paintingId(), request.deliveryNeeded()));

            redisTemplate.opsForValue().set(redisKey, "COMPLETED", Duration.ofHours(24));

            log.info("Successfully created and saved sale entry for painting ID: {} for total amount: {}", request.paintingId(), costStructure.getTotalPrice().getAmount());

        } catch (Exception e) {
            redisTemplate.delete(redisKey);
            throw e;
        }
    }

    public CostStructure calculateCostStructure(Painting painting, boolean deliveryNeeded) {
        Money paintingPrice = painting.getPrice();
        String currency = paintingPrice.getCurrency();

        BigDecimal baseAmount = paintingPrice.getAmount();
        BigDecimal currentShippingFeeAmount = deliveryNeeded ? shippingCost : BigDecimal.ZERO;

        BigDecimal commissionNetto = baseAmount.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxValue = commissionNetto.multiply(vatRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = baseAmount.add(commissionNetto).add(taxValue).add(currentShippingFeeAmount).setScale(2, RoundingMode.HALF_UP);

        CostStructure cost = new CostStructure();
        cost.setBasePrice(new Money(baseAmount, currency));
        cost.setShippingFee(new Money(currentShippingFeeAmount, currency));
        cost.setPlatformFee(new Money(commissionNetto, currency));
        cost.setPlatformTax(new Money(taxValue, currency));
        cost.setTotalPrice(new Money(totalAmount, currency));

        cost.setAppliedCommissionRate(this.commissionRate);
        cost.setAppliedVatRate(this.vatRate);
        cost.setAppliedShippingCost(Money.pln(shippingCost));

        return cost;
    }

    @Transactional(readOnly = true)
    public SaleOfferResponse getSaleOffer(long paintingId) {
        log.debug("Fetching sale offer for painting ID: {}", paintingId);

        Painting painting = paintingQueryService.getActiveWithDetailsOrThrow(paintingId);

        if (painting.isArchived()) {
            log.warn("Attempt to fetch sale offer for an archived painting ID: {}", paintingId);
            throw new IllegalStateException("Painting is archived and is not for sale!");
        }

        PaintingPlacement placement = placementQueryService.getActivePlacement(paintingId);

        if (placement.getPlacementStatus() != PlacementStatus.EXHIBITED) {
            log.warn("Attempt to fetch offer for unexhibited painting ID: {} (status: {})", paintingId, placement.getPlacementStatus());
            throw new IllegalStateException("Painting is not currently exhibited for sale!");
        }

        CostStructure costWithDelivery = calculateCostStructure(painting, true);
        CostStructure costWithSelfReception = calculateCostStructure(painting, false);

        String lockKey = LOCK_PREFIX + paintingId;
        String lockedByIp = redisTemplate.opsForValue().get(lockKey);
        boolean isLocked = (lockedByIp != null);

        Instant lockExpiresAt = null;
        if (isLocked) {
            Long expireSeconds = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
            if (expireSeconds != null && expireSeconds > 0) {
                lockExpiresAt = Instant.now().plusSeconds(expireSeconds);
            }
        }

        return SaleOfferResponse.builder()
                .paintingId(painting.getId())
                .title(painting.getTitle())
                .artistName(painting.getArtist().getNickname())
                .imageUrls(painting.getImagesAsFileKeys().stream()
                        .map(fileUrlService::buildUrl)
                        .toList())
                .dimensions(painting.getDimensions())
                .costWithDelivery(costWithDelivery)
                .costWithSelfReception(costWithSelfReception)
                .isLocked(isLocked)
                .lockExpiresAt(lockExpiresAt)
                .build();
    }



    public boolean acquireLockForPainting(long paintingId) {
        String clientIp = SecurityUtils.getClientIP();
        String lockKey = LOCK_PREFIX + paintingId;

        log.debug("Attempting to acquire sale lock for painting ID: {} by IP address: {}", paintingId, clientIp);

        String existingIp = redisTemplate.opsForValue().get(lockKey);

        if (existingIp != null) {
            if (existingIp.equals(clientIp)) {
                redisTemplate.expire(lockKey, Duration.ofSeconds(lockExpireTimeInSeconds));
                log.debug("Refreshed existing lock for painting ID: {} for the same IP: {}", paintingId, clientIp);
                return true;
            }
            log.warn("Rejected lock acquisition for painting ID: {} – painting is locked by another IP address", paintingId);
            return false;
        }

        String ipCountKey = IP_LOCK_COUNT_PREFIX + clientIp;
        String currentCountStr = redisTemplate.opsForValue().get(ipCountKey);
        int currentCount = currentCountStr != null ? Integer.parseInt(currentCountStr) : 0;

        if (currentCount >= maxActiveLocksPerIp) {
            log.warn("Active locks limit ({}) reached for IP address: {}", maxActiveLocksPerIp, clientIp);
            throw new IllegalStateException("Limit of locks reached");
        }

        Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, clientIp, Duration.ofSeconds(lockExpireTimeInSeconds));

        if (Boolean.TRUE.equals(success)) {
            redisTemplate.opsForValue().increment(ipCountKey);
            redisTemplate.expire(ipCountKey, Duration.ofSeconds(lockExpireTimeInSeconds));

            log.info("Successfully created a new sale lock for painting ID: {} for IP address: {}", paintingId, clientIp);
            return true;
        } else {
            log.warn("Concurrent lock acquisition conflict for painting ID: {} by IP: {}. Lock already exists.", paintingId, clientIp);
            return false;
        }
    }
}