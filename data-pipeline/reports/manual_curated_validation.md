# Manual Curated Validation - Adoption Batch

## Scope

Validated files:

- `data-pipeline/data/curated/reference/roles.csv`
- `data-pipeline/data/curated/reference/branches.csv`
- `data-pipeline/data/curated/adoption/dog_profiles.csv`
- `data-pipeline/data/curated/fixtures/users.csv`
- `data-pipeline/data/curated/adoption/adoption_posts.csv`
- `data-pipeline/data/curated/adoption/adoption_applications.csv`

## Record Counts

| Dataset | Records |
| --- | ---: |
| users | 6 |
| dog_profiles | 5 |
| adoption_posts | 3 |
| adoption_applications | 4 |

## Validation Results

| Check | Result | Notes |
| --- | --- | --- |
| 1. User `role_code` exists in roles.csv | PASS | All users use `ROLE_ADMIN`, `ROLE_BRANCH_MANAGER`, or `ROLE_CUSTOMER`. |
| 2. Manager `branch_code` exists in branches.csv | PASS | Managers map to `BR_HCM_01`, `BR_HN_01`, `BR_DN_01`. |
| 3. AdoptionPost references valid DogProfile and manager | PASS | All posts reference existing dog profiles and branch managers. |
| 4. Branch manager matches DogProfile `branch_code` | PASS | Milo/HCM, Bong/HN, Lucky/DN all match. |
| 5. AdoptionApplication references valid AdoptionPost and CUSTOMER | PASS | All applicants are customer fixture accounts. |
| 6. Each post has at most one APPROVED application | PASS | Only `post_lucky_closed` has one approved application. |
| 7. Post with APPROVED is CLOSED; CLOSED post has no PENDING application | PASS | Lucky post is `CLOSED`; its other application is `REJECTED`. |
| 8. No duplicate `seed_key` within each file | PASS | Checked users, dog profiles, posts, applications. |
| 9. No real password, PII, token or secret | PASS | Password placeholder is `TV3_SEED_REQUIRED`; phones and avatars are blank; emails use `.test` or `.example`. |

## Conclusion

PASS. The manual curated adoption batch is consistent with data contract v0.1 and ready for TV1/TV3 review before any DB import work.
