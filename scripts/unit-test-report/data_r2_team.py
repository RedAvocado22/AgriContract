# product, user, inspection and reputation unit tests added after 31/10/2026 (one row per scenario)
N, A, B = "Normal", "Abnormal", "Boundary"
NONE = "N/A"
IEP = "InvalidEventPayloadException"
IAE = "IllegalArgumentException"

MODULES = [
    # ── inspection-service ─────────────────────────────────────────────────
    {
        "sheet": "InspectionHash",
        "test_class": "InspectionHashTest",
        "module": "inspection-service",
        "date": "05/11/2026",
        "desc": "InspectionHash: băm biên bản giám định (RFC 8785) và kiểm tra cam kết",
        "pre": "Biên bản và kết quả giám định mẫu hợp lệ",
        "groups": [
            ("canonicalize() / hash()", [
                (NONE, "Bộ vector chuẩn RFC 8785 (số, thứ tự key lồng nhau)", "đúng chuỗi chuẩn hoá", NONE, N),
                (NONE, "Cùng dữ liệu, cách viết khác nhau", "cùng bytes, cùng hash", NONE, N),
                (NONE, "Unicode, escape, surrogate không hợp lệ", "sắp xếp/escape đúng; surrogate lỗi bị từ chối", IAE, B),
                (NONE, "Danh sách lý do", "giữ nguyên có/không và thứ tự", NONE, N),
                (NONE, "Thời điểm và phần chiếu biên bản", "giữ chính xác", NONE, N),
            ]),
            ("Kiểm tra đầu vào", [
                (NONE, "Field bắt buộc vắng hoặc null", NONE, IAE, A),
                (NONE, "Field chủ quan ở cả 2 cấp", NONE, IAE, A),
                (NONE, "Ép kiểu, key trùng, ký tự thừa cuối, giá trị sai", NONE, IAE, A),
                (NONE, "Metric thiếu field không null", NONE, IAE, A),
                (NONE, "Field chuỗi của biên bản không đúng kiểu", NONE, IAE, A),
                (NONE, "Giá trị sai khác; danh sách người gọi bị sửa sau khi đưa vào", NONE, IAE + " / không bị ảnh hưởng", B),
            ]),
            ("verify()", [
                (NONE, "Sửa bất kỳ field nào của kết quả", NONE, "InspectionCommitmentMismatchException", A),
                (NONE, "Sửa bất kỳ field nào của biên bản", NONE, "InspectionCommitmentMismatchException", A),
                (NONE, "Hash đúng / lệch / sai định dạng", "đúng thì qua, lệch và sai định dạng bị từ chối", IAE, N),
            ]),
        ],
    },
    {
        "sheet": "InspectionHashes",
        "test_class": "InspectionHashesTest",
        "module": "inspection-service",
        "date": "05/11/2026",
        "desc": "InspectionHashes: biên số liệu trước khi băm (thời điểm, UUID, khối lượng, phần trăm)",
        "pre": "Kết quả giám định mẫu",
        "groups": [
            ("Kiểm tra biên", [
                (NONE, "Thời điểm không chuẩn hoặc sai", NONE, IAE, A),
                (NONE, "Thời điểm chuẩn, id chữ thường", "chấp nhận", NONE, N),
                (NONE, "UUID chữ hoa", NONE, IAE + " (không tạo cam kết thứ hai cho cùng id)", B),
                (NONE, "Khối lượng có số 0 thừa cuối, đúng biên", "chấp nhận", NONE, B),
                (NONE, "Khối lượng ngoài miền giá trị", NONE, IAE, B),
                (NONE, "Phần trăm có số 0 thừa cuối, đúng biên", "chấp nhận", NONE, B),
                (NONE, "Phần trăm ngoài miền giá trị", NONE, IAE, B),
                (NONE, "Khối lượng chấp nhận > khối lượng đo", NONE, IAE, A),
                (NONE, "2 số khác nhau rất gần nhau", "không bị gộp thành 1 khi chuẩn hoá", NONE, B),
                (NONE, "Hash lệch vs hash sai định dạng", "2 lỗi phân biệt", NONE, A),
            ]),
        ],
    },
    {
        "sheet": "InspectionHashContract",
        "test_class": "InspectionHashContractTest",
        "module": "inspection-service",
        "date": "05/11/2026",
        "desc": "Kết quả giám định khớp 2 schema đã chốt",
        "pre": "Schema trong contracts/",
        "groups": [
            ("Sinh kết quả", [
                (NONE, "Kết quả sinh ra; thêm field chính sách", "khớp cả 2 schema; field chính sách bị từ chối", NONE, N),
                (NONE, "Thời điểm nguồn của reportHash", "lấy confirmedAt, không đổi tên key", NONE, N),
            ]),
        ],
    },
    {
        "sheet": "AssignmentConfiguration",
        "test_class": "AssignmentConfigurationTest",
        "module": "inspection-service",
        "date": "10/11/2026",
        "desc": "Cấu hình phân công giám định và lịch ngày làm việc",
        "pre": NONE,
        "groups": [
            ("Khởi động", [
                (NONE, "Lịch sai định dạng", NONE, "Khởi động lỗi (không âm thầm chỉ bỏ cuối tuần)", A),
                (NONE, "Thiếu cấu hình", NONE, "Khởi động lỗi", A),
                (NONE, "Cấu hình ngoài hợp lệ", "dùng đúng cấu hình", NONE, N),
            ]),
        ],
    },
    {
        "sheet": "Level15RequestedConsumer",
        "test_class": "Level15RequestedConsumerTest",
        "module": "inspection-service",
        "date": "10/11/2026",
        "desc": "Consumer yêu cầu giám định Level 1.5",
        "pre": "Event hợp lệ; use case và bảng processed_event mock",
        "groups": [
            ("onMessage()", [
                (NONE, "Event chuẩn", "dùng codec chung, gọi use case qua application boundary", NONE, N),
                (NONE, "Payload hỏng; payload tự chỉ định inspector", "không thay đổi gì", IEP, A),
                (NONE, "Lỗi DB hoặc dependency", "vẫn retry được", "IllegalStateException", A),
                ("eventId đã xử lý", "Event lặp", "không gọi use case", NONE, B),
                (NONE, "Field hợp đồng sai", NONE, IEP, A),
            ]),
        ],
    },
    # ── reputation-service ─────────────────────────────────────────────────
    {
        "sheet": "BatchSummaryApplication",
        "test_class": "BatchSummaryApplicationTest",
        "module": "reputation-service",
        "date": "06/11/2026",
        "desc": "Đọc tóm tắt uy tín theo lô",
        "pre": "Repository fake; clock cố định",
        "groups": [
            ("summaries()", [
                (NONE, "Lô nhiều user", "1 lần gọi repository, lấy thời gian 1 lần", NONE, N),
                (NONE, "Tóm tắt công khai", "cùng phép chiếu và thời gian như lịch sử", NONE, N),
                (NONE, "Danh sách id trùng; sửa danh sách sau khi gọi", NONE, IAE + "; danh sách được copy", A),
            ]),
        ],
    },
    {
        "sheet": "InternalServiceAuthFilter",
        "test_class": "InternalServiceAuthenticationFilterTest",
        "module": "reputation-service",
        "date": "06/11/2026",
        "desc": "Filter xác thực gọi nội bộ giữa service",
        "pre": NONE,
        "groups": [
            ("doFilter()", [
                (NONE, "Secret cấu hình thiếu hoặc rỗng", "không bao giờ xác thực", NONE, A),
                (NONE, "Secret đúng", "principal là service, không có danh tính người dùng; context được dọn", NONE, N),
            ]),
        ],
    },
    # ── product-service ────────────────────────────────────────────────────
    {
        "sheet": "AssignCategoryCommodity",
        "test_class": "AssignCategoryCommodityUseCaseImplTest",
        "module": "product-service",
        "date": "07/11/2026",
        "desc": "Gán commodity cho category cũ",
        "pre": "CategoryRepository mock",
        "groups": [
            ("execute()", [
                ("Category APPROVED cũ chưa có commodity", "Gán COFFEE", "lưu category đã gán", NONE, N),
                (NONE, "Category không tồn tại", NONE, "CategoryNotFoundException", A),
                ("Category đã có commodity", "Gán lại", "không lưu", "IllegalStateException", A),
            ]),
        ],
    },
    {
        "sheet": "CheckPlotForestBaseline",
        "test_class": "CheckPlotForestBaselineUseCaseImplTest",
        "module": "product-service",
        "date": "07/11/2026",
        "desc": "Kiểm tra plot chồng lấn rừng tự nhiên theo lô",
        "pre": "Plot đang chờ kiểm tra; dữ liệu nền rừng mock",
        "groups": [
            ("execute()", [
                (NONE, "Plot chồng rừng tự nhiên", "ghi rủi ro HIGH kèm bản ghi audit", NONE, N),
                (NONE, "Plot ngoài vùng có dữ liệu nền", "không ghi gì, chờ hết hạn", NONE, B),
                (NONE, "Các plot được chọn", "đánh dấu đã thử trước khi kiểm", NONE, N),
                ("Plot đổi trong lúc kiểm", "Kết quả bị bỏ", "không tính", NONE, B),
                (NONE, "1 plot lỗi", "lô vẫn chạy tiếp", NONE, A),
            ]),
        ],
    },
    {
        "sheet": "ContractCreatedConsumer",
        "test_class": "ContractCreatedConsumerTest",
        "module": "product-service",
        "date": "07/11/2026",
        "desc": "Consumer contract.created: giữ hàng cho listing",
        "pre": "Event contract.created hợp lệ",
        "groups": [
            ("onMessage()", [
                (NONE, "Event hợp lệ; khối lượng thập phân", "gọi use case với đúng khối lượng", NONE, N),
                (NONE, "Fixture hợp lệ", "khớp schema đã chốt", NONE, N),
                (NONE, "Sai eventType / producer / version; field lạ ở envelope hoặc payload", NONE, IEP + " → DLQ", A),
                (NONE, "Khối lượng ≤ 0 hoặc không phải số", NONE, IEP + " → DLQ", B),
                (NONE, "contractId không phải UUID; JSON hỏng", NONE, IEP + " → DLQ", A),
            ]),
        ],
    },
    {
        "sheet": "GetListingContractSnapshot",
        "test_class": "GetListingContractSnapshotUseCaseImplTest",
        "module": "product-service",
        "date": "10/11/2026",
        "desc": "Snapshot listing cho contract-service khi tạo offer",
        "pre": "Listing cà phê có plot và category; token phiên bản hiện tại",
        "groups": [
            ("execute()", [
                (NONE, "Token đúng phiên bản", "trả bản sao listing khớp 2 schema đã chốt", NONE, N),
                (NONE, "Dữ liệu mùa vụ null", "gửi null tường minh", NONE, B),
                (NONE, "Token cũ (kể cả listing cũ)", "409 VERSION_MISMATCH, kiểm trước mọi thứ", NONE, A),
                (NONE, "Listing cũ thiếu plot/hàng; cà phê không có plot; thiếu category", "409 STATE_CONFLICT", NONE, A),
                (NONE, "Listing không tồn tại", NONE, "ListingNotFoundException", A),
                (NONE, "Token rỗng", "không đọc gì", IAE, B),
            ]),
        ],
    },
    {
        "sheet": "ListActiveListings",
        "test_class": "ListActiveListingsUseCaseImplTest",
        "module": "product-service",
        "date": "10/11/2026",
        "desc": "Danh sách listing kèm uy tín seller",
        "pre": "Repository và client reputation mock",
        "groups": [
            ("execute()", [
                (NONE, "Khách chưa đăng nhập", "listing không có uy tín, không gọi reputation", NONE, N),
                (NONE, "Khách chưa đăng nhập lọc theo uy tín", "không đọc gì", "ReputationSignInRequiredException", A),
                (NONE, "Đã đăng nhập", "mỗi seller tra 1 lần, hiện uy tín", NONE, N),
                (NONE, "Lọc điểm tối thiểu", "chỉ bỏ seller có điểm thấp hơn; chưa có điểm vẫn hiện", NONE, B),
                (NONE, "Reputation sập toàn bộ / một phần", "giữ listing, đánh dấu không có dữ liệu; chỉ lọc seller biết điểm", NONE, A),
                (NONE, "Trang rỗng", "không gọi reputation", NONE, B),
            ]),
        ],
    },
    {
        "sheet": "ReputationSummaryClient",
        "test_class": "ReputationSummaryClientTest",
        "module": "product-service",
        "date": "10/11/2026",
        "desc": "Client gọi reputation-service lấy điểm theo lô",
        "pre": "HTTP server giả lập reputation",
        "groups": [
            ("lookup()", [
                (NONE, "1 trang seller", "1 lần gọi; chỉ giữ điểm và trạng thái khoá", NONE, N),
                (NONE, "Điểm dạng số nguyên; id viết hoa", "chấp nhận", NONE, B),
                (NONE, "Thiếu 1 seller trong kết quả", "chỉ seller đó là chưa biết", NONE, B),
                (NONE, "Response hỏng; content type không phải JSON", "mọi seller chưa biết, không tin dữ liệu", NONE, A),
                (NONE, "Mã lỗi HTTP; service sập; quá timeout", "tra cứu không đủ, không ném lỗi", NONE, A),
                (NONE, "Không có seller; quá giới hạn lô", "không gọi", NONE, B),
            ]),
            ("Khởi tạo", [
                (NONE, "Secret rỗng", NONE, IAE, A),
            ]),
        ],
    },
    {
        "sheet": "ListenerRetry",
        "test_class": "ListenerRetryTest",
        "module": "product-service",
        "date": "07/11/2026",
        "desc": "Chính sách retry của listener RabbitMQ",
        "pre": "Listener container cấu hình như production",
        "groups": [
            ("Retry", [
                (NONE, "Message hỏng", "chỉ thử 1 lần", "AmqpRejectAndDontRequeueException → DLQ", A),
                (NONE, "Lỗi xử lý", "thử 3 lần rồi từ chối", "AmqpRejectAndDontRequeueException → DLQ", A),
            ]),
        ],
    },
    {
        "sheet": "OrphanReservationReaperJob",
        "test_class": "OrphanReservationReaperJobTest",
        "module": "product-service",
        "date": "07/11/2026",
        "desc": "Job dọn giữ hàng mồ côi",
        "pre": "Clock cố định; thời gian ân hạn cấu hình",
        "groups": [
            ("run()", [
                (NONE, "Giữ hàng tạo trước thời gian ân hạn", "bị dọn", NONE, N),
                (NONE, "Thời gian ân hạn = 0", NONE, IAE + " lúc khởi động", B),
            ]),
        ],
    },
    # ── user-service ───────────────────────────────────────────────────────
    {
        "sheet": "ApplyLockChange",
        "test_class": "ApplyLockChangeUseCaseImplTest",
        "module": "user-service",
        "date": "06/11/2026",
        "desc": "Áp khoá/mở khoá uy tín vào hồ sơ người dùng",
        "pre": "Repository và publisher mock",
        "groups": [
            ("execute()", [
                (NONE, "Khoá được áp", "publish theo hồ sơ đọc lại", NONE, N),
                (NONE, "Mở khoá được áp", "dùng câu thông báo mở khoá", NONE, N),
                (NONE, "Revision cũ hơn", "bỏ qua, không publish", NONE, B),
                (NONE, "User không tồn tại", "không publish", NONE, A),
            ]),
        ],
    },
    {
        "sheet": "EligibleInspectorBlankSecret",
        "test_class": "EligibleInspectorBlankSecretTest",
        "module": "user-service",
        "date": "05/11/2026",
        "desc": "Endpoint nội bộ danh sách inspector khi secret rỗng",
        "pre": NONE,
        "groups": [
            ("Truy cập nội bộ", [
                (NONE, "Secret cấu hình rỗng hoặc toàn khoảng trắng", "không bao giờ cho truy cập", NONE, A),
            ]),
        ],
    },
    {
        "sheet": "GetUserInfo",
        "test_class": "GetUserInfoUseCaseImplTest",
        "module": "user-service",
        "date": "06/11/2026",
        "desc": "Lấy thông tin công khai của user",
        "pre": "Repository mock",
        "groups": [
            ("execute()", [
                (NONE, "User tồn tại", "chỉ trả field công khai", NONE, N),
                (NONE, "User không tồn tại", NONE, "NoSuchElementException", A),
            ]),
        ],
    },
    {
        "sheet": "ListEligibleInspectors",
        "test_class": "ListEligibleInspectorsUseCaseImplTest",
        "module": "user-service",
        "date": "05/11/2026",
        "desc": "Danh sách inspector đủ điều kiện",
        "pre": "Repository mock; clock cố định",
        "groups": [
            ("execute()", [
                (NONE, "Có inspector", "dùng thời gian hiện tại; chỉ trả userId theo thứ tự repository", NONE, N),
                (NONE, "Không có ai", "danh sách rỗng", NONE, B),
            ]),
        ],
    },
    {
        "sheet": "OutboxKycResultPublisher",
        "test_class": "OutboxKycResultPublisherTest",
        "module": "user-service",
        "date": "06/11/2026",
        "desc": "Ghi kết quả KYC vào outbox",
        "pre": "Outbox fake; schema đã chốt",
        "groups": [
            ("decided()", [
                (NONE, "VERIFIED", "lệnh khớp schema, không có lý do", NONE, N),
                (NONE, "REJECTED", "lệnh khớp schema, có lý do", NONE, N),
                (NONE, "Gọi nhiều lần", "mỗi lần một eventId mới", NONE, N),
                (NONE, "PENDING", NONE, "IllegalStateException", A),
            ]),
        ],
    },
    {
        "sheet": "OutboxLockChangePublisher",
        "test_class": "OutboxLockChangePublisherTest",
        "module": "user-service",
        "date": "06/11/2026",
        "desc": "Ghi thay đổi khoá vào outbox",
        "pre": "Outbox fake",
        "groups": [
            ("publish()", [
                (NONE, "Khoá", "báo khoá đang lưu, không phải khoá vừa nhận", NONE, N),
                (NONE, "Mở khoá hoàn toàn", "effectiveUntil = null", NONE, B),
            ]),
        ],
    },
    {
        "sheet": "ReputationLockConsumer",
        "test_class": "ReputationLockConsumerTest",
        "module": "user-service",
        "date": "06/11/2026",
        "desc": "Consumer reputation.locked / unlocked",
        "pre": "Event hợp lệ theo schema đã chốt",
        "groups": [
            ("toCommand()", [
                (NONE, "Fixture", "khớp schema", NONE, N),
                (NONE, "reputation.locked", "lệnh khoá giữ chuỗi correlation", NONE, N),
                (NONE, "reputation.unlocked với khoá hiệu lực null", "mở khoá hoàn toàn", NONE, B),
                (NONE, "Thời điểm có offset", "chuẩn hoá UTC, micro giây", NONE, B),
                (NONE, "Thiếu correlationId", "bắt đầu chuỗi từ event này", NONE, B),
                (NONE, "Locked thiếu lockedUntil; unlocked thiếu key effectiveLockedUntil; field lạ", NONE, IEP, A),
                (NONE, "Revision < 1 hoặc không phải số nguyên; user không phải UUID", NONE, IEP, B),
                (NONE, "Sai producer hoặc eventType", NONE, IEP, A),
            ]),
        ],
    },
]
