import math, re, hashlib
from collections import Counter
from datetime import date, timedelta

def sentences(text: str) -> list[str]:
    normalized = text.replace("\r\n", "\n")
    # Avoid variable-width look-behind so the same code works on standard Python regex engines.
    normalized = re.sub(r"([.!?。])\s+", r"\1\n", normalized)
    normalized = re.sub(r"(다\.|요\.|니다\.)\s+", r"\1\n", normalized)
    return [s.strip() for s in re.split(r"\n+", normalized) if len(s.strip()) >= 4]

def hash_vector(text: str, dims: int = 768) -> list[float]:
    """Dependency-free local fallback embedding. Stable and Korean-friendly via character n-grams."""
    compact = re.sub(r"\s+", " ", text.lower())
    grams = [compact[i:i+3] for i in range(max(1, len(compact)-2))] or [compact]
    vec = [0.0] * dims
    for gram, count in Counter(grams).items():
        digest = hashlib.blake2b(gram.encode("utf-8"), digest_size=8).digest()
        idx = int.from_bytes(digest, "big") % dims
        vec[idx] += float(count)
    norm = math.sqrt(sum(x*x for x in vec)) or 1.0
    return [x/norm for x in vec]

def cosine(a: list[float], b: list[float]) -> float:
    return sum(x*y for x, y in zip(a,b))

def parse_source_date(value: str | None) -> date:
    if value:
        try: return date.fromisoformat(value)
        except ValueError: pass
    return date.today()

def next_weekday(base: date, weekday: int, force_next: bool = False) -> date:
    delta = (weekday - base.weekday()) % 7
    if delta == 0 or force_next: delta += 7 if delta == 0 else 0
    return base + timedelta(days=delta)
