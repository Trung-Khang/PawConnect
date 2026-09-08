"""Generate deterministic PawConnect Product demo data from approved references."""

import argparse
import csv
import hashlib
import json
import re
import sys
import tempfile
from collections import Counter, defaultdict
from copy import deepcopy
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from random import Random


DEFAULT_SEED = 20260908
DATASET_VERSION = "generated-product-0.1.0"
CONTRACT_VERSION = "v0.2"
GENERATED_AT = "2026-09-08T16:00:00+07:00"

PRODUCT_HEADER = [
    "seed_key", "product_kind", "name", "description", "price", "stock", "image_url",
    "suitable_size", "is_breeding_dog", "category_code", "branch_code", "breed_code",
    "breed_type", "life_stage", "age_months", "current_weight_kg", "current_size",
    "expected_adult_size", "health_status", "care_instructions",
]
CATEGORY_HEADER = ["seed_key", "category_code", "name", "description"]
BRANCH_HEADER = ["seed_key", "branch_code", "name", "address", "phone", "latitude", "longitude"]
BREED_HEADER = [
    "seed_key", "breed_code", "breed_name", "default_size", "min_weight_kg",
    "max_weight_kg", "min_age_months", "max_age_months", "description",
]
BAND_HEADER = [
    "market_segment", "breed_code", "life_stage", "category_code", "source_category",
    "sample_count", "min_price_vnd", "median_price_vnd", "max_price_vnd",
]

PRODUCT_KINDS = {"PUPPY", "FOOD", "ACCESSORY"}
BREED_TYPES = {"PUREBRED", "MIXED", "UNKNOWN"}
LIFE_STAGES = {"PUPPY", "ADULT", "UNKNOWN"}
SIZES = {"SMALL", "MEDIUM", "LARGE"}
SIZE_RANK = {"SMALL": 1, "MEDIUM": 2, "LARGE": 3}
PUPPY_BREED_CODES = ["BREED_POODLE", "BREED_CHIHUAHUA", "BREED_PHU_QUOC"]
MATURITY_AGE_MONTHS_BY_SIZE = {"SMALL": 10, "MEDIUM": 14, "LARGE": 18}

FOOD_PRODUCTS = [
    ("Thức ăn hạt demo cỡ nhỏ", "SMALL", 180000, 12),
    ("Thức ăn hạt demo cỡ vừa", "MEDIUM", 240000, 10),
    ("Thức ăn hạt demo cỡ lớn", "LARGE", 310000, 8),
]
ACCESSORY_PRODUCTS = [
    ("Vòng cổ demo cỡ nhỏ", "SMALL", 80000, 15),
    ("Dây dắt demo cỡ vừa", "MEDIUM", 140000, 12),
    ("Đồ chơi demo cỡ lớn", "LARGE", 120000, 10),
]
EMAIL_PATTERN = re.compile(r"[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}", re.I)
PHONE_PATTERN = re.compile(r"(?<!\d)(?:\+?84|0)(?:[ .-]?\d){8,10}(?!\d)")
SECRET_PATTERN = re.compile(r"\b(password|token|api[_ -]?key|secret|cookie)\b", re.I)


def project_root():
    return Path(__file__).resolve().parents[2]


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


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def rng_for(seed, stable_key):
    material = f"{seed}:{stable_key}".encode("utf-8")
    return Random(int.from_bytes(hashlib.sha256(material).digest(), "big"))


def rounded_decimal(value):
    return Decimal(value).quantize(Decimal("0.1"), rounding=ROUND_HALF_UP)


def rounded_vnd(value):
    return int((Decimal(value) / Decimal("50000")).quantize(Decimal("1"), rounding=ROUND_HALF_UP) * 50000)


def require_codes(rows, key, label):
    values = [row[key] for row in rows]
    if any(not value for value in values) or len(values) != len(set(values)):
        raise ValueError(f"Invalid or duplicate {key} in {label}")
    return set(values)


