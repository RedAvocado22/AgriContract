# AgriContract — Giai đoạn A: Keycloakify Theme

Theme đăng nhập độc lập, tên `agricontract`, Keycloakify **11.16.0**, React, Vite và Tailwind v4. Khởi tạo từ [keycloakify-starter chính thức](https://github.com/keycloakify/keycloakify-starter/tree/3b648ea9f1a5dbde21061cdc6892a8a8d451c3f9), giữ LICENSE của starter.

## Phạm vi

Chỉ tùy biến `login.ftl`, `register.ftl`, `login-reset-password.ftl`, `login-update-password.ftl`, `info.ftl`, `error.ftl`. Các trang khác dùng `DefaultPage` và `Template` của Keycloakify; CSS tùy biến chỉ được nạp khi mở trang tùy biến. Không tạo account theme hay email theme.

- Đăng nhập, ghi nhớ phiên, liên kết đăng ký/quên mật khẩu tuân theo các cờ và URL do Keycloak cung cấp. Identity provider chỉ xuất hiện nếu realm thực sự cấu hình; không có nút VNeID giả.
- Đăng ký chỉ thu email, mật khẩu và nhập lại mật khẩu. Không tạo hồ sơ tổ chức, không gán BUYER/SELLER, không giả lập trạng thái VERIFIED.
- Quên mật khẩu dùng liên kết email của Keycloak. Không dựng OTP/SMS 6 số từ mockup vì backend xác thực hiện tại không cung cấp luồng đó.
- Mật khẩu được gửi bằng HTML POST tới action URL của Keycloak, giữ các tham số phiên do server tạo. Không password grant, không lưu mật khẩu/token, không gọi API nghiệp vụ.
- Form dùng react-hook-form + zod cho kiểm tra rỗng/email/khớp mật khẩu; Keycloak quyết định chính sách mật khẩu và xác thực cuối cùng.
- Thông báo server giữ nguyên ý nghĩa và được sanitize khi render HTML. Không tự báo gửi email hoặc đổi mật khẩu thành công trước phản hồi server.
- Token `@theme`, Be Vietnam Pro, JetBrains Mono và bốn primitive được sao chép từ app, giữ nguyên class. TextInput chỉ bổ sung ref, required và liên kết lỗi cho khả năng truy cập; icon trang trí có `aria-hidden`. Font được đóng gói cục bộ trong JAR.
- Tiếng Việt qua i18n Keycloakify cho sáu trang và thông điệp email/password phổ biến. Realm phải chọn `vi` để cả thông báo server dùng tiếng Việt. Trang ngoài phạm vi giữ bản dịch mặc định; nội dung email hiện vẫn là email mặc định của Keycloak.
- Không đưa số liệu giả 2.400 hợp đồng/98,2% từ mockup lên trang đăng nhập.

**Không triển khai B/C/D.** App `frontend/` và các form mock hiện có giữ nguyên; chưa chuyển route app sang Keycloak. `pending-approval-card.tsx` không bị thay đổi.

## Cài và build

Yêu cầu Node.js 22+, Java và Maven trên PATH; Docker đang chạy khi kiểm thử Keycloak.

```sh
cd frontend-keycloak-theme
npm ci
npm run lint
npm run build
npx keycloakify build
```

Hoặc `npm run build-keycloak-theme` để chạy build giao diện rồi đóng gói liên tiếp. Artifact cho **Keycloak 24.0.4**:

```text
dist_keycloak/keycloak-theme-for-kc-22-to-25.jar
```

Không commit `dist/`, `dist_keycloak/`, `node_modules/`, `.keycloakify/`, ảnh kiểm thử hoặc thông tin phiên kiểm thử.

Trên máy Windows đang dùng, Maven có trong wrapper cache nhưng không nằm trên PATH. Có thể thêm thư mục `bin` của Maven sẵn có vào PATH của **phiên PowerShell hiện tại** trước khi chạy `npx keycloakify build`; không cần sửa backend hay cấu hình hệ thống.

## Xem trước giao diện

```sh
npm run dev
```

Mở `http://localhost:5174/?page=login.ftl`. Thay `page` bằng một trong sáu tên FTL ở trên; thêm `&error=1` để xem banner lỗi. Preview có nhãn rõ ràng, dùng fixture của Keycloakify, ngăn gửi form và chỉ được nạp khi `import.meta.env.DEV`. Đây không phải bằng chứng nghiệp vụ backend đã hoàn tất.

## Kiểm thử Keycloak riêng

Không dùng realm/volume/service của `backend/docker-compose.yml`.

```sh
# MailHog riêng, chỉ nhận email kiểm thử cục bộ; UI ở cổng 8026.
docker run -d --name agricontract-theme-mailhog -p 127.0.0.1:8026:8025 -p 127.0.0.1:1026:1025 mailhog/mailhog:v1.0.1

npm run prepare-local-realm
npx keycloakify start-keycloak
```

`npm run start-keycloak` gộp hai lệnh cuối. Keycloakify chạy **24.0.4**, container `keycloak-keycloakify`, cổng **8181**, realm **myrealm**. Admin và tài khoản thử theo starter: `admin/admin`, `testuser/password123`; chỉ dùng cục bộ. Lệnh chuẩn của Keycloakify: [tài liệu](https://docs.keycloakify.dev/testing-your-theme/inside-of-keycloak).

Script `prepare-local-realm.mjs` tạo `.keycloakify/realm-kc-24.json` từ fixture chính thức: bật tiếng Việt, email-as-username, đăng ký/reset, bỏ trường demo của starter, SMTP `host.docker.internal:1026`, thêm client `theme-smoke`. Nếu file đã có, script giữ nguyên cấu hình kiểm thử đã lưu. Không đưa fixture này vào realm thật.

Chạy Vite ở cổng 5174 rồi mở luồng smoke test:

```text
http://localhost:8181/realms/myrealm/protocol/openid-connect/auth?client_id=theme-smoke&redirect_uri=http%3A%2F%2Flocalhost%3A5174%2F&response_type=code&scope=openid&state=theme-smoke
```

Callback chỉ dùng quan sát Keycloak trả authorization code, không đổi code lấy token và không tích hợp auth vào app. Có thể dùng ứng dụng thử do `start-keycloak` in ra để kiểm tra thủ công.

## Kiểm chứng ngày 08/11/2026

| Kiểm tra                                                  | Kết quả                                                                                                       |
| --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| Theme `npm run lint`, `npm run build`                     | Pass; Vite có cảnh báo annotation trong dependency zod, không lỗi build                                       |
| `npx keycloakify build`                                   | Pass; JAR target 22–25, dùng được với 24.0.4                                                                  |
| `npx keycloakify start-keycloak`                          | Chạy thật Keycloak 24.0.4 ở 8181, không thay đổi Keycloak dự án 8180                                          |
| Đăng nhập `testuser`                                      | Sai mật khẩu: lỗi tiếng Việt; đúng: trả authorization code; rememberMe gửi `on`                               |
| Đăng ký `theme-smoke@agricontract.test`                   | Kiểm tra nhập lại không khớp; mật khẩu ngắn bị server từ chối bằng tiếng Việt; tạo tài khoản thật             |
| Đăng ký `theme-final@agricontract.test`                   | Email + hai ô mật khẩu tạo tài khoản và trả authorization code                                                |
| Quên/đổi mật khẩu `theme-final@agricontract.test`         | Email thực đến MailHog cục bộ; mở liên kết, đổi mật khẩu; mật khẩu cũ bị từ chối, mật khẩu mới đăng nhập được |
| Đặt lại mật khẩu từ phiên trình duyệt khác với `testuser` | Trang `info.ftl` thật hiển thị thông báo thành công tiếng Việt                                                |
| Client không tồn tại                                      | Trang `error.ftl` thật, HTTP 400, thông báo tiếng Việt                                                        |
| Sáu trang preview, 1440/390/320 px                        | Không tràn ngang; font Be Vietnam Pro, `lang=vi`; ảnh trong `output/playwright/`                              |
| Trang đổi mật khẩu thật, 390 px                           | Hiển thị đúng, gửi `logout-sessions=on`                                                                       |
| App `frontend/`: lint/build                               | Exit 0; lint có 4 cảnh báo Fast Refresh có sẵn; build có cảnh báo chunk >500 kB. Không sửa ngoài phạm vi      |

Các tài khoản trên chỉ tồn tại trong realm kiểm thử `myrealm`; không phải buyer/seller nghiệp vụ. Bước điều khoản mặc định của starter đã được tắt riêng trong realm thử để kiểm tra Giai đoạn A. Không chấp nhận điều khoản thay người dùng.

**Chưa kiểm thử:** cài JAR vào compose thật, `buyer1/seller1` trên realm agricontract, email SMTP thật, VNeID/IdP, WebAuthn/passkey, CAPTCHA/Terms đăng ký, Safari/iOS/thiết bị thật, thu hồi mọi phiên trên nhiều thiết bị. Chỉ xác nhận checkbox thu hồi phiên gửi đúng giá trị, chưa chứng minh đầy đủ việc thu hồi. Các trường hợp realm bật CAPTCHA/Terms hoặc yêu cầu thêm trường bắt buộc sẽ không cho gửi form đăng ký tùy biến; cần thống nhất phạm vi trước khi mở rộng.

## BE / Infra cần thực hiện

1. Mount JAR đã build vào `/opt/keycloak/providers/keycloak-theme-for-kc-22-to-25.jar` của service `keycloak`; khởi động lại/build image theo quy trình Infra. Theme agent không sửa compose.
2. Realm `agricontract`: `loginTheme=agricontract`, `registrationAllowed=true`, `resetPasswordAllowed=true`, `registrationEmailAsUsername=true`, `loginWithEmailAllowed=true`, `duplicateEmailsAllowed=false`. Bật `rememberMe` nếu cần hiện checkbox.
3. User Profile của realm: email bắt buộc; bỏ yêu cầu firstName/lastName và các thuộc tính tổ chức ở đăng ký. Không tự điền dữ liệu giả để vượt validation. Xác nhận registration flow dùng mật khẩu thông thường; CAPTCHA/Terms/WebAuthn cần kiểm thử và phạm vi riêng nếu bật.
4. `internationalizationEnabled=true`, `supportedLocales=["vi"]`, `defaultLocale=vi`. Khi test tài khoản cũ có locale/cookie tiếng Anh, chọn lại `vi`/dùng phiên mới. Bản dịch tùy biến realm có thể ghi đè bản dịch theme; cần rà soát cùng Infra.
5. SMTP của **realm** trong compose: host `mailhog`, port `1025`, địa chỉ gửi phù hợp; môi trường local tắt auth/TLS nếu MailHog không bật. Đây khác SMTP của notification-service. UI mail dự án ở 8025. Cấu hình này không phải SMTP riêng 1026 của smoke test.
6. Cấu hình/chốt password policy ở realm; theme không tự áp một chính sách khác server. Nếu yêu cầu email tiếng Việt, cần email theme hoặc bản dịch email riêng, ngoài sáu login pages của A.
7. Chốt cơ chế cấp role cho tài khoản tự đăng ký trước Giai đoạn C. Theme không gán role, không tạo hồ sơ, không coi tài khoản mới là VERIFIED. App vẫn cần Giai đoạn B để chuyển sang authorization-code + PKCE và bảo đảm `auth_time`.

## File bàn giao

Tất cả file mới thuộc `frontend-keycloak-theme/`:

- Cấu hình: `.gitignore`, `LICENSE`, `README.md`, `package.json`, `package-lock.json`, `index.html`, `vite.config.ts`, `tsconfig.json`, `tsconfig.node.json`.
- Khởi chạy: `src/main.tsx`, `src/kc.gen.tsx` (Keycloakify sinh), `src/vite-env.d.ts`, `src/preview.ts`, `scripts/prepare-local-realm.mjs`.
- Theme: `src/index.css`, `src/login/{KcContext.ts,KcPage.tsx,KcPageStory.tsx,AuthLayout.tsx,auth.css,form.tsx,i18n.ts,i18n.vi.ts,messages.ts}`.
- Sáu trang: `src/login/pages/{Login,Register,ResetPassword,UpdatePassword,Info,Error}.tsx`.
- Sao chép từ app: `src/shared/lib/utils.ts`, `src/shared/ui/{button,text-input,checkbox,banner}.tsx`.

Không thay đổi `frontend/`, `backend/`, `contracts/`, `docs/design/`, mockup hoặc prompt gốc. Không tạo primitive mới trong app. Không có hàm API mock `TODO(BE)` mới; chỉ có fixture giao diện phát triển được đánh dấu rõ.
