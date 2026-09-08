# PawConnect - Generated Adoption Validation Report

- dataset_version: `generated-adoption-0.1.0`
- schema_contract_version: `v0.1`
- generated_at: `2026-09-06T11:00:00+07:00`
- random_seed: `20260908`
- command: `python data-pipeline/src/generate_adoption_demo.py`

## Output files

- `data-pipeline/data/generated/adoption/dog_profiles.csv`: sha256 `36f6326ef54ed6e33276f1509f8e7631a5ef3369db7d46e7bfa65006be8ff5b0`
- `data-pipeline/data/generated/adoption/adoption_posts.csv`: sha256 `af3bea9496a3f2f409d60f652656e74f39f13ee780080952ee1f0b1862dbfe9c`
- `data-pipeline/data/generated/adoption/adoption_applications.csv`: sha256 `045aebd033ac99f180d661edafec80eb8321745fe87d2e2562cd0fadec4bfe99`

## Record counts

| Dataset | Records |
| --- | ---: |
| DogProfile generated | 12 |
| AdoptionPost generated | 8 |
| AdoptionApplication generated | 12 |
| Total generated | 32 |
| Valid records | 32 |
| Error/quarantined records | 0 |

## Validation checks

| Check | Result |
| --- | --- |
| Required headers/reference CSV | PASS |
| Breed exists and size/age/weight range | PASS |
| Enum gender/vaccination/post/application | PASS |
| Unique seed_key and DogProfile image_url | PASS |
| User role, branch manager and customer references | PASS |
| Adoption rules: max one APPROVED, APPROVED closes post, no PENDING on CLOSED/APPROVED post | PASS |
| PII/password/token/API key generated | PASS - none generated |
| Temporary invalid-data validator test | PASS |

## Errors

- None

## Conclusion: PASS

Generated output is separated from curated foundation data. No crawler, Cloudinary upload, SQL, migration, backend, endpoint or database import was executed.
