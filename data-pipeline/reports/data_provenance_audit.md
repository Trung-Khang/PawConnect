# Audit Nguồn Gốc và Provenance Final Seed V3

## Phạm vi và quy tắc trung thực

Báo cáo này audit nguồn của breed và commerce trong Final Seed V3; không phải
nguồn import database. Bằng chứng chỉ lấy từ curated catalog, config crawler,
source crawler/cleaner, manifest/provenance V3, raw local còn tồn tại, Git
history và validation report. Không có crawl mạng, build seed hay thay đổi CSV.

Nhãn dùng trong báo cáo:

- **AUTHORITATIVE_REFERENCE**: có nguồn standard/research trong
  breed_references.csv.
- **OBSERVED_FROM_CHOTOT**: có URL và retrieved_at trong raw local Chợ Tốt.
- **GENERATED_FROM_CURATED_RULE**: record V3 sinh có kiểm soát từ curated rule.
- **UNKNOWN/UNVERIFIED**: repository không có URL/evidence phù hợp để xác minh.

Không suy ra rằng PuppyListing là tin đăng thật chỉ vì breed đó từng xuất hiện
trong raw Chợ Tốt. Không có URL/metadata riêng của từng listing trong Seed V3.

## A. Tổng quan pipeline provenance

    raw -> candidate/review -> curated -> deterministic build -> seed/v3 -> database

- **Raw** là evidence crawl cục bộ; không import database.
- **Candidate/review** là lớp làm sạch và chờ review; không import database.
- **Curated** là master data/rule đã chuẩn hóa.
- **Seed V3** là snapshot bootstrap bất biến từ curated/rule.
- **Database sau import** là nguồn dữ liệu vận hành.

Seed V3 không được sửa để cập nhật website và không đồng bộ hai chiều với
database. Raw/candidate không được import trực tiếp.

## B. Audit 15 breed catalog

Nguồn chính là data-pipeline/data/curated/catalog/breed_references.csv. “Có”
ở cột Chợ Tốt nghĩa là raw local có nhãn khớp ở mức quan sát; không khẳng định
thuần chủng, số lượng hay giá của Seed.

