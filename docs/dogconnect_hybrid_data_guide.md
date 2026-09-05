# Hướng Dẫn Xây Dựng Module Sinh Dữ Liệu Hybrid (Crawler + Faker) Cho DogConnect

Phương pháp Hybrid kết hợp việc **cào dữ liệu tĩnh** (để có hình ảnh, mô tả thực tế) và **sinh tự động dữ liệu động** (để tạo ra số lượng lớn bản ghi có cấu trúc hoàn hảo cho CSDL) là giải pháp tối ưu nhất để test hệ thống phần mềm nghiệp vụ. 

Tài liệu này hướng dẫn chi tiết quy trình xây dựng module data generator từ đầu đến cuối, được thiết kế để tích hợp mượt mà vào luồng phát triển backend Java Spring Boot.

---

## 1. Giai Đoạn 1: Chuẩn bị Môi trường & Nguồn Crawl

**Mục tiêu:** Thiết lập công cụ xử lý dữ liệu và xác định các website mục tiêu chứa thông tin các giống chó phổ biến (Poodle, Husky, Alaska, Corgi, Phú Quốc,...).

*   **Công cụ khuyến nghị:** 
    *   Môi trường: Python, Jupyter Notebook tích hợp trong Visual Studio Code.
    *   Thư viện: `requests`, `BeautifulSoup` (hoặc `Selenium` nếu web render bằng JS), `pandas` (để xử lý dữ liệu dạng bảng), `Faker` (để sinh dữ liệu giả).
*   **Gợi ý nguồn Crawl (Nguồn tham khảo):**
    *   *AZPet / Dogily / Sieupet:* Các trang web chuyên bán thú cưng này có kho hình ảnh chất lượng cao và mô tả giống chó rất chuẩn. Rất phù hợp để lấy dữ liệu cho bảng `Product` (Thương mại).
    *   *Hanoi Pet Adoption / Trạm cứu hộ động vật:* Phù hợp để lấy content mô tả hoàn cảnh, hình ảnh thực tế cho các bài đăng thuộc bảng `AdoptionPost` và `DogProfile`.
    *   *Chợ Tốt (Mục Thú cưng):* Nguồn dữ liệu phong phú về các dòng chó cỏ, chó lai, chó mông cộc, phù hợp để tạo tính đa dạng cho hệ thống.

**Điều kiện yêu cầu:**
*   Chỉ crawl các thông tin Public (Tên giống, Hình ảnh, Mô tả).
*   Tuân thủ `robots.txt` của các trang nguồn. Nên đặt `time.sleep()` giữa các request để tránh bị block IP.

**Kết quả nghiệm thu:** 
*   Một file `raw_breeds_data.csv` chứa 3 cột cơ bản: `Breed_Name`, `Description`, `Image_URL`.

---

## 2. Giai Đoạn 2: Xây dựng Script Crawler (Dữ liệu tĩnh)

**Mục tiêu:** Tự động hóa việc lấy dữ liệu tĩnh từ các nguồn đã chọn.

**Chi tiết các bước thực hiện:**
1.  Khởi tạo script Python, sử dụng `requests` để tải HTML của trang danh mục chó.
2.  Dùng `BeautifulSoup` bóc tách các thẻ chứa Tên giống chó và Link hình ảnh.
3.  *(Tùy chọn nâng cao)*: Tích hợp API của Cloudinary ngay trong script. Mỗi khi lấy được một `Image_URL`, script sẽ gọi lệnh upload lên Cloudinary của dự án và lấy URL trả về để lưu trữ. Điều này giúp hệ thống backend sau này không bị phụ thuộc vào link ảnh gốc (có thể bị die link).
4.  Lưu toàn bộ kết quả vào một Pandas DataFrame và xuất ra file CSV.

**Điều kiện yêu cầu:**
*   Dữ liệu crawl về phải được làm sạch cơ bản (strip khoảng trắng, loại bỏ emoji rác).
*   Tối thiểu thu thập được 15-20 giống chó phổ biến tại Việt Nam.

**Kết quả nghiệm thu:**
*   Script `crawler.py` chạy ổn định.
*   Link ảnh phải là link sống (ưu tiên link Cloudinary trả về).

---

