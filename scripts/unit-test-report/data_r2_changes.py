# Round 2 (07/11/2026) changes to the 31/10 sheets, plus unit test classes the 31/10 run missed because another
# service has a test class with the same name. A row may carry a 6th element: the date its test reached main.
N, A, B = "Normal", "Abnormal", "Boundary"
NONE = "N/A"

# sheets whose test class no longer exists: bank lock classes were renamed into the ledger command classes
# (their cases continue there), the Java notification-service was replaced by the NestJS one, and
# SignContractUseCase was split into Contract.sign() and the signing endpoint.
REMOVED = {"LockRequestConsumer", "ProcessLockRequest", "NotificationEventConsumer", "ProcessNotification",
           "SignContract"}

# sheet -> rows to append (each with its date), grouped like the sheet's own groups
APPEND = {
    "Contract": [
        ("activate()", [
            ("Contract SIGNED", "(không tham số)", "status ACTIVE; không phát event Phase 1", NONE, N, "08/11/2026"),
            ("Contract chưa SIGNED", "(không tham số)", NONE, "ContractConflictException (NOT_ACTIVATABLE)", A, "08/11/2026"),
        ]),
    ],
    "AssignUserRole": [
        ("execute()", [
            ("Admin đang đăng nhập", "Đổi role của chính mình", "không update", "SelfRoleChangeException", A, "06/11/2026"),
            ("Admin đang đăng nhập", "Gán lại đúng role hiện tại của mình", "không update (ném lỗi thay vì bỏ qua)", "SelfRoleChangeException", B, "06/11/2026"),
        ]),
    ],
    "UserProfile": [
        ("assignRole()", [
            ("Role BUYER", "actor = chính user đó, role khác", "giữ role cũ", "SelfRoleChangeException", A, "06/11/2026"),
            ("Role BUYER", "actor = chính user đó, cùng role", "ném lỗi trước khi coi là không đổi", "SelfRoleChangeException", B, "06/11/2026"),
            ("Role BUYER", "actor rỗng", "giữ role cũ", "IllegalArgumentException", A, "06/11/2026"),
        ]),
    ],
    "VerifyUser": [
        ("execute()", [
            ("Profile đã bị người khác cập nhật (version cũ)", "verify", "không publish", "ConcurrentProfileUpdateException", A, "06/11/2026"),
        ]),
    ],
    "RejectUser": [
        ("execute()", [
            ("Profile đã bị người khác cập nhật (version cũ)", "reject", "không publish", "ConcurrentProfileUpdateException", A, "06/11/2026"),
        ]),
    ],
    "Category": [
        ("approve()", [
            ("Category PENDING", "commodity = null", "vẫn PENDING", "IllegalArgumentException", A, "10/11/2026"),
        ]),
        ("assignCommodity()", [
            ("Category APPROVED cũ chưa có commodity", "COFFEE", "commodity COFFEE, không phát event", NONE, N, "10/11/2026"),
            ("Đã có commodity", "COFFEE", NONE, "IllegalStateException", A, "10/11/2026"),
            ("Category chưa APPROVED", "COFFEE", NONE, "IllegalStateException", A, "10/11/2026"),
            ("Category APPROVED", "null", NONE, "IllegalArgumentException", A, "10/11/2026"),
        ]),
    ],
    "CreateListing": [
        ("execute() – điều kiện seller", [
            (NONE, "user-service không trả lời", "không đọc gì thêm", "UserServiceUnavailableException (fail closed)", A, "10/11/2026"),
            ("Seller đang bị khoá uy tín", "Tạo listing", NONE, "Từ chối (403)", A, "10/11/2026"),
            ("Khoá hết hạn đúng thời điểm hiện tại", "Tạo listing", "tạo được listing", NONE, B, "10/11/2026"),
            ("Seller chưa KYC", "Tạo listing", NONE, "Từ chối (403)", A, "10/11/2026"),
            ("Thẩm quyền đại diện hết hạn", "Tạo listing", NONE, "Từ chối (403)", A, "10/11/2026"),
            ("Không có hồ sơ user", "Tạo listing", NONE, "Từ chối (403)", A, "10/11/2026"),
        ]),
    ],
    "CreateProduct": [
        ("execute()", [
            ("Category cũ vừa được Operator gán commodity", "Tạo product", "tạo được product", NONE, N, "10/11/2026"),
        ]),
    ],
    "PlotRegistryEntry": [
        ("geoCheck()", [
            ("Plot PENDING", "Chồng lấn rừng tự nhiên", "CHECKED, HIGH_RISK; vẫn dùng được cho listing", NONE, N, "07/11/2026"),
            ("Plot PENDING", "Chỉ chồng vùng không rõ / không chồng", "INCONCLUSIVE / LOW_RISK", NONE, B, "07/11/2026"),
            ("Plot không PENDING", "Kiểm tra", "mức rủi ro giữ nguyên", "IllegalStateException", A, "07/11/2026"),
        ]),
    ],
    "ProposeCategory": [
        ("execute()", [
            ("Chưa có tên \"ca phe\"", "\"Cà Phê\"", "lưu kèm email người đề xuất lấy từ user-service; trả PENDING", NONE, N, "10/11/2026"),
            ("Đã có tên trùng sau chuẩn hoá", "\"Ca Phe\"", "không gọi user-service, không lưu", "DuplicateCategoryException", A, "10/11/2026"),
            (NONE, "user-service không trả lời", "không lưu", "UserServiceUnavailableException (fail closed)", A, "10/11/2026"),
        ]),
    ],
    "ReputationFactConsumer": [
        ("onMessage()", [
            (NONE, "eventVersion dạng chuỗi", "không ghi fact", "AmqpRejectAndDontRequeueException", A, "06/11/2026"),
            (NONE, "Envelope sai", "message lỗi chung, không lộ giá trị hay nguyên nhân", "AmqpRejectAndDontRequeueException", A, "06/11/2026"),
        ]),
    ],
    "EventEnvelopeCodec": [
        ("decode()", [
            (NONE, "Yêu cầu Level 1.5 có actor bắt buộc nhưng null", "giữ null, không tự thêm field thiếu", NONE, B, "10/11/2026"),
        ]),
    ],
    "InternalUserEligibility": [
        ("loadEligiblePool()", [
            (NONE, "Đọc danh sách inspector", "gọi endpoint nội bộ riêng; response chỉ có danh tính tối thiểu", NONE, N, "10/11/2026"),
            (NONE, "Danh sách rỗng", "hợp lệ", NONE, B, "10/11/2026"),
            (NONE, "Response hỏng", NONE, "EligibilityUnavailableException (không coi là rỗng)", A, "10/11/2026"),
            (NONE, "user-service không trả lời", NONE, "EligibilityUnavailableException (không coi là rỗng)", A, "10/11/2026"),
            (NONE, "Danh sách có id trùng", "không làm lệch việc chọn inspector", NONE, B, "10/11/2026"),
        ]),
    ],
    "UserContextInjectionFilter": [
        ("filter() – request có JWT", [
            ("JWT không có email", "GET /api/v1/users/me", "forward, không có header X-User-Email", NONE, B, "10/11/2026"),
            ("JWT có auth_time dạng số", "GET", "forward auth_time dạng epoch seconds", NONE, N, "08/11/2026"),
            ("JWT có auth_time dạng Instant", "GET", "forward auth_time dạng epoch seconds", NONE, N, "08/11/2026"),
            ("JWT không có auth_time", "Client tự gửi header auth_time", "không forward auth_time", NONE, A, "08/11/2026"),
        ]),
        ("filter() – request ẩn danh", [
            ("Không JWT", "Header auth_time giả", "bị xoá", NONE, A, "08/11/2026"),
        ]),
    ],
}

