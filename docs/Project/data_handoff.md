# PawConnect Data Handoff

## Bootstrap chung

`data-pipeline/data/seed/v3/` la dataset bootstrap duy nhat cua PawConnect cho TV1, TV2 va TV3 tren cung mot database dev/test/demo. Importer chi doc v3 va kiem tra `manifest.json` truoc khi import. Release da phat hanh la immutable: muon cap nhat du lieu phai tao release ke tiep, khong sua truc tiep v3.

Thu tu importer: `reference -> catalog -> users -> adoption -> commerce`.

## TV1

TV1 dung `reference/branches.csv`, `reference/categories.csv`, `reference/service_types.csv`, `commerce/puppy_listings.csv` va `commerce/products.csv`. PuppyListing la bang thuong mai rieng; gia la VND cho moi be va stock la 1-20. `products.csv` chi chua FOOD va ACCESSORY.

## TV2

TV2 dung `catalog/breeds.csv`, `adoption/dog_profiles.csv`, `adoption/adoption_posts.csv` va `adoption/adoption_applications.csv`. DogProfile adoption khong phai PuppyListing. Map bang stable code va `seed_key`, khong dung ID tu tang trong CSV.

## TV3

TV3 dung `reference/roles.csv`, `fixtures/users.csv` va `reference/branches.csv` de map BRANCH_MANAGER. `image_url` dang rong va duoc bo sung sau khi Cloudinary duoc tich hop. `credential_mode=RUNTIME_ENV` yeu cau importer tao credential tai runtime tu cau hinh local hoac bien moi truong khong commit.

## Quy tac chung

Khong import tu raw crawl, candidate, workspace hay release cu. Khong tu doi stable code, enum, header hay quan he khoa ngoai. Conflict voi code phai duoc bao lai TV2/Data Engineer truoc khi doi contract.

`name`, `listing_title` va `description` la du lieu khoi tao. `seed_key` va `breed_code` phai duoc map sang database ID qua lookup/import registry va giu on dinh khi import lai. Seed chi bootstrap lan dau va khong tu chay lai moi lan ung dung khoi dong; importer khong ghi de du lieu nguoi dung da sua trong database. Website doc du lieu hien tai tu database, khong sua CSV hoac build lai v3 de cap nhat giao dien.

Admin va Branch Manager duoc sua du lieu trong database theo quyen cua ung dung. Sau import, `listing_title`, `description`, `price`, `stock`, `status` va `image_url` la du lieu nghiep vu co the duoc cap nhat trong database. Khi doi breed cua mot listing, tao listing moi va dong listing cu de giu lich su va stable key.
