package org.example.mypaintings.painting.attachedimages;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class AttachedImageTransactionService {

    private final AttachedImageRepository attachedImageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long saveMetadata(String fileKey, String contentType, String originalFilename, long fileSize) {
        AttachedImage attachment = new AttachedImage();
        attachment.setFileKey(fileKey);
        attachment.setContentType(contentType);
        attachment.setOriginalFileName(originalFilename);
        attachment.setFileSize(fileSize);

        Long savedId = attachedImageRepository.save(attachment).getId();
        log.info("Saved attachment metadata id={} key={}", savedId, fileKey);
        return savedId;
    }
}