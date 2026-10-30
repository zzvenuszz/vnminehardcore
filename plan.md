# Chuẩn hóa luồng quest và quản lý dialog — rà soát 2026-10-30

## Đã làm trong lượt này

- [x] Thêm lọc quest tự giao cho NPC tutorial trong `/vninit` theo đại lục và chủng tộc; bộ lọc có lựa chọn bất kỳ, phân trang và giữ lựa chọn khi đổi trang.
- [x] Áp dụng scope từ điều kiện `selected-continent`/`selected-race` và objective `world-select`/`race-select`. Quest không có scope cụ thể hoặc đặt `*` vẫn xuất hiện ở mọi bộ lọc.
- [x] Rà soát flow hiện tại và ghi các điểm cần thống nhất dưới đây; cập nhật README/changelog, version nguồn 2.38.0.
- [ ] Chưa build JAR/chưa thử trong server.

## Luồng người chơi hiện tại và điểm cần làm rõ

| Giai đoạn | Flow hiện có | Rủi ro/điểm chưa thống nhất | Quy trình đích |
|---|---|---|---|
| Vào server | Hồ sơ mới được đưa qua world bắt đầu; nhiệm vụ hướng dẫn yêu cầu gặp NPC; `/start` có thể mở lại lựa chọn đại lục. | Có onboarding state, quest `First Steps` legacy và quest catalog cùng tồn tại; người chơi/admin khó phân biệt nhiệm vụ hệ thống với quest RPG. | Một state machine onboarding duy nhất; mỗi bước hiển thị rõ điều kiện, NPC/world đích, cách tiếp tục và trạng thái đã hoàn thành. Quest legacy chỉ là adapter tương thích, không tạo luồng song song. |
| Chọn đại lục/race/class | NPC world bắt đầu mở chọn đại lục; sau đó NPC đại lục hướng dẫn chọn race/class; sách skill lấy theo mapping. | Các quest auto-grant được cấu hình ở global, world, race và class; hiện chưa có một thứ tự duy nhất và có thể cấp nhiều quest cùng lúc. | Quy định thứ tự: quest global sau khi tạo nhân vật, tiếp đó quest theo world, race, class; loại trùng ID, kiểm tra giới hạn 3 quest, ghi log nguồn cấp và hiển thị xác nhận rõ. |
| Tìm và nhận quest | NPC ghép quest theo giver ID; có dialog theo trạng thái, điều kiện, GUI nhận và tối đa 3 quest active. | NPC có thể có danh sách dài; status chưa thay thế được bộ lọc theo world/race/category; tên role của NPC không đủ để người chơi hiểu nhóm quest. | NPC mở tab/category (cốt truyện, hướng dẫn, săn quái, thu thập, boss, nghề nghiệp); chỉ hiện quest phù hợp với người chơi, có bộ lọc và trang; giữ dialog theo từng trạng thái. |
| Tiến hành quest | Objective tuần tự, tracking, nộp item, NPC/boss/thu thập và scoreboard đã có. | Hành vi tracking phụ thuộc loại objective; objective mới cần được kiểm tra mapping tới sự kiện, tiến độ, tên hiển thị, phần thưởng và world đích. | Mỗi objective phải khai báo đồng bộ: điều kiện kích hoạt, đích/tracking nếu có, tiến độ, tên bản địa hóa, tiêu chí hoàn tất và log debug. Wizard chỉ cho lưu cấu hình hợp lệ. |
| Hoàn tất/chuỗi quest | Có dialog hoàn thành, nhận thưởng, lặp lại, giới hạn ngày và quest tiếp theo. | Quest auto-grant ở onboarding/race và successor có thể đụng nhau hoặc cấp vượt giới hạn active. | Một hàng đợi quest tiếp theo có nguồn và thứ tự rõ; idempotent khi login/retry; báo rõ quest đã nhận, bị bỏ qua do trùng, hoặc đang chờ slot. |

## Đích chuẩn hóa quản trị quest

1. **Tạo quest bằng wizard nhiều bước:** thông tin cơ bản → category → NPC giao → scope world/race/class → điều kiện nhận → objective tuần tự → reward/giới hạn lặp → dialog → xem trước → lưu/bật. Có thể quay lại sửa và lưu nháp.
2. **Phân loại rõ:** category bắt buộc (tutorial, story, side, daily, hunt, gather, boss, profession, class/race); tag tùy chọn. Filter theo category, NPC, world, race, trạng thái enabled và từ khóa; mặc định không đổ toàn bộ quest vào một trang.
3. **Chọn từ dữ liệu đã lưu:** NPC/world/race/class/item/boss/quest/dialog phải là GUI picker có tìm kiếm/phân trang. Ngay trong picker có nút tạo mới khi admin có quyền, sau khi lưu quay lại đúng picker và giữ các filter.
4. **Scope và điều kiện tách bạch:** scope xác định quest thuộc world/race/category nào để tìm kiếm; requirements quyết định người chơi có đủ điều kiện nhận hay không. Không dùng requirement như cách phân loại duy nhất.
5. **Preview trước khi lưu:** xem đúng tên NPC/world/item, các bước objective, điều kiện, dialog theo trạng thái, reward, repeat limit và cách quest hiện trong GUI người chơi.

## Đích chuẩn hóa dialog/NPC

- Dialog quest riêng nằm trong edit quest và khóa theo quest + trạng thái; dialog public có category, trạng thái, ngôn ngữ/tags, người tạo và phạm vi dùng.
- Dialog mở đầu/tutorial/story gắn trực tiếp với NPC hoặc chapter; dialog public có picker riêng, không trộn vào list private của mọi quest.
- Bộ quản lý có nhóm theo category → NPC/chapter → public/private → trạng thái; tìm kiếm theo ID/tên, preview nội dung và hiển thị nơi đang được tham chiếu trước khi sửa/xóa.
- Chỉ cho chọn dialog cùng loại trạng thái; dialog được dùng ở nhiều nơi phải có xác nhận trước khi sửa nội dung dùng chung.

## Các phase tiếp theo chưa làm

- [ ] Phase 1: thêm category/tag cho quest, migration an toàn cho quest cũ và filter category/NPC/world/race trong admin list.
- [ ] Phase 2: thống nhất API picker dùng chung (tìm kiếm, phân trang, tạo mới tại chỗ, quay lại giữ state) cho quest/NPC/world/race/item/boss.
- [ ] Phase 3: wizard tạo quest và preview; validate objective/scope/reward trước khi bật.
- [ ] Phase 4: tổ chức dialog theo NPC/chapter, category và public/private; có tham chiếu sử dụng và bảo vệ dialog public đang được dùng.
- [ ] Phase 5: chuẩn hóa bộ điều phối onboarding và auto-grant theo thứ tự global → world → race → class → successor, chống cấp trùng và log nguyên nhân bỏ qua.
- [ ] Phase 6: kiểm thử flow người chơi mới, từng nhánh điều kiện, NPC nhiều quest, dialog 4 trạng thái, nộp item, world khác, repeat/daily và full 3 quest.

# Chat formatting và tên NPC trong hội thoại — yêu cầu 2026-10-30

- [x] Áp dụng mã màu/format/rainbow cho nội dung tin nhắn chat theo quyền riêng từng mã; OP được bỏ qua permission.
- [x] Dùng chung bộ lọc permission cho alias và text chat; alias do admin đặt vẫn giữ quyền dùng mọi định dạng.
- [x] Bảo toàn tên NPC đã format trong hội thoại story, quest và tutorial; cắt tên dài trên action bar không làm mất mã màu.
- [x] Khi click NPC Tutorial lúc hồ sơ đang tải, phát lại tương tác sau khi hồ sơ hoàn tất thay vì chỉ gửi thông báo chung.
- [x] Cập nhật README/changelog, tăng version nguồn lên 2.37.1.
- [ ] Chưa build JAR/chưa thử trong server. Xác minh chat bằng OP, người chơi có một quyền màu/format, người không có quyền; alias admin/player; NPC story/quest/tutorial có tên màu.

# OP, NPC tutorial và mục tiêu lựa chọn nhân vật — yêu cầu 2026-10-30

- [x] OP giữ mọi permission plugin sau khi PermissionManager áp dụng group/user attachments, kể cả khi attachment chứa deny cho node plugin.
- [x] NPC tutorial sau khi hoàn tất onboarding tiếp tục vào cùng luồng quest list/quest-dialog theo trạng thái như quest-giver; giữ riêng luồng hội thoại chọn đại lục/race khi người chơi còn trong onboarding.
- [x] Sửa bộ chọn dialog mở đầu NPC tutorial: context public `AVAILABLE`, chỉ hiển thị dialog public tương ứng; dialog theo trạng thái tạo và gán trong edit quest, không hiện lỗi thiếu quest/state.
- [x] `/vninit` thay nút sửa First Step bằng GUI chọn quest RPG đầu tiên tự giao sau khi người chơi chọn race/class; quest nội dung tiếp tục quản lý trong `/vnquest admin`.
- [x] Thêm mục tiêu tuần tự `world-select` và `race-select`, mỗi loại chọn đích cụ thể hoặc “bất kỳ” bằng GUI, lưu được vào quest definitions và ghi nhận khi người chơi chọn nhân vật.
- [x] Điều kiện nhận quest đại lục/race hỗ trợ đích cụ thể hoặc “bất kỳ”; bất kỳ nghĩa là hồ sơ đã có lựa chọn hợp lệ.
- [x] Menu điều kiện quest theo bố cục hai hàng dữ liệu, một hàng glass ngăn cách, hai hàng thao tác và hàng cuối cho điều hướng; cập nhật `.clinerules` thành quy tắc bố cục dùng chung.
- [x] Cập nhật README/changelog, tăng version nguồn lên 2.37.0.
- [ ] Chưa build JAR và chưa xác minh trong server thật. Sau build cần thử OP có deny permission trong group; NPC tutorial trong onboarding và sau onboarding; quest-dialog public/private; objectives/requirements chọn world/race cụ thể/bất kỳ; quest đầu tiên được chọn trong `/vninit`.

## Fix plugin tự disable khi khởi động 2.36.15 — log 2026-10-30

- [x] `latest.log` cho thấy plugin 2.36.15 dừng ở `QuestCatalogManager.loadDefinitions()` với `Quest 'world-start-class-nhan-toc-selected' has invalid fields or unsupported bounds.`
- [x] Nguyên nhân: `LegacyText.translate()` trả chuỗi rỗng cho input không chứa rainbow marker; tên quest First Step bị coi là trống. Các lỗi `/feed`, `/debug` và tab-completion sau đó xảy ra do plugin đã bị disable.
- [x] Sửa formatter để text bình thường vẫn được chuyển màu legacy và giữ nguyên nội dung.
- [x] Build Java 25 bằng `mvn -DskipTests package`, version 2.36.16; JAR 3,106,873 bytes, SHA-256 `dbae8f8f54cdc2f6a08b6fc8356ee137653c811676d6d33ad402de078b3e52a9`; có SQLite native Linux x86_64 và AArch64.
- [x] Sao lưu 2.36.15 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.36.15-pre-2.36.16-20261030.jar`, cài 2.36.16 tại thư mục plugin; chỉ còn một JAR VnMineHardcore ở cấp thư mục plugin.
- [ ] Restart xác nhận plugin enable cùng quest catalog load thành công.

## Alias, format text và quest-dialog riêng theo quest — yêu cầu 2026-10-30

- [x] Thêm formatter legacy dùng chung cho mã màu `&0–&f`, format `&k–&o`, `&r` và rainbow `&rl`, `&r1`, `&r2`, `&r3`; áp dụng cho tên NPC/đại lục, text display, sign, nhãn quest/item, dialogue và nội dung leaderboard có cấu hình.
- [x] Thêm `/alias set <tên>` và `/alias` để xóa alias; hỗ trợ alias có khoảng trắng và lưu theo UUID trong `aliases.yml`.
- [x] Áp dụng alias ở chat thường, tab list, thông báo join/quit/death và tên bảng xếp hạng; quản trị viên có thể đặt/xóa alias cho online/offline player.
- [x] Khai báo permission riêng theo từng màu (`vnmine.alias.color.<code>`), format (`vnmine.alias.format.<code>`) và rainbow (`vnmine.alias.rainbow`). Người đặt alias chỉ được dùng các mã được cấp; alias do admin đặt không cần cấp mã format cho người chơi.
- [x] Bỏ mục quản lý quest-dialog độc lập và đưa tạo/sửa dialog vào selector của từng trạng thái trong quest editor. Dialog mới lưu stage type, mặc định private theo quest; public có thể được dùng bởi quest khác cùng stage.
- [x] Duy trì dữ liệu dialog trước đây bằng cách nạp schema cũ như dialog public legacy; dialog tạo mới luôn có stage rõ ràng.
- [x] Cache quyền màu/format alias; cập nhật cache sau khi PermissionManager áp dụng lại quyền, để lọc mã ngay cả khi nhóm hoặc quyền người chơi thay đổi.
- [x] Build Java 25 bằng `mvn -DskipTests package`, version 2.36.15; JAR 3,106,833 bytes, SHA-256 `de5ae7866063815e9ad74eb83d7f4b9c855941811dd39ca366164e6973c04638`; JAR có SQLite native Linux x86_64 và AArch64.
- [x] Sao lưu 2.36.14 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.36.14-pre-2.36.15-20261030.jar`, cài 2.36.15 tại thư mục plugin; chỉ còn một JAR VnMineHardcore ở thư mục plugin.
- [ ] Kiểm thử runtime: chat/tab list alias, từng permission format, admin đặt alias cho offline player, reset alias; tạo private/public dialog ở đủ bốn trạng thái và xác nhận lọc không lộ dialog private của quest khác.

## Sửa lỗi tương tác NPC Tutorial và onboarding — yêu cầu 2026-10-29

- [x] Log server 2.36.12 cho thấy ProtocolLib 5.4.0 ném `ExceptionInInitializerError` trong `WrappedEnumEntityUseAction.<clinit>` khi nhận packet tương tác trên Paper 26.3. Các lần click sau đó tiếp tục ném `NoClassDefFoundError`, làm listener không xử lý packet NPC player-model.
- [x] Loại bỏ wrapper enum không tương thích; listener chỉ giải mã entity ID, kiểm tra map NPC ảo, chặn packet khớp và gọi luồng tương tác NPC trên server thread. Debug log giữ player/NPC/entity ID.
- [x] Sửa nhánh quản lý quest tự giao theo race: GUI giữ inventory cha từ lúc vào qua `/vninit`; nút quay lại và lưu lựa chọn tiếp tục giữ đúng inventory nguồn, không trỏ ngược vào GUI trung gian.
- [x] Thêm trường tên hiển thị đại lục bắt đầu trong `/vninit`, lưu vào `rpg.onboarding.start-world.display-name`, giới hạn 1–48 ký tự; tên này không đổi tên world.
- [x] Thay nhãn “NPC Tutorial tại world bắt đầu” bằng tên NPC được gán và tên hiển thị đại lục trong First Steps, scoreboard/điều hướng; objective `VISIT_NPC` cũng hiển thị tên NPC thay cho ID, và dùng tên đại lục khi đó là NPC Tutorial world bắt đầu.
- [x] Build Java 25 bằng `mvn -DskipTests package`, version 2.36.13; JAR 3,085,857 bytes, SHA-256 `659f2fce7c159f861e895276c4ea2680d999303fc934dc9d3d051a68b26061cc`; đã xác nhận có SQLite native Linux x86_64 và AArch64.
- [x] Sao lưu 2.36.12 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.36.12-pre-2.36.13-20261029.jar`, cài 2.36.13 vào thư mục plugin; chỉ còn một JAR VnMineHardcore ở thư mục plugin.
- [ ] Còn một cảnh báo log độc lập: đại lục `world-ma-gioi` chưa có world được nạp/spawn hợp lệ. Gán hoặc tạo đúng world trong `/vninit` trước khi kiểm thử tracking/quest của đại lục này.
- [ ] Sau khi cài JAR, thử click NPC tutorial trước/sau khi reset nhân vật và chỉnh quest; mở `/vninit` → quest theo race → quay lại, rồi xác nhận trở về đúng dashboard.

## Lệnh bật/tắt console debug trong game — yêu cầu 2026-10-29

- [x] Thêm `/debug ON|OFF`; chỉ người có `vnmine.command.debug` (mặc định OP) dùng được. Sai cú pháp hiển thị `/debug ON|OFF`; Tab gợi ý ON/OFF.
- [x] Lệnh cập nhật `logging.console-debug` tại runtime và lưu `config.yml`; đồng bộ `ConfigManager.consoleDebug` để logger đổi ngay, không cần restart/reload.
- [x] Đưa lệnh vào `/vnhelp`, README và plugin.yml.
- [x] Build Java 25 bằng `mvn -DskipTests package`, version 2.36.12; JAR 3,084,573 bytes, SHA-256 `9b9613809ec6e2bf39f7767329a02c5677302e6cd8b9f6c91a41ae779c889a2a`; kèm SQLite native Linux x86_64 và AArch64.
- [x] Backup JAR 2.36.11, cài JAR 2.36.12 vào thư mục plugin và push mã nguồn.
- [ ] Sau restart server, thử `/debug OFF`, `/debug ON`, `/debug` sai cú pháp và xác nhận `config.yml` giữ trạng thái sau restart.

## Bố cục /vninit, editor First Step và NPC Tutorial world bắt đầu — yêu cầu 2026-10-29

- [x] `/vninit`: đặt tối đa 18 đại lục vào hai hàng đầu; hàng 3 dùng stained glass làm vạch ngăn; nhóm chức năng chuyển xuống dưới. Phân trang danh sách đại lục; chọn realm mở editor như trước.
- [x] Dashboard có các nút riêng, dễ nhận diện cho quản lý đại lục, NPC Tutorial world bắt đầu, bảo vệ world, tên/environment/seed, hành động sau tạo nhân vật, quest theo race, sách skill và onboarding.
- [x] Trong **Quest RPG và quest hướng dẫn**, mở **Sửa First Step** để đổi tên, mô tả, start-message, complete-message và objective; các objective hiện hỗ trợ là NPC Tutorial world bắt đầu, NPC Tutorial đại lục đã chọn hoặc spawn đại lục. Cấu hình ghi vào `quests/tutorial/first-steps.yml` và nạp lại ngay.
- [x] Tên First Step mới được dùng trên scoreboard onboarding và trong chọn prerequisite quest; dòng objective tutorial phản ánh loại objective đã cấu hình.
- [x] NPC player-model nhận click `INTERACT`/`INTERACT_AT` cả khi packet 26.3 không giải mã được hand; vẫn bỏ qua attack và click off-hand. Thêm debug log khi NPC xuất hiện, packet được nhận, packet không khớp ID hoặc giải mã thất bại.
- [x] Build Java 25 bằng `mvn -DskipTests package`, version 2.36.11; JAR 3,083,806 bytes, SHA-256 `f930a12b12bd098a6c1df1c9cc0a718a0a22385ec24ebab4b38d161889416b07`; kèm SQLite native Linux x86_64 và AArch64.
- [x] Backup JAR 2.36.10, cài JAR 2.36.11 vào thư mục plugin, cập nhật README/CHANGELOG và push mã nguồn.
- [ ] Kiểm tra trong game: dashboard đúng bố cục và phân trang; sửa title/objective First Step, relog để xác nhận title và tracking; thử tương tác NPC Tutorial trong world bắt đầu. Nếu vẫn lỗi, bật `logging.console-debug: true` và gửi các dòng `[RPG-DEBUG][NPC][Interaction]` cùng `[Onboarding]`.

## Điều hướng GUI, chuyển VNCOIN và tương thích schematic — yêu cầu 2026-10-29

- [x] Theo dõi inventory plugin đang mở để nút quay lại điều hướng về GUI nguồn thực tế; giữ lại lịch sử khi GUI tạm đóng để nhập tên người chơi qua chat. Chuẩn hóa icon mọi GUI: quay lại = bàn chế tạo, trang trước = thảm xanh lá mạ, trang tiếp = thảm xanh nước biển.
- [x] Cân lại menu ví: số dư, lịch sử mua/bán và chuyển tiền nằm tách biệt; nút quay lại ở hàng cuối. Menu chuyển tiền có danh sách online, nhập tên offline, điều chỉnh ±1/10/100/1000 và xác nhận rõ tên cùng số tiền trước giao dịch.
- [x] Thêm `/pay <tên người chơi> <số tiền>`; cả lệnh và GUI đều yêu cầu xác nhận trước khi gửi. DB ghi trừ ví người gửi/cộng ví người nhận trong cùng transaction, kiểm tra số dư và giới hạn số dư.
- [x] Lịch sử gần đây chỉ truy vấn giao dịch merchant mua/bán, không hiển thị nhặt coin hay nguồn khác.
- [x] Schema Build bỏ thuộc tính `waterlogged=false` khi block state không hỗ trợ thuộc tính này; giữ các state còn lại và chỉ bỏ qua đúng state khi parser sau chuẩn hóa vẫn thất bại.
- [x] Build Java 25 bằng `mvn -DskipTests package`, version 2.36.10; JAR 3,078,926 bytes, SHA-256 `546c5b0cfd6feb6faf1d67ada63f74ce347fb999d23d1d95c76f2fec1a74cef7`; đóng gói kèm SQLite native Linux x86_64 và AArch64.
- [x] Sao lưu JAR 2.36.9 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.36.9-pre-2.36.10-20261029.jar`, cài bản 2.36.10 vào thư mục plugin; SHA-256 khớp artifact và chỉ còn một JAR VnMineHardcore ở cấp thư mục plugin.
- [x] Cập nhật README/CHANGELOG và commit/push mã nguồn lên GitHub.
- [ ] Kiểm thử GUI: mở quản lý race quest từ `/vninit` và xác nhận từng nút quay lại theo đúng nhánh; rà vị trí icon phân trang trong các GUI lớn.
- [ ] Kiểm thử chuyển VNCOIN online/offline, hủy xác nhận, nhập tên sai, chuyển 1/10/100/1000, không đủ tiền, ví người nhận gần giới hạn, `/pay` và lịch sử mua/bán.
- [ ] Kiểm thử Schema Build bằng `.schem` có `minecraft:chain[axis=z,waterlogged=false]`; xác nhận chain và hướng trục được giữ đúng trong công trình.

## Chọn NPC Tutorial theo world đại lục — yêu cầu 2026-10-29

- [x] Trong `/vninit` → **Quản lý đại lục/world** → chọn đại lục, làm rõ nút mở danh sách NPC Tutorial theo đúng world của đại lục.
- [x] Danh sách chỉ hiện NPC `tutorial` đang được đặt tại world đó; hiển thị tên, ID, world và đánh dấu NPC đang gán. Có nút bỏ gán liên kết mà không xóa NPC.
- [x] Bộ chọn NPC mục tiêu quest tiếp tục liệt kê mọi NPC, gồm NPC `tutorial`; giao diện ghi rõ loại và xác nhận NPC Tutorial dùng được cho mục tiêu gặp NPC.
- [x] Build Java 25 bằng `mvn -DskipTests package`, version 2.36.9; JAR 3,057,373 bytes, SHA-256 `8e6eeff3c2aeb517f88dc290e5fbfa56011892eb08291d9a78bf66b64f034ce0`; xác nhận SQLite native Linux x86_64 và AArch64 có trong JAR. Test suite bỏ qua.
- [x] Sao lưu JAR 2.36.8 trước khi thay bằng bản 2.36.9 trong thư mục plugin; checksum cài đặt trùng artifact và chỉ giữ một JAR VnMineHardcore ở cấp thư mục plugin.
- [ ] Kiểm thử trong game: tạo 2 NPC tutorial ở hai world, xác nhận mỗi realm chỉ thấy NPC đúng world; chọn/bỏ chọn; mở `/vnquest admin` và chọn NPC tutorial làm objective gặp NPC.

## Khả năng nhìn thấy race, quest requirement và tên item — yêu cầu 2026-10-29

