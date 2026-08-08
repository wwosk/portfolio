package org.example.mypaintings.painting.styles;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "painting_styles")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PaintingStyle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    @EqualsAndHashCode.Include
    private String normalizedName;

    private boolean isOfficial;

    @Column(nullable = false)
    private long usageCount = 0L;

    public PaintingStyle(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Style name cannot be null or blank");
        }
        this.name = name.trim();
        this.normalizedName = this.name.toLowerCase();
        this.isOfficial = false;
        this.usageCount = 0L;
    }

    public void incrementUsageCount(long officialThreshold) {
        this.usageCount++;
        if (this.usageCount > officialThreshold) {
            this.isOfficial = true;
        }
    }
}