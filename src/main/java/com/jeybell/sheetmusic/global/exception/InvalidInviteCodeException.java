package com.jeybell.sheetmusic.global.exception;

/**
 * 회원가입 초대코드가 올바르지 않거나, 서버에 초대코드가 설정되어 있지 않아
 * 가입이 비활성화된 경우 던진다.
 */
public class InvalidInviteCodeException extends RuntimeException {

    public InvalidInviteCodeException(String message) {
        super(message);
    }
}
