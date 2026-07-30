package com.samhap.kokomen.resume.service.dto;

import com.samhap.kokomen.global.dto.ClientIp;

/**
 * 게스트 제출의 소유 식별 정보. guestToken은 소유 증명 토큰, guestLockValue는 IP 락 해제용 별개 UUID다(§7-5).
 */
public record GuestInfo(
        String guestToken,
        ClientIp clientIp,
        String guestLockValue
) {

    public static GuestInfo none() {
        return new GuestInfo(null, null, null);
    }
}
