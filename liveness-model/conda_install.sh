#!/bin/bash

# Cross-platform conda installation script for liveness detection
# Works on Windows (Git Bash/WSL), Linux, and macOS (Intel/Apple Silicon)

set -e  # Exit on any error

echo "🔍 Detecting operating system..."

# Detect OS and architecture
OS="$(uname -s)"
ARCH="$(uname -m)"

case "${OS}" in
    Linux*)     MACHINE=Linux;;
    Darwin*)    MACHINE=Mac;;
    CYGWIN*|MINGW*|MSYS*) MACHINE=Windows;;
    *)          MACHINE="UNKNOWN:${OS}"
esac

echo "📋 Detected: ${MACHINE} (${ARCH})"

# Function to download and install Miniconda
install_miniconda() {
    echo "📦 Installing Miniconda..."
    
    case "${MACHINE}" in
        Linux)
            if [[ "${ARCH}" == "x86_64" ]]; then
                MINICONDA_URL="https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh"
            elif [[ "${ARCH}" == "aarch64" ]]; then
                MINICONDA_URL="https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-aarch64.sh"
            else
                echo "❌ Unsupported Linux architecture: ${ARCH}"
                exit 1
            fi
            ;;
        Mac)
            if [[ "${ARCH}" == "arm64" ]]; then
                MINICONDA_URL="https://repo.anaconda.com/miniconda/Miniconda3-latest-MacOSX-arm64.sh"
            elif [[ "${ARCH}" == "x86_64" ]]; then
                MINICONDA_URL="https://repo.anaconda.com/miniconda/Miniconda3-latest-MacOSX-x86_64.sh"
            else
                echo "❌ Unsupported macOS architecture: ${ARCH}"
                exit 1
            fi
            ;;
        Windows)
            echo "🪟 For Windows, please install Miniconda manually from:"
            echo "   https://repo.anaconda.com/miniconda/Miniconda3-latest-Windows-x86_64.exe"
            echo "   Then run this script again in Git Bash or WSL."
            exit 1
            ;;
        *)
            echo "❌ Unsupported operating system: ${MACHINE}"
            exit 1
            ;;
    esac
    
    # Download and install Miniconda
    curl -L -O "${MINICONDA_URL}"
    INSTALLER_NAME=$(basename "${MINICONDA_URL}")
    bash "${INSTALLER_NAME}" -b -p "$HOME/miniconda3"
    
    # Clean up installer
    rm "${INSTALLER_NAME}"
    
    # Add conda to PATH
    export PATH="$HOME/miniconda3/bin:$PATH"
    echo "✅ Miniconda installed successfully!"
    echo "🔄 Please restart your terminal and run this script again."
    exit 0
}

# Function to download shape predictor model
download_shape_predictor() {
    echo "📥 Downloading shape predictor model..."
    
    MODEL_URL="http://dlib.net/files/shape_predictor_68_face_landmarks.dat.bz2"
    MODEL_FILE="shape_predictor_68_face_landmarks.dat"
    
    # Check if model already exists
    if [ -f "${MODEL_FILE}" ]; then
        echo "✅ Shape predictor model already exists: ${MODEL_FILE}"
        return 0
    fi
    
    # Download the compressed model
    echo "📡 Downloading from: ${MODEL_URL}"
    if command -v curl &> /dev/null; then
        curl -L -O "${MODEL_URL}"
    elif command -v wget &> /dev/null; then
        wget "${MODEL_URL}"
    else
        echo "❌ Neither curl nor wget found. Please install one of them."
        exit 1
    fi
    
    # Extract the model
    COMPRESSED_FILE="shape_predictor_68_face_landmarks.dat.bz2"
    if [ -f "${COMPRESSED_FILE}" ]; then
        echo "📦 Extracting model file..."
        
        # Check if bzip2 is available
        if command -v bzip2 &> /dev/null; then
            bzip2 -d "${COMPRESSED_FILE}"
        elif command -v bunzip2 &> /dev/null; then
            bunzip2 "${COMPRESSED_FILE}"
        elif python3 -c "import bz2" 2>/dev/null; then
            echo "Using Python to extract..."
            python3 -c "
import bz2
import shutil
with bz2.BZ2File('${COMPRESSED_FILE}', 'rb') as f_in:
    with open('${MODEL_FILE}', 'wb') as f_out:
        shutil.copyfileobj(f_in, f_out)
"
        else
            echo "❌ No bzip2 extractor found. Please install bzip2 or Python3."
            exit 1
        fi
        
        # Verify extraction
        if [ -f "${MODEL_FILE}" ]; then
            echo "✅ Model extracted successfully: ${MODEL_FILE}"
            # Clean up compressed file
            rm -f "${COMPRESSED_FILE}"
        else
            echo "❌ Failed to extract model file"
            exit 1
        fi
    else
        echo "❌ Failed to download compressed model file"
        exit 1
    fi
}

