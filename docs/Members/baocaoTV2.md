# Báo cáo TV2 - Catalog và module nhận nuôi

## Giai đoạn 0 - Chốt hiện trạng và phạm vi

**Trạng thái: COMPLETED - có blocker trước Giai đoạn 1.**

### Phạm vi đã rà soát

- `docs/Project/data_contract.md`, `docs/Project/data_handoff.md`, `docs/Members/TV2.md` và `docs/Project/Workflow.md`.
- Seed V3: manifest, báo cáo validation, User fixture và ba dataset adoption.
- Entity, repository, security, Conversation/WebSocket, cấu hình ứng dụng và test Java hiện có.

### Hiện trạng đã xác nhận

| Hạng mục | Kết quả |
| --- | --- |
| Seed V3 | PASS; có 15 Breed, 6 User, 30 DogProfile, 30 AdoptionPost và 36 AdoptionApplication. |
| Entity nhận nuôi | Chưa có `DogProfile`, `AdoptionPost`, `AdoptionApplication`; chưa có enum nhận nuôi, repository, DTO, service, controller hoặc test của TV2. |
| API TV2 | Chưa có 9 endpoint adoption đã được giao trong `docs/Members/TV2.md`. |
| Nền tảng TV3 | Đã có `User`, `RoleName` gồm `CUSTOMER`, `BRANCH_MANAGER`, `ADMIN`; JWT, `@PreAuthorize`, exception handler và `Conversation` dùng `adoptionPostId` dạng `Long`. |
| Mapping Seed V3 | `Branch.code`, `User.seedKey` và `User.branch` đã có từ TV3. Theo quyết định hiện tại, DogProfile lưu `breed` là tên giống chuẩn từ Seed V3; catalog/reference được dùng để validate ở importer, không cần Breed entity/FK. |
| Ảnh | Seed V3 để trống `image_url`; TV1 quản lý nội dung/upload, TV3 xây Cloudinary service/API, TV2 chỉ tích hợp URL/metadata ảnh vào module nhận nuôi sau khi hai phần này sẵn sàng. |

### Rule phải giữ khi triển khai

- `DogProfile`: `size` thuộc `SMALL|MEDIUM|LARGE`, `gender` thuộc `MALE|FEMALE`, `vaccination_status` thuộc `NOT_VACCINATED|PARTIALLY_VACCINATED|FULLY_VACCINATED`, tuổi dùng `age_months`.
- `AdoptionPost`: chỉ `AVAILABLE|CLOSED`; người tạo là `ADMIN` hoặc `BRANCH_MANAGER`; manager phải cùng Branch với DogProfile.
- `AdoptionApplication`: chỉ `PENDING|APPROVED|REJECTED`; applicant là `CUSTOMER`; mỗi post tối đa một `APPROVED`; approve phải đóng post và không để đơn khác ở `PENDING`.
- `DogProfile` nhận nuôi không phải `PuppyListing` hoặc `Product` thương mại.
- Import phải map `seed_key`/stable code sang database ID, có idempotency và không ghi đè dữ liệu vận hành đã chỉnh sửa.

### Blocker và bàn giao cần xác nhận

1. **Đã xử lý:** TV3 đã bổ sung `Branch.code`, quan hệ `User -> Branch`, `User.seedKey`, JWT và API mapping User ID.
2. **Đã xử lý:** baseline Maven đã compile/test được sau khi nâng Lombok và cấu hình annotation processor/Surefire cho JDK 26.
3. **Quyết định hiện tại:** không tạo `Breed` entity/FK cho module nhận nuôi; `DogProfile.breed` giữ tên chuẩn từ Seed V3 và importer Giai đoạn 2 sẽ đối chiếu catalog/reference.
4. **Cần bàn giao ở Giai đoạn 7:** TV3 chốt thời điểm và contract mở Conversation từ `AdoptionApplication` được duyệt; `Conversation` hiện chỉ nhận `adoptionPostId` và không tạo cross-module JPA mapping.

### File đã tạo trong Giai đoạn 1

- TV2: năm enum, ba entity, ba repository và `AdoptionPersistenceTest` cho module nhận nuôi.
- Cấu hình baseline: `pom.xml` cập nhật Lombok annotation processor và Surefire cho JDK 26.
- Chưa tạo DTO, mapper, service, controller hoặc importer Seed V3; các phần này thuộc giai đoạn tiếp theo.
- Không tạo `Breed` entity ở phạm vi module nhận nuôi hiện tại.

### Kết quả kiểm tra

| Lệnh | Kết quả | Ghi chú |
| --- | --- | --- |
| `python -m py_compile data-pipeline/src/build_seed_release.py data-pipeline/src/build_seed_v3.py` | PASS | Builder V3 biên dịch được. |
| `python data-pipeline/src/build_seed_release.py --verify v3` | PASS | Manifest, checksum, quan hệ và validation release hợp lệ. |
| `python data-pipeline/src/build_seed_release.py --self-test-v3` | PASS | Validator từ chối puppy stock không hợp lệ. |
| `mvn test` | PASS | 11 tests pass, gồm `AdoptionPersistenceTest`; Lombok/Byte Buddy đã được cấu hình tương thích JDK 26. |

## Giai đoạn 1 - Domain model nhận nuôi

**Trạng thái: COMPLETED.**

- Đã thêm `DogProfile`, `AdoptionPost`, `AdoptionApplication`, năm enum chuẩn contract và ba repository tương ứng.
- `DogProfile` dùng `breed` là tên giống chuẩn trong `seed/v3/adoption/dog_profiles.csv`; không tạo `Breed` entity hoặc FK theo quyết định hiện tại.
- Đã thêm `seedKey` unique cho ba entity để chuẩn bị importer/idempotency; DogProfile gắn Branch, AdoptionPost gắn DogProfile/User tạo bài, AdoptionApplication gắn Post/User nộp đơn.
- Đã thêm `imageUrl` và `imagePublicId` cho DogProfile/AdoptionPost để nhận metadata từ Cloudinary sau này, không lưu secret hoặc ảnh trong Seed V3.
- `AdoptionPersistenceTest` kiểm tra FK, enum, repository query, timestamp và chặn `seedKey` DogProfile trùng.
- Rule approve/close, kiểm tra role và branch ownership chưa được đặt trong entity; sẽ được enforce ở service/API Giai đoạn 3-4.
- Đã sửa baseline Maven: Lombok `1.18.48` với annotation processor cho JDK 26; Surefire bật chế độ tương thích Byte Buddy. `mvn test` PASS 11/11.

## Progress log

| Ngày | Chức năng | File/Module | API | Test | Trạng thái | Bàn giao |
| --- | --- | --- | --- | --- | --- |
| 2026-09-15 | Giai đoạn 0: audit contract, Seed V3 và backend | Tài liệu TV2, Seed V3, entity/security/test hiện có | 9 API adoption chưa tạo | V3 verify/self-test PASS; `mvn test` FAIL ở Shop/Booking compile | COMPLETED - BLOCKED cho Giai đoạn 1 | TV1 xác nhận Breed/Branch code; TV3 xác nhận User-Branch/JWT/Chat; nhóm xử lý baseline build |
| 2026-09-15 | Giai đoạn 1: domain model nhận nuôi và sửa baseline Maven | Entity/enum/repository adoption, `pom.xml`, `AdoptionPersistenceTest` | Chưa tạo API; chuẩn bị cho service/API | `mvn test` PASS 11/11; persistence/FK/enum/unique seed key PASS | COMPLETED | TV2 bàn giao entity/repository; Giai đoạn 2 import Seed V3 dùng Branch/User mapping TV3 và breed text chuẩn từ catalog |
