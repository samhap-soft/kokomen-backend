package com.samhap.kokomen.interview.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.category.domain.Category;
import org.junit.jupiter.api.Test;

class RootQuestionTest {

    @Test
    void 코드_타입_루트_질문의_초기_질문_내용은_제목과_본문을_합친다() {
        RootQuestion rootQuestion = RootQuestion.forCode(Category.ALGORITHM_DATA_STRUCTURE, "Two Sum",
                "정수 배열에서 두 수의 합이 target이 되는 인덱스를 반환하세요.");

        String initialQuestionContent = rootQuestion.createInitialQuestionContent();

        assertThat(initialQuestionContent)
                .isEqualTo("Two Sum\n\n정수 배열에서 두 수의 합이 target이 되는 인덱스를 반환하세요.");
    }

    @Test
    void 일반_타입_루트_질문의_초기_질문_내용은_본문_그대로이다() {
        RootQuestion rootQuestion = new RootQuestion(Category.OPERATING_SYSTEM, "프로세스와 스레드 차이 설명해주세요.");

        String initialQuestionContent = rootQuestion.createInitialQuestionContent();

        assertThat(initialQuestionContent).isEqualTo("프로세스와 스레드 차이 설명해주세요.");
    }
}
