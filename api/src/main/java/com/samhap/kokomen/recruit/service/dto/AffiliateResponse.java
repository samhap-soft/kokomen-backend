package com.samhap.kokomen.recruit.service.dto;

import com.samhap.kokomen.recruit.domain.Affiliate;

public record AffiliateResponse(
        String name,
        String image
) {
    public static AffiliateResponse from(Affiliate affiliate) {
        return new AffiliateResponse(
                affiliate.getName(),
                affiliate.getImage()
        );
    }
}
