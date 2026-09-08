import argparse
import csv
import hashlib
import random
import sys
import tempfile
from collections import defaultdict
from pathlib import Path
from urllib.parse import quote_plus


DEFAULT_SEED = 20260908
DATASET_VERSION = "generated-adoption-0.1.0"
CONTRACT_VERSION = "v0.1"
GENERATED_AT = "2026-09-06T11:00:00+07:00"
BASE_CREATED_AT = [
    "2026-09-06T11:00:00+07:00",
    "2026-09-06T11:05:00+07:00",
    "2026-09-06T11:10:00+07:00",
    "2026-09-06T11:15:00+07:00",
    "2026-09-06T11:20:00+07:00",
    "2026-09-06T11:25:00+07:00",
    "2026-09-06T11:30:00+07:00",
    "2026-09-06T11:35:00+07:00",
]

DOG_PROFILE_HEADER = [
    "seed_key",
    "name",
    "breed",
    "size",
    "age_months",
    "weight_kg",
    "gender",
    "vaccination_status",
    "image_url",
    "description",
    "branch_code",
]
ADOPTION_POST_HEADER = [
    "seed_key",
    "dog_profile_seed_key",
    "created_by_user_key",
    "title",
    "description",
    "health_note",
    "status",
    "created_at",
]
ADOPTION_APPLICATION_HEADER = [
    "seed_key",
    "adoption_post_seed_key",
    "applicant_user_key",
    "message",
    "status",
]

BREEDS_HEADER = [
    "seed_key",
    "breed_code",
    "breed_name",
    "default_size",
    "min_weight_kg",
    "max_weight_kg",
    "min_age_months",
    "max_age_months",
    "description",
]
BRANCHES_HEADER = [
    "seed_key",
    "branch_code",
    "name",
    "address",
    "phone",
    "latitude",
    "longitude",
]
ROLES_HEADER = ["seed_key", "role_code", "name"]
USERS_HEADER = [
    "seed_key",
    "full_name",
    "email",
    "password_placeholder",
    "phone",
    "avatar_url",
    "role_code",
    "branch_code",
    "created_at",
]

SIZE_ENUM = {"SMALL", "MEDIUM", "LARGE"}
GENDER_ENUM = {"MALE", "FEMALE"}
VACCINATION_ENUM = {
    "NOT_VACCINATED",
    "PARTIALLY_VACCINATED",
    "FULLY_VACCINATED",
}
POST_STATUS_ENUM = {"AVAILABLE", "CLOSED"}
APPLICATION_STATUS_ENUM = {"PENDING", "APPROVED", "REJECTED"}
MANAGER_ROLES = {"ROLE_ADMIN", "ROLE_BRANCH_MANAGER"}
CUSTOMER_ROLE = "ROLE_CUSTOMER"

DOG_NAMES = [
    "Mochi",
    "Bingo",
    "Nori",
    "Bim",
    "Susu",
    "Ken",
    "Mika",
    "Bo",
    "Toto",
    "Nana",
    "Rin",
    "Bun",
]

HEALTH_NOTES = {
    "NOT_VACCINATED": "Ho so tiem phong chua duoc cap nhat trong du lieu demo.",
    "PARTIALLY_VACCINATED": "Da co mot phan lich tiem phong trong du lieu demo.",
    "FULLY_VACCINATED": "Da hoan tat lich tiem phong theo ho so demo.",
}


def project_root() -> Path:
    return Path(__file__).resolve().parents[2]


def read_csv(path: Path, expected_header):
    if not path.exists():
        raise ValueError(f"Missing required CSV: {path}")
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != expected_header:
            raise ValueError(
                f"Invalid header in {path}. Expected {expected_header}, got {reader.fieldnames}"
            )
        return list(reader)


def write_csv(path: Path, header, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=header)
        writer.writeheader()
        writer.writerows(rows)


def require_unique(rows, field, dataset_name, errors):
    seen = set()
    for row in rows:
        value = row.get(field, "")
        if not value:
            errors.append(f"{dataset_name}: missing {field}")
        elif value in seen:
            errors.append(f"{dataset_name}: duplicate {field}={value}")
        seen.add(value)


