# NestJS services (Jest): one sheet per spec file, one row per scenario. Tests that only repeat one check over
# a table of inputs are one row whose input lists the values. "test_file" is the spec path inside the service.
N, A, B = "Normal", "Abnormal", "Boundary"
NONE = "N/A"
DLQ = "NonRetryableError → DLQ"


def node(sheet, module, test_file, date, desc, pre, groups):
    return {"sheet": sheet, "module": module, "test_file": test_file, "date": date, "desc": desc, "pre": pre,
            "groups": groups}


NOTIF = "notification-service"
PRICING = "pricing-service"
ANALYTICS = "analytics-service"

MODULES = [
    # ── notification-service (Nguyen Viet Hung) ──────────────────────────────
    node("NotificationSender", NOTIF, "src/application/notification-sender.spec.ts", "02/11/2026",
         "NotificationSender.send: gửi email 1 lần cho mỗi (event, người nhận, loại thông báo)",
         "Repository notification_log in-memory; email adapter giả; clock và sleep điều khiển được", [
             ("send()", [
                 (NONE, "Event mới, 1 người nhận", "gửi; ghi SENT kèm email người nhận", NONE, N),
                 ("Người nhận đã SENT cho event này", "Gửi lại", "bỏ qua, không gửi", NONE, B),
                 (NONE, "Provider từ chối", "ghi FAILED, retry_count + 1", "Ném lỗi để retry", A),
                 ("Lần trước FAILED", "Event gửi lại", "gửi lại, ghi SENT", NONE, N),
                 (NONE, "Cùng người nhận, event khác", "là email riêng", NONE, N),
             ]),
             ("Message id của provider", [
                 (NONE, "Provider trả id / không trả id", "ghi id nếu có; SENT không id nếu không có", NONE, N),
                 (NONE, "Lần lỗi rồi gửi lại; nhiều người nhận 1 event", "lần lỗi không ghi id; mỗi người nhận giữ id riêng", NONE, B),
             ]),
             ("Chống trùng theo (event, người nhận, loại)", [
                 (NONE, "Gửi thành công", "ghi loại email, thời điểm gửi, id nền tảng của người nhận nếu có", NONE, N),
                 (NONE, "1 event 2 người nhận; 1 người nhận 2 loại thông báo", "2 email, 2 bản ghi", NONE, N),
                 (NONE, "Retry sau nhiều lần lỗi", "dùng lại 1 bản ghi; ghi nội dung của lần gửi này", NONE, B),
                 ("Bản ghi cũ trước migration V2 ở trạng thái SENT / FAILED / PENDING", "Event gửi lại",
                  "SENT thì không gửi; FAILED/PENDING thì gửi với bản ghi mới; bản ghi của người khác không chặn", NONE, B),
             ]),
             ("Nhiều lần giao cùng event chạy song song", [
                 (NONE, "2 lần giao cùng lúc", "chỉ bên giữ claim gửi; bên kia chờ, thấy SENT thì thôi", NONE, N),
                 ("Bản ghi PENDING quá hạn lease (bên gửi đã chết)", "Lần giao mới", "lấy lại và gửi ngay", NONE, B),
                 ("Bản ghi FAILED", "2 lần giao cùng lúc", "đúng 1 bên lấy lại; bên kia thấy SENT", NONE, B),
             ]),
             ("Chờ claim của lần giao khác", [
                 (NONE, "Claim còn sống", "chờ tối đa 50 s, hỏi lại mỗi 1 s", NONE, B),
                 (NONE, "Trong lúc chờ: SENT / FAILED / bên giữ chết quá 45 s", "bỏ qua / lấy claim và gửi / lấy claim và gửi", NONE, N),
                 (NONE, "Bên giữ còn sống sau 50 s", "không gửi", "SendInProgressError (retry được)", A),
                 ("Đang tắt service", "Đang chờ claim", "nhìn lần cuối rồi dừng; SENT thì bỏ qua; sleep kết thúc ngay khi bắt đầu tắt", "ClaimWaitAbortedError", B),
             ]),
             ("Heartbeat giữ claim khi đang gửi", [
                 (NONE, "Gửi lâu", "lease 45 s, làm mới mỗi 15 s; lần giao khác không lấy được", NONE, N),
                 (NONE, "Gửi xong / gửi lỗi", "dừng heartbeat", NONE, N),
                 (NONE, "1 nhịp heartbeat lỗi; nhịp trước chưa xong", "log lỗi chỉ có eventId và mã, vẫn gửi tiếp; không chồng nhịp", NONE, A),
             ]),
             ("Message lỗi an toàn để log", [
                 (NONE, "Gửi lỗi; repository lỗi; claim còn sống sau khi chờ",
                  "message chỉ có event và mã lỗi, không có người nhận hay text của driver", NONE, A),
             ]),
         ]),
    node("OtpEmailRequest", NOTIF, "src/application/otp/otp-email-request.spec.ts", "07/11/2026",
         "parseOtpEmailRequest: kiểm body yêu cầu gửi email OTP", "Body mẫu hợp lệ theo OtpEmailRequest", [
             ("parseOtpEmailRequest()", [
                 (NONE, "Body hợp lệ; expiresAt có offset và phần lẻ giây", "trả đúng các field của contract", NONE, N),
                 (NONE, "Body là null, [], chuỗi, số", NONE, "Lỗi validate", A),
                 (NONE, "Thiếu từng field", "báo từng field thiếu", "Lỗi validate", A),
                 (NONE, "requestId rỗng / quá 36 ký tự / có khoảng trắng; recipientUserId, contractId rỗng", NONE, "Lỗi validate", B),
                 (NONE, "Email sai định dạng hoặc quá dài", NONE, "Lỗi validate", B),
                 (NONE, "otpCode 5 / 7 chữ số, có chữ, dạng số", NONE, "Lỗi validate", B),
                 (NONE, "signedContentHash viết hoa hoặc thiếu 1 ký tự", NONE, "Lỗi validate", B),
                 (NONE, "expiresAt chỉ có ngày / thiếu múi giờ / ngày không tồn tại", NONE, "Lỗi validate", A),
                 (NONE, "Field lạ", "message không nêu tên field lạ", "Lỗi validate", A),
                 (NONE, "Mọi lỗi", "message không bao giờ trích giá trị", NONE, A),
             ]),
             ("purpose (D42)", [
                 (NONE, "Không có purpose", "SIGN_CONTRACT; không bị báo thiếu", NONE, B),
                 (NONE, "SIGN_CONTRACT / CANCEL_ACTIVATION", "chấp nhận", NONE, N),
                 (NONE, "null, chữ thường, TERMINATE_CONTRACT, số 1, rỗng", NONE, "Lỗi validate", A),
             ]),
         ]),
    node("OtpEmailTemplate", NOTIF, "src/application/otp/otp-email-template.spec.ts", "07/11/2026",
         "renderOtpEmail: nội dung email OTP", "Yêu cầu OTP mẫu", [
             ("renderOtpEmail() – ký hợp đồng", [
                 (NONE, "Yêu cầu SIGN_CONTRACT", "subject không chứa mã; mã nằm riêng 1 dòng, sau đó là hợp đồng và hash đầy đủ", NONE, N),
                 (NONE, "expiresAt có offset", "hiện giờ Việt Nam và UTC", NONE, N),
                 (NONE, "So với bản otp-email/v1", "giống hệt từng ký tự", NONE, N),
             ]),
             ("renderOtpEmail() – huỷ kích hoạt", [
                 (NONE, "Yêu cầu CANCEL_ACTIVATION", "subject riêng không có mã; nói rõ mã xác nhận yêu cầu huỷ, không phải chữ ký; có mã, hợp đồng, hạn, hash", NONE, N),
             ]),
             ("Định dạng email", [
                 (NONE, "Contract id 66 ký tự, cả 2 loại email", "mỗi dòng ≤ 76 ký tự; gửi 7bit nên mã và hash không bị cắt", NONE, B),
                 (NONE, "Nội dung lưu vào log", "chỉ là placeholder, không chứa mã", NONE, N),
             ]),
         ]),
    node("SendOtpEmail", NOTIF, "src/application/otp/send-otp-email.use-case.spec.ts", "07/11/2026",
         "SendOtpEmailUseCase: gửi email OTP 1 lần theo requestId", "Repository và email adapter giả", [
             ("execute()", [
                 (NONE, "Yêu cầu mới", "gửi email; ghi SENT không lưu mã; ghi id provider nếu có", NONE, N),
                 (NONE, "Mã ký và mã huỷ cùng địa chỉ", "2 email, 2 bản ghi; mã huỷ dùng template và loại riêng", NONE, N),
                 ("requestId đã SENT", "Gọi lại", "không gửi lại", NONE, B),
                 (NONE, "Provider từ chối", "ghi FAILED", "Lỗi chỉ có mã", A),
                 ("requestId đã FAILED", "Gọi lại", "gửi lại", NONE, N),
                 ("Lần gọi khác đang giữ claim", "Gọi", "trả về ngay", "OtpEmailInProgressError", B),
                 ("Claim quá hạn lease", "Gọi", "lấy lại và gửi", NONE, B),
                 (NONE, "Không claim được log", "không gửi gì", NONE, A),
                 (NONE, "Không ghi được SENT / FAILED", "vẫn thành công, chỉ log mã / vẫn báo lỗi provider", NONE, A),
             ]),
         ]),
    node("NotifyContractAnchor", NOTIF, "src/application/use-cases/notify-contract-anchor.use-case.spec.ts", "10/11/2026",
         "NotifyContractAnchorUseCase: email neo hợp đồng đã ký", "Lệnh contract_anchor_requested mẫu", [
             ("execute()", [
                 (NONE, "Lệnh có 2 người nhận", "gửi từng người theo thứ tự; body có hash và toàn bộ terms đã ký", NONE, N),
                 (NONE, "Dòng terms chuẩn hoá in trong email (fixture terms, mẫu RFC 8785)", "băm lại ra đúng hash đã ký", NONE, N),
                 (NONE, "Ghi log", "1 bản ghi / người nhận, có id nền tảng và phiên bản template; không có id thì null", NONE, N),
                 (NONE, "Lệnh gửi lại", "không gửi lần 2", NONE, B),
                 ("Người nhận thứ 2 lỗi", "Gửi lại", "chỉ gửi lại người thứ 2", NONE, A),
                 (NONE, "2 người nhận cùng địa chỉ", "1 email", NONE, B),
             ]),
         ]),
    node("NotifyActivationCancel", NOTIF, "src/application/use-cases/notify-activation-cancel-requested.use-case.spec.ts", "10/11/2026",
         "NotifyActivationCancelRequestedUseCase: báo bên kia có yêu cầu huỷ kích hoạt", "Lệnh activation_cancel_requested mẫu", [
             ("execute()", [
                 (NONE, "Lệnh hợp lệ", "gửi bên kia, subject có hợp đồng; ghi loại, phiên bản template, id nền tảng", NONE, N),
                 (NONE, "Lệnh gửi lại", "không gửi lần 2", NONE, B),
                 (NONE, "Gửi lỗi", "ném lại lỗi; lần giao sau gửi được", NONE, A),
             ]),
             ("activationCancelBody()", [
                 (NONE, "Buyer / seller yêu cầu", "nói ai yêu cầu, bên kia làm được gì, từ khi nào im lặng là đủ", NONE, N),
                 (NONE, "Người nhận là OPERATOR hoặc chính bên yêu cầu", "chỉ có thông tin, không có dòng hướng dẫn hành động", NONE, N),
                 (NONE, "Thời điểm có offset; người nhận BUYER / SELLER / OPERATOR", "đổi múi giờ đúng; mọi dòng trong giới hạn ký tự", NONE, B),
             ]),
         ]),
    node("NotifyLegacyUseCases", NOTIF, "src/application/use-cases.spec.ts", "02/11/2026",
         "9 use case thông báo Phase 1 (giữ nguyên hành vi bản Java)", "Fixture email chụp từ service Java", [
             ("Notify*UseCase", [
                 (NONE, "9 loại event Phase 1", "mỗi loại 1 use case; subject, body, người nhận và thứ tự giống bản Java", NONE, N),
                 (NONE, "contract.cancelled / disputed thiếu lý do", "in \"null\" như bản Java", NONE, B),
                 (NONE, "contract.disputed", "gửi admin, seller rồi buyer", NONE, N),
                 ("Người nhận thứ 2 lỗi", "Retry", "không gửi lại người thứ nhất", NONE, A),
                 (NONE, "Event gửi lại khi mọi người đã SENT; gửi lại qua lần deploy V2", "không gửi / chỉ gửi người chưa SENT", NONE, B),
             ]),
         ]),
    node("NotificationLog", NOTIF, "src/domain/notification-log.spec.ts", "02/11/2026",
         "NotificationLog: bản ghi thông báo", NONE, [
             ("NotificationLog", [
                 (NONE, "Tạo mới", "PENDING, chưa retry, id UUID; khoá theo event, người nhận, loại; lấy phiên bản template của loại", NONE, N),
                 (NONE, "Có / không có id nền tảng của người nhận", "giữ id / null", NONE, B),
                 (NONE, "markSent / markFailed", "SENT không đổi retry_count / FAILED và đếm mỗi lần lỗi", NONE, N),
                 (NONE, "reconstitute; giá trị enum", "giữ nguyên trạng thái lưu; enum giống bản Java", NONE, N),
             ]),
         ]),
    node("NotificationKinds", NOTIF, "src/application/notification-command-kinds.spec.ts", "10/11/2026",
         "Danh mục loại thông báo (lệnh Phase 2 và event Phase 1)", NONE, [
             ("COMMAND_KINDS", [
                 (NONE, "Mỗi lệnh tiêu thụ", "đúng 1 loại; loại và phiên bản template không trùng; vừa cột VARCHAR(50)", NONE, B),
             ]),
             ("PHASE1_KINDS (phase1-notification-kinds.spec.ts)", [
                 (NONE, "Mỗi routing key Phase 1", "đúng 1 loại riêng, không dùng LEGACY_P1 hay p1-unknown; tên loại đã lưu không đổi", NONE, B),
             ]),
         ]),
    node("CanonicalJson", NOTIF, "src/application/canonical-json.spec.ts", "10/11/2026",
         "canonicalJson: chuẩn hoá JSON theo RFC 8785", NONE, [
             ("canonicalJson()", [
                 (NONE, "Mẫu RFC 8785 §3.2.4; fixture terms", "đúng chuỗi của RFC; 1 dòng không khoảng trắng", NONE, N),
                 (NONE, "Key cần sắp theo UTF-16; object lồng; mảng; null", "sắp key đúng, giữ thứ tự mảng và field null", NONE, B),
                 (NONE, "NaN, Infinity, undefined", NONE, "Từ chối", A),
             ]),
         ]),
    node("NotificationEnv", NOTIF, "src/config/env.validation.spec.ts", "02/11/2026",
         "validate(env): đọc biến môi trường", NONE, [
             ("validate()", [
                 (NONE, "Không đặt gì", "dùng mặc định như bản Java", NONE, N),
                 (NONE, "Đặt giá trị; port dạng chuỗi số", "đọc đúng, đổi port sang số", NONE, N),
                 (NONE, "SERVICE_INTERNAL_SECRET rỗng", NONE, "Từ chối", A),
                 (NONE, "Port không hợp lệ (âm, quá 65535, có chữ, rỗng)", NONE, "Từ chối", B),
                 (NONE, "Nhiều biến sai", "báo tất cả một lần, không in secret", NONE, A),
             ]),
         ]),
    node("NotifInfraHelpers", NOTIF, "src/infrastructure/describe-error.spec.ts", "02/11/2026",
         "Hàm hỗ trợ hạ tầng: describeError, eventingLogger, smoke test thư viện eventing", NONE, [
             ("describeError()", [
                 (NONE, "AggregateError; lỗi chỉ có code / name; giá trị không phải Error", "liệt kê lỗi con; dùng code rồi name; chuyển thành chuỗi", NONE, N),
             ]),
             ("eventingLogger (eventing-logger.spec.ts)", [
                 (NONE, "Các mức log; meta; meta vòng lặp", "map đúng mức; nối meta dạng JSON; không ném lỗi", NONE, N),
             ]),
             ("@agricontract/eventing (eventing.smoke.spec.ts)", [
                 (NONE, "Nạp thư viện thật dưới Jest", "ack kết quả PROCESSED; NonRetryableError vào DLQ ngay không retry", NONE, N),
             ]),
         ]),
    node("NodemailerEmailAdapter", NOTIF, "src/infrastructure/email/nodemailer-email.adapter.spec.ts", "02/11/2026",
         "NodemailerEmailAdapter: gửi email qua SMTP", "Transport giả", [
             ("send()", [
                 (NONE, "Cấu hình", "SMTP thường không auth/STARTTLS như bản Java; timeout theo cấu hình (OTP < 5 s)", NONE, N),
                 (NONE, "Gửi email", "text thường từ MAIL_FROM, mặc định noreply@agricontract.com", NONE, N),
                 (NONE, "Server trả Message-ID; không trả / rỗng / transport không trả gì; id có khoảng trắng", "trả id / null / id đã trim", NONE, B),
                 (NONE, "SMTP từ chối; AggregateError; lỗi không có code", "EmailDeliveryError chỉ có mã, không có địa chỉ; bỏ qua code không hợp lệ", NONE, A),
             ]),
         ]),
    node("NotificationHealth", NOTIF, "src/infrastructure/health/health.spec.ts", "02/11/2026",
         "Health check RabbitMQ và MySQL", NONE, [
             ("GET /actuator/health", [
                 (NONE, "Mọi thứ chạy", "200, UP", NONE, N),
                 (NONE, "RabbitMQ chưa có channel; database ping lỗi", "503, DOWN", NONE, A),
             ]),
         ]),
    node("OtpEmailHttp", NOTIF, "src/infrastructure/http/otp-email.http.spec.ts", "07/11/2026",
         "POST /internal/v1/notifications/otp-email", "App Nest với use case thật, email adapter giả", [
             ("POST /otp-email", [
                 (NONE, "Body hợp lệ, secret đúng", "204, body rỗng sau khi provider nhận", NONE, N),
                 (NONE, "Gọi lại cùng requestId", "204, không gửi email thứ 2", NONE, B),
                 (NONE, "Thiếu secret; sai secret; secret kèm giá trị khác; dùng secret của gateway", "401, không gửi", NONE, A),
                 (NONE, "JSON hỏng; field sai; body quá giới hạn", "400 / 413, chỉ nêu tên field, không trích body", NONE, A),
                 (NONE, "Provider từ chối", "502, log chỉ có requestId và mã", NONE, A),
                 (NONE, "Đang có lần gọi khác cùng requestId", "409 ngay", NONE, B),
                 (NONE, "Route nội bộ không tồn tại", "404 dạng error envelope", NONE, A),
             ]),
             ("OtpEmailModule (otp-email.module.spec.ts)", [
                 (NONE, "Khởi tạo module", "controller và use case dùng transport OTP timeout ngắn", NONE, N),
             ]),
         ]),
    node("AmqpProvider", NOTIF, "src/infrastructure/messaging/amqp.provider.spec.ts", "02/11/2026",
         "AmqpService: kết nối RabbitMQ", "Broker giả", [
             ("Kết nối", [
                 (NONE, "Broker chưa sẵn sàng", "khởi động không chờ; retry 1 s, 2 s… tối đa 30 s", NONE, B),
                 (NONE, "Kết nối / kết nối lại", "đặt prefetch, khai báo topology, chạy callback; callback đăng ký sau cũng chạy ngay", NONE, N),
                 (NONE, "Khai báo topology lỗi", "log lỗi và retry", NONE, A),
                 (NONE, "Đã destroy", "ngừng retry", NONE, B),
             ]),
             ("Tắt", [
                 (NONE, "Module destroy rồi application shutdown", "giữ channel cho consumer xả hết; sau đó đóng channel rồi connection", NONE, N),
             ]),
         ]),
    node("NotifDlqMonitor", NOTIF, "src/infrastructure/messaging/dlq-monitor.spec.ts", "02/11/2026",
         "DlqMonitor (notification): cảnh báo khi DLQ có message", "Channel giả", [
             ("check()", [
                 (NONE, "Chu kỳ", "chạy mỗi 5 phút như bản Java", NONE, N),
                 (NONE, "Mọi DLQ rỗng / có DLQ có message", "im lặng / cảnh báo từng DLQ", NONE, N),
                 (NONE, "Channel chưa sẵn sàng; 1 lần kiểm lỗi", "bỏ qua không crash; log và dừng vòng này", NONE, A),
             ]),
         ]),
    node("LegacyPayloadParsers", NOTIF, "src/infrastructure/messaging/legacy-payload.parsers.spec.ts", "02/11/2026",
         "Parser 9 event Phase 1 (contract.signed/cancelled/delivered/disputed, escrow.locked/released/penalized, category.approved/rejected)",
         "Payload phẳng mẫu cho từng loại event", [
             ("parse<Event>()", [
                 (NONE, "Payload hợp lệ (object hoặc chuỗi), có field thừa", "map đúng lệnh, bỏ field thừa, không trim hay đổi kiểu", NONE, N),
                 (NONE, "Mỗi field bắt buộc: thiếu, null, rỗng, là số, là object", NONE, DLQ + " (nêu tên field)", A),
                 (NONE, "Body: JSON hỏng, mảng, null, số, chuỗi", NONE, DLQ, A),
             ]),
             ("Field tuỳ chọn", [
                 (NONE, "Thiếu lý do; categoryId", "chấp nhận", NONE, B),
                 (NONE, "Lý do sai kiểu", NONE, DLQ + " (như bản Java)", A),
             ]),
             ("escrow.penalized", [
                 (NONE, "penalizedParty BUYER / SELLER / giá trị khác", "chọn email buyer / seller / seller như bản Java; chỉ bắt buộc email bên được chọn", NONE, B),
             ]),
             ("Message lỗi", [
                 (NONE, "Field sai; JSON hỏng có dữ liệu cá nhân; eventId không phải id thường",
                  "chỉ nêu kiểu field và eventId, không bao giờ có giá trị payload", NONE, A),
             ]),
         ]),
    node("NotificationCommandParsers", NOTIF, "src/infrastructure/messaging/notification-command.parsers.spec.ts", "10/11/2026",
         "Parser lệnh notification.* Phase 2 (envelope chuẩn)", "Lệnh mẫu hợp lệ theo schema đã chốt", [
             ("parseContractAnchorRequested()", [
                 (NONE, "Lệnh hợp lệ", "đọc eventId của envelope và mọi field; dedup theo eventId envelope; userId null giữ null", NONE, N),
                 (NONE, "Không phải JSON; không có envelope (dạng Phase 1); field envelope sai; có subject", NONE, DLQ, A),
                 (NONE, "contractId, terms snapshot, hash (sai / sai độ dài), signedAt sai; field payload lạ", NONE, DLQ, A),
                 (NONE, "Không có người nhận; người nhận sai kiểu / thiếu userId / userId không phải UUID / email sai / role ngoài contract / field lạ", NONE, DLQ, A),
                 (NONE, "Mọi lỗi", "message nêu lệnh, field, event, chỉ số người nhận; không trích địa chỉ; bỏ eventId lạ", NONE, A),
             ]),
             ("parseActivationCancelRequested()", [
                 (NONE, "Yêu cầu của buyer / seller", "đọc đủ field", NONE, N),
                 (NONE, "requestedByRole sai / null / thiếu; oneSidedCancelAllowedFrom, contractId, trigger sai", NONE, DLQ + " (nêu field, không nêu giá trị)", A),
                 (NONE, "Lệnh neo hợp đồng gửi nhầm vào queue này", NONE, DLQ, A),
             ]),
         ]),
    node("NotificationEventConsumer", NOTIF, "src/infrastructure/messaging/notification-event.consumer.spec.ts", "02/11/2026",
         "NotificationEventConsumer: nhận message từ các queue và gọi use case", "AmqpService và use case giả", [
             ("Đăng ký", [
                 (NONE, "Khởi động; kết nối lại", "đăng ký qua onChannelReady; consume mọi queue trên mỗi channel", NONE, N),
             ]),
             ("Xử lý message", [
                 (NONE, "9 queue Phase 1, queue neo hợp đồng, queue huỷ kích hoạt", "gọi đúng use case với lệnh đã parse rồi ack", NONE, N),
                 (NONE, "Lệnh sai eventType so với queue; payload hỏng", "không gọi use case", DLQ, A),
                 (NONE, "Lỗi tạm thời", "retry 3 lần (1 s, 2 s) rồi nack không requeue; thành công khi retry thì ack", NONE, A),
                 (NONE, "NonRetryableError", "nack ngay không retry", NONE, A),
             ]),
             ("Tắt service", [
                 (NONE, "Đang có message", "huỷ consumer rồi chờ message đang chạy; xả tối đa 40 s (giữa timeout SMTP 30 s và stop_grace 60 s)", NONE, B),
                 (NONE, "Message xong sớm / quá timeout", "trả ngay khi ack xong / dừng chờ, để message chưa ack", NONE, B),
                 (NONE, "Channel mở trong lúc tắt", "không gắn consumer", NONE, B),
                 (NONE, "Lần xử lý đang chạy lúc bắt đầu tắt", "chạy xong và ack; lỗi hẳn thì nack vào DLQ", NONE, N),
                 (NONE, "Retry, backoff, message mới tới, message chờ claim trong lúc tắt", "đỗ lại: không chạy, không ack, không nack", NONE, B),
             ]),
         ]),
    node("NotifShutdownAndPii", NOTIF, "src/infrastructure/messaging/shutdown-order.spec.ts", "02/11/2026",
         "Thứ tự tắt service và không log dữ liệu cá nhân", "Vòng đời Nest thật, broker và DB giả", [
             ("Thứ tự tắt", [
                 (NONE, "Có message đang xử lý", "ack message, đóng channel, rồi đóng pool DB", NONE, N),
                 (NONE, "Quá timeout xả; retry tới hạn lúc đang tắt", "đóng channel không ack/nack rồi mới đóng pool; retry bị đỗ lại", NONE, B),
             ]),
             ("Không log dữ liệu cá nhân (pii-logging.spec.ts)", [
                 (NONE, "SMTP từ chối có trích người nhận; JSON hỏng có dữ liệu cá nhân; lệnh bị từ chối; lỗi lạ có dữ liệu cá nhân",
                  "log retry và DLQ chỉ có eventId và mã lỗi, không có địa chỉ hay payload", NONE, A),
             ]),
         ]),
    node("NotificationTopology", NOTIF, "src/infrastructure/messaging/topology.spec.ts", "02/11/2026",
         "Topology RabbitMQ của notification (khớp bản Java)", NONE, [
             ("Khai báo", [
                 (NONE, "Tên queue và DLQ", "đúng số lượng và tên", NONE, N),
                 (NONE, "Exchange; queue; binding", "exchange durable không auto-delete; queue chính chỉ có x-dead-letter-exchange; DLQ bind DLX cùng routing key", NONE, N),
             ]),
         ]),
    node("NotificationPersistence", NOTIF, "src/infrastructure/persistence/notification-log.mysql-repository.spec.ts", "02/11/2026",
         "MySQL repository của notification_log và MysqlService", "Pool MySQL giả ghi lại câu SQL", [
             ("claim()", [
                 (NONE, "Bản ghi cũ trước V2 đã SENT", "ALREADY_SENT, không ghi gì", NONE, B),
                 (NONE, "Lần giao đầu", "insert PENDING kèm loại, thời gian UTC của DB; recipient_user_id NULL nếu chỉ có địa chỉ", NONE, N),
                 (NONE, "Bản ghi đã có", "chỉ lấy lại nếu FAILED hoặc PENDING quá lease; ghi đè nội dung lần trước; không lấy được thì ALREADY_SENT / IN_PROGRESS", NONE, B),
                 (NONE, "Lỗi insert khác trùng khoá", NONE, "Ném lại lỗi", A),
             ]),
             ("heartbeat / markSent / markFailed", [
                 (NONE, "Heartbeat", "chỉ đổi updated_at theo giờ DB", NONE, N),
                 (NONE, "markSent có / không có id; id quá dài", "ghi SENT, id, sent_at; không id; bỏ id quá dài thay vì lỗi", NONE, B),
                 (NONE, "markFailed", "FAILED, tăng retry_count trong SQL, lưu mã lỗi", NONE, N),
             ]),
             ("MysqlService (mysql.provider.spec.ts)", [
                 (NONE, "Khởi động; DB không tới được; tắt", "ping SELECT 1; lỗi nêu đích không nêu mật khẩu; đóng pool", NONE, N),
             ]),
             ("NotificationModule (notification.module.spec.ts)", [
                 (NONE, "Khởi tạo module", "dựng các class theo token và route mọi queue", NONE, N),
             ]),
         ]),
    node("NotificationContracts", NOTIF, "test/contract/notification-commands.contract.spec.ts", "10/11/2026",
         "Đối chiếu code với contract đã chốt (2 lệnh notification và OtpEmailRequest)", "Schema trong contracts/", [
             ("contract_anchor_requested", [
                 (NONE, "Đọc schema", "là lệnh service này tiêu thụ; eventType, producer, field và role khớp code", NONE, N),
                 (NONE, "Message hợp lệ; người nhận không id; 2 người nhận", "schema và parser cùng chấp nhận", NONE, N),
                 (NONE, "Field lạ; không người nhận; hash viết hoa; contractId sai; role sai; producer / eventType khác; thiếu correlationId; có subject",
                  "schema và parser cùng từ chối", NONE, A),
             ]),
             ("activation_cancel_requested (activation-cancel-requested.contract.spec.ts)", [
                 (NONE, "Đọc schema", "khớp eventType, producer, field, role bên yêu cầu", NONE, N),
                 (NONE, "Message hợp lệ; seller yêu cầu; OPERATOR không id; thời điểm có offset", "cả 2 bên cùng chấp nhận", NONE, N),
                 (NONE, "Field lạ; không người nhận; role sai / chữ thường / thiếu; ngày không giờ; contractId sai; producer khác; nhầm eventType; thiếu correlationId",
                  "cả 2 bên cùng từ chối", NONE, A),
             ]),
             ("OtpEmailRequest (otp-email-request.contract.spec.ts)", [
                 (NONE, "Đọc OpenAPI", "đúng operation, path, xác thực service; field khớp, purpose mặc định SIGN_CONTRACT", NONE, N),
                 (NONE, "20 mẫu body hợp lệ và không hợp lệ", "code và schema cho cùng kết quả", NONE, B),
                 (NONE, "requestId dài hơn cột event_id; contractId / recipientUserId rỗng", "code chặt hơn schema", NONE, B),
             ]),
         ]),
    # ── pricing-service (Le Phu Minh) ────────────────────────────────────────
    node("PricingArchitecture", PRICING, "test/architecture.spec.ts", "27/10/2026",
         "Kiến trúc pricing-service: layout và chiều phụ thuộc", "Quét mã nguồn src/", [
             ("Layout", [
                 (NONE, "Thư mục src/", "chỉ domain/, application/, infrastructure/, main.ts; use case, port, adapter đúng chỗ", NONE, N),
             ]),
             ("Chiều phụ thuộc", [
                 (NONE, "Từng file trong 22 file nguồn", "chỉ import tầng được phép", NONE, N),
                 (NONE, "domain, application", "không dùng framework hay driver; HTTP status chỉ ở tầng web", NONE, N),
             ]),
             ("NestJS", [
                 (NONE, "Vị trí Nest; guard; main.ts; tắt service", "Nest chỉ ở infrastructure và main.ts; phân quyền là guard chạy trước khi đọc body; main.ts chỉ bootstrap; đóng MySQL và Redis khi tắt", NONE, N),
             ]),
         ]),
    node("PricingContract", PRICING, "test/contract.spec.ts", "27/10/2026",
         "Đối chiếu pricing-service với contract đã chốt", "OpenAPI trong contracts/", [
             ("supplementary-services-api.yaml", [
                 (NONE, "Path nhập giá tay", "có trong contract, thuộc pricing; bắt Idempotency-Key; chỉ OPERATOR (không ADMIN); đúng mã response; chỉ COFFEE", NONE, N),
             ]),
             ("ErrorEnvelope", [
                 (NONE, "Response lỗi validate, role, gateway, trùng key", "khớp schema ErrorEnvelope chung", NONE, A),
             ]),
             ("Không nằm trên đường đồng bộ", [
                 (NONE, "Code contract-service, escrow-service, pricing-service", "không có client, URL hay route giữa chúng; pricing không gọi HTTP ra ngoài", NONE, N),
             ]),
         ]),
    node("PricingHttp", PRICING, "test/http.spec.ts", "27/10/2026",
         "POST /api/v1/prices/manual: Operator nhập giá tay", "App Nest; MySQL và Redis giả", [
             ("Phân quyền", [
                 (NONE, "OPERATOR; nhiều role có OPERATOR, không phân biệt hoa thường", "201", NONE, N),
                 (NONE, "ADMIN, SELLER, BUYER, INSPECTOR, SELLER+BUYER", "403, không lưu", NONE, A),
                 (NONE, "Không qua gateway; secret giả; thiếu danh tính", "401, không lưu", NONE, A),
                 (NONE, "Body sai và không có quyền", "kiểm quyền trước khi đọc body", NONE, B),
             ]),
             ("Lưu giá", [
                 (NONE, "Body hợp lệ", "lưu nguồn OPERATOR_MANUAL, ngày áp dụng, thời điểm nhập, nguồn tham chiếu, lý do; trim chữ", NONE, N),
                 (NONE, "Sửa giá", "thêm dòng mới, dòng cũ giữ nguyên (append-only)", NONE, N),
                 (NONE, "Cache", "chỉ cập nhật sau khi ghi DB; Redis lỗi không làm request lỗi", NONE, A),
             ]),
             ("Validate", [
                 (NONE, "Không phải cà phê; thiếu tỉnh / lý do / nguồn; field lạ; itemName không có chữ", "400, không lưu", NONE, A),
                 (NONE, "Giá dạng số, không có 2 số lẻ, 0, âm, vượt DECIMAL, số 0 đầu, 1 số lẻ", "400, không lưu", NONE, B),
                 (NONE, "Ngày không tồn tại; dạng date-time; ngày tương lai", "400, không lưu", NONE, B),
                 (NONE, "Giá nhỏ viết chuẩn; hôm nay theo giờ Việt Nam", "201", NONE, B),
                 (NONE, "JSON hỏng; content type không phải JSON", "400 / 415", NONE, A),
             ]),
             ("Idempotency-Key", [
                 (NONE, "Thiếu key", "400", NONE, A),
                 (NONE, "Gửi lại cùng key cùng body; 2 request đồng thời cùng key", "trả lại 201 cũ, chỉ 1 dòng", NONE, B),
                 (NONE, "Cùng key khác body", "409", NONE, A),
                 (NONE, "Cùng key, actor khác", "tính riêng theo actor", NONE, B),
             ]),
             ("Routing", [
                 (NONE, "GET path nhập giá; path lạ; health; X-Request-Id; lỗi repository",
                  "405; 404 error envelope; health trả lời; traceId = X-Request-Id; 500 không lộ chi tiết", NONE, N),
             ]),
         ]),
    node("PricingRedis", PRICING, "test/redis.spec.ts", "27/10/2026",
         "Cache Redis không làm treo pricing-service", "Redis giả: từ chối kết nối / không bắt tay / ngừng trả lời", [
             ("Cache", [
                 (NONE, "Redis từ chối kết nối; không bắt tay; ngừng trả lời", "factory trả ngay; cập nhật lỗi ngay hoặc sau commandTimeoutMs", NONE, A),
                 (NONE, "POST nhập giá khi Redis sập / treo", "201 vẫn lưu dòng, log cảnh báo", NONE, A),
                 (NONE, "Khởi động khi Redis sập", "/health vẫn trả lời", NONE, A),
             ]),
         ]),
    node("PricingReplay", PRICING, "test/replay.spec.ts", "27/10/2026",
         "Gửi lại cùng Idempotency-Key", "MySQL và Redis giả (5 test cần MySQL thật bị skip)", [
             ("Replay", [
                 (NONE, "Giá có số 0 cuối", "body lần gửi lại giống hệt lần đầu", NONE, B),
                 (NONE, "Giá mà MySQL trả về dạng khác", "bị từ chối ngay từ đầu", NONE, B),
                 ("Redis giữ giá A; B lưu nhưng ghi cache lỗi", "Gửi lại B", "Redis có B; không ghi đè giá mới hơn", NONE, B),
             ]),
         ]),
    node("PricingUnits", PRICING, "test/units.spec.ts", "27/10/2026",
         "Hàm và adapter của pricing-service", NONE, [
             ("Hàm thuần", [
                 (NONE, "slugify / cache key; validateManualPrice; businessToday",
                  "đúng ví dụ design; chấp nhận body đúng, từ chối body không phải object, giới hạn độ dài, hash bỏ khoảng trắng ngoài; qua ngày theo giờ ICT", NONE, N),
             ]),
             ("RedisPriceCache", [
                 (NONE, "Ghi giá; sửa giá lùi ngày; sửa giá cùng ngày", "key price:coffee:{tỉnh}:{loại} không TTL; giá ngày sau không bị thay; cùng ngày thì thay", NONE, B),
             ]),
             ("MysqlPriceHistoryRepository", [
                 (NONE, "Insert; trùng key; đọc lại; mã nguồn", "ghi đủ cột nguồn, UTC; trùng key → DuplicateIdempotencyKeyError; đọc lại đúng; không có UPDATE/DELETE", NONE, N),
             ]),
             ("Migration và config", [
                 (NONE, "Migration; config", "chỉ tạo bảng mới, chỉ COFFEE, chạy theo thứ tự; bắt buộc gateway secret, timeout Redis là số nguyên dương", NONE, N),
             ]),
         ]),
    node("RecordManualPrice", PRICING, "test/usecase.spec.ts", "27/10/2026",
         "recordManualPrice và ánh xạ lỗi sang HTTP", NONE, [
             ("recordManualPrice()", [
                 (NONE, "Thiếu key; body sai hoặc key quá dài; cùng key khác body", NONE, "IdempotencyKeyRequired / ValidationFailed / IdempotencyConflict", A),
             ]),
             ("Tầng web", [
                 (NONE, "3 lỗi trên; lỗi khác", "400 / 400 / 409 đúng mã; lỗi khác thành 500", NONE, N),
             ]),
         ]),
    # ── analytics-service (Le Phu Minh) ──────────────────────────────────────
    node("AnalyticsArchitecture", ANALYTICS, "test/architecture.spec.ts", "23/10/2026",
         "Kiến trúc analytics-service: layout và chiều phụ thuộc", "Quét mã nguồn src/", [
             ("Layout và phụ thuộc", [
                 (NONE, "Thư mục src/; 31 file nguồn", "đúng layout; mỗi file chỉ import tầng và package được phép", NONE, N),
             ]),
         ]),
    node("ContractOutcomes", ANALYTICS, "test/contract-outcomes.spec.ts", "27/10/2026",
         "Fact kết thúc hợp đồng: ai vi phạm, ai yêu cầu", "Ingest event qua consumer thật, DB in-memory", [
             ("terminationOutcome()", [
                 (NONE, "TERMINATION_FOR_BREACH có final role", "là vi phạm, quy cho role đó", NONE, N),
                 (NONE, "Kết thúc thoả thuận có role; bất khả kháng; kích hoạt thất bại; rút offer", "không phải vi phạm / ngoài phạm vi", NONE, B),
                 (NONE, "Vi phạm không có final role; có requestedBy", "không quy cho ai; không đọc requestedBy", NONE, B),
             ]),
             ("Fact", [
                 (NONE, "Seller yêu cầu, buyer vi phạm (và ngược lại)", "requestedBy và finalBreachingRole tách riêng", NONE, N),
                 (NONE, "Mọi nhánh kết thúc; kết thúc thoả thuận", "đều lưu; chỉ 2 nhánh vi phạm có quy kết tính là vi phạm", NONE, N),
                 (NONE, "Gửi lại cùng eventId / eventId mới; contract.settled lặp", "chỉ 1 fact", NONE, B),
                 (NONE, "Event phát khi analytics tắt", "bật lại thì lưu đủ, mỗi cái 1 lần", NONE, B),
                 (NONE, "terminationType lạ", "vào DLQ, không ghi fact", NONE, A),
             ]),
         ]),
    node("AnalyticsDlqMonitor", ANALYTICS, "test/dlq-monitor.spec.ts", "23/10/2026",
         "DLQ monitor của analytics", "Channel giả", [
             ("check()", [
                 (NONE, "Mọi event đã đăng ký", "kiểm DLQ của từng event; có message thì cảnh báo queue và số lượng; rỗng thì im", NONE, N),
                 (NONE, "1 lần kiểm lỗi", "log và vẫn kiểm các queue còn lại", NONE, A),
                 (NONE, "start / stop", "kiểm ngay rồi lặp; stop thì dừng; mỗi lúc 1 vòng", NONE, N),
             ]),
         ]),
    node("AnalyticsEventContracts", ANALYTICS, "test/event-contracts.spec.ts", "23/10/2026",
         "Validator của analytics khớp 7 event schema đã chốt", "golden-flow-events.yaml", [
             ("So với schema", [
                 (NONE, "7 event (contract.signed/settled/terminated, 4 event milestone)", "cùng producer, field bắt buộc, không thừa thiếu, cùng enum; mọi event có bộ luật", NONE, N),
                 (NONE, "Fixture hợp lệ; bỏ / thêm / thay từng field", "validator và schema YAML cho cùng kết quả", NONE, B),
                 (NONE, "Người nhận", "chỉ userId, email, role BUYER/SELLER", NONE, N),
             ]),
             ("Validator production", [
                 (NONE, "Ngày không tồn tại; field chỉ có trên prototype; producer lạ payload rỗng; version không hỗ trợ; sai queue; payload không phải object",
                  "từ chối, không ném lỗi; message không có giá trị payload", NONE, A),
             ]),
         ]),
    node("AnalyticsHealth", ANALYTICS, "test/health.spec.ts", "23/10/2026",
         "GET /health của analytics", NONE, [
             ("GET /health", [
                 (NONE, "Đang chờ MySQL/RabbitMQ; đã bắt đầu ingest; path khác", "503 STARTING; 200 UP; 404", NONE, N),
             ]),
         ]),
    node("AnalyticsIngestion", ANALYTICS, "test/ingestion.spec.ts", "23/10/2026",
         "Consumer analytics: validate, lưu, dedup, ack", "Broker và DB in-memory", [
             ("Consumer", [
                 (NONE, "Mỗi queue đã đăng ký", "1 consumer; event được validate, lưu, đánh dấu đã xử lý, ack", NONE, N),
                 (NONE, "Event gửi lại cùng eventId; nhiều event cùng hợp đồng", "lưu đúng 1 lần; lưu đủ", NONE, B),
                 (NONE, "Ghi lỗi; projection lỗi", "không còn dòng nào, retry lưu được (1 transaction)", NONE, A),
                 (NONE, "Hết retry", "vào DLQ; replay eventId gốc được ingest", NONE, A),
             ]),
             ("Biên ingest", [
                 (NONE, "Producer lạ payload rỗng; số tiền sai; field lạ; version không hỗ trợ; envelope sai; sai queue", "vào DLQ, không đoán", NONE, A),
                 (NONE, "Event hợp lệ sau 1 event bị từ chối", "vẫn được ingest", NONE, N),
                 (NONE, "milestone.settled có email người nhận", "email không bao giờ vào DB", NONE, N),
             ]),
             ("Không chặn producer", [
                 (NONE, "Analytics sập; projection chậm hoặc lỗi", "producer vẫn publish được, message chờ trong queue", NONE, A),
                 (NONE, "Bật lại với backlog; prefetch lớn", "lưu đủ theo thứ tự, mỗi event 1 lần", NONE, B),
             ]),
             ("Ràng buộc", [
                 (NONE, "Projection cho event không có queue; consumer khởi động", "từ chối; log chỉ có loại event và queue", NONE, A),
             ]),
         ]),
    node("AnalyticsInvalidEvents", ANALYTICS, "test/invalid-events.spec.ts", "23/10/2026",
         "Event không hợp lệ vào DLQ mà không để lại dấu", NONE, [
             ("Ingest", [
                 (NONE, "signedAt ngày không tồn tại; giờ 24; field lạ tên constructor / toString",
                  "vào DLQ, không có dòng nào, không đánh dấu đã xử lý, không retry", NONE, A),
                 (NONE, "Event đã sửa, cùng eventId", "được ingest", NONE, N),
             ]),
         ]),
    node("AnalyticsLifecycle", ANALYTICS, "test/lifecycle.spec.ts", "23/10/2026",
         "Tắt analytics an toàn", NONE, [
             ("gracefulShutdown()", [
                 (NONE, "Tắt bình thường", "thứ tự: not ready, ngừng nhận, xả, đóng MySQL, đóng RabbitMQ", NONE, N),
                 (NONE, "1 bước lỗi; 1 bước treo", "log và chạy tiếp các bước sau; bỏ bước treo sau stepTimeoutMs", NONE, A),
                 (NONE, "Có handler đang chạy", "chờ xong, ack rồi mới đóng DB; handler không xong thì không chặn", NONE, B),
                 (NONE, "Message còn trong queue; message tới sau khi tắt", "instance sau xử lý; trả lại broker (requeue)", NONE, B),
             ]),
         ]),
    node("AnalyticsMilestoneLock", ANALYTICS, "test/milestone-lock.spec.ts", "23/10/2026",
         "Thứ tự khoá theo milestone khi chiếu dữ liệu", NONE, [
             ("Projection", [
                 (NONE, "settled / cancelled; force majeure; quyết định bị từ chối",
                  "khoá trước khi đọc cờ; khoá trước khi tìm fact; không đụng gì kể cả khoá", NONE, N),
             ]),
         ]),
    node("AnalyticsPersistence", ANALYTICS, "test/persistence.spec.ts", "23/10/2026",
         "Ánh xạ event thành lệnh ingest và lưu MySQL", NONE, [
             ("toIngestCommand()", [
                 (NONE, "Envelope có người nhận", "giữ định danh, giờ UTC, tracing id; bỏ email, giữ userId và role; không sửa envelope gốc", NONE, N),
                 (NONE, "Payload không có người nhận", "giữ nguyên", NONE, B),
             ]),
             ("MysqlIngestedEventStore", [
                 (NONE, "Insert", "1 dòng đủ cột trong transaction được truyền vào; giờ UTC DATETIME(6)", NONE, N),
             ]),
             ("ProjectionsModule (nest-projections.spec.ts)", [
                 (NONE, "Khởi tạo module", "cung cấp đủ các projection", NONE, N),
             ]),
         ]),
    node("AnalyticsProjections", ANALYTICS, "test/projections.spec.ts", "23/10/2026",
         "Projection fact/dimension của analytics", "DB in-memory", [
             ("Tiền", [
                 (NONE, "Trừ số âm, số dưới 1 đơn vị; giá trị không có 2 số lẻ", "trừ chính xác; từ chối", NONE, B),
             ]),
             ("Projection", [
                 (NONE, "contract.signed lặp", "upsert dim_contract, không trùng", NONE, N),
                 (NONE, "milestone.settled; cancelled_with_penalty", "1 fact với chênh lệch = khoá − thực tế; fact CANCELLED_WITH_PENALTY", NONE, N),
                 (NONE, "Bất khả kháng duyệt trước / sau khi có fact; bị từ chối; khác milestone", "gộp vào fact mới / cập nhật dòng; không đổi; chỉ milestone của nó", NONE, B),
                 (NONE, "Event phát khi analytics tắt; thứ tự tới khác nhau", "bắt kịp đủ; cùng trạng thái cuối", NONE, B),
                 (NONE, "Projection lỗi; event thiếu số tiền", "rollback toàn bộ; vào DLQ không ghi fact", NONE, A),
             ]),
         ]),
    node("AnalyticsRuntime", ANALYTICS, "test/runtime.spec.ts", "23/10/2026",
         "Wiring production: tắt, bật lại, replay", NONE, [
             ("Runtime", [
                 (NONE, "Event phát khi analytics tắt rồi bật lại", "ingest đủ, mỗi event 1 lần", NONE, B),
                 (NONE, "Replay cùng eventId; replay DLQ của event đã sửa", "không làm gì; ingest được", NONE, B),
                 (NONE, "Khởi động; event bị từ chối", "tự khai báo topology, chạy DLQ monitor; cảnh báo DLQ chỉ có queue và số lượng", NONE, N),
             ]),
         ]),
    node("AnalyticsTopology", ANALYTICS, "test/topology-contract.spec.ts", "23/10/2026",
         "Topology, contract, migration và wiring của analytics", NONE, [
             ("RabbitMQ", [
                 (NONE, "Khai báo exchange, queue, DLQ", "giống service Java; queue không giới hạn độ dài/TTL; khai báo 2 lần không đổi gì; tên theo quy ước", NONE, N),
             ]),
             ("Contract", [
                 (NONE, "Event đã đăng ký", "đều có trong golden-flow-events.yaml; phủ đủ dòng của bản đồ tích hợp; đủ 6 projection", NONE, N),
             ]),
             ("Không nằm trên đường giao dịch (Matrix 23)", [
                 (NONE, "Code escrow-service; analytics-service", "không có client/URL tới analytics; analytics không gọi HTTP ra ngoài, không publish ngược", NONE, N),
                 (NONE, "Code contract-service", "không có client/URL tới analytics", NONE, N),
             ]),
             ("Migration, config, NestJS", [
                 (NONE, "V1–V4; config mặc định; wiring Nest", "chỉ thêm bảng, tiền DECIMAL(18,2); chạy theo thứ tự; /health lên trước MySQL và RabbitMQ; tắt êm khi SIGTERM/SIGINT", NONE, N),
             ]),
         ]),
]

# Jest files folded into another sheet above (they are counted in that sheet's note)
FOLDED = {
    NOTIF: {"src/application/phase1-notification-kinds.spec.ts": "NotificationKinds",
            "src/infrastructure/logging/eventing-logger.spec.ts": "NotifInfraHelpers",
            "src/infrastructure/eventing.smoke.spec.ts": "NotifInfraHelpers",
            "src/otp-email.module.spec.ts": "OtpEmailHttp",
            "src/infrastructure/messaging/pii-logging.spec.ts": "NotifShutdownAndPii",
            "src/infrastructure/persistence/mysql.provider.spec.ts": "NotificationPersistence",
            "src/notification.module.spec.ts": "NotificationPersistence",
            "test/contract/activation-cancel-requested.contract.spec.ts": "NotificationContracts",
            "test/contract/otp-email-request.contract.spec.ts": "NotificationContracts"},
    ANALYTICS: {"test/nest-projections.spec.ts": "AnalyticsPersistence"},
}