- [x] Đưa nút quản lý quest tự giao theo chủng tộc lên trang chính `/vninit`; cho phép quản trị viên có quyền setup mở menu race trực tiếp, không cần tìm trong Admin menu.
- [x] Thêm công tắc riêng `rpg.onboarding.character-complete.teleport-enabled`; GUI thể hiện BẬT/TẮT. Tắt công tắc thì sau khi chọn class người chơi ở nguyên vị trí hoàn tất tutorial; chọn một điểm đến sẽ tự bật công tắc lại.
- [x] Mục tiêu quest “Nộp item” mở inventory cấu hình; admin đặt nhiều loại item với số lượng bằng stack, lưu thành các bước nộp liên tiếp. Hủy/đóng menu sẽ trả item cấu hình lại cho admin.
- [x] Tên mục tiêu câu cá hiển thị tiếng Việt (`COD` → cá tuyết, `SALMON` → cá hồi, v.v.). Các objective dùng material/item ID hiển thị tên dễ đọc cho block, craft, consume, sell/purchase, collect và submit; item RPG giữ tên custom.
- [x] Nhãn item rơi dùng Adventure translatable component theo ngôn ngữ client Minecraft, giữ tên custom của item RPG và nối nhãn giá VNCOIN.
- [x] Thêm requirement quest chọn từ GUI: hoàn thành quest có sẵn hoặc quest hệ thống `first-steps` (“Bước chân đầu tiên”), đã chọn đại lục, đã chọn chủng tộc. Kiểm tra điều kiện lúc liệt kê quest và lúc nhận quest; lưu ID trong YAML.
- [x] Khi tutorial `first-steps` hoàn thành, cập nhật cache quest đang online ngay; admin reset tutorial cũng xóa cache prerequisite để trạng thái điều kiện khớp DB.
- [x] Cập nhật README/CHANGELOG và version source lên 2.36.8. Build Java 25 bằng `mvn -DskipTests package`; JAR 3,059,509 bytes, SHA-256 `0fdee3f89cf8993151cbdeae43fd3a1f61fcf6bd48280ce2e3aa56b9202eb71c`; xác nhận JAR chứa SQLite native Linux x86_64 và AArch64. Test suite bỏ qua.
- [x] Sao lưu JAR 2.36.7 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.36.7-pre-2.36.8-20261029.jar`, cài 2.36.8 vào thư mục `plugins`, xác nhận SHA khớp và chỉ còn một JAR VnMineHardcore được Paper nạp.
- [ ] Kiểm thử trong game: bật/tắt dịch chuyển, mở menu race từ `/vninit`, đặt nhiều loại item nộp (kể cả item RPG), hủy menu để xác nhận trả item, quest COD/submit, prerequisite `first-steps`, realm và race, tên drop theo ngôn ngữ client.

## Reset người chơi, onboarding đích đến và mở rộng objective quest — yêu cầu 2026-10-29

Tham chiếu loại objective: [zQuests quest types](https://docs.groupez.dev/zquests/configurations/quest-types). Danh sách tham khảo gồm block, entity, item, potion, economy, progression và command; cần chỉ bật loại nào có event và dữ liệu giao dịch đáng tin cậy trong plugin.

- [x] Nút reset toàn bộ RPG chạy trong một transaction: xóa profile/chọn đại lục, quest active/completed/daily/successor, skill và quick slot; giữ VNCOIN cùng inventory, ban/mute và lịch sử chết.
- [x] Reset người chơi online xóa cờ onboarding/starter cũ, đưa về world start và chờ nói chuyện NPC tutorial; không tự cấp quest hoặc EXP từ callback reset.
- [x] `/vninit` có menu cấu hình hành động sau tạo nhân vật: spawn đại lục, spawn world bắt đầu, vị trí admin đã lưu, hoặc không dịch chuyển; chọn quest mặc định từ GUI. Quest gắn theo race vẫn có thể cấp riêng.
- [x] Objective editor dùng hai hàng đầu hiển thị các bước, hàng giữa là kính phân cách, hai hàng dưới chọn loại; cả hai vùng có nút phân trang riêng. Danh sách loại kéo dài thêm trang khi vượt 18 lựa chọn.
- [x] Có thể xóa bước đầu khi còn bước khác; kéo-thả bước sang bước khác để đổi thứ tự, và vẫn có thao tác bấm hai bước làm phương án thay thế. Không cho xóa mục tiêu cuối để quest không rơi vào trạng thái không có điều kiện hoàn thành.
- [x] Thêm objective `block-break`, `fishing` (loại cá hoặc ANY), `tame`, `craft`, `item-consume`, `enchant`, `brew`, `sell`, `purchase`, `entity-damage` (mob vanilla hoặc boss theo ID), `experience-gain` (điểm hoặc level), `command`; vẫn giữ kill mob/boss, thu thập/nộp item và gặp NPC.
- [x] Mục tiêu lưu/đọc YAML; event hook cập nhật tiến độ cho block, câu cá, thuần hóa, craft, consume, enchant, brewing, sát thương trực tiếp/projectile, XP, level và lệnh. Mục tiêu mua/bán chỉ tăng sau giao dịch merchant đã áp dụng thành công; giao dịch hủy/hoàn tiền không được tính. Shop ID là tùy chọn.
- [x] Mỗi loại có icon, tên, mô tả và ví dụ nhập hợp lệ trong GUI/chat; chọn NPC/boss có sẵn qua GUI. Quest vẫn giới hạn 45 bước tổng.
- [x] Tăng version lên 2.36.7, cập nhật README/CHANGELOG; build lại bằng Java 25 (`mvn -DskipTests package`) ngày 2026-10-29 09:40 UTC. Build thành công; 61 source Java và 14 test source biên dịch, test suite được bỏ qua.
- [x] JAR 2.36.7 SHA-256 `108f23b649e34f1dd4896f12f491b3cb35dd18e4aaf3a2a901a84b63c5440632`, 3,048,616 bytes; xác nhận đóng gói SQLite native Linux x86_64 và AArch64.
- [x] Backup bản cài trước khi build lại tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.36.7-pre-rebuild-20261029.jar`; thay JAR tại thư mục plugin bằng bản vừa build, xác nhận SHA khớp và chỉ còn một JAR VnMineHardcore đang hoạt động.
- [ ] Kiểm thử trên server: thử kéo-thả qua trang mục tiêu, từng event objective, brewing stand attribution, shop system/custom, boss-ID, level/XP và command; xác nhận tiến độ sau relog và không ghi nhận sự kiện bị hủy.
- [ ] Kiểm thử reset toàn bộ trên người chơi online/offline và từng kiểu đích onboarding (realm, start world, vị trí admin, không teleport), xác nhận quest mặc định và quest theo race không vượt giới hạn nhận quest.
- [ ] Mục tiêu cuối cùng không thể xóa; muốn cho phép quest không có objective cần thêm trạng thái draft/disabled và chặn người chơi nhận quest đó.

## Chọn tham chiếu đã lưu bằng GUI — yêu cầu 2026-10-28

- [x] Trình sửa quest tải NPC đã lưu để chọn NPC giao quest, NPC mục tiêu và NPC của bước đầu; giữ lại nhập chat cho dữ liệu mới như tên quest, số lượng, loại entity và ID mới.
- [x] Thêm GUI chọn boss từ cấu hình boss hiện có trước khi nhập số lần hạ.
- [x] Quản lý race tải các quest đang bật để admin bấm gán quest tự giao sau khi chọn race; hỗ trợ phân trang và bỏ gán.
- [x] Kiểm tra lại tham chiếu khi lưu để tránh dùng NPC/quest đã bị xóa hoặc vô hiệu hóa trong lúc GUI đang mở.
- [x] Tăng version source lên 2.36.6; ghi nhận thay đổi trong README/CHANGELOG.
- [x] Build JAR 2.36.6 bằng Java 25 (`mvn -DskipTests package`) lúc `2026-10-28 15:12 UTC`; SHA-256 `6116149fa9dd4d795aca7fd9b34b03c606d8fe919c1224cf122615a425c529c2`. Xác minh SQLite native Linux x86_64/AArch64; test suite bỏ qua.
- [x] Backup 2.36.5 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.36.5.jar`, cài 2.36.6 tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.36.6.jar`; checksum khớp và chỉ còn một JAR plugin ở thư mục gốc.
- [ ] Kiểm thử trong game: gán NPC giao quest, thêm NPC objective, chọn NPC bước đầu, thêm boss objective, gán/bỏ quest tự giao cho race.

## Animation rainbow cho tên NPC — yêu cầu 2026-10-28

- [x] Thêm `&rl:`/`§rl:`: toàn đoạn đổi đồng màu; `&r1:`/`§r1:`: từng ký tự bắt đầu đổi màu từ trái sang phải; `&r2:`: từ phải sang trái; `&r3:`: bắt đầu ở giữa rồi lan ra hai phía. `r1`–`r3` nhận cả dạng rút gọn không có `:`, như `&r3123456`.
- [x] Hiệu ứng chạy lặp vô hạn, bảng màu cầu vồng 7 màu; nhiều đoạn được tạo bằng cách mở một mã hiệu ứng mới. Đoạn hiệu ứng kết thúc tại mã rainbow kế tiếp hoặc hết tên.
- [x] Giữ màu/format legacy trong tên, xử lý mã hiệu ứng trước `&r` reset. Cập nhật gợi ý trong GUI edit NPC và tài liệu README.
- [x] Chỉ cập nhật TextDisplay mỗi 4 tick cho NPC có animation, đang trong phạm vi viewer; không gửi animation packet cho tên tĩnh.
- [x] Tăng version source lên 2.36.5.
- [x] Build JAR 2.36.5 bằng Java 25 (`mvn -DskipTests package`) lúc `2026-10-28 14:28:04 UTC`; 3.024.962 bytes, SHA-256 `89852b2b8cf7f8e1df1116543094137219493eba4fc72c2ef278c98ae612d191`. Xác minh SQLite native Linux x86_64/AArch64; test suite bỏ qua.
- [x] Backup 2.36.4 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.36.4.jar`, cài 2.36.5 tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.36.5.jar`; checksum khớp artifact và thư mục plugin chỉ còn một JAR VnMineHardcore.
- [ ] Gameplay test: thử cả bốn mã, màu/format đi kèm, nhiều đoạn trong một tên, rút gọn `&r3123456`, đổi tên/di chuyển NPC và ngắt kết nối viewer; kiểm tra tải packet khi đông NPC.

## Khôi phục tên/màu NPC player sau sửa protocol — phản hồi 2026-10-28

- Sau bản 2.36.3, NPC không còn gây ngắt kết nối nhưng `&4Welcome` hiện thành `Welcome_3e2s` màu trắng vì tên được lấy từ profile của fake-player.
- [x] Ẩn profile name của fake-player; tạo nhãn TextDisplay riêng từ tên NPC đã parse mã màu legacy `&`, giữ nguyên tên và màu.
- [x] Dọn TextDisplay khi NPC rời phạm vi, đổi world hoặc viewer thoát; không gửi lại `ENTITY_METADATA` cho entity Player.
- [x] Tăng phiên bản source lên 2.36.4; cập nhật README/CHANGELOG.
- [x] Build JAR 2.36.4 bằng Java 25 (`mvn -DskipTests package`) lúc `2026-10-28 14:04:56 UTC`; 3.018.268 bytes, SHA-256 `dc2fd884855a3d83227c932b93c2d446ff6e5bc9e15652b78b50b919b1df706b`. Đã xác minh native SQLite Linux x86_64/AArch64; test suite bỏ qua.
- [x] Backup 2.36.3 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.36.3.jar`, cài 2.36.4 tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.36.4.jar`; checksum khớp và chỉ còn một JAR plugin ở cấp thư mục.
- [ ] Gameplay test Paper 26.3: `&4Welcome` hiển thị đỏ sẫm, các màu/format khác giữ đúng; đăng nhập không bị disconnect.

## Sửa disconnect sau khi NPC player xuất hiện — phản hồi test 2026-10-28

- Người chơi kết nối được, thấy NPC thoáng qua dưới dạng player, rồi client báo lỗi giao thức và ngắt. Server chỉ ghi `lost connection: Disconnected`, không có decoder stack trace; thời điểm lỗi trùng lúc packet fake-player được gửi.
- [x] Bỏ `ENTITY_METADATA` tạo bằng chỉ số hard-code để tránh gói metadata không tương thích client 26.3. Player dùng metadata mặc định; tên NPC được làm sạch thành profile name hợp lệ (tối đa 16 ký tự).
- [x] Tăng phiên bản source lên 2.36.3; cập nhật CHANGELOG/README.
- [x] Build JAR 2.36.3 bằng Java 25 (`mvn -DskipTests package`) lúc `2026-10-28 13:57:25 UTC`; 3.017.703 bytes, SHA-256 `3f3e292db72eadf3683f70253f92e02996de703fd5309befc304ddbdc9d6d012`. Xác minh JAR chứa SQLite native Linux x86_64 và AArch64; test suite được bỏ qua.
- [x] Sao lưu 2.36.2 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.36.2.jar`, cài 2.36.3 tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.36.3.jar`; checksum cài khớp artifact và thư mục plugin chỉ còn một JAR VnMineHardcore.
- [ ] Chưa thể xác nhận hết disconnect khi chưa có client log hoặc lần thử sau khi cài bản sửa. Nếu còn lỗi, cần `logs/latest.log` cùng `logs/debug.log` phía client tại thời điểm disconnect.

# Kế hoạch hoàn thiện VnMineHardcore: RPG, VNCOIN và vận hành server

## Schema Build cho admin — yêu cầu 2026-10-28

- [x] Thêm mục **Schema Build** trong Menu chính → Admin và quyền `vnmine.command.schemabuild`; tự tạo `plugins/VnMineHardcore/schemas/` cùng file mẫu.
- [~] Quét `.schem` theo cây thư mục con; parser Sponge Schematic v2/v3 gzip NBT có kiểm tra palette/block state/kích thước/volume, nhưng chưa xác minh với các file xuất từ WorldEdit/Litematica thực tế.
- [~] Khi chọn schema, dựng preview BlockDisplay riêng trước mặt admin; GUI hỗ trợ xoay/lật X/Y/Z. Nút preview ngoài world đóng GUI, cho admin tự di chuyển/ngắm vị trí; `!put` đặt, `!back` quay lại menu chỉnh.
- [x] Thêm Undo/Redo trong preview và danh sách schema; lưu tối đa cấu hình số lượt trong phiên, chỉ áp dụng khi block hiện tại còn khớp trạng thái đã ghi. Dựng kiểm tra world border, chiều cao, chunk đã nạp và phát `BlockPlaceEvent` để plugin bảo vệ có thể chặn.
- [x] Giới hạn max blocks và số entity preview qua `schema-build`; schema lớn hơn ngưỡng preview vẫn đặt đủ nhưng xem trước một phần. Tile entity data (inventory, sign text) chưa thuộc format; không ghi đè tile entity đang tồn tại, build có tile entity không thể Undo/Redo an toàn.
- [x] Thêm mẫu Sponge `schemas/examples/small-platform.schem`, cập nhật README, permission/config và CHANGELOG cho Schema Build 2.29.0.
- [x] Compile 57 file Java bằng JDK 25 (`mvn -DskipTests compile`) thành công; test suite chưa chạy.
- [x] Build 2.29.0 (`mvn -DskipTests clean package`) bằng JDK 25 thành công lúc `2026-10-28 06:37:21 UTC`; 57 source Java và 14 test source biên dịch, test suite bỏ qua. JAR `VnMineHardcore/target/VnMineHardcore-2.29.0.jar`, 2,319,154 bytes, SHA-256 `defa905bcc92449f01958dd5f0db0b1e1279ce1fda78666443b855f9e794020f`. `plugin.yml` xác nhận version 2.29.0; archive chỉ chứa SQLite native Linux x86_64 và service metadata.
- [x] Sao lưu JAR 2.28.0 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.28.0.jar`, cài 2.29.0 vào `/home/ubuntu/DATA/VNMINE/plugins/` và xác nhận hash/byte trùng artifact.
- [ ] Gameplay test trên server: duyệt thư mục lồng nhau, preview khi di chuyển/ngắm, xoay X/Y/Z, vùng protection từ plugin ngoài, đặt/undo/redo, quit/disable xóa preview, schema quá giới hạn và file hỏng.
- [ ] Xem xét dựng block entity NBT như nội dung rương, sign, banner và entity/biome từ `.schem`; parser hiện chỉ áp dụng block state vanilla.

### Cập nhật preview tự do và định dạng Sponge — 2026-10-28

- [x] Đổi scanner và file ví dụ từ YAML `.schema` sang Sponge `.schem` gzip NBT; parser hiện nhận version 2/3 và block palette vanilla. File YAML `.schema` cũ không còn được liệt kê, cần xuất/lưu lại dưới định dạng Sponge `.schem`.
- [x] Tách hai giai đoạn: chọn schema mở menu xoay/lật với bóng mờ trước mặt; nút **Preview ngoài world** đóng GUI và bật cập nhật vị trí theo block đang ngắm.
- [x] Trong preview ngoài world, `!put` xác nhận đặt; `!back` mở lại menu xoay/lật. Thoát/revoke quyền/quit vẫn dọn preview theo lifecycle của session.
- [x] Build/package Java 25 cho 2.30.0 thành công; kiểm tra archive có file `.schem` mẫu, parser và SQLite native Linux x86_64.
- [ ] Chưa gameplay test. Cần thử file Sponge `.schem` thực tế v2/v3, state xoay/lật của block đặc biệt, di chuyển/preview, `!put`/`!back`, giới hạn kích thước và protection.

## Phát hành 2.30.0 — 2026-10-28

- Tăng Maven version từ `2.29.0` lên `2.30.0` cho Sponge `.schem`, preview tự do/`!put`/`!back`, lật X/Y/Z và NPC tutorial world bắt đầu.
- `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-arm64 mvn -DskipTests clean package` thành công lúc `2026-10-28 07:22:19 UTC`; 58 source Java và 14 test source biên dịch, test suite bỏ qua.
- JAR `VnMineHardcore/target/VnMineHardcore-2.30.0.jar`, 2,330,487 bytes, SHA-256 `de86ddc25fda424abbfd6510596a152610bce45f43c52a04135a200abb576fb8`. Metadata plugin 2.30.0; archive gồm `.schem` mẫu, parser Sponge, SQLite native Linux x86_64 và service metadata.
- Sao lưu 2.29.0 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.29.0.jar`; cài 2.30.0 vào `/home/ubuntu/DATA/VNMINE/plugins/`, hash khớp artifact. Server chưa restart/gameplay test.

## Bổ sung sửa race, quản lý world và objective gặp NPC — yêu cầu 2026-10-28

- [x] `/vninit` dùng toàn bộ race definition hợp lệ thay vì chỉ các race có class đang khả dụng; ghép race/class mặc định còn thiếu từ tài nguyên plugin vào bộ nhớ khi server còn file cũ, không sửa file hay ghi đè nội dung admin.
- [x] Quản lý world ghi manifest theo tên world, environment và seed tùy chọn. Khi restart thấy environment/seed đổi (bao gồm xóa seed tùy chỉnh), unload world an toàn, chuyển thư mục cũ sang backup timestamp và tạo world mới; nếu unload/create không an toàn thì từ chối thay đổi, log rõ và cố phục hồi thư mục cũ.
- [x] Thêm nhập seed nguyên 64-bit tùy chọn trong `/vninit` cho world bắt đầu và từng realm; `!clear` bỏ seed tùy chỉnh. Đổi seed/environment chỉ áp dụng sau restart, có backup dữ liệu world cũ.
- [x] Objective `VISIT_NPC` ghi nhận khi người chơi tương tác mở luồng NPC, trước khi xử lý tutorial/dialog/menu; khi quest state còn tải thì queue lượt gặp, sau khi tải xử lý objective để chuyển bước, hoàn tất quest, thưởng và kích hoạt quest successor theo luồng hiện tại.
- [x] Tăng version nguồn lên 2.28.0; cập nhật README, CHANGELOG và ghi chú cấu hình world.
- [x] Compile/đóng gói 2.28.0 bằng JDK 25 (`mvn -DskipTests clean package`) thành công lúc `2026-10-28 02:22:28 UTC`; biên dịch 56 source Java, 14 test source được compile nhưng test suite bỏ qua. JAR `VnMineHardcore/target/VnMineHardcore-2.28.0.jar`, 2,286,615 bytes, SHA-256 `1422b42689f1c001b7f6f46c5a7a7128809e7172b990d02f8218f9474d0bd46e`. Metadata xác nhận plugin.yml 2.28.0; archive chỉ chứa SQLite native Linux x86_64 và service metadata.
- [x] Sao lưu `VnMineHardcore-2.27.0.jar` tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/`, cài 2.28.0 vào `/home/ubuntu/DATA/VNMINE/plugins/` và đối chiếu byte/hash với artifact build.
- [ ] Gameplay test Paper: migration từ file race/class cũ, đủ race trong `/vninit`, config race/class theo realm, seed set/clear, đổi NORMAL↔THE_END sau restart, backup và rollback khi world không unload được.
- [ ] Gameplay test objective gặp NPC ở objective đầu và giữa chuỗi; xác nhận GUI/dialog vẫn hiển thị, bước tiếp theo cập nhật, title/reward và quest tự giao chạy đúng, không ghi nhận nhầm khi admin shift-click để edit.
- [x] Build và cài JAR 2.28.0; chưa kiểm thử gameplay Paper.

## Onboarding theo đại lục và sửa reset quest — yêu cầu 2026-10-28

- [~] Sửa luồng admin reset quest: reset cả quest thường và tutorial, xóa session tracking trong bộ nhớ, lưu reset chờ cho người chơi offline và chuyển người chơi online về world bắt đầu. Không gọi lại bộ khởi tạo quest trong callback reset để tránh nhận EXP ngay lúc reset.
- [~] Chuyển người mới sang luồng tường minh `/start`: đăng nhập chỉ nhắc lệnh; người chơi chọn đại lục, được chuyển tới spawn đã cấu hình, theo dõi “Bước chân đầu tiên” tới NPC Tutorial đúng realm. Quest tutorial nhận thêm objective `visit-tutorial-npc`, vẫn đọc được type cũ.
- [~] Tại NPC Tutorial chạy quest-dialog qua action bar; sau hội thoại người mới chỉ thấy race/class được allowlist của realm cho phép, rồi nhận sách skill cấu hình và quest tự giao. Người có hồ sơ reset quest được đi lại bước NPC mà không bị yêu cầu tạo lại race/class.
- [~] Trong `/vninit`, admin nhập tên world cho realm/world bắt đầu, chọn NORMAL/NETHER/THE_END và bật giới hạn race/class theo allowlist. Cấu hình realm đặt tại `worlds/<realm>.yml`; world được đánh dấu tạo mới sẽ được tạo lúc plugin khởi động, không bị tạo/xóa bởi thao tác lưu khi server đang chạy.
- [~] Shift + chuột phải NPC loại `tutorial` mở editor để chọn dialog và quest tự giao. Quest picker chỉ liệt kê quest đang bật có giver NPC ID đúng với NPC tutorial; dialog được quản lý bằng `/vnquestdialog admin`.
- [x] Compile 56 file Java với JDK 25 (`mvn -DskipTests compile`) thành công; không chạy test suite.
- [x] Build 2.27.0 bằng `mvn -DskipTests clean package` lúc `2026-10-28 01:08:35 UTC`; 56 source Java biên dịch, test suite bỏ qua. JAR `VnMineHardcore/target/VnMineHardcore-2.27.0.jar` (2,278,153 bytes), SHA-256 `913821f731252653f9e60c1492d0085987a50e36b83e87b42c46f4cceaf8073b`. Metadata xác nhận version 2.27.0; SQLite native duy nhất là Linux x86_64, kèm `META-INF/services/java.sql.Driver`.
- [x] Cài JAR 2.27.0 tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.27.0.jar`, so sánh byte với artifact build; giữ 2.26.0 tại plugin folder và sao chép backup vào `.old-builds/`.
- [ ] Gameplay test Paper: kiểm tra người chơi mới, hồ sơ cũ, reset online/offline, pending reset qua restart, world thiếu/tên sai, chuyển realm, allowlist rỗng, dialog bị xóa, quest auto-give đầy 3 slot/đã hoàn thành, inventory đầy khi giao sách và tránh thưởng trùng.
- [ ] Chốt UX trường hợp world `world_start`: hiện đây là staging world an toàn; `/start` vẫn là bước chọn đại lục để teleport tới realm và NPC Tutorial tại đó. Kiểm thử xác nhận admin mong muốn dùng world này như lobby bắt đầu/reset.
- Mã nguồn đã thay đổi, compile/build thành công và JAR đã cài; gameplay test chưa chạy nên checklist vận hành vẫn mở.

## Wizard setup server RPG — yêu cầu 2026-10-27

- [x] Thêm `/vninit` và quyền `vnmine.command.init` (mặc định OP; admin group VnMine được cấp). Dashboard GUI nêu trạng thái onboarding, các đại lục đang khai báo, mapping sách khởi đầu và quest hướng dẫn; có thể mở GUI cấu hình sách/quest hiện hành.
- [x] Cấu hình riêng từng đại lục: bật/tắt cho chọn khởi đầu, chọn world đang được nạp, chọn NPC runtime loại `tutorial`, lưu vị trí hiện tại làm spawn hoặc quay về spawn của world. Mỗi thao tác ghi file YAML nguyên tử, nạp lại realm catalog và ghi log admin/action; GUI đánh dấu rõ điều kiện còn thiếu.
- [x] Bật/tắt onboarding trong `config.yml`. Admin có thể đóng wizard ở bất kỳ màn hình nào; vì các giá trị lưu ngay nên `/vninit` lần sau tiếp tục từ cấu hình hiện tại, đồng thời vẫn dùng được để sửa setup.
- [x] Hỗ trợ objective spawn của quest tutorial theo đại lục: loader chấp nhận realm ID hợp lệ; mỗi nhân vật được dẫn/đánh dấu hoàn thành tại spawn có world trùng `homeWorld`, thay vì bỏ qua quest của người chơi bắt đầu ngoài Nhân giới.
- [x] Tăng version nguồn lên 2.26.0; cập nhật README và hướng dẫn lệnh.
- [x] **Build 2.26.0:** `mvn -DskipTests clean package` thành công bằng JDK 25 lúc `2026-10-27 23:39:02 UTC`; biên dịch 56 file Java, test suite được bỏ qua. JAR `VnMineHardcore/target/VnMineHardcore-2.26.0.jar` (2,262,941 bytes), SHA-256 `288410a2e83c6e8585b94a14175caa729200c0388eabc6035178ad4c5e22c14f`; metadata xác nhận Paper API 26.3, chỉ có SQLite native Linux x86_64 cùng service metadata.
- [x] Sao lưu `VnMineHardcore-2.25.0.jar` vào `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/`, cài 2.26.0 vào `/home/ubuntu/DATA/VNMINE/plugins/` và gỡ bản cũ khỏi thư mục plugin để tránh nạp hai phiên bản. File cài đã được đối chiếu byte với artifact build.
- [ ] **Chưa Paper test:** kiểm tra GUI, quyền, ghi YAML và phục hồi sau thoát/restart; cấu hình các đại lục, xác nhận world/NPC không khớp được báo chính xác; tạo nhân vật mới ở từng đại lục và kiểm tra NPC, chọn race/class, sách khởi đầu, quest first-steps tracking/hoàn thành ở đúng spawn.
- [ ] Xác minh mapping sách race/class đầy đủ theo thiết kế từng chủng tộc và các quest tutorial tiếp nối đã được bật/cấu hình; wizard hiện báo trạng thái và mở menu quản lý hiện có, chưa tự tạo nội dung sách hoặc chuỗi quest.

## Quản trị người chơi và quest lặp lại — yêu cầu 2026-10-26

