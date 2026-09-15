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

### Phối hợp TV1/TV3

- **TV3 - đã xác nhận:** `Branch.code`, quan hệ `User -> Branch`, `User.seedKey`, JWT và mapping User ID đã sẵn sàng cho module nhận nuôi.
- **TV1 - cần xác nhận trước Giai đoạn 2B:** database PawConnect chung dùng MySQL 8, import Branch idempotent và cách lookup `branch_code -> Branch.id`.
- **Quyết định TV2 hiện tại:** không tạo `Breed` entity/FK cho nhận nuôi. `DogProfile.breed` giữ tên giống chuẩn từ Seed V3; importer đối chiếu catalog/reference.
- **TV3 - cần bàn giao ở Giai đoạn 7:** chốt thời điểm và contract mở Conversation từ `AdoptionApplication` được duyệt. `Conversation` hiện chỉ nhận `adoptionPostId`, không tạo cross-module JPA mapping.

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

### Phối hợp TV1/TV3

- **TV3:** giữ tương thích `User.seedKey`, `User.branch`, `Branch.code` và ba role `CUSTOMER`, `BRANCH_MANAGER`, `ADMIN`. Nếu đổi mapping phải báo TV2 trước khi đổi contract/stable code.
- **TV1:** giữ `Branch.code` và mapping Branch ổn định. Việc tạo Breed entity/FK là quyết định tích hợp chung, không tự thêm FK làm sai lựa chọn `DogProfile.breed` hiện tại.

## Giai đoạn 2A - Import Seed V3 trên H2 local

**Trạng thái: COMPLETED.**

- Đã thêm `AdoptionSeedImportService`; service không tự chạy khi khởi động ứng dụng và chỉ import khi được gọi tường minh.
- Đọc trực tiếp Seed V3: catalog `breeds.csv`, `breed_references.csv` và ba CSV adoption. `DogProfile.breed` được đối chiếu theo `breed_name`, không tạo hoặc yêu cầu Breed entity/FK.
- Yêu cầu Branch và User fixture đã tồn tại; map `branch_code` qua `Branch.code`, map User bằng `User.seedKey`.
- Validate trước khi ghi: header UTF-8, seed key, breed/provenance, size/tuổi/cân nặng, enum, HTTPS image URL nếu có, FK, role tạo post, branch ownership, tối đa một APPROVED, CLOSED/PENDING và AVAILABLE/APPROVED.
- Lần import đầu trên H2 tạo 30 DogProfile, 30 AdoptionPost, 36 AdoptionApplication. Lần chạy lại tạo 0 record mới và nhận diện đúng 30/30/36 stable key đã có.
- `AdoptionSeedImportServiceIntegrationTest` có một bản Seed lỗi trong thư mục tạm; breed không tồn tại bị reject trước khi ghi record adoption nào.
- Giai đoạn 2B chưa thực hiện: chờ TV1 dựng MySQL chung/import Branch và TV3 import User fixture vào cùng database.

### Phối hợp TV1/TV3

- **TV1 cần bàn giao:** một schema MySQL 8 PawConnect dùng chung; importer reference Branch idempotent; giữ các code `BR_HCM_01`, `BR_HN_01`, `BR_DN_01`; không tách Shop và Adoption thành database riêng.
- **TV3 cần bàn giao:** import `roles.csv` và `users.csv` sau Branch, trước Adoption, trong cùng database; cung cấp mapping User/Branch để TV2 kiểm chứng import.
- **TV2 sẽ thực hiện 2B** khi hai phần trên đã sẵn sàng, theo thứ tự `reference -> catalog -> users -> adoption -> commerce`.

## Giai đoạn 2B - Nghiệm thu import trên MySQL chung

**Trạng thái: WAITING TV1/TV3.**

- Không có code TV2 mới ở giai đoạn này cho đến khi database chung, Branch reference và User fixture đã được TV1/TV3 bàn giao.
- Tiêu chí bắt đầu: TV1 xác nhận MySQL/schema/import Branch; TV3 xác nhận Role/User/Branch mapping cùng database; TV2 có cấu hình local không chứa secret để chạy integration test.
- Tiêu chí nghiệm thu: importer Adoption V3 idempotent trên MySQL, map đúng stable key/code sang ID, không ghi đè dữ liệu vận hành và toàn bộ FK/enum/rule Adoption PASS.

## Giai đoạn 3 - Service và rule nghiệp vụ

**Trạng thái: COMPLETED.**

- Đã thêm DTO request/response và `AdoptionMapper`; controller Giai đoạn 4 chỉ cần truyền email principal, không nhận staff/applicant ID từ request.
- `AdoptionService` quản lý DogProfile, AdoptionPost và AdoptionApplication: tạo/sửa/xóa hồ sơ; tạo/sửa bài; danh sách post AVAILABLE; nộp, xem, duyệt và từ chối đơn.
- Service kiểm soát `ADMIN`/`BRANCH_MANAGER` cho thao tác quản lý và giới hạn Branch Manager trong chi nhánh của mình; chỉ `CUSTOMER` được nộp/xem đơn của chính họ.
- Khi approve, service khóa AdoptionPost, chuyển đơn thành `APPROVED`, chuyển post thành `CLOSED` và từ chối các đơn `PENDING` còn lại. Post đóng thủ công cũng từ chối đơn chờ; post đã có đơn duyệt không được mở lại.
- `AdoptionServiceIntegrationTest` PASS 4 case: phân quyền/branch ownership, approve/close, chặn application PENDING trùng, close post và lọc manage theo branch.
- Chưa có controller/API, giao diện hoặc thao tác Cloudinary; các phần này thuộc Giai đoạn 4-6.

