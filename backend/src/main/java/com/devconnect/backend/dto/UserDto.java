package com.devconnect.backend.dto;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public class UserDto implements Serializable {  // ← ADD implements Serializable
    private Long id;
    private String username;
    private String email;
    private String bio;
    private String avatarUrl;
    private LocalDateTime createdAt;
}