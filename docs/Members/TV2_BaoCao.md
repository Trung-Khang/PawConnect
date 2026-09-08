# PawConnect - TV2 Báo cáo tiến độ Data Engineer

## 1. Vai trò và phạm vi của TV2

TV2 phụ trách phân hệ nhận nuôi (`DogProfile`, `AdoptionPost`, `AdoptionApplication`) và kiêm Data Engineer cho shared data pipeline. Trong giai đoạn hiện tại, TV2 đã chuẩn bị contract, reference seed, catalog, curated fixture thủ công, raw crawler demo có kiểm soát cho catalog giống chó và synthetic generator; cleaner/dedup, candidate/curated merge, SQL/import DB và backend chưa triển khai.

## 2. Tóm tắt trạng thái hiện tại

| Giai đoạn | Nội dung | Trạng thái |
| --- | --- | --- |
| Giai đoạn 1A - Data Foundation | Data contract, reference seed, breed catalog, DogProfile, User fixture, AdoptionPost/Application, manual validation | COMPLETED |
| Giai đoạn 1B - Data Pipeline Core | Cleaner, normalize, validate, deduplicate, quarantine, manifest | NOT STARTED |
| Giai đoạn 2A - Controlled Crawler | Wikipedia raw breed crawler demo: 3 URL allowlist, 3 raw records, validation PASS | COMPLETED |
| Giai đoạn 2B - Synthetic Generator | Python synthetic generator adoption demo đã sinh 12 DogProfile, 8 AdoptionPost, 12 AdoptionApplication; validation PASS, 0 lỗi | COMPLETED |
| Giai đoạn 3 - Database Integration | Mapping ID, migration/seed, import idempotent | NOT STARTED |
| Giai đoạn 4 - Module Development | API/chức năng TV1, TV2, TV3 dùng dữ liệu | NOT STARTED |
| Giai đoạn 5 - Integration, Test và Demo | Tích hợp, test và demo | NOT STARTED |

## 3. Giai đoạn 1A - Data Foundation

Đã hoàn thành dữ liệu nền thủ công cho dev/test/demo:

- `docs/Project/data_contract.md`: contract v0.1 cho quy ước dữ liệu, enum, stable code và rule adoption.
- Reference seed: Role, Branch, Category, ServiceType.
- Catalog giống chó nội bộ thủ công, chưa phải dữ liệu crawl.
- Curated adoption fixture: DogProfile, User fixture, AdoptionPost, AdoptionApplication.
- Manual validation report: PASS.

## 4. Thống kê số lượng bản ghi

| Dữ liệu | Số bản ghi |
| --- | ---: |
| Role | 3 |
| Branch | 3 |
| Category | 4 |
| ServiceType | 3 |
| Breed | 12 |
| DogProfile | 5 |
| User fixture | 6 |
| AdoptionPost | 3 |
| AdoptionApplication | 4 |
| Manual validation | PASS |

## 5. Chi tiết file bàn giao

| File | Dữ liệu | Người sở hữu nghiệp vụ | Người sử dụng | Dùng hiện tại | Bàn giao gần tới |
| --- | --- | --- | --- | --- | --- |
| `data-pipeline/data/curated/reference/roles.csv` | Role | TV3 | Cả nhóm tham chiếu phân quyền | Mapping role bằng `role_code` | TV3 xác nhận role name khớp Spring Security |
| `data-pipeline/data/curated/reference/branches.csv` | Branch | TV1 | TV2/TV3 dùng cho liên kết chi nhánh | Mapping bằng `branch_code` | TV1 xác nhận branch seed |
| `data-pipeline/data/curated/reference/categories.csv` | Category | TV1 | TV1 dùng cho Product | Mapping Product bằng `category_code` | TV1 xác nhận category seed |
| `data-pipeline/data/curated/reference/service_types.csv` | ServiceType | TV1 | TV1 dùng cho ServiceBooking | Mapping bằng `service_type_code` | TV1 xác nhận thời lượng và giá |
| `data-pipeline/data/curated/catalog/breeds.csv` | Breed catalog | TV2 duy trì | TV1 và TV2 dùng chung để sinh dữ liệu chó hợp lý | Rule size, tuổi, cân nặng | TV1/TV2 dùng khi tạo Product/DogProfile mở rộng |
| `data-pipeline/data/curated/adoption/dog_profiles.csv` | DogProfile | TV2 | TV2, có thể liên kết dịch vụ sau này | Hồ sơ chó nhận nuôi demo | TV1/TV3 xác nhận `DogProfile.branch_id` |
| `data-pipeline/data/curated/fixtures/users.csv` | User fixture | TV3 sở hữu cách import và credential | TV1/TV2 dùng khóa tham chiếu khi test | Tham chiếu manager/customer/admin | TV3 xác nhận hash password và User-Branch |
| `data-pipeline/data/curated/adoption/adoption_posts.csv` | AdoptionPost | TV2 | TV3 dùng để tích hợp Conversation/Chat | 2 AVAILABLE, 1 CLOSED | TV3 xác nhận mapping Conversation |
| `data-pipeline/data/curated/adoption/adoption_applications.csv` | AdoptionApplication | TV2 | TV3 dùng cho authorization và Chat | PENDING, APPROVED, REJECTED | TV3 xác nhận luồng approve/chat |
| `data-pipeline/reports/manual_curated_validation.md` | Báo cáo validation | Cả nhóm | Cả nhóm kiểm tra chất lượng bàn giao | Manual validation PASS | Dùng làm baseline trước khi có validator tự động |

