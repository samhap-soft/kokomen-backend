package com.samhap.kokomen.recruit.schedular.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CompanyDto {
    private String id;
    private String name;
    private String image;

    @Override
    public String toString() {
        return "CompanyDto{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", image='" + image + '\'' +
                '}';
    }
}
