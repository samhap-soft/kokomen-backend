package com.samhap.kokomen.global.fixture.async;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 제출한 작업을 호출 스레드에서 바로 실행하는 실행기. 비동기 서비스를 이것으로 손으로 조립하면 제출 메서드가 돌아올 때 작업이
 * 끝나 있으므로 완료를 기다리지 않고 결과를 볼 수 있다. 스레드 풀은 만들지 않는다(initialize를 부르지 않는다).
 */
public class CallerRunsTaskExecutor extends ThreadPoolTaskExecutor {

    @Override
    public void execute(Runnable task) {
        task.run();
    }
}
