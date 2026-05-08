package com.devconnect.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String bio;
    private String avatarUrl;
    private LocalDateTime createdAt;
}