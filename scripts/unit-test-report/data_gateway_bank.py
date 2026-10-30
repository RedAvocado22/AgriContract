N, A, B = "Normal", "Abnormal", "Boundary"
NONE = "N/A"

MODULES = [
    {
        "sheet": "UserContextInjectionFilter",
        "module": "api-gateway",
        "desc": "UserContextInjectionFilter: thay header danh tính bằng claim JWT, gắn gateway secret",
        "pre": "Filter dựng với SECRET cố định; chain mock giữ lại request được forward",
        "groups": [
            ("filter() – request có JWT", [
                ("JWT user-123, user@agri.test, role BUYER", "Header giả: X-User-Id=attacker, X-User-Role=ADMIN, X-Gateway-Secret=forged, Authorization", "forward X-User-Id=user-123, X-User-Role=BUYER, X-User-Email=user@agri.test, X-Gateway-Secret=SECRET; bỏ Authorization", NONE, A),
                ("JWT role: default-roles-agricontract, offline_access, SELLER, uma_authorization", "GET /api/v1/users/me", "X-User-Role = SELLER (bỏ role housekeeping của realm)", NONE, N),
                ("JWT đủ 6 role nghiệp vụ + offline_access", "GET /api/v1/users/me", "X-User-Role = BUYER,SELLER,ADMIN,OPERATOR,ARBITRATOR,INSPECTOR", NONE, N),
                ("JWT không có email", "GET /api/v1/users/me", "không forward", "ResponseStatusException 401 UNAUTHORIZED", A),
                ("JWT role rỗng", "GET /api/v1/users/me", "không forward", "ResponseStatusException 403 FORBIDDEN", A),
                ("JWT chỉ có offline_access", "GET /api/v1/users/me", "không forward", "ResponseStatusException 403 FORBIDDEN", B),
            ]),
            ("filter() – request ẩn danh", [
                ("Không JWT", "X-User-Id=attacker, X-User-Role=ADMIN, X-Gateway-Secret=forged", "bỏ X-User-*; X-Gateway-Secret = SECRET", NONE, A),
                ("Không JWT", "x-user-tenant, X-USER-ID, X-Internal-Secret, X-Correlation-Id=keep-me", "bỏ mọi header x-user-* (không phân biệt hoa thường) và X-Internal-Secret; giữ X-Correlation-Id", NONE, B),
                ("Không JWT", "Authorization: bearer not-parsed-by-security", "không forward Authorization", NONE, A),
            ]),
        ],
    },
    {
        "sheet": "LedgerEntry",
        "module": "bank-service",
        "desc": "LedgerEntry: quy tắc tạo bút toán (fund, milestone, remedy, id)",
        "pre": "AMOUNT và NOW cố định; id() sinh UUID ngẫu nhiên",
        "groups": [
            ("create()", [
                (NONE, "LOCK_BUYER_DEPOSIT, BUYER_DEPOSIT", "entryId là UUID hợp lệ; createdAt = NOW; amount = AMOUNT", NONE, N),
                (NONE, "RELEASE_TO_SELLER, BUYER_DEPOSIT", NONE, "InvalidLedgerEntryException \"cannot move\"", A),
                (NONE, "LOCK_MILESTONE không milestoneId; LOCK_SELLER_DEPOSIT có milestoneId; REFUND_TO_BUYER MILESTONE_PAYMENT có milestoneId", "case 3 tạo được", "case 1: \"needs a milestoneId\"; case 2: \"contract-level\"", A),
                (NONE, "REFUND_TO_BUYER chỉ có 1 trong 2 remedy id", NONE, "InvalidLedgerEntryException \"come together\"", A),
                (NONE, "LOCK_BUYER_DEPOSIT kèm remedy id", NONE, "InvalidLedgerEntryException \"never a remedy leg\"", A),
                (NONE, "DEPOSIT_FORFEITURE / CONTRACTUAL_PENALTY không remedy; DAMAGES_COMPENSATION đủ remedy", "DAMAGES_COMPENSATION tạo được", "2 case đầu: InvalidLedgerEntryException \"needs a remedy decision\"", A),
                (NONE, "RELEASE_TO_SELLER có và không có remedy id", "cả 2 tạo được", NONE, N),
                (NONE, "sourceEventId = null; contractId = \"contract-1\"; entryType = null", NONE, "InvalidLedgerEntryException (\"sourceEventId\", \"not a UUID\")", A),
            ]),
        ],
    },
    {
        "sheet": "Money",
        "module": "bank-service",
        "desc": "Money (bank): chuẩn hoá 2 chữ số thập phân, giới hạn DECIMAL(18,2)",
        "pre": NONE,
        "groups": [
            ("new Money()", [
                (NONE, "\"100\"", "scale = 2, giá trị 100.00", NONE, N),
                (NONE, "\"100.5\"", "scale = 2, giá trị 100.50", NONE, N),
                (NONE, "\"100.50\"", "scale = 2, giá trị 100.50", NONE, N),
                (NONE, "\"100.000\"", "scale = 2, giá trị 100.00 (không làm tròn)", NONE, B),
                (NONE, "\"100.005\"", NONE, "InvalidLedgerEntryException \"2 decimal places\"", B),
                (NONE, "\"0.00\"", NONE, "InvalidLedgerEntryException \"strictly positive\"", B),
                (NONE, "\"-0.01\"", NONE, "InvalidLedgerEntryException \"strictly positive\"", B),
                (NONE, "null", NONE, "InvalidLedgerEntryException", A),
                (NONE, "\"9999999999999999.99\" rồi \"10000000000000000.00\"", "giá trị đầu tạo được", "giá trị sau: InvalidLedgerEntryException \"DECIMAL(18,2)\"", B),
            ]),
            ("equals()", [
                (NONE, "\"100.5\" và \"100.50\"", "2 Money bằng nhau", NONE, N),
            ]),
        ],
    },
    {
        "sheet": "LockRequestConsumer",
        "module": "bank-service",
        "desc": "LockRequestConsumer: parse event yêu cầu khoá tiền thành LockFundsCommand",
        "pre": "Event hợp lệ theo contract frozen, amount 40.000.000,00",
        "groups": [
            ("toCommand()", [
                (NONE, "Event hợp lệ", "command map đủ eventId, correlationId, contractId; LOCK_BUYER_DEPOSIT / BUYER_DEPOSIT; amount 40000000.00", NONE, N),
            ] + [
                (NONE, why, NONE, "InvalidEventPayloadException", typ)
                for why, typ in [
                    ("sourceEventId khác eventId", A),
                    ("thiếu correlationId", A),
                    ("entryType không phải lock", A),
                    ("amount 1 chữ số thập phân", B),
                    ("amount dạng number thay vì string", A),
                    ("amount = 0", B),
                    ("thiếu key bắt buộc (nullable)", A),
                    ("payload có field lạ", A),
                    ("producer sai", A),
                    ("body không phải JSON", A),
                ]
            ]),
        ],
    },
    {
        "sheet": "ProcessLockRequest",
        "module": "bank-service",
        "desc": "ProcessLockRequestUseCaseImpl: ghi bút toán lock, idempotent theo sourceEventId",
        "pre": "Ledger và publisher kết quả là fake in-memory",
        "groups": [
            ("execute()", [
                ("Ledger rỗng", "Lock buyer deposit 40.000.000,00", "ghi 1 entry; publish completed với entryId đó", NONE, N),
                ("Ledger rỗng", "Cùng command gửi 2 lần", "chỉ append 1 lần; completed 2 lần cùng entryId", NONE, B),
                ("Đã xử lý command 40.000.000,00", "Cùng sourceEventId, amount 50.000.000,00", "không append, không confirm thêm, không failed", NONE, A),
                ("Ledger rỗng", "LOCK_MILESTONE thiếu milestoneId", "không ghi; publish failed \"needs a milestoneId\"", NONE, A),
                ("Request khác đã insert trước (race)", "Lock buyer deposit", "confirm entryId của bên thắng race", NONE, B),
            ]),
        ],
    },
]
