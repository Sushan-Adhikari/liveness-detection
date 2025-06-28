package com.example.pensionerverification.controller;

import com.example.pensionerverification.model.User;
import com.example.pensionerverification.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:4200")
public class UserController {

    private final UserRepository userRepository;
    private final Path root = Paths.get("uploads");

    @Autowired
    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize folder for upload!");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam("profilePicture") MultipartFile file) {
        try {
            if (userRepository.findByUsername(username).isPresent()) {
                return new ResponseEntity<>("Username is already taken!", HttpStatus.BAD_REQUEST);
            }

            User user = new User();
            user.setUsername(username);
            user.setPassword(password); // In production, hash the password

            // Save file with unique name
            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path filePath = this.root.resolve(fileName);
            Files.copy(file.getInputStream(), filePath);
            user.setProfilePicturePath(filePath.toString());

            userRepository.save(user);
            return new ResponseEntity<>("User registered successfully!", HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("Registration failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody User loginDetails) {
        Optional<User> userData = userRepository.findByUsername(loginDetails.getUsername());
        if (userData.isPresent()) {
            User user = userData.get();
            // In production, compare hashed passwords
            if (user.getPassword().equals(loginDetails.getPassword())) {
                // Return user data without password
                User responseUser = new User();
                responseUser.setId(user.getId());
                responseUser.setUsername(user.getUsername());
                responseUser.setProfilePicturePath(user.getProfilePicturePath());
                return new ResponseEntity<>(responseUser, HttpStatus.OK);
            }
        }
        return new ResponseEntity<>("Invalid username or password!", HttpStatus.UNAUTHORIZED);
    }

    @GetMapping("/profile/{username}")
    public ResponseEntity<?> getUserProfile(@PathVariable String username) {
        Optional<User> userData = userRepository.findByUsername(username);
        if (userData.isPresent()) {
            User user = userData.get();
            // Return user data without password
            User responseUser = new User();
            responseUser.setId(user.getId());
            responseUser.setUsername(user.getUsername());
            responseUser.setProfilePicturePath(user.getProfilePicturePath());
            return new ResponseEntity<>(responseUser, HttpStatus.OK);
        }
        return new ResponseEntity<>("User not found!", HttpStatus.NOT_FOUND);
    }
}