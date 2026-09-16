#!/usr/bin/env python3
"""Small, explicit validator for Hub's single .env contract."""
from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

KEY_RE = re.compile(r"^[A-Z][A-Z0-9_]*$")
TRUE_FALSE = {"true", "false"}


def parse_dotenv(path: Path) -> tuple[dict[str, str], list[str]]:
    values: dict[str, str] = {}
    errors: list[str] = []
    for number, raw in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            errors.append(f"line {number}: KEY=VALUE 형식이 아닙니다.")
            continue
        key, value = line.split("=", 1)
        key, value = key.strip(), value.strip()
        if not KEY_RE.fullmatch(key):
            errors.append(f"line {number}: 잘못된 변수 이름: {key}")
            continue
        if key in values:
            errors.append(f"line {number}: 중복 변수: {key}")
            continue
        if len(value) >= 2 and value[:1] == value[-1:] and value[0] in {'"', "'"}:
            value = value[1:-1]
        values[key] = value
    return values, errors


def process_env() -> dict[str, str]:
    return {k: v for k, v in os.environ.items() if KEY_RE.fullmatch(k)}


def require(values: dict[str, str], key: str, errors: list[str], why: str = "필수") -> None:
    if not values.get(key, "").strip():
        errors.append(f"{key}: {why} 값이 비어 있습니다.")


def validate(values: dict[str, str], mode: str) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    example = mode == "example"

    ai_mode = values.get("HUB_AI_MODE", "mock").lower()
    embed_mode = values.get("HUB_EMBED_MODE", "hash").lower()
    if ai_mode not in {"mock", "gemini"}:
        errors.append("HUB_AI_MODE은 mock 또는 gemini 여야 합니다.")
    if embed_mode not in {"hash", "e5"}:
        errors.append("HUB_EMBED_MODE은 hash 또는 e5 여야 합니다.")
    if not example and ai_mode == "gemini":
        require(values, "GEMINI_API_KEY", errors, "Gemini 사용 시 필수")
    google = ["GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET", "GOOGLE_REFRESH_TOKEN"]
    filled = [bool(values.get(k, "").strip()) for k in google]
    if any(filled) and not all(filled):
        errors.append("Google Drive는 GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_REFRESH_TOKEN 세 값을 함께 설정해야 합니다.")

    for key in ("HUB_COOKIE_SECURE", "HUB_ENFORCE_SECURE_CONFIG", "HUB_LOCAL_AUTO_OPEN"):
        if key in values and values[key] and values[key].lower() not in TRUE_FALSE:
            errors.append(f"{key}: true 또는 false를 사용하세요.")

    jwt = values.get("HUB_JWT_SECRET", "")
    if jwt and len(jwt) < 32:
        errors.append("HUB_JWT_SECRET은 32자 이상으로 설정하세요.")

    if mode == "deploy":
        require(values, "DB_PASSWORD", errors, "Docker/PostgreSQL 배포 시 필수")
        require(values, "HUB_JWT_SECRET", errors, "배포 시 필수")

    if values.get("HUB_BIND_ADDRESS") == "0.0.0.0" and values.get("HUB_COOKIE_SECURE", "false").lower() != "true":
        warnings.append("LAN 전체 바인딩 중입니다. 다른 PC에서 브라우저 직접 녹음을 쓸 계획이면 HTTPS(Caddy)를 권장합니다.")

    return errors, warnings


def main() -> int:
    ap = argparse.ArgumentParser()
    source = ap.add_mutually_exclusive_group(required=True)
    source.add_argument("--file", type=Path)
    source.add_argument("--from-process", action="store_true")
    ap.add_argument("--mode", choices=["example", "local", "deploy"], default="local")
    args = ap.parse_args()

    if args.file:
        if not args.file.exists():
            print(f"[ENV FAIL] 파일 없음: {args.file}", file=sys.stderr)
            return 2
        values, parse_errors = parse_dotenv(args.file)
    else:
        values, parse_errors = process_env(), []

    errors, warnings = validate(values, args.mode)
    errors = parse_errors + errors
    for warning in warnings:
        print(f"[ENV WARN] {warning}")
    if errors:
        for error in errors:
            print(f"[ENV FAIL] {error}", file=sys.stderr)
        return 1
    print(f"[ENV PASS] mode={args.mode}, keys={len(values)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
