package com.hulahoop.blueback.auth.controller;

import com.hulahoop.blueback.auth.model.dto.LoginRequest;
import com.hulahoop.blueback.auth.model.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        String token = authService.login(loginRequest.getId(), loginRequest.getPassword());
        return ResponseEntity.ok(Map.of("token", token));
    }
}
