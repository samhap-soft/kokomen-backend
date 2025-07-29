package com.samhap.kokomen.interview.external;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.exception.InternalApiException;
import com.samhap.kokomen.interview.service.dto.NotificationRequest;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;


// TODO: 타임아웃 설정하면서 테스트하는 방법 알아낸 뒤 적용하기
@Disabled
@RestClientTest
@EnableAutoConfiguration(exclude = {
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class
})
class NotificationClientTest {

    @MockitoBean(name = "jpaAuditingHandler")
    Object dummyAuditingHandler;

    @Value("${notification.base-url}")
    private String notificationBaseUrl;

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private ObjectMapper objectMapper;

    private NotificationClient notificationClient;
    private MockRestServiceServer server;


    @BeforeEach
    void init() {
        NotificationClientBuilder notificationClientBuilder = new NotificationClientBuilder(
                restClientBuilder,
                objectMapper,
                notificationBaseUrl,
                Duration.ofMillis(100),
                Duration.ofMillis(100)
        );
        notificationClient = new NotificationClient(notificationClientBuilder);

        server = MockRestServiceServer.bindTo(notificationClientBuilder.getNotificationClientBuilder()).build();
    }

    @Test
    void 정상_요청시_X_REQUEST_ID_헤더가_포함된다() {
        // given
        String requestId = "test-request-id-123";
        MDC.put("requestId", requestId);
        NotificationRequest req = Mockito.mock(NotificationRequest.class);

        server.expect(MockRestRequestMatchers.requestTo(notificationBaseUrl + "/internal/v1/notifications"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(header("X-Request-Id", requestId))
                .andRespond(MockRestResponseCreators.withSuccess());

        // when
        notificationClient.request(req);
        MDC.clear();
    }

    @Test
    void notification_API가_4xx_5xx_응답을_주면_InternalApiException으로_변환된다() {
        // given
        NotificationRequest req = Mockito.mock(NotificationRequest.class);

        server.expect(MockRestRequestMatchers.requestTo(notificationBaseUrl + "/internal/v1/notifications"))
                .andRespond(MockRestResponseCreators.withServerError());

        // when & then
        assertThatThrownBy(() -> notificationClient.request(req))
                .isInstanceOf(InternalApiException.class);
    }
} 
