const fs = require("fs");
const path = require("path");

const {
  D, P, H1, H2, H3, bullet, numbered, callout, risk,
  table, spacer, cover, endMark, buildDoc,
} = require("./acdocx.js");

const { Packer } = D;
const OUTPUT_DIR = path.resolve(__dirname, "../..");
const VERSION = "v1.3";
const BASELINE = "Baseline code: main · cập nhật 17/07/2026";

function staticToc(entries) {
  return [
    new D.Paragraph({
      spacing: { after: 180 },
      children: [new D.TextRun({ text: "Mục lục", font: "Calibri", size: 30, bold: true, color: "1F2A37" })],
    }),
    table(
      [900, 8738],
      ["Mục", "Nội dung"],
      entries.map((entry, index) => [String(index + 1), entry]),
      { boldCol: [true, false] },
    ),
    new D.Paragraph({ spacing: { before: 240 }, children: [new D.PageBreak()] }),
  ];
}

function baseChildren(kicker, title, subtitle, tocEntries) {
  return [
    ...cover(kicker, title, subtitle, ["Phase 1 MVP · " + VERSION, BASELINE]),
    ...staticToc(tocEntries),
    callout(
      "TRẠNG THÁI TÀI LIỆU —",
      "Tài liệu này mô tả implementation đang có trên main. Nội dung tại đây thay thế các giả định Phase 1 cũ nếu có mâu thuẫn về API, state machine, database, event hoặc cách chạy hệ thống.",
      "note",
    ),
  ];
}

