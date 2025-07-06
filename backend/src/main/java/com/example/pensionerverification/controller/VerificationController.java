package com.example.pensionerverification.controller;

import com.example.pensionerverification.model.User;
import com.example.pensionerverification.repository.UserRepository;
import com.example.pensionerverification.service.HuggingFaceService;
import com.example.pensionerverification.service.HuggingFaceService.LivenessVerificationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
public class VerificationController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HuggingFaceService huggingFaceService;

    @PostMapping("/verify/{username}")
    public ResponseEntity<Map<String, Object>> verifyLiveness(@PathVariable String username,
            @RequestParam("video") MultipartFile videoFile) {
        try {
            // Find user
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (!userOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("User not found"));
            }

            User user = userOpt.get();

            // Validate video file
            if (videoFile.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(createErrorResponse("No video file provided"));
            }

            // Check if user has profile picture
            if (user.getProfilePicturePath() == null || user.getProfilePicturePath().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(createErrorResponse(
                                "User profile picture not found. Please upload a profile picture first."));
            }

            // Check file type
            String contentType = videoFile.getContentType();
            if (contentType == null || !contentType.startsWith("video/")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(createErrorResponse("Invalid file type. Please upload a video file."));
            }

            // Check file size (50MB limit)
            if (videoFile.getSize() > 50 * 1024 * 1024) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(createErrorResponse("Video file too large. Maximum size is 50MB."));
            }

            // Perform HuggingFace liveness verification
            LivenessVerificationResult result = huggingFaceService.verifyLiveness(user, videoFile);

            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isVerified());
            response.put("message", result.getReason());
            response.put("confidence", result.getConfidence());
            response.put("username", username);
            response.put("timestamp", System.currentTimeMillis());

            if (result.isVerified()) {
                // Add verification details for successful verification
                response.put("verificationDetails", result.getDetails());

                // Update user's last verification timestamp
                user.setLastVerificationDate(new java.util.Date());
                userRepository.save(user);

                return ResponseEntity.ok(response);
            } else {
                // Include diagnostic information for failed verification
                response.put("diagnostics", result.getDetails());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Error processing video: " + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Verification service unavailable: " + e.getMessage()));
        }
    }

    @PostMapping("/upload-profile-picture/{username}")
    public ResponseEntity<Map<String, Object>> uploadProfilePicture(@PathVariable String username,
            @RequestParam("image") MultipartFile imageFile) {
        try {
            // Find user
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (!userOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("User not found"));
            }

            User user = userOpt.get();

            // Validate image file
            if (imageFile.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(createErrorResponse("No image file provided"));
            }

            // Check file type
            String contentType = imageFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(createErrorResponse("Invalid file type. Please upload an image."));
            }

            // Check file size (10MB limit)
            if (imageFile.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(createErrorResponse("Image file too large. Maximum size is 10MB."));
            }

            // Save the profile picture
            String profilePicturePath = huggingFaceService.saveProfilePicture(user, imageFile);

            // Update user's profile picture path
            user.setProfilePicturePath(profilePicturePath);
            userRepository.save(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Profile picture uploaded successfully");
            response.put("profilePicturePath", profilePicturePath);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Error saving profile picture: " + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Service unavailable: " + e.getMessage()));
        }
    }

    @GetMapping("/verification-status/{username}")
    public ResponseEntity<Map<String, Object>> getVerificationStatus(@PathVariable String username) {
        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (!userOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("User not found"));
            }

            User user = userOpt.get();
            Map<String, Object> status = new HashMap<>();
            status.put("username", username);
            status.put("lastVerificationDate", user.getLastVerificationDate());
            status.put("verificationRequired", isVerificationRequired(user));
            status.put("hasProfilePicture",
                    user.getProfilePicturePath() != null && !user.getProfilePicturePath().isEmpty());

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Error retrieving verification status: " + e.getMessage()));
        }
    }

    private boolean isVerificationRequired(User user) {
        if (user.getLastVerificationDate() == null) {
            return true;
        }

        // Check if verification is older than 6 months (typical pension renewal period)
        long sixMonthsInMillis = 6L * 30 * 24 * 60 * 60 * 1000;
        long timeSinceLastVerification = System.currentTimeMillis() - user.getLastVerificationDate().getTime();

        return timeSinceLastVerification > sixMonthsInMillis;
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}