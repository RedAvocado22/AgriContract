# AgriContract — Script sinh tài liệu DOCX

The bank-service and reputation-service design files use their original filenames as the single current source; there are no split `-final`/`.superseded` variants.

Toàn bộ tài liệu DOCX được sinh từ source code bằng `docx.js`. Không chỉnh sửa thủ công file Word output.

## Yêu cầu môi trường

- Node.js + npm.
- LibreOffice có lệnh `libreoffice` hoặc `soffice` trên `PATH`.
- Python có UNO bridge của LibreOffice. Trên Debian/Ubuntu, `/usr/bin/python3` thường có module `uno`; có thể override bằng biến `LIBREOFFICE_PYTHON`.

LibreOffice được dùng trong pipeline để cập nhật và lưu cache mục lục. Vì vậy DOCX mở bằng Word hoặc LibreOffice đều hiển thị mục lục ngay, không cần người nhận tự nhấn `F9`.

## Sinh toàn bộ tài liệu

```bash
npm install
bash build_all.sh
```

Hoặc:

```bash
npm run build:all
```

Kết quả được ghi vào thư mục `generated/` và đồng thời tồn tại ở `/tmp`.

## Lệnh chi tiết

```bash
npm install
node build01.js
node build02.js
node build03_part2.js
node build04.js
node build05.js
node build06.js
node build07.js
node build08.js
node build_sds_full.js
```

Mỗi build script tự gọi `materialize_toc.py` qua helper `docx_output.js`, nên file output đã có TOC được materialize.

## Bản đồ file

| Script | Sinh ra |
|---|---|
| `acdocx.js` | Style, layout và helper dùng chung |
| `docx_output.js` | Ghi DOCX và gọi bước materialize TOC |
| `materialize_toc.py` | Cập nhật Writer document indexes/TOC bằng LibreOffice UNO |
| `build_all.sh` | Chạy toàn bộ generator và copy 9 DOCX vào `generated/` |
| `build01.js` | Phân tích thị trường v5 |
| `build02.js` | Giải pháp & Mô hình v5 |
| `build03_part1.js` + `build03_part2.js` | Kiến trúc v2 — chạy `node build03_part2.js` |
| `build04.js` | SDS Phần 1 — Nền tảng & chuẩn chung |
| `build05.js` | SDS Phần 2 — Core: contract/escrow/bank |
| `build06.js` | SDS Phần 3 — inspection/reputation/audit |
| `build07.js` | SDS Phần 4 — product/file/pricing |
| `build08.js` | SDS Phần 5 — user/notification/analytics + phụ lục |
| `build_sds_full.js` | SDS Full — ghép năm phần; tự truyền merged context cho Part 1 |

## Nguyên tắc sửa

- Nội dung nghiệp vụ: sửa đúng `buildXX.js` liên quan.
- Style/layout/helper dùng chung: sửa `acdocx.js`.
- Không đưa nội dung nghiệp vụ vào `acdocx.js`, `docx_output.js` hoặc `materialize_toc.py`.
- DDL trong tài liệu dùng MySQL 8; UUID được lưu dưới dạng `CHAR(36)` để dễ đọc. “UUID” trong mô tả domain vẫn là định dạng định danh, không phải native MySQL column type.
