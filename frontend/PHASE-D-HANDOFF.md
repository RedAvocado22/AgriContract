# Phase D — Contract Golden Flow

Ngày kiểm tra: **09/11/2026**, môi trường local. Branch `feat/fe-contract-golden-flow`, từ commit Phase C `4cde933`. Lịch sử có Phase B `54bb8d4` và Phase A `f5cef49`.

**Trạng thái: đã triển khai phần frontend có thể thực hiện với API hiện tại; chưa nghiệm thu Golden Flow end-to-end.** Danh sách/chi tiết có adapter gọi Gateway thật. Tạo hợp đồng mới, lưu đàm phán, ký thật và tài liệu vẫn bị khóa theo các blocker dưới đây. Không có hợp đồng, chữ ký, PDF hoặc Escrow giả được đưa vào dữ liệu runtime.

## 1. Đối chiếu thiết kế và triển khai backend

Đã đọc `fe-agent-prompt.md`, `docs/contracts/golden-flow.md`, `contracts/openapi/golden-flow-api.yaml`, tài liệu signature/contract-document-consent và state-machines, SRS `docs/reports/Report-3_SRS_1_7.docx`, handoff C, controller/DTO/use case thực tế, auth/API/guards B/C, UI library và mockup hợp đồng. Yêu cầu mới của task có ưu tiên hơn hướng dẫn cũ về hardcode consent `1.0`, mock tạo hợp đồng và commit.

| Chức năng | API thiết kế | Backend trong source hiện tại | Real / Adapter / Mock | Blocker |
| --- | --- | --- | --- | --- |
| Danh sách | GET `/api/v1/contracts` | `ContractController.listContracts`; envelope `PaginatedResponse<ContractResponse>` Phase 1 | Adapter gọi API thật | Runtime local trả 500 vì Gateway không resolve được contract-service |
| Chi tiết | GET `/api/v1/contracts/{id}` | `getContract`: cà phê trả trực tiếp `CoffeeContractResponse`; legacy trả envelope | Adapter gọi API thật, kiểm tra participant/id | Chưa smoke được response thành công thật; thiếu dữ liệu chữ ký/lịch sử/thời gian |
| Điều khoản | PATCH `/{id}/terms` + Idempotency-Key | `UpdateContractTermsUseCase` có kiểm tra ownership, replay, frozen terms | Transport adapter + form đề xuất cục bộ; **khóa lưu** | DTO không trả first-signature freeze/quyền sửa; PATCH chưa có precondition phiên bản cũ |
| Consent | Statement đang có hiệu lực cho đúng action/hash | Cấu hình statement nằm phía server; chưa có public read endpoint | `getSigningConsent()` trả **null (unavailable)**; không gọi URL đoán | Cần BE công bố nguồn statement text/version, action và binding nội dung |
| OTP | POST `/{id}/sign/initiate`, POST `/{id}/sign/verify` | `ContractSigningController` có cả hai; initiate trả otpId/expiresAt, verify 204 | Adapter thật đã chuẩn bị; UI/session được kiểm thử bằng mock; **khóa ký runtime** | Thiếu consent read API, runtime contract-service, hồ sơ VERIFIED và hợp đồng test |
| PDF nháp | GET `/{id}/document-preview` | Chưa có controller | Adapter unavailable, nút vô hiệu hóa | BE cần render server-side và watermark |
| PDF cuối | GET `/{id}/documents`, GET `/{id}/documents/{documentId}` | Chưa có controller | Adapter unavailable; không có URL/file giả | BE cần metadata PENDING/READY/FAILED và proxy tải READY |
| Kích hoạt | GET chi tiết; ACTIVE sau `escrow.deposit_locked` | DTO có status; chưa có activationRecovery/progress | Hiển thị status thật, polling giới hạn khi SIGNED | Chưa kiểm thử event/khóa cọc/PDF; chưa có dữ liệu recovery |
| Tạo mới | POST `/api/v1/contracts` Phase 2 | POST hiện nhận CreateContractRequest Phase 1, gọi `Contract.offer`; chưa dùng listingVersionToken/reservation Phase 2 | UI chuẩn bị + adapter từ chối; không gọi POST cũ thay thế | BE #232; FE listing hiện là mock, chưa có nguồn hàng thật/availability/min order |
| Hủy do lỗi kích hoạt | Các route activation/cancel trong prompt cũ | Chưa có controller | Không mô phỏng hủy/hoàn tiền; TODO #234 | Cần BE chốt/triển khai API; không gọi endpoint thiếu |

