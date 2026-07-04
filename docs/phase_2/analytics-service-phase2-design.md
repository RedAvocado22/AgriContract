---
name: analytics-service-phase2-design
description: "Analytics-service — CQRS read model thuần tuý, time-series aggregation phục vụ báo cáo quản trị B2B (VICOFA/VRA). Nguồn: design session 06/07/2026."
status: DESIGNED — chưa code.
metadata:
  type: design
  phase: 2
  extends: "services.md § analytics-service (8093 — CQRS analytics, time-series aggregation, pre-computed vs real-time tradeoff)"
  related: "milestone-escrow-phase2-design.md §7 (Event Catalog làm nguồn data ingest); reputation-service-phase2-design.md (cùng pattern eventual consistency)"
---

## 1. Bối cảnh & Scope

**Analytics-service là một PURE CONSUMER.** Nó không sở hữu bất kỳ quy trình nghiệp vụ lõi nào, không gọi ngược (REST/Feign) về các service khác để lấy dữ liệu. Mọi dữ liệu nó có đều phải đến từ việc "nghe" RabbitMQ events. Lý do thiết kế cứng này: analytics là non-critical path. Dù `analytics-service` có sập hay bị quá tải vì query báo cáo nặng, nó tuyệt đối không được tạo tải ngược (cascading failure) lên `contract-service` hay `bank-service` đang xử lý giao dịch.

**Mục tiêu (B2B Agritech context):** Cung cấp các chỉ số đo lường sức khoẻ nền tảng có ý nghĩa thật cho Hiệp hội (VICOFA/VRA) và Admin nền tảng, để ra quyết định vĩ mô. Không vẽ dashboard vô thưởng vô phạt. Cụ thể:
- **Tỷ lệ bẻ kèo (Cancellation/Default Rate):** Đo lường lòng tin vào hợp đồng forward.
- **Hiệu quả Milestone Escrow (Escrow Effectiveness):** Giải quyết câu hỏi "Mô hình chia nhỏ batch có thực sự giúp giảm tỷ lệ vốn bị giam (locked) vô ích so với thực nhận (actual) không?".
- **Xu hướng Bất khả kháng (Force Majeure Trends):** Đo lường rủi ro đứt gãy chuỗi cung ứng do thiên tai theo mùa vụ/ngành hàng.

**Ràng buộc đồ án (5 tháng/5 người):** Giữ stack ở mức MySQL (lưu trữ read model) + Redis (cache API response) + RabbitMQ (ingest) + Spring `@Scheduled` (batch pre-compute). **Đã loại hẳn** ClickHouse, Elasticsearch, Kafka hay Spark — over-engineer so với lượng data của một nền tảng B2B forward contract (vốn có tần suất giao dịch thấp nhưng giá trị cao).

---

## 2. Domain Model — CQRS Read Models

Thiết kế DB tuân theo mô hình **Star Schema thu nhỏ**, tách biệt bảng Dimension (thông tĩnh) và Fact/Aggregate (số liệu).

### 2.1 Bảng Dimension (Tái tạo local state)

Bởi vì không được gọi Feign ngược, analytics phải tự build một bản sao thu gọn của thông tin hợp đồng để dùng làm các "chiều" (dimensions) phân tích (như `commodity`, `buyerId`, `sellerId`).

```sql
CREATE TABLE dim_contract (
    contract_id    UUID PRIMARY KEY,
    commodity      VARCHAR(50) NOT NULL,
    buyer_id       UUID NOT NULL,
    seller_id      UUID NOT NULL,
    signed_at      TIMESTAMP NOT NULL
);
```
*[CẦN XÁC NHẬN: Các event hiện tại trong Event Catalog (milestone-escrow §7) chưa định nghĩa rõ payload có chứa `commodity` hay không. Để populate được `dim_contract` thuần qua event, đề xuất contract-service phải publish `contract.signed` kèm đủ metadata (commodity, buyerId, sellerId) ngay khi ký xong, hoặc nhúng sẵn `commodity` vào payload của mọi event. Chờ Cường/team xác nhận payload detail.]*

