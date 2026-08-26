package com.jeybell.sheetmusic.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.jeybell.sheetmusic.auth.dto.RegisterRequest;
import com.jeybell.sheetmusic.global.exception.InvalidInviteCodeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceInviteCodeTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    JwtService jwtService;

    private AuthService authService(String configuredCode) {
        return new AuthService(userRepository, passwordEncoder, jwtService, configuredCode);
    }

    @Test
    void rejectsRegistrationWhenServerHasNoInviteCode() {
        AuthService service = authService("");
        RegisterRequest request = new RegisterRequest("newuser", "password123", "anything");

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(InvalidInviteCodeException.class)
                .hasMessageContaining("비활성화");
    }

    @Test
    void rejectsWrongInviteCode() {
        AuthService service = authService("secret-code");
        RegisterRequest request = new RegisterRequest("newuser", "password123", "wrong-code");

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(InvalidInviteCodeException.class)
                .hasMessageContaining("올바르지 않");
    }

    @Test
    void allowsRegistrationWithCorrectInviteCode() {
        AuthService service = authService("secret-code");
        RegisterRequest request = new RegisterRequest("newuser", "password123", "  secret-code  ");
        lenient().when(userRepository.existsByUsername("newuser")).thenReturn(false);
        lenient().when(passwordEncoder.encode("password123")).thenReturn("hashed");
        lenient().when(userRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken("newuser")).thenReturn("token-123");

        var response = service.register(request);

        assertThat(response.token()).isEqualTo("token-123");
        assertThat(response.username()).isEqualTo("newuser");
    }
}
