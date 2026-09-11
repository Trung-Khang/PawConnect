# Bàn Giao Dữ Liệu PawConnect

## 1. Mục đích và phạm vi

data-pipeline/data/seed/v3/ là bộ bootstrap duy nhất để khởi tạo một database
PawConnect chung cho TV1, TV2 và TV3. CSV được importer đọc rồi ghi vào database;
sau import, website đọc dữ liệu vận hành từ database. V3 là release bất biến,
không phải dữ liệu giao dịch thực tế.

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

TV1 phụ trách database chung và module thương mại.

**File CSV sử dụng**

- reference/branches.csv: tạo dữ liệu Branch.
- reference/categories.csv: tạo Category cho sản phẩm.
- reference/service_types.csv: tạo loại dịch vụ.
- catalog/breeds.csv: tạo Breed để PuppyListing tham chiếu.
- commerce/products.csv: tạo Product FOOD/ACCESSORY.
- commerce/puppy_listings.csv: tạo PuppyListing bán chó theo giống, giá và stock.

**Công việc**

1. Đối chiếu CSV với Entity/schema hiện tại.
2. Tạo hoặc cập nhật bảng Branch, Category, ServiceType, Breed, Product và PuppyListing.
3. Viết importer đọc Seed V3 và map stable code/seed_key sang database ID.
4. Dùng Product cho chức năng xem, thêm, sửa, xóa sản phẩm.
5. Dùng PuppyListing cho chức năng bán puppy theo giống và chi nhánh.
6. Khi bán thành công, giảm stock; stock bằng 0 thì chuyển SOLD_OUT.
7. Phân quyền Branch Manager quản lý chi nhánh của mình, Admin quản lý toàn hệ thống.

name, listing_title và description chỉ là nội dung khởi tạo. Sau import,
Admin/Branch Manager sửa title, description, price, stock, status và image trong
database; website đọc từ database, không sửa CSV hoặc build lại Seed V3. Giữ ổn
định seed_key và breed_code; muốn đổi breed thì nên tạo listing mới và đóng
listing cũ.

**Phân công hình ảnh**

TV1 phụ trách chuẩn bị dữ liệu hình ảnh cho Product, PuppyListing, DogProfile,
AdoptionPost và User profile khi cần. TV1 dùng thêm commerce/products.csv,
commerce/puppy_listings.csv, adoption/dog_profiles.csv, adoption/adoption_posts.csv
và fixtures/users.csv nếu có trường avatar/image.

- Upload ảnh lên Cloudinary theo thư mục và naming convention thống nhất.
- Ghi secure URL và public ID vào database sau khi importer tạo record.
- Đảm bảo mỗi bản ghi cần hiển thị có ảnh phù hợp hoặc ảnh mặc định đã thống nhất.
- Không đưa file ảnh nhị phân, Cloudinary secret, token hoặc API key vào CSV/Git.
- Không sửa image_url trong Seed V3 để cập nhật ảnh sau khi website chạy.

Ảnh phục vụ trực tiếp cho toàn bộ giao diện website, không chỉ PuppyListing.

**Cách báo cáo lại nhóm**

- File CSV đã sử dụng.
- Entity/bảng đã tạo hoặc đối chiếu.
- Mapping đã thực hiện.
- Phần chưa khớp với dataset.
- File code đã sửa.
- Chức năng đã hoàn thành và chức năng còn thiếu.

## 6. Bàn giao cho TV2

TV2 phụ trách catalog chó và module nhận nuôi.

**File CSV sử dụng**

- catalog/breeds.csv: danh mục giống chó.
- adoption/dog_profiles.csv: hồ sơ từng chú chó.
- adoption/adoption_posts.csv: bài đăng nhận nuôi.
- adoption/adoption_applications.csv: đơn đăng ký nhận nuôi.
- fixtures/users.csv và reference/branches.csv: dùng khóa để liên kết User/Branch.

**Công việc**

1. Tạo hoặc đối chiếu Entity DogProfile, AdoptionPost và AdoptionApplication.
2. Map breed_code sang Breed ID.
3. Map created_by_user_key và applicant_user_key sang User ID.
4. Map dog_profile_seed_key và adoption_post_seed_key sang đúng bản ghi liên quan.
5. Làm chức năng xem/tìm kiếm hồ sơ chó và bài nhận nuôi.
6. Làm chức năng tạo, sửa, đóng bài nhận nuôi.
7. Làm chức năng gửi, duyệt và từ chối đơn nhận nuôi.
8. Khi một đơn được APPROVED, đóng bài và xử lý các đơn còn lại theo contract.
9. Không dùng PuppyListing thương mại thay cho DogProfile nhận nuôi.

**Cách báo cáo lại nhóm**

- File CSV đã sử dụng.
- Entity/bảng đã tạo hoặc đối chiếu.
- Mapping đã thực hiện.
- Phần chưa khớp với dataset.
- File code đã sửa.
- Chức năng đã hoàn thành và chức năng còn thiếu.

## 7. Bàn giao cho TV3

TV3 phụ trách User, Role, bảo mật và Cloudinary.

**File CSV sử dụng**

- reference/roles.csv: tạo Role.
- reference/branches.csv: liên kết Branch Manager với Branch.
- fixtures/users.csv: tạo các User bootstrap.
- Các cột image_url đang trống trong DogProfile, Product và PuppyListing.

**Công việc**

1. Tạo hoặc đối chiếu User, Role và quan hệ User–Branch.
2. Map role_code và branch_code sang database ID.
3. Tạo và hash credential tại runtime; không lấy password từ CSV.
4. Cung cấp User ID mapping để TV1 và TV2 liên kết dữ liệu.
5. Tích hợp Cloudinary cho ảnh User, DogProfile, AdoptionPost, Product và PuppyListing.
6. Khi upload ảnh, lưu secure URL và public ID vào database.
7. Khi thay hoặc xóa ảnh, cập nhật Cloudinary và database, không sửa Seed V3.
8. Không đưa Cloudinary secret, token hoặc API key vào CSV/Git.

**Phân công kỹ thuật Cloudinary**

TV3 xây dựng service/configuration Cloudinary, đọc credential từ biến môi trường
hoặc cấu hình local không commit, cung cấp chức năng upload/thay thế/xóa ảnh và
trả secure URL cùng public ID cho TV1 lưu vào database. TV3 phân quyền upload
theo User/Admin/Branch Manager và hỗ trợ TV1 với ảnh Product, PuppyListing,
DogProfile, AdoptionPost và User profile.

TV1 quản lý nội dung và upload ảnh của dataset/business records; TV3 xây dựng
service/API Cloudinary và bảo mật tích hợp. Database chỉ lưu metadata ảnh như
secure_url và public_id; Cloudinary chỉ lưu file ảnh. Seed V3 giữ image_url trống,
ảnh thật được bổ sung sau khi import database. Không lưu file ảnh trong database,
không tạo URL giả và không ghi secret Cloudinary vào CSV/Git.

**Cách báo cáo lại nhóm**

- File CSV đã sử dụng.
- Entity/bảng đã tạo hoặc đối chiếu.
- Mapping đã thực hiện.
- Phần chưa khớp với dataset.
- File code đã sửa.
- Chức năng đã hoàn thành và chức năng còn thiếu.

## 8. Vận hành sau import

Seed V3 là bootstrap bất biến; database là nguồn dữ liệu vận hành; Website/Admin
UI là nơi dữ liệu được cập nhật. Seed không đồng bộ hai chiều với database.
Không sửa CSV V3 sau phát hành; muốn đổi bootstrap phải tạo release kế tiếp.
