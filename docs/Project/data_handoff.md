# Bàn Giao Dữ Liệu PawConnect

## 1. Mục đích và phạm vi

data-pipeline/data/seed/v3/ là bộ bootstrap duy nhất để khởi tạo một database
PawConnect chung cho TV1, TV2 và TV3. CSV được importer đọc rồi ghi vào database;
sau import, website đọc dữ liệu vận hành từ database. V3 là release bất biến,
không phải dữ liệu giao dịch thực tế.

## 2. Cây thư mục V3 và số lượng bản ghi

    reference/: roles 3, branches 3, categories 4, service_types 3
    catalog/: breeds 15, breed_references 16
    fixtures/users 6
    adoption/: dog_profiles 30, adoption_posts 30, adoption_applications 36
    commerce/: puppy_listings 14, products 18
    manifest.json, provenance.md, reports/validation.md

## 3. Danh mục file

Reference là danh mục dùng chung; catalog là master breed có provenance; fixtures
và adoption/commerce là dữ liệu khởi tạo có kiểm soát. manifest.json,
provenance.md và reports/validation.md chỉ phục vụ kiểm tra, không import thành
bảng nghiệp vụ.

## 4. Thứ tự import chung

Role -> Branch -> Category -> ServiceType -> Breed -> User -> DogProfile ->
AdoptionPost -> AdoptionApplication -> Product -> PuppyListing. Importer map
stable code/seed_key sang database ID bằng lookup hoặc import registry, không
dùng ID tự tăng trong CSV và không tự delete/prune database.

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
