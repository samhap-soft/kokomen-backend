package com.samhap.kokomen.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.resume.tool.PdfTextExtractor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class BaseTestMockRegistrationTest extends BaseTest {

    @Test
    void 이력서_분석_LLM_클라이언트_2개가_목으로_등록된다() {
        assertThat(Mockito.mockingDetails(resumeAnalysisEvaluationBedrockClient).isMock()).isTrue();
        assertThat(Mockito.mockingDetails(resumeAnalysisQuestionBedrockClient).isMock()).isTrue();
    }

    // 비동기 워커 목은 BaseTest에 단일 선언되어 있어야 한다. 하위 클래스가 같은 타입을 다시 선언하면
    // Spring이 중복 오버라이드를 거부해 그 클래스의 컨텍스트 기동이 실패한다.
    @Test
    void 이력서_분석_비동기_서비스가_목으로_등록된다() {
        assertThat(Mockito.mockingDetails(resumeAnalysisAsyncService).isMock()).isTrue();
    }

    // 외부 연동 목 7개가 그대로 유지되고 있는지의 게이트다. 목을 정리하면서 존치 대상까지 함께 지우면 여기서 잡힌다.
    @Test
    void 존치_목_선언은_그대로_유지된다() {
        assertAll(
                () -> assertThat(Mockito.mockingDetails(supertoneClient).isMock()).isTrue(),
                () -> assertThat(Mockito.mockingDetails(s3Client).isMock()).isTrue(),
                () -> assertThat(Mockito.mockingDetails(tosspaymentsClient).isMock()).isTrue(),
                () -> assertThat(Mockito.mockingDetails(interviewProceedBedrockClient).isMock()).isTrue(),
                () -> assertThat(Mockito.mockingDetails(answerFeedbackBedrockClient).isMock()).isTrue(),
                () -> assertThat(Mockito.mockingDetails(kakaoOAuthClient).isMock()).isTrue(),
                () -> assertThat(Mockito.mockingDetails(googleOAuthClient).isMock()).isTrue()
        );
    }

    // PdfValidator/PdfTextExtractor는 BaseTest에 승격된 공용 목이다. 하위 클래스의 로컬 선언으로 되돌리면
    // 컨텍스트 캐시 키가 갈라져 컨텍스트 fork가 늘어난다.
    @Test
    void PDF_도구_2종은_BaseTest에서_목으로_등록된다() {
        assertAll(
                () -> assertThat(Mockito.mockingDetails(pdfValidator).isMock()).isTrue(),
                () -> assertThat(Mockito.mockingDetails(pdfTextExtractor).isMock()).isTrue()
        );
    }

    // 목 총수를 리플렉션으로 세어 정본과 대조한다. 목이 조용히 늘거나 줄면 여기서 잡힌다.
    @Test
    void BaseTest의_목_개수는_정본과_일치한다() {
        long mocks = Arrays.stream(BaseTest.class.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(MockitoBean.class))
                .count();
        long spies = Arrays.stream(BaseTest.class.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(MockitoSpyBean.class))
                .count();

        assertAll(
                () -> assertThat(mocks).isEqualTo(12),
                () -> assertThat(spies).isEqualTo(2)
        );
    }

    // BaseTest의 pdfTextExtractor는 목이므로 스텁하지 않으면 무엇을 넣어도 null을 반환한다. 실 구현을 거치지 않고
    // 항상 통과해 버리므로, 이 테스트만 예외적으로 실 인스턴스를 직접 만들어 실제 동작을 검증한다.
    @Test
    void extractTextWithLinks는_빈_파일에_null을_반환한다() {
        PdfTextExtractor realExtractor = new PdfTextExtractor();

        String extracted = realExtractor.extractTextWithLinks(
                new MockMultipartFile("resume", "resume.pdf", "application/pdf", new byte[0]));

        assertThat(extracted).isNull();
    }
}
