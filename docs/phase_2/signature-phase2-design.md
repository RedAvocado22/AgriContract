---
name: signature-phase2-design
description: "Chữ ký điện tử buyer/seller + xác minh thẩm quyền đại diện — chi tiết hoá services.md mục 9. Nguồn: design session 02-03/07/2026, cập nhật 08/07/2026 (payload contract.signed mở rộng, escrow-service thêm làm consumer)."
status: DESIGNED — chưa code.
metadata:
    type: design
    phase: 2
    extends: "services.md § Security gaps (mục 9)"
    related: "hash-chain-phase2-design.md §4; user-service-phase2-design.md §3-4; notification-service-phase2-design.md §2.2/§4"
---

## 1. Bối cảnh & Scope

`services.md` mục 9 chốt khái niệm chung: chữ ký số chứng minh "đúng người đã ký", KYC chứng minh "người đó có thẩm quyền đại diện" — kết hợp cả hai mới cover hoàn toàn non-repudiation. Doc này chốt phần **thiết kế cụ thể**: cấu trúc bản ghi `Signature`, verify logic chạy lúc `sign()`, và cơ chế xác minh thẩm quyền đại diện gắn liền với nó — áp dụng cho cả Buyer lẫn Seller.

**Không detail lại** `reportHash` (`inspection-service`, mục 6 `services.md`) — INSPECTOR là third-party actor, khác luồng identity với buyer/seller, để dành session riêng.

---

## 2. Căn cứ pháp lý — Tier chữ ký điện tử

**Chốt (03/07/2026):** Điều 22 Luật GDĐT 2023 phân loại chữ ký điện tử thành 3 loại có tên riêng — chữ ký điện tử chuyên dùng (chỉ tổ chức tự tạo lập cho hoạt động riêng), chữ ký số công cộng, chữ ký số chuyên dùng công vụ. Không loại nào trong 3 loại này cho phép cá nhân tự tạo lập chữ ký điện tử của riêng mình. Buyer/seller trên AgriContract là cá nhân đại diện HTX/doanh nghiệp — nên approach JWT (`sub` + `auth_time` + `signedContentHash`) rơi vào **base-tier chữ ký điện tử** theo định nghĩa mở đầu Điều 22, không phải 1 trong 3 loại có tên.

**Điều 23 — 2 vế không đối xứng:**

- Khoản 1: chữ ký điện tử (mọi dạng, kể cả JWT) **không bị phủ nhận** giá trị pháp lý chỉ vì là điện tử.
- Khoản 2: chỉ chữ ký điện tử chuyên dùng bảo đảm an toàn hoặc chữ ký số (có chứng thư từ CA được cấp phép) mới có giá trị pháp lý **tương đương chữ ký tay**.

JWT đạt khoản 1, không đạt khoản 2. **Không đồng nghĩa hợp đồng vô hiệu** — Điều 34-36 (giá trị pháp lý hợp đồng điện tử) tách biệt hoàn toàn khỏi loại chữ ký, không đòi hỏi chữ ký số để hợp đồng có hiệu lực. Khác biệt thật nằm ở **gánh nặng chứng minh khi tranh chấp**: chữ ký số được luật tự động suy đoán là đúng người/đúng thời điểm; base-tier thì không — platform phải tự chứng minh.

**Hệ quả thiết kế:** vì không có presumption pháp lý mặc định, `Signature` + audit trail (`hash-chain-phase2-design.md`) đóng vai trò **bằng chứng bù đắp** cho phần luật không tự cho — không phải lớp phụ, mà là lý do tồn tại của toàn bộ thiết kế này.

---

## 3. Domain Model — `Signature`

Value object, không phải aggregate riêng — sống/chết cùng `Contract`, không có state machine hay nghiệp vụ độc lập (cùng nguyên tắc Vernon đã áp cho `ProductPlot`, xem `product-phase2-design.md` §2.1).

