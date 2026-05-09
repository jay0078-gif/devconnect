package com.devconnect.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PostDto implements java.io.Serializable {
    private Long id;
    private String title;
    private String content;
    private Long likeCount;
    private Long viewCount;
    private Long authorId;
    private String authorUsername;
    private LocalDateTime createdAt;
}