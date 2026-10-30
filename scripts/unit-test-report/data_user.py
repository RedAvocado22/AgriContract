N, A, B = "Normal", "Abnormal", "Boundary"
NONE = "N/A"
IAE = "IllegalArgumentException"
NSE = "NoSuchElementException"
IVT = "InvalidVerificationTransitionException"

MODULES = [
    {
        "sheet": "UserProfile",
        "module": "user-service",
        "desc": "UserProfile: đăng ký, quyết định KYC (verify/reject/resubmit), đổi role",
        "pre": "user-123, \"Cong ty A\", SELLER; ACTOR_ID, DECIDED_AT, EXPIRES_AT cố định",
        "groups": [
            ("create()", [
                (NONE, "user-123, Cong ty A, SELLER", "đủ field; status PENDING", NONE, N),
                (NONE, "Profile mới", "chưa có người duyệt, thời điểm duyệt, hạn, lý do", NONE, N),
                (NONE, "Profile mới", "phát 1 UserRegisteredEvent cho user-123", NONE, N),
            ]),
            ("reconstitute()", [
                (NONE, "Dữ liệu VERIFIED từ DB", "không phát event", NONE, N),
                (NONE, "Dữ liệu REJECTED kèm lý do", "khôi phục status, người duyệt, thời điểm, lý do", NONE, N),
            ]),
            ("pullDomainEvents()", [
                ("Đã pull 1 lần", "pull lần 2", "list rỗng", NONE, B),
            ]),
            ("verify()", [
                ("PENDING", "ACTOR_ID, DECIDED_AT, EXPIRES_AT", "VERIFIED; ghi người duyệt, thời điểm, hạn; không lý do", NONE, N),
                ("PENDING", "expiry = null", "VERIFIED, uỷ quyền không thời hạn", NONE, B),
                ("PENDING", "expiry = DECIDED_AT", "vẫn PENDING", IAE, B),
                ("PENDING", "actor null; actor \" \"; thời điểm null", "vẫn PENDING", IAE, A),
            ]),
            ("reject()", [
                ("PENDING", "lý do \"  Giay phep het han  \"", "REJECTED; lý do đã trim; ghi người duyệt, thời điểm", NONE, N),
                ("PENDING", "lý do null; \"\"; \"   \"", "vẫn PENDING", IAE, B),
                ("PENDING", "actor \" \"; thời điểm null", "vẫn PENDING", IAE, A),
            ]),
            ("Tự duyệt hồ sơ", [
                ("PENDING của user-123", "verify / reject với actor = user-123", "vẫn PENDING, chưa có người duyệt", "SelfKycDecisionException", A),
            ]),
            ("resubmit()", [
                ("REJECTED", NONE, "về PENDING; xoá lý do; giữ lịch sử người duyệt", NONE, N),
                ("REJECTED rồi resubmit", "verify bởi operator-2, sau 1 giờ", "VERIFIED bởi operator-2 với thời điểm mới", NONE, N),
            ]),
            ("Chuyển trạng thái sai", [
                ("VERIFIED", "verify; reject; resubmit", NONE, IVT, A),
                ("REJECTED", "verify; reject", "vẫn REJECTED", IVT, A),
                ("PENDING", "resubmit", "vẫn PENDING", IVT, A),
            ]),
            ("updateContactInfo()", [
                (NONE, "new@example.com, 0999999999, HCM", "email mới được lưu", NONE, N),
                (NONE, "null", "giữ thông tin cũ", NONE, B),
            ]),
            ("assignRole()", [
                ("Role BUYER", "OPERATOR", "true; role OPERATOR", NONE, N),
                ("Role ARBITRATOR", "ARBITRATOR", "false; không đổi", NONE, B),
                ("Role OPERATOR", "ARBITRATOR", "giữ OPERATOR", "RoleSeparationViolationException", A),
                ("Role ARBITRATOR", "OPERATOR", "giữ ARBITRATOR", "RoleSeparationViolationException", A),
                ("Role ADMIN", "ARBITRATOR", "true; role ARBITRATOR", NONE, N),
                ("Role BUYER", "null", "giữ BUYER", IAE, A),
            ]),
        ],
    },
    {
        "sheet": "AssignUserRole",
        "module": "user-service",
        "desc": "AssignUserRoleUseCaseImpl: ADMIN gán role, giữ tách biệt OPERATOR/ARBITRATOR",
        "pre": "UserProfileRepository mock",
        "groups": [
            ("execute()", [
                ("Không có user", "OPERATOR", "không update", NSE, A),
                ("User BUYER", "INSPECTOR", "update role INSPECTOR; response INSPECTOR", NONE, N),
                ("User OPERATOR", "OPERATOR", "trả OPERATOR, không update", NONE, B),
                ("User OPERATOR", "ARBITRATOR", "không update", "RoleSeparationViolationException", A),
            ]),
        ],
    },
    {
        "sheet": "GetInternalUserInfo",
        "module": "user-service",
        "desc": "GetInternalUserInfoUseCaseImpl: thông tin đủ điều kiện cho service nội bộ",
        "pre": "seller-1 \"HTX Ca Phe\", SELLER",
        "groups": [
            ("execute()", [
                ("VERIFIED, có hạn uỷ quyền và lockedUntil", "seller-1", "trả đủ: tổ chức, email, role, status, hạn, lockedUntil", NONE, N),
                ("PENDING, không hạn, không khoá", "seller-1", "authorizationExpiresAt, lockedUntil = null", NONE, B),
                ("Không có user", "seller-1", NONE, NSE, A),
            ]),
        ],
    },
    {
        "sheet": "GetKycProfile",
        "module": "user-service",
        "desc": "GetKycProfileUseCaseImpl: xem hồ sơ KYC, bắt buộc ghi audit đọc",
        "pre": "Actor OPERATOR; ReadAuditRepository mock",
        "groups": [
            ("execute()", [
                ("Hồ sơ PENDING tồn tại", "target, actor, \"OPERATOR\"", "ghi audit (actor, role, target) rồi trả hồ sơ đầy đủ", NONE, N),
                ("Không có user", "target", "không ghi audit", NSE, A),
                ("Ghi audit lỗi (DB down)", "target", "không trả dữ liệu", "ReadAuditUnavailableException", A),
            ]),
        ],
    },
    {
        "sheet": "ListUsersByVerification",
        "module": "user-service",
        "desc": "ListUsersByVerificationStatusUseCaseImpl: hàng đợi KYC theo status, phân trang",
        "pre": "UserProfileRepository mock",
        "groups": [
            ("execute()", [
                ("1 hồ sơ PENDING", "status = null, page 0, size 20", "mặc định PENDING; 1 phần tử seller-1; page 0, size 20", NONE, B),
                ("10 hồ sơ REJECTED", "REJECTED, page 2, size 5", "content rỗng; page 2, size 5, total 10", NONE, B),
            ]),
        ],
    },
    {
        "sheet": "RejectUser",
        "module": "user-service",
        "desc": "RejectUserUseCaseImpl: từ chối KYC với giờ server và actor",
        "pre": "UserProfileRepository mock; clock cố định NOW",
        "groups": [
            ("execute()", [
                ("Không có user", "lý do \"Thieu giay phep\"", "không update", NSE, A),
                ("PENDING", "lý do \"Thieu giay phep\"", "REJECTED; lý do, actor, thời điểm = NOW", NONE, N),
                ("PENDING", "lý do \"  \"", "không update", IAE, B),
                ("Đã REJECTED lần 1", "lý do \"Lan 2\"", "không update", IVT, A),
            ]),
        ],
    },
    {
        "sheet": "VerifyUser",
        "module": "user-service",
        "desc": "VerifyUserUseCaseImpl: duyệt KYC với giờ server và actor",
        "pre": "UserProfileRepository mock; clock cố định NOW",
        "groups": [
            ("execute()", [
                ("Không có user", "EXPIRES_AT", "không update", NSE, A),
                ("PENDING", "EXPIRES_AT", "VERIFIED; actor, thời điểm = NOW, hạn = EXPIRES_AT", NONE, N),
                ("Clock 2026-10-07T03:00:00.123456789Z", "EXPIRES_AT", "verifiedAt cắt về .123456Z", NONE, B),
                ("Đã VERIFIED", "EXPIRES_AT", "không update", IVT, A),
                ("PENDING", "expiry = NOW", "không update", IAE, B),
                ("PENDING", "actor = chính user đó", "không update", "SelfKycDecisionException", A),
            ]),
        ],
    },
]
