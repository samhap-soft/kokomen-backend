package com.samhap.kokomen.global.external.gpt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GptProperties.class)
public class GptConfig {
}
