# PawConnect - Data Contract v0.1

> **Nguoi phu trach de xuat:** TV2 kiem Data Engineer  
> **Trang thai:** Draft de TV1, TV2, TV3 review va xac nhan  
> **Pham vi file:** Tai lieu nay chi huong dan du lieu chia se. Khong tu dong thay doi code, entity, endpoint, database schema hoac migration.

---

## 1. Muc tieu va pham vi

Data contract v0.1 thong nhat cach TV1, TV2 va TV3 tao, nhan, validate va ban giao du lieu dung chung cho PawConnect.

Muc tieu chinh:

- Tao mot shared data pipeline co the chay lap lai cho moi truong `dev`, `test`, `demo`.
- Cung cap seed tham chieu toi thieu de cac module phat trien song song.
- Giam loi tich hop do sai enum, sai khoa ngoai, sai don vi tuoi, sai dinh dang tien hoac URL anh.
- Dam bao du lieu demo khong chua PII, secret, anh khong ro quyen su dung.
- Giu moi mapping co the truy vet bang `seed_key`, stable reference code, dataset version, checksum va validation report.

Pham vi du lieu chia se:

| Nhom du lieu | Entity/Dataset | Ben dung chinh | Ben xac nhan |
| --- | --- | --- | --- |
| Tham chieu dung chung | Role, Branch, Category, ServiceType | TV1, TV2, TV3 | TV1 + TV3 |
| Thuong mai | Product | TV1 | TV1 |
| Nhan nuoi | DogProfile, AdoptionPost, AdoptionApplication | TV2 | TV2 + TV3 |
| Nen tang nguoi dung | User, Role, fixture account | TV1, TV2, TV3 | TV3 |

Pipeline chi xuat CSV/JSON da validate trong v0.1. Seed SQL, migration hoac import truc tiep DB chi thuc hien sau khi schema va DB da duoc nhom chot.

---

## 2. Quy uoc du lieu chung

Tat ca file trung gian va file ban giao phai tuan thu cac quy uoc sau:

- Encoding: `UTF-8`.
- Ten cot: `snake_case`.
- Khoa on dinh cho moi ban ghi seed: `seed_key`.
- Ma tham chieu on dinh: dung `code` hoac `<entity>_code`, vi du `BR_HCM_01`, `CAT_DOG`, `SERVICE_SPA`, `ROLE_CUSTOMER`.
- Khong hard-code ID tu tang cua database trong file CSV/JSON.
- URL anh: chi chap nhan HTTPS image URL trong cot `image_url`.
- Tien: luu bang so nguyen VND, khong luu chuoi co dau phay hoac ky hieu tien te.
- Boolean: dung `true` hoac `false`.
- Ngay gio: dung ISO 8601, vi du `2026-09-06T09:00:00+07:00`.
- Khoa ngoai trong file trung gian phai tham chieu bang `seed_key` hoac stable reference code, khong tham chieu ID DB.
- Cung mot `seed_key` khong duoc trung trong cung dataset version.

---

## 3. Enum chung

Enum phai dung chu hoa dung nhu contract. Pipeline fail neu gap gia tri ngoai danh sach.

### 3.1. Size

```text
SMALL
MEDIUM
LARGE
```

Ap dung cho `Product.suitable_size` va `DogProfile.size`.

### 3.2. Gender

```text
MALE
FEMALE
```

Ap dung cho `DogProfile.gender`.

### 3.3. Age

Contract phan biet ro hai don vi tuoi:

- `age_months`: so thang tuoi, kieu so nguyen, dung cho DogProfile trong pipeline v0.1.
- `age_years`: so nam tuoi, kieu so nguyen hoac so thap phan neu nhom can hien thi theo nam.

De xuat v0.1: pipeline luu nguon chuan la `age_months`; neu UI can hien thi nam tuoi thi tinh ra `age_years` o tang DTO/view. Neu schema hien tai chi co `age`, nhom can xac nhan don vi truoc khi import.

### 3.4. DogProfile.vaccination_status

```text
NOT_VACCINATED
PARTIALLY_VACCINATED
FULLY_VACCINATED
```

Ap dung cho `DogProfile.vaccination_status`. Pipeline fail neu gia tri vaccination status khong thuoc enum nay.

### 3.5. AdoptionPost.status