function solutionDocument() {
  const body = baseChildren(
    "AGRICONTRACT · PHASE 1",
    "Giải Pháp & Mô Hình Vận Hành",
    "Marketplace nông sản B2B · Hợp đồng điện tử · Escrow giả lập",
    [
      "AgriContract là gì",
      "Vấn đề và giá trị giải pháp",
      "Người dùng và quyền",
      "Deliverable Phase 1 hiện tại",
      "Biên giới Phase 1 và Phase 2",
    ],
  );

  body.push(H1("1. AgriContract là gì"));
  body.push(P("AgriContract là nền tảng số hóa giao dịch hợp đồng nông sản B2B giữa hợp tác xã hoặc seller và doanh nghiệp thu mua. Phase 1 triển khai một marketplace công khai, quy trình đàm phán có lịch sử revision, ký bởi cả hai bên và cơ chế escrow giả lập để thực thi incentive trước khi giao hàng."));
  body.push(P("Nền tảng không thay thế hợp đồng giấy, cơ quan tài phán hoặc ngân hàng. Nó cung cấp bằng chứng dữ liệu và tự động hóa các quy tắc đã được hai bên chấp nhận: lock payment, seller deposit, penalty khi hủy, giữ tiền khi tranh chấp và phân chia theo quyết định Admin."));

  body.push(H2("1.1 Luồng giao dịch đã triển khai"));
  body.push(table(
    [700, 3400, 2200, 2200],
    ["Bước", "Hành động", "Contract", "Escrow"],
    [
      ["1", "Seller tạo product và listing", "—", "—"],
      ["2", "Buyer gửi offer từ listing ACTIVE", "OFFERED", "—"],
      ["3", "Hai bên counter-offer; lưu từng terms revision", "NEGOTIATING", "—"],
      ["4", "Buyer và Seller ký cùng revision", "SIGNED", "BUYER_LOCKED"],
      ["5", "Seller xác nhận cọc", "ACTIVE", "FULLY_LOCKED"],
      ["6", "Buyer xác nhận đã nhận hàng", "DELIVERED", "DELIVERY_PENDING"],
      ["7A", "Hết dispute window mà không có tranh chấp", "SETTLED", "RELEASED"],
      ["7B", "Buyer dispute trong cửa sổ; Admin phân chia", "DISPUTED → SETTLED", "DISPUTED → ARBITRATED"],
    ],
    { boldCol: [true, false, false, false] },
  ));
  body.push(spacer());
  body.push(callout("ĐIỂM THAY ĐỔI QUAN TRỌNG —", "Buyer xác nhận giao hàng không còn làm escrow release ngay. Escrow chuyển sang DELIVERY_PENDING và chờ mặc định 30 giây để buyer có thể mở dispute. Scheduler chỉ release khi cửa sổ kết thúc mà escrow vẫn chưa bị hold.", "note"));

  body.push(H2("1.2 Cancel và dispute"));
  body.push(table(
    [2200, 2300, 4238],
    ["Tình huống", "Kết quả", "Xử lý tiền giả lập"],
    [
      ["Buyer cancel khi ACTIVE", "PENALIZED_BUYER", "Penalty = totalAmount × buyerPenaltyRate chuyển cho Seller; phần còn lại hoàn Buyer; cọc hoàn Seller."],
      ["Seller cancel khi ACTIVE", "PENALIZED_SELLER", "Hoàn toàn bộ buyer payment; seller deposit trở thành penalty cho Buyer."],
      ["Buyer dispute khi DELIVERED", "DISPUTED", "Hủy lịch release và giữ cả payment lẫn seller deposit."],
      ["Admin arbitrate", "ARBITRATED", "buyerAmount + sellerAmount phải bằng totalAmount + sellerDeposit; justification là bắt buộc."],
    ],
  ));

  body.push(H1("2. Vấn đề và giá trị giải pháp"));
  body.push(table(
    [2500, 3000, 4138],
    ["Vấn đề", "Rủi ro giao dịch", "Cơ chế Phase 1"],
    [
      ["Điều khoản thay đổi bằng lời nói", "Không chứng minh được bản nào đã được hai bên chấp nhận", "termsRevision tăng theo counter-offer; chữ ký cũ bị reset; history lưu append theo revision."],
      ["Một bên bẻ kèo sau khi ký", "Thiệt hại khó thu hồi", "Buyer payment và seller deposit được lock trước khi Contract ACTIVE; cancellation tự áp penalty."],
      ["Chất lượng hàng có tranh chấp", "Release tiền quá sớm làm mất khả năng phân xử", "Dispute window, optimistic locking, escrow hold và Admin arbitration."],
      ["Async event bị gửi lại", "Side effect trùng", "Business idempotency theo contractId/status; notification unique theo eventId và recipient."],
    ],
  ));

  body.push(H1("3. Người dùng và quyền"));
  body.push(table(
    [1600, 3900, 4138],
    ["Role", "Tác vụ chính", "Giới hạn"],
    [
      ["SELLER", "Tạo product/listing, counter-offer, ký, xác nhận cọc, cancel", "Không tạo offer cho listing; không confirm delivery; không arbitrate."],
      ["BUYER", "Browse marketplace, tạo offer, negotiate, ký, confirm delivery, cancel, dispute", "Chỉ thao tác contract của chính mình; không confirm seller deposit."],
      ["ADMIN", "Đọc contract/history và arbitrate escrow DISPUTED", "Split phải đúng tổng held amount và có justification."],
    ],
  ));

  body.push(H1("4. Deliverable Phase 1 hiện tại"));
  [
    "React SPA với public marketplace, Keycloak login, profile onboarding và role-aware routes.",
    "Sáu Spring Boot service, API Gateway, năm MySQL schema và Flyway migrations.",
    "Transactional outbox ở product-service, contract-service và escrow-service.",
    "Contract negotiation history, two-party signing, cancellation penalty, delayed release và arbitration.",
    "Email qua MailHog cho signing, delivery, cancellation, dispute, release, penalty, arbitration và category review.",
    "Vitest component tests, Playwright real-stack flows và Bruno API/E2E collections.",
  ].forEach((item) => body.push(bullet(item)));

  body.push(H1("5. Biên giới Phase 1 và Phase 2"));
  body.push(risk("GIỚI HẠN PHASE 1 —", "Escrow chỉ là mock database ledger, không giữ tiền thật. Frontend chạy riêng bằng Vite. Category Admin API chưa được route qua Gateway mặc định. Marketplace filter chủ yếu chạy phía client. Outbox dùng scheduled poller, chưa dùng CDC."));
  body.push(P("Các nội dung bank custody, milestone escrow, inspection, reputation, file storage, pricing, audit và analytics thuộc thiết kế Phase 2. Chúng không được mô tả như capability đã chạy của Phase 1."));
  body.push(endMark());

  return buildDoc(body, {
    title: "AgriContract — Giải Pháp & Mô Hình Vận Hành Phase 1",
    headerText: "AgriContract · Phase 1 · Giải pháp",
    footerText: "AgriContract " + VERSION,
  });
}

