package org.example.mypaintings.painting.styles;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;



public interface PaintingStyleRepository extends JpaRepository<PaintingStyle, Long> {

    Optional<PaintingStyle> findByNormalizedName(String normalizedName);

    @Query("""
    SELECT s FROM PaintingStyle s
    WHERE LOWER(s.name) LIKE LOWER(CONCAT(:query, '%'))
    ORDER BY s.isOfficial DESC, s.usageCount DESC, s.name ASC
""")
    List<PaintingStyle> autocomplete(@Param("query") String query, Pageable pageable);

    List<PaintingStyle> findByIsOfficialTrueOrderByUsageCountDesc(Pageable pageable);

}