package com.samhap.kokomen.global.config;

import com.samhap.kokomen.global.infrastructure.ClientIpArgumentResolver;
import com.samhap.kokomen.global.infrastructure.MemberAuthArgumentResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(
            @Value("${cors.allowed-origins}") String[] allowedOrigins
    ) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "DELETE", "PUT", "PATCH", "OPTIONS")
                .allowCredentials(true)
                .allowedHeaders("*");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.addAll(List.of(
                new MemberAuthArgumentResolver(),
                new ClientIpArgumentResolver()
        ));
    }

    /**
     * Redisson이 끌어오는 jackson-dataformat-yaml 2.x 때문에 Spring이 기본 변환기로 Jackson 2 YAML 변환기를 넣는다. 이 변환기는 Spring
     * 기본 설정의 매퍼라 전부터 앱 매퍼 설정(SNAKE_CASE)을 쓰지 않았고, Jackson 3 전환 뒤에는 DTO의 Jackson 3 애너테이션과
     * GlobalExceptionHandler의 Jackson 3 예외 분기도 보지 못한다. API는 JSON만 주고받으므로 MVC에서 뺀다. 관리 서버(actuator) 자식
     * 컨텍스트도 이 설정을 받는다. YAML 본문 요청은 415이고, YAML만 받겠다는 요청은 application/xml만 받겠다는 요청처럼 본문 없이
     * 끝난다(정상 결과는 406. Jackson3ChangedHttpBehaviorTest, NonJsonAcceptServerResponseTest). 폼 변환기가 multipart를 쓸 때 고르는
     * 부품 변환기에서도 뺀다. JSON 변환기와 그 순서는 건드리지 않는다(SessionAndJsonWiringTest).
     */
    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(converters -> {
            converters.removeIf(WebConfig::supportsYaml);
            converters.stream()
                    .filter(FormHttpMessageConverter.class::isInstance)
                    .map(FormHttpMessageConverter.class::cast)
                    .forEach(formConverter -> formConverter.setPartConverters(formConverter.getPartConverters().stream()
                            .filter(partConverter -> !supportsYaml(partConverter))
                            .collect(Collectors.toCollection(ArrayList::new))));
        });
    }

    private static boolean supportsYaml(HttpMessageConverter<?> converter) {
        return converter.getSupportedMediaTypes().stream().anyMatch(MediaType.APPLICATION_YAML::equalsTypeAndSubtype);
    }
}
