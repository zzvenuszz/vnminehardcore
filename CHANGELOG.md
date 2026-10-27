# Changelog

## [v2.22.0] - 2026-10-27
- Đổi luồng quản trị hồ sơ thành **Menu chính → Admin → Người chơi → chọn online/offline → chức năng hồ sơ**; giữ các tiện ích teleport/gamemode trong mục riêng khi admin có quyền tương ứng.

## [v2.21.0] - 2026-10-27
- Làm rõ luồng quản trị người chơi: mở menu tìm offline tại cuối danh sách, có điều hướng về menu quản trị, thêm submenu quản lý quest để xem quest hoàn thành, mở lại quest hoặc reset toàn bộ; nút giới hạn lặp lại chỉ hoạt động khi bật repeatable.

## [v2.20.0] - 2026-10-26
- Mở rộng picker `/vnplayeradmin` thành danh mục phân trang gồm nguyên liệu quest, sách skill đang được triển khai, item drop/giáp boss cấu hình, item shop NPC, gear và token lính đánh thuê; cấp item offline hoặc khi túi đầy bằng hàng đợi giao nhận bền vững, giữ nguyên PDC.

## [v2.17.1] - 2026-10-25
- Chuẩn hóa metadata không mang dữ liệu RPG của các item block rơi ra để chúng stack với item vanilla; giữ nguyên item có PDC, tên, lore, enchant hoặc metadata đặc biệt.
- Thêm nút bật/tắt theo dõi riêng cho từng quest ngay trong nhật ký và nút bật/tắt particle có trạng thái rõ ràng, lưu bền.
- Chỉ dẫn đường tới NPC khi objective hiện tại là gặp NPC hoặc nộp item; hiển thị title/subtitle khi hoàn thành quest.
- Đặt bình EXP ở giữa cụm nút tăng/giảm, ngay phía trên icon VNCOIN trong giao diện sửa quest.

## [v2.17.0] - 2026-10-25
- Thêm công tắc particle tracking riêng cho người chơi; thay đường thẳng bằng mũi tên particle bám mặt đất và tìm đường quanh vật cản cho quest NPC/tutorial.
- Chuyển hội thoại story/quest sang action bar chạy chữ theo dạng `[Tên NPC:]`; không gửi nội dung vào chat/lore. Dialog nhận quest kết thúc bằng GUI nhận quest.
- Thêm góc xoay độc lập với facing cho `/vntext`, vẫn tương thích cú pháp cũ với rotation mặc định `0°`.
- Thêm objective gặp NPC theo ID và hỗ trợ nối tiếp các mục tiêu nộp item RPG đã cấu hình.
- Thêm shop thu mua item cho system/custom merchant với nút chỉnh số lượng/giá VNCOIN; hiển thị giá bán trên item rơi, mặc định `64 = 1 VNCOIN` và hỗ trợ override theo Material.
- Mở rộng chest GUI 27 ô thành 54 ô; Quick Cast vẫn giữ một hàng 9 ô.

## [v2.16.0] - 2026-10-25
- Thay luồng NPC story thành GUI nền kính phát nội dung chapter theo kiểu typewriter; admin shift+right-click để chọn chapter.
- Thêm trình quản lý quest-dialog riêng và gán hội thoại theo trạng thái quest: có thể nhận, đang làm, hoàn tất/nhận thưởng, hoặc chưa đủ điều kiện; quest đã hoàn thành dùng hội thoại hoàn tất.
- Thêm `story-chapter-id` bền vững cho NPC story và dùng hội thoại trước khi nhận quest hoặc nộp vật phẩm.

## [v2.19.0] - 2026-10-26
- Thêm `/vnplayeradmin` và GUI quản trị người chơi online/offline: ban/unban, kick, mute theo thời hạn, xóa inventory, phục hồi tối đa 10 inventory tử vong bằng hàng đợi bền vững, xem/reset quest hoàn thành, reset quest/skill/race/class/hồ sơ, cấp level/EXP và item RPG nhiệm vụ; ghi audit log và thêm cấu hình VI/EN.
- Thêm quest repeatable ON/OFF cùng số lượt/ngày 0-10 trong editor; 0 là không giới hạn. Lượt hoàn thành và mã giao thưởng được lưu SQLite theo ngày/attempt để chống callback phát thưởng trùng.
- Mở lại luồng chọn race/class cho hồ sơ admin đã reset; reset race/class giữ quest, còn reset toàn hồ sơ xóa tiến trình RPG nhưng giữ ví VNCOIN và khởi động lại onboarding.

## [Unreleased]
- Bổ sung GUI lịch sử tiến chức: xem class gốc, class hiện tại và từng lần chuyển class theo thời gian trong menu tiến chức.
- Thêm GUI tiến chức class từ cây kỹ năng: class con kế thừa skill hoạt động trong lineage được liệt kê, hiển thị cấp/quest/NPC/vật phẩm yêu cầu, cần xác nhận trước khi đổi; giữ skill đã học. Lưu lịch sử chuyển class bằng SQLite schema v13; hỗ trợ điều kiện level, quest, NPC, Material và RPG quest-item theo ID, log các lần kiểm tra.
- Thêm nền tảng kế thừa skill cho class nâng cao bằng `parent-class-id`: kiểm tra parent tồn tại/cùng race/không tạo chu trình; skill cha được xét trong cây skill, sách, học/nâng cấp, quick slot, passive và cast. Có log `[ClassProgression]`; điều kiện item/quest/NPC, chỉ số class và GUI lịch sử vẫn đang làm.
- Thêm flow onboarding lưu theo người chơi: chọn đại lục, xác minh NPC tutorial nằm đúng world, hướng dẫn khác world và navigation particle trong cùng world trước khi mở chọn race/class.
- Lưu world khởi đầu vào hồ sơ; hiển thị bước onboarding trên scoreboard và không khởi chạy quest spawn Nhân giới khi người chơi bắt đầu ở world khác. Cần compile và kiểm thử Paper/gameplay trước phát hành.
- Thêm GUI quản lý sách skill đầu tiên theo race/class qua `/vnskillbook config` và mục Quản trị → RPG; sách được giao bằng hàng đợi bền vững, quest tutorial chỉ bắt đầu khi đúng transaction của sách đã có trong túi/kho đồ, không cấp hồi tố cho profile cũ. Bổ sung debug log mapping, retry và đối soát. Cần compile và kiểm thử onboarding, cấu hình mapping và giao sách khi túi đầy.
- Thêm liên kết quest successor trong GUI admin; khi hoàn thành, link được ghi bền vững cùng transaction reward và tự nhận khi đủ điều kiện/còn slot, có retry sau join/quest complete/abandon. Chặn self-link, cycle và xóa quest đang được tham chiếu; bổ sung log `[Quest][Successor]`. Chưa compile/gameplay test.
- Thêm marker NPC ảo riêng theo từng người chơi: `!` quest có thể nhận, `?` story chưa đọc/NPC tutorial đang chờ; trạng thái story lưu trong PDC sau khi dialog hoàn tất. Thêm cấu hình khoảng cách/chu kỳ/bật tắt và log marker transition. Chưa compile/gameplay test.
- Giới hạn shop merchant tùy chỉnh chỉ hiện và thu mua buy-order được cấu hình tại chính NPC đó; bỏ fallback giá từ NPC khác/giá mặc định cho item không được cấu hình. Kiểm tra lại order và log số lượng/PDC fingerprint an toàn trước khi bán; chưa compile/gameplay test.
- Tách giá system merchant sang allowlist `merchant-prices.yml`; thêm bảng giá vanilla khởi điểm, `sell-value` cho skill book, boss loot, quest drops/rewards và gear lính, cảnh báo shop custom tốt hơn, cùng debug log đối chiếu giao dịch. E2 vẫn chờ kiểm thử vận hành và rà nguồn item còn lại.
- Thêm intent/escrow SQLite cho giao dịch bán: khóa lượng item bằng PDC, lưu inventory trước khi tiêu thụ và chỉ cộng VNCOIN trong transaction idempotent; đối soát khi join hoặc mở `/vncoin`, hoàn item vào hàng đợi bền vững nếu ví chạm giới hạn, và ngăn escrow item rơi khi chết. Thêm log chuyển trạng thái/recovery. Chưa compile hoặc kiểm thử crash/restart; E2 vẫn chưa hoàn tất.
- Thêm giá `sell-value` mặc định 0 cho nguyên liệu RPG nhiệm vụ, từng stack thưởng quest và gear lính đánh thuê; bổ sung giới hạn/ghi chú VI-EN, tự thêm default cho file quest cũ, hiển thị giá thưởng trong GUI admin và log giá lúc drop/giao thưởng/tải config. Chưa compile hoặc kiểm thử bán trên Paper.

