package com.coreapi.guardrails.dto;

import com.coreapi.guardrails.model.AuthorType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LikeRequest {
    @NotNull private Long authorId;
    @NotNull private AuthorType authorType;
}
