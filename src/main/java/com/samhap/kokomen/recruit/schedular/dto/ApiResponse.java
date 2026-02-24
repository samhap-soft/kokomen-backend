package com.samhap.kokomen.recruit.schedular.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ApiResponse<T> {
    private String timestamp;
    private Boolean success;
    private T data;
    private String code;
    private String message;
}
