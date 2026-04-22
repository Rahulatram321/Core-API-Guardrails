package com.coreapi.guardrails.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuardrailService {
    private static final int MAX_BOT_REPLIES_PER_POST = 100;
    private static final int MAX_THREAD_DEPTH = 20;
    private static final long BOT_COOLDOWN_MINUTES = 10L;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<Long> horizontalCapScript;

    public boolean checkHorizontalCap(Long postId) {
        Long res = redisTemplate.execute(horizontalCapScript,
                Collections.singletonList(horizontalCapKey(postId)),
                MAX_BOT_REPLIES_PER_POST);
        if (res == null || res == -1) {
            log.warn("Horizontal cap hit for post {}", postId);
            return false;
        }
        return true;
    }

    public boolean checkVerticalCap(int depth) {
        if (depth > MAX_THREAD_DEPTH) {
            log.warn("Vertical cap hit: {}", depth);
            return false;
        }
        return true;
    }

    public boolean checkBotCooldown(Long botId, Long userId) {
        Boolean set = redisTemplate.opsForValue().setIfAbsent(cooldownKey(botId, userId), "1",
                BOT_COOLDOWN_MINUTES, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(set)) {
            log.warn("Cooldown active for bot {} and user {}", botId, userId);
            return false;
        }
        return true;
    }

    public void releaseHorizontalCap(Long postId) {
        redisTemplate.opsForValue().decrement(horizontalCapKey(postId));
    }

    public void releaseBotCooldown(Long botId, Long userId) {
        redisTemplate.delete(cooldownKey(botId, userId));
    }

    private String horizontalCapKey(Long postId) {
        return "post:" + postId + ":bot_count";
    }

    private String cooldownKey(Long botId, Long userId) {
        return "cooldown:bot_" + botId + ":user_" + userId;
    }
}
