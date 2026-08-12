package com.samhap.kokomen.member.domain.survey;

import com.samhap.kokomen.global.persistence.EnumListJsonConverter;
import jakarta.persistence.Converter;

@Converter
public class PrepStageListJsonConverter extends EnumListJsonConverter<PrepStage> {

    public PrepStageListJsonConverter() {
        super(PrepStage.class);
    }
}
