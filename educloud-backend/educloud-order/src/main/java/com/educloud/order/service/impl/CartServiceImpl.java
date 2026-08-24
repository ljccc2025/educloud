package com.educloud.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.order.dto.request.CartAddRequest;
import com.educloud.order.dto.response.CartItemResponse;
import com.educloud.order.dto.response.CartSummaryResponse;
import com.educloud.order.entity.CartItemEntity;
import com.educloud.order.exception.OrderBizException;
import com.educloud.order.exception.OrderErrorCode;
import com.educloud.order.mapper.CartItemMapper;
import com.educloud.order.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartItemMapper cartItemMapper;

    @Override
    @Transactional
    public CartItemResponse addItem(Long studentId, CartAddRequest request) {
        Long courseId = request.getCourseId();
        LambdaQueryWrapper<CartItemEntity> query = new LambdaQueryWrapper<CartItemEntity>()
                .eq(CartItemEntity::getStudentId, studentId)
                .eq(CartItemEntity::getCourseId, courseId);
        CartItemEntity existing = cartItemMapper.selectOne(query);
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setSelected(true);
            existing.setUpdatedAt(now);
            cartItemMapper.updateById(existing);
            return toResponse(existing);
        }

        CartItemEntity newEntity = CartItemEntity.builder()
                .studentId(studentId)
                .courseId(courseId)
                .selected(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        cartItemMapper.insert(newEntity);
        return toResponse(newEntity);
    }

    @Override
    @Transactional
    public void updateSelection(Long studentId, Long courseId, Boolean selected) {
        LambdaQueryWrapper<CartItemEntity> query = new LambdaQueryWrapper<CartItemEntity>()
                .eq(CartItemEntity::getStudentId, studentId)
                .eq(CartItemEntity::getCourseId, courseId);
        CartItemEntity existing = cartItemMapper.selectOne(query);
        if (existing == null) {
            throw new OrderBizException(OrderErrorCode.CART_ITEM_NOT_FOUND);
        }
        existing.setSelected(selected);
        existing.setUpdatedAt(LocalDateTime.now());
        cartItemMapper.updateById(existing);
    }

    @Override
    @Transactional
    public void removeItem(Long studentId, Long courseId) {
        LambdaQueryWrapper<CartItemEntity> query = new LambdaQueryWrapper<CartItemEntity>()
                .eq(CartItemEntity::getStudentId, studentId)
                .eq(CartItemEntity::getCourseId, courseId);
        cartItemMapper.delete(query);
    }

    @Override
    @Transactional
    public void clearCart(Long studentId, Boolean onlySelected) {
        LambdaQueryWrapper<CartItemEntity> query = new LambdaQueryWrapper<CartItemEntity>()
                .eq(CartItemEntity::getStudentId, studentId);
        if (Boolean.TRUE.equals(onlySelected)) {
            query.eq(CartItemEntity::getSelected, true);
        }
        cartItemMapper.delete(query);
    }

    @Override
    public CartSummaryResponse getCartSummary(Long studentId) {
        LambdaQueryWrapper<CartItemEntity> query = new LambdaQueryWrapper<CartItemEntity>()
                .eq(CartItemEntity::getStudentId, studentId)
                .orderByDesc(CartItemEntity::getCreatedAt);
        List<CartItemEntity> entities = cartItemMapper.selectList(query);
        if (entities == null || entities.isEmpty()) {
            return CartSummaryResponse.builder()
                    .items(List.of())
                    .totalCount(0)
                    .selectedCount(0)
                    .selectedAmount(BigDecimal.ZERO)
                    .build();
        }

        List<CartItemResponse> responses = new ArrayList<>(entities.size());
        int totalCount = entities.size();
        int selectedCount = 0;
        BigDecimal selectedAmount = BigDecimal.ZERO;

        for (CartItemEntity entity : entities) {
            CartItemResponse res = toResponse(entity);
            responses.add(res);
            if (Boolean.TRUE.equals(entity.getSelected())) {
                selectedCount++;
                if (res.getUnitPrice() != null) {
                    selectedAmount = selectedAmount.add(res.getUnitPrice());
                }
            }
        }

        return CartSummaryResponse.builder()
                .items(responses)
                .totalCount(totalCount)
                .selectedCount(selectedCount)
                .selectedAmount(selectedAmount)
                .build();
    }

    private CartItemResponse toResponse(CartItemEntity entity) {
        return CartItemResponse.builder()
                .id(entity.getId())
                .courseId(entity.getCourseId())
                .courseTitle("课程 " + entity.getCourseId())
                .coverFileId(null)
                .unitPrice(BigDecimal.ZERO)
                .selected(entity.getSelected())
                .isOnSale(true)
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