function architectureDocument() {
  const body = baseChildren(
    "AGRICONTRACT · PHASE 1",
    "Kiến Trúc Kỹ Thuật Hệ Thống",
    "Service boundaries · Security · Event choreography · Data ownership",
    [
      "Tổng quan kiến trúc",
      "Security và trust boundary",
      "Data ownership và schema",
      "State machines",
      "Messaging architecture",
      "Consistency và concurrency",
      "Local deployment",
    ],
  );

  body.push(H1("1. Tổng quan kiến trúc"));
  body.push(P("Phase 1 là monorepo gồm React SPA và sáu Spring Boot service. Browser chỉ gọi API Gateway; các service sở hữu database riêng; contract, escrow và product phát domain event thông qua transactional outbox; notification-service consume event và gửi email qua MailHog."));
  body.push(table(
    [2100, 1450, 3100, 2988],
    ["Thành phần", "Port", "Business capability", "Data / integration"],
    [
      ["frontend", "5173", "Marketplace và contract workflow UI", "Keycloak JS; Axios; React Query"],
      ["api-gateway", "8888 / host 8080", "Auth policy, routing, trusted headers", "Keycloak JWKS"],
      ["user-service", "8081", "User profile và organization", "user_db; Keycloak sub"],
      ["product-service", "8082", "Category, product, image, listing", "product_db; product outbox"],
      ["contract-service", "8083", "Contract state machine và revisions", "contract_db; Feign; contract outbox"],
      ["escrow-service", "8084", "Mock escrow và arbitration", "escrow_db; escrow outbox"],
      ["notification-service", "8085", "Idempotent email", "notification_db; RabbitMQ; MailHog"],
    ],
  ));

  body.push(H2("1.1 Luồng đồng bộ và bất đồng bộ"));
  body.push(table(
    [2200, 3200, 4238],
    ["Kiểu", "Đường đi", "Mục đích"],
    [
      ["Browser REST", "Frontend → Gateway → downstream", "Mọi business command/query; public GET chỉ cho products/listings."],
      ["Internal REST", "contract-service → product/user", "Snapshot listing/user khi tạo offer; đóng listing khi ký."],
      ["Async events", "Outbox → RabbitMQ → consumers", "Activate/settle contract, xử lý escrow, gửi notification."],
      ["SMTP", "notification-service → MailHog", "Development email catcher; không gửi email thật."],
    ],
  ));

  body.push(H1("2. Security và trust boundary"));
  body.push(numbered("Gateway cho phép public GET collection/item của products và listings."));
  body.push(numbered("Route khác yêu cầu bearer token hợp lệ từ realm agricontract."));
  body.push(numbered("Gateway đọc sub, email, realm_access.roles và bỏ Authorization trước khi forward."));
  body.push(numbered("Gateway inject X-User-Id, X-User-Email, X-User-Role và X-Gateway-Secret."));
  body.push(numbered("Downstream tạo Spring Authentication từ trusted headers; direct call không có secret bị chặn."));
  body.push(numbered("Feign service-to-service sử dụng X-Internal-Secret độc lập."));
  body.push(callout("PUBLIC SURFACE —", "Public API sử dụng được gồm GET /api/v1/products, GET /api/v1/listings và GET /api/v1/listings/{listingId}. ProductController hiện không có GET item endpoint. /api/v1/listings/seller không public.", "note"));

  body.push(H1("3. Data ownership và schema"));
  body.push(table(
    [1750, 2500, 5388],
    ["Database", "Owner", "Tables hiện tại"],
    [
      ["user_db", "user-service", "user_profiles"],
      ["product_db", "product-service", "categories, products, listings, product_domain_events"],
      ["contract_db", "contract-service", "contracts, contract_negotiations, contract_domain_events"],
      ["escrow_db", "escrow-service", "escrow_accounts, escrow_transactions, escrow_domain_events"],
      ["notification_db", "notification-service", "notification_logs"],
    ],
  ));
  body.push(P("Contract snapshot productName, buyer/seller organization và email khi tạo offer. Listing snapshot productName và coverImageUrl khi tạo listing. Đây là denormalization có chủ đích để record giao dịch không đổi khi dữ liệu nguồn thay đổi."));

  body.push(H1("4. State machines"));
  body.push(H2("4.1 Contract"));
  body.push(table(
    [1900, 2200, 2500, 3038],
    ["Từ", "Trigger", "Đến", "Guard chính"],
    [
      ["—", "Buyer tạo offer", "OFFERED", "Listing ACTIVE; buyer khác seller"],
      ["OFFERED/NEGOTIATING", "Counter-offer", "NEGOTIATING", "Participant; tăng revision; reset signatures"],
      ["OFFERED/NEGOTIATING", "Đủ hai chữ ký", "SIGNED", "Cùng terms revision"],
      ["SIGNED", "escrow.locked", "ACTIVE", "Seller deposit đã lock"],
      ["ACTIVE", "Buyer confirm", "DELIVERED", "Chỉ buyer"],
      ["DELIVERED", "escrow.released", "SETTLED", "Hết dispute window"],
      ["ACTIVE", "Participant cancel", "CANCELLED", "Lưu cancelledBy và reason"],
      ["DELIVERED", "Buyer dispute", "DISPUTED", "Chỉ buyer"],
      ["DISPUTED", "escrow.arbitrated", "SETTLED", "Escrow split hoàn tất"],
    ],
  ));

  body.push(H2("4.2 Escrow"));
  body.push(table(
    [1900, 2350, 2200, 3188],
    ["Từ", "Trigger", "Đến", "Side effect"],
    [
      ["—", "contract.signed", "BUYER_LOCKED", "LOCK buyer payment"],
      ["BUYER_LOCKED", "Seller confirm-deposit", "FULLY_LOCKED", "LOCK seller deposit; escrow.locked"],
      ["FULLY_LOCKED", "contract.delivered", "DELIVERY_PENDING", "Set releaseEligibleAt"],
      ["DELIVERY_PENDING", "Scheduler due", "RELEASED", "RELEASE payment; refund seller deposit"],
      ["FULLY_LOCKED/DELIVERY_PENDING", "contract.disputed", "DISPUTED", "Clear releaseEligibleAt; hold funds"],
      ["DISPUTED", "Admin arbitrate", "ARBITRATED", "Two allocation ledger rows; escrow.arbitrated"],
      ["FULLY_LOCKED", "contract.cancelled", "PENALIZED_*", "Penalty và refund ledger rows"],
    ],
  ));

  body.push(H1("5. Messaging architecture"));
  body.push(table(
    [2250, 2200, 2700, 2488],
    ["Exchange", "Producer", "Consumer", "Routing keys chính"],
    [
      ["agricontract.events", "product-service", "notification-service", "category.approved / rejected"],
      ["agricontract.contracts", "contract-service", "escrow, notification", "contract.signed / delivered / cancelled / disputed"],
      ["agricontract.escrow", "escrow-service", "contract, notification", "escrow.locked / released / penalized / arbitrated"],
    ],
  ));
  body.push(P("Contract, product và escrow outbox poll mỗi giây. Consumer retry tối đa ba lần với backoff 1s, 2s, 4s; payload invalid không retry; message hết retry đi DLQ theo service và routing key."));

  body.push(H1("6. Consistency và concurrency"));
  [
    "Aggregate change và outgoing event được lưu cùng local transaction.",
    "Outbox row ở PENDING khi RabbitMQ lỗi; poll sau thử lại; publish thành công chuyển PUBLISHED.",
    "Contract và Escrow dùng optimistic version để tránh lost update.",
    "Release scheduler và dispute cùng thay đổi EscrowAccount; optimistic locking bảo vệ race tại dispute deadline.",
    "Escrow unique theo contractId; notification unique theo cặp eventId và recipient.",
    "Không có bảng processed_events TTL trong implementation hiện tại.",
  ].forEach((item) => body.push(bullet(item)));

  body.push(H1("7. Local deployment"));
  body.push(table(
    [2500, 2500, 4638],
    ["Surface", "URL / port", "Ghi chú"],
    [
      ["Frontend", "localhost:5173", "Chạy npm run dev; chưa nằm trong Compose."],
      ["API Gateway", "localhost:8080", "Map vào container port 8888."],
      ["Keycloak", "localhost:8180", "Import agricontract-realm.json thủ công."],
      ["RabbitMQ UI", "localhost:15672", "guest / guest trong local."],
      ["MailHog", "localhost:8025", "Xem email phát từ notification-service."],
      ["Direct services", "8081–8084", "Chỉ expose khi dùng backend/docker-compose.override.yml."],
    ],
  ));
  body.push(endMark());

  return buildDoc(body, {
    title: "AgriContract — Kiến Trúc Kỹ Thuật Phase 1",
    headerText: "AgriContract · Phase 1 · Architecture",
    footerText: "AgriContract " + VERSION,
  });
}

