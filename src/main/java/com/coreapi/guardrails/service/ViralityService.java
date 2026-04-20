package com.coreapi.guardrails.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ViralityService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String VIRALITY_PREFIX = "post:%d:virality_score";

    public enum InteractionType {
        BOT_REPLY(1),
        HUMAN_LIKE(20),
        HUMAN_COMMENT(50);

        private final int points;

        InteractionType(int points) {
            this.points = points;
        }

        public int getPoints() {
            return points;
        }
    }

    public void recordInteraction(Long postId, InteractionType type) {
        String key = String.format(VIRALITY_PREFIX, postId);
        redisTemplate.opsForValue().increment(key, type.getPoints());
    }

    public Long getViralityScore(Long postId) {
        String key = String.format(VIRALITY_PREFIX, postId);
        Object score = redisTemplate.opsForValue().get(key);
        return score != null ? Long.valueOf(score.toString()) : 0L;
    }
}
