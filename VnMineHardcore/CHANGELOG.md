# Changelog

## 2.32.0 — 2026-10-28

- Bổ sung nhiều lệnh WorldEdit tích hợp cho búa gỗ: chọn điểm/vùng, set, replace, walls, faces, overlay, center, count, move, stack, clipboard copy/cut/paste và undo/redo.
- Hỗ trợ ID block, block state và pattern trộn trọng số; giới hạn khối và áp dụng theo batch từng tick.
- Từ chối thao tác đụng block entity như rương/bảng hiệu để tránh làm mất dữ liệu; cập nhật README và plan.

## 2.31.0 — 2026-10-28

- `/vninit` thêm GUI bảo vệ world bắt đầu/reset: đặt/phá, tương tác, PvP, mob, thời gian, đói và khát.
- Thêm `/vnregion` và menu admin WorldEdit/WorldGuard: búa gỗ chọn vùng, đặt ID, lưu và chỉnh flags.
- Schema Build cấp ID world_schema_số, lưu vị trí/biến đổi/block nền, hỗ trợ `protect: ON`, `/vnbuild select`, `!edit`, `!copy`, `!remove`.
- Thêm xử lý bảo vệ vùng vào đặt/phá/tương tác/PvP/sinh mob và drain đói/khát.

## 2.30.0 — 2026-10-28

- Schema Build đọc Sponge Schematic `.schem` v2/v3 thay cho YAML `.schema` thử nghiệm.
- Thêm preview ngoài world có thể theo dõi vị trí khi admin di chuyển; `!put` xác nhận dựng, `!back` quay lại menu.
- Thêm thao tác lật trục X/Y/Z bên cạnh xoay công trình.
- Thêm lựa chọn NPC tutorial cho world bắt đầu/reset trong `/vninit`, cấu hình dialog và quest tự giao theo NPC.
