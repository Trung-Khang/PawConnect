package com.pawconnect.util;

import com.pawconnect.entity.Branch;
import com.pawconnect.entity.RoleName;
import com.pawconnect.entity.User;
import java.util.Locale;

/** Keeps branch account labels consistent while preserving their stored credentials and role. */
public final class UserDisplayName {

    private UserDisplayName() {
    }

    public static String of(User user) {
        if (user == null) {
            return null;
        }
        if (user.getRole().getName() != RoleName.BRANCH_MANAGER) {
            return user.getFullName();
        }

        String branchCode = user.getBranch() == null || user.getBranch().getCode() == null
                ? "" : user.getBranch().getCode().toUpperCase(Locale.ROOT);
        if (branchCode.contains("HCM")) {
            return "Nhân viên TP.HCM";
        }
        if (branchCode.contains("HN")) {
            return "Nhân viên Hà Nội";
        }
        if (branchCode.contains("DN")) {
            return "Nhân viên Đà Nẵng";
        }

        Branch branch = user.getBranch();
        if (branch != null && branch.getName() != null && !branch.getName().isBlank()) {
            return "Nhân viên " + branch.getName().replaceFirst("(?i)^PawConnect\\s+", "");
        }
        return user.getFullName();
    }
}
