package com.samhap.kokomen.global.infrastructure;

import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Slf4j
@Component
public class MemberAuthArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(MemberAuth.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {
        Authentication authentication = parameter.getParameterAnnotation(Authentication.class);
        if (authentication == null) {
            throw new IllegalStateException("MemberAuth 파라미터는 @Authentication 어노테이션이 있어야 합니다.");
        }
        boolean authenticationRequired = authentication.required();
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        HttpSession session = request.getSession(false);

        validateAuthentication(session, authenticationRequired);
        if (session == null) {
            return new MemberAuth(null);
        }
        Long memberId = (Long) session.getAttribute("MEMBER_ID");
        validateAuthentication(memberId, authenticationRequired);

        return new MemberAuth(memberId);
    }

    private void validateAuthentication(HttpSession session, boolean authenticationRequired) {
        if (session == null && authenticationRequired) {
            throw new UnauthorizedException("로그인이 필요합니다");
        }
    }

    private void validateAuthentication(Long memberId, boolean authenticationRequired) {
        if (memberId == null) {
            log.error("세션에 MEMBER_ID가 없습니다.");
        }
        if (memberId == null && authenticationRequired) {
            throw new IllegalStateException("세션에 MEMBER_ID가 없습니다.");
        }
    }
}
