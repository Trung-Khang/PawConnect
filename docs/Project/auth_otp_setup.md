# PawConnect Auth OTP - Vận hành local và MySQL

## Phạm vi

Authentication mới cung cấp đăng ký, xác minh email OTP, đăng nhập bằng username hoặc email, refresh token, quên mật khẩu và đặt lại mật khẩu. Mọi email và thông báo dùng thương hiệu **PawConnect**.

Tài khoản seed/role có trước migration được giữ `email_verified = true`, do đó tiếp tục đăng nhập bằng email như cũ. Tài khoản đăng ký mới bắt buộc có username và phải xác minh email trước khi đăng nhập.

## API

Các endpoint có cả prefix tương thích `/api/auth` và prefix chuẩn `/api/v1/auth`:

- `POST /register`
- `POST /verify-email`
- `POST /resend-verification`
- `POST /login`
- `POST /refresh-token`
- `GET /me`
- `POST /logout`
- `POST /forgot-password`
- `POST /verify-reset-otp`
- `POST /reset-password`

Access token và refresh token chỉ dùng các key `pawconnect.accessToken` và `pawconnect.refreshToken`. Frontend không lưu password, OTP hoặc reset token vào `localStorage`.

## Migration MySQL 8

Trước lần chạy MySQL đầu tiên, kiểm tra phone hiện có để bảo đảm có thể thêm unique constraint mà không xóa dữ liệu:

```sql
USE pawconnect;
SELECT phone, COUNT(*) AS total
FROM users
WHERE phone IS NOT NULL AND phone <> ''
GROUP BY phone
HAVING COUNT(*) > 1;
```

Nếu không có dòng trùng, chạy **một lần** script [V4_0_0__pawconnect_auth_otp_mysql.sql](../../data-pipeline/migrations/V4_0_0__pawconnect_auth_otp_mysql.sql) bằng MySQL client. Script chỉ thêm cột/bảng/index; không xóa user, seed hoặc dữ liệu nghiệp vụ.

### Chat MySQL compatibility

Sau khi sao luu database, chay **một lần** [V4_1_0__rename_chat_message_read_column_mysql.sql](../../data-pipeline/migrations/V4_1_0__rename_chat_message_read_column_mysql.sql). Script nay xu ly ca ba trang thai: tao `chat_messages` neu database cu chua co bang, doi cot cu ``read`` thanh `is_read` de tranh tu khoa MySQL, hoac bo qua neu schema da dung. No khong xoa conversation hay message.

Sau khi chay, kiem tra:

```sql
USE pawconnect;
SHOW COLUMNS FROM chat_messages;
SELECT COUNT(*) AS messages FROM chat_messages;
```

Ten cot phai la `is_read`; frontend/API van nhan field JSON `read` nhu cu.

Khi migration được quản lý thủ công, chạy ứng dụng MySQL với `JPA_DDL_AUTO=validate` để phát hiện schema sai thay vì để Hibernate tự sửa schema. Không chạy lại script sau khi nó thành công.

## SMTP qua environment

Source chỉ đọc SMTP từ environment. Không đưa credential vào `application.yml`, JavaScript, Git hoặc log.

Các biến cần có trong process đang chạy PawConnect:

```powershell
$env:SMTP_HOST = "smtp.gmail.com"
$env:SMTP_PORT = "587"
$env:SMTP_USERNAME = "<email-gui-otp>"
$env:SMTP_PASSWORD = "<app-password>"
$env:SMTP_FROM = "<email-gui-otp>"
$env:SMTP_FROM_NAME = "PawConnect"
```

`mvn spring-boot:run` chạy embedded Tomcat và **không tự đọc** `C:\apache-tomcat-11.0.25\bin\setenv.bat`. Khi chạy bằng Maven, export các biến trên vào PowerShell hiện tại từ nguồn credential riêng của nhóm. Khi deploy Apache Tomcat ngoài, giữ biến trong `setenv.bat` và khởi động lại Tomcat sau khi thay đổi.

Nếu SMTP không cấu hình hoặc gửi lỗi, API không báo đã gửi email thành công; người dùng có thể gửi lại sau. Gửi timeout được thử lại tối đa một lần.

## Kiểm tra thủ công

1. Mở `/auth/register`; nhập username hợp lệ, email, phone Việt Nam và password đạt đủ checklist.
2. Kiểm tra email PawConnect; nhập OTP trong 5 phút. OTP cũ mất hiệu lực khi gửi lại, mỗi mã sai tối đa năm lần.
3. Đăng nhập tại `/auth/login` bằng username hoặc email; kiểm tra `/api/auth/me` trả đúng role.
4. Mở `/auth/forgot-password`, nhận OTP, đặt password mới khác password cũ và thử đăng nhập lại.
5. Kiểm tra reset token cũ không dùng lại được, logout xóa hai token ở frontend.

Không chụp/ghi log OTP, password, reset token hoặc biến SMTP khi nghiệm thu.
