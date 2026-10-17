# Prompt Dieu Phoi Hoan Thien PawConnect

## Vai tro va muc tieu

Ban la ky su truong dieu phoi viec hoan thien PawConnect. Muc tieu la dua he thong tu trang thai H2 da qua test sang trang thai co the nghiem thu an toan tren MySQL Railway, sau do chuan bi deploy backend that.

Khong tu mo rong tinh nang, khong reseed database chung, khong sua `SecurityConfig` de che loi UI, khong commit secret. Moi thay doi phai co pham vi nho, test phu hop va bao cao bang evidence.

## Hien trang da xac nhan

- `mvn clean test` tai HEAD: PASS 61 test, 0 failure, 0 error, 1 skip. Day la baseline H2, khong phai MySQL acceptance.
- Seed V3 co debt da chap nhan: `products.csv` co 20 record, bao gom 2 `DOG`; `python data-pipeline/src/build_seed_release.py --verify v3` dang FAIL `count failure` do manifest/validator/tai lieu cu. Khong rollback, khong reseed, khong tao Seed V4 de sua debt nay trong dot hoan thien hien tai.
- Database `pawconnect` da duoc import len Railway. Day la database dung chung; migration hay thao tac ghi chi duoc chay co kiem soat.
- Blocker MySQL hien tai: `ChatMessage` map field `read` thanh cot `read`; day la tu khoa MySQL. Source chua co migration sua cot nay.
- Auth OTP/reset, profile/avatar, Shop, Booking, Order, Adoption, quan tri user/product/order va backend Chat/WebSocket da co source. Phan con thieu la nghiem thu lien module tren MySQL va UI chat/realtime hoan chinh.

## Bat buoc truoc khi bat dau

1. Tao nhanh gan day tu `main`; khong lam tren nhanh co thay doi khong lien quan.
2. Chay va ghi lai baseline:

   ```powershell
   mvn clean test
   git diff --check
   git status --short
   ```

3. Khong commit cac file huong dan local co credential. Neu phat hien secret tung duoc ghi trong tai lieu, ngung chia se file do, thu hoi/rotate secret tren dich vu tuong ung va thay bang placeholder.
4. Dung Railway qua private network khi backend da deploy. Khi chay backend local, tunnel cua tung may phai duoc mo trong mot terminal rieng; khong hard-code host, port hay password tunnel vao source.
5. Truyen password, JWT secret, SMTP password va Cloudinary secret qua environment cua Railway hoac kenh rieng, khong qua Git, issue, screenshot hay markdown.

## Giai doan A - Mo khoa MySQL an toan

### A1. Sua mapping Chat

Nguoi phu trach: TV3. Reviewer: TV2.

1. Doi ten cot database cua boolean `read` thanh mot ten khong trung tu khoa, vi du `is_read`. Giu ten field Java va JSON contract `read` neu frontend dang phu thuoc vao no.
2. Tao migration idempotent cho MySQL de:
   - xu ly database moi;
   - doi ten cot neu `chat_messages.read` da ton tai;
   - tao cot/chi muc can thiet ma khong lam mat lich su chat;
   - khong dong cham cac bang khong lien quan.
3. Khong chi dua vao `ddl-auto=update` de sua schema production. Migration phai duoc review truoc khi chay tren Railway.
4. Viet test persistence hoac migration acceptance cho ChatMessage tren MySQL. Test phai bao phu tao conversation, gui message, doc message va lay history.

### A2. Chay migration tren Railway

Nguoi phu trach: TV3. Nguoi xac nhan: TV1 hoac TV2.

1. Sao luu schema/data Railway truoc khi chay.
2. Xac nhan dang dung database `pawconnect`, khong phai database mac dinh cua provider.
3. Chay migration mot lan duy nhat; ghi timestamp, commit SHA, nguoi chay va ket qua SQL vao bao cao.
4. Kiem tra sau migration:

   ```sql
   SHOW TABLES FROM pawconnect;
   SHOW COLUMNS FROM pawconnect.chat_messages;
   SELECT COUNT(*) AS users FROM pawconnect.users;
   SELECT COUNT(*) AS conversations FROM pawconnect.conversations;
   SELECT COUNT(*) AS messages FROM pawconnect.chat_messages;
   ```