def stable_slug_from_breed_code(breed_code):
    return breed_code.lower().removeprefix("breed_")


def load_sources(root: Path):
    curated = root / "data-pipeline" / "data" / "curated"
    breeds = read_csv(curated / "catalog" / "breeds.csv", BREEDS_HEADER)
    branches = read_csv(curated / "reference" / "branches.csv", BRANCHES_HEADER)
    roles = read_csv(curated / "reference" / "roles.csv", ROLES_HEADER)
    users = read_csv(curated / "fixtures" / "users.csv", USERS_HEADER)
    validate_sources(breeds, branches, roles, users)
    return breeds, branches, roles, users


def validate_sources(breeds, branches, roles, users):
    errors = []
    require_unique(breeds, "seed_key", "breeds", errors)
    require_unique(breeds, "breed_code", "breeds", errors)
    require_unique(branches, "seed_key", "branches", errors)
    require_unique(branches, "branch_code", "branches", errors)
    require_unique(roles, "seed_key", "roles", errors)
    require_unique(roles, "role_code", "roles", errors)
    require_unique(users, "seed_key", "users", errors)

    branch_codes = {row["branch_code"] for row in branches}
    role_codes = {row["role_code"] for row in roles}
    for breed in breeds:
        if breed["default_size"] not in SIZE_ENUM:
            errors.append(f"breeds: invalid default_size for {breed['seed_key']}")
        if int(breed["min_weight_kg"]) <= 0:
            errors.append(f"breeds: min_weight_kg must be positive for {breed['seed_key']}")
        if int(breed["max_weight_kg"]) < int(breed["min_weight_kg"]):
            errors.append(f"breeds: invalid weight range for {breed['seed_key']}")
        if int(breed["min_age_months"]) < 0:
            errors.append(f"breeds: invalid min_age_months for {breed['seed_key']}")
        if int(breed["max_age_months"]) <= int(breed["min_age_months"]):
            errors.append(f"breeds: invalid age range for {breed['seed_key']}")

    for user in users:
        role_code = user["role_code"]
        branch_code = user["branch_code"]
        if role_code not in role_codes:
            errors.append(f"users: role_code not found for {user['seed_key']}")
        if role_code == "ROLE_BRANCH_MANAGER" and branch_code not in branch_codes:
            errors.append(f"users: manager branch_code invalid for {user['seed_key']}")
        if role_code in {"ROLE_CUSTOMER", "ROLE_ADMIN"} and branch_code:
            errors.append(f"users: branch_code must be empty for {user['seed_key']}")
        if user["password_placeholder"] != "TV3_SEED_REQUIRED":
            errors.append(f"users: password placeholder is not allowed for {user['seed_key']}")

    if errors:
        raise ValueError("Reference validation failed:\n- " + "\n- ".join(errors))


def choose_manager_for_branch(users, branch_code):
    for user in users:
        if user["role_code"] == "ROLE_BRANCH_MANAGER" and user["branch_code"] == branch_code:
            return user["seed_key"]
    for user in users:
        if user["role_code"] == "ROLE_ADMIN":
            return user["seed_key"]
    raise ValueError(f"No manager/admin fixture can create post for branch {branch_code}")


def generate_dog_profiles(breeds, branches, rng):
    branch_codes = [row["branch_code"] for row in branches]
    vaccination_statuses = sorted(VACCINATION_ENUM)
    rows = []
    for index, breed in enumerate(breeds):
        breed_name = breed["breed_name"]
        slug = stable_slug_from_breed_code(breed["breed_code"])
        name = DOG_NAMES[index % len(DOG_NAMES)]
        min_age = int(breed["min_age_months"])
        max_age = int(breed["max_age_months"])
        min_weight = int(breed["min_weight_kg"])
        max_weight = int(breed["max_weight_kg"])
        age_months = rng.randint(min_age, max_age)
        weight_kg = rng.randint(min_weight, max_weight)
        vaccination_status = vaccination_statuses[index % len(vaccination_statuses)]
        rows.append(
            {
                "seed_key": f"dog_gen_{slug}",
                "name": name,
                "breed": breed_name,
                "size": breed["default_size"],
                "age_months": str(age_months),
                "weight_kg": str(weight_kg),
                "gender": "MALE" if index % 2 == 0 else "FEMALE",
                "vaccination_status": vaccination_status,
                "image_url": "https://placehold.co/800x600/png?text="
                + quote_plus(f"PawConnect {name} {index + 1}"),
                "description": f"Ho so demo cho {breed_name} phuc vu luong nhan nuoi PawConnect.",
                "branch_code": branch_codes[index % len(branch_codes)],
            }
        )
    return rows