```text
AVAILABLE
CLOSED
```

Ap dung cho `AdoptionPost.status`. Pipeline fail neu gia tri status cua AdoptionPost khong thuoc enum nay.

### 3.6. AdoptionApplication.status

```text
PENDING
APPROVED
REJECTED
```

Ap dung cho `AdoptionApplication.status`. Pipeline fail neu gia tri status cua AdoptionApplication khong thuoc enum nay.

Rule trang thai adoption:

- Khi AdoptionApplication chuyen `APPROVED`, AdoptionPost tuong ung chuyen `CLOSED`.
- Cac AdoptionApplication khac cua cung AdoptionPost khong duoc giu `PENDING`.
- Day la enum mac dinh cua data pipeline; truoc khi import DB, TV2/TV3 phai bao dam entity Java dung dung gia tri hoac co mapping ro rang.

---

## 4. Du lieu tham chieu dung chung

### 4.1. Role

TV3 cung cap va xac nhan Role. Dataset tham chieu toi thieu:

```text
seed_key,role_code,name
role_customer,ROLE_CUSTOMER,CUSTOMER
role_branch_manager,ROLE_BRANCH_MANAGER,BRANCH_MANAGER
role_admin,ROLE_ADMIN,ADMIN
```

Rule:

- `name` phai khop role dung trong Spring Security.
- `role_code` la ma tham chieu on dinh trong pipeline.
- Khong tu tao role moi trong seed neu TV3 chua xac nhan.

### 4.2. Branch

TV1 quan ly Branch; TV3 co the dung Branch cho admin dashboard va phan quyen manager.

Cot de xuat:

```text
seed_key,branch_code,name,address,phone,latitude,longitude
```

Rule:

- `branch_code` la stable reference code, vi du `BR_HCM_01`.
- `name` khong rong.
- `phone` trong demo khong dung so dien thoai that cua ca nhan.
- `latitude`, `longitude` co the rong neu nhom chua can ban do.

### 4.3. Category

TV1 quan ly Category.

Cot de xuat:

```text
seed_key,category_code,name,description
```

Gia tri toi thieu can TV1 xac nhan:

```text
CAT_BREEDING_DOG
CAT_FOOD
CAT_ACCESSORY
CAT_MEDICAL_SUPPLY
```

Rule:

- `category_code` duoc Product tham chieu qua `category_code`.
- Khong doi nghia category sau khi dataset da ban giao; neu doi thi tang dataset version.

### 4.4. ServiceType

TV1 quan ly ServiceType.

Cot de xuat:

```text
seed_key,service_type_code,name,duration_minutes,price
```

Gia tri toi thieu can TV1 xac nhan:

```text
SERVICE_SPA
SERVICE_CHECKUP
SERVICE_VACCINATION
```

Rule:

- `duration_minutes` la so nguyen duong.
- `price` la so nguyen VND va lon hon hoac bang 0.

---

## 5. Hop dong du lieu TV1 nhan: Product

TV2/Data Engineer co the sinh dataset Product de TV1 review va import sau khi schema chot.

Cot de xuat:

```text
seed_key,name,description,price,stock,image_url,suitable_size,is_breeding_dog,category_code,branch_code
```

Rule bat buoc:

- `seed_key`, `name`, `price`, `stock`, `image_url`, `suitable_size`, `category_code`, `branch_code` khong rong.
- `price` la so nguyen VND va `price > 0`.
- `stock` la so nguyen va `stock >= 0`.
- `image_url` phai la HTTPS image URL da ro quyen su dung.
- `suitable_size` thuoc `SMALL`, `MEDIUM`, `LARGE`.
- `is_breeding_dog` chi dung `true` hoac `false`.
- `category_code` phai ton tai trong Category seed.
- `branch_code` phai ton tai trong Branch seed.
- Product la cho giong doc ban nen co `stock` bang `0` hoac `1`.
- Product khong phai cho giong khong duoc chen gia tri giong/tuoi/can nang gia vao description theo cach gay nham voi DogProfile.

---

## 6. Hop dong du lieu TV2 nhan

### 6.1. DogProfile

Cot de xuat v0.1:

```text
seed_key,name,breed,size,age_months,weight_kg,gender,vaccination_status,image_url,description,branch_code
```