| Field               | Loại                     | Ghi chú                                                                                            |
| ------------------- | ------------------------ | -------------------------------------------------------------------------------------------------- |
| `signatureId`       | UUID                     |                                                                                                    |
| `contractId`        | UUID (FK)                |                                                                                                    |
| `signerRole`        | Enum (`BUYER`, `SELLER`) |                                                                                                    |
| `signerUserId`      | UUID                     | = claim `sub` trong JWT — định danh ổn định, không đổi theo `preferred_username`                   |
| `authTime`          | Timestamp                | = claim `auth_time`, lấy từ JWT ngay sau step-up re-auth (`prompt=login`) trigger lúc bấm nút "Ký" |
| `signedAt`          | Timestamp                | Server time lúc gọi `sign()`                                                                       |
| `signedContentHash` | VARCHAR(64)              | Từ `services.md` mục 4 — cùng `ContractTerms` hash cho cả 2 chữ ký của 1 hợp đồng                  |
| `ipAddress`         | VARCHAR                  | Bằng chứng phụ, không dùng làm điều kiện chính                                                     |

`UNIQUE(contractId, signerRole)` — mỗi bên ký đúng 1 lần cho 1 hợp đồng. `Contract` chuyển `SIGNED` khi tồn tại đủ 2 dòng (`BUYER` + `SELLER`) cho cùng `contractId`, không cần thêm cờ `buyerSigned`/`sellerSigned` riêng trên `Contract`.

**⟢ Lưu ý cho team code — schema `Signature` bị `inspection-service` mở rộng thêm (owner của phần mở rộng: `inspection-phase2-design.md` §2.1/§5):** `signerRole` enum thêm giá trị `INSPECTOR`, thêm cột `report_id`, và constraint cho nhánh INSPECTOR đổi thành `UNIQUE(report_id)` thay vì `UNIQUE(contractId, signerRole)`. Phần định nghĩa ở §3/§7 của file này chỉ mô tả nhánh `BUYER`/`SELLER` (thuộc contract-service); đừng migrate bảng `signature` chỉ dựa file này — phải đọc kèm inspection §5 để có schema đầy đủ.

---

## 4. Sole Control — Session Freshness

**Vấn đề:** JWT/password là knowledge-based, không phải possession-based như private key của chữ ký số — không tự thoả điều kiện "dữ liệu tạo chữ ký chỉ thuộc sự kiểm soát của chủ thể ký tại thời điểm ký" (Điều 22 khoản 3(c), áp cho chữ ký số, nhưng đáng dùng làm chuẩn stress-test cho base-tier). Nếu không giới hạn độ mới của phiên xác thực, `auth_time` có thể cũ hàng giờ so với `signedAt` — mất đúng ý nghĩa "chấp thuận tại đúng thời điểm ký".

**Chốt (03/07/2026):** thêm config `signatureAuthMaxAgeSeconds` trong `application.yml` — không đặt trong `ContractTerms` (không có lý do hợp đồng cà phê cần window khác hợp đồng gạo, đây là invariant kỹ thuật, cùng loại với "Escalation cap Level 1.5" ở `milestone-escrow-phase2-design.md` §8). **Giá trị đề xuất: 300 giây (5 phút).**

**Flow đúng — tách 2 đoạn, không nhầm với "đọc hợp đồng trong 5 phút":** người dùng đọc/xem lại hợp đồng bằng session đăng nhập bình thường, không giới hạn thời gian. Chỉ tới lúc bấm nút "Ký", UI mới trigger step-up re-auth. `authTime` được set ngay lúc đó. 5 phút chỉ tính từ đúng thời điểm step-up tới lúc gọi API `sign()` — quãng xác nhận cuối cùng, không phải quãng đọc hợp đồng.

**Check ở `sign()`:** `signedAt - authTime <= signatureAuthMaxAgeSeconds`. Vi phạm → reject, bắt step-up lại.

### 4.1 OTP Email — Lớp thứ 2 song song (bổ sung 03/07/2026)

**Chốt:** thêm OTP gửi qua email làm lớp thứ 2, **song song** với step-up re-auth ở trên — không thay thế. Lý do cần cả 2, không phải 1 đủ: 2 lớp chặn 2 hướng lộ khác nhau, không phải cùng 1 hướng kiểm 2 lần.

- **JWT/session bị đánh cắp** (XSS, máy dùng chung không log out) nhưng attacker không biết password → step-up re-auth chặn được (attacker không qua nổi `prompt=login`).
- **Password bị lộ** (phishing, dùng lại password cũ bị leak ở web khác) nhưng attacker không truy cập được hộp mail của buyer/seller → OTP chặn được (attacker qua được step-up bằng password đánh cắp, nhưng không đọc được mã gửi vào mail).

Chỉ khi **cả 2 cùng lúc** bị lộ (password lẫn quyền truy cập mail) attacker mới qua được toàn bộ flow — đúng nghĩa defense-in-depth, không phải kiểm trùng.

**OTP gắn liền pháp lý, không chỉ là mã xác thực:** email gửi kèm OTP kèm luôn `signedContentHash` của `ContractTerms` hiện tại — không chỉ chứng minh "đúng người vừa xác thực", mà còn "xác nhận đúng cho **nội dung cụ thể này**". Nối liền cơ chế multi-location hash + email anchor đã có (`hash-chain-phase2-design.md` §4), không phải thêm 1 mảnh rời.

**Config (`application.yml`, cùng nhóm invariant kỹ thuật với `signatureAuthMaxAgeSeconds`):**

| Config | Giá trị | Ghi chú |
|---|---|---|
| `otpLength` | 6 số | Đủ entropy (10^6) kết hợp rate limit, chuẩn ngành banking |
| `otpExpirySeconds` | 300s (5 phút) | Tách biệt hoàn toàn khỏi `signatureAuthMaxAgeSeconds` — đo từ lúc gửi OTP tới lúc nhập đúng mã, không cộng dồn vào cửa sổ password freshness |
| `otpMaxAttempts` | 5 lần | Nhập sai quá số này → khoá OTP hiện tại, bắt resend mới |
| `otpMaxResendPerHour` | 5 lần | Chống spam gửi mail |
| `otpResendCooldownSeconds` | 60s | Cooldown giữa 2 lần gửi liên tiếp |

**Lưu trữ:** OTP lưu **hash**, không lưu plaintext — cùng nguyên tắc bảo mật áp cho password, dù sống ngắn hạn.

---

## 5. Xác minh thẩm quyền đại diện (BLDS 2015 Điều 142)

**Rủi ro pháp lý:** giao dịch dân sự do người không có thẩm quyền đại diện xác lập có thể bị tuyên vô hiệu (BLDS 2015 Điều 142). HTX/doanh nghiệp là pháp nhân, nhưng người bấm nút ký trên platform phải đúng là người có thẩm quyền đại diện hợp lệ tại đúng thời điểm ký — không phải bất kỳ ai có account.

**Chốt (03/07/2026) — áp dụng đối xứng cho cả Buyer và Seller:** tài liệu gốc (`AgriContract_02_GiaiPhap_MoHinh_v5.docx`, mục Rủi ro 3) chỉ mô tả quy trình xác minh cho Seller. Điều 142 không phân biệt bên nào yếu thế hơn — rủi ro áp y hệt cho Buyer (thường là doanh nghiệp thu mua, người ký cũng cần đúng thẩm quyền đại diện). Đây không phải thiếu sót nhỏ — nếu chỉ verify Seller, mọi Buyer ký được mà chưa từng ai kiểm tra thẩm quyền, đúng lỗ hổng Điều 142 nhưng không ai note ra.

**Gate đặt lúc đăng ký tài khoản, không phải check chèn thêm sau:** cả Buyer và Seller đều phải nộp giấy đăng ký kinh doanh + xác nhận người đại diện (hoặc giấy uỷ quyền hợp lệ) trước khi tài khoản được kích hoạt đủ quyền tạo listing/ký hợp đồng. Admin duyệt thủ công — không pass, không được đăng listing/ký hợp đồng, đúng nguyên tắc gốc docx đã ghi cho Seller, mở rộng đối xứng.