### 2.2 Sự đánh đổi: Real-time vs Pre-computed

**Vấn đề cốt lõi:** Nếu mọi API dashboard đều gộp data bằng lệnh `SELECT SUM(...) GROUP BY DATE(...)` từ hàng trăm nghìn event thô, DB sẽ nghẽn lúc hiệp hội vào xem báo cáo cuối tháng. Nếu tính sẵn mọi thứ, thì code ingest cực kỳ phức tạp vì phải lo race condition khi nhiều event update cùng 1 bucket.

**Chốt (06/07/2026) — Hybrid Approach (Kết hợp 2 chiến lược):**

1. **Fact Tables (Lưu Raw Events - Ghi cực nhanh, không khoá bảng):**
   Dùng để lưu sự thật gốc rễ, incremental append-only.

   ```sql
   -- Bảng Fact cho Milestone
   CREATE TABLE fact_milestone_performance (
       fact_id                 UUID PRIMARY KEY,
       contract_id             UUID NOT NULL,
       milestone_id            UUID NOT NULL,
       status                  VARCHAR(20) NOT NULL, -- SETTLED / CANCELLED_WITH_PENALTY
       locked_amount           DECIMAL(15,2) NOT NULL,
       actual_amount           DECIMAL(15,2) NOT NULL,
       delta_shortfall_amount  DECIMAL(15,2) NOT NULL, -- = locked - actual
       has_force_majeure       BOOLEAN NOT NULL DEFAULT FALSE,
       resolved_at             TIMESTAMP NOT NULL
   );
   CREATE INDEX idx_fact_milestone_time ON fact_milestone_performance(resolved_at);

   -- Bảng Fact cho Contract Cancellation
   CREATE TABLE fact_contract_cancellation (
       fact_id                 UUID PRIMARY KEY,
       contract_id             UUID NOT NULL,
       initiated_by            VARCHAR(10) NOT NULL, -- BUYER / SELLER
       cancelled_at            TIMESTAMP NOT NULL
   );
   ```

2. **Pre-computed Aggregate Tables (Tính toán sẵn - Đọc cực nhanh):**
   Dùng cho các metrics phức tạp (tỷ lệ %, chia trung bình). Không cập nhật real-time. Do một job Spring `@Scheduled` chạy batch hàng đêm (VD: 1:00 AM) quét từ bảng Fact để điền vào. Time bucket chia theo **Tháng** (phù hợp với chu kỳ báo cáo nông sản).

   ```sql
   CREATE TABLE agg_monthly_commodity_stats (
       month_id                VARCHAR(7) NOT NULL,  -- Format 'YYYY-MM'
       commodity               VARCHAR(50) NOT NULL,
       total_contracts_settled INT NOT NULL DEFAULT 0,
       total_contracts_cancelled INT NOT NULL DEFAULT 0,
       cancellation_rate       DECIMAL(5,4) NOT NULL DEFAULT 0,
       force_majeure_incidents INT NOT NULL DEFAULT 0,
       total_value_settled     DECIMAL(15,2) NOT NULL DEFAULT 0,
       escrow_efficiency_rate  DECIMAL(5,4) NOT NULL DEFAULT 0, -- (actual_amount / locked_amount) toàn tháng
       PRIMARY KEY (month_id, commodity)
   );
   ```

### 2.3 Idempotency Log (Ràng buộc bắt buộc cho Analytics)

RabbitMQ đảm bảo at-least-once delivery. Nếu 1 event `milestone.settled` bị kẹt và gửi lại 2 lần, số tiền trong hệ thống báo cáo sẽ bị nhân đôi. Analytics nhạy cảm với vụ này không kém gì bank-service.

```sql
CREATE TABLE analytics_idempotency_log (
    message_id      VARCHAR(255) PRIMARY KEY, -- ID của RabbitMQ message
    processed_at    TIMESTAMP NOT NULL DEFAULT now()
);
```

