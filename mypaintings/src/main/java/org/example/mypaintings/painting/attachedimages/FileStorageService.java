package org.example.mypaintings.painting.attachedimages;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.exception.files.FileStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;


@Slf4j
@Service
public class FileStorageService {

    private final Path rootLocation;

    public FileStorageService(@Value("${app.upload.path}") String uploadPath) {
        this.rootLocation = Paths.get(uploadPath).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(rootLocation);
            log.info("File storage root initialized at: {}", rootLocation);
        } catch (IOException e) {
            throw new FileStorageException("Could not initialize storage directory: " + rootLocation, e);
        }
    }


    public void store(MultipartFile file, String fileKey) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Cannot store empty files.");
        }

        Path target = resolveSafely(fileKey);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(tmp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {

                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("Stored files: {} ({} bytes)", target, file.getSize());
        } catch (IOException e) {

            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            throw new FileStorageException("Failed to store files: " + fileKey, e);
        }
    }

    public void delete(String fileKey) {
        try {
            Files.deleteIfExists(resolveSafely(fileKey));
        } catch (IOException e) {
            log.warn("Failed to delete files: {}", fileKey, e);
        }
    }

    private Path resolveSafely(String fileKey) {
        Path resolved = rootLocation.resolve(fileKey).normalize();
        if (!resolved.startsWith(rootLocation)) {
            throw new FileStorageException("Invalid files key (path traversal): " + fileKey);
        }
        return resolved;
    }
}