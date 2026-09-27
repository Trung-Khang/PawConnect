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
- **TV3 - đã tích hợp ở Giai đoạn 7:** khi `AdoptionApplication` được duyệt, TV2 gọi `ConversationService.openForAdoption` với Customer, người tạo bài và `adoptionPostId`; vẫn không tạo cross-module JPA mapping.

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
- Giai đoạn 2B đã nghiệm thu trên MySQL 8 local dùng database chung `pawconnect`: reference có 3 Branch, 4 Category, 3 ServiceType; User fixture có 6 account; Adoption có đúng 30 DogProfile, 30 AdoptionPost và 36 AdoptionApplication.

### Phối hợp TV1/TV3

- **TV1 đã bàn giao:** profile MySQL 8, importer reference/commerce idempotent và các Branch code ổn định; Shop và Adoption dùng cùng database `pawconnect`.
- **TV3 đã bàn giao:** Role/User fixture chạy trước Adoption và mapping User/Branch hợp lệ trong cùng database.
- **TV2 đã hoàn tất 2B** theo thứ tự `reference -> catalog -> users -> adoption -> commerce`; importer chạy lại giữ nguyên count `30/30/36`.

## Giai đoạn 2B - Nghiệm thu import trên MySQL chung

**Trạng thái: COMPLETED.**

- TV1 đã bàn giao MySQL Connector/J, profile `mysql`, `ddl-auto=update` và cờ `pawconnect.seed.enabled` cho importer Branch/Category/ServiceType/Product/PuppyListing.
- TV2 thêm `AdoptionSeedInitializer`; runner chỉ chạy khi `pawconnect.seed.adoption.enabled=true`, đọc Seed V3 từ `pawconnect.seed.release-directory` và in summary created/existing. Không chạy tự động ở startup thông thường.
- Thêm `AdoptionSeedImportMySqlIntegrationTest`, chỉ chạy khi `RUN_MYSQL_INTEGRATION_TESTS=true`; test import V3 hai lần, kiểm count `30/30/36` và idempotency lần hai.
- `application-mysql.yml` không còn default `MYSQL_PASSWORD`; password chỉ lấy từ environment local. Stable PuppyListing code giữ nguyên dạng `BREED_*`, ví dụ `BREED_CORGI`, không rút gọn thành `CORGI`.

### Trình tự nghiệm thu MySQL

1. Chạy reference/commerce của TV1 với `pawconnect.seed.enabled=true` và không bật seed User/Adoption.
2. Chạy User fixture của TV3 với profile `mysql,dev`, `SEED_USERS_ENABLED=true` và `SEED_USER_PASSWORD` local.
3. Chạy Adoption của TV2 với `pawconnect.seed.adoption.enabled=true`, không bật hai seed còn lại.
4. Bật `RUN_MYSQL_INTEGRATION_TESTS=true` để chạy acceptance test; không dùng database đã có dữ liệu vận hành cần bảo toàn.

- Đã chạy MySQL thật với credential chỉ đặt local. Importer Adoption V3 idempotent: count giữ nguyên `30/30/36` sau lần chạy lại; tất cả FK Application -> User/Post và Post -> DogProfile hợp lệ.
- Rule dữ liệu MySQL PASS: `AVAILABLE=24`, `CLOSED=6`; `PENDING=24`, `APPROVED=6`, `REJECTED=6`; không có APPROVED trên AVAILABLE, PENDING trên CLOSED hoặc post có hơn một APPROVED.

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
- **TV1 cần xử lý:** `BookingServiceConcurrencyTest` từng fail độc lập, không liên quan code TV2. MySQL/reference import đã hoàn tất cho 2B.
- **TV1 cần đối chiếu khi tích hợp commerce:** Seed V3 tách `PuppyListing` khỏi `Product`; không trộn dữ liệu puppy bán với `DogProfile` nhận nuôi.
- **TV2 bàn giao:** 9 endpoint đã sẵn sàng; Seed V3 đã được nghiệm thu trên MySQL và ảnh Adoption đã được nghiệm thu Cloudinary thật.

## Giai đoạn 5 - Giao diện nhận nuôi

**Trạng thái: COMPLETED về code và integration test.**

