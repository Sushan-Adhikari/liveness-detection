// profile.component.ts
import { Component, ElementRef, ViewChild, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';

interface VerificationStep {
  title: string;
  description: string;
  duration: number;
}

interface VerificationResponse {
  success: boolean;
  message: string;
  confidence: number;
  username: string;
  timestamp: number;
  verificationDetails?: any;
  diagnostics?: any;
}

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css']
})
export class ProfileComponent implements OnDestroy {
  @ViewChild('videoElement') videoElement: ElementRef | undefined;
  @ViewChild('profileImageInput') profileImageInput: ElementRef | undefined;
  
  // Verification states
  isVerifying = false;
  verificationComplete = false;
  verificationSuccess = false;
  verificationMessage = '';
  verificationDetails: any = null;
  
  // Profile picture states
  hasProfilePicture = false;
  isUploadingProfile = false;
  profileUploadMessage = '';
  selectedProfileFile: File | null = null;
  profilePreviewUrl: string | null = null;
  
  // Video recording states
  currentStep = 0;
  progressPercentage = 0;
  mediaRecorder: MediaRecorder | undefined;
  recordedChunks: Blob[] = [];
  stream: MediaStream | undefined;
  
  // Configuration
  private readonly API_BASE_URL = 'http://localhost:8080/api';
  private readonly MAX_RECORDING_DURATION = 15000; // 15 seconds
  
  verificationSteps: VerificationStep[] = [
    {
      title: 'Look at the camera',
      description: 'Position your face in the center of the screen and look directly at the camera.',
      duration: 1
    },
    {
      title: 'Blink naturally',
      description: 'Please blink your eyes 2-3 times clearly during the recording.',
      duration: 2
    },
    // {
    //   title: 'Turn your head slowly',
    //   description: 'Slowly turn your head left, then right, then return to center.',
    //   duration: 5
    // },
    {
      title: 'Final verification',
      description: 'Hold still while we complete the recording. Almost done!',
      duration: 1
    }
  ];

  constructor(private http: HttpClient) {
    this.checkVerificationStatus();
  }

  get currentInstruction(): VerificationStep {
    return this.verificationSteps[this.currentStep] || this.verificationSteps[0];
  }

  get currentUser(): string {
    return localStorage.getItem('currentUser') || 'testuser';
  }

  // Profile picture methods
  onProfileImageSelect(event: any) {
    const file = event.target.files[0];
    if (file) {
      // Validate file type
      if (!file.type.startsWith('image/')) {
        alert('Please select a valid image file.');
        return;
      }
      
      // Validate file size (10MB)
      if (file.size > 10 * 1024 * 1024) {
        alert('Image file too large. Maximum size is 10MB.');
        return;
      }
      
      this.selectedProfileFile = file;
      
      // Create preview
      const reader = new FileReader();
      reader.onload = (e) => {
        this.profilePreviewUrl = e.target?.result as string;
      };
      reader.readAsDataURL(file);
    }
  }

  async uploadProfilePicture() {
    if (!this.selectedProfileFile) {
      alert('Please select an image file first.');
      return;
    }

    this.isUploadingProfile = true;
    this.profileUploadMessage = '';

    const formData = new FormData();
    formData.append('image', this.selectedProfileFile);

    try {
      const response = await this.http.post<any>(
        `${this.API_BASE_URL}/upload-profile-picture/${this.currentUser}`,
        formData
      ).toPromise();

      if (response.success) {
        this.hasProfilePicture = true;
        this.profileUploadMessage = 'Profile picture uploaded successfully!';
        this.selectedProfileFile = null;
        this.profilePreviewUrl = null;
        
        // Reset file input
        if (this.profileImageInput) {
          this.profileImageInput.nativeElement.value = '';
        }
      } else {
        this.profileUploadMessage = response.message || 'Upload failed';
      }
    } catch (error: any) {
      this.profileUploadMessage = error.error?.message || 'Upload failed. Please try again.';
    } finally {
      this.isUploadingProfile = false;
    }
  }

  // Verification status check
  async checkVerificationStatus() {
    try {
      const response = await this.http.get<any>(
        `${this.API_BASE_URL}/verification-status/${this.currentUser}`
      ).toPromise();

      this.hasProfilePicture = response.hasProfilePicture;
    } catch (error) {
      console.error('Error checking verification status:', error);
    }
  }

