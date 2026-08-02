package com.samhap.kokomen.resume.service.dto;

import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;

/**
 * 회원 경로에서만 채워지는 저장 자료 FK. 게스트는 member_resume.member_id NOT NULL 제약으로 행을 만들 수 없다.
 */
public record MaterialRefs(
        MemberResume memberResume,
        MemberPortfolio memberPortfolio
) {

    public static MaterialRefs empty() {
        return new MaterialRefs(null, null);
    }
}
