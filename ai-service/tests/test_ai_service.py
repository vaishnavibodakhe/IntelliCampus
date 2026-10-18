import cv2
import io
import json
import numpy as np
import pytest
from unittest.mock import MagicMock, patch
from fastapi.testclient import TestClient

import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent))

from app.main import app, get_face_analyzer

client = TestClient(app)

def create_dummy_jpeg(width=300, height=300, color=(128, 128, 128)):
    """Creates a blank solid color JPEG image in memory."""
    img = np.full((height, width, 3), color, dtype=np.uint8)
    _, buf = cv2.imencode(".jpg", img)
    return io.BytesIO(buf.tobytes())

def create_textured_jpeg(width=300, height=300):
    """Creates a non-blurry textured JPEG image in memory."""
    img = np.random.randint(0, 256, (height, width, 3), dtype=np.uint8)
    _, buf = cv2.imencode(".jpg", img)
    return io.BytesIO(buf.tobytes())


def create_mock_face(bbox, embedding, det_score=0.95):
    """Helper to mock an InsightFace detected face object."""
    mock = MagicMock()
    mock.bbox = np.array(bbox, dtype=np.float32)
    mock.det_score = float(det_score)
    normed = np.array(embedding, dtype=np.float32)
    normed = normed / np.linalg.norm(normed)
    mock.normed_embedding = normed
    mock.embedding = normed
    return mock


def test_health_check():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "UP"
    assert "buffalo_l" in data["engine"]


def test_enroll_zero_faces():
    """Testing rejection when 0 faces are detected in image."""
    dummy_img = create_dummy_jpeg(300, 300)
    # Using blank image without mocking will naturally detect 0 faces
    response = client.post(
        "/ai/enroll",
        files={"image": ("blank.jpg", dummy_img, "image/jpeg")}
    )
    assert response.status_code == 400
    assert "No face detected" in response.json()["detail"]


def test_enroll_multiple_faces_reject():
    """Testing rejection when more than 1 face is detected during enrollment."""
    dummy_img = create_dummy_jpeg(400, 400)
    emb1 = np.random.randn(512)
    emb2 = np.random.randn(512)
    mock_faces = [
        create_mock_face([50, 50, 150, 150], emb1),
        create_mock_face([200, 200, 300, 300], emb2)
    ]

    with patch("app.main.get_face_analyzer") as mock_get_analyzer:
        analyzer_mock = MagicMock()
        analyzer_mock.get.return_value = mock_faces
        mock_get_analyzer.return_value = analyzer_mock

        response = client.post(
            "/ai/enroll",
            files={"image": ("two_faces.jpg", dummy_img, "image/jpeg")}
        )
        assert response.status_code == 400
        assert "Multiple faces detected" in response.json()["detail"]


def test_enroll_single_face_success():
    """Testing successful single-face enrollment returning 512-d ArcFace embedding."""
    dummy_img = create_textured_jpeg(400, 400)
    emb = np.random.randn(512)
    mock_face = create_mock_face([50, 50, 200, 200], emb, det_score=0.96)

    with patch("app.main.get_face_analyzer") as mock_get_analyzer:
        analyzer_mock = MagicMock()
        analyzer_mock.get.return_value = [mock_face]
        mock_get_analyzer.return_value = analyzer_mock

        response = client.post(
            "/ai/enroll",
            files={"image": ("single_face.jpg", dummy_img, "image/jpeg")}
        )
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "SUCCESS"
        assert len(data["embedding"]) == 512
        assert data["quality"] == 0.96


def test_enroll_face_too_small():
    """Testing rejection when detected face bounding box is smaller than 40x40."""
    dummy_img = create_dummy_jpeg(200, 200)
    emb = np.random.randn(512)
    # 20x20 bounding box
    tiny_face = create_mock_face([10, 10, 30, 30], emb)

    with patch("app.main.get_face_analyzer") as mock_get_analyzer:
        analyzer_mock = MagicMock()
        analyzer_mock.get.return_value = [tiny_face]
        mock_get_analyzer.return_value = analyzer_mock

        response = client.post(
            "/ai/enroll",
            files={"image": ("tiny_face.jpg", dummy_img, "image/jpeg")}
        )
        assert response.status_code == 400
        assert "too small" in response.json()["detail"]


def test_recognize_matching_and_duplicate_conflict_handling():
    """
    Test classroom recognition:
    - Face 1 matches Student 101 with high similarity (0.85)
    - Face 2 also has similarity 0.75 with Student 101, but Student 101 must only match once!
    - Face 2 instead matches its next best or remains unmatched.
    """
    dummy_img = create_dummy_jpeg(640, 480)

    # Create known candidate embedding for Student 101 and 102
    target_emb = np.zeros(512, dtype=np.float32)
    target_emb[0] = 1.0  # Unit vector along axis 0

    other_emb = np.zeros(512, dtype=np.float32)
    other_emb[1] = 1.0  # Unit vector along axis 1

    candidates = [
        {"studentId": 101, "embedding": target_emb.tolist()},
        {"studentId": 102, "embedding": other_emb.tolist()}
    ]

    # Face 1: similarity ~0.95 with Student 101
    f1_emb = np.zeros(512, dtype=np.float32)
    f1_emb[0] = 0.95
    f1_emb[2] = 0.31
    f1_emb = f1_emb / np.linalg.norm(f1_emb)

    # Face 2: similarity ~0.80 with Student 101 (duplicate conflict!) and ~0.40 with Student 102
    f2_emb = np.zeros(512, dtype=np.float32)
    f2_emb[0] = 0.80
    f2_emb[1] = 0.40
    f2_emb[3] = 0.44
    f2_emb = f2_emb / np.linalg.norm(f2_emb)

    mock_faces = [
        create_mock_face([40, 40, 160, 160], f1_emb, det_score=0.92),
        create_mock_face([200, 40, 320, 160], f2_emb, det_score=0.90)
    ]

    with patch("app.main.get_face_analyzer") as mock_get_analyzer:
        analyzer_mock = MagicMock()
        analyzer_mock.get.return_value = mock_faces
        mock_get_analyzer.return_value = analyzer_mock

        response = client.post(
            "/ai/recognize",
            files={"image": ("class.jpg", dummy_img, "image/jpeg")},
            data={"candidates": json.dumps(candidates)}
        )
        assert response.status_code == 200
        data = response.json()

        assert data["faceCount"] == 2
        # Student 101 should only be matched ONCE (to Face 1 with higher similarity)
        f1_result = data["faces"][0]
        f2_result = data["faces"][1]

        assert f1_result["matchedStudentId"] == 101
        assert f1_result["similarity"] > 0.90

        # Face 2 must NOT match student 101 because student 101 was already matched
        assert f2_result["matchedStudentId"] != 101
        assert f1_result["spoofScore"] is None


def test_early_warning_risk_scoring():
    payload = [
        {
            "studentId": 201,
            "attendanceRate": 62.0,
            "attendanceDelta4w": -12.0,
            "internalMarksRate": 38.0,
            "marksDelta": -15.0,
            "backlogs": 2,
            "leaveDaysLast30d": 6
        }
    ]
    response = client.post("/ai/risk", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert len(data) == 1
    assert data[0]["studentId"] == 201
    assert data[0]["riskScore"] > 0.3
    assert len(data[0]["topFactors"]) > 0