function sdsDocument() {
  const body = baseChildren(
    "AGRICONTRACT · PHASE 1",
    "Software Design Specification",
    "Module design · Interface contracts · Runtime behavior · Verification",
    [
      "Mục đích và baseline",
      "Module map",
      "Interface contracts",
      "Backend design",
      "Frontend design",
      "Authorization và transition matrix",
      "Failure handling",
      "Verification",
    ],
  );

  body.push(H1("1. Mục đích và baseline"));
  body.push(P("SDS này ghi nhận thiết kế thực tế sau khi frontend Phase 1, negotiation history, dispute hold, delayed release và arbitration flow đã được merge vào main. Đây không còn là task breakdown theo người hoặc kế hoạch tuần."));

  body.push(H1("2. Module map"));
  body.push(table(
    [2100, 2600, 2600, 2338],
    ["Module", "Phụ thuộc", "Được dùng bởi", "Aggregate / state"],
    [
      ["user-service", "Gateway trusted headers", "contract-service", "UserProfile"],
      ["product-service", "Gateway; RabbitMQ", "contract-service; frontend", "Category, Product, Listing"],
      ["contract-service", "product/user Feign; RabbitMQ", "escrow; notification; frontend", "Contract; NegotiationRevision"],
      ["escrow-service", "RabbitMQ; Gateway", "contract; notification; frontend", "EscrowAccount; EscrowTransaction"],
      ["notification-service", "RabbitMQ; MailHog", "Development users", "NotificationLog"],
      ["frontend", "Gateway; Keycloak", "Browser users", "Auth/UI interaction state"],
    ],
  ));

  body.push(H1("3. Interface contracts"));
  body.push(H2("3.1 REST envelope"));
  body.push(P("Các backend API dùng envelope thống nhất: success (boolean), data (generic hoặc null), error (string hoặc null), timestamp (Instant). Validation/domain errors được map thành HTTP 400/403/404/409 phù hợp."));
  body.push(H2("3.2 Trusted headers"));
  body.push(table(
    [2200, 2800, 4638],
    ["Header", "Nguồn", "Mục đích"],
    [
      ["Authorization", "Frontend → Gateway", "Bearer JWT; bị strip trước khi forward."],
      ["X-User-Id", "Gateway", "Keycloak sub; principal name downstream."],
      ["X-User-Email", "Gateway", "Email snapshot và notification recipient."],
      ["X-User-Role", "Gateway", "Comma-separated realm roles."],
      ["X-Gateway-Secret", "Gateway", "Chứng minh request đi qua gateway."],
      ["X-Internal-Secret", "Service caller", "Cho phép Feign internal operation."],
    ],
  ));

  body.push(H1("4. Backend design"));
  body.push(H2("4.1 user-service"));
  [
    "POST /api/v1/users/register lấy identity từ trusted headers, không nhận userId/email/role từ body.",
    "Create idempotent: 201 khi mới, 200 và trả profile cũ khi đã tồn tại.",
    "GET /me phục vụ AuthBootstrap; GET /{userId} phục vụ contract-service snapshot.",
  ].forEach((item) => body.push(bullet(item)));

  body.push(H2("4.2 product-service"));
  [
    "Category có PENDING → APPROVED/REJECTED; duplicate chặn bằng normalized_name unique.",
    "Product bắt buộc approved category và 1–5 image URLs; create hỗ trợ client-provided productId.",
    "Listing snapshot productName và coverImageUrl; public list chỉ lấy ACTIVE; seller list là protected.",
    "Category review event được ghi product_domain_events và gửi email cho proposer.",
  ].forEach((item) => body.push(bullet(item)));

  body.push(H2("4.3 contract-service"));
  [
    "CreateContract gọi product-service để kiểm tra listing và user-service để snapshot organization/email.",
    "Mỗi counter-offer tăng termsRevision, clear signatories và append contract_negotiations.",
    "Đủ hai chữ ký mới phát contract.signed; listing được đóng bằng internal Feign call.",
    "List API phân trang theo buyer hoặc seller và optional status; negotiation history là endpoint riêng.",
    "escrow.locked activate; escrow.released settle delivery bình thường; escrow.arbitrated settle dispute.",
  ].forEach((item) => body.push(bullet(item)));

  body.push(H2("4.4 escrow-service"));
  [
    "contract.signed lock buyer amount và tạo BUYER_LOCKED; seller confirm tạo FULLY_LOCKED.",
    "contract.delivered schedule release, không release ngay; mặc định dispute window PT30S.",
    "contract.disputed đưa FULLY_LOCKED hoặc DELIVERY_PENDING sang DISPUTED và hủy deadline.",
    "Admin split toàn bộ totalAmount + sellerDeposit; tạo hai arbitration ledger rows.",
    "Escrow aggregate save cùng outgoing event trong escrow_domain_events; row dùng optimistic version.",
  ].forEach((item) => body.push(bullet(item)));

  body.push(H2("4.5 notification-service"));
  [
    "Consume contract, escrow và category events qua RabbitMQ.",
    "Notification key là (eventId, recipient), cho phép một event gửi nhiều recipient nhưng không duplicate từng người.",
    "Send failure đánh FAILED và tăng retryCount; listener retry tối đa ba lần trước DLQ.",
    "Arbitration result email gửi buyer và seller, gồm contractId và justification.",
  ].forEach((item) => body.push(bullet(item)));

  body.push(H1("5. Frontend design"));
  body.push(table(
    [2350, 2050, 5238],
    ["Route", "Access", "Behavior"],
    [
      ["/login", "Public", "Start Keycloak login."],
      ["/register-profile", "Authenticated", "Profile onboarding khi GET /users/me trả missing."],
      ["/listings, /listings/:id", "Public", "Marketplace; buyer authenticated tạo offer."],
      ["/listings/create, /listings/mine", "SELLER", "Tạo product/listing và xem listing của mình."],
      ["/contracts, /contracts/:id", "Authenticated", "List, revision history và action theo role/status."],
      ["/escrow", "Authenticated", "Escrow account và append-only transaction history."],
      ["/admin/arbitrate/:id", "ADMIN", "Split held amount và gửi justification."],
    ],
  ));
  body.push(P("TanStack Query quản lý server state và polling. Zustand giữ auth/session và interaction state nhỏ. Axios refresh Keycloak token; global interceptor chuyển 401 về login và phát UI notification cho 403/5xx."));

  body.push(H1("6. Authorization và transition matrix"));
  body.push(table(
    [2050, 1850, 2000, 3738],
    ["Action", "Actor", "State hợp lệ", "Kết quả"],
    [
      ["Counter-offer", "Buyer/Seller", "OFFERED, NEGOTIATING", "NEGOTIATING; revision++ và reset signatures"],
      ["Sign", "Buyer/Seller", "OFFERED, NEGOTIATING", "SIGNED khi đủ hai bên"],
      ["Confirm deposit", "Seller", "BUYER_LOCKED", "FULLY_LOCKED; contract ACTIVE async"],
      ["Cancel", "Buyer/Seller", "ACTIVE", "CANCELLED và penalty async"],
      ["Confirm delivery", "Buyer", "ACTIVE", "DELIVERED; release scheduled"],
      ["Dispute", "Buyer", "DELIVERED", "DISPUTED; escrow hold async"],
      ["Arbitrate", "Admin", "Escrow DISPUTED", "Escrow ARBITRATED; contract SETTLED async"],
    ],
  ));

  body.push(H1("7. Failure handling"));
  body.push(table(
    [2700, 3000, 3938],
    ["Failure", "Cơ chế", "Kết quả"],
    [
      ["RabbitMQ unavailable khi publish", "Outbox row giữ PENDING", "Poll sau publish lại."],
      ["Consumer transient exception", "3 attempts, exponential backoff", "Hết retry vào DLQ."],
      ["Payload malformed", "InvalidEventPayloadException non-retryable", "Reject và DLQ ngay."],
      ["Duplicate contract/escrow action", "Unique ID + status guard", "Không lặp business side effect hợp lệ."],
      ["Concurrent release và dispute", "JPA optimistic version", "Một transaction thắng; transaction stale rollback/skip."],
      ["Duplicate notification delivery", "Unique (event_id, user_id)", "Không gửi trùng recipient."],
    ],
  ));

  body.push(H1("8. Verification"));
  body.push(table(
    [2300, 2200, 5138],
    ["Suite", "Tool", "Coverage hiện tại"],
    [
      ["Backend", "Maven / JUnit", "Domain transitions, use cases, security filters, consumers, release scheduler."],
      ["Frontend unit", "Vitest + Testing Library", "ContractActionsBar và ContractNegotiationHistory."],
      ["Browser E2E", "Playwright", "Login, marketplace, offer/sign/deposit, settle, cancel, dispute, arbitrate."],
      ["API E2E", "Bruno", "Auth bypass, invalid states, idempotency, list filters, penalties và arbitration."],
    ],
  ));
  body.push(endMark());

  return buildDoc(body, {
    title: "AgriContract — Software Design Specification Phase 1",
    headerText: "AgriContract · Phase 1 · SDS",
    footerText: "AgriContract " + VERSION,
  });
}

