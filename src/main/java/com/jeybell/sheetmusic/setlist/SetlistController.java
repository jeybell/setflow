package com.jeybell.sheetmusic.setlist;

import com.jeybell.sheetmusic.setlist.dto.SetlistDuplicateRequest;
import com.jeybell.sheetmusic.setlist.dto.SetlistRequest;
import com.jeybell.sheetmusic.setlist.dto.SetlistResponse;
import com.jeybell.sheetmusic.setlist.dto.SetlistTextImportRequest;
import com.jeybell.sheetmusic.setlist.dto.SharedSetlistResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/setlists")
public class SetlistController {

    private final SetlistService setlistService;

    public SetlistController(SetlistService setlistService) {
        this.setlistService = setlistService;
    }

    @GetMapping
    public ResponseEntity<List<SetlistResponse>> getSetlists() {
        return ResponseEntity.ok(setlistService.getSetlists());
    }

    @PostMapping
    public ResponseEntity<SetlistResponse> createSetlist(@Valid @RequestBody SetlistRequest request) {
        SetlistResponse response = setlistService.createSetlist(request);
        return ResponseEntity
                .created(Objects.requireNonNull(URI.create("/api/setlists/" + response.setlistId())))
                .body(response);
    }

    @GetMapping("/{setlistId}")
    public ResponseEntity<SetlistResponse> getSetlist(@PathVariable("setlistId") Long setlistId) {
        return ResponseEntity.ok(setlistService.getSetlist(setlistId));
    }

    @PutMapping("/{setlistId}")
    public ResponseEntity<SetlistResponse> updateSetlist(
            @PathVariable("setlistId") Long setlistId,
            @Valid @RequestBody SetlistRequest request
    ) {
        return ResponseEntity.ok(setlistService.updateSetlist(setlistId, request));
    }

    @DeleteMapping("/{setlistId}")
    public ResponseEntity<Void> deleteSetlist(@PathVariable("setlistId") Long setlistId) {
        setlistService.deleteSetlist(setlistId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/from-text")
    public ResponseEntity<SetlistResponse> createFromText(@Valid @RequestBody SetlistTextImportRequest request) {
        SetlistResponse response = setlistService.createFromText(request.text());
        return ResponseEntity
                .created(Objects.requireNonNull(URI.create("/api/setlists/" + response.setlistId())))
                .body(response);
    }

    @PostMapping("/{setlistId}/duplicate")
    public ResponseEntity<SetlistResponse> duplicateSetlist(
            @PathVariable("setlistId") Long setlistId,
            @Valid @RequestBody SetlistDuplicateRequest request
    ) {
        SetlistResponse response = setlistService.duplicateSetlist(setlistId, request.serviceDate());
        return ResponseEntity
                .created(Objects.requireNonNull(URI.create("/api/setlists/" + response.setlistId())))
                .body(response);
    }

    @PostMapping("/{setlistId}/share")
    public ResponseEntity<Map<String, String>> generateShareToken(@PathVariable("setlistId") Long setlistId) {
        String token = setlistService.generateShareToken(setlistId);
        return ResponseEntity.ok(Map.of("shareToken", token));
    }

    @DeleteMapping("/{setlistId}/share")
    public ResponseEntity<Void> revokeShareToken(@PathVariable("setlistId") Long setlistId) {
        setlistService.revokeShareToken(setlistId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/share/{token}")
    public ResponseEntity<SharedSetlistResponse> getShared(@PathVariable("token") String token) {
        return ResponseEntity.ok(setlistService.getByShareToken(token));
    }
}
