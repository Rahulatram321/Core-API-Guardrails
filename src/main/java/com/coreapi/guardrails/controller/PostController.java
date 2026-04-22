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
    private final UserRepository userRepository;
    private final GuardrailService guardrailService;
    private final ViralityService viralityService;
    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<?> createPost(@Valid @RequestBody PostRequest req) {
        if (!authorExists(req.getAuthorId(), req.getAuthorType())) {
            return ResponseEntity.badRequest().body("Author does not exist");
        }

        Post post = Post.builder()
                .authorId(req.getAuthorId())
                .authorType(req.getAuthorType())
                .content(req.getContent())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(postRepository.save(post));
    }

    @PostMapping("/{postId}/comments")
    @Transactional
    public ResponseEntity<?> addComment(@PathVariable Long postId, @Valid @RequestBody CommentRequest req) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            return ResponseEntity.notFound().build();
        }

        if (!authorExists(req.getAuthorId(), req.getAuthorType())) {
            return ResponseEntity.badRequest().body("Author does not exist");
        }

        Comment parentComment = resolveParentComment(postId, req.getParentCommentId());
        if (req.getParentCommentId() != null && parentComment == null) {
            return ResponseEntity.badRequest().body("Parent comment not found for this post");
        }

        int depthLevel = calculateDepth(req.getDepthLevel(), parentComment);
        if (!guardrailService.checkVerticalCap(depthLevel)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Thread too deep");
        }

        Long cooldownTargetUserId = resolveCooldownTargetUserId(post, parentComment);
        boolean horizontalSlotReserved = false;
        boolean cooldownGranted = false;

        try {
            if (req.getAuthorType() == AuthorType.BOT) {
                if (!guardrailService.checkHorizontalCap(postId)) {
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Bot limit reached");
                }
                horizontalSlotReserved = true;

                if (cooldownTargetUserId != null && !guardrailService.checkBotCooldown(req.getAuthorId(), cooldownTargetUserId)) {
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Cooldown active");
                }
                cooldownGranted = cooldownTargetUserId != null;
            }

            Comment comment = Comment.builder()
                    .postId(postId)
                    .parentCommentId(req.getParentCommentId())
                    .authorId(req.getAuthorId())
                    .authorType(req.getAuthorType())
                    .content(req.getContent())
                    .depthLevel(depthLevel)
                    .build();
            Comment savedComment = commentRepository.save(comment);

            if (req.getAuthorType() == AuthorType.BOT) {
                viralityService.recordInteraction(postId, ViralityService.InteractionType.BOT_REPLY);
                if (cooldownTargetUserId != null) {
                    String botName = botRepository.findById(req.getAuthorId()).map(Bot::getName).orElse("Bot");
                    notificationService.sendOrQueueNotification(cooldownTargetUserId, botName + " replied to your post");
                }
            } else {
                viralityService.recordInteraction(postId, ViralityService.InteractionType.HUMAN_COMMENT);
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(savedComment);
        } catch (RuntimeException ex) {
            if (horizontalSlotReserved) {
                guardrailService.releaseHorizontalCap(postId);
            }
            if (cooldownGranted && cooldownTargetUserId != null) {
                guardrailService.releaseBotCooldown(req.getAuthorId(), cooldownTargetUserId);
            }
            throw ex;
        }
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<?> likePost(@PathVariable Long postId, @Valid @RequestBody LikeRequest req) {
        if (!postRepository.existsById(postId)) {
            return ResponseEntity.notFound().build();
        }
        if (!authorExists(req.getAuthorId(), req.getAuthorType())) {
            return ResponseEntity.badRequest().body("Author does not exist");
        }
        if (req.getAuthorType() == AuthorType.USER) {
            viralityService.recordInteraction(postId, ViralityService.InteractionType.HUMAN_LIKE);
        }
        return ResponseEntity.ok("Action tracked");
    }

    @GetMapping("/{postId}/virality")
    public ResponseEntity<Long> getVirality(@PathVariable Long postId) {
        return ResponseEntity.ok(viralityService.getViralityScore(postId));
    }

    private boolean authorExists(Long authorId, AuthorType authorType) {
        return switch (authorType) {
            case USER -> userRepository.existsById(authorId);
            case BOT -> botRepository.existsById(authorId);
        };
    }

    private Comment resolveParentComment(Long postId, Long parentCommentId) {
        if (parentCommentId == null) {
            return null;
        }
        return commentRepository.findByIdAndPostId(parentCommentId, postId).orElse(null);
    }

    private int calculateDepth(Integer requestedDepth, Comment parentComment) {
        if (parentComment != null) {
            return parentComment.getDepthLevel() + 1;
        }
        return requestedDepth != null ? requestedDepth : 1;
    }

    private Long resolveCooldownTargetUserId(Post post, Comment parentComment) {
        if (parentComment != null && parentComment.getAuthorType() == AuthorType.USER) {
            return parentComment.getAuthorId();
        }
        if (post.getAuthorType() == AuthorType.USER) {
            return post.getAuthorId();
        }
        return null;
    }
}
