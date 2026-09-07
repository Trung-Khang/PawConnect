# PawConnect - Hướng dẫn xây dựng Data Pipeline Hybrid

> **Phạm vi:** Crawler có kiểm soát + dữ liệu giả có quy luật + kiểm định + xuất dữ liệu cho Spring Boot.
>
> **Người phụ trách chính:** TV2 kiêm Data Engineer.
>
> **Tên file được giữ nguyên để không làm hỏng liên kết tài liệu cũ; tên dự án thống nhất là PawConnect.**

## 1. Kết luận và thứ tự ưu tiên

TV2 nên dựng **data foundation cho cả nhóm trong tuần 1-2**, trước khi phát triển đầy đủ phân hệ nhận nuôi. Tuy nhiên, không bắt đầu bằng việc crawl hàng loạt. Thứ tự đúng là:

```text
Chốt data contract -> Seed tham chiếu tối thiểu -> Pipeline chạy lặp lại
-> Bộ dữ liệu demo nhỏ -> TV1/TV3 xác nhận -> Mở rộng dữ liệu -> Tích hợp DB
```

Pipeline phải giúp TV1, TV2 và TV3 có dữ liệu để phát triển song song, nhưng không được tự ý thay đổi entity, enum, endpoint hoặc quyền nghiệp vụ của module khác.

Trong trạng thái repository hiện tại, pipeline có thể bắt đầu độc lập bằng contract và CSV/JSON, nhưng **chưa xuất migration SQL chính thức** cho đến khi nhóm có project Spring Boot chạy được, đã chọn DB và đã tạo entity/migration nền.

### 1.1. Phạm vi dữ liệu nên cung cấp

| Nhóm dữ liệu | Ví dụ | Quy mô ban đầu | Người duyệt |
| --- | --- | ---: | --- |
| Tham chiếu ổn định | Role, Branch, Category, ServiceType | 3-10 bản ghi/bảng | TV1 + TV3 |
| Danh mục giống chó | breed, size, weight range, age range | 15-20 giống | TV2 |
| Catalog thương mại | Product | 30-50 sản phẩm/chó giống | TV1 |
| Nhận nuôi | DogProfile, AdoptionPost | 15-25 hồ sơ, 10-20 tin | TV2 |
| Fixture tích hợp tối thiểu | User giả, Order, Application, Conversation | 2-5 luồng mẫu | Chủ module tương ứng |

Không sinh hàng nghìn bản ghi ở giai đoạn đầu. Với đồ án môn học, dữ liệu ít nhưng bao phủ đủ trạng thái và tình huống biên có giá trị hơn dữ liệu lớn nhưng trùng lặp.

### 1.2. Dữ liệu không được crawl hoặc phát tán

- Email, số điện thoại, địa chỉ, tên người đăng hoặc nội dung có thể nhận diện cá nhân.
- Ảnh/nội dung không rõ quyền sử dụng hoặc điều khoản nguồn không cho phép sao chép.
- Cookie, token, API key, thông tin đăng nhập, dữ liệu sau màn hình đăng nhập.
- Đơn hàng, hồ sơ người dùng, tin nhắn thật hoặc đơn nhận nuôi thật.

Chỉ dùng dữ liệu công khai, dữ liệu được cấp phép hoặc dữ liệu do nhóm tự tạo. Không mặc định rằng nội dung công khai đồng nghĩa với được phép sao chép và tải lại.

---

## 2. Việc phải chốt trước khi viết crawler

Tạo một cuộc review ngắn với TV1 và TV3, ghi kết quả vào pull request hoặc issue. Pipeline chỉ chuyển sang xuất SQL sau khi các mục sau đã được xác nhận:

1. Hệ quản trị CSDL chính thức: MySQL, PostgreSQL hay SQL Server.
2. Tên bảng/cột thực tế, kiểu dữ liệu, độ dài chuỗi, `nullable`, khóa ngoại và unique constraint.
3. Giá trị enum chính xác, có phân biệt hoa/thường hay không.
4. Đơn vị của `DogProfile.age` là tháng hay năm; khuyến nghị thống nhất là **tháng** và đổi tên thành `ageMonths` nếu nhóm còn có thể sửa schema.
5. Cách xác định chi nhánh của dữ liệu nhận nuôi.

### 2.1. Điểm chưa nhất quán cần nhóm quyết định

Tài liệu tổng quan và `Workflow.md` liệt kê **14 entity**, không phải 13. Pipeline không tạo dữ liệu cho cả 14 bảng; nó chỉ tạo các bảng cần thiết cho phát triển và demo.