Phân biệt **API có trong source** với **dịch vụ chạy được trong môi trường**: các controller đọc/ký tồn tại nhưng chưa thể gọi thành công qua Gateway local lần này. OpenAPI không được dùng làm bằng chứng backend đã chạy.

## 2. Chức năng frontend

- `/contracts`: vai trò BUYER/SELLER lấy từ phiên B; bộ lọc trạng thái, page zero-based/size 20 theo backend; DataTable desktop, RecordCard mobile, loading/empty/error/retry. Query có userId/role/status/page; không gửi userId để truy vấn người khác. Adapter từ chối response không thuộc người đang đăng nhập hoặc sai vai trò được chọn.
- Bộ lọc chỉ gửi 5 giá trị mà Java enum hiện nhận: OFFERED, NEGOTIATING, SIGNED, ACTIVE, SETTLED. Các nhãn terminal Phase 2 có mapping hiển thị nếu server sau này trả về, không tự tạo trạng thái đó.
- `/contracts/:id`: phân biệt legacy/cà phê, kiểm tra response đúng mã yêu cầu, hiển thị chủ thể (tên nếu có; không có thì mã), điều khoản hàng hóa/chất lượng/giao nhận/kiểm định/pháp lý/cọc, lịch giao cam kết và milestones chỉ đọc. `CoffeeContractResponse.from` hiện trả milestones rỗng; FE không biến lịch cam kết thành tiến độ thực hiện giả.
- Hiển thị signedContentHash và phiên bản mẫu từ server. Phiên bản sửa điều khoản, chữ ký từng bên, thời gian tạo, lịch sử và dữ liệu không có được ghi là chưa cung cấp/chưa xác định. Hash thay đổi khi refetch sẽ có cảnh báo và bỏ bản đề xuất/consent/OTP cục bộ cũ.
- Legacy chỉ đọc. Cà phê OFFERED/NEGOTIATING có form RHF/Zod **đề xuất đơn giá cục bộ** và so sánh trước/sau. Form chưa phải bộ chỉnh sửa toàn bộ ContractTermsInput; lưu bị khóa vì chưa xác định freeze/stale precondition. Không gọi PATCH từ UI. Transport `patchTerms` và allowlist `termsInput` sẵn để nối tiếp sau khi BE giải quyết blocker; test PATCH success/conflict là mock Axios, không phải lưu đàm phán thật.
- Ký: tái sử dụng Stepper/Checkbox/OTPInput/Countdown/ContentHashDisplay, guard VERIFIED + participant/JWT/profile role, hook step-up B. Adapter yêu cầu consent khớp action/contract/hash và version/text không rỗng. Không hardcode statement/version, không tự hash.
- `SigningSession`: memory-only, chống thao tác đồng thời, key gắn một logical operation, giữ key cho retry thủ công khi lỗi mạng/5xx không rõ kết quả; resend loại challenge cũ ngay cả khi request lỗi; cooldown; kiểm tra deadline ngay trước verify; 204 mới xác nhận chữ ký của người dùng. OTP sai/hết hạn/đổi điều khoản/giới hạn gửi được xử lý riêng. Không tự retry lệnh ký, không tự ghi SIGNED/ACTIVE. Sau verify thành công dự kiến invalidate detail/list. **Luồng này chưa mở trong runtime vì consent unavailable**.
- Step-up chỉ lưu intent không chứa OTP/JWT; khi quay lại yêu cầu đọc/xác nhận lại, không replay write. Refresh browser bỏ challenge/OTP trong bộ nhớ; không suy diễn đã ký nếu response thiếu chữ ký.
- SIGNED: “đã ký hai bên — chờ xử lý và khóa cọc”, không coi là ACTIVE. GET poll mỗi 5 giây, tối đa 2 phút tính từ khi mount màn chi tiết; dừng khi lỗi/khác SIGNED, không poll nền. Sau đó người dùng tải lại thủ công. Khi refetch thật trả ACTIVE mới hiển thị kích hoạt/toast.
- PDF và lịch sử hiện hiển thị chưa sẵn sàng; không suy ra PENDING/READY từ SIGNED, không link MinIO/object key, không dựng PDF thay server. Helpers mapping trạng thái PDF là phần chuẩn bị, chưa có document feed runtime.
- `/contracts/new` và link từ listing: UI chuẩn bị cà phê B2B nội địa; khóa tạo; ghi rõ listing hiện là dữ liệu mẫu, không liên kết nguồn hàng mock vào hợp đồng thật. Không giữ hàng hoặc tính availability trên client.
- Axios/error parser B dùng chung; xử lý 400/401/403/404/409/422/5xx/network, không đổi trạng thái thành công khi lỗi. 401 vẫn do B chuyển Keycloak; không retry write.
- MoneyDisplay mở rộng nhận chuỗi decimal bằng định dạng chuỗi, giữ tương thích callers số cũ. Không tính tổng/cọc/phạt trên FE. Số tiền legacy dạng number chỉ hiển thị khi là số nguyên an toàn; số đã có nguy cơ mất chính xác không được đoán lại. UTC hiển thị giờ Việt Nam; date-only giữ ngày, không sửa deadline.

