# PawConnect Data Pipeline

Thu muc nay chua du lieu tham chieu chung cho cac moi truong `dev`, `test` va `demo` cua PawConnect.

Bo seed hien tai chi gom cac file CSV curated cho Role, Branch, Category va ServiceType. Cac file nay khong dung ID tu tang cua database; module import sau nay phai map bang stable code nhu `role_code`, `branch_code`, `category_code` va `service_type_code`.

Chu so huu du lieu:

- Role do TV3 xac nhan va quan ly.
- Branch, Category va ServiceType do TV1 xac nhan va quan ly.
- TV2 chi duy tri pipeline, dinh dang ban giao va kiem tra chat luong du lieu.

Khong import du lieu nay vao production khi chua co migration hoac quy trinh import duoc review. Seed CSV chi duoc dung cho phat trien, kiem thu va demo.
