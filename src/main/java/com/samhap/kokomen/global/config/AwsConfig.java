package com.samhap.kokomen.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.interview.external.SystemDesignAnswerBedrockTimeouts;
import com.samhap.kokomen.interview.external.SystemDesignBedrockTimeouts;
import com.samhap.kokomen.resume.external.ResumeAnalysisBedrockTimeouts;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(BedrockConverseProperties.class)
public class AwsConfig {

    /**
     * 자격증명은 DefaultCredentialsProvider로 받는다.
     * InstanceProfileCredentialsProvider는 EC2 IMDS만 조회하므로, IMDS가 없는 환경(EC2 밖)에서는
     * 환경변수로 키를 넣어도 무시되고 무조건 실패한다. 기본 체인은
     * 시스템 프로퍼티 -> 환경변수 -> 프로파일 -> 컨테이너 -> IMDS 순으로 찾으므로
     * EC2에서는 종전과 동일하게 IAM 역할로 동작하고, 그 밖에서는 환경변수를 쓸 수 있다.
     * S3 클라이언트가 이미 쓰던 방식과 같다.
     */
    @Primary
    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(60)
                        .connectionAcquisitionTimeout(java.time.Duration.ofSeconds(5))
                        .connectionTimeout(java.time.Duration.ofSeconds(3))
                        .socketTimeout(java.time.Duration.ofSeconds(60))
                )
                .region(Region.US_EAST_1)
                .build();
    }

    /**
     * 이력서 분석 전용 클라이언트. 평가 콜이 최대 16,000 토큰을 생성해 약 258초가 걸리므로 공용 빈의
     * 소켓 타임아웃 60초로는 완주할 수 없다. 공용 빈을 올리면 인터뷰 호출도 장애 시 6분을 붙잡게 되어
     * 빠른 실패 특성을 잃으므로 분리한다. maxConnections 60은 resumeAnalysisExecutor의
     * corePoolSize·maxPoolSize와 같은 값이며, 인터뷰 풀과 분리되어야 서로 고갈시키지 않는다.
     */
    @Bean
    public BedrockRuntimeClient resumeAnalysisBedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(ResumeAnalysisBedrockTimeouts.MAX_CONNECTIONS)
                        .connectionAcquisitionTimeout(java.time.Duration.ofSeconds(5))
                        .connectionTimeout(ResumeAnalysisBedrockTimeouts.CONNECT_TIMEOUT)
                        .socketTimeout(ResumeAnalysisBedrockTimeouts.SOCKET_TIMEOUT)
                )
                .overrideConfiguration(override -> override
                        .apiCallTimeout(ResumeAnalysisBedrockTimeouts.API_CALL_TIMEOUT)
                        .retryStrategy(ResumeAnalysisBedrockTimeouts.retryStrategy()))
                .region(Region.US_EAST_1)
                .build();
    }

    @Bean
    public BedrockConverseClient resumeAnalysisBedrockConverseClient(
            @Qualifier("resumeAnalysisBedrockRuntimeClient") BedrockRuntimeClient resumeAnalysisBedrockRuntimeClient,
            BedrockConverseProperties properties,
            ObjectMapper objectMapper
    ) {
        return new BedrockConverseClient(resumeAnalysisBedrockRuntimeClient, properties, objectMapper);
    }

    /**
     * 시스템 설계 역질문 답 전용 클라이언트. 역질문 답은 HTTP 요청 스레드에서 동기로 부르므로 공용 빈의 소켓 타임아웃 60초와
     * SDK 기본 재시도를 그대로 쓰면 장애 때 요청 하나가 Tomcat 스레드를 분 단위로 붙잡는다. 짧은 타임아웃과 좁은 재시도로 분리한다.
     * maxConnections는 동시 실행 상한(SystemDesignLlmBulkhead)과 같다. 리전과 모델은 공용 빈과 같다.
     */
    @Bean
    public BedrockRuntimeClient systemDesignBedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(SystemDesignBedrockTimeouts.MAX_CONNECTIONS)
                        .connectionAcquisitionTimeout(java.time.Duration.ofSeconds(5))
                        .connectionTimeout(SystemDesignBedrockTimeouts.CONNECT_TIMEOUT)
                        .socketTimeout(SystemDesignBedrockTimeouts.SOCKET_TIMEOUT)
                )
                .overrideConfiguration(override -> override
                        .apiCallTimeout(SystemDesignBedrockTimeouts.API_CALL_TIMEOUT)
                        .retryStrategy(SystemDesignBedrockTimeouts.retryStrategy()))
                .region(Region.US_EAST_1)
                .build();
    }

    @Bean
    public BedrockConverseClient systemDesignBedrockConverseClient(
            @Qualifier("systemDesignBedrockRuntimeClient") BedrockRuntimeClient systemDesignBedrockRuntimeClient,
            BedrockConverseProperties properties,
            ObjectMapper objectMapper
    ) {
        return new BedrockConverseClient(systemDesignBedrockRuntimeClient, properties, objectMapper);
    }

    /**
     * 시스템 설계 답변 채점(L2·L3) 전용 클라이언트. 채점은 비동기 워커에서 부르지만, 공용 빈의 소켓 60초와 SDK 기본 재시도(최대 4회)를
     * 그대로 쓰면 생성이 60초를 넘을 때 같은 채점을 네 번 보내 네 번 과금하고 폴링 한도를 넘긴다. 긴 소켓 타임아웃, 전체 호출 상한, 좁은
     * 재시도로 분리한다(SystemDesignAnswerBedrockTimeouts). maxConnections는 채점 실행기 스레드 수와 같다. 리전과 모델은 공용 빈과 같다.
     */
    @Bean
    public BedrockRuntimeClient systemDesignAnswerBedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(SystemDesignAnswerBedrockTimeouts.MAX_CONNECTIONS)
                        .connectionAcquisitionTimeout(java.time.Duration.ofSeconds(5))
                        .connectionTimeout(SystemDesignAnswerBedrockTimeouts.CONNECT_TIMEOUT)
                        .socketTimeout(SystemDesignAnswerBedrockTimeouts.SOCKET_TIMEOUT)
                )
                .overrideConfiguration(override -> override
                        .apiCallTimeout(SystemDesignAnswerBedrockTimeouts.API_CALL_TIMEOUT)
                        .retryStrategy(SystemDesignAnswerBedrockTimeouts.retryStrategy()))
                .region(Region.US_EAST_1)
                .build();
    }

    @Bean
    public BedrockConverseClient systemDesignAnswerBedrockConverseClient(
            @Qualifier("systemDesignAnswerBedrockRuntimeClient")
            BedrockRuntimeClient systemDesignAnswerBedrockRuntimeClient,
            BedrockConverseProperties properties,
            ObjectMapper objectMapper
    ) {
        return new BedrockConverseClient(systemDesignAnswerBedrockRuntimeClient, properties, objectMapper);
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                // AWS 자격증명: 환경변수, ~/.aws/credentials, EC2 IAM Role 등 기본 제공 방식 사용
                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.AP_NORTHEAST_2)
                .build();
    }
}
