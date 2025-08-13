package com.samhap.kokomen.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

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
                        .readTimeout(java.time.Duration.ofSeconds(20))
                )
                .region(Region.AP_NORTHEAST_2)
                .build();
    }
}