- Đã thêm bốn route giao diện Thymeleaf: `/adoptions`, `/adoptions/{id}`, `/adoptions/my-applications` và `/adoptions/manage`; tất cả dùng chung template `community/adoptions` để không sao chép giao diện.
- Danh sách và chi tiết bài nhận nuôi gọi hai GET public; có tìm kiếm phía trình duyệt, empty state, error state và fallback ký tự tên khi `image_url` đang trống theo Seed V3.
- CUSTOMER có form nộp đơn và trang đơn của mình. Admin/Branch Manager có form tạo DogProfile + AdoptionPost, cập nhật bài và danh sách đơn để duyệt/từ chối. Mọi ghi dữ liệu đi qua 9 API đã chốt, service tiếp tục là nơi quyết định role, branch scope và rule approve/close.
- Response bài nhận nuôi chứa thêm `dogProfile` đầy đủ. Trang chi tiết hiển thị giống, kích thước, tuổi, cân nặng, giới tính và tình trạng tiêm chủng; tìm kiếm cũng nhận biết tên giống.
- Form quản trị cập nhật đồng thời hồ sơ chó và bài nhận nuôi qua `PUT /api/adoptions/{id}`; chi nhánh được chọn từ `/api/branches` thay vì nhập ID số. UI hỗ trợ upload/thay/xóa riêng ảnh DogProfile và AdoptionPost.
- Không thêm endpoint CRUD DogProfile ngoài hợp đồng 9 API. Xóa vật lý chỉ được service cho phép với DogProfile chưa có lịch sử; DogProfile đã có bài nhận nuôi bị chặn và bài được đóng bằng trạng thái `CLOSED` để giữ lịch sử.
- Không tạo API mới, không dùng PuppyListing/Product thương mại, không đưa URL ảnh giả hoặc secret vào giao diện. Upload ảnh thật vẫn thuộc Giai đoạn 6.
- `AdoptionViewControllerIntegrationTest` PASS: bốn route UI render template và có đủ control quản trị hồ sơ/ảnh. Bộ test mục tiêu Adoption/Chat PASS `18/18`.

### Phối hợp TV1/TV3

- **TV3 cần xác nhận:** convention key JWT của giao diện đăng nhập. Trong thời gian tích hợp, UI đọc `pawconnect.accessToken`, `accessToken` hoặc `token` trong local/session storage để gọi `/api/auth/me`; khi TV3 chốt key chung, TV2 sẽ chỉ giữ một convention.
- **TV3 đã bàn giao cho Giai đoạn 6:** service/API Cloudinary, metadata `secure URL`/`public ID` và rule phân quyền upload/thay/xóa ảnh đã được TV2 tái sử dụng.
- **TV1 cần xác nhận:** khi giao diện quản trị chung hoàn thiện, link/điều hướng tới `/adoptions/manage` không được thay đổi API hoặc trộn PuppyListing/Product với nhận nuôi.
- **Kiểm thử còn lại ở Giai đoạn 8:** chạy thao tác browser end-to-end bằng login UI trên MySQL chung; phần API, MockMvc, MySQL importer và Cloudinary thật đã được nghiệm thu độc lập.

## Giai đoạn 6 - Hình ảnh và Cloudinary cho Adoption

**Trạng thái: COMPLETED trên H2/mock và MySQL/Cloudinary thật.**

- Giữ nguyên `imageUrl` và `imagePublicId` đã có ở `DogProfile` và `AdoptionPost`; không lưu file ảnh trong database và không sửa Seed V3.
- Thêm `AdoptionMediaService` dùng đúng `CloudinaryMediaService` và `MediaAccessService` có sẵn. Service kiểm tra `ADMIN`/`BRANCH_MANAGER` và branch ownership của bản ghi trước khi upload, thay hoặc xóa ảnh.
- Thêm bốn endpoint: `POST`/`DELETE` `/api/adoptions/dogs/{id}/image` và `POST`/`DELETE` `/api/adoptions/posts/{id}/image`. Endpoint thay ảnh lưu `secureUrl`/`publicId` mới vào DB trước, sau đó xóa asset cũ; nếu ghi DB lỗi thì cố gắng xóa asset mới để tránh orphan.
- UI nhận nuôi có input ảnh hồ sơ chó và ảnh bài đăng khi tạo; có input thay ảnh bài đăng khi cập nhật. Khi URL rỗng hoặc ảnh lỗi, UI vẫn hiển thị fallback ký tự tên hiện có.
- Bỏ credential Cloudinary hard-code khỏi `application.properties`; cấu hình legacy chỉ đọc `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` từ môi trường. Endpoint upload legacy cũng không còn mở cho anonymous và yêu cầu `ADMIN` hoặc `BRANCH_MANAGER`.