  // Video recording methods
  async startVerification() {
    if (!this.hasProfilePicture) {
      alert('Please upload a profile picture before starting verification.');
      return;
    }

    try {
      this.isVerifying = true;
      this.currentStep = 0;
      this.progressPercentage = 0;
      this.recordedChunks = [];
      this.verificationComplete = false;
      this.verificationSuccess = false;
      this.verificationMessage = '';

      // Request camera access
      this.stream = await navigator.mediaDevices.getUserMedia({ 
        video: { 
          width: { ideal: 640 },
          height: { ideal: 480 },
          facingMode: 'user'
        },
        audio: false 
      });

      if (this.videoElement) {
        this.videoElement.nativeElement.srcObject = this.stream;
        
        // Setup MediaRecorder
        const options = {
          mimeType: 'video/webm;codecs=vp9'
        };
        
        // Fallback to basic webm if vp9 not supported
        if (!MediaRecorder.isTypeSupported(options.mimeType)) {
          options.mimeType = 'video/webm';
        }
        
        // Final fallback
        if (!MediaRecorder.isTypeSupported(options.mimeType)) {
          options.mimeType = 'video/mp4';
        }

        this.mediaRecorder = new MediaRecorder(this.stream, options);
        
        this.mediaRecorder.ondataavailable = (event) => {
          if (event.data.size > 0) {
            this.recordedChunks.push(event.data);
          }
        };

        this.mediaRecorder.onstop = () => {
          this.processRecording();
        };

        // Start recording
        this.mediaRecorder.start();
        this.startStepProgression();
        
        // Safety timeout to prevent infinite recording
        setTimeout(() => {
          if (this.mediaRecorder && this.mediaRecorder.state === 'recording') {
            this.stopRecording();
          }
        }, this.MAX_RECORDING_DURATION);
      }
    } catch (error) {
      console.error('Error starting verification:', error);
      this.handleVerificationError('Failed to access camera. Please ensure camera permissions are granted.');
    }
  }

  startStepProgression() {
    const totalDuration = this.verificationSteps.reduce((sum, step) => sum + step.duration, 0);
    let elapsedTime = 0;

    const interval = setInterval(() => {
      elapsedTime += 0.1;
      this.progressPercentage = Math.min(100, (elapsedTime / totalDuration) * 100);

      let stepStartTime = 0;
      for (let i = 0; i < this.currentStep; i++) {
        stepStartTime += this.verificationSteps[i].duration;
      }

      if (elapsedTime >= stepStartTime + this.currentInstruction.duration) {
        if (this.currentStep < this.verificationSteps.length - 1) {
          this.currentStep++;
        } else {
          clearInterval(interval);
          this.stopRecording();
        }
      }
    }, 100);
  }

  stopRecording() {
    if (this.mediaRecorder && this.mediaRecorder.state === 'recording') {
      this.mediaRecorder.stop();
    }
    this.stopCamera();
  }

  stopVerification() {
    this.stopRecording();
    this.resetVerification();
  }

  stopCamera() {
    if (this.stream) {
      this.stream.getTracks().forEach(track => track.stop());
      this.stream = undefined;
    }
  }

  async processRecording() {
    if (this.recordedChunks.length === 0) {
      this.handleVerificationError('No video data recorded. Please try again.');
      return;
    }

    try {
      const videoBlob = new Blob(this.recordedChunks, { type: 'video/webm' });
      
      // Check if video is too small
      if (videoBlob.size < 1024) { // Less than 1KB
        this.handleVerificationError('Video recording is too short. Please try again.');
        return;
      }

      await this.sendForVerification(videoBlob);
    } catch (error) {
      this.handleVerificationError('Error processing video recording. Please try again.');
    }
  }

  async sendForVerification(videoBlob: Blob) {
    const formData = new FormData();
    formData.append('video', videoBlob, 'verification.webm');

    try {
      const response = await this.http.post<VerificationResponse>(
        `${this.API_BASE_URL}/verify/${this.currentUser}`,
        formData
      ).toPromise();

      if (response) {
        this.verificationSuccess = response.success;
        this.verificationMessage = response.message;
        this.verificationDetails = response.verificationDetails || response.diagnostics;
        this.verificationComplete = true;
        this.isVerifying = false;
      }
    } catch (error: any) {
      this.handleVerificationError(
        error.error?.message || 'Verification failed. Please try again.'
      );
    }
  }

  handleVerificationError(message: string) {
    this.verificationComplete = true;
    this.verificationSuccess = false;
    this.verificationMessage = message;
    this.isVerifying = false;
    this.stopCamera();
  }

  resetVerification() {
    this.isVerifying = false;
    this.verificationComplete = false;
    this.verificationSuccess = false;
    this.verificationMessage = '';
    this.verificationDetails = null;
    this.currentStep = 0;
    this.progressPercentage = 0;
    this.recordedChunks = [];
    this.stopCamera();
  }

  ngOnDestroy() {
    this.stopCamera();
  }
}