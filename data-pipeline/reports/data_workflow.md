# PawConnect Data Workflow

## Bootstrap hien hanh

Seed duy nhat cho importer la `data-pipeline/data/seed/v3/`. Curated la master data; v3 la release immutable. Raw crawl, candidate va workspace khong phai input importer. Importer chay theo thu tu `reference -> catalog -> users -> adoption -> commerce`.

## Cap nhat catalog va commerce

1. Crawl chi sau khi source, robots va Terms duoc review.
2. Chay cleaner, review quarantine va candidate.
3. Cap nhat curated catalog hoac commerce rule sau khi co evidence.
4. Tao release moi, verify manifest/checksum va cap nhat handoff.

Raw khong duoc chua PII, contact, anh tai ve hay secret. Candidate khong duoc import DB truc tiep.

## Nguon va evidence

Breed reference dung Wikipedia allowlist va cac reference adult da duoc review trong `curated/catalog/breed_references.csv`. Commercial observation chi dung source da duoc phe duyet trong config; robots va Terms phai duoc kiem tra truoc khi bat network. Evidence tu raw/candidate chi de review, khong phai du lieu nghiep vu hay importer input.

De them breed moi: tao stable `breed_code`, ghi adult size/weight/maturity vao curated catalog, them provenance PASS, sau do cap nhat price rule neu breed duoc phep ban puppy. Thieu evidence thi chi de xuat, khong dua vao release.

De them PuppyListing, Product hoac Adoption data: cap nhat curated rule/input da review, giu stable key va FK hop le, chay validation, roi build release moi. Khong dung raw listing hay candidate de ghi truc tiep vao release.

## Lenh van hanh

    python data-pipeline/src/crawlers/crawl_breeds.py --dry-run
    python data-pipeline/src/crawlers/crawl_breeds.py --self-test
    python data-pipeline/src/crawlers/crawl_commercial.py --source "Chợ Tốt" --max-pages 2 --allow-network
    python data-pipeline/src/clean_commercial_observations.py --dry-run
    python data-pipeline/src/clean_commercial_observations.py --self-test
    python data-pipeline/src/build_seed_release.py --verify v3
    python data-pipeline/src/build_seed_release.py --self-test-v3

Crawler commercial chi dung `--allow-network` khi source da duoc phe duyet. Truoc commit: chay verify, `git diff --check` va kiem tra khong stage raw, candidate, workspace, cache hay secret.

## Phat hanh ke tiep

Khong sua truc tiep v3. Builder phat hanh moi phai co version moi, manifest, checksum, report validation va handoff da cap nhat. Quy trinh release la review evidence va curated/rule -> build version ke tiep -> verify va self-test -> review manifest/count/checksum -> cap nhat handoff -> commit. Seed v3 van la bootstrap hien hanh cho den khi release moi duoc duyet.
