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

## Giai đoạn 2A - Import Seed V3 trên H2 local

**Trạng thái: COMPLETED.**

- Đã thêm `AdoptionSeedImportService`; service không tự chạy khi khởi động ứng dụng và chỉ import khi được gọi tường minh.
- Đọc trực tiếp Seed V3: catalog `breeds.csv`, `breed_references.csv` và ba CSV adoption. `DogProfile.breed` được đối chiếu theo `breed_name`, không tạo hoặc yêu cầu Breed entity/FK.
- Yêu cầu Branch và User fixture đã tồn tại; map `branch_code` qua `Branch.code`, map User bằng `User.seedKey`.
- Validate trước khi ghi: header UTF-8, seed key, breed/provenance, size/tuổi/cân nặng, enum, HTTPS image URL nếu có, FK, role tạo post, branch ownership, tối đa một APPROVED, CLOSED/PENDING và AVAILABLE/APPROVED.
- Lần import đầu trên H2 tạo 30 DogProfile, 30 AdoptionPost, 36 AdoptionApplication. Lần chạy lại tạo 0 record mới và nhận diện đúng 30/30/36 stable key đã có.
- `AdoptionSeedImportServiceIntegrationTest` có một bản Seed lỗi trong thư mục tạm; breed không tồn tại bị reject trước khi ghi record adoption nào.
- Giai đoạn 2B chưa thực hiện: chờ TV1 dựng MySQL chung/import Branch và TV3 import User fixture vào cùng database.

## Giai đoạn 3 - Service và rule nghiệp vụ

**Trạng thái: COMPLETED.**

- Đã thêm DTO request/response và `AdoptionMapper`; controller Giai đoạn 4 chỉ cần truyền email principal, không nhận staff/applicant ID từ request.
- `AdoptionService` quản lý DogProfile, AdoptionPost và AdoptionApplication: tạo/sửa/xóa hồ sơ; tạo/sửa bài; danh sách post AVAILABLE; nộp, xem, duyệt và từ chối đơn.
- Service kiểm soát `ADMIN`/`BRANCH_MANAGER` cho thao tác quản lý và giới hạn Branch Manager trong chi nhánh của mình; chỉ `CUSTOMER` được nộp/xem đơn của chính họ.
- Khi approve, service khóa AdoptionPost, chuyển đơn thành `APPROVED`, chuyển post thành `CLOSED` và từ chối các đơn `PENDING` còn lại. Post đóng thủ công cũng từ chối đơn chờ; post đã có đơn duyệt không được mở lại.
- `AdoptionServiceIntegrationTest` PASS 4 case: phân quyền/branch ownership, approve/close, chặn application PENDING trùng, close post và lọc manage theo branch.
- Chưa có controller/API, giao diện hoặc thao tác Cloudinary; các phần này thuộc Giai đoạn 4-6.

TV2 đã hoàn thành Giai đoạn 1 của module nhận nuôi:
DogProfile, AdoptionPost, AdoptionApplication, enum/repository và test persistence.

TV2 sẽ dùng H2 local để phát triển/test độc lập. Đây chỉ là DB giả lập
trên máy TV2, không phải database riêng của kiến trúc dự án.

Theo data_handoff, TV1 phụ trách database PawConnect chung và commerce.
Nhờ TV1 xác nhận/hoàn thành các điểm sau để TV2 chuẩn bị Giai đoạn 2 import Seed V3:

1. Database tích hợp chung là MySQL 8 và dùng một schema PawConnect cho cả nhóm.
2. Import reference Seed V3 cho Branch/Category/ServiceType phải idempotent.
3. Branch.code phải map đúng branch_code Seed V3 như BR_HCM_01.
4. Không tách database Shop và Adoption; DogProfile của TV2 sẽ FK tới Branch chung.
5. Báo lại cách TV2 lookup branch_code -> Branch.id khi import adoption.
6. Sau khi pull/merge thay đổi mới, chạy mvn test để xác nhận baseline PASS.

TV2 không tạo Breed entity/FK trong module adoption hiện tại.
DogProfile lưu breed là tên chuẩn từ Seed V3 và importer sẽ validate theo catalog.

## Progress log

| Ngày | Chức năng | File/Module | API | Test | Trạng thái | Bàn giao |
| --- | --- | --- | --- | --- | --- |
| 2026-09-15 | Giai đoạn 0: audit contract, Seed V3 và backend | Tài liệu TV2, Seed V3, entity/security/test hiện có | 9 API adoption chưa tạo | V3 verify/self-test PASS; `mvn test` FAIL ở Shop/Booking compile | COMPLETED - BLOCKED cho Giai đoạn 1 | TV1 xác nhận Breed/Branch code; TV3 xác nhận User-Branch/JWT/Chat; nhóm xử lý baseline build |
| 2026-09-15 | Giai đoạn 1: domain model nhận nuôi và sửa baseline Maven | Entity/enum/repository adoption, `pom.xml`, `AdoptionPersistenceTest` | Chưa tạo API; chuẩn bị cho service/API | `mvn test` PASS 11/11; persistence/FK/enum/unique seed key PASS | COMPLETED | TV2 bàn giao entity/repository; Giai đoạn 2 import Seed V3 dùng Branch/User mapping TV3 và breed text chuẩn từ catalog |
| 2026-09-15 | Giai đoạn 2A: importer Seed V3 trên H2 local | `AdoptionSeedImportService`, summary, integration test | Chưa tạo API; importer gọi tường minh, không chạy startup | PASS: tạo 30/30/36, chạy lại idempotent, invalid seed rollback | COMPLETED | 2B chờ TV1 MySQL/Branch chung và TV3 User fixture trên cùng DB |
| 2026-09-15 | Giai đoạn 3: service và rule nghiệp vụ nhận nuôi | DTO/mapper, `AdoptionService`, repository query, integration test | Chưa tạo controller; chuẩn bị đúng 9 endpoint ở Giai đoạn 4 | PASS: role, branch ownership, duplicate PENDING, approve/close/reject và manage scope | COMPLETED | Giai đoạn 4 có thể triển khai trên H2; 2B vẫn chờ MySQL chung của TV1 |