def load_references(root):
    curated = root / "data-pipeline" / "data" / "curated"
    categories = read_csv(curated / "reference" / "categories.csv", CATEGORY_HEADER)
    branches = read_csv(curated / "reference" / "branches.csv", BRANCH_HEADER)
    breeds = read_csv(curated / "catalog" / "breeds.csv", BREED_HEADER)
    category_codes = require_codes(categories, "category_code", "categories.csv")
    require_codes(branches, "branch_code", "branches.csv")
    require_codes(breeds, "breed_code", "breeds.csv")
    expected_categories = {"CAT_BREEDING_DOG", "CAT_FOOD", "CAT_ACCESSORY"}
    if not expected_categories.issubset(category_codes):
        raise ValueError("Missing required Product categories")
    breeds_by_code = {row["breed_code"]: row for row in breeds}
    for breed_code in PUPPY_BREED_CODES:
        if breed_code not in breeds_by_code:
            raise ValueError(f"Missing required puppy breed: {breed_code}")
    return categories, branches, breeds_by_code


def load_price_bands(root):
    path = root / "data-pipeline" / "data" / "candidate" / "commercial" / "market_price_bands.csv"
    rows = read_csv(path, BAND_HEADER)
    bands = {}
    for row in rows:
        if row["market_segment"] != "PUPPY_MARKET" or row["source_category"] != "PUPPY_MARKETPLACE":
            continue
        sample_count = int(row["sample_count"])
        lower = int(row["min_price_vnd"])
        median = int(row["median_price_vnd"])
        upper = int(row["max_price_vnd"])
        if sample_count < 3 or not (0 < lower <= median <= upper):
            raise ValueError(f"Invalid price band for {row['breed_code']}")
        bands[row["breed_code"]] = {"min": lower, "median": median, "max": upper, "sample_count": sample_count}
    missing = set(PUPPY_BREED_CODES) - set(bands)
    if missing:
        raise ValueError(f"Missing approved market price bands: {', '.join(sorted(missing))}")
    return bands


def maturity_age_months(breed):
    return MATURITY_AGE_MONTHS_BY_SIZE[breed["default_size"]]


def current_size_for_progress(expected_adult_size, progress):
    if expected_adult_size == "SMALL":
        return "SMALL"
    if expected_adult_size == "MEDIUM":
        return "SMALL" if progress < Decimal("0.45") else "MEDIUM"
    if progress < Decimal("0.25"):
        return "SMALL"
    return "MEDIUM" if progress < Decimal("0.65") else "LARGE"


def generate_puppy(seed, branch_code, breed, band):
    stable_key = f"product_puppy_{branch_code.lower()}_{breed['breed_code'].lower()}"
    rng = rng_for(seed, stable_key)
    maturity = maturity_age_months(breed)
    age_months = rng.randint(2, min(8, maturity - 1))
    adult_low = Decimal(breed["min_weight_kg"])
    adult_high = Decimal(breed["max_weight_kg"])
    adult_target = rounded_decimal(adult_low + (adult_high - adult_low) * Decimal(str(rng.random())))
    progress_low = Decimal("0.12") + Decimal(age_months) * Decimal("0.05")
    progress_high = min(Decimal("0.70"), progress_low + Decimal("0.14"))
    growth_progress = progress_low + (progress_high - progress_low) * Decimal(str(rng.random()))
    current_weight = rounded_decimal(adult_target * growth_progress)
    if current_weight <= 0 or current_weight >= adult_target:
        raise ValueError(f"Invalid synthetic growth model for {stable_key}")
    adjusted = rounded_vnd(Decimal(band["median"]) * Decimal(str(rng.choice([0.9, 1.0, 1.1]))))
    price = min(band["max"], max(band["min"], adjusted))
    expected_size = breed["default_size"]
    return {
        "seed_key": stable_key,
        "product_kind": "PUPPY",
        "name": f"{breed['breed_name']} puppy demo {branch_code}",
        "description": "Product puppy synthetic cho môi trường dev/test/demo, không sao chép listing thị trường.",
        "price": str(price),
        "stock": "1",
        "image_url": "",
        # Compatibility field for current TV1 UI; canonical adult intent remains expected_adult_size.
        "suitable_size": expected_size,
        "is_breeding_dog": "true",
        "category_code": "CAT_BREEDING_DOG",
        "branch_code": branch_code,
        "breed_code": breed["breed_code"],
        "breed_type": "PUREBRED",
        "life_stage": "PUPPY",
        "age_months": str(age_months),
        "current_weight_kg": format(current_weight, "f"),
        "current_size": current_size_for_progress(expected_size, growth_progress),
        "expected_adult_size": expected_size,
        "health_status": "DEMO_HEALTH_CHECKED",
        "care_instructions": "Theo dõi khẩu phần và lịch chăm sóc phù hợp với puppy trong dữ liệu demo.",
    }


