package com.samhap.kokomen.global.fixture.recruit;

import com.samhap.kokomen.recruit.domain.Affiliate;

public class AffiliateFixtureBuilder {

    private Long id;
    private String name;
    private String image;

    public static AffiliateFixtureBuilder builder() {
        return new AffiliateFixtureBuilder();
    }

    public AffiliateFixtureBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public AffiliateFixtureBuilder name(String name) {
        this.name = name;
        return this;
    }

    public AffiliateFixtureBuilder image(String image) {
        this.image = image;
        return this;
    }

    public Affiliate build() {
        return new Affiliate(
                id,
                name != null ? name : "사람인",
                image != null ? image : "https://example.com/saramin.png"
        );
    }
}
