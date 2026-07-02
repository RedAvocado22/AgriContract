---
name: product-phase2-design
description: "Product-service — bổ sung Farm Plot Geolocation cho EUDR compliance (Phase 2). Nguồn: design session 02/07/2026."
status: DESIGNED — chưa code. Cần Cường (Lead) review trước khi đưa vào Architecture/SDS/TechnicalSpec chính thức.
metadata:
  type: design
  phase: 2
  extends: "architecture.md § Product Aggregate (Phase 1, đã code + E2E verified)"
---

## 1. Bối cảnh & Scope

EUDR (EU Deforestation Regulation) áp dụng từ **30/12/2026** cho large/medium operator (Regulation 2025/2650, dời deadline lần 2, đã chốt cuối cùng — không còn dời tiếp). Yêu cầu cốt lõi: mọi lô hàng đưa ra thị trường EU phải truy được về **toạ độ chính xác của (các) plot đất nơi hàng được trồng**, chứng minh không có phá rừng sau 31/12/2020.

**Điểm quan trọng nhất quyết định thiết kế này — EUDR cấm mass balance:** không được gộp hàng từ nhiều nguồn rồi khai đại diện bằng 1 plot. Phải khai đủ **toàn bộ** plot đóng góp vào lô hàng, segregate rõ ràng, không được "coi như" hoặc pha trộn.

Điều này va trực tiếp với bản chất HTX: một listing của HTX thường là hàng **gom từ nhiều hộ thành viên**, mỗi hộ có plot đất riêng — không phải 1 nông trại lớn duy nhất. Nên geolocation không thể là 1 cặp lat/long gắn vào `Product`, mà phải là **mảng plot**, mỗi phần tử ứng với 1 hộ đóng góp.

**Vị trí trong kiến trúc:** đây là dữ liệu **tĩnh, gắn với nguồn gốc hàng**, không đổi qua vòng đời `OFFERED → ... → SETTLED` của `Contract`/`Milestone`. Không có "bước" nào trong Contract-service hay Milestone Escrow cần chụp GPS — nó phải tồn tại **trước khi** listing được tạo, thuộc về `product-service` (Phase 1, đã code + E2E verify). Đây là additive change — thêm bảng con, không đụng business logic đã test của `Product` aggregate.

---

## 2. Domain Model Changes

### 2.1 `ProductPlot` (nested Value Object list — không phải aggregate riêng)

**Chốt (02/07/2026):** khác với `Milestone` (tách aggregate riêng vì có state machine + lifecycle độc lập, xem `milestone-escrow-phase2-design.md` §2.2), `ProductPlot` **không cần** tách aggregate riêng — nó không có state machine, không có nghiệp vụ độc lập, chỉ là dữ liệu khai báo tĩnh gắn chặt với `Product`. Sống/chết cùng `Product` (không có ProductPlot nào tồn tại độc lập khi Product bị xoá), không có lý do gì cần transaction boundary riêng. Đúng nguyên tắc Vernon: entity con chỉ tách aggregate khi có invariant/lifecycle cần bảo vệ độc lập — ở đây không có, nên giữ làm value object list bên trong `Product` aggregate.

| Field | Loại | Ghi chú |
|---|---|---|
| `plotId` | UUID | Định danh nội bộ, không phải aggregate root |
| `householdLabel` | String | Tên/mã hộ thành viên đóng góp plot này. **Không** cần map tới `User` account — HTX có thể có hộ chưa từng đăng ký tài khoản trên platform, đây chỉ là nhãn khai báo phục vụ truy xuất nguồn gốc. |
| `geometryType` | Enum (`POINT`, `POLYGON`) | Quyết định bởi `areaHectares` — xem quy tắc §2.2 |
| `geoJson` | Text (GeoJSON string) | Format bắt buộc theo EUDR — `POINT` (1 toạ độ, 6 số thập phân) nếu ≤4ha, `POLYGON` (nhiều toạ độ khép kín, 6 số thập phân) nếu >4ha |
| `areaHectares` | BigDecimal | Diện tích plot — dùng để quyết `geometryType`, không phải field hiển thị đơn thuần |
| `declaredAt` | Timestamp | Thời điểm HTX khai báo — dùng làm mốc đối chiếu nếu sau này có tranh chấp về tính xác thực |

### 2.2 Quy tắc `geometryType` (theo ngưỡng EUDR)

