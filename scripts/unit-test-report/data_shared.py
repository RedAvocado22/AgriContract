N, A, B = "Normal", "Abnormal", "Boundary"
NONE = "N/A"
IAE = "IllegalArgumentException"
INV = "InvalidEnvelopeException"


def each(values, pre, fmt, ret, exc, typ):
    return [(pre, fmt.format(v), ret, exc, typ) for v in values]


MODULES = [
    {
        "sheet": "ErrorEnvelopeHandler",
        "module": "shared",
        "desc": "ErrorEnvelopeExceptionHandler: trả lỗi theo error envelope chung",
        "pre": "MockMvc standalone với TestController; clock cố định 2026-10-27T03:00:00Z",
        "groups": [
            ("Xử lý exception", [
                (NONE, "GET /conflict, X-Correlation-Id = corr-1", "409; code TERMS_FROZEN, message, path, timestamp; traceId = corr-1", NONE, N),
                (NONE, "GET /conflict không correlation id", "traceId vẫn được sinh", NONE, B),
                (NONE, "POST /validated {\"name\":\"\"}", "400; code VALIDATION_FAILED", NONE, A),
                (NONE, "POST /validated body \"{\"", "400; code MALFORMED_REQUEST", NONE, A),
                (NONE, "GET /denied", "403; code FORBIDDEN", NONE, A),
                (NONE, "GET /boom (exception lạ)", "500; code INTERNAL_ERROR; message \"Internal server error\" (giấu message gốc)", NONE, A),
                (NONE, "GET /service-specific", "404; code LISTING_NOT_FOUND (handler riêng của service thắng catch-all)", NONE, N),
            ]),
            ("new ApiException()", [
                (NONE, "code \"termsFrozen\"", NONE, IAE + " (code phải UPPER_SNAKE_CASE)", A),
            ]),
        ],
    },
    {
        "sheet": "EventContext",
        "module": "shared",
        "desc": "EventContext: lan truyền correlationId / causationId giữa các event",
        "pre": NONE,
        "groups": [
            ("root()", [
                (NONE, "\"corr-1\"", "correlationId corr-1; causationId null", NONE, N),
                (NONE, "null", "không tự sinh correlationId", NONE, B),
            ]),
            ("derivedFrom()", [
                (NONE, "Event cha gốc, corr-1", "causationId = eventId cha; giữ corr-1", NONE, N),
                (NONE, "Event cha đã có causation \"grandparent-id\"", "causationId = eventId cha, không phải ông", NONE, N),
                (NONE, "Event cha không có correlationId", "correlationId null", NONE, B),
                (NONE, "Chuỗi e1 → e2 → e3", "mỗi event trỏ về cha trực tiếp; cùng corr-1", NONE, N),
            ]),
            ("Constructor", [
                (NONE, "correlationId \"\"", NONE, IAE + " \"correlationId\"", B),
                (NONE, "causationId \"\"", NONE, IAE + " \"causationId\"", B),
            ]),
        ],
    },
    {
        "sheet": "EventEnvelope",
        "module": "shared",
        "desc": "EventEnvelope: validate envelope theo contracts/events/event-envelope.yaml",
        "pre": "Bộ field hợp lệ: contract.signed v1, contract-service, contract-1",
        "groups": [
            ("Constructor – hợp lệ", [
                (NONE, "Đủ field, causationId cause-1", "giữ nguyên mọi giá trị", NONE, N),
                (NONE, "correlationId, causationId, subject = null", "tạo được", NONE, N),
                (NONE, "payload = {}", "tạo được", NONE, B),
                (NONE, "producer \" \", correlationId \" \"", "tạo được (minLength 1)", NONE, B),
            ] + each(["contract.signed", "escrow.deposit_locked", "milestone.dispute_resolved", "bank.v2.lock_1"], NONE,
                     "eventType \"{}\"", "tạo được", NONE, N)),
            ("Constructor – không hợp lệ", [
                (NONE, "eventId null", NONE, IAE + " \"eventId\"", A),
            ] + each(["null", "\"\""], NONE, "eventType = {}", NONE, IAE + " \"eventType\"", B)
              + each(["contract", "contract.", ".signed", "Contract.signed", "contract.Signed",
                      "1contract.signed", "contract-signed", "\"contract.signed \"", "contract..signed"], NONE,
                     "eventType {} (sai pattern)", NONE, IAE + " \"eventType\"", A)
              + each(["0", "-1"], NONE, "eventVersion {}", NONE, IAE + " \"eventVersion\"", B)
              + [(NONE, "occurredAt null", NONE, IAE + " \"occurredAt\"", A)]
              + each(["null", "\"\""], NONE, "producer = {}", NONE, IAE + " \"producer\"", B)
              + each(["null", "\"\""], NONE, "aggregateId = {}", NONE, IAE + " \"aggregateId\"", B)
              + [
                (NONE, "correlationId \"\"", NONE, IAE + " \"correlationId\"", B),
                (NONE, "causationId \"\"", NONE, IAE + " \"causationId\"", B),
                (NONE, "payload null", NONE, IAE + " \"payload\"", A),
            ]),
        ],
    },
    {
        "sheet": "EventSubject",
        "module": "shared",
        "desc": "EventSubject: type + id của chủ thể event",
        "pre": NONE,
        "groups": [
            ("Constructor", each(["CONTRACT", "USER_PAIR", "SYSTEM"], NONE, "type {}, id subject-1", "giữ type, id", NONE, N) + [
                (NONE, "type null", NONE, IAE + " \"subject.type\"", A),
            ] + each(["null", "\"\""], NONE, "id = {}", NONE, IAE + " \"subject.id\"", B)),
        ],
    },
    {
        "sheet": "EventEnvelopeCodec",
        "module": "shared",
        "desc": "EventEnvelopeCodec: serialize/parse envelope JSON chặt theo schema, không lộ dữ liệu trong lỗi",
        "pre": "JSON envelope hợp lệ mẫu, occurredAt 2026-10-23T03:00:00Z",
        "groups": [
            ("toJson()", [
                (NONE, "Envelope có correlationId", "occurredAt là chuỗi ISO có Z", NONE, N),
                (NONE, "subject, correlationId, causationId = null", "các field này vắng mặt, không ghi null", NONE, B),
                (NONE, "payload có remedyLegId = null", "giữ \"remedyLegId\": null trong payload", NONE, B),
                (NONE, "Envelope đủ field", "chỉ có tên field trong schema; subject.type = \"CONTRACT\"", NONE, N),
                (NONE, "toJson rồi parse lại", "envelope bằng bản gốc", NONE, N),
                (NONE, "Cùng envelope 2 lần", "chuỗi JSON giống hệt", NONE, N),
            ]),
            ("parse() – hợp lệ", [
                (NONE, "JSON tối thiểu", "parse được; subject, correlationId = null", NONE, N),
                (NONE, "correlationId, causationId = null tường minh", "parse được", NONE, B),
            ]),
            ("parse() – không hợp lệ", [
                (NONE, "subject = null tường minh", NONE, INV, A),
            ] + each(["field lạ ở envelope", "field lạ trong subject", "subject.type BUYER (ngoài enum)", "eventId lặp key"], NONE,
                     "{}", NONE, INV, A) + [
                (NONE, "Thiếu eventVersion", NONE, INV, A),
                (NONE, "eventType \"Contract.Signed\"", NONE, INV, A),
                (NONE, "occurredAt không có offset", NONE, INV, B),
            ] + each(["\"\"", "\"not json\"", "\"[]\"", "\"\\\"text\\\"\"", "\"{\\\"eventId\\\":\""], NONE,
                     "Body {}", NONE, INV, A) + [
                (NONE, "payload là string", NONE, INV, A),
                (NONE, "JSON hợp lệ + \"{}\" thừa phía sau", NONE, INV, A),
                (NONE, "Field lạ chứa email", NONE, INV + "; message không chứa email", A),
                (NONE, "eventVersion = email", NONE, INV + "; message và cause không chứa email", A),
                (NONE, "subject.type BUYER", NONE, INV + " nêu đường dẫn \"subject.type\"", A),
                (NONE, "eventVersion 0", NONE, INV + " \"eventVersion must be >= 1\", không cause", B),
            ] + each(["eventVersion \"1\"", "eventVersion 1.9", "producer 123", "aggregateId true",
                      "occurredAt 1700000000", "occurredAt 1700000000.5", "occurredAt true", "eventId 5"], NONE,
                     "{} (sai kiểu)", NONE, INV + " (không tự ép kiểu)", A) + [
                (NONE, "correlationId 5", NONE, INV + " (không tự ép kiểu)", A),
                (NONE, "occurredAt 1700000000", NONE, INV + " nêu \"occurredAt\"", A),
            ]),
        ],
    },
]