`DogProfile` và `AdoptionPost` hiện không có `branchId`, trong khi `BRANCH_MANAGER` cần quản lý dữ liệu theo chi nhánh. Nhóm phải chọn một trong hai phương án trước khi sinh dữ liệu:

- **Khuyến nghị:** thêm `branchId` vào `DogProfile` hoặc `AdoptionPost` và lọc theo chi nhánh ở repository/service.
- Giữ schema hiện tại: suy ra chi nhánh qua tài khoản tạo tin. Khi đó `User` phải có liên kết chi nhánh rõ ràng và rule này phải được TV3 xác nhận.

Không để script tự gán `branchId` nếu entity nhận nuôi chưa có trường hoặc quan hệ tương ứng.

### 2.2. Không phụ thuộc trực tiếp vào ID tự tăng

Trong file trung gian, dùng mã ổn định như `BR_HCM_01`, `CAT_DOG`, `SERVICE_SPA`, không hard-code `branchId = 1` hoặc `categoryId = 1`. Khi import, mã ổn định được ánh xạ sang ID thật của DB.

Nếu schema chưa có cột `code`, importer phải tra cứu bằng một khóa duy nhất đã được nhóm thống nhất. Không tra cứu theo tên nếu tên có thể thay đổi hoặc không có unique constraint.

---

## 3. Kiến trúc pipeline đề xuất

```text
Nguồn được duyệt
      |
      v
Extract thô, có provenance
      |
      v
Normalize + Deduplicate
      |
      v
Generate có seed cố định
      |
      v
Validate + Quarantine lỗi
      |
      v
Curated CSV/JSON -> Flyway seed dev/demo -> Spring Boot
```

Nguyên tắc:

- **Raw là bất biến:** không sửa file raw bằng tay; chạy mới thì tạo batch mới.
- **Deterministic:** cùng input + config + `random_seed` phải tạo cùng output.
- **Idempotent:** chạy import lần hai không tạo bản ghi trùng.
- **Fail fast:** dữ liệu sai khóa ngoại, enum hoặc rule nghiệp vụ phải làm pipeline thất bại.
- **Traceable:** mọi nội dung lấy từ nguồn ngoài phải truy được nguồn, thời điểm và quyền sử dụng.
- **Profile-safe:** dữ liệu demo chỉ được nạp trong `dev`, `test` hoặc `demo`, không tự nạp ở production.

### 3.1. Cấu trúc thư mục tối thiểu

```text
data-pipeline/
├── README.md
├── requirements.txt
├── .env.example
├── config/
│   ├── sources.yaml
│   ├── generation.yaml
│   └── contracts/
├── src/
│   ├── crawl.py
│   ├── normalize.py
│   ├── generate.py
│   ├── validate.py
│   └── export.py
├── data/
│   ├── raw/                 # Không commit dữ liệu crawl lớn
│   ├── quarantine/          # Bản ghi lỗi + lý do
│   └── curated/             # Dataset nhỏ đã được duyệt để chia sẻ
├── tests/
├── output/                  # File sinh lại được; mặc định không commit
└── reports/
    └── validation-summary.json

src/main/resources/db/migration/
├── V1__schema.sql
└── V2__seed_demo_data.sql   # Chỉ tạo sau khi DB/schema đã chốt
```

Không bắt buộc dùng Jupyter Notebook. Notebook phù hợp để khám phá dữ liệu, nhưng logic chính phải nằm trong file `.py` có thể chạy từ terminal và test tự động; notebook chỉ gọi lại các hàm đó.

---

## 4. Data contract và định dạng trung gian

CSV/JSON là nguồn dữ liệu chuẩn của pipeline. Tên cột dùng `snake_case`; UTF-8; ngày giờ dùng ISO 8601; số tiền dùng số nguyên VND; boolean dùng `true/false`.

### 4.1. Catalog nguồn (`breed_catalog.csv`)

```text
source_key,breed_name,size,min_weight_kg,max_weight_kg,
min_age_months,max_age_months,description_template,image_url,
source_url,source_name,license,author,fetched_at,content_sha256
```

Các cột provenance chỉ tồn tại trong pipeline. Không đẩy `source_url`, `author` hoặc `content_sha256` vào entity nếu schema ứng dụng không cần chúng.

### 4.2. Product đầu ra

```text
seed_key,name,description,price,stock,image_url,suitable_size,
is_breeding_dog,category_code,branch_code
```

