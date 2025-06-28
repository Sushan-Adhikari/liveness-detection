// profile.component.ts
import { Component, ElementRef, ViewChild, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';

interface VerificationStep {
  title: string;
  description: string;
  duration: number;
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
  
  isVerifying = false;
  verificationComplete = false;
  verificationSuccess = false;
  verificationMessage = '';
  
  currentStep = 0;
  progressPercentage = 0;
  
  mediaRecorder: MediaRecorder | undefined;
  recordedChunks: Blob[] = [];
  stream: MediaStream | undefined;
  
  verificationSteps: VerificationStep[] = [
    {
      title: 'Look at the camera',
      description: 'Position your face in the center of the screen and look directly at the camera.',
      duration: 3
    },
    {
      title: 'Blink twice',
      description: 'Please blink your eyes twice clearly. Make sure your blinks are distinct.',
      duration: 5
    },
    {
      title: 'Turn your head left',
      description: 'Slowly turn your head to the left, then return to center.',
      duration: 4
    },
    {
      title: 'Turn your head right',
      description: 'Slowly turn your head to the right, then return to center.',
      duration: 4
    },
    {
      title: 'Final verification',
      description: 'Hold still while we verify your identity. Almost done!',
      duration: 2
    }
  ];

  constructor(private http: HttpClient) {}

  get currentInstruction(): VerificationStep {
    return this.verificationSteps[this.currentStep] || this.verificationSteps[0];
  }

  async startVerification() {
    try {
      this.isVerifying = true;
      this.currentStep = 0;
      this.progressPercentage = 0;
      this.recordedChunks = [];

      this.stream = await navigator.mediaDevices.getUserMedia({ 
        video: { width: 640, height: 480 },
        audio: false 
      });

      if (this.videoElement) {
        this.videoElement.nativeElement.srcObject = this.stream;
        
        this.mediaRecorder = new MediaRecorder(this.stream, {
          mimeType: 'video/webm;codecs=vp9'
        });
        
        this.mediaRecorder.ondataavailable = (event) => {
          if (event.data.size > 0) {
            this.recordedChunks.push(event.data);
          }
        };

        this.mediaRecorder.onstop = () => {
          this.processRecording();
        };

        this.mediaRecorder.start();
        this.startStepProgression();
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
      this.progressPercentage = (elapsedTime / totalDuration) * 100;

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

  processRecording() {
    if (this.recordedChunks.length === 0) {
      this.handleVerificationError('No video data recorded');
      return;
    }

    const videoBlob = new Blob(this.recordedChunks, { type: 'video/webm' });
    this.sendForVerification(videoBlob);
  }

  sendForVerification(videoBlob: Blob) {
    const username = localStorage.getItem('currentUser') || 'testuser';
    
    const formData = new FormData();
    formData.append('video', videoBlob, 'verification.webm');

    this.http.post(`http://localhost:8080/api/verify/${username}`, formData, {
      responseType: 'text'
    }).subscribe({
      next: (response) => {
        this.verificationSuccess = true;
        this.verificationMessage = response;
        this.verificationComplete = true;
        this.isVerifying = false;
      },
      error: (error) => {
        this.verificationSuccess = false;
        this.verificationMessage = error.error || 'Verification failed. Please try again.';
        this.verificationComplete = true;
        this.isVerifying = false;
      }
    });
  }

  handleVerificationError(message: string) {
    this.verificationSuccess = false;
    this.verificationMessage = message;
    this.verificationComplete = true;
    this.isVerifying = false;
    this.stopCamera();
  }

  resetVerification() {
    this.isVerifying = false;
    this.verificationComplete = false;
    this.verificationSuccess = false;
    this.verificationMessage = '';
    this.currentStep = 0;
    this.progressPercentage = 0;
    this.recordedChunks = [];
    this.stopCamera();
  }

  ngOnDestroy() {
    this.stopCamera();
  }
}