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
- [ ] Thêm sink không bắt buộc như sửa gear/nguyên liệu tiện ích; xác nhận giá trước khi mua.
- [ ] Thêm encounter/source ID idempotent cho VNCOIN boss payout và reward quest; không trả trùng khi event callback lặp hoặc server restart sát thời điểm boss chết.
- [x] `/vncoin history` và audit admin cơ bản đã có trong Phase B; leaderboard số dư vẫn là tùy chọn.
- [ ] Theo dõi lạm phát và ghi thay đổi giá/drop vào changelog.

**Chấp nhận:** mua bán không tạo/mất tiền khi lỗi; mọi giao dịch có ledger; người chơi hiểu cách kiếm/tiêu.

**Cập nhật triển khai 2026-10-25:** đã thêm merchant hệ thống bán danh mục chung có phân trang và merchant tùy chỉnh theo NPC. Admin có thể tạo `merchant`, mở `/vnnpc edit <id>` hoặc Shift-click NPC, chuyển item có PDC plugin vào bản nháp, chỉnh giá bằng nút ±1/10/100, gỡ món và lưu item/giá nguyên bản trong `npcs.yml`; cấu hình giới hạn số món/giá/mức bước. Boss drop được đánh dấu PDC RPG để có thể đưa vào shop; coin boss dùng ID drop xác định từ UUID encounter và vị trí drop để ledger từ chối claim trùng khi callback thưởng cùng encounter chạy lặp. Tiếp tục Phase C đã thêm bảng outbox giao vật phẩm (schema 8), trừ VNCOIN và ghi item chờ giao trong cùng transaction SQLite, đối soát lúc đăng nhập hoặc mở `/vncoin`, gắn transaction ID vào item để tránh nhân đôi, khóa item cho tới khi SQLite xác nhận, và lưu player data trước khi acknowledge. Nếu túi đầy, delivery vẫn chờ thay vì hoàn tiền không chắc chắn. JAR 2.6.0 đã được build và cài; native SQLite chỉ có Linux x86_64. Thêm `/vncoin stats` (cửa sổ ngày cấu hình 1-365, mặc định 30) tổng hợp phát hành/chi tiêu/net và tối đa 20 nguồn giao dịch; compile Java 25 thành công. Bản 2.7.0 đang được chuẩn bị. Server chưa được restart và chưa chạy gameplay/migration test, nên merchant còn mở để xác minh disconnect/crash, túi đầy, migration DB và khôi phục; thống kê cần kiểm tra số liệu trên ledger server thật. Sink VNCOIN và thưởng quest idempotent chưa triển khai.

### Phase D — Class và trải nghiệm người chơi

- [ ] Phân loại class available/locked/planned; ẩn class chưa playable.
- [ ] Hoàn thành từng class gồm skill, book, passive, UI, tutorial, balance và test.
- [ ] Tutorial hướng dẫn nhặt/dùng VNCOIN, merchant, portal và boss.
- [ ] Quest journal lưu progress, claim một lần và waypoint.
- [ ] Party cơ bản định nghĩa rõ chia EXP/loot; không nhân đôi VNCOIN.
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