- [x] Thêm `/vnplayeradmin`: danh sách online, nút offline có icon riêng, nhập tên qua chat với ví dụ và `!cancel`; hồ sơ quản trị có xác nhận Yes/No cho thao tác phá hủy.
- [x] Thêm ban/unban, kick người đang online, mute/unmute và mute theo giây; lưu mute bền vững, giới hạn thời lượng cấu hình được, ghi audit actor/target/action.
- [x] Sắp xếp lại menu quản trị thành 54 ô với nút về menu chính ở giữa hàng cuối; thêm nút điều hướng về menu quản trị vào danh sách/hồ sơ người chơi, đặt tìm người offline ở cuối hàng và thêm submenu quest trong hồ sơ để xem quest hoàn thành, mở lại quest hoặc reset toàn bộ.
- [x] Sắp xếp đúng tuyến truy cập: Menu chính → Admin → Người chơi mở danh sách chọn online/offline → hồ sơ chứa chức năng tương tác; tách tiện ích teleport/gamemode sang mục riêng để không chiếm nút Người chơi.
- [x] Xóa inventory online có xác nhận; duyệt tối đa 10 snapshot chết và phục hồi vào hàng đợi giao item bền vững, kể cả inventory đầy hoặc người chơi offline.
- [x] Tra cứu có phân trang quest hoàn thành online/offline và đánh dấu một quest chưa hoàn thành; đồng bộ lại cache khi người chơi online, attempt thưởng mới dùng transaction ID riêng.
- [x] Có thao tác reset toàn bộ quest, skill/quick slot, race/class (giữ quest) và toàn bộ hồ sơ RPG (giữ VNCOIN); hồ sơ reset race/class được phép đi lại luồng tạo nhân vật.
- [x] Cấp level/EXP qua GUI tiến trình hiện có; picker item phân trang nay tổng hợp nguyên liệu quest ở mọi objective, sách skill đang được triển khai, loot/giáp boss theo config, item shop NPC, gear và token lính đánh thuê; giữ item factory/PDC gốc, cấp offline/túi đầy qua hàng đợi giao nhận bền vững.
- [x] Quest editor có công tắc repeatable ON/OFF, nút tăng/giảm lượt/ngày 0–10; 0 không giới hạn. Lượt/ngày và source ID lưu SQLite, giới hạn được kiểm tra trong transaction completion.
- [x] **Biên dịch/đóng gói:** Maven `clean package` thành công với Java 25 cho bản 2.20.0 lúc 2026-10-26 23:35 UTC; biên dịch 55 file Java, bộ test bỏ qua (`-DskipTests`), không xem đây là kiểm thử gameplay. JAR: `VnMineHardcore/target/VnMineHardcore-2.20.0.jar` (2,226,174 byte), SHA-256 `59afce5a638292d7db35c4b80ec9b66c172c224abdaf62f9c2a487816e6a7948`; kiểm tra plugin.yml là 2.20.0 và SQLite native chỉ có Linux x86_64.
- [x] **Bản sửa giao diện 2.21.0:** Maven `clean package` thành công với Java 25 lúc 2026-10-27 02:17 UTC; biên dịch 55 file Java, bỏ qua test. JAR `VnMineHardcore/target/VnMineHardcore-2.21.0.jar` (2,227,102 byte), SHA-256 `602fa06a4f799aa36f26e891ec2b92ba40f7a8f7eb4351f4b57bd4f6d7ca4ed3`; plugin.yml xác nhận 2.21.0, SQLite native chỉ gồm Linux x86_64.
- [x] **Điều chỉnh tuyến menu 2.22.0:** Admin → Người chơi mở thẳng danh sách chọn online/offline; mục Tiện ích người chơi giữ riêng thao tác teleport/gamemode khi có quyền. Maven `clean package` thành công với Java 25 lúc 2026-10-27 05:03 UTC; biên dịch 55 file, bỏ qua test. JAR `VnMineHardcore/target/VnMineHardcore-2.22.0.jar` (2,227,355 byte), SHA-256 `62b558e8f2ad8708c77865e1a0d9eb042b2cd338be66ffc3bf06b20a122ef3cd`; plugin.yml xác nhận 2.22.0, SQLite native chỉ gồm Linux x86_64.
- [x] **Hotfix Give item RPG 2.22.1:** log server xác nhận `IllegalArgumentException` do enchantment không áp dụng được lên vật liệu giáp trong lúc tạo catalog boss. Enchantment không tương thích bị bỏ qua có warning; lỗi ở catalog boss không còn chặn toàn bộ picker.
- [x] **Build hotfix:** Maven `clean package` thành công với Java 25 cho 2.22.1 lúc 2026-10-27 07:16 UTC; biên dịch 55 file, bỏ qua test. JAR `VnMineHardcore/target/VnMineHardcore-2.22.1.jar` (2,227,611 byte), SHA-256 `6cffa15e5d71032e7c1f412a16806f4581dcb3d6d940683afa1256a93bf4575e`; plugin.yml xác nhận 2.22.1, SQLite native chỉ gồm Linux x86_64.
- [ ] **Chấp nhận vận hành:** kiểm thử Paper/gameplay admin online/offline, chat cancel, target đổi trạng thái, death restore khi túi đầy, reset/cache sau relog, repeat 1/10/unlimited qua ngày và restart/crash không trả thưởng trùng.
- [ ] **Chấp nhận giao diện:** xác minh trên server với JAR 2.23.0 tuyến Menu chính → Admin → Người chơi → hồ sơ → Give item RPG; kiểm tra picker phân loại, gear theo class/material/star/set, cấp lẻ và nguyên set.
- [ ] **Chấp nhận vận hành:** kiểm tra từng loại item, PDC và khả năng dùng/trang bị, giao nhận offline/túi đầy, chỉ số gear theo hai tay và đối soát sau relog. Gameplay trên Paper chưa được kiểm chứng.

## Mở rộng give item và bộ gear lính — yêu cầu 2026-10-27

- [x] Đổi lối vào Give item RPG của hồ sơ admin thành các nhóm: vật phẩm người chơi, trang bị lính và lệnh triệu hồi. Nhóm vật phẩm người chơi tách theo danh mục có sẵn như quest, skill book, boss và shop; giữ factory/PDC gốc.
- [x] Tạo menu gear theo class → vật liệu → sao → set → món; cấp một món hoặc toàn bộ ô hợp lệ của set. Giao item cho người chơi offline/túi đầy qua hàng đợi bền vững đang có.
- [x] Tạo tập ô gear theo class, bỏ giả định tất cả đều có khiên; chỉ class được cấu hình `offhand` mới có món tay phụ. Dùng số ô hợp lệ làm mẫu số chia chỉ số để class không bị hụt bonus sau khi bỏ khiên.
- [x] Thêm `gear-system.item-materials` với chú thích VI/EN: cung thủ bow + rabbit-foot charm, sát thủ song kiếm, chiến binh song rìu, tu sĩ rừng trident + spellbook, pháp sư băng snowball + spellbook, vệ binh kiếm + shield, y sư potion + spellbook, kỵ sĩ spear + shield, pháp sư lửa fire charge + spellbook, trinh sát crossbow + spyglass. Item có vật liệu cố định vẫn nhận hệ số sức mạnh theo tier/star trong PDC.
- [x] Cộng bonus chỉ số của gear cả hai tay; cho gear ảnh hưởng sát thương đòn đánh thường. Chất lượng spellbook Tu sĩ rừng tăng giới hạn Vex; chất lượng spyglass Trinh Sát tăng tầm bắn, số tên của ILLUMINATION và thời gian đuốc.
- [x] **Build 2.23.0:** Maven `clean package -DskipTests` thành công bằng Java 25 lúc 2026-10-27 10:13 UTC; compile 55 file Java, test được bỏ qua. JAR `VnMineHardcore/target/VnMineHardcore-2.23.0.jar` (2,234,896 bytes), SHA-256 `74224d4aef6ab2e525c95bd9075f1c328935d23fa5f9ef746e220f4d33b3fe5a`; `plugin.yml` xác nhận 2.23.0, SQLite native chỉ gồm Linux x86_64.
- [ ] **Cần kiểm thử Paper:** mở từng menu trong tuyến Menu chính → Admin → Người chơi → hồ sơ → Give item RPG; thử single/full-set, offline và inventory đầy; xác minh các class không bị gán shield, gear tier/star hiển thị đúng, tay phụ được lưu/trang bị, chỉ số hai tay cộng dồn và bonus cung/Vex/băng/hỏa/hồi máu/cooldown/range/đuốc.
- [ ] **Cần rà soát thêm:** kiểm tra crafting/recipe cho gear lính. Luồng hiện tại tạo item qua admin GUI; cấu hình recipe chế tạo theo nguyên liệu chưa có trong thay đổi này và cần thiết kế riêng nếu muốn người chơi tự chế gear.

### Chỉnh sửa menu theo phản hồi admin — 2026-10-27

