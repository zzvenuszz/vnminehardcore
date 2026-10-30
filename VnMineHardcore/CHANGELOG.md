# Changelog

## 2.38.1 — 2026-10-30

- Thêm `/realname <username|alias>`; alias được chuẩn hóa bằng cách bỏ màu/format trước khi so khớp, nên `/realname hoanbh` tìm được alias `&4hoanbh`. Nếu username và alias trỏ tới nhiều người, lệnh liệt kê các kết quả.
- Bộ chọn quest tự giao đầu tiên trong `/vninit` có nút tạo quest; quay lại từ editor trở về đúng bộ chọn.
- NPC editor có menu quest riêng để xem, gán quest có sẵn hoặc tạo quest mới với NPC giao đã điền sẵn.
- NPC editor có menu hội thoại riêng: tạo dialog private gắn với NPC hoặc public, chọn/sửa/xóa dialog; text được phát qua action bar trước khi mở menu chức năng NPC. Lưu `dialog-id` theo NPC trong `npcs.yml`.

## 2.38.0 — 2026-10-30

- Menu chọn quest tự giao cho NPC tutorial ở `/vninit` có bộ lọc theo đại lục và chủng tộc, có lựa chọn bỏ lọc và giữ bộ lọc khi chuyển trang.
- Quest được lọc theo scope khai báo trong điều kiện nhận hoặc objective `world-select`/`race-select`; quest dùng scope bất kỳ hoặc không giới hạn vẫn xuất hiện.
- Rà soát luồng onboarding, quest, NPC và dialog; bổ sung roadmap chuẩn hóa phân loại quest/dialog, lựa chọn tại chỗ và tránh cấu hình tự giao chồng chéo vào `plan.md`.

## 2.37.1 — 2026-10-30

- Chat thường áp dụng mã `&`/`§` cho nội dung tin nhắn và tên alias; người chơi cần quyền riêng cho từng màu/format/rainbow, OP được bỏ qua kiểm tra này.
- Giữ màu/format tên NPC trên action bar hội thoại truyện, quest và tutorial; cắt tên dài mà vẫn bảo toàn mã định dạng.
- Nếu người chơi tương tác với NPC Tutorial trong lúc hồ sơ đang tải, phát lại tương tác sau khi hồ sơ hoàn tất để mở luồng NPC bình thường thay vì hiện thông báo đã hoàn thành chung.

## 2.37.0 — 2026-10-30

- Đồng bộ NPC type `tutorial` với luồng quest-giver sau khi người chơi hoàn thành onboarding; tương tác lại hiển thị quest gắn bằng giver NPC ID và dialog theo trạng thái trong edit quest.
- Sửa bộ chọn quest-dialog mở đầu trong `/vninit`: chỉ liệt kê dialog public loại Có thể nhận; dialog theo trạng thái tiếp tục được tạo/gán trong quest editor, không còn lỗi thiếu quest context.
- `/vninit` thay nút sửa First Step riêng bằng GUI chọn quest RPG đầu tiên tự giao sau khi người chơi chọn race/class.
- Thêm objective tuần tự `world-select` và `race-select`, cho phép chọn đích cụ thể hoặc bất kỳ qua GUI; điều kiện nhận quest hỗ trợ yêu cầu đã chọn bất kỳ đại lục/race.
- OP được cấp lại mọi permission node do plugin khai báo sau khi áp dụng group/user attachment, tránh quyền deny trong attachment làm mất quyền của OP.
- Chuẩn hóa menu điều kiện quest theo 2 hàng danh sách, hàng kính ngăn cách, 2 hàng tùy chọn và hàng điều hướng; ghi quy tắc GUI dùng chung vào `.clinerules`.

## 2.36.16 — 2026-10-30

- Fix formatter `LegacyText`: văn bản thường không có marker rainbow trước đó trả về chuỗi rỗng, làm title quest bị từ chối khi plugin khởi động. Nay đường xử lý text thường vẫn dịch mã màu `&` và giữ nguyên nội dung.

## 2.36.15 — 2026-10-30

- Hoàn thiện cache quyền màu/format alias để kiểm tra Bukkit permissions khi quyền được cấp hoặc thu hồi, không gọi permission API từ luồng chat async.
- Chốt parser `&rl:` đúng cú pháp rainbow line, không làm lộ dấu hai chấm; hoàn thiện tên có màu/format trong boss bar và các nội dung cấu hình liên quan.

## 2.36.14 — 2026-10-30

- Thêm `/alias set <tên>` và `/alias` để đặt/xóa alias; admin dùng `/alias set <người_chơi> <tên>` hoặc `/alias clear <người_chơi>`. Alias được lưu bền vững, hiển thị trong chat, tab list, join/quit, death và bảng xếp hạng.
- Tách permission alias theo từng mã màu `vnmine.alias.color.<code>`, format `vnmine.alias.format.<code>` và `vnmine.alias.rainbow`; alias do admin đặt bỏ qua yêu cầu permission format của người chơi.
- Tạo bộ xử lý legacy text dùng chung cho màu/format `&` và rainbow `&rl`, `&r1`, `&r2`, `&r3`; áp dụng cho tên NPC/đại lục và các trường text quest, dialogue, sign, text display, leaderboard.
- Chuyển quest-dialog vào selector trạng thái trong editor quest: tạo dialog tại chỗ, gán loại Có thể nhận/Đang làm/Hoàn tất/Chưa đủ điều kiện, mặc định private theo quest; có tùy chọn public để quest khác cùng trạng thái sử dụng. Bỏ lệnh/menu quản lý dialog độc lập; dialog cũ được nạp như public để tương thích dữ liệu.

## 2.36.13 — 2026-10-29

- Fix click NPC player-model: bỏ `WrappedEnumEntityUseAction` vì ProtocolLib 5.4.0 không khởi tạo được wrapper này trên Paper 26.3; packet nhắm đến entity NPC ảo được nhận và chuyển vào luồng tương tác NPC.
- Sửa nút quay lại quản lý quest tự giao theo race để trở về đúng menu nguồn (như dashboard `/vninit`) sau khi mở quest, quay lại hoặc lưu lựa chọn.
- Bổ sung tên hiển thị cho đại lục bắt đầu trong `/vninit`; tên này thay “world bắt đầu” trong mục tiêu First Steps, scoreboard, chỉ đường và mô tả quest gặp NPC. Các quest hiển thị tên NPC thay cho ID khi có cấu hình tên.

## 2.36.12 — 2026-10-29

- Thêm `/debug ON|OFF` để đổi và lưu `logging.console-debug` vào `config.yml` ngay trong lúc server chạy; cập nhật cache config dùng chung để logger nhận trạng thái tức thì.
- Thêm permission quản trị `vnmine.command.debug`, tab completion ON/OFF và hướng dẫn lệnh trong `/vnhelp`.

## 2.36.11 — 2026-10-29

- `/vninit` bố trí danh sách đại lục vào hai hàng đầu, hàng thứ ba làm kính phân cách và đưa nhóm cài đặt xuống dưới; danh sách giữ phân trang.
- Nút First Step trong phần quest hướng dẫn mở editor riêng để đổi tên, mô tả, lời bắt đầu/hoàn thành và chọn objective: NPC Tutorial world bắt đầu, NPC Tutorial đại lục hoặc spawn đại lục. Tên mới được dùng trong scoreboard và prerequisite quest.
- Sửa tương tác NPC player-model: click packet có hand bị thiếu vẫn được nhận như click chính, chỉ bỏ click off-hand/attack; thêm log packet và trạng thái NPC hiện với `logging.console-debug`.

## 2.36.10 — 2026-10-29

- Lưu đường quay lại theo GUI nguồn để menu con mở từ `/vninit` và các nhánh khác trở về đúng nơi đã mở; chuẩn hóa nút quay lại thành Bàn chế tạo, trang trước thành thảm xanh lá mạ và trang tiếp thành thảm xanh nước biển.
- Sắp xếp lại ví VNCOIN với nút quay lại ở hàng cuối; thêm menu chuyển tiền cho người chơi online và nhập tên người chơi offline, các mức cộng/trừ 1, 10, 100, 1000 cùng màn hình xác nhận người nhận và số tiền.
- Thêm `/pay <tên người chơi> <số tiền>`; giao dịch chuyển tiền được ghi atomically trong một transaction DB và không cho phép tự chuyển, vượt số dư hay vượt giới hạn ví.
- Lịch sử ví chỉ hiện các giao dịch mua/bán tại shop; việc nhặt coin và các nguồn khác không còn lẫn vào lịch sử này.
- Schema Build chấp nhận schematic chứa `waterlogged=false` không được API server hỗ trợ bằng cách loại riêng thuộc tính false đó và giữ nguyên các thuộc tính còn lại như `axis=z`.

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
