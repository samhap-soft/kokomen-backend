package com.samhap.kokomen.resume.external;

import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;

/**
 * 신규 이력서 분석 GPT 폴백 전용 타임아웃. BaseGptClient에는 ClientHttpRequestFactory 설정이 없어
 * 타임아웃이 무제한이며, 워커가 무한 대기하면 sweep이 먼저 실패를 찍는다.
 * BaseGptClient는 InterviewProceedGptClient도 상속하므로 거기에 타임아웃을 걸면 면접 진행 호출의 동작까지
 * 바뀐다. 그래서 이력서 분석 클라이언트 생성자에서만 적용한다.
 * clone()으로 복제해 주입받은 빌더 원본을 변형하지 않는다.
 */
public final class ResumeAnalysisGptTimeouts {

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(90);

    private ResumeAnalysisGptTimeouts() {
    }

    public static RestClient.Builder apply(RestClient.Builder builder) {
        return builder.clone()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(ClientHttpRequestFactorySettings.defaults()
                                .withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT)));
    }
}