# rows of a sheet that describe a removed test: (sheet, group, input text of the row)
DROP_ROWS = {
    ("UserContextInjectionFilter", "filter() – request có JWT", "GET /api/v1/users/me", "JWT không có email"),
    # renamed tests: the new rows in APPEND replace them
    ("ProposeCategory", "execute()", 'category-1, "Cà Phê"', 'Chưa có tên "ca phe"'),
    ("ProposeCategory", "execute()", '"Ca Phe"', 'Đã có category "ca phe" (bất kể status)'),
}
# whole groups of a sheet whose tests were removed
DROP_GROUPS = {("Contract", "sign()"), ("Contract", "activate()")}

DLQ_ROWS = lambda svc, date: [
    ("checkDlqDepth()", [
        ("DLQ có 7 message", NONE, f"1 dòng WARN event={svc}_dlq_nonempty, nêu queue và số message", NONE, N, date),
        ("Mọi DLQ rỗng", NONE, "không log; chỉ đếm thụ động", NONE, N, date),
        ("Probe lỗi", NONE, "log chỉ nêu queue, không lộ chi tiết broker; vẫn đóng connection", NONE, A, date),
        ("Broker không kết nối được", NONE, "lỗi không thoát ra khỏi lần chạy theo lịch", NONE, A, date),
    ]),
    ("Constructor", [
        (NONE, NONE, "dùng cấu hình connection riêng, có giới hạn thời gian", NONE, N, date),
    ]),
]