Rule bat buoc:

- `seed_key`, `name`, `breed`, `size`, `age_months`, `gender`, `image_url` khong rong.
- `size` thuoc `SMALL`, `MEDIUM`, `LARGE`.
- `gender` thuoc `MALE`, `FEMALE`.
- `age_months` la so nguyen va `age_months >= 0`.
- `weight_kg` la so duong neu co.
- `image_url` phai la HTTPS image URL da ro quyen su dung.
- Mot cho ca the khong duoc xuat hien dong thoi trong `Product` va `DogProfile`.
- `branch_code` la de xuat de xac dinh chi nhanh quan ly ho so nhan nuoi.

**Luu y ve `branch_id`:** TV2 de xuat `DogProfile` co `branch_id` de du lieu nhan nuoi xac dinh duoc chi nhanh quan ly. Day la quyet dinh can TV1/TV3 xac nhan truoc khi sua schema. Truoc khi schema duoc xac nhan, pipeline chi giu `branch_code` trong CSV/JSON va khong tu import vao cot chua ton tai.

### 6.2. AdoptionPost

Cot de xuat:

```text
seed_key,dog_profile_seed_key,created_by_user_key,title,description,health_note,status,created_at
```

Rule bat buoc:

- `dog_profile_seed_key` phai ton tai trong DogProfile dataset.
- `created_by_user_key` phai ton tai trong User fixture va bat buoc la user co role `ADMIN` hoac `BRANCH_MANAGER`; user role `CUSTOMER` khong duoc tao AdoptionPost.
- Neu `created_by_user_key` la `BRANCH_MANAGER`, user do phai cung `branch_code` voi DogProfile duoc tham chieu.
- Moi DogProfile chi co toi da mot AdoptionPost `AVAILABLE` tai cung thoi diem.
- `created_at` dung ISO 8601 va khong nam trong tuong lai tai thoi diem sinh dataset.
- `status` khong duoc tu y mo rong; danh sach status can TV2 xac nhan voi entity thuc te.

### 6.3. AdoptionApplication

Cot de xuat:

```text
seed_key,adoption_post_seed_key,applicant_user_key,message,status
```

Rule bat buoc:

- `adoption_post_seed_key` phai ton tai trong AdoptionPost dataset.
- `applicant_user_key` phai ton tai trong User fixture va co role `CUSTOMER`.
- `message` khong chua PII that.
- Mot customer khong co nhieu don dang hoat dong cho cung mot AdoptionPost.
- Neu co mot don `APPROVED`, AdoptionPost tuong ung phai duoc dong theo rule nghiep vu TV2.
- Khi mot AdoptionApplication co status `APPROVED`, cac AdoptionApplication khac cung `adoption_post_seed_key` khong duoc giu status `PENDING`.
- `status` khong duoc tu y mo rong; danh sach status can TV2 xac nhan voi entity thuc te.

---

## 7. Hop dong du lieu TV3 cung cap

### 7.1. User

TV3 cung cap User fixture de TV1/TV2 test cac luong can JWT va phan quyen.

Cot de xuat cho file fixture:

```text
seed_key,full_name,email,password_placeholder,phone,avatar_url,role_code,branch_code,created_at
```

Rule bat buoc:

- `seed_key`, `full_name`, `email`, `role_code` khong rong.
- `role_code` phai ton tai trong Role seed.
- `branch_code` bat buoc voi User co role `BRANCH_MANAGER`.
- `branch_code` de trong voi User co role `CUSTOMER` hoac `ADMIN`.
- `email` la email gia lap, khong dung email that cua thanh vien hoac khach hang.
- `password_placeholder` khong phai mat khau that; neu can hash thi TV3 quyet dinh cach seed an toan.
- `phone` neu co chi dung so demo, khong dung so ca nhan that.
- `avatar_url` neu co phai la HTTPS image URL ro quyen su dung.

### 7.2. Role

Role la du lieu tham chieu do TV3 xac nhan cuoi. TV1/TV2 chi tham chieu bang `role_code` hoac role name da chot.

### 7.3. Fixture account toi thieu

De xuat TV3 cung cap toi thieu:

```text
user_customer_demo
user_branch_manager_demo
user_admin_demo
```

Rule:

