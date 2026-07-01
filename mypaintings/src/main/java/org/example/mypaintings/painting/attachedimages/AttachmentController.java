package org.example.mypaintings.painting.attachedimages;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.example.mypaintings.security.RateLimited;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachedImageService attachedImageService;


    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/upload-painting-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimited
    public ResponseEntity<Long> uploadImage(@RequestParam("file") MultipartFile file) {
        Long attachmentId = attachedImageService.store(file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(attachmentId);
    }
}