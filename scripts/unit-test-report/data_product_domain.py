N, A, B = "Normal", "Abnormal", "Boundary"
NONE = "N/A"
IAE = "IllegalArgumentException"
ISE = "IllegalStateException"

MODULES = [
    {
        "sheet": "Reservation",
        "module": "product-service",
        "desc": "Reservation: giữ hàng cho hợp đồng, so sánh request, chuyển trạng thái",
        "pre": "CONTRACT_ID, LISTING_ID cố định",
        "groups": [
            ("create()", [
                (NONE, "quantity 40", "status RESERVED; contractId, listingId, quantity đúng", NONE, N),
                (NONE, "quantity 0; -1; null", NONE, IAE, B),
                (NONE, "contractId null; listingId null; ContractId(\" \")", NONE, IAE, A),
            ]),
            ("isSameRequest()", [
                ("Reservation 40 trên LISTING_ID", "(LISTING_ID, 40.000); (LISTING_ID, 41); (listing-2, 40)", "true; false; false", NONE, B),
            ]),
            ("ReservationStatus.canTransitionTo()", [
                (NONE, "Mọi cặp trạng thái", "chỉ cho RESERVED→SIGNED_RELEASABLE/RELEASED, SIGNED_RELEASABLE→COMMITTED/RELEASED; COMMITTED, RELEASED là terminal", NONE, N),
            ]),
        ],
    },
    {
        "sheet": "PlotRegistryEntry",
        "module": "product-service",
        "desc": "PlotRegistryEntry: đăng ký lô đất, re-import, chuẩn hoá nhãn hộ",
        "pre": "Geometry mẫu từ PlotFixtures; T0, T1 cố định",
        "groups": [
            ("register()", [
                (NONE, "plot-1, seller-1, nhãn \"  Ho A \", MANUAL_PIN point, accuracy 8.5, DAK_LAK", "nhãn \"Ho A\"; PENDING, SELF_DECLARED; importedAt = T0; chưa dùng cho listing được", NONE, N),
                (NONE, "MANUAL_PIN với polygon", NONE, IAE + " \"POINT\"", A),
                (NONE, "KML_IMPORT polygon kèm accuracy 5", NONE, IAE, A),
            ]),
            ("reimport()", [
                ("Entry đã CHECKED, LOW_RISK", "Cùng geometry, tỉnh LAM_DONG, T1", "giữ CHECKED, LOW_RISK; tỉnh = LAM_DONG; importedAt = T1", NONE, N),
                ("Entry đã CHECKED", "Geometry khác (polygon)", "về PENDING, risk = null, không dùng cho listing được", NONE, N),
            ]),
            ("canBackListing()", [
                (NONE, "Entry CHECKED; entry UNVERIFIED", "cả 2 true", NONE, N),
            ]),
            ("normalizeLabel()", [
                (NONE, "\"Hộ Lê Văn A\" dạng NFD; \"Hồ\" so với \"Hổ\"", "NFD → NFC bằng bản gốc; giữ dấu nên Hồ ≠ Hổ", NONE, N),
                (NONE, "255 ký tự; 256 ký tự; 255 \"ộ\" dạng NFD (765 char)", "255 ok; NFD đếm sau NFC = 255", "256 ký tự: " + IAE + " \"255\"", B),
            ]),
            ("isOwnedBy()", [
                ("Entry của seller-1", "seller-1; seller-2", "true; false", NONE, N),
            ]),
        ],
    },
    {
        "sheet": "Category",
        "module": "product-service",
        "desc": "Category: đề xuất, duyệt, từ chối danh mục",
        "pre": "Category PENDING mẫu, người đề xuất có email",
        "groups": [
            ("propose()", [
                (NONE, "Tên hợp lệ", "PENDING; name, normalizedName, proposedBy đúng; không phát event", NONE, N),
                (NONE, "Tên \" \"", NONE, IAE, B),
            ]),
            ("approve()", [
                ("PENDING", NONE, "APPROVED, commodity COFFEE; phát CategoryApprovedEvent kèm email người đề xuất", NONE, N),
                ("Đã APPROVED", "approve lần 2", NONE, ISE, A),
            ]),
            ("reject()", [
                ("PENDING", "\"duplicate meaning\"", "REJECTED, lưu lý do; phát CategoryRejectedEvent kèm lý do", NONE, N),
                ("PENDING", "lý do \" \"", NONE, IAE, B),
                ("Đã REJECTED", "reject lần 2", NONE, ISE, A),
            ]),
            ("pullDomainEvents()", [
                ("Đã approve và pull 1 lần", "pull lần 2", "list rỗng", NONE, B),
            ]),
        ],
    },
    {
        "sheet": "Listing",
        "module": "product-service",
        "desc": "Listing: số lượng khả dụng, version token, phân loại lý do giữ hàng thất bại",
        "pre": "Listing 100 đơn vị, token TOKEN",
        "groups": [
            ("create()", [
                (NONE, "minOrderQuantity 10", "quantityAvailable = 100; min = 10; versionToken = null", NONE, N),
                (NONE, "min 100.001; min 0; min null", "null được chấp nhận", "100.001 và 0: " + IAE, B),
            ]),
            ("reconstruct()", [
                (NONE, "Dòng cũ quantityAvailable null", "quantityAvailable vẫn null", NONE, B),
                (NONE, "updatedAt 2026-10-20 08:00:00.123456", "versionToken = \"2026-10-20T08:00:00.123456Z\"", NONE, N),
            ]),
            ("classifyRejectedReserve()", [
                (NONE, "Giữ 30 khi: còn null; còn 20; còn 60 nhưng min 40; còn 0 và CLOSED", "INSUFFICIENT_QUANTITY", NONE, B),
                (NONE, "Còn 60, CLOSED hoặc EXPIRED", "LISTING_STATE_CONFLICT", NONE, A),
                (NONE, "Còn 60, ACTIVE, token cũ", "VERSION_MISMATCH", NONE, A),
            ]),
        ],
    },
    {
        "sheet": "Product",
        "module": "product-service",
        "desc": "Product: tạo sản phẩm và quản lý ảnh (1–5 ảnh, URL http/https)",
        "pre": "Product \"Gao ST25\", category-1",
        "groups": [
            ("create()", [
                (NONE, "2 ảnh https", "đủ field; cover = ảnh đầu", NONE, N),
                (NONE, "List ảnh rỗng", NONE, IAE, B),
                (NONE, "images = null", NONE, IAE, A),
                (NONE, "6 ảnh", NONE, IAE, B),
                (NONE, "2 ảnh trùng URL", NONE, IAE, A),
                (NONE, "\"not-a-url\"", NONE, IAE, A),
                (NONE, "\"ftp://cdn.test/a.jpg\"", NONE, IAE, A),
            ]),
            ("updateImages()", [
                ("Product có 1 ảnh", "2 ảnh mới x, y", "thay toàn bộ; cover = x", NONE, N),
                ("Product có 1 ảnh", "List rỗng", "giữ nguyên ảnh cũ", IAE, A),
            ]),
        ],
    },
    {
        "sheet": "PlotGeometries",
        "module": "product-service",
        "desc": "PlotGeometries: dựng point/polygon, tính diện tích, hash hình học",
        "pre": "Toạ độ quanh (108.05, 12.66) – Đắk Lắk",
        "groups": [
            ("point()", [
                (NONE, "(108.0501234567, 12.6667891234), 1.5 ha", "WKT làm tròn 6 số; area 1.5000; hash 64 hex, trùng với toạ độ đã làm tròn", NONE, N),
                (NONE, "4.01 ha; 4 ha", "4 ha vẫn là POINT", "4.01 ha: " + IAE + " \"POLYGON\"", B),
                (NONE, "area null; area 0", NONE, IAE, B),
                (NONE, "Lat/lng đảo (12.66, 108.05); NaN", NONE, IAE + " \"outside Viet Nam\"", A),
            ]),
            ("polygon()", [
                (NONE, "Hình vuông cạnh 0.001° và 0.003°", "~1.18–1.24 ha và ~10.7–11.1 ha (tính trên mặt cầu)", NONE, N),
                (NONE, "Cạnh 0.08° (~7.800 ha); 0.1° (~12.000 ha); 1.0°", "7.800 ha được nhận", "0.1°: \"10000 ha\"; 1.0°: \"check the coordinates\"", B),
                (NONE, "Vuông 0.003° có lỗ 0.001°", "diện tích giảm ~1.18–1.24 ha", NONE, N),
                (NONE, "Ring hình nơ tự cắt", NONE, IAE + " \"Invalid polygon\"", A),
                (NONE, "Ring không đóng; ring 3 điểm", NONE, IAE + " \"closed\" / \"at least 4\"", B),
                (NONE, "Cùng hình, đổi điểm bắt đầu / đảo chiều; hình lệch 0.0001°", "hash giống nhau; hình lệch có hash khác", NONE, N),
            ]),
        ],
    },
]
