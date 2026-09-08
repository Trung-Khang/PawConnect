# PawConnect - Data Handoff

## Mục lục

1. [Trạng thái phát hành](#trạng-thái-phát-hành)
2. [Data Inventory & Consumer](#data-inventory--consumer)
3. [Bàn giao TV1](#tv1--product-database-và-commerce)
4. [Bàn giao TV2](#tv2--adoption-dogprofile-và-pipeline)
5. [Bàn giao TV3](#tv3--user-security-và-cloudinary)
6. [Importer DB tương lai](#importer-db-tương-lai)
7. [Quy tắc chung](#quy-tắc-chung)

## Trạng thái phát hành

- Bootstrap mặc định cho `dev/test/demo`: `data-pipeline/data/seed/v2/`.
- `seed/v1` là baseline immutable để đối chiếu, không phải nguồn import mặc định.
- `seed/v2`: 14 breed, 28 DogProfile, 28 AdoptionPost, 36 AdoptionApplication và 27 Product.
- Curated là master data; generated là workspace build; raw và candidate không được import DB trực tiếp.
- Manifest và validation report trong release là nguồn kiểm tra chính thức cho header, count, checksum và dependency.
- Release chỉ được dùng cho `dev/test/demo`. DB import vẫn `NOT_READY_FOR_DB_IMPORT` cho đến khi importer, schema và mapping được review.

## Data Inventory & Consumer

| File path | Loại | Producer | Consumer chính | Module/chức năng | DB target tương lai | Import trực tiếp | Quy tắc/rủi ro |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `data-pipeline/data/seed/v2/reference/roles.csv` | seed | TV3 ownership, TV2 release | TV1/TV2/TV3 | authorization | Role | Có, khi importer sẵn sàng | Map bằng `role_code`, không tự đổi enum. |
| `data-pipeline/data/seed/v2/reference/branches.csv` | seed | TV1 ownership, TV2 release | TV1/TV2/TV3 | branch scope | Branch | Có | Map bằng `branch_code`; không dùng DB ID trong CSV. |
| `data-pipeline/data/seed/v2/reference/categories.csv` | seed | TV1 ownership, TV2 release | TV1 | Product catalog | Category | Có | `category_code` là stable reference. |
| `data-pipeline/data/seed/v2/reference/service_types.csv` | seed | TV1 ownership, TV2 release | TV1 | booking | ServiceType | Có | Giá là VND nguyên; map bằng `service_type_code`. |
| `data-pipeline/data/seed/v2/catalog/breeds.csv` | seed | TV2 | TV1/TV2 | adoption, puppy product | Breed nếu schema có | Có, sau review schema | Adult size/weight reference, không dùng làm current puppy weight. |
| `data-pipeline/data/seed/v2/catalog/breed_references.csv` | seed | TV2 | TV2/reviewer | provenance | Không bắt buộc | Không | Dùng review provenance, không phải entity nghiệp vụ. |
| `data-pipeline/data/seed/v2/fixtures/users.csv` | seed | TV3 | TV1/TV2/TV3 | auth, adoption ownership | User | Có điều kiện | `TV3_SEED_REQUIRED` không phải password; hash tại runtime. |
| `data-pipeline/data/seed/v2/adoption/dog_profiles.csv` | seed | TV2 | TV2/TV3 | adoption | DogProfile | Có điều kiện | Map breed/branch trước; image URL chỉ là demo placeholder hợp lệ. |
| `data-pipeline/data/seed/v2/adoption/adoption_posts.csv` | seed | TV2 | TV2/TV3 | adoption, chat context | AdoptionPost | Có điều kiện | Creator phải ADMIN/BRANCH_MANAGER; manager cùng branch với dog. |
| `data-pipeline/data/seed/v2/adoption/adoption_applications.csv` | seed | TV2 | TV2/TV3 | adoption, chat context | AdoptionApplication | Có điều kiện | Tối đa một APPROVED; post CLOSED không có PENDING. |
| `data-pipeline/data/seed/v2/product/products.csv` | seed | TV2 | TV1 | commerce | Product | Chưa | Chờ Entity/DTO/schema Product v0.2 tương thích. |
| `data-pipeline/data/seed/v2/manifest.json` | seed metadata | TV2 | importer/reviewer | integrity | Import registry | Không | Kiểm checksum, header, count, dependency trước import. |
| `data-pipeline/data/generated/adoption/*.csv` | generated | TV2 generator | TV2 | workspace build | Không | Không | Không phải importer input; tạo lại bằng seed cố định. |
| `data-pipeline/data/generated/product/products.csv` | generated | TV2 generator | TV1/TV2 | workspace build | Không | Không | Snapshot đã được đưa vào `seed/v2/product/`. |
| `data-pipeline/data/candidate/commercial/*.csv` | candidate | TV2 cleaner | TV2/reviewer | review, price band | Không | Không | Không có listing identity; không phải DB input. |
| `data-pipeline/data/raw/**` | raw | crawler | TV2 pipeline | evidence | Không | Không | Local/gitignored; không PII, ảnh crawl hoặc contact. |

Đường dẫn trong bảng là tương đối với repository.

## TV1 - Product, Database và Commerce

**Đọc trước:** `docs/Project/data_contract.md`, `seed/v2/manifest.json`, `reference/categories.csv`, `reference/branches.csv`, `catalog/breeds.csv` và `product/products.csv`.

`products.csv` là snapshot 27 Product synthetic để TV1 chuẩn bị UI, mapping và importer adapter. Không đọc raw/candidate để import DB.

| CSV field | Mapping TV1 |
| --- | --- |
| `name`, `description`, `price`, `stock`, `image_url`, `suitable_size`, `is_breeding_dog`, `health_status`, `care_instructions` | Field Product tương ứng. |
| `category_code` | Lookup Category rồi map `categoryId`. |
| `branch_code` | Lookup Branch rồi map `branchId`. |
| `seed_key` | Idempotency/import registry, không dùng làm DB ID. |

- `PUPPY`: `stock=1`; `breed_code`, `breed_type`, `life_stage`, `age_months`, `current_weight_kg`, `current_size`, `expected_adult_size` có ý nghĩa.
- `FOOD` và `ACCESSORY`: toàn bộ puppy field phải rỗng.
- Field v0.2 chưa có Entity/DTO hiện tại: `product_kind`, `breed_code`, `breed_type`, `life_stage`, `age_months`, `current_weight_kg`, `current_size`, `expected_adult_size`.
- `image_url` của Product seed đang rỗng. Chưa có Cloudinary.

Việc TV1 cần làm: thiết kế Entity/DTO/schema theo contract, map stable code sang DB ID, viết importer idempotent, validate VND nguyên và stock, rồi mới xem xét import Product. Không coi Product v0.2 là DB-ready trước khi mapping hoàn tất.

## TV2 - Adoption, DogProfile và Pipeline

**Đọc trước:** `seed/v2/catalog/breeds.csv`, `seed/v2/fixtures/users.csv`, toàn bộ `seed/v2/adoption/` và `seed/v2/manifest.json`.

Thứ tự mapping: Breed reference -> DogProfile -> AdoptionPost -> AdoptionApplication. Foreign key CSV dùng `breed`/stable code và `seed_key`, không dùng ID tự tăng.

- DogProfile: `gender` là `MALE|FEMALE`; `vaccination_status` là `NOT_VACCINATED|PARTIALLY_VACCINATED|FULLY_VACCINATED`.
- AdoptionPost: `AVAILABLE|CLOSED`; creator là ADMIN hoặc BRANCH_MANAGER. Manager phải cùng branch với DogProfile.
- AdoptionApplication: `PENDING|APPROVED|REJECTED`; mỗi post tối đa một APPROVED; APPROVED yêu cầu post CLOSED và không còn PENDING.
- Breed catalog là adult reference. Current puppy fields chỉ thuộc Product commercial, không dùng adult range để validate current puppy weight.

TV2 là owner pipeline, catalog/release, mapping Adoption/DogProfile và review release mới. Không biến listing crawl thành DogProfile thật.

## TV3 - User, Security và Cloudinary

**Đọc trước:** `seed/v2/reference/roles.csv`, `seed/v2/reference/branches.csv`, `seed/v2/fixtures/users.csv` và toàn bộ seed adoption.

- User fixture không có password thật. `TV3_SEED_REQUIRED` phải được thay bằng credential demo được hash tại runtime qua environment/config an toàn; không ghi secret vào CSV hoặc Git.
- BRANCH_MANAGER bắt buộc có `branch_code`; CUSTOMER và ADMIN để trống.
- Enum Java, migration và backward compatibility phải tương thích contract v0.2; không tự đổi role, stable code hoặc enum.
- Cloudinary chỉ upload ảnh do hệ thống/người dùng được phép cung cấp. Không dùng ảnh crawl/raw.
- Chỉ lưu URL Cloudinary đã upload vào DB. `image_url` rỗng trong Product seed là hợp lệ.
- Dùng environment variables cho credential Cloudinary; không commit API secret, token, preset secret hoặc URL có credential.

## Importer DB tương lai

Importer chỉ đọc `seed/v2`, không đọc raw, candidate hoặc generated. Import theo thứ tự:

`Role -> Branch -> Category -> ServiceType -> Breed (nếu schema có) -> User -> DogProfile -> AdoptionPost -> AdoptionApplication -> Product`.

- Lookup bằng `seed_key`/stable code hoặc import registry để idempotent.
- Không delete/prune tự động.
- Không import `TV3_SEED_REQUIRED` như password thật.
- Chưa import Product v0.2 trước khi Entity/DTO/schema tương thích.
- Đây không phải production import.

## Quy tắc chung

- `docs/Project/data_contract.md` là chuẩn chung; không tự đổi header, stable code hay enum.
- Không PII, password thật, token, API key, ảnh crawl hoặc contact data.
- Raw/candidate chỉ phục vụ pipeline và review.
- Conflict về contract hoặc code phải báo TV2/Data Engineer.
- `v2` là default bootstrap release. Chỉ tạo `v3+` sau khi curated/rule được review; không ghi đè release đã phát hành.