## [v2.15.0] - 2026-10-25
- Thêm lệnh `/vntext` để tạo, chỉnh sửa, di chuyển, xem và xóa text display cố định; hỗ trợ facing 6 hướng, màu tên/hex, scale và lưu entity bền vững qua chunk/restart.
- Thiết kế lại mục quest scoreboard để hiển thị tối đa 3 quest đang nhận, bậc mục tiêu và tiến độ; tên quest có thể mở chi tiết bằng `/vnquest detail <id>`.
- Thêm GUI Nhiệm vụ RPG gồm danh sách đang nhận, nhật ký hoàn thành, chi tiết/reward preview, theo dõi và hủy quest có xác nhận; giới hạn đồng thời 3 quest.
- Mở rộng objective theo chuỗi với hạ boss hệ thống và nộp item cho NPC giao quest; gắn marker PDC lên boss hệ thống và tracking particle cập nhật tới vị trí NPC, hướng dẫn world khi khác thế giới.

## [v2.14.0] - 2026-10-25
- Thêm lối vào quản lý quest/NPC trong menu Admin; bổ sung GUI CRUD NPC và prompt chat có ví dụ, màu phân biệt, hỗ trợ `!cancel`.
- Sửa lỗi NPC thiếu dialogue definition làm ngăn hiển thị quest theo NPC; dùng hội thoại mặc định tiếng Việt và ưu tiên definition khớp ID NPC.

## [v2.13.0] - 2026-10-25
- Hiển thị mục tiêu tutorial đang theo dõi trong scoreboard và thêm nút compass có hover mô tả để bật tracking trực tiếp từ journal.
- Thêm quest NPC săn zombie/skeleton/spider và thu thập nguyên liệu RPG có PDC, lưu tiến độ SQLite schema 10, trả VNCOIN một lần khi hoàn thành, log debug theo cờ console và hiện tiến độ quest đang chọn trên scoreboard.
- Mở rộng `/vnquest admin`: quản lý danh sách, tên/NPC giao, bật/tắt/xóa, điều kiện nhận AND, mục tiêu hạ mob/thu thập item tuần tự, rương item thưởng và nút chỉnh EXP/VNCOIN; NPC chỉ hiện quest phù hợp với NPC và điều kiện người chơi.

## [v2.12.0] - 2026-10-25

### Added
- Thêm tracking particle cho tutorial quest, bật/tắt theo người chơi và đánh dấu đích.
- Thêm schema SQLite 9 và API nền ghi lượt quest VNCOIN lặp lại tối đa 10 lần/ngày, chống callback trùng bằng source ID; chưa nối vào quest gameplay/UI.
- Thêm nội dung cho 9 chapter, bấm chapter mở sách Minecraft; NPC Người giữ ký ức mở được danh mục truyện.

## [v2.11.0] - 2026-10-25

### Added
- Thêm công tắc tracking nhiệm vụ theo người chơi trong journal; dùng một ticker chung để cập nhật đường particle và đánh dấu đích.
- Bổ sung cấu hình tracking mặc định, marker đích và chu kỳ cập nhật đường; giữ tương thích với `first-steps.yml` cũ.
- Mở rộng hướng phát triển quest đa mục tiêu, lặp lại theo ngày, preview reward, nhánh class/race buff và cốt truyện vào `plan.md`.

## [v2.10.0] - 2026-10-25

### Changed
- Gắn trạng thái `available`/`locked`/`planned` cho class; chỉ hiển thị class available có skill hoạt động trong giao diện tạo nhân vật, đồng thời giữ tương thích với cấu hình cũ thiếu trạng thái.

## [v2.9.0] - 2026-10-25

### Added
- Thêm màn hình xác nhận số lượng và giá VNCOIN trước khi bắt đầu giao dịch merchant.

## [v2.8.0] - 2026-10-25

### Added
- Cho phép quest tutorial thưởng VNCOIN; đánh dấu nhận thưởng và ghi giao dịch ledger bằng transaction SQLite nguyên tử với ID cố định theo người chơi và quest để chống nhận trùng.

## [v2.7.0] - 2026-10-25

### Added
- Thêm `/vncoin stats` cho admin: tổng hợp lượng VNCOIN phát hành, đã tiêu và biến động theo nguồn trong cửa sổ thời gian cấu hình.

## [v2.6.0] - 2026-10-25

### Fixed
- Làm giao dịch mua merchant bền vững qua disconnect/crash: trừ tiền và lưu item chờ giao trong cùng transaction SQLite; khôi phục giao dịch khi vào lại hoặc mở `/vncoin`, ngăn sử dụng/chuyển vật phẩm trong khi xác nhận và lưu dữ liệu người chơi trước khi đánh dấu đã giao.
- Không còn hoàn tiền theo phỏng đoán khi phát item lỗi; item tiếp tục nằm trong hàng chờ cho tới khi giao được xác nhận.

## [v2.5.0] - 2026-10-25

### Added
- Phân biệt NPC `system-merchant` bán danh mục chung có phân trang với NPC `merchant` có shop riêng; thêm GUI admin để đưa item RPG vào shop, chỉnh giá bằng nút +/-1/10/100, gỡ món và lưu item/giá theo NPC.
- Cửa hàng VNCOIN chặn mua lặp đồng thời, yêu cầu chỗ trống, ghi giao dịch vào ledger và thêm luồng hoàn tiền khi plugin phát hiện giao item thất bại.
- Tạo ID ổn định cho từng drop VNCOIN của cùng một boss encounter để ledger từ chối cộng trùng khi callback thưởng bị gọi lại.

## [v2.4.0] - 2026-10-23

### Changed
- Đặt tỉ lệ rơi mặc định thành 30% độc lập cho từng món loot thường, loot theo cấp, từng món giáp boss, gear lính và token boss trên toàn bộ boss.

### Added
- Chia menu admin thành các nhóm Máy chủ, Người chơi, Quản lý quyền, RPG và Quân đội; thêm GUI chọn người chơi để cấp sách kỹ năng hoặc EXP/level RPG.
- Thêm quyền `vnmine.command.progression.admin` và cấu hình `menus.admin.rpg-tools.enabled`; cấp level trực tiếp tôn trọng level cap và cộng skill point theo số cấp thực nhận.

## [v2.3.1] - 2026-10-23

### Changed
- Quick Cast chỉ hiển thị một hàng 9 slot; bấm phím số để cast luôn đóng menu, kể cả khi slot trống hoặc cast thất bại.

## [v2.3.0] - 2026-10-23

### Added
- Thêm nút “Quản lý kỹ năng” trong menu chính, mở cây skill và có nút quay lại menu chính.

### Fixed
- Nhận thao tác học skill book ngay cả khi plugin khác đã hủy `PlayerInteractEvent`; vẫn chỉ xử lý nhấp phải bằng tay chính.

## [v2.2.2] - 2026-10-23

### Changed
- Đổi thông báo khi đi qua portal thành “Bạn đã dịch chuyển từ <cổng nguồn> đến <cổng đích>”.

## [v2.2.1] - 2026-10-23

### Added
- Ghi log console cho lệnh portal, kết quả lưu cấu hình, trạng thái điểm đến và kết quả validate để hỗ trợ chẩn đoán trên server.

### Fixed
- Phân biệt phím Q với thao tác kéo item ra ngoài inventory: chỉ phím Q mở Quick Cast và bị chặn rơi item; thao tác bằng chuột vẫn giữ nguyên.

## [v2.2.0] - 2026-10-22

