package com.samhap.kokomen.global.fixture.recruit;

import com.samhap.kokomen.recruit.domain.Company;

public class CompanyFixtureBuilder {

    private String id;
    private String name;
    private String image;

    public static CompanyFixtureBuilder builder() {
        return new CompanyFixtureBuilder();
    }

    public CompanyFixtureBuilder id(String id) {
        this.id = id;
        return this;
    }

    public CompanyFixtureBuilder name(String name) {
        this.name = name;
        return this;
    }

    public CompanyFixtureBuilder image(String image) {
        this.image = image;
        return this;
    }

    public Company build() {
        return new Company(
                id != null ? id : "company-1",
                name != null ? name : "삼합소프트",
                image != null ? image : "https://example.com/company.png"
        );
    }
}
