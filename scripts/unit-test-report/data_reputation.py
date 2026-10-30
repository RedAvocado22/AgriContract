N, A, B = "Normal", "Abnormal", "Boundary"
NONE = "N/A"
IAE = "IllegalArgumentException"
REJECT = "AmqpRejectAndDontRequeueException; không gọi use case"


def each(values, pre, fmt, ret, exc, typ):
    return [(pre, fmt.format(v), ret, exc, typ) for v in values]


MODULES = [
    {
        "sheet": "ApplicationBoundary",
        "module": "reputation-service",
        "desc": "Biên application layer: use case chỉ nhận/trả DTO bất biến, map command sang fact",
        "pre": "FactRepository, ReputationReadRepository mock; thời điểm cố định",
        "groups": [
            ("Kiểu dữ liệu use case", [
                (NONE, "AppendReputationFact, GetPublicReputationSummary", "tham số và kiểu trả về của execute() đều là record trong application.dto", NONE, N),
            ]),
            ("AppendReputationFact.execute()", [
                ("Repository trả INSERTED", "AppendCompletedContractCommand", "fact đủ field; settledAt cắt về micro giây; kết quả INSERTED", NONE, N),
                ("Repository trả DUPLICATE", "AppendDisputeOutcomeCommand, favors \"SELLER\"", "fact giữ người flag và resolution; kết quả DUPLICATE", NONE, B),
                (NONE, "favors \"UNKNOWN\"", "không chạm repository", IAE, A),
                ("Repository ném ImmutableFactConflictException", "AppendCompletedContractCommand", "không biến thành thành công", "ImmutableFactConflictException", A),
            ]),
            ("GetPublicReputationSummary.execute()", [
                ("buyer có 4 hợp đồng hoàn tất, 1 khoá đang active", "PublicSummaryQuery(buyer)", "score 80, locked = true; lockHistory là bản sao bất biến, chỉ lộ field cho phép", NONE, N),
            ]),
        ],
    },
    {
        "sheet": "FactAndScore",
        "module": "reputation-service",
        "desc": "Công thức điểm uy tín và validate fact (CompletedContractFact, DisputeOutcomeFact)",
        "pre": NONE,
        "groups": [
            ("score(completed, locks)", [
                (NONE, "(0, 0)", "null (chưa có dữ liệu)", NONE, B),
                (NONE, "(5, 0)", "100", NONE, N),
                (NONE, "(4, 1)", "80", NONE, N),
                (NONE, "(1, 7)", "13", NONE, N),
                (NONE, "(0, 7)", "0", NONE, B),
                (NONE, "(2, 1)", "67 (làm tròn, scale 0)", NONE, N),
                (NONE, "(1, 2)", "33 (làm tròn, scale 0)", NONE, N),
                (NONE, "(Long.MAX_VALUE, Long.MAX_VALUE)", "50, không tràn số", NONE, B),
                (NONE, "(-1, 0); (0, -1)", NONE, IAE, A),
            ]),
            ("Fact record", [
                (NONE, "Thời điểm có nano giây", "là record; thời gian cắt về micro giây", NONE, N),
            ] + each(["contractId", "buyerId", "sellerId", "settledAt", "eventId"], NONE,
                     "CompletedContractFact thiếu {}", NONE, "Exception (field bắt buộc)", A)
              + each(["milestoneId", "contractId", "flaggedBy", "resolutionFavors", "resolvedAt", "eventId"], NONE,
                     "DisputeOutcomeFact thiếu {}", NONE, "Exception (field bắt buộc)", A)
              + each(["\"1-1-1-1-1\"", "\"not-uuid\"", "\"\"", "\" 00000000-…-000000000001\" (có khoảng trắng)"], NONE,
                     "UUID = {}", NONE, IAE + " (UUID chặt)", B)),
        ],
    },
    {
        "sheet": "RabbitTopology",
        "module": "reputation-service",
        "desc": "RabbitConfiguration: DLX riêng của reputation, mỗi queue có DLQ cùng routing key",
        "pre": NONE,
        "groups": [
            ("reputationBindings()", [
                (NONE, "Topology khai báo", "1 DLX agricontract.reputation.dlx durable; mỗi queue có x-dead-letter-exchange, không đổi routing key; DLQ bind đúng key gốc", NONE, N),
            ]),
        ],
    },
    {
        "sheet": "FrozenEventContract",
        "module": "reputation-service",
        "desc": "Fixture event dùng trong test khớp schema frozen trong contracts/",
        "pre": "Schema frozen nạp từ contracts/events",
        "groups": [
            ("Schema", [
                (NONE, "Fixture contract.settled", "khớp schema contractSettled", NONE, N),
                (NONE, "Fixture milestone.dispute_resolved", "khớp schema milestoneDisputeResolved", NONE, N),
            ]),
        ],
    },
    {
        "sheet": "DlqMonitor",
        "module": "reputation-service",
        "desc": "DlqMonitor: đo độ sâu DLQ, log WARN an toàn, không lộ dữ liệu nhạy cảm",
        "pre": "ConnectionFactory, Channel mock; log appender ghi lại",
        "groups": [
            ("checkDlqDepth()", [
                ("DLQ contract.settled có 7 message", NONE, "Log: 1 dòng WARN \"event=reputation_dlq_nonempty queue=… messageCount=7\", không stacktrace", NONE, N),
                ("Mọi DLQ rỗng", NONE, "không log; chỉ kiểm queue của mình; đóng connection mỗi lần", NONE, N),
                ("Probe queue đầu ném IOException chứa secret", "Chạy 2 lần", "Log: 1 dòng \"reputation_dlq_probe_failed\" không lộ secret; queue khác vẫn được kiểm", NONE, A),
                ("Mở connection: timeout rồi runtime error", NONE, "không ném ra ngoài; 2 dòng log không chứa \"private\"", NONE, A),
                ("Driver báo lỗi bất đồng bộ chứa secret", NONE, "không log gì", NONE, A),
                ("Broker nhận socket nhưng không bắt tay", NONE, "kết thúc trong giới hạn timeout", NONE, B),
            ]),
            ("Constructor", [
                (NONE, NONE, "timeout 1000 ms mọi chặng; tắt auto recovery; dùng bản clone của factory", NONE, N),
            ] + each(["(0, 300000)", "(10001, 300000)", "(1000, 0)", "(1000, 29999)", "(1000, 3600001)"], NONE,
                     "(timeout, interval) = {}", NONE, IAE, B)),
        ],
    },
    {
        "sheet": "ReputationFactConsumer",
        "module": "reputation-service",
        "desc": "ReputationFactConsumer: parse contract.settled / milestone.dispute_resolved thành command",
        "pre": "Event hợp lệ theo contract frozen; AppendReputationFact mock",
        "groups": [
            ("completed()", [
                (NONE, "contract.settled hợp lệ", "command với UUID contract/buyer/seller/event, thời điểm UTC", NONE, N),
            ] + each([
                "thiếu eventId", "eventId \"1-1-1-1-1\"", "eventVersion 0", "eventVersion 2", "eventVersion 1.1",
                "eventVersion 4294967297", "producer inspection-service", "eventType breach.reported",
                "eventType remedy.finalized", "eventType contract.terminated", "occurredAt \"not-time\"",
                "thiếu aggregateId", "field lạ \"email\" ở envelope", "payload là string", "correlationId rỗng",
                "subject type INVALID", "payload.buyerId là số", "field lạ \"email\" trong payload",
            ], NONE, "Envelope: {}", NONE, REJECT, A) + [
                ("Use case ném ImmutableFactConflictException; rồi DataAccessResourceFailureException", "contract.settled hợp lệ", "conflict: không retry; DB sập: ném lại để retry", "AmqpRejectAndDontRequeueException / DataAccessResourceFailureException", A),
                (NONE, "Body \"null\", \"{\", \"[]\", JSON thừa ký tự, eventVersion trùng key", NONE, REJECT, A),
            ]),
            ("dispute()", [
                (NONE, "milestone.dispute_resolved hợp lệ", "command map trực tiếp milestone, contract, người flag, \"SELLER\"", NONE, N),
                (NONE, "payload thêm flaggedBy = SELLER", NONE, REJECT, A),
            ]),
        ],
    },
    {
        "sheet": "GatewayConfiguration",
        "module": "reputation-service",
        "desc": "GatewayIdentityFilter: thiếu gateway secret thì không cho khởi động",
        "pre": NONE,
        "groups": [
            ("Constructor", each(["null", "\"\"", "\" \"", "\"\\t\""], NONE, "secret = {}", NONE, IAE, B)),
        ],
    },
]