5. Neu migration fail: dung lai, khong sua truc tiep bang tay tren production. Ghi log loi, rollback theo ke hoach da review va mo PR sua migration.

### A3. Nghiem thu backend MySQL

1. Mo Railway tunnel trong terminal A.
2. Trong terminal B, cau hinh environment MySQL theo host/port tunnel cua chinh may do; khong dung port da copy tu may khac.
3. Chay backend voi profile `mysql` va kiem tra startup khong co Hibernate DDL error.
4. Chay MySQL integration voi `RUN_MYSQL_INTEGRATION_TESTS=true` theo huong dan test hien co.
5. Chi khi backend startup va MySQL integration PASS moi duoc danh dau Giai doan A xong.

## Giai doan B - Auth, cau hinh va bao mat

Nguoi phu trach: TV3. Ho tro UI: TV1. Regression API: TV2.

1. Dat cac bien `JWT_SECRET`, SMTP va Cloudinary tren Railway; production khong duoc su dung JWT development fallback.
2. Thiet lap production voi migration duoc kiem soat va schema validation; khong de `ddl-auto=update` tu tao schema len production lau dai.
3. Manual E2E bat buoc:
   - register;
   - nhan va verify OTP;
   - login bang username va email;
   - refresh token, logout;
   - forgot password, verify reset OTP, dat password moi, login lai;
   - cap nhat profile, so dien thoai Viet Nam hop le, upload/thay/xoa avatar.
4. Regression bao mat:
   - OTP het han, sai qua gioi han, cooldown va OTP cu vo hieu;
   - reset token het han/da dung lai;
   - khong doi ve password cu;
   - forgot-password khong tiet lo email co ton tai;
   - anonymous/CUSTOMER khong duoc quan ly order, user hay ghi Product;
   - BRANCH_MANAGER khong duoc quan tri user hoac truy cap du lieu khac branch.
5. Dung account demo duoc phan phoi qua kenh rieng. Khong dat credential vao test output, document hay commit.

### Trang thai thuc hien 17/10/2026

- Source: da co profile `production` buoc `JWT_SECRET`, SMTP, Cloudinary va WebSocket origin; profile nay dat `ddl-auto=validate`, tat H2 console va tat seed user. Gia tri secret khong nam trong source.
- Regression them: quyen quan ly don hang cua anonymous/CUSTOMER va BRANCH_MANAGER khac branch, quyen reset khong dung lai password cu, va response forgot-password khong tiet lo identity ton tai. `mvn clean test` PASS: 65 tests, 0 failure, 0 error, 1 MySQL acceptance test skip theo dieu kien moi truong.
- Railway: project hien chi co dich vu MySQL. Chua co backend service de dat cac bien production, do do SMTP/Cloudinary that va manual browser E2E van chua duoc danh dau PASS.
- Handoff TV3: tao/chon backend Railway service, dat bien theo `docs/Project/production_configuration.md`, deploy, sau do chay toan bo manual E2E cua Giai doan B voi account demo duoc chia se qua kenh rieng.

## Giai doan C - Chot contract va lam Chat

**Contract da chot ngay 17/10/2026: Hang doi va tiep quan conversation.** Khong viet UI hay migration Chat ngoai contract nay neu chua duoc ca nhom review.

**Trang thai implementation 17/10/2026:** Da co entity/API queue-claim, REST va WebSocket broadcast, widget header, migration `V4_2_0` va regression H2. `mvn clean test` PASS: 68 tests, 0 failure, 0 error, 1 MySQL acceptance skip. Chua duoc chay `V4_2_0` tren Railway; phai backup, review va nghiem thu MySQL truoc khi danh dau Giai doan C hoan tat.