- Moi account gan dung mot Role trong `CUSTOMER`, `BRANCH_MANAGER`, `ADMIN`.
- Fixture account chi dung cho `dev`, `test`, `demo`.
- Khong commit secret dang ro; neu can password demo thi ghi cach dang nhap trong tai lieu rieng do TV3 quan ly.

---

## 8. Du lieu khong duoc dung

Pipeline, seed va fixture khong duoc chua:

- PII that: email, so dien thoai, dia chi nha rieng, ten nguoi that, ho so nguoi dung that.
- Secret: API key, token, cookie, Cloudinary secret, mat khau that, refresh token.
- Anh khong ro quyen su dung, anh copy tu website khong cho phep tai lai, anh sau man hinh dang nhap.
- Don hang that, don nhan nuoi that, noi dung chat that.
- Du lieu crawl tu nguon cam bot, cam sao chep, hoac chua kiem tra dieu khoan su dung.

Trong yeu cau hien tai: khong crawl web, khong upload Cloudinary.

---

## 9. Quy tac ban giao dataset

Moi lan TV2/Data Engineer ban giao dataset cho TV1/TV3 phai kem cac thong tin sau:

```text
dataset_name:
dataset_version:
schema_contract_version: v0.1
generated_at:
random_seed:
record_counts:
checksum_sha256:
validation_report_path:
known_limitations:
owner:
reviewers:
```

Quy tac:

- `dataset_version` tang khi them/xoa/sua ban ghi hoac doi rule sinh du lieu.
- `random_seed` phai co dinh de chay lai cho cung output.
- `checksum_sha256` tinh tren file output da sap xep on dinh.
- Validation report phai ghi ro tong so ban ghi, so ban ghi hop le, duplicate, quarantined va loi blocking.
- Moi dataset ban giao phai co danh sach file kem duong dan tuong doi.
- Neu validation co loi blocking thi khong ban giao de import.
- Neu import DB duoc tao sau nay, phai chung minh import idempotent: chay lai khong tao ban ghi trung.

Mau validation report toi thieu:

```json
{
  "contract_version": "v0.1",
  "dataset_version": "demo-0.1.0",
  "generated_at": "2026-09-06T09:00:00+07:00",
  "random_seed": 20260906,
  "tables": {
    "product": 5,
    "dog_profile": 5,
    "adoption_post": 3
  },
  "valid": 13,
  "duplicates": 0,
  "quarantined": 0,
  "blocking_errors": []
}
```

---

## 10. Quyet dinh can nhom xac nhan

1. DB chinh thuc va chien luoc seed sau nay: CSV/JSON only, Flyway, Liquibase hay import tool rieng.
2. Ten bang/cot thuc te, nullable, unique constraint va mapping tu `seed_key`/stable code sang ID DB.
3. Co them `branch_id` vao `DogProfile` hay `AdoptionPost` de quan ly nhan nuoi theo chi nhanh hay khong.
4. Neu them chi nhanh cho nhan nuoi, chot `branch_id` nam o `DogProfile`, `AdoptionPost`, hay ca hai.
5. Don vi tuoi trong entity hien tai: `age` la thang hay nam; de xuat chuan pipeline la `age_months`.
6. Danh sach status chinh thuc cho `AdoptionPost` va `AdoptionApplication`.
7. Gia tri seed toi thieu cho Branch, Category va ServiceType.
8. TV3 xac nhan Role name dung trong security: `CUSTOMER`, `BRANCH_MANAGER`, `ADMIN`.
9. TV3 xac nhan fixture account demo va cach seed password an toan.
10. Nguon anh hop le cho demo: anh nhom tu tao, anh duoc cap phep ro rang, hay placeholder hop le.
11. Nguong validation truoc demo: co cho phep warning nao khong, va loi nao bat buoc fail pipeline.
12. Dataset version dau tien duoc dung chung cho Integration/Test tuan 7.

---

## 11. Quyet dinh mac dinh va diem can xac nhan

### 11.1. Quyet dinh mac dinh de pipeline v0.1 ap dung

