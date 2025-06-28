import cv2
import dlib
import sys
import numpy as np
import face_recognition
from imutils import face_utils
from scipy.spatial import distance as dist
import json

class EnhancedLivenessDetector:
    def __init__(self):
        self.EYE_AR_THRESH = 0.25
        self.EYE_AR_CONSEC_FRAMES = 3
        self.BLINK_COUNT_GOAL = 2
        self.MOVEMENT_THRESHOLD = 20  # pixels
        
        # Initialize face detection
        self.detector = dlib.get_frontal_face_detector()
        self.predictor = dlib.shape_predictor("liveness-model/shape_predictor_68_face_landmarks.dat")
        
        # Eye landmark indices
        self.left_eye_start, self.left_eye_end = 42, 48
        self.right_eye_start, self.right_eye_end = 36, 42
        
        # Nose tip landmark index for movement detection
        self.nose_tip_index = 30
        
    def eye_aspect_ratio(self, eye):
        """Calculate eye aspect ratio for blink detection"""
        A = dist.euclidean(eye[1], eye[5])
        B = dist.euclidean(eye[2], eye[4])
        C = dist.euclidean(eye[0], eye[3])
        ear = (A + B) / (2.0 * C)
        return ear
    
    def detect_blinks(self, frames):
        """Detect blinks in video frames"""
        blink_counter = 0
        frame_counter = 0
        
        for frame in frames:
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            rects = self.detector(gray, 0)
            
            if len(rects) == 0:
                continue
                
            for rect in rects:
                shape = self.predictor(gray, rect)
                shape = face_utils.shape_to_np(shape)
                
                left_eye = shape[self.left_eye_start:self.left_eye_end]
                right_eye = shape[self.right_eye_start:self.right_eye_end]
                
                left_ear = self.eye_aspect_ratio(left_eye)
                right_ear = self.eye_aspect_ratio(right_eye)
                ear = (left_ear + right_ear) / 2.0
                
                if ear < self.EYE_AR_THRESH:
                    frame_counter += 1
                else:
                    if frame_counter >= self.EYE_AR_CONSEC_FRAMES:
                        blink_counter += 1
                    frame_counter = 0
        
        return blink_counter >= self.BLINK_COUNT_GOAL
    
    def detect_head_movement(self, frames):
        """Detect left and right head movements"""
        nose_positions = []
        
        for frame in frames:
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            rects = self.detector(gray, 0)
            
            if len(rects) == 0:
                continue
                
            for rect in rects:
                shape = self.predictor(gray, rect)
                shape = face_utils.shape_to_np(shape)
                nose_tip = shape[self.nose_tip_index]
                nose_positions.append(nose_tip[0])  # x-coordinate
        
        if len(nose_positions) < 10:  # Need enough frames
            return False, False
        
        # Analyze movement patterns
        positions = np.array(nose_positions)
        center_position = np.mean(positions)
        
        # Check for significant left movement (nose moves right in image)
        max_right = np.max(positions)
        moved_right = (max_right - center_position) > self.MOVEMENT_THRESHOLD
        
        # Check for significant right movement (nose moves left in image)
        min_left = np.min(positions)
        moved_left = (center_position - min_left) > self.MOVEMENT_THRESHOLD
        
        return moved_left, moved_right
    
    def match_face_with_profile(self, frames, profile_image_path):
        """Match face in video with profile picture"""
        try:
            # Load and encode the profile picture
            profile_image = face_recognition.load_image_file(profile_image_path)
            profile_encodings = face_recognition.face_encodings(profile_image)
            
            if len(profile_encodings) == 0:
                return False, "No face found in profile picture"
            
            profile_encoding = profile_encodings[0]
            
            # Check multiple frames from the video
            matches = []
            for i in range(0, len(frames), len(frames)//5):  # Check 5 frames
                frame = frames[i]
                rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                
                # Find face encodings in current frame
                face_encodings = face_recognition.face_encodings(rgb_frame)
                
                for face_encoding in face_encodings:
                    # Compare with profile picture
                    match = face_recognition.compare_faces([profile_encoding], face_encoding, tolerance=0.6)
                    matches.append(match[0])
            
            # Require at least 60% of frames to match
            match_percentage = sum(matches) / len(matches) if matches else 0
            return match_percentage >= 0.6, f"Match confidence: {match_percentage:.2%}"
            
        except Exception as e:
            return False, f"Face matching error: {str(e)}"
    
    def process_video(self, video_path, profile_image_path):
        """Process video for complete liveness verification"""
        cap = cv2.VideoCapture(video_path)
        frames = []
        
        # Read all frames
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            frames.append(frame)
        
        cap.release()
        
        if len(frames) < 30:  # Need at least 1 second of video at 30fps
            return False, "Video too short for verification"
        
        results = {}
        
        # 1. Check blinks
        blink_success = self.detect_blinks(frames)
        results['blinks'] = blink_success
        
        # 2. Check head movements
        moved_left, moved_right = self.detect_head_movement(frames)
        results['moved_left'] = moved_left
        results['moved_right'] = moved_right
        
        # 3. Face matching
        face_match, match_info = self.match_face_with_profile(frames, profile_image_path)
        results['face_match'] = face_match
        results['match_info'] = match_info
        
        # Overall success
        overall_success = (blink_success and moved_left and moved_right and face_match)
        
        return overall_success, results

def main():
    if len(sys.argv) != 3:
        print("Usage: python enhanced_liveness_check.py <video_path> <profile_image_path>")
        sys.exit(1)
    
    video_path = sys.argv[1]
    profile_image_path = sys.argv[2]
    
    detector = EnhancedLivenessDetector()
    success, results = detector.process_video(video_path, profile_image_path)
    
    if success:
        print("VERIFICATION_SUCCESS")
        print(f"All checks passed: {json.dumps(results)}")
        sys.exit(0)
    else:
        print("VERIFICATION_FAILED")
        print(f"Failed checks: {json.dumps(results)}")
        sys.exit(1)

if __name__ == "__main__":
    main()