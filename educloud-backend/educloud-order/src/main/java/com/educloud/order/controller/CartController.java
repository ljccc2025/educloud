package com.educloud.order.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.order.dto.request.CartAddRequest;
import com.educloud.order.dto.request.CartSelectionRequest;
import com.educloud.order.dto.response.CartItemResponse;
import com.educloud.order.dto.response.CartSummaryResponse;
import com.educloud.order.security.JwtSecurityUtils;
import com.educloud.order.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final ApiResponseFactory responses;

    @GetMapping
    public ApiResponse<CartSummaryResponse> getCartSummary(@AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        return responses.success(cartService.getCartSummary(studentId));
    }

    @PostMapping("/items")
    public ApiResponse<CartItemResponse> addItem(
            @Valid @RequestBody CartAddRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        return responses.success(cartService.addItem(studentId, request));
    }

    @PutMapping("/items/{courseId}/selection")
    public ApiResponse<Void> updateSelection(
            @PathVariable Long courseId,
            @Valid @RequestBody CartSelectionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        cartService.updateSelection(studentId, courseId, request.getSelected());
        return responses.success(null);
    }

    @DeleteMapping("/items/{courseId}")
    public ApiResponse<Void> removeItem(
            @PathVariable Long courseId,
            @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        cartService.removeItem(studentId, courseId);
        return responses.success(null);
    }

    @DeleteMapping("/items")
    public ApiResponse<Void> clearCart(
            @RequestParam(value = "onlySelected", defaultValue = "false") Boolean onlySelected,
            @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        cartService.clearCart(studentId, onlySelected);
        return responses.success(null);
    }
}