def generate_non_puppy(seed, branch_code, product_kind, index, spec):
    name, suitable_size, price, stock = spec
    category_code = "CAT_FOOD" if product_kind == "FOOD" else "CAT_ACCESSORY"
    return {
        "seed_key": f"product_{product_kind.lower()}_{branch_code.lower()}_{index:02d}",
        "product_kind": product_kind,
        "name": f"{name} {branch_code}",
        "description": "Product synthetic cho môi trường dev/test/demo, không suy ra từ category raw commercial.",
        "price": str(price),
        "stock": str(stock),
        "image_url": "",
        "suitable_size": suitable_size,
        "is_breeding_dog": "false",
        "category_code": category_code,
        "branch_code": branch_code,
        "breed_code": "",
        "breed_type": "",
        "life_stage": "",
        "age_months": "",
        "current_weight_kg": "",
        "current_size": "",
        "expected_adult_size": "",
        "health_status": "",
        "care_instructions": "Hướng dẫn sử dụng demo theo loại Product.",
    }


def generate_products(seed, branches, breeds_by_code, bands):
    rows = []
    for branch in branches:
        branch_code = branch["branch_code"]
        for breed_code in PUPPY_BREED_CODES:
            rows.append(generate_puppy(seed, branch_code, breeds_by_code[breed_code], bands[breed_code]))
        for index, spec in enumerate(FOOD_PRODUCTS, start=1):
            rows.append(generate_non_puppy(seed, branch_code, "FOOD", index, spec))
        for index, spec in enumerate(ACCESSORY_PRODUCTS, start=1):
            rows.append(generate_non_puppy(seed, branch_code, "ACCESSORY", index, spec))
    return rows


def is_safe_text(field, value):
    if field in {"price", "stock", "age_months", "current_weight_kg"}:
        return True
    text = str(value or "")
    return not (EMAIL_PATTERN.search(text) or PHONE_PATTERN.search(text) or SECRET_PATTERN.search(text))


