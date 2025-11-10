package com.hanghae.ecommerce.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    private List<Long> cartItemIds;
    private ShippingAddress shippingAddress;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingAddress {
        private String recipientName;
        private String phone;
        private String zipCode;
        private String address;
        private String detailAddress;
    }
}
