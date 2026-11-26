package com.samhap.kokomen.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.samhap.kokomen.global.BaseTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/**
 * Boot 4에서 자동 설정이 모듈별로 나뉘면서, 의존성이 빠져도 컨텍스트는 그대로 뜨고 테스트도 통과하는 배선을 고정한다.
 * <ul>
 *     <li>세션: spring-boot-starter-session-data-redis가 없으면 Spring Session이 꺼지고 Tomcat 메모리 세션으로 돈다.
 *     MockMvc 테스트는 그래도 통과하지만, 배포하면 기존 로그인이 모두 풀리고 블루그린 인스턴스끼리 세션을 공유하지 못한다.</li>
 *     <li>JSON: Boot 4 기본 매퍼는 Jackson 3다. 우리 코드와 API 응답은 Jackson 2 ObjectMapper(SNAKE_CASE, non_null)를 쓰므로
 *     spring.jackson2.* 설정과 MVC 변환기 선택(preferred-json-mapper: jackson2)이 그대로인지 본다.</li>
 * </ul>
 */
class SessionAndJsonWiringTest extends BaseTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 세션은_Redis_저장소를_쓰는_Spring_Session_필터로_처리한다() {
        assertThat(context.getBeansOfType(SessionRepositoryFilter.class)).isNotEmpty();
        assertThat(context.getBeansOfType(RedisSessionRepository.class)).isNotEmpty();
    }

    @Test
    void 주입받는_Jackson2_ObjectMapper는_운영과_같은_명명_규칙과_null_생략을_쓴다() {
        assertThat(objectMapper.getPropertyNamingStrategy()).isSameAs(PropertyNamingStrategies.SNAKE_CASE);
        assertThat(objectMapper.getSerializationConfig().getDefaultPropertyInclusion().getValueInclusion())
                .isEqualTo(JsonInclude.Include.NON_NULL);
    }

    @Test
    void MVC_JSON_변환기는_주입받는_Jackson2_ObjectMapper를_쓴다() {
        List<HttpMessageConverter<?>> converters = context.getBean(RequestMappingHandlerAdapter.class).getMessageConverters();

        assertThat(converters)
                .filteredOn(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(converter -> ((MappingJackson2HttpMessageConverter) converter).getObjectMapper())
                .isNotEmpty()
                .allSatisfy(mapper -> assertThat(mapper).isSameAs(objectMapper));
    }
}