def validate_products(rows, categories, branches, breeds_by_code, bands, seed):
    errors = []
    category_codes = {row["category_code"] for row in categories}
    branch_codes = {row["branch_code"] for row in branches}
    seen_keys = set()
    branch_kind_counts = Counter()
    category_counts = Counter()

    for row in rows:
        if list(row) != PRODUCT_HEADER:
            errors.append("Product row header mismatch")
            continue
        seed_key = row["seed_key"]
        if not seed_key or seed_key in seen_keys:
            errors.append(f"Duplicate or missing seed_key: {seed_key}")
        seen_keys.add(seed_key)
        for field, value in row.items():
            if not is_safe_text(field, value):
                errors.append(f"Unsafe generated value in {seed_key}:{field}")
        if not row["name"] or not row["description"]:
            errors.append(f"Missing required text for {seed_key}")
        if row["image_url"]:
            errors.append(f"image_url must be empty for generated Product: {seed_key}")
        if not row["price"].isdigit() or int(row["price"]) <= 0:
            errors.append(f"Invalid VND price for {seed_key}")
        if not row["stock"].isdigit() or int(row["stock"]) < 0:
            errors.append(f"Invalid stock for {seed_key}")
        if row["suitable_size"] not in SIZES:
            errors.append(f"Invalid suitable_size for {seed_key}")
        if row["category_code"] not in category_codes or row["branch_code"] not in branch_codes:
            errors.append(f"Unknown stable category/branch code for {seed_key}")
        if row["product_kind"] not in PRODUCT_KINDS:
            errors.append(f"Invalid product_kind for {seed_key}")
            continue
        branch_kind_counts[(row["branch_code"], row["product_kind"])] += 1
        category_counts[row["category_code"]] += 1

        puppy_fields = ["breed_code", "breed_type", "life_stage", "age_months", "current_weight_kg", "current_size", "expected_adult_size"]
        if row["product_kind"] == "PUPPY":
            if row["category_code"] != "CAT_BREEDING_DOG" or row["stock"] != "1" or row["is_breeding_dog"] != "true":
                errors.append(f"Puppy category, stock, or breeding flag invalid for {seed_key}")
            if row["breed_code"] not in PUPPY_BREED_CODES or row["breed_code"] not in breeds_by_code:
                errors.append(f"Puppy breed invalid for {seed_key}")
                continue
            breed = breeds_by_code[row["breed_code"]]
            if row["breed_type"] != "PUREBRED" or row["life_stage"] != "PUPPY":
                errors.append(f"Puppy enum invalid for {seed_key}")
            if not row["age_months"].isdigit() or not (2 <= int(row["age_months"]) < maturity_age_months(breed)):
                errors.append(f"Puppy age invalid for {seed_key}")
            try:
                current_weight = Decimal(row["current_weight_kg"])
            except Exception:
                current_weight = Decimal("0")
            target_rng = rng_for(seed, seed_key)
            target_rng.randint(2, min(8, maturity_age_months(breed) - 1))
            adult_low = Decimal(breed["min_weight_kg"])
            adult_high = Decimal(breed["max_weight_kg"])
            adult_target = rounded_decimal(adult_low + (adult_high - adult_low) * Decimal(str(target_rng.random())))
            if current_weight <= 0 or current_weight >= adult_target:
                errors.append(f"Puppy current weight invalid for {seed_key}")
            if row["expected_adult_size"] != breed["default_size"]:
                errors.append(f"Puppy expected adult size invalid for {seed_key}")
            if row["current_size"] not in SIZES or SIZE_RANK[row["current_size"]] > SIZE_RANK[breed["default_size"]]:
                errors.append(f"Puppy current size invalid for {seed_key}")
            band = bands.get(row["breed_code"])
            if not band or not (band["min"] <= int(row["price"]) <= band["max"]):
                errors.append(f"Puppy price outside approved band for {seed_key}")
        else:
            expected_category = "CAT_FOOD" if row["product_kind"] == "FOOD" else "CAT_ACCESSORY"
            if row["category_code"] != expected_category or row["is_breeding_dog"] != "false" or int(row["stock"]) < 1:
                errors.append(f"Non-puppy category, stock, or breeding flag invalid for {seed_key}")
            if any(row[field] for field in puppy_fields):
                errors.append(f"Non-puppy contains puppy field for {seed_key}")

    expected_branch_kind = {(branch["branch_code"], kind) for branch in branches for kind in PRODUCT_KINDS}
    for key in expected_branch_kind:
        if branch_kind_counts[key] != 3:
            errors.append(f"Expected exactly 3 {key[1]} records for {key[0]}")
    if len(rows) != 27:
        errors.append(f"Expected 27 Product records, got {len(rows)}")
    if category_counts["CAT_BREEDING_DOG"] != 9 or category_counts["CAT_FOOD"] != 9 or category_counts["CAT_ACCESSORY"] != 9:
        errors.append("Category distribution must be 9 puppy, 9 food, and 9 accessory")
    return errors, branch_kind_counts, category_counts


