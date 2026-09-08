# PawConnect Generated Product Validation

- dataset_version: `generated-product-0.1.0`
- schema_contract_version: `v0.2`
- generated_at: `2026-09-08T16:00:00+07:00`
- random_seed: `20260908`
- command: `python data-pipeline/src/generate_product_demo.py`
- products_sha256: `5199afb838f5964fa4bba10571820d359c82661c6449570afeadb1599e86008c`
- deterministic_result: `PASS` for the same seed and unchanged input references.

## Record counts

| Product kind | Records |
| --- | ---: |
| PUPPY | 9 |
| FOOD | 9 |
| ACCESSORY | 9 |
| Total | 27 |

## Branch and category distribution

| Branch code | PUPPY | FOOD | ACCESSORY | Total |
| --- | ---: | ---: | ---: | ---: |
| BR_DN_01 | 3 | 3 | 3 | 9 |
| BR_HCM_01 | 3 | 3 | 3 | 9 |
| BR_HN_01 | 3 | 3 | 3 | 9 |

| Category code | Records |
| --- | ---: |
| CAT_BREEDING_DOG | 9 |
| CAT_FOOD | 9 |
| CAT_ACCESSORY | 9 |

## Price and growth rules

- Puppy prices use only approved market price bands with sample_count >= 3; the median is adjusted by a deterministic +/-10% factor, rounded to 50,000 VND, then clamped to the band min/max.
- Food prices are fixed synthetic values: 180000, 240000, 310000 VND. Accessory prices are fixed synthetic values: 80000, 140000, 120000 VND.
- Food/accessory prices are not inferred from Pet Mart raw data because no confirmed subcategory mapping exists.
- Internal maturity ages are SMALL=10, MEDIUM=14, LARGE=18 months; they do not reuse breeds.csv max_age_months.
- current_weight_kg is a deterministic growth proportion of a sampled adult target and is always greater than zero and below that target. It is not validated against the adult min/max as a current puppy range.

## Validation

| Check | Result |
| --- | --- |
| Reference headers, stable category/branch/breed codes | PASS |
| 27 records; 3 branch x (3 PUPPY + 3 FOOD + 3 ACCESSORY) | PASS |
| Puppy stock, enum, breed, growth, expected adult size and price-band rules | PASS |
| Food/accessory puppy fields empty and stock >= 1 | PASS |
| image_url empty; no Cloudinary, placeholder, PII, secret or raw listing data | PASS |
| Temporary invalid-data self-test | PASS - 5 validation errors caught |

## Limitations

- Generated Product data is for dev/test/demo only; there is no DB import, backend mapping, SQL/migration, or Cloudinary upload.
- Current Product entity/DTO does not yet contain the commercial v0.2 fields; stable codes require a future importer mapping to DB IDs.
- Chợ Tốt listing identity is not copied. Pet Mart category_code remains unavailable in candidate data.
