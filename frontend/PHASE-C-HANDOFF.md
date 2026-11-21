# Phase C — Hồ sơ tổ chức sau đăng ký

Triển khai trên branch `feat/fe-organization-onboarding`, tạo từ HEAD `54bb8d4` đã có A/B. Chỉ sửa frontend; không sửa Keycloak adapter/store/route guards của B, theme A, backend, Compose, contracts hoặc tài liệu thiết kế. Không triển khai Phase D; không commit/push/PR.

## Chức năng và quyết định theo API thật

- Feature `organization/{api,hooks,components}` gọi `GET /api/v1/users/me` và `POST /api/v1/users/register` qua Axios client B và Gateway. Dùng cùng query key `['my-profile', userId]` để TopBar/AppSidebar/AccountMenu không có hai nguồn hồ sơ.
- Query chỉ chạy khi auth B đã `ready`, authenticated và có userId. GET huỷ được bằng AbortSignal; cache được B xoá khi đổi danh tính/đăng xuất.
- Thiếu hồ sơ chỉ được công nhận khi GET đúng `/api/v1/users/me` trả 404, envelope `success=false`, `data=null/absent`, `error='User not found: <current userId>'`. Đây là thông điệp hiện tại của `GetUserProfileUseCaseImpl` + `GlobalExceptionHandler`. 404 HTML/Gateway, lỗi khác hoặc userId khác vẫn là lỗi, không mở form đăng ký.
- `/account` và `/workspace` dùng guard hồ sơ bên trong RequireAuth. Thiếu hồ sơ chuyển `/onboarding`; hồ sơ có sẵn truy cập `/onboarding` chuyển về `/account`. Trang công khai vẫn được xem, không bị biến thành trang yêu cầu xác minh. Callback đăng nhập B về `/account` sẽ áp guard C.
- Form RHF + Zod: BUYER/SELLER, tên doanh nghiệp bắt buộc sau trim, điện thoại/địa chỉ tuỳ chọn. Không tự đặt regex/độ dài cho phone/address khi contract chưa quy định. Có trạng thái đang gửi, giữ dữ liệu khi lỗi và chặn submit trùng.
- Model form có `role`, nhưng payload chỉ có `organizationName`, `phone`, `address`, đúng `RegisterUserRequest`. Chỉ cho submit khi có đúng một realm role nghiệp vụ BUYER hoặc SELLER và lựa chọn khớp role đó. Không role/role đặc quyền/nhiều role nhận thông báo và bị chặn. Backend đang chọn role hợp lệ đầu tiên từ danh tính, nên trường hợp nhiều role không thể để frontend đoán.
- POST dùng response thật để cập nhật cache, rồi invalidate/refetch GET. Không tự gán VERIFIED. Backend idempotent trả 200 khi đã có hồ sơ, 201 khi tạo mới; FE không ép chỉ một status.
- `/account` hiển thị tên tổ chức, email, vai trò, điện thoại, địa chỉ, trạng thái xác minh. Thiếu trường hiển thị “Chưa có thông tin”; hồ sơ có userId nhưng thiếu tên không bị coi là chưa đăng ký. Response thiếu/sai userId là lỗi.
- `PENDING` → chờ Operator; `REJECTED` → chưa được chấp thuận; trạng thái thiếu/không nhận biết → chưa xác định, không mở quyền ghi. Dùng Banner thay `pending-approval-card.tsx` vì card cũ còn copy chặn tìm nguồn hàng và hứa gửi SMS/email chưa phù hợp. File card cũ được giữ nguyên.
- `useOrganizationPermissions` chỉ cho phép tạo/ký khi hồ sơ VERIFIED và JWT có role BUYER/SELLER khớp hồ sơ. Guard `RequireVerifiedOrganization` sẵn cho các route ghi ở phase sau. Không áp guard VERIFIED lên các route chỉ đọc.
- Đã rà App/routes: chưa có route/API tạo hoặc ký hợp đồng. CTA trên listing được nối với trạng thái hồ sơ; người chưa VERIFIED không có action ghi khả dụng. Người VERIFIED cũng chưa thể gửi đề nghị vì Phase D chưa triển khai. Không dựng hợp đồng/OTP giả.
- Layout theo mockup tài khoản: sidebar, top bar, mục hồ sơ/pháp lý, card thông tin hai cột và tài liệu; responsive. Không thêm dữ liệu mã số thuế, đại diện, ngày uỷ quyền giả từ mockup vì DTO không cung cấp các thông tin đó.

## Tài liệu doanh nghiệp — chưa upload

