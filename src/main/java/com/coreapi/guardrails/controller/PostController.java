package com.coreapi.guardrails.controller;

import com.coreapi.guardrails.dto.*;
import com.coreapi.guardrails.model.*;
import com.coreapi.guardrails.repository.*;
import com.coreapi.guardrails.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final BotRepository botRepository;
    private final GuardrailService guardrailService;
    private final ViralityService viralityService;
    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<Post> createPost(@Valid @RequestBody PostRequest req) {
        Post post = Post.builder().authorId(req.getAuthorId()).authorType(req.getAuthorType()).content(req.getContent()).build();
        return ResponseEntity.status(HttpStatus.CREATED).body(postRepository.save(post));
    }

    @PostMapping("/{postId}/comments")
    @Transactional
    public ResponseEntity<?> addComment(@PathVariable Long postId, @Valid @RequestBody CommentRequest req) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) return ResponseEntity.notFound().build();

        if (!guardrailService.checkVerticalCap(req.getDepthLevel())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Thread too deep");
        }

        if (req.getAuthorType() == AuthorType.BOT) {
            if (!guardrailService.checkHorizontalCap(postId)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Bot limit reached");
            }
            if (post.getAuthorType() == AuthorType.USER) {
                if (!guardrailService.checkBotCooldown(req.getAuthorId(), post.getAuthorId())) {
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Cooldown active");
                }
                String botName = botRepository.findById(req.getAuthorId()).map(Bot::getName).orElse("Bot");
                notificationService.sendOrQueueNotification(post.getAuthorId(), botName + " replied");
            }
            viralityService.recordInteraction(postId, ViralityService.InteractionType.BOT_REPLY);
        } else {
            viralityService.recordInteraction(postId, ViralityService.InteractionType.HUMAN_COMMENT);
        }

        Comment comment = Comment.builder().postId(postId).authorId(req.getAuthorId()).authorType(req.getAuthorType())
                .content(req.getContent()).depthLevel(req.getDepthLevel()).build();
        return ResponseEntity.status(HttpStatus.CREATED).body(commentRepository.save(comment));
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<?> likePost(@PathVariable Long postId, @Valid @RequestBody LikeRequest req) {
        if (req.getAuthorType() == AuthorType.USER) {
            viralityService.recordInteraction(postId, ViralityService.InteractionType.HUMAN_LIKE);
        }
        return ResponseEntity.ok("Action tracked");
    }

    @GetMapping("/{postId}/virality")
    public ResponseEntity<Long> getVirality(@PathVariable Long postId) {
        return ResponseEntity.ok(viralityService.getViralityScore(postId));
    }
}
