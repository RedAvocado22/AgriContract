---
name: contract-document-consent-phase2-design
description: "Chuẩn hoá hợp đồng cà phê Việt Nam, bằng chứng đồng ý theo từng hành vi và vòng đời tài liệu hợp đồng Phase 2."
status: DESIGNED — chưa code.
metadata:
  type: design
  phase: 2
  related: "signature-phase2-design.md; milestone-escrow-phase2-design.md; file-service-phase2-design.md; hash-chain-phase2-design.md; notification-service-phase2-design.md"
---

## 1. Phạm vi đã chốt

Phase 2 chỉ hỗ trợ giao dịch nội địa Việt Nam và chỉ hỗ trợ hàng hoá `COFFEE`. Mọi API/schema tạo mới phải reject commodity khác; dữ liệu legacy đã ký chỉ được đọc/vận hành theo snapshot cũ, không được dùng để tạo hợp đồng Phase 2 mới.

AgriContract dùng một mẫu hợp đồng mua bán cà phê B2B tiếng Việt có version và một phụ lục chất lượng cà phê có version. Không tuyên bố mẫu này “đạt ISO 27001” hoặc “đạt ANSI”: ISO/IEC 27001 là chuẩn hệ thống quản lý an toàn thông tin, không phải chuẩn nội dung hợp đồng; ANSI không phải một mẫu hợp đồng quốc tế duy nhất. Các chuẩn đo/thử chất lượng bên ngoài chỉ được ghi vào profile sau khi nhóm chọn đúng tiêu chuẩn và phương pháp thử.

## 2. Giá trị pháp lý và ranh giới tuyên bố

Cơ chế ký vẫn là chữ ký điện tử tầng cơ sở theo `signature-phase2-design.md`: định danh tài khoản, thẩm quyền đại diện, step-up authentication, OTP, hash nội dung bất biến, timestamp và audit trail. Checkbox là bằng chứng thể hiện ý chí đối với đúng hành vi và đúng nội dung đang hiển thị; nó không phải vân tay sinh trắc học, không phải “điểm chỉ” vật lý và không tự nâng chữ ký thành chữ ký số có chứng thư CA.

Hệ thống phải chứng minh được đồng thời:

1. ai thực hiện hành vi;
2. người đó có quyền đại diện tại thời điểm thực hiện;
3. họ đã được hiển thị đúng statement và đúng nội dung;
4. họ chủ động tick checkbox và hoàn tất mức xác thực tương ứng;
5. nội dung, thời điểm và chuỗi audit không bị sửa sau đó.

## 3. Hồ sơ mẫu hợp đồng

```text
ContractTemplateProfile:
  contractTemplateCode = VN_COFFEE_B2B
  contractTemplateVersion
  contractTemplateHash
  coffeeQualityProfileVersion
```

`contractTemplateCode`, `contractTemplateVersion`, `contractTemplateHash` và `coffeeQualityProfileVersion` là một phần của `ContractTermsSnapshot`, được canonicalize theo RFC 8785 trước khi tính `signedContentHash`. Thay bất kỳ version/hash nào phải làm đổi `signedContentHash` và bắt hai bên xem/ký lại.

Mẫu dùng mô hình hybrid:

- Điều khoản pháp lý cố định nằm trong template có version: chủ thể, giải thích thuật ngữ, luật áp dụng, hiệu lực, thông báo, bất khả kháng, vi phạm, chấm dứt và giải quyết tranh chấp.
- Điều khoản thực thi nằm trong field có cấu trúc: giá, khối lượng, milestone, giao nhận, chất lượng, inspection, đặt cọc, penalty và remedy.
- Điều khoản hybrid có cả câu chữ render cho người đọc và rule cấu trúc cho hệ thống. Text và rule phải cùng lấy từ một snapshot/version; không được để PDF nói một ý còn engine tính một ý khác.

Chưa chốt hai lựa chọn phải để hiển thị rõ là cấu hình/pháp lý cần duyệt, không tự bịa mặc định: bộ tiêu chuẩn/phương pháp thử chất lượng cà phê bên ngoài; toà án Việt Nam hay trọng tài thương mại trong nước là cơ quan giải quyết tranh chấp.

## 4. Bằng chứng đồng ý theo từng hành vi

```text
ConsentEvidence:
  consentId
  actionType
  subjectType
  subjectId
  actorUserId
  actorRole
  statementCode
  statementVersion
  statementHash
  presentedContentHash
  consentedAt
  correlationId
```

`statementHash` là SHA-256 của đúng câu checkbox đã hiển thị. `presentedContentHash` gắn consent với nội dung mà người dùng đang nhìn; server không tin hash do client tự quyết mà phải đối chiếu với hash hiện hành của subject. Consent record append-only, được ghi cùng transaction của hành vi hoặc transaction chuẩn bị challenge tương ứng.

| Nhóm hành vi | Ví dụ | Mức bằng chứng |
|---|---|---|
| Ký/cam kết rủi ro cao | ký hợp đồng, xác nhận mutual replacement, mutual termination | Checkbox bắt buộc + session step-up còn hạn + OTP bind subject/action/content hash |
| Xác nhận vận hành có hệ quả | khai cân giao, xác nhận nhận hàng sạch, flag hàng, chấp nhận giá trong band | Checkbox bắt buộc + authenticated session + ownership/role check |
| Quyết định quản trị | resolve breach/force majeure | Statement xác nhận quyết định + authenticated privileged session + maker-checker nếu owner design yêu cầu |

