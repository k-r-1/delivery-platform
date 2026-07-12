package com.example.whostolemyfood.address.application.service;

import com.example.whostolemyfood.address.domain.entity.AddressEntity;
import com.example.whostolemyfood.address.domain.repository.AddressRepository;
import com.example.whostolemyfood.address.presentation.dto.request.ReqCreateAddressDtoV1;
import com.example.whostolemyfood.address.presentation.dto.request.ReqUpdateAddressDtoV1;
import com.example.whostolemyfood.address.presentation.dto.response.ResCreateAddressDtoV1;
import com.example.whostolemyfood.address.presentation.dto.response.ResGetAddressDtoV1;
import com.example.whostolemyfood.global.exception.CustomException;
import com.example.whostolemyfood.global.exception.ErrorCode;
import com.example.whostolemyfood.global.security.UserRoleValidator;
import com.example.whostolemyfood.user.domain.entity.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressServiceV1 {

    private final AddressRepository addressRepository;
    private final UserRoleValidator userRoleValidator;

    /**
     * 배송지 생성
     */
    @Transactional
    public ResCreateAddressDtoV1 createAddress(ReqCreateAddressDtoV1 request, UUID userId, UserRole role) {
        userRoleValidator.validate(userId, role);

        log.info("[Address] Creating new address for User: {}, Alias: {}", userId, request.getAlias());
        
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            handleDefaultAddress(userId);
        }

        AddressEntity address = request.toEntity(userId);
        address.markCreatedBy(userId);

        AddressEntity savedAddress = addressRepository.save(address);
        
        return ResCreateAddressDtoV1.from(savedAddress, "배송지가 성공적으로 생성되었습니다.");
    }

    /**
     * 본인의 배송지 목록 조회
     */
    public Page<ResGetAddressDtoV1> getMyAddresses(UUID userId, UserRole role, String alias, Pageable pageable) {
        userRoleValidator.validate(userId, role);

        log.info("[Address] Fetching addresses for User: {}, Filter: {}", userId, alias);
        
        Page<AddressEntity> addresses;
        if (alias != null && !alias.isBlank()) {
            addresses = addressRepository.findAllByUserIdAndAliasContainingAndIsDeletedFalse(userId, alias, pageable);
        } else {
            addresses = addressRepository.findAllByUserIdAndIsDeletedFalse(userId, pageable);
        }
        
        return addresses.map(ResGetAddressDtoV1::from);
    }

    /**
     * 배송지 수정
     */
    @Transactional
    public ResGetAddressDtoV1 updateAddress(UUID addressId, ReqUpdateAddressDtoV1 request, UUID userId, UserRole role) {
        userRoleValidator.validate(userId, role);

        AddressEntity address = addressRepository.findByIdAndIsDeletedFalse(addressId)
                .orElseThrow(() -> new CustomException(ErrorCode.ADDRESS_NOT_FOUND));

        // [인가] 본인 확인 로직
        if (!address.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ADDRESS_NOT_OWNER);
        }

        if (Boolean.TRUE.equals(request.getIsDefault()) && !address.getIsDefault()) {
            handleDefaultAddress(userId);
        }

        address.updateAddress(
                request.getAlias(),
                request.getAddress(),
                request.getDetail(),
                request.getZipCode(),
                request.getIsDefault()
        );
        
        address.markUpdatedBy(userId);

        return ResGetAddressDtoV1.from(address, "배송지 정보가 성공적으로 수정되었습니다.");
    }

    /**
     * 배송지 삭제 (Soft Delete)
     */
    @Transactional
    public void deleteAddress(UUID addressId, UUID userId, UserRole role) {
        userRoleValidator.validate(userId, role);

        AddressEntity address = addressRepository.findByIdAndIsDeletedFalse(addressId)
                .orElseThrow(() -> new CustomException(ErrorCode.ADDRESS_NOT_FOUND));

        // [인가] 본인 확인 로직
        if (!address.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ADDRESS_NOT_OWNER);
        }

        address.softDelete(userId);
    }

    private void handleDefaultAddress(UUID userId) {
        addressRepository.findByUserIdAndIsDefaultTrueAndIsDeletedFalse(userId)
                .ifPresent(existingDefault -> existingDefault.setDefault(false));
    }
}
