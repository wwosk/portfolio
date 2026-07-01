package org.example.mypaintings.painting.attachedimages;

import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.exception.files.InvalidPhotoException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;


@Slf4j
@Component
public class ImageContentValidator {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC  = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    public void validate(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String declaredType = file.getContentType();

        if (file.isEmpty()) {
            throw new InvalidPhotoException(originalName, "File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidPhotoException(originalName,
                    "File exceeds max size of " + MAX_FILE_SIZE + " bytes");
        }
        if (declaredType == null || !ALLOWED_CONTENT_TYPES.contains(declaredType.toLowerCase())) {
            throw new InvalidPhotoException(originalName,
                    "Content-Type must be image/jpeg or image/png");
        }
        if (!magicBytesMatch(file, declaredType)) {
            log.warn("Magic bytes mismatch for files: {} (declared {})", originalName, declaredType);
            throw new InvalidPhotoException(originalName,
                    "File content does not match declared Content-Type");
        }
    }

    private boolean magicBytesMatch(MultipartFile file, String contentType) {
        byte[] header = new byte[8];
        try (InputStream in = file.getInputStream()) {
            int read = in.read(header);
            if (read < 3) return false;
        } catch (IOException e) {
            return false;
        }
        if ("image/jpeg".equalsIgnoreCase(contentType)) {
            return startsWith(header, JPEG_MAGIC);
        }
        if ("image/png".equalsIgnoreCase(contentType)) {
            return startsWith(header, PNG_MAGIC);
        }
        return false;
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }
}