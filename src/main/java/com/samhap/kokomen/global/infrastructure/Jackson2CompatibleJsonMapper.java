package com.samhap.kokomen.global.infrastructure;

import java.util.Collection;
import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.NullValueProvider;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.deser.ValueInstantiator;
import tools.jackson.databind.deser.jdk.CollectionDeserializer;
import tools.jackson.databind.deser.jdk.StringCollectionDeserializer;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.CollectionType;

/**
 * 코드에서 직접 만드는 Jackson 3 매퍼의 시작점이다. Jackson 2의 {@code new ObjectMapper()}와 같게 동작하도록 맞춘다.
 *
 * <p>{@link JsonFactory#builderWithJackson2Defaults()}와 {@code configureForJackson2()}가 기본값 차이(속성 알파벳 정렬,
 * null을 primitive로 읽기, 뒤따르는 토큰, enum toString, UTF-8 출력의 서로게이트 쌍 이스케이프 등)를 Jackson 2로 되돌린다.
 * 둘이 되돌리지 않는 것은 여기서 Jackson 2 값으로 둔다: 입출력 크기 제한(중첩 깊이, 문자열 길이), 뷰 기본 포함, Void 속성,
 * 비교할 수 없는 맵 키 정렬 실패, 문자열 컬렉션 읽기({@link #stringCollectionModule()}).
 * Boot가 만드는 매퍼는 application.yml의 spring.jackson 설정과 JacksonConfig로 같은 값을 쓴다.
 */
public final class Jackson2CompatibleJsonMapper {

    public static final int MAX_NESTING_DEPTH = 1000;
    public static final int MAX_STRING_LENGTH = 20_000_000;
    public static final String STRING_COLLECTION_MODULE_NAME = "jackson2-string-collection";

    private Jackson2CompatibleJsonMapper() {
    }

    public static JsonMapper.Builder builder() {
        JsonFactory jsonFactory = JsonFactory.builderWithJackson2Defaults()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(MAX_NESTING_DEPTH)
                        .maxStringLength(MAX_STRING_LENGTH)
                        .build())
                .streamWriteConstraints(StreamWriteConstraints.builder()
                        .maxNestingDepth(MAX_NESTING_DEPTH)
                        .build())
                .build();
        return JsonMapper.builder(jsonFactory)
                .configureForJackson2()
                .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .disable(MapperFeature.ALLOW_VOID_VALUED_PROPERTIES)
                .enable(SerializationFeature.FAIL_ON_ORDER_MAP_BY_INCOMPARABLE_KEY)
                .addModule(stringCollectionModule());
    }

    /**
     * {@code List<String>} 같은 문자열 컬렉션을 Jackson 2처럼 읽는다.
     *
     * <p>Jackson 3(3.1.8에서 확인)의 문자열 컬렉션 역직렬화기는 원소 자리에 배열이 오면 그 안의 값을 원소로 이어 읽고, 바깥 배열의 닫는 괄호를
     * 읽지 않은 채 끝낸다. 뒤따르는 토큰을 허용하는 Jackson 2 기본값에서는 그 뒤의 필드가 조용히 버려진다(예: {@code [["a","b"]]}가
     * {@code [a, b]}가 되고, 객체 안이면 다음 필드가 null이 된다). Jackson 2는 이 입력을 거절했다. 원소 역직렬화기를 따로 지정하지 않은
     * 문자열 컬렉션은 {@link Jackson2StringCollectionDeserializer}로 바꿔 원소마다 문자열 규칙(숫자·boolean은 문자열로, 배열·객체는
     * 거절)을 Jackson 2와 같게 적용한다. 원소 역직렬화기를 지정한 경우(contentUsing, 전역 String 역직렬화기)는 그대로 둔다.
     */
    public static JacksonModule stringCollectionModule() {
        SimpleModule module = new SimpleModule(STRING_COLLECTION_MODULE_NAME);
        module.setDeserializerModifier(new ValueDeserializerModifier() {
            @Override
            public ValueDeserializer<?> modifyCollectionDeserializer(DeserializationConfig config, CollectionType type,
                                                                     BeanDescription.Supplier beanDescription,
                                                                     ValueDeserializer<?> deserializer) {
                if (deserializer instanceof StringCollectionDeserializer stringCollectionDeserializer
                        && stringCollectionDeserializer.getContentDeserializer() == null) {
                    return new Jackson2StringCollectionDeserializer(type,
                            stringCollectionDeserializer.getValueInstantiator(), beanDescription.get().getClassInfo());
                }
                return deserializer;
            }
        });
        return module;
    }

    /**
     * 원소 역직렬화기 자리를 비운 일반 컬렉션 역직렬화기다. 맥락화할 때 기본 문자열 역직렬화기가 들어간다.
     * 배열 대신 문자열 하나가 오면 Jackson 2 문자열 컬렉션 역직렬화기처럼 문자열 강제 변환 규칙으로 처리한다(빈 문자열은 값 오류).
     */
    private static final class Jackson2StringCollectionDeserializer extends CollectionDeserializer {

        private Jackson2StringCollectionDeserializer(JavaType type, ValueInstantiator valueInstantiator,
                                                     AnnotatedClass classInfo) {
            super(type, null, null, valueInstantiator, classInfo);
        }

        private Jackson2StringCollectionDeserializer(CollectionDeserializer source) {
            super(source);
        }

        // 맥락화한 뒤에도 이 타입을 유지한다.
        @Override
        protected CollectionDeserializer withResolved(ValueDeserializer<?> delegateDeserializer,
                                                      ValueDeserializer<?> valueDeserializer,
                                                      TypeDeserializer valueTypeDeserializer,
                                                      NullValueProvider nullValueProvider, Boolean unwrapSingle) {
            return new Jackson2StringCollectionDeserializer(super.withResolved(delegateDeserializer, valueDeserializer,
                    valueTypeDeserializer, nullValueProvider, unwrapSingle));
        }

        @Override
        protected Collection<Object> _deserializeFromString(JsonParser p, DeserializationContext ctxt, String value) {
            boolean canWrapSingleValue = Boolean.TRUE.equals(_unwrapSingle)
                    || (_unwrapSingle == null && ctxt.isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY));
            if (!canWrapSingleValue) {
                return _deserializeFromStringForContainer(p, ctxt);
            }
            return super._deserializeFromString(p, ctxt, value);
        }
    }
}
