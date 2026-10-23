-- Display names only: emails, usernames, passwords, roles and branch assignments remain unchanged.
UPDATE users
SET full_name = CASE seed_key
    WHEN 'user_manager_hcm' THEN 'Nhân viên TP.HCM'
    WHEN 'user_manager_hn' THEN 'Nhân viên Hà Nội'
    WHEN 'user_manager_dn' THEN 'Nhân viên Đà Nẵng'
END
WHERE seed_key IN ('user_manager_hcm', 'user_manager_hn', 'user_manager_dn');
