package com.samhap.kokomen.global.fixture.resume;

import com.samhap.kokomen.interview.external.dto.response.GeneratedQuestionDto;
import com.samhap.kokomen.resume.external.dto.ResumeAnalysisQuestionResult;
import java.util.List;
import java.util.stream.IntStream;

public final class ResumeAnalysisQuestionResultFixture {

    private ResumeAnalysisQuestionResultFixture() {
    }

    public static ResumeAnalysisQuestionResult five() {
        return of(5);
    }

    public static ResumeAnalysisQuestionResult of(int count) {
        List<GeneratedQuestionDto> questions = IntStream.rangeClosed(1, count)
                .mapToObj(i -> new GeneratedQuestionDto("질문 " + i, "이유 " + i))
                .toList();
        return new ResumeAnalysisQuestionResult(questions);
    }
}