Rule tối thiểu:

- `price > 0`, `stock >= 0`, `name` và `image_url` không rỗng.
- `suitable_size` phải đúng enum mà TV1 chốt, ví dụ `SMALL|MEDIUM|LARGE`.
- Chó giống độc bản nên có `stock` bằng `0` hoặc `1`; không random `1-5` như hàng hóa thường.
- Product không phải chó giống không được gán cân nặng, tuổi hoặc giống chó giả.
- `category_code` và `branch_code` phải tồn tại trong seed tham chiếu.

### 4.3. DogProfile đầu ra

```text
seed_key,name,breed,size,age_months,weight_kg,gender,
vaccination_status,image_url,description
```

Rule tối thiểu:

- Tuổi, cân nặng phải nằm trong khoảng cấu hình của giống và size.
- `gender`, `size`, `vaccination_status` phải đúng enum/format trong Java.
- Không dùng cùng một ảnh đại diện cho nhiều hồ sơ chó cá thể.
- Một chú chó không đồng thời xuất hiện trong `Product` và `DogProfile`.

### 4.4. AdoptionPost đầu ra

```text
seed_key,dog_profile_seed_key,created_by_user_key,title,
description,health_note,status,created_at
```

Rule tối thiểu:

- `dog_profile_seed_key` và `created_by_user_key` phải tồn tại.
- Mỗi DogProfile chỉ có tối đa một tin `AVAILABLE` tại cùng thời điểm.
- `status` phải đúng enum đã chốt, không tự tạo thêm trạng thái trong script.
- `created_at` hợp lệ và không nằm trong tương lai.

### 4.5. AdoptionApplication và fixture liên module

Chỉ sinh sau khi TV3 cung cấp tài khoản CUSTOMER giả và TV2 đã có AdoptionPost hợp lệ:

```text
seed_key,adoption_post_seed_key,applicant_user_key,message,status
```

Rule tối thiểu:

- `applicant_user_key` phải tham chiếu User giả có role `CUSTOMER`.
- Không dùng nội dung đơn thật hoặc thông tin liên hệ thật.
- Một khách không có nhiều đơn đang hoạt động cho cùng một tin.
- Nếu một đơn `APPROVED`, tin tương ứng phải đóng và không có đơn thứ hai được duyệt.
- Fixture Order/Conversation/ChatMessage chỉ được tạo khi TV1/TV3 yêu cầu và phải do chủ module duyệt; pipeline không tự suy diễn quan hệ bảo mật.

---

## 5. Quy trình triển khai theo các quality gate

### Gate 0 - Contract approved

- Xuất schema hiện tại từ entity/migration.
- TV1 duyệt Product, Branch, Category.
- TV2 duyệt DogProfile, AdoptionPost.
- TV3 duyệt User, Role và tài khoản fixture.
- Lưu enum và mapping khóa ngoại trong `config/contracts/`.

**Đạt khi:** script kiểm tra contract chạy được và không còn trường/enum mơ hồ.

### Gate 1 - Source approved

Mỗi nguồn trong `sources.yaml` phải có:

```yaml
- id: source_01
  base_url: https://example.org
  allowed_paths: [/dogs/]
  purpose: breed_reference
  robots_checked_at: 2026-09-06
  terms_checked_at: 2026-09-06
  license: CC-BY-4.0
  max_pages: 20
  requests_per_second: 0.5
  enabled: false
```

Chỉ đổi `enabled: true` sau khi kiểm tra `robots.txt`, điều khoản sử dụng và giấy phép nội dung/ảnh. Nếu không xác định được quyền tải lại ảnh, chỉ lưu metadata để nghiên cứu và dùng ảnh do nhóm tự chụp hoặc nguồn có giấy phép rõ ràng cho bản demo.

**Đạt khi:** tất cả nguồn bật crawl đều có phạm vi, giới hạn và quyền sử dụng được ghi nhận.

### Gate 2 - Extract an toàn

Crawler phải có:

- User-Agent nhận diện dự án và thông tin liên hệ của nhóm.
- Timeout kết nối/đọc; retry hữu hạn với exponential backoff.
- Rate limit mặc định tối đa `0.5 request/second/domain`; không crawl song song khi chưa cần.
- Giới hạn `max_pages`, kích thước response và tổng dung lượng mỗi batch.
- Cache HTML để chạy lại bước transform mà không gọi lại website.
- Chỉ giữ trường cần thiết; cache/raw có thời hạn lưu mặc định 7 ngày sau khi curated data được duyệt, trừ khi cần giữ lâu hơn để audit.
- Chỉ cho phép `http/https`, chặn localhost, IP private và redirect sang domain ngoài whitelist.
- Log URL, HTTP status, content type, thời gian và checksum; không log secret.
- Dừng lịch sự khi gặp `401`, `403`, `429` hoặc thay đổi cấu trúc HTML lớn.

Không dùng Selenium/Playwright chỉ để vượt cơ chế chặn. Chỉ dùng trình duyệt tự động khi trang cho phép và dữ liệu thực sự render bằng JavaScript.

**Đạt khi:** chạy thử 3-5 trang, không phát sinh lỗi, không lấy PII và không vượt giới hạn nguồn.

### Gate 3 - Normalize và chống trùng

- Chuẩn hóa Unicode, khoảng trắng, URL tuyệt đối và tên giống theo bảng alias.
- Không xóa emoji hoặc HTML một cách tùy tiện; chỉ giữ plain text đã sanitize.
- Loại trùng chính xác bằng `source_url` và `content_sha256`.
- Phát hiện gần trùng bằng `normalized_breed + normalized_title`; ảnh có thể kiểm tra thêm perceptual hash.
- Bản ghi thiếu trường bắt buộc đi vào `data/quarantine/` cùng mã lỗi, không âm thầm bỏ qua.

**Đạt khi:** báo cáo ghi rõ tổng input, valid, duplicate, quarantined và lý do.

### Gate 4 - Generate có quy luật

- Chốt `random_seed` trong `generation.yaml`.
- Dùng bảng rule theo giống/size thay vì nhiều câu `if` rời rạc.
- Chia bộ dữ liệu theo mục đích: `dev-small`, `demo`, `test-edge-cases`.
- Chủ động tạo tình huống biên: hết hàng, chó giống stock 1, tin CLOSED, đơn PENDING/APPROVED/REJECTED.
- Không dùng Faker cho dữ liệu cần đúng nghiệp vụ; Faker chỉ dùng cho tên/mô tả giả không nhạy cảm.

**Đạt khi:** chạy hai lần với cùng seed cho checksum đầu ra giống nhau.

### Gate 5 - Validate

Pipeline phải trả exit code khác 0 nếu có lỗi nghiêm trọng:

- Null ở trường bắt buộc, sai kiểu, sai độ dài hoặc sai enum.
- ID/mã tham chiếu không tồn tại.
- Giá âm, stock âm, tuổi/cân nặng ngoài rule.
- Trùng `seed_key`, URL ảnh không phải HTTPS hoặc không đúng domain đã duyệt.
- Ảnh không phải MIME `image/jpeg|image/png|image/webp`, quá dung lượng hoặc quá nhỏ.
- Một ảnh chó cá thể được dùng cho cả chó bán và chó nhận nuôi.

Kiểm tra schema/rule phải chạy được offline và luôn là blocking. Kiểm tra URL ảnh sống là chế độ online có retry; lỗi mạng tạm thời được ghi riêng, chỉ trở thành blocking ở lần kiểm tra trước demo hoặc trước khi upload Cloudinary.

Report tối thiểu:

```json
{
  "run_id": "2026-09-06T120000Z",
  "random_seed": 20260906,
  "input": 40,
  "valid": 32,
  "duplicates": 5,
  "quarantined": 3,
  "errors": []
}
```

**Đạt khi:** không có lỗi nghiêm trọng, tỷ lệ hợp lệ đạt ngưỡng nhóm thống nhất và test pipeline pass.

### Gate 6 - Export và import idempotent

Ưu tiên bàn giao theo thứ tự:

1. Curated CSV/JSON đã validate.
2. Báo cáo chất lượng và checksum.
3. Migration/seed SQL do DB đã chốt sinh ra và được review.

Không nối chuỗi thủ công bằng `iterrows()` để tạo SQL từ nội dung crawl. Nếu cần import trực tiếp, dùng parameterized query/batch insert. Nếu cần file SQL để commit, phải có hàm quote theo đúng DB, test apostrophe/Unicode và review file sinh ra.

Với Spring Boot:

- Dùng Flyway/Liquibase cho schema và bộ seed demo đã version hóa.
- Không bật `spring.sql.init.mode=always` cho DB dùng chung; cấu hình này có thể nạp lại dữ liệu và gây trùng sau mỗi lần khởi động.
- Chỉ kích hoạt seed ở profile `dev`, `test` hoặc `demo`.
- Không trộn `data.sql` cơ bản với Flyway/Liquibase trong cùng chiến lược khởi tạo.
- Migration đã chạy không được sửa nội dung; tạo migration mới khi cần thay đổi.

**Đạt khi:** import vào DB rỗng thành công; chạy lại ứng dụng không sinh bản ghi trùng; mọi khóa ngoại hợp lệ; API TV1/TV2 đọc được dữ liệu.

---

## 6. Quản lý ảnh và Cloudinary

Cloudinary là bước publish asset, không phải nơi quyết định quyền sử dụng ảnh.

- Chỉ upload ảnh đã được nguồn cho phép tải lại hoặc ảnh của nhóm.
- Lưu `source_url`, license, author và checksum trong manifest của pipeline.
- Đặt `public_id` xác định, ví dụ `pawconnect/demo/<seed_key>`, để upload lại không tạo asset trùng.
- Dùng `overwrite=false` mặc định; chỉ overwrite khi có chủ ý.
- API secret chỉ đọc từ biến môi trường; `.env` thật không commit, chỉ commit `.env.example`.
- Validate HTTPS, MIME, kích thước byte, chiều rộng/chiều cao trước khi upload.
- Không upload SVG hoặc file không xác định từ nguồn crawl.
- Tạo ảnh thumbnail qua transformation khi hiển thị; không tải nhiều bản resize vào kho.

Nếu chưa chốt quyền ảnh, pipeline dùng placeholder hợp lệ hoặc ảnh được cấp phép rõ ràng. Không hotlink trực tiếp website bán hàng/rao vặt vì link có thể chết và có rủi ro bản quyền.

---

## 7. Git, bàn giao và phân quyền sở hữu

### 7.1. Nên commit

- Source code pipeline, test, config mẫu, contract.
- `requirements.txt` hoặc file khóa dependency.
- Dataset curated nhỏ đã được duyệt và không chứa dữ liệu nhạy cảm.
- Migration seed demo đã review.
- `.env.example`, không có secret thật.

### 7.2. Không nên commit

- `.env`, Cloudinary secret, cookie, token.
- HTML/ảnh raw crawl hàng loạt, cache, log và file tạm.
- Notebook output lớn, dataset lỗi trong quarantine.
- Dữ liệu có PII hoặc chưa rõ giấy phép.

### 7.3. Trách nhiệm

| Hạng mục | TV2/Data Engineer | TV1 | TV3 |
| --- | --- | --- | --- |
| Khung pipeline, provenance, validation | Thực hiện | Review | Review |
| Contract Product/Branch/Category | Hỗ trợ | Duyệt cuối | Theo dõi |
| Contract DogProfile/Adoption | Duyệt cuối | Theo dõi | Review security/user ref |
| Contract User/Role/fixture account | Không tự sửa | Theo dõi | Duyệt cuối |
| Seed/migration DB dùng chung | Tạo đề xuất | Xác nhận dữ liệu TV1 | Xác nhận tích hợp |
| Lỗi dữ liệu module nào | Phân tích pipeline | Sửa rule TV1 | Sửa rule TV3 |

Mỗi lần bàn giao phải kèm: version dataset, checksum, random seed, schema version, số bản ghi từng bảng, báo cáo validation và lệnh import/rollback.

---

## 8. Test tối thiểu

1. Unit test cho normalize, mapping alias, sinh giá/cân nặng và quote Unicode.
2. Contract test so cột output với schema/DTO/entity hiện hành.
3. Referential integrity test cho toàn bộ mã tham chiếu.
4. Determinism test: cùng seed tạo cùng checksum.
5. Duplicate test trong từng bảng và chéo Product/DogProfile.
6. Security test URL ảnh: scheme, redirect, domain whitelist, MIME và size.
7. Integration test import trên DB sạch.
8. Re-run test: chạy lại không tăng số bản ghi ngoài dự kiến.
9. Smoke test API:
   - `GET /api/products`
   - `GET /api/adoptions`
   - `GET /api/adoptions/{id}`

---

## 9. Kế hoạch thực dụng cho TV2

### Trong 1-2 ngày đầu

- Chốt DB, enum, `age` unit và ownership theo chi nhánh.
- Tạo contract Product, DogProfile, AdoptionPost.
- Tạo seed tham chiếu nhỏ: Role, Branch, Category, ServiceType.
- Tạo 5 Product + 5 DogProfile + 3 AdoptionPost bằng dữ liệu tự viết để TV1/TV3 tích hợp sớm.

