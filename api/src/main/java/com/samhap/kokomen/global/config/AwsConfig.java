package com.samhap.kokomen.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsConfig {

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .credentialsProvider(InstanceProfileCredentialsProvider.create())
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(60)
                        .connectionAcquisitionTimeout(java.time.Duration.ofSeconds(5))
                        .connectionTimeout(java.time.Duration.ofSeconds(3))
                        .socketTimeout(java.time.Duration.ofSeconds(15))
                )
                .region(Region.AP_NORTHEAST_2)
                .build();
    }

    @Bean
    public BedrockAgentRuntimeAsyncClient bedrockAgentRuntimeClient() {
        return BedrockAgentRuntimeAsyncClient.builder()
                .credentialsProvider(InstanceProfileCredentialsProvider.create())
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        .maxConcurrency(1000)
                        .connectionAcquisitionTimeout(java.time.Duration.ofSeconds(10))
                        .connectionTimeout(java.time.Duration.ofSeconds(3))
                        .readTimeout(java.time.Duration.ofSeconds(180))
                )
                .region(Region.AP_NORTHEAST_2)
                .build();
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
