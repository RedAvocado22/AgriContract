# VERIFY.md — Report-3_SRS_v1_6.docx

Kết quả kiểm tra theo checklist ở §6 của PLAN_SRS_v1.6.md.

1. **validate.py pass** — PASS.
   `python3 validate.py Report-3_SRS_v1_6.docx --original srs.docx`
   → `Paragraphs: 5380 → 5420 (+40)` · `All validations PASSED!`
   (Không có cảnh báo cấu trúc nào mới so với bản gốc.)

2. **Xem bằng Read các trang thay đổi** — PASS, đã mở từng trang render PNG/JPEG và kiểm tra bằng mắt:
   - Context diagram (trang 10): 8 actor đúng (có Arbitrator, màu tím #E1D5E7), không méo, không tràn lề.
   - 7 business flow BF-01→BF-07 (trang 11–17): mỗi hình là activity diagram UML đúng quy ước (1 action/node, swimlane đúng vai, quyết định dùng `if/then/else`, kết thúc bằng `stop` sau node kết quả ghi lời thường). Đã phát hiện và sửa 2 lỗi dựng hình trước khi nhúng:
     - BF-01: bước 8 "Reject with a reason" bị lạc sang lane System do PlantUML kế thừa lane từ nhánh "yes" khi nhánh "no" không khai báo lại `|Operator|` — đã sửa bằng cách khai báo lane tường minh ở đầu mỗi nhánh if/else.
     - BF-03: dùng `backward:` chung với `if/endif` khiến node 11 "Notify both parties and Operator" bị vẽ rời rạc, không có cạnh nối — đã thay bằng `if/else` tường minh.
   - ERD (trang 17–18): không còn 3 hình cũ, PRODUCT_PLOT xuất hiện ở 3.2, notation đúng crow's-foot.
   - 4 UC diagram Operator/Admin/Arbitrator/External Verifier (trang 37–39): đúng scope từng vai; Arbitrator mới (4.3.8) chèn đúng vị trí trước External Verifier (đổi thành 4.3.9).
   - Permission Matrix (trang 39+): đối chiếu các dòng breach case / force majeure / reputation action / inspection — cột Admin/Operator/Arbitrator nhất quán với UC diagram.
   - Screen flow (trang 44): 8 lane đúng, Arbitrator lane (tím) tách biệt Operator (xanh) và Admin (đỏ, rút gọn còn 6 màn audit/analytics).

3. **ERD** — PASS: không còn 3 hình cũ; notes đúng text A4 (many-to-many Product–Plot → PRODUCT_PLOT); entity PRODUCT_PLOT có ở mục 3.2.

4. **`grep -c "Admin as fallback"`** — PASS: 0 occurrence trong text đã trích xuất.
   Dòng thay thế đúng: "Each staff role holds only the actions of its own function; no staff role acts as a fallback for another." (xác nhận ở cuối Permission Matrix, trang 43.)

5. **Khớp §B5 (Admin → 3 vai)** — PASS, đối chiếu lại toàn bộ 50 occurrence còn lại của "Admin" trong văn bản cuối: tất cả đều đúng phạm vi hẹp (quản lý tài khoản UC08, audit/security UC48-50, analytics UC53, hoặc các câu liệt kê vai trò chung). Trong quá trình rà soát cuối này (ngoài phạm vi bảng B5 gốc, vốn chỉ phủ các vị trí bảng/actor/UC spec có cấu trúc), phát hiện và sửa thêm **7 chỗ sót** là văn xuôi tự do (rationale, "Enforced By", chú thích màn hình, notation legend) không nằm trong batch-replace B5:
   - Notation legend 4.3: câu ví dụ "A hollow-triangle arrow from Admin to Operator…" mô tả quan hệ generalization đã bị xoá khỏi mọi UC diagram sau khi tách vai — xoá câu này.
   - Màn hình #42 "Breach Case Detail (Arbitrator)": mô tả còn ghi "Allows the Admin to…" — sửa thành "Allows the Arbitrator to…".
   - GB-06 (rationale): "a single Admin…" → "a single Arbitrator…" (rule đã ghi rõ quyết định thuộc Arbitrator khác người đề xuất).
   - GB-17 (Enforced By): "…the Admin during breach case resolution" → "…the Arbitrator during breach case resolution".
   - GB-28 (Enforced By): "…the Admin through the final decision" → "…the Arbitrator through the final decision".
   - GB-30 (Enforced By): "…the Admin during case review" → "…the Arbitrator during case review".
   - GB-32 (Enforced By): "The Admin during force majeure review…" → "The Arbitrator during force majeure review…" (rule statement đã ghi "decided by the Arbitrator" — Enforced By trước đó mâu thuẫn với chính câu rule).
   - GB-39 (Enforced By): "…the Admin during external report review" → "…the Operator during external report review" (khớp UC47, màn hình #53 Pending External Report Review thuộc Operator).

   Các mention "Admin" còn lại (GB-01, GB-36, GB-40, GB-48, MSG103, replay-approval maker-checker ở JOB retry…) đã đối chiếu và xác nhận ĐÚNG — đều là chức năng audit/security/account-admin thật sự thuộc Admin, không phải sót.

6. **Mục I không còn khối UC27; II.2.6 còn nguyên** — PASS.
   - Đã xoá 23 paragraph (heading "2.6 Propose Contract Replacement (UC27)" + toàn bộ bảng spec bị paste nhầm, từ ngay sau ảnh BF-06 đến hết Postconditions "…without restoring the old contract to ACTIVE.", cộng 3 paragraph heading rỗng còn sót) — chỉ ở mục I.
   - Xác nhận bằng pdftotext: "Propose Contract Replacement (UC27)" còn đúng 2 lần (TOC + mục II.2.6, trang 55), không còn ở mục I (trang 16 chuyển thẳng từ BF-06 sang "2.7 Emergency Security Lock (BF-07)").
   - Mục II.2.6 (trang 55) còn nguyên vẹn bảng spec đầy đủ (Primary Actors, Description, Preconditions, Normal Flow 10 bước, Alternative Flows, Postconditions).

7. **Số trang so với bản gốc** — PASS: bản gốc 179 trang, bản v1.6 180 trang sau khi thêm section ngang (chênh lệch 0; các thay đổi là thay ảnh tại chỗ + xoá/thêm một lượng nhỏ văn bản cân bằng nhau).

8. **Không có heading mới ngoài "4.3.8 UCs for Arbitrator"** — PASS: đối chiếu toàn bộ heading 4.3.x giữa bản gốc và bản mới, chỉ có đúng 1 heading mới (4.3.8 UCs for Arbitrator), heading cũ 4.3.8 UCs for External Verifier đổi thành 4.3.9 (đúng kế hoạch), không có heading lạ nào khác xuất hiện trong toàn văn bản.

9. **Thứ tự lane BF (review lần 2)** — bản trước 6/7 hình sai thứ tự lane so với plan (PlantUML xếp theo lần xuất hiện đầu tiên). Đã khai báo lane đầu file theo đúng thứ tự plan, render lại, nhúng lại; validate PASS, 179 trang.

10. **Sửa thêm (review lần 2)** — Change Log v1.6, footer NUMPAGES, section landscape cho BF-03/04/06, OPERATOR_MANUAL, câu Admin/GB-48. validate PASS (5380 → 5424 paragraph), 180 trang (thêm 1 trang do section break), trang 13–14 và 16 khổ ngang, số trang chạy liên tục 1→180, bìa không có footer.

**Kết luận:** Tất cả 8 mục checklist PASS. File cuối: `Report-3_SRS_v1_6.docx`.

11. **Open issues (12/10/2026 tối)** — UC41 thêm contest window + alt flow "Decision Contested"; GB-49 (tách khỏi bảng constraints) + GB-50 vào appendix; BF-05 vẽ lại (kháng nghị → Level 1.5). validate PASS (5380 → 5437 paragraph), 180 trang.
