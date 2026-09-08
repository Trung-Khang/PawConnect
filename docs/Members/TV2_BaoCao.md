# PawConnect - TV2 Báo cáo tiến độ Data Engineer

## 1. Vai trò và phạm vi của TV2

TV2 phụ trách phân hệ nhận nuôi (`DogProfile`, `AdoptionPost`, `AdoptionApplication`) và kiêm Data Engineer cho shared data pipeline. Trong giai đoạn hiện tại, TV2 chỉ chuẩn bị contract, reference seed, catalog và curated fixture thủ công; chưa triển khai crawler, cleaner, generator, SQL/import DB hay backend.

## 2. Tóm tắt trạng thái hiện tại

| Giai đoạn | Nội dung | Trạng thái |
| --- | --- | --- |
| Giai đoạn 1A - Data Foundation | Data contract, reference seed, breed catalog, DogProfile, User fixture, AdoptionPost/Application, manual validation | COMPLETED |
| Giai đoạn 1B - Data Pipeline Core | Cleaner, normalize, validate, deduplicate, quarantine, manifest | NOT STARTED |
| Giai đoạn 2A - Controlled Crawler | Crawl nguồn đã được duyệt | NOT STARTED |
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
- Chưa bắt đầu crawler, SQL/migration/import DB.
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
| 08/09/2026 | Giai doan 2B | Synthetic Generator adoption demo | `data-pipeline/src/generate_adoption_demo.py`; `data-pipeline/data/generated/adoption/*.csv`; `data-pipeline/reports/generated_adoption_validation.md` | Contract v0.1, curated breed/branch/role/user fixture | `python data-pipeline/src/generate_adoption_demo.py`; `python data-pipeline/src/generate_adoption_demo.py --self-test-invalid` | COMPLETED - 12 DogProfile, 8 AdoptionPost, 12 AdoptionApplication, validation PASS, 0 loi | Generated CSV/report cho dev/test/demo; chua crawler, chua SQL/migration, chua DB import | Cho TV1/TV3 xac nhan schema/import truoc Giai doan DB |

Trạng thái dùng chung: `NOT STARTED`, `IN PROGRESS`, `BLOCKED`, `READY FOR REVIEW`, `COMPLETED`.

## Việc chưa làm

- Chưa có crawler.
- Chưa có cleaner/normalizer tự động.
- Python synthetic generator adoption demo đã sinh 12 DogProfile, 8 AdoptionPost, 12 AdoptionApplication; validation PASS, 0 lỗi.
- Chưa có SQL/migration/import DB.
- Chưa có entity, service, controller hoặc API được triển khai từ data pipeline.
- URL ảnh hiện là placeholder, chưa phải Cloudinary hay ảnh crawl chính thức.
