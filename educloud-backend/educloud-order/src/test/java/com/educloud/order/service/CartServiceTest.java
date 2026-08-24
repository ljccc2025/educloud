package com.educloud.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.order.dto.request.CartAddRequest;
import com.educloud.order.dto.response.CartItemResponse;
import com.educloud.order.dto.response.CartSummaryResponse;
import com.educloud.order.entity.CartItemEntity;
import com.educloud.order.exception.OrderBizException;
import com.educloud.order.exception.OrderErrorCode;
import com.educloud.order.mapper.CartItemMapper;
import com.educloud.order.service.impl.CartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CartServiceTest {

    private CartItemMapper cartItemMapper;
    private CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        cartItemMapper = mock(CartItemMapper.class);
        cartService = new CartServiceImpl(cartItemMapper);
    }

    @Test
    void addsNewItemToCart() {
        when(cartItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            CartItemEntity entity = invocation.getArgument(0);
            entity.setId(1001L);
            return 1;
        }).when(cartItemMapper).insert(any(CartItemEntity.class));

        CartAddRequest request = CartAddRequest.builder().courseId(9001L).build();
        CartItemResponse response = cartService.addItem(2001L, request);

        assertThat(response).isNotNull();
        assertThat(response.getCourseId()).isEqualTo(9001L);
        assertThat(response.getSelected()).isTrue();

        ArgumentCaptor<CartItemEntity> captor = ArgumentCaptor.forClass(CartItemEntity.class);
        verify(cartItemMapper).insert(captor.capture());
        assertThat(captor.getValue().getStudentId()).isEqualTo(2001L);
        assertThat(captor.getValue().getCourseId()).isEqualTo(9001L);
        assertThat(captor.getValue().getSelected()).isTrue();
    }

    @Test
    void addsExistingItemToCartIdempotentlySetsSelected() {
        CartItemEntity existing = CartItemEntity.builder()
                .id(1001L)
                .studentId(2001L)
                .courseId(9001L)
                .selected(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(cartItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        CartAddRequest request = CartAddRequest.builder().courseId(9001L).build();
        CartItemResponse response = cartService.addItem(2001L, request);

        assertThat(response.getSelected()).isTrue();
        verify(cartItemMapper).updateById(existing);
        assertThat(existing.getSelected()).isTrue();
    }

    @Test
    void updatesSelectionState() {
        CartItemEntity existing = CartItemEntity.builder()
                .id(1001L)
                .studentId(2001L)
                .courseId(9001L)
                .selected(true)
                .build();
        when(cartItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        cartService.updateSelection(2001L, 9001L, false);

        verify(cartItemMapper).updateById(existing);
        assertThat(existing.getSelected()).isFalse();
    }

    @Test
    void throwsWhenUpdatingNonExistingCartItem() {
        when(cartItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> cartService.updateSelection(2001L, 9001L, false))
                .isInstanceOf(OrderBizException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    void removesCartItem() {
        cartService.removeItem(2001L, 9001L);
        verify(cartItemMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void clearsOnlySelectedOrAllCartItems() {
        cartService.clearCart(2001L, true);
        verify(cartItemMapper, times(1)).delete(any(LambdaQueryWrapper.class));

        cartService.clearCart(2001L, false);
        verify(cartItemMapper, times(2)).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void getsCartSummary() {
        CartItemEntity item1 = CartItemEntity.builder()
                .id(1001L)
                .studentId(2001L)
                .courseId(9001L)
                .selected(true)
                .createdAt(LocalDateTime.now())
                .build();
        CartItemEntity item2 = CartItemEntity.builder()
                .id(1002L)
                .studentId(2001L)
                .courseId(9002L)
                .selected(false)
                .createdAt(LocalDateTime.now())
                .build();
        when(cartItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item1, item2));

        CartSummaryResponse summary = cartService.getCartSummary(2001L);

        assertThat(summary).isNotNull();
        assertThat(summary.getTotalCount()).isEqualTo(2);
        assertThat(summary.getSelectedCount()).isEqualTo(1);
        assertThat(summary.getItems()).hasSize(2);
    }
}