`api/documents.ts` có `// TODO(BE): organization document upload`. Adapter chọn tệp trả `storage: 'local-only'`; adapter upload từ chối rõ ràng vì chưa có API. Không gửi file, không trả URL/fileId giả hoặc thông báo upload thành công.

Tái sử dụng FileUpload, bổ sung trạng thái `local` với nhãn **“Chỉ trên thiết bị”**, có ví dụ trong showcase. File chỉ ở bộ nhớ trang, không gửi cùng POST hồ sơ và không lưu qua reload/navigation. Không áp accept/MIME/max-size/max-count giả định. Khu vực này không phải danh sách tài liệu đã lưu trên server.

## Kiểm thử API thật — Compose local ngày 08/11/2026

Không dùng realm độc lập của A/B để thay thế kiểm thử lần này. Đăng nhập qua Keycloak 8180, realm `agricontract`, client `agricontract-frontend`, frontend 5173 và Gateway 8080.

| Luồng | Bằng chứng |
| --- | --- |
| Chưa đăng nhập vào `/account` | Chuyển Keycloak code flow + PKCE S256 |
| `buyer1` GET `/me` | 200; `Công ty Cà phê Demo Buyer`, BUYER, **PENDING**. Giữ nguyên dữ liệu, không ép onboarding |
| `seller1` trước đăng ký | 404 đúng `User not found: <sub>`; vào onboarding, mặc định SELLER |
| Tên doanh nghiệp trống | Validation tiếng Việt, không POST |
| Chọn BUYER trên `seller1` | Submit bị khoá do không khớp role |
| Đăng ký `seller1` qua UI | POST **201**, chỉ ba field DTO; GET refetch **200**, chuyển `/account`, trạng thái **PENDING** |
| Dữ liệu local đã tạo | Tên **Hồ sơ kiểm thử Phase C — seller1**; phone/address null. Đây là hồ sơ kiểm thử local được lưu thật, không phải tổ chức đã xác minh |
| Reload buyer/seller | Giữ phiên, đọc hồ sơ, không loop onboarding |
| Regression refresh B | Refresh JWT thật; store khớp adapter, auth_time giữ nguyên, không lưu token vào localStorage/sessionStorage |
| Regression step-up B | `prompt=login&max_age=0`, nhập lại mật khẩu, quay `/account`, khôi phục bước xác nhận phiên |
| Regression logout B | Đăng xuất về origin; truy cập `/account` lại yêu cầu Keycloak login với PKCE S256 |

Không tự duyệt KYC, không đổi role, không sửa dữ liệu buyer1 hoặc xoá database. Sau bài test, seller1 đã có hồ sơ nên lần đăng nhập sau không còn là case “chưa có hồ sơ”. Muốn demo case mới cần tài khoản local khác do BE chuẩn bị; không xoá hồ sơ seller1 để lặp lại.

## Kiểm thử bằng mock và tự động

Mock chỉ được đưa vào trình duyệt Playwright để kiểm thử, không nằm trong API runtime của app:

- 404 thiếu hồ sơ, validation, role mismatch, chọn file local, POST/refetch/PENDING.
- VERIFIED không bị onboarding/pending chặn; REJECTED có thông báo riêng. **Chưa có buyer1/seller1 VERIFIED thật** và không tự đổi trạng thái backend để test.
- 404 không rõ nguồn, GET 403/500/network giữ error state; retry phục hồi.
- Mock 401 một lần: interceptor B mở đăng nhập lại đúng một lần và phục hồi `/account` qua SSO.
- Loading skeleton; account không có role bị chặn submit (thay state UI trong test, không thay JWT/realm).
- POST 500 giữ dữ liệu form; retry thành công; hai submit khi request còn chờ không tạo POST trùng.
- Các mock POST đều bị intercept, **không ghi backend**.

`npm test`: **44/44 pass, 4 file** (22 bài Phase B giữ nguyên và 22 bài organization mới). Bao gồm nhận diện 404 chính xác, DTO không role, role không rõ, optional fields, verification fail-closed, lỗi HTTP/network và upload không có fake success. Unit test stub Axios không được xem là API thật.

`npm run lint`: exit 0, còn bốn cảnh báo Fast Refresh có sẵn tại button/banner/status-badge/toast. `npm run build`: exit 0, còn cảnh báo bundle >500 kB. Không thêm dependency hoặc đổi lockfile.

UI profile đã xem ở 1440/390/320 px, không tràn ngang; onboarding đã xem ở 390 px. Đã mở `/components` và `/components/mobile`, kiểm tra ví dụ FileUpload local. Browser QA dùng Chromium, chưa kiểm tra thiết bị thật/Safari/iOS.