### Added
- Thêm nội tại Tâm Kiếm cho kiếm sĩ đã học sách: tăng sát thương đánh thường/đánh quét lên quái theo rank, nâng rank bằng skill point theo cấp yêu cầu và cấu hình riêng; không áp dụng cho PvP, đạn hoặc sát thương skill chủ động.
- Thêm nội tại Thành Đồng cho hộ vệ đã học sách: đòn đánh của quái thù địch cộng stack giảm sát thương theo rank, tự hết hạn sau thời gian cấu hình; hỗ trợ nâng rank bằng skill point.
- Thêm nội tại Mạch Pháp cho pháp sư đã học sách: cast skill chủ động thành công liên tiếp hồi mana theo rank, với cửa sổ combo và lượng mana có thể cấu hình; hỗ trợ nâng rank bằng skill point.
- Thêm registry định nghĩa và NPC runtime dạng villager bất tử, đứng yên; lưu/khôi phục vị trí, đổi tên/di chuyển/xóa/liệt kê bằng `/vnnpc`, giới hạn số NPC cấu hình được và quyền quản trị riêng.
- Thêm tương tác nhấp phải NPC mở GUI thoại tối đa 20 dòng theo định nghĩa; khóa thao tác kéo/đổi vật phẩm trong GUI và nút đóng riêng.
- Thêm nút hành động NPC cấu hình được để mở nhật ký quest/skill tree hoặc cấp skill book, giới hạn chống cấp trùng và sửa thứ tự khởi tạo NPC listener.
- Thêm waypoint tutorial cấu hình được: action bar hiển thị vị trí/khoảng cách spawn và particle End Rod dẫn đường trong tầm gần, có giới hạn tần suất cập nhật.

## [v2.0.3] - 2026-10-21

### Added
- Thêm nền tảng dữ liệu RPG bằng SQLite, GUI tạo nhân vật theo race/class có bước xác nhận, và cấu hình race/class riêng cho quản trị viên.
- Thêm loader/validator chapter truyện, kiểm tra khóa YAML trùng và cơ chế merge mặc định không ghi đè cấu hình server.

## [v2.1.0] - 2026-10-22

### Added
- Mở rộng menu chính thành 45 ô, phân nhóm tiện ích máy chủ, chức năng RPG và trợ giúp/admin; bổ sung header và separator cho menu admin, giữ nguyên quyền truy cập/điều hướng.
- Thêm progression nhân vật RPG: EXP quest tự lên nhiều cấp trong cùng transaction, tích lũy skill point, hiển thị level/EXP/điểm chưa dùng trong GUI hồ sơ và cấu hình đường cong cấp.
- Thêm skill registry YAML và 9 skill book cho các class `swordsman`, `guardian`, `mage`; admin cấp sách bằng `/vnskillbook give`, người chơi dùng sách để học và xem kỹ năng đã học bằng `/vnskills`.
- Hoàn thiện Phase 3 RPG: Quick Cast, slot 1–9, cast sáu skill chủ động, cooldown/mana cấu hình YAML, hồi mana theo thời gian, GUI skill tree nâng rank bằng skill point và migration SQLite v5→v6.
- Thêm quản lý portal RPG bằng `/vnportal`: rìu gỗ chọn vùng không phụ thuộc WorldEdit, lưu portal/link trong `portals.yml`, đặt điểm đến, particle ring và chặn dịch chuyển khi chết/combat theo cấu hình.
- Thêm file `story/chapters.yml` với chapter có ID ổn định, thứ tự hiển thị và loader/kiểm tra cấu hình riêng; giữ nguyên danh sách artifact và `story-id` hiện có trong `story.yml`.
- Kiểm thử và tách logic merge cấu hình YAML: chỉ thêm giá trị mặc định còn thiếu, không ghi đè cấu hình server; giữ nội dung story legacy, không tự động migration hoặc xóa dữ liệu `story.yml`.
- Từ chối file story có khóa YAML trùng trước khi cấu hình map có thể ghi đè định nghĩa ID; lỗi được log và nội dung file đó không được nạp.
- Thêm GUI tạo nhân vật race/class có bước xem trước và xác nhận, chặn tạo nhiều nhân vật trên cùng UUID, và nạp danh sách tùy chỉnh từ `races.yml`/`player-classes.yml`.
- Nạp hồ sơ nhân vật đã lưu khi người chơi vào server, giữ profile trong session và xóa cache khi người chơi thoát; reconnect/restart không tạo hoặc ghi đè profile.
- Thêm định nghĩa đại lục Nhân giới trong `worlds/human-realm.yml`, phân giải spawn với world dự phòng đã nạp, và vùng an toàn chặn sát thương, PvP, quái thù địch cùng phá hoại địa hình.
- Thêm quest tutorial `first-steps` tự bắt đầu cho profile đang hoạt động; objective `visit-realm-spawn` được kiểm tra cấu hình và lưu trạng thái active/completed trong SQLite.
- Thêm GUI nhật ký nhiệm vụ từ menu chính: hiển thị trạng thái/checkpoint/chỉ dẫn/phần thưởng, làm mới tiến trình và ẩn mục menu qua cấu hình.
- Mở rộng hub RPG: mục Nhân vật RPG hiển thị hồ sơ đã lưu hoặc đưa người chơi mới tới luồng tạo nhân vật; trang hồ sơ liên kết tới nhật ký nhiệm vụ và menu chính.
- Thêm mục Sổ truyện trong hub, hiển thị chapter đã validate theo thứ tự với phân trang và trạng thái rỗng; không giả định tiến trình mở khóa cá nhân.
- Thêm Bản đồ đại lục phân trang, hiển thị access condition và trạng thái world/spawn cho các realm đã validate; có thể ẩn qua `rpg.world-map.enabled`.
- Thêm definition Yêu giới với điều kiện mở `completed-quest:first-steps`; map đọc quest progress SQLite, resolve world/fallback và không tự tạo world.
- Thêm dịch chuyển qua card đủ điều kiện bằng Paper async teleport; kiểm tra profile/access/world/combat/death/respawn grace và chỉ lưu unlock sau teleport thành công; failure không ghi unlock.
- Thêm travel qua map bằng `teleportAsync`; chặn khi chết/respawn grace, combat theo config hoặc primary destination không hợp lệ; chỉ ghi unlock sau teleport thành công.

### Changed
- Hoàn thành quest tutorial và cộng XP nhân vật trong cùng transaction SQLite; cờ `reward_claimed` chống trao lặp và hỗ trợ nhận bù một lần cho quest đã hoàn tất trước khi có reward.
- Nâng schema hồ sơ SQLite lên v2 với migration v1→v2 transactional; ghi hồ sơ dùng `BEGIN IMMEDIATE`, backup checkpoint WAL trước khi ghi/migration, và phục hồi an toàn từ backup hợp lệ khi database bị lỗi.
- Nâng schema hồ sơ SQLite từ v2 lên v3 transactional; chuyển EXP profile hiện có qua đường cong cấp để tạo level/skill point tương ứng và giữ EXP dư.
- Nâng schema hồ sơ SQLite từ v3 lên v4 transactional; lưu kỹ năng đã học và rank ban đầu, kiểm tra class/cấp yêu cầu trước khi ghi.
- Thêm backup SQLite định kỳ và giới hạn số bản lưu cấu hình được; retry có giới hạn cho lỗi database tạm bận, áp dụng lại qua `/vnreload`.
- Thêm API SQLite idempotent cho mở khóa đại lục và lưu trạng thái/bước/hoàn thành quest; duplicate unlock/completion không tạo tiến trình trùng.

### Fixed
- Đồng bộ lại checkpoint và objective quest tutorial sau khi người chơi hồi sinh, tránh giữ trạng thái session cũ sau death/recovery.

## [Unreleased]

## [v2.0.2] - 2026-10-21

### Fixed
- Build sạch lại JAR để đóng gói đúng danh sách skill mercenary 2.0.0; khắc phục lỗi khởi động `Unknown skill 'GUARDIAN_LINK'` do bytecode cũ trong `target/classes`.

## [v2.0.0] - 2026-10-21

### Added
- Thêm gear cho lính đánh thuê theo class, vật liệu, 1-5 sao, set và slot; chỉ số gear có hệ số, bonus set và giới hạn cấu hình riêng.
- Thêm `/vnarmy item give` để admin cấp một món hoặc trọn set gear bằng GUI hoặc tham số; dùng quyền hiện có `vnmine.command.army.admin`.
- Boss có thể rơi gear lính theo level; mỗi slot được roll riêng theo cấu hình `mercenary-gear-drops`.
- Bổ sung AI boss: lựa chọn mục tiêu có trọng số, né projectile, phối hợp minion, chiến thuật theo phase, giới hạn độ cao boss bay và scaling kích thước/HP.

### Changed
- Tăng thời lượng boss mặc định từ 20 lên 30 phút và di trú các file boss cũ đang đặt 1200 giây.
- Thêm giới hạn sát thương toàn cục boss gây trên mỗi hit người chơi, mặc định 6.
- Bổ sung gear stats theo class cho lính đánh thuê và cập nhật cấu hình boss/armies tương ứng.

## [v1.46.1] - 2026-10-19

