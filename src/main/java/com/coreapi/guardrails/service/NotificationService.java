package com.coreapi.guardrails.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final RedisTemplate<String, Object> redisTemplate;

    public void sendOrQueueNotification(Long userId, String msg) {
        String cooldownKey = "notif_cooldown:user_" + userId;
        String pendingKey = "user:" + userId + ":pending_notifs";

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            redisTemplate.opsForList().rightPush(pendingKey, msg);
        } else {
            log.info("Push Notification Sent to User {}: {}", userId, msg);
            redisTemplate.opsForValue().set(cooldownKey, "1", 15, TimeUnit.MINUTES);
        }
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void sweepNotifications() {
        Set<String> keys = redisTemplate.keys("user:*:pending_notifs");
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            List<Object> pending = redisTemplate.opsForList().range(key, 0, -1);
            if (pending != null && !pending.isEmpty()) {
                log.info("Summarized Notification: {} and {} others interacted with you.", 
                        ((String)pending.get(0)).split(" ")[0], pending.size() - 1);
                redisTemplate.delete(key);
            }
        }
    }
}