### Kết quả kiểm tra

| Lệnh | Kết quả | Ghi chú |
| --- | --- | --- |
| `mvn -Dtest=AdoptionMediaServiceTest,AdoptionControllerIntegrationTest,CloudinaryMediaServiceTest,MediaAccessServiceIntegrationTest test` | PASS | 13 test: upload/thay/xóa, branch scope, customer bị chặn, cleanup khi DB lỗi và validator media. |
| `node --check src/main/resources/static/js/adoption.js` | PASS | Không có lỗi cú pháp JavaScript. |
| Quét cấu hình Cloudinary tracked | PASS | Không còn API key/secret literal trong `src/main/resources`. |
| `git diff --check` | PASS | Không có lỗi whitespace. |

### Bàn giao và giới hạn

- Nghiệm thu thật trên MySQL bằng Manager HCM thuộc branch `BR_HCM_01`: upload, replace và delete PASS cho DogProfile `7` và AdoptionPost `7`; mọi URL là HTTPS, public ID thuộc folder branch phù hợp, hai DELETE trả HTTP 204 và metadata cuối cùng trở về NULL.
- Khi Cloudinary trả lỗi credential, `CloudinaryMediaService` đã được vá để bọc runtime lỗi upload/xóa thành lỗi storage chung, không trả chi tiết provider/API key ra HTTP response.
- Upload thật dùng `CLOUDINARY_ENABLED=true` và ba biến `CLOUDINARY_*` local, không commit. Tài khoản Manager HCM tạm thời chỉ phục vụ nghiệm thu cần được xóa khỏi MySQL khi không còn dùng để test.

## Giai đoạn 7 - Bàn giao Chat sau khi duyệt đơn

**Trạng thái: COMPLETED về tích hợp service.**

- `AdoptionService.approve` gọi `ConversationService.openForAdoption` trong cùng transaction sau khi chuyển đơn sang `APPROVED`, đóng bài và từ chối các đơn `PENDING` khác.
- Conversation dùng đúng Customer được duyệt, User đã tạo AdoptionPost và `adoptionPostId`. Hàm Chat hiện có tự tìm Conversation cùng bộ khóa trước khi tạo nên không nhân đôi.
- Không sửa Entity, Controller, WebSocket hoặc API Chat của TV3; TV2 chỉ sử dụng public service contract đã có.
- Integration test xác nhận approve tạo Conversation đúng Customer/Post; `ConversationServiceIntegrationTest` tiếp tục PASS participant rule và gửi/đọc tin nhắn.
- `mvn test` chạy 41 test: 40 PASS, 1 FAIL tại `BookingServiceConcurrencyTest` của TV1 (kỳ vọng một optimistic-lock conflict nhưng nhận 0). Đây là blocker Giai đoạn 8, không phát sinh từ Adoption/Chat và TV2 không sửa module Booking.

## Progress log

