package org.example.mypaintings.painting.attachedimages;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.mypaintings.painting.Painting;



@Entity
@Getter
@Setter
public class AttachedImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileKey;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private Long fileSize;

    @ManyToOne
    private Painting painting;

}