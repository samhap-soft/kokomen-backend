package com.samhap.kokomen.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.infrastructure.Jackson2CompatibleJsonMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.jackson.EndpointJsonMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Boot 4에서 자동 설정이 모듈별로 나뉘면서, 의존성이 빠져도 컨텍스트는 그대로 뜨고 테스트도 통과하는 배선을 고정한다.
 * <ul>
 *     <li>세션: spring-boot-starter-session-data-redis가 없으면 Spring Session이 꺼지고 Tomcat 메모리 세션으로 돈다.
 *     MockMvc 테스트는 그래도 통과하지만, 배포하면 기존 로그인이 모두 풀리고 블루그린 인스턴스끼리 세션을 공유하지 못한다.</li>
 *     <li>JSON: 우리 코드와 API 응답은 Boot가 만드는 Jackson 3 JsonMapper(SNAKE_CASE, non_null)를 쓴다. spring.jackson.* 설정과
 *     MVC JSON 변환기가 그 매퍼를 쓰는지 본다. 클래스패스에는 logback·Redisson이 쓰는 Jackson 2가 남아 있으므로, JSON을 쓰는 변환기가
 *     Jackson 2로 되돌아가지 않는지도 본다.</li>
 *     <li>Jackson 2 호환 설정: Boot 매퍼(application.yml)와 코드에서 만드는 매퍼(Jackson2CompatibleJsonMapper)가 Jackson 3 기본값이
 *     다른 설정을 Jackson 2 값으로 쓰는지, 둘이 Jackson 2 때처럼 Boot가 바꾸는 설정만 다른지 본다.</li>
 * </ul>
 */
