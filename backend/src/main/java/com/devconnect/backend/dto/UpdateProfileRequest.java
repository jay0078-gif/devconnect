package com.devconnect.backend.dto;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String bio;
    private String avatarUrl;
}