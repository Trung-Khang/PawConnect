"""Clean raw commercial observations into non-identifying candidate datasets."""

import argparse
import csv
import hashlib
import json
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
from pathlib import Path
from urllib.parse import parse_qsl, urlencode, urlparse, urlunparse


RAW_HEADER = [
    "source_url", "store_name", "retrieved_at", "source_category", "breed_label",
    "breed_type", "age_months", "current_weight_kg", "current_size", "brand_label",
    "public_price_vnd", "availability_label", "checksum",
]
BREEDS_HEADER = [
    "seed_key", "breed_code", "breed_name", "default_size", "min_weight_kg",
    "max_weight_kg", "min_age_months", "max_age_months", "description",
]
PUPPY_CANDIDATE_HEADER = [
    "candidate_key", "retrieved_at", "breed_code", "breed_type", "life_stage",
    "age_months", "current_weight_kg", "current_size", "public_price_vnd",
    "availability_label",
]
PRODUCT_CANDIDATE_HEADER = [
    "candidate_key", "retrieved_at", "source_category", "category_code",
    "brand_label", "public_price_vnd", "availability_label",
]
PRICE_BAND_HEADER = [
    "market_segment", "breed_code", "life_stage", "category_code", "source_category",
    "sample_count", "min_price_vnd", "median_price_vnd", "max_price_vnd",
]

SOURCE_INPUTS = [
    ("cho_tot", "Chợ Tốt", "PUPPY_MARKETPLACE"),
    ("cho_tot_cho_giong", "Chợ Tốt (cho-giong)", "PUPPY_MARKETPLACE"),
    ("pet_mart", "Pet Mart", "PRODUCT_CATALOG"),
]
ALLOWED_BREED_TYPES = {"PUREBRED", "MIXED", "UNKNOWN"}
ALLOWED_SIZES = {"SMALL", "MEDIUM", "LARGE"}
CHAT_MARKERS = ("zalo.me", "zalo", "m.me", "messenger.com", "wa.me", "whatsapp", "t.me", "telegram", "livechat", "/chat/")
CONTACT_PATTERN = re.compile(r"\b(lien he|sdt|so dien thoai|inbox|nhan tin|goi ngay|call)\b")
EMAIL_PATTERN = re.compile(r"[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}", re.I)
PHONE_PATTERN = re.compile(r"(?<!\d)(?:\+?84|0)(?:[ .-]?\d){8,10}(?!\d)")
CHECKSUM_PATTERN = re.compile(r"[0-9a-f]{64}\Z")


def project_root():
    return Path(__file__).resolve().parents[2]


def normalize_text(value):
    return re.sub(r"\s+", " ", str(value or "")).strip()


def ascii_fold(value):
    return "".join(
        character for character in unicodedata.normalize("NFD", normalize_text(value))
        if unicodedata.category(character) != "Mn"
    ).casefold()


def read_csv(path, expected_header):
    if not path.exists():
        raise ValueError(f"Missing required CSV: {path}")
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != expected_header:
            raise ValueError(f"Invalid header in {path}; expected {expected_header}, got {reader.fieldnames}")
        return list(reader)


def write_csv(path, header, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=header)
        writer.writeheader()
        writer.writerows(rows)


def canonical_url(value):
    parsed = urlparse(normalize_text(value))
    if parsed.scheme != "https" or not parsed.netloc or not parsed.path:
        return ""
    query = [(key, item) for key, item in parse_qsl(parsed.query, keep_blank_values=True)
             if not key.casefold().startswith("utm_") and key.casefold() not in {"fbclid", "gclid"}]
    return urlunparse(("https", parsed.netloc.casefold(), parsed.path.rstrip("/") or "/", "", urlencode(query), ""))


def sensitive_reason(row):
    """Scan every raw value except checksum; known price values are not phone numbers."""
    for field, value in row.items():
        if field in {"checksum", "candidate_key"}:
            continue
        text = normalize_text(value)
        folded = ascii_fold(text)
        if EMAIL_PATTERN.search(text):
            return "pii_email"
        if any(marker in folded for marker in CHAT_MARKERS):
            return "chat_or_contact_marker"
        if CONTACT_PATTERN.search(folded):
            return "chat_or_contact_marker"
        if field != "public_price_vnd" and PHONE_PATTERN.search(text):
            return "pii_phone"
    return ""


def valid_price(value):
    text = normalize_text(value)
    return text.isdigit() and int(text) > 0


def valid_optional_decimal(value):
    if not normalize_text(value):
        return True
    try:
        return Decimal(normalize_text(value)) > 0
    except InvalidOperation:
        return False


def valid_optional_age(value):
    return not normalize_text(value) or (normalize_text(value).isdigit() and int(value) >= 0)


