package com.example.whostolemyfood.global.security;

import com.example.whostolemyfood.global.exception.CustomException;
import com.example.whostolemyfood.global.exception.ErrorCode;
import com.example.whostolemyfood.user.domain.entity.UserEntity;
import com.example.whostolemyfood.user.domain.entity.UserRole;
import com.example.whostolemyfood.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserRoleValidatorTest {

    @Mock
    private UserRepository userRepository;

    private UserRoleValidator userRoleValidator;

    private UUID userId;
    private UserEntity mockUser;

    @BeforeEach
    void setUp() {
        userRoleValidator = new UserRoleValidator(userRepository);
        userId = UUID.randomUUID();
        mockUser = UserEntity.builder()
                .email("test@example.com")
                .role(UserRole.CUSTOMER)
                .build();
        ReflectionTestUtils.setField(mockUser, "id", userId);
        ReflectionTestUtils.setField(mockUser, "isDeleted", false);
    }

    @Test
    @DisplayName("[성공] 토큰 권한과 DB 권한이 같으면 통과")
    void validate_pass_whenRoleMatches() {
        ReflectionTestUtils.setField(mockUser, "userRole", UserRole.CUSTOMER);
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));

        assertThatCode(() -> userRoleValidator.validate(userId, UserRole.CUSTOMER))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[실패] 유저가 없으면 USER_NOT_FOUND")
    void validate_fail_whenUserNotFound() {
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> userRoleValidator.validate(userId, UserRole.CUSTOMER));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("[실패] 탈퇴한 유저면 USER_NOT_FOUND")
    void validate_fail_whenUserDeleted() {
        ReflectionTestUtils.setField(mockUser, "isDeleted", true);
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));

        CustomException ex = assertThrows(CustomException.class,
                () -> userRoleValidator.validate(userId, UserRole.CUSTOMER));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("[실패] 토큰 권한과 DB 권한이 다르면 ACCESS_DENIED")
    void validate_fail_whenRoleMismatch() {
        ReflectionTestUtils.setField(mockUser, "userRole", UserRole.MASTER);
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));

        CustomException ex = assertThrows(CustomException.class,
                () -> userRoleValidator.validate(userId, UserRole.CUSTOMER));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
    }
}
