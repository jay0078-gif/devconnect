package com.devconnect.backend.controller;

import com.devconnect.backend.dto.UpdateProfileRequest;
import com.devconnect.backend.dto.UserDto;
import com.devconnect.backend.repository.UserRepository;
import com.devconnect.backend.saga.DeleteAccountSagaService;
import com.devconnect.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.devconnect.backend.entity.User;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final DeleteAccountSagaService sagaService;
    private final UserRepository userRepository;
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserDto> getMe(Authentication authentication) {
        return ResponseEntity.ok(userService.getUserByEmail(authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/me")
    public ResponseEntity<UserDto> updateProfile(Authentication authentication,
                                                 @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(authentication.getName(), request));
    }

    @DeleteMapping("/me")
    public ResponseEntity<String> deleteAccount(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        sagaService.initiateDeletion(user.getId(), authentication.getName());
        return ResponseEntity.ok("Account deletion initiated");
    }

}