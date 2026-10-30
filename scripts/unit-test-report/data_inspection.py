N, A, B = "Normal", "Abnormal", "Boundary"
NONE = "N/A"
UNAV = "EligibilityUnavailableException"
UNAV_MSG = UNAV + " \"Authoritative inspector eligibility could not be validated\", không kèm cause"
DENIED = "InspectionAssignmentAccessDeniedException"


def each(values, pre, fmt, ret, exc, typ):
    return [(pre, fmt.format(v), ret, exc, typ(v) if callable(typ) else typ) for v in values]


MODULES = [
    {
        "sheet": "InspectionAuthorization",
        "module": "inspection-service",
        "desc": "InspectionAuthorization: inspector chỉ truy cập commission/milestone được giao cho mình",
        "pre": "EligibilityGate, AssignmentRepository mock; actor = inspector-a",
        "groups": [
            ("requireCommission()", [
                ("inspector-a đủ điều kiện; commission giao cho inspector-a", "commission", "trả assignment đó", NONE, N),
                ("commission giao cho inspector-b", "commission", NONE, DENIED, A),
                ("Không có commission", "missing", NONE, DENIED + " \"No authorized inspection assignment\" (không lộ việc không tồn tại)", A),
                ("Assignment của inspector-a đã kết thúc", "commission", NONE, DENIED, B),
                ("Gate ném InspectorNotEligibleException", "commission", "không đọc assignment", "InspectorNotEligibleException", A),
            ]),
            ("requireMilestone()", [
                ("Assignment active (contract, milestone) của inspector-a", "(contract, milestone) rồi (other-contract, milestone)", "case 1 trả inspector-a", "case 2: " + DENIED, N),
                ("Assignment active thuộc inspector-b", "(contract, milestone)", NONE, DENIED, A),
                ("Gate ném EligibilityUnavailableException (user-service sập)", "(contract, milestone)", "không đọc assignment", UNAV, A),
            ]),
        ],
    },
    {
        "sheet": "InspectorEligibilityGate",
        "module": "inspection-service",
        "desc": "InspectorEligibilityGate: kiểm role, KYC, hạn giấy phép inspector; lỗi thì fail-closed",
        "pre": "SecurityContext có ACTOR role INSPECTOR; UserEligibilityPort mock; NOW cố định",
        "groups": [
            ("requireEligibleInspector()", [
                ("Profile INSPECTOR, VERIFIED, hết hạn NOW + 1s", NONE, "trả ACTOR", NONE, B),
                ("Profile INSPECTOR, VERIFIED, không hạn (null)", NONE, "trả ACTOR", NONE, N),
            ] + each(["BUYER", "SELLER", "ADMIN", "OPERATOR", "ARBITRATOR"],
                     "Đăng nhập không phải INSPECTOR", "Role đăng nhập = {}",
                     "không gọi user-service", "AccessDeniedException", A)
              + each(["BUYER", "SELLER", "ADMIN", "OPERATOR", "ARBITRATOR"],
                     "Đăng nhập INSPECTOR", "Profile user-service role = {}, VERIFIED",
                     NONE, "InspectorNotEligibleException", A)
              + each(["PENDING", "REJECTED", "UNKNOWN"],
                     "Đăng nhập INSPECTOR", "Profile INSPECTOR, status = {}",
                     NONE, "InspectorNotEligibleException", A)
              + each(["NOW - 1s", "NOW"],
                     "Đăng nhập INSPECTOR", "Profile VERIFIED, hết hạn = {}",
                     NONE, "InspectorNotEligibleException", B)
              + [
                ("user-service lỗi", NONE, NONE, UNAV, A),
                ("Không có profile", NONE, NONE, UNAV, A),
                ("Profile trả về userId = someone-else", NONE, NONE, UNAV, A),
                ("SecurityContext rỗng", NONE, "không gọi user-service", "AuthenticationCredentialsNotFoundException", A),
                ("Token chưa authenticated", NONE, NONE, "AuthenticationCredentialsNotFoundException", A),
                ("AnonymousAuthenticationToken có ROLE_INSPECTOR", NONE, NONE, "AuthenticationCredentialsNotFoundException", A),
            ]),
        ],
    },
    {
        "sheet": "InternalUserEligibility",
        "module": "inspection-service",
        "desc": "InternalUserEligibilityClient: gọi /internal/v1/users/{id}, mọi lỗi đều fail-closed",
        "pre": "HTTP server giả trong JVM trả response cấu hình được; secret test-service-secret",
        "groups": [
            ("load()", [
                ("Server trả profile hợp lệ", "ID", "gọi đúng /internal/v1/users/{ID} kèm X-Internal-Secret; profile đủ điều kiện", NONE, N),
                ("authorizationExpiresAt = 2027-06-30T16:59:59.999Z", "ID", "parse đúng thời điểm hết hạn", NONE, N),
            ]
            + each(["{}", "null", "[]", "not JSON"], NONE, "Body = {}", NONE, UNAV + " (malformed_response)", A)
            + [(NONE, "Body hợp lệ nhưng userId = \"x\"", NONE, UNAV + " (identity_mismatch)", A)]
            + each(["userId", "role", "verificationStatus", "authorizationExpiresAt"], NONE, "Body thiếu field {}", NONE, UNAV, A)
            + each(["0", "false", "\"\"", "\"invalid timestamp\""], NONE, "authorizationExpiresAt = {}", "không được hiểu thành vô hạn", UNAV, B)
            + each(["302", "401", "403", "404", "500", "503"], NONE, "HTTP {}", "không lộ body response", UNAV_MSG.replace(", không kèm cause", ""), A)
            + [
                (NONE, "Server đã tắt", NONE, UNAV + " (transport)", A),
                (NONE, "Server treo không trả header", NONE, UNAV_MSG, B),
                (NONE, "Body bị ngắt giữa chừng", NONE, UNAV_MSG, A),
                (NONE, "Body chứa email/secret nhạy cảm, sai định dạng", "không lộ nội dung vào message", UNAV_MSG, A),
            ]
            + each(["text/plain", "invalid-media-type"], NONE, "Content-Type = {}", NONE, UNAV_MSG, A)),
            ("Constructor", [
                (NONE, "secret = \" \"", NONE, "IllegalArgumentException", B),
            ]),
        ],
    },
    {
        "sheet": "GatewayAuthenticationFilter",
        "module": "inspection-service",
        "desc": "GatewayAuthenticationFilter: chỉ tin header danh tính khi có X-Gateway-Secret đúng",
        "pre": "Request có X-User-Id = inspector-a, X-User-Role = INSPECTOR; secret gateway-test-secret",
        "groups": [
            ("doFilter()", [
                (NONE, "Secret đúng", "SecurityContext = inspector-a trong request; xoá sau request", NONE, N),
                (NONE, "Không có X-Gateway-Secret (chỉ có X-Internal-Secret)", "401, không vào chain", NONE, A),
                (NONE, "X-Gateway-Secret = forged-secret", "401, không vào chain", NONE, A),
                ("SecurityContext đã có inspector-b", "Header inspector-a", "giữ inspector-b", NONE, A),
            ]
            + each(["X-User-Id", "X-User-Role", "X-Gateway-Secret"], NONE, "Header {} xuất hiện 2 lần", "401, không vào chain", NONE, A)
            + each(["null", "\"\"", "\" \"", "\"\\t\"", "\" \\t \""], NONE, "X-User-Role = {}", "401, không set SecurityContext", NONE, B)
            + [
                (NONE, "X-User-Role = ARBITRATOR", "authority ROLE_ARBITRATOR", "requireInspectorId(): AccessDeniedException", A),
                (NONE, "X-User-Role = \" BUYER, INSPECTOR,INSPECTOR \"", "authority ROLE_BUYER, ROLE_INSPECTOR (trim, bỏ trùng)", NONE, B),
                (NONE, "GET /actuator/health không header", "vào chain, không set SecurityContext", NONE, N),
            ]),
            ("Constructor", [
                (NONE, "secret = \" \"", NONE, "IllegalArgumentException", B),
            ]),
        ],
    },
]
