package com.samhap.kokomen.global.fixture.token;

import com.samhap.kokomen.token.domain.Token;
import com.samhap.kokomen.token.domain.TokenType;

public class TokenFixtureBuilder {

    private Long id;
    private Long memberId;
    private TokenType type;
    private Integer tokenCount;

    public static TokenFixtureBuilder builder() {
        return new TokenFixtureBuilder();
    }

    public TokenFixtureBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public TokenFixtureBuilder memberId(Long memberId) {
        this.memberId = memberId;
        return this;
    }

    public TokenFixtureBuilder type(TokenType type) {
        this.type = type;
        return this;
    }

    public TokenFixtureBuilder tokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
        return this;
    }

    public Token build() {
        return new Token(
                memberId != null ? memberId : 1L,
                type != null ? type : TokenType.FREE,
                tokenCount != null ? tokenCount : 20
        );
    }
}
