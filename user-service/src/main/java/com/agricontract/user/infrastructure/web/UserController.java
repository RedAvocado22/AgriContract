package com.agricontract.user.infrastructure.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Object request) {
        // TODO
        return ResponseEntity.status(201).build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile() {
        // TODO
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getProfile(@PathVariable String userId) {
        // TODO
        return ResponseEntity.ok().build();
    }
}
