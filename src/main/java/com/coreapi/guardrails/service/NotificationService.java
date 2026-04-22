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
    private static final String NOTIFICATION_COOLDOWN_PREFIX = "notif_cooldown:user_";
    private static final String PENDING_NOTIFICATION_PREFIX = "user:%d:pending_notifs";
    private static final String PENDING_USERS_SET = "pending_notification_users";
    private static final long NOTIFICATION_COOLDOWN_MINUTES = 15L;

    private final RedisTemplate<String, Object> redisTemplate;

    public void sendOrQueueNotification(Long userId, String msg) {
        String cooldownKey = NOTIFICATION_COOLDOWN_PREFIX + userId;
        String pendingKey = String.format(PENDING_NOTIFICATION_PREFIX, userId);

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            redisTemplate.opsForList().rightPush(pendingKey, msg);
            redisTemplate.opsForSet().add(PENDING_USERS_SET, userId.toString());
        } else {
            log.info("Push Notification Sent to User {}: {}", userId, msg);
            redisTemplate.opsForValue().set(cooldownKey, "1", NOTIFICATION_COOLDOWN_MINUTES, TimeUnit.MINUTES);
        }
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void sweepNotifications() {
        Set<Object> pendingUsers = redisTemplate.opsForSet().members(PENDING_USERS_SET);
        if (pendingUsers == null || pendingUsers.isEmpty()) {
            return;
        }

        for (Object pendingUser : pendingUsers) {
            Long userId = Long.valueOf(pendingUser.toString());
            List<Object> pending = drainPendingNotifications(userId);
            if (pending.isEmpty()) {
                redisTemplate.opsForSet().remove(PENDING_USERS_SET, userId.toString());
                continue;
            }

            String firstActor = extractActorName(pending.get(0).toString());
            int remainingCount = Math.max(0, pending.size() - 1);
            log.info("Summarized Push Notification: {} and {} others interacted with your posts.", firstActor, remainingCount);
            redisTemplate.opsForSet().remove(PENDING_USERS_SET, userId.toString());
        }
    }

    private List<Object> drainPendingNotifications(Long userId) {
        String pendingKey = String.format(PENDING_NOTIFICATION_PREFIX, userId);
        List<Object> drained = new java.util.ArrayList<>();
        Object next;
        while ((next = redisTemplate.opsForList().leftPop(pendingKey)) != null) {
            drained.add(next);
        }
        return drained;
    }

    private String extractActorName(String message) {
        int delimiterIndex = message.indexOf(" replied");
        if (delimiterIndex > 0) {
            return message.substring(0, delimiterIndex);
        }
        return message;
    }
}
