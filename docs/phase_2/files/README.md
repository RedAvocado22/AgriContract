# AgriContract Phase 2 — Final DOCX build scripts

Bộ này tái sử dụng toàn bộ nội dung đã đồng bộ từ Phase 2 design, Verification Matrix và frozen contracts, nhưng render bằng pipeline canonical của repository:

`content/*.md -> build*.js -> md_to_acdocx.js -> acdocx.js -> docx_output.js -> materialize_toc.py`

Không chỉnh sửa thủ công file Word output.

## Sinh đúng 4 deliverable

```bash
npm install
bash build_all.sh
```

Kết quả trong `generated/`:

- `AgriContract_01_PhanTichThiTruong_final.docx`
- `AgriContract_02_GiaiPhap_MoHinh_final.docx`
- `AgriContract_Architecture_final.docx`
- `AgriContract_SDS_final.docx`

`build04.js` đến `build08.js` chỉ là module nội bộ chia SDS theo cụm nội dung. Chúng không sinh các file `SDS Part...`; `build_sds_full.js` ghép chúng thành một SDS duy nhất.

## Vai trò file

| File | Vai trò |
|---|---|
| `acdocx.js` | Canonical style/layout/helper của repository |
| `md_to_acdocx.js` | Adapter generic từ content Markdown sang helper `acdocx`; không chứa quyết định nghiệp vụ |
| `content/*.md` | Nội dung final đã đồng bộ |
| `build01.js` | Phân tích thị trường |
| `build02.js` | Giải pháp và mô hình |
| `build03_part1.js`, `build03_part2.js` | Architecture |
| `build04.js`–`build08.js` | Các module nội bộ của SDS hợp nhất |
| `build_sds_full.js` | Ghép và sinh SDS final |
| `docx_output.js` | Ghi DOCX và materialize TOC |
| `materialize_toc.py` | Cập nhật TOC bằng LibreOffice UNO |

## Yêu cầu

- Node.js + npm
- LibreOffice/soffice trên PATH
- Python có UNO bridge; mặc định `/usr/bin/python3`, override bằng `LIBREOFFICE_PYTHON`