## 3. Kiểm thử thực tế

Keycloak `http://localhost:8180`, realm `agricontract`, client `agricontract-frontend`; Gateway `http://localhost:8080`; frontend `http://localhost:5173`.

| Kiểm tra thật | Kết quả |
| --- | --- |
| Chưa đăng nhập vào `/contracts` | Chuyển Keycloak authorization code + PKCE S256, giữ return URL |
| buyer1 | Đăng nhập thành công; GET `/users/me` 200, BUYER, **PENDING** |
| seller1 | Đăng nhập thành công; GET `/users/me` 200, SELLER, **PENDING**; hồ sơ C vẫn có |
| GET `/contracts?role=BUYER&page=0&size=20` | **500**, UI error state/retry |
| GET `/contracts?role=SELLER&page=0&size=20` | **500**, UI error state/retry |
| Chẩn đoán read-only | `docker ps -a --filter name=contract-service` không có container; Gateway log `UnknownHostException: Failed to resolve 'contract-service'` |
| Refresh token buyer1/seller1 | Thành công; store khớp Keycloak adapter, auth_time còn và không đổi do refresh, không lưu JWT vào local/sessionStorage |
| Quay `/account` | Hồ sơ thật hiển thị, không ép onboarding lại |

Không sửa Compose, không khởi tạo dịch vụ BE, không ghi DB, không tự duyệt hồ sơ hoặc tạo contract fixture backend. Không thử lấy OTP từ DB/MailHog. Không có POST/PATCH hợp đồng thật trong đợt này.

## 4. Test mock, lint, build và giới hạn

