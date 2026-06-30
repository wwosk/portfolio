package org.example.mypaintings.painting;

import jakarta.persistence.*;
import lombok.*;
import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.painting.features.Orientation;
import org.example.mypaintings.painting.features.PaintingMedium;
import org.example.mypaintings.painting.features.SizeCategory;
import org.example.mypaintings.sale.financial.Money;
import org.example.mypaintings.user.User;
import org.example.mypaintings.painting.attachedimages.AttachedImage;
import org.example.mypaintings.painting.styles.PaintingStyle;

import java.util.*;
import java.util.stream.Collectors;

@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Painting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(nullable = false)
    private User artist;

    @Column(nullable = false)
    private String title;

    @OneToMany(mappedBy = "painting", fetch = FetchType.LAZY)
    private Set<AttachedImage> attachments = new HashSet<>();


    @Embedded
    @Column(nullable = false)
    private Dimensions dimensions;

    private String description;

    private boolean archived;

    @Column(nullable = false)
    private Money price;


    @ManyToMany
    @JoinTable(
            name = "painting_painting_styles",
            joinColumns = @JoinColumn(name = "painting_id"),
            inverseJoinColumns = @JoinColumn(name = "style_id")
    )
    private Set<PaintingStyle> styles = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SizeCategory sizeCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Orientation orientation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaintingMedium medium;

    public boolean isOwnedBy(long userId) {
        return artist != null && Objects.equals(artist.getId(), userId);
    }


    public void addStyle(PaintingStyle style) {
        if (this.styles.size() >= 3) {
            throw new IllegalStateException("Maximum amount of styles achieved (3).");
        }
        this.styles.add(style);
    }

    public void addAttachment(AttachedImage attachment) {
        if (this.attachments.size() >= 3) {
            throw new IllegalStateException("Maximum amount of attached images achieved (3).");
        }
        this.attachments.add(attachment);
        attachment.setPainting(this);
    }


    public List<String> getImagesAsFileKeys() {
        if (this.attachments == null) {
            return Collections.emptyList();
        }
        return this.attachments.stream()
                .map(AttachedImage::getFileKey)
                .collect(Collectors.toList());
    }

    public String getFirstImageFileKey() {
        return getImagesAsFileKeys().stream().findFirst().orElse(null);
    }


    public void setDimensions(Dimensions dimensions) {
        this.dimensions = dimensions;
        this.updateMetadata();
    }


    @PrePersist
    @PreUpdate
    private void updateMetadata() {
        if (this.dimensions != null) {
            this.sizeCategory = SizeCategory.calculateSizeCategory(this.dimensions);
            this.orientation = Orientation.calculateOrientation(this.dimensions);
        } else {
            this.sizeCategory = null;
            this.orientation = null;
        }
    }

    public void archive()
    {
        this.archived = true;
    }

    @Builder
    public Painting(User artist,
                    String title,
                    Set<AttachedImage> attachments,
                    Dimensions dimensions,
                    String description,
                    Money price,
                    Set<PaintingStyle> styles,
                    PaintingMedium medium) {
        this.artist = artist;
        this.title = title;
        this.attachments = (attachments != null) ? attachments : new HashSet<>();
        this.description = description;
        this.price = price;
        this.styles = (styles != null) ? styles : new HashSet<>();
        this.medium = medium;

        setDimensions(dimensions);
    }




}
