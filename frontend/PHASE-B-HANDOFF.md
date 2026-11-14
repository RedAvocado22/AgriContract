# Giai đoạn B — Đăng nhập thật trong React

Chỉ triển khai B. Theme A giữ nguyên. Không sửa backend, Compose, contracts hay tài liệu thiết kế; không triển khai hồ sơ tổ chức (C), hợp đồng/OTP/ký (D).

## Cách chạy với môi trường dự án

`npm ci`, sao chép `.env.example` thành `.env.local` nếu chưa có, rồi `npm run dev -- --port 5173 --strictPort`.

```dotenv
VITE_API_BASE_URL=http://localhost:8080
VITE_KEYCLOAK_URL=http://localhost:8180
VITE_KEYCLOAK_REALM=agricontract
VITE_KEYCLOAK_CLIENT_ID=agricontract-frontend
```

`.env.local` đã được tạo cục bộ và được Git ignore. Mọi API dùng đường dẫn đầy đủ `/api/v1/...`; base URL là origin Gateway, không thêm `/api` lần nữa. Không ghi token hay refresh token vào localStorage/sessionStorage. Keycloak JS quản lý bộ nhớ token; Zustand phản ánh danh tính và access token. Các claim phía client chỉ phục vụ UI, quyền thực thi vẫn do Gateway/backend kiểm tra.

## Chức năng

- `check-sso`, authorization code (`flow: standard`) + PKCE S256, silent SSO callback. Xử lý callback trước khi render BrowserRouter; init dùng chung promise để không gọi hai lần trong StrictMode.
- Refresh `updateToken(30)` trước request, mỗi 15 giây khi đang đăng nhập và khi adapter báo hết hạn. Refresh thất bại thì xoá phiên adapter và mở đăng nhập; không gửi request với token cũ. 401 mở đăng nhập, không tự replay request ghi.
- Zustand: authenticated/token/sub/email, sáu realm role được cho phép và `auth_time`. Không dùng `iat` thay cho `auth_time`. Xoá cache TanStack Query khi đổi người dùng/đăng xuất.
- `/login`, `/register`, `/forgot-password` là điểm vào Keycloak. Quên mật khẩu mở login với `prompt=login`; người dùng chọn liên kết “Quên mật khẩu?” của Keycloak. Adapter không có phương thức reset-credentials riêng; không tự chế URL chứa session code, không dùng `UPDATE_PASSWORD` như luồng quên mật khẩu.
- `/account` có RequireAuth, chỉ hiển thị phiên đăng nhập và đọc tên tổ chức. `/workspace` có RequireRole cho sáu role, hiện thông báo chức năng nghiệp vụ sẽ bổ sung. Tài khoản chưa có role vẫn vào `/account`, không tự gán role hoặc tạo hồ sơ.
- AccountMenu trên public nav/TopBar; AppSidebar lọc mục theo realm roles. Tên tổ chức chỉ lấy `GET /api/v1/users/me`, cache theo userId; đang tải/lỗi/404 hiển thị trung thực. Không ép sang onboarding C.
- Axios nhận envelope cũ và object trực tiếp; `parseApiError` tách mã trước dấu `:` đầu tiên; `newIdempotencyKey()` sinh UUID mới theo từng lần gọi chủ động.
- `useFreshLogin(300)` dùng biên 30 giây, xét clock skew của adapter, lưu ý định kèm actor/route/thời điểm trong sessionStorage, gọi `prompt=login,maxAge=0`. Intent sống tối đa 10 phút, chỉ khôi phục cùng người dùng/route sau xác thực mới. `consumeIntent` lấy một lần để mở lại bước UI, không tự thực thi hành vi nghiệp vụ. Logout xoá intent.
- Nút “Xác thực lại” ở `/account` kiểm tra vòng đi/về thật với action `confirm-session`. Đây chỉ là xác nhận phiên, không giả lập ký hợp đồng.

Các màn/form mock auth cũ đã được thay bằng wrapper chuyển hướng; `mock-auth.ts` không còn credential/OTP giả hay export hoạt động. Giữ nguyên `pending-approval-card.tsx`. Dữ liệu demo sẵn có ở các trang công khai/showcase ngoài auth chưa được tích hợp backend trong B. Không thêm hàm API mock `TODO(BE)`.

## Dùng step-up ở giai đoạn sau

```tsx
const { ensureFreshLogin, handleStepUpError, pendingIntent, consumeIntent } = useFreshLogin(300)
// Chỉ là ví dụ tích hợp; B chưa có màn ký hoặc API ký.
const intent = { action: "sign", contractId, step: "consent" }
if (!await ensureFreshLogin(intent)) return
try {
  await sendBusinessRequest()
} catch (error) {
  if (!await handleStepUpError(error, intent)) throw error
}
// Khi quay lại, kiểm tra pendingIntent.action/contractId, consumeIntent("sign")
// rồi mở lại đúng bước xác nhận. Không tự gửi lại sendBusinessRequest().
```

403 `STEP_UP_REQUIRED` buộc đăng nhập lại dù clock client còn cho rằng phiên mới. Caller phải truyền intent cho `handleStepUpError`; interceptor toàn cục không đoán action/contractId. Chưa gắn helper này vào nghiệp vụ D.

## Kiểm chứng ngày 08/11/2026

### Môi trường Compose dự án

- Keycloak container ở 8180 có chạy, nhưng discovery của realm `agricontract` trả **404**.
- Gateway 8080 **connection refused**. Chưa chứng minh được `/users/me`, CORS, xác thực JWT Gateway hoặc tên tổ chức thật.
- Theme A chưa được cài vào Keycloak Compose. Không thay đổi realm/service Compose để vượt qua các phụ thuộc này.
- Vì vậy **chưa đạt tiêu chí nghiệm thu B bằng `buyer1/seller1` của dự án**. Mã đã triển khai và kiểm chứng độc lập; cần kiểm thử tích hợp sau khi môi trường sẵn sàng.