class SessionAndJsonWiringTest extends BaseTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void 세션은_Redis_저장소를_쓰는_Spring_Session_필터로_처리한다() {
        assertThat(context.getBeansOfType(SessionRepositoryFilter.class)).isNotEmpty();
        assertThat(context.getBeansOfType(RedisSessionRepository.class)).isNotEmpty();
    }

    @Test
    void 주입받는_JsonMapper는_운영과_같은_명명_규칙과_null_생략을_쓴다() {
        assertThat(jsonMapper.serializationConfig().getPropertyNamingStrategy())
                .isSameAs(PropertyNamingStrategies.SNAKE_CASE);
        assertThat(jsonMapper.serializationConfig().getDefaultPropertyInclusion().getValueInclusion())
                .isEqualTo(JsonInclude.Include.NON_NULL);
    }

    @Test
    void MVC_JSON_변환기는_주입받는_JsonMapper를_쓴다() {
        List<HttpMessageConverter<?>> converters = context.getBean(RequestMappingHandlerAdapter.class).getMessageConverters();

        assertThat(converters)
                .filteredOn(converter -> converter.canWrite(Object.class, MediaType.APPLICATION_JSON))
                .singleElement()
                .isInstanceOf(JacksonJsonHttpMessageConverter.class);
        assertThat(converters)
                .filteredOn(JacksonJsonHttpMessageConverter.class::isInstance)
                .map(converter -> ((JacksonJsonHttpMessageConverter) converter).getMapper())
                .isNotEmpty()
                .allSatisfy(mapper -> assertThat(mapper).isSameAs(jsonMapper));
    }

    /**
     * 기능 플래그 가운데 Jackson 2와 Jackson 3의 기본값이 다른 것만 모아, develop의 Jackson 2 값(Jackson 2.21.8에서 확인)을 적는다.
     * 앱 매퍼와 코드에서 만드는 매퍼가 모두 이 값을 쓴다. Jackson 2에서도 두 매퍼가 같았던 값이다.
     * 출력·입력 바이트가 Jackson 2와 같은지는 global.json의 특성 테스트가 따로 본다.
     */
    private static final Map<String, Boolean> JACKSON2_VALUES_WHERE_JACKSON3_DEFAULT_DIFFERS = Map.ofEntries(
            Map.entry("MapperFeature.SORT_PROPERTIES_ALPHABETICALLY", false),
            Map.entry("MapperFeature.FIX_FIELD_NAME_UPPER_CASE_PREFIX", false),
            Map.entry("MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS", true),
            Map.entry("MapperFeature.USE_GETTERS_AS_SETTERS", true),
            Map.entry("MapperFeature.ALLOW_VOID_VALUED_PROPERTIES", false),
            Map.entry("DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES", false),
            Map.entry("DeserializationFeature.FAIL_ON_TRAILING_TOKENS", false),
            Map.entry("SerializationFeature.FAIL_ON_EMPTY_BEANS", true),
            Map.entry("SerializationFeature.FAIL_ON_ORDER_MAP_BY_INCOMPARABLE_KEY", true),
            Map.entry("EnumFeature.READ_ENUMS_USING_TO_STRING", false),
            Map.entry("EnumFeature.WRITE_ENUMS_USING_TO_STRING", false),
            Map.entry("JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES", true),
            Map.entry("StreamReadFeature.USE_FAST_DOUBLE_PARSER", false),
            Map.entry("StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER", false),
            Map.entry("JsonWriteFeature.COMBINE_UNICODE_SURROGATES_IN_UTF8", false),
            Map.entry("JsonWriteFeature.ESCAPE_FORWARD_SLASHES", false));

    @Test
    void 주입받는_JsonMapper와_코드에서_만드는_매퍼는_Jackson_3_기본값이_다른_설정을_Jackson_2_값으로_쓴다() {
        JsonMapper codeBuiltMapper = Jackson2CompatibleJsonMapper.builder().build();

        assertThat(features(jsonMapper)).containsAllEntriesOf(JACKSON2_VALUES_WHERE_JACKSON3_DEFAULT_DIFFERS);
        assertThat(features(codeBuiltMapper)).containsAllEntriesOf(JACKSON2_VALUES_WHERE_JACKSON3_DEFAULT_DIFFERS);
        assertThat(constraints(jsonMapper)).isEqualTo(constraints(codeBuiltMapper)).containsExactly(
                Jackson2CompatibleJsonMapper.MAX_NESTING_DEPTH, Jackson2CompatibleJsonMapper.MAX_STRING_LENGTH,
                Jackson2CompatibleJsonMapper.MAX_NESTING_DEPTH);
        assertThat(moduleNames(jsonMapper)).contains(Jackson2CompatibleJsonMapper.STRING_COLLECTION_MODULE_NAME);
        assertThat(moduleNames(codeBuiltMapper)).contains(Jackson2CompatibleJsonMapper.STRING_COLLECTION_MODULE_NAME);
    }

    /**
     * Jackson 2에서 Boot 매퍼(Jackson2ObjectMapperBuilder)와 {@code new ObjectMapper()}는 네 설정만 달랐다. Boot가 모르는 필드를 무시하고,
     * 날짜와 기간을 문자열로 쓰고, 뷰 기본 포함을 끈다. Jackson 3의 두 매퍼도 그 넷만 다르다. 속성 이름 인터닝은 성능 설정이라 함께 둔다.
     */
    @Test
    void 주입받는_JsonMapper와_코드에서_만드는_매퍼는_Jackson_2_때처럼_Boot가_바꾸는_설정만_다르다() {
        JsonMapper codeBuiltMapper = Jackson2CompatibleJsonMapper.builder().build();

        assertThat(differentFeatures(jsonMapper, codeBuiltMapper)).containsExactlyInAnyOrder(
                "DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES",
                "DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS",
                "DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS",
                "MapperFeature.DEFAULT_VIEW_INCLUSION",
                "TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES");
    }

    /**
     * Jackson 2는 logback·Redisson 의존성으로 클래스패스에 남는다. 그 때문에 Boot가 Jackson 2 매퍼나 변환기를 따로 만들지 않는지 본다.
     * 빈을 만들지 않고 타입만 본다. Jackson 2 타입은 com.fasterxml.jackson 패키지이고, Spring·Boot의 Jackson 2 연동 타입
     * (MappingJackson2HttpMessageConverter, EndpointJackson2ObjectMapper 등)은 이름에 Jackson2가 들어간다.
     * Jackson 2 자동 설정 클래스 자체는 클래스 조건이 맞아 빈으로 올라오지만 빈 메서드 조건이 맞지 않아 아무것도 만들지 않으므로 뺀다.
     */
    @Test
    void 컨텍스트에는_Jackson_2_타입의_빈이_없고_actuator는_Jackson_2_호환_매퍼_하나를_쓴다() {
        List<String> jackson2Beans = Arrays.stream(context.getBeanDefinitionNames())
                .filter(name -> isJackson2Type(context.getType(name, false)))
                .toList();

        assertThat(jackson2Beans).isEmpty();
        assertThat(context.getBeanNamesForType(EndpointJsonMapper.class))
                .containsExactly("jackson2CompatibleEndpointJsonMapper");
    }

    private static boolean isJackson2Type(Class<?> type) {
        if (type == null || AnnotatedElementUtils.hasAnnotation(type, Configuration.class)) {
            return false;
        }
        return type.getName().startsWith("com.fasterxml.jackson.") || type.getSimpleName().contains("Jackson2");
    }

    private static List<String> differentFeatures(JsonMapper left, JsonMapper right) {
        Map<String, Boolean> leftFeatures = features(left);
        Map<String, Boolean> rightFeatures = features(right);
        List<String> different = new ArrayList<>();
        leftFeatures.forEach((name, enabled) -> {
            if (!enabled.equals(rightFeatures.get(name))) {
                different.add(name);
            }
        });
        return different;
    }

    private static Map<String, Boolean> features(JsonMapper mapper) {
        Map<String, Boolean> features = new TreeMap<>();
        for (MapperFeature feature : MapperFeature.values()) {
            features.put("MapperFeature." + feature.name(), mapper.isEnabled(feature));
        }
        for (SerializationFeature feature : SerializationFeature.values()) {
            features.put("SerializationFeature." + feature.name(), mapper.isEnabled(feature));
        }
        for (DeserializationFeature feature : DeserializationFeature.values()) {
            features.put("DeserializationFeature." + feature.name(), mapper.isEnabled(feature));
        }
        for (EnumFeature feature : EnumFeature.values()) {
            features.put("EnumFeature." + feature.name(), mapper.isEnabled(feature));
        }
        for (DateTimeFeature feature : DateTimeFeature.values()) {
            features.put("DateTimeFeature." + feature.name(), mapper.isEnabled(feature));
        }
        for (JsonNodeFeature feature : JsonNodeFeature.values()) {
            features.put("JsonNodeFeature." + feature.name(), mapper.isEnabled(feature));
        }
        for (StreamReadFeature feature : StreamReadFeature.values()) {
            features.put("StreamReadFeature." + feature.name(), mapper.isEnabled(feature));
        }
        for (StreamWriteFeature feature : StreamWriteFeature.values()) {
            features.put("StreamWriteFeature." + feature.name(), mapper.isEnabled(feature));
        }
        for (JsonReadFeature feature : JsonReadFeature.values()) {
            features.put("JsonReadFeature." + feature.name(), mapper.isEnabled(feature));
        }
        for (JsonWriteFeature feature : JsonWriteFeature.values()) {
            features.put("JsonWriteFeature." + feature.name(), mapper.isEnabled(feature));
        }
        for (TokenStreamFactory.Feature feature : TokenStreamFactory.Feature.values()) {
            features.put("TokenStreamFactory.Feature." + feature.name(), mapper.isEnabled(feature));
        }
        return features;
    }

    private static List<String> moduleNames(JsonMapper mapper) {
        return mapper.registeredModules().stream().map(JacksonModule::getModuleName).toList();
    }

    private static List<Integer> constraints(JsonMapper mapper) {
        return List.of(mapper.tokenStreamFactory().streamReadConstraints().getMaxNestingDepth(),
                mapper.tokenStreamFactory().streamReadConstraints().getMaxStringLength(),
                mapper.tokenStreamFactory().streamWriteConstraints().getMaxNestingDepth());
    }
}
