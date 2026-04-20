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
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<Long> horizontalCapScript;

    // Hardcoded limits for simpler look
    public boolean checkHorizontalCap(Long postId) {
        String key = "post:" + postId + ":bot_count";
        Long res = redisTemplate.execute(horizontalCapScript, Collections.singletonList(key), 100);
        if (res == null || res == -1) {
            log.warn("Horizontal cap hit for post {}", postId);
            return false;
        }
        return true;
    }

    public boolean checkVerticalCap(int depth) {
        if (depth > 20) {
            log.warn("Vertical cap hit: {}", depth);
            return false;
        }
        return true;
    }

    public boolean checkBotCooldown(Long botId, Long userId) {
        String key = "cooldown:bot_" + botId + ":user_" + userId;
        Boolean set = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(set)) {
            log.warn("Cooldown active for bot {}", botId);
            return false;
        }
        return true;
    }
}
