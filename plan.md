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

### 3.1 Cast skill có thể mất tài nguyên dù hiệu ứng thất bại — P1

- SkillManager lưu kết quả applySkillEffect vào castSucceeded nhưng vẫn đặt cooldown và báo cast thành công dù hiệu ứng trả về false.
- Đường đi có thể thất bại nếu mục tiêu mất tính hợp lệ sau lần kiểm tra đầu, hoặc AoE không còn mục tiêu lúc áp dụng.
- Hậu quả: người chơi mất mana và cooldown nhưng không nhận hiệu ứng; thông báo cũng sai.
- Hướng sửa: chỉ xác nhận cast, đặt cooldown, xử lý passive và báo thành công khi hiệu ứng được áp dụng. Dùng cùng một snapshot mục tiêu cho validation và execution nếu phù hợp. Nếu lỗi sau khi đã trừ mana thì phải hoàn mana qua thao tác an toàn.
- Bổ sung kiểm thử cho target biến mất, AoE rỗng, mục tiêu bị plugin khác hủy và lỗi DB. Log giới hạn tần suất để tránh spam.

### 3.2 JDBC/SQLite chạy đồng bộ trong nhiều thao tác gameplay — P1/P2

- Mở GUI skill, xác nhận profile, học sách, nâng rank, cast skill và tiêu/hồi mana có truy vấn SQLite trực tiếp từ event hoặc command Bukkit.
- Một thao tác có thể thực hiện nhiều truy vấn liên tiếp. Ổ đĩa chậm, database lock, backup hoặc nhiều người thao tác có thể làm chậm main thread và tụt TPS.
- Gom truy vấn liên quan thành transaction hoặc một lần đọc/ghi; dùng DB queue/executor có giới hạn cho JDBC; chỉ đưa thao tác Bukkit cần thiết về main thread.
- Không gọi Bukkit API từ async; đảm bảo thứ tự thao tác cùng người chơi, không để task cũ ghi đè dữ liệu mới, và không thao tác Player đã offline.
- Đo thời gian truy vấn, giới hạn retry/backoff và cảnh báo khi vượt ngưỡng. Không tạo thread tùy ý cho từng thao tác.

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

- /vncoin hoặc /balance: xem số dư; aliases thống nhất plugin.yml.
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

- [ ] Xác minh schema hiện tại; thêm migration ví/ledger, backup và rollback.
- [ ] EconomyService có transaction nguyên tử/idempotent; chặn balance âm, overflow và duplicate.
- [ ] Drop 30% cho mob allowlist; amount 1–10 theo tier; loại boss/PvP/summon khỏi bảng cơ bản.
- [ ] Item VNCOIN PDC có UUID; pickup ghi có một lần; lỗi DB không xóa item.
- [ ] Thêm /vncoin balance và admin give/take có quyền/audit.
- [ ] Config song ngữ, log và thông báo không spam.
- [ ] Đo drop trên staging, thu nhập/giờ và exploit mob farm.

**Chấp nhận:** tỷ lệ thực nghiệm quanh 30% trên mẫu đủ lớn; drop claim một lần; restart/backup/restore giữ đúng balance/ledger.

### Phase C — Economy sink

- [ ] Merchant NPC với giá cấu hình, transaction nguyên tử và chống double click/disconnect.
- [ ] Thêm sink không bắt buộc như sửa gear/nguyên liệu tiện ích; xác nhận giá trước khi mua.
- [ ] Quest/boss reward có idempotent source ID; không trả trùng encounter.
- [ ] Thêm history/admin audit; leaderboard là tùy chọn.
- [ ] Theo dõi lạm phát và ghi thay đổi giá/drop vào changelog.

**Chấp nhận:** mua bán không tạo/mất tiền khi lỗi; mọi giao dịch có ledger; người chơi hiểu cách kiếm/tiêu.

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
