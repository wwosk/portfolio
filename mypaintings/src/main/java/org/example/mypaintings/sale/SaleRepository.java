package org.example.mypaintings.sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;


public interface SaleRepository extends JpaRepository<Sale, Long> {
    boolean existsByPaintingId(Long paintingId);

    @Query("SELECT s FROM Sale s " +
            "JOIN FETCH s.painting p " +
            "LEFT JOIN FETCH p.attachments " +
            "WHERE p.artist.id = :artistId")
    List<Sale> findAllByPaintingArtistIdWithPaintingAndAttachments(@Param("artistId") long artistId);

    @Query("SELECT s FROM Sale s " +
            "JOIN FETCH s.painting p " +
            "WHERE p.id IN :paintingIds")
    List<Sale> findAllByPaintingIdInWithDetails(@Param("paintingIds") Collection<Long> paintingIds);

}