### Changed
- Giảm bonus vũ khí tay chính của Vệ Binh xuống 10%; Hộ Vệ chuyển 40% sát thương chủ nhận sang lính.
- Điều chỉnh Xung Phong lao tối đa 8 block ở cấp thấp và tăng 1 block mỗi 5 Level; đổi tên, làm rõ mô tả Thánh Thể, Vây Hãm và Hộ Giá.
- Bổ sung di trú cấu hình mặc định Vệ Binh mới, giữ nguyên các giá trị người quản trị đã tùy chỉnh.

## [v1.46.0] - 2026-10-19

### Added
- Thay bộ skill Vệ Binh bằng sáu kỹ năng tanker tự kích hoạt: Trấn Phạt, Hộ Vệ Liên Kết, Xung Phong Đoạn Hải, Thánh Thể Bảo Hộ, Địa Chấn Giam Hãm và Đại Địa Tuyệt Đối: Hộ Giá.
- Tách tăng trưởng HP/giáp/damage Vệ Binh theo Bậc và Level; kiếm tay chính tăng damage, khiên tay phụ tăng HP/Absorption; mô tả hiển thị tổng và thành phần chỉ số bằng màu.
- Thêm cấu hình song ngữ, di trú bộ skill Guardian cũ và tài liệu cơ chế tanker; Địa Chấn chỉ dùng block giả phía client, không thay đổi địa hình thật.

## [v1.45.0] - 2026-10-19

### Changed
- Hiển thị tên loại vũ khí/vật phẩm ở tay chính và tay phụ thay cho ID vật phẩm; tách sát thương đòn đánh và kỹ năng thành các phần cơ bản, vũ khí, tăng theo cấp và hệ số phần trăm bằng màu.

## [v1.44.1] - 2026-10-19

### Changed
- Đổi model mặc định của Vệ Binh sang Husk để hiển thị vật phẩm ở cả hai tay; tự cập nhật cấu hình cũ còn dùng Vindicator.

## [v1.44.0] - 2026-10-19

### Changed
- Ngăn Pháp Sư Lửa/Băng ném potion; trang bị Snowball/Fire Charge đúng class và cho Fire Charge ưu tiên Fireball.
- Tăng 30% damage cho đòn của lính có vũ khí hợp lệ ở tay chính; hệ số có thể cấu hình trong `armies.yml`.
- Cập nhật icon/vũ khí mặc định và ưu tiên tự chọn vật phẩm cho tay chính/tay phụ của các class; Y Sư ném potion có lợi từ tay phụ mà không tiêu hao bình.

## [v1.43.0] - 2026-10-19

### Changed
- Chuyển Bão Lửa thành hình nón 3D xoay ngược chiều kim đồng hồ quanh caster; particle và sát thương dùng chung thể tích, bán kính mặc định 5 block tăng theo level.

## [v1.42.1] - 2026-10-19

### Fixed
- Buộc build sạch để JAR chứa đúng bytecode mới nhất; khắc phục lỗi khởi động khi cấu hình `FLAME_BOLT` bị nhận nhầm là skill không hợp lệ.

## [v1.42.0] - 2026-10-19

### Changed
- Chỉ cho Enderman boss nhắm và tấn công người chơi đang nhìn thấy boss, đồng thời yêu cầu tầm nhìn trực tiếp.
- Giới hạn nóng/lạnh/mưa axit và sát thương nước theo danh sách biome có thể cấu hình; mưa axit chỉ tác động khi mưa ngoài trời ở biome rừng, Nether luôn nóng.
- Ghi log ngày/world/ngưỡng/level boss tự nhiên đã chọn và đồng bộ hướng dẫn tiến trình level với cấu hình hiện hành.
- Đổi tên và thiết kế lại skill pháp sư: Hỏa Ngục/Băng Ngục là lồng tạm quanh mục tiêu, Mưa Lửa thả Fireball theo lượt và Bão Lửa gây sát thương theo thời gian.
- Bão Lửa tạo 10 hạt lửa ở level 1 và tăng thêm 1 hạt mỗi level.
- Hoàn nguyên block lồng sau khi kết thúc hoặc khi lính bị thu hồi/hạ; cấu hình mới của các skill có chú thích tiếng Việt và tiếng Anh.

### Fixed
- Chặn kỹ năng nhện gây damage tại vị trí người chơi ngoài tầm; bỏ đòn đánh thủ công lặp song song với AI gốc và giới hạn damage tối đa mỗi hit lên người chơi.

## [v1.41.3] - 2026-10-18

### Fixed
- Cho Pháp Sư Lửa tìm mục tiêu dựa trên bộ kỹ năng lửa, để AI tấn công và kích hoạt kỹ năng.

## [v1.41.2] - 2026-10-18

### Fixed
- Build lại từ mã nguồn mới nhất để JAR chứa danh sách skill hợp lệ đã đồng bộ, không đóng gói lớp biên dịch cũ.

## [v1.41.1] - 2026-10-18

### Fixed
- Đồng bộ danh sách skill hợp lệ dùng để kiểm tra cấu hình lính, tránh JAR cũ từ chối `ICE_SPIKE` trong cấu hình Pháp Sư Băng.

## [v1.41.0] - 2026-10-18

### Added
- Thiết kế lại Pháp Sư Lửa với sáu kỹ năng Hỏa Cầu, Thiêu Đốt, Hỏa Bão, Hỏa Tường, Thiên Thạch Lửa và Phượng Hoàng Tái Sinh.
- Thêm cấu hình damage, cooldown, DoT/lây lan, khống chế, tầm, giới hạn mục tiêu và block tường lửa; dọn miễn sát thương, hiệu ứng và block tạm theo thời hạn/reload/disable.
- Tự di trú bộ skill Pháp Sư Lửa mặc định cũ sang bộ mới mà không ghi đè danh sách kỹ năng đã tùy chỉnh.

## [v1.40.1] - 2026-10-18

### Fixed
- Tự chuyển cấu hình snowball cũ bị đặt nhầm trong `classes.frost_mage.skill-settings.SNOWBALL` sang `classes.frost_mage.basic-attack`, tránh làm plugin lỗi khi khởi động.

## [v1.40.0] - 2026-10-18

### Added
- Thiết kế lại Pháp Sư Băng với snowball bay thẳng và tám kỹ năng: Ice Spike, Blizzard, Frost Nova, Ice Wall, Frost Shield, Frost Step, Frost Walker và Ice Age.
- Thêm cấu hình riêng cho sát thương, cooldown, tầm, thời lượng, bán kính và giới hạn mục tiêu; damage/cooldown/range có scaling riêng theo level, không nhân chồng multiplier skill chung.
- Hoàn nguyên băng tạm khi hết thời gian, chặn phá/hủy/dịch chuyển block tạm và dọn task, hiệu ứng, block khi reload hoặc tắt plugin; Ice Age không biến đổi địa hình.

## [v1.39.3] - 2026-10-18

### Fixed
- Khi lính đánh thuê nâng bậc, đặt level về 1 và EXP về 0 để chỉ số cơ bản khớp với bậc mới; hoàn tác đầy đủ nếu không lưu được.

## [v1.39.2] - 2026-10-17

### Fixed
- Ngăn kỹ năng lính đánh thuê tác động tức thời lên mục tiêu sau vật cản hoặc bị che dưới địa hình; kiểm tra tầm nhìn lại ngay khi thi triển thay vì chỉ lúc chọn mục tiêu.

## [v1.39.1] - 2026-10-17

### Fixed
- Khắc phục leaderboard sát thương boss không cập nhật khi boss dùng máu ảo làm hủy sự kiện damage; giờ bảng nhận sát thương từ cùng luồng đã xác nhận đóng góp của boss.

## [v1.39.0] - 2026-10-17

### Added
- Thêm giới hạn triệu hồi đồng thời cho lính đánh thuê (mặc định 3, cấu hình riêng với giới hạn sở hữu); gọi cả đội ưu tiên bậc rồi level cao nhất.
- Mở rộng mỗi hạng top-1 đến top-10 thành pool khoảng 20 phần thưởng và chọn/lưu đúng một món ngẫu nhiên mỗi mùa.
- Tách tooltip người chơi và quản trị; `/vnhelp` lọc từng lệnh theo quyền và tooltip hướng dẫn phím F/`/vnmenu`.