## Demo local

1. Bảo đảm Keycloak/Gateway/User-service local sẵn sàng như trên; giữ `.env.local` hiện tại, không đưa file này vào Git.
2. Trong `frontend/`: `npm ci`, `npm run dev -- --port 5173 --strictPort`.
3. Mở `http://localhost:5173/account`, đăng nhập buyer1 bằng tài khoản test đã có. Kiểm tra hồ sơ thật và banner PENDING. Có thể mở `/onboarding` để thấy tự quay về account, không đăng ký lại.
4. Dùng trình duyệt/profile khác đăng nhập seller1. Hiện hồ sơ kiểm thử đã tạo và các ô phone/address “Chưa có thông tin”.
5. Để demo onboarding mới, nhờ BE cấp tài khoản local chưa có hồ sơ, có đúng một role BUYER/SELLER. Đăng nhập → `/onboarding`; thử tên trống, chọn sai loại, rồi nhập dữ liệu test được thống nhất và gửi.
6. Chọn tài liệu để thấy nhãn “Chỉ trên thiết bị”; reload sẽ mất lựa chọn. Không có bước upload server.
7. Bấm “Xác thực lại” trong mục bảo mật để kiểm tra Phase B vẫn hoạt động. `npm test`, `npm run lint`, `npm run build` để kiểm tra lại code.

## BE / Infra cần xử lý và phần chưa nghiệm thu

1. Chốt cơ chế cấp BUYER/SELLER cho tài khoản tự đăng ký, và chính sách nhiều role. UI không gửi role như một cách tự cấp quyền. Hiện tài khoản chưa có role chỉ chọn được loại mong muốn trên form nhưng **không thể gửi hồ sơ**.
2. Cung cấp API upload/list giấy tờ và binding với hồ sơ; chốt loại giấy tờ, MIME, kích thước/số lượng, kiểm tra an toàn, quyền truy cập, retention và trạng thái xử lý. Chưa nghiệm thu upload/lưu trữ tài liệu thật.
3. Nên có mã lỗi ổn định cho “chưa có hồ sơ”, thay vì phải khớp chuỗi `User not found: <userId>`. FE đang cố ý nhận diện hẹp để không tạo hồ sơ khi Gateway lỗi.
4. Cần dữ liệu local VERIFIED/REJECTED do Operator xử lý đúng quy trình để nghiệm thu bằng API thật; hai tài khoản đã kiểm tra đều PENDING. Không coi unit/browser mock là bằng chứng backend đã duyệt.
5. Theme Compose vẫn đang hiển thị giao diện Keycloak mặc định trong lần kiểm tra này. Nếu cần đồng bộ theme A, Infra thực hiện cấu hình/mount theo handoff A; C không tự thay.
6. Các màn cập nhật hồ sơ, giấy uỷ quyền, thông tin pháp lý bổ sung và workflow bổ sung hồ sơ REJECTED chưa có API trong phạm vi C này; UI không giả tạo nút lưu có hiệu lực.
7. Quyền trên UI chỉ là hướng dẫn/chặn thao tác phía client. Backend vẫn phải quyết định xác minh/role/ownership/step-up khi Phase D thực hiện API ghi. Chưa nghiệm thu nghiệp vụ tạo/ký hợp đồng vì D chưa được triển khai.

## File thêm/sửa

Sửa:

- `src/App.tsx`
- `src/features/auth/api/profile.ts` và `src/features/auth/hooks/use-my-profile.ts` — compatibility entry point dùng feature organization, giữ query key B
- `src/routes/account-page.tsx`
- `src/routes/component-showcase.tsx`
- `src/routes/listing-detail-page.tsx` — chỉ trạng thái/khả năng CTA, không tích hợp nghiệp vụ D
- `src/shared/ui/file-upload.tsx`

Thêm:

- `src/features/organization/api/{profile,documents}.ts`
- `src/features/organization/hooks/{use-organization-profile,use-organization-permissions}.ts`
- `src/features/organization/components/{organization-layout,organization-form,organization-profile,organization-documents,organization-guards,profile-state,verification-banner}.tsx`
- `src/routes/organization-onboarding-page.tsx`
- `tests/organization.test.ts`
- `PHASE-C-HANDOFF.md`

Không sửa `.env.local`, dependencies, package-lock, auth store/adapter, Axios client, step-up hook, theme A hoặc `pending-approval-card.tsx`. Prompt untracked ban đầu được giữ nguyên. Tất cả thay đổi C chưa stage/commit; không push/PR/reset.
