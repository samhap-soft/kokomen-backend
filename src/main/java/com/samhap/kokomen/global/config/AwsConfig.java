package com.samhap.kokomen.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.resume.external.ResumeAnalysisBedrockTimeouts;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(BedrockConverseProperties.class)
public class AwsConfig {

    @Primary
    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .credentialsProvider(InstanceProfileCredentialsProvider.create())
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
                .credentialsProvider(InstanceProfileCredentialsProvider.create())
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

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                // AWS 자격증명: 환경변수, ~/.aws/credentials, EC2 IAM Role 등 기본 제공 방식 사용
                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.AP_NORTHEAST_2)
                .build();
    }
}