---

## 3. Ingest Pipeline

Mỗi khi nhận event từ RabbitMQ, pipeline xử lý phải đi qua bước check `analytics_idempotency_log`. Nếu đã xử lý → skip ngay lập tức.

### 3.1 Nhóm Event "Milestone Escrow"
*(Nguồn: milestone-escrow-phase2-design.md §7.1)*

* **Nhận `milestone.settled` (mang `lockedAmount`, `actualAmount`):**
    * *Hành động:* `INSERT` vào `fact_milestone_performance`. Tính `delta_shortfall_amount = lockedAmount - actualAmount`.
    * *Chiến lược:* Ghi incremental, không group ngay. Nhanh và triệt tiêu table lock contention.
* **Nhận `milestone.force_majeure_resolved` (chỉ ghi nhận khi APPROVED):**
    * *Hành động:* Thực ra, ta cần cờ này map chung vào milestone đó. Sẽ update `has_force_majeure = TRUE` vào một record trong bảng fact nếu có.
* **Nhận `milestone.cancelled_with_penalty`:**
    * *Hành động:* `INSERT` vào `fact_milestone_performance` với `status = CANCELLED`, `actual_amount = 0`.

### 3.2 Nhóm Event "Contract Level"
*(Nguồn: milestone-escrow-phase2-design.md §7.2)*

* **Nhận `contract.cancelled` (mang `initiatedBy`):**
    * *Hành động:* `INSERT` vào `fact_contract_cancellation`.

### 3.3 Batch Job `@Scheduled` (Pre-compute)

**Chốt (06/07/2026):** Job `MonthlyAggregationJob` chạy lúc **1:00 AM mỗi ngày** (off-peak, trước giờ `VerifyChainJob` của audit-service 2:00 AM để tránh dẫm chân tài nguyên DB nội bộ nếu triển khai chung node).
* *Cách chạy:* Quét dữ liệu ngày hôm qua từ `fact_milestone_performance` (JOIN với `dim_contract` để lấy `commodity`), cộng dồn (upsert) vào dòng `agg_monthly_commodity_stats` của tháng hiện tại.
* *Tính Rate:* `cancellation_rate = total_contracts_cancelled / (settled + cancelled)`. Tính và ghi đè thẳng vào cột, API sau này chỉ việc `SELECT` và trả về O(1).

---

## 4. API Endpoints

Các API này phục vụ Dashboard của Admin/Hiệp hội, nên được Redis cache với TTL 1 giờ (dữ liệu phân tích vĩ mô không cần fresh tới từng giây).

### 4.1. Báo cáo Tỷ lệ bẻ kèo theo Ngành hàng
Đo lường lòng tin vào hợp đồng forward. Chỉ số cancellation cao ở một ngành hàng có thể cảnh báo Hiệp hội về biến động giá thị trường khắc nghiệt hoặc cấu trúc hợp đồng có vấn đề.

* `GET /api/v1/analytics/cancellations?year={YYYY}`
* **Response Shape:**
    ```json
    {
      "year": "2026",
      "data": [
        {
          "commodity": "COFFEE",
          "totalSettled": 120,
          "totalCancelled": 15,
          "cancellationRate": 0.1111,
          "breakdown": {
            "buyerInitiated": 10,
            "sellerInitiated": 5
          }
        }
      ]
    }
    ```

### 4.2. Hiệu quả Milestone Escrow (Escrow Effectiveness)
Chứng minh giá trị thật của cơ chế Milestone Phase 2 so với Two-phase lock Phase 1. Trả lời câu hỏi: "Tiền bị khoá (locked_amount) có thực sự bám sát thực nhận (actual_amount) không, hay đang khoá dư thừa?".

* `GET /api/v1/analytics/escrow-effectiveness?month={YYYY-MM}`
* **Response Shape:**
    ```json
    {
      "month": "2026-10",
      "metrics": {
        "totalLockedVolume": 5000000000,
        "totalActualDisbursed": 4850000000,
        "totalShortfallDelta": 150000000,
        "escrowEfficiencyRate": 0.97,
        "forceMajeureImpact": 50000000
      }
    }
    ```

