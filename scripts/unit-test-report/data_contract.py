# contract-service unit tests
N, A, B = "Normal", "Abnormal", "Boundary"
NONE = "N/A"

MODULES = [
    {
        "sheet": "Contract",
        "module": "contract-service",
        "desc": "Aggregate Contract: offer, counter-offer, ký, kích hoạt, domain event",
        "pre": "Terms mẫu: 100 kg, 500.000 VND, giao sau 30 ngày, Grade A",
        "groups": [
            ("offer()", [
                (NONE, "buyer-1, seller-1, listing-1, terms mẫu", "status OFFERED; phát 1 ContractOfferedEvent", NONE, N),
            ]),
            ("counterOffer()", [
                ("Contract OFFERED", "buyer-1, terms mới (200 kg, 450.000 VND)", "status NEGOTIATING; terms = terms mới; phát ContractNegotiatingEvent", NONE, N),
                ("Contract NEGOTIATING", "seller-1, terms mới lần 2", "vẫn NEGOTIATING; terms cập nhật; phát thêm 1 ContractNegotiatingEvent", NONE, N),
                ("Contract SIGNED", "buyer-1, terms mới", NONE, "IllegalArgumentException", A),
                ("Contract OFFERED", "stranger-id, terms mới", NONE, "UnauthorizedContractAccessException", A),
            ]),
            ("sign()", [
                ("Contract OFFERED, chưa ai ký", "buyer-1", "vẫn OFFERED; phát ContractPartiallySignedEvent", NONE, N),
                ("Contract OFFERED, chưa ai ký", "seller-1", "vẫn OFFERED; phát ContractPartiallySignedEvent", NONE, N),
                ("buyer-1 đã ký", "seller-1", "status SIGNED; phát ContractSignedEvent", NONE, N),
                ("buyer-1 đã ký", "buyer-1 ký lần 2", NONE, "IllegalArgumentException", A),
                ("Contract SIGNED", "buyer-1", NONE, "IllegalArgumentException", A),
                ("Contract OFFERED", "stranger-id", NONE, "UnauthorizedContractAccessException", A),
            ]),
            ("activate()", [
                ("Contract SIGNED", "(không tham số)", "status ACTIVE; phát ContractActivatedEvent", NONE, N),
                ("Contract OFFERED", "(không tham số)", NONE, "IllegalArgumentException", A),
            ]),
            ("pullDomainEvents()", [
                ("Contract vừa offer, đã pull 1 lần", "gọi lần 2", "list rỗng", NONE, B),
            ]),
        ],
    },
    {
        "sheet": "CreateContract",
        "module": "contract-service",
        "desc": "CreateContractUseCase: tạo hợp đồng từ listing",
        "pre": "ListingPort, UserPort, ContractRepository được mock",
        "groups": [
            ("execute()", [
                ("listing-1 status CLOSED", "buyer-1, listing-1, terms mẫu", "không gọi save", "IllegalArgumentException", A),
                ("listing-1 ACTIVE; user buyer-1, seller-1 tồn tại", "contractId = null, buyer-1, listing-1", "lưu contract OFFERED, buyer-1/seller-1, product \"Rice\"; response status OFFERED", NONE, N),
                ("Như UTC002", "contractId = \"fixed-id\"", "contract lưu với id \"fixed-id\"", NONE, N),
                ("Như UTC002", "contractId = null", "id tự sinh, không rỗng", NONE, N),
                ("Như UTC002", "buyer-1, listing-1", "snapshot org name/email từ UserPort; sellerId lấy từ listing, không từ command", NONE, N),
            ]),
        ],
    },
    {
        "sheet": "GetContract",
        "module": "contract-service",
        "desc": "GetContractUseCase: xem chi tiết, kiểm quyền truy cập",
        "pre": "ContractRepository mock; contract-1 giữa buyer-1 và seller-1",
        "groups": [
            ("execute()", [
                ("Không có contract-1", "contract-1, buyer-1, isAdmin = false", NONE, "ContractNotFoundException", A),
                ("contract-1 tồn tại", "contract-1, buyer-1, false", "response contract-1, buyerId = buyer-1", NONE, N),
                ("contract-1 tồn tại", "contract-1, seller-1, false", "response, sellerId = seller-1", NONE, N),
                ("contract-1 tồn tại", "contract-1, admin-99, isAdmin = true", "response contract-1", NONE, N),
                ("contract-1 tồn tại", "contract-1, stranger-99, false", NONE, "UnauthorizedContractActionException", A),
                ("contract-1 giữa buyer-1 và seller-1", "contract-1, buyer-2, false", NONE, "UnauthorizedContractActionException", A),
            ]),
        ],
    },
    {
        "sheet": "ListContracts",
        "module": "contract-service",
        "desc": "ListContractsUseCase: liệt kê theo buyer/seller, lọc status, phân trang",
        "pre": "ContractRepository mock; page 0, size 20",
        "groups": [
            ("executeByBuyer()", [
                ("buyer-1 có 2 contract", "buyer-1, status = null", "2 contract, đều của buyer-1", NONE, N),
                ("buyer-1 có 1 contract OFFERED", "buyer-1, status = OFFERED", "1 contract, totalElements = 1", NONE, N),
                ("buyer-1 không có contract", "buyer-1, status = null", "content rỗng, totalElements = 0, empty = true", NONE, B),
            ]),
            ("executeBySeller()", [
                ("seller-1 có 2 contract", "seller-1, status = null", "2 contract, đều của seller-1", NONE, N),
                ("seller-1 có 1 contract OFFERED", "seller-1, status = OFFERED", "1 contract, totalElements = 1", NONE, N),
                ("seller-1 không có contract", "seller-1, status = null", "content rỗng, empty = true", NONE, B),
            ]),
            ("Phân trang", [
                ("buyer-1 có 11 contract", "buyer-1, page = 1, size = 10", "page 1, size 10, total 11, totalPages 2, first = false, last = true", NONE, B),
            ]),
        ],
    },
    {
        "sheet": "NegotiateContract",
        "module": "contract-service",
        "desc": "NegotiateContractUseCase: counter-offer qua use case",
        "pre": "contract-1 ở OFFERED",
        "groups": [
            ("execute()", [
                ("Không có contract-1", "contract-1, buyer-1, terms mới", "không save", "ContractNotFoundException", A),
                ("contract-1 OFFERED", "contract-1, buyer-1, terms mới (120 kg, 480.000 VND)", "status NEGOTIATING; terms mới; có save", NONE, N),
                ("contract-1 OFFERED", "contract-1, seller-1, terms mới", "status NEGOTIATING; có save", NONE, N),
                ("contract-1 OFFERED", "contract-1, stranger-99, terms mới", "không save", "UnauthorizedContractAccessException", A),
            ]),
        ],
    },
    {
        "sheet": "SignContract",
        "module": "contract-service",
        "desc": "SignContractUseCase: ký, đóng listing khi đủ 2 chữ ký",
        "pre": "contract-1 ở OFFERED; ListingPort mock",
        "groups": [
            ("execute()", [
                ("Không có contract-1", "contract-1, buyer-1", "không save", "ContractNotFoundException", A),
                ("contract-1 OFFERED", "contract-1, stranger-99", "không save", "UnauthorizedContractAccessException", A),
                ("contract-1 ACTIVE", "contract-1, buyer-1", "không save", "IllegalArgumentException", A),
                ("contract-1 OFFERED, chưa ai ký", "contract-1, buyer-1", "vẫn OFFERED; signatories = [buyer-1]; có save; không đóng listing", NONE, N),
                ("contract-1 OFFERED", "buyer-1 rồi seller-1", "status SIGNED; đóng listing-1; save 2 lần", NONE, N),
                ("buyer-1 đã ký", "buyer-1 ký lần 2", "chỉ save 1 lần", "IllegalArgumentException", A),
                ("contract-1 NEGOTIATING", "buyer-1 rồi seller-1", "status SIGNED; đóng listing-1", NONE, N),
                ("contract-1 OFFERED, chưa ai ký", "contract-1, seller-1", "vẫn OFFERED; signatories = [seller-1]; không đóng listing", NONE, N),
            ]),
        ],
    },
]
