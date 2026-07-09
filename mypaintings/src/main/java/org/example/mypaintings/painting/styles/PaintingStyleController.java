package org.example.mypaintings.painting.styles;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.mypaintings.painting.styles.dto.CreateStyleRequest;
import org.example.mypaintings.painting.styles.dto.StyleResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;



@RestController
@RequestMapping("/api/styles")
@RequiredArgsConstructor
public class PaintingStyleController {

    private final PaintingStyleService styleService;

    @GetMapping("/autocomplete")
    public ResponseEntity<List<StyleResponse>> autocomplete(
            @RequestParam("query") String query,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(styleService.autocomplete(query, limit));
    }

    @GetMapping("/official/popular")
    public ResponseEntity<List<StyleResponse>> getPopularOfficialStyles(
            @RequestParam(value = "limit", defaultValue = "2") int limit) {
        return ResponseEntity.ok(styleService.getPopularOfficialStyles(limit));
    }

    @PostMapping("/resolve")
    public ResponseEntity<StyleResponse> getOrCreateStyle(@Valid @RequestBody CreateStyleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(styleService.getOrCreateStyle(request.name()));
    }

}