| Breed code | Tên catalog | Thương mại | Adult size / kg / min age | Source type | Source, URL, retrieved_at | Evidence | Chợ Tốt | Ghi chú |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| BREED_HMONG_COC_DUOI | Chó H'Mông cộc đuôi | Có | MEDIUM, 16–26, 12 tháng | AUTHORITATIVE_REFERENCE | Vietnam Kennel Association; Vietnam-Russia Tropical Center; https://vka.vn/bang-tieu-chuan-cho-hmong-coc-duoi/; 2026-09-09T00:00:00Z | PASS; breed_references.csv có thêm URL nghiên cứu Trung tâm Nhiệt đới Việt–Nga | Có; raw Chợ Tốt 2026-09-08T15:25:10Z | 15 nhãn khớp từ raw local; listing V3 vẫn sinh từ rule. |
| BREED_POMERANIAN | Pomeranian | Có | SMALL, 1.4–3.2, 12 tháng | AUTHORITATIVE_REFERENCE | American Kennel Club; https://www.akc.org/dog-breeds/pomeranian; 2026-09-09T00:00:00Z | PASS | Có; raw Chợ Tốt 2026-09-08T15:25:06Z | Alias Phốc Sóc/Pom được quan sát. |
| BREED_CORGI | Corgi | Có | MEDIUM, 10–14, 12 tháng | UNKNOWN/UNVERIFIED | CURATED_LEGACY_V0_1; không có URL external | LEGACY_ACCEPTED | Có; raw Chợ Tốt 2026-09-08T15:25:06Z | Có 2 nhãn khớp; adult range không có URL external trong repository. |
| BREED_SHIBA_INU | Shiba Inu | Có | MEDIUM, 8–12, 12 tháng | UNKNOWN/UNVERIFIED | CURATED_LEGACY_V0_1; không có URL external | LEGACY_ACCEPTED | Có; raw Chợ Tốt 2026-09-08T15:25:10Z | Có 1 nhãn khớp. |
| BREED_GOLDEN_RETRIEVER | Golden Retriever | Có | LARGE, 25–34, 18 tháng | UNKNOWN/UNVERIFIED | CURATED_LEGACY_V0_1; không có URL external | LEGACY_ACCEPTED | Có; raw Chợ Tốt 2026-09-08T15:25:06Z | Có 2 nhãn khớp. |
| BREED_LABRADOR_RETRIEVER | Labrador Retriever | Có | LARGE, 25–36, 18 tháng | UNKNOWN/UNVERIFIED | CURATED_LEGACY_V0_1; không có URL external | LEGACY_ACCEPTED | Có; raw Chợ Tốt 2026-09-08T15:25:10Z | Có 1 nhãn khớp. |
| BREED_HUSKY_SIBERIAN | Husky Siberian | Có | LARGE, 16–27, 18 tháng | UNKNOWN/UNVERIFIED | CURATED_LEGACY_V0_1; không có URL external | LEGACY_ACCEPTED | Không | Alias “Husky” map chính thức thành BREED_HUSKY_SIBERIAN. |
| BREED_SAMOYED | Samoyed | Có | LARGE, 16–30, 18 tháng | AUTHORITATIVE_REFERENCE | American Kennel Club; https://www.akc.org/dog-breeds/samoyed/; 2026-09-09T00:00:00Z | PASS | Không | Provenance AKC đã duyệt. |
| BREED_ALASKAN_MALAMUTE | Alaskan Malamute | Có | LARGE, 32–43, 18 tháng | UNKNOWN/UNVERIFIED | CURATED_LEGACY_V0_1; không có URL external | LEGACY_ACCEPTED | Không | Chưa có evidence/URL Chợ Tốt trong raw local. |
| BREED_BEAGLE | Beagle | Có | MEDIUM, 9–15, 12 tháng | UNKNOWN/UNVERIFIED | CURATED_LEGACY_V0_1; không có URL external | LEGACY_ACCEPTED | Không | Chưa có evidence/URL Chợ Tốt trong raw local. |
| BREED_FRENCH_BULLDOG | French Bulldog | Có | SMALL, 8–14, 12 tháng | UNKNOWN/UNVERIFIED | CURATED_LEGACY_V0_1; không có URL external | LEGACY_ACCEPTED | Không | Chưa có evidence/URL Chợ Tốt trong raw local. |
| BREED_POODLE | Poodle | Có | SMALL, 3–8, 12 tháng | UNKNOWN/UNVERIFIED | CURATED_LEGACY_V0_1; raw Wikipedia: https://en.wikipedia.org/wiki/Poodle; 2026-09-08T12:49:04Z | LEGACY_ACCEPTED; raw evidence tồn tại | Có; raw Chợ Tốt 2026-09-08T15:25:06Z | Wikipedia allowlist là evidence tham khảo, không thay thế provenance adult legacy. |
| BREED_CHIHUAHUA | Chihuahua | Có | SMALL, 1–3, 12 tháng | UNKNOWN/UNVERIFIED | CURATED_LEGACY_V0_1; raw Wikipedia: https://en.wikipedia.org/wiki/Chihuahua_(dog); 2026-09-08T12:49:06Z | LEGACY_ACCEPTED; raw evidence tồn tại | Có; raw Chợ Tốt 2026-09-08T15:25:06Z | Wikipedia raw có range 1–3 kg. |
| BREED_PUG | Pug | Có | SMALL, 6–10, 12 tháng | UNKNOWN/UNVERIFIED | CURATED_LEGACY_V0_1; raw Wikipedia: https://en.wikipedia.org/wiki/Pug; 2026-09-08T12:49:08Z | LEGACY_ACCEPTED; raw evidence tồn tại | Không | Wikipedia raw có range 6.35–8.16 kg. |
| BREED_PHU_QUOC | Chó Phú Quốc | Không | MEDIUM, 12–20, 12 tháng | UNKNOWN/UNVERIFIED | CURATED_LEGACY_V0_1; không có URL external | LEGACY_ACCEPTED | Có; raw Chợ Tốt 2026-09-08T15:25:06Z | Giữ trong catalog nhưng không thuộc PuppyListing thương mại V3. |

URL raw Chợ Tốt được đối chiếu trong hai file local
data-pipeline/data/raw/commercial_observations/cho_tot.csv và
cho_tot_cho_giong.csv. Ví dụ evidence theo nhãn: Pomeranian
https://www.chotot.com/mua-ban-cho-quan-6-tp-ho-chi-minh/133429260.htm,
Poodle https://www.chotot.com/mua-ban-cho-quan-go-vap-tp-ho-chi-minh/134565889.htm,
và H'Mông cộc đuôi
https://www.chotot.com/mua-ban-cho-quan-go-vap-tp-ho-chi-minh/134449095.htm.

## C. Audit 14 PuppyListing thương mại

Mọi record dưới đây có source_type **GENERATED_FROM_CURATED_RULE**: builder dùng
catalog và puppy_price_rules.csv đã APPROVED. “Evidence Chợ Tốt” chỉ là evidence
breed-level từ raw local, không phải URL của listing V3.

| seed_key | breed_code | listing_title | Branch | Stock | Giá VND/bé | Evidence Chợ Tốt | Price rule | Provenance status |
| --- | --- | --- | --- | ---: | ---: | --- | --- | --- |
| puppy_listing_hmong_coc_duoi | BREED_HMONG_COC_DUOI | Chó con Chó H'Mông cộc đuôi khỏe mạnh | BR_HCM_01 | 7 | 5.550.000 | Có | 5.500.000–9.000.000 | GENERATED_FROM_CURATED_RULE |
| puppy_listing_pomeranian | BREED_POMERANIAN | Chó con Pomeranian khỏe mạnh | BR_HN_01 | 3 | 6.600.000 | Có | 4.000.000–7.000.000 | GENERATED_FROM_CURATED_RULE |
| puppy_listing_corgi | BREED_CORGI | Chó con Corgi khỏe mạnh | BR_DN_01 | 17 | 9.100.000 | Có | 7.000.000–11.000.000 | GENERATED_FROM_CURATED_RULE |
| puppy_listing_shiba_inu | BREED_SHIBA_INU | Chó con Shiba Inu khỏe mạnh | BR_HCM_01 | 16 | 10.100.000 | Có | 8.000.000–13.000.000 | GENERATED_FROM_CURATED_RULE |
| puppy_listing_golden_retriever | BREED_GOLDEN_RETRIEVER | Chó con Golden Retriever khỏe mạnh | BR_HN_01 | 6 | 10.550.000 | Có | 7.500.000–12.000.000 | GENERATED_FROM_CURATED_RULE |
| puppy_listing_labrador_retriever | BREED_LABRADOR_RETRIEVER | Chó con Labrador Retriever khỏe mạnh | BR_DN_01 | 11 | 9.600.000 | Có | 7.000.000–11.000.000 | GENERATED_FROM_CURATED_RULE |
| puppy_listing_husky_siberian | BREED_HUSKY_SIBERIAN | Chó con Husky Siberian khỏe mạnh | BR_HCM_01 | 3 | 8.650.000 | Không | 8.500.000–13.500.000 | GENERATED_FROM_CURATED_RULE |
| puppy_listing_samoyed | BREED_SAMOYED | Chó con Samoyed khỏe mạnh | BR_HN_01 | 15 | 10.250.000 | Không | 10.000.000–16.000.000 | GENERATED_FROM_CURATED_RULE |
| puppy_listing_alaskan_malamute | BREED_ALASKAN_MALAMUTE | Chó con Alaskan Malamute khỏe mạnh | BR_DN_01 | 1 | 14.150.000 | Không | 10.000.000–16.000.000 | GENERATED_FROM_CURATED_RULE |
| puppy_listing_beagle | BREED_BEAGLE | Chó con Beagle khỏe mạnh | BR_HCM_01 | 17 | 8.100.000 | Không | 5.000.000–8.500.000 | GENERATED_FROM_CURATED_RULE |
| puppy_listing_french_bulldog | BREED_FRENCH_BULLDOG | Chó con French Bulldog khỏe mạnh | BR_HN_01 | 20 | 7.100.000 | Không | 6.500.000–11.000.000 | GENERATED_FROM_CURATED_RULE |
| puppy_listing_poodle | BREED_POODLE | Chó con Poodle khỏe mạnh | BR_DN_01 | 20 | 3.500.000 | Có | 2.000.000–5.500.000 | GENERATED_FROM_CURATED_RULE |
| puppy_listing_chihuahua | BREED_CHIHUAHUA | Chó con Chihuahua khỏe mạnh | BR_HCM_01 | 14 | 4.700.000 | Có | 4.000.000–7.000.000 | GENERATED_FROM_CURATED_RULE |
| puppy_listing_pug | BREED_PUG | Chó con Pug khỏe mạnh | BR_HN_01 | 12 | 4.200.000 | Không | 4.000.000–6.500.000 | GENERATED_FROM_CURATED_RULE |

