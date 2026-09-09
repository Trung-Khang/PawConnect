# Runbook Data Pipeline PawConnect

## 1. Tổng quan kiến trúc

Luồng dữ liệu là: raw -> candidate/review -> curated -> deterministic build ->
seed release -> DB importer.

| Lớp | Mục đích | Git |
| --- | --- | --- |
| Raw | Quan sát công khai có kiểm soát và evidence ban đầu. | Local, ignored; không import DB. |
| Candidate/review | Normalize, deduplicate, quarantine, price band để reviewer xét. | Không là bootstrap hay import DB. |
| Curated | Reference/catalog/rule đã được duyệt, có stable code và provenance. | Track. |
| Seed release | Snapshot bootstrap bất biến từ curated/rule. | Track; V3 là release hiện hành. |
| Database | Dữ liệu vận hành sau import. | Không phải CSV pipeline. |

Candidate và workspace cũ đã bị loại khỏi Git. Công cụ tạo output tạm phải dùng
thư mục ignored hoặc thư mục tạm. Raw/candidate/generated không được import DB.

## 2. Lịch sử có bằng chứng

Lịch sử dưới đây lấy từ git log và các commit pipeline: 3fb6bec, 5aadedf,
ba1f48a, db338d7, 4bae80c, 4fa0b7e, 0cdd32f và 855b655.

### Giai đoạn 1A — Data Foundation

Commit 3fb6bec tạo curated foundation: data contract, reference data, catalog,
User/Adoption, stable code và seed_key.

### Giai đoạn 1B — Pipeline Core

Commit ba1f48a bổ sung commercial cleaning, price band, deduplication và báo cáo
quarantine. Candidate là đầu vào review, không phải dữ liệu nghiệp vụ.

### Giai đoạn 2A — Controlled Crawling và Evidence

Commit db338d7 bổ sung controlled crawl pipeline. Cấu hình Wikipedia dùng
allowlist; crawler commercial dùng source đã cấu hình. Raw chỉ lưu local, không
lưu PII/contact/ảnh. Permission, robots, Terms, rate limit và giới hạn request
được source/config kiểm soát.

### Giai đoạn 2B — Generation và release thử nghiệm

Commit 5aadedf tạo adoption generator; 4bae80c tạo product workspace; 4fa0b7e
tạo versioned release và catalog. Các snapshot thử nghiệm từng trộn Puppy/Product,
có text nhãn nội bộ và trùng snapshot; chúng không còn là bootstrap cuối. Hai
generator thử nghiệm đã obsolete và được loại ở commit 855b655.

### Giai đoạn 3 — Final Seed V3

Commit 0cdd32f thêm Samoyed, đưa catalog lên 15 breed, chốt allowlist commerce
14 breed, tách PuppyListing khỏi Product, để Product chỉ FOOD/ACCESSORY, để
image_url rỗng, dùng credential runtime, tạo manifest/provenance/validation và
bootstrap đầy đủ cho ba thành viên. Commit 855b655 dọn release/workspace cũ.

## 3. Nguồn và provenance

| Nguồn | Mục đích | Vào Seed | Giới hạn |
| --- | --- | --- | --- |
| Wikipedia English allowlist trong config/sources.json | Breed raw reference cho Poodle, Chihuahua, Pug. | Chỉ evidence sau review. | Chỉ đường dẫn allowlist, không media; giữ attribution. |
| American Kennel Club trong breed_references.csv | Adult metadata Pomeranian/Samoyed. | Qua curated provenance/range đã duyệt. | Không sao chép mô tả nguồn làm UI. |
| Vietnam Kennel Association và Vietnam-Russia Tropical Center | Adult/maturity H'Mông cộc đuôi. | Qua curated provenance/range đã duyệt. | Dùng evidence, không hiển thị nội dung nguồn trực tiếp. |
| Chợ Tốt cấu hình commercial | Quan sát puppy và khoảng giá khi được phép. | Không trực tiếp; price rule curated sau review. | Không lấy contact/chat/ảnh; dừng khi không được phép. |
| Pet Mart cấu hình commercial | Quan sát catalog sản phẩm công khai. | Không trực tiếp. | Không lấy ảnh/mô tả dài/review. |

Ngày retrieved, URL và evidence status nằm trong breed_references.csv hoặc raw
batch khi có. Nếu không có observation hợp lệ, commerce dùng rule curated đã
review để tạo dữ liệu có kiểm soát; không được gọi đó là dữ liệu crawl thật.

## 4. Thống kê qua các mốc

| Mốc | Breed | DogProfile | AdoptionPost | AdoptionApplication | PuppyListing | Product | Trạng thái |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Curated foundation | 12 | 5 | 3 | 4 | Không đủ bằng chứng | Không đủ bằng chứng | Curated ban đầu. |
| Workspace thử nghiệm | 14 | 28 | 8 | 12 | Không đủ bằng chứng | 27 | Lịch sử, không bootstrap. |
| Seed V1 | 12 | 24 | 24 | 32 | Không đủ bằng chứng | 27 | Lịch sử, đã dọn. |
| Seed V2 | 14 | 28 | 28 | 36 | Không đủ bằng chứng | 27 | Lịch sử, đã dọn. |
| Seed V3 | 15 | 30 | 30 | 36 | 14 | 18 | Bootstrap hiện hành. |

