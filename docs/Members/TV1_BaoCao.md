# PawConnect - TV1 Báo cáo nhận bàn giao dữ liệu

> Đây là file hướng dẫn nhận bàn giao và mẫu để TV1 cập nhật tiến độ. Chưa ghi nhận API/chức năng TV1 đã hoàn thành nếu chưa có code và test.

## 1. Phạm vi TV1

TV1 phụ trách các entity và luồng nghiệp vụ: `Branch`, `Category`, `Product`, `Order`, `OrderItem`, `ServiceType`, `ServiceBooking`.

## 2. Dữ liệu TV2/Data Engineer đã chuẩn bị cho TV1

| File | Dữ liệu | Người sở hữu nghiệp vụ | Người sử dụng | Dùng hiện tại | Bàn giao gần tới |
| --- | --- | --- | --- | --- | --- |
| `data-pipeline/data/curated/reference/branches.csv` | 3 chi nhánh: `BR_HCM_01`, `BR_HN_01`, `BR_DN_01` | TV1 | TV1, TV2, TV3 | Tham chiếu chi nhánh dev/test/demo | TV1 xác nhận tên, địa chỉ cấp quận/thành phố và stable code |
| `data-pipeline/data/curated/reference/categories.csv` | 4 category cho Product | TV1 | TV1 | Mapping Product bằng `category_code` | TV1 xác nhận danh mục chính thức |
| `data-pipeline/data/curated/reference/service_types.csv` | 3 dịch vụ Spa/Khám bệnh/Tiêm phòng | TV1 | TV1 | Mapping ServiceBooking bằng `service_type_code` | TV1 xác nhận thời lượng và giá VND |
| `data-pipeline/data/curated/catalog/breeds.csv` | 12 giống chó, size, tuổi và cân nặng hợp lý | TV2 duy trì | TV1, TV2 | Sinh dữ liệu chó giống/Product hợp lý | TV1 phản hồi nếu Product cần thêm rule |
| `docs/Project/data_contract.md` | Quy ước UTF-8, snake_case, stable code, enum, VND | Cả nhóm | Cả nhóm | Nguồn thống nhất khi code/import | TV1 báo conflict nếu entity không khớp |

## 3. TV1 cần kiểm tra và xác nhận

- Xác nhận `BR_HCM_01`, `BR_HN_01`, `BR_DN_01`.
- Xác nhận bốn category code: `CAT_BREEDING_DOG`, `CAT_FOOD`, `CAT_ACCESSORY`, `CAT_MEDICAL_SUPPLY`.
- Xác nhận ba service type code, thời lượng và giá: `SERVICE_SPA` 60 phút 150000 VND, `SERVICE_CHECKUP` 45 phút 200000 VND, `SERVICE_VACCINATION` 30 phút 250000 VND.
- Xác nhận Product contract: `seed_key,name,description,price,stock,image_url,suitable_size,is_breeding_dog,category_code,branch_code`.
- Không hard-code ID database; importer phải map bằng stable code.
- Không tự đổi stable code mà không báo TV2/Data Engineer.

## 4. Dữ liệu TV1 có thể dùng ngay

TV1 có thể dùng reference seed và breed catalog để mock màn hình, unit test mapping hoặc chuẩn bị Product CSV sau này. Dữ liệu hiện chỉ dành cho `dev`, `test`, `demo`.

## 5. Dữ liệu chưa được phép import production

Chưa được import production vì chưa có migration/import DB được review. URL ảnh trong dữ liệu adoption hiện là placeholder, chưa phải Cloudinary hay ảnh crawl chính thức.

## 6. Khi contract không khớp entity

Nếu entity/DTO/schema của TV1 không khớp data contract, TV1 ghi rõ conflict, file liên quan, đề xuất mapping hoặc thay đổi cần nhóm xác nhận. Không tự đổi schema/enum/stable code của module khác.

## 7. Tiêu chí báo cáo cho mỗi chức năng

Mỗi lần làm chức năng, TV1 cần ghi: file đã sửa, API/chức năng, dữ liệu đầu vào, lệnh test, kết quả test, đầu ra bàn giao, blocker hoặc contract conflict. Chỉ đánh dấu `COMPLETED` khi có code và test.

## 8. Mẫu progress log

| Ngày | Giai đoạn | Chức năng/API | File đã sửa | Dữ liệu đầu vào | Test | Kết quả | Bàn giao | Blocker |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  | NOT STARTED |  |  |

Trạng thái dùng chung: `NOT STARTED`, `IN PROGRESS`, `BLOCKED`, `READY FOR REVIEW`, `COMPLETED`.