## 6. Validation đã thực hiện

- Mọi `role_code` của User tồn tại trong `roles.csv`.
- Mọi `branch_code` của manager tồn tại trong `branches.csv`.
- Mọi AdoptionPost tham chiếu DogProfile và manager hợp lệ.
- Branch manager khớp `branch_code` của DogProfile.
- Mọi AdoptionApplication tham chiếu AdoptionPost và CUSTOMER hợp lệ.
- Mỗi post có tối đa một APPROVED application.
- Post có APPROVED phải CLOSED; post CLOSED không còn application PENDING.
- Không trùng `seed_key` trong từng file.
- Không chứa password thật, PII, token hoặc secret.

## 7. Dependency và nội dung cần TV1/TV3 xác nhận

- TV1 và TV3 xác nhận `DogProfile.branch_id`.
- TV3 xác nhận liên kết User-Branch cho `BRANCH_MANAGER`.
- TV3 xác nhận cách tạo credential/hash password demo.
- TV1 xác nhận Branch, Category và ServiceType seed.
- Enum Java phải khớp data contract hoặc có mapping rõ ràng.

## 8. Công việc gần nhất tiếp theo

- Giai đoạn 1B: thiết kế cleaner/normalizer/validator/deduplicate/quarantine/manifest.
- Cleaner/normalizer/deduplicate/quarantine và SQL/migration/import DB chưa bắt đầu.
- Chỉ chuyển sang DB integration sau khi TV1/TV3 xác nhận contract và schema.

## 9. Quy trình cập nhật báo cáo về sau

1. Đọc data contract trước khi code.
2. Ghi nhận dữ liệu/module nhận từ thành viên khác.
3. Chỉ đánh dấu `COMPLETED` khi có code và test.
4. Ghi rõ file đã sửa, API/chức năng, lệnh test và kết quả.
5. Ghi đầu ra bàn giao cho thành viên nào.
6. Ghi blocker hoặc contract conflict.
7. Không tự đổi schema/enum/stable code của module khác.
8. Cập nhật báo cáo sau khi test, trước khi commit.

## 10. Mẫu progress log

| Ngày | Giai đoạn | Hạng mục | File đã sửa | Dữ liệu nhận | Test | Kết quả | Bàn giao | Blocker |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
|  | Giai đoạn 1A | Data Foundation | CSV/Markdown curated | Contract v0.1 | Manual validation | COMPLETED | TV1/TV3 review | Chờ xác nhận schema/mapping |
| 08/09/2026 | Giai đoạn 2A | Controlled Breed Crawler raw demo | `data-pipeline/src/crawlers/crawl_breeds.py`; `sources.json`; raw Wikipedia JSONL; validation report | Wikipedia English, 3 URL article allowlist | dry-run, self-test, crawl 3 trang, checksum/rate-limit/curated hash | COMPLETED - fetched 3, accepted 3, skipped 0 | Raw only; chưa cleaner, dedup, candidate/curated merge hoặc DB import | Review nguồn và mapping trước khi mở rộng allowlist |
| 08/09/2026 | Giai doan 2B | Synthetic Generator adoption demo | `data-pipeline/src/generate_adoption_demo.py`; `data-pipeline/data/generated/adoption/*.csv`; `data-pipeline/reports/generated_adoption_validation.md` | Contract v0.1, curated breed/branch/role/user fixture | `python data-pipeline/src/generate_adoption_demo.py`; `python data-pipeline/src/generate_adoption_demo.py --self-test-invalid` | COMPLETED - 12 DogProfile, 8 AdoptionPost, 12 AdoptionApplication, validation PASS, 0 loi | Generated CSV/report cho dev/test/demo; chua crawler, chua SQL/migration, chua DB import | Cho TV1/TV3 xac nhan schema/import truoc Giai doan DB |

