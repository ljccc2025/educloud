package com.educloud.order.service;

import com.educloud.order.dto.request.CartAddRequest;
import com.educloud.order.dto.response.CartItemResponse;
import com.educloud.order.dto.response.CartSummaryResponse;

public interface CartService {

    CartItemResponse addItem(Long studentId, CartAddRequest request);

    void updateSelection(Long studentId, Long courseId, Boolean selected);

    void removeItem(Long studentId, Long courseId);

    void clearCart(Long studentId, Boolean onlySelected);

    CartSummaryResponse getCartSummary(Long studentId);
}
