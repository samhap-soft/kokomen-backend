package com.samhap.kokomen.admin.service;

import com.samhap.kokomen.admin.service.dto.RootQuestionVoiceResponse;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.interview.service.RootQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AdminService {

    private final RootQuestionService rootQuestionService;

    public RootQuestionVoiceResponse uploadRootQuestionVoice(Long rootQuestionId) {
        if (rootQuestionService.isRootQuestionVoiceExists(rootQuestionId)) {
            throw new BadRequestException("이미 S3에 올라가있는 음성파일입니다. rootQuestionId = " + rootQuestionId);
        }
        String rootQuestionVoiceCdnUrl = rootQuestionService.createAndUploadRootQuestionVoice(rootQuestionId);
        return new RootQuestionVoiceResponse(rootQuestionVoiceCdnUrl);
    }
}
