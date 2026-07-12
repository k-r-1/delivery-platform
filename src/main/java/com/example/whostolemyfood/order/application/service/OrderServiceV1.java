package com.example.whostolemyfood.order.application.service;

import com.example.whostolemyfood.address.domain.entity.AddressEntity;
import com.example.whostolemyfood.address.domain.repository.AddressRepository;
import com.example.whostolemyfood.global.exception.CustomException;
import com.example.whostolemyfood.global.exception.ErrorCode;
import com.example.whostolemyfood.menu.domain.entity.MenuEntity;
import com.example.whostolemyfood.menu.domain.repository.MenuRepository;
import com.example.whostolemyfood.order.domain.entity.OrderEntity;
import com.example.whostolemyfood.order.domain.entity.OrderItemEntity;
import com.example.whostolemyfood.order.domain.entity.OrderStatus;
import com.example.whostolemyfood.order.domain.repository.OrderRepository;
import com.example.whostolemyfood.order.presentation.dto.request.ReqCreateOrderDtoV1;
import com.example.whostolemyfood.order.presentation.dto.response.ResCreateOrderDtoV1;
import com.example.whostolemyfood.order.presentation.dto.response.ResGetOrderDtoV1;
import com.example.whostolemyfood.order.presentation.dto.response.ResGetOrderListDtoV1;
import com.example.whostolemyfood.store.domain.entity.StoreEntity;
import com.example.whostolemyfood.store.domain.entity.StoreStatus;
import com.example.whostolemyfood.store.domain.repository.StoreRepository;
import com.example.whostolemyfood.global.security.UserRoleValidator;
import com.example.whostolemyfood.user.domain.entity.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceV1 {

    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final AddressRepository addressRepository;
    private final UserRoleValidator userRoleValidator;

    /**
     * 주문 생성 (CUSTOMER 전용)
     */
    @Transactional
    public ResCreateOrderDtoV1 createOrder(ReqCreateOrderDtoV1 request, UUID userId, UserRole role) {
        userRoleValidator.validate(userId, role);

        log.info("[Order] Creating order. User: {}, Store: {}", userId, request.getStoreId());

        StoreEntity store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));

        if (Boolean.TRUE.equals(store.getIsHidden())) {
            throw new CustomException(ErrorCode.STORE_NOT_FOUND);
        }

        if (store.getStatus() != StoreStatus.OPEN) {
            throw new CustomException(ErrorCode.STORE_CLOSED);
        }

        LocalTime nowTime = LocalTime.now();
        if (nowTime.isBefore(store.getOpenTime()) || nowTime.isAfter(store.getCloseTime())) {
            throw new CustomException(ErrorCode.STORE_CLOSED);
        }

        AddressEntity address = addressRepository.findByIdAndIsDeletedFalse(request.getAddressId())
                .orElseThrow(() -> new CustomException(ErrorCode.ADDRESS_NOT_FOUND));
        
        if (Boolean.TRUE.equals(address.getIsDeleted())) {
            throw new CustomException(ErrorCode.ADDRESS_NOT_FOUND);
        }

        if (!address.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ADDRESS_NOT_OWNER);
        }

        int calculatedItemTotalPrice = 0;
        for (ReqCreateOrderDtoV1.OrderItemRequest itemRequest : request.getOrderItems()) {
            MenuEntity menu = menuRepository.findById(itemRequest.getMenuId())
                    .orElseThrow(() -> new CustomException(ErrorCode.MENU_NOT_FOUND));
            
            if (Boolean.TRUE.equals(menu.getIsDeleted()) || Boolean.TRUE.equals(menu.getIsHidden())) {
                throw new CustomException(ErrorCode.MENU_NOT_FOUND);
            }

            if (!menu.getPrice().equals(itemRequest.getPriceAtOrder())) {
                throw new CustomException(ErrorCode.PRICE_MISMATCH);
            }
            calculatedItemTotalPrice += menu.getPrice() * itemRequest.getQuantity();
        }

        if (store.getMinOrderPrice() != null && calculatedItemTotalPrice < store.getMinOrderPrice()) {
            throw new CustomException(ErrorCode.ORDER_MIN_PRICE_NOT_MET);
        }

        int deliveryFee = 3000; 
        int finalTotalPrice = calculatedItemTotalPrice + deliveryFee;

        OrderEntity order = OrderEntity.builder()
                .userId(userId)
                .storeId(request.getStoreId())
                .addressId(request.getAddressId())
                .request(request.getRequest())
                .totalPrice(finalTotalPrice)
                .deliveryFee(deliveryFee)
                .status(OrderStatus.PENDING)
                .build();
        
        order.markCreatedBy(userId);

        List<OrderItemEntity> orderItems = request.getOrderItems().stream()
                .map(itemRequest -> OrderItemEntity.builder()
                        .order(order)
                        .menuId(itemRequest.getMenuId())
                        .quantity(itemRequest.getQuantity())
                        .priceAtOrder(itemRequest.getPriceAtOrder())
                        .createdBy(userId)
                        .build())
                .toList();

        order.getOrderItems().addAll(orderItems);

        OrderEntity savedOrder = orderRepository.save(order);
        log.info("[Order] Successfully created. OrderId: {}", savedOrder.getOrderId());
        return ResCreateOrderDtoV1.from(savedOrder, "주문이 성공적으로 생성되었습니다.");
    }

    /**
     * 주문 상세 조회
     */
    public ResGetOrderDtoV1 getOrder(UUID orderId, UUID userId, UserRole role) {
        userRoleValidator.validate(userId, role);

        OrderEntity order = orderRepository.findById(orderId)
                .filter(o -> !o.getIsDeleted())
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));

        boolean isAuthorized = false;
        if (role == UserRole.MASTER || role == UserRole.MANAGER) {
            isAuthorized = true;
        } else if (role == UserRole.CUSTOMER && order.getUserId().equals(userId)) {
            isAuthorized = true;
        } else if (role == UserRole.OWNER) {
            StoreEntity store = storeRepository.findById(order.getStoreId())
                    .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));
            if (store.getUser() != null && store.getUser().getId().equals(userId)) {
                isAuthorized = true;
            }
        }

        if (!isAuthorized) {
            throw new CustomException(ErrorCode.ORDER_NOT_OWNER);
        }

        return ResGetOrderDtoV1.from(order);
    }

    /**
     * 주문 목록 조회
     */
    public Page<ResGetOrderListDtoV1> getOrders(UUID storeId, Boolean isHidden, Pageable pageable, UUID userId, UserRole role) {
        userRoleValidator.validate(userId, role);

        if (role == UserRole.CUSTOMER) {
            return orderRepository.findAllByUserIdAndIsDeletedFalse(userId, pageable).map(ResGetOrderListDtoV1::from);
        }

        if (role == UserRole.OWNER) {
            if (storeId == null) { throw new CustomException(ErrorCode.VALIDATION_ERROR); }
            StoreEntity store = storeRepository.findById(storeId)
                    .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));
            if (store.getUser() == null || !store.getUser().getId().equals(userId)) {
                throw new CustomException(ErrorCode.ORDER_FORBIDDEN_FOR_OWNER);
            }
            return orderRepository.findAllByStoreIdAndIsDeletedFalse(storeId, pageable).map(ResGetOrderListDtoV1::from);
        }

        if (storeId != null && isHidden != null) {
            return orderRepository.findAllByStoreIdAndIsHiddenAndIsDeletedFalse(storeId, isHidden, pageable).map(ResGetOrderListDtoV1::from);
        } else if (storeId != null) {
            return orderRepository.findAllByStoreIdAndIsDeletedFalse(storeId, pageable).map(ResGetOrderListDtoV1::from);
        } else if (isHidden != null) {
            return orderRepository.findAllByIsHiddenAndIsDeletedFalse(isHidden, pageable).map(ResGetOrderListDtoV1::from);
        }
        return orderRepository.findAllByIsDeletedFalse(pageable).map(ResGetOrderListDtoV1::from);
    }

    /**
     * 주문 취소 (CUSTOMER(본인/5분), MASTER 전용)
     */
    @Transactional
    public ResGetOrderDtoV1 cancelOrder(UUID orderId, UUID userId, UserRole role) {
        userRoleValidator.validate(userId, role);

        OrderEntity order = orderRepository.findById(orderId)
                .filter(o -> !o.getIsDeleted())
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));

        // MASTER는 무조건 취소 가능
        if (role == UserRole.MASTER) {
            order.cancelOrder();
            order.markUpdatedBy(userId);
            return ResGetOrderDtoV1.from(order, "[MASTER] 주문이 강제 취소되었습니다.");
        }

        if (role == UserRole.CUSTOMER) {
            if (!order.getUserId().equals(userId)) { throw new CustomException(ErrorCode.ORDER_NOT_OWNER); }
            if (order.getStatus() != OrderStatus.PENDING) { throw new CustomException(ErrorCode.ORDER_CANCEL_NOT_PENDING); }
            
            LocalDateTime now = LocalDateTime.now();
            Duration duration = Duration.between(order.getCreatedAt(), now);
            if (duration.toMinutes() >= 5) { throw new CustomException(ErrorCode.ORDER_CANCEL_TIME_EXCEEDED); }
            
            order.cancelOrder();
            order.markUpdatedBy(userId);
            return ResGetOrderDtoV1.from(order, "주문이 성공적으로 취소되었습니다.");
        }

        throw new CustomException(ErrorCode.ACCESS_DENIED);
    }

    /**
     * 주문 요청사항 수정
     */
    @Transactional
    public ResGetOrderDtoV1 updateOrderRequest(UUID orderId, String newRequest, UUID userId, UserRole role) {
        userRoleValidator.validate(userId, role);

        OrderEntity order = orderRepository.findById(orderId)
                .filter(o -> !o.getIsDeleted())
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.ORDER_NOT_OWNER);
        }

        try {
            order.updateRequest(newRequest); 
            order.markUpdatedBy(userId);
        } catch (IllegalStateException e) {
            throw new CustomException(ErrorCode.ORDER_REQUEST_UPDATE_FAILED);
        }
        
        return ResGetOrderDtoV1.from(order, "요청사항이 성공적으로 수정되었습니다.");
    }

    /**
     * 주문 상태 변경 (사장님 순차전이, 관리자 슈퍼변경 🚨)
     */
    @Transactional
    public ResGetOrderDtoV1 updateOrderStatus(UUID orderId, OrderStatus nextStatus, UUID userId, UserRole role) {
        userRoleValidator.validate(userId, role);

        OrderEntity order = orderRepository.findById(orderId)
                .filter(o -> !o.getIsDeleted())
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));
        
        if (role == UserRole.OWNER) {
            StoreEntity store = storeRepository.findById(order.getStoreId())
                    .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));
            if (store.getUser() == null || !store.getUser().getId().equals(userId)) {
                throw new CustomException(ErrorCode.ORDER_FORBIDDEN_FOR_OWNER);
            }
        }

        try {
            if (role == UserRole.MASTER || role == UserRole.MANAGER) {
                order.forceUpdateStatus(nextStatus); 
            } else {
                order.updateStatus(nextStatus); 
            }
            order.markUpdatedBy(userId);
        } catch (IllegalStateException e) {
            throw new CustomException(ErrorCode.ORDER_STATUS_UPDATE_FAILED);
        }
        
        return ResGetOrderDtoV1.from(order, "주문 상태가 변경되었습니다.");
    }

    /**
     * 주문 삭제
     */
    @Transactional
    public void deleteOrder(UUID orderId, UUID userId, UserRole role) {
        userRoleValidator.validate(userId, role);

        OrderEntity order = orderRepository.findById(orderId)
                .filter(o -> !o.getIsDeleted())
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.COMPLETED) {
            throw new CustomException(ErrorCode.ORDER_CANNOT_DELETE_DELIVERED);
        }

        order.softDelete(userId); 
    }
}
