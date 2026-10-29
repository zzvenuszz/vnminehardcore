# Changelog

## 2.36.9 — 2026-10-29

- Làm rõ nút chọn NPC Tutorial trong menu sửa đại lục `/vninit`; danh sách hiển thị tên, ID, world và NPC đang được gán, chỉ liệt kê loại `tutorial` trong đúng world.
- Thêm nút bỏ gán NPC Tutorial của đại lục mà không xóa NPC.
- Bộ chọn NPC mục tiêu quest hiển thị loại NPC và xác nhận NPC Tutorial vẫn có thể chọn làm mục tiêu gặp NPC.

## 2.36.8 — 2026-10-29

- `/vninit` có nút vào quản lý quest theo chủng tộc và công tắc dịch chuyển độc lập sau khi chọn class.
- Mục tiêu quest nộp item dùng inventory cấu hình nhiều loại vật phẩm; mỗi stack trở thành một bước nộp, và item cấu hình được trả lại khi hủy/đóng.
- Điều kiện nhận quest chọn được quest hệ thống `first-steps`, đại lục và race từ GUI; điều kiện được kiểm tra khi hiển thị và nhận quest.
- Hiển thị tên cá tiếng Việt trong mục tiêu câu cá; mô tả item quest dễ đọc hơn. Tên item rơi dùng Minecraft translation component theo ngôn ngữ client.

## 2.36.7 — 2026-10-29

- Quản trị người chơi có nút reset toàn bộ tiến trình RPG, xóa hồ sơ đại lục/race/class, level/EXP, quest active/completed/daily và skill/quick slot trong một transaction; người đang online được đưa về lại bước tutorial.
- `/vninit` cấu hình nơi dịch chuyển và quest mặc định sau khi người chơi chọn race/class; vẫn hỗ trợ quest tự giao theo race.
- Sắp xếp lại quest objective editor thành vùng bước 2 hàng, kính phân cách, vùng lựa chọn 2 hàng có phân trang; hỗ trợ kéo-thả và xóa bước đầu khi còn bước khác.
- Thêm objective block-break, fishing, tame, enchant, brew, craft, sell/purchase, entity damage mob/boss, XP/level, consume và command; lưu/đọc YAML cùng event hooks.
- Chỉ ghi tiến độ sell/purchase sau khi giao dịch VNCOIN hoàn tất; giao dịch bị hủy/hoàn tiền không được tính.

## 2.34.1 — 2026-10-28

- Bổ sung revision cấu hình world để đổi THE_END → NORMAL rồi đổi lại trước restart vẫn đánh dấu cần tái tạo world.
- Lên lịch tái tạo một lần cho `world_start` đang bị kẹt dữ liệu dimension cũ; lần khởi động kế tiếp sẽ backup folder Paper thật và tạo lại NORMAL.

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