- [x] Sách kỹ năng trong Give item RPG đi theo Chủng tộc → Class → sách; danh sách sách lấy từ SkillManager nên giữ kế thừa class và item factory/PDC hiện hành.
- [x] Danh mục boss chỉ đưa vật phẩm khác biệt/hiếm vào picker; loại nguyên liệu phổ thông như kim cương, lục bảo, thỏi quặng và khử item trùng hoàn toàn.
- [x] Menu give gear tái sử dụng tên, icon class và icon vật liệu từ cấu hình/menu quản lý quân đội; icon từng món lấy từ item gear thực tế đã tạo.
- [x] Lệnh triệu hồi được phân theo Class → Bậc; dùng icon class và icon bậc giống menu thuê lính, sau đó cấp token gốc theo đúng class/bậc.
- [x] Giữ chức năng admin hiện hữu, dùng chung nguồn dữ liệu cấu hình/factory của các menu quân đội thay vì thêm hệ thống item, gear hoặc token thứ hai.
- [x] **Build 2.24.0:** Maven `clean package -DskipTests` thành công bằng JDK 25 lúc `2026-10-27 10:45:18 UTC`; biên dịch 55 file Java, test suite được bỏ qua. JAR `VnMineHardcore/target/VnMineHardcore-2.24.0.jar` (2,239,242 bytes), SHA-256 `5c34fe4dc406d3171f6708fcb3e89843ddc69d1f993a565e851ba455bf3868a`; `plugin.yml` xác nhận 2.24.0 và archive chỉ chứa SQLite native Linux x86_64. Đã cài tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.24.0.jar`, sao lưu 2.23.0 tại `.old-builds/VnMineHardcore-2.23.0.jar`.
- [ ] **Kiểm thử Paper:** xác nhận thứ tự Chủng tộc → Class → sách, icon gear/token khớp menu quân đội/thuê lính, lọc boss, thao tác quay lại/phân trang và cấp item; chưa kiểm thử gameplay trên server.

## Chỉnh sửa gear và kỹ năng lính — yêu cầu 2026-10-27

- [x] Kỵ sĩ dùng spear material riêng từ Paper 26.3 (`WOODEN/COPPER/IRON/GOLDEN/DIAMOND/NETHERITE_SPEAR`) cho gear và vũ khí bắt đầu; bỏ đường quy đổi spear thành trident. Tự sửa cấu hình cũ nếu `paladin.mainhand` đang là `TRIDENT`.
- [x] Đặt tên gear theo vai trò thay cho nhãn tay chính/tay phụ chung chung. Sát Thủ/Chiến binh chỉ sinh một mẫu song kiếm/song rìu, có thể đặt ở một trong hai tay; marker gear cho phép equip ở cả hai slot và chỉ số sẽ cộng dồn nếu lính dùng hai bản.
- [x] Làm rõ và áp dụng bonus sách phép theo chất lượng vật liệu/sao: sách Tu Sĩ Rừng tăng giới hạn Vex theo cấu hình; sách Băng tăng sát thương và giảm hồi chiêu theo chỉ số gear, đồng thời kéo dài hiệu ứng làm chậm của Bão Tuyết/Băng Nộ/Tường Băng; sách Y Thuật ghi rõ lượng hồi máu và giảm hồi chiêu; sách Hỏa Thuật tăng sát thương lửa và thời gian cháy từ Hỏa Cầu/Thiêu Đốt. Giá trị và hiệu ứng hiện rõ trong lore item.
- [x] Trinh Sát tự kích hoạt Soi Sáng khi ánh sáng quanh lính thấp, không cần mục tiêu; tên được bố trí theo vòng quanh lính, số lượng tự giảm nếu cần để giữ khoảng cách, và sự kiện đặt đuốc chặn vị trí quá gần các đuốc tạm đang tồn tại. Khoảng cách mặc định 3 block, chỉnh được trong `armies.yml`.
- [x] Đổi version nguồn lên 2.25.0; `mvn -DskipTests clean package` thành công bằng JDK 25 lúc `2026-10-27 11:11:14 UTC`, biên dịch 55 file Java; test suite được bỏ qua. JAR `VnMineHardcore/target/VnMineHardcore-2.25.0.jar` (2,242,966 bytes), SHA-256 `20dca1d90bd0e5bde57ee7ed5a2f0a98413a8bce482e75c06b8f16fbc2333964`; metadata xác nhận Paper API 26.3 và archive chỉ chứa SQLite native Linux x86_64 cùng service metadata.
- [x] Đã sao lưu `VnMineHardcore-2.24.0.jar` vào `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/` và cài JAR 2.25.0 vào `/home/ubuntu/DATA/VNMINE/plugins/`; xác nhận file cài trùng byte với artifact build.
- [ ] **Kiểm thử Paper:** xem spear trong menu thuê/give gear và lúc lính cầm; thử gear song kiếm/song rìu ở một hoặc cả hai tay; so lore và chỉ số từng vật liệu/sao; xác nhận sách tăng đúng Vex/heal/damage/cooldown/duration; kiểm tra ranger soi sáng ban đêm và trong hang không có mục tiêu, các đuốc cách nhau và tự dọn sau thời hạn.

> Tài liệu này ghi nhận các vấn đề và đề xuất từ đợt rà soát mã nguồn, đồng thời xác định lộ trình hoàn thiện plugin cho server Minecraft RPG hardcore. Đây là kế hoạch triển khai; các hạng mục dưới đây không được coi là đã có trong mã hiện tại nếu chưa được triển khai và kiểm chứng.

## 1. Quyết định thiết kế đã chốt

### 1.1 Luật tử vong

- Người chơi **không bị ban khi chết**. Đây là luật cố định của server; không đổi thành permadeath, ban tạm thời hoặc xóa tài khoản.
- Chết vẫn có hậu quả gameplay: ghi lịch sử tử vong, xử lý mộ/vật phẩm, thời gian hồi sinh và death penalty theo cấu hình hiện có.
- MVP không xóa hoặc trừ VNCOIN khi chết. Nếu sau này thiết kế phí cứu hộ hoặc mất tiền thì phải là tùy chọn cấu hình riêng, công bố trước và mặc định tắt.
- Combat tag là trạng thái cảnh báo/chặn một số dịch chuyển. Thoát game trong combat không tự động ban người chơi.
- README, config, GUI, command và thông báo phải nói nhất quán rằng chết không bị ban và giải thích rõ hậu quả hiện hành.

### 1.2 Tiền tệ

- Đơn vị tiền xuyên suốt là **VNCOIN**; số dư là số nguyên không âm.
- Số dư VNCOIN lưu phía server trong SQLite. Không dùng scoreboard, lore vật phẩm hoặc dữ liệu client làm nguồn gốc.
- Mob cơ bản đủ điều kiện có **30% xác suất** rơi VNCOIN; lượng rơi từ **1 đến 10**, tùy bậc sức mạnh mob.
- Boss, thiên tai, quest và nguồn khác có reward definition riêng, không cộng chồng với bảng rơi mob cơ bản.
- Mọi nguồn phát hành và nơi tiêu VNCOIN cần được ghi ledger để kiểm toán và chống nhân đôi.

## 2. Phạm vi hiện tại và cách đọc trạng thái

- Plugin hiện đã có nền tảng như profile SQLite, level/EXP/skill point, một số skill, race/class, NPC, tutorial quest, portal, boss, thiên tai, mộ/lịch sử chết, lính đánh thuê, phân quyền và luật sinh tồn.
- player-classes.yml khai báo nhiều class chọn được, trong khi skill registry và cast handler hiện tập trung vào một tập nhỏ: swordsman, guardian, mage; sáu skill chủ động cùng các passive được triển khai. Cần phân biệt class khai báo, class chọn được và class hoàn chỉnh gameplay.
- plan.md trước đây ghi nhiều phase là hoàn tất và chứa số liệu test lịch sử. Cần đối chiếu lại với mã nguồn, server Paper/ProtocolLib hiện tại và lần kiểm chứng mới trước khi phát hành.
- Đợt rà soát vừa rồi là đọc mã tĩnh; không chạy build, unit test hoặc gameplay test trong phiên đó.

## 3. Vấn đề và rủi ro đã phát hiện

### 3.1 Cast skill có thể mất tài nguyên khi hiệu ứng lỗi — P1

- Trước đây nhánh applySkillEffect trả về false vẫn có thể đi tiếp như cast thành công. Sửa ở commit làm việc hiện tại: chỉ đặt cooldown và báo thành công khi hiệu ứng trả về true; nếu trả false sau khi trừ mana thì hoàn mana, không đặt cooldown.
- Rà soát bổ sung tìm thấy handler cũng có thể ném RuntimeException sau khi mana đã bị trừ. Đã sửa để ghi stack trace mức SEVERE và vẫn thử hoàn mana. Nếu DB không thể hoàn, người chơi được báo lỗi và log chỉ rõ skill/player; cần kiểm tra thủ công số dư trong trường hợp này.
- Bật `logging.console-debug: true` để có log `[RPG-DEBUG]` cho cast/AOE, mana, skill UI/sách/nâng cấp/quick slot, profile, quest, portal/world map, command và phiên bản runtime. `[RPG-DB]` ghi queue wait và DB duration; `[RPG-PERF][DB]` cảnh báo khi DB >=100ms hoặc queue wait >=250ms; task lỗi và queue rejection ghi stack trace. Tắt debug sau phiên lấy log vì log gameplay có thể nhiều.
- Còn cần gameplay test trên server thật: cast thành công, không đủ mana, không có mục tiêu, mục tiêu/AoE mất hợp lệ, lỗi effect nếu tái hiện được và xác nhận mana/cooldown sau mỗi trường hợp.

### 3.2 JDBC/SQLite chạy đồng bộ trong nhiều thao tác gameplay — P1/P2

- Mở GUI skill, xác nhận profile, học sách, nâng rank, cast skill và tiêu/hồi mana có truy vấn SQLite trực tiếp từ event hoặc command Bukkit.
- Một thao tác có thể thực hiện nhiều truy vấn liên tiếp. Ổ đĩa chậm, database lock, backup hoặc nhiều người thao tác có thể làm chậm main thread và tụt TPS.
- Gom truy vấn liên quan thành transaction hoặc một lần đọc/ghi; dùng DB queue/executor có giới hạn cho JDBC; chỉ đưa thao tác Bukkit cần thiết về main thread.
- Không gọi Bukkit API từ async; đảm bảo thứ tự thao tác cùng người chơi, không để task cũ ghi đè dữ liệu mới, và không thao tác Player đã offline.
- Đo thời gian truy vấn, giới hạn retry/backoff và cảnh báo khi vượt ngưỡng. Không tạo thread tùy ý cho từng thao tác.
- Cập nhật 2026-10-25: đã thêm một DB executor đơn luồng, queue tối đa 256 việc và báo lỗi rõ khi queue đầy/đang shutdown. Các luồng profile khi join/tạo nhân vật, menu và cast/nâng/học/gán skill, quest journal/progress/reward, kiểm tra portal và world map đã chuyển sang executor; cập nhật GUI/message/cache quay về main thread. Giao dịch cast kiểm tra class/rank và trừ mana nguyên tử.
- Đã thêm chặn nâng rank lặp trong lúc DB còn xử lý, kiểm tra người chơi còn online trước callback UI và shutdown executor có thời hạn chờ 5 giây. Chưa đo TPS/độ trễ thực tế; cần staging test nhiều người và kiểm tra log queue/DB.
- Phần world map hiện truy vấn tuần tự theo từng realm nhưng chạy ngoài main thread; nếu số realm tăng lớn, gom trạng thái quest/unlock thành bulk query. Tiếp tục rà các luồng DB khác ít gặp, đặc biệt admin và reward khác, trước khi coi việc chuyển JDBC hoàn tất toàn project.

### 3.3 Class chọn được nhưng có thể thiếu nội dung skill — P1

- Có class trong player-classes.yml không có nghĩa là class đó có cây skill, sách, passive, cân bằng damage và tutorial hoàn chỉnh.
- Active skill có trong YAML nhưng không có cast handler sẽ không dùng được. Passive chưa triển khai nâng rank cũng tạo cảm giác tính năng có nhưng thiếu.
- Registry phải kiểm tra mọi active skill có handler và mọi passive có implementation hoặc trạng thái planned rõ ràng. YAML không hợp lệ phải báo ID cụ thể và từ chối nạp.
- Class chưa sẵn sàng phải ẩn/khóa ở GUI hoặc có nhãn đang phát triển; không cho người chơi tạo nhân vật rồi nhận cây skill trống.
- Hoàn thiện theo vertical slice: bộ skill, sách, chỉ số, UI, quest giới thiệu, cân bằng PvE/PvP và kiểm chứng trước khi mở class tiếp theo.

### 3.4 ProtocolLib/Paper chưa được khóa thành ma trận phát hành — P1

- plugin.yml khai báo ProtocolLib là dependency cứng; code từ chối bật plugin nếu version không bắt đầu bằng 5.5.0-SNAPSHOT.
- Maven compile với ProtocolLib 5.4.0; ghi chú POM nói server phải dùng dev build 5.5.0-SNAPSHOT. Có thể đây là workaround có chủ ý nhưng API compile/runtime khác nhau.
- Ghi rõ Java, Paper build và ProtocolLib build đã xác nhận tương thích. Smoke test JAR phát hành trên chính ma trận server đích.
- Rà lại API/packet runtime; nếu có artifact chính thức phù hợp thì chuyển sang artifact đó. Không đóng gói ProtocolLib vào plugin.
- Lỗi khởi động cần nêu version cần có và version đang phát hiện.

### 3.5 Đăng ký command bị lặp — P3

- VnMineHardcore.onEnable có tên command lặp trong danh sách; vnmenu được gán executor trước rồi gán lại cho MercenaryManager.
- Việc gán cuối có thể có chủ ý, nhưng khai báo trùng làm khó xác định executor thực tế.
- Mỗi command chỉ nên đăng ký một lần từ bảng khai báo rõ ràng. Kiểm tra mọi lệnh trong plugin.yml đều có executor, permission, usage và tab completion mong muốn.
- Đã rà điểm gán executor hiện tập trung qua `registerCommand`; thêm guard làm startup fail nếu cùng command được đăng ký hai lần. Cần xác nhận startup log `[RPG-DEBUG][CommandRegistry]` sau khi test.

### 3.6 Cấu hình/tài liệu có thể lệch runtime — P2

- Nhiều YAML và hệ thống reload riêng; cấu hình sai không được để runtime ở trạng thái nạp một phần.
- Validation phải diễn ra trước khi thay thế cấu hình đang chạy. Reload lỗi giữ nguyên trạng thái cũ và trả lỗi cụ thể.
- Đồng bộ README, command, permission, config key, GUI, changelog và trạng thái plan.
- Phân biệt rõ: đã code, unit tested, Paper smoke tested, gameplay tested và đã phát hành. Trạng thái hoàn tất cần có phiên bản/ngày kiểm chứng.

### 3.7 Rủi ro cần kiểm tra thêm

- SQLite: backup/restore trong lúc server chạy, transaction migration, phục hồi ghi lỗi, retention và tính toàn vẹn ledger.
- Task lifecycle: hủy khi disable/reload; không truy cập world/entity/player từ async; không để task tồn tại sau reload.
- Exploit reward: spawner/grinder, mob bị giết bởi môi trường, lính đánh thuê/pet, nhiều người cùng đánh, coin item nhân đôi, DB lỗi, rollback chunk và reconnect.
- PvP: MVP không phát VNCOIN từ player kill để tránh farm phối hợp.
- Death/grave: khôi phục inventory không làm mất hoặc sinh đôi item; người chết luôn có đường tiếp tục chơi.
- Cân bằng: damage multiplier, boss/skill, death penalty stacking và recovery không được làm nhân vật suy yếu đến mức không thể chơi.

### Trạng thái xử lý sau rà soát

- Ngày 2026-10-25, commit 6399586 đã build bằng Java 25 với lệnh Maven clean package thành công; 104 unit test đạt, không lỗi/skip.
- Smoke test staging cô lập thành công trên Paper 26.3-214 và ProtocolLib 5.5.0-SNAPSHOT-6720a70: plugin bật, lệnh /vnhardcore phản hồi, log không có lỗi khởi động; class chưa có skill runtime được log là ẩn.
- Chưa có Minecraft client trong lượt staging này nên chưa xác nhận tương tác GUI tạo nhân vật, cast skill thành công/thất bại hoặc hoàn mana trong gameplay.
- Còn mở: rà soát bổ sung mọi luồng DB ngoài các luồng gameplay/UI đã chuyển nền; chạy gameplay test cho cast/refund, profile/skill/quest/portal và GUI; đo queue/TPS dưới tải; mở rộng ma trận tương thích ngoài bộ Java 25/Paper 26.3-214/ProtocolLib 5.5.0-SNAPSHOT đã smoke test.
- Ngày 2026-10-25, đã bổ sung nhánh hoàn mana khi skill effect ném RuntimeException, debug trace cho luồng cast và cảnh báo `[RPG-PERF]` nếu truy vấn DB đồng bộ trong cast kéo dài ít nhất 100 ms. Chưa build hoặc chạy test sau thay đổi này; cần người vận hành kiểm tra bằng build/deploy thực tế và gửi log có marker `[RPG-DEBUG][SkillCast]` hoặc `[RPG-PERF]`.
- Ngày 2026-10-25, đã thêm mana bar tạm thời trên action bar sau cast/thiếu mana và đổi AOE mage `arcane-field` thành Mưa Sao Băng: vòng particle cảnh báo, 6–10 luồng thiên thạch chéo, sát thương một lần mỗi quái trong vùng, không tạo vụ nổ thật hoặc sửa block. Gameplay/balance chưa được kiểm thử. Vì skill definitions là file cấu hình người dùng và plugin không ghi đè file đã tồn tại, khi triển khai cần cập nhật `plugins/VnMineHardcore/skills/skill-definitions.yml` theo resource mới để đồng bộ tên, mô tả, mana 30 và hồi chiêu 24 giây.
- Sau phản hồi live test, nguyên nhân mana gần như không giảm đã xác được: config server hồi 5 mana/giây, trong khi Hỏa Cầu tốn 12 và AOE cũ tốn 18; cooldown cho phép hồi lại phần lớn hoặc toàn bộ trước cast tiếp theo. Đã giảm mức mặc định và config server xuống 1 mana/giây; đồng bộ AOE server sang Mưa Sao Băng 30 mana/24 giây. Cần restart và đo lại tốc độ cạn/hồi mana để cân bằng tiếp.
- Cập nhật hiệu năng 2026-10-25: Maven package thành công bằng Java 25 với `-Dmaven.test.skip=true` sau khi sửa lỗi kiểu plugin phát hiện lúc compile; không chạy unit/gameplay test. JAR này chưa smoke test trên Paper và chưa đo tải.
- Phase A test build: bật `logging.console-debug: true` trong resource và server test; thêm log có operation name cho DB queue/duration, Profile, SkillCast/Meteor, SkillUI/Book/Upgrade/QuickSlot, Quest, Portal/WorldMap, Command và AdminProgression. Startup ghi Java/Paper/ProtocolLib. Active skill không có cast handler giờ chặn registry; passive chưa có implementation ghi cảnh báo rõ. Thêm duplicate registration guard. Debug JAR đã khởi động trên server test; gameplay coverage xem kết quả ngay dưới đây.
- Kết quả gameplay log ngày 2026-10-25 (`VNMINE/logs/latest.log`): debug JAR bật thành công trên Java 25, Paper 26.3-175, ProtocolLib 5.5.0-SNAPSHOT-d89a5de; 32 command được đăng ký, registry nạp 9 skill/9 sách và 3 class chơi được. Đã thấy mở skill tree/details/quick cast, cast Fire Bolt và Mưa Sao Băng thành công, từ chối cast không mục tiêu/cooldown/thiếu mana, hiển thị mana, học sách Mana Flow và hồi +1 mana theo passive. Có 106 DB tasks; queue wait trung bình 0.15 ms/tối đa 9 ms, DB trung bình 3.35 ms/tối đa 14 ms; không có `[RPG-PERF]`, SEVERE, exception, DB error hoặc queue rejection. Nâng Mana Flow khi chưa học trả `SKILL_NOT_LEARNED` như dự kiến. Chưa thấy portal/world map, quest objective/reward claim, create profile, cast effect failure/refund hoặc test tải đồng thời trong log.
- Log `cast transaction ... dbMs=49` của bản vừa test thực ra đo thời gian từ submit đến callback main-thread, không riêng DB; log `[RPG-DB][OK]` mới là thời gian queue/DB riêng. Đã sửa nhãn thành `taskToMainCallbackMs` và chỉ cảnh báo `[RPG-PERF][SkillCast]` từ 250 ms trở lên để tránh hiểu nhầm.

## 4. Thiết kế VNCOIN

### 4.1 Drop từ mob cơ bản

- Xác suất cố định là **30% mỗi lần hạ mob đủ điều kiện**. Trong MVP, tỉ lệ không tăng theo level, class, Fortune, số người trong party hoặc bonus khác.
- Lượng rơi chọn ngẫu nhiên trong khoảng bậc mob:

| Bậc | Mức sức mạnh tham khảo | Lượng VNCOIN | Ví dụ tham khảo |
|---|---|---:|---|
| I — yếu | Quái thường, ít máu/sát thương | 1–3 | zombie, skeleton, spider, silverfish |
| II — thường | Biến thể hoặc áp lực cao hơn | 2–5 | husk, stray, drowned, cave spider |
| III — mạnh | Quái có kỹ năng/khả năng gây áp lực | 4–7 | witch, enderman, pillager, magma cube lớn |
| IV — tinh nhuệ | Quái nguy hiểm/hiếm theo luật server | 6–10 | ravager, evoker, guardian theo phân loại server |

- Đây là bảng khởi điểm. Danh sách mob và tier phải cấu hình được; không suy đoán sức mạnh từ tên entity hay level người chơi.
- Boss vanilla/boss plugin, mob do boss/event triệu hồi, mob thân thiện và mob do người chơi triệu hồi không dùng bảng cơ bản. Boss/event có reward riêng.
- Mỗi mob chỉ cấp tối đa một lần. Mob không có killer hợp lệ không rơi coin trong MVP.
- Định nghĩa rõ credit: killer, projectile, lính/pet và assist theo cửa sổ sát thương. Đề xuất một người nhận reward cho mỗi mob, không nhân tiền theo số người đánh.
- Kiểm tra chống farm spawner/AFK; có thể giảm/tắt reward cho mob từ spawner hoặc khu vực bị đánh dấu farm. Quy tắc phải hiển thị trong config.

### 4.2 Hình thức rơi và ghi có

- Mob đủ điều kiện tạo item VNCOIN trên mặt đất; item có PDC/NamespacedKey riêng, không xác thực bằng display name/lore.
- Khi nhặt, ghi có vào ví bằng transaction SQLite. Chỉ xóa item sau khi DB xác nhận; DB lỗi thì coin còn trên đất và lỗi được log có giới hạn.
- Mỗi drop có UUID duy nhất. Ledger đánh dấu drop đã claim để item/entity bị nhân bản hoặc chunk rollback không nạp tiền lần hai.
- Nếu coin despawn, rơi vào lava hoặc bị xóa thì chưa nhặt không tự cộng vào ví.
- Đặt thời gian tồn tại và chính sách hopper rõ ràng; mặc định không cho hopper hút coin.
- Hiển thị VNCOIN, lượng và nguồn rơi. Có actionbar/âm thanh tùy chọn, không broadcast toàn server.
- Giai đoạn sau có thể thêm auto-deposit khi đứng gần; MVP dùng item rơi vật lý để người chơi thấy loot.

### 4.3 Ví, ledger và migration

- Đọc schema thật trong code trước khi chọn version migration mới; tài liệu cũ ghi nhận schema v6 nhưng phải xác minh lại.
- Thêm wallet balance hoặc bảng ví riêng. Mọi thay đổi phải đi qua EconomyService/API duy nhất.
- Ledger bất biến tối thiểu gồm transaction ID, UUID người chơi, delta, loại nguồn/đích, source ID duy nhất, timestamp, actor admin và metadata an toàn.
- Transaction nguyên tử, idempotency key duy nhất, số dư không âm, giới hạn amount/overflow và không cấp coin trước khi DB commit.
- Admin give/take yêu cầu permission riêng, lý do, log và audit; ưu tiên give/take thay vì set để giữ lịch sử.
- Backup/restore gồm cả ví và ledger; có quy trình kiểm tra integrity sau restore.

### 4.4 Nguồn kiếm và nơi tiêu

- MVP: mob drop 30% theo bảng; admin grant có audit.
- Mở rộng: quest một lần, boss/event, bounty, achievement. Mỗi reward cần source ID idempotent, cooldown/giới hạn khi cần.
- Thêm sink cùng lúc hoặc ngay sau nguồn kiếm:
  - NPC merchant bán vật tư, đồ tiêu hao và recipe với giá cấu hình.
  - Sửa trang bị, dịch vụ hub/portal hoặc đổi nguyên liệu; không thu phí cho mọi hành động thiết yếu.
  - Player trade/auction chỉ sau khi có escrow, log, timeout và chống disconnect.
- Không bán sức mạnh vượt progression. Merchant phải tôn trọng tier thế giới và quest/boss gate.
- Theo dõi coin phát hành, coin tiêu, số dư trung vị và nguồn phát hành lớn nhất để phát hiện lạm phát.
- Không phạt VNCOIN khi chết trong MVP.

### 4.5 Command và giao diện

- Lệnh chuẩn là `/vncoin` (xem số dư mặc định hoặc `/vncoin balance`); không đăng ký alias `/balance` vì thường xung đột với plugin economy khác.
- /vncoin history [player]: xem lịch sử; người chơi chỉ xem ví của mình, admin có quyền mới xem người khác.
- /vncoin give|take <player> <amount> [reason]: admin với permission granular, audit, giới hạn số tiền và xác nhận thao tác lớn.
- Leaderboard số dư là tùy chọn và nên tắt mặc định nếu gây khuyến khích farm.
- Placeholder/scoreboard/actionbar chỉ dùng cache có invalidation; SQLite vẫn là dữ liệu gốc.
- Mọi thông báo cần dùng đúng một tên VNCOIN, định dạng số và ngôn ngữ tiếng Việt.

## 5. Hoàn thiện vòng lặp RPG

### 5.1 Class và build

- Chia class thành available, locked và planned; chỉ available mới chọn được.
- Hoàn thiện class theo vai trò rõ: chỉ số, active/passive, mana/cooldown, sách, feedback, counterplay, quest giới thiệu và cân bằng.
- Ưu tiên hoàn thiện swordsman, guardian, mage theo skill registry hiện có; tiếp theo chọn archer/ranger, healer/support, rogue theo thiết kế thế giới.
- Tách SkillDefinition, skill handler/execution và persistence; thêm skill mới không cần mở rộng switch tập trung.
- UI giải thích chỉ số cuối và nguồn cộng/trừ; không dùng attribute/potion effect ẩn khó hiểu.
- Skill book mở khóa, skill point nâng rank. Respec có thể thu VNCOIN tại hub ở phase sau, có cooldown/giới hạn và audit.

### 5.2 Quest, story và tutorial

- Tutorial có mục tiêu tương tác: tạo nhân vật, xem profile, hạ mob, nhặt VNCOIN, mua đồ, học/slot skill, qua portal và đánh miniboss.
- Objective hỗ trợ nói chuyện NPC, thu item PDC, hạ mob theo loại, tới vùng, dùng portal, hạ boss, chế tạo và nộp vật phẩm.
- Lưu tiến độ bền vững, idempotent qua restart/reload; reward coin/EXP/item không claim lặp.
- Quest journal thể hiện active/complete/claimable, waypoint và reward rõ ràng.
- Story theo chapter/vùng/level; portal/world unlock theo tiến độ; người chơi có thể tiếp tục nếu bỏ qua dialogue.
- NPC definition có schema version, ID liên kết hợp lệ và persistence không tạo entity trùng khi restart/chunk unload.

#### Yêu cầu mở rộng 2026-10-25: nhiệm vụ, tracking, skills, chủng tộc và cốt truyện

**Hiện trạng cần tính đến:** tutorial `visit-realm-spawn` vẫn tồn tại; quest NPC hiện có ba nhiệm vụ săn zombie/skeleton/spider và một quest Thợ rèn thu thập mảnh sắt RPG có PDC. Journal nhiều quest, giới hạn 3 quest, reward preview, hủy xác nhận, nộp item, boss-ID objective và tracking NPC vừa được thêm vào working tree nhưng chưa build/gameplay test; không coi là đã phát hành cho đến khi xác minh. Quest lặp/day cap gameplay và chống farm/spawner vẫn thiếu. Chapter story có metadata và artifact cũ, nhưng chưa có vòng chơi quest/story end-to-end nhiều vùng. `races.yml` hiện chỉ có tên, icon, mô tả; chưa khai báo race buff có tác động gameplay. Trong 15 class khai báo, 3 class đang mở chơi được; registry có 6 active handler và 3 passive handler, chưa có nhánh build theo nguyên tố/vai trò.

**Quest tracking đã triển khai, chờ kiểm tra trên server:** tutorial journal có công tắc particle riêng trên từng người chơi, lưu bằng PDC; RPG quest details cũng có công tắc particle riêng, tách khỏi lựa chọn quest đang tracking. Cả hai loại waypoint dùng bộ tìm đường A* có giới hạn lượt mở rộng, không ép tải chunk; END_ROD vẽ các mũi tên `>>>` theo bề mặt đi bộ, né vật cản và cập nhật lại khi người chơi di chuyển; vòng đánh dấu đích nằm trên mặt đất, bỏ cột particle đứng. Nếu không tìm thấy đường, RPG quest vẫn hiện tọa độ NPC bằng action bar. Cấu hình server-wide và chu kỳ cập nhật vẫn có hiệu lực. Chưa build/gameplay test thay đổi này; cần test bật/tắt và reconnect, cầu thang/địa hình dốc, tường/vật cản, target xa/chunk chưa tải, cùng hiệu năng nhiều người chơi.

**Quest lặp lại — nền lưu lượt đã triển khai, chưa bật gameplay:** schema SQLite 9 thêm `player_daily_quest_runs` với run number giới hạn 1..10, ngày reset và source ID duy nhất. API `awardDailyRepeatableQuestWalletReward` kiểm tra giới hạn, chống callback trùng, ghi ledger VNCOIN và đánh dấu lượt trong cùng transaction; thay đổi schema có đường migrate 8→9 và backup trước migration như luồng hiện có. API hiện có trong JAR 2.12.0 nhưng chưa được gọi bởi quest definition hay objective handler; chưa có cấu hình timezone/giờ reset, UI bộ đếm, quest lặp thực tế, hay reward EXP/item. Không xem tính năng nhiệm vụ lặp là hoàn tất cho tới khi có objective end-to-end và kiểm chứng migration trên bản sao DB/server.

**Quest framework — đã build trong JAR 2.13.0, chưa chạy integration/gameplay test:** `QuestCatalogManager` nạp `quests/quest-definitions.yml`; `accept-quest` trong dialogue chỉ nhận quest từ đúng NPC ID; bốn mẫu gồm ba quest hạ zombie/skeleton/spider và một quest thu thập item RPG rơi từ iron golem. Item mang PDC ID riêng, tiến độ chỉ tăng khi nhặt đúng vật phẩm; completion trả VNCOIN một lần. Tiến độ mục tiêu lưu bền vững và được nạp lại khi vào server; debug log chi tiết tuân theo `logging.console-debug`. Scoreboard hiển thị tiến độ quest thường. Schema 10 được tích hợp; cần test migration từ schema 9 và luồng nhận/tiến độ/thưởng trên server thật.

**Quest admin GUI — đã build trong JAR 2.13.0, chờ test trên server:** `/vnquest admin` có danh sách phân trang; tạo ID bằng nhập chat; sửa tên, NPC giao, bật/tắt và xóa có xác nhận. Các trường chat có `!cancel` và mở lại submenu tương ứng. Định nghĩa ghi YAML theo atomic move và các trường do editor ghi có chú thích VI/EN. NPC chỉ liệt kê quest bật theo giver ID; màn hình nhận quest lọc điều kiện. Có thể cấu hình điều kiện AND gồm level RPG tối thiểu, quest đã hoàn thành và item chính xác đang cầm (hỗ trợ metadata/PDC). Màn hình mục tiêu sửa bước hạ mob đầu tiên và thêm/xóa tối đa 44 bước hạ mob hoặc thu thập chính xác item đang cầm; progress mỗi bước lưu trong SQLite `current_step`/`objective_progress`, reset khi chuyển bước. Mục tiêu thu thập RPG legacy vẫn chạy ở bước đầu. Màn hình thưởng nhận tối đa 45 stack item và nút +/-1/10/100 cho EXP, VNCOIN; item giao qua delivery outbox. Không cho sửa objective/xóa quest khi có người chơi đang làm quest để tránh orphan/misalign progress. Permission `vnmine.command.quest.admin` mặc định op. Build `mvn clean package -DskipTests` thành công bằng JDK 25 lúc 2026-10-25 07:14:48 UTC. JAR: `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.13.0.jar`, 1,981,232 bytes, SHA-256 `071858a87b665afde09f2ae66adee3ad4870b9024ef3cb74d422df00f33dc9fa`. Bản 2.12.0 được lưu trong `plugins/.old-builds`; test gameplay/migration trên server thật vẫn cần thực hiện.

**Sự cố NPC giao quest phát hiện sau khi cài 2.13.0 — đã sửa trong 2.14.0:** log server cho thấy `npc-definitions.yml` chỉ nạp 3 definition, không có definition ID `hunter` hoặc role `quest-giver`; runtime `npcs.yml` có NPC `hunter` loại `quest-giver`. Mã cũ dừng tương tác trước khi ghép quest và hiện chuỗi tiếng Hà Lan. Bản sửa ưu tiên definition cùng ID dù role khác; khi thiếu definition tạo hội thoại dự phòng tiếng Việt và tiếp tục ghép quest theo NPC ID. Đã compile và đóng gói trong JAR 2.14.0; cần xác minh tương tác trên server thật.

**Menu quản trị quest/NPC — đã build trong JAR 2.14.0, chờ test trên server:** menu Admin từ menu chính có nút riêng cho quản lý quest và NPC, chỉ hiện theo quyền tương ứng. GUI NPC có danh sách phân trang, tạo qua nhập ID/loại/tên ở chat tại vị trí admin, sửa tên/loại, di chuyển tới vị trí hiện tại, xóa có xác nhận. Đổi loại merchant bị chặn nếu còn offer. Prompt quest/NPC nêu định dạng, giới hạn, ví dụ; màu vàng là yêu cầu, trắng là dữ liệu mẫu, đỏ là `!cancel`; sau nhập/hủy quay lại đúng menu. NPC thiếu dialogue definition dùng hội thoại dự phòng tiếng Việt và vẫn liệt kê quest theo runtime ID. Build `mvn clean package -DskipTests` thành công bằng JDK 25 lúc 2026-10-25 07:48:59 UTC. JAR: `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.14.0.jar`, 1,991,154 bytes, SHA-256 `0512bc835c46d0d29891254299448cb705d7d725db579559adc94a02bc9a2239`. Bản 2.13.0 lưu trong `plugins/.old-builds`; chưa kiểm thử gameplay/menu trên server thật.

**Quest framework tiếp theo:**
- Hoàn thiện editor còn thiếu: chọn objective theo Minecraft Material/RPG ID (thay cho chọn exact item cầm tay), tiêu thụ/giao item, objective hoàn thành quest khác, boss encounter/NPC/region, cùng validate và preview phần thưởng. Hiện GUI cho sửa bước kill đầu và thêm/xóa bước kill/collect exact item tuần tự; collect RPG qua drop PDC vẫn dùng định nghĩa legacy làm bước đầu.
- Mở rộng loại requirement (ví dụ quyền, race/class, vùng, item ID có số lượng); loại hiện có đã được nối vào lọc danh sách NPC và chấp nhận quest. Đánh giá hiệu năng và lỗi cạnh tranh khi cache completed quest được nạp/reconnect.
- Mở rộng loader/runtime thành objective executor tổng quát cho collect/nộp item PDC, boss encounter ID, nói chuyện NPC, vùng, portal và crafting; thêm vật phẩm RPG có nguồn tạo được kiểm soát cho tuyến Thợ rèn/Giả kim.
- Tách `QuestDefinition`, objective executor, tracker, reward preview và persistence khỏi `TutorialQuestManager` để nạp nhiều quest có ID ổn định. (Phần loader kill quest và progress persistence ban đầu đã có; GUI/tracker tổng quát vẫn chưa có.)
- Nối nền run ledger schema 9 vào quest hoàn chỉnh; thêm cấu hình reset date/timezone, giới hạn mặc định tối đa 10 và UI số lượt còn lại. Chỉ cho phép completion callback có source ID ổn định đi vào giao dịch.
- Bộ quest mẫu cần có chapter mở đầu tại Người dẫn đường, chuỗi Thợ săn (hạ zombie/skeleton theo số lượng), Thợ rèn (nộp quặng RPG có PDC), Giả kim (thu thập thảo dược RPG), Truyền tin (nói chuyện với NPC/đến vùng), và Thợ săn tiền thưởng (hạ boss hệ thống theo encounter ID). Thêm bảng nhiệm vụ ngày riêng cho thợ săn/thợ rèn; từng quest khai báo NPC nhận, điều kiện, objective, phần thưởng, chapter liên kết và cap tối đa 10 lượt/ngày. Đây là danh sách thiết kế, chưa có config/runtime.
- [~] Thêm objective theo dữ liệu: đã bổ sung objective tuần tự nộp item chính xác cho NPC giao quest và hạ boss hệ thống theo ID PDC; còn thiếu collect theo Material/PDC tổng quát, NPC/region/portal/crafting và attribution co-op.
- [~] Lưu nhiều quest progress cùng lúc, giới hạn tối đa 3 quest active, chọn một quest làm tracked quest. GUI RPG có danh sách đang nhận, nhật ký hoàn thành, chi tiết/checkpoint, xem trước VNCOIN/EXP/item, theo dõi/bỏ theo dõi và hủy có xác nhận; mở bằng `/vnquest journal` hoặc Hồ sơ RPG → Nhiệm vụ. Cung cấp `/vnquest detail <id>` để mở chi tiết quest được đánh dấu ở scoreboard. Sidebar liệt kê tối đa 3 quest, bước và yêu cầu hiện tại. Tên quest chỉ có thể được gạch chân; Bukkit scoreboard API dùng tại dự án không phát sinh sự kiện click. Chưa build/gameplay test.
- Hỗ trợ quest repeatable tối đa 10 lần mỗi ngày cho mỗi người chơi/quest; giới hạn phải được kiểm tra và tăng trong cùng transaction để tránh hai lần nhận song song. Cấu hình timezone/reset giờ/ngày, reward và cap có mặc định, bounds và comment VI/EN.
- Ghi encounter/source ID idempotent cho kill/reward; kiểm tra killer/contributor, loại spawn, party attribution, item nộp và rollback lỗi. Không tính mob farm vô hạn từ spawner hoặc boss callback chạy lặp.
- [~] Tracking NPC quest dùng một task chung, cập nhật theo người chơi/vị trí NPC; khác world sẽ hiện hướng dẫn tên world, cùng world dùng A* có giới hạn để vẽ mũi tên particle theo mặt đất. Có công tắc particle riêng của người chơi trong GUI chi tiết quest; config server-wide vẫn override. Thay đổi đường đi chưa build/gameplay test; tracking sang lục địa khác trong cùng world chưa có region metadata để phân biệt.

**Skills và build class:**
- Thiết kế mage thành các nhánh lửa, băng và hỗ trợ; mỗi nhánh có active/passive, điều kiện mở, nâng rank, mana/cooldown, particle/sound, phạm vi, counterplay và UI thể hiện mục tiêu/self-cast. Bổ sung skill không cần target như buff bản thân, lá chắn hoặc dịch chuyển có điều kiện.
- Hoàn thiện mỗi class theo vertical slice trước khi mở status `available`: ít nhất nhiều hướng build có ý nghĩa, skill book, passive có implementation, menu/preview, quest hướng dẫn, cân bằng PvE/boss và gameplay test.
- Race buffs khai báo trong `races.yml` với stat, amount, giới hạn và nguồn áp dụng rõ; hiển thị tác động thực tế trong race selection/profile. Tránh buff ẩn và xếp chồng attribute không có cap; giữ tương thích profile đã tạo.
- Thêm ma trận kiểm tra để mọi skill trong YAML có handler/passive thực, mọi sách tham chiếu skill hợp lệ và class chỉ playable nếu nội dung cần thiết đã được xác minh.

**Story mở rộng:**
- Nối chapter metadata với quest chain, NPC dialogue, portal gates, boss encounter hiện có và cờ story persist; cho phép bỏ qua đối thoại mà không khóa tiến độ.
- Xây nhiều vùng theo nhịp khám phá: dấu hiệu → điều tra → miniboss → mở khóa vùng/di vật → hé lộ mối đe dọa lớn hơn. Mỗi chapter cần objective và phần thưởng, không chỉ title/summary.
- Đích truyện dài hạn là mở đường tới một boss cuối có tên gợi ý **“Đấng DIỆT THẾ Vô Danh”**. Chỉ cài cắm manh mối và điểm móc cấu hình/NPC; để trống boss, chỉ số và trận cuối để chủ server thiết kế sau.
- Gợi ý thêm: hỗ trợ quest co-op có attribution/reward không nhân đôi, nhật ký sự kiện theo nhân vật, mục tiêu tracking bằng tọa độ/cross-world, tùy chọn giảm/tắt particle cho accessibility và dashboard admin theo dõi lượt nhận/reward/dấu hiệu farm.

**Story reader — đã triển khai, chờ test:** 9 chapter mặc định có nội dung nối tiếp, kết thúc mở bằng manh mối về “Đấng DIỆT THẾ”; sổ truyện cũ vẫn mở sách Minecraft. NPC runtime loại `story` lưu chapter được gán, click phải phát chapter bằng typewriter trên action bar; admin shift+click phải để chọn chapter. NPC mới mặc định chapter đầu tiên nếu có chapter cấu hình. Không đưa nội dung dialog ra chat/lore. Cập nhật trình bày này chưa build/gameplay test. Chưa có khóa chapter theo tiến độ.

**Phần quest còn lại:** thay đổi scoreboard/journal/submit/boss/tracking ở working tree trước đó và hội thoại theo trạng thái mới hiện chưa gameplay test. Đã thêm GUI quản lý quest-dialog riêng (`/vnquestdialog admin`, cũng mở từ `/vnquest admin`), lựa chọn bốn trạng thái trong editor và luồng hội thoại trước nhận/nộp. Completed dialog mở sau lượt nộp cuối nhận thưởng và khi người chơi quay lại NPC sau khi quest hoàn tất; không tự bật giao diện giữa trận chiến. Unavailable dùng cho điều kiện chưa đạt hoặc đã đầy giới hạn quest đang làm. Chưa xác minh gameplay. Chưa có tuyến quest mẫu đầy đủ Thợ săn tiền thưởng/Giả kim, collect theo ID tổng quát, objective vùng/portal/crafting, boss contributor/party attribution và chống farm/spawner. Scoreboard sidebar API không hỗ trợ hyperlink click; `/vnquest detail <id>` mở trang chi tiết và GUI quest có thể click. Quest lặp tối đa 10 lần/ngày vẫn chỉ có API nền schema 9, chưa nối gameplay. Phím I mở inventory là phím client mặc định; Paper không cung cấp sự kiện nút I riêng để chuyển thành GUI plugin. Hiện menu plugin dùng F hoặc `/vnmenu`; không chặn inventory mặc định để tránh làm mất thao tác inventory.

### 5.3 Thế giới, portal và dungeon

- Mỗi realm/region định nghĩa level, mob pool, tài nguyên, boss, thời tiết/sự kiện và bảng VNCOIN.
- Portal hỗ trợ điều kiện quest/level/party, cooldown, combat tag và điểm đến an toàn; thông báo lý do bị chặn.
- Dungeon/instance theo phase: party, timer, checkpoint, boss encounter, loot attribution, reset và cleanup entity/block tạm.
- Thiên tai không phá vĩnh viễn hub, nhà người chơi hoặc vùng bảo vệ; mọi thay đổi địa hình có rollback/phạm vi.
- Endgame có nội dung lặp lại giới hạn như dungeon rotation, boss tuần, bounty; không chỉ tăng HP/damage.

### 5.4 Trang bị, crafting và sinh tồn

- Item RPG dùng ID/PDC ổn định, rarity, tier, stat roll có cap, nguồn nhận, binding/trade rule và version migration.
- Thêm profession/crafting theo phase: khai khoáng, săn bắt, giả kim, rèn; nguyên liệu phải có nguồn và sink rõ.
- Stat gear đi qua một pipeline thống nhất, có cap và hiển thị tương tác với armor/enchantment/skill/mercenary/boss.
- Hunger, thirst, weather và environment cân bằng theo world/biome/level; cảnh báo trước damage và cách khắc phục.
- Hub có PvP rule, bảo vệ block, spawn, merchant an toàn và chống mob farm/portal camp.

### 5.5 Party, PvP và xã hội

- Party tối thiểu: invite/accept/leave/disband, leader, chia EXP, loot attribution, quest sharing chống claim trùng và hiển thị đồng đội.
- Chốt PvP theo vùng/realm hoặc opt-in; protection người mới; không dùng mercenary/trade để grief.
- MVP không cấp VNCOIN từ player kill.
- Guild, claim và territory để sau party, khi đã có luật chống grief/chiếm giữ.

## 6. Kiến trúc và vận hành

### 6.1 Database/concurrency

- Migration tăng schema version; transaction, backup trước, rollback plan và test upgrade từ schema còn hỗ trợ.
- Tách repository/service cho profile, progression, wallet/ledger, quest và world progress; không để listener tự viết SQL.
- Async API có thứ tự/idempotency; lỗi DB không cập nhật GUI, không consume item/điểm/coin.
- Backup không chặn main thread; thiết lập busy timeout, retry/backoff, shutdown an toàn.
- Có diagnostics integrity và restore có xác nhận.

### 6.2 Config, permission và command

- Config economy đề xuất: economy.enabled, mob-drops.enabled, mob-drops.chance = 0.30, amount-by-tier, mob tier allowlist, spawn-origin eligibility, despawn, hopper và giới hạn reward.
- Validation số hữu hạn, min/max, enum, ID duy nhất; reload lỗi giữ config runtime cũ.
- Permission riêng cho xem tiền, lịch sử, quản trị ví, merchant và economy.
- Mỗi command đăng ký một lần; aliases tương thích nếu đã phát hành; usage/tab completion chính xác.
- Không cấp quyền admin bằng wildcard ngoài ý muốn; mọi thay đổi ví có audit.

### 6.3 Hiệu năng và bảo vệ

- Task lặp có interval/budget và dừng khi disable/reload; tránh scan toàn world hoặc quá nhiều entity/particle.
- Theo dõi thời gian DB, số giao dịch, coin tạo/tiêu, validation error, entity plugin và task đang chạy.
- Rate limit command/GUI ghi DB; cache có TTL/invalidation, không thay persistence.
- Log không ghi secret hoặc dump inventory/profile không cần thiết.
- Xác thực item plugin bằng PDC/version, không bằng tên/lore.

### 6.4 Phát hành và tài liệu

- Khóa Java/Paper/ProtocolLib và kiểm tra JAR phát hành trên ma trận phiên bản staging.
- Kiểm tra plugin.yml, command/permission, resource filtering, SQLite native library và khởi động từ data folder trống.
- Release checklist: backup, staging, log startup/reload, migration, người mới/cũ, restart/disable, permission và rollback.
- README ghi luật không ban khi chết, death penalty, grave, VNCOIN, lệnh, quyền và giới hạn bản phát hành.
- Trạng thái plan phân biệt planned/in progress/merged/automated test/gameplay test/released và ghi phiên bản/ngày kiểm chứng.

## 7. Lộ trình và tiêu chí chấp nhận

### Phase A — Sửa lỗi và giảm rủi ro

- [ ] Cast thất bại không mất mana/cooldown và không báo thành công; kiểm thử target mất hợp lệ/AoE rỗng.
- [ ] Rà mọi JDBC từ event/command; giảm truy vấn và chuyển luồng phù hợp sang DB executor an toàn.
- [ ] Registry từ chối active skill không handler và báo passive chưa hỗ trợ rõ ràng.
- [ ] Mỗi command đăng ký một lần; kiểm tra handler/permission/usage/tab completion.
- [ ] Chốt Java/Paper/ProtocolLib, hướng dẫn triển khai và smoke test JAR phát hành.
- [ ] Đối chiếu README/config/plan; giữ rõ luật chết không ban.

**Chấp nhận:** không có cast báo thành công sai; DB error không tiêu item/điểm/tiền; staging bật/reload/disable sạch; command và permission đúng.

### Phase B — VNCOIN core và mob drop

- [x] Xác minh schema hiện tại là v6; thêm migration v6→v7 tạo wallet, ledger và index trong transaction. Cơ chế migration hiện hữu tạo backup trước migration và rollback khi lỗi.
- [x] Thêm API giao dịch ví nguyên tử/idempotent; chặn số dư âm, vượt trần 2,147,483,647 và transaction ID trùng.
- [x] Thêm mob allowlist bốn tier; cấu hình hiện tại 20% tổng cơ hội, regular drop 1–2 VNCOIN, critical chiếm 5% trong số lần drop thành công với 5–7 coin. Chỉ mob có killer/spawn reason cho phép; boss/minion/lính RPG không dùng bảng cơ bản.
- [x] Item VNCOIN dùng `minecraft:sunflower`, PDC UUID/số lượng; claim được xử lý qua attempt-pickup nên không cần inventory trống, ghi ledger trước khi xóa entity; lỗi DB giữ/khôi phục item; chặn hopper, merge và người chơi tự thả coin đã gắn tag.
- [x] Boss có thể rơi 100 VNCOIN thành 20 item riêng ×5, bắn tỏa ra bán kính 5 block; mỗi người chơi có 750 ms giữa các lượt claim coin boss để nhiều người có cơ hội tranh loot.
- [x] Item entity rơi được đặt display name nhìn thấy được; tên item/boss/NPC/lính và tên lấy từ config được thay màu đen sang xám đậm/tím khi nạp hoặc rơi.
- [x] Thêm cài đặt nhãn item theo từng người chơi ở menu chính: tắt toàn bộ, hiển thị toàn bộ, chỉ RPG hoặc chỉ quý hiếm. Lưu lựa chọn trong PDC người chơi và lọc metadata packet riêng từng client để item vẫn hiện/nhặt được; thay đổi áp dụng ngay với item gần người chơi.
- Phân loại `RPG` dựa trên PDC namespace của plugin và metadata custom (tên/lore/enchant); `quý hiếm` gồm enchant/lore/tên tùy chỉnh, coin boss/critical và nhóm vật liệu hiếm. Có thể mở rộng bằng marker rarity chuẩn hóa cho từng hệ loot.
- [x] Thêm `/vncoin`, `/vncoin history` và admin `give|take`, permission riêng, reason, audit log và giới hạn mỗi giao dịch quản trị.
- [x] Thêm ví GUI có số dư/giao dịch gần đây, hiển thị số dư VNCOIN bằng sunflower trong hồ sơ nhân vật. Menu chính mở rộng 45→54 ô; tiện ích server giữ ở hàng trên, RPG chuyển vào submenu 54 ô, ví có mục riêng và hàng dưới chừa cho mở rộng.
- [x] Thêm log khởi tạo, pickup/lỗi DB và audit giao dịch admin; log từng mob roll/drop có cờ riêng `economy.mob-drops.debug-logs` (mặc định tắt để tránh spam console), đồng thời chỉ xét/log mob trong allowlist. Default config được merge mà giữ nguyên thiết lập server.
- [ ] Chạy gameplay trên server để xác nhận xác suất thực nghiệm, tier/loại mob, pickup khi inventory đầy, drop sau restart, menu/profile/4 chế độ nhãn, tên item, boss scatter và quyền admin; đo thu nhập/giờ và exploit mob farm.

**Chấp nhận:** tỷ lệ thực nghiệm quanh 20% trên mẫu đủ lớn; critical khoảng 1% số quái bị hạ theo xác suất có điều kiện; coin boss claim được một lần và rải đủ rộng; restart/backup/restore giữ đúng balance/ledger. Phần code đã triển khai nhưng Phase B chưa được coi là hoàn tất cho tới khi gameplay và migration được xác nhận trên server/staging.

**Ghi chú UI:** sunflower là biểu tượng vật phẩm rơi và số dư. Màn hình chính không nhồi từng chức năng RPG vào một trang; trang 54 ô chỉ là hub điều hướng, còn nhân vật, kỹ năng, bản đồ, quest và truyện ở submenu RPG. Giữ các slot viền và vùng trống ổn định để bổ sung menu mới mà không phải dời các icon hiện tại.

**Cập nhật triển khai 2026-10-25:** sửa nhãn thời gian cast từ `dbMs` sang `taskToMainCallbackMs` vì metric gồm cả chờ callback về main thread; DB riêng vẫn có `queueWaitMs` và `dbMs`. Phase B đã package thành công bằng Java 25 (`-Dmaven.test.skip=true`, không chạy test). JAR nằm ở `/home/ubuntu/DATA/VnMineHardcore-2.4.0-vncoin-phase-b.jar` và đã được đặt trong `VNMINE/plugins` cho lần khởi động kế tiếp. Server Paper hiện đang chạy, nên code trong tiến trình chưa đổi cho tới khi restart. JAR Phase A được giữ ở dạng `.bak`; chưa chạy gameplay/migration test cho Phase B.

**Cập nhật tiếp theo:** theo yêu cầu live feedback, cấu hình server được hạ xuống 20% rơi thường 1–2 VNCOIN, critical 5–7 VNCOIN với xác suất có điều kiện 5%; boss đủ điều kiện thưởng được chia 100 coin thành các drop 5 coin rải trong bán kính cấu hình. Thêm xử lý `PlayerAttemptPickupItemEvent` để VNCOIN ghi vào ví dù túi đầy, giới hạn claim boss mỗi người chơi, và nhãn nhìn thấy được cho item entity trên đất. Tên đen trong boss spider/eclipse được đổi và có lọc màu đen runtime cho item, boss, NPC/lính/config. Cần server test sau restart để xác nhận hành vi pickup đầy túi, boss scatter và hiển thị tên.

Build cập nhật các thay đổi này thành công bằng Java 25, `-Dmaven.test.skip=true`; không chạy test gameplay. JAR đã thay thế bản Phase B trong thư mục plugins cho lần bật kế tiếp. Server process hiện vẫn đang chạy và chưa nạp code mới.

Menu lọc item theo người chơi cũng đã package bằng Java 25 và chép vào cùng JAR Phase B trong `VNMINE/plugins`; cài đặt được lưu trên player PDC, không cần DB migration. Cần restart để nạp listener ProtocolLib mới.

### Phase C — Economy sink

- [ ] Merchant NPC với giá cấu hình, transaction nguyên tử và chống double click/disconnect (đã có code và compile; chờ test server).
- [ ] Thêm sink không bắt buộc như sửa gear/nguyên liệu tiện ích; merchant đã có trang xác nhận giá, còn sink gear chưa có và giao dịch cần test server.
- [ ] Thêm encounter/source ID idempotent cho VNCOIN boss payout và reward quest; không trả trùng khi event callback lặp hoặc server restart sát thời điểm boss chết.
- [x] `/vncoin history` và audit admin cơ bản đã có trong Phase B; leaderboard số dư vẫn là tùy chọn.
- [ ] Theo dõi lạm phát và ghi thay đổi giá/drop vào changelog.

**Chấp nhận:** mua bán không tạo/mất tiền khi lỗi; mọi giao dịch có ledger; người chơi hiểu cách kiếm/tiêu.

**Cập nhật triển khai 2026-10-25:** đã thêm merchant hệ thống bán danh mục chung có phân trang và merchant tùy chỉnh theo NPC. Admin có thể tạo `merchant`, mở `/vnnpc edit <id>` hoặc Shift-click NPC, chuyển item có PDC plugin vào bản nháp, chỉnh giá bằng nút ±1/10/100, gỡ món và lưu item/giá nguyên bản trong `npcs.yml`; cấu hình giới hạn số món/giá/mức bước. Boss drop được đánh dấu PDC RPG để có thể đưa vào shop; coin boss dùng ID drop xác định từ UUID encounter và vị trí drop để ledger từ chối claim trùng khi callback thưởng cùng encounter chạy lặp. Tiếp tục Phase C đã thêm bảng outbox giao vật phẩm (schema 8), trừ VNCOIN và ghi item chờ giao trong cùng transaction SQLite, đối soát lúc đăng nhập hoặc mở `/vncoin`, gắn transaction ID vào item để tránh nhân đôi, khóa item cho tới khi SQLite xác nhận, và lưu player data trước khi acknowledge. Nếu túi đầy, delivery vẫn chờ thay vì hoàn tiền không chắc chắn. JAR 2.6.0 đã được build và cài; native SQLite chỉ có Linux x86_64. Thêm `/vncoin stats` (cửa sổ ngày cấu hình 1-365, mặc định 30) tổng hợp phát hành/chi tiêu/net và tối đa 20 nguồn giao dịch; compile Java 25 thành công. JAR 2.7.0 build lúc 2026-10-25 04:39 UTC, dung lượng 1,897,304 bytes, SHA-256 `a3411f8e0022607738b6ad27bedc1d905d253c018491a4654ab80c2d730a970c`, đã cài vào `VNMINE/plugins`; native SQLite chỉ có Linux x86_64. Server chưa được restart và chưa chạy gameplay/migration test, nên merchant còn mở để xác minh disconnect/crash, túi đầy, migration DB và khôi phục; thống kê cần kiểm tra số liệu trên ledger server thật. Sink VNCOIN và thưởng quest idempotent chưa triển khai.

**Tiếp tục Phase C:** hỗ trợ cấu hình quest thưởng `vncoin` 1–1,000,000 bên cạnh `character-exp`. Khi hoàn thành, cập nhật trạng thái quest, cờ nhận thưởng và wallet ledger trong cùng transaction; ID giao dịch cố định theo UUID người chơi và quest ngăn trả thưởng lặp. Quest đã hoàn thành nhưng chưa nhận được thưởng sẽ được thử lại lúc vào server. Compile Java 25 thành công; chưa package/test gameplay. Merchant, thống kê và quest reward vẫn cần test trên server; sink VNCOIN chưa triển khai. *(Ghi chú “sink VNCOIN và thưởng quest idempotent chưa triển khai” ở đoạn lịch sử ngay trước đó đã lỗi thời; phần thưởng quest đã được triển khai, sink vẫn còn thiếu.)*

**Merchant purchase confirmation:** bấm offer giờ mở trang xác nhận có số lượng và giá cuối; chỉ nút xác nhận mới bắt đầu debit. Nút hủy quay lại đúng trang merchant. JAR 2.9.0 build lúc 2026-10-25 04:43 UTC, dung lượng 1,900,289 bytes, SHA-256 `8b7015859634f7714cb63e8dca95e6c9e3034176b639ba1d0298c7cf33688ff1`, đã cài vào `VNMINE/plugins`; native SQLite chỉ có Linux x86_64. Chưa test GUI trên server. Chưa có sink sửa gear riêng.

### Phase D — Class và trải nghiệm người chơi

- [x] Phân loại class available/locked/planned; ẩn class chưa playable.
- [ ] Hoàn thành từng class gồm skill, book, passive, UI, tutorial, balance và test.
- [ ] Tutorial hướng dẫn nhặt/dùng VNCOIN, merchant, portal và boss.
- [~] Mở rộng quest journal cho nhiều quest, preview reward và tracked quest; đã có GUI đang nhận/nhật ký/chi tiết/reward/track/cancel-confirm, tối đa 3 quest và sidebar theo từng mục tiêu. Chưa build/gameplay test; tutorial có journal riêng.
- [ ] Party cơ bản định nghĩa rõ chia EXP/loot; không nhân đôi VNCOIN.

**Tiến độ Phase D:** `player-classes.yml` hiện phân loại 3 class `available` (Lính kiếm, Vệ binh, Pháp sư) và 12 class `planned`; GUI chỉ hiện class có trạng thái available cùng skill đang được hỗ trợ. Cấu hình server cũ thiếu khóa availability dùng auto-detect để không làm hỏng character creation. JAR 2.10.0 trước đó build lúc 2026-10-25 04:57 UTC, dung lượng 1,900,771 bytes, SHA-256 `d4ac9f0916a66f396b7660f2f70a1b350d540600c709cd3bee131a83e67b8cc0`, đã cài vào `VNMINE/plugins`. Bản 2.11.0 bổ sung tracking tutorial; build SUCCESS lúc 2026-10-25 05:29 UTC, 1,914,375 bytes, SHA-256 `7666ceb5a907d1efc04f86215903a5e13b0711bbae14016613a75cd1f982b724`, cài tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.11.0.jar`. SQLite native đã xác minh chỉ có `org/sqlite/native/Linux/x86_64/libsqlitejdbc.so` và service metadata. JAR cũ được chuyển ra `.old-builds` khỏi thư mục plugin đang quét. Cần restart và kiểm tra GUI/particle trên server. Các class còn lại chưa được làm skill/UI/tutorial/balance.
- [ ] Trang bị/crafting theo tier sau khi economy sink ổn định.

