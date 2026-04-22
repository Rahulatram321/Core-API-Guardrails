package com.coreapi.guardrails.service;

import com.coreapi.guardrails.CoreApiApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = CoreApiApplication.class)
public class ConcurrentBotCommentTest {
    private static final Long POST_ID = 999L;

    @Autowired
    private GuardrailService guardrailService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void resetRedisState() {
        redisTemplate.delete("post:" + POST_ID + ":bot_count");
    }

    @Test
    public void testConcurrentHorizontalCap() throws InterruptedException {
        int threadCount = 200;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    if (guardrailService.checkHorizontalCap(POST_ID)) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        assertEquals(100, successCount.get(), "Should allow exactly 100 bot comments");
        assertEquals(100, failCount.get(), "Should reject exactly 100 bot comments");
    }
}