**`authorizationExpiresAt` (field trên `User`, `user-service`):** nhập tay bởi Admin lúc duyệt hồ sơ, đọc trực tiếp từ ngày hết hạn ghi trên giấy uỷ quyền/giấy tờ đã nộp — **không phải hằng số cố định do platform tự áp** (kiểu "6 tháng cho mọi người"). Lý do: giấy tờ khác nhau có hạn khác nhau (1 năm, 2 năm, theo nhiệm kỳ, hoặc vô thời hạn) — hardcode 1 con số chung gây 2 lỗi ngược nhau: chặn nhầm người có giấy còn hiệu lực dài hơn mốc cứng, hoặc vẫn cho ký khi giấy đã hết hạn thật ngoài đời (đúng lỗ hổng Điều 142, chỉ trễ hơn thay vì biến mất). `NULL` nếu giấy ghi vô thời hạn.

**Khác tầng với §4 — không gộp chung 1 check:** freshness (§4) đo bằng phút, xác nhận "vừa mới xác thực xong". Authorization expiry (§5) đo bằng tháng/năm, xác nhận "thẩm quyền đại diện còn hiệu lực". Hai câu hỏi độc lập, có thể pass cái này fail cái kia.

**Fail-closed by default:** tài khoản chưa qua duyệt hồ sơ → không tạo được listing, không ký được hợp đồng. Không có trạng thái "account tồn tại nhưng chưa verify vẫn ký được" — verify là điều kiện cần để account có quyền ký, không phải cờ riêng kiểm tra lại mỗi lần `sign()`.

---

## 6. Verify logic tại `sign()` (cập nhật 03/07/2026 — thêm OTP layer, §4.1)

Tách 2 bước, không còn 1 lệnh `sign()` duy nhất — buyer/seller phải qua đủ cả step-up lẫn OTP mới thật sự ký:

```
InitiateSign(contractId, signerRole, jwt):
  1. authorizationExpiresAt = User.findById(signerUserId).authorizationExpiresAt
     Nếu authorizationExpiresAt != NULL AND authorizationExpiresAt <= now() → REJECT
     (§5 — thẩm quyền đại diện còn hiệu lực)

  2. authTime = jwt.claims["auth_time"]
     Nếu now() - authTime > signatureAuthMaxAgeSeconds → REJECT, yêu cầu step-up lại
     (§4 — phiên xác thực đủ mới)

  3. Sinh OTP (otpLength số), hash, INSERT signature_otp
     (sent_at=now(), expires_at=now()+otpExpirySeconds)

  4. Gọi sync `POST /internal/v1/notifications/otp-email` với otpId làm requestId,
     OTP + signedContentHash = SHA256(ContractTerms hiện tại). Chỉ trả thành công
     khi notification-service xác nhận mail provider đã nhận request; lỗi → trả lỗi,
     không nói "OTP đã gửi" và không có background retry gửi muộn.

  5. Trả về "OTP đã gửi" — CHƯA tạo Signature, chưa chuyển Contract state.
     Retry cùng otpId là idempotent; resend thật tạo OTP/otpId mới theo cooldown.

VerifyOtpAndSign(contractId, signerRole, otpCode):
  1. Lấy signature_otp mới nhất chưa verified cho (contractId, signerRole)
     now() > expires_at → REJECT, yêu cầu resend
     attempt_count >= otpMaxAttempts → REJECT, khoá, yêu cầu resend OTP mới
     hash(otpCode) != otp_hash → attempt_count += 1, REJECT

  2. Khớp → verified_at = now()

  3. INSERT Signature (contractId, signerRole, signerUserId=jwt.sub,
     authTime (từ InitiateSign bước 2), signedAt=verified_at, signedContentHash, ipAddress)
     — UNIQUE(contractId, signerRole) tự chặn ký 2 lần cho cùng 1 bên

  4. Nếu tồn tại đủ 2 dòng Signature (BUYER + SELLER) cho contractId
     → Contract.transitionTo(SIGNED)

  5. Publish Contract.signedContentHash vào audit-service (hash-chain-phase2-design.md §2.2)
     + publish `notification.contract_anchor_requested` cho notification-service,
       payload `{eventId, contractId, recipients[{userId,email,role}],
       contractTermsSnapshot, signedContentHash, signedAt}`. contract-service lấy email qua
       InternalUserInfo đã dùng cho KYC; domain event `contract.signed` ở bước 6 không bị
       nhồi snapshot/email chỉ để phục vụ notification.

  6. Publish `contract.signed` (RabbitMQ, domain event — mới 06/07/2026, payload mở
     rộng 08/07/2026) — tách biệt với việc push hash ở bước 5 (đó là ghi vào audit
     chain, không phải domain event cho consumer khác nghe). Payload mang thêm
     `buyerDepositAmount`/`sellerDepositAmount` (đã tính sẵn, không phải rate thô) —
     escrow-service (mới, 08/07/2026) cũng consume event này để biết số tiền cần khoá,
     bên cạnh analytics-service. Payload + consumer đầy đủ — chi tiết catalog ở
     milestone-escrow-phase2-design.md §7.2.
```

