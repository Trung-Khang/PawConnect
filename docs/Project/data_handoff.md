# Bàn Giao Dữ Liệu PawConnect

## 1. Mục đích và phạm vi

data-pipeline/data/seed/v3/ là bộ dữ liệu bootstrap duy nhất để khởi tạo một database PawConnect dùng chung cho TV1, TV2 và TV3. Mỗi thành viên phụ trách nhóm bảng riêng nhưng dùng cùng stable code, seed_key và database ID sau khi importer map dữ liệu. Không tạo database riêng hoặc seed riêng cho từng module.

CSV không phải database chạy trực tiếp: importer đọc CSV, kiểm tra manifest rồi ghi vào database. Sau import, website đọc dữ liệu đang vận hành từ database. V3 là release bất biến và chỉ là dữ liệu khởi tạo có kiểm soát, không phải dữ liệu giao dịch thực tế hay dữ liệu người dùng phát sinh.

## 2. Cây thư mục V3 và số lượng bản ghi

    data-pipeline/data/seed/v3/
    ├─ manifest.json
    ├─ provenance.md
    ├─ reference/ roles.csv (3), branches.csv (3), categories.csv (4), service_types.csv (3)
    ├─ catalog/ breeds.csv (15), breed_references.csv (16)
    ├─ fixtures/users.csv (6)
    ├─ adoption/ dog_profiles.csv (30), adoption_posts.csv (30), adoption_applications.csv (36)
    ├─ commerce/ puppy_listings.csv (14), products.csv (18)
    └─ reports/validation.md

## 3. Danh mục file

| File | Nội dung, khóa ổn định và phụ thuộc | Thành viên sử dụng | DB |
| --- | --- | --- | --- |
| reference/roles.csv | Role CUSTOMER, BRANCH_MANAGER, ADMIN; role_code ổn định. User tham chiếu Role. | TV3 | Import Role. |
| reference/branches.csv | Ba chi nhánh; branch_code lookup sang Branch ID. | TV1, TV2, TV3 | Import Branch; commerce/service, nhận nuôi, Branch Manager cùng dùng. |
| reference/categories.csv | Category; category_code lookup sang Category ID. | TV1 | Import Category cho Product/PuppyListing. |
| reference/service_types.csv | Danh mục ServiceType, không tự tạo booking giao dịch. | TV1 | Import ServiceType/Booking reference. |
| catalog/breeds.csv | Catalog 15 giống, adult size/weight, maturity rule; breed_code không đổi. Có BREED_SAMOYED và BREED_PHU_QUOC. | TV1, TV2 | Import/lookup Breed. BREED_PHU_QUOC không thuộc 14 PuppyListing hiện tại. |
| catalog/breed_references.csv | Provenance/reference của Breed, phụ thuộc breeds.csv. | TV2, reviewer | Chỉ audit; không bắt buộc là bảng nghiệp vụ, không dùng nội dung website nguồn làm UI. |
| fixtures/users.csv | Sáu User bootstrap; seed_key, Role, Branch. credential_mode=RUNTIME_ENV; không password/hash cố định, token, secret. | TV3, TV2, TV1 | Import User. Credential tạo/hash runtime từ cấu hình local không commit. |
| adoption/dog_profiles.csv | Cá thể chó nhận nuôi: tên, giống, tuổi, cân nặng, giới tính, vaccine, ảnh, mô tả. | TV2 | Import DogProfile. image_url rỗng; seed_key là khóa Post. Không phải puppy thương mại. |
| adoption/adoption_posts.csv | Bài nhận nuôi, title/description/health_note, AVAILABLE/CLOSED. | TV2 | Import AdoptionPost; phụ thuộc DogProfile/User. |
| adoption/adoption_applications.csv | Đơn PENDING/APPROVED/REJECTED. CLOSED có tối đa một APPROVED và không còn PENDING. | TV2 | Import AdoptionApplication; phụ thuộc Post/User. |
| commerce/puppy_listings.csv | Lô puppy theo giống/chi nhánh, không phải DogProfile. stock 1–20; price_per_puppy_vnd là giá một bé. | TV1 | Import PuppyListing riêng; map Breed/Branch/Category, không có BREED_PHU_QUOC. |
| commerce/products.csv | Chỉ FOOD và ACCESSORY, không PUPPY; name/description là nội dung khởi tạo. | TV1 | Import Product; map Category/Branch. |
| manifest.json | Release, checksum file/header, count, stable key, dependency. | Mọi thành viên | Chỉ kiểm tra; importer từ chối nếu checksum/header/count sai. |
| provenance.md | Nguồn và cách tạo release. | Reviewer | Không import, không hiển thị UI. |
| reports/validation.md | Nghiệm thu đóng gói cùng release. | Reviewer | Không import. |

## 4. Thứ tự import chung

1. Role từ reference/roles.csv: role_code sang Role ID.
2. Branch từ reference/branches.csv: branch_code sang Branch ID.
3. Category từ reference/categories.csv: category_code sang Category ID.
4. ServiceType từ reference/service_types.csv: service_type_code sang ServiceType ID.
5. Breed từ catalog/breeds.csv: breed_code sang Breed ID.
6. User từ fixtures/users.csv: map Role/Branch, lưu seed_key sang User ID.
7. DogProfile: map Breed/Branch, lưu seed_key sang DogProfile ID.
8. AdoptionPost: map DogProfile/User key, lưu seed_key sang AdoptionPost ID.
9. AdoptionApplication: map Post/User key.
10. Product: map Category/Branch.
11. PuppyListing: map Breed/Category/Branch.

Importer cần import registry hoặc unique key để idempotent, không dùng DB ID tự tăng từ CSV và không tự delete/prune database.

## 5. Bàn giao cho TV1

TV1 nhận Branch, Category, ServiceType, Breed, products.csv, puppy_listings.csv và manifest.json. Cần đối chiếu các bảng Branch, Category, ServiceType, Breed, Product và PuppyListing. PuppyListing là entity riêng, không gộp Product hoặc DogProfile; Product chỉ FOOD/ACCESSORY.

PuppyListing bán theo giống/số lượng. stock là số puppy còn lại; price_per_puppy_vnd là giá mỗi bé. Bán thành công phải giảm stock bằng transaction an toàn, không cho stock âm; stock bằng 0 thì status thành SOLD_OUT. Branch Manager chỉ sửa dữ liệu thuộc chi nhánh được phân quyền; Admin quản lý toàn hệ thống.

name trong products.csv, cùng listing_title và description trong puppy_listings.csv, chỉ là nội dung khởi tạo ban đầu. Sau import, Branch Manager/Admin sửa title/mô tả qua giao diện; dữ liệu mới lưu trong database và website hiển thị dữ liệu hiện tại từ database. Không cần sửa CSV hay build lại Seed V3. Seed không tự chạy lại để ghi đè nội dung đã sửa.

| Nhóm | Field |
| --- | --- |
| PuppyListing ổn định | seed_key; breed_code sau khi listing đã có lịch sử |
| PuppyListing có thể sửa | listing_title, description, price_per_puppy_vnd, stock, status, image_url, health_status, care_instructions |
| Product ổn định | seed_key; product_kind sau khi có giao dịch |
| Product có thể sửa | name, description, price, stock, image_url, suitable_size, health_status, care_instructions |

Nếu đổi breed của listing, ưu tiên tạo listing mới và đóng listing cũ để giữ lịch sử. Không viết CommandLineRunner hoặc startup hook luôn upsert field có thể sửa khi Spring Boot khởi động. Chỉ reset/reseed khi người vận hành gọi rõ ràng trong môi trường được phép.

## 6. Bàn giao cho TV2

TV2 nhận breeds.csv, ba CSV adoption, branches.csv, users.csv để hiểu khóa tham chiếu và manifest.json. DogProfile map Breed/Branch; Post map DogProfile/User; Application map Post/User. Không dùng PuppyListing làm DogProfile.

AVAILABLE không có APPROVED. CLOSED có tối đa một APPROVED và không còn PENDING. Quản trị viên có thể sửa nội dung bài nhận nuôi trong database; không chạy lại seed để cập nhật bài. Khi website phát sinh dữ liệu mới, database là nguồn sự thật vận hành.

## 7. Bàn giao cho TV3

TV3 nhận roles.csv, branches.csv, users.csv, các image_url rỗng và manifest.json. TV3 map User–Role, map BRANCH_MANAGER–Branch, và cung cấp User ID mapping cho importer chung/TV1/TV2 khi cần.

Credential phải được tạo/hash runtime từ cấu hình local không commit. Không import credential placeholder. Cloudinary tích hợp theo schema nhóm duyệt; không ghi secret vào CSV/Git, không tạo URL giả. Khi upload ảnh thật, lưu secure URL/public ID vào database, không sửa Seed V3.

## 8. Vận hành sau import

- Seed V3: bootstrap bất biến.
- Database: nguồn dữ liệu đang vận hành.
- Website/Admin UI: nơi người dùng được phân quyền cập nhật dữ liệu.

Seed không đồng bộ hai chiều với database; sửa DB không đổi CSV. Không sửa CSV V3 sau phát hành. Muốn đổi bootstrap cho môi trường mới phải tạo release kế tiếp. Importer không ghi đè field có thể sửa của record đã tồn tại; reset có chủ đích cần backup hoặc transaction.

## 9. Checklist xác nhận

| TV1 | TV2 | TV3 |
| --- | --- | --- |
| Schema/enum Product và PuppyListing tương thích | Schema/enum adoption tương thích | Schema User/Role/Branch tương thích |
| Mapping code/key và thứ tự import đúng | FK/status/count đúng | Credential runtime, không secret |
| Import idempotent, không overwrite DB | Không lẫn commerce/adoption | Cloudinary cập nhật DB, không sửa seed |
| Ghi điểm chưa khớp trước khi đổi contract | Ghi điểm chưa khớp trước khi đổi contract | Ghi điểm chưa khớp trước khi đổi contract |
