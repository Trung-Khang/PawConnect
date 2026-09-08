# PawConnect Seed Release v2 Validation

## Result

- Status: PASS
- release_id: `pawconnect-seed-v2`
- release_status: `APPROVED_FOR_DEV_TEST_DEMO`
- random_seed: `20260908`
- Deterministic verification: PASS

## Catalog

- Legacy breeds retained unchanged: 12
- New breed admissions: 2
- Pomeranian: admitted with PASS adult provenance.
- H'Mong coc duoi: admitted with VKA/Tropical Center adult evidence and a 12-month internal maturity threshold.

| Candidate | Chotot local evidence | Catalog action |
| --- | --- | --- |
| Pomeranian / Phoc Soc | CONFIRMED | admitted with PASS adult provenance |
| Corgi | CONFIRMED | retained existing curated breed |
| Shiba Inu | NOT_CONFIRMED | retained existing curated breed; no new admission |
| Golden Retriever | CONFIRMED | retained existing curated breed |
| Labrador Retriever | NOT_CONFIRMED | retained existing curated breed; no new admission |
| Husky | NOT_CONFIRMED | retained existing curated breed; no new admission |
| Samoyed | NOT_CONFIRMED | retained existing curated breed; no new admission |
| Alaskan Malamute | NOT_CONFIRMED | retained existing curated breed; no new admission |
| Beagle | NOT_CONFIRMED | retained existing curated breed; no new admission |
| French Bulldog | NOT_CONFIRMED | retained existing curated breed; no new admission |

## Record Counts

| Dataset | Count |
| --- | ---: |
| breeds | 14 |
| dog_profiles | 28 |
| adoption_posts | 28 |
| adoption_applications | 36 |
| products | 27 |

## Dataset Hashes

| Path | SHA-256 |
| --- | --- |
| `adoption/adoption_applications.csv` | `64490def721e02247bb1ea9b76e27780824e6245762e89076a5cf69f2d059dd3` |
| `adoption/adoption_posts.csv` | `18ea562ec3baeae6a5271ceb1c405330654c221158c63bd56c42a7a72a768562` |
| `adoption/dog_profiles.csv` | `e614c9e23f06f78b94d8b0e1bf0cc8d9c8dcb2de17377d3980ceff03fd4fbcc0` |
| `catalog/breed_proposals.csv` | `6e60561725be4f2b6258e5000924b5546bd8a9212e60bd430888022f3158383b` |
| `catalog/breed_references.csv` | `8810fb9c09d188c99d91cf5073ff5ea887fa06261ad2f7a2866638464fe5a3eb` |
| `catalog/breeds.csv` | `a49035ee499f048e4e0374f57f537b5538f53c7f728ca55d391291b75fd7c1aa` |
| `fixtures/users.csv` | `22d8c487f87271f5ee91ff9ecd2df97877241050f19d2cca815f6e4207366d52` |
| `product/products.csv` | `945964fa150bc0f7d08d44ba8a83bfb776848554f9c11d39bb800982b82f5895` |
| `reference/branches.csv` | `b790127df617275725e48c482b41ebb47c05670327dab373831ea4ce5f4c87ca` |
| `reference/categories.csv` | `a6da239cf7f66be1ced75a789793a32696498fbd3ff56232bea37f37fbe65578` |
| `reference/roles.csv` | `483bd3b45e26390e06ca56a1b634ba496b42624e0db42b6a3e544842a2aec46c` |
| `reference/service_types.csv` | `4fb76a9633bbcab36551d02ecda0a1d34f23f9e9274f3dac5599b6e5a2951703` |

## Limits

- No importer, backend mapping, migration, DB import, or Cloudinary integration is included.
- Raw/candidate commercial observations are not copied into this release and are not DB import input.
- Product v0.2 remains pending Entity/DTO, enum, migration, and backward-compatibility integration.