**Chấp nhận:** người mới tự tạo nhân vật, hiểu class, hoàn thành tutorial, kiếm/tiêu coin và tới boss đầu mà không cần admin.

### Phase E — Endgame và phát hành

- [ ] Region theo tier/quest, portal gate và thông báo.
- [ ] Dungeon/instance có cleanup, checkpoint, attribution, party và giới hạn farm.
- [ ] Cân bằng boss, disaster, mercenary, gear và death penalty qua nhiều class.
- [ ] Kiểm thử tải, DB contention, backup/restore, reload, restart, chunk unload và offline player.
- [ ] Phát hành staging, lấy feedback, cập nhật hướng dẫn và kế hoạch rollback.

**Chấp nhận:** không mất dữ liệu theo kịch bản lỗi định nghĩa; không còn exploit nhân đôi coin/loot đã biết; TPS đạt mục tiêu tải dự kiến.

## 8. Ma trận kiểm thử trước server chính

### Economy

- Mob hợp lệ chết do player/projectile/lính/pet/môi trường.
- Mob passive, summon, spawner/farm, boss/event, không killer và PvP.
- Pickup thường, inventory đầy, hopper, lava, despawn, logout, chunk unload/load.
- Pickup đồng thời, clone item/entity, DB busy/readonly/disk full, crash giữa claim.
- Admin sai quyền, số âm/quá lớn, offline target và audit.
- Merchant spam click, disconnect, stack item, inventory full, DB failure.

### RPG/death/runtime

- Cast thành công/thất bại, thiếu mana, target mất, cooldown, class sai, PvP/đồng minh và vật cản.
- Class thiếu handler, YAML sai schema/duplicate key, sách cũ sau update.
- Chết trong/ngoài combat, respawn protection, grave, inventory history, death penalty recovery, quit.
- Không ban người chơi trên mọi đường death/quit; thông báo rõ mất đồ/penalty.
- Quest reward one-time, restart, portal gate, NPC persistence và task cleanup.

## 9. Đề xuất sâu hơn cho server

- Theo dõi ngân sách kinh tế: coin mới tạo, coin tiêu, số dư trung vị/top balance; cân bằng theo mùa thay vì chỉnh drop theo cảm giác.
- Bounty board theo mob/boss/region có giới hạn ngày, xoay vòng và chống farm mob trong khu kín.
- Achievement/mốc tiến trình một lần (boss đầu, realm mới, chapter) làm nguồn coin không farm vô hạn.
- Làm reward attribution trước khi mở party: sát thương/assist theo cửa sổ; một encounter không thể trả nhiều reward do đổi chủ/summon.
- Merchant theo tier và giá mua lại thấp hơn giá bán để không tạo vòng tiền vô hạn.
- Dashboard admin tối giản: ví/ledger, coin tạo/tiêu, drop rate, farm đáng ngờ, quest claim lỗi, transaction errors.
- Chính sách mùa công bố trước: ví được giữ hay reset, ngày reset, snapshot và phần thưởng chuyển tiếp.
- Thử thách đến từ gameplay, thời gian, đồ và penalty được giải thích trước; **chết không ban**, luôn có đường hồi phục để tiếp tục.

## 10. Nguyên tắc triển khai

- Mỗi phase có scope, tiêu chí chấp nhận và kiểm thử trước khi chuyển phase.
- YAML mới chưa phải tính năng hoàn chỉnh nếu thiếu validation, permission, UI/lệnh, persistence, logging và lỗi fallback.
- Không consume item/cấp điểm/cập nhật balance trước khi transaction thành công.
- Async không gọi Bukkit API; cache không thay dữ liệu bền vững.
- Không ban người chơi khi chết hoặc thoát game. Mọi death penalty phải giữ nguyên nguyên tắc này.
- 30% và 1–10 VNCOIN là baseline yêu cầu; mọi điều chỉnh phải ghi config và changelog.

## Cập nhật NPC story và quest-dialog — 2026-10-25

- Đã triển khai và build trong `2.16.0`: NPC `story` có chapter ID lưu bền vững và GUI kính phát nội dung từng trang bằng typewriter; admin shift+click phải để gán chapter. NPC quest-giver mở quest-dialog theo trạng thái; bốn loại dialog được chọn trong giao diện **sửa quest**, không phải giao diện sửa NPC. Có GUI riêng để tạo/sửa/xóa quest-dialog. Build đã xác nhận compile/package; gameplay test còn chờ.
- Cần xác minh sau build/deploy: NPC cũ vẫn nạp được `npcs.yml` không có chapter ID; prompt GUI lưu và hủy đúng; typewriter đóng/hủy không chạy callback ngoài ý muốn; các trạng thái nhận quest/đang làm/nộp item/hoàn thành/đã hoàn thành/không đủ điều kiện hiển thị đúng dialog; xóa dialog đang được tham chiếu bị chặn.

### Điều chỉnh cách trình bày hội thoại và góc xoay `/vntext` — 2026-10-25

- Đã đổi story chapter và quest-dialog thành typewriter trên action bar với định dạng `[Tên NPC:]`; nội dung không được gửi vào chat hay lore. Chapter story vẫn gán bằng shift+chuột phải. Từ danh sách NPC, người chơi chọn quest; dialog trạng thái chạy xong thì quest khả dụng mở GUI chi tiết/xác nhận nhận, còn dialog nộp item mở GUI xác nhận nộp.
- Đã thêm tham số rotation `0-360°` độc lập với facing cho `/vntext create|edit`; cú pháp cũ vẫn hoạt động với rotation mặc định `0°`. Cấu hình text-display cũ nạp rotation bằng giá trị mặc định.
- Đã cập nhật README và CHANGELOG. Build/package `2.17.0` thành công; test Paper/gameplay còn chờ. Cần xác minh compile đã đạt, định dạng action bar, callback mở đúng GUI, cú pháp mới/cũ của `/vntext` và persistence rotation.

## Quest objectives, shop thu mua và kích thước GUI — 2026-10-25

- Đã thêm objective `visit-npc` tuần tự: admin chọn NPC ID tồn tại trong GUI; tương tác trực tiếp NPC đó tăng tiến độ. Có thể đặt mục tiêu gặp NPC làm bước đầu, rồi thêm bước nộp item RPG đã cấu hình. Nộp item dùng `ItemStack` đầy đủ (Material, tên, enchant/PDC), có xác nhận và chỉ trừ item khi đã kiểm tra số lượng.
- Shop custom merchant có danh sách buy-order trong `npcs.yml`; **strict-scope đã được triển khai ở E1**: chỉ nhận item khớp chính xác buy-order của NPC hiện tại. System merchant dùng allowlist Material trong `merchant-prices.yml` và `sell-value` trên một số định nghĩa item RPG (đang tiếp tục E2). Giao dịch bán vẫn cần phase escrow để chịu crash.
- Item entity trên mặt đất hiển thị quote tốt nhất trong các shop có thể thu mua; item không có giá hiển thị “Không có giá thu mua”. Giá fallback chung 64=1 và Material override cũ đã được loại khỏi luồng quote; bảng giá vanilla quản lý tại `merchant-prices.yml`.
- Quest editor được bố trí lại theo nhóm: thông tin/điều kiện, mục tiêu/thưởng, dialog, EXP và VNCOIN; các chest GUI trước đây 27 ô được nâng lên 54 ô. Thanh Quick Cast vẫn 9 ô.
- **Cập nhật 2026-10-26:** đã viết intent/escrow và recovery sau join hoặc mở `/vncoin` cho giao dịch bán. Luồng còn chưa kiểm thử build/restart; vì vậy chưa xác nhận crash-safety trên Paper và vẫn để E2 ở trạng thái đang làm.
- **Phân loại danh sách tham khảo:** danh sách user đưa có 29 mã (dù ghi 30). Đã có tương đương: `ENTITY_KILL` (mob/boss), collect/submit item, `SELL`/`PURCHASE` qua shop, và objective gặp NPC (bổ sung ngoài danh sách). Có thể triển khai trực tiếp bằng Paper events nhưng chưa làm: `BLOCK_BREAK`, `BLOCK_PLACE`, `FARMING` (cần chỉ tính cây trưởng thành), `FISHING`, `TAME`, `ENCHANT`, `BREW`, `SMELT`, `CRAFT`, `ENTITY_DAMAGE`, `EXPERIENCE_GAIN`, `ITEM_CONSUME`, `ITEM_BREAK`, `ITEM_MENDING`, `RESURRECT`, `SMITHING`, `HATCHING`, `INVENTORY_OPEN`, `INVENTORY_CONTENT`, `SHEAR`. Cần điều tiết task/ray trace và chống spam: `LOOK_AT_BLOCK`, `LOOK_AT_ENTITY`; cần danh sách whitelist có kiểm tra quyền và kết quả: `COMMAND`; cần dependency/sự kiện ngoài hoặc contract tích hợp: `VOTE`; cần mô hình vùng/WorldGuard: `CUBOID`; cần public event/API và giới hạn chạy: `CUSTOM`. Tránh cộng tiến độ từ event đã bị cancel hoặc từ hành động không thuộc người chơi.
- Đã build/package thành công ở bản `2.17.0` (2026-10-25 10:40:24 UTC); compile/package được xác minh, test tự động bị bỏ qua. Chưa gameplay test. Cần test objective gặp NPC và nhiều bước submit, buy/sell exact và fallback/default, lỗi wallet/đủ số lượng/đầy túi, label item khi load chunk/restart, cấu hình cũ `npcs.yml` và toàn bộ GUI 54 ô.

