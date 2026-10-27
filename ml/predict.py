"""Predict severity and component for one defect with the trained models.

Usage:
    python predict.py --title "Checkout page crashes when card is declined" --description "Steps: ..."
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import joblib

MODELS_DIR = Path(__file__).parent / "models"
TARGETS = ("severity", "component")
TOP_K = 3


def predict(title: str, description: str) -> dict[str, list[dict[str, float | str]]]:
    text = f"{title} . {title} . {description}"
    output: dict[str, list[dict[str, float | str]]] = {}
    for target in TARGETS:
        model_path = MODELS_DIR / f"{target}.joblib"
        if not model_path.is_file():
            continue
        model = joblib.load(model_path)
        probabilities = model.predict_proba([text])[0]
        ranked = sorted(zip(model.classes_, probabilities), key=lambda pair: pair[1], reverse=True)[:TOP_K]
        output[target] = [{"label": str(label), "probability": round(float(p), 3)} for label, p in ranked]
    return output


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--title", required=True)
    parser.add_argument("--description", default="")
    args = parser.parse_args()

    result = predict(args.title, args.description)
    if not result:
        print(f"No models in {MODELS_DIR}. Run train.py first.", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
