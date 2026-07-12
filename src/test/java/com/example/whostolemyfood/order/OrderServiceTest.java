package com.example.whostolemyfood.order;

import com.example.whostolemyfood.address.domain.entity.AddressEntity;
import com.example.whostolemyfood.address.domain.repository.AddressRepository;
import com.example.whostolemyfood.global.exception.CustomException;
import com.example.whostolemyfood.global.exception.ErrorCode;
import com.example.whostolemyfood.global.security.UserRoleValidator;
import com.example.whostolemyfood.menu.domain.entity.MenuEntity;
import com.example.whostolemyfood.menu.domain.repository.MenuRepository;
import com.example.whostolemyfood.order.application.service.OrderServiceV1;
import com.example.whostolemyfood.order.domain.entity.OrderEntity;
import com.example.whostolemyfood.order.domain.entity.OrderStatus;
import com.example.whostolemyfood.order.domain.repository.OrderRepository;
import com.example.whostolemyfood.order.presentation.dto.request.ReqCreateOrderDtoV1;
import com.example.whostolemyfood.order.presentation.dto.response.ResCreateOrderDtoV1;
import com.example.whostolemyfood.order.presentation.dto.response.ResGetOrderDtoV1;
import com.example.whostolemyfood.order.presentation.dto.response.ResGetOrderListDtoV1;
import com.example.whostolemyfood.store.domain.entity.StoreEntity;
import com.example.whostolemyfood.store.domain.entity.StoreStatus;
import com.example.whostolemyfood.store.domain.repository.StoreRepository;
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

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    private OrderServiceV1 orderService;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private MenuRepository menuRepository;
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

        orderService = new OrderServiceV1(
                orderRepository, storeRepository, menuRepository, addressRepository,
                new UserRoleValidator(userRepository));
    }

    private void mockUserCheck(UserRole role) {
        ReflectionTestUtils.setField(mockUser, "userRole", role);
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));
    }

    /**
     * [보안 재검증] validateUserRoleFromDB 3종 세트
     */
    @Test
    @DisplayName("[보안 실패] DB 권한 재검증 - 토큰의 Role과 실제 DB의 Role이 다를 경우 차단")
    void security_Fail_AuthorityMismatch() {
        mockUserCheck(UserRole.MASTER);
        assertThrows(CustomException.class, () -> 
            orderService.createOrder(ReqCreateOrderDtoV1.builder().build(), userId, UserRole.CUSTOMER));
    }

    @Test
    @DisplayName("[보안 실패] 유저 부재/탈퇴 - 유저가 DB에 없거나 삭제된 경우 차단")
    void security_Fail_UserNotFoundOrDeleted() {
        given(userRepository.findById(userId)).willReturn(Optional.empty());
        assertThrows(CustomException.class, () -> orderService.getOrder(UUID.randomUUID(), userId, UserRole.CUSTOMER));
    }

    /**
     * [성공 케이스] 사장님(OWNER)의 주문 상태 업데이트 순차 흐름 테스트
     * - PENDING -> ACCEPTED -> COOKING -> DELIVERING -> DELIVERED -> COMPLETED
     */
    @Test
    @DisplayName("[성공] 상태 업데이트 흐름 - 사장님이 비즈니스 규칙에 맞게 단계를 하나씩 진행")
    void updateOrderStatus_FullFlow_ByOwner() {
        // Given
        UUID ownerId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        ReflectionTestUtils.setField(mockUser, "id", ownerId);
        ReflectionTestUtils.setField(mockUser, "userRole", UserRole.OWNER);
        given(userRepository.findById(ownerId)).willReturn(Optional.of(mockUser));

        // 초기 상태: PENDING
        OrderEntity order = OrderEntity.builder().storeId(storeId).status(OrderStatus.PENDING).build();
        given(orderRepository.findById(any())).willReturn(Optional.of(order));

        UserEntity ownerEntity = UserEntity.builder().build();
        ReflectionTestUtils.setField(ownerEntity, "id", ownerId);
        StoreEntity store = StoreEntity.builder().user(ownerEntity).build();
        given(storeRepository.findById(storeId)).willReturn(Optional.of(store));

        // When & Then (순차적 전이 검증)
        // 1. PENDING -> ACCEPTED
        orderService.updateOrderStatus(order.getOrderId(), OrderStatus.ACCEPTED, ownerId, UserRole.OWNER);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);

        // 2. ACCEPTED -> COOKING
        orderService.updateOrderStatus(order.getOrderId(), OrderStatus.COOKING, ownerId, UserRole.OWNER);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COOKING);

        // 3. COOKING -> DELIVERING
        orderService.updateOrderStatus(order.getOrderId(), OrderStatus.DELIVERING, ownerId, UserRole.OWNER);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERING);

        // 4. DELIVERING -> DELIVERED
        orderService.updateOrderStatus(order.getOrderId(), OrderStatus.DELIVERED, ownerId, UserRole.OWNER);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        // 5. DELIVERED -> COMPLETED
        orderService.updateOrderStatus(order.getOrderId(), OrderStatus.COMPLETED, ownerId, UserRole.OWNER);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    /**
     * [비즈니스 방어] 상태에 따른 수정 불가 검증
     */
    @Test
    @DisplayName("[비즈니스 실패] 요청사항 수정 차단 - 이미 조리 중인 주문은 수정할 수 없음")
    void updateOrderRequest_Fail_WhenAlreadyCooking() {
        mockUserCheck(UserRole.CUSTOMER);
        OrderEntity cookingOrder = OrderEntity.builder().userId(userId).status(OrderStatus.COOKING).build();
        given(orderRepository.findById(any())).willReturn(Optional.of(cookingOrder));

        CustomException ex = assertThrows(CustomException.class, () -> 
            orderService.updateOrderRequest(UUID.randomUUID(), "지금이라도 오이 빼주세요", userId, UserRole.CUSTOMER));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORDER_REQUEST_UPDATE_FAILED);
    }

    /**
     * [조회 안정성] 검색 결과가 없을 때 (Empty Page)
     */
    @Test
    @DisplayName("[성공] 주문 목록 조회 - 검색 결과가 없는 경우 빈 페이지를 반환함")
    void getOrders_ReturnEmptyPage_WhenNoOrdersMatch() {
        UUID storeId = UUID.randomUUID();
        mockUserCheck(UserRole.MANAGER);
        given(orderRepository.findAllByStoreIdAndIsDeletedFalse(eq(storeId), any()))
                .willReturn(new PageImpl<>(Collections.emptyList()));

        Page<ResGetOrderListDtoV1> result = orderService.getOrders(storeId, null, PageRequest.of(0, 10), userId, UserRole.MANAGER);

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("[성공] 주문 취소 - 5분 이내 정상 취소")
    void cancelOrder_Success_Within5Min() {
        mockUserCheck(UserRole.CUSTOMER);
        OrderEntity order = OrderEntity.builder().userId(userId).status(OrderStatus.PENDING).build();
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now().minusMinutes(2)); 
        given(orderRepository.findById(any())).willReturn(Optional.of(order));

        ResGetOrderDtoV1 response = orderService.cancelOrder(UUID.randomUUID(), userId, UserRole.CUSTOMER);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("[실패] 주문 취소 - CUSTOMER가 5분을 초과한 경우 취소 실패 (요구사항)")
    void cancelOrder_Fail_TimeExceeded() {
        // Given
        mockUserCheck(UserRole.CUSTOMER);
        OrderEntity order = OrderEntity.builder().userId(userId).status(OrderStatus.PENDING).build();
        // 6분 전으로 강제 설정
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now().minusMinutes(6)); 
        
        given(orderRepository.findById(any())).willReturn(Optional.of(order));

        // When & Then
        CustomException ex = assertThrows(CustomException.class, () -> 
            orderService.cancelOrder(UUID.randomUUID(), userId, UserRole.CUSTOMER));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORDER_CANCEL_TIME_EXCEEDED);
    }

    /**
     * [추가 실패 케이스] 가게 운영 정책 위반 시 주문 생성 차단
     */
    @Test
    @DisplayName("[비즈니스 실패] 주문 생성 차단 - 가게가 CLOSED 상태거나 최소 주문 금액 미달 시 예외 발생")
    void createOrder_Fail_StorePolicyViolation() {
        // Given 1: 가게가 CLOSED 상태인 경우
        mockUserCheck(UserRole.CUSTOMER);
        UUID storeId = UUID.randomUUID();
        StoreEntity closedStore = StoreEntity.builder().status(StoreStatus.CLOSED).build();
        given(storeRepository.findById(storeId)).willReturn(Optional.of(closedStore));

        ReqCreateOrderDtoV1 request = ReqCreateOrderDtoV1.builder().storeId(storeId).build();

        // When & Then 1
        assertThat(assertThrows(CustomException.class, () -> 
            orderService.createOrder(request, userId, UserRole.CUSTOMER)).getErrorCode())
            .isEqualTo(ErrorCode.STORE_CLOSED);

        // Given 2: 최소 주문 금액 미달인 경우
        StoreEntity openStore = StoreEntity.builder()
                .status(StoreStatus.OPEN).openTime(LocalTime.MIN).closeTime(LocalTime.MAX)
                .minOrderPrice(50000).build(); // 최소 5만원
        given(storeRepository.findById(storeId)).willReturn(Optional.of(openStore));
        given(addressRepository.findByIdAndIsDeletedFalse(any())).willReturn(Optional.of(AddressEntity.builder().userId(userId).build()));
        given(menuRepository.findById(any())).willReturn(Optional.of(MenuEntity.builder().price(10000).build())); // 1만원만 주문

        ReqCreateOrderDtoV1 minPriceRequest = ReqCreateOrderDtoV1.builder()
                .storeId(storeId).addressId(UUID.randomUUID())
                .orderItems(List.of(ReqCreateOrderDtoV1.OrderItemRequest.builder().menuId(UUID.randomUUID()).quantity(1).priceAtOrder(10000).build()))
                .build();

        // When & Then 2
        assertThat(assertThrows(CustomException.class, () -> 
            orderService.createOrder(minPriceRequest, userId, UserRole.CUSTOMER)).getErrorCode())
            .isEqualTo(ErrorCode.ORDER_MIN_PRICE_NOT_MET);
    }

    /**
     * [추가 실패 케이스] 권한 없는 제3자의 접근 차단
     */
    @Test
    @DisplayName("[보안 실패] 주문 상세 조회 차단 - 본인이나 가게 사장님이 아닌 제3자가 조회 시 예외 발생")
    void getOrder_Fail_UnauthorizedAccess() {
        // Given: 주문 유저는 A인데, 조회 요청자는 B인 상황
        UUID userA = UUID.randomUUID();
        UUID userB = userId; // BeforeEach에서 세팅된 userId를 사용함

        // mockUserCheck(UserRole.CUSTOMER) 하나로 DB 조회 결과 세팅 완료
        mockUserCheck(UserRole.CUSTOMER); 

        OrderEntity orderOfUserA = OrderEntity.builder().userId(userA).storeId(UUID.randomUUID()).build();
        given(orderRepository.findById(any())).willReturn(Optional.of(orderOfUserA));
        // 제거: CUSTOMER 권한 조회 시에는 storeRepository.findById가 호출되지 않으므로 UnnecessaryStubbing 발생

        // When & Then
        CustomException ex = assertThrows(CustomException.class, () -> 
            orderService.getOrder(UUID.randomUUID(), userB, UserRole.CUSTOMER));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_OWNER);
    }
    }