def map_breed(label, breeds, breed_type):
    if breed_type == "MIXED":
        return ""
    folded = ascii_fold(label)
    matches = []
    for breed_name, breed_code in breeds.items():
        if re.search(rf"(?<![a-z0-9]){re.escape(breed_name)}(?![a-z0-9])", folded):
            matches.append(breed_code)
    return matches[0] if len(matches) == 1 else ""


def effective_breed_type(row):
    raw_type = normalize_text(row["breed_type"])
    if not raw_type:
        return "UNKNOWN"
    if raw_type not in ALLOWED_BREED_TYPES:
        return ""
    title = ascii_fold(row["breed_label"])
    if "thuan chung" in title:
        return "PUREBRED"
    if re.search(r"(?<![a-z])lai(?![a-z])", title):
        return "MIXED"
    return raw_type


def median_price(values):
    ordered = sorted(values)
    middle = len(ordered) // 2
    if len(ordered) % 2:
        return ordered[middle]
    median = ((Decimal(ordered[middle - 1]) + Decimal(ordered[middle])) / 2).quantize(
        Decimal("1"), rounding=ROUND_HALF_UP
    )
    return int(median)


def stable_key(facts, occurrence):
    material = json.dumps({"facts": facts, "occurrence": occurrence}, ensure_ascii=True, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(material.encode("utf-8")).hexdigest()


def empty_stats():
    return {
        "raw": 0,
        "accepted": 0,
        "quarantined": 0,
        "deduped": 0,
        "age_unverified": 0,
        "insufficient_sample": 0,
        "unmapped_breed": 0,
        "candidate": 0,
        "band_contribution": 0,
        "reasons": Counter(),
    }


def validate_row(row):
    url = canonical_url(row["source_url"])
    if not url:
        return "missing_or_invalid_source_url", ""
    sensitive = sensitive_reason(row)
    if sensitive:
        return sensitive, ""
    if not valid_price(row["public_price_vnd"]):
        return "invalid_price", ""
    if not CHECKSUM_PATTERN.fullmatch(normalize_text(row["checksum"])):
        return "invalid_checksum", ""
    if not valid_optional_age(row["age_months"]):
        return "invalid_age", ""
    if not valid_optional_decimal(row["current_weight_kg"]):
        return "invalid_current_weight", ""
    if normalize_text(row["current_size"]) not in {"", *ALLOWED_SIZES}:
        return "invalid_current_size", ""
    if not effective_breed_type(row):
        return "invalid_breed_type", ""
    return "", url


def logical_signature(row):
    return "|".join([
        ascii_fold(row["breed_label"]),
        normalize_text(row["public_price_vnd"]),
        normalize_text(row["age_months"]),
        normalize_text(row["availability_label"]),
    ])


def load_clean_observations(root, breeds):
    raw_root = root / "data-pipeline" / "data" / "raw" / "commercial_observations"
    stats = {name: empty_stats() for _, name, _ in SOURCE_INPUTS}
    clean = []
    seen_urls = set()
    seen_checksums = set()
    seen_logical_puppy = set()

    for file_slug, source_name, expected_category in SOURCE_INPUTS:
        rows = read_csv(raw_root / f"{file_slug}.csv", RAW_HEADER)
        source_stats = stats[source_name]
        for row in rows:
            source_stats["raw"] += 1
            reason, url = validate_row(row)
            if reason:
                source_stats["quarantined"] += 1
                source_stats["reasons"][reason] += 1
                continue
            if normalize_text(row["source_category"]) != expected_category:
                source_stats["quarantined"] += 1
                source_stats["reasons"]["unexpected_source_category"] += 1
                continue
            checksum = normalize_text(row["checksum"])
            if url in seen_urls:
                source_stats["deduped"] += 1
                source_stats["reasons"]["duplicate_source_url"] += 1
                continue
            if checksum in seen_checksums:
                source_stats["deduped"] += 1
                source_stats["reasons"]["duplicate_checksum"] += 1
                continue
            if expected_category == "PUPPY_MARKETPLACE":
                signature = logical_signature(row)
                if signature in seen_logical_puppy:
                    source_stats["deduped"] += 1
                    source_stats["reasons"]["duplicate_logical_listing"] += 1
                    continue
                seen_logical_puppy.add(signature)
            seen_urls.add(url)
            seen_checksums.add(checksum)
            breed_type = effective_breed_type(row)
            observation = {
                "source_name": source_name,
                "source_category": expected_category,
                "retrieved_at": normalize_text(row["retrieved_at"]),
                "breed_label": normalize_text(row["breed_label"]),
                "breed_type": breed_type,
                "age_months": normalize_text(row["age_months"]),
                "current_weight_kg": normalize_text(row["current_weight_kg"]),
                "current_size": normalize_text(row["current_size"]),
                "brand_label": normalize_text(row["brand_label"]),
                "public_price_vnd": normalize_text(row["public_price_vnd"]),
                "availability_label": normalize_text(row["availability_label"]),
                "internal_url": url,
            }
            if expected_category == "PUPPY_MARKETPLACE":
                observation["breed_code"] = map_breed(observation["breed_label"], breeds, breed_type)
                if observation["age_months"]:
                    source_stats["age_unverified"] += 1
                observation["age_months"] = ""
                observation["life_stage"] = "UNKNOWN"
                if not observation["breed_code"]:
                    source_stats["unmapped_breed"] += 1
            source_stats["accepted"] += 1
            clean.append(observation)
    return clean, stats


def product_candidates(clean, stats):
    rows = []
    occurrences = Counter()
    products = [row for row in clean if row["source_category"] == "PRODUCT_CATALOG"]
    products.sort(key=lambda row: (
        row["retrieved_at"], row["source_category"], row["brand_label"], row["public_price_vnd"],
        row["availability_label"], row["internal_url"],
    ))
    for row in products:
        facts = {
            "retrieved_at": row["retrieved_at"],
            "source_category": row["source_category"],
            "category_code": "",
            "brand_label": row["brand_label"],
            "public_price_vnd": row["public_price_vnd"],
            "availability_label": row["availability_label"],
        }
        signature = json.dumps(facts, ensure_ascii=True, sort_keys=True, separators=(",", ":"))
        occurrences[signature] += 1
        rows.append({"candidate_key": stable_key(facts, occurrences[signature]), **facts})
        stats[row["source_name"]]["candidate"] += 1
    return rows


def price_bands(clean, stats):
    groups = defaultdict(list)
    for row in clean:
        if row["source_category"] != "PUPPY_MARKETPLACE" or not row.get("breed_code"):
            continue
        groups[(row["breed_code"], row["life_stage"])].append(row)

    bands = []
    for (breed_code, life_stage), rows in sorted(groups.items()):
        if len(rows) < 3:
            for row in rows:
                stats[row["source_name"]]["insufficient_sample"] += 1
            continue
        prices = [int(row["public_price_vnd"]) for row in rows]
        bands.append({
            "market_segment": "PUPPY_MARKET",
            "breed_code": breed_code,
            "life_stage": life_stage,
            "category_code": "",
            "source_category": "PUPPY_MARKETPLACE",
            "sample_count": str(len(rows)),
            "min_price_vnd": str(min(prices)),
            "median_price_vnd": str(median_price(prices)),
            "max_price_vnd": str(max(prices)),
        })
        for row in rows:
            stats[row["source_name"]]["band_contribution"] += 1
    return bands


def ensure_candidate_safe(rows, header):
    forbidden = {"source_url", "breed_label", "store_name", "title", "description", "image_url", "phone", "email", "address"}
    if forbidden.intersection(header):
        raise ValueError("Candidate header contains a forbidden field")
    for row in rows:
        if set(row) != set(header):
            raise ValueError("Candidate row does not match its header")
        reason = sensitive_reason(row)
        if reason:
            raise ValueError(f"Candidate contains sensitive data: {reason}")
        if not CHECKSUM_PATTERN.fullmatch(row["candidate_key"]):
            raise ValueError("Candidate key must be a SHA-256 hex value")


def render_quarantine_report(stats):
    lines = ["# Commercial Quarantine Summary", "", "No raw record, URL, title, contact value, or PII is repeated in this report.", "", "| Source | Quarantined | Deduped | Reasons |", "| --- | ---: | ---: | --- |"]
    for _, source_name, _ in SOURCE_INPUTS:
        source = stats[source_name]
        reasons = ", ".join(f"{reason}={count}" for reason, count in sorted(source["reasons"].items())) or "none"
        lines.append(f"| {source_name} | {source['quarantined']} | {source['deduped']} | {reasons} |")
    return "\n".join(lines) + "\n"


def render_validation_report(stats, puppy_rows, product_rows, bands):
    lines = [
        "# Commercial Cleaning Validation",
        "",
        "- Contract scope: commercial observation v0.2; raw data remains unchanged.",
        "- Candidate datasets contain no source URL, listing title, store name, description, image, phone, email, address, or chat marker.",
        "- Chợ Tốt individual listings are excluded from the puppy candidate file; only aggregate price bands are eligible for handoff.",
        "- Raw Chợ Tốt age values have no source phrase evidence, so every populated value is cleared and represented as `life_stage=UNKNOWN` for aggregation.",
        "- Pet Mart category_code remains blank because raw input has no confirmed subcategory mapping.",
        "",
        "| Source | Raw | Accepted | Quarantined | Deduped | Age unverified | Unmapped breed | Insufficient sample | Candidate | Band contribution |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for _, source_name, _ in SOURCE_INPUTS:
        source = stats[source_name]
        lines.append(
            f"| {source_name} | {source['raw']} | {source['accepted']} | {source['quarantined']} | "
            f"{source['deduped']} | {source['age_unverified']} | {source['unmapped_breed']} | "
            f"{source['insufficient_sample']} | {source['candidate']} | {source['band_contribution']} |"
        )
    lines.extend([
        "",
        "## Output counts",
        "",
        f"- puppy_market_observations.csv: {len(puppy_rows)} rows (required to remain empty for Chợ Tốt listing privacy).",
        f"- product_catalog_observations.csv: {len(product_rows)} rows.",
        f"- market_price_bands.csv: {len(bands)} rows; bands require sample_count >= 3.",
        "- Result: PASS.",
    ])
    return "\n".join(lines) + "\n"


def write_report(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="\n")


def run(root, write_outputs):
    breeds_rows = read_csv(root / "data-pipeline" / "data" / "curated" / "catalog" / "breeds.csv", BREEDS_HEADER)
    breeds = {ascii_fold(row["breed_name"]): row["breed_code"] for row in breeds_rows}
    if len(breeds) != len(breeds_rows) or any(not code for code in breeds.values()):
        raise ValueError("breeds.csv contains an invalid or duplicate canonical breed name")

    clean, stats = load_clean_observations(root, breeds)
    puppy_rows = []
    product_rows = product_candidates(clean, stats)
    bands = price_bands(clean, stats)
    ensure_candidate_safe(puppy_rows, PUPPY_CANDIDATE_HEADER)
    ensure_candidate_safe(product_rows, PRODUCT_CANDIDATE_HEADER)

    if write_outputs:
        candidate_root = root / "data-pipeline" / "data" / "candidate" / "commercial"
        reports_root = root / "data-pipeline" / "reports"
        write_csv(candidate_root / "puppy_market_observations.csv", PUPPY_CANDIDATE_HEADER, puppy_rows)
        write_csv(candidate_root / "product_catalog_observations.csv", PRODUCT_CANDIDATE_HEADER, product_rows)
        write_csv(candidate_root / "market_price_bands.csv", PRICE_BAND_HEADER, bands)
        write_report(reports_root / "commercial_quarantine_summary.md", render_quarantine_report(stats))
        write_report(reports_root / "commercial_cleaning_validation.md", render_validation_report(stats, puppy_rows, product_rows, bands))
    return stats, puppy_rows, product_rows, bands


def self_test():
    sample = {field: "" for field in RAW_HEADER}
    sample.update({
        "source_url": "https://www.chotot.com/mua-ban-cho/123.htm",
        "source_category": "PUPPY_MARKETPLACE",
        "breed_label": "Poodle thuần chủng",
        "breed_type": "UNKNOWN",
        "public_price_vnd": "1500000",
        "checksum": "a" * 64,
    })
    assert validate_row(sample)[0] == ""
    assert effective_breed_type(sample) == "PUREBRED"
    assert map_breed(sample["breed_label"], {"poodle": "BREED_POODLE"}, "PUREBRED") == "BREED_POODLE"
    assert median_price([100000, 100001]) == 100001
    sample["source_url"] = "https://zalo.me/contact"
    assert validate_row(sample)[0] == "chat_or_contact_marker"
    sample["source_url"] = "https://www.chotot.com/mua-ban-cho/123.htm"
    sample["public_price_vnd"] = "0"
    assert validate_row(sample)[0] == "invalid_price"
    print("PASS self-test: invalid chat URL and invalid price are quarantined; candidate key logic is deterministic.")


def print_summary(stats, puppy_rows, product_rows, bands, mode):
    print(f"PASS {mode}")
    for _, source_name, _ in SOURCE_INPUTS:
        source = stats[source_name]
        print(
            f"{source_name}: raw={source['raw']} accepted={source['accepted']} quarantined={source['quarantined']} "
            f"deduped={source['deduped']} age_unverified={source['age_unverified']} "
            f"insufficient_sample={source['insufficient_sample']} candidate={source['candidate']}"
        )
    print(f"outputs: puppy_candidate={len(puppy_rows)} product_candidate={len(product_rows)} price_bands={len(bands)}")


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Clean PawConnect commercial raw observations without exposing listing identity.")
    parser.add_argument("--dry-run", action="store_true", help="Validate and calculate counts without writing outputs.")
    parser.add_argument("--self-test", action="store_true", help="Run in-memory invalid-data checks without reading or writing datasets.")
    args = parser.parse_args()
    try:
        if args.self_test:
            self_test()
            return 0
        stats, puppy_rows, product_rows, bands = run(project_root(), write_outputs=not args.dry_run)
        print_summary(stats, puppy_rows, product_rows, bands, "dry-run" if args.dry_run else "cleaning")
        return 0
    except (OSError, ValueError, csv.Error) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