V3 còn có Role 3, Branch 3, Category 4, ServiceType 3, BreedReference 16 và
User 6 theo manifest.json.

## 5. Quy trình tạo Final Seed V3

1. Chuẩn hóa contract và stable code.
2. Thu thập evidence; crawler là tự động hóa có kiểm soát.
3. Lưu raw local; cleaner normalize/deduplicate/quarantine tự động.
4. Reviewer đánh giá evidence, price rule và provenance thủ công.
5. Chuyển rule đã duyệt vào curated.
6. Builder deterministic tạo V3 từ curated/rule.
7. Validator kiểm tra FK, enum, count, checksum, UI text và image URL.
8. Tạo manifest/provenance/report, verify/self-test, đóng release và handoff.

## 6. Kết quả nghiệm thu V3

- Catalog 15 breed; PuppyListing đúng allowlist 14 breed.
- BREED_PHU_QUOC không có PuppyListing; Husky map BREED_HUSKY_SIBERIAN.
- Product chỉ FOOD/ACCESSORY.
- Recursive banned-word, placeholder image, security, FK, enum, count, header,
  checksum và deterministic verify đều PASS.
- image_url rỗng, chờ Cloudinary; V3 độc lập với raw, candidate, workspace và
  release lịch sử.

## 7. Hướng dẫn bảo trì

### Bước 1: Chuẩn bị

Làm việc từ project root với Python đang được hệ thống nhận diện. Build/verify
local không cần credential. Crawler network chỉ chạy khi source/permission đã
được review.

### Bước 2: Breed crawler

    python data-pipeline/src/crawlers/crawl_breeds.py --dry-run
    python data-pipeline/src/crawlers/crawl_breeds.py --self-test
    python data-pipeline/src/crawlers/crawl_breeds.py --run-id YYYYMMDDTHHMMSSZ

Output là raw batch; review manifest/request log/evidence trước khi cập nhật
curated. Không đưa raw trực tiếp vào Seed.

### Bước 3: Commercial crawler

    python data-pipeline/src/crawlers/crawl_commercial.py --source "Chợ Tốt" --max-pages 2 --allow-network
    python data-pipeline/src/crawlers/crawl_commercial.py --source "Chợ Tốt (cho-giong)" --max-pages 2 --allow-network
    python data-pipeline/src/crawlers/crawl_commercial.py --source "Pet Mart" --max-pages 2 --allow-network

Chỉ dùng source allowlist trong config; giữ rate limit, không bypass chặn truy
cập. Nếu nguồn chặn hoặc không cho phép, dừng và không đưa dữ liệu đó vào curated.

### Bước 4: Cleaner

    python data-pipeline/src/clean_commercial_observations.py --dry-run
    python data-pipeline/src/clean_commercial_observations.py --self-test
    python data-pipeline/src/clean_commercial_observations.py

Cleaner normalize, deduplicate và quarantine; xem report lỗi/quarantine. Output
không tự động trở thành curated.

### Bước 5: Review và curated

Thêm breed bằng stable breed_code, adult size/weight/maturity vào breeds.csv và
provenance vào breed_references.csv. Cập nhật puppy_price_rules.csv với đơn vị
VND, tuổi và min/default/max đã review. Không thêm breed vào allowlist commerce
V3 nếu chưa được nhóm duyệt; allowlist hiện tại là 14 breed.

### Bước 6: Dữ liệu adoption và commerce

Hai generator cũ đã bị xóa. Builder V3 hiện tạo 30 DogProfile/30 Post/36
Application, 14 PuppyListing và 18 Product theo rule cố định trong
build_seed_v3.py. Muốn tăng số lượng hoặc thay rule phải phát triển/review
builder release mới và curated input; không sửa trực tiếp seed/v3. Generator độc
lập cho release sau là đề xuất chưa triển khai.

### Bước 7: Release kế tiếp

Không ghi đè V3. CLI hiện chỉ hỗ trợ release V3; chưa có lệnh V4. Cần phát triển
builder/release folder mới, cập nhật release ID, manifest, provenance và
validation trước khi build release kế tiếp.

### Bước 8: Verify

    python -m py_compile data-pipeline/src/build_seed_release.py data-pipeline/src/build_seed_v3.py
    python data-pipeline/src/build_seed_release.py --verify v3
    python data-pipeline/src/build_seed_release.py --self-test-v3
    git diff --check
    git status --short

Kiểm tra thêm banned word, placeholder URL, secret, duplicate stable key, FK,
enum, count, checksum và deterministic output trước khi bàn giao.

### Bước 9: Bàn giao team

Cập nhật data_contract.md, data_handoff.md, manifest/provenance/report; TV1,
TV2, TV3 xác nhận mapping. Chỉ commit release mới khi toàn bộ validation PASS.

## 8. Không được làm

- Không chạy crawler mạng trong lượt viết tài liệu.
- Không sửa hoặc tái tạo Seed V3.
- Không sửa curated CSV, phục hồi release/workspace lịch sử hoặc tạo generator giả.
- Không đưa secret hoặc dữ liệu cá nhân thật vào tài liệu.
- Không commit/push trong lượt viết tài liệu.
