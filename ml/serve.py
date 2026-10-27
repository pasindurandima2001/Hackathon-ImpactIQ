"""HTTP prediction service for the trained triage models (used by the Spring Boot Defect Triage Agent).

Endpoints:
    GET  /health   -> {"status": "UP", "models": [...]}
    POST /predict  -> {"severity": [...top-3...], "component": [...top-3...], "source": "..."}

Usage:
    python serve.py                      # 127.0.0.1:8001
    ML_HOST=0.0.0.0 PORT=9000 python serve.py
"""

from __future__ import annotations

import os
from typing import Any

import joblib
import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from predict import MODELS_DIR, TARGETS, TOP_K

DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 8001
MAX_TITLE_CHARS = 500
MAX_DESCRIPTION_CHARS = 5000
MODEL_SOURCE = "TF-IDF + Logistic Regression trained on public Mozilla Bugzilla defects"

app = FastAPI(title="ImpactIQ triage model", docs_url=None, redoc_url=None)
models: dict[str, Any] = {}


class PredictRequest(BaseModel):
    title: str = Field(min_length=1, max_length=MAX_TITLE_CHARS)
    description: str = Field(default="", max_length=MAX_DESCRIPTION_CHARS)


class LabelScore(BaseModel):
    label: str
    probability: float


class PredictResponse(BaseModel):
    severity: list[LabelScore] = []
    component: list[LabelScore] = []
    source: str = MODEL_SOURCE


def load_models() -> None:
    for target in TARGETS:
        path = MODELS_DIR / f"{target}.joblib"
        if path.is_file():
            models[target] = joblib.load(path)


def rank(model: Any, text: str) -> list[LabelScore]:
    probabilities = model.predict_proba([text])[0]
    ranked = sorted(zip(model.classes_, probabilities), key=lambda pair: pair[1], reverse=True)[:TOP_K]
    return [LabelScore(label=str(label), probability=round(float(p), 3)) for label, p in ranked]


@app.get("/health")
def health() -> dict[str, Any]:
    return {"status": "UP" if models else "NO_MODELS", "models": sorted(models)}


@app.post("/predict", response_model=PredictResponse)
def predict(request: PredictRequest) -> PredictResponse:
    if not models:
        raise HTTPException(status_code=503, detail="No trained models loaded; run train.py")
    text = f"{request.title} . {request.title} . {request.description}"
    return PredictResponse(**{target: rank(model, text) for target, model in models.items()})


def main() -> None:
    load_models()
    if not models:
        print(f"Warning: no models in {MODELS_DIR}; /predict returns 503 until train.py is run.")
    # uvicorn handles SIGINT/SIGTERM with a graceful shutdown.
    uvicorn.run(app, host=os.environ.get("ML_HOST", DEFAULT_HOST), port=int(os.environ.get("PORT", DEFAULT_PORT)))


if __name__ == "__main__":
    main()
