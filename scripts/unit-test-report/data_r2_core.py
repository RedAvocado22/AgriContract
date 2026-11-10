# bank, escrow, shared and api-gateway unit tests added after 31/10/2026 (one row per scenario)
N, A, B = "Normal", "Abnormal", "Boundary"
NONE = "N/A"
IEP = "InvalidEventPayloadException"

MODULES = [
    {
        "sheet": "LedgerCommandConsumer",
        "test_class": "LedgerCommandConsumerTest",
        "module": "bank-service",
        "date": "07/11/2026",
        "desc": "LedgerCommandConsumer: parse lệnh bank.*_requested thành LedgerCommand",
        "pre": "Event bank.lock_requested hợp lệ theo schema đã chốt",
        "groups": [
            ("toCommand()", [
                (NONE, "bank.lock_requested hợp lệ", "entryType LOCK_BUYER_DEPOSIT, fundType BUYER_DEPOSIT, đủ id và số tiền", NONE, N),
                (NONE, "bank.release_requested / bank.refund_requested hợp lệ", "đúng loại bút toán của từng lệnh", NONE, N),
                (NONE, "Queue của lệnh này nhận event hoặc loại bút toán của lệnh khác", NONE, IEP, A),
                (NONE, "Payload ngoài schema (thiếu field, field lạ, sai kiểu, số tiền sai định dạng)", NONE, IEP, A),
            ]),
        ],
    },
    {
        "sheet": "ProcessLedgerCommand",
        "test_class": "ProcessLedgerCommandUseCaseImplTest",
        "module": "bank-service",
        "date": "07/11/2026",
        "desc": "ProcessLedgerCommandUseCaseImpl: ghi bút toán 1 lần theo sourceEventId và báo kết quả",
        "pre": "Ledger và outbox là fake in-memory",
        "groups": [
            ("execute()", [
                (NONE, "Lệnh lần đầu", "ghi đúng 1 bút toán; publish kết quả completed", NONE, N),
                ("Đã ghi lệnh này", "Lệnh lặp lại (cùng sourceEventId)", "không ghi thêm; xác nhận lại đúng bút toán cũ", NONE, B),
                ("Đã ghi lệnh này", "Cùng sourceEventId nhưng nội dung khác", "không ghi thêm; không xác nhận", NONE, A),
                (NONE, "Lệnh vi phạm luật sổ cái", "publish kết quả failed", NONE, A),
                ("Request khác chèn trước cùng sourceEventId", "Lệnh thua race khi insert", "xác nhận bút toán của bên thắng", NONE, B),
            ]),
        ],
    },
    {
        "sheet": "ActivationDeposits",
        "test_class": "ActivationDepositsTest",
        "module": "escrow-service",
        "date": "08/11/2026",
        "desc": "ActivationDeposits: gom trạng thái các leg cọc để biết bên nào lỗi",
        "pre": "Hợp đồng có leg cọc BUYER và SELLER; lịch retry 1 lần",
        "groups": [
            ("failedLeg()", [
                (NONE, "Buyer LOCKED, seller hết lượt retry", "FailedLeg = SELLER", NONE, N),
                (NONE, "Một leg hết lượt, leg kia còn đang xử lý", "chưa coi là lỗi", NONE, B),
                (NONE, "Cả 2 leg hết lượt; hợp đồng chỉ có cọc bên mua lỗi", "BOTH; hợp đồng 1 leg cũng lỗi được", NONE, N),
            ]),
            ("restartFailedLegs()", [
                (NONE, "1 leg FAILED, 1 leg LOCKED", "leg lỗi về REQUESTED với vòng retry mới; leg LOCKED giữ nguyên", NONE, N),
            ]),
        ],
    },
    {
        "sheet": "BankLockResultConsumer",
        "test_class": "BankLockResultConsumerTest",
        "module": "escrow-service",
        "date": "08/11/2026",
        "desc": "BankLockResultConsumer: parse bank.lock_completed / bank.lock_failed",
        "pre": "Event kết quả khoá hợp lệ theo schema",
        "groups": [
            ("toCommand()", [
                (NONE, "bank.lock_completed", "lệnh LOCKED cho leg tương ứng", NONE, N),
                (NONE, "bank.lock_failed", "lệnh FAILED kèm lý do", NONE, N),
                (NONE, "Không có milestoneId", "chấp nhận (cọc cấp hợp đồng)", NONE, B),
                (NONE, "Payload ngoài schema", NONE, IEP, A),
                (NONE, "Sai eventType hoặc producer", NONE, IEP, A),
            ]),
        ],
    },
    {
        "sheet": "DepositLegResult",
        "test_class": "DepositLegResultTest",
        "module": "escrow-service",
        "date": "08/11/2026",
        "desc": "DepositLeg: nhận kết quả khoá, lịch retry khi lỗi",
        "pre": "Leg REQUESTED; lịch retry 5m / 30m / 2h; clock cố định",
        "groups": [
            ("markLocked()", [
                (NONE, "Kết quả completed (kể cả gửi lặp)", "leg LOCKED, chỉ 1 lần", NONE, N),
                ("Leg đã lỗi 1 lần", "Lần retry thành công", "leg LOCKED", NONE, N),
            ]),
            ("markFailed()", [
                (NONE, "Lỗi liên tiếp", "RETRY_SCHEDULED theo lịch; hết lịch → RETRIES_EXHAUSTED, leg FAILED, 4 lần lỗi", NONE, B),
                ("Leg LOCKED", "Kết quả lỗi tới muộn", "IGNORED_ALREADY_LOCKED, leg vẫn LOCKED", NONE, B),
            ]),
            ("retryRequested()", [
                ("Leg chờ retry", "Gọi trước / đúng giờ retry; gọi khi leg không lỗi", "chỉ đúng giờ mới về REQUESTED", "IllegalStateException khi leg không lỗi", B),
            ]),
            ("new RetrySchedule()", [
                (NONE, "Danh sách rỗng hoặc có khoảng 0", NONE, "RuntimeException", A),
            ]),
        ],
    },
    {
        "sheet": "DepositLegReturn",
        "test_class": "DepositLegReturnTest",
        "module": "escrow-service",
        "date": "08/11/2026",
        "desc": "DepositLeg: trả cọc khi huỷ kích hoạt",
        "pre": "Leg ở các trạng thái LOCKED / FAILED / REQUESTED; lịch retry trả cọc",
        "groups": [
            ("stopForRefund()", [
                ("Leg LOCKED", "Yêu cầu dừng để hoàn cọc (kể cả gửi lặp)", "RETURN_REQUESTED với id lệnh mới; lần lặp → ALREADY_STOPPED", NONE, N),
                ("Leg FAILED", "Yêu cầu dừng", "CANCELLED; lỗi tới sau → IGNORED_CANCELLED, không retry nữa", NONE, N),
                ("Lệnh khoá đang bay", "Yêu cầu dừng", "WAITING_FOR_LOCK_RESULT; khoá xong → RETURN_REQUESTED, khoá lỗi → CANCELLED", NONE, B),
            ]),
            ("markReturned() / markReturnFailed()", [
                ("Leg RETURN_REQUESTED", "Kết quả trả thành công (kể cả lặp)", "xác nhận đã trả 1 lần", NONE, N),
                ("Leg RETURN_REQUESTED", "Bank từ chối trả liên tiếp", "gửi lại theo lịch; hết lịch → RETURN_FAILED", NONE, B),
                ("Leg không có lệnh trả", "Kết quả trả tới", NONE, "InvalidDepositLegException", A),
            ]),
            ("ActivationDeposits.refundSettled()", [
                (NONE, "Các leg CANCELLED / RETURNED / còn đang trả", "chỉ xong khi mọi leg CANCELLED hoặc RETURNED", NONE, N),
            ]),
        ],
    },
    {
        "sheet": "VietnamBusinessDays",
        "test_class": "VietnamBusinessDaysTest",
        "module": "shared",
        "date": "08/11/2026",
        "desc": "VietnamBusinessDays: cộng ngày làm việc (bỏ cuối tuần, ngày lễ)",
        "pre": "Danh sách ngày lễ cấu hình qua VIETNAM_HOLIDAYS",
        "groups": [
            ("plusBusinessDays()", [
                (NONE, "+5 ngày qua cuối tuần và ngày lễ liền nhau", "bỏ qua đủ cuối tuần và lễ", NONE, N),
                (NONE, "Thời điểm gần nửa đêm, JVM khác múi giờ", "tính theo ngày giờ Việt Nam (ICT)", NONE, B),
                (NONE, "Cộng qua năm mới", "bỏ qua Tết Dương lịch", NONE, B),
                (NONE, "+0 / +31 ngày", NONE, "IllegalArgumentException", B),
                (NONE, "+30 ngày", "chấp nhận", NONE, B),
            ]),
            ("Cấu hình ngày lễ", [
                (NONE, "Có khoảng trắng, trùng, token rỗng", "bỏ qua, đọc đúng", NONE, B),
                (NONE, "Thiếu hoặc sai định dạng", NONE, "IllegalArgumentException nêu tên VIETNAM_HOLIDAYS", A),
            ]),
        ],
    },
    {
        "sheet": "EventingAutoConfiguration",
        "test_class": "EventingAutoConfigurationTest",
        "module": "shared",
        "date": "07/11/2026",
        "desc": "EventingAutoConfiguration: bean outbox/codec tự cấu hình theo classpath và property",
        "pre": "ApplicationContextRunner với các tổ hợp classpath và property",
        "groups": [
            ("Mặc định", [
                (NONE, "Không bật poller", "có writer, store, codec; poller tắt", NONE, N),
                (NONE, "Không có JdbcTemplate", "không tạo writer và store", NONE, B),
                (NONE, "Chỉ có codec, không JDBC/Rabbit", "khởi động với codec", NONE, B),
            ]),
            ("Bật poller", [
                (NONE, "Có exchange và publisher confirms", "có poller và stale monitor", NONE, N),
                (NONE, "Có kèm OutboxPoller cũ của service", "cả hai cùng tồn tại", NONE, N),
                (NONE, "Không khai báo chu kỳ", "dùng chu kỳ mặc định", NONE, N),
                (NONE, "Chu kỳ dạng PT1S", NONE, "Khởi động lỗi, nêu tên key poll-interval", A),
                (NONE, "Chu kỳ dạng mili giây", "chấp nhận", NONE, B),
                (NONE, "Thiếu exchange", NONE, "Khởi động lỗi", A),
                (NONE, "Không bật publisher confirms", NONE, "Khởi động lỗi, nêu publisher-confirm-type=correlated", A),
            ]),
        ],
    },
    {
        "sheet": "ClientIpRateLimitFilter",
        "test_class": "ClientIpRateLimitGatewayFilterFactoryTest",
        "module": "api-gateway",
        "date": "02/11/2026",
        "desc": "ClientIpRateLimitGatewayFilterFactory: giới hạn request theo IP client",
        "pre": "Giới hạn N request / cửa sổ; clock điều khiển được",
        "groups": [
            ("filter()", [
                (NONE, "Vượt N request trong cửa sổ", "429 tới hết cửa sổ", NONE, B),
                (NONE, "2 IP khác nhau", "mỗi IP một bucket riêng", NONE, N),
                ("Không có proxy tin cậy", "Client tự gửi X-Forwarded-For", "không đổi được bucket (vẫn 429)", NONE, A),
                ("1 proxy tin cậy", "Request qua proxy", "bucket theo địa chỉ proxy thêm vào", NONE, N),
            ]),
            ("apply()", [
                (NONE, "Giới hạn ≤ 0", NONE, "IllegalArgumentException", B),
            ]),
        ],
    },
    {
        "sheet": "CorsConfig",
        "test_class": "CorsConfigTest",
        "module": "api-gateway",
        "date": "02/11/2026",
        "desc": "CorsConfig: đọc allowlist origin",
        "pre": NONE,
        "groups": [
            ("corsConfiguration()", [
                (NONE, "Danh sách origin cụ thể", "đúng các origin, không cho credentials", NONE, N),
                (NONE, "Origin \"*\" hoặc có wildcard", NONE, "IllegalStateException lúc khởi động", A),
            ]),
        ],
    },
]