## Cập nhật phát hành 2.17.0 — 2026-10-25

- Tăng phiên bản `2.16.0` → `2.17.0`; cập nhật README và CHANGELOG cho objective gặp NPC, shop thu mua, nhãn giá item rơi, hội thoại action bar, rotation `/vntext`, particle tracking và GUI 54 ô.
- Maven `clean package -Dmaven.test.skip=true` thành công bằng JDK 25 lúc `2026-10-25 10:40:24 UTC`. Đây chỉ xác nhận biên dịch/đóng gói; test tự động và gameplay chưa chạy.
- JAR: `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.17.0.jar`, `2,093,432` bytes; SHA-256 `c04b77716cf37d8dfcb4995d97d713284196a04cb75f6d030e3a265968362b90`.
- Đã xác minh chỉ có native SQLite `org/sqlite/native/Linux/x86_64/libsqlitejdbc.so`; JAR giữ `org/sqlite/JDBC.class` và `META-INF/services/java.sql.Driver`. Bản `2.16.0` cũ được chuyển vào `.old-builds` để Paper không nạp hai bản plugin cùng lúc.

## Sửa stack item và trải nghiệm quest — 2026-10-25

- Đã thêm chuẩn hóa cho block item rơi ra chỉ khi stack không có PDC, tên/lore/enchant, custom model, modifier hay metadata block đặc biệt. Các stack vanilla sạch được dựng lại theo Material và số lượng để loại thành phần phụ làm chúng không stack; metadata của item RPG/custom được giữ nguyên.
- Nhật ký nhiệm vụ có nút riêng cho từng quest, hiển thị trạng thái theo dõi bật/tắt; có nút particle ở trang nhật ký và trang chi tiết, đổi nhãn ngay sau click và lưu PDC người chơi.
- Waypoint hiện chọn NPC theo objective đang chạy: `VISIT_NPC` dẫn tới NPC mục tiêu, `SUBMIT_EXACT_ITEM` dẫn tới NPC giao quest; objective hạ mob/boss hoặc thu thập không còn chỉ về NPC sai. Hint cũ được xóa khi không có đích.
- Khi quest NPC hoặc tutorial hoàn thành, người chơi nhận title/subtitle có tên quest và phần thưởng. Bình EXP được đặt giữa cụm nút chỉnh EXP và nằm ngay trên icon VNCOIN trong editor.
- Đã build/package thành công bản `2.17.1`; chưa kiểm thử Paper/gameplay. Cần xác minh stack torch và các block drop thường/custom, toggle tracking/particle qua mở lại menu và đăng nhập lại, điều hướng cho từng loại objective, title/subtitle và vị trí icon EXP/VNCOIN.

## Cập nhật phát hành 2.17.1 — 2026-10-25

- Tăng phiên bản `2.17.0` → `2.17.1` cho các sửa lỗi và cập nhật UX; README, CHANGELOG và kế hoạch đã được đồng bộ.
- Maven `clean package -Dmaven.test.skip=true` thành công bằng JDK 25 lúc `2026-10-25 11:04:24 UTC`. Xác nhận compile/package; test tự động bị bỏ qua và gameplay Paper chưa chạy.
- JAR: `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.17.1.jar`, `2,095,106` bytes; SHA-256 `2403fbf7e71a9df7c4a6455787659a09144cf6ffda412d4fffe25cb639d9abe2`.
- Đã xác minh chỉ có native SQLite `org/sqlite/native/Linux/x86_64/libsqlitejdbc.so`, đồng thời giữ `org/sqlite/JDBC.class` và `META-INF/services/java.sql.Driver`. JAR `2.17.0` cũ được chuyển vào `.old-builds` để Paper không nạp trùng phiên bản.

## Cập nhật phát hành 2.16.0 — 2026-10-25

- Build Maven `clean package` thành công bằng JDK 25 lúc `2026-10-25 09:42:57 UTC`; test tự động được bỏ qua (`-Dmaven.test.skip=true`), vì vậy kết quả xác nhận compile/package, chưa xác nhận gameplay.
- JAR: `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.16.0.jar`, `2,058,193` bytes; SHA-256 `a4d839445456b486b7b5c54e24bb66fb669bee615fa2c8df06ff7fc1d9f8c755`.
- Đã kiểm tra JAR chỉ chứa native SQLite `org/sqlite/native/Linux/x86_64/libsqlitejdbc.so`, đồng thời giữ `org/sqlite/JDBC.class` và `META-INF/services/java.sql.Driver`.
- Đã tăng version `2.15.0` → `2.16.0`; tính năng đã ghi trong README/CHANGELOG. Chưa test trên Paper thật; cần kiểm tra editor quest/dialog, hội thoại bốn trạng thái, NPC story và tương thích dữ liệu NPC cũ.

## Cập nhật phát hành 2.15.0 — 2026-10-25

- Đã thêm `/vntext` để admin tạo/quản lý text display cố định với 6 hướng, màu tên hoặc mã hex, scale tùy chỉnh và lưu bền qua chunk/restart. Lệnh được đăng ký quyền `vnmine.command.text.manage`; giới hạn mặc định 100, cấu hình `rpg.text-display.max-active` (1–500). README và CHANGELOG đã cập nhật.
- Đã hoàn thiện trong working tree GUI nhật ký nhiệm vụ, scoreboard tối đa 3 quest, objective nộp item/hạ boss và tracking tới NPC; các phần này có trong cùng JAR phát hành nhưng chưa gameplay test.
- Tăng version từ 2.14.0 lên 2.15.0. Maven `clean package` thành công bằng JDK 25 vào 2026-10-25; test được bỏ qua (`-Dmaven.test.skip=true`), vì vậy đây là xác minh compile/package, không phải xác nhận gameplay.
- JAR: `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.15.0.jar`, 2,028,299 bytes, SHA-256 `5892307b6ed5569153cb2ea2dcb8f2f2cce605968778ab9342f24b4d039cd7f7`.
- Đã xác minh chỉ đóng gói native SQLite `org/sqlite/native/Linux/x86_64/libsqlitejdbc.so`, đồng thời giữ `org/sqlite/JDBC.class` và `META-INF/services/java.sql.Driver`. Chưa kiểm thử trên server thật; cần restart và test command `/vntext` theo 6 hướng/màu/scale, persistence sau restart, cùng luồng quest/scoreboard/tracking và nộp item.

## Kế hoạch flow khởi đầu RPG và kinh tế merchant — 2026-10-26

Phần này ghi nhận yêu cầu và kết quả rà soát mã nguồn cho hành trình người chơi mới, class theo chủng tộc, chuỗi tutorial, dấu hiệu NPC và quy tắc mua item. Đây là kế hoạch, chưa phải chức năng đã triển khai. Chỉ sửa `plan.md`; chưa thay đổi code/config, chưa build hoặc chạy test.

### Đánh giá hiện trạng và điểm cần thiết kế trước

- **Chưa có bước chọn đại lục khi đăng nhập lần đầu.** `CharacterSelectionManager.finalizeSelection` tạo hồ sơ sau khi người chơi chọn race/class. Bảng profile hiện yêu cầu race/class; tutorial quest chỉ khởi tạo khi hồ sơ active tồn tại. Flow mong muốn cần lưu đại lục và hoàn thành cuộc gặp tutorial NPC trước khi chọn race/class. Không thể chỉ đổi thứ tự mở GUI: cần trạng thái onboarding bền vững độc lập hoặc thay đổi schema hồ sơ.
- **NPC runtime đã biết world nhưng chưa biết đại lục.** `NpcManager.NpcRecord` lưu world/tọa độ; `getNpcTarget` trả được world và vị trí nếu world đã nạp. Chưa có realm/continent ID gắn với NPC. Cần ID đại lục ổn định trên cấu hình đại lục, spawn và NPC, đồng thời validate world/realm nhất quán. Nếu NPC ở world khác, tracker có thể hướng dẫn người chơi đi tới world trước; sau đó mới hiện navigation cụ thể.
- **Các world đang sẵn sàng chưa bao phủ mọi lựa chọn trong `classes.md`.** Resource hiện có cấu hình `human-realm` và `yeu-realm`; plugin không tự tạo world. Chỉ cho chọn đại lục có world, spawn và tutorial NPC hợp lệ; lựa chọn chưa sẵn sàng phải bị khóa/ẩn với lỗi cấu hình rõ ràng.
- **Tutorial hiện tự bắt đầu quest `visit-realm-spawn` khi profile active được nạp**, không phải chuỗi gặp tutorial NPC rồi chọn race/class. Cần state machine: chọn đại lục → gặp đúng tutorial NPC → chọn race → chọn class → cấp skill book → cấp quest tutorial. Mỗi bước phải idempotent để reconnect/callback lặp không tạo trùng dữ liệu.
- **Class cần là tổ hợp riêng theo chủng tộc.** `classes.md` là tài liệu tham khảo; registry hiện tìm class theo race nhưng chưa hiện thực các nhánh nghề riêng như Human Mage, Demon Witch, Fairy Priest. Cần giữ ID nghề cơ bản ổn định và tránh dùng chung class ID theo cách khiến UI/sách/skill bị lẫn.
- **Chưa có mô hình tiến chức class.** Class cao cấp cần kế thừa skill class cha theo ID, cộng chỉ số và mở skill mới. Cần điều kiện level/item/quest/NPC có thể cấu hình; chặn chu trình kế thừa, skill trùng và nhận tiến chức lặp; giữ nguyên skill đã học khi migration.
- **Chưa có cấp skill book đầu tiên theo tổ hợp race-class.** Cần GUI admin ánh xạ `race + class → starter skill book`, preview và validate tham chiếu. Cấp sách đúng một lần; nếu inventory đầy, dùng luồng nhận lại/kho thư bền vững thay vì làm rơi hoặc mất sách.
- **Chưa có dấu `!`/`?` cá nhân trên NPC.** `!` phải phụ thuộc quest có thể nhận của từng người; `?` phải phụ thuộc trạng thái đã đọc story/tutorial của từng người/chapter. Không thể dùng marker toàn cục. Cần thiết kế viewer-specific và dọn marker khi người/NPC/chunk/world unload.
- **Đã xác định và sửa ở E1:** trước đây custom shop có thể lấy báo giá từ merchant khác hoặc giá mặc định do `EconomyManager.openMerchantSellInventory` fallback sang `NpcManager.getBestMerchantBuyQuote` rồi `groundItemSellQuote`. Luồng custom hiện chỉ hiện buy-order thuộc đúng NPC; system quote đang được chuyển sang allowlist riêng trong E2.
- **Chưa đủ dữ liệu để khẳng định nguyên nhân duy nhất của lỗi bán lần hai cần đủ 64 item.** Fallback 64=1 phù hợp với triệu chứng khi item không khớp exact buy-order, nhưng cần kiểm tra order trong `npcs.yml`, metadata/PDC của item, số lượng cấu hình và log kết quả `ItemStack.isSimilar`. Phase sửa shop phải có log quote source, NPC/order ID, số lượng yêu cầu/đang có và item identity an toàn; kiểm thử bán lặp trước/sau restart.
- **Lịch sử rủi ro giao dịch bán (đã có bản sửa chưa xác minh):** trước đây inventory được lưu sau khi trừ item, rồi mới ghi giao dịch ví bất đồng bộ. E2 hiện đã thêm intent/escrow, khóa item và đối soát bền vững; cần migration/restart testing để xác nhận bản sửa khép đúng cửa sổ crash trước khi mở rộng thu mua.

### Nguyên tắc thiết kế

1. Tách “đã chọn đại lục” khỏi “đã hoàn tất tạo nhân vật”; không tạo race/class mặc định để lách thứ tự onboarding.
2. Lưu trạng thái onboarding, cấp sách, mở khóa quest và giao dịch shop bền vững, có khóa idempotency và phục hồi sau restart.
3. Cấu hình mới có default an toàn, validation, chú thích tiếng Việt/Anh và ID ổn định thay cho tên hiển thị.
4. Không để người mới kẹt vì world/NPC chưa nạp hoặc inventory đầy; hiển thị bước tiếp theo và hướng dẫn world/portal.
5. Giữ nguyên luật tử vong: không ban người chơi khi chết.

### Phase A — Đại lục và onboarding người mới

- Tạo registry đại lục có ID, tên/icon/mô tả, world ID, spawn, tutorial NPC ID và trạng thái khả dụng. Admin chỉ mở lựa chọn đã cấu hình hợp lệ; plugin không tự tạo world.
- Lưu `selected_continent` và các bước onboarding theo UUID, độc lập với profile race/class hiện có. Migration giữ nguyên người chơi cũ, không tự mở GUI hoặc đổi class.
- Đăng nhập lần đầu: sau khi dữ liệu đã tải mới mở GUI chọn đại lục; chống mở lặp khi reconnect/callback lặp. Nếu không có đại lục hợp lệ thì không giao quest hỏng, đồng thời log cấu hình thiếu.
- Sau chọn đại lục, giao objective gặp đúng `tutorialNpcId`. Nếu ở world khác, hiện world đích; navigation theo tọa độ chỉ bật khi cùng world. NPC thiếu, world chưa nạp hoặc mapping sai phải giữ tiến trình và báo lỗi cụ thể.
- Chỉ sau cuộc gặp mới mở chọn race rồi các class hợp lệ thuộc race đó. Lưu từng bước trước khi chuyển giao diện.
- **Chấp nhận:** đúng thứ tự; reconnect không reset; profile cũ không bị đưa vào flow; không thể hoàn tất nhầm NPC ở world khác; thiếu world/NPC có thông báo cho player/admin.

### Phase B — Nghề theo chủng tộc, sách đầu tiên và tiến chức

- Dùng `classes.md` để chuẩn hóa các nghề cơ bản theo cặp race-class. Mở từng vertical slice có skill, sách, chỉ số, UI và hướng dẫn hoàn chỉnh; không mở class chỉ vì đã khai báo.
- Thêm GUI quản trị ánh xạ `race ID + base class ID → starter skill-book ID`, preview nội dung và chặn lưu nếu class/sách không tồn tại hoặc bị tắt.
- Cấp sách đúng một lần và đánh dấu bền vững. Nếu túi đầy, tạo claim/kho thư có ID duy nhất, không làm rơi sách hoặc nhân đôi khi reconnect.
- [~] Nền tảng `parent-class-id` đã được nạp/kiểm tra: parent phải tồn tại, cùng race và không tạo chu trình; skill từ class cha được nhận diện xuyên cây skill, sách, học/nâng cấp, quick slot, passive và cast.
- [~] GUI cây skill có mục tiến chức; hiển thị class con có skill hỗ trợ, cấp/quest/NPC/vật phẩm yêu cầu, preview và xác nhận. Kiểm tra class hiện tại/cấp/quest hoàn thành trong SQLite; Material hoặc RPG quest-item theo ID phải có trong túi, NPC phải từng được gặp; lưu class mới cùng lịch sử, giữ skill đã học.
- [~] GUI lịch sử class hiển thị base/current class và các lần tiến chức theo thời gian.
- [ ] Hỗ trợ ID item boss/system và tiêu thụ nguyên liệu bền vững; thêm chỉ số class và skill mở mới; quản lý chống skill trùng/tiến chức lặp qua chuỗi.
- Lưu rõ class gốc, class tiến chức và nguồn skill đã học; migration class cũ phải có bảng ánh xạ và giữ skill.
- **Chấp nhận:** mỗi tổ hợp race-class nhận đúng sách một lần; class cao dùng được skill kế thừa; điều kiện tiến chức đủ/thiếu và migration class cũ hoạt động.

### Phase C — Quest tutorial nối tiếp

- Bổ sung quest editor để chọn quest successor được tự giao sau khi quest trước hoàn thành. MVP giới hạn một successor để đơn giản, nhưng schema nên mở rộng được cho nhánh.
- Hoàn thành objective, ghi reward và giao successor phải idempotent. Nếu chạm giới hạn 3 quest active, lưu successor ở trạng thái chờ nhận thay vì làm mất; khuyến nghị tự nhận tutorial khi còn chỗ, còn lại thông báo và giữ pending.
- Validate successor tồn tại/đang bật, không tự trỏ hoặc tạo chu trình; xử lý quest bị tắt/xóa sau khi đã nhận.
- **Chấp nhận:** callback lặp/restart không nhân quest/reward; giới hạn active không làm đứt chuỗi.

### Phase D — Marker NPC theo người chơi

- Hiện `!` vàng trên NPC có quest người chơi đủ điều kiện nhận: quest đang bật, đúng giver, đạt requirement, chưa nhận/chưa hoàn thành và còn slot.
- Hiện `?` trên NPC có tutorial/story chưa đọc; lưu trạng thái đọc theo UUID và chapter/story ID. Chỉ đánh dấu đã đọc khi dialog kết thúc.
- Nếu NPC có cả quest lẫn story, cấu hình thứ tự/marker tổng hợp để tránh chồng entity và tên.
- Chọn cách hiển thị riêng viewer dựa trên API Paper/ProtocolLib; dọn marker khi chunk/entity/player unload, đổi world, đổi điều kiện hoặc plugin disable.
- Cập nhật theo event quest/story và chu kỳ giới hạn, không quét mọi NPC/quest mỗi tick; đặt trần marker và đo chi phí.
- **Chấp nhận:** hai người có trạng thái khác nhau thấy marker khác nhau; nhận quest/đọc chapter cập nhật đúng; không sinh marker/task rác sau unload/restart.

### Phase E — Merchant và kinh tế VNCOIN

#### E1. Custom merchant strict-scope

- Custom merchant chỉ nhận buy-order thuộc chính NPC đó; item không cấu hình tại NPC thì không thể bán ở đó. Không global quote, không Material fallback.
- Buy-order ghi chính xác item identity, số lượng giao và VNCOIN nhận; lần bán tiếp theo dùng cùng order, không phát sinh yêu cầu 64 ngoài cấu hình.
- Log chẩn đoán order/NPC, số lượng yêu cầu/đang có, quote source, Material và RPG item ID/PDC fingerprint an toàn. Test item metadata, stack bị tách, bán hai lần, restart và click cạnh tranh.

##### Trạng thái E1 sau khi bắt đầu triển khai — 2026-10-26

- [~] Shop custom bỏ quote từ merchant khác và giá fallback cho item không có buy-order tại NPC hiện tại; danh sách chỉ thêm đúng các order của NPC đó.
- [~] Trước khi trừ item, xác minh buy-order còn tồn tại và số lượng/giá chưa đổi kể từ lúc mở GUI; log NPC, offer ID, Material, số lượng có/cần, giá, key PDC và 8 byte đầu của SHA-256 item serialization (không ghi giá trị PDC).
- [~] README/CHANGELOG mô tả rõ phạm vi mua của custom shop. System merchant vẫn giữ logic cũ cho tới E2.
- Chưa compile/gameplay test. Cần xác nhận item metadata khác nhau bị phân biệt đúng, số lượng cấu hình chính xác kể cả stack bị tách, bán liên tiếp, order bị admin sửa khi GUI đang mở, và không còn item không cấu hình trong custom shop trước khi coi E1 hoàn tất.

#### E2. System merchant và item RPG

- Tạo `merchant-prices.yml` riêng, giá được biểu diễn là “số lượng giao → VNCOIN trả”, có chú thích VI/EN, bounds và default. Chỉ mua Material được allowlist; item không khai báo/tắt thì không mua, không dùng giá mặc định toàn cục cho mọi item. **Đã triển khai; chờ kiểm thử server.**
- Item RPG chỉ được system merchant mua theo `sell-value` trong định nghĩa item; default `0` nghĩa là không mua. Không lấy giá theo Material nền hoặc lore.
- Thêm `sell-value: 0` vào định nghĩa item RPG hiện có (sách, gear, nguyên liệu quest, từng item thưởng quest, boss loot/token và gear lính); mặc định an toàn, giới hạn 0-1000000, có mô tả VI/EN. Thay đổi config quest/army có hiệu lực sau restart; `/vnreload` áp dụng giá boss/merchant theo loader sẵn có.
- Nếu custom merchant có giá mua tốt hơn cho item chính xác đó, system merchant hỏi xác nhận và nêu NPC/tên/ID, world, lượng giao và X VNCOIN. Yes vẫn bán theo giá system; No quay lại. Chỉ cảnh báo nếu người chơi đủ lượng giao và so sánh cùng item identity/số lượng tương đương.
- Thay giao dịch trừ item rồi mới ghi ví bằng intent/escrow bền vững, transaction ID idempotent và đối soát khi restart; không mất item hoặc cộng coin hai lần. **Đã viết luồng SQLite intent, khóa PDC, lưu inventory, settlement idempotent, hoàn hàng qua delivery queue khi ví chạm giới hạn và recovery khi join/mở `/vncoin`; chưa compile/restart-test nên chưa xác nhận bảo đảm vận hành.**

##### Trạng thái E2 sau khi bắt đầu triển khai — 2026-10-26

- [~] Thêm `merchant-prices.yml` làm allowlist strict theo Material; giá thiếu/Material không hợp lệ không tự nhận giá mặc định. Loader kiểm tra schema/duplicate/bounds atomically, giữ bảng hợp lệ trước đó khi reload file lỗi; `/vnreload` nạp lại file.
- [~] Đưa mốc giá sắt, kim cương, khuôn Netherite, thịt sống/chín vào allowlist, cộng giá thận trọng cho một số tài nguyên farmable. Đã bỏ universal ground-item fallback khỏi luồng quote; nhãn item đất báo không có giá khi cả system lẫn custom merchant đều không nhận.
- [~] Bắt đầu `sell-value` (0 mặc định, 0-1000000 VNCOIN) cho skill book, drop boss thường, drop theo level, armor set, token boss và gear lính do boss thả; giá được gắn PDC để phân biệt với vanilla cùng Material. Unknown plugin-tagged RPG item không dùng giá Material. Skill-book registry nạp khi khởi động; boss definitions được đọc lại qua `/vnreload`.
- [~] Bổ sung `objective.sell-value` cho item quest RPG, `reward.item-sell-values` theo thứ tự các stack thưởng và `gear-system.sell-value` cho gear lính tạo ngoài loot boss; mọi giá mặc định 0 và gắn vào item bằng PDC. File quest cũ tự được thêm trường mặc định lúc khởi động, GUI xem thưởng hiển thị giá từng món, thay item trong rương admin giữ giá theo item khớp chính xác. Chưa compile/gameplay test.
- [~] System merchant cảnh báo khi người chơi đủ số lượng để bán cùng item chính xác tại custom NPC có tỷ lệ trả/đơn vị cao hơn; Yes vẫn dùng giá system. Đối chiếu lại giá và cảnh báo lúc xác nhận. Bổ sung log quote/reject/validate kèm NPC, Material, số lượng, nguồn và hash rút gọn item serialization.
- [~] Thêm SQLite intent/escrow khi bán, trạng thái PREPARED/TAGGED, khóa item bằng PDC, transaction ID idempotent, đối soát sau join hoặc mở `/vncoin`, và hoàn item qua hàng đợi giao bền vững nếu ví chạm giới hạn. Chặn di chuyển/sử dụng và ngăn death drops cho item escrow; log các bước, lỗi và khôi phục. Đây mới là triển khai mã, chưa xác minh crash/restart trên server.
- Unknown plugin-tagged items vẫn bị từ chối nếu chưa có `rpg_sell_value`; cần tiếp tục rà soát các nguồn item hệ thống khác ngoài skill book, boss loot, quest, token và gear lính để xác nhận không bỏ sót catalog.
- Chưa compile/gameplay test. Cần kiểm tra migration schema 11→12, restart tại từng trạng thái intent, reconnect, thao tác inventory/death, đủ/thiếu item, balance limit, DB lỗi, exact PDC identity, cảnh báo custom khác world, reload khi GUI đang mở, và không fallback cho item RPG giá 0 trước khi hoàn tất E2.

#### E3. Bảng giá khởi điểm để cân bằng

**Cập nhật 2026-10-26:** các mức trong bảng dưới đã được đưa vào `merchant-prices.yml` làm giá khởi điểm có thể chỉnh. Mỗi hàng là tổng lượng item giao đổi lấy VNCOIN, không phải giá từng item. Chưa xác minh nguồn cung thực tế và lạm phát trên server.

| Nhóm | Item | Lượng giao | VNCOIN | Ghi chú |
|---|---|---:|---:|---|
| Khoáng sản | `IRON_INGOT` | 10 | 1 | Mức thận trọng nếu iron farm vẫn hoạt động; cần chốt mâu thuẫn giá/farm trước khi triển khai. |
| Khoáng sản | `DIAMOND` | 1 | 10 | Mốc người dùng yêu cầu. |
| Nâng cấp | `NETHERITE_UPGRADE_SMITHING_TEMPLATE` | 1 | 500 | Mốc người dùng yêu cầu; kiểm thử dupe. |
| Thịt sống | Các raw meat phổ biến | 64 | 10 | Mốc người dùng yêu cầu; cấu hình theo Material. |
| Thịt chín | Các cooked meat phổ biến | 64 | 15 | Mốc người dùng yêu cầu. |
| Khoáng farmable | `GOLD_INGOT` | 16 | 1 | Nguồn farm/trade có thể tạo cung lớn. |
| Khoáng phổ biến | `COPPER_INGOT` | 32 | 1 | Có thể tích lũy lượng lớn. |
| Khoáng phổ biến | `COAL` | 32 | 1 | Nguồn khai thác phổ biến. |
| Khoáng phổ biến | `REDSTONE` | 64 | 1 | Dễ tích lũy/farm. |
| Khoáng phổ biến | `LAPIS_LAZULI` | 32 | 1 | Giá trị thấp-trung bình. |
| Villager/farm | `EMERALD` | 4 | 1 | Có thể farm qua trade. |
| Khối farmable | Đá, cobblestone, đất, gỗ phổ thông | 64 | 1 | Giá thấp để hạn chế máy farm; cấu hình từng Material. |
| Nông sản farmable | Lúa mì, cà rốt, khoai tây, củ dền, mía | 64 | 2 | Cần cân theo yield trên server. |
| Mob farmable | Rotten flesh, bone, string, gunpowder | 32–64 | 1 | Giá thấp vì có thể farm AFK. |
| Mob drop | Ender pearl, leather | 16 | 1 | Khởi đầu thấp, cân lại theo nguồn cung. |