Có đúng 14 listing/14 breed thương mại, không trùng breed, không có
BREED_PHU_QUOC. Stock là lượng lúc bootstrap, không phải số lượng quan sát
Chợ Tốt. price_per_puppy_vnd là giá mỗi bé.

## D. Audit 18 Product

Products.csv chỉ có FOOD và ACCESSORY. Không record nào có source URL,
retrieved_at, brand hoặc evidence gắn trực tiếp trong Seed; vì vậy mọi record là
**GENERATED_FROM_CURATED_RULE** với evidence_status
**UNVERIFIED_GENERATED**. Không được diễn giải là dữ liệu Pet Mart hoặc Chợ Tốt.

| seed_key | Kind | Name | Category | Branch | Price | Stock | Source/evidence |
| --- | --- | --- | --- | --- | ---: | ---: | --- |
| product_food_br_hcm_01_1 | FOOD | Thức ăn hạt cỡ nhỏ | CAT_FOOD | BR_HCM_01 | 180.000 | 9 | UNVERIFIED_GENERATED |
| product_food_br_hcm_01_2 | FOOD | Thức ăn hạt cỡ vừa | CAT_FOOD | BR_HCM_01 | 220.000 | 10 | UNVERIFIED_GENERATED |
| product_food_br_hcm_01_3 | FOOD | Thức ăn hạt cỡ lớn | CAT_FOOD | BR_HCM_01 | 260.000 | 11 | UNVERIFIED_GENERATED |
| product_accessory_br_hcm_01_1 | ACCESSORY | Dây dắt chó | CAT_ACCESSORY | BR_HCM_01 | 120.000 | 9 | UNVERIFIED_GENERATED |
| product_accessory_br_hcm_01_2 | ACCESSORY | Bát ăn chống trượt | CAT_ACCESSORY | BR_HCM_01 | 90.000 | 10 | UNVERIFIED_GENERATED |
| product_accessory_br_hcm_01_3 | ACCESSORY | Đồ chơi bóng cao su | CAT_ACCESSORY | BR_HCM_01 | 75.000 | 11 | UNVERIFIED_GENERATED |
| product_food_br_hn_01_1 | FOOD | Thức ăn hạt cỡ nhỏ | CAT_FOOD | BR_HN_01 | 180.000 | 9 | UNVERIFIED_GENERATED |
| product_food_br_hn_01_2 | FOOD | Thức ăn hạt cỡ vừa | CAT_FOOD | BR_HN_01 | 220.000 | 10 | UNVERIFIED_GENERATED |
| product_food_br_hn_01_3 | FOOD | Thức ăn hạt cỡ lớn | CAT_FOOD | BR_HN_01 | 260.000 | 11 | UNVERIFIED_GENERATED |
| product_accessory_br_hn_01_1 | ACCESSORY | Dây dắt chó | CAT_ACCESSORY | BR_HN_01 | 120.000 | 9 | UNVERIFIED_GENERATED |
| product_accessory_br_hn_01_2 | ACCESSORY | Bát ăn chống trượt | CAT_ACCESSORY | BR_HN_01 | 90.000 | 10 | UNVERIFIED_GENERATED |
| product_accessory_br_hn_01_3 | ACCESSORY | Đồ chơi bóng cao su | CAT_ACCESSORY | BR_HN_01 | 75.000 | 11 | UNVERIFIED_GENERATED |
| product_food_br_dn_01_1 | FOOD | Thức ăn hạt cỡ nhỏ | CAT_FOOD | BR_DN_01 | 180.000 | 9 | UNVERIFIED_GENERATED |
| product_food_br_dn_01_2 | FOOD | Thức ăn hạt cỡ vừa | CAT_FOOD | BR_DN_01 | 220.000 | 10 | UNVERIFIED_GENERATED |
| product_food_br_dn_01_3 | FOOD | Thức ăn hạt cỡ lớn | CAT_FOOD | BR_DN_01 | 260.000 | 11 | UNVERIFIED_GENERATED |
| product_accessory_br_dn_01_1 | ACCESSORY | Dây dắt chó | CAT_ACCESSORY | BR_DN_01 | 120.000 | 9 | UNVERIFIED_GENERATED |
| product_accessory_br_dn_01_2 | ACCESSORY | Bát ăn chống trượt | CAT_ACCESSORY | BR_DN_01 | 90.000 | 10 | UNVERIFIED_GENERATED |
| product_accessory_br_dn_01_3 | ACCESSORY | Đồ chơi bóng cao su | CAT_ACCESSORY | BR_DN_01 | 75.000 | 11 | UNVERIFIED_GENERATED |

