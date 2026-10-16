# Token contract giao diện

**Cập nhật:** 16/10/2026
**Áp dụng cho:** Shop admin, Adoption và các giao diện tiếp theo.

## API và thời hạn

| Nội dung | Giá trị |
| --- | --- |
| Đăng nhập | `POST /api/auth/login` |
| Làm mới | `POST /api/auth/refresh-token` với `{ "refreshToken": "..." }` |
| Người dùng hiện tại | `GET /api/auth/me` với `Authorization: Bearer <accessToken>` |
| Access token | JWT claim `token_type=access`, TTL mặc định `PT15M` |
| Refresh token | JWT claim `token_type=refresh`, TTL mặc định `P14D` |

## Quy ước lưu phía trình duyệt

- Chỉ dùng `localStorage["pawconnect.accessToken"]` và `localStorage["pawconnect.refreshToken"]`.
- Không dùng `token`, `accessToken`, `refreshToken` hay `sessionStorage` cho code mới.
- Adoption tự migrate một lần từ các key cũ sang hai key chuẩn, rồi xóa key cũ.
- Không ghi token vào HTML, source, log hoặc Console.

## Quy tắc request

1. Request protected gửi `Authorization: Bearer <accessToken>`.
2. Nếu nhận `401`, client thử refresh **một lần**.
3. Refresh thành công: lưu cặp token mới bằng key chuẩn và retry request ban đầu đúng một lần.
4. Refresh lỗi, token sai hoặc retry vẫn `401`: xóa cả access/refresh token và key legacy; không lặp request.
5. Trong Giai đoạn 7A chưa có giao diện `/login`; trang Adoption protected trở về `/adoptions?session=expired`, là trang public an toàn. Giai đoạn 7B phải thay đích này bằng login UI có `returnTo`.

## Bảo mật cấu hình

- Secret chỉ lấy qua biến môi trường hoặc file local bị `.gitignore`.
- `.env.example` chỉ chứa biến trống; `application.yml`/`application-mysql.yml` dùng placeholder môi trường.
- Quét tracked files và Git history ngày 16/10/2026 không tìm thấy Cloudinary, MySQL hoặc JWT secret literal.