- DB mac dinh la MySQL 8 neu giang vien khong yeu cau DB khac.
- `DogProfile` duoc de xuat co `branch_id` de xac dinh chi nhanh quan ly ho so cho nhan nuoi.
- `AdoptionPost` suy ra chi nhanh tu `DogProfile`, khong can luu lap `branch_id` trong AdoptionPost neu nhom chot theo huong nay.
- `age` trong DB duoc hieu la so thang tuoi; pipeline dung cot nguon `age_months`.
- `AdoptionPost.status` chi gom:

```text
AVAILABLE
CLOSED
```

- `AdoptionApplication.status` chi gom:

```text
PENDING
APPROVED
REJECTED
```

### 11.2. Diem TV1 can phan hoi

- Xac nhan MySQL 8 co phu hop voi module Shop/Service va yeu cau mon hoc khong.
- Xac nhan danh sach Branch seed toi thieu va `branch_code` dung chung.
- Xac nhan Category seed toi thieu va mapping `category_code` cho Product.
- Xac nhan ServiceType seed toi thieu, `duration_minutes` va `price` VND.
- Xac nhan Product contract co du cot cho luong shop/gallery/cart/order hay can them cot nao trong dataset trung gian.
- Phan hoi viec `DogProfile.branch_id` co dung voi cach TV1 quan ly Branch trong shared database khong.

### 11.3. Diem TV3 can phan hoi

- Xac nhan MySQL 8 co phu hop voi cau hinh security, auth, chat va deploy khong.
- Xac nhan Role name chinh thuc: `CUSTOMER`, `BRANCH_MANAGER`, `ADMIN`.
- Xac nhan User fixture toi thieu va cach seed password/credential an toan cho demo.
- Xac nhan `BRANCH_MANAGER` co lien ket chi nhanh bang truong nao de validate rule cung `branch_code` voi DogProfile.
- Xac nhan khi `AdoptionApplication` duoc approve, Conversation/Chat cua TV3 can trang thai hoac trigger nao tu TV2 khong.
- Xac nhan viec `AdoptionPost` suy ra chi nhanh tu `DogProfile` co du cho phan quyen va admin dashboard khong.

---

## 12. Data Contract v0.2 Draft - Commercial Puppy Data

Trang thai: `APPROVED FOR DEV/TEST/DEMO` - TV2/Data Engineer chot lam chuan du lieu chung.
Trang thai nay chua approved cho production hoac DB import.

Muc tieu cua phan nay la mo ta cho con ban tai pet shop ma khong lam thay doi y nghia
cua breed reference truong thanh va cac curated adoption data v0.1.

### 12.1. Breed reference truong thanh

Trong `data-pipeline/data/curated/catalog/breeds.csv`, cac cot hien co giu nguyen header
va gia tri. Contract v0.2 lam ro y nghia:

- `default_size` la adult breed reference size.
- `min_weight_kg` va `max_weight_kg` la adult breed reference weight range.
- Khong dung adult weight range de validate `current_weight_kg` cua puppy.
- Khong doi ten, doi gia tri hoac ghi de `breeds.csv` hien co trong Draft nay.

### 12.2. Commercial Dog profile tren Product

Product cho con la Product rieng trong module thuong mai. Phan commercial profile moi:

```text
breed_code,breed_type,life_stage,age_months,current_weight_kg,current_size,expected_adult_size
```

Enum:

```text
breed_type: PUREBRED | MIXED | UNKNOWN
life_stage: PUPPY | ADULT | UNKNOWN
size: SMALL | MEDIUM | LARGE
```

### 12.2.1. Generated Product kind

Generated Product profile dung enum sau de tach ro Product puppy va Product khong
phai ca the cho:

```text
product_kind: PUPPY | FOOD | ACCESSORY
```

Rule:

- `product_kind` chi ap dung cho generated Product profile trong `dev`, `test`, `demo`.
- `PUPPY` phai dung commercial puppy fields theo muc 12.2.
- `FOOD` va `ACCESSORY` phai de trong toan bo commercial puppy fields; khong duoc
  mo ta nhu mot ca the cho.
- Generated Product demo duoc phep de trong `image_url`; khong dung placeholder,
  Cloudinary hay anh crawl khi chua co tai san duoc cap phep.

Quy tac:

- Product puppy co `stock=1`.
- `age_months` la so nguyen va `age_months >= 0`.
- `current_weight_kg` la can nang tai thoi diem ghi nhan, khong phai adult weight.
- `current_size` la size hien tai neu nguon xac dinh ro.
- `expected_adult_size` la size truong thanh du kien.
- `PUREBRED` bat buoc co `breed_code` ton tai trong `breeds.csv`.
- Khi `PUREBRED`, `expected_adult_size` lay tu `default_size` cua breed reference.
- `MIXED` va `UNKNOWN` khong duoc suy doan breed, expected adult size hoac adult weight.
- `Product.suitable_size` cu khong doi nghia khi dung cho thuc an/phu kien.
- `Product.age` kieu String la legacy display; pipeline chuan dung `age_months`.
- `is_breeding_dog` khong thay the cho `breed_type`.

De tuong thich, cac cot v0.1 va stable code hien co duoc giu nguyen. Cac cot v0.2
chi duoc them sau khi TV1 xac nhan mapping entity/DTO.

### 12.3. Validation va raw commercial observation

- Puppy khong bi bat buoc `current_weight_kg` nam trong adult weight range.
- Adult purebred moi duoc doi chieu adult weight range khi du lieu can nang co ro rang.
- Mixed/unknown chi validate enum va reference duoc cung cap; khong suy doan adult values.
- Raw commercial data chi la observation, khong ghi de curated catalog, Product curated hoac DB.
- Crawler chi ghi `age_months`, `current_weight_kg` va `price` khi trang cong khai the hien ro.
  Truong thieu hoac khong chac chan phai de trong; khong suy doan.

### 12.4. Handoff va diem can xac nhan

- TV1 co trach nhiem trien khai mapping cac cot commercial moi vao Product entity/DTO.
- TV1 xac nhan `suitable_size` cu giu nguyen nghia cho food/accessory.
- TV3 co trach nhiem ho tro enum Java, migration sau nay va backward compatibility khi integration.
- TV3 xac nhan commercial Product co tach khoi DogProfile hay khong.
- DogProfile adoption va Product thuong mai la hai dataset/entity nghiep vu khac nhau.
- Neu conflict voi code, TV1/TV3 bao lai TV2; khong tu doi stable code, enum hoac contract.
- Chua duoc sua entity/backend, SQL/migration, CSV curated/generated hoac import DB.
- Contract nay chua approved cho production hoac DB import.

## 13. Seed Release Policy

`data-pipeline/data/curated/` la master data chinh thuc. Seed Release la snapshot
bat bien cua curated master va build output da validation, dung cho `dev`,
`test`, `demo` va bootstrap DB sau khi importer duoc review.

- `data-pipeline/data/seed/v3/` la importer input duy nhat, dung random seed
  `20260908` va trang thai `APPROVED_FOR_DEV_TEST_DEMO`.
- Release da phat hanh khong duoc ghi de. Thay doi du lieu can release moi voi
  manifest moi va validation moi.
- Raw/candidate commercial chi la evidence/observation. Khong import truc tiep vao DB
  va khong dua raw URL, listing title, contact, anh crawl hoac secret vao release.
- `breeds.csv` giu y nghia adult reference. Breed moi chi duoc admission khi co stable
  code, adult size/weight, maturity rule, provenance PASS va evidence reference duoc
  review. Truong hop thieu evidence phai nam trong `breed_proposals.csv`, khong dung
  cho seed/generator.
- Seed Product v0.2 van chua san sang DB import cho den khi TV1/TV3 hoan thanh mapping
  Entity/DTO, enum, migration va backward compatibility.

## 14. Final Seed v3 - Commerce Bootstrap

Trang thai: **APPROVED FOR DEV/TEST/DEMO**. Seed `data-pipeline/data/seed/v3/`
la bootstrap chung duy nhat khi importer duoc ban giao; raw, candidate va release
cu khong duoc import truc tiep vao DB.

### 14.1. Catalog va commercial price rule

- `breeds.csv` la adult breed reference: `default_size` va weight range chi mo ta
  kich co, can nang truong thanh. Catalog hien co 15 breed, bao gom
  `BREED_SAMOYED` va `BREED_PHU_QUOC`.
- `data-pipeline/data/curated/commerce/puppy_price_rules.csv` la curated rule
  duoc review cho gia ban puppy. Builder v3 chi doc curated catalog, reference,
  fixture va price rule nay; khong doc `raw/` hoac `candidate/`.
