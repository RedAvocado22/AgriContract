# AgriContract — Phân công Service Ownership (Phase 2)

*Lập: 14/07/2026 · Nguyên tắc: chia theo service (1 người own trọn service end-to-end: backend + màn hình React + test + section SDS tương ứng). Team size chưa chốt 4/5 người → bảng chính lập cho 5, kèm fallback định sẵn cho kịch bản 4.*

---

## 1. Bảng phân công chính (5 người)

| # | Người | Services | Ghi chú tải |
|---|---|---|---|
| P1 | **Cường** | `contract` (core, gồm feature `Signature`) · `milestone-escrow` · `bank-service` · `hash-chain` (audit) · `api-gateway` | Rất nặng — nguyên money core. Van xả: xem §4 |
| P2 | **Long** | `product` (geolocation) · `user-service` (kèm fix KI-3 — email IDOR + ownership check) | Vừa — là buffer overflow của team, còn dư slot |
| P3 | **Node** | `notification` · `pricing-service` · `analytics-service` | Nhẹ vừa — cả 3 hợp Node.js: pricing = crawler/scraper, analytics = batch job, notification = event consumer. Ít dính transaction Spring, chạy service Node riêng được |
| P4 | **Thành viên khá** | `inspection-service` · `reputation-service` | Vừa-nặng — 2 service nhiều quyết định nghiệp vụ nhất trong phần còn lại, cùng "tầng trust" |
| P5 | **Thành viên yếu** | `file-service` | Nhẹ — spec đã chốt cứng, gần như implement-theo-spec. `Signature` không phải service riêng; thuộc `contract-service` của P1. |

## 2. Lý do các cặp ghép chính

- **P1 (money core):** contract → escrow → bank → hash-chain là một chuỗi transaction liền mạch; một người giữ đảm bảo tính nhất quán ledger + audit. Gateway đi kèm vì contract flow là luồng qua gateway nhiều nhất.
- **P2:** user-service là chủ identity/KYC — nền của mọi service khác; product/geolocation độc lập tương đối, phù hợp chạy song song.
- **P3:** ba service data/event thuần, biên giới rõ, tách stack Node không ảnh hưởng core Spring.
- **P4:** inspection có admin review flow, allowlist accreditation, commissioning Level 1.5 dính thẳng dispute của escrow; reputation có logic tín hiệu AML + consume event từ escrow và analytics — cần người tự xử được ambiguity.
- **P5:** file-service có pipeline virus scan/storage key đã spec chi tiết. **Cơ chế kèm cặp built-in:** file intake nuôi inspection của P4 nên P4 tự nhiên review/integrate với P5 thường xuyên. Feature `Signature` nằm trong `contract-service`; P1 own transaction ký, P2 cung cấp KYC/thẩm quyền, P3 cung cấp OTP delivery/email anchor.

## 3. Fallback — kịch bản 4 người

Chưa biết ai rớt → ghi sẵn mũi tên cho từng service. Ai rớt thì service của người đó tự rơi về chủ mới theo bảng, **4 người còn lại không đổi việc**:

| Service | Fallback owner | Lý do |
|---|---|---|
| `file-service` | P4 (khá) | Dính inspection (evidence pipeline) |
| `reputation-service` | P2 (Long) | Slot dư của Long |
| `inspection-service` | P1 (Cường) — **tránh nếu được** | Cường đã max tải; nếu rơi vào đây nên họp chia lại thay vì theo mũi tên máy móc |
| `notification` | P2 (Long) | Nhẹ, cross-cutting |
| `pricing-service` | P4 (khá) | Nhẹ, ít UI |
| `analytics-service` | P4 (khá) | Cặp event `structuring_pattern_detected` → reputation cùng về một người |
| `product` | P4 (khá) | Nếu Long rớt |
| `user-service` | P1 (Cường) | Identity dính security/kill-switch |

**Kịch bản dễ nhất:** rớt P5 → file về P4, không xáo trộn ownership khác.
**Kịch bản xấu nhất:** rớt P4 → nên họp chia lại toàn cục, không theo bảng.

## 4. Van xả tải (không làm gì bây giờ, chỉ ghi sẵn)

Nếu giữa kỳ **Cường (P1) ngộp**: bóc `api-gateway` đẩy sang P2 hoặc P3 — cross-cutting, ít nghiệp vụ, tách ra không chảy máu. Đây là thứ duy nhất gỡ được khỏi money core mà không phá tính nhất quán.

## 5. Cặp integrate cần negotiate contract sớm

| Cặp | Nội dung |
|---|---|
| P1 ↔ P4 | Event dispute/commissioning Level 1.5 (escrow ↔ inspection) |
| P1 ↔ P3 | Event `contract.signed` → analytics (`dim_contract`) |
| P3 ↔ P4 | Event `structuring_pattern_detected` (analytics → reputation) |
| P4 ↔ P5 | File intake → inspection |
| P1 ↔ P2 ↔ P3 | Signature flow: contract transaction ↔ KYC/thẩm quyền ↔ gửi OTP/email anchor |
| P2 ↔ tất cả | `UserInfoResponse` DTO sau fix KI-3 (bỏ `email`, thêm ownership check) — mọi Feign client phải cập nhật theo |

---

*Nguyên tắc điều chỉnh: service là đơn vị cố định — plan không bao giờ vẽ lại, chỉ đổi cột owner. Timeline milestone giữ nguyên giữa 2 kịch bản, chỉ nới deadline ~20-25% nếu còn 4 người.*

---

# Phần 2 — Tier cut & Luồng vàng (kỷ luật scope)

## Luồng vàng — kịch bản demo phải chạy trọn end-to-end

Đăng listing → ký hợp đồng (ký số) → buyer khoá tiền milestone 1 → giao hàng đợt 1, nghiệm thu, settle tự động → milestone 2 phát sinh **dispute** → giám định Level 1.5 → settle theo phán quyết → hết hợp đồng → reputation cập nhật → ledger đối soát khớp từng đồng.

Mọi quyết định scope trả lời một câu duy nhất: *"luồng vàng đã chạy end-to-end tới đâu?"* Hội đồng nhớ câu chuyện này, không nhớ số lượng service.

## Bảng tier

| Tier | Services | Chuẩn chất lượng |
|---|---|---|
| **T1 — không được phép mỏng** | contract · escrow · bank (mock) · user · gateway | Code đầy đủ, test tử tế, mọi nhánh của luồng vàng chạy được |
| **T2 — chạy luồng chính, mặt phụ được mỏng** | inspection (đủ Level 1.5 cho cú dispute demo) · reputation (lock + score cơ bản) · file · notification | Happy path + nhánh demo; edge case ghi nhận, không bắt buộc code |
| **T3 — hiện diện, không cần sâu** | pricing (Admin nhập tay) · analytics (1 dashboard) · audit (hash chain chạy, anchor testnet/giả lập) · geolocation (demo 1 plot) | Đủ để chỉ vào và giải thích thiết kế đầy đủ trong SDS |

## Luật sắt

1. **T1 xong trước.** Không ai code T3 khi luồng vàng chưa chạy hết.
2. **Co giãn 4/5 người chỉ được đụng T2-T3** — không bao giờ đụng T1.
3. Mỗi tuần review đúng 1 câu: luồng vàng chạy tới bước nào, gãy ở đâu.