def generate_adoption_posts(dog_profiles, users):
    rows = []
    selected_dogs = dog_profiles[:8]
    closed_indexes = {5, 6, 7}
    for index, dog in enumerate(selected_dogs):
        status = "CLOSED" if index in closed_indexes else "AVAILABLE"
        rows.append(
            {
                "seed_key": f"post_gen_{index + 1:02d}_{dog['seed_key'].replace('dog_gen_', '')}",
                "dog_profile_seed_key": dog["seed_key"],
                "created_by_user_key": choose_manager_for_branch(users, dog["branch_code"]),
                "title": f"{dog['name']} san sang trong du lieu demo",
                "description": f"Tin nhan nuoi demo cho {dog['breed']} tai PawConnect.",
                "health_note": HEALTH_NOTES[dog["vaccination_status"]],
                "status": status,
                "created_at": BASE_CREATED_AT[index],
            }
        )
    return rows


def generate_adoption_applications(adoption_posts, users):
    customers = [user["seed_key"] for user in users if user["role_code"] == CUSTOMER_ROLE]
    if len(customers) < 2:
        raise ValueError("Need at least 2 CUSTOMER fixture accounts for generated applications")

    plan = [
        [(customers[0], "PENDING"), (customers[1], "REJECTED")],
        [(customers[1], "PENDING")],
        [(customers[0], "REJECTED")],
        [(customers[0], "PENDING")],
        [(customers[1], "REJECTED")],
        [(customers[0], "APPROVED"), (customers[1], "REJECTED")],
        [(customers[1], "APPROVED"), (customers[0], "REJECTED")],
        [(customers[0], "APPROVED"), (customers[1], "REJECTED")],
    ]
    rows = []
    for post_index, post in enumerate(adoption_posts):
        for app_index, (customer_key, status) in enumerate(plan[post_index]):
            rows.append(
                {
                    "seed_key": f"application_gen_{post_index + 1:02d}_{app_index + 1:02d}",
                    "adoption_post_seed_key": post["seed_key"],
                    "applicant_user_key": customer_key,
                    "message": "Don demo khong chua thong tin lien he ca nhan.",
                    "status": status,
                }
            )
    return rows