- `npm test`: **91/91 pass, 5 files** — giữ 44 tests B/C, thêm 47 tests contracts. Các fixtures mang danh tính test và chỉ nằm trong tests.
- Test mới: envelope/direct mapping, null hash, participant/role/id mismatch, list empty, HTTP/network failures, whitelist field server-owned, PATCH transport success/conflict, form validation, capability unavailable không gọi endpoint, consent thiếu/sai binding, initiate DTO, verify chỉ nhận 204, OTP sai/hết hạn/challenge cũ, duplicate action, key reuse khi retry, resend/cooldown, deadline khi browser timer bị chậm, pending/verified permissions, SIGNED != ACTIVE, PDF PENDING != READY, poll bounded, decimal chính xác và UTC/date-only.
- `npm run lint`: exit 0; **4 cảnh báo Fast Refresh có sẵn** tại button/banner/status-badge/toast. Không thêm cảnh báo mới.
- `npm run build`: exit 0; còn cảnh báo chunk >500 kB (đã có từ trước). Không thêm dependency, không đổi lockfile.
- Playwright Chromium với **intercept tại lớp mạng**, không mock API trong app: list success/empty/403/404/500/network/retry; detail success/403/404/500; PENDING bị khóa ký; VERIFIED vẫn bị khóa do thiếu consent; form đơn giá trống/đề xuất/review/khóa lưu; cảnh báo hash đổi; SIGNED chờ, ACTIVE theo response; PDF không có link; reload không loop; tạo mới bị khóa. Có **0 write request** trong các ca UI này.
- Mock 401 một lần → B chuyển qua Keycloak thật, SSO trở lại `/contracts`, GET thứ hai phục hồi. Mock 404 đúng `User not found: <sub>` → onboarding C; bỏ intercept → `/account` lại đọc hồ sơ thật.
- Đã kiểm tra bố cục danh sách và chi tiết ở 1440/390/320 px, trang chuẩn bị tạo ở 320 px, không tràn ngang; thử điều khiển lọc/phân trang trên trình duyệt. Mockup được mở bằng trình duyệt; không sao chép dữ liệu mẫu, giới hạn slider hoặc mô tả OTP SMS cũ trái với API email OTP hiện hành.
- Chưa test Safari/iOS/thiết bị thật. Tests OTP/PDF/lifecycle không chứng minh hai chữ ký, worker PDF hoặc escrow chạy thật. UI OTP có consent hợp lệ chưa được browser E2E vì không có nguồn consent; session/transport mới được unit test.

## 5. Demo local

1. Giữ cấu hình `.env.local` Phase B (không commit): Gateway 8080, Keycloak 8180, realm/client ở trên. Trong `frontend/` chạy `npm ci` nếu cần, rồi `npm run dev -- --port 5173 --strictPort`.
2. Mở `http://localhost:5173/contracts`, đăng nhập buyer1. Sidebar/TopBar hiện hồ sơ thật. Với môi trường hiện tại sẽ thấy lỗi dịch vụ 500; bấm thử lại không tạo hợp đồng hay chữ ký.
3. Dùng browser/profile khác đăng nhập seller1; danh sách mặc định chọn Bên bán. Cả hai hiện PENDING, không tự thay thành VERIFIED.
4. Mở `/account` để kiểm tra B/C; refresh browser vẫn giữ phiên. Sidebar có link Hợp đồng của tôi.
5. Mở `/contracts/new`, hoặc từ listing bấm “Xem bước chuẩn bị hợp đồng cà phê”: đọc blocker, nút tạo bị khóa. Không dùng mã listing mẫu để gọi POST.
6. Sau khi BE cung cấp contract-service và hợp đồng participant-owned: vào danh sách, chọn trạng thái/trang, mở mã hợp đồng để xem. Với hợp đồng cà phê, thử đề xuất giá và xem lại thay đổi; lưu/ký vẫn bị khóa đến khi capability/consent được tích hợp.
7. Với dữ liệu SIGNED thật sau khi BE chuẩn bị đúng quy trình: mở chi tiết để xem refetch; chỉ khi backend trả ACTIVE mới hiển thị đã kích hoạt. Chưa có chức năng tải PDF thật.
8. Chạy lại `npm test`, `npm run lint`, `npm run build`. Các test tự động dùng mock được gắn nhãn; không có nút bật backend giả hoặc nút bypass verification trong app.

## 6. BE/Infra cần hỗ trợ và phần chưa nghiệm thu

