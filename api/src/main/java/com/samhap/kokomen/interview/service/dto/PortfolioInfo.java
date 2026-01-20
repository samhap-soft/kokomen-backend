package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.resume.domain.MemberPortfolio;

public record PortfolioInfo(String name, String url) {

    public static PortfolioInfo fromNullable(MemberPortfolio portfolio) {
        if (portfolio == null) {
            return null;
        }
        return new PortfolioInfo(portfolio.getTitle(), portfolio.getPortfolioUrl());
    }
}
