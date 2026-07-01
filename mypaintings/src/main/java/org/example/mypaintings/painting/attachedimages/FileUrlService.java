package org.example.mypaintings.painting.attachedimages;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Slf4j
@Service
public class FileUrlService {

    @Value("${app.base-url}")
    private String baseUrl;

    public String buildUrl(String fileKey) {
        try {
            String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/uploads/")
                    .path(fileKey)
                    .toUriString();

            log.debug("Generated URL based on the current request context for key: {}", fileKey);
            return url;

        } catch (IllegalStateException e) {
            log.debug("No active request context. Used base-url fallback for key: {}", fileKey);
            return baseUrl + "/uploads/" + fileKey;
        }
    }
}