| Ngày | Chức năng | File/Module | API | Test | Trạng thái | Bàn giao |
| ---- | --------- | ----------- | --- | ---- | ---------- | -------- |
| 15/09/2026 | Giai đoạn 0: rà soát contract, Seed V3 và backend | Tài liệu TV2, Seed V3, entity/security/test hiện có | Chưa tạo API Adoption | Seed V3 verify/self-test PASS; baseline Maven đã được khắc phục | ✅ | TV3 đã bàn giao User/Role/JWT/Branch; TV1 cần chốt MySQL chung và Branch import cho 2B |
| 15/09/2026 | Giai đoạn 1: domain model nhận nuôi | Entity/enum/repository Adoption, `AdoptionPersistenceTest`, `pom.xml` | Chưa tạo API; chuẩn bị service/API | `mvn test` PASS 11/11; FK, enum, timestamp và unique seed key PASS | ✅ | TV3 giữ mapping User/Branch/role; TV1 giữ Branch code ổn định và không tự đổi lựa chọn breed text |
| 15/09/2026 | Giai đoạn 2A: import Seed V3 trên H2 local | `AdoptionSeedImportService`, summary, integration test | Import gọi tường minh, không chạy khi startup | PASS: tạo 30/30/36, chạy lại idempotent, Seed lỗi rollback | ✅ | Chờ TV1 bàn giao MySQL/Branch reference và TV3 bàn giao Role/User cùng database cho 2B |
| 27/09/2026 | Giai đoạn 2B: nghiệm thu import MySQL chung | `AdoptionSeedInitializer`, MySQL acceptance test, profile MySQL | Không có API mới; seed chỉ chạy qua runtime flag | Thực hiện: chạy bootstrap theo thứ tự reference/commerce -> users -> adoption bằng biến môi trường local; kiểm tra SQL count 3 Branch, 4 Category, 3 ServiceType, 6 User, 30 DogProfile, 30 AdoptionPost, 36 AdoptionApplication; chạy lại Adoption importer và count không tăng.<br>PASS: Post -> DogProfile, Application -> Post/User đều không mồ côi; AVAILABLE=24/CLOSED=6, PENDING=24/APPROVED=6/REJECTED=6; không APPROVED trên AVAILABLE, không PENDING trên CLOSED, không post có hơn một APPROVED. | ✅ | TV1/TV3 giữ MySQL profile, Branch/User mapping và không ghi credential vào Git |
| 15/09/2026 | Giai đoạn 3: service và rule nghiệp vụ nhận nuôi | DTO/mapper, `AdoptionService`, repository query, integration test | Sẵn sàng cho 9 endpoint Giai đoạn 4 | PASS: role, branch ownership, PENDING trùng, approve/close/reject và manage scope | ✅ | TV3 giữ JWT principal email và role/branch authority; TV1 giữ mapping Branch khi tích hợp MySQL |
| 15/09/2026 | Giai đoạn 4: REST API nhận nuôi | `AdoptionController`, DTO create/validation, MockMvc test, SecurityConfig matcher public | Đủ 9 endpoint theo `TV2.md`; không có endpoint DogProfile riêng | PASS: 7 MockMvc test, 401/403/404/409/400, public GET, nested DogProfile, branch ownership, approve/reject | ✅ | TV3 giữ public GET/security và principal email; TV1 giữ commerce tách PuppyListing khỏi Product theo Seed V3 |
| 15/09/2026 | Giai đoạn 5: giao diện nhận nuôi | `AdoptionViewController`, template, CSS/JS, view integration test | UI list/detail/apply/my applications/manage; sửa DogProfile cùng post và quản lý ảnh qua API hiện có | PASS: 4 route view, control hồ sơ/ảnh và JavaScript syntax; bộ test mục tiêu Adoption/Chat 18/18 | ✅ | TV3 giữ key lưu JWT; TV1 giữ điều hướng/commerce tách biệt; browser E2E trên MySQL thuộc Giai đoạn 8 |
| 27/09/2026 | Giai đoạn 6: ảnh DogProfile và AdoptionPost | `AdoptionMediaService`, controller, UI Adoption và Cloudinary media service | POST/DELETE `/api/adoptions/dogs/{id}/image`; POST/DELETE `/api/adoptions/posts/{id}/image` | Thực hiện: tạo account test cục bộ qua Register API, gán tạm BRANCH_MANAGER cho branch HCM trong MySQL, đăng nhập lại để lấy JWT có role/branch mới; thử file ảnh không đọc được và nhận 400; dùng ảnh JPEG local hợp lệ để upload.<br>PASS: 14 test mục tiêu; upload/replace/delete thật cho DogProfile 7 và AdoptionPost 7; public ID thay đổi sau replace, asset cũ được xử lý, hai DELETE trả 204 và SQL xác nhận `image_url`/`image_public_id` cuối cùng là NULL. Lỗi provider được bọc thành thông báo storage chung, không trả chi tiết credential. | ✅ | TV1/TV3 tiếp tục giữ Cloudinary credential ở biến môi trường; TV2 xóa account Manager HCM tạm khi không còn cần nghiệm thu |
| 27/09/2026 | Hoàn thiện UI DogProfile và Giai đoạn 7 Chat handoff | DTO/mapper/service Adoption, template/CSS/JS, test Adoption/Chat | Giữ 9 API cốt lõi; response có nested DogProfile; `PUT /api/adoptions/{id}` cập nhật hồ sơ; approve mở Conversation | JavaScript syntax PASS; 18/18 test mục tiêu PASS. Full Maven: 40/41 PASS, chỉ còn `BookingServiceConcurrencyTest` của TV1 FAIL ngoài phạm vi TV2. | ✅ | TV3 giữ ổn định `ConversationService.openForAdoption` và participant rule; TV1 xử lý Booking concurrency để Giai đoạn 8 full build PASS |
