# Changelog

## 2.34.0 — 2026-10-28

- Sửa tái tạo world khi environment/seed đổi trên Paper hiện đại: dùng đúng thư mục dimension thực tế thay vì giả định thư mục `<server>/<world>`.
- Lưu đường dẫn tương đối của managed world vào manifest; world cũ được chuyển sang backup bên cạnh thư mục dimension trước khi tạo world mới.
- Nếu không thể xác định duy nhất thư mục dữ liệu, từ chối thao tác và log lỗi thay vì đụng nhầm world.

## 2.33.0 — 2026-10-28

- Sửa luồng onboarding: người chơi chưa có nhân vật được đưa tới world bắt đầu, gặp NPC Tutorial rồi chọn đại lục, race và class; objective First Steps nhận diện lần gặp NPC này.
- Thêm menu admin gán quest tự nhận theo race trong races.yml; quest được nhận sau khi hồ sơ nhân vật được tạo.
- Thêm Admin → Quản lý world với quy tắc per-world cho block, tương tác, PvP, mob, boss, thiên tai, thời gian, đói và khát; áp dụng vào các manager gameplay liên quan.
- Làm rõ tương tác NPC Tutorial, ghi debug route khi bật console debug và bỏ yêu cầu NPC tutorial từng đại lục khỏi điều kiện chọn đại lục.

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
