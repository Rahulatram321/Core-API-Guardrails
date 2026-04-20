package com.coreapi.guardrails.dto;

import com.coreapi.guardrails.model.AuthorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PostRequest {
    @NotNull private Long authorId;
    @NotNull private AuthorType authorType;
    @NotBlank private String content;
}
