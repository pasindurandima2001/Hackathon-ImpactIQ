"""Download a labelled public defect dataset from Mozilla Bugzilla (public REST API, no key needed).

Each row: bug id, title, description (first comment), product, component, raw severity and the
ImpactIQ severity label (CRITICAL / HIGH / MEDIUM / LOW).

Usage:
    python fetch_dataset.py                    # default: up to 600 bugs per severity value
    python fetch_dataset.py --per-severity 200 --no-description
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

BUGZILLA_REST_URL = "https://bugzilla.mozilla.org/rest"
PRODUCTS = ("Firefox", "Core")
USER_AGENT = "ImpactIQ-hackathon-dataset-fetcher/1.0"
PAGE_SIZE = 200
COMMENT_BATCH_SIZE = 40
REQUEST_PAUSE_SECONDS = 0.5
REQUEST_TIMEOUT_SECONDS = 60
MAX_RETRIES = 4
MAX_DESCRIPTION_CHARS = 3000
DEFAULT_PER_SEVERITY = 600
DEFAULT_OUTPUT = Path(__file__).parent / "data" / "mozilla_bugs.csv"

# Mozilla severity (current S1-S4 scale plus the legacy names) -> ImpactIQ risk level.
SEVERITY_MAP: dict[str, str] = {
    "S1": "CRITICAL",
    "blocker": "CRITICAL",
    "critical": "CRITICAL",
    "S2": "HIGH",
    "major": "HIGH",
    "S3": "MEDIUM",
    "normal": "MEDIUM",
    "S4": "LOW",
    "minor": "LOW",
    "trivial": "LOW",
}

CSV_FIELDS = ("bug_id", "title", "description", "product", "component", "raw_severity", "severity")


def get_json(path: str, params: list[tuple[str, str]]) -> dict[str, Any]:
    url = f"{BUGZILLA_REST_URL}{path}?{urllib.parse.urlencode(params)}"
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
                return json.loads(response.read().decode("utf-8"))
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
            if attempt == MAX_RETRIES:
                raise RuntimeError(f"Request failed after {MAX_RETRIES} attempts: {url}") from error
            backoff = 2**attempt
            print(f"  retry {attempt}/{MAX_RETRIES} in {backoff}s ({error})", file=sys.stderr)
            time.sleep(backoff)
    raise AssertionError("unreachable")


def fetch_bugs_for_severity(severity: str, limit: int) -> list[dict[str, Any]]:
    bugs: list[dict[str, Any]] = []
    offset = 0
    while len(bugs) < limit:
        params = [("product", product) for product in PRODUCTS]
        params += [
            ("type", "defect"),
            ("severity", severity),
            ("resolution", "FIXED"),
            ("include_fields", "id,summary,product,component,severity"),
            ("order", "bug_id DESC"),
            ("limit", str(min(PAGE_SIZE, limit - len(bugs)))),
            ("offset", str(offset)),
        ]
        page = get_json("/bug", params).get("bugs", [])
        if not page:
            break
        bugs.extend(page)
        offset += len(page)
        time.sleep(REQUEST_PAUSE_SECONDS)
    return bugs


def fetch_descriptions(bug_ids: list[int]) -> dict[int, str]:
    """First comment of each bug = the original report text."""
    descriptions: dict[int, str] = {}
    for start in range(0, len(bug_ids), COMMENT_BATCH_SIZE):
        batch = bug_ids[start:start + COMMENT_BATCH_SIZE]
        params = [("ids", str(bug_id)) for bug_id in batch[1:]]
        params.append(("include_fields", "text,count"))
        try:
            payload = get_json(f"/bug/{batch[0]}/comment", params)
        except RuntimeError as error:
            print(f"  skipped description batch at {start}: {error}", file=sys.stderr)
            continue
        for bug_id, entry in payload.get("bugs", {}).items():
            comments = entry.get("comments", [])
            first = next((c for c in comments if c.get("count") == 0), comments[0] if comments else None)
            if first:
                descriptions[int(bug_id)] = first.get("text", "")[:MAX_DESCRIPTION_CHARS]
        print(f"  descriptions {min(start + COMMENT_BATCH_SIZE, len(bug_ids))}/{len(bug_ids)}")
        time.sleep(REQUEST_PAUSE_SECONDS)
    return descriptions


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--per-severity", type=int, default=DEFAULT_PER_SEVERITY,
                        help="max bugs fetched for each raw Mozilla severity value")
    parser.add_argument("--no-description", action="store_true", help="titles only (much faster)")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    rows: dict[int, dict[str, Any]] = {}
    for raw_severity, label in SEVERITY_MAP.items():
        print(f"Fetching severity={raw_severity} -> {label}")
        try:
            bugs = fetch_bugs_for_severity(raw_severity, args.per_severity)
        except RuntimeError as error:
            print(f"  failed: {error}", file=sys.stderr)
            continue
        for bug in bugs:
            rows[bug["id"]] = {
                "bug_id": bug["id"],
                "title": bug.get("summary", "").strip(),
                "description": "",
                "product": bug.get("product", ""),
                "component": bug.get("component", ""),
                "raw_severity": raw_severity,
                "severity": label,
            }
        print(f"  got {len(bugs)}")

    if not rows:
        print("No bugs downloaded; check network access to bugzilla.mozilla.org", file=sys.stderr)
        return 1

    if not args.no_description:
        print(f"Fetching descriptions for {len(rows)} bugs")
        for bug_id, text in fetch_descriptions(list(rows)).items():
            rows[bug_id]["description"] = text

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS)
        writer.writeheader()
        writer.writerows(rows.values())
    print(f"Saved {len(rows)} rows to {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