**Dieu chinh contract sau review UI:** Inbox la mot luong duy nhat, khong hien tab `Tu van`/`Nhan nuoi`. `type` chi giu noi bo de render card ngu canh. Moi staff dung branch va ADMIN deu doc/gui duoc; `assignedStaffId` chi la nhan thong bao ai da mo/tiep nhan truoc, khong khoa staff khac. Nhan nut chat o san pham/cho con/bai nhan nuoi se tao message `CONTEXT` co link, anh, tom tat va cau quan tam mac dinh. Migration bo sung: `V4_3_0__chat_context_messages_mysql.sql`; phai chay tren Railway sau backup truoc khi restart backend MySQL.

### C1. Mo hinh conversation va pham vi

- He thong dung chung `ChatMessage` cho hai loai `Conversation`: `SUPPORT` va `ADOPTION`.
- Tab `Tu van` la tab mac dinh cua hop chat goc phai. Khach dung de hoi ve cho, san pham, phu kien, thuc an va dich vu.
- Tab `Nhan nuoi` chi hien conversation sau khi don `AdoptionApplication` da `APPROVED`; khong tron noi dung tu van voi ho so nhan nuoi.
- Mot conversation `ADOPTION` thuoc branch cua `AdoptionPost`. Mot conversation `SUPPORT` phai co `branchId` khi bat dau tu san pham/cho; voi cau hoi chung, UI/API phai co quy tac chon branch/triage ro rang truoc khi tao conversation.
- Chi `BRANCH_MANAGER` cua dung branch va `ADMIN` thay duoc conversation dang cho trong hang doi. CUSTOMER chi thay conversation cua chinh minh.

`Conversation` can duoc mo rong qua migration da review, khong dung `ddl-auto`:

```text
id
type: SUPPORT | ADOPTION
customerId
assignedStaffId nullable
assignedAt nullable
branchId
adoptionPostId nullable (bat buoc voi ADOPTION)
productId nullable
puppyListingId nullable
status: OPEN | CLAIMED | CLOSED
createdAt
lastMessageAt
```

`ChatMessage` giu mot bang dung chung: `id`, `conversationId`, `senderId`, `content`, `sentAt`, `isRead`.

### C2. Quy tac tiep quan va phan quyen

1. Conversation moi bat dau o `OPEN`, hien thi `Chua co nhan vien tiep quan`.
2. Bat ky `BRANCH_MANAGER` dung branch hoac `ADMIN` co the bam `Nhan cuoc tro chuyen`.
3. Nhan vien thanh cong dau tien duoc gan `assignedStaffId`, `assignedAt` va conversation chuyen `CLAIMED`.
4. Nhan vien khac mo conversation da `CLAIMED` chi thay thong bao `Cuoc tro chuyen dang do [ten nhan vien] tiep quan`; ho khong duoc doc noi dung hoac gui tin.
5. Nhan vien dang phu trach co the `Chuyen giao` hoac `Bo tiep quan`; conversation tro lai `OPEN`. ADMIN co the xem, chuyen giao hoac tiep quan khi can.
6. Khi staff duoc gan bi khoa, inactive hoac khong con thuoc branch, conversation phai duoc tra lai hang doi hoac do ADMIN chuyen giao theo event/luong quan tri duoc review.
7. CUSTOMER chi gui/nhan tin trong conversation cua minh. Staff chi gui/nhan tin khi dang duoc assigned; ADMIN co quyen override theo audit log.

Nhan conversation phai atomic de hai nguoi khong cung tra loi:

```sql
UPDATE conversations
SET assigned_staff_id = :staffId,
    assigned_at = NOW(),
    status = 'CLAIMED'
WHERE id = :conversationId
  AND assigned_staff_id IS NULL
  AND status = 'OPEN';
```

Neu lenh nay update `0` dong, API tra `409 Conflict`; UI reload metadata va hien thong bao da co nhan vien khac tiep quan. Khong duoc chi kiem tra bang frontend.

### C3. Rule Nhap nuoi va idempotency

- Conversation `ADOPTION` chi duoc tao sau `APPROVED`.
- Moi cap `customerId + adoptionPostId` co toi da mot conversation nhan nuoi. Migration phai doi unique constraint hien co de khong phu thuoc `assignedStaffId`; approve lap lai khong tao conversation trung.
- Approve dong `AdoptionPost` thanh `CLOSED`; don duoc duyet giu `APPROVED`; cac don `PENDING` con lai phai duoc dong theo mot status da chot va ghi ro trong test.
- Reject mot don khong dong post. Close thu cong chan don moi nhung giu lich su don va chat.

