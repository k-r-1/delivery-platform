package com.example.whostolemyfood.address;

import com.example.whostolemyfood.address.application.service.AddressServiceV1;
import com.example.whostolemyfood.address.domain.entity.AddressEntity;
import com.example.whostolemyfood.address.domain.repository.AddressRepository;
import com.example.whostolemyfood.address.presentation.dto.request.ReqCreateAddressDtoV1;
import com.example.whostolemyfood.address.presentation.dto.request.ReqUpdateAddressDtoV1;
import com.example.whostolemyfood.address.presentation.dto.response.ResCreateAddressDtoV1;
import com.example.whostolemyfood.address.presentation.dto.response.ResGetAddressDtoV1;
import com.example.whostolemyfood.global.exception.CustomException;
import com.example.whostolemyfood.global.exception.ErrorCode;
import com.example.whostolemyfood.global.security.UserRoleValidator;
import com.example.whostolemyfood.user.domain.entity.UserEntity;
import com.example.whostolemyfood.user.domain.entity.UserRole;
import com.example.whostolemyfood.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class AddressServiceTest {

    private AddressServiceV1 addressService;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private UserRepository userRepository;

    private UUID userId;
    private UserEntity mockUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mockUser = UserEntity.builder()
                .email("test@example.com")
                .role(UserRole.CUSTOMER)
                .build();
        ReflectionTestUtils.setField(mockUser, "id", userId);
        ReflectionTestUtils.setField(mockUser, "isDeleted", false);

        addressService = new AddressServiceV1(addressRepository, new UserRoleValidator(userRepository));
    }

    private void mockUserCheck(UserRole role) {
        ReflectionTestUtils.setField(mockUser, "userRole", role);
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
    }

    /**
     * [보안 재검증] 실시간 권한 대조 테스트
     */
    @Test
    @DisplayName("[보안 실패] DB 권한 재검증 - 토큰의 Role과 실제 DB의 Role이 다를 경우 차단")
    void security_Fail_AuthorityMismatch() {
        // Given: 토큰 권한은 CUSTOMER인데, DB에는 MASTER로 바뀌어 있는 상황 가정
        UserRole tokenRole = UserRole.CUSTOMER;
        mockUserCheck(UserRole.MASTER); // DB 상태 업데이트

        // When & Then
        CustomException ex = assertThrows(CustomException.class, () -> 
            addressService.createAddress(ReqCreateAddressDtoV1.builder().build(), userId, tokenRole));
        
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("[보안 실패] DB 권한 재검증 - 유저가 DB에 존재하지 않을 경우 USER_NOT_FOUND 발생")
    void security_Fail_UserNotFound() {
        // Given
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // When & Then
        CustomException ex = assertThrows(CustomException.class, () -> 
            addressService.getMyAddresses(userId, UserRole.CUSTOMER, null, PageRequest.of(0, 10)));
        
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("[보안 실패] DB 권한 재검증 - 유저가 탈퇴(isDeleted=true) 상태인 경우 USER_NOT_FOUND 발생")
    void security_Fail_UserDeleted() {
        // Given
        ReflectionTestUtils.setField(mockUser, "isDeleted", true); // 탈퇴 처리
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));

        // When & Then
        CustomException ex = assertThrows(CustomException.class, () -> 
            addressService.deleteAddress(UUID.randomUUID(), userId, UserRole.CUSTOMER));
        
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    /**
     * [비즈니스 로직] 기본 배송지 자동 전환 테스트
     */
    @Test
    @DisplayName("[성공] 기본 배송지 전환 - 새로운 기본 주소 설정 시 기존 기본 주소는 해제됨")
    void createAddress_Success_HandleDefaultAddress() {
        // Given
        mockUserCheck(UserRole.CUSTOMER);
        ReqCreateAddressDtoV1 request = ReqCreateAddressDtoV1.builder()
                .address("새로운 기본집").isDefault(true).build();
        
        // 기존에 이미 '기본 배송지'인 데이터가 있다고 가정
        AddressEntity existingDefault = AddressEntity.builder()
                .userId(userId).address("옛날 기본집").isDefault(true).build();
        
        given(addressRepository.findByUserIdAndIsDefaultTrueAndIsDeletedFalse(userId))
                .willReturn(Optional.of(existingDefault));
        
        given(addressRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // When
        addressService.createAddress(request, userId, UserRole.CUSTOMER);

        // Then
        assertThat(existingDefault.getIsDefault()).isFalse(); // 기존꺼는 자동으로 꺼져야 함
    }

    /**
     * 소유권 및 Soft Delete] 삭제 인가 테스트
     */
    @Test
    @DisplayName("[인가 실패] 배송지 삭제 - 타인의 주소를 삭제 시도 시 ADDRESS_NOT_OWNER 발생")
    void deleteAddress_Fail_NotOwner() {
        // Given
        mockUserCheck(UserRole.CUSTOMER);
        UUID addressId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        
        // 주인은 'otherUserId'인 배송지 정보
        AddressEntity otherAddress = AddressEntity.builder().userId(otherUserId).build();
        given(addressRepository.findByIdAndIsDeletedFalse(addressId)).willReturn(Optional.of(otherAddress));

        // When & Then
        CustomException ex = assertThrows(CustomException.class, () -> 
            addressService.deleteAddress(addressId, userId, UserRole.CUSTOMER));
        
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ADDRESS_NOT_OWNER);
    }

    @Test
    @DisplayName("[성공] 배송지 삭제 - 본인 소유인 경우 Soft Delete 정상 수행")
    void deleteAddress_Success() {
        // Given
        mockUserCheck(UserRole.CUSTOMER);
        UUID addressId = UUID.randomUUID();
        AddressEntity myAddress = AddressEntity.builder().userId(userId).build();
        ReflectionTestUtils.setField(myAddress, "id", addressId);
        
        given(addressRepository.findByIdAndIsDeletedFalse(addressId)).willReturn(Optional.of(myAddress));

        // When
        addressService.deleteAddress(addressId, userId, UserRole.CUSTOMER);

        // Then
        assertThat(myAddress.getIsDeleted()).isTrue(); // Soft Delete 확인
    }

    /**
     * [조회 및 필터링] 목록 검색 테스트
     */
    @Test
    @DisplayName("[성공] 본인 배송지 목록 조회 - 별칭 검색어 필터링 연동 확인")
    void getMyAddresses_WithFiltering_Success() {
        // Given
        mockUserCheck(UserRole.CUSTOMER);
        String searchAlias = "우리집";
        AddressEntity filteredAddress = AddressEntity.builder().userId(userId).alias(searchAlias).build();
        
        given(addressRepository.findAllByUserIdAndAliasContainingAndIsDeletedFalse(eq(userId), eq(searchAlias), any()))
                .willReturn(new PageImpl<>(List.of(filteredAddress)));

        // When
        Page<ResGetAddressDtoV1> result = addressService.getMyAddresses(userId, UserRole.CUSTOMER, searchAlias, PageRequest.of(0, 10));

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAlias()).isEqualTo(searchAlias);
    }
}