# Check if conda is available
if ! command -v conda &> /dev/null; then
    echo "❌ Conda not found."
    install_miniconda
fi

echo "🐍 Conda found. Proceeding with environment setup..."

# Remove existing virtual environment
if [ -d "venv" ]; then
    echo "🧹 Removing existing virtual environment..."
    rm -rf venv
fi

# Create conda environment
echo "🏗️ Creating conda environment..."
conda create -n liveness-env python=3.11 -y

# Activate conda environment
echo "🔄 Activating conda environment..."
# Handle different conda initialization methods
if [ -f "$(conda info --base)/etc/profile.d/conda.sh" ]; then
    source "$(conda info --base)/etc/profile.d/conda.sh"
elif [ -f "$HOME/miniconda3/etc/profile.d/conda.sh" ]; then
    source "$HOME/miniconda3/etc/profile.d/conda.sh"
elif [ -f "$HOME/anaconda3/etc/profile.d/conda.sh" ]; then
    source "$HOME/anaconda3/etc/profile.d/conda.sh"
fi

conda activate liveness-env

# Install packages using conda-forge (pre-compiled)
echo "📦 Installing packages from conda-forge..."
conda install -c conda-forge -y \
    opencv \
    dlib \
    numpy \
    scipy \
    pip \
    bzip2

# Install additional packages using pip
echo "📦 Installing additional packages with pip..."
pip install face-recognition imutils

# Download and extract shape predictor model
download_shape_predictor

# Test installation
echo "🧪 Testing installation..."
python -c "
import sys
try:
    import cv2, dlib, face_recognition, numpy as np, imutils, scipy
    print('✅ All packages imported successfully!')
    print(f'🐍 Python: {sys.version.split()[0]}')
    print(f'🔢 NumPy: {np.__version__}')
    print(f'👁️ OpenCV: {cv2.__version__}')
    print(f'🤖 dlib: {dlib.version}')
    
    # Test shape predictor model
    import os
    model_path = 'shape_predictor_68_face_landmarks.dat'
    if os.path.exists(model_path):
        print(f'📊 Shape predictor model: ✅ Found ({os.path.getsize(model_path)} bytes)')
        # Try to load the model
        predictor = dlib.shape_predictor(model_path)
        print('📊 Shape predictor model: ✅ Loaded successfully')
    else:
        print('📊 Shape predictor model: ❌ Not found')
        sys.exit(1)
        
except ImportError as e:
    print(f'❌ Import error: {e}')
    sys.exit(1)
except Exception as e:
    print(f'❌ Error: {e}')
    sys.exit(1)
"

echo ""
echo "🎉 Installation complete!"
echo ""
echo "📝 To use this environment in the future:"
echo "   conda activate liveness-env"
echo ""
echo "🚀 Your Python script should now work with:"
echo "   python enhanced_liveness_check.py"
echo ""
echo "📁 Files created:"
echo "   - shape_predictor_68_face_landmarks.dat (face landmark model)"
echo ""
echo "💡 Note: The conda environment 'liveness-env' contains all required packages."