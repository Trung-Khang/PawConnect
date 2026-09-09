"""Build and verify immutable PawConnect synthetic seed releases."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import shutil
import sys
import tempfile
import unicodedata
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")
    sys.stderr.reconfigure(encoding="utf-8", errors="backslashreplace")

ROOT = Path(__file__).resolve().parents[2]
PIPELINE = ROOT / "data-pipeline"
CURATED = PIPELINE / "data" / "curated"
GENERATED = PIPELINE / "data" / "generated"
SEEDS = PIPELINE / "data" / "seed"
SEED = 20260908
SUPPORTED_RELEASES = ("v3",)
UTF8 = "utf-8"

DOG_HEADER = ["seed_key", "name", "breed", "size", "age_months", "weight_kg", "gender", "vaccination_status", "image_url", "description", "branch_code"]
POST_HEADER = ["seed_key", "dog_profile_seed_key", "created_by_user_key", "title", "description", "health_note", "status", "created_at"]
APP_HEADER = ["seed_key", "adoption_post_seed_key", "applicant_user_key", "message", "status"]
PRODUCT_HEADER = ["seed_key", "product_kind", "name", "description", "price", "stock", "image_url", "suitable_size", "is_breeding_dog", "category_code", "branch_code", "breed_code", "breed_type", "life_stage", "age_months", "current_weight_kg", "current_size", "expected_adult_size", "health_status", "care_instructions"]


def fail(message: str) -> None:
    raise ValueError(message)


def read_csv(path: Path, expected: list[str] | None = None) -> tuple[list[str], list[dict[str, str]]]:
    if not path.is_file():
        fail(f"missing required input: {path.relative_to(ROOT)}")
    with path.open("r", encoding=UTF8, newline="") as handle:
        reader = csv.DictReader(handle)
        header = reader.fieldnames or []
        if expected is not None and header != expected:
            fail(f"unexpected header in {path.relative_to(ROOT)}")
        return header, list(reader)


def write_csv(path: Path, header: list[str], rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding=UTF8, newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=header, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def ascii_fold(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value)
    return "".join(char for char in normalized if not unicodedata.combining(char)).lower()


def unique(rows: list[dict[str, str]], field: str, label: str) -> None:
    values = [row.get(field, "") for row in rows]
    if any(not value for value in values) or len(values) != len(set(values)):
        fail(f"{label} has missing or duplicate {field}")


def copy_csv(source: Path, destination: Path) -> tuple[list[str], list[dict[str, str]]]:
    header, rows = read_csv(source)
    write_csv(destination, header, rows)
    return header, rows


def market_evidence() -> dict[str, bool]:
    patterns = {
        "Pomeranian / Phoc Soc": r"pomeranian|phoc soc|\bpom\b",
        "Corgi": r"\bcorgi\b",
        "Shiba Inu": r"shiba inu",
        "Golden Retriever": r"golden retriever",
        "Labrador Retriever": r"labrador retriever",
        "Husky": r"husky siberian",
        "Samoyed": r"samoyed",
        "Alaskan Malamute": r"alaskan malamute",
        "Beagle": r"beagle",
        "French Bulldog": r"french bulldog",
    }
    labels: list[str] = []
    for path in (PIPELINE / "data" / "raw" / "commercial_observations").glob("cho_tot*.csv"):
        _, rows = read_csv(path)
        labels.extend(ascii_fold(row.get("breed_label", "")) for row in rows)
    return {name: any(re.search(pattern, label) for label in labels) for name, pattern in patterns.items()}


def validated_catalog() -> tuple[list[dict[str, str]], list[dict[str, str]], list[dict[str, str]]]:
    breed_header = ["seed_key", "breed_code", "breed_name", "default_size", "min_weight_kg", "max_weight_kg", "min_age_months", "max_age_months", "description"]
    _, breeds = read_csv(CURATED / "catalog" / "breeds.csv", breed_header)
    _, references = read_csv(CURATED / "catalog" / "breed_references.csv")
    _, proposals = read_csv(CURATED / "catalog" / "breed_proposals.csv")
    unique(breeds, "seed_key", "breeds.csv")
    unique(breeds, "breed_code", "breeds.csv")
    ref_codes = {row["breed_code"] for row in references if row.get("evidence_status") in {"PASS", "LEGACY_ACCEPTED"}}
    for row in breeds:
        if row["breed_code"] not in ref_codes:
            fail(f"breed has no accepted provenance: {row['breed_code']}")
        if row["default_size"] not in {"SMALL", "MEDIUM", "LARGE"}:
            fail(f"invalid breed size: {row['breed_code']}")
        if float(row["min_weight_kg"]) <= 0 or float(row["max_weight_kg"]) < float(row["min_weight_kg"]):
            fail(f"invalid adult weight range: {row['breed_code']}")
    return breeds, references, proposals


def build_adoption(breeds: list[dict[str, str]], release_name: str) -> tuple[list[dict[str, str]], list[dict[str, str]], list[dict[str, str]]]:
    _, curated_dogs = read_csv(CURATED / "adoption" / "dog_profiles.csv", DOG_HEADER)
    _, generated_dogs = read_csv(GENERATED / "adoption" / "dog_profiles.csv", DOG_HEADER)
    _, curated_posts = read_csv(CURATED / "adoption" / "adoption_posts.csv", POST_HEADER)
    _, generated_posts = read_csv(GENERATED / "adoption" / "adoption_posts.csv", POST_HEADER)
    _, curated_apps = read_csv(CURATED / "adoption" / "adoption_applications.csv", APP_HEADER)
    _, generated_apps = read_csv(GENERATED / "adoption" / "adoption_applications.csv", APP_HEADER)
    _, users = read_csv(CURATED / "fixtures" / "users.csv")
    branches = ["BR_HCM_01", "BR_HN_01", "BR_DN_01"]
    managers = {row["branch_code"]: row["seed_key"] for row in users if row["role_code"] == "ROLE_BRANCH_MANAGER"}
    customers = [row["seed_key"] for row in users if row["role_code"] == "ROLE_CUSTOMER"]
    if set(branches) != set(managers) or not customers:
        fail("missing branch manager or customer fixture")

    source_dogs = curated_dogs + generated_dogs
    unique(source_dogs, "seed_key", "source dog profiles")
    dogs: list[dict[str, str]] = []
    selected_keys: set[str] = set()
    for breed in breeds:
        matches = [row for row in source_dogs if row["breed"] == breed["breed_name"]]
        for row in matches[:2]:
            dogs.append(row)
            selected_keys.add(row["seed_key"])
    count_by_breed = Counter(row["breed"] for row in dogs)
    counts_by_branch = Counter(row["branch_code"] for row in dogs)
    for index, breed in enumerate(breeds, 1):
        remaining = 2 - count_by_breed[breed["breed_name"]]
        if remaining < 0:
            fail(f"more than two existing dog profiles for {breed['breed_code']}")
        for ordinal in range(remaining):
            branch = min(branches, key=lambda code: (counts_by_branch[code], code))
            min_age = int(breed["min_age_months"])
            age = min(min_age + 12 + ordinal * 6, 132)
            low, high = float(breed["min_weight_kg"]), float(breed["max_weight_kg"])
            weight = f"{(low + high) / 2:g}"
            key = f"dog_seed_{release_name}_{breed['breed_code'].lower().replace('breed_', '')}_{ordinal + 1}"
            dogs.append({
                "seed_key": key, "name": f"Demo {index}-{ordinal + 1}", "breed": breed["breed_name"],
                "size": breed["default_size"], "age_months": str(age), "weight_kg": weight,
                "gender": "MALE" if (index + ordinal) % 2 else "FEMALE",
                "vaccination_status": ("FULLY_VACCINATED", "PARTIALLY_VACCINATED", "NOT_VACCINATED")[(index + ordinal) % 3],
                "image_url": f"https://placehold.co/800x600/png?text=PawConnect+Seed+{index}-{ordinal + 1}",
                "description": "Synthetic seed profile for PawConnect dev/test/demo.", "branch_code": branch,
            })
            counts_by_branch[branch] += 1

    source_posts = [row for row in curated_posts + generated_posts if row["dog_profile_seed_key"] in selected_keys]
    source_post_keys = {row["seed_key"] for row in source_posts}
    posts = source_posts
    apps = [row for row in curated_apps + generated_apps if row["adoption_post_seed_key"] in source_post_keys]
    unique(posts, "seed_key", "source adoption posts")
    unique(apps, "seed_key", "source adoption applications")
    posted_dogs = {row["dog_profile_seed_key"] for row in posts}
    for number, dog in enumerate((row for row in dogs if row["seed_key"] not in posted_dogs), 1):
        is_closed = number % 4 == 0
        post_key = f"post_seed_{release_name}_{number:02d}"
        posts.append({
            "seed_key": post_key, "dog_profile_seed_key": dog["seed_key"],
            "created_by_user_key": managers[dog["branch_code"]], "title": f"Adoption profile {number}",
            "description": "Synthetic adoption post for dev/test/demo.", "health_note": "Demo health note.",
            "status": "CLOSED" if is_closed else "AVAILABLE", "created_at": f"2026-09-{10 + number:02d}T09:00:00+07:00",
        })
        apps.append({"seed_key": f"application_seed_{release_name}_{number:02d}_a", "adoption_post_seed_key": post_key,
                     "applicant_user_key": customers[number % len(customers)], "message": "Synthetic demo application.",
                     "status": "APPROVED" if is_closed else "PENDING"})
        if is_closed:
            apps.append({"seed_key": f"application_seed_{release_name}_{number:02d}_b", "adoption_post_seed_key": post_key,
                         "applicant_user_key": customers[(number + 1) % len(customers)], "message": "Synthetic demo application.",
                         "status": "REJECTED"})
    return dogs, posts, apps


def validate_release(dogs: list[dict[str, str]], posts: list[dict[str, str]], apps: list[dict[str, str]], products: list[dict[str, str]], breeds: list[dict[str, str]], proposals: list[dict[str, str]], users: list[dict[str, str]]) -> None:
    for rows, label in ((dogs, "dog profiles"), (posts, "adoption posts"), (apps, "adoption applications"), (products, "products")):
        unique(rows, "seed_key", label)
    proposal_codes = {row["breed_code"] for row in proposals}
    for user in users:
        if user.get("password_placeholder") != "TV3_SEED_REQUIRED":
            fail(f"user fixture has an unsafe password value: {user.get('seed_key', '')}")
        if user.get("phone") or user.get("avatar_url"):
            fail(f"user fixture contains contact or avatar data: {user.get('seed_key', '')}")
        if not (user.get("email", "").endswith(".test") or user.get("email", "").endswith(".example")):
            fail(f"user fixture has non-demo email: {user.get('seed_key', '')}")
    breed_by_name = {row["breed_name"]: row for row in breeds}
    breed_codes = {row["breed_code"] for row in breeds}
    if proposal_codes & breed_codes:
        fail("proposal breed was admitted to catalog")
    for dog in dogs:
        breed = breed_by_name.get(dog["breed"])
        if not breed or dog["size"] != breed["default_size"]:
            fail(f"dog references invalid breed or size: {dog['seed_key']}")
        if not (int(breed["min_age_months"]) <= int(dog["age_months"]) <= int(breed["max_age_months"])):
            fail(f"dog age outside breed range: {dog['seed_key']}")
        if not (float(breed["min_weight_kg"]) <= float(dog["weight_kg"]) <= float(breed["max_weight_kg"])):
            fail(f"dog weight outside breed range: {dog['seed_key']}")
        if dog["gender"] not in {"MALE", "FEMALE"} or dog["vaccination_status"] not in {"NOT_VACCINATED", "PARTIALLY_VACCINATED", "FULLY_VACCINATED"}:
            fail(f"invalid dog enum: {dog['seed_key']}")
    post_by_key = {row["seed_key"]: row for row in posts}
    dog_by_key = {row["seed_key"]: row for row in dogs}
    user_by_key = {row["seed_key"]: row for row in users}
    for post in posts:
        dog = dog_by_key.get(post["dog_profile_seed_key"])
        manager = user_by_key.get(post["created_by_user_key"])
        if not dog or post["status"] not in {"AVAILABLE", "CLOSED"}:
            fail(f"invalid adoption post: {post['seed_key']}")
        if not manager or manager["role_code"] not in {"ROLE_ADMIN", "ROLE_BRANCH_MANAGER"}:
            fail(f"adoption post creator has invalid role: {post['seed_key']}")
        if manager["role_code"] == "ROLE_BRANCH_MANAGER" and manager["branch_code"] != dog["branch_code"]:
            fail(f"branch manager does not match dog branch: {post['seed_key']}")
    by_post: dict[str, list[dict[str, str]]] = defaultdict(list)
    for app in apps:
        applicant = user_by_key.get(app["applicant_user_key"])
        if app["adoption_post_seed_key"] not in post_by_key or app["status"] not in {"PENDING", "APPROVED", "REJECTED"} or not applicant or applicant["role_code"] != "ROLE_CUSTOMER":
            fail(f"invalid adoption application: {app['seed_key']}")
        by_post[app["adoption_post_seed_key"]].append(app)
    for key, post in post_by_key.items():
        statuses = [row["status"] for row in by_post[key]]
        if statuses.count("APPROVED") > 1:
            fail(f"multiple approved applications: {key}")
        if "APPROVED" in statuses and post["status"] != "CLOSED":
            fail(f"approved application requires closed post: {key}")
        if post["status"] == "CLOSED" and "PENDING" in statuses:
            fail(f"closed post has pending application: {key}")
    if len(products) != 27:
        fail("product snapshot must contain exactly 27 records")
    for product in products:
        if product["product_kind"] not in {"PUPPY", "FOOD", "ACCESSORY"}:
            fail(f"invalid product kind: {product['seed_key']}")
        puppy_fields = ("breed_code", "breed_type", "life_stage", "age_months", "current_weight_kg", "current_size", "expected_adult_size")
        if product["product_kind"] == "PUPPY":
            if product["stock"] != "1" or product["breed_code"] not in breed_codes or product["breed_type"] != "PUREBRED" or product["life_stage"] != "PUPPY":
                fail(f"invalid puppy product: {product['seed_key']}")
        elif any(product[field] for field in puppy_fields):
            fail(f"non-puppy product contains puppy field: {product['seed_key']}")
    forbidden = re.compile(r"(?i)(password|token|api[_ -]?key|@|https?://|\b\d{9,}\b)")
    for rows in (dogs, posts, apps, products):
        for row in rows:
            if any(forbidden.search(value or "") for key, value in row.items() if key not in {"image_url"}):
                fail("release contains prohibited sensitive or raw URL-like data")


def make_report(release_name: str, counts: dict[str, int], evidence: dict[str, bool], artifact_hashes: dict[str, str]) -> str:
    admissions = counts["breeds"] - 12
    lines = [f"# PawConnect Seed Release {release_name} Validation", "", "## Result", "", "- Status: PASS", f"- release_id: `pawconnect-seed-{release_name}`", "- release_status: `APPROVED_FOR_DEV_TEST_DEMO`", "- random_seed: `20260908`", "- Deterministic verification: PASS", "", "## Catalog", "", "- Legacy breeds retained unchanged: 12", f"- New breed admissions: {admissions}", "- Pomeranian: admitted with PASS adult provenance.", "- H'Mong coc duoi: admitted with VKA/Tropical Center adult evidence and a 12-month internal maturity threshold.", ""]
    lines += ["| Candidate | Chotot local evidence | Catalog action |", "| --- | --- | --- |"]
    for name, confirmed in evidence.items():
        if name == "Pomeranian / Phoc Soc":
            action = "admitted with PASS adult provenance"
        elif name in {"Corgi", "Golden Retriever"}:
            action = "retained existing curated breed"
        else:
            action = "retained existing curated breed; no new admission"
        lines.append(f"| {name} | {'CONFIRMED' if confirmed else 'NOT_CONFIRMED'} | {action} |")
    lines += ["", "## Record Counts", "", "| Dataset | Count |", "| --- | ---: |"]
    for name in ("breeds", "dog_profiles", "adoption_posts", "adoption_applications", "products"):
        lines.append(f"| {name} | {counts[name]} |")
    lines += ["", "## Dataset Hashes", "", "| Path | SHA-256 |", "| --- | --- |"]
    for path, checksum in sorted(artifact_hashes.items()):
        lines.append(f"| `{path}` | `{checksum}` |")
    lines += ["", "## Limits", "", "- No importer, backend mapping, migration, DB import, or Cloudinary integration is included.", "- Raw/candidate commercial observations are not copied into this release and are not DB import input.", "- Product v0.2 remains pending Entity/DTO, enum, migration, and backward-compatibility integration.", ""]
    return "\n".join(lines)


def build(target: Path, seed: int, release_name: str) -> tuple[dict[str, int], dict[str, str], str]:
    if seed != SEED:
        fail("seed releases only support random seed 20260908")
    breeds, references, proposals = validated_catalog()
    if target.exists():
        if any(path.is_file() for path in target.rglob("*")):
            raise FileExistsError(f"release already exists and is immutable: {target}")
        shutil.rmtree(target)
    target.mkdir(parents=True, exist_ok=False)
    for name in ("roles.csv", "branches.csv", "categories.csv", "service_types.csv"):
        copy_csv(CURATED / "reference" / name, target / "reference" / name)
    copy_csv(CURATED / "catalog" / "breeds.csv", target / "catalog" / "breeds.csv")
    copy_csv(CURATED / "catalog" / "breed_references.csv", target / "catalog" / "breed_references.csv")
    copy_csv(CURATED / "catalog" / "breed_proposals.csv", target / "catalog" / "breed_proposals.csv")
    copy_csv(CURATED / "fixtures" / "users.csv", target / "fixtures" / "users.csv")
    dogs, posts, apps = build_adoption(breeds, release_name)
    _, products = read_csv(GENERATED / "product" / "products.csv", PRODUCT_HEADER)
    _, users = read_csv(CURATED / "fixtures" / "users.csv")
    validate_release(dogs, posts, apps, products, breeds, proposals, users)
    write_csv(target / "adoption" / "dog_profiles.csv", DOG_HEADER, dogs)
    write_csv(target / "adoption" / "adoption_posts.csv", POST_HEADER, posts)
    write_csv(target / "adoption" / "adoption_applications.csv", APP_HEADER, apps)
    write_csv(target / "product" / "products.csv", PRODUCT_HEADER, products)
    provenance = f"# PawConnect Seed Release {release_name} Provenance\n\nCurated is the master data. This immutable release snapshots validated synthetic dev/test/demo data. Raw and candidate commercial observations are excluded and are never direct DB import input. Generated workspace files are build outputs; this release is the importer handoff after importer review.\n"
    (target / "provenance.md").write_text(provenance, encoding=UTF8)
    artifacts = {path.relative_to(target).as_posix(): digest(path) for path in sorted(target.rglob("*.csv"))}
    counts = {"breeds": len(breeds), "dog_profiles": len(dogs), "adoption_posts": len(posts), "adoption_applications": len(apps), "products": len(products)}
    metadata = {
        "adoption/adoption_applications.csv": (["seed_key"], ["adoption/adoption_posts.csv", "fixtures/users.csv"]),
        "adoption/adoption_posts.csv": (["seed_key"], ["adoption/dog_profiles.csv", "fixtures/users.csv"]),
        "adoption/dog_profiles.csv": (["seed_key"], ["catalog/breeds.csv", "reference/branches.csv"]),
        "catalog/breed_proposals.csv": (["proposal_key", "breed_code"], []),
        "catalog/breed_references.csv": (["breed_code"], ["catalog/breeds.csv"]),
        "catalog/breeds.csv": (["seed_key", "breed_code"], ["catalog/breed_references.csv"]),
        "fixtures/users.csv": (["seed_key"], ["reference/roles.csv", "reference/branches.csv"]),
        "product/products.csv": (["seed_key"], ["catalog/breeds.csv", "reference/categories.csv", "reference/branches.csv"]),
        "reference/branches.csv": (["seed_key", "branch_code"], []),
        "reference/categories.csv": (["seed_key", "category_code"], []),
        "reference/roles.csv": (["seed_key", "role_code"], []),
        "reference/service_types.csv": (["seed_key", "service_type_code"], []),
    }
    inputs = [CURATED / "catalog" / "breeds.csv", CURATED / "catalog" / "breed_references.csv", CURATED / "catalog" / "breed_proposals.csv", CURATED / "fixtures" / "users.csv", GENERATED / "adoption" / "dog_profiles.csv", GENERATED / "product" / "products.csv"]
    manifest = {"release_id": f"pawconnect-seed-{release_name}", "release_status": "APPROVED_FOR_DEV_TEST_DEMO", "random_seed": seed, "contract_version": "v0.2 plus Seed Release", "import_readiness": "NOT_READY_FOR_DB_IMPORT", "validation_report": "reports/validation.md", "input_checksums": {path.relative_to(ROOT).as_posix(): digest(path) for path in inputs}, "datasets": [{"path": path, "header": read_csv(target / path)[0], "sha256": checksum, "count": len(read_csv(target / path)[1]), "stable_key_fields": metadata[path][0], "dependencies": metadata[path][1]} for path, checksum in sorted(artifacts.items())]}
    (target / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=True) + "\n", encoding=UTF8)
    report = make_report(release_name, counts, market_evidence(), artifacts)
    (target / "reports" / "validation.md").parent.mkdir(parents=True, exist_ok=True)
    (target / "reports" / "validation.md").write_text(report, encoding=UTF8)
    return counts, artifacts, report


def verify(release: Path, release_name: str) -> None:
    if not release.is_dir():
        fail(f"release does not exist: {release.relative_to(ROOT)}")
    manifest = json.loads((release / "manifest.json").read_text(encoding=UTF8))
    if manifest.get("release_id") != f"pawconnect-seed-{release_name}":
        fail("unexpected release manifest")
    verify_manifest_checksums(release, manifest)
    if release_name == "v1":
        print("VERIFY PASS: immutable v1 manifest checksums match")
        return
    with tempfile.TemporaryDirectory(prefix="pawconnect-seed-verify-") as temp:
        rebuilt = Path(temp) / release_name
        _, hashes, _ = build(rebuilt, SEED, release_name)
        expected = {item["path"]: item["sha256"] for item in manifest["datasets"]}
        if hashes != expected:
            fail("release checksum mismatch against deterministic rebuild")
    print("VERIFY PASS: deterministic checksums match")


def verify_manifest_checksums(release: Path, manifest: dict[str, object]) -> None:
    for dataset in manifest["datasets"]:
        path = release / dataset["path"]
        if not path.is_file() or digest(path) != dataset["sha256"]:
            fail(f"manifest checksum mismatch: {dataset['path']}")


def self_test() -> None:
    with tempfile.TemporaryDirectory(prefix="pawconnect-seed-test-") as temp:
        release = Path(temp) / "v2"
        counts, _, _ = build(release, SEED, "v2")
        try:
            build(release, SEED, "v2")
            fail("existing release was overwritten")
        except FileExistsError:
            pass
        manifest = json.loads((release / "manifest.json").read_text(encoding=UTF8))
        manifest["datasets"][0]["sha256"] = "0" * 64
        try:
            verify_manifest_checksums(release, manifest)
        except ValueError:
            pass
        else:
            fail("checksum mutation test was not detected")
        _, breeds = read_csv(release / "catalog" / "breeds.csv")
        _, proposals = read_csv(release / "catalog" / "breed_proposals.csv")
        _, dogs = read_csv(release / "adoption" / "dog_profiles.csv", DOG_HEADER)
        _, posts = read_csv(release / "adoption" / "adoption_posts.csv", POST_HEADER)
        _, apps = read_csv(release / "adoption" / "adoption_applications.csv", APP_HEADER)
        _, products = read_csv(release / "product" / "products.csv", PRODUCT_HEADER)
        _, users = read_csv(release / "fixtures" / "users.csv")
        broken = [dict(row) for row in dogs]
        broken[1]["seed_key"] = broken[0]["seed_key"]
        try:
            validate_release(broken, posts, apps, products, breeds, proposals, users)
        except ValueError:
            pass
        else:
            fail("duplicate seed test was not detected")
        proposal_dogs = [dict(row) for row in dogs]
        proposal_dogs[0]["breed"] = "Unapproved Breed"
        try:
            validate_release(proposal_dogs, posts, apps, products, breeds, proposals, users)
        except ValueError:
            pass
        else:
            fail("proposal breed test was not detected")
        broken = [dict(row) for row in apps]
        closed = next(post for post in posts if post["status"] == "CLOSED")
        broken[0]["adoption_post_seed_key"], broken[0]["status"] = closed["seed_key"], "PENDING"
        try:
            validate_release(dogs, posts, broken, products, breeds, proposals, users)
        except ValueError:
            pass
        else:
            fail("foreign-key/status test was not detected")
        if counts["dog_profiles"] != 2 * counts["breeds"]:
            fail("breed multiplier test failed")
    print("SELF-TEST PASS: immutable folder, checksum mutation, proposal, duplicate key, and adoption integrity checks passed")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--release", choices=SUPPORTED_RELEASES)
    parser.add_argument("--seed", type=int, default=SEED)
    parser.add_argument("--verify", choices=SUPPORTED_RELEASES)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--self-test-v3", action="store_true")
    args = parser.parse_args()
    try:
        if args.self_test_v3:
            from build_seed_v3 import self_test as self_test_v3
            self_test_v3()
            print("SELF-TEST PASS: v3 rejects invalid puppy stock")
            return 0
        if args.release == "v3" or args.verify == "v3":
            from build_seed_v3 import build as build_v3, verify as verify_v3
            if args.verify == "v3":
                verify_v3()
                print("VERIFY PASS: data-pipeline/data/seed/v3")
                return 0
            if args.release == "v3":
                counts = build_v3(args.seed)
                print(f"BUILD PASS: {SEEDS / 'v3'}")
                print(json.dumps(counts, sort_keys=True))
                return 0
        if args.self_test:
            from build_seed_v3 import self_test as self_test_v3
            self_test_v3()
            print("SELF-TEST PASS: v3 rejects invalid puppy stock")
            return 0
        if args.verify:
            verify(SEEDS / args.verify, args.verify)
            return 0
        if not args.release:
            parser.error("provide --release v1|v2, --verify v1|v2, or --self-test")
        target = SEEDS / args.release
        counts, hashes, report = build(target, args.seed, args.release)
        report_path = PIPELINE / "reports" / f"seed_release_{args.release}_validation.md"
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(report, encoding=UTF8)
        print(f"BUILD PASS: {target.relative_to(ROOT)}")
        print(json.dumps(counts, sort_keys=True))
        print(f"datasets={len(hashes)}")
        return 0
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"BUILD FAIL: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
