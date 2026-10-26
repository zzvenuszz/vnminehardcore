# Kế hoạch hoàn thiện VnMineHardcore: RPG, VNCOIN và vận hành server

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
- Shop custom merchant có thêm danh sách buy-order: admin thêm item RPG từ inventory, sửa số lượng item cần giao và VNCOIN trả bằng nút ±1/10/100, xóa và lưu vào `npcs.yml`. System/custom shop mở menu bán đồ; shop hiển thị quote theo item thực trong inventory, ưu tiên exact buy-order, sau đó Material override, cuối cùng giá mặc định. Bán cần xác nhận, lấy item và ghi VNCOIN vào ledger. Item đang là VNCOIN drop hoặc delivery bị khóa không được bán.
- Item entity trên mặt đất hiển thị `[số lượng = VNCOIN]`. Giá mặc định cấu hình tại `economy.merchant.ground-item-default` là 64 item = 1 VNCOIN; `ground-item-price-overrides` cho phép chỉnh theo Material. Exact buy-order cho RPG item được ưu tiên. Sau khi NPC được nạp, tên của item đang nằm trên đất được làm mới.
- Quest editor được bố trí lại theo nhóm: thông tin/điều kiện, mục tiêu/thưởng, dialog, EXP và VNCOIN; các chest GUI trước đây 27 ô được nâng lên 54 ô. Thanh Quick Cast vẫn 9 ô.
- Rủi ro còn cần giải quyết trước khi coi giao dịch bán là crash-safe tuyệt đối: nếu tiến trình server dừng đúng sau khi `saveData()` đã lưu việc lấy item khỏi túi nhưng trước khi DB ghi giao dịch cộng VNCOIN, item có thể chưa được hoàn lại. Lỗi DB khi server còn chạy được hoàn item và ghi log; để khép cửa sổ crash cần transaction intent/outbox có đối soát sau restart.
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
- **Shop custom hiện có thể lấy báo giá từ merchant khác hoặc giá mặc định.** Trong `EconomyManager.openMerchantSellInventory`, nếu không tìm thấy buy-order khớp ở shop đang mở, code gọi `NpcManager.getBestMerchantBuyQuote` (duyệt tất cả merchant), sau đó mới fallback `groundItemSellQuote` (có mặc định 64 item = 1 VNCOIN). Đây là sai phạm vi: custom merchant phải chỉ mua các item được cấu hình tại chính NPC đó.
- **Chưa đủ dữ liệu để khẳng định nguyên nhân duy nhất của lỗi bán lần hai cần đủ 64 item.** Fallback 64=1 phù hợp với triệu chứng khi item không khớp exact buy-order, nhưng cần kiểm tra order trong `npcs.yml`, metadata/PDC của item, số lượng cấu hình và log kết quả `ItemStack.isSimilar`. Phase sửa shop phải có log quote source, NPC/order ID, số lượng yêu cầu/đang có và item identity an toàn; kiểm thử bán lặp trước/sau restart.
- **Giao dịch bán có cửa sổ crash mất item.** Mã lưu inventory sau khi trừ item rồi mới ghi giao dịch ví bất đồng bộ. Lỗi runtime có nhánh hoàn item, nhưng server crash giữa hai bước có thể làm mất item chưa nhận tiền. Cần transaction intent/escrow và reconciliation sau restart trước khi mở rộng thu mua.

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
- Mô hình class cao cấp dạng node `parent-class-id`, điều kiện tiến chức, chỉ số cộng và skill mở mới. Kế thừa skill theo ID; chặn chu trình, parent thiếu, skill trùng và tiến chức lặp.
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

- Tạo `merchant-prices.yml` riêng, giá được biểu diễn là “số lượng giao → VNCOIN trả”, có chú thích VI/EN, bounds và default. Chỉ mua Material được allowlist; item không khai báo/tắt thì không mua, không dùng giá mặc định toàn cục cho mọi item.
- Item RPG chỉ được system merchant mua theo `sell-value` trong định nghĩa item; default `0` nghĩa là không mua. Không lấy giá theo Material nền hoặc lore.
- Thêm `sell-value: 0` vào mọi định nghĩa item RPG (sách, gear, nguyên liệu, boss/quest và item hệ thống). Admin chỉnh config; reload validate và giữ config cũ nếu file lỗi.
- Nếu custom merchant có giá mua tốt hơn cho item chính xác đó, system merchant hỏi xác nhận và nêu NPC/tên/ID, world, lượng giao và X VNCOIN. Yes vẫn bán theo giá system; No quay lại. Chỉ cảnh báo nếu người chơi đủ lượng giao và so sánh cùng item identity/số lượng tương đương.
- Thay giao dịch trừ item rồi mới ghi ví bằng intent/escrow bền vững, transaction ID idempotent và đối soát khi restart; không mất item hoặc cộng coin hai lần.

#### E3. Bảng giá khởi điểm để cân bằng

Các giá sau là đề xuất cấu hình ban đầu, chưa áp dụng. Mỗi hàng là tổng lượng item giao đổi lấy VNCOIN, không phải giá mỗi item.

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

- Chỉ dùng allowlist từng Material, không wildcard bao trùm item chưa khảo sát; item không liệt kê không có quote. Bổ sung giá sau khi rà nguồn farm và vật phẩm thực tế của server.
- **Cần làm rõ riêng về sắt:** yêu cầu mô tả 64 thỏi = 1 coin khi có farm, nhưng 10 thỏi = 1 coin nếu chặn farm; hai mức có vẻ ngược với logic hạn chế lạm phát. Đề xuất không phá cơ chế vanilla, dùng 10 thỏi = 1 coin khi farm còn tồn tại. Nếu thật sự chặn farm thì có thể điều chỉnh giá sau khi đo nguồn cung; không âm thầm sửa cơ chế golem/villager vì có thể ảnh hưởng gameplay khác.
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
- Chưa xác minh bằng compile hoặc gameplay. Phần class advancement (parent/skill inheritance/điều kiện chuyển chức) vẫn chưa triển khai; chưa mở thêm các class trong `classes.md` vì hiện không có skill handler/vertical slice tương ứng. Cần kiểm tra mapping invalid/missing, cấp đúng sách, inventory đầy, restart ở các điểm giao dịch và bảo đảm không tạo sách trùng trước khi coi Phase B hoàn tất.

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

### Thứ tự và tối ưu phạm vi

1. Phase A trước vì đây là cổng vào hành trình.
2. Phase B tiếp theo để người chơi được chọn đúng nghề và nhận đúng sách.
3. Phase C rồi D vì marker phụ thuộc trạng thái quest ổn định.
4. Phase E1 và crash-safe trước khi mở rộng; sau đó system price/config/confirmation.
5. Phase F end-to-end trước phát hành.

Không nên triển khai cùng lúc mọi nghề trong `classes.md`: hoàn thiện một số vertical slice trước rồi mở nhánh tiếp theo. Gắn realm ID tường minh thay vì suy từ tên world để hỗ trợ một đại lục nhiều world. Chưa bật mua system cho item chưa định giá; `0` phải luôn là không mua. Chưa chặn iron farm trước khi có quyết định riêng. Danh sách nghề theo từng race, mapping sách, NPC/tutorial theo đại lục và danh mục giá vanilla đầy đủ cần được chốt từ cấu hình/server data trước khi code các phase tương ứng.