### Changed
- Chia tổng EXP boss theo tỷ lệ sát thương, từ 50 EXP ở level 1 đến 150 EXP ở level 10; điểm mùa cũng chia theo tỷ lệ đóng góp cho mọi người gây sát thương.
- EXP mob cấp cho lính đang triệu hồi được chia đều, phần dư phân phối ổn định theo ID; chủ nhân tiếp tục nhận EXP mob.
- Đổi chu kỳ reset leaderboard mặc định thành 100 ngày trong game; ngưỡng damage boss chỉ còn áp dụng cho vật phẩm.

### Fixed
- Ngăn lính đánh dân làng/thực thể thụ động và mục tiêu ngoài tầm nhìn theo cấu hình AI mặc định.
- Cho kỵ sĩ được gọi lại riêng lẻ tạo và gắn ngựa; VEX bay về trong bán kính cấu hình quanh Tu sĩ rừng thay vì dịch chuyển khác world.

## [v1.38.4] - 2026-10-16

### Fixed
- Bảo vệ đuốc của skill ILLUMINATION khỏi phá block, nước, piston và vụ nổ cho đến khi hết thời hạn.
- Chỉ cho Trinh Sát dùng ILLUMINATION khi trời tối hoặc ánh sáng tại vị trí của nó thấp theo ngưỡng cấu hình.
- AI mặc định bỏ qua mục tiêu bị che khuất nằm dưới mặt địa hình, đồng thời vẫn cho phép tìm đường quanh vật cản trên mặt đất.

## [v1.38.3] - 2026-10-16

### Changed
- Điều chỉnh Trinh Sát: sát thương cơ bản, tăng theo cấp và sát thương tên còn 70% Xạ Thủ; giữ tầm phát hiện 48 block và tầm bắn thường 32 block; tầm VOLLEY/Soi Sáng là 20.8 block (30% xa hơn tầm Xạ Thủ 16 block).
- Tự cập nhật các giá trị mặc định Ranger cũ một lần trong `armies.yml`, không ghi đè các giá trị đã tùy chỉnh khác mặc định cũ.

## [v1.38.2] - 2026-10-16

### Fixed
- Cho AI lính tìm mục tiêu theo danh sách ưu tiên kể cả khi mục tiêu đang khuất tầm nhìn, để Pathfinder tự tìm đường; Trinh Sát chỉ dừng khi đã ở trong tầm bắn và nhìn thấy mục tiêu.

## [v1.38.1] - 2026-10-16

### Fixed
- Cho Trinh Sát tiếp tục chọn và tấn công mục tiêu theo AI của chế độ khi đang bảo vệ chủ, thay vì chỉ ưu tiên đi theo chủ nhân.
- Tự bổ sung skill `ILLUMINATION` một lần vào cấu hình Ranger cũ, đồng thời giữ quyền tùy chỉnh danh sách skill sau khi nâng cấp.

## [v1.38.0] - 2026-10-16

### Added
- Thêm `/vndeathhistory [player]` và menu admin xem tối đa 10 snapshot inventory khi chết; khôi phục thay thế túi đồ, giáp và tay phụ sau bước xác nhận.
- Thêm bước xác nhận trước khi xóa lính bằng GUI quản lý.
- Thêm skill Soi Sáng cho Trinh Sát, bắn tên lửa tạo đuốc tạm 30 giây; thêm cấu hình số tên, tầm tỏa, tốc độ và hồi chiêu.

### Changed
- Đặt mộ trên mặt nước khi chết dưới nước, dùng block hỗ trợ tạm và khôi phục block gốc khi mộ bị dọn.
- Trinh Sát dùng nỏ, chủ động tìm mục tiêu theo AI ưu tiên chung, có tầm phát hiện/bắn xa gấp đôi Xạ Thủ; tiêu thụ tên trong kho hoặc dùng Spectral Arrow miễn phí khi hết.
- Chặn `/quatanthu` nếu người chơi đã nhận quà trước đây hoặc hiện sở hữu lính đánh thuê.

## [v1.37.0] - 2026-10-16

### Fixed
- Chặn lính đánh thuê và VEX gây sát thương lên người chơi/lính của chủ khác, trừ khi chủ nhân trực tiếp đánh đúng mục tiêu trong thời hạn 15 giây; thêm cấu hình để tắt hoàn toàn ngoại lệ PvP.
- Khi ngựa Kỵ sĩ chết, lưu trạng thái qua khởi động lại và không tự tạo ngựa thay thế; nghỉ Kỵ sĩ sẽ xóa ngựa còn sống và cho phép tạo ngựa mới ở lần xuất chiến tiếp theo.

## [v1.36.0] - 2026-10-16

### Fixed
- Giới hạn mục tiêu người chơi do chủ nhân chủ động đánh trong 15 giây; lính không tiếp tục đuổi theo mục tiêu PvP cũ và có thể truy đuổi mục tiêu chủ nhân bắn trúng kể cả khi lính chưa nhìn thấy.
- Xóa VEX được Tu sĩ rừng triệu hồi khi lính nghỉ; VEX quay về bằng đường bay chậm thay vì dịch chuyển giật cục.
- Dịch chuyển cả ngựa của Kỵ sĩ cùng lính; thêm cấu hình tốc độ ngựa mặc định bằng mức phi nước đại tối đa.

## [v1.35.0] - 2026-10-16

### Changed
- Đổi thanh HP VEX sang màu xanh lá và thêm biểu tượng hành động AI cập nhật theo trạng thái tấn công, theo Tu sĩ hoặc đứng yên; màu và biểu tượng có thể tùy chỉnh trong `armies.yml`.
- Khi nghỉ, VEX quay về gần Tu sĩ rừng và được giữ trong bán kính tối đa 5 block.

## [v1.34.0] - 2026-10-16

### Added
- Thêm thanh HP riêng dạng ô đầy/rỗng cho từng VEX của Tu sĩ rừng và AI ưu tiên mục tiêu Tu sĩ đang đánh, kẻ tấn công Tu sĩ, kẻ tấn công chủ nhân, rồi theo Tu sĩ.
- VEX của Tu sĩ rừng tăng sức chứa thêm một con mỗi 5 level, bắt đầu từ một con ở level 1; thêm cấu hình giới hạn và thứ tự ưu tiên AI.

## [v1.33.4] - 2026-10-16

### Fixed
- Buộc mọi lính Cuồng Chiến có cấu hình cũ `PIGLIN_BRUTE` được tạo và khôi phục dưới dạng `ZOMBIFIED_PIGLIN`; ghi log cảnh báo khi gặp cấu hình cũ.

## [v1.33.3] - 2026-10-16

### Fixed
- Chuyển Cuồng Chiến sang ZOMBIFIED_PIGLIN để loại bỏ nguyên nhân biến hình ở Overworld; tự cập nhật cấu hình mặc định cũ và thay entity đang chạy sau khi lưu trang bị.

## [v1.33.2] - 2026-10-16

### Fixed
- Bảo toàn vật phẩm lính đang mang khi chặn sự kiện biến hình; khôi phục ô bị trống từ dữ liệu đã lưu ở tick kế tiếp và tái áp dụng miễn nhiễm zombification định kỳ.

## [v1.33.1] - 2026-10-16

### Fixed
- Ngăn lính đánh thuê thuộc nhóm Piglin, bao gồm Cuồng Chiến, biến thành Zombified Piglin ở Overworld; áp dụng cả khi lính được tải lại từ chunk.

## [v1.33.0] - 2026-10-16

### Changed
- Hiển thị tên chủ, tên lính, bậc và level ở dòng cuối của thanh trạng thái.

## [v1.32.0] - 2026-10-16

### Added
- Thêm cấu hình `status-display.see-through` để điều khiển khả năng nhìn thanh HP xuyên tường; mặc định bật.

## [v1.31.0] - 2026-10-16

### Changed
- Thanh lính đánh thuê hiển thị hai dòng: vạch HP riêng và dòng tim/HP/dấu sao/EXP.

## [v1.30.3] - 2026-10-16

### Fixed
- Dùng `IntList` cho packet `ENTITY_DESTROY` theo đúng kiểu `entityIds` trên Paper 26.3; giữ `int[]` cho packet `MOUNT`.

## [v1.30.2] - 2026-10-16

### Fixed
- Sửa metadata `TEXT_DISPLAY`: dùng `Component` không bọc Optional, billboard đúng index 15 và opacity đầy đủ tại index 26.
- Sửa modifier entity ID của packet `MOUNT`/`ENTITY_DESTROY` cho schema 26.3 dùng `int[]`, tránh lỗi `FieldAccessException` khi gọi lính.

## [v1.30.0] - 2026-10-16

