package com.samhap.kokomen.auth.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GoogleTokenResponse(
        @JsonProperty("access_token")
        String accessToken,
        
        @JsonProperty("token_type")
        String tokenType,
        
        @JsonProperty("expires_in")
        Integer expiresIn,
        
        @JsonProperty("refresh_token")
        String refreshToken,
        
        String scope
) {
}