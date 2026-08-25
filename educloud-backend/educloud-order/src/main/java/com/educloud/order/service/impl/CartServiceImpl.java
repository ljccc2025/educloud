package com.educloud.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.api.ApiResponse;
import com.educloud.order.dto.request.CartAddRequest;
import com.educloud.order.dto.response.CartItemResponse;
import com.educloud.order.dto.response.CartSummaryResponse;
import com.educloud.order.entity.CartItemEntity;
import com.educloud.order.exception.OrderBizException;
import com.educloud.order.exception.OrderErrorCode;
import com.educloud.order.feign.CourseClient;
import com.educloud.order.feign.dto.CourseSalesSnapshotDto;
import com.educloud.order.mapper.CartItemMapper;
import com.educloud.order.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartItemMapper cartItemMapper;
    private final CourseClient courseClient;

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
            return toResponse(existing, fetchCourseSnapshot(existing.getCourseId()));
        }

        CartItemEntity newEntity = CartItemEntity.builder()
                .studentId(studentId)
                .courseId(courseId)
                .selected(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        cartItemMapper.insert(newEntity);
        return toResponse(newEntity, fetchCourseSnapshot(newEntity.getCourseId()));
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

        // BUG-020 修复：逐项拉取课程销售快照（title/封面/价格/上架状态），
        // 替代硬编码假数据；快照获取失败或已下架的课程 isOnSale=false 且
        // 不计入 selectedAmount（fail-closed，购物车条目量小，循环单查可接受）。
        List<CartItemResponse> responses = new ArrayList<>(entities.size());
        int totalCount = entities.size();
        int selectedCount = 0;
        BigDecimal selectedAmount = BigDecimal.ZERO;

        for (CartItemEntity entity : entities) {
            CourseSalesSnapshotDto snapshot = fetchCourseSnapshot(entity.getCourseId());
            CartItemResponse res = toResponse(entity, snapshot);
            responses.add(res);
            if (Boolean.TRUE.equals(entity.getSelected())) {
                selectedCount++;
                if (Boolean.TRUE.equals(res.getIsOnSale()) && res.getUnitPrice() != null) {
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

    private CourseSalesSnapshotDto fetchCourseSnapshot(Long courseId) {
        try {
            ApiResponse<CourseSalesSnapshotDto> response = courseClient.getCourseDetail(courseId);
            return (response != null) ? response.data() : null;
        } catch (Exception ex) {
            log.warn("Failed to fetch course snapshot for cart item, marking not on sale: courseId={}", courseId, ex);
            return null;
        }
    }

    private CartItemResponse toResponse(CartItemEntity entity, CourseSalesSnapshotDto snapshot) {
        boolean onSale = snapshot != null && snapshot.isPurchasable();
        BigDecimal price = (onSale && snapshot.getPrice() != null) ? snapshot.getPrice() : BigDecimal.ZERO;
        String title = (snapshot != null && snapshot.getTitle() != null)
                ? snapshot.getTitle() : ("课程 " + entity.getCourseId());
        return CartItemResponse.builder()
                .id(entity.getId())
                .courseId(entity.getCourseId())
                .courseTitle(title)
                .coverFileId(snapshot != null ? snapshot.getCoverFileId() : null)
                .unitPrice(price)
                .selected(entity.getSelected())
                .isOnSale(onSale)
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