### Added
- Chuyển thanh HP/EXP lính đánh thuê sang `TextDisplay` ảo qua ProtocolLib; chỉ chủ nhân nhìn thấy, người khác chỉ thấy tên lính.
- Thêm token triệu hồi theo class cấu hình riêng từng boss; bậc token khớp level boss.
- Thêm thanh HP/EXP phía trên lính, tự thu EXP orb cho chủ online, cấu hình ưu tiên AI theo mode và bán kính tuần tra.
- Thêm cơ chế Kỵ sĩ tự tìm mount do plugin quản lý, lưu/chọn giáp ngựa tốt nhất và cấu hình HP riêng cho ngựa.
- VEX của Tu sĩ rừng tăng giới hạn theo level, mỗi lần cast chỉ gọi thêm một con; summon dùng chung bộ lọc mục tiêu của lính.

### Changed
- Đồng bộ compile target với Paper `26.3.build.175-stable` và Java 25; yêu cầu ProtocolLib runtime `5.5.0-SNAPSHOT`.
- Việt hóa tên class/skill và hiển thị mô tả, thông số, cooldown thực tế theo level; sức mạnh tăng tuyến tính, cooldown giảm tối đa vài giây.
- Lính chọn vũ khí tốt nhất giữa tay chính và kho; chỉ cấp vũ khí mặc định khi không có vũ khí phù hợp hơn.
- AI phân thứ tự trả đũa, tìm mục tiêu, theo chủ và tuần tra; không chủ động tấn công lính/đệ của người chơi khác.

### Fixed
- Chặn biến hình ở ưu tiên sự kiện cao và lưu gear trước khi hủy transform.

## [v1.29.0] - 2026-10-14

### Changed
- Giảm sát thương của các boss xuống mức tối đa 10 damage và giới hạn phạm vi skill trong khoảng 3-5 block để cân bằng combat.
- Nâng cấp build mới cho phiên bản 1.29.0.

## [v1.28.0] - 2026-10-14

### Fixed
- Chặn biến hình của mercenary để giữ nguyên item trên người và bảo toàn gear khi entity đổi form.
- Cập nhật AI lính đánh thuê: chủ động tiêu diệt hostile mob trong 50 block, không tấn công đồng minh/người chơi khác ngoài trường hợp chủ nhân đã tấn công trước, và giữ dải tấn công an toàn với đội cùng chủ.
- Đồng bộ và việt hóa giao diện thông báo cho người dùng sang tiếng Việt.

## [v1.27.1] - 2026-10-14

### Fixed
- Chặn biến hình của mercenary để giữ nguyên item trên người và bảo toàn gear khi entity đổi form.
- Cập nhật AI lính đánh thuê: chủ động tiêu diệt hostile mob trong 50 block, không tấn công đồng minh/người chơi khác ngoài trường hợp chủ nhân đã tấn công trước, và giữ dải tấn công an toàn với đội cùng chủ.

## [v1.27.0] - 2026-10-14

### Added
- Thêm túi quà tân thủ `BLACK_BUNDLE`, `/quatanthu` để nhận túi lần đầu, menu chọn token triệu hồi lính đánh thuê bậc 1 và lưu trạng thái chỉ nhận một lần duy nhất theo người chơi.

## [v1.24.2] - 2026-10-14

### Fixed
- Bỏ âm thanh ting phát cùng thông báo lính đánh thuê sử dụng kỹ năng; giữ particle và action bar.

## [v1.24.1] - 2026-10-14

### Fixed
- Thông tin menu lính hiển thị vũ khí phù hợp theo cấu hình class thay vì vũ khí hiện đang cầm.

## [v1.24.0] - 2026-10-14

### Added
- Hiển thị loại vũ khí tay chính trong thông tin chi tiết lính đánh thuê.

### Fixed
- Bảo toàn vật phẩm trong kho lính khi đóng giao diện, kể cả thao tác chuyển nhanh hoặc vật phẩm nằm ở ô giao diện.
- Chặn cháy nắng trên toàn bộ lính đánh thuê và ngựa đang chở Kỵ sĩ.

## [v1.23.0] - 2026-10-14

### Changed
- Kỵ sĩ dùng `IRON_SPEAR` khi runtime hỗ trợ, tự dự phòng sang TRIDENT khi không có Material tương ứng; hiệu ứng lao giáo có thêm hoạt ảnh/âm thanh dễ nhận biết.
- Tắt riêng âm thanh `ENTITY_EVOKER_PREPARE_ATTACK` của Evoker lính đánh thuê; các âm thanh Evoker khác vẫn theo trạng thái mute hiện tại.

## [v1.22.1] - 2026-10-14

### Fixed
- Chặn VEX tiếp tục đánh mục tiêu bị cấm và chặn sát thương lên chủ nhân/lính đồng minh.
- Sửa trang bị TRIDENT cho Kỵ sĩ, skill lao giáo khi cưỡi ngựa và dọn ngựa cùng Kỵ sĩ khi nghỉ/thoát game/chết.

## [v1.22.0] - 2026-10-14

### Fixed
- Chặn hành vi panic/flee của mercenary dùng model Villager để giữ custom AI chiến đấu.
- Chặn biến thể zombification cho mercenary Piglin/Piglin Brute và loại bỏ Vex do Evoker sinh ra khỏi quân lính.
- Chuyển class Kỵ sĩ sang model Zombie cưỡi ngựa, đổi tên hiển thị thành "Kỵ sĩ" và bổ sung skill lao giáo khi đang cưỡi.

## [v1.21.0] - 2026-10-14

### Changed
- Menu gọi boss của admin hiển thị skull đúng loại boss hoặc spawn egg tương ứng; dùng barrier khi Minecraft không cung cấp cả skull lẫn spawn egg (hiện gồm Wither và Giant).

## [v1.20.1] - 2026-10-14

### Fixed
- Kỹ năng `VOLLEY` dùng đúng loại mũi tên trong kho lính, bao gồm tên phổ và tên tẩm hiệu ứng; mũi tên vẫn không bị tiêu hao.

## [v1.20.0] - 2026-10-14

### Added
- Hiển thị tiền tố chủ lính xanh lá sáng, đậm với chủ và xanh da trời sáng, đậm với người xem khác; yêu cầu ProtocolLib 5.5.0-SNAPSHOT dev-build hỗ trợ Paper 26.3.

### Changed
- Mô tả đúng điều kiện rơi token boss: roll 30% khi có người chơi gây sát thương boss.

## [v1.19.0] - 2026-10-13

### Added
- Mở rộng quân đội lên 10 class với Pháp Sư Lửa, Druid, Sát Thủ và Pháp Sư Băng; mỗi class có vũ khí, chỉ số, skill và chi phí nâng bậc riêng.
- Thêm skill `FIRE_BOLT`, `ROOT_TARGET`, `LIFESTEAL`, `BERSERK`; thông số và cooldown có thể tùy chỉnh trong `armies.yml`.

## [v1.18.0] - 2026-10-13

### Added
- Nâng giới hạn đội lính mặc định lên 10; tách bậc thuê khỏi cấp farm tối đa 30 và hiển thị EXP/trạng thái đầy đủ.
- Thêm `/vnarmy rename <ID> <Tên mới>`, recall/rest theo ID, nâng bậc bằng nút GUI và chi phí vật phẩm theo class/bậc.
- Thêm hồi phục lính sau khi bị hạ, EXP boss theo loại/level và EXP cho người gây sát thương cùng lính đang xuất chiến.
- Thêm trang bị mặc định, tự nhận diện vũ khí/giáp, kho vật phẩm bền vững, mũi tên vô hạn và thức ăn hồi máu.
- Mỗi class mặc định có 4–5 kỹ năng; hiệu ứng cast, sức mạnh theo level và thông số chiến đấu có thể cấu hình trong `armies.yml`.
- Bổ sung `VOLLEY` cho Xạ Thủ, `POWER_STRIKE`/`TAUNT` cho Vệ Binh và `CLEANSE_OWNER` cho Y Sư; cấu hình riêng cooldown, sát thương, số/tốc độ/độ tỏa tên, bán kính và danh sách hiệu ứng.
- Vũ khí mặc định được ghi vào trang bị ngay lúc triệu hồi; gear đã tùy chỉnh của người chơi không bị ghi đè.
- Thêm ba class lính: Cuồng Chiến (`berserker`), Trinh Sát (`ranger`) và Thánh Kỵ (`paladin`), mỗi class có 4–5 skill, vũ khí, chỉ số và chi phí nâng bậc riêng trong `armies.yml`.
- Thêm `tooltips.yml` với nhắc nhở định kỳ theo thứ tự hoặc ngẫu nhiên; tinh chỉnh AI vẫn ở `armies.yml`.
- Đặt tỉ lệ token boss mặc định 30% và thêm dòng EXP cơ bản riêng cho từng boss.

### Changed
- Bỏ dòng chat mô tả tác động/cách phòng tránh thiên tai; thông báo world bị ảnh hưởng vẫn giữ.
- Lính đánh thuê bỏ qua sát thương môi trường và không nhận mục tiêu khuất tầm nhìn.

## [v1.17.0] - 2026-10-13

### Added
- Thêm menu chức năng mở bằng F hoặc `/vnmenu`, menu admin và luồng GUI thuê/cấp lính theo lớp, tier.
- Tách tier triệu hồi khỏi level farm; lính nhận XP khi hạ quái, tăng chỉ số theo level và lưu tiến trình riêng.
- Thêm `/vnarmy remove`, `/vnarmy clear`, `/vnarmy list <user>` và bật/tắt âm thanh riêng từng lính.
- GUI hiển thị HP, giáp, sát thương, skill, thông số và cooldown; chọn tier có tooltip requirement.

### Fixed
- Sửa lệch vị trí click GUI, ngăn gear mất độ bền, chặn lính phản công chủ và loại mục tiêu đang bay.
- Lính bám mục tiêu chủ vừa đánh, ngừng khi mục tiêu chết; tự dịch chuyển theo ngưỡng cấu hình.
- Recall triệu hồi cạnh chủ; damage boss và loot do lính gây ra được tính cho chủ nhân.

### Added
- Boss có thể rơi lệnh triệu hồi lính cấp trùng với cấp boss; bật/tắt và chỉnh tỉ lệ trong `bosses/_settings.yml`.
- Thêm `/vnarmy gui` để xem đội quân, đổi mode, nghỉ/triệu hồi từng lính và mở giao diện trang bị.
- Thêm `/vnarmy give <player> token <level>` và `/vnarmy give <player> army <class> <level>`; lệnh cấp lính trực tiếp vẫn tuân giới hạn quân số.
- Thêm skill lính `RANGED_ATTACK`, `BUFF_ALLIES`, `SHIELD_OWNER`; cooldown, mục tiêu, tầm và hiệu ứng có thể tùy chỉnh theo lớp trong `armies.yml`.

## [v1.15.0] - 2026-10-13

### Added
- Thêm nền tảng `/vnarmy`: thuê lính theo token, XP và vật liệu; dữ liệu, chế độ và trang bị được lưu bền vững.
- Thêm các lệnh recall/rest, attack/defend/protect, danh sách quân và giao diện trang bị.
- Thêm lớp Xạ Thủ, Vệ Binh, Y Sư; hỗ trợ AI chiến đấu, hồi máu chủ nhân/đồng minh và buff.
- Thêm `armies.yml` để cấu hình số lượng, chi phí, cấp, lớp lính, AI và kỹ năng.

## [v1.14.0] - 2026-10-13

### Added
- Đặt xác suất mưa mặc định 40%, thời lượng tối đa 5 phút và chọn ngẫu nhiên để mưa có thể kết thúc sớm.
- Thêm ngưỡng đóng góp sát thương boss 50%; người vượt ngưỡng được tính điểm/loot dù boss chết bởi nguyên nhân khác, trừ `/vnboss kill`.
- Thêm bộ giáp bốn món cho cả 13 boss, vật liệu/enchant tăng theo cấp và lore truyện riêng trong `story.yml`.
- Tăng xác suất rơi vũ khí/sách boss, cân enchant từ cấp dưới chuẩn đến over-enchant ở level cao.
- Tăng dần xác suất spawn boss theo ngày đến 100% và cân level từ 1 đến 10.
- Cân đều trọng số thiên tai đang bật và chọn ngẫu nhiên trong nhóm phù hợp điều kiện.
- Việt hóa tên skill boss trong thông báo, bổ sung broadcast cho toàn bộ người chơi.
- Làm nổi bật boss bằng viền phát sáng, bật persistence và ghi log khi entity boss mất hiệu lực.
- Giant có chuyển động đuổi mục tiêu và đi lang thang chậm hơn.
- Boss chết không có người chơi vượt ngưỡng sát thương sẽ được ghi log và không nhận điểm/drop.
- Ghi console log và thông báo skill cast riêng cho OP; có thể bật/tắt trong `bosses/_settings.yml`.

### Fixed
- Creeper không còn bị bộ xử lý kẹt giữ nguyên vận tốc dọc và bay tăng độ cao; khôi phục gravity và chỉ dùng pathfinding cho mob.
- Các boss mob giữ AI/navigation vanilla trong truy đuổi và đi lang thang; bỏ ép quay về tâm ngẫu nhiên gây ngắt hành vi.
- Enderman có hiệu ứng cổng và âm thanh khi dùng Void Snatch; chỉ đánh dấu cast thành công khi teleport hợp lệ.
- Ground Slam không còn gây sát thương nhiều lần khi các vùng sóng chồng lấn; khối tạm không ghi đè thay đổi của người chơi.
- Ender Dragon không còn bị ép bay tới điểm ngẫu nhiên mới mỗi chu kỳ AI.

## [v1.13.1] - 2026-10-13

### Fixed
- Chặn sát thương Ground Slam lặp trong cùng một lần cast và bảo vệ chỉnh sửa của người chơi với khối tạm.
- Giảm điều khiển cưỡng bức chuyển động Ender Dragon để tránh đường bay giật.

## [v1.13.0] - 2026-10-13

### Added
- Thêm `/vnboss kill` để kết thúc boss hiện tại mà không trao điểm hoặc loot.
- Thêm skill và trait cấu hình cho Enderman, Ravager, Phantom, Ghast, Spider, Vindicator, Witch và Giant.
- Thêm cấu hình terrain hazard tạm thời và allowlist block cho Ravager phá địa hình.

### Changed
- Chỉ trao điểm tiêu diệt boss khi người chơi hạ boss trước khi hết thời gian; timeout/despawn không được điểm.
- Giữ AI mặc định của boss; Creeper tiếp tục truy đuổi mục tiêu nhưng không tự kích nổ vanilla.
- Bảo vệ spawn region khỏi thay đổi địa hình do skill boss.

## [v1.12.0] - 2026-10-12

### Added
- Cung Skeleton level 1 luôn rơi cung Flame I và Infinity I.

### Changed
- Rút gọn lore di vật trong `story.yml` còn tối đa 30 ký tự mỗi dòng và 6 dòng mỗi item, giữ liên kết giữa các chương boss.

### Fixed
- Boss không còn bị dịch chuyển lên cao khi thoát kẹt; Creeper không còn tự nhận sát thương từ skill sét.

## [v1.11.1] - 2026-10-12

### Fixed
- Mưa axit chỉ gây sát thương và hiệu ứng khi người chơi thực sự ở ngoài trời có mưa.

## [v1.11.0] - 2026-10-12

### Added
- Thêm kho truyện `story.yml` cho di vật boss; tên và lore cấp thấp được cân chỉnh theo sức mạnh vật phẩm.

### Fixed
- Boss miễn nhiễm sát thương môi trường và sát thương từ skill tự tạo; chỉ rơi item khi người chơi đã gây sát thương.

## [v1.10.0] - 2026-10-12

### Added
- Thêm quản lý nhóm/quyền trong `permission.yml` qua `/vngroup` và `/vnuser`, mặc định có nhóm `user` và `admin`.
- Tách quyền lệnh theo chức năng để có thể cấp riêng quyền xem trạng thái, quản lý thiên tai/boss, spawn, thời gian, game mode và quản lý người dùng.
- Thêm quyền `vnmine.spawnprotection.bypass` cho OP/nhân viên được ủy quyền.
- Thêm `story.yml` làm kho lore tập trung cho các di vật hiếm boss, kết nối thành vũ trụ truyện chung và hỗ trợ mở rộng bằng `story-id`; cấp thấp dùng tên/lore giản dị hơn.

### Fixed
- Spawn protection chặn cả phá/đặt block, cháy, dòng chất lỏng, thay đổi block do entity và phá block do vụ nổ.
- Các thao tác đặt, đốt, thay thế, nhấc block và nổ trực tiếp từ thiên tai bỏ qua block nằm trong vùng bảo vệ.
- Boss miễn nhiễm sát thương môi trường (kể cả sát thương rơi) và sát thương từ skill tự tạo; mọi drop chỉ rơi khi có người chơi gây sát thương.

## [v1.9.1] - 2026-10-12

### Fixed
- Đóng gói lại JAR từ resource hiện tại để `plugin.yml` chứa đầy đủ lệnh `vnspawn` và `tp`, tránh lỗi `getCommand(...)` trả về `null` khi khởi động.

## [v1.9.0] - 2026-10-12

### Added
- `/vnspawn set` đặt spawn của world hiện tại tại vị trí và hướng đang đứng.
- `/vnspawn go <world>` dịch chuyển người gọi đến spawn của world đang tải; hỗ trợ gợi ý tên world bằng Tab.

## [v1.8.0] - 2026-10-12

### Added
- `/tp <user> <world>` dịch chuyển người chơi đang trực tuyến đến spawn của world; Tab gợi ý người chơi và world.
- `/vnspawn protect <X>`, `/vnspawn list`, `/vnspawn remove <ID>` quản lý vùng bảo vệ theo world, lưu trong `spawns-protection.yml`.
- `/vnevent stop` hủy cảnh báo hoặc thiên tai đang hoạt động, dừng các tác vụ liên quan và khôi phục trạng thái world được theo dõi.
- Earthquake hất người chơi ngoài trời theo phương ngang và dọc; vận tốc có thể chỉnh trong `disasters/earthquake.yml`.
- Mỗi file thiên tai có `chat-description` tùy chỉnh; chat hiển thị tác động, gợi ý phòng tránh và tên world tại lúc cảnh báo/bắt đầu.

### Fixed
- Bộ lọc thiên tai nhận diện đúng tên world và tên dimension; điều kiện `conditions.worlds` hiện được áp dụng.
- Chorus Explosion tắt mặc định để tránh khởi chạy tự động ngoài ý muốn.

## [v1.7.0] - 2026-10-12

### Added
- `/vntime set <day|night> <phút> [world]` đặt thời lượng toàn cục hoặc riêng cho world đang tải
- `/vnday set <ngày>` đặt world hiện tại về bình minh của ngày trong game tương ứng
- Loot hiếm và cấu hình HP riêng cho boss level 1–10; lore truyền thuyết tối đa 4 dòng, số dòng enchant tăng dần theo cấp
- Thời gian tồn tại mặc định của mọi boss là 20 phút; tạm dừng roll boss tiếp theo khi đang cảnh báo hoặc có boss hoạt động

## [v1.6.0] - 2026-10-12

### Added
- `/scoreboard reset` xóa điểm, mùa và phần thưởng boss đang chờ nhận
- `/vntime set day <phút>` và `/vntime set night <phút>` thay đổi, lưu thời lượng mặc định toàn cục; giữ nguyên override theo world
- `/vnhelp` liệt kê mọi lệnh plugin; Tab gợi ý ID và giá trị cho các tham số lệnh

## [v1.5.0] - 2026-10-12

### Added
- Creeper Boss có skill tự gọi sét, gây sát thương lên bản thân và sinh vật trong bán kính cấu hình, rồi chuyển sang trạng thái charged trong thời gian cấu hình
- Creeper Boss phát nổ thường/charged mạnh hơn tương ứng nhưng không tự bị xóa; vụ nổ không phá block

## [v1.4.0] - 2026-10-12

### Added
- Loot hiếm theo level trong file riêng của từng boss; hỗ trợ lore, enchant thường/over-enchant và sách lưu enchant để áp dụng lên vật phẩm
- Broadcast danh sách loot và tỉ lệ khi boss xuất hiện
- Bảng xếp hạng damage trực tiếp và điểm boss theo mùa; top 5 chia `10 × level` điểm theo damage gây ra
- Reset theo ngày trong game, thưởng cấu hình cho top 10, lệnh `/reward`, nhắc nhận quà, kiểm tra inventory và title chúc mừng top 1
- `scoreboard.yml` cho cấu hình mùa/sidebar/phần thưởng và `scoreboard-data.yml` cho lưu trạng thái

## [v3.0.0] - 2025-07-12

### Added
- Config tách rời: Boss và Disaster không còn nằm trong `config.yml`
- Thêm `bosses/*.yml`: Mỗi boss 1 file, thêm boss mới chỉ cần thêm file
- Thêm `disasters/*.yml`: Mỗi disaster 1 file, thêm disaster mới chỉ cần thêm file
- Action system: Config-driven cho disaster, cho phép custom hành vi hoàn toàn từ file
- Weighted target system trong disaster actions (player, mob, ground, trees,...)
- Condition system cho disaster (only-night, only-day, worlds, weather)
- Boss immunity config (sunlight_burn, fire, poison,...) trong boss file

### Changed
- `config.yml`: Giảm từ 1481 dòng xuống ~721 dòng (bỏ sections boss-events, disasters, safe-zone)
- `ConfigManager.java`: Thêm load từ file rời (`bosses/*.yml`, `disasters/*.yml`, `disasters/_settings.yml`)
- `BossEventManager.java`: Sửa `EntityCombustEvent` để tôn trọng boss immunity

### Removed
- Config section `boss-events` (khoảng 592 dòng) → chuyển sang `bosses/*.yml`
- Config section `disasters` (khoảng 239 dòng) → chuyển sang `disasters/*.yml` + `_settings.yml`
- Config section `disasters.safe-zone` → chuyển vào `disasters/_settings.yml`
- Config section `disasters.messages` → chuyển vào `disasters/_settings.yml`

### Technical Debt
- `BossEventManager.java`: ~760 dòng (giữ nguyên, logic spawn/drop/AI không đổi)
- `DisasterManager.java`: ~1082 dòng (cần refactor action engine nếu disaster mới)
- `ConfigManager.java`: ~530 dòng (thêm ~150 dòng cho external config loader)

### File Stats

#### Deleted from config.yml
| Feature | Issues (lines) removed |
|---------|----------------------|
| boss-events | ~592 dòng (1 section, 9 bosses) |
| disasters | ~239 dòng (1 section, 14 disasters) |
| disasters.safe-zone | ~15 dòng |
| disasters.messages | ~19 dòng |

#### Added to bosses/*.yml
| File | Features/Issues |
|------|----------------|
| `_settings.yml` | General settings (interval, spawn-radius) |
| `wither.yml` | 1 boss + immunity + drops (7 items) |
| `ender_dragon.yml` | 1 boss + immunity + drops (10 items) |
| `ghast.yml` | 1 boss + immunity + drops (9 items) |
| `zombie_boss.yml` | 1 boss + immunity + drops (10 items) |
| `skeleton_boss.yml` | 1 boss + immunity + drops (9 items) |
| `spider_boss.yml` | 1 boss + immunity + drops (8 items) |
| `creeper_boss.yml` | 1 boss + immunity + drops (8 items) |
| `enderman_boss.yml` | 1 boss + immunity + drops (8 items) |
| `witch_boss.yml` | 1 boss + immunity + drops (9 items) |
| `ravager_boss.yml` | 1 boss + immunity + drops (7 items) |
| `vindicator_boss.yml` | 1 boss + immunity + drops (7 items) |
| `phantom_boss.yml` | 1 boss + immunity + drops (8 items) |

#### Added to disasters/*.yml
| File | Features/Issues |
|------|----------------|
| `_settings.yml` | Global settings, safe-zone, messages |
| `blood-moon.yml` | 1 disaster + 3 actions (spawn, potion, actionbar) |
| `meteor.yml` | 1 disaster + 2 actions (lightning, explosion) |
| `mega-storm.yml` | 1 disaster + 4 actions (lightning, damage, potion, weather) |
| `solar-flare.yml` | 1 disaster + 3 actions (damage, fire, potion) |
| `plague.yml` | 1 disaster + 1 action (potion effects) |
| `tornado.yml` | 1 disaster + 1 action (velocity) |
| `eclipse.yml` | 1 disaster + 3 actions (time, potion, spawn) |
| `earthquake.yml` | 1 disaster + 2 actions (potion, falling_block) |
| `inferno-storm.yml` | 1 disaster + 4 actions (damage, fire, place_block, spawn) |
| `soul-eruption.yml` | 1 disaster + 4 actions (damage, potion, explosion, spawn) |
| `lava-geyser.yml` | 1 disaster + 3 actions (damage, fire, place_block) |
| `end-surge.yml` | 1 disaster + 2 actions (spawn, potion) |
| `void-storm.yml` | 1 disaster + 2 actions (damage, potion) |
| `chorus-explosion.yml` | 1 disaster + 2 actions (damage, teleport) |