function technicalSpecDocument() {
  const body = baseChildren(
    "AGRICONTRACT · PHASE 1",
    "Technical Specification",
    "Stack · REST API · Database · Events · Configuration · Tests",
    [
      "Technology baseline",
      "REST API",
      "Core DTO và value objects",
      "Database specification",
      "Event catalog",
      "Runtime configuration",
      "Build và verification commands",
    ],
  );

  body.push(H1("1. Technology baseline"));
  body.push(table(
    [2500, 3200, 3938],
    ["Layer", "Technology", "Version / usage"],
    [
      ["Backend", "Java, Spring Boot, Spring Cloud", "Java 21; Boot 3.3.5; Cloud 2023.0.3"],
      ["Persistence", "JPA, Flyway, MySQL", "Database per service; MySQL 8"],
      ["Messaging", "Spring AMQP, RabbitMQ", "RabbitMQ 3.13; Topic exchanges; DLQ"],
      ["IAM", "Keycloak", "24.0.4; realm agricontract; PKCE S256"],
      ["Frontend", "React, TypeScript, Vite", "React 19; TypeScript 6; Vite 8"],
      ["Frontend state", "TanStack Query, Zustand", "Server state và auth/client interaction state"],
      ["Testing", "JUnit, Vitest, Playwright, Bruno", "Unit, component, browser và API E2E"],
    ],
  ));

  body.push(H1("2. REST API"));
  body.push(H2("2.1 User endpoints"));
  body.push(table(
    [1000, 3300, 5338],
    ["Method", "Path", "Contract"],
    [
      ["POST", "/api/v1/users/register", "Body organizationName, phone, address; identity từ gateway headers; 201 mới / 200 existing."],
      ["GET", "/api/v1/users/me", "Profile của principal hiện tại."],
      ["GET", "/api/v1/users/{userId}", "UserInfo snapshot cho contract-service."],
    ],
  ));

  body.push(H2("2.2 Product endpoints"));
  body.push(table(
    [1000, 3500, 5138],
    ["Method", "Path", "Access / contract"],
    [
      ["GET", "/api/v1/products", "Public; pagination page/size/sortBy/sortDirection."],
      ["POST", "/api/v1/products", "SELLER; categoryId APPROVED; 1–5 imageUrls."],
      ["PUT", "/api/v1/products/{id}/images", "SELLER; replace 1–5 imageUrls."],
      ["GET", "/api/v1/listings", "Public; ACTIVE listings; pagination."],
      ["GET", "/api/v1/listings/{id}", "Public listing detail."],
      ["GET", "/api/v1/listings/seller", "SELLER; own listings."],
      ["POST", "/api/v1/listings", "SELLER; productId, quantity/unit, floor price/currency, deadline."],
      ["PUT", "/api/v1/listings/{id}/close", "Internal secret; idempotent close."],
      ["POST", "/api/v1/categories", "SELLER/BUYER; direct product-service in current local setup."],
      ["PUT", "/api/v1/categories/{id}/approve|reject", "ADMIN; direct product-service in current local setup."],
    ],
  ));

  body.push(H2("2.3 Contract endpoints"));
  body.push(table(
    [1000, 3800, 4838],
    ["Method", "Path", "Contract"],
    [
      ["POST", "/api/v1/contracts", "Create offer; optional contractId idempotency key."],
      ["GET", "/api/v1/contracts", "role BUYER(default)/SELLER; optional status; pagination."],
      ["GET", "/api/v1/contracts/{id}", "Participant hoặc ADMIN."],
      ["GET", "/api/v1/contracts/{id}/negotiations", "Ordered terms revision history."],
      ["PUT", "/api/v1/contracts/{id}/negotiate", "Body newTerms; revision++ và reset signatures."],
      ["PUT", "/api/v1/contracts/{id}/sign", "Principal ký revision hiện tại."],
      ["PUT", "/api/v1/contracts/{id}/cancel", "Body reason; chỉ ACTIVE."],
      ["PUT", "/api/v1/contracts/{id}/confirm-delivery", "Chỉ Buyer; ACTIVE → DELIVERED."],
      ["PUT", "/api/v1/contracts/{id}/dispute", "Body reason; Buyer; chỉ DELIVERED."],
    ],
  ));

  body.push(H2("2.4 Escrow endpoints"));
  body.push(table(
    [1000, 3900, 4738],
    ["Method", "Path", "Contract"],
    [
      ["GET", "/api/v1/escrows/contract/{contractId}", "Participant/Admin; account status."],
      ["GET", "/api/v1/escrows/{escrowId}/transactions", "Participant/Admin; append-only ledger."],
      ["PUT", "/api/v1/escrows/{contractId}/confirm-deposit", "SELLER; BUYER_LOCKED → FULLY_LOCKED."],
      ["PUT", "/api/v1/escrows/{contractId}/arbitrate", "ADMIN; buyerAmount, sellerAmount, justification."],
    ],
  ));

  body.push(H1("3. Core DTO và value objects"));
  body.push(table(
    [2500, 7138],
    ["Type", "Fields chính"],
    [
      ["ApiResponse<T>", "success, data, error, timestamp"],
      ["ContractTerms", "quantity(value/unit), agreedPrice(amount/currency), deliveryDeadline, buyerPenaltyRate, sellerDepositRate, qualitySpec"],
      ["ContractResponse", "contract/listing/buyer/seller snapshots, terms, status, cancel fields, termsRevision, signatories"],
      ["NegotiationHistoryResponse", "contractId, termsRevision, proposedBy, proposedAt, terms"],
      ["ProductResponse", "productId, name, unit, categoryId, images"],
      ["ListingResponse", "IDs, productName, coverImageUrl, quantity, floor price, deadline, status"],
      ["EscrowAccountResponse", "escrowId, contract/party IDs, amount/deposit/rate, status, releaseEligibleAt"],
      ["EscrowTransactionResponse", "transactionId, escrowId, type, amount/currency, note, createdAt"],
    ],
  ));

  body.push(H1("4. Database specification"));
  body.push(H2("4.1 product_db"));
  body.push(table(
    [2400, 7238],
    ["Table", "Columns / constraints chính"],
    [
      ["categories", "category_id unique; normalized_name unique; status; reason; proposer identity/email"],
      ["products", "product_id unique; name; unit; category_id; images JSON"],
      ["listings", "listing_id unique; seller/product refs; product_name + cover_image_url snapshots; quantity; price; deadline; status"],
      ["product_domain_events", "event_id unique; type; aggregate; JSON payload; PENDING/PUBLISHED; timestamps"],
    ],
  ));
  body.push(H2("4.2 contract_db"));
  body.push(table(
    [2400, 7238],
    ["Table", "Columns / constraints chính"],
    [
      ["contracts", "contract_id unique; snapshots; terms; status; cancel fields; signatories; terms_revision; optimistic version"],
      ["contract_negotiations", "FK contract_id; unique (contract_id, terms_revision); proposer/time; full terms snapshot"],
      ["contract_domain_events", "event_id unique; type; aggregate; JSON payload; PENDING/PUBLISHED; timestamps"],
    ],
  ));
  body.push(H2("4.3 escrow_db"));
  body.push(table(
    [2400, 7238],
    ["Table", "Columns / constraints chính"],
    [
      ["escrow_accounts", "escrow_id + contract_id unique; party IDs/emails; total/deposit/rate; status; release_eligible_at; optimistic version"],
      ["escrow_transactions", "transaction_id unique; account FK; type; amount/currency; note; created_at"],
      ["escrow_domain_events", "event_id unique; type; aggregate; JSON payload; PENDING/PUBLISHED; timestamps"],
    ],
  ));
  body.push(H2("4.4 user_db và notification_db"));
  body.push(table(
    [2400, 7238],
    ["Table", "Columns / constraints chính"],
    [
      ["user_profiles", "user_id unique; organization; role; email/phone/address; verification_status"],
      ["notification_logs", "notification_id unique; unique (event_id, user_id); channel; subject/body; status; retry_count"],
    ],
  ));

  body.push(H1("5. Event catalog"));
  body.push(table(
    [2350, 2300, 2500, 2488],
    ["Event", "Producer", "Consumer Phase 1", "Payload quan trọng"],
    [
      ["category.approved/rejected", "product", "notification", "categoryId, name, proposer email, rejectionReason"],
      ["contract.signed", "contract", "escrow, notification", "contract/party IDs + emails, listingId, terms"],
      ["contract.delivered", "contract", "escrow, notification", "contractId, emails, confirmedBy"],
      ["contract.cancelled", "contract", "escrow, notification", "cancelledBy, reason, buyerPenaltyRate"],
      ["contract.disputed", "contract", "escrow, notification", "disputedBy, reason"],
      ["escrow.locked", "escrow", "contract, notification", "escrowId, contractId, emails"],
      ["escrow.released", "escrow", "contract, notification", "escrowId, contractId, emails"],
      ["escrow.penalized", "escrow", "notification", "penalizedParty, amount"],
      ["escrow.arbitrated", "escrow", "contract, notification", "buyerAmount, sellerAmount, justification"],
    ],
  ));
  body.push(P("Mọi DomainEvent có eventId và occurredAt. Các lifecycle events khác vẫn được publish cho khả năng mở rộng nhưng không nhất thiết có consumer Phase 1."));

  body.push(H1("6. Runtime configuration"));
  body.push(table(
    [3300, 2100, 4238],
    ["Variable", "Default local", "Mục đích"],
    [
      ["GATEWAY_INTERNAL_SECRET", "required in .env", "Gateway → downstream trust."],
      ["SERVICE_INTERNAL_SECRET", "required in .env", "Feign service-to-service trust."],
      ["KEYCLOAK_JWK_URI", "keycloak realm JWKS", "Gateway validation."],
      ["DELIVERY_DISPUTE_WINDOW", "PT30S", "Khoảng chờ trước release."],
      ["DELIVERY_RELEASE_POLL_INTERVAL_MS", "1000", "Release scheduler interval."],
      ["VITE_API_BASE_URL", "http://localhost:8080", "Frontend gateway target."],
      ["VITE_KEYCLOAK_URL", "http://localhost:8180", "Frontend identity provider."],
      ["VITE_USE_MOCKS", "false", "Switch API adapters sang mock data."],
    ],
  ));

  body.push(H1("7. Build và verification commands"));
  body.push(P("Backend: mvn test"));
  body.push(P("Frontend: npm test; npm run lint; npm run build"));
  body.push(P("Browser E2E: npm run test:e2e với backend, Keycloak realm và frontend đang chạy."));
  body.push(P("Bruno contract E2E: bật backend/docker-compose.override.yml rồi chạy ./scripts/run-e2e.sh."));
  body.push(risk("KNOWN BOUNDARIES —", "Frontend chưa có Docker service; category routes chưa qua gateway mặc định; Swagger downstream không được proxy; filter marketplace chủ yếu client-side; escrow chỉ là mock."));
  body.push(endMark());

  return buildDoc(body, {
    title: "AgriContract — Technical Specification Phase 1",
    headerText: "AgriContract · Phase 1 · Technical Specification",
    footerText: "AgriContract " + VERSION,
  });
}

async function writeDocument(fileName, document) {
  const buffer = await Packer.toBuffer(document);
  const output = path.join(OUTPUT_DIR, fileName);
  fs.writeFileSync(output, buffer);
  process.stdout.write(`built ${output} (${buffer.length} bytes)\n`);
}

async function main() {
  await writeDocument("AgriContract_02_GiaiPhap_MoHinh.docx", solutionDocument());
  await writeDocument("AgriContract_Architecture_v1_2.docx", architectureDocument());
  await writeDocument("AgriContract_SDS_v1_3.docx", sdsDocument());
  await writeDocument("AgriContract_TechnicalSpec_v1_2.docx", technicalSpecDocument());
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
