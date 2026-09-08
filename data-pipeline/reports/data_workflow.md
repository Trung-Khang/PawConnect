# PawConnect Data Workflow

## Mục đích

Pipeline tách dữ liệu tham chiếu giống chó, quan sát thị trường và dữ liệu demo
nghiệp vụ. Raw data không phải dữ liệu bàn giao cho TV1, TV2 hoặc TV3.

## Các lớp dữ liệu

| Lớp | Nguồn | Mục đích | Được dùng để bàn giao? |
| --- | --- | --- | --- |
| Breed reference | Wikipedia allowlist và catalog nội bộ | Chuẩn adult size, adult weight range và stable `breed_code` | Có, sau validation/review |
| Market observation raw | Nguồn commercial đã duyệt | Quan sát public về giá, age, label giống và availability | Không |
| Candidate | Cleaner + deduplication | Fact đã canonical, product observation sạch và aggregate price band | Có, để review |
| Generated business data | Synthetic generator | Dữ liệu demo nghiệp vụ cho dev/test/demo | Có, sau validation |
| Curated handoff | Dataset được nhóm xác nhận | Seed/fixture dùng cho tích hợp | Có |

## Quy ước Commercial Puppy v0.2

| Field | Ý nghĩa |
| --- | --- |
| `breed_code` | Chỉ có khi label khớp chắc chắn với `breeds.csv` |
| `breed_type` | `PUREBRED`, `MIXED` hoặc `UNKNOWN` |
| `age_months` | Tuổi được nguồn công khai thể hiện rõ; không suy đoán |
| `current_weight_kg` | Cân nặng tại thời điểm quan sát, không phải adult weight |
| `current_size` | Kích cỡ hiện tại nếu nguồn xác định rõ |
| `expected_adult_size` | Adult size từ breed reference, chỉ áp dụng khi mapping đủ chắc chắn |

`default_size`, `min_weight_kg` và `max_weight_kg` trong `breeds.csv` là adult
reference. Không dùng adult weight range để validate cân nặng hiện tại của puppy.

DogProfile nhận nuôi và Product thương mại là hai dataset/entity nghiệp vụ khác
nhau. `Product.suitable_size` vẫn giữ nghĩa cũ cho thức ăn và phụ kiện.

## Luồng chính thức

```text
Breed reference raw / catalog
        +
Commercial observation raw
        |
        v
Cleaner + validation + deduplication
        |
        +--> Candidate product observation
        |
        +--> Aggregate market price bands
        |
        v
Commercial Product generator (giai đoạn sau)
        |
        v
Generated dev/test/demo data
        |
        v
Curated handoff cho TV1 / TV2 / TV3
```

## Quy tắc xử lý raw commercial

- Crawler chỉ ghi raw observation; cleaner quyết định record nào đủ điều kiện.
- Không suy đoán breed, tuổi, cân nặng hiện tại, size hiện tại hoặc category.
- Quarantine record có PII/contact, URL chat, giá lỗi, thiếu URL hoặc duplicate.
- Candidate/handoff không chứa `source_url`, listing title, store name, mô tả, ảnh,
  phone, email, địa chỉ hoặc chat/contact marker.
- Chợ Tốt chỉ cung cấp aggregate price band; không bàn giao từng listing cá thể.
- Pet Mart chỉ giữ fact công khai đã sạch. `category_code` để trống khi raw chưa có
  mapping subcategory được xác nhận.

## Trạng thái hiện tại

| Giai đoạn | Trạng thái | Kết quả |
| --- | --- | --- |
| 1A - Data Foundation | COMPLETED | Reference seed, breed catalog, curated adoption fixture và validation |
| 2A - Breed crawler | COMPLETED | Raw Wikipedia breed crawler allowlist |
| 2A.2 - Commercial raw crawler | COMPLETED | Raw Chợ Tốt và Pet Mart tách theo source |
| 2B - Synthetic adoption generator | COMPLETED | 12 DogProfile, 8 AdoptionPost, 12 AdoptionApplication |
| 1B - Commercial Cleaner + Deduplication | COMPLETED | Candidate product, aggregate price band, quarantine summary và validation |
| Commercial Product generator | NOT STARTED | Chỉ thực hiện sau candidate review |
| Candidate/curated merge và DB import | NOT STARTED | Chờ review, mapping entity và migration |

## Kết quả Giai đoạn 1B

| Source | Raw | Candidate | Quarantine | Dedup | Age unverified |
| --- | ---: | ---: | ---: | ---: | ---: |
| Chợ Tốt | 38 | 0 | 0 | 0 | 27 |
| Chợ Tốt (cho-giong) | 54 | 0 | 0 | 4 | 37 |
| Pet Mart | 96 | 73 | 23 | 0 | 0 |

- `puppy_market_observations.csv`: 0 record theo rule bảo vệ listing cá thể.
- `product_catalog_observations.csv`: 73 record sạch.
- `market_price_bands.csv`: 3 band, chỉ nhóm có `sample_count >= 3`.
- Age Chợ Tốt không có evidence phrase gốc được xóa khỏi candidate fact và dùng
  `life_stage=UNKNOWN` khi tổng hợp.

## Artifact liên quan

| Artifact | Vai trò |
| --- | --- |
| `data-pipeline/data/raw/commercial_observations/` | Raw source, không dùng trực tiếp để handoff |
| `data-pipeline/data/candidate/commercial/` | Candidate sạch và aggregate price band |
| `data-pipeline/reports/commercial_quarantine_summary.md` | Chỉ reason/count, không lặp raw hoặc PII |
| `data-pipeline/reports/commercial_cleaning_validation.md` | Validation count và kết quả PASS/FAIL |
| `docs/Project/data_contract.md` | Chuẩn dữ liệu dùng chung v0.1/v0.2 |

## Bước tiếp theo

1. Review candidate và price band với TV1/TV3.
2. Chốt mapping Product/DTO cho commercial profile v0.2.
3. Tạo Commercial Product generator từ candidate facts và price bands.
4. Chỉ cân nhắc candidate/curated merge hoặc DB import sau khi mapping và migration
   được nhóm review.
