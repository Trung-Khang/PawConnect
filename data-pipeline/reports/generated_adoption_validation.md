# PawConnect - Generated Adoption Validation Report

- dataset_version: `generated-adoption-0.1.0`
- schema_contract_version: `v0.1`
- generated_at: `2026-09-06T11:00:00+07:00`
- random_seed: `20260908`
- command: `python data-pipeline/src/generate_adoption_demo.py`

## Output files

- `data-pipeline/data/generated/adoption/dog_profiles.csv`: sha256 `ea0e1247e66822b08989605d314a9d6646d71d19a626bfede380164b8b0338e0`
- `data-pipeline/data/generated/adoption/adoption_posts.csv`: sha256 `373bda83ea440f0ef1bfbee0de2450de75584f791064e511e799128290150f2e`
- `data-pipeline/data/generated/adoption/adoption_applications.csv`: sha256 `fd1b89c3b4e1815438a2fcd4db67aca23aebc1054a1826bd6c6fb2742eca79d6`

## Record counts

| Dataset | Records |
| --- | ---: |
| DogProfile generated | 28 |
| AdoptionPost generated | 8 |
| AdoptionApplication generated | 12 |
| Total generated | 48 |
| Valid records | 48 |
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