def run_self_test(seed, categories, branches, breeds_by_code, bands, products):
    bad_rows = deepcopy(products)
    puppies = [row for row in bad_rows if row["product_kind"] == "PUPPY"]
    foods = [row for row in bad_rows if row["product_kind"] == "FOOD"]
    puppies[0]["stock"] = "2"
    puppies[1]["breed_code"] = "BREED_UNKNOWN"
    foods[0]["age_months"] = "5"
    foods[1]["price"] = "0"
    puppies[2]["breed_type"] = "INVALID"
    with tempfile.TemporaryDirectory(prefix="pawconnect_product_invalid_") as temp_name:
        temporary = Path(temp_name) / "products.csv"
        write_csv(temporary, PRODUCT_HEADER, bad_rows)
        copied_rows = read_csv(temporary, PRODUCT_HEADER)
        errors, _branch_counts, _category_counts = validate_products(
            copied_rows, categories, branches, breeds_by_code, bands, seed
        )
    if len(errors) < 5:
        raise ValueError("Self-test did not detect all required invalid Product cases")
    return len(errors)


def render_report(root, seed, products_path, branch_kind_counts, category_counts, self_test_error_count):
    lines = [
        "# PawConnect Generated Product Validation",
        "",
        f"- dataset_version: `{DATASET_VERSION}`",
        f"- schema_contract_version: `{CONTRACT_VERSION}`",
        f"- generated_at: `{GENERATED_AT}`",
        f"- random_seed: `{seed}`",
        "- command: `python data-pipeline/src/generate_product_demo.py`",
        f"- products_sha256: `{sha256_file(products_path)}`",
        "- deterministic_result: `PASS` for the same seed and unchanged input references.",
        "",
        "## Record counts",
        "",
        "| Product kind | Records |",
        "| --- | ---: |",
        "| PUPPY | 9 |",
        "| FOOD | 9 |",
        "| ACCESSORY | 9 |",
        "| Total | 27 |",
        "",
        "## Branch and category distribution",
        "",
        "| Branch code | PUPPY | FOOD | ACCESSORY | Total |",
        "| --- | ---: | ---: | ---: | ---: |",
    ]
    branch_codes = sorted({branch_code for branch_code, _kind in branch_kind_counts})
    for branch_code in branch_codes:
        puppy = branch_kind_counts[(branch_code, "PUPPY")]
        food = branch_kind_counts[(branch_code, "FOOD")]
        accessory = branch_kind_counts[(branch_code, "ACCESSORY")]
        lines.append(f"| {branch_code} | {puppy} | {food} | {accessory} | {puppy + food + accessory} |")
    lines.extend([
        "",
        "| Category code | Records |",
        "| --- | ---: |",
        f"| CAT_BREEDING_DOG | {category_counts['CAT_BREEDING_DOG']} |",
        f"| CAT_FOOD | {category_counts['CAT_FOOD']} |",
        f"| CAT_ACCESSORY | {category_counts['CAT_ACCESSORY']} |",
        "",
        "## Price and growth rules",
        "",
        "- Puppy prices use only approved market price bands with sample_count >= 3; the median is adjusted by a deterministic +/-10% factor, rounded to 50,000 VND, then clamped to the band min/max.",
        "- Food prices are fixed synthetic values: 180000, 240000, 310000 VND. Accessory prices are fixed synthetic values: 80000, 140000, 120000 VND.",
        "- Food/accessory prices are not inferred from Pet Mart raw data because no confirmed subcategory mapping exists.",
        "- Internal maturity ages are SMALL=10, MEDIUM=14, LARGE=18 months; they do not reuse breeds.csv max_age_months.",
        "- current_weight_kg is a deterministic growth proportion of a sampled adult target and is always greater than zero and below that target. It is not validated against the adult min/max as a current puppy range.",
        "",
        "## Validation",
        "",
        "| Check | Result |",
        "| --- | --- |",
        "| Reference headers, stable category/branch/breed codes | PASS |",
        "| 27 records; 3 branch x (3 PUPPY + 3 FOOD + 3 ACCESSORY) | PASS |",
        "| Puppy stock, enum, breed, growth, expected adult size and price-band rules | PASS |",
        "| Food/accessory puppy fields empty and stock >= 1 | PASS |",
        "| image_url empty; no Cloudinary, placeholder, PII, secret or raw listing data | PASS |",
        f"| Temporary invalid-data self-test | PASS - {self_test_error_count} validation errors caught |",
        "",
        "## Limitations",
        "",
        "- Generated Product data is for dev/test/demo only; there is no DB import, backend mapping, SQL/migration, or Cloudinary upload.",
        "- Current Product entity/DTO does not yet contain the commercial v0.2 fields; stable codes require a future importer mapping to DB IDs.",
        "- Chợ Tốt listing identity is not copied. Pet Mart category_code remains unavailable in candidate data.",
    ])
    return "\n".join(lines) + "\n"


