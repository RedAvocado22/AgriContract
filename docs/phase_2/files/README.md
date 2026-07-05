# AgriContract — Script sinh tài liệu .docx

Toàn bộ tài liệu (.docx) được sinh bằng code (docx.js), không gõ tay trong Word.

## Cách chạy

Cần Node.js. Đặt tất cả file trong CÙNG một thư mục (các build script `require('./acdocx.js')`).

```bash
npm install docx          # thư viện sinh docx
node build01.js           # → AgriContract_01_PhanTichThiTruong_v5.docx
```

Chạy file nào sinh ra .docx tương ứng.

## Bản đồ file

| Script | Sinh ra |
|---|---|
| `acdocx.js` | **Module dùng chung** — style/token/helper (H1/H2/table/callout/legal/cover/toc…). MỌI build script phụ thuộc file này, không xoá. |
| `build01.js` | Phân tích thị trường v5 |
| `build02.js` | Giải pháp & Mô hình v5 |
| `build03_part1.js` + `build03_part2.js` | Kiến trúc v2 — **chạy `node build03_part2.js`** (nó require part1) |
| `build04.js` | SDS Phần 1 — Nền tảng & chuẩn chung |
| `build05.js` | SDS Phần 2 — Core (contract/escrow/bank) |
| `build06.js` | SDS Phần 3 — Giám định & tin cậy (inspection/reputation/audit) |
| `build07.js` | SDS Phần 4 — Dữ liệu & hỗ trợ (product/file/pricing) |
| `build08.js` | SDS Phần 5 — Ngoại vi & tổng hợp (user/notification/analytics) + phụ lục |

## Sửa nội dung

- Đổi **style toàn cục** (màu, font, kích thước heading, callout): sửa token `T = {...}` và các helper trong `acdocx.js` → mọi tài liệu đổi theo.
- Đổi **nội dung một tài liệu**: sửa các mảng `push(...)` trong `buildXX.js` tương ứng.
- `build08.js` đã được viết sẵn ở dạng module (`module.exports = { body }`, guard `IS_MAIN`) để phục vụ việc ghép 5 phần SDS thành 1 file. Nếu muốn ghép, các build04–07 cần chỉnh tương tự (guard cover/toc/pack, export `body`) rồi viết một `build_full.js` ráp lại — chưa làm.

## Ghi chú

- TOC (mục lục) trong .docx là field — mở trong Word/LibreOffice nó tự cập nhật số trang (đã bật `updateFields`).
- Không dùng icon/emoji; callout pháp lý là hộp nền xám có nhãn "Căn cứ pháp lý".