NEW_MODULES = [
    {
        "sheet": "UserDlqMonitor",
        "test_class": "DlqMonitorTest",
        "module": "user-service",
        "date": "06/11/2026",
        "desc": "DlqMonitor (user-service): cảnh báo khi DLQ có message",
        "pre": "RabbitMQ connection factory giả lập; appender bắt log",
        "groups": DLQ_ROWS("user", "06/11/2026"),
    },
    {
        "sheet": "InspectionDlqMonitor",
        "test_class": "DlqMonitorTest",
        "module": "inspection-service",
        "date": "10/11/2026",
        "desc": "DlqMonitor (inspection-service): cảnh báo khi DLQ có message",
        "pre": "RabbitMQ connection factory giả lập; appender bắt log",
        "groups": DLQ_ROWS("inspection", "10/11/2026") + [
            ("Cấu hình bean", [
                (NONE, "Property tắt monitor", "không tạo bean monitor", NONE, B, "10/11/2026"),
                (NONE, "Mặc định", "bật, dùng probe riêng", NONE, N, "10/11/2026"),
            ]),
        ],
    },
    {
        "sheet": "ProductDlqMonitor",
        "test_class": "DlqMonitorTest",
        "module": "product-service",
        "date": "07/11/2026",
        "desc": "DlqMonitor (product-service): cảnh báo khi DLQ có message",
        "pre": "RabbitMQ connection factory giả lập; appender bắt log",
        "groups": [
            ("checkDlqDepth()", [
                ("DLQ có message", NONE, "log cảnh báo", NONE, N, "07/11/2026"),
                ("DLQ rỗng", NONE, "không log", NONE, N, "07/11/2026"),
                ("Probe lỗi", NONE, "log chỉ nêu queue, không ném lỗi", NONE, A, "07/11/2026"),
            ]),
            ("Constructor", [
                (NONE, NONE, "connection probe có giới hạn thời gian và tách riêng", NONE, N, "07/11/2026"),
                (NONE, "Thời gian ngoài giới hạn", NONE, "IllegalArgumentException", B, "07/11/2026"),
            ]),
        ],
    },
    {
        "sheet": "ProductUserEligibility",
        "test_class": "InternalUserEligibilityClientTest",
        "module": "product-service",
        "date": "10/11/2026",
        "desc": "InternalUserEligibilityClient (product-service): điều kiện seller từ user-service",
        "pre": "HTTP server giả lập user-service",
        "groups": [
            ("load()", [
                (NONE, "userId", "gọi endpoint nội bộ kèm secret; đọc đủ điều kiện", NONE, N, "10/11/2026"),
                ("User bị khoá", "userId", "trả kèm lockedUntil", NONE, N, "10/11/2026"),
                ("Không có hồ sơ", "userId", "rỗng, không coi là lỗi kết nối", NONE, B, "10/11/2026"),
                (NONE, "Mã lỗi khác; service sập; quá timeout; content type không phải JSON", NONE, "Unavailable (fail closed)", A, "10/11/2026"),
            ]),
            ("Constructor", [
                (NONE, "Secret rỗng", NONE, "IllegalArgumentException", A, "10/11/2026"),
            ]),
        ],
    },
    {
        "sheet": "ProductUserServiceClient",
        "test_class": "UserServiceClientTest",
        "module": "product-service",
        "date": "10/11/2026",
        "desc": "UserServiceClient (product-service): lấy email người dùng",
        "pre": "HTTP server giả lập user-service",
        "groups": [
            ("getEmail()", [
                (NONE, "User có email", "trả email; gửi kèm internal secret", NONE, N, "10/11/2026"),
                (NONE, "404", NONE, "UserProfileNotFoundException", A, "10/11/2026"),
                (NONE, "401; 5xx; response thiếu email", NONE, "UserServiceUnavailableException", A, "10/11/2026"),
            ]),
        ],
    },
]


def apply(modules):
    """Returns the 31/10 modules with round 2 changes; rows keep 5 fields (old) or 6 (with a date)."""
    out = []
    for m in modules:
        if m["sheet"] in REMOVED:
            continue
        groups = []
        for label, cases in m["groups"]:
            if (m["sheet"], label) in DROP_GROUPS:
                continue
            kept = [c for c in cases if (m["sheet"], label, c[1], c[0]) not in DROP_ROWS]
            groups.append((label, kept))
        for label, cases in APPEND.get(m["sheet"], []):
            for i, (g, existing) in enumerate(groups):
                if g == label:
                    groups[i] = (g, existing + cases)
                    break
            else:
                groups.append((label, cases))
        out.append({**m, "groups": groups, "old": True})
    return out
