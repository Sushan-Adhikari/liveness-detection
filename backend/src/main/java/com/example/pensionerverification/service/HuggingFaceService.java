package com.example.pensionerverification.service;

import com.example.pensionerverification.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class HuggingFaceService {

    @Value("${huggingface.api.url:https://sushanadhikari-lightweight-liveliness.hf.space}")
    private String huggingFaceApiUrl;

    @Value("${huggingface.api.token:}")
    private String huggingFaceToken;

    @Value("${upload.dir:uploads}")
    private String uploadDir;

    @Value("${temp.dir:temp-videos}")
    private String tempDir;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public HuggingFaceService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public static class LivenessVerificationResult {
        private boolean verified;
        private double confidence;
        private String reason;
        private Map<String, Object> details;

        public LivenessVerificationResult(boolean verified, double confidence, String reason,
                Map<String, Object> details) {
            this.verified = verified;
            this.confidence = confidence;
            this.reason = reason;
            this.details = details;
        }

        public boolean isVerified() {
            return verified;
        }

        public double getConfidence() {
            return confidence;
        }

        public String getReason() {
            return reason;
        }

        public Map<String, Object> getDetails() {
            return details;
        }
    }

    public LivenessVerificationResult verifyLiveness(User user, MultipartFile videoFile) throws IOException {
        try {
            // Create directories if they don't exist
            createDirectories();

            // Save video to temporary location
            String videoPath = saveTemporaryVideo(videoFile);

            // Get profile picture path
            String profilePicturePath = getFullProfilePicturePath(user);

            // Call HuggingFace API using the gradio_client pattern
            LivenessVerificationResult result = callHuggingFaceAPI(profilePicturePath, videoPath);

            // Clean up temporary video file
            cleanupTemporaryFile(videoPath);

            return result;

        } catch (Exception e) {
            throw new IOException("Failed to verify liveness: " + e.getMessage(), e);
        }
    }

    private LivenessVerificationResult callHuggingFaceAPI(String profileImagePath, String videoPath)
            throws IOException {
        try {
            // Prepare the API request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // Add HuggingFace token if available
            if (huggingFaceToken != null && !huggingFaceToken.isEmpty()) {
                headers.setBearerAuth(huggingFaceToken);
            }

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // Add profile image file
            body.add("profile_image", new FileSystemResource(profileImagePath));

            // Add video file with the correct structure expected by the API
            Map<String, Object> videoData = new HashMap<>();
            videoData.put("video", new FileSystemResource(videoPath));
            videoData.put("subtitles", null); // No subtitles
            body.add("video_file", videoData);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // Make the API call to the predict endpoint
            String apiEndpoint = huggingFaceApiUrl + "/call/predict";
            ResponseEntity<String> response = restTemplate.postForEntity(apiEndpoint, requestEntity, String.class);

            // Parse response
            return parseHuggingFaceResponse(response.getBody());

        } catch (Exception e) {
            // Try alternative approach with direct file upload
            return callHuggingFaceAPIAlternative(profileImagePath, videoPath);
        }
    }

    private LivenessVerificationResult callHuggingFaceAPIAlternative(String profileImagePath, String videoPath)
            throws IOException {
        try {
            // Alternative approach: use the direct API endpoint format
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            if (huggingFaceToken != null && !huggingFaceToken.isEmpty()) {
                headers.setBearerAuth(huggingFaceToken);
            }

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("data", "[\"" + profileImagePath + "\", {\"video\": \"" + videoPath + "\", \"subtitles\": null}]");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // Try the API endpoint
            String apiEndpoint = huggingFaceApiUrl + "/api/predict";
            ResponseEntity<String> response = restTemplate.postForEntity(apiEndpoint, requestEntity, String.class);

            return parseHuggingFaceResponse(response.getBody());

        } catch (Exception e) {
            throw new IOException("Failed to call HuggingFace API: " + e.getMessage(), e);
        }
    }

    private LivenessVerificationResult parseHuggingFaceResponse(String responseBody) throws IOException {
        try {
            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            // Initialize default values
            boolean isVerified = false;
            double confidence = 0.0;
            String reason = "Verification completed";
            Map<String, Object> details = new HashMap<>();

            // Check if response is in the expected format
            if (jsonResponse.has("data") && jsonResponse.get("data").isArray()) {
                JsonNode dataArray = jsonResponse.get("data");
                if (dataArray.size() >= 2) {
                    String markdownResult = dataArray.get(0).asText();
                    JsonNode jsonDetails = dataArray.get(1);

                    // Parse the markdown result
                    isVerified = parseVerificationStatus(markdownResult);
                    reason = extractReasonFromMarkdown(markdownResult);

                    // Extract details from JSON
                    if (jsonDetails != null && !jsonDetails.isNull()) {
                        details = parseJsonDetails(jsonDetails);
                        confidence = extractConfidence(jsonDetails);
                    }

                    // Add the full markdown result to details
                    details.put("fullResult", markdownResult);
                }
            } else if (jsonResponse.isArray() && jsonResponse.size() >= 2) {
                // Direct array response format
                String markdownResult = jsonResponse.get(0).asText();
                JsonNode jsonDetails = jsonResponse.get(1);

                isVerified = parseVerificationStatus(markdownResult);
                reason = extractReasonFromMarkdown(markdownResult);

                if (jsonDetails != null && !jsonDetails.isNull()) {
                    details = parseJsonDetails(jsonDetails);
                    confidence = extractConfidence(jsonDetails);
                }

                details.put("fullResult", markdownResult);
            } else {
                // Fallback for unexpected format
                details.put("rawResponse", responseBody);
                reason = "Unexpected response format";
            }

            return new LivenessVerificationResult(isVerified, confidence, reason, details);

        } catch (Exception e) {
            throw new IOException("Failed to parse HuggingFace response: " + e.getMessage(), e);
        }
    }

    private boolean parseVerificationStatus(String markdownResult) {
        if (markdownResult == null)
            return false;

        // Check for verification success indicators
        return markdownResult.contains("✅ VERIFIED - GENUINE PERSON") ||
                markdownResult.contains("VERIFIED") ||
                markdownResult.contains("GENUINE");
    }

    private String extractReasonFromMarkdown(String markdownResult) {
        if (markdownResult == null)
            return "No result";

        if (markdownResult.contains("❌ VERIFICATION FAILED")) {
            return "Verification failed - please try again";
        } else if (markdownResult.contains("❌ SPOOFING SUSPECTED")) {
            return "Spoofing detected - please ensure you are a real person";
        } else if (markdownResult.contains("✅ VERIFIED")) {
            return "Verification successful - genuine person detected";
        } else {
            return "Verification completed";
        }
    }

    private double extractConfidence(JsonNode jsonDetails) {
        if (jsonDetails == null)
            return 0.0;

        if (jsonDetails.has("confidence")) {
            return jsonDetails.get("confidence").asDouble();
        }

        // Look for confidence in nested objects
        if (jsonDetails.has("details") && jsonDetails.get("details").has("confidence")) {
            return jsonDetails.get("details").get("confidence").asDouble();
        }

        return 0.0;
    }

    private Map<String, Object> parseJsonDetails(JsonNode jsonDetails) {
        Map<String, Object> details = new HashMap<>();

        try {
            if (jsonDetails.isObject()) {
                jsonDetails.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    JsonNode value = entry.getValue();

                    if (value.isTextual()) {
                        details.put(key, value.asText());
                    } else if (value.isNumber()) {
                        details.put(key, value.asDouble());
                    } else if (value.isBoolean()) {
                        details.put(key, value.asBoolean());
                    } else {
                        details.put(key, value.toString());
                    }
                });
            }
        } catch (Exception e) {
            details.put("parseError", e.getMessage());
        }

        return details;
    }

    public String saveProfilePicture(User user, MultipartFile imageFile) throws IOException {
        createDirectories();

        String fileName = UUID.randomUUID().toString() + "_" + imageFile.getOriginalFilename();
        Path filePath = Paths.get(uploadDir, fileName);

        Files.write(filePath, imageFile.getBytes());

        return fileName; // Return relative path
    }

    private String saveTemporaryVideo(MultipartFile videoFile) throws IOException {
        String fileName = UUID.randomUUID().toString() + "_" + videoFile.getOriginalFilename();
        Path filePath = Paths.get(tempDir, fileName);

        Files.write(filePath, videoFile.getBytes());

        return filePath.toString(); // Return full path
    }

    private String getFullProfilePicturePath(User user) throws IOException {
        if (user.getProfilePicturePath() == null || user.getProfilePicturePath().isEmpty()) {
            throw new IOException("User profile picture not found");
        }

        Path fullPath = Paths.get(uploadDir, user.getProfilePicturePath());
        if (!Files.exists(fullPath)) {
            throw new IOException("Profile picture file not found: " + fullPath);
        }

        return fullPath.toString();
    }

    private void createDirectories() throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        Path tempPath = Paths.get(tempDir);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        if (!Files.exists(tempPath)) {
            Files.createDirectories(tempPath);
        }
    }

    private void cleanupTemporaryFile(String filePath) {
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            // Log the error but don't throw - cleanup failure shouldn't break the main flow
            System.err.println("Failed to cleanup temporary file: " + filePath + " - " + e.getMessage());
        }
    }
}