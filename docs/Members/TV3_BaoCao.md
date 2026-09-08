# PawConnect - TV3 Báo cáo nhận bàn giao dữ liệu

> Đây là file hướng dẫn nhận bàn giao và mẫu để TV3 cập nhật tiến độ. Chưa ghi nhận JWT, Security hoặc Chat đã hoàn thành nếu chưa có code và test.

## 1. Phạm vi TV3

TV3 phụ trách `User`, `Role`, JWT, Spring Security, Conversation, ChatMessage, Admin dashboard và integration toàn hệ thống.

## 2. Dữ liệu TV2/Data Engineer đã chuẩn bị cho TV3

| File | Dữ liệu | Người sở hữu nghiệp vụ | Người sử dụng | Dùng hiện tại | Bàn giao gần tới |
| --- | --- | --- | --- | --- | --- |
| `data-pipeline/data/curated/reference/roles.csv` | Role | TV3 | Cả nhóm tham chiếu phân quyền | Mapping role bằng `role_code` | TV3 xác nhận role name khớp Spring Security |
| `data-pipeline/data/curated/reference/branches.csv` | Branch | TV1 | TV2/TV3 dùng cho liên kết chi nhánh | `BRANCH_MANAGER` fixture gắn branch | TV3 xác nhận User-Branch mapping |
| `data-pipeline/data/curated/fixtures/users.csv` | User fixture | TV3 sở hữu cách import và credential | TV1/TV2 dùng khóa tham chiếu khi test | Admin, 3 manager, 2 customer demo | TV3 tạo hash password demo an toàn |
| `data-pipeline/data/curated/adoption/dog_profiles.csv` | DogProfile | TV2 | TV2/TV3 | Suy ra branch cho AdoptionPost | TV3 xác nhận `DogProfile.branch_id` hoặc mapping |
| `data-pipeline/data/curated/adoption/adoption_posts.csv` | AdoptionPost | TV2 | TV3 dùng để tích hợp Conversation/Chat | 2 AVAILABLE, 1 CLOSED | TV3 map Conversation tới post/customer/manager |
| `data-pipeline/data/curated/adoption/adoption_applications.csv` | AdoptionApplication | TV2 | TV3 dùng cho authorization và Chat | Đủ PENDING, APPROVED, REJECTED | TV3 xác nhận rule approve/chat |
| `data-pipeline/reports/manual_curated_validation.md` | Báo cáo validation | Cả nhóm | Cả nhóm kiểm tra chất lượng bàn giao | Manual validation PASS | Dùng làm baseline trước integration |

## 3. TV3 cần kiểm tra và xác nhận

- Role name khớp Spring Security: `CUSTOMER`, `BRANCH_MANAGER`, `ADMIN`.
- `BRANCH_MANAGER` có liên kết Branch rõ ràng.
- Credential demo được hash/import an toàn.
- User fixture chỉ dùng `dev`, `test`, `demo`.
- Conversation tham chiếu đúng AdoptionPost, customer và manager/admin.
- Không lưu `TV3_SEED_REQUIRED` như password thật.
- Không tự sửa Adoption enum hoặc stable code mà không báo TV2.

## 4. Hướng dẫn xử lý User fixture và credential

`users.csv` chỉ chứa `password_placeholder=TV3_SEED_REQUIRED`. TV3 cần tự quyết định cách import password demo an toàn, ví dụ sinh hash trong seed nội bộ hoặc tạo account qua service auth. Không đưa password thật vào CSV.

## 5. Hướng dẫn mapping User-Branch

User role `BRANCH_MANAGER` bắt buộc có `branch_code`. `CUSTOMER` và `ADMIN` để trống `branch_code`. Khi import DB, TV3 cần map `branch_code` sang Branch ID thật và dùng mapping này để kiểm tra quyền quản lý dữ liệu theo chi nhánh.

## 6. Dữ liệu Adoption dùng cho Conversation/Chat

TV3 có thể dùng `adoption_posts.csv` và `adoption_applications.csv` để chuẩn bị Conversation:

- Customer lấy từ `applicant_user_key`.
- Manager/admin lấy từ `created_by_user_key` của AdoptionPost hoặc rule TV3 xác nhận.
- AdoptionPost lấy từ `adoption_post_seed_key`.
- Post `CLOSED` với application `APPROVED` không còn application `PENDING`.

## 7. Tiêu chí báo cáo cho mỗi chức năng

Mỗi lần làm chức năng, TV3 cần ghi: file đã sửa, API/chức năng, dữ liệu nhận, security/test, kết quả, đầu ra bàn giao, blocker hoặc contract conflict. Chỉ đánh dấu `COMPLETED` khi có code và test.

## 8. Mẫu progress log

| Ngày | Giai đoạn | Chức năng/API | File đã sửa | Dữ liệu nhận | Security/Test | Kết quả | Bàn giao | Blocker |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  | NOT STARTED |  |  |

Trạng thái dùng chung: `NOT STARTED`, `IN PROGRESS`, `BLOCKED`, `READY FOR REVIEW`, `COMPLETED`.

---

## 9. Báo cáo rà soát bàn giao dữ liệu từ TV2 cho TV3 - 2026-09-08

### 9.1. Phạm vi đã đọc

TV3 đã rà soát các nguồn sau:

- `data-pipeline/` và toàn bộ CSV curated hiện có.
- `docs/Project/data_contract.md`. Lưu ý: đường dẫn người bàn giao ghi `docs/Project/data-contract.md`, nhưng trong repository hiện có file thật là `docs/Project/data_contract.md`.
- `docs/Members/TV1_BaoCao.md`, `docs/Members/TV2_BaoCao.md`, `docs/Members/TV3_BaoCao.md`.
- `General.pdf`, 19 trang, là tài liệu tổng quan dự án DogConnect/PawConnect.

### 9.2. Kết quả rà soát tổng quan

| Hạng mục | Kết quả | Ghi chú |
| --- | --- | --- |
| Data contract v0.1 | ACCEPTED FOR DEV/TEST/DEMO | Đủ quy ước UTF-8, `snake_case`, `seed_key`, stable code, enum adoption và rule User-Branch. |
| Reference seed | ACCEPTED FOR DEV/TEST/DEMO | Role 3, Branch 3, Category 4, ServiceType 3. |
| Breed catalog | ACCEPTED FOR DEV/TEST/DEMO | 12 giống chó, đủ SMALL/MEDIUM/LARGE, tuổi và cân nặng có rule. |
| Adoption fixture | ACCEPTED FOR DEV/TEST/DEMO | DogProfile 5, User fixture 6, AdoptionPost 3, AdoptionApplication 4. |
| Manual validation | PASS | Report hiện có xác nhận role, branch, manager, adoption rule và duplicate seed. |
| Production import | NOT APPROVED | Chưa có migration/import DB được review. |
| Crawler/Cleaner/Generator | NOT STARTED | Chưa có crawler, cleaner, normalizer, Faker generator hoặc SQL. |
| Backend/API từ data pipeline | NOT VERIFIED | Chưa dùng báo cáo này để xác nhận code backend đã hoàn thành. |

### 9.3. Kiểm tra dữ liệu TV3 đã chạy

| Check | Kết quả |
| --- | --- |
| User `role_code` tồn tại trong `roles.csv` | PASS |
| Manager `branch_code` tồn tại trong `branches.csv` | PASS |
| AdoptionPost tham chiếu DogProfile và manager hợp lệ | PASS |
| Branch manager khớp `branch_code` của DogProfile | PASS |
| AdoptionApplication tham chiếu AdoptionPost và CUSTOMER hợp lệ | PASS |
| Rule APPROVED: tối đa một application được duyệt, post có APPROVED là CLOSED, không còn PENDING cùng post | PASS |
| Số bản ghi | roles=3, branches=3, users=6, dog_profiles=5, adoption_posts=3, adoption_applications=4 |
| Secret/password thật | PASS có điều kiện: chỉ thấy placeholder `TV3_SEED_REQUIRED`, đúng contract; không được import làm password thật. |

### 9.4. Rà soát theo từng thành viên

| Thành viên | Nội dung nhận từ TV2/Data Engineer | Kết luận TV3 |
| --- | --- | --- |
| TV1 | Branch, Category, ServiceType, Breed catalog, Product contract guidance | Cần TV1 xác nhận stable code và mapping Product. Dữ liệu seed dùng được cho dev/test/demo, chưa import production. |
| TV2 | Data contract, reference seed, breed catalog, DogProfile, AdoptionPost, AdoptionApplication, validation report | Giai đoạn 1A Data Foundation có thể xem là COMPLETED ở mức dữ liệu thủ công. Các giai đoạn crawler/cleaner/generator/DB integration vẫn NOT STARTED. |
| TV3 | Role, User fixture, User-Branch mapping, adoption data cho Conversation/Chat | TV3 cần xác nhận entity Java/security mapping trước khi import: Role name, branch của manager, credential hash, Conversation link. |

### 9.5. Điểm cần TV3 xác nhận trước khi nhận chính thức

- `Role.name` trong Java/Spring Security phải khớp `CUSTOMER`, `BRANCH_MANAGER`, `ADMIN` hoặc có mapping rõ từ `ROLE_CUSTOMER`, `ROLE_BRANCH_MANAGER`, `ROLE_ADMIN`.
- Entity `User` hoặc mapping phụ phải biểu diễn được branch của `BRANCH_MANAGER`; ba manager hiện map lần lượt `BR_HCM_01`, `BR_HN_01`, `BR_DN_01`.
- `TV3_SEED_REQUIRED` chỉ là placeholder; TV3 phải tạo hash/password demo bằng quy trình an toàn trước khi seed DB.
- `DogProfile.branch_id` đang là đề xuất của data contract; TV3 cần xác nhận có thêm vào schema hay mapping qua bảng/quan hệ khác.
- Conversation/Chat cần chốt rule lấy admin/manager từ `created_by_user_key` của AdoptionPost hay từ một admin tổng.
- Enum Java cho adoption phải khớp: AdoptionPost `AVAILABLE`, `CLOSED`; AdoptionApplication `PENDING`, `APPROVED`, `REJECTED`.

### 9.6. Nhận xét từ General.pdf

- General.pdf xác nhận mô hình hệ thống gồm thương mại, dịch vụ chăm sóc, nhận nuôi và chat real-time với JWT/WebSocket.
- PDF ủng hộ shared database phân tách chi nhánh bằng `branch_id`, phù hợp đề xuất `DogProfile.branch_id`.
- PDF ghi "13 thực thể" nhưng danh sách thực thể thực tế gồm 14: User, Role, Branch, Category, Product, Order, OrderItem, ServiceType, ServiceBooking, DogProfile, AdoptionPost, AdoptionApplication, Conversation, ChatMessage. Cần thống nhất lại trong tài liệu tổng quan khi có dịp.
- PDF vẫn dùng tên DogConnect ở nhiều chỗ; các tài liệu pipeline hiện thống nhất tên dự án là PawConnect. Đây là khác biệt tên tài liệu, chưa ảnh hưởng dữ liệu.

### 9.7. Kết luận bàn giao TV3

TV3 chấp nhận bộ dữ liệu của TV2 ở trạng thái `READY FOR REVIEW` cho mục đích dev/test/demo. Chưa chấp nhận import DB hoặc production vì còn thiếu schema/migration/import review và xác nhận mapping Java.

Trạng thái đề xuất:

| Giai đoạn | Trạng thái TV3 xác nhận |
| --- | --- |
| Giai đoạn 1A - Data Foundation | COMPLETED ở mức curated Markdown/CSV thủ công |
| Giai đoạn 1B - Data Pipeline Core | NOT STARTED |
| Giai đoạn 2A - Controlled Crawler | NOT STARTED |
| Giai đoạn 2B - Synthetic Generator | NOT STARTED |
| Giai đoạn 3 - Database Integration | NOT STARTED |
| Giai đoạn 4 - Module Development | NOT VERIFIED trong báo cáo này |
| Giai đoạn 5 - Integration, Test và Demo | NOT STARTED |
