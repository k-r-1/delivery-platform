package com.example.whostolemyfood.global.security;

import com.example.whostolemyfood.global.exception.CustomException;
import com.example.whostolemyfood.global.exception.ErrorCode;
import com.example.whostolemyfood.user.domain.entity.UserEntity;
import com.example.whostolemyfood.user.domain.entity.UserRole;
import com.example.whostolemyfood.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * JWT는 무상태라 발급 이후의 권한 변화(차단·강등·탈퇴)를 반영하지 못한다.
 * 주문·배송지 등 민감 도메인에서 매 요청마다 DB의 실제 권한과 대조해 재검증한다.
 * (각 서비스에 중복돼 있던 검증 로직을 이곳으로 통합)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRoleValidator {

    private final UserRepository userRepository;

    public void validate(UUID userId, UserRole tokenRole) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.getIsDeleted()) {
            log.warn("[Security] Deleted user access attempt. userId={}", userId);
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        if (user.getUserRole() != tokenRole) {
            log.warn("[Security] Role mismatch detected. userId={}, tokenRole={}, dbRole={}",
                    userId, tokenRole, user.getUserRole());
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
    }
}
