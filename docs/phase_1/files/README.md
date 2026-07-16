# Phase 1 Word document builder

Các file Word Phase 1 được sinh bằng `docx.js` và helper `acdocx.js` do dự án cung cấp.

```bash
cd docs/phase_1/files
npm install docx
node build_phase1_docs.js
```

Script ghi bốn output vào `docs/`:

- `AgriContract_02_GiaiPhap_MoHinh.docx`
- `AgriContract_Architecture_v1_2.docx`
- `AgriContract_SDS_v1_3.docx`
- `AgriContract_TechnicalSpec_v1_2.docx`

Tên file cũ được giữ để không làm hỏng link/tài liệu bàn giao; version bên trong đã nâng lên v1.3 và ghi rõ baseline code trên `main` ngày 17/07/2026.

`acdocx.js` trong thư mục này là bản copy từ `/home/cuong/Downloads/acdocx.js` dùng cho lần cập nhật Phase 1 này. Không sử dụng hay sửa helper/build script trong `docs/phase_2`.
