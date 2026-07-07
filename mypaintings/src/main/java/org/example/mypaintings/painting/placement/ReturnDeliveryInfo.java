package org.example.mypaintings.painting.placement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Entity
@Getter
@Setter
public class ReturnDeliveryInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullAddress;

    @ManyToOne
    @JoinColumn(nullable = false, unique = true)
    private PaintingPlacement placement;
}