- `BREED_HUSKY_SIBERIAN` la stable-code chinh thuc cho nhan hien thi Husky.

### 14.2. Puppy listing

Puppy sale la commerce listing theo lo, khong phai `DogProfile` nhan nuoi va
khong phai `Product` FOOD/ACCESSORY. Header chinh thuc:

```text
seed_key,listing_title,description,branch_code,category_code,breed_code,breed_type,life_stage,age_months,current_weight_kg,current_size,expected_adult_size,price_per_puppy_vnd,stock,status,image_url,health_status,care_instructions
```

- `breed_type`: `PUREBRED`, `MIXED`, `UNKNOWN`; v3 listing curated dung
  `PUREBRED`.
- `life_stage`: `PUPPY`, `ADULT`, `UNKNOWN`; v3 listing curated dung `PUPPY`.
- `current_size` va `expected_adult_size`: `SMALL`, `MEDIUM`, `LARGE`.
- `stock` la integer tu 1 den 20; `price_per_puppy_vnd` la VND integer duong
  cho mot be. `AVAILABLE` chi hop le khi `stock > 0`; enum `status` la
  `AVAILABLE`, `SOLD_OUT`.
- 14 breed commercial bat buoc la `BREED_HMONG_COC_DUOI`, `BREED_POMERANIAN`,
  `BREED_CORGI`, `BREED_SHIBA_INU`, `BREED_GOLDEN_RETRIEVER`,
  `BREED_LABRADOR_RETRIEVER`, `BREED_HUSKY_SIBERIAN`, `BREED_SAMOYED`,
  `BREED_ALASKAN_MALAMUTE`, `BREED_BEAGLE`, `BREED_FRENCH_BULLDOG`,
  `BREED_POODLE`, `BREED_CHIHUAHUA`, `BREED_PUG`. `BREED_PHU_QUOC` van la
  catalog reference nhung khong co puppy listing v3.
- `current_weight_kg` la can nang tai thoi diem ghi nhan. No duoc sinh tu
  progress phat trien va phai lon hon 0, nho hon adult target; khong validate
  bang adult weight range nhu mot ca the da truong thanh.

### 14.3. Product v3 va UI text

- `seed/v3/commerce/products.csv` chi chua `FOOD` va `ACCESSORY`. Tat ca
  puppy/breed fields cua hai loai nay phai rong; `is_breeding_dog=false`.
- `product_kind` cua profile Product generated: `PUPPY`, `FOOD`, `ACCESSORY`.
  `PUPPY` duoc ban giao qua `puppy_listings.csv`; `products.csv` final khong
  duoc chua `PUPPY`.
- Cac field hien thi UI nhu name, title, description va message trong seed v3
  khong duoc chua cac tu `demo`, `synthetic`, `generated` (khong phan biet hoa
  thuong). Provenance chi nam trong manifest/report, khong nam trong UI data.

### 14.4. Handoff cho TV1/TV3

- TV1 can tao mapping bang rieng cho puppy listing, dung header o 14.2 va enum
  `AVAILABLE|SOLD_OUT`, `PUREBRED|MIXED|UNKNOWN`, `PUPPY|ADULT|UNKNOWN`,
  `SMALL|MEDIUM|LARGE`; khong map puppy listing vao DogProfile adoption.
- TV1 giu Product FOOD/ACCESSORY tu `commerce/products.csv` va khong doi stable
  code trong seed.
- TV3 can bao dam enum, migration va backward compatibility khi importer DB
  duoc tich hop. Conflict voi code phai bao lai TV2 truoc khi doi contract hay
  stable code.

### 14.5. Fixture va adoption bootstrap v3

- `seed/v3/fixtures/users.csv` khong co password, password hash, token, secret
  hoac password placeholder. Importer tao credential phat trien tai runtime tu
  bien moi truong hay cau hinh local khong commit; `credential_mode=RUNTIME_ENV`
  chi la huong dan importer, khong phai credential.
- Bootstrap v3 co 30 `DogProfile`, 30 `AdoptionPost` va 36
  `AdoptionApplication`: moi breed catalog co hai ho so. Sau post CLOSED co mot
  APPROVED va mot REJECTED; post AVAILABLE co mot PENDING. DogProfile adoption
  van tach biet hoan toan voi PuppyListing thuong mai.