### 4.3. Xu hướng Bất khả kháng (Force Majeure Trends)
Rất quan trọng với VICOFA/VRA để báo cáo tình hình chuỗi cung ứng với Bộ NN&PTNT.

* `GET /api/v1/analytics/force-majeure-trends?year={YYYY}`
* **Response Shape:**
    ```json
    {
      "year": "2026",
      "totalIncidents": 24,
      "byCommodity": {
        "COFFEE": 18,
        "RICE": 6
      },
      "peakMonths": ["2026-09", "2026-10"] // Các tháng có bão lũ
    }
    ```

---

## 5. Known Limitations (Giới hạn có chủ đích)

Những điều này phải báo cáo thẳng thắn với hội đồng bảo vệ, đây là sự đánh đổi kinh điển của kiến trúc Event-Driven/CQRS, không phải bug:

1.  **Eventual Consistency & Data Lag (Độ trễ dữ liệu):** Dữ liệu trên Dashboard sẽ không khớp tới từng VNĐ ngay thời điểm hiện tại so với `bank-service` hay `contract-service`. Có hai lớp lag:
    * *Lag hệ thống:* Độ trễ của RabbitMQ (thường là mili-giây, nhưng có thể lâu hơn nếu queue kẹt).
    * *Lag nghiệp vụ:* Bảng `agg_monthly_commodity_stats` chỉ được cập nhật qua batch job 1:00 AM. Những thay đổi trong ngày hôm nay sẽ không phản ánh vào các biểu đồ chia rate (%) cho tới ngày mai.
2.  **Không có Historical Backfill (Dữ liệu quá khứ trống không):** Vì service này là một *pure consumer* không có API gọi ngược, nếu deploy `analytics-service` vào giữa Phase 2 (tháng thứ 3 chẳng hạn), nó sẽ chỉ bắt đầu ghi nhận data từ ngày deploy. Các hợp đồng đã SETTLED ở tháng 1, tháng 2 sẽ không tồn tại trên dashboard trừ khi ta viết script thủ công tái phát (replay) event từ `audit-service` đẩy vào lại RabbitMQ. Đây là cái giá thật của event-driven architecture.
3.  **Không bắt được thay đổi cấu trúc bảng cũ:** Nếu `milestone-escrow` quyết định sửa lịch sử giao dịch (data fix tay bằng SQL từ phía Admin), event RabbitMQ không được sinh ra, dẫn đến `analytics-service` sẽ vĩnh viễn bị lệch số so với DB gốc.

---

## 6. Status — Analytics-service Design

**Chốt (06/07/2026):** `analytics-service` là pure consumer, cqrs read-model, không phụ thuộc đồng bộ vào service nào. Áp dụng mô hình Star Schema thu nhỏ: `fact` tables (lưu event thô incremental, giải quyết nghẽn ghi) + `agg` tables (pre-computed định kỳ 1:00 AM bằng `@Scheduled`, giải quyết nghẽn đọc/tính toán %). Bắt buộc có bảng Log Idempotency theo `message_id` để tránh at-least-once làm lặp số. Metric tập trung hoàn toàn vào B2B value (cancellation, force majeure, escrow effectiveness). Chấp nhận độ trễ (data lag) và trống data quá khứ lúc mới deploy làm tradeoff.

**1 điểm treo cần Cường xác nhận:** Cần đảm bảo các event cấp Contract/Milestone ở upstream (như `contract.signed` hoặc `milestone.settled`) có bơm đủ metadata (đặc biệt là `commodity`) vào payload để analytics-service không bị "mù" do không được gọi Feign ngược.

Analytics-service — **đóng session, service cuối cùng của Phase 2, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.**

---

*Design session: 06/07/2026 · Chưa code · Đã đóng kiến trúc Phase 2 tổng thể.*