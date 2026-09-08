# PawConnect Data Pipeline Workflow

## Mục lục

1. Mục tiêu và giới hạn
2. Kiến trúc pipeline
3. Trạng thái dữ liệu hiện tại
4. Nguồn và provenance
5. Nghiệm thu
6. Vận hành tương lai
7. Quick Start / Quick Update Dataset

## Mục tiêu và giới hạn

Pipeline cung cấp dữ liệu tái lập cho `dev/test/demo`, không phải dữ liệu người dùng thật. Pipeline hiện không triển khai DB import, backend, migration hay Cloudinary. Curated là master; generated là workspace build; `seed/vN` là release immutable.

Raw crawler là local artifact bị gitignore. Không commit raw, sample HTML, PII, secret, `__pycache__` hoặc `.pyc`.

## Kiến trúc pipeline

```text
Curated master -> Seed release immutable -> Importer DB tương lai

Raw crawl -> Cleaner/Dedup -> Candidate/price bands -> review
         -> curated/rule được duyệt -> Seed release vN+1

Generated -> workspace build -> snapshot vào release sau validation
```

Cleaner không tự thêm dữ liệu vào generator/release. Muốn cập nhật release phải review, cập nhật curated hoặc rule, tạo `vN+1`, verify manifest/checksum rồi cập nhật handoff. Không ghi đè v1/v2.

## Trạng thái dữ liệu hiện tại

| Hạng mục | Input | Output/hiện trạng | Kết quả |
| --- | --- | --- | --- |
| Data Foundation | contract, reference CSV | Role/Branch/Category/ServiceType/catalog | Curated master sẵn sàng. |
| Adoption generator | curated catalog/branch/user | generated adoption CSV | 28 DogProfile, 8 post, 12 application; PASS. |
| Breed crawler | Wikipedia allowlist | raw breed batch | Raw/provenance demo, không import trực tiếp. |
| Commercial crawler | configured sources | raw observation | Chợ Tốt/Pet Mart observation, không import trực tiếp. |
| Cleaner + Dedup | raw commercial | candidate + price band | 73 Product candidate, 3 puppy market price band. |
| Product generator | catalog + price-band rule | generated Product CSV | 27 Product; PASS. |
| Contract v0.2 | shared decision | commercial puppy profile | DEV/TEST/DEMO only; chưa DB-ready. |
| Seed v1 | 12-breed snapshot | baseline immutable | 24 dog, 24 post, 32 application, 27 Product. |
| Seed v2 | curated catalog + provenance | default bootstrap | 14 breed, 28 dog, 28 post, 36 application, 27 Product. |

Checksum/count chính thức nằm trong `data-pipeline/data/seed/v2/manifest.json`; không hardcode hash trong tài liệu này.

## Nguồn và provenance

| Nguồn | Vai trò | Giới hạn |
| --- | --- | --- |
| Wikipedia English allowlist | Breed raw demo | Raw/reference review, allowlist và attribution. |
| Chợ Tốt | Public market observation | Không handoff listing, contact, chat, ảnh hoặc URL nguồn. |
| Pet Mart | Public product observation | Chỉ fact sạch; không ảnh/mô tả dài/review. |
| AKC Pomeranian | Adult size/weight reference | Provenance trong `breed_references.csv`. |
| VKA H'Mông cộc đuôi | National breed standard | Provenance trong `breed_references.csv`. |
| Vietnam-Russia Tropical Center | H'Mông maturity/weight research | Provenance trong `breed_references.csv`. |

Robots permission không tự là quyền tái sử dụng nội dung. Không bypass Terms/robots/login/CAPTCHA, không lưu PII/ảnh.

## Nghiệm thu

Đã PASS: `py_compile`; generator validation/self-test; cleaner dry-run/self-test; release build/verify/self-test; immutable v1 check; `git diff --check`.

Chưa triển khai: importer idempotent, DB schema/migration, Product v0.2 Entity/DTO mapping, credential runtime seed, backend mapping và Cloudinary upload.

## Vận hành tương lai

### Crawl breed reference

```powershell
python data-pipeline/src/crawlers/crawl_breeds.py --dry-run
python data-pipeline/src/crawlers/crawl_breeds.py --self-test
python data-pipeline/src/crawlers/crawl_breeds.py --run-id YYYYMMDDTHHMMSSZ
```

Đọc `data-pipeline/config/sources.json` và allowlist trước. Review manifest, request log, checksum/provenance; raw không commit.

### Crawl commercial

Network bị khóa mặc định. Chỉ chạy source được config/review:

```powershell
python data-pipeline/src/crawlers/crawl_commercial.py --source "Chợ Tốt" --allow-network
python data-pipeline/src/crawlers/crawl_commercial.py --source "Chợ Tốt (cho-giong)" --allow-network
python data-pipeline/src/crawlers/crawl_commercial.py --source "Pet Mart" --allow-network
```

Có thể smoke test bằng `--max-pages 2`. Không lấy chat/contact/phone/email/address/image và không bypass restrictions.

### Cleaner và review candidate

```powershell
python data-pipeline/src/clean_commercial_observations.py --dry-run
python data-pipeline/src/clean_commercial_observations.py --self-test
python data-pipeline/src/clean_commercial_observations.py
```

Đọc quarantine/validation report. Candidate/price band không phải DB input; review mapping/evidence trước khi sửa curated/rule.

### Sinh workspace data

```powershell
python data-pipeline/src/generate_adoption_demo.py --seed 20260908
python data-pipeline/src/generate_adoption_demo.py --self-test-invalid
python data-pipeline/src/generate_product_demo.py --seed 20260908
python data-pipeline/src/generate_product_demo.py --self-test
```

Kiểm tra report/checksum. Product generator không nhận raw listing làm input trực tiếp.

### Tạo release mới

Builder hiện hỗ trợ `v1` và `v2`. Không ghi đè release; để phát hành v3 phải mở rộng explicit allowlist trong builder trước.

```powershell
python data-pipeline/src/build_seed_release.py --release v2 --seed 20260908
python data-pipeline/src/build_seed_release.py --verify v2
python data-pipeline/src/build_seed_release.py --self-test
```

Review `manifest.json`, validation report, count/checksum, rồi mới đổi default bootstrap/handoff.

### Checklist commit

- Stage code/config/report/curated/seed đã review.
- Không stage raw, sample HTML, PII, secret, pycache.
- Chạy `git diff --check`, `git status --short`, sau đó commit/push trên branch TV2.

## Quick Start / Quick Update Dataset

**Không mạng:** đọc contract/manifest, chạy generator self-test, cleaner dry-run/self-test, release verify.

**Có mạng:** chỉ crawl với `--allow-network` sau khi source, Terms, robots và scope được review.

**Cập nhật data:** backup/review curated -> chạy generator -> review report -> tạo release version mới -> verify/self-test -> cập nhật `docs/Project/data_handoff.md`. Không thay đổi release cũ.
