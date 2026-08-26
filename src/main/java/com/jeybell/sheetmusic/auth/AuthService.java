package com.jeybell.sheetmusic.auth;

import com.jeybell.sheetmusic.auth.dto.AuthResponse;
import com.jeybell.sheetmusic.auth.dto.LoginRequest;
import com.jeybell.sheetmusic.auth.dto.RegisterRequest;
import com.jeybell.sheetmusic.global.exception.DuplicateUsernameException;
import com.jeybell.sheetmusic.global.exception.InvalidCredentialsException;
import com.jeybell.sheetmusic.global.exception.InvalidInviteCodeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String inviteCode;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${registration.invite-code:}") String inviteCode
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.inviteCode = inviteCode;
    }

    public AuthResponse register(RegisterRequest request) {
        verifyInviteCode(request.inviteCode());
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUsernameException(request.username());
        }
        User user = new User(request.username(), passwordEncoder.encode(request.password()));
        try {
            // existsByUsername 체크와 save 사이의 경쟁 상태 방어: 동시 요청으로 유니크 제약을
            // 위반하면 500 대신 명확한 409(DuplicateUsername)로 변환한다.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateUsernameException(request.username());
        }
        return new AuthResponse(jwtService.generateToken(user.getUsername()), user.getUsername(), user.getRole().name());
    }

    /**
     * 초대코드 검증. 서버에 초대코드가 설정되어 있지 않으면(blank) 가입을 완전히 막는다(fail-closed).
     * 설정되어 있으면 입력값과 정확히 일치해야만 통과한다.
     */
    private void verifyInviteCode(String provided) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new InvalidInviteCodeException("현재 회원가입이 비활성화되어 있습니다. 관리자에게 문의해 주세요.");
        }
        if (provided == null || !inviteCode.equals(provided.trim())) {
            throw new InvalidInviteCodeException("초대코드가 올바르지 않습니다.");
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return new AuthResponse(jwtService.generateToken(user.getUsername()), user.getUsername(), user.getRole().name());
    }

    /**
     * 가입 없이 바로 사용해볼 수 있는 게스트 로그인. 현재 데이터는 팀 전체가 공유하고
     * 사용자별로 분리되지 않으므로(로그인은 접근 게이트 용도), 실제 계정 없이
     * 고정된 게스트 신원으로 토큰만 발급한다.
     * DB를 전혀 조회하지 않으므로 클래스 레벨 @Transactional을 명시적으로 꺼서(NOT_SUPPORTED)
     * Hibernate가 read-only 트랜잭션 시작 시 커넥션 풀에서 물리 커넥션을 선점하지 않게 한다
     * (readOnly 트랜잭션도 시작 시점에 read-only 플래그를 걸기 위해 커넥션을 즉시 확보함).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuthResponse guestLogin() {
        return new AuthResponse(jwtService.generateToken("guest"), "guest", UserRole.USER.name());
    }
}
