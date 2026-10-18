package com.samhap.kokomen.global.aop;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.KokomenApplication;
import com.samhap.kokomen.global.BaseTest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.annotation.AspectJAdvisorFactory;
import org.springframework.aop.aspectj.annotation.BeanFactoryAspectInstanceFactory;
import org.springframework.aop.aspectj.annotation.MetadataAwareAspectInstanceFactory;
import org.springframework.aop.aspectj.annotation.ReflectiveAspectJAdvisorFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * 애플리케이션 Aspect의 모든 어드바이스가 컨텍스트 빈의 메서드 하나 이상에 걸리는지 고정한다.
 * 포인트컷은 문자열이라 대상 메서드의 이름·패키지·인자 수가 바뀌어도 컴파일 오류 없이 매칭 0건이 되고, 그 어드바이스는
 * 예외도 로그도 없이 꺼진다. 인자 3개를 요구하는 args(...) 포인트컷이 인자가 5개로 늘어난 대상 메서드를 놓쳐
 * 면접 종료·답변 등급 지표가 한 번도 기록되지 않은 일이 그 예다.
 * <p>
 * 어드바이저는 Spring이 기동할 때 쓰는 ReflectiveAspectJAdvisorFactory로 만든다. 이 팩토리가 포인트컷의 선언 범위를
 * Aspect 클래스로, 파라미터 이름·타입을 어드바이스 메서드 시그니처로 채우므로 이름 붙은 포인트컷 참조와 args(...)·
 * {@code @annotation(...)} 바인딩이 실제와 똑같이 해석된다. 바인딩이 맞지 않으면 Spring은 어드바이저를 만들지 않거나
 * (getAdvisor가 null) 포인트컷 해석 실패로 매칭 0건으로 처리하고 디버그 로그만 남긴다. 두 경우 모두 걸리지 않은 것으로 본다.
 * 매칭 판정은 Spring이 프록시 대상을 고를 때 쓰는 AopUtils.canApply를 그대로 쓴다. 테스트 컨텍스트는 지연 초기화라
 * 빈 인스턴스 대신 빈 정의의 타입을 대상 클래스로 삼는다.
 * <p>
 * 판정 단위는 어드바이스 하나라서 {@code @within(X) || @annotation(X)}처럼 OR로 묶인 포인트컷의 한쪽만 죽는 것은 잡지
 * 못한다. 또 빈으로 등록된 Aspect만 검사하므로 {@code @Component}가 빠져 통째로 꺼진 Aspect는 대상에서 빠진다.
 */
class AspectPointcutMatchTest extends BaseTest {

    private static final List<Class<? extends Annotation>> ADVICE_ANNOTATIONS =
            List.of(Around.class, Before.class, After.class, AfterReturning.class, AfterThrowing.class);

    @Autowired
    private ConfigurableListableBeanFactory beanFactory;

    @Test
    void 모든_어드바이스는_컨텍스트_빈의_메서드_하나_이상에_걸린다() {
        AspectJAdvisorFactory advisorFactory = new ReflectiveAspectJAdvisorFactory(beanFactory);
        String[] beanNames = beanFactory.getBeanNamesForType(Object.class, true, false);
        List<Class<?>> targetClasses = findTargetClasses(advisorFactory, beanNames);

        List<String> checkedAdvices = new ArrayList<>();
        List<String> unmatchedAdvices = new ArrayList<>();
        for (String aspectName : findApplicationAspectNames(advisorFactory, beanNames)) {
            Class<?> aspectClass = beanFactory.getType(aspectName, false);
            MetadataAwareAspectInstanceFactory instanceFactory =
                    new BeanFactoryAspectInstanceFactory(beanFactory, aspectName, aspectClass);
            for (Method adviceMethod : findAdviceMethods(aspectClass)) {
                String adviceName = aspectClass.getSimpleName() + "." + adviceMethod.getName();
                Advisor advisor = advisorFactory.getAdvisor(adviceMethod, instanceFactory, 0, aspectName);
                checkedAdvices.add(adviceName);
                if (advisor == null || targetClasses.stream().noneMatch(type -> AopUtils.canApply(advisor, type))) {
                    unmatchedAdvices.add(adviceName);
                }
            }
        }

        assertThat(checkedAdvices).isNotEmpty();
        assertThat(unmatchedAdvices).as("어느 빈 메서드에도 걸리지 않는 어드바이스").isEmpty();
    }

    // Spring은 Aspect 빈 자신에게는 어드바이스를 걸지 않으므로 대상 클래스에서 뺀다.
    private List<Class<?>> findTargetClasses(AspectJAdvisorFactory advisorFactory, String[] beanNames) {
        return Arrays.stream(beanNames)
                .map(beanName -> beanFactory.getType(beanName, false))
                .filter(type -> type != null && !advisorFactory.isAspect(type))
                .map(ClassUtils::getUserClass)
                .distinct()
                .toList();
    }

    private List<String> findApplicationAspectNames(AspectJAdvisorFactory advisorFactory, String[] beanNames) {
        String applicationPackage = KokomenApplication.class.getPackageName();
        return Arrays.stream(beanNames)
                .filter(beanName -> {
                    Class<?> type = beanFactory.getType(beanName, false);
                    return type != null && advisorFactory.isAspect(type)
                            && type.getPackageName().startsWith(applicationPackage);
                })
                .toList();
    }

    private static List<Method> findAdviceMethods(Class<?> aspectClass) {
        List<Method> adviceMethods = new ArrayList<>();
        ReflectionUtils.doWithMethods(aspectClass, adviceMethods::add,
                method -> ADVICE_ANNOTATIONS.stream().anyMatch(method::isAnnotationPresent));
        return adviceMethods;
    }
}