---

## 7. Database

Additive trên `contract_db` (Signature) và `user_db` (authorization fields) — không sửa bảng `contract` hay `user` cốt lõi:

```sql
CREATE TABLE signature (
    signature_id         UUID PRIMARY KEY,
    contract_id          UUID NOT NULL REFERENCES contract(contract_id),
    signer_role           VARCHAR(10) NOT NULL,   -- 'BUYER' | 'SELLER' (inspection-service thêm 'INSPECTOR' + cột report_id, xem inspection §5 — schema đầy đủ ở đó)
    signer_user_id         UUID NOT NULL,
    auth_time              TIMESTAMP NOT NULL,
    signed_at              TIMESTAMP NOT NULL,
    signed_content_hash    VARCHAR(64) NOT NULL,
    ip_address              VARCHAR(45),
    UNIQUE (contract_id, signer_role)
);

-- OTP layer (bổ sung 03/07/2026, §4.1) — bảng riêng, vòng đời khác Signature
CREATE TABLE signature_otp (
    otp_id          UUID PRIMARY KEY,
    contract_id      UUID NOT NULL REFERENCES contract(contract_id),
    signer_role       VARCHAR(10) NOT NULL,
    user_id           UUID NOT NULL,
    otp_hash          VARCHAR(64) NOT NULL,     -- hash, không lưu plaintext
    sent_at           TIMESTAMP NOT NULL,
    expires_at        TIMESTAMP NOT NULL,       -- sent_at + otpExpirySeconds
    verified_at       TIMESTAMP NULL,
    attempt_count     INT NOT NULL DEFAULT 0
);

-- user_db, bổ sung vào User aggregate hiện có:
ALTER TABLE app_user ADD COLUMN authorization_expires_at TIMESTAMP NULL;
-- NULL = giấy uỷ quyền vô thời hạn, hoặc chưa qua duyệt (fail-closed ở use case, không phải ở DB constraint)
```

---

## 8. Out of Scope / Known Limitation (có chủ đích, không phải thiếu sót)

**WebAuthn/passkey (sole control mạnh hơn):** đóng đúng gap của §4 theo nghĩa kỹ thuật — private key sinh và giữ trong secure enclave thiết bị, không transmit qua network, thoả sole-control theo nghĩa đen thay vì "trust password". **Không** đổi tier pháp lý ở §2 — vẫn thiếu điều kiện "phải được bảo đảm bởi chứng thư chữ ký số" (Điều 22 khoản 3(đ)), một certificate chỉ do CA được Bộ TT&TT cấp phép mới phát hành được, platform không tự phong được. Giá trị thật của WebAuthn: nâng chất lượng bằng chứng khi cần chứng minh trong tranh chấp, không nâng presumption pháp lý mặc định. Ghi nhận làm hướng nâng cấp tương lai, chưa thiết kế chi tiết trong doc này.

