# Pensioner Verification System

A comprehensive verification system for pensioners with face liveness detection, built using Angular, Spring Boot, and Python.

## 🏗️ Architecture

- **Frontend**: Angular (TypeScript)
- **Backend**: Spring Boot (Java with Maven)
- **Liveness Detection**: Python with OpenCV and dlib
- **Database**: Configured via Spring Boot (check application.properties)

## 📁 Project Structure

```
pensioner-verification-system/
├── frontend/           # Angular application
├── backend/            # Spring Boot REST API
└── liveness-model/     # Python face liveness detection
```

## 🚀 Quick Start

### Prerequisites

- **Node.js** (v16 or higher)
- **Java** (JDK 11 or higher)
- **Maven** (3.6 or higher)
- **Python** (3.8 or higher)
- **Conda** (recommended for Python environment)

### 1. Backend Setup (Spring Boot)

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

The backend will start on `http://localhost:8080`

### 2. Frontend Setup (Angular)

```bash
cd frontend
npm install
ng serve
```

The frontend will start on `http://localhost:4200`

### 3. Liveness Detection Setup (Python)

```bash
cd liveness-model

# Using conda (recommended)
chmod +x conda_install.sh
./conda_install.sh

# Or using pip
pip install -r requirements.txt
```

## 📋 Features

- **User Registration & Login**: Secure authentication system
- **Profile Management**: User profile creation and updates
- **Face Liveness Detection**: Anti-spoofing verification using computer vision
- **File Upload**: Support for image and video uploads
- **RESTful API**: Clean API endpoints for frontend-backend communication

## 🔧 Configuration

### Backend Configuration

Edit `backend/src/main/resources/application.properties`:

```properties
# Database configuration
spring.datasource.url=jdbc:your-database-url
spring.datasource.username=your-username
spring.datasource.password=your-password

# File upload settings
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

### Frontend Configuration

The Angular app connects to the backend API. Update API endpoints in your service files if needed.

## 🧪 Testing

### Backend Tests

```bash
cd backend
mvn test
```

### Frontend Tests

```bash
cd frontend
ng test
```

### Liveness Model Testing

```bash
cd liveness-model
python enhanced_liveness_check.py
```

## 📚 API Endpoints

### User Management

- `POST /api/users/register` - User registration
- `POST /api/users/login` - User login
- `GET /api/users/profile` - Get user profile

### Verification

- `POST /api/verification/upload` - Upload verification documents
- `POST /api/verification/liveness` - Perform liveness check

## 🤝 Development Workflow

1. **Backend Development**: Make changes in `backend/src/main/java/`
2. **Frontend Development**: Develop components in `frontend/src/app/components/`
3. **Liveness Model**: Enhance detection algorithms in `liveness-model/`

## 📦 Deployment

### Backend Deployment

```bash
cd backend
mvn clean package
java -jar target/pensioner-verification-*.jar
```

### Frontend Deployment

```bash
cd frontend
ng build --prod
# Deploy dist/ folder to your web server
```

## 🔒 Security Considerations

- Implement proper authentication and authorization
- Validate all file uploads
- Sanitize user inputs
- Use HTTPS in production
- Secure database credentials

## 🐛 Troubleshooting

### Common Issues

**Backend not starting:**

- Check Java version compatibility
- Verify database connection settings
- Ensure port 8080 is available

**Frontend build errors:**

- Run `npm install` to ensure dependencies are installed
- Check Node.js version compatibility

**Python liveness detection issues:**

- Ensure OpenCV and dlib are properly installed
- Check camera permissions
- Verify shape_predictor model file exists

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📞 Support

For support and questions, please open an issue in the GitHub repository.

---

**Note**: Make sure to configure your database connection and update API endpoints according to your deployment environment.
