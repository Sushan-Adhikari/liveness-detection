package com.example.pensionerverification.controller;

import com.example.pensionerverification.model.User;
import com.example.pensionerverification.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
public class VerificationController {

    @Autowired
    private UserRepository userRepository;

    private final Path tempDir = Paths.get("temp-videos");

    public VerificationController() {
        try {
            Files.createDirectories(tempDir);
        } catch (Exception e) {
            throw new RuntimeException("Could not create temp directory!");
        }
    }

    @PostMapping("/verify/{username}")
    public ResponseEntity<?> verifyLiveness(@PathVariable String username,
            @RequestParam("video") MultipartFile videoFile) {
        try {
            // Find user and get their profile picture path
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (!userOpt.isPresent()) {
                return new ResponseEntity<>("User not found", HttpStatus.NOT_FOUND);
            }

            User user = userOpt.get();
            String profilePicturePath = user.getProfilePicturePath();

            // Save the video file temporarily
            String videoFileName = "verification_" + System.currentTimeMillis() + ".webm";
            Path videoPath = tempDir.resolve(videoFileName);
            Files.copy(videoFile.getInputStream(), videoPath);

            // Execute the enhanced Python script
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "python",
                    "liveness-model/enhanced_liveness_check.py",
                    videoPath.toString(),
                    profilePicturePath);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();

            // Clean up temporary file
            Files.deleteIfExists(videoPath);

            if (exitCode == 0) {
                return new ResponseEntity<>("Verification successful! " + output.toString(), HttpStatus.OK);
            } else {
                return new ResponseEntity<>("Liveness check failed: " + output.toString(), HttpStatus.BAD_REQUEST);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error during verification: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}