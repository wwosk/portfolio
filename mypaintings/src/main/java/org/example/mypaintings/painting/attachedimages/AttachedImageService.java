package org.example.mypaintings.painting.attachedimages;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.exception.duplicate.ConflictException;
import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class AttachedImageService {

    private final AttachedImageRepository attachedImageRepository;
    private final AttachedImageTransactionService transactionService;
    private final FileStorageService fileStorageService;
    private final ImageContentValidator imageContentValidator;

    public Long store(MultipartFile file) {
        imageContentValidator.validate(file);

        String originalFilename = file.getOriginalFilename();
        String cleanFileName = StringUtils.cleanPath(
                originalFilename != null ? originalFilename : "unknown_file");
        String fileKey = UUID.randomUUID() + "_" + cleanFileName;

        fileStorageService.store(file, fileKey);
        try {
            return transactionService.saveMetadata(
                    fileKey,
                    file.getContentType(),
                    originalFilename,
                    file.getSize()
            );
        } catch (Exception dbEx) {
            log.error("DB save failed for key={}, deleting files", fileKey, dbEx);
            fileStorageService.delete(fileKey);
            throw dbEx;
        }
    }

    public List<AttachedImage> resolveAndValidateAttachments(List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            throw new IllegalArgumentException("Attachments list cannot be null or empty.");
        }

        return imageIds.stream().map(imageId -> {
            if (imageId == null) {
                throw new IllegalArgumentException("Attachment ID cannot be null.");
            }

            AttachedImage attachedImage = attachedImageRepository.findById(imageId)
                    .orElseThrow(() -> {
                        log.error("Attachment id={} not found during painting registration", imageId);
                        return new ResourceNotFoundException(AttachedImage.class, imageId);
                    });

            if (attachedImage.getPainting() != null) {
                log.warn("Conflict: attachment id={} already assigned to painting", imageId);
                throw new ConflictException("Photo with ID " + imageId + " is already assigned!");
            }
            return attachedImage;
        }).toList();
    }
}