package com.samhap.kokomen.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class AdminApiAuthenticationTest extends BaseTest {

    private static final String ADMIN_API_PATH_PREFIX = "/api/v1/admin";

    @Qualifier("requestMappingHandlerMapping")
    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    // 관리자 API가 로그인 검증 없이 추가되는 것을 막는다. 관리자 여부 검증은 각 관리자 컨트롤러 테스트에서 고정한다.
    @Test
    void 모든_관리자_API는_로그인한_회원만_호출할_수_있다() {
        List<HandlerMethod> adminHandlerMethods = requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getKey().getPatternValues().stream()
                        .anyMatch(pattern -> pattern.startsWith(ADMIN_API_PATH_PREFIX)))
                .map(Map.Entry::getValue)
                .toList();

        assertThat(adminHandlerMethods).isNotEmpty();
        assertThat(adminHandlerMethods).allSatisfy(handlerMethod -> assertThat(handlerMethod.getMethodParameters())
                .as("%s에 @Authentication MemberAuth 파라미터가 없다", handlerMethod)
                .anySatisfy(parameter -> {
                    assertThat(parameter.getParameterType()).isEqualTo(MemberAuth.class);
                    assertThat(parameter.getParameterAnnotation(Authentication.class))
                            .isNotNull()
                            .extracting(Authentication::required)
                            .isEqualTo(true);
                }));
    }
}
