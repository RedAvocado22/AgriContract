# contract-service unit tests added after 31/10/2026 (one row per scenario; "date" = merge date on main)
N, A, B = "Normal", "Abnormal", "Boundary"
NONE = "N/A"
CONFLICT = "ContractConflictException"
ITE = "InvalidContractTermsException"
SNE = "SignerNotEligibleException"
USU = "UserServiceUnavailableException"

MODULES = [
    {
        "sheet": "ContractTermsRules",
        "module": "contract-service",
        "date": "07/11/2026",
        "desc": "ContractTerms (cà phê): luật điều khoản theo LegalProfile và hồ sơ chất lượng",
        "pre": "Terms cà phê mẫu hợp lệ: Robusta, 2 đợt giao, có plot, LegalProfile thương mại",
        "groups": [
            ("ContractTerms.of()", [
                (NONE, "Terms hợp lệ, giá × khối lượng các đợt", "tổng giá trị = đơn giá × tổng khối lượng các đợt", NONE, N),
                (NONE, "Hàng không phải cà phê", NONE, ITE, A),
                (NONE, "Hồ sơ chất lượng không phải cà phê", NONE, ITE, A),
                (NONE, "Cà phê không gắn plot nào", NONE, ITE, A),
                ("LegalProfile Luật Thương mại (trần phạt 8%)", "Phạt vi phạm 9%", NONE, ITE, B),
                (NONE, "Client gửi trần phạt cao hơn trần luật", NONE, ITE, A),
                ("LegalProfile Bộ luật Dân sự", "Phạt vi phạm vượt 8%", "chấp nhận; chỉ áp chính sách bồi thường của profile dân sự", NONE, N),
                (NONE, "Giá thoả thuận nằm ngoài biên giá", NONE, ITE, B),
                (NONE, "Giám định Level 2 không có tổ chức giám định", NONE, ITE, A),
                (NONE, "Chỉ số các đợt giao 1, 3 (thiếu 2)", NONE, ITE, A),
                (NONE, "Ngưỡng lệch chất lượng không tăng dần dung sai → phạt → từ chối", NONE, ITE, A),
                (NONE, "Phần trăm chất lượng có 3 chữ số thập phân", NONE, ITE, B),
                (NONE, "Cọc bên mua mới nằm ngoài biên đàm phán", NONE, ITE, B),
                (NONE, "Vân tay mẫu hợp đồng không phải SHA-256", NONE, ITE, A),
            ]),
        ],
    },
    {
        "sheet": "ContractReviseCoffeeTerms",
        "module": "contract-service",
        "date": "07/11/2026",
        "desc": "Contract.reviseCoffeeTerms(): sửa điều khoản khi đàm phán",
        "pre": "Hợp đồng cà phê OFFERED giữa buyer và seller; khối lượng đã giữ hàng 1.000 kg",
        "groups": [
            ("reviseCoffeeTerms()", [
                ("Hợp đồng OFFERED", "Buyer hoặc seller gửi terms mới", "status NEGOTIATING; terms = terms mới", NONE, N),
                (NONE, "Người ngoài hợp đồng sửa", NONE, "UnauthorizedContractAccessException", A),
                ("Đã có chữ ký thứ nhất", "Một bên sửa terms", NONE, CONFLICT + " (TERMS_FROZEN)", A),
                ("Hợp đồng SIGNED", "Một bên sửa terms", NONE, CONFLICT + " (NOT_NEGOTIABLE)", A),
                (NONE, "Tổng khối lượng khác khối lượng đã giữ hàng", NONE, ITE, B),
                (NONE, "Đổi lịch giao, giữ nguyên tổng khối lượng", "chấp nhận", NONE, B),
                (NONE, "Đổi hàng hoá sang loại khác", NONE, ITE, A),
                ("Hợp đồng Phase 1 không có terms cà phê", "Một bên sửa terms", NONE, CONFLICT + " (LEGACY_TERMS)", A),
            ]),
        ],
    },
    {
        "sheet": "ContractConsentToSign",
        "module": "contract-service",
        "date": "07/11/2026",
        "desc": "Contract.consentToSign(): ghi nhận đồng ý câu cam kết trước khi ký",
        "pre": "Hợp đồng cà phê NEGOTIATING; câu cam kết phiên bản hiện hành; vân tay terms hiện tại",
        "groups": [
            ("consentToSign()", [
                (NONE, "Seller đồng ý, đúng phiên bản câu cam kết và vân tay terms", "bằng chứng đồng ý gắn với câu cam kết và terms hiện tại", NONE, N),
                (NONE, "Buyer đồng ý", "nhận câu cam kết của bên mua; trạng thái hợp đồng không đổi", NONE, N),
                ("Bên kia đã ký", "Bên còn lại đồng ý", "chấp nhận", NONE, N),
                (NONE, "Người ngoài hợp đồng", NONE, "UnauthorizedContractAccessException", A),
                (NONE, "Buyer khai là seller", NONE, "UnauthorizedContractAccessException", A),
                (NONE, "Phiên bản câu cam kết cũ", NONE, CONFLICT, A),
                (NONE, "Vân tay terms cũ (terms đã đổi)", NONE, CONFLICT, A),
                ("Hợp đồng SIGNED", "Một bên đồng ý", NONE, CONFLICT, A),
                ("Bên đó đã ký", "Bên đó đồng ý lại", NONE, CONFLICT, A),
                ("Hợp đồng Phase 1 không có terms cà phê", "Một bên đồng ý", NONE, CONFLICT, A),
            ]),
        ],
    },
    {
        "sheet": "ContractSign",
        "module": "contract-service",
        "date": "08/11/2026",
        "desc": "Contract.sign(): ghi chữ ký theo OTP challenge và vân tay terms",
        "pre": "Hợp đồng cà phê NEGOTIATING; OTP challenge hợp lệ của từng bên",
        "groups": [
            ("sign()", [
                (NONE, "Chữ ký thứ nhất", "ghi chữ ký với challenge và vân tay terms hiện tại; hợp đồng chưa SIGNED", NONE, N),
                ("Bên kia đã ký cùng terms", "Chữ ký thứ hai", "status SIGNED", NONE, N),
                ("Chữ ký thứ nhất trên terms khác", "Chữ ký thứ hai", "status vẫn NEGOTIATING", "IllegalStateException", A),
                ("Bên đó đã ký", "Cùng bên ký lần 2", NONE, CONFLICT, A),
                (NONE, "Challenge cấp cho terms khác", NONE, CONFLICT, A),
                (NONE, "Người gọi không phải bên đó", NONE, "UnauthorizedContractAccessException", A),
                ("Hợp đồng SIGNED", "Ký thêm", NONE, CONFLICT, A),
            ]),
            ("roleOf()", [
                (NONE, "buyerId / sellerId / người lạ", "BUYER / SELLER / rỗng", NONE, N),
            ]),
        ],
    },
    {
        "sheet": "ContractWithdraw",
        "module": "contract-service",
        "date": "10/11/2026",
        "desc": "Contract.withdraw(): rút offer trước chữ ký thứ hai",
        "pre": "Hợp đồng cà phê giữa buyer và seller",
        "groups": [
            ("withdraw()", [
                ("Status OFFERED / NEGOTIATING, chưa đủ 2 chữ ký", "Buyer hoặc seller rút", "status WITHDRAWN", NONE, N),
                ("Bên đó đã ký", "Bên đã ký rút", "status WITHDRAWN", NONE, N),
                ("Bên kia đã ký", "Bên chưa ký rút", "status WITHDRAWN", NONE, N),
                ("Status SIGNED / ACTIVE / đã kết thúc", "Một bên rút", NONE, CONFLICT + " (NOT_WITHDRAWABLE)", A),
                (NONE, "Người ngoài hợp đồng rút", "hợp đồng không đổi", "UnauthorizedContractAccessException", A),
                ("Hợp đồng Phase 1 không có terms cà phê", "Một bên rút", "status không đổi", CONFLICT + " (LEGACY_TERMS)", A),
            ]),
        ],
    },
    {
        "sheet": "ActivationRecovery",
        "module": "contract-service",
        "date": "08/11/2026",
        "desc": "ActivationRecovery: báo cáo khoá cọc lỗi và cửa sổ huỷ kích hoạt",
        "pre": "Hợp đồng SIGNED, escrow báo khoá cọc lỗi",
        "groups": [
            ("recordFailure()", [
                ("Đã có báo cáo lỗi", "Lỗi mới tới sau", "chỉ cập nhật báo cáo mới nhất", NONE, N),
            ]),
            ("canCancelOneSided()", [
                (NONE, "Thời điểm = đúng lúc mở cửa sổ huỷ một bên", "cho phép huỷ một bên", NONE, B),
            ]),
            ("new ActivationRecovery()", [
                (NONE, "Cửa sổ huỷ kết thúc trước thời điểm lỗi", NONE, "IllegalArgumentException", A),
            ]),
        ],
    },
    {
        "sheet": "DepositAmount",
        "module": "contract-service",
        "date": "08/11/2026",
        "desc": "DepositAmount: tính cọc làm tròn tới đồng (HALF_UP)",
        "pre": NONE,
        "groups": [
            ("of()", [
                (NONE, "Tổng giá trị × tỉ lệ cọc ra số lẻ", "làm tròn tới đồng", NONE, N),
                (NONE, "Kết quả lẻ đúng 0,5 đồng", "làm tròn lên", NONE, B),
                (NONE, "Kết quả chẵn đồng", "giữ nguyên", NONE, N),
            ]),
        ],
    },
    {
        "sheet": "OtpChallenge",
        "module": "contract-service",
        "date": "07/11/2026",
        "desc": "OtpChallenge: hạn dùng, số lần sai, trạng thái đã dùng",
        "pre": "Policy: hết hạn 300 s, tối đa 5 lần sai; clock cố định",
        "groups": [
            ("issue() / verify()", [
                (NONE, "Kiểm tra trong 300 s", "challenge còn hiệu lực", NONE, N),
                (NONE, "Kiểm tra đúng giây thứ 300", NONE, CONFLICT + " (OTP_EXPIRED)", B),
                (NONE, "Nhập sai lần thứ 5 rồi nhập tiếp", NONE, CONFLICT + " (OTP_LOCKED)", B),
                ("Challenge đã dùng", "Dùng lại", NONE, CONFLICT + " (OTP_USED)", A),
                (NONE, "Policy có giá trị 0 hoặc âm", NONE, "IllegalArgumentException", A),
            ]),
        ],
    },
    {
        "sheet": "OtpPolicy",
        "module": "contract-service",
        "date": "08/11/2026",
        "desc": "OtpPolicy: giới hạn gửi mã (cooldown 60 s, tối đa 5 mã/giờ)",
        "pre": "Lịch sử gửi mã của 1 người cho 1 hợp đồng",
        "groups": [
            ("checkCanSend()", [
                (NONE, "Mã đầu tiên; mã sau cooldown", "được gửi", NONE, N),
                (NONE, "Mã thứ hai trong 60 s", NONE, CONFLICT + " (RESEND_TOO_SOON)", B),
                (NONE, "Mã thứ 6 trong 1 giờ", NONE, CONFLICT + " (RESEND_LIMIT)", B),
            ]),
        ],
    },
    {
        "sheet": "HmacOtpCodes",
        "module": "contract-service",
        "date": "07/11/2026",
        "desc": "HmacOtpCodes: sinh mã OTP và băm có khoá (pepper)",
        "pre": "Pepper cấu hình đủ dài",
        "groups": [
            ("generate() / hash()", [
                (NONE, "Sinh nhiều mã", "luôn 6 chữ số, giữ số 0 đầu", NONE, N),
                (NONE, "Băm 1 mã", "giá trị băm phụ thuộc khoá, không bao giờ là mã gốc", NONE, N),
                (NONE, "Pepper ngắn hoặc null", NONE, "IllegalArgumentException", A),
            ]),
        ],
    },
    {
        "sheet": "JcsTermsCanonicalizer",
        "module": "contract-service",
        "date": "07/11/2026",
        "desc": "JcsTermsCanonicalizer: vân tay điều khoản (RFC 8785 + SHA-256)",
        "pre": "Terms cà phê mẫu",
        "groups": [
            ("fingerprint()", [
                (NONE, "Cùng terms băm nhiều lần", "cùng vân tay", NONE, N),
                (NONE, "Cùng giá trị viết số khác nhau (1.50 / 1.5)", "cùng vân tay", NONE, B),
                (NONE, "Đổi bất kỳ điều khoản nào", "vân tay khác", NONE, N),
                (NONE, "Chỉ đổi mẫu hợp đồng hoặc hồ sơ chất lượng", "vân tay khác", NONE, N),
                (NONE, "Xem dạng chuẩn hoá", "phẳng, sắp xếp key, giữ key null", NONE, N),
                (NONE, "Terms đọc lại từ DB", "ra đúng vân tay đã lưu", NONE, N),
                (NONE, "Terms lưu trong DB bị sửa tay", NONE, "IllegalStateException", A),
                (NONE, "Code mới thêm field", "vân tay của terms cũ không đổi", NONE, B),
            ]),
        ],
    },
    {
        "sheet": "SignerEligibility",
        "module": "contract-service",
        "date": "08/11/2026",
        "desc": "SignerEligibility: điều kiện người ký và bên kia trước khi gửi OTP",
        "pre": "UserPort mock; clock cố định",
        "groups": [
            ("requireFreshLogin()", [
                (NONE, "Đăng nhập trong 5 phút", "qua", NONE, N),
                (NONE, "Đăng nhập cũ, thiếu hoặc ở tương lai", NONE, SNE + " (STEP_UP_REQUIRED)", B),
            ]),
            ("requireSigner()", [
                (NONE, "Đã KYC, không bị khoá, thẩm quyền còn hạn", "đủ điều kiện", NONE, N),
                (NONE, "Thẩm quyền hết hạn sau này; khoá đã hết", "đủ điều kiện", NONE, B),
                (NONE, "Chưa KYC", NONE, SNE + " (NOT_VERIFIED)", A),
                (NONE, "Thẩm quyền hết hạn đúng hoặc trước hiện tại", NONE, SNE + " (AUTHORIZATION_EXPIRED)", B),
                (NONE, "Đang bị khoá uy tín", NONE, SNE + " (LOCKED)", A),
                (NONE, "Không có email nhận mã", NONE, SNE + " (NO_EMAIL)", A),
                (NONE, "User không tồn tại", NONE, SNE + " (UNKNOWN_USER)", A),
                (NONE, "user-service sập", NONE, USU + " (fail closed)", A),
            ]),
            ("requireCounterparty()", [
                (NONE, "Bên kia ký được, không có email", "qua", NONE, N),
                (NONE, "Bên kia chưa KYC / hết thẩm quyền / bị khoá / không tồn tại", NONE, CONFLICT + " (COUNTERPARTY_NOT_ELIGIBLE), không nêu lý do của bên kia", A),
                (NONE, "user-service sập khi kiểm bên kia", NONE, USU + " (fail closed)", A),
            ]),
        ],
    },
    {
        "sheet": "OutboxContractEventPublisher",
        "module": "contract-service",
        "date": "08/11/2026",
        "desc": "OutboxContractEventPublisher: ghi event/command vào outbox, khớp schema đã chốt",
        "pre": "Outbox writer fake; validate bằng schema trong contracts/events",
        "groups": [
            ("contractSigned()", [
                ("Hợp đồng SIGNED", "Publish contract.signed", "khớp schema; cọc và vân tay lấy từ terms", NONE, N),
                ("Hợp đồng chưa SIGNED", "Publish contract.signed", NONE, "IllegalStateException", A),
                (NONE, "buyerId không phải UUID", NONE, "IllegalStateException (chặn ở đây, không để vào DLQ bên nhận)", A),
            ]),
            ("contractAnchorRequested()", [
                ("Hợp đồng SIGNED", "Publish lệnh gửi email neo hợp đồng", "khớp schema; đủ 2 bên và đúng terms đã ký", NONE, N),
                ("Hợp đồng chưa SIGNED", "Publish lệnh", NONE, "IllegalStateException", A),
            ]),
            ("withdraw events", [
                ("Hợp đồng WITHDRAWN", "contract.withdrawn", "khớp schema, kèm khối lượng đã giữ", NONE, N),
                ("Hợp đồng WITHDRAWN", "contract.terminated (WITHDRAW_OFFER)", "khớp schema, không breach, không remedy", NONE, N),
                ("Hợp đồng WITHDRAWN", "Thông báo rút offer", "chỉ gửi bên kia; khớp schema", NONE, N),
                ("Hợp đồng chưa WITHDRAWN", "Các event rút offer", NONE, "IllegalStateException", A),
            ]),
        ],
    },
    {
        "sheet": "ConsumerQueuesDeclaredAhead",
        "module": "contract-service",
        "date": "08/11/2026",
        "desc": "Khai báo trước queue của service nhận, cùng tham số với service chủ",
        "pre": "Đọc cấu hình RabbitMQ của contract-service",
        "groups": [
            ("Khai báo queue", [
                (NONE, "Queue anchor_requested của notification", "cùng tham số notification khai báo", NONE, N),
                (NONE, "Queue contract.activated của product", "cùng tham số product khai báo", NONE, N),
                (NONE, "Queue activation_failed của notification", "cùng tham số", NONE, N),
                (NONE, "Các queue nhánh huỷ kích hoạt", "cùng tham số service chủ", NONE, N),
                (NONE, "Các queue nhánh rút offer", "cùng tham số service chủ", NONE, N),
                (NONE, "Queue audit cho contract.signed / terminated", "được khai báo", NONE, N),
            ]),
        ],
    },
    {
        "sheet": "ActivationRefundConsumer",
        "module": "contract-service",
        "date": "08/11/2026",
        "desc": "ActivationRefundCompletedConsumer: parse escrow.activation_refund_completed",
        "pre": "Event hợp lệ theo schema",
        "groups": [
            ("toCommand()", [
                (NONE, "Event hợp lệ, có hoặc không có danh sách leg đã trả", "đọc đúng contractId", NONE, N),
                (NONE, "Payload sai schema (thiếu field, field lạ, sai kiểu)", NONE, "InvalidEventPayloadException", A),
            ]),
        ],
    },
    {
        "sheet": "ActivationRecoveryController",
        "module": "contract-service",
        "date": "08/11/2026",
        "desc": "POST retry-deposit-lock: Operator thử khoá cọc lại",
        "pre": "MockMvc standalone; use case mock",
        "groups": [
            ("POST /contracts/{id}/activation/retry-deposit-lock", [
                (NONE, "OPERATOR, có Idempotency-Key", "204; actor = operator", NONE, N),
                (NONE, "BUYER / SELLER / ADMIN; hoặc thiếu Idempotency-Key", "403 / 400", NONE, A),
            ]),
        ],
    },
    {
        "sheet": "ContractSigningEndpoint",
        "module": "contract-service",
        "date": "08/11/2026",
        "desc": "Endpoint ký: POST /sign/initiate và POST /sign/verify",
        "pre": "MockMvc standalone; use case mock; header danh tính từ gateway",
        "groups": [
            ("POST /sign/initiate", [
                (NONE, "Body hợp lệ, có Idempotency-Key", "200, challenge trả trần; truyền auth_time từ gateway", NONE, N),
                (NONE, "Field lạ, thiếu field, hash sai định dạng", "400", NONE, A),
                (NONE, "Thiếu Idempotency-Key", "400", NONE, A),
                (NONE, "Role khác BUYER/SELLER", "403", NONE, A),
                (NONE, "Use case từ chối (các loại lỗi)", "đúng mã HTTP của từng lỗi", NONE, A),
            ]),
            ("POST /sign/verify", [
                (NONE, "Mã OTP hợp lệ", "204; truyền mã, auth_time, không truyền IP", NONE, N),
                (NONE, "Body sai", "400, không lặp lại mã trong response", NONE, A),
                (NONE, "Role khác BUYER/SELLER", "403", NONE, A),
                (NONE, "Sai mã; bên kia không đủ điều kiện", "409", NONE, A),
            ]),
        ],
    },
    {
        "sheet": "GetContractEndpoint",
        "module": "contract-service",
        "date": "08/11/2026",
        "desc": "GET /contracts/{id}: trả ContractResponse theo contract đã chốt",
        "pre": "MockMvc standalone; use case mock",
        "groups": [
            ("GET /contracts/{id}", [
                ("Hợp đồng cà phê", NONE, "body đúng ContractResponse, có vân tay terms, không bọc wrapper", NONE, N),
                (NONE, "Hợp đồng có / chưa có lỗi khoá cọc", "activationRecovery chỉ xuất hiện sau lỗi khoá cọc", NONE, N),
                ("Hợp đồng Phase 1 không có terms cà phê", NONE, "giữ body cũ có wrapper", NONE, B),
            ]),
        ],
    },
    {
        "sheet": "UpdateContractTermsEndpoint",
        "module": "contract-service",
        "date": "07/11/2026",
        "desc": "PATCH /contracts/{id}/terms: sửa điều khoản",
        "pre": "MockMvc standalone; use case mock",
        "groups": [
            ("PATCH /contracts/{id}/terms", [
                (NONE, "Body hợp lệ", "200; trả nguyên terms đã lưu, không wrapper", NONE, N),
                (NONE, "Body có field do server tự tính", "400", NONE, A),
                (NONE, "Thiếu Idempotency-Key", "400", NONE, A),
                (NONE, "ADMIN sửa thay các bên", "403", NONE, A),
            ]),
        ],
    },
    {
        "sheet": "WithdrawOfferEndpoint",
        "module": "contract-service",
        "date": "10/11/2026",
        "desc": "POST /contracts/{id}/withdraw: rút offer",
        "pre": "MockMvc standalone; use case mock",
        "groups": [
            ("POST /contracts/{id}/withdraw", [
                (NONE, "Buyer/seller, có Idempotency-Key", "204, không body; người gọi lấy từ principal", NONE, N),
                (NONE, "Thiếu Idempotency-Key", "400", NONE, A),
                (NONE, "OPERATOR / ADMIN rút thay", "403", NONE, A),
                ("Đã có chữ ký thứ hai", "Rút", "409", NONE, A),
                (NONE, "Người ngoài hợp đồng; hợp đồng không tồn tại", "403; 404", NONE, A),
            ]),
        ],
    },
    {
        "sheet": "GatewayAuthDetails",
        "module": "contract-service",
        "date": "08/11/2026",
        "desc": "GatewayAuthDetails: đọc thời điểm đăng nhập từ header gateway",
        "pre": NONE,
        "groups": [
            ("authTime()", [
                (NONE, "Header epoch seconds hợp lệ", "auth_time tương ứng", NONE, N),
                (NONE, "Thiếu header hoặc không đọc được", "không có auth_time", NONE, A),
            ]),
        ],
    },
    {
        "sheet": "UserPortAdapter",
        "module": "contract-service",
        "date": "08/11/2026",
        "desc": "UserPortAdapter: gọi user-service lấy thông tin người ký",
        "pre": "Feign client mock",
        "groups": [
            ("load()", [
                (NONE, "user-service trả 404", "rỗng (user không tồn tại)", NONE, N),
                (NONE, "Lỗi khác (5xx, timeout)", NONE, USU + ", không bao giờ coi là qua", A),
            ]),
        ],
    },
]

for _m in MODULES:
    _m["test_class"] = {"ActivationRefundConsumer": "ActivationRefundCompletedConsumerTest"}.get(
        _m["sheet"], _m["sheet"] + "Test")