**`reportHash`/INSPECTOR:** INSPECTOR là third-party actor, không qua cùng luồng KYC buyer/seller ở §5 — "same treatment" không áp trực tiếp được, cần session riêng xác định lại từ đầu.

**Chi tiết nghiệp vụ buyer KYC (form nào, giấy tờ nào cụ thể):** doc này chỉ chốt **nguyên tắc đối xứng** với Seller (§5) — checklist giấy tờ cụ thể cho từng loại hình doanh nghiệp buyer (TNHH, cổ phần, hộ kinh doanh...) là việc tra quy định nghiệp vụ/pháp lý thật khi soạn form đăng ký, không phải quyết định thiết kế treo ở tầng này — điền chi tiết lúc làm màn hình KYC, không block phần còn lại của design.

---

## 9. Status — Signature Design

**Chốt (03/07/2026):** `Signature` là value object trong `Contract` aggregate, `UNIQUE(contractId, signerRole)` thay cho 2 cờ riêng trên `Contract`. Base-tier chữ ký điện tử theo Điều 22-23 Luật GDĐT 2023 — không bị phủ nhận giá trị pháp lý, không tương đương chữ ký tay, hợp đồng vẫn có hiệu lực đầy đủ, khác biệt nằm ở gánh nặng chứng minh khi tranh chấp. `signatureAuthMaxAgeSeconds` = 300s (`application.yml`) cho session freshness. Xác minh thẩm quyền đại diện (BLDS 2015 Điều 142) áp dụng đối xứng Buyer/Seller, gate lúc đăng ký tài khoản, `authorizationExpiresAt` nhập tay từ giấy tờ thật — không hardcode. Fail-closed mặc định khi chưa qua duyệt. WebAuthn và `reportHash`/INSPECTOR để ngoài phạm vi, có chủ đích.

**Chốt bổ sung (03/07/2026) — OTP Email, lớp thứ 2 song song:** không đổi tier pháp lý (đã xác nhận qua Điều 22-23 — cả `chữ ký điện tử chuyên dùng bảo đảm an toàn` lẫn `chữ ký số` đều không đạt được bằng cơ chế xác thực mạnh hơn, 1 đường chỉ dành cho tổ chức đăng ký với Bộ TT&TT, đường kia cần chứng thư CA), chỉ nâng chất lượng bằng chứng — thêm possession-based factor (đang truy cập được hộp mail tại đúng thời điểm) song song với knowledge-based factor sẵn có (password step-up). Flow tách `InitiateSign`/`VerifyOtpAndSign` (§6), bảng `signature_otp` riêng (§7), config `otpLength`=6, `otpExpirySeconds`=300s, `otpMaxAttempts`=5, `otpMaxResendPerHour`=5, `otpResendCooldownSeconds`=60s (§4.1). Email OTP kèm `signedContentHash` — vừa xác thực người, vừa gắn đúng nội dung. WebAuthn/sinh trắc học vẫn để ngoài phạm vi — lý do không đổi (không có đường nào tới tier pháp lý cao hơn qua cơ chế xác thực kỹ thuật), OTP chọn vì rẻ hơn, tận dụng hạ tầng SendGrid sẵn có, không thêm third-party dependency mới.

**Cập nhật (08/07/2026, phát sinh từ rà soát cross-service A1):** payload `contract.signed` (bước 6, §6) mở rộng thêm `buyerDepositAmount`/`sellerDepositAmount` — escrow-service (không chỉ analytics-service) giờ cũng consume event này để biết số tiền cần khoá lúc `SIGNED`. Không đổi gì ở logic ký/verify của file này — chỉ đổi payload của event đã publish sẵn.

Signature — **đóng session, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.**

---

_Design session: 02-03/07/2026 · Bổ sung OTP Email: 03/07/2026 · Chưa code · Chưa đưa vào Architecture/SDS/TechnicalSpec chính thức._
