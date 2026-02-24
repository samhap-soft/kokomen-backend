package com.samhap.kokomen.global.infrastructure;

import com.samhap.kokomen.global.dto.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Slf4j
@Component
public class ClientIpArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String UNKNOWN_IP_ADDRESS = "0.0.0.0";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(ClientIp.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String ipAddress = Objects.requireNonNullElse(request.getHeader("X-Real-IP"), UNKNOWN_IP_ADDRESS);
        return new ClientIp(ipAddress);
    }
}
