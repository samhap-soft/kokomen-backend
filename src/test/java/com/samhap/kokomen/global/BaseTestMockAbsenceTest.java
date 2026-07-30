package com.samhap.kokomen.global;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

// 구 평가 플로우(Task 8)·구 질문생성 플로우(Task 9)의 목 선언이 되살아나지 않는지의 회귀 가드다.
// 리팩터링 중 실수로 되돌리면(예: git revert 일부 적용) 여기서 즉시 잡힌다.
class BaseTestMockAbsenceTest {

    private static final List<String> FORBIDDEN_FIELD_NAMES = List.of(
            "resumeEvaluationBedrockClient", "resumeEvaluationGptClient",
            "resumeBasedQuestionGptClient", "resumeBasedQuestionBedrockService", "questionGenerationAsyncService");

    @Test
    void 삭제된_구_목_선언은_되살아나지_않는다() {
        List<String> fieldNames = Arrays.stream(BaseTest.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertThat(fieldNames).doesNotContainAnyElementsOf(FORBIDDEN_FIELD_NAMES);
    }
}