Statement phải đặc thù theo hành vi, không dùng một câu “Tôi đồng ý” chung. Ví dụ xác nhận nhận hàng phải nói rõ số lượng/evidence đang xác nhận và ghi rõ việc nhận hàng không mặc nhiên từ bỏ quyền kiểm tra chất lượng, khiếu nại hoặc inspection còn hiệu lực theo hợp đồng.

Client gửi `statementVersion` và `presentedContentHash`; server resolve `statementCode`, tính lại `statementHash`, kiểm tra version đang active và kiểm tra `presentedContentHash` đúng subject hiện hành. Sai action, subject, version hoặc hash đều reject và không tạo side effect.

## 5. Vòng đời tài liệu

```text
ContractDocument:
  documentId
  contractId
  documentType
  templateCode
  templateVersion
  sourceContentHash
  documentFileHash
  fileId
  status = PENDING | READY | FAILED
  generatedAt
  failureReason
```

`sourceContentHash` là hash nội dung pháp lý canonical, ví dụ `signedContentHash`. `documentFileHash` là SHA-256 của bytes PDF đã render. Hai hash giải quyết hai câu hỏi khác nhau và tuyệt đối không dùng thay nhau.

Các loại tài liệu Phase 2:

- `DRAFT_PREVIEW`: sinh từ offer/negotiation hiện hành, luôn có watermark `CHƯA CÓ HIỆU LỰC`; có thể sinh lại vì chưa phải bản ký cuối.
- `PARTIALLY_SIGNED`: bản trình bày sau chữ ký đầu, thể hiện rõ còn chờ bên kia và chưa phải bản final.
- `FINAL_SIGNED_CONTRACT`: PDF bất biến sau chữ ký thứ hai, lưu qua file-service với `ingestChannel = SYSTEM_GENERATED`.
- `ACTIVATION_CONFIRMATION`, `DELIVERY_RECEIPT`, `INSPECTION_RECORD`: tài liệu hậu ký độc lập, tham chiếu contract/hash gốc; không sửa bytes hoặc nội dung của `FINAL_SIGNED_CONTRACT`.

`FINAL_SIGNED_CONTRACT` dùng `templateCode = VN_COFFEE_B2B`; tài liệu hậu ký dùng code riêng có prefix `VN_COFFEE_...` và version riêng, nhưng luôn reference `sourceContentHash` của contract/action nguồn.

Flow final document:

```text
Chữ ký thứ hai commit
  -> Contract SIGNED + publish contract.signed
  -> tạo ContractDocument PENDING
  -> durable worker render PDF
  -> file-service lưu blob SYSTEM_GENERATED
  -> ContractDocument READY
  -> publish contract.document_generated
  -> notification-service gửi final PDF/email anchor
```

Không render PDF trong transaction ký. Lỗi render/file storage không được làm mất hoặc rollback hai chữ ký; worker retry idempotent theo `documentId`, và cùng một final source/version chỉ có một document READY. Final PDF không được overwrite; sửa lỗi nội dung phải tạo contract replacement/version mới theo flow nghiệp vụ, không thay file cũ.

## 6. API và event

Public read surface do contract-service sở hữu:

```text
GET /api/v1/contracts/{contractId}/document-preview
GET /api/v1/contracts/{contractId}/documents
GET /api/v1/contracts/{contractId}/documents/{documentId}
```

Endpoint list trả metadata; endpoint theo `documentId` proxy bytes của document READY sau khi kiểm tra participant ownership. Không lộ bucket/object key trực tiếp. Preview chỉ render server-side từ snapshot hiện hành và bắt buộc watermark.

Canonical event `contract.document_generated` mang `documentId`, `contractId`, `documentType`, `fileId`, `sourceContentHash`, `documentFileHash`, `templateCode`, `templateVersion`, `generatedAt`. Audit-service ingest event này; notification command cho final document dedup theo `documentId + recipient + notificationType`.

## 7. Lưu trữ, retention và kiểm chứng

- file-service đánh dấu final PDF là `SYSTEM_GENERATED`, áp retention/legal hold ít nhất bằng evidence hợp đồng và chỉ cho tải qua authorization proxy.
- audit-service lưu cả `sourceContentHash` và `documentFileHash` trong typed content của record; verify lại hash bytes file tách biệt với verify chain.
- Email final document chỉ được gửi sau `contract.document_generated`, không gửi file “final” trực tiếp từ `contract.signed` khi PDF chưa READY.
- Dữ liệu consent, signature, template profile, document metadata và audit record đều append-only về mặt nghiệp vụ.

## 8. Known limitations

- Chưa tích hợp chữ ký số CA hoặc ký sinh trắc học; không được marketing checkbox là điểm chỉ/vân tay thật.
- Chưa chốt citation tiêu chuẩn chất lượng cà phê cụ thể; `coffeeQualityProfileVersion` là profile nội bộ có version cho tới khi pháp lý/nghiệp vụ duyệt nguồn ngoài.
- Chưa chốt toà án hay trọng tài trong nước; template phải fail validation trước ký nếu deployment chưa cấu hình lựa chọn đã được duyệt.
- PDF chuyên nghiệp là representation của snapshot đã ký, không phải nguồn sự thật thay cho structured terms/hash.

---

*Design chốt: 06/10/2026 · Phạm vi Việt Nam + cà phê · Chưa code · Không đồng bộ Architecture/SDS/DOCX trong đợt này.*
