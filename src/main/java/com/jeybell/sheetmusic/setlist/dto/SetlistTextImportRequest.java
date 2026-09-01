package com.jeybell.sheetmusic.setlist.dto;

import jakarta.validation.constraints.NotBlank;

public record SetlistTextImportRequest(
        @NotBlank(message = "text is required")
        String text
) {
}