### Sau khi team xác nhận

- Dựng crawler một nguồn được phép, chỉ 3-5 trang thử nghiệm.
- Hoàn thiện normalize, provenance, dedupe, quarantine và report.
- Mở rộng lên 30-50 Product, 15-25 DogProfile và 10-20 AdoptionPost.
- Xuất seed cho profile demo; chạy integration test và bàn giao checksum.

### Chỉ làm sau cùng

- Selenium/Playwright, crawl nhiều domain, perceptual hash nâng cao.
- Dataset lớn, scheduler tự động hoặc dashboard dữ liệu.
- Tự động upload hàng loạt lên Cloudinary.

---

## 10. Cách tìm và đánh giá mã nguồn mẫu trên GitHub

Từ khóa nên dùng:

```text
java web crawler crawler4j
jsoup scraper example maven
spring boot web scraper jsoup
spring boot flyway seed data
python pandas faker deterministic seed
csv data validation pytest
```

Cách lọc repository:

1. Có `LICENSE`; chỉ học/copy phần code tương thích với license của dự án.
2. Có README chạy được, Maven/Gradle rõ ràng và không hard-code secret.
3. Có test hoặc ví dụ input/output.
4. Kiểm tra ngày commit/release, issue mở và dependency có quá cũ không.
5. Đọc luồng `fetch -> parse -> validate -> persist`; không copy nguyên project.
6. Không lấy repository Java 8 cũ làm chuẩn dependency cho Spring Boot hiện tại; chỉ tham khảo cách tổ chức.

Nguồn tham khảo ưu tiên:

- crawler4j chính thức: <https://github.com/yasserg/crawler4j>
- jsoup Cookbook - CSS selectors: <https://jsoup.org/cookbook/extracting-data/selector-syntax>
- Spring Boot - Database Initialization: <https://docs.spring.io/spring-boot/how-to/data-initialization.html>
- Cloudinary - Upload images: <https://cloudinary.com/documentation/upload_images>
- Ví dụ Spring Boot + jsoup để đọc cấu trúc, không dùng nguyên dependency: <https://github.com/imaginalis/spring-boot-web-scraper-rss-feed-generator>

---

## 11. Prompt điều khiển Codex trong VS Code

Dùng prompt theo từng gate, không giao một lần toàn bộ pipeline. Mẫu:

```text
Đọc fix.pdf, docs/Members/TV2.md, docs/Project/Workflow.md và
docs/Project/dogconnect_hybrid_data_guide.md trước khi sửa code.

Hãy thực hiện Gate <N> của data pipeline PawConnect.
Phạm vi file được phép sửa: <liệt kê đường dẫn>.
Không đổi endpoint, entity, enum, schema hay file của TV1/TV3 nếu chưa có
data contract được xác nhận. Nếu phát hiện mâu thuẫn, dừng và liệt kê quyết
định cần nhóm chốt.

Trước khi code: nêu plan ngắn và tiêu chí nghiệm thu.
Sau khi code: chạy test liên quan, đưa lệnh chạy lại, tóm tắt file đã sửa,
kết quả test, dữ liệu bị quarantine và rủi ro còn lại.
Không commit, push, merge, upload Cloudinary hoặc crawl website thật nếu tôi
chưa yêu cầu rõ ràng.
```

Với mỗi lần giao việc tiếp theo, chỉ thay `<N>`, phạm vi file và tiêu chí nghiệm thu. Cách này giúp agent không tự mở rộng phạm vi, không sửa chồng module và để bạn kiểm soát từng bước.

---

## 12. Definition of Done

Pipeline được xem là hoàn thành khi:

- Tên dự án, schema, 14 entity và phạm vi dữ liệu nhất quán với tài liệu nhóm.
- Source registry có provenance và quyền sử dụng rõ ràng.
- Dữ liệu sinh lại được bằng một lệnh với seed cố định.
- Validation fail đúng khi sai enum, khóa ngoại, rule hoặc ảnh.
- Không có PII, secret, ảnh không rõ quyền hoặc bản ghi trùng.
- Product, DogProfile và AdoptionPost khớp contract đã duyệt.
- Import DB sạch thành công và chạy lại không nhân đôi dữ liệu.
- Seed chỉ hoạt động ở dev/test/demo.
- TV1 và TV3 có hướng dẫn, checksum và report để tích hợp độc lập.