Trạng thái dùng chung: `NOT STARTED`, `IN PROGRESS`, `BLOCKED`, `READY FOR REVIEW`, `COMPLETED`.

## Việc chưa làm

- Đã có controlled raw crawler demo cho 3 URL Wikipedia; chưa có cleaner, dedup hoặc candidate/curated merge.
- Chưa có cleaner/normalizer tự động.
- Python synthetic generator adoption demo đã sinh 12 DogProfile, 8 AdoptionPost, 12 AdoptionApplication; validation PASS, 0 lỗi.
- Chưa có SQL/migration/import DB.
- Chưa có entity, service, controller hoặc API được triển khai từ data pipeline.
- URL ảnh hiện là placeholder, chưa phải Cloudinary hay ảnh crawl chính thức.

## 11. Progress log - Data Contract v0.2 Draft

| Ngày | Giai đoạn | Hạng mục | File đã sửa | Test | Kết quả | Bàn giao | Blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 08/09/2026 | Contract v0.2 | Commercial puppy/Product profile | `docs/Project/data_contract.md`; báo cáo TV1/TV2/TV3 | `git diff --check` | APPROVED FOR DEV/TEST/DEMO - TV2/Data Engineer chốt chuẩn dữ liệu chung | Adult breed reference tách khỏi puppy observation; chưa approved production/DB import | TV1 triển khai mapping Product/DTO; TV3 hỗ trợ enum, migration và backward compatibility |

Contract v0.2 bổ sung `breed_type`, `life_stage`, `age_months`, `current_weight_kg`,
`current_size`, `expected_adult_size` và `breed_code` cho commercial Product. Raw
commercial chỉ là observation; dữ liệu thiếu để trống, không ghi đè curated/DB.
DogProfile adoption và Product thương mại là hai dataset/entity nghiệp vụ khác nhau.
Nếu conflict với code, TV1/TV3 báo lại TV2; không tự đổi stable code, enum hoặc contract.

## 12. Progress log - Commercial crawler raw observation

| Ngày | Giai đoạn | Hạng mục | File đã sửa | Test | Kết quả | Bàn giao | Blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 08/09/2026 | Giai đoạn 2A.2 | Commercial raw crawler | `data-pipeline/config/commercial_sources.json`; `data-pipeline/src/crawlers/crawl_commercial.py`; CSV raw theo source | `py_compile`; fixture Chợ Tốt 19 record; smoke crawl network | COMPLETED - Chợ Tốt search: 38 record/2 trang/3 request; Chợ Tốt cho-giong: 54 record/3 trang/3 request; Pet Mart: 96 record/8 trang/8 request | Raw market observation cho bước Cleaner + Dedup; chưa candidate/curated merge hay DB import | Cần chuẩn hóa và dedup trước khi dùng làm candidate |

Crawler chạy theo offline-first: network bị khóa mặc định và chỉ crawl thật khi truyền `--allow-network`. Parser ưu tiên JSON-LD/`__NEXT_DATA__`, fallback HTML bằng `html.parser`; phân trang dùng `page_url_pattern` và `max_pages` trong config hoặc CLI override. Retry áp dụng cho HTTP 408/429/5xx, redirect chỉ cùng domain, rate limit tối thiểu 2 giây. Raw commercial chỉ thu thập trường observation cho phép, không lấy PII, ảnh hay secret.

## 13. Progress log - Giai đoạn 1B Commercial Cleaner + Deduplication

| Ngày | Giai đoạn | Hạng mục | File đã sửa | Test | Kết quả | Bàn giao | Blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 08/09/2026 | Giai đoạn 1B | Commercial Cleaner + Deduplication | `data-pipeline/src/clean_commercial_observations.py`; candidate CSV; quarantine/validation report | `py_compile`; `--dry-run`; cleaning; `--self-test`; `git diff --check` | COMPLETED - Pet Mart: 73 candidate, 23 `invalid_price` quarantine; Chợ Tốt không bàn giao listing cá thể; 3 market price bands; 4 duplicate; 64 `age_unverified` | Candidate product, aggregate price band và báo cáo cho bước candidate review | Chưa Product generator, DB import, SQL/migration hoặc backend; `category_code` Pet Mart vẫn rỗng do raw không chứng minh được |
