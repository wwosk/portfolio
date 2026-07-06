package org.example.mypaintings.reservation;

import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.example.mypaintings.painting.Painting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


public interface ReservationOfferRepository extends JpaRepository<ReservationOffer, Long> {

    default ReservationOffer getOrThrow(Long id) {
        return findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException(ReservationOffer.class,id));
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ReservationOffer o SET o.status = 'REJECTED' " +
            "WHERE o.painting = :painting AND o.id != :acceptedOfferId")
    void rejectOtherOffers(Painting painting, Long acceptedOfferId);


    boolean existsByPaintingIdAndStatus(long paintingId, ReservationStatus status);

    @Query("SELECT o FROM ReservationOffer o " +
            "JOIN FETCH o.painting p " +
            "JOIN FETCH p.artist " +
            "WHERE o.id = :id")
    Optional<ReservationOffer> findByIdWithDetails(@Param("id") Long id);



    List<ReservationOffer> findAllByPainting(Painting painting);



    Optional<ReservationOffer> findByHotelIdAndPaintingId(Long hotelId, Long paintingId);


}
