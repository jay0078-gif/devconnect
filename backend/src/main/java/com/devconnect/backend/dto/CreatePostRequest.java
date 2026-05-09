package com.devconnect.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePostRequest {

    private String title;

    @NotBlank(message = "Content is required")
    @Size(max = 3000, message = "Post cannot exceed 3000 characters")
    private String content;
}
