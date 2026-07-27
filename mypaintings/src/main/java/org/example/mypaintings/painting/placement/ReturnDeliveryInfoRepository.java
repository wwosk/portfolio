package org.example.mypaintings.painting.placement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReturnDeliveryInfoRepository extends JpaRepository<ReturnDeliveryInfo, Long> {
    List<ReturnDeliveryInfo> findAllByPlacementIdIn(Collection<Long> placementIds);
}