def validate_generated(dog_profiles, adoption_posts, adoption_applications, breeds, branches, users):
    errors = []
    valid_records = 0
    breed_by_name = {row["breed_name"]: row for row in breeds}
    branch_codes = {row["branch_code"] for row in branches}
    users_by_key = {row["seed_key"]: row for row in users}
    dog_by_key = {row["seed_key"]: row for row in dog_profiles}
    post_by_key = {row["seed_key"]: row for row in adoption_posts}

    require_unique(dog_profiles, "seed_key", "generated dog_profiles", errors)
    require_unique(dog_profiles, "image_url", "generated dog_profiles", errors)
    require_unique(adoption_posts, "seed_key", "generated adoption_posts", errors)
    require_unique(adoption_applications, "seed_key", "generated adoption_applications", errors)

    if len(dog_profiles) != 12:
        errors.append(f"dog_profiles: expected 12 records, got {len(dog_profiles)}")
    if len(adoption_posts) != 8:
        errors.append(f"adoption_posts: expected 8 records, got {len(adoption_posts)}")
    if len(adoption_applications) != 12:
        errors.append(f"adoption_applications: expected 12 records, got {len(adoption_applications)}")

    breed_usage = defaultdict(int)
    for row in dog_profiles:
        breed = breed_by_name.get(row["breed"])
        if not breed:
            errors.append(f"dog_profiles: breed not found for {row['seed_key']}")
            continue
        breed_usage[row["breed"]] += 1
        if row["size"] != breed["default_size"]:
            errors.append(f"dog_profiles: size mismatch for {row['seed_key']}")
        if row["gender"] not in GENDER_ENUM:
            errors.append(f"dog_profiles: invalid gender for {row['seed_key']}")
        if row["vaccination_status"] not in VACCINATION_ENUM:
            errors.append(f"dog_profiles: invalid vaccination_status for {row['seed_key']}")
        if row["branch_code"] not in branch_codes:
            errors.append(f"dog_profiles: branch_code not found for {row['seed_key']}")
        if not row["image_url"].startswith("https://"):
            errors.append(f"dog_profiles: image_url must be HTTPS for {row['seed_key']}")
        age = int(row["age_months"])
        weight = int(row["weight_kg"])
        if not int(breed["min_age_months"]) <= age <= int(breed["max_age_months"]):
            errors.append(f"dog_profiles: age out of breed range for {row['seed_key']}")
        if not int(breed["min_weight_kg"]) <= weight <= int(breed["max_weight_kg"]):
            errors.append(f"dog_profiles: weight out of breed range for {row['seed_key']}")
        valid_records += 1

    for breed in breeds:
        if breed_usage[breed["breed_name"]] != 1:
            errors.append(
                f"dog_profiles: breed {breed['breed_name']} must appear once, got {breed_usage[breed['breed_name']]}"
            )

    available_by_dog = defaultdict(int)
    for row in adoption_posts:
        dog = dog_by_key.get(row["dog_profile_seed_key"])
        user = users_by_key.get(row["created_by_user_key"])
        if not dog:
            errors.append(f"adoption_posts: dog_profile_seed_key not found for {row['seed_key']}")
            continue
        if not user:
            errors.append(f"adoption_posts: created_by_user_key not found for {row['seed_key']}")
            continue
        if user["role_code"] not in MANAGER_ROLES:
            errors.append(f"adoption_posts: creator role cannot create post for {row['seed_key']}")
        if user["role_code"] == "ROLE_BRANCH_MANAGER" and user["branch_code"] != dog["branch_code"]:
            errors.append(f"adoption_posts: manager branch mismatch for {row['seed_key']}")
        if row["status"] not in POST_STATUS_ENUM:
            errors.append(f"adoption_posts: invalid status for {row['seed_key']}")
        if row["status"] == "AVAILABLE":
            available_by_dog[row["dog_profile_seed_key"]] += 1
        if not row["created_at"].endswith("+07:00"):
            errors.append(f"adoption_posts: created_at must be fixed ISO 8601 +07:00 for {row['seed_key']}")
        valid_records += 1

    for dog_key, count in available_by_dog.items():
        if count > 1:
            errors.append(f"adoption_posts: more than one AVAILABLE post for dog {dog_key}")

    applications_by_post = defaultdict(list)
    active_customer_post = set()
    for row in adoption_applications:
        post = post_by_key.get(row["adoption_post_seed_key"])
        user = users_by_key.get(row["applicant_user_key"])
        if not post:
            errors.append(f"adoption_applications: post not found for {row['seed_key']}")
            continue
        if not user:
            errors.append(f"adoption_applications: applicant not found for {row['seed_key']}")
            continue
        if user["role_code"] != CUSTOMER_ROLE:
            errors.append(f"adoption_applications: applicant must be CUSTOMER for {row['seed_key']}")
        if row["status"] not in APPLICATION_STATUS_ENUM:
            errors.append(f"adoption_applications: invalid status for {row['seed_key']}")
        if row["status"] == "PENDING":
            active_key = (row["adoption_post_seed_key"], row["applicant_user_key"])
            if active_key in active_customer_post:
                errors.append(f"adoption_applications: duplicate active application for {row['seed_key']}")
            active_customer_post.add(active_key)
        applications_by_post[row["adoption_post_seed_key"]].append(row)
        valid_records += 1

    for post_key, applications in applications_by_post.items():
        post = post_by_key[post_key]
        approved = [row for row in applications if row["status"] == "APPROVED"]
        pending = [row for row in applications if row["status"] == "PENDING"]
        if len(approved) > 1:
            errors.append(f"adoption_applications: post {post_key} has more than one APPROVED")
        if approved and post["status"] != "CLOSED":
            errors.append(f"adoption_applications: post {post_key} has APPROVED but is not CLOSED")
        if approved and pending:
            errors.append(f"adoption_applications: post {post_key} has APPROVED and PENDING")
        if post["status"] == "CLOSED" and pending:
            errors.append(f"adoption_applications: CLOSED post {post_key} still has PENDING")

    total_records = len(dog_profiles) + len(adoption_posts) + len(adoption_applications)
    return {
        "total_records": total_records,
        "valid_records": total_records if not errors else max(0, total_records - len(errors)),
        "error_count": len(errors),
        "errors": errors,
    }


