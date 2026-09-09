# PawConnect Data Pipeline

Curated data trong `data/curated/` la master data. Bootstrap chinh thuc duy nhat la `data/seed/v3/`, dung chung cho TV1, TV2 va TV3 tren mot database PawConnect dev/test/demo.

Kiem tra release:

    python data-pipeline/src/build_seed_release.py --verify v3
    python data-pipeline/src/build_seed_release.py --self-test-v3

Importer doc theo thu tu `reference -> catalog -> users -> adoption -> commerce` va map bang stable code/seed key. Khong import raw crawl, candidate hay workspace.

Chu so huu: TV3 xac nhan Role/User, TV1 xac nhan Branch/Category/ServiceType va commerce mapping, TV2 duy tri catalog, adoption, release va validation.

Khong sua truc tiep release v3. Sau khi review curated/rule, tao release ke tiep va cap nhat handoff sau khi verify.