### Keycloak độc lập, có theme A

Keycloak 24.0.4 ở 8181, realm `myrealm`, client riêng `agri-phase-b`, app kiểm thử ở 5176 (override env trong process, không đổi `.env.local`). Client chỉ bật code + PKCE, không password grant. Hai tài khoản fixture `phase-b-buyer@agricontract.test`, `phase-b-seller@agricontract.test` có role được cấp trong realm thử, **không phải buyer1/seller1 và không có hồ sơ nghiệp vụ**.

Đã chạy thật trong Chromium bằng Playwright:

- RequireAuth chuyển tới Keycloak với `response_type=code` và `code_challenge_method=S256`; cả BUYER và SELLER đăng nhập/quay lại app và đăng xuất về origin.
- Access token có `auth_time`, role đúng, Zustand khớp adapter; không thấy token trong localStorage/sessionStorage.
- Ép refresh qua adapter thành công; giữ `auth_time`. Đặt lifespan client thử 60 giây: bộ hẹn giờ tự refresh trả HTTP 200, vẫn ở app và giữ vai trò SELLER.
- Reload khôi phục SSO; đăng xuất xong truy cập trang bảo vệ yêu cầu đăng nhập lại.
- Step-up thật yêu cầu nhập lại mật khẩu, trở lại `/account` và hiển thị đúng bước xác nhận intent.
- `testuser` không có role app bị chặn ở `/workspace`.
- Nút đăng ký mở đúng trang registration có theme A; nút khôi phục mở login và liên kết của server dẫn tới reset-credentials. Không gửi thêm đăng ký/email trong lần kiểm tra điều hướng B này.
- `/account` 390 px không tràn ngang. Khi Gateway không chạy, hiện lỗi hồ sơ; không có tên tổ chức thay thế giả.

Kiểm tra tự động: `npm test` — **22 test pass, 3 file** — kiểm tra identity/role/freshness, ràng buộc intent, hai kiểu response, lỗi API, refresh/401/không replay, chặn gửi bearer ra origin khác, init một lần và lifecycle adapter. Đây là unit test với SDK/HTTP được stub, không phải backend integration.

`npm run lint` và `npm run build` thành công. Lint còn bốn cảnh báo Fast Refresh có sẵn ở button/banner/status-badge/toast; build còn cảnh báo bundle >500 kB. `npm audit` báo 29 advisory (1 low, 8 moderate, 19 high, 1 critical), bằng kết quả audit lockfile tại HEAD trước thay đổi; không thực hiện nâng dependency ngoài phạm vi. Vitest là dependency dev duy nhất được bổ sung.

### Chưa kiểm thử

`buyer1/seller1` với realm thật; `/users/me` thành công/404/401/403 từ Gateway thật; tên tổ chức trên TopBar/Sidebar; 403 STEP_UP_REQUIRED từ hành vi nghiệp vụ thật; SSO/logout nhiều tab, trình duyệt chặn cookie bên thứ ba, Safari/iOS; refresh khi mất mạng dài hạn; registration/reset email qua client Compose. Không có claim hoàn tất C/D hoặc nghiệp vụ backend.

## BE / Infra

1. Cung cấp realm `agricontract` tại 8180 và Gateway tại 8080; cấu hình issuer/audience và CORS cho origin `http://localhost:5173`.
2. Client public `agricontract-frontend`: Standard Flow bật, PKCE S256; redirect `http://localhost:5173/*` gồm `silent-check-sso.html`, Web Origins `http://localhost:5173`, post-logout redirect `http://localhost:5173`. Không cần client secret trong frontend.
3. Đảm bảo access token có `sub`, `email`, `realm_access.roles`, `auth_time` trong code flow, kể cả sau refresh. Không biến `iat` thành auth_time. Xác nhận timeout phiên/refresh token phù hợp.
4. Cài theme A và cấu hình registration/reset/SMTP/locale theo `frontend-keycloak-theme/README.md`. App dùng theme đang được realm chọn; không thể ép cài theme qua keycloak-js.
5. Chuẩn bị tài khoản buyer1/seller1, role và hồ sơ thật cho `/api/v1/users/me`; sau đó chạy checklist đăng nhập → reload → refresh → tên tổ chức → step-up → đăng xuất. Tài khoản tự đăng ký chưa có role vẫn là phụ thuộc cần chốt trước C.

## Danh sách file

Sửa: `.env.example`, `package.json`, `package-lock.json`, `src/App.tsx`, `src/main.tsx`, `src/routes/auth-page.tsx`, `src/features/auth/components/{login-form,register-form,forgot-password-form}.tsx`, `src/features/auth/lib/mock-auth.ts`, `src/shared/ui/{public-nav,top-bar,app-sidebar}.tsx`.

Thêm: `PHASE-B-HANDOFF.md`, `public/silent-check-sso.html`, `src/routes/account-page.tsx`, `src/shared/auth/{auth-store,keycloak,step-up}.ts`, `src/shared/api/{client,errors,query-client}.ts`, `src/features/auth/api/profile.ts`, `src/features/auth/hooks/{use-my-profile,use-fresh-login}.ts`, `src/features/auth/components/{account-menu,auth-action,auth-guards}.tsx`, `tests/{auth,client,keycloak}.test.ts`.

File local ignored: `.env.local`, `node_modules`, `dist`. Không commit/push/stage/reset. Branch triển khai: `feat/fe-keycloak-auth`; prompt untracked ban đầu được giữ nguyên.

Tham khảo API adapter: [Keycloak JavaScript adapter](https://www.keycloak.org/securing-apps/javascript-adapter).