def checksum(path: Path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_report(path, seed, validation, output_files, self_test):
    root = project_root()
    lines = [
        "# PawConnect - Generated Adoption Validation Report",
        "",
        f"- dataset_version: `{DATASET_VERSION}`",
        f"- schema_contract_version: `{CONTRACT_VERSION}`",
        f"- generated_at: `{GENERATED_AT}`",
        f"- random_seed: `{seed}`",
        "- command: `python data-pipeline/src/generate_adoption_demo.py`",
        "",
        "## Output files",
        "",
    ]
    for label, file_path in output_files.items():
        display_path = file_path.relative_to(root).as_posix()
        lines.append(f"- `{display_path}`: sha256 `{checksum(file_path)}`")

    lines.extend(
        [
            "",
            "## Record counts",
            "",
            "| Dataset | Records |",
            "| --- | ---: |",
            "| DogProfile generated | 12 |",
            "| AdoptionPost generated | 8 |",
            "| AdoptionApplication generated | 12 |",
            f"| Total generated | {validation['total_records']} |",
            f"| Valid records | {validation['valid_records']} |",
            f"| Error/quarantined records | {validation['error_count']} |",
            "",
            "## Validation checks",
            "",
            "| Check | Result |",
            "| --- | --- |",
            "| Required headers/reference CSV | PASS |",
            "| Breed exists and size/age/weight range | PASS |",
            "| Enum gender/vaccination/post/application | PASS |",
            "| Unique seed_key and DogProfile image_url | PASS |",
            "| User role, branch manager and customer references | PASS |",
            "| Adoption rules: max one APPROVED, APPROVED closes post, no PENDING on CLOSED/APPROVED post | PASS |",
            "| PII/password/token/API key generated | PASS - none generated |",
            "| Temporary invalid-data validator test | PASS |" if self_test else "| Temporary invalid-data validator test | NOT RUN |",
            "",
            "## Errors",
            "",
        ]
    )
    if validation["errors"]:
        lines.extend(f"- {error}" for error in validation["errors"])
        conclusion = "FAIL"
    else:
        lines.append("- None")
        conclusion = "PASS"

    lines.extend(
        [
            "",
            f"## Conclusion: {conclusion}",
            "",
            "Generated output is separated from curated foundation data. No crawler, Cloudinary upload, SQL, migration, backend, endpoint or database import was executed.",
            "",
        ]
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def generate(seed):
    root = project_root()
    rng = random.Random(seed)
    breeds, branches, _roles, users = load_sources(root)
    dog_profiles = generate_dog_profiles(breeds, branches, rng)
    adoption_posts = generate_adoption_posts(dog_profiles, users)
    adoption_applications = generate_adoption_applications(adoption_posts, users)
    validation = validate_generated(
        dog_profiles,
        adoption_posts,
        adoption_applications,
        breeds,
        branches,
        users,
    )
    if validation["errors"]:
        raise ValueError("Generated data validation failed:\n- " + "\n- ".join(validation["errors"]))

    output_dir = root / "data-pipeline" / "data" / "generated" / "adoption"
    output_files = {
        "dog_profiles": output_dir / "dog_profiles.csv",
        "adoption_posts": output_dir / "adoption_posts.csv",
        "adoption_applications": output_dir / "adoption_applications.csv",
    }
    write_csv(output_files["dog_profiles"], DOG_PROFILE_HEADER, dog_profiles)
    write_csv(output_files["adoption_posts"], ADOPTION_POST_HEADER, adoption_posts)
    write_csv(output_files["adoption_applications"], ADOPTION_APPLICATION_HEADER, adoption_applications)

    # Validate the persisted CSV files, not only the rows held in memory.
    persisted_dog_profiles = read_csv(output_files["dog_profiles"], DOG_PROFILE_HEADER)
    persisted_adoption_posts = read_csv(output_files["adoption_posts"], ADOPTION_POST_HEADER)
    persisted_adoption_applications = read_csv(
        output_files["adoption_applications"], ADOPTION_APPLICATION_HEADER
    )
    validation = validate_generated(
        persisted_dog_profiles,
        persisted_adoption_posts,
        persisted_adoption_applications,
        breeds,
        branches,
        users,
    )
    if validation["errors"]:
        raise ValueError("Persisted generated data validation failed:\n- " + "\n- ".join(validation["errors"]))

    self_test_validation = run_invalid_self_test(seed)
    if self_test_validation["error_count"] < 1:
        raise ValueError("Invalid-data self-test failed: validator did not report any errors")

    report_path = root / "data-pipeline" / "reports" / "generated_adoption_validation.md"
    write_report(report_path, seed, validation, output_files, self_test=True)
    return output_files, report_path, validation


def run_invalid_self_test(seed):
    root = project_root()
    breeds, branches, _roles, users = load_sources(root)
    rng = random.Random(seed)
    dog_profiles = generate_dog_profiles(breeds, branches, rng)
    adoption_posts = generate_adoption_posts(dog_profiles, users)
    adoption_applications = generate_adoption_applications(adoption_posts, users)
    bad_dogs = [dict(row) for row in dog_profiles]
    bad_posts = [dict(row) for row in adoption_posts]
    bad_apps = [dict(row) for row in adoption_applications]
    bad_dogs[0]["age_months"] = "1"
    bad_posts[0]["created_by_user_key"] = "user_customer_mai"
    bad_apps[0]["status"] = "APPROVED"

    with tempfile.TemporaryDirectory(prefix="pawconnect_invalid_") as temp_name:
        temp_dir = Path(temp_name)
        write_csv(temp_dir / "dog_profiles.csv", DOG_PROFILE_HEADER, bad_dogs)
        write_csv(temp_dir / "adoption_posts.csv", ADOPTION_POST_HEADER, bad_posts)
        write_csv(temp_dir / "adoption_applications.csv", ADOPTION_APPLICATION_HEADER, bad_apps)
        copied_dogs = read_csv(temp_dir / "dog_profiles.csv", DOG_PROFILE_HEADER)
        copied_posts = read_csv(temp_dir / "adoption_posts.csv", ADOPTION_POST_HEADER)
        copied_apps = read_csv(temp_dir / "adoption_applications.csv", ADOPTION_APPLICATION_HEADER)
        validation = validate_generated(copied_dogs, copied_posts, copied_apps, breeds, branches, users)

    if validation["error_count"] < 3:
        raise ValueError("Invalid-data self-test failed: validator did not catch expected errors")
    return validation


def main():
    parser = argparse.ArgumentParser(description="Generate PawConnect adoption demo CSV data.")
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED, help="Deterministic random seed.")
    parser.add_argument(
        "--self-test-invalid",
        action="store_true",
        help="Run validator against temporary invalid data and expect blocking errors.",
    )
    args = parser.parse_args()

    try:
        if args.self_test_invalid:
            validation = run_invalid_self_test(args.seed)
            print(
                "PASS invalid-data self-test: "
                f"validator caught {validation['error_count']} expected errors"
            )
            return 0

        output_files, report_path, validation = generate(args.seed)
        print("PASS generated adoption demo data")
        print(f"DogProfile records: 12")
        print(f"AdoptionPost records: 8")
        print(f"AdoptionApplication records: 12")
        print(f"Validation errors: {validation['error_count']}")
        print(f"Report: {report_path.relative_to(project_root()).as_posix()}")
        return 0
    except Exception as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
