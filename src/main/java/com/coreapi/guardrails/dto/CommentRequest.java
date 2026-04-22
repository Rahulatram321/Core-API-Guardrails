package com.coreapi.guardrails.dto;

import com.coreapi.guardrails.model.AuthorType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommentRequest {
    @NotNull private Long authorId;
    @NotNull private AuthorType authorType;
    @NotBlank private String content;
    private Long parentCommentId;
    @Min(1) @Max(20) private Integer depthLevel;
}