| Diện tích plot | `geometryType` | Format |
|---|---|---|
| ≤ 4 hecta | `POINT` | 1 toạ độ lat/long, 6 số thập phân |
| > 4 hecta | `POLYGON` | GeoJSON polygon, các điểm khép kín viền plot, 6 số thập phân mỗi điểm |

Áp dụng theo từng plot riêng lẻ, không phải theo tổng diện tích cả listing — 1 listing có thể vừa có plot dùng `POINT` vừa có plot dùng `POLYGON`.

### 2.3 `Product` aggregate (cập nhật)

Thêm quan hệ 1-N với `ProductPlot`. Không đổi field nào hiện có, không đổi state machine hiện tại của `Product`.

---

## 3. Database Migration

Additive — 1 Flyway script, không sửa bảng `product` gốc:

```sql
CREATE TABLE product_plot (
    plot_id         UUID PRIMARY KEY,
    product_id      UUID NOT NULL REFERENCES product(product_id),
    household_label VARCHAR(255) NOT NULL,
    geometry_type   VARCHAR(20) NOT NULL, -- POINT | POLYGON
    geo_json        TEXT NOT NULL,
    area_hectares   DECIMAL(10,4) NOT NULL,
    declared_at     TIMESTAMP NOT NULL
);
CREATE INDEX idx_product_plot_product_id ON product_plot(product_id);
```

Không có `UPDATE`/migration nào chạy trên data cũ — bảng mới, rỗng, không ảnh hưởng listing đã tồn tại.

---

## 4. Use Case Changes

- `CreateListing`: nhận thêm `List<ProductPlotRequest>` (bắt buộc ≥1 plot). Validate: mỗi plot phải có `geoJson` hợp lệ theo `geometryType` tương ứng với `areaHectares` khai.
- `UpdateListing`: cho phép thêm/sửa/xoá plot trước khi listing chuyển khỏi trạng thái cho phép sửa (theo state machine `Product` hiện có, không đổi).
- `GetListing`/`SearchListing`: không đổi logic, chỉ thêm field trả về nếu client cần hiển thị.

Không đụng tới `contract-service`, `escrow-service`, hay bất kỳ use case nào của Milestone Escrow.

---

## 5. EUDR Compliance Notes

- **Không mass balance:** listing phải khai đủ toàn bộ plot đóng góp, không được chọn 1 plot đại diện. Validate ở tầng use case: `List<ProductPlotRequest>` không được rỗng.
- **Không cần tỷ lệ đóng góp:** EUDR không đòi biết chính xác bao nhiêu kg đến từ hộ nào — chỉ cần khai đủ danh sách plot, miễn không plot nào bị phá rừng sau 31/12/2020. Nên `ProductPlot` **không có** field khối lượng/tỷ lệ đóng góp — tránh over-engineer thứ EUDR không yêu cầu.
- **Dữ liệu self-declared:** platform không có khả năng verify vệ tinh/thực địa (ngoài scope 5 tháng/3 người). `declaredAt` + hash chain (xem `services.md` mục 5, sẽ hook vào session hash chain đang làm) là cơ chế chống sửa sau khi khai, không phải cơ chế xác minh tính đúng đắn ban đầu. Giữ nhất quán với lập luận "trusted operator, không phải trustless" đã dùng cho hash chain/chữ ký điện tử.

---

## 6. Out of Scope (có chủ đích, không phải thiếu sót)

**Luồng export sang `audit-service` để tạo báo cáo EUDR** — để dành cho session hash chain đang chạy, vì `audit-service` cần đọc `ProductPlot` qua cross-service reference (Feign hoặc read model), kết hợp với `signedContentHash`/audit trail đã thiết kế. Session này chỉ chốt phần data model gốc ở `product-service` — nơi dữ liệu geolocation thực sự sống.

---

## 7. Status — Product Phase 2 (Farm Plot Geolocation)

**Chốt (02/07/2026):** `ProductPlot` là nested VO list trong `Product` aggregate, không tách aggregate riêng (khác `Milestone`). Mảng theo hộ thành viên, không theo tỷ lệ đóng góp. `geometryType` quyết theo ngưỡng 4ha/plot. Migration additive, không đụng data/logic Phase 1 đã test. Export sang `audit-service` để ngoài phạm vi session này.

Product Phase 2 (Farm Plot Geolocation) — **đóng session, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.**

---

*Design session: 02/07/2026 · Chưa code · Chưa đưa vào Architecture/SDS/TechnicalSpec chính thức.*
