N, A, B = "Normal", "Abnormal", "Boundary"
NONE = "N/A"

MODULES = [
    {
        "sheet": "ContractSignedConsumer",
        "module": "escrow-service",
        "desc": "ContractSignedConsumer: parse contract.signed thành ContractSignedCommand",
        "pre": "Event contract.signed hợp lệ: buyer deposit 40.000.000,00, seller deposit 20.000.000,00",
        "groups": [
            ("toCommand()", [
                (NONE, "Event hợp lệ", "map eventId, correlationId, contractId, buyerId, sellerId, 2 khoản cọc", NONE, N),
                (NONE, "Event không có correlationId", "correlationId = eventId (bắt đầu chuỗi mới)", NONE, B),
                (NONE, "sellerDepositAmount = \"0.00\"", "sellerDepositAmount = 0", NONE, B),
            ] + [
                (NONE, why, NONE, "InvalidEventPayloadException", typ)
                for why, typ in [
                    ("sai eventType", A),
                    ("sai producer", A),
                    ("version không hỗ trợ", A),
                    ("thiếu field bắt buộc", A),
                    ("payload có field lạ", A),
                    ("buyerId không phải UUID", A),
                    ("sellerId = null", A),
                    ("deposit 1 chữ số thập phân", B),
                    ("deposit dạng number thay vì string", A),
                    ("deposit âm", B),
                ]
            ]),
        ],
    },
    {
        "sheet": "RequestActivationDeposits",
        "module": "escrow-service",
        "desc": "RequestActivationDepositsUseCaseImpl: tạo deposit leg và gửi lệnh khoá cọc",
        "pre": "Repository và publisher là fake ghi lại leg đã insert / message đã gửi; NOW cố định",
        "groups": [
            ("execute()", [
                (NONE, "buyer 1.500.000,00; seller 750.000,00", "2 leg BUYER, SELLER trạng thái REQUESTED, createdAt = NOW; gửi 2 lệnh kèm correlationId, eventId", NONE, N),
                (NONE, "buyer 1.500.000,00; seller 0.00", "chỉ 1 leg BUYER, 1 lệnh", NONE, B),
                (NONE, "buyer 0.00; seller 750.000,00", "không lưu, không gửi gì", "InvalidDepositLegException", B),
                (NONE, "buyer 1.500.000,00; seller -500.00", "không lưu, không gửi gì", "InvalidDepositLegException \"sellerDepositAmount\"", A),
                (NONE, "buyer 1.500.000,00; seller 10.005", "không lưu leg buyer, không gửi gì", "InvalidDepositLegException", B),
            ]),
        ],
    },
    {
        "sheet": "DepositLeg",
        "module": "escrow-service",
        "desc": "DepositLeg và Money (escrow): tạo leg cọc, validate dữ liệu",
        "pre": "CONTRACT_ID, BUYER_ID là UUID; AMOUNT, NOW cố định",
        "groups": [
            ("request()", [
                (NONE, "CONTRACT_ID, BUYER, BUYER_ID, AMOUNT, NOW", "leg REQUESTED đủ field; createdAt = updatedAt = NOW", NONE, N),
                (NONE, "Tạo 2 leg liên tiếp", "mỗi leg có legId, requestEventId là UUID riêng, không trùng nhau", NONE, N),
                (NONE, "contractId = \"contract-1\"", NONE, "InvalidDepositLegException \"contractId\"", A),
                (NONE, "userId = null", NONE, "InvalidDepositLegException \"userId\"", A),
                (NONE, "party / amount / time = null (từng cái)", NONE, "InvalidDepositLegException", A),
            ]),
            ("new Money()", [
                (NONE, "0; 10.005; 10.5 so với 10.50", "10.5 = 10.50", "0 và 10.005: InvalidDepositLegException", B),
            ]),
        ],
    },
    {
        "sheet": "ProcessNotification",
        "module": "notification-service",
        "desc": "ProcessNotificationUseCaseImpl: gửi email khi category được duyệt/từ chối, chống gửi trùng",
        "pre": "NotificationLogRepository, EmailPort mock",
        "groups": [
            ("handleCategoryApproved()", [
                ("Chưa có log cho event-1", "event-1, category \"Ca Phe\", seller@test.com", "gửi email có \"approved\", \"Ca Phe\"; save log 2 lần", NONE, N),
                ("Log event-1 đã SENT", "event-1, seller@test.com", "không gửi lại, không save", NONE, A),
            ]),
            ("handleCategoryRejected()", [
                ("Chưa có log cho event-2", "event-2, lý do \"trung ten\"", "gửi email có \"rejected\" và lý do", NONE, N),
            ]),
        ],
    },
    {
        "sheet": "NotificationEventConsumer",
        "module": "notification-service",
        "desc": "NotificationEventConsumer: parse event category và chuyển cho use case",
        "pre": "ProcessNotificationUseCase mock",
        "groups": [
            ("onCategoryApproved()", [
                (NONE, "eventId, categoryId, name, proposedByEmail hợp lệ", "gọi handleCategoryApproved với categoryId, email đúng", NONE, N),
                (NONE, "categoryId = 12345 (số)", "không gọi use case", "InvalidEventPayloadException", A),
            ]),
            ("onCategoryRejected()", [
                (NONE, "Payload hợp lệ, rejectionReason \"trung ten\"", "gọi handleCategoryRejected với lý do đúng", NONE, N),
            ]),
        ],
    },
]