## 3. Giai Đoạn 3: Sinh Dữ liệu Động Bằng Pandas (Mock Data)

**Mục tiêu:** Trộn dữ liệu tĩnh vừa crawl với các thuộc tính ngẫu nhiên nhưng có quy luật để map chính xác với cấu trúc thiết kế CSDL của DogConnect.

**Chi tiết các bước thực hiện:**
1.  Load file `raw_breeds_data.csv` vào Pandas DataFrame.
2.  Khởi tạo các hàm sinh ngẫu nhiên có trọng số. Ví dụ:
    *   `price`: Poodle (từ 4-8 triệu), Corgi (từ 10-20 triệu), Chó cỏ (từ 200k - 500k).
    *   `gender`: Random 50% Đực, 50% Cái.
    *   `age`: Random từ 2 - 12 tháng tuổi.
    *   `branch_id`: Random phân bổ đều cho 3 chi nhánh cửa hàng (ID 1, 2, 3).
    *   `isBreedingDog`: Boolean (True/False).
    *   `vaccinationStatus`: "Đã tiêm 1 mũi", "Đã tiêm 2 mũi".
3.  Tạo DataFrame mới cho bảng **Product** (Chó thương mại):
    *   Cột: `name`, `description`, `price`, `stock` (random 1-5), `imageUrl`, `suitableSize`, `isBreedingDog` (True), `categoryId`, `branchId`.
4.  Tạo DataFrame mới cho bảng **DogProfile** (Chó chờ nhận nuôi):
    *   Cột: `name` (Dùng Faker sinh tên thú cưng như Lu, Milu, Kiki), `breed`, `size`, `age`, `weight`, `gender`, `vaccinationStatus`, `imageUrl`, `description`.

**Điều kiện yêu cầu:**
*   Các khóa ngoại (`branchId`, `categoryId`) phải khớp với dữ liệu gốc của CSDL (ví dụ CategoryID của "Chó cảnh" là 1).
*   Logic giá tiền và cân nặng phải hợp lý (không thể có con Chihuahua nặng 40kg).

**Kết quả nghiệm thu:**
*   File Jupyter Notebook `data_generation.ipynb` hiển thị rõ các bước Data Cleaning & Transformation.
*   Hai file đầu ra: `mock_products.csv` và `mock_dog_profiles.csv` hoàn toàn khớp với schema 13 thực thể của hệ thống.

---

## 4. Giai Đoạn 4: Xuất Script SQL & Bàn Giao Tích Hợp

**Mục tiêu:** Chuyển đổi DataFrame thành các câu lệnh SQL để dễ dàng import vào database MySQL/PostgreSQL của dự án Spring Boot.

**Chi tiết các bước thực hiện:**
1.  Viết hàm Python duyệt qua từng dòng của DataFrame (`iterrows`).
2.  Format chuỗi string thành định dạng SQL:
    ```sql
    INSERT INTO product (name, description, price, stock, image_url, suitable_size, is_breeding_dog, category_id, branch_id) 
    VALUES ('Poodle Thuần Chủng', '...', 5000000, 2, 'https://res.cloudinary.com/...', 'SMALL', true, 1, 1);
    ```
3.  Đảm bảo xử lý ký tự nháy đơn (`'`) trong phần `description` (escape character) để tránh lỗi cú pháp SQL.
4.  Ghi tất cả vào một file `data.sql`.

**Kết quả nghiệm thu & Bàn giao:**
1.  **Gói Source Code:** Bao gồm script Python (Crawler + Sinh Data). Cấu trúc gọn gàng, có comment giải thích rõ ràng. Có thể đẩy thẳng lên một thư mục `data-scripts/` trên repository GitHub của nhóm để mọi người cùng review.
2.  **File `data.sql`:** File này sẽ được bàn giao để copy vào thư mục `src/main/resources` của dự án Spring Boot. Cấu hình thuộc tính `spring.sql.init.mode=always` trong `application.properties` để tự động nạp dữ liệu mỗi khi Tomcat server khởi động lại.
3.  Hệ thống chạy mượt mà, phân trang hiển thị đầy đủ chó giống và chó nhận nuôi trên giao diện Thymeleaf/Bootstrap, sẵn sàng cho việc quay video demo báo cáo.
