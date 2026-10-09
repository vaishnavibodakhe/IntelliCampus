# 🧠 IntelliCampus - AI & Machine Learning Microservice

[![Python 3.11](https://img.shields.io/badge/Python-3.11-blue.svg?logo=python)](https://www.python.org/)
[![FastAPI](https://img.shields.io/badge/Framework-FastAPI-009688.svg?logo=fastapi)](https://fastapi.tiangolo.com/)
[![InsightFace](https://img.shields.io/badge/Computer%20Vision-InsightFace-red.svg)](https://github.com/deepinsight/insightface)
[![scikit-learn](https://img.shields.io/badge/ML-scikit--learn-F7931E.svg?logo=scikit-learn)](https://scikit-learn.org/)
[![Docker](https://img.shields.io/badge/Container-Docker-2496ED.svg?logo=docker)](https://www.docker.com/)

---

## 📖 Overview

The **IntelliCampus AI Service** is a dedicated, internal microservice developed in **Python 3.11** using **FastAPI**. It encapsulates computer vision and machine learning tasks:
1. **Facial Detection & Embedding**: Detects student faces in classroom photos, generating 512-dimensional normalized ArcFace embeddings.
2. **Liveness & Anti-Spoofing Analysis**: Evaluates faces for presentation attacks (printed photographs, digital screens, replays).
3. **Student Attrition Risk Prediction (EWS)**: Analyzes multi-factor student indicators using a Random Forest classifier to identify at-risk students and compute explainable feature importances.

To safeguard student privacy and comply with **DPDP Act 2023**, the AI service processes images **exclusively in ephemeral memory**; no image files or raw pixels are stored on disk or in caches.

---

## 📡 Service REST Contracts

The AI service operates strictly inside the internal private network (`port 8000`) and is never directly exposed to external public clients.

### 1. Multi-Face Detection & Analysis (`POST /ai/analyze`)

Extracts all detected faces from the teacher's classroom photograph, scoring each for image quality and spoof probability.

- **Request**: `multipart/form-data`
  - `image`: Binary image file (JPEG / PNG, maximum 15 MB)
  - `sessionId`: Session UUID or integer identifier

- **Response Payload**:
```json
{
  "sessionId": "51",
  "faceCount": 2,
  "faces": [
    {
      "faceId": 1,
      "bbox": [120, 80, 210, 190],
      "quality": 0.91,
      "spoofScore": 0.97,
      "embedding": [
        0.0124, -0.0441, 0.1032, 0.0051, 
        /* ... 512 normalized floating-point numbers ... */
      ]
    },
    {
      "faceId": 2,
      "bbox": [330, 95, 405, 180],
      "quality": 0.62,
      "spoofScore": 0.41,
      "embedding": [
        0.0712, 0.0201, -0.0184, 0.0891,
        /* ... 512 normalized floating-point numbers ... */
      ]
    }
  ]
}
```

- **Field Definitions**:
  - `bbox`: Coordinates `[x_min, y_min, x_max, y_max]` in pixels.
  - `quality`: Scaled metric ($0.0 - 1.0$) evaluating sharpness, face size, and illumination.
  - `spoofScore`: Probability ($0.0 - 1.0$) that the face is a living person. Values below $0.60$ indicate digital screens or printed media.
  - `embedding`: 512-dimensional vector normalized to unit length for cosine distance comparisons in PostgreSQL `pgvector`.

---

### 2. Early Warning Risk Scoring (`POST /ai/risk`)

Evaluates student telemetry against trained machine learning models to forecast academic difficulty.

- **Request Payload**:
```json
{
  "students": [
    {
      "studentId": 1042,
      "attendancePercentage": 61.5,
      "attendanceDelta4Weeks": -14.2,
      "internalMarksPercentage": 42.0,
      "marksDeltaPrevious": -18.5,
      "backlogCount": 2,
      "leaveDaysLast30Days": 6
    }
  ]
}
```

- **Response Payload**:
```json
{
  "results": [
    {
      "studentId": 1042,
      "riskScore": 0.78,
      "riskLevel": "HIGH",
      "topFactors": [
        "Internal assessment dropped 18.5 points from previous term",
        "Attendance percentage is 61.5% (below 75% threshold)",
        "Attendance dropped 14.2% over past 4 weeks"
      ]
    }
  ]
}
```

---

## 🧮 Model Architectures & Pipelines

### Facial Feature Extraction (InsightFace)
- Utilizes the **InsightFace Buffalo_l** model pack running **ArcFace** on ONNX Runtime.
- Crops face regions aligned using a 5-point facial landmark detector.
- Outputs 512-dimensional $L_2$-normalized vector representations.

### Anti-Spoofing Neural Network
- Employs a multi-scale CNN trained on presentation attack benchmarks.
- Evaluates frequency analysis and moiré screen textures to distinguish live skin from LCD/OLED mobile screens or photo printouts.

### Early Warning Risk Classifier (EWS)
- **Primary Model**: Scikit-Learn **Random Forest Classifier** with balanced class weights.
- **Explainability**: Computes per-feature contributions using Tree SHAP / feature importances, translating top statistical weights into human-readable sentences for mentors.
- **Baseline**: Regularized **Logistic Regression** model for transparent sanity validation.

---

## 🛠️ Local Development & Setup

### Requirements
- Python 3.11
- CMake and C++ build tools (required for dlib / ONNX extensions)

### 1. Virtual Environment Setup
```bash
cd ai-service

# Create virtual environment
python -m venv .venv

# Activate on Linux/macOS:
source .venv/bin/activate
# Activate on Windows:
.venv\Scripts\activate

# Install dependencies
pip install --upgrade pip
pip install -r requirements.txt
```

### 2. Run the Service Locally
```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Interactive OpenAPI documentation is available at `http://localhost:8000/docs`.

---

## 🐳 Docker Deployment

The service is packaged as a slim, containerized microservice:

```bash
docker build -t intellicampus-ai:latest .
docker run -p 8000:8000 intellicampus-ai:latest
```
