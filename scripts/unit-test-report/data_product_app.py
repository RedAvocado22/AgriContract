N, A, B = "Normal", "Abnormal", "Boundary"
NONE = "N/A"
IAE = "IllegalArgumentException"
ISE = "IllegalStateException"

MODULES = [
    {
        "sheet": "ApproveCategory",
        "module": "product-service",
        "desc": "ApproveCategoryUseCaseImpl: duyệt danh mục đang chờ",
        "pre": "CategoryRepository mock",
        "groups": [
            ("execute()", [
                ("category-1 PENDING", "category-1", "status APPROVED; có save", NONE, N),
                ("Không có category", "missing", "không save", "CategoryNotFoundException", A),
                ("category-1 đã APPROVED", "category-1", NONE, ISE, A),
            ]),
        ],
    },
    {
        "sheet": "ProposeCategory",
        "module": "product-service",
        "desc": "ProposeCategoryUseCaseImpl: đề xuất danh mục, chặn trùng tên đã chuẩn hoá",
        "pre": "CategoryRepository mock; người đề xuất seller-1",
        "groups": [
            ("execute()", [
                ("Chưa có tên \"ca phe\"", "category-1, \"Cà Phê\"", "trả categoryId, name; có save", NONE, N),
                ("Đã có category \"ca phe\" (bất kể status)", "\"Ca Phe\"", "không save", "DuplicateCategoryException", A),
                ("Lookup không thấy, nhưng save vi phạm unique (race)", "\"Ca Phe\"", NONE, "DuplicateCategoryException", B),
            ]),
        ],
    },
    {
        "sheet": "RejectCategory",
        "module": "product-service",
        "desc": "RejectCategoryUseCaseImpl: từ chối danh mục kèm lý do",
        "pre": "CategoryRepository mock",
        "groups": [
            ("execute()", [
                ("category-1 PENDING", "category-1, \"trung ten\"", "status REJECTED, lưu lý do; có save", NONE, N),
                ("Không có category", "missing, \"reason\"", "không save", "CategoryNotFoundException", A),
                ("category-1 PENDING", "category-1, \" \"", "không save", IAE, B),
            ]),
        ],
    },
    {
        "sheet": "ImportPlotsFromKml",
        "module": "product-service",
        "desc": "ImportPlotsFromKmlUseCaseImpl: import lô đất từ KML, từ chối từng placemark lỗi",
        "pre": "PlotRegistrar mock; seller cố định",
        "groups": [
            ("execute()", [
                (NONE, "KML: Ho A, Ho B (đã có), không diện tích, 4.5 ha, toạ độ ngoài VN, Ho A lặp, tỉnh DAK_LAK", "created [Ho A], updated [Ho B]; các placemark lỗi bị liệt kê theo tên", NONE, N),
                (NONE, "Nhãn dài 300 ký tự, polygon cả độ vuông, cùng Ho A, Ho B", "Ho A, Ho B vẫn vào; rejected [nhãn dài, Huge]", NONE, B),
                (NONE, "1 polygon Ho D, tỉnh GIA_LAI", "đăng ký POLYGON, diện tích > 10 ha, KML_IMPORT, GIA_LAI", NONE, N),
                (NONE, "KML không placemark; tỉnh = null", "không gọi registrar", IAE + " \"no Placemark\" / \"declaredProvince\"", A),
                (NONE, "MAX_PLACEMARKS + 1 placemark", "không lưu gì", IAE + " \"split\"", B),
            ]),
        ],
    },
    {
        "sheet": "PlotRegistrar",
        "module": "product-service",
        "desc": "PlotRegistrar: tạo mới hoặc cập nhật lô đất theo (seller, nhãn hộ)",
        "pre": "PlotRegistryRepository mock; NOW cố định",
        "groups": [
            ("register()", [
                ("Chưa có hộ \"Ho A\"", "seller-1, \" Ho A \", MANUAL_PIN", "created = true; nhãn \"Ho A\", PENDING, importedAt = NOW; không update", NONE, N),
                ("Đã có \"Hộ Lê Văn A\" (NFC)", "Cùng nhãn dạng NFD, tỉnh LAM_DONG", "created = false, cập nhật plot-1; giữ kết quả geo-check vì cùng geometry", NONE, B),
                ("Lookup trống, create đụng unique (race)", "\"Hộ Lê Văn A\"", "đọc lại bản thắng và update plot-1", NONE, B),
                ("create lỗi \"Data too long\"", "\"Ho A\"", "không coi là race, không update", "ném lại đúng DataIntegrityViolationException", A),
                (NONE, "Nhãn 256 ký tự", "không chạm DB", IAE, B),
            ]),
        ],
    },
    {
        "sheet": "CreateListing",
        "module": "product-service",
        "desc": "CreateListingUseCaseImpl: tạo listing cà phê gắn lô đất của seller",
        "pre": "Product cà phê Robusta tồn tại; ListingRepository, PlotRegistryRepository mock",
        "groups": [
            ("execute()", [
                ("plot-1, plot-2 của seller, UNVERIFIED", "2 plot, 100 kg, min 10", "snapshot ảnh bìa, plot; quantityAvailable 100; min 10", NONE, N),
                ("plot-1 hợp lệ", "Request kèm goods khai báo", "category, commodity, mùa vụ lấy từ product; quality spec, chứng nhận, vùng lấy từ request", NONE, N),
                ("Category duyệt từ trước khi có commodity", "plot-1", "không save, không đọc plot", IAE + " \"COFFEE\"", A),
                ("plot-1 hợp lệ", "Độ ẩm 12.555", "không save", IAE + " \"2 decimals\"", B),
                ("plot-1 thuộc seller-2", "plot-1", "không save", "PlotNotOwnedException", A),
                ("Không có plot-404", "plot-404", "trả lỗi giống plot của người khác; không save", "PlotNotOwnedException", A),
                ("plot-2 còn PENDING geo-check", "plot-1, plot-2", "không save", "PlotNotEligibleException \"plot-2\"", A),
                (NONE, "plot-1 hai lần", "không save", IAE, A),
                ("listing-1 đã tạo trước", "Replay cùng id: spec 12.50 (= 12.5); đổi sang plot-2; đổi sang ARABICA", "spec cùng giá trị trả listing-1; không save lần nào", "đổi plot / chất lượng: " + ISE, B),
            ]),
        ],
    },
    {
        "sheet": "CreateProduct",
        "module": "product-service",
        "desc": "CreateProductUseCaseImpl: tạo sản phẩm trong category cà phê đã duyệt, idempotent theo id",
        "pre": "ProductRepository, CategoryRepository mock",
        "groups": [
            ("execute()", [
                ("category-1 APPROVED, COFFEE", "product-1 \"Gao ST25\"", "trả categoryId, ảnh đúng", NONE, N),
                ("category-1 APPROVED", "cropYear 2026, harvestSeason \"  \", 1 chứng nhận", "cropYear 2026; harvestSeason = null; giữ chứng nhận", NONE, B),
                ("Category duyệt từ trước khi có commodity", "product-1", "không save", IAE + " \"COFFEE\"", A),
                ("Không có category", "category = missing", "không save", "CategoryNotFoundException", A),
                ("category-1 PENDING", "product-1", "không save", "CategoryNotApprovedException", A),
                ("product-1 đã tồn tại cùng thuộc tính", "Cùng request", "trả product cũ; không save, không đọc category", NONE, B),
                ("product-1 đã tồn tại", "Cùng id, tên \"Gao khac\"", NONE, ISE, A),
            ]),
        ],
    },
    {
        "sheet": "UpdateProductImages",
        "module": "product-service",
        "desc": "UpdateProductImagesUseCaseImpl: thay toàn bộ ảnh sản phẩm",
        "pre": "ProductRepository mock",
        "groups": [
            ("execute()", [
                ("product-1 có 1 ảnh", "product-1, ảnh x, y", "ảnh = [x, y]; có save", NONE, N),
                ("Không có product", "missing", "không save", "ProductNotFoundException", A),
                ("product-1 tồn tại", "List ảnh rỗng", "không save", IAE, B),
            ]),
        ],
    },
    {
        "sheet": "KmlPlacemarkReader",
        "module": "product-service",
        "desc": "KmlPlacemarkReader: đọc placemark point/polygon từ KML, chặn XXE",
        "pre": NONE,
        "groups": [
            ("read()", [
                (NONE, "Point \"Hộ Lê Văn A\", area_ha 1.5 trong ExtendedData", "1 placemark POINT, toạ độ (108.050123, 12.666789), 1.5 ha", NONE, N),
                (NONE, "SchemaData dien_tich = \"2,25\"", "area 2.25 (đọc được dấu phẩy thập phân)", NONE, B),
                (NONE, "Point không có diện tích", "đọc được, area = null", NONE, B),
                (NONE, "Polygon có 1 lỗ", "POLYGON, shell 5 điểm, 1 hole 4 điểm", NONE, N),
                (NONE, "Placemark không tên, MultiGeometry, LineString, toạ độ \"abc\", không geometry, 1 placemark tốt", "5 cái đầu REJECTED kèm lý do (\"#1\", \"MultiGeometry\", \"not a number\"); cái cuối POINT", NONE, A),
                (NONE, "KML có DOCTYPE + external entity", NONE, IAE + " \"not well-formed\"", A),
                (NONE, "\"<gpx/>\"; \"not xml\"", NONE, IAE + " \"<kml>\"", A),
            ]),
        ],
    },
]
