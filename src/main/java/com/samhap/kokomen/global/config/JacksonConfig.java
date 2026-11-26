package com.samhap.kokomen.global.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.samhap.kokomen.global.infrastructure.Jackson2CompatibleJsonMapper;
import org.springframework.boot.actuate.endpoint.jackson.EndpointJsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Boot가 만드는 Jackson 3 매퍼를 Jackson 2(Boot 3) 때와 같게 맞추는 설정 중 application.yml로 할 수 없는 것을 둔다.
 */
@Configuration
public class JacksonConfig {

    /**
     * 앱 매퍼에도 코드에서 만드는 매퍼와 같은 문자열 컬렉션 읽기 보정을 건다. Boot는 JacksonModule 빈을 앱 매퍼에 등록한다.
     */
    @Bean
    public JacksonModule jackson2StringCollectionModule() {
        return Jackson2CompatibleJsonMapper.stringCollectionModule();
    }

    /**
     * 관리 포트 actuator JSON을 쓰는 매퍼다. Boot가 따로 만드는 actuator 매퍼는 Jackson 3 기본값이라 키를 알파벳 순으로 쓴다.
     * Jackson 2 때 Boot가 만들던 actuator 매퍼(Jackson2ObjectMapperBuilder: 모르는 필드 무시, 뷰 기본 포함 끔, 날짜·기간은 문자열,
     * non_null)와 같게 만들고,
     * Boot의 매퍼는 끈다(management.endpoints.jackson.isolated-json-mapper: false).
     */
    @Bean
    public EndpointJsonMapper jackson2CompatibleEndpointJsonMapper() {
        JsonMapper jsonMapper = Jackson2CompatibleJsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .changeDefaultPropertyInclusion(inclusion ->
                        JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
                .build();
        return () -> jsonMapper;
    }
}