## E. Bảng tổng hợp nguồn

| Source | URL | Loại | Dataset sử dụng | Direct hay tham khảo | Evidence/retrieved | Terms/licence | Trạng thái |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Wikipedia English | https://en.wikipedia.org | Breed reference | Raw Poodle/Chihuahua/Pug | Tham khảo | raw breeds 20260908T121000Z, 2026-09-08 | CC BY-SA 4.0/GFDL theo config; không collect media | APPROVED raw allowlist |
| American Kennel Club | https://www.akc.org | Breed standard | Pomeranian, Samoyed | Curated provenance | 2026-09-09 | License note trong breed_references.csv | PASS |
| Vietnam Kennel Association | https://vka.vn/bang-tieu-chuan-cho-hmong-coc-duoi/ | Breed standard | H'Mông cộc đuôi | Curated provenance | 2026-09-09 | License note trong breed_references.csv | PASS |
| Vietnam-Russia Tropical Center | https://trungtamnhietdoivietnga.com.vn/initial-results-of-research-on-biological-characteristics-of-short-tailed-h-mong-dog-breed-of-vietnam-russia-tropical-center | Research | H'Mông cộc đuôi | Curated provenance | 2026-09-09 | License note trong breed_references.csv | PASS |
| Chợ Tốt | https://www.chotot.com/mua-ban-cho-tp-ho-chi-minh?event_source=search_suggestion | Marketplace observation | Raw puppy labels/prices | Chỉ tham khảo | raw local 2026-09-08 | config yêu cầu allowlist, permission/robots/terms APPROVED; không PII/chat/ảnh | Evidence cục bộ |
| Chợ Tốt cho giống | https://www.chotot.com/tags/mua-ban-cho/cho-giong | Marketplace observation | Raw puppy labels/prices | Chỉ tham khảo | raw local 2026-09-08 | Cùng giới hạn Chợ Tốt | Evidence cục bộ |
| Pet Mart | https://www.petmart.vn/cho | Commercial catalog | Raw product observation | Chỉ tham khảo | Không có evidence gắn Product V3 | permission/robots/terms APPROVED trong config | Không direct vào V3 |

## F. Thống kê provenance

- Breed catalog: **15**.
- Breed có authoritative reference PASS: **3** (Pomeranian, H'Mông cộc đuôi,
  Samoyed).
- Breed có evidence Chợ Tốt raw local: **9**.
- Breed chỉ legacy curated/không URL external trong provenance: **12**; trong đó
  Poodle, Chihuahua, Pug có raw Wikipedia bổ sung nhưng không có authoritative
  external reference trong breed_references.csv.
- PuppyListing: **14**; có evidence Chợ Tốt ở mức breed: **8**; sinh từ curated
  price rule: **14**.
- Product: **18**; FOOD: **9**; ACCESSORY: **9**; có URL/evidence trực tiếp:
  **0**; generated/không URL: **18**.

## G. Kết luận sử dụng dữ liệu

Catalog Breed là master data đã review, không phải toàn bộ dữ liệu crawl Chợ Tốt.
Chợ Tốt chỉ là quan sát sự hiện diện/giá khi evidence hợp lệ. Breed hoặc Product
không có Chợ Tốt không được coi là dữ liệu thị trường thật; commerce V3 được
đánh dấu GENERATED_FROM_CURATED_RULE hoặc UNVERIFIED_GENERATED khi không có URL.

Seed V3 dùng bootstrap database, không phải bản đồng bộ thị trường thời gian
thực. Sau import, database là nguồn sự thật; thay đổi title, description, price,
stock, status, image_url thực hiện trong DB, không sửa CSV hay build lại V3.
Nguồn mới phải đi qua crawl/clean/review/curated trước khi tạo release kế tiếp.

## Các điểm chưa xác minh và việc cần làm sau này

- Mười hai breed legacy chưa có URL authoritative trong breed_references.csv.
- Sáu PuppyListing không có evidence Chợ Tốt ở mức breed; tất cả vẫn là bootstrap
  từ curated price rule.
- Toàn bộ 18 Product không có source URL/evidence gắn trực tiếp; không được gán
  thương hiệu, thành phần, công dụng hay giá thị trường.
- Pet Mart có trong config/raw local nhưng không có Product V3 nào chứng minh
  nguồn trực tiếp.
- Khi bổ sung evidence, không sửa V3; review curated/provenance rồi tạo release
  mới.