### C4. Phan cong sau contract

- TV3: migration/entity/repository claim atomic, API queue/claim/release/transfer, REST history, WebSocket subscribe/send/unread, reconnect, loading/error/empty state va xu ly access-token het han.
- TV2: giu approve/open `ADOPTION` idempotent, scope customer/branch/assigned staff, regression post/application/conversation va xu ly staff inactive.
- TV1: icon hop thu nho goc phai, badge unread, animation ton trong `prefers-reduced-motion`, drawer/modal co tab `Tu van` mac dinh va `Nhan nuoi`, CTA dung route Chat va khong dan sang Booking.

### C5. Test bat buoc

- customer khong doc/gui conversation cua customer khac;
- manager khac branch khong thay hang doi, khong claim va khong doc/gui;
- hai staff claim cung luc: chi mot request thanh cong, request con lai `409`;
- staff khong duoc assign khong doc/gui conversation `CLAIMED`;
- ADMIN co the takeover/transfer theo policy va staff inactive tra conversation ve hang doi;
- approve lap lai khong tao conversation `ADOPTION` trung;
- history dung thu tu, realtime chi den dung participant, reconnect khong tao message/conversation ban sao;
- unread, release/transfer va reload UI dung trang thai;
- CTA chat khong dan den Service Booking.

## Giai doan D - Regression giao dien va thuong mai

### TV1

Test desktop va mobile cho `/shop`, `/service`, `/adoptions`, `/adoptions/my-applications`, `/admin/products`, `/admin/orders` bang Anonymous, CUSTOMER, BRANCH_MANAGER va ADMIN.

Kiem tra:

- header/logo/menu/search/cart/booking/account can thang hang, khong chong lap;
- hamburger/sidebar chi xuat hien dung role;
- font va baseline Dang nhap/Dang xuat dong deu;
- Shop bulk delete, huy delete, reload danh sach va branch scope;
- ADMIN thay toan bo order, Manager chi thay order branch cua minh; trang thai cap nhat dung sau reload.

Moi loi phai bao URL, role, viewport, buoc tai hien va anh chup. Khong sua phan quyen de lam an nut UI.

### TV2

- Regression Adoption CRUD, apply, approve/reject va branch ownership.
- Nghiem thu Cloudinary upload/replace/delete cho DogProfile, AdoptionPost va avatar tren MySQL that.
- Kiem tra cleanup/orphan khi database hoac provider loi; URL HTTPS va public ID dung folder.
- Giu debt Seed V3 tach biet: chi lap ke hoach dong bo manifest/validator/tai lieu sau demo, khong dong vao database dang dung.

## Giai doan E - Release gate

Chi goi la san sang deploy khi toan bo dieu kien sau PASS:

- `mvn clean test` PASS;
- MySQL integration PASS voi `RUN_MYSQL_INTEGRATION_TESTS=true`;
- backend chay profile MySQL khong loi DDL;
- Auth/OTP/reset/profile/avatar E2E PASS;
- Customer -> Adoption application -> Manager decision -> Chat PASS;
- Product/PuppyListing/Booking/Order dung role va branch scope;
- Cloudinary upload/replace/delete PASS, khong co orphan quan trong;
- `git diff --check` PASS;
- quet secret PASS va khong co credential trong Git;
- Seed V3 debt duoc ghi ro la known follow-up, khong bi danh dau PASS gia.

## Cach bao cao moi task

Moi thanh vien bao cao theo mau sau, khong dung tu "xong" neu chua co evidence:

```text
Task:
Owner:
Commit/PR:
Pham vi file:
Du lieu/migration da tac dong:
Lenh test da chay:
Ket qua PASS/FAIL:
Manual evidence (URL, role, viewport neu co):
Rollback/known limitation:
Task tiep theo va nguoi nhan:
```

Neu task lien quan Railway, bo sung database/environment, thoi diem chay, va xac nhan da backup. Tuyet doi khong dan secret vao bao cao.