### Phối hợp TV1/TV3

- **TV3:** JWT principal phải tiếp tục là email; role authority và Branch của `BRANCH_MANAGER` phải khớp dữ liệu User. Không đổi cơ chế này mà không báo TV2 vì service dùng để phân quyền.
- **TV1:** chưa cần sửa service nhận nuôi; khi tích hợp MySQL phải giữ Branch ID/mapping để service và importer áp dụng đúng branch scope.

## Giai đoạn 4 - REST API theo hợp đồng TV2

**Trạng thái: COMPLETED.**

- Đã thêm `AdoptionController` với đúng chín endpoint `/api/adoptions` đã chốt: danh sách/chi tiết public, tạo/sửa post, apply, đơn của tôi, manage, approve và reject.
- `POST /api/adoptions` nhận đúng một trong hai lựa chọn: `dogProfileId` có sẵn hoặc `dogProfile` mới; tạo mới DogProfile và AdoptionPost trong cùng transaction, không thêm endpoint DogProfile thứ mười.
- Controller lấy email từ JWT principal; không nhận staff ID hoặc applicant ID từ request body. Validation request trả `400`; rule nghiệp vụ trả `404` hoặc `409`; role sai trả `403`; thiếu đăng nhập trả `401`.
- Đã cập nhật tối thiểu `SecurityConfig` của TV3 để chỉ permit hai GET public đúng contract. JWT và các rule security khác không thay đổi.
- `AdoptionControllerIntegrationTest` PASS 5 case, bao phủ đủ 9 route, public GET, CUSTOMER/manager, branch ownership, create/apply/approve/reject, và lỗi `400/401/403/404/409`.
- `mvn test` toàn bộ hiện BLOCKED bởi `BookingServiceConcurrencyTest` của TV1: test này FAIL cả khi chạy độc lập, kỳ vọng một giao dịch bị optimistic locking nhưng nhận 0. TV2 không sửa test/service TV1.

### Phối hợp TV1/TV3

- **TV3 cần xác nhận:** thay đổi tối thiểu tại `SecurityConfig` chỉ mở public hai GET Adoption đúng contract; JWT principal email và rule `401/403` phải tiếp tục tương thích khi TV3 chỉnh security.
- **TV1 cần xử lý:** `BookingServiceConcurrencyTest` đang fail độc lập, không liên quan code TV2; đồng thời hoàn tất MySQL/reference import để mở Giai đoạn 2B.
- **TV1 cần đối chiếu khi tích hợp commerce:** Seed V3 tách `PuppyListing` khỏi `Product`; không trộn dữ liệu puppy bán với `DogProfile` nhận nuôi.
- **TV2 bàn giao:** 9 endpoint đã sẵn sàng trên H2. Import MySQL và Cloudinary thực chỉ thực hiện sau khi đầu mối TV1/TV3 xác nhận các dependency trên.

## Progress log

| Ngày | Chức năng | File/Module | API | Test | Trạng thái | Bàn giao |
| ---- | --------- | ----------- | --- | ---- | ---------- | -------- |
| 15/09/2026 | Giai đoạn 0: rà soát contract, Seed V3 và backend | Tài liệu TV2, Seed V3, entity/security/test hiện có | Chưa tạo API Adoption | Seed V3 verify/self-test PASS; baseline Maven đã được khắc phục | ✅ | TV3 đã bàn giao User/Role/JWT/Branch; TV1 cần chốt MySQL chung và Branch import cho 2B |
| 15/09/2026 | Giai đoạn 1: domain model nhận nuôi | Entity/enum/repository Adoption, `AdoptionPersistenceTest`, `pom.xml` | Chưa tạo API; chuẩn bị service/API | `mvn test` PASS 11/11; FK, enum, timestamp và unique seed key PASS | ✅ | TV3 giữ mapping User/Branch/role; TV1 giữ Branch code ổn định và không tự đổi lựa chọn breed text |
| 15/09/2026 | Giai đoạn 2A: import Seed V3 trên H2 local | `AdoptionSeedImportService`, summary, integration test | Import gọi tường minh, không chạy khi startup | PASS: tạo 30/30/36, chạy lại idempotent, Seed lỗi rollback | ✅ | Chờ TV1 bàn giao MySQL/Branch reference và TV3 bàn giao Role/User cùng database cho 2B |
| 15/09/2026 | Giai đoạn 2B: nghiệm thu import MySQL chung | Chưa triển khai code mới | Không có API mới | Chưa chạy; phụ thuộc database chung | 🟨 WAITING | TV1: MySQL/Branch importer; TV3: Role/User/Branch mapping; sau đó TV2 chạy import và integration test MySQL |
| 15/09/2026 | Giai đoạn 3: service và rule nghiệp vụ nhận nuôi | DTO/mapper, `AdoptionService`, repository query, integration test | Sẵn sàng cho 9 endpoint Giai đoạn 4 | PASS: role, branch ownership, PENDING trùng, approve/close/reject và manage scope | ✅ | TV3 giữ JWT principal email và role/branch authority; TV1 giữ mapping Branch khi tích hợp MySQL |
| 15/09/2026 | Giai đoạn 4: REST API nhận nuôi | `AdoptionController`, DTO create/validation, MockMvc test, SecurityConfig matcher public | Đủ 9 endpoint theo `TV2.md`; không có endpoint DogProfile riêng | PASS: 5 MockMvc test, 401/403/404/409/400, public GET, branch ownership, approve/reject | ✅ | TV3 review public GET/security; TV1 xử lý Booking concurrency test, MySQL và tách PuppyListing khỏi Product theo Seed V3 |
