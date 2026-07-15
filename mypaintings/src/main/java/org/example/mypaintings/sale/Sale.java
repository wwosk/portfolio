package org.example.mypaintings.sale;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.sale.financial.CostStructure;
import org.springframework.data.annotation.CreatedDate;

import java.time.Instant;


@Entity
@Table(name = "sales")
@Getter
@Setter
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(unique = true)
    private Painting painting;

    private boolean requiresShipping;

    @Embedded
    private BuyerContactInfo buyerContactInfo;

    @Embedded
    private CostStructure costStructure;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant saleDate = Instant.now();





}