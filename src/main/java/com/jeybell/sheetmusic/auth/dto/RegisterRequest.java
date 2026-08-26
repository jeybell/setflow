package com.jeybell.sheetmusic.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank(message = "초대코드를 입력해 주세요.") String inviteCode
) {
}
