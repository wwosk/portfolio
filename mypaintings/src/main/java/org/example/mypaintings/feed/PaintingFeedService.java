package org.example.mypaintings.feed;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.example.mypaintings.feed.dto.PaintingDetailsResponse;
import org.example.mypaintings.feed.dto.PaintingFeedItemResponse;
import org.example.mypaintings.feed.dto.PaintingFilterCriteria;
import org.example.mypaintings.feed.social.SocialInteractionsQueryService;
import org.example.mypaintings.geolocation.UserProximity;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.PaintingQueryService;
import org.example.mypaintings.painting.attachedimages.FileUrlService;
import org.example.mypaintings.painting.styles.PaintingStyle;
import org.example.mypaintings.reservation.ReservationOffer;
import org.example.mypaintings.reservation.ReservationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaintingFeedService {

    private final FileUrlService fileUrlService;
    private final SocialInteractionsQueryService socialInteractionsQueryService;
    private final PaintingQueryService paintingQueryService;


    @Transactional(readOnly = true)
    public Slice<PaintingFeedItemResponse> providePaintingFeed(
            long viewerId,
            PaintingFilterCriteria criteria,
            Pageable pageable
    ) {
        Specification<Painting> spec = buildFeedSpecification(viewerId, criteria);
        return fetchAndMapPaintings(spec, viewerId, pageable);
    }

    @Transactional(readOnly = true)
    public Slice<PaintingFeedItemResponse> provideArtistProfilePaintings(
            long viewerId,
            long artistId,
            Pageable pageable
    ) {
        Specification<Painting> spec = (root, _, cb) -> cb.and(
                cb.equal(root.get("artist").get("id"), artistId),
                cb.isFalse(root.get("archived"))
        );

        return fetchAndMapPaintings(spec, viewerId, pageable);
    }


    @Transactional(readOnly = true)
    public PaintingDetailsResponse getPaintingDetails(long paintingId, long hotelId) {

        Painting painting = paintingQueryService.getActiveOrThrow(paintingId);

        boolean liked = socialInteractionsQueryService.isPaintingLikedByHotel(painting, hotelId);

        List<String> styleNames = painting.getStyles().stream()
                .map(PaintingStyle::getName)
                .toList();

        List<String> imageUrls = painting.getImagesAsFileKeys().stream()
                .map(fileUrlService::buildUrl)
                .toList();

        return new PaintingDetailsResponse(
                painting.getId(),
                painting.getTitle(),
                painting.getDescription(),
                painting.getPrice(),
                imageUrls,
                liked,
                painting.getArtist().getId(),
                painting.getArtist().getNickname(),
                painting.getDimensions(),
                painting.getSizeCategory(),
                painting.getOrientation(),
                painting.getMedium(),
                styleNames
        );
    }




    private Specification<Painting> buildFeedSpecification(long viewerId, PaintingFilterCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!isCountQuery(query)) {
                query.distinct(true);
            }

            addRadiusPredicate(predicates, root, query, cb, viewerId, criteria);
            addStylesPredicate(predicates, root, query, cb, criteria);
            addInPredicate(predicates, root, "sizeCategory", criteria.sizeCategory());
            addInPredicate(predicates, root, "medium", criteria.medium());
            addInPredicate(predicates, root, "orientation", criteria.orientation());

            predicates.add(cb.isFalse(root.get("archived")));
            addAcceptedReservationsPredicate(predicates, root, query, cb);

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addRadiusPredicate(
            List<Predicate> predicates,
            Root<Painting> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            long viewerId,
            PaintingFilterCriteria criteria
    ) {
        if (criteria.radiusKm() == null || criteria.radiusKm() <= 0) {
            return;
        }

        Subquery<Long> nearbyArtists = query.subquery(Long.class);
        Root<UserProximity> proximityRoot = nearbyArtists.from(UserProximity.class);

        nearbyArtists
                .select(proximityRoot.get("nearbyUser").get("id"))
                .where(
                        cb.equal(proximityRoot.get("user").get("id"), viewerId),
                        cb.lessThanOrEqualTo(proximityRoot.get("distanceKm"), criteria.radiusKm())
                );

        predicates.add(root.get("artist").get("id").in(nearbyArtists));
    }

    private void addStylesPredicate(
            List<Predicate> predicates,
            Root<Painting> root,
            CriteriaQuery<?> query, // dodaj query, żeby móc zrobić subquery
            CriteriaBuilder cb,
            PaintingFilterCriteria criteria
    ) {
        if (criteria.style() == null || criteria.style().isEmpty()) {
            return;
        }

        Subquery<Long> styleSubquery = query.subquery(Long.class);
        Root<Painting> paintingRoot = styleSubquery.from(Painting.class);
        // zakładając, że styles to kolekcja encji PaintingStyle, a styl ma pole id lub porównujemy całe obiekty:
        var stylesJoin = paintingRoot.join("styles");

        styleSubquery.select(paintingRoot.get("id"))
                .where(stylesJoin.in(criteria.style()));

        predicates.add(root.get("id").in(styleSubquery));
    }

    private <T> void addInPredicate(
            List<Predicate> predicates,
            Root<Painting> root,
            String fieldName,
            Collection<T> values
    ) {
        if (values == null || values.isEmpty()) {
            return;
        }
        predicates.add(root.get(fieldName).in(values));
    }

    private void addAcceptedReservationsPredicate(
            List<Predicate> predicates,
            Root<Painting> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb
    ) {
        Subquery<Long> acceptedReservations = query.subquery(Long.class);
        Root<ReservationOffer> reservationRoot = acceptedReservations.from(ReservationOffer.class);

        acceptedReservations
                .select(reservationRoot.get("painting").get("id"))
                .where(cb.equal(reservationRoot.get("status"), ReservationStatus.ACCEPTED));

        predicates.add(cb.not(root.get("id").in(acceptedReservations)));
    }



    private boolean isCountQuery(CriteriaQuery<?> query) {
        Class<?> resultType = query.getResultType();
        return resultType == Long.class || resultType == long.class;
    }


    private Slice<PaintingFeedItemResponse> fetchAndMapPaintings(
            Specification<Painting> spec,
            long viewerId,
            Pageable pageable
    ) {
        Slice<Painting> paintingsSlice = paintingQueryService.getPaintingSlicesBySpecification(spec, pageable);

        if (paintingsSlice.isEmpty()) {
            return new SliceImpl<>(List.of(), pageable, paintingsSlice.hasNext());
        }

        List<Long> paintingIds = paintingsSlice.getContent().stream()
                .map(Painting::getId)
                .toList();

        List<Painting> orderedPaintings = loadEnrichedPaintingsInOrder(paintingIds);
        Set<Long> likedPaintingIds = socialInteractionsQueryService.findLikedPaintingIds(viewerId, paintingIds);

        List<PaintingFeedItemResponse> content = orderedPaintings.stream()
                .map(painting -> mapToFeedItem(painting, likedPaintingIds))
                .toList();

        return new SliceImpl<>(content, pageable, paintingsSlice.hasNext());
    }

    private List<Painting> loadEnrichedPaintingsInOrder(List<Long> orderedIds) {
        Map<Long, Painting> byId = paintingQueryService.getPaintingsWithDetailsByIds(orderedIds).stream()
                .collect(Collectors.toMap(Painting::getId, Function.identity()));

        return orderedIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private PaintingFeedItemResponse mapToFeedItem(Painting painting, Set<Long> likedPaintingIds) {
        List<String> imageUrls = painting.getImagesAsFileKeys().stream()
                .map(fileUrlService::buildUrl)
                .toList();

        return new PaintingFeedItemResponse(
                painting.getId(),
                painting.getTitle(),
                imageUrls,
                likedPaintingIds.contains(painting.getId()),
                painting.getArtist().getNickname(),
                painting.getArtist().getId()
        );
    }


}