- Chỉ dùng allowlist từng Material, không wildcard bao trùm item chưa khảo sát; item không liệt kê không có quote. Cấu hình hiện có các nhóm trong bảng, nhưng vẫn cần đối chiếu với vật phẩm thực tế và nguồn farm trên server.
- **Sắt:** hiện giữ 10 thỏi = 1 VNCOIN như yêu cầu khi farm còn hoạt động. Chưa chặn iron farm: lọc spawn golem hoặc villager toàn cục có thể phá cơ chế phòng thủ làng và gameplay; chỉ cân nhắc giới hạn riêng khi có rule rõ theo world/chunk và xác minh trên staging.
- Chưa gameplay test bảng giá, tốc độ kiếm tiền và độ phù hợp của mức nông sản/mob drop; cân chỉnh sau khi nhận dữ liệu kinh tế từ server thật.
- **Chấp nhận:** custom merchant đúng phạm vi; system merchant chỉ mua allowlist vanilla và RPG item có giá >0; GUI hiện đúng lượng/tiền; warning Yes/No vẫn thanh toán system rate; giao dịch chịu được DB lỗi/restart mà không mất item hoặc nhân đôi coin.

### Phase F — Migration, kiểm chứng và tài liệu

- Kiểm thử profile cũ, onboarding dang dở, quest đang nhận, class ID cũ, NPC `npcs.yml` chưa có realm ID, buy-order cũ và YAML giá sai/thiếu. Có backup/rollback; không reset tiến trình.
- Lập ma trận test: DB chậm/lỗi, đóng GUI/reconnect, túi đầy, world/NPC thiếu hoặc chưa nạp, qua portal, click nhanh, quest successor khi đầy slot, class/book mapping sai, item metadata không khớp, bán lặp và restart giữa transaction.
- Log bước onboarding, successor, marker eligibility, quote/transaction với operation ID; không ghi secret.
- Chạy unit/integration test và staging Paper trước phát hành; chỉ đánh dấu phase hoàn tất sau khi xác minh. Cập nhật README/CHANGELOG cùng lệnh, permission, ID và cấu hình khi tính năng được làm.

### Trạng thái Phase A sau khi bắt đầu triển khai — 2026-10-26

- [~] Đã thêm cấu hình `tutorial-npc-id` và `starting-selectable` vào loader đại lục; thiếu khóa ở YAML cũ dùng giá trị suy ra tương thích. Human Realm được chọn mặc định; Yêu Realm vẫn khóa theo cấu hình đến khi admin có world/NPC phù hợp.
- [~] Đã thêm GUI chọn đại lục cho người chưa có profile; lựa chọn và bước onboarding được lưu trên PersistentDataContainer của người chơi. Hồ sơ cũ không bị đưa vào flow mới; world khởi đầu được ghi vào `home_world` khi tạo profile.
- [~] NPC tutorial chỉ hoàn tất bước onboarding khi ID và world trùng với realm đã chọn; sau đó mở GUI race/class. Scoreboard hiển thị mục tiêu gặp NPC; hướng dẫn world khác và particle navigation cùng world được cập nhật theo tick.
- [~] Quest legacy hướng tới spawn chỉ khởi chạy khi world khởi đầu của profile không xung đột với world đích của quest.
- Chưa xác minh bằng compile, test tự động, Paper integration hoặc gameplay; chưa đánh dấu Phase A hoàn tất. Cần kiểm tra GUI join, đóng/reconnect, PDC persistence, ID/world NPC sai, chunk/world chưa nạp, lựa chọn Yêu Realm và particle navigation; sau kiểm thử cần sửa các lỗi phát hiện trước khi hoàn tất phase.

### Trạng thái Phase B sau khi bắt đầu triển khai — 2026-10-26

- [~] Đã thêm GUI `/vnskillbook config` và nút trong Quản trị → RPG để ánh xạ sách chỉ thuộc đúng class cho từng cặp race/class; mapping lưu atomically trong `starter-skill-books.yml` và có validation/log.
- [~] Sau khi tạo profile qua onboarding, sách khởi đầu được đưa vào durable delivery queue bằng transaction ID cố định theo UUID. Inventory đầy thì delivery chờ; marker PDC cho phép thử lại sau lỗi/restart; profile cũ không nhận sách hồi tố.
- [~] Quest tutorial được hoãn trong lúc sách còn pending; callback chỉ hoàn tất onboarding khi đúng transaction ID của sách starter đã được xác nhận và item còn trong inventory/ender chest. Callback không bị gọi nhầm khi một reconcile khác trả danh sách cũ/rỗng; callback được giữ qua lỗi/túi đầy, dedupe theo transaction, trạng thái PDC cho phép reconnect tiếp tục.
- [~] Bổ sung `[RPG-DEBUG][Delivery]` cho đăng ký callback, số delivery chờ, transaction mục tiêu, hết chỗ, callback trì hoãn/hoàn tất; README hướng dẫn bật bằng `logging.console-debug`.
- [~] Đã thêm khai báo parent tùy chọn vào class catalog; startup báo rõ cạnh kế thừa hợp lệ và từ chối parent thiếu/sai race/chu trình. DB skill operations đối chiếu tập class con kế thừa, còn GUI cây skill và passive cũng xét skill cha. Có debug logs cho graph load và log nghiệp vụ skill sẵn có.
- [~] Đã thêm GUI preview/xác nhận tiến chức theo cấp, quest hoàn thành, tương tác NPC ghi trong PDC, Material và RPG quest-item chính xác trong túi (item không tiêu thụ); giữ skill đã học. GUI lịch sử hiển thị base/current class và các chuyển đổi. `selected_class` lưu class hiện tại; SQLite schema v13 lưu lịch sử, migration tạo bảng có backup. Debug log bao gồm candidates, điều kiện, request/result và NPC visit.
- Chưa xác minh bằng compile/gameplay hoặc chạy migration trên bản sao DB. Chưa có ID item boss/system/tiêu thụ item crash-safe, chỉ số class, skill unlock riêng, explicit base-class field/nguồn học skill, quản trị đồ thị class bằng GUI, và kiểm tra end-to-end luồng restart/chuyển chức đồng thời. Chưa mở class trong `classes.md` vì chưa có vertical slice hoàn chỉnh. Giữ phase ở trạng thái `[~]`.

### Trạng thái Phase C sau khi bắt đầu triển khai — 2026-10-26

- [~] Quest editor có mục chọn/xóa successor, danh sách có phân trang; chặn tự trỏ, vòng lặp và xóa quest đang được quest khác tham chiếu. YAML `successor-quest-id` là tùy chọn, có chú thích VI/EN; loader kiểm tra link trước khi bật catalog.
- [~] Khi hoàn thành quest, pending successor được ghi trong cùng SQLite transaction với trạng thái hoàn thành và reward. Hệ thống tự nhận successor theo điều kiện thường dùng, giữ pending nếu đủ 3 slot/thiếu điều kiện, retry sau join, hoàn thành hoặc hủy quest; chấp nhận trạng thái đã có để chống nhận trùng sau crash.
- [~] Tăng schema SQLite từ 10 lên 11; migration tạo bảng pending successor có backup theo quy trình hiện tại, bảng cũng được tạo cho database mới.
- [~] Thêm log debug/load/attempt/retained/cleared và log cảnh báo khi link đang pending trỏ tới quest bị tắt/thiếu.
- Chưa compile hoặc gameplay test. Cần test chuỗi 2+ quest, vòng lặp qua GUI/YAML, đủ 3 quest, điều kiện chưa đạt, abandon, restart ngay sau khi hoàn thành và click/reconnect trùng trước khi đánh dấu hoàn tất Phase C.

### Trạng thái Phase D sau khi bắt đầu triển khai — 2026-10-26

- [~] Thêm ProtocolLib virtual TextDisplay làm marker theo từng viewer: `!` vàng khi có quest đủ điều kiện nhận và còn slot; `?` xanh nhạt cho chapter story chưa đọc hoặc đúng NPC tutorial của đại lục đang onboarding. Hai dấu có thể cùng hiện.
- [~] Đọc trạng thái story được ghi vào PDC chỉ khi action-bar dialog chạy hết; khóa marker theo chapter nên chapter mới có thể hiện lại. Tutorial marker kiểm tra stage và NPC ID đại lục đã chọn.
- [~] Marker tự dọn khi eligibility/range/world/tracking thay đổi hoặc entity NPC được thay; cấu hình `rpg.npc.markers.enabled`, update interval 5-100 ticks, range 8-128 blocks. Chỉ đánh giá NPC đang được client track trong phạm vi.
- [~] Log state transition `[RPG-DEBUG][NPCMarker]`, đọc chapter `[RPG-DEBUG][NPC]`; packet send error có giới hạn log 30 giây/người chơi.
- Chưa compile/gameplay test. Cần xác nhận metadata TextDisplay trên ProtocolLib/server đích, kiểm tra marker riêng hai người chơi, quest requirements/slot, story complete/interrupt/restart, tutorial đúng đại lục, world/chunk/NPC unload/reload, config disable, packet cleanup và chi phí với NPC/player gần giới hạn trước khi hoàn tất Phase D.

## Build & deploy 2.18.0 — 2026-10-26 (ca trực tiếp)

- Build `mvn clean package` **kèm unit test** thành công bằng JDK 25 lúc 2026-10-26 03:30 UTC; 104/104 test đạt, không lỗi/skip. Đây là lần compile đầu tiên của các commit Phase A/B/C/D + E1/E2 (trước đó chỉ chạy `-Dmaven.test.skip=true`).
- Sửa lỗi compile phát hiện khi build: biến `characters` trùng khai báo trong `TutorialQuestManager.initializePlayer`; `Material.CHAIN` không tồn tại trên Paper API 26.3 → đổi icon successor quest sang `Material.IRON_CHAIN`.
- Đồng bộ mặc định `waypoint-update-interval-ticks` về 20 (loader + `first-steps.yml`) theo kỳ vọng test backward-compatible.
- `findDialogueDefinition`: ưu tiên definition khớp cả ID lẫn role; chỉ fallback theo role, nếu không có thì mới dùng definition lệch role (đúng hợp đồng test).
- Test `NpcDefinitionLoaderTest`: cập nhật số NPC bundled 3→7 theo `npc-definitions.yml` hiện hành.
- Test DB: thay hằng schema cũ cứng bằng `SCHEMA_VERSION`; case "DB mới hơn" tạo fixture ở `SCHEMA_VERSION + 1`.
- Tăng version `2.17.1` → `2.18.0`. JAR: `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.18.0.jar`, 2,181,440 bytes; SHA-256 `7a9e4be6e6d29985b1c8be737b0d572999237b39ac291ef948de167dc8fd6252`. Đã kiểm tra native SQLite `org/sqlite/native/Linux/x86_64/libsqlitejdbc.so` + `META-INF/services/java.sql.Driver`.
- Bản `2.17.1` đã chuyển vào `plugins/.old-builds`. Server cổng 25565 đang chạy code cũ; cần restart để nạp 2.18.0. Gameplay/migration test trên server vẫn chưa chạy.

## Build 2.19.0 — 2026-10-26

- Nâng phiên bản Maven từ `2.18.0` lên `2.19.0` cho tính năng quản trị người chơi và quest lặp lại.
- `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-arm64 mvn -DskipTests clean package` hoàn tất thành công lúc `2026-10-26 23:14 UTC`; 55 source Java biên dịch thành công. Không chạy test suite hoặc test Paper/gameplay.
- JAR: `VnMineHardcore/target/VnMineHardcore-2.19.0.jar`; dung lượng `2,213,084` bytes; SHA-256 `cebf2b531a9f31578d1373a448664163d8b6428bcc0520c904899835927974ea`.
- Đã xác nhận metadata trong JAR là `2.19.0`, command `/vnplayeradmin` có mặt, SQLite chỉ chứa `org/sqlite/native/Linux/x86_64/libsqlitejdbc.so` cùng `META-INF/services/java.sql.Driver`.
- Chưa cài JAR vào server đang chạy; cần kiểm thử Paper/gameplay theo checklist phía trên trước khi phát hành.

### Thứ tự và tối ưu phạm vi

1. Phase A trước vì đây là cổng vào hành trình.
2. Phase B tiếp theo để người chơi được chọn đúng nghề và nhận đúng sách.
3. Phase C rồi D vì marker phụ thuộc trạng thái quest ổn định.
4. Phase E1 và crash-safe trước khi mở rộng; sau đó system price/config/confirmation.
5. Phase F end-to-end trước phát hành.

Không nên triển khai cùng lúc mọi nghề trong `classes.md`: hoàn thiện một số vertical slice trước rồi mở nhánh tiếp theo. Gắn realm ID tường minh thay vì suy từ tên world để hỗ trợ một đại lục nhiều world. Chưa bật mua system cho item chưa định giá; `0` phải luôn là không mua. Chưa chặn iron farm trước khi có quyết định riêng. Danh sách nghề theo từng race, mapping sách, NPC/tutorial theo đại lục và danh mục giá vanilla đầy đủ cần được chốt từ cấu hình/server data trước khi code các phase tương ứng.

## Bổ sung NPC tutorial cho world bắt đầu/reset — 2026-10-28

- [x] Thêm nút chọn NPC tutorial của world bắt đầu/reset vào dashboard `/vninit`; danh sách chỉ hiện NPC loại `tutorial` đang đặt trong world đã cấu hình.
- [x] Cho phép bỏ lựa chọn NPC; lưu ID tại `rpg.onboarding.start-world.tutorial-npc-id`.
- [x] Shift + chuột phải NPC đã chọn mở được phần cấu hình dialog và quest tự giao riêng cho world bắt đầu; quest chỉ xuất hiện nếu giver NPC ID khớp.
- [x] Khi người chơi tương tác NPC tutorial đã chọn trong đúng world, phát dialog đã cấu hình rồi giao quest tùy chọn; có kiểm tra world và debug log.
- [x] Compile Java 25 với `mvn -DskipTests compile` thành công; `git diff --check` sạch. Không chạy test suite.
- [ ] Chưa chạy trên Paper. Cần xác nhận GUI chọn/bỏ NPC, chọn dialog/quest, tương tác NPC ở world bắt đầu và tương tác sai world; sau đó kiểm tra việc chuyển/đổi tên world có để lại NPC ID cũ hay không.

## WorldGuard/WorldEdit và quản lý công trình Schema — yêu cầu 2026-10-28

- [~] `/vninit` có mục cài đặt start-world: công tắc bảo vệ chung và đặt/phá/tương tác riêng; PvP, sinh mob, chu kỳ ngày/đêm, tiêu hao đói/khát có công tắc riêng. Áp dụng bảo vệ vào sự kiện Bukkit và hệ drain tùy chỉnh; cần compile và kiểm chứng trong server.
- [~] Thêm menu Admin → WorldEdit/WorldGuard và `/vnregion`: cấp búa gỗ nhận diện bằng PDC, chọn hai góc, nhập ID vùng, lưu YAML, GUI flags cho đặt/phá/tương tác/PvP/mob/đói/khát.
- [~] Schema Build tạo ID `<world>_<schema>_<số>`, lưu block nền/trạng thái sau đặt cùng thông tin xoay/lật/bảo vệ tại `builds.yml`; `protect: ON` đăng ký vùng cùng ID. `/vnbuild select` chọn công trình gần nhất cùng world; `!edit` giữ ID, `!copy` tạo ID mới, `!remove` phục hồi block nền khi trạng thái block còn khớp.
- [x] Compile Java 25 bằng `mvn -DskipTests compile` thành công; `git diff --check` sạch. Không chạy test suite.
- [x] Build và cài JAR 2.31.0: `mvn -DskipTests package` thành công lúc `2026-10-28 07:43:56 UTC`; JAR 2,356,451 bytes, SHA-256 `8e70bebc3b999eb53ba09b8873e2b9e3b4e4cfe1413af8d88096d4cbc1e92721`. Cài tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.31.0.jar`; backup 2.30.0 tại `.old-builds/`. Bản 2.29.0 trùng checksum với backup có sẵn nên file trong plugins được dời vào `.old-builds/VnMineHardcore-2.29.0-plugin-copy.jar`.
- [ ] Gameplay test các cờ start-world, selection wand, flags/overlap, persistence qua restart, edit/copy/remove, block bị người chơi thay đổi và world chưa load.
- Ghi chú giới hạn: quản lý vùng là cuboid nhẹ do plugin tự lưu, không thay thế toàn bộ WorldEdit/WorldGuard. Mô hình lưu block snapshot không bao gồm dữ liệu inventory/sign/entity; thao tác xóa được từ chối khi block đã thay đổi hoặc có tile entity. Region protection hiện có thể bypass bởi OP/permission quản trị.

## Mở rộng lệnh WorldEdit cho búa gỗ — yêu cầu 2026-10-28

- [x] Đọc docs WorldEdit 7.4 chính thức và dùng các nhóm command/selection/clipboard/pattern/history làm tham khảo; không nhúng thư viện WorldEdit.
- [x] Chặn lệnh `//...` vào bộ xử lý plugin; chọn cuboid bằng búa, `//pos1`, `//pos2`, `//hpos1`, `//hpos2`, `//chunk`, điều chỉnh `//expand`, `//contract`, `//shift`; có `//size`, `//desel`.
- [x] Thêm `//set`, `//replace`, `//walls`, `//faces`, `//overlay`, `//center`, `//count`, `//move`, `//stack`, `//copy`, `//cut`, `//paste [-a] [-o]`, `//undo`, `//redo`, `//clearhistory`, `//limit`; nhận material ID/block state và pattern block phân bố có trọng số.
- [x] Thêm giới hạn khối/chunk, xác nhận chunks nạp/world border/chiều cao, từ chối tile entity để bảo toàn inventory/sign, giữ chunk trong khi áp dụng batch từng tick, lưu undo/redo và clipboard trong phiên; bổ sung help, permission, config, README và CHANGELOG. `config.yml` và `plugin.yml` parse YAML hợp lệ.
- [x] Compile Java 25 bằng `mvn -DskipTests compile` thành công lúc `2026-10-28 08:16 UTC`; không chạy test suite.
- [x] Build JAR 2.32.0 bằng Java 25 với `mvn -DskipTests clean package`; build thành công lúc `2026-10-28 08:21:03 UTC`, 2,390,476 bytes, SHA-256 `2cb94db0dd8b34cb4ecf37ca215bfd3d55565ad90e1440a3cfb5ecb3c2fcf9cd`. `plugin.yml` xác nhận phiên bản 2.32.0; JAR giữ SQLite JDBC/service metadata và chỉ đóng gói native Linux x86_64. Test suite được bỏ qua theo yêu cầu build.
- [x] Sao lưu JAR 2.31.0 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.31.0.jar`, cài 2.32.0 tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.32.0.jar`, đối chiếu byte/hash với artifact; gỡ JAR 2.31.0 khỏi thư mục plugin để Paper không nạp trùng phiên bản.
- [x] Commit `e7db658` và push thay đổi mã nguồn/tài liệu lên `origin/main`.
- [ ] Kiểm thử trên server các lệnh set/replace/copy/paste/move/stack/undo/redo, pattern, giới hạn lệnh và tile entity; server cần restart để nạp 2.32.0.
- Giới hạn hiện tại: cuboid-only; không có entity/biome edit, brush, generator, selection shapes phi cuboid, schematic I/O hoặc history xuyên restart. Clipboard/history mất khi thoát.

## Onboarding world bắt đầu và quản lý rule theo world — yêu cầu 2026-10-28

- [x] Dựa trên `/home/ubuntu/DATA/VNMINE/logs/latest.log`: sự kiện click NPC `world-start-tutorial` đã tới `QuestCatalogManager`; bảo vệ đặt/phá block không chặn entity click. Config thật đã chọn đúng NPC nhưng `dialog-id` và `auto-quest-id` đều rỗng, nên handler cũ xử lý im lặng và không mở bước chọn tiếp theo.
- [x] Người mới chưa có profile được chuyển tới `rpg.onboarding.start-world.name`; sidebar và navigation dẫn tới NPC Tutorial world bắt đầu. Click NPC ghi nhận First Steps, phát dialog nếu cấu hình, rồi mở menu chọn đại lục.
- [x] Chọn đại lục không dịch chuyển ra khỏi world bắt đầu; GUI lọc race/class theo realm. Sau khi tạo profile, plugin giao sách skill, tự nhận quest cấu hình theo race và đưa người chơi đến spawn của đại lục. NPC tutorial riêng của realm không còn bắt buộc để mở onboarding.
- [x] Thêm Admin → Quản lý chủng tộc: nhập quest ID đang bật hoặc `!clear`; lưu tại `races.<race-id>.onboarding.auto-quest-id` trong `races.yml`.
- [x] Thêm Admin → Quản lý world cho các rule đặt/phá block, tương tác, PvP, mob, boss, thiên tai, thời gian, đói và khát; lưu thay đổi trong `world-settings.yml` và nối vào protection, time, drain, boss spawn và disaster targeting.
- [x] Compile Java 25 bằng `mvn -DskipTests compile` thành công lúc `2026-10-28 09:29 UTC`; kiểm tra YAML hợp lệ và `git diff --check` sạch. Không chạy test suite.
- [x] Build JAR 2.33.0 bằng Java 25 với `mvn -DskipTests package`; build thành công lúc `2026-10-28 09:51:57 UTC`, 2,404,569 bytes, SHA-256 `3aece9ef3af927a576f42eb37353da52ab648b5d564b18765bfdd107b5f30bb6`. Test suite được bỏ qua.
- [x] Sao lưu JAR 2.32.0 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.32.0.jar`, cài 2.33.0 tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.33.0.jar`, đối chiếu checksum với artifact; gỡ 2.32.0 khỏi thư mục plugin để Paper không nạp hai phiên bản.
- [x] Commit source và plan.md (`Cải thiện onboarding và quản lý cấu hình world`); đã push lên `origin/main`.
- [ ] Gameplay test sau phát hành cần kiểm tra NPC Tutorial bằng profile mới và profile đã có, chọn realm/race/class, giao quest race, từng cờ world (đặc biệt boss/thiên tai/thời gian) và lưu qua restart.

## Sửa đổi environment cho managed world trên Paper 26.3 — yêu cầu 2026-10-28

- [x] Rà soát `latest.log` và dữ liệu server: Paper 26.3 lưu world phụ dưới `<world chính>/dimensions/minecraft/<world-name>`, không phải `<server>/<world-name>`. Luồng cũ chỉ archive đường dẫn legacy nên dữ liệu dimension cũ vẫn còn trong khi manifest đã ghi environment mới.
- [x] Dùng `World#getWorldFolder()` khi world đang nạp; khi chưa nạp, tra manifest hoặc tìm đường dẫn dimension tồn tại. Từ chối thao tác nếu đường dẫn không an toàn hoặc có nhiều ứng viên không thể phân biệt.
- [x] Lưu folder tương đối vào `managed-worlds.yml`; archive world cũ cạnh thư mục dimension thật, rồi tạo và xác nhận world mới theo environment/seed yêu cầu.
- [x] `/vninit` tăng revision mỗi lần admin đổi environment để đổi qua rồi quay lại trước restart vẫn đánh dấu cần tái tạo.
- [x] Đánh dấu tái tạo một lần cho `world_start` hiện tại trong managed-world manifest; lần khởi động bằng JAR sửa lỗi sẽ backup dimension folder cũ và tạo NORMAL.
- [x] Compile Java 25 (`mvn -DskipTests compile`) thành công lúc `2026-10-28 10:14:39 UTC`; không chạy test suite.
- [x] Build JAR 2.34.1 với `mvn -DskipTests package` thành công lúc `2026-10-28 10:14:45 UTC`, 2,409,671 bytes, SHA-256 `fa62792c98afac1777cfb3f5eeb5bcf4552afdb0caeda1453b5422a740524796`.
- [x] Sao lưu 2.34.0 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.34.0.jar`, cài 2.34.1 tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.34.1.jar`; checksum khớp và chỉ còn một plugin JAR.
- [x] Commit/push thay đổi lên `origin/main`.
- [ ] Kiểm chứng trên Paper: THE_END → NORMAL sau restart; xác nhận dimension backup tồn tại cạnh folder cũ và world mới có environment NORMAL.


## Quản lý đại lục, permission, tiện ích và tài liệu — yêu cầu 2026-10-28

- [~] `/vninit` có menu quản lý đại lục riêng: thêm theo ID/tên hiển thị/tên world, sửa ID/tên/world và xóa sau xác nhận. Đổi ID lưu bí danh cũ trong `realm-id-aliases.yml`; onboarding/profile có ID cũ sẽ được canonical hóa khi tra cứu.
- [~] Đổi environment đại lục có GUI xác nhận, tăng revision để đánh dấu tái tạo sau restart. Thao tác từ chối nếu world là start-world hoặc đang được đại lục khác dùng; dữ liệu world cũ được chuyển thành backup có timestamp.
- [~] Xóa đại lục archive file cấu hình; chỉ đặt lịch archive folder world nếu world không dùng chung. Nếu ghi manifest hoặc reload catalog thất bại, cố gắng khôi phục file cấu hình và hủy marker archive.
- [~] Scheduled boss/thiên tai không roll khi người chơi chỉ ở các world tắt sự kiện; lượt bị hủy và timer được reset để không roll dồn khi người chơi đổi world.
- [~] OP lưu gamemode trong PersistentDataContainer lúc thoát, phục hồi sau join. Đã thêm `/feed` và `/heal` với node permission riêng.
- [~] PermissionManager tự bổ sung các permission command còn thiếu vào nhóm admin, các quyền cơ bản vào nhóm mặc định `user`, đăng ký node theo cấu hình khi khởi tạo; nhóm user không tự nhận feed/heal.
- [x] Viết lại README thành tài liệu hướng dẫn có mục lục, chức năng, onboarding, cấu hình, lệnh, permissions, vận hành và thông tin nhóm phát triển một người.
- Cần kiểm chứng đổi nhiều lần ID liên tiếp, alias chain sau restart, người chơi offline đang ở bước onboarding và rollback khi YAML/manifest lỗi.
- [x] Compile Java 25 (`mvn -DskipTests compile`) thành công lúc `2026-10-28 11:09 UTC`; YAML `plugin.yml` và `permission.yml` hợp lệ, mọi command khai báo permission, `git diff --check` sạch. Không chạy test suite.
- [x] Build JAR 2.35.0 bằng Java 25 (`mvn -DskipTests package`) thành công lúc `2026-10-28 11:10:42 UTC`; 2,420,868 bytes, SHA-256 `2ab208ba82666bb1f765ebff58eeaa797ea29bb27dfbbc4079f16b1194162851`. Đã xác minh JAR chỉ chứa native SQLite Linux x86_64 và plugin.yml báo version 2.35.0.
- [x] Sao lưu JAR 2.34.1 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.34.1.jar`, cài 2.35.0 tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.35.0.jar`; so sánh byte khớp và gỡ bản 2.34.1 khỏi thư mục plugin để Paper không nạp trùng.
- [ ] Chưa chạy server Paper/gameplay test. Source/tài liệu đã commit và push lên `origin/main` (commit `6573b20`).

## Lỗi khởi tạo sau khi cài 2.35.0 — 2026-10-28

- Log `/home/ubuntu/DATA/VNMINE/logs/latest.log` lúc `11:21:16` báo `IllegalStateException: Command '/quatanthu' is missing from plugin.yml`; plugin bị disable, nên các lệnh sau như `/gm1` cũng lỗi.
- Nguyên nhân: `plugin.yml` có hai khóa `permissions:` cấp cao nhất. Do đó `/vninit`, `/vnregion`, `/vnbuild`, `/start`, `/quatanthu` bị đọc như permission declarations thay vì commands.
- Đã gộp permission definitions vào một section và giữ toàn bộ command declarations trong `commands`; parser xác nhận 43 lệnh, 51 quyền và không còn lệnh thiếu permission.
- Cùng lần khởi động, `world_human` được archive và tạo lại do cấu hình revision `3` khác manifest revision `0` dù environment đều `NORMAL`. World cũ được giữ tại `/home/ubuntu/DATA/VNMINE/world/dimensions/minecraft/world_human.backup-20261028-112109`; không tự phục hồi để tránh ghi đè world mới.
- [x] Sửa `plugin.yml`, bump patch lên 2.35.1, xác minh 43 command và 51 permission node; mọi command đều khai báo permission.
- [x] Build artifact 2.35.1 bằng Java 25 lúc `2026-10-28 11:24:19 UTC`, 2,420,760 bytes, SHA-256 `f49d28248ebfaad472e893cd54ee798c72ea7fde7e33f7e34370b3e27da14124`; chỉ chứa SQLite native Linux x86_64.
- [x] Bản 2.35.2 giữ native SQLite Linux `aarch64` và `x86_64` trong cùng JAR; bỏ duy nhất bộ lọc loại trừ native AArch64, các native OS/kiến trúc khác vẫn được loại.
- [x] Build `mvn -DskipTests package` bằng Java 25 thành công lúc `2026-10-28 11:32:45 UTC`; artifact 2.35.2 có dung lượng 2,995,554 bytes, SHA-256 `8f2934e9f139997d4ae1b39436f829b714c4aa05fbd0a4c26444e252aacf03bb`. Đã kiểm tra JAR có cả `org/sqlite/native/Linux/aarch64/libsqlitejdbc.so` và `org/sqlite/native/Linux/x86_64/libsqlitejdbc.so`; test suite được bỏ qua.
- [x] Sao lưu JAR 2.35.0 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.35.0.jar`, cài 2.35.2 tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.35.2.jar`, đối chiếu SHA-256 với artifact và gỡ 2.35.0 khỏi thư mục plugin để Paper không nạp trùng.
- [ ] Cần khởi động lại Paper để nạp 2.35.2, sau đó kiểm tra plugin enable và thao tác SQLite (profile/VNCOIN) qua restart trên AArch64. Lỗi `plugin.yml` trong log trước thuộc source 2.35.0; bản sửa 2.35.1 đã được push, còn 2.35.2 bổ sung native ARM64.

## Schema Build: giới hạn kích thước file tải về — 2026-10-28

- Log Paper 2.35.2 cho thấy parser đọc được Sponge v2/v3 nhưng từ chối hai file vì giới hạn `schema-build.max-blocks: 5000`: `2 trees portal.schem` có kích thước `69×60×59` (244.260 ô), `moon_house.schem` có `90×119×33` (353.430 ô).
- [x] Tăng mặc định hỗ trợ lên 400.000 ô và trần cấu hình lên 500.000; cập nhật thông báo lỗi để nêu dimensions, volume và configured limit.
- [x] Schema lỗi chỉ ghi warning một lần cho mỗi fingerprint file (size/mtime), không lặp stack trace mỗi lần mở menu; admin nhận lý do cụ thể.
- [x] Build `mvn -DskipTests package` bằng Java 25 thành công lúc `2026-10-28 11:54:46 UTC`; JAR 2.35.3 có 2.997.995 bytes, SHA-256 `1a7cce9346ddc2923b91e48c45ac65df5ddecf0a2b9e5c1a0c835e6b1652ff80`. Đã xác minh JAR giữ native SQLite Linux `aarch64` và `x86_64`; test suite được bỏ qua.
- [x] Sao lưu JAR 2.35.2 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.35.2.jar`, cài 2.35.3 tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.35.3.jar`, checksum khớp artifact và chỉ còn một plugin JAR trong thư mục.
- [ ] Chưa Paper test hai file. Cần restart server, xác nhận chúng xuất hiện trong Schema Build; dựng thử trên vùng test để đo hiệu năng vì `place()` hiện xử lý volume lớn trên main thread. Preview vẫn chỉ hiển thị tối đa `preview-blocks` block.

## NPC facing và mã format `&` — yêu cầu 2026-10-28

- [x] Managed villager NPC cập nhật yaw/pitch mỗi 10 tick để nhìn người chơi gần nhất cùng world trong tầm `rpg.npc.look-at.range-blocks`; thêm công tắc `rpg.npc.look-at.enabled` và dừng task khi plugin tắt.
- [x] Đổi tên NPC giữ hỗ trợ mã legacy đã có; khi ghi bảng hiệu, chuyển `&0-9a-f`, `&k-o`, `&r` sang định dạng Minecraft. `/vntext` chuyển mã trong nội dung trước khi hiển thị.
- [x] README, CHANGELOG và config mặc định được cập nhật; version source tăng lên 2.35.4.
- [x] Các thay đổi này được đóng gói trong JAR 2.36.0 bên dưới; chưa gameplay test. Cần kiểm tra NPC xoay đúng hướng khi có nhiều người chơi, công tắc disable, và màu/format trên bảng hiệu cùng text display.

## Mô hình player và skin tùy chỉnh cho NPC — yêu cầu 2026-10-28

- [x] Tách kiểu hiển thị khỏi loại chức năng NPC: player model giả lập không thay đổi role `merchant`, `quest-giver`, `story` hay `tutorial`.
- [x] Thêm nút editor nhập username tài khoản Minecraft Java, `!default` để bỏ skin; lưu vào `plugins/VnMineHardcore/npcs.yml` dưới khóa `skin-owner`.
- [x] Tải signed texture profile bất đồng bộ từ dịch vụ hồ sơ Mojang, cache theo username trong phiên và ghi log khi thành công/thất bại; lỗi lookup dùng skin mặc định.
- [x] Hiển thị tên NPC bằng metadata player và chuyển click vào fake player về cùng xử lý click của NPC hiện hữu. Mô hình chỉ gửi cho người chơi cùng world/trong khoảng cách cấu hình và được dọn khi rời tầm/thoát server.
- [x] Thêm config `rpg.npc.player-model.enabled` (mặc định true) và `view-distance-blocks` (mặc định 48, giới hạn 8-128); khi tắt, hiện lại villager gốc.
- [x] Cập nhật README/CHANGELOG; tăng phiên bản source từ 2.35.4 lên 2.36.0.
- [x] Compile bằng Java 25 (`mvn -DskipTests compile`) thành công lúc `2026-10-28 13:04 UTC`; có các warning deprecated sẵn trong API Paper/ProtocolLib, test suite không chạy.
- [x] Build JAR 2.36.0 bằng Java 25 (`mvn -DskipTests clean package`) thành công lúc `2026-10-28 13:11:45 UTC`; 3.021.564 bytes, SHA-256 `8b6493e5632ece09f5b9db167ba08e8cc6fe4cbef4e65ddea39f7cdffb9b4355`. Đã xác minh JAR có SQLite native Linux x86_64 và AArch64; test suite được bỏ qua.
- [x] Sao lưu 2.35.3 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.35.3.jar`, cài 2.36.0 tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.36.0.jar`; checksum khớp artifact và chỉ còn một JAR VnMineHardcore trong thư mục plugin.
- [ ] Chưa gameplay test ProtocolLib/Paper; cần kiểm tra skin, tên màu, click NPC, nhiều người xem, dọn entity sau đổi world/thoát, và bật/tắt player-model.

## Sửa NPC không hiển thị trên Paper 26.3 — log 2026-10-28

- Log Paper `26.3-175` + ProtocolLib `5.5.0-SNAPSHOT-d89a5de` báo `Could not find packet for type NAMED_ENTITY_SPAWN` ở mọi chu kỳ spawn; cấu hình ẩn villager gốc khiến không còn NPC hiển thị. Ghi nhận 396 task exception trong log.
- Tài khoản `notch` đã lưu trong `npcs.yml`; request tới Mojang timeout lúc `13:32:50`, không có log profile tải thành công.
- [x] Thay packet cũ bằng `SPAWN_ENTITY` + `EntityType.PLAYER`, sửa thứ tự góc spawn theo giao thức hiện đại và gửi vector vận tốc rỗng cần cho protocol mới.
- [x] Nếu player packet lỗi, vô hiệu player-model trong phiên, khôi phục Villager hiển thị và chỉ ghi lỗi một lần thay vì spam stack trace mỗi 10 tick.
- [x] Tách HttpClient transport executor khỏi pool tải skin; tăng request timeout lên 15 giây và thử lại tự động sau 30 giây khi lookup lỗi.
- [x] Tăng phiên bản source lên 2.36.1.
- [x] Build JAR 2.36.1 bằng Java 25 (`mvn -DskipTests package`) thành công lúc `2026-10-28 13:39:26 UTC`; 3.017.042 bytes, SHA-256 `84344afd9cc66a772c5c6a36fb52ed91e7253a1b5a7d536f05bcb4ef6a7fca3c`. Xác minh JAR có SQLite native Linux x86_64 và AArch64; test suite bỏ qua.
- [x] Sao lưu 2.36.0 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.36.0.jar`, cài 2.36.1 tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.36.1.jar`; checksum khớp và chỉ còn một JAR VnMineHardcore trong thư mục plugin.
- [x] Commit `28389a6` đã push lên `origin/main`.
- [ ] Chưa gameplay test. Khởi động lại server để nạp bản sửa, sau đó xác nhận NPC/skin `Notch` cùng log không còn lặp packet exception.

## Sửa client ngắt kết nối khi nhận packet NPC — log 2026-10-28

- Server log 2.36.1 ghi nhận người chơi xác thực và vào `world_start`, rồi `lost connection: Disconnected` ngay sau đó; không có stack trace server-side. Skin `notch` tải thành công sau lúc client ngắt.
- [x] Cập nhật `PLAYER_INFO` với đủ action cho profile player hiện đại; tên hiển thị tiếp tục đặt qua metadata thực thể.
- [x] Gửi packet thực thể NPC ảo không kích hoạt packet listener cho entity thật; chờ 40 tick sau `PlayerJoinEvent` trước khi gửi packet NPC.
- [x] Tăng phiên bản source lên 2.36.2.
- [x] Build JAR 2.36.2 bằng Java 25 (`mvn -DskipTests package`) thành công lúc `2026-10-28 13:50:30 UTC`; 3.017.729 bytes, SHA-256 `55ee211f91279c2e7ee54d5596a47c7a6edcd6c2e5364c236d67676177a771e2`. Đã xác minh SQLite native Linux x86_64 và AArch64; test suite bỏ qua.
- [x] Sao lưu 2.36.1 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.36.1.jar`, cài 2.36.2 tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.36.2.jar`; checksum khớp artifact và chỉ còn một JAR VnMineHardcore.
- [ ] Chưa xác minh client protocol trên Paper; restart server rồi thử đăng nhập. Nếu vẫn ngắt, cần lấy log client Minecraft để xác định decoder exception cụ thể.

## Phát hành 2.38.0 — bộ lọc quest onboarding và chuẩn hóa workflow — 2026-10-30

- [x] Bộ chọn quest tự giao trong `/vninit` lọc được theo đại lục và chủng tộc; hỗ trợ bỏ bộ lọc và giữ scope khi chuyển trang.
- [x] Quest có scope world/race cụ thể được lọc theo scope khai báo ở điều kiện nhận hoặc objective `world-select`/`race-select`; quest không giới hạn vẫn xuất hiện.
- [x] Ghi roadmap chuẩn hóa cách tổ chức quest/dialog và lựa chọn dữ liệu đã tồn tại bằng GUI; ưu tiên phân loại quest theo world, race, NPC và trạng thái dialog, cho phép tạo ngay tại selector, đồng thời tránh tự giao quest trùng lặp.
- [x] Maven `clean package` bằng Java 25 hoàn tất lúc `2026-10-30 01:06:26 UTC`; 104 test pass, không có failure/error. Cập nhật hai kỳ vọng test tutorial cũ sang objective `visit-start-world-tutorial-npc` hiện hành.
- [x] JAR `VnMineHardcore-2.38.0.jar`, SHA-256 `7a588fbd6963e226b8c78c92ffc1d1a669e7e2a93e317c65a6f25ab85750f37d`; xác minh chỉ đóng gói SQLite native Linux x86_64.
- [x] Sao lưu JAR 2.36.16 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.36.16-pre-2.38.0-20261030.jar`; cài 2.38.0 vào `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.38.0.jar`. SHA-256 bản cài trùng artifact và thư mục plugin chỉ còn một JAR VnMineHardcore.
- [ ] Chưa Paper/gameplay test bản này; sau restart cần kiểm tra bộ lọc quest tutorial theo world/race, menu phân trang và toàn bộ onboarding/quest flow trên server thật.

## Phát hành 2.38.1 — tra cứu alias và quản lý quest/dialog theo NPC — 2026-10-30

- [x] `/realname <tên người chơi|alias>` tra cứu người chơi theo tên thật hoặc alias; chuẩn hóa mã màu/format trước khi so sánh để alias `&4hoanbh` vẫn tìm được bằng `hoanbh`.
- [x] Trong `/vninit`, bộ chọn quest tự giao có nút tạo quest mới; sau khi lưu, quay lại đúng luồng cấu hình ban đầu.
- [x] NPC quest-giver/tutorial có menu chọn quest hiện có hoặc tạo quest mới với NPC được điền sẵn.
- [x] Menu NPC có quản lý hội thoại riêng: chọn, tạo, sửa, xóa dialog gắn với NPC hoặc dialog public. Dialog mặc định cũ không còn tự xuất hiện như một lựa chọn không rõ nguồn.
- [x] Quest-dialog lưu phiên bản schema mới để hỗ trợ quan hệ NPC và phạm vi public/private; README và CHANGELOG đã cập nhật.
- [x] `timeout 15m mvn clean package` thành công bằng Java 25 lúc `2026-10-30 01:32 UTC`; 104 test pass, 0 failure/error/skip.
- [x] JAR `VnMineHardcore-2.38.1.jar`, kích thước 2.5 MB, SHA-256 `369285cdf6051f7d8522b71fd28f01448c621880910c84739f336dc228d0deaf`; xác minh chỉ đóng gói SQLite native Linux x86_64.
- [x] Sao lưu JAR 2.38.0 trước khi thay bản, cài 2.38.1 vào `/home/ubuntu/DATA/VNMINE/plugins/`; checksum bản cài phải khớp artifact. Chỉ giữ một JAR VnMineHardcore trong thư mục plugin.
- [ ] Chưa gameplay test. Sau khi khởi động lại server, kiểm tra `/realname` với alias có màu, tạo quest từ `/vninit` và NPC, selector dialog private/public, và điều hướng quay lại menu nguồn.

## Phát hành 2.39.0 — NPC, dialog, WorldEdit và quản lý world — 2026-10-30

- [x] Tăng scale mặc định marker `!`/`?` trên đầu NPC lên 2.0 và thêm cấu hình scale 1.0–4.0.
- [x] Tăng độ dài dòng action bar dialog mặc định từ 36 lên 48 ký tự; bấm lại NPC khi dialog đang chạy sẽ skip và tiếp tục callback hiện tại.
- [x] Sắp menu NPC thành hai hàng quest khả dụng/đang làm, hàng kính ngăn cách, hai hàng chức năng và hàng điều hướng cuối. Quest đã hoàn thành mở riêng trong nhật ký; đường quay lại nhật ký trở về menu NPC trước đó.
- [x] Nút **Di chuyển NPC** vào chế độ chờ `!put`/`!cancel`; cập nhật tooltip chat ngẫu nhiên để bỏ hướng dẫn admin và ưu tiên nút F/menu.
- [x] WorldEdit vẽ viền lựa chọn particle đỏ; thêm `//square <bán_kính> [block]`, `//circle <bán_kính> [block]`, tab completion cho lệnh `//`, và `//limit unlimited` để bỏ giới hạn block/chunk. Mặc định vẫn 32.768 block, 256 chunk.
- [x] Menu quản lý world có công tắc riêng sinh mob thân thiện; cần bật cả công tắc sinh quái chung để các mob này xuất hiện.
- [x] `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-arm64 timeout 15m mvn clean package` thành công lúc `2026-10-30 02:32:04 UTC`; 104 test pass, 0 failure/error/skip. Build có cảnh báo deprecated API hiện hữu.
- [x] JAR `VnMineHardcore-2.39.0.jar`, 2.5 MB, SHA-256 `b3cfcbad3b983e343dc066b931a333cc2f9bf6d0d9551703e8091bf3a1d7b8dd`; xác minh chỉ đóng gói SQLite native Linux x86_64.
- [x] Sao lưu JAR đang cài 2.38.1 trước khi thay, cài 2.39.0 vào `/home/ubuntu/DATA/VNMINE/plugins/`; checksum bản cài phải khớp artifact và thư mục plugin chỉ giữ một JAR VnMineHardcore.
- [ ] Chưa gameplay test. Sau restart, kiểm tra kích thước marker và tầm nhìn, skip dialog/callback, di chuyển NPC bằng chat, tab completion, `//square`/`//circle`, thao tác `//limit unlimited` trên vùng thử, và toggle mob thân thiện theo world.

## Sửa lỗi khởi động 2.39.1 — serializer NPC marker — 2026-10-30

- Log server 2.39.0 lúc `02:33:10` cho thấy plugin bị disable trong `onEnable`: `WrappedDataWatcher.Registry.get(Vector3F.class)` không tìm thấy serializer. Lệnh `/vnarmy` sau đó lỗi vì plugin đã bị disable.
- [x] Dùng API `WrappedDataWatcher.Registry.getVectorSerializer()` để lấy serializer vector Minecraft đúng kiểu; tăng phiên bản patch lên 2.39.1.
- [x] `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-arm64 mvn -DskipTests clean package` build thành công lúc `2026-10-30 02:40:43 UTC`; test suite được bỏ qua theo yêu cầu build, compile hoàn tất.
- [x] JAR `VnMineHardcore-2.39.1.jar`, 2.560.789 bytes, SHA-256 `2e1131a39d952d6b5a5ee655e5d5614308687e9ef479e27d8a379710e8e5c010`; xác minh chỉ có SQLite native Linux x86_64.
- [x] Sao lưu bản 2.39.0 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.39.0-pre-2.39.1-20261030.jar`, cài 2.39.1 tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.39.1.jar`; checksum cài đặt khớp artifact và thư mục chỉ giữ một JAR VnMineHardcore hoạt động.
- [ ] Cần khởi động lại Paper để xác nhận plugin enable thành công và marker NPC hiển thị; kiểm tra log không còn lỗi `No serializer found for Vector3F`.

## Sửa lỗi client disconnect khi hiển thị marker NPC — 2.39.2 — 2026-10-30

- Log Paper 26.3 lúc `02:43:26` xác nhận tương tác NPC và hoàn thành quest thành công; ngay sau đó packet marker `!` lỗi `Vector3F cannot be cast to net.minecraft.core.Rotations`, khiến client bị ngắt kết nối. Lỗi lặp lại khi người chơi vào lại server.
- [x] Thay packet metadata tự dựng bằng entity `TextDisplay` do Paper quản lý; đặt entity ẩn mặc định và chỉ `showEntity` cho người chơi đủ điều kiện nhận marker. Paper chịu trách nhiệm mã hóa metadata theo phiên bản server.
- [x] Giữ marker theo từng người xem, cập nhật text trực tiếp, và xóa entity khi marker hết điều kiện, NPC đổi vị trí/biến mất, người chơi thoát hoặc plugin tắt.
- [x] Tăng phiên bản patch lên 2.39.2. `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-arm64 mvn -DskipTests clean package` thành công lúc `2026-10-30 02:46:07 UTC`; test suite được bỏ qua, compile/package thành công.
- [x] JAR `VnMineHardcore-2.39.2.jar`, 2.560.044 bytes, SHA-256 `d85a56094722d5ad785f0bbdaefa368e9794e45f45396bb7436a5eaabd0ad074`; xác minh chỉ có SQLite native Linux x86_64.
- [x] Sao lưu 2.39.1 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.39.1-pre-2.39.2-20261030.jar`, cài 2.39.2 tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.39.2.jar`; checksum khớp và chỉ giữ một JAR plugin hoạt động.
- [ ] Khởi động lại Paper và kiểm tra marker `!`/`?`, tương tác NPC quest-giver, tầm nhìn riêng theo người chơi, và log không còn lỗi `set_entity_data`/ngắt kết nối.

## Editor quest khởi đầu và chọn quest tại NPC tutorial — 2.40.0 — 2026-10-30

- [x] Đưa nút sửa **Bước chân đầu tiên** lên dashboard `/vninit` và editor NPC `tutorial`; từ editor NPC, nút quay lại trả admin về đúng menu cấu hình NPC.
- [x] Mở rộng editor quest hướng dẫn: bật/tắt, sửa tiêu đề/mô tả/objective và thông báo, đổi loại thưởng EXP/VNCOIN, tăng/giảm lượng thưởng; lưu YAML nguyên tử, reload definition và ghi log thao tác admin.
- [x] Đổi nhãn cấu hình `tutorial.auto-quest-id` thành **Quest khởi đầu tại NPC**, mô tả rõ đây là quest được giao sau dialog NPC. Bộ chọn vẫn lọc theo giver NPC; có thể chọn từ editor tutorial NPC hoặc `/vninit` cho world start/đại lục.
- [x] Đóng gói cùng release 2.41.0 bằng Java 25 (`mvn -DskipTests clean package`); compile/package thành công, test suite bỏ qua.
- [ ] Cần kiểm tra trong game lưu/reload các trường quest, giới hạn phần thưởng, nút quay lại từ từng NPC tutorial, chọn/bỏ quest gắn với NPC, và xác nhận quest được giao đúng sau khi hội thoại kết thúc.

## Hologram marker NPC lớn và dễ thấy — 2.41.0 — 2026-10-30

- [x] Tăng scale hiển thị marker từ cấu hình nền 2.0 lên 6.0 (nhân 3); giới hạn cấu hình cũ 1.0–4.0 vẫn hợp lệ và tương ứng scale hiển thị 3.0–12.0, nên config đã tồn tại tiếp tục dùng được.
- [x] Tạo dấu `!` nhiều dòng, đậm, ghép từ glyph trang trí/thân/chấm; dấu `?` story/tutorial có kiểu hologram đồng bộ. TextDisplay trong suốt, billboard theo viewer và see-through để dễ nhận ra từ xa.
- [x] `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-arm64 mvn -DskipTests clean package` thành công lúc `2026-10-30 13:43:36 UTC`; test suite bỏ qua, compile/package thành công.
- [x] JAR `VnMineHardcore-2.41.0.jar`, 2.562.665 bytes, SHA-256 `6334f07f15b837a9074ef6658c3d3462d403f9eb48bf6640024ea1fc647a7e73`; xác minh chỉ chứa SQLite native Linux x86_64.
- [x] Sao lưu 2.39.2 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.39.2-pre-2.41.0-20261030.jar`; cài 2.41.0 vào `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.41.0.jar`, checksum khớp artifact và chỉ còn một JAR plugin hoạt động.
- [ ] Chưa gameplay test. Cần kiểm tra glyph với resource pack/client khác nhau, khoảng cách đọc, vị trí trên NPC cao, visibility xuyên tường và hiệu năng khi nhiều marker cùng lúc; đồng thời kiểm tra editor quest khởi đầu và quest được giao từ NPC tutorial.

## Tinh chỉnh marker theo phản hồi — 2.41.1 — 2026-10-30

- [x] Giảm scale mặc định từ 6.0 xuống 3.0 (50%); giữ hệ số cấu hình nền hiện tại tương thích và đổi phép nhân từ 3.0 sang 1.5.
- [x] Bỏ glyph ngôi sao. Dấu `!` chỉ ghép từ thân `┃` và chấm `●` in đậm trên hai dòng; dấu `?` bỏ glyph trang trí.
- [x] `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-arm64 mvn -DskipTests clean package` thành công lúc `2026-10-30 14:31:22 UTC`; test suite được bỏ qua.
- [x] JAR `VnMineHardcore-2.41.1.jar`, 2.562.624 bytes, SHA-256 `0ba37f187ec378f59e2ad5abf10b01fdf6d9ad1b1cc2695632bc5c767c757c70`; xác minh chỉ chứa SQLite native Linux x86_64.
- [x] Sao lưu 2.41.0 tại `/home/ubuntu/DATA/VNMINE/plugins/.old-builds/VnMineHardcore-2.41.0-pre-2.41.1-20261030.jar`, cài 2.41.1 tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.41.1.jar`; checksum khớp artifact và chỉ còn một JAR plugin hoạt động.
- [ ] Chưa test trên client. Cần xem độ dễ đọc và khoảng cách sau restart server.