1. Khởi chạy/cấu hình DNS/network cho contract-service trong môi trường local để Gateway route được; xác nhận version image khớp source. Cần smoke lại GET list/detail thành công, 403 ownership, 404 thật và phân trang với dữ liệu thật.
2. Chuẩn bị đúng quy trình hồ sơ VERIFIED cho buyer1/seller1 và hợp đồng cà phê OFFERED của chính hai bên. Prompt cũ nói đã VERIFIED nhưng kiểm tra thật vẫn PENDING. FE không sửa DB/role/verification để vượt guard.
3. Cấp read API cho consent đang active: text, version, action/signer role, cách bind subject/hash và chính sách cache/invalidations. Không bật ký bằng cách thay null thành literal `1.0` hoặc copy cấu hình nội bộ.
4. Cấp trạng thái chữ ký từng bên/first-signature freeze/quyền edit/sign trong read model; chốt version/ETag/precondition PATCH để tránh ghi đè thay đổi của đối tác. Khi đó cần hoàn thiện form các điều khoản còn lại, nối mutation/invalidation/conflict flow và test lưu thật. Hiện chỉ chuẩn bị đề xuất đơn giá cục bộ.
5. BE #232: thay create Phase 1 bằng create cà phê Phase 2, listingVersionToken, minOrderQuantity/quantityAvailable, reservation/idempotency và error codes. FE listing cần nguồn dữ liệu thật trong phạm vi được duyệt trước khi mở tạo mới. Không tự fallback sang `/listings/{id}/offers` vì prompt cũ nêu route này.
6. Triển khai preview/list/document proxy và worker final PDF; làm rõ projection tiến độ tài liệu/khóa cọc/recovery. Cần nghiệm thu PDF PENDING → READY, quyền tải, không lộ storage key và SIGNED → ACTIVE qua `escrow.deposit_locked` thật.
7. BE #234 và recovery: API hủy/hoàn cọc chưa có; cần chốt với frozen API trước khi mở action. Ngoài phạm vi đợt này: giao hàng, inspection workflow, dispute, settlement, operator actions và Phase E.
8. Cần kiểm thử signing E2E hai browser: active consent → step-up → OTP email → verify mỗi bên → signed → final PDF → escrow ACTIVE. Hiện **chưa nghiệm thu bất kỳ chuỗi E2E này**; không coi tests mock là backend hoàn thiện.
9. Giữ các blocker C: cấp role cho tự đăng ký, API upload tài liệu tổ chức; Infra cần mount theme A nếu muốn thay giao diện Keycloak Compose mặc định (hiện vẫn thấy theme mặc định).

## 7. File thêm/sửa và ảnh hưởng A/B/C

Thêm (đường dẫn tương đối từ `frontend/`):

- `src/features/contracts/api/contracts.ts`
- `src/features/contracts/api/capabilities.ts`
- `src/features/contracts/api/signing.ts`
- `src/features/contracts/hooks/use-contracts.ts`
- `src/features/contracts/hooks/signing-session.ts`
- `src/features/contracts/components/contracts-layout.tsx`
- `src/features/contracts/components/contract-state.tsx`
- `src/features/contracts/components/contract-terms.tsx`
- `src/features/contracts/components/negotiation-form.tsx`
- `src/features/contracts/components/signing-panel.tsx`
- `src/features/contracts/schemas/negotiation.ts`
- `src/features/contracts/types/contract.ts`
- `src/features/contracts/types/presentation.ts`
- `src/routes/contracts-page.tsx`
- `src/routes/contract-detail-page.tsx`
- `src/routes/create-contract-page.tsx`
- `src/shared/lib/decimal-display.ts`
- `tests/contracts.test.ts`
- `PHASE-D-HANDOFF.md`

Sửa:

- `src/App.tsx` — thêm route dưới auth/profile/role guard hiện có; không sửa logic guard.
- `src/features/organization/components/organization-layout.tsx` — thêm link hợp đồng cho BUYER/SELLER.
- `src/routes/listing-detail-page.tsx` — link trang chuẩn bị và nhãn nguồn hàng mẫu; không nối POST cũ.
- `src/shared/ui/money-display.tsx` — nhận thêm decimal string, callers number giữ hành vi cũ.
- `src/routes/component-showcase.tsx` — ví dụ MoneyDisplay chuỗi số lớn chính xác.

Rủi ro tích hợp A/B/C giới hạn ở route/navigation và mở rộng MoneyDisplay. Auth adapter/store/Axios/step-up/organization guard không bị sửa; tests B/C vẫn pass; account và refresh token thật đã kiểm tra. Theme A không thay đổi và chưa rebuild/retest trong D.

Git: HEAD giữ `4cde933`; toàn bộ thay đổi D chưa stage/commit. File `fe-agent-prompt.md` untracked có sẵn được giữ nguyên. Không commit/push/PR/reset/xóa thay đổi; không sửa backend/contracts/design/Compose/theme, `.env.local`, token/password hoặc đưa build artifacts vào Git.
