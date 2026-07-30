package com.samhap.kokomen.global;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

// 구 평가 플로우(Task 8)의 목 선언이 되살아나지 않는지의 회귀 가드다.
// 리팩터링 중 실수로 되돌리면(예: git revert 일부 적용) 여기서 즉시 잡힌다.
//
// 구 질문생성 플로우(resumeBasedQuestionGptClient, resumeBasedQuestionBedrockService,
// questionGenerationAsyncService)는 Task 9가 삭제할 때까지 BaseTest에 의도적으로 남아 있으므로
// 아직 이 목록에 넣지 않는다 — 넣으면 Task 9 전까지 이 테스트가 항상 RED가 된다. Task 9가 그 3개를
// BaseTest에서 삭제하면서 이 목록에도 추가해야 최종 게이트가 완성된다.
class BaseTestMockAbsenceTest {

    private static final List<String> FORBIDDEN_FIELD_NAMES = List.of(
            "resumeEvaluationBedrockClient", "resumeEvaluationGptClient");

    @Test
    void 삭제된_구_목_선언은_되살아나지_않는다() {
        List<String> fieldNames = Arrays.stream(BaseTest.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertThat(fieldNames).doesNotContainAnyElementsOf(FORBIDDEN_FIELD_NAMES);
    }
}
