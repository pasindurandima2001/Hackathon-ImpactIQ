"""Train ImpactIQ triage models on the public defect dataset.

Two text classifiers (TF-IDF + Logistic Regression):
  * severity  -> CRITICAL / HIGH / MEDIUM / LOW
  * component -> the most common components in the dataset

Prints a held-out test report (with a majority-class baseline for honest comparison) and saves
models/<target>.joblib plus models/metrics.json.

Usage:
    python train.py
    python train.py --data data/mozilla_bugs.csv --top-components 15
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

import joblib
import pandas as pd
from sklearn.dummy import DummyClassifier
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, f1_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline

BASE_DIR = Path(__file__).parent
DEFAULT_DATA = BASE_DIR / "data" / "mozilla_bugs.csv"
MODELS_DIR = BASE_DIR / "models"
TEST_SIZE = 0.2
RANDOM_STATE = 42
DEFAULT_TOP_COMPONENTS = 15
MIN_SAMPLES_PER_COMPONENT = 30
MAX_TFIDF_FEATURES = 60_000
MAX_ITERATIONS = 3000


def build_text(frame: pd.DataFrame) -> pd.Series:
    # Title is repeated so the short, information-dense summary weighs more than long logs.
    title = frame["title"].fillna("")
    description = frame["description"].fillna("")
    return title + " . " + title + " . " + description


def build_pipeline() -> Pipeline:
    return Pipeline([
        ("tfidf", TfidfVectorizer(
            ngram_range=(1, 2),
            sublinear_tf=True,
            min_df=2,
            max_df=0.9,
            max_features=MAX_TFIDF_FEATURES,
            strip_accents="unicode",
        )),
        ("classifier", LogisticRegression(max_iter=MAX_ITERATIONS, class_weight="balanced")),
    ])


def train_target(name: str, texts: pd.Series, labels: pd.Series) -> dict[str, object]:
    x_train, x_test, y_train, y_test = train_test_split(
        texts, labels, test_size=TEST_SIZE, random_state=RANDOM_STATE, stratify=labels)

    baseline = DummyClassifier(strategy="most_frequent").fit(x_train, y_train)
    baseline_pred = baseline.predict(x_test)

    model = build_pipeline().fit(x_train, y_train)
    predictions = model.predict(x_test)

    metrics: dict[str, object] = {
        "train_rows": len(x_train),
        "test_rows": len(x_test),
        "classes": sorted(labels.unique().tolist()),
        "accuracy": round(accuracy_score(y_test, predictions), 4),
        "macro_f1": round(f1_score(y_test, predictions, average="macro"), 4),
        "baseline_accuracy": round(accuracy_score(y_test, baseline_pred), 4),
        "baseline_macro_f1": round(f1_score(y_test, baseline_pred, average="macro"), 4),
    }

    print(f"\n=== {name} ===")
    print(f"train={metrics['train_rows']} test={metrics['test_rows']} classes={len(metrics['classes'])}")
    print(classification_report(y_test, predictions, zero_division=0))
    print(f"model    accuracy={metrics['accuracy']}  macro-F1={metrics['macro_f1']}")
    print(f"baseline accuracy={metrics['baseline_accuracy']}  macro-F1={metrics['baseline_macro_f1']}"
          "  (always predicts the most common class)")

    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, MODELS_DIR / f"{name}.joblib")
    return metrics


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", type=Path, default=DEFAULT_DATA)
    parser.add_argument("--top-components", type=int, default=DEFAULT_TOP_COMPONENTS)
    args = parser.parse_args()

    if not args.data.is_file():
        print(f"Dataset not found: {args.data}. Run fetch_dataset.py first.", file=sys.stderr)
        return 1

    frame = pd.read_csv(args.data)
    frame = frame[frame["title"].fillna("").str.strip() != ""].drop_duplicates(subset="bug_id")
    print(f"Loaded {len(frame)} defects from {args.data}")
    texts = build_text(frame)

    results: dict[str, object] = {
        "trained_at": datetime.now(timezone.utc).isoformat(),
        "dataset": args.data.name,
        "rows": len(frame),
    }

    results["severity"] = train_target("severity", texts, frame["severity"])

    counts = frame["component"].value_counts()
    keep = counts[counts >= MIN_SAMPLES_PER_COMPONENT].head(args.top_components).index
    component_rows = frame["component"].isin(keep)
    if len(keep) >= 2:
        results["component"] = train_target("component", texts[component_rows], frame.loc[component_rows, "component"])
    else:
        print("Not enough samples per component to train a component model; skipped.")

    (MODELS_DIR / "metrics.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
    print(f"\nSaved models and metrics to {MODELS_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