def generate(seed):
    root = project_root()
    categories, branches, breeds_by_code = load_references(root)
    bands = load_price_bands(root)
    products = generate_products(seed, branches, breeds_by_code, bands)
    errors, branch_kind_counts, category_counts = validate_products(products, categories, branches, breeds_by_code, bands, seed)
    if errors:
        raise ValueError("Generated Product validation failed:\n- " + "\n- ".join(errors))

    output_path = root / "data-pipeline" / "data" / "generated" / "product" / "products.csv"
    write_csv(output_path, PRODUCT_HEADER, products)
    persisted = read_csv(output_path, PRODUCT_HEADER)
    errors, branch_kind_counts, category_counts = validate_products(persisted, categories, branches, breeds_by_code, bands, seed)
    if errors:
        raise ValueError("Persisted Product validation failed:\n- " + "\n- ".join(errors))
    if products != persisted:
        raise ValueError("Persisted Product output differs from generated rows")

    regenerated = generate_products(seed, branches, breeds_by_code, bands)
    if products != regenerated:
        raise ValueError("Determinism check failed for same seed")
    self_test_error_count = run_self_test(seed, categories, branches, breeds_by_code, bands, products)

    report_path = root / "data-pipeline" / "reports" / "generated_product_validation.md"
    report_path.write_text(
        render_report(root, seed, output_path, branch_kind_counts, category_counts, self_test_error_count),
        encoding="utf-8",
        newline="\n",
    )
    return output_path, report_path, len(products), self_test_error_count


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Generate PawConnect Product demo CSV data.")
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED, help="Deterministic random seed.")
    parser.add_argument("--self-test", action="store_true", help="Run temporary invalid-data validator checks.")
    args = parser.parse_args()
    try:
        if args.self_test:
            root = project_root()
            categories, branches, breeds_by_code = load_references(root)
            bands = load_price_bands(root)
            products = generate_products(args.seed, branches, breeds_by_code, bands)
            error_count = run_self_test(args.seed, categories, branches, breeds_by_code, bands, products)
            print(f"PASS Product invalid-data self-test: validator caught {error_count} errors")
            return 0
        output_path, report_path, count, self_test_error_count = generate(args.seed)
        print("PASS generated Product demo data")
        print(f"Product records: {count}")
        print(f"Self-test errors caught: {self_test_error_count}")
        print(f"Output: {output_path.relative_to(project_root()).as_posix()}")
        print(f"Report: {report_path.relative_to(project_root()).as_posix()}")
        return 0
    except (OSError, ValueError, csv.Error) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
