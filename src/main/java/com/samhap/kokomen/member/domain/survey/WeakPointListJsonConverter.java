package com.samhap.kokomen.member.domain.survey;

import com.samhap.kokomen.global.persistence.EnumListJsonConverter;
import jakarta.persistence.Converter;

@Converter
public class WeakPointListJsonConverter extends EnumListJsonConverter<WeakPoint> {

    public WeakPointListJsonConverter() {
        super(WeakPoint.class);
    }
}
