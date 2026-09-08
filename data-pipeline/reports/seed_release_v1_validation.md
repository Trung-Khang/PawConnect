# PawConnect Seed Release v1 Validation

## Result

- Status: PASS
- release_id: `pawconnect-seed-v1`
- release_status: `APPROVED_FOR_DEV_TEST_DEMO`
- random_seed: `20260908`
- Deterministic verification: PASS

## Catalog

- Legacy breeds retained unchanged: 12
- New breed admissions: 0
- H'Mong coc duoi: proposal; no trusted adult size/weight reference or confirmed local mapping.
- Pomeranian / Phoc Soc: proposal; local market labels confirmed but adult reference is missing.

| Candidate | Chotot local evidence | Catalog action |
| --- | --- | --- |
| Pomeranian / Phoc Soc | CONFIRMED | proposal; adult reference missing |
| Corgi | CONFIRMED | retained existing curated breed |
| Shiba Inu | NOT_CONFIRMED | not confirmed; no catalog change |
| Golden Retriever | CONFIRMED | retained existing curated breed |
| Labrador Retriever | NOT_CONFIRMED | not confirmed; no catalog change |
| Husky | NOT_CONFIRMED | not confirmed; no catalog change |
| Samoyed | NOT_CONFIRMED | not confirmed; no catalog change |
| Alaskan Malamute | NOT_CONFIRMED | not confirmed; no catalog change |
| Beagle | NOT_CONFIRMED | not confirmed; no catalog change |
| French Bulldog | NOT_CONFIRMED | not confirmed; no catalog change |

## Record Counts

| Dataset | Count |
| --- | ---: |
| breeds | 12 |
| dog_profiles | 24 |
| adoption_posts | 24 |
| adoption_applications | 32 |
| products | 27 |

## Dataset Hashes

| Path | SHA-256 |
| --- | --- |
| `adoption/adoption_applications.csv` | `9de9f0fcc7bada6118a0ae1aeb07cb3f33052e6a70dca11d4d4cb114272b9641` |
| `adoption/adoption_posts.csv` | `c9664712350c4d0274239b7771f1712256daa7a47cd8a3c3aaf164440faa6e2e` |
| `adoption/dog_profiles.csv` | `71a4bd294cd4bc80f1ac2d5b4236e60b335eac7308fd5cfd8cec2a5b5a32fe65` |
| `catalog/breed_proposals.csv` | `70be64ad05b2b206dea7038a84a9f648625fa771329e5cfb1fe14645f49c4fc2` |
| `catalog/breed_references.csv` | `340f03d7d8f931743d9fae4120b706f93a191b31479ce1dd595c41c0dd49e431` |
| `catalog/breeds.csv` | `7b2c9f1a7343f3d11ef3a667813ad55b15f967788885edb537a2f5ee2cb7ee79` |
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
