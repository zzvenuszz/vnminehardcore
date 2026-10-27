# VnMineHardcore

Plugin tăng độ khó sinh tồn cho Minecraft Paper 26.3. Người chơi không bị ban khi chết.

Yêu cầu runtime: Java 25+ và ProtocolLib `5.5.0-SNAPSHOT` dev-build có hỗ trợ Minecraft 26.3 ([official builds](https://github.com/dmulloy2/ProtocolLib/releases/tag/dev-build)). Plugin không tải hoặc đóng gói ProtocolLib thay server.

## Tính năng chính

- ☠ **Tử vong**: lưu thống kê, broadcast và phát âm thanh; không ban người chơi
- 🤝 **Lính đánh thuê**: Shift + chuột phải vào lính của bạn để mở kho trang bị ngay lập tức
- ⚔ **Sát thương nhân hệ số** (quái, ngã, lửa, nổ, PvP...)
- 🍔 **Hệ thống đói** (hao hụt nhanh, ngộ độc thức ăn sống, nghẹn khi ăn nhanh)
- 💧 **Hệ thống khát nước** (uống từ nguồn, chai, xô; nước tự nhiên gây độc)
- 🌍 **Môi trường khắc nghiệt** (nóng/lạnh/mưa axit theo biome, sương mù, chặn ngủ, chóng mặt, sợ hầm, giới hạn mưa)
- 🌋 **Thiên tai tự động** (Blood Moon, Meteor, Storm, Solar Flare, Plague, Tornado, Earthquake, Eclipse và các thiên tai theo dimension)
- ☀️🌙 **Điều khiển ngày/đêm** (cấu hình riêng thời lượng ban ngày và ban đêm)
- ⛏️💥 **Kiểm soát đào quặng** (thay vật phẩm rơi, gãy cúp và nổ theo block/cúp)
- 🏷 **Đổi tên hiển thị sau khi chết** (tùy chỉnh format trong config)

## Lệnh

| Lệnh | Mô tả | Quyền |
|------|-------|-------|
| `/vnstats` | Xem thống kê cá nhân (chỉ người chơi) | `vnmine.command.stats` |
| `/vnhardcore` | Xem trạng thái plugin | `vnmine.command.status` |
| `/vn death reset <player>` | Reset số lần chết của player về 0 | `vnmine.command.death.reset` |
| `/vnevent [<id> <warning-giây> <duration-giây>\|stop]` | Xem, gọi hoặc dừng thiên tai | `vnmine.command.disasters` |
| `/vnboss <id> <level> <warning-giây> <duration-giây>` | Gọi boss theo level | `vnmine.command.bosses` |
| `/vnboss kill` | Kết thúc boss đang hoạt động, không điểm và không loot | `vnmine.command.bosses` |
| `/reward` | Nhận phần thưởng xếp hạng boss đang chờ (cần chỗ trống trong inventory) | `vnmine.command.reward` |
| `/scoreboard reset` | Xóa điểm mùa và phần thưởng chờ (bí danh: `/vnscoreboard`, `/sb`) | `vnmine.command.scoreboard.reset` |
| `/vntime set <day\|night> <phút> [world]` | Đặt thời lượng ngày/đêm toàn cục hoặc riêng cho world đang tải | `vnmine.command.time.manage` |
| `/vnday set <ngày>` | Đặt world hiện tại về bình minh của ngày trong game tương ứng (chỉ người chơi) | `vnmine.command.day.manage` |
| `/tp <user> <world>` | Đưa người chơi đang trực tuyến đến điểm spawn của world | `vnmine.command.teleport.other` |
| `/vnspawn set\|go\|protect\|list\|remove` | Quản lý spawn và vùng bảo vệ | `vnmine.command.spawn.manage` |
| `/vnrespawn` | Chọn hồi sinh khi đang chờ sau khi chết | `vnmine.command.respawn` |
| `/vnleave` | Thoát máy chủ khi đang chờ sau khi chết | `vnmine.command.leave` |
| `/vndeathhistory [player]` | Mở lịch sử chết của người chơi online; admin có thể chọn snapshot và khôi phục sau bước xác nhận | `vnmine.command.death.history` |
| `/quatanthu` | Xin túi quà tân thủ nếu chưa nhận trước đây và chưa sở hữu lính đánh thuê | Không yêu cầu quyền riêng |
| `/fly`, `/nodame`, `/spawn` | Tiện ích quản trị dành cho người chơi | `vnmine.command.fly`, `.nodame`, `.spawn` |
| `/gm0`, `/gm1`, `/gm2`, `/gm3` | Chuyển game mode (chỉ người chơi) | `vnmine.command.gamemode` |
| `/vnreload` | Tải lại config và các hệ thống | `vnmine.command.reload` |
| `/vnhelp`, `/vn help` | Hiển thị lệnh theo quyền người dùng; admin/OP thấy thêm lệnh quản trị | `vnmine.command.help`, `.utility` |
| `/vnmenu` hoặc phím `F` | Mở menu chức năng; phím F thay thao tác đổi tay phụ | Không yêu cầu quyền riêng |
| `/vngroup ...` | Tạo/xóa nhóm, xem nhóm, cấp/thu hồi quyền nhóm | `vnmine.command.groups.manage` |
| `/vnuser ...` | Xem/gán nhóm, cấp/thu hồi quyền cá nhân, reset người chơi | `vnmine.command.users.manage` |
| `/vnarmy ...` | Thuê/quản lý lính, đổi chế độ AI và trang bị | `vnmine.command.army` |
| `/vnarmy item give ...` | Admin cấp một món hoặc trọn set gear cho lính | `vnmine.command.army.admin` |
| `/vncoin [balance\|history [player]\|give\|take ...]` | Xem ví/lịch sử; quản trị viên có thể cộng hoặc trừ VNCOIN | `vnmine.command.vncoin.admin` cho give/take; xem lịch sử người khác cần `vnmine.command.vncoin.history.others` |

Ví dụ gọi boss Wither level 3, cảnh báo 30 giây, tồn tại 120 giây:
`/vnboss wither 3 30 120`.
Các lệnh có tham số hỗ trợ nhấn Tab để gợi ý ID, tên người chơi, world, ID
bảo vệ và các giá trị phổ biến cho từng vị trí tham số. `/vnspawn protect X`
dùng bán kính ngang X/Z tính từ block hiện tại và bảo vệ ở mọi độ cao; dữ liệu
được lưu trong `plugins/VnMineHardcore/spawns-protection.yml`.

### VNCOIN và cửa hàng NPC

Người chơi xem số dư bằng `/vncoin` hoặc mở ví từ menu. Có hai loại NPC bán hàng:

- Merchant hệ thống tạo bằng `/vnnpc create <id> system-merchant`; bán toàn bộ
  mặt hàng trong danh mục chung `economy.merchant.offers` của `config.yml`
  (tối đa 1.000 món, phân trang 44 món).
- Merchant tùy chỉnh tạo bằng `/vnnpc create <id> merchant`. Admin dùng
  `/vnnpc edit <id>` hoặc Shift + chuột phải vào NPC để mở GUI chỉnh shop.
  Shift-click vật phẩm RPG có PDC của plugin từ túi để thêm; chuột trái vào món
  trong shop để mở bảng chỉnh giá; chuột phải để gỡ món; nút **Lưu cửa hàng** ghi
  item và giá vào `npcs.yml`. Khi thêm, stack vật phẩm được chuyển khỏi túi admin
  vào bản nháp của shop; chỉ bấm lưu mới ghi thay đổi xuống file.

GUI chỉnh giá có các nút `-1/+1`, `-10/+10`, `-100/+100` VNCOIN; mức bước và giá
tối đa tùy chỉnh tại `economy.merchant.custom.price-steps` và `max-price` trong
`config.yml`. Giới hạn số món mỗi shop ở `max-offers` (mặc định 45). Tắt shop
chung bằng `economy.merchant.enabled: false`; chỉ tắt merchant tùy chỉnh bằng
`economy.merchant.custom.enabled: false`. Người chơi không cần permission riêng
để mua; quản trị merchant cần `vnmine.command.npc.admin`.

Mua hàng yêu cầu một ô trống trong túi; mỗi người chỉ có một giao dịch đang xử lý.
Giao dịch ghi vào wallet ledger và nếu plugin phát hiện không thể giao item thì
thực hiện giao dịch hoàn tiền riêng. Crash ngay giữa lúc trừ tiền và giao item
vẫn cần kiểm chứng và sẽ được xử lý bằng hộp thư giao vật phẩm bền vững ở bước
hoàn thiện Phase C.

### Nhóm và quyền (`permission.yml`)

Mặc định, nhóm `user` chỉ có các lệnh cơ bản; nhóm `admin` có toàn bộ quyền
plugin. Dùng `/vngroup list`, `/vngroup info <group>`,
`/vngroup create <group>`, `/vngroup delete <group>` và
`/vngroup permission <add|remove> <group> <permission>` để quản lý nhóm.
Dùng `/vnuser info <player>`, `/vnuser group <player> <group>`,
`/vnuser permission <add|remove> <player> <permission>` hoặc
`/vnuser reset <player>` để quản lý người chơi. Người chơi phải online hoặc đã
có bản ghi trong file để được chọn; tên UUID ổn định, còn tên chỉ dùng để tra cứu.

Các permission node được tách theo chức năng và liệt kê trong `plugin.yml`.
Ví dụ: `vnmine.command.disasters`, `vnmine.command.spawn.manage`,
`vnmine.command.users.manage`; node bypass vùng bảo vệ là
`vnmine.spawnprotection.bypass`. OP và console luôn có toàn quyền.
`vnmine.command.army` mặc định cấp cho người chơi; quyền
`vnmine.command.army.admin` mặc định chỉ dành cho OP để cấp/xóa và xem quân đội
của người khác. `F` mở menu chính thay thao tác đổi tay phụ; cấu hình tại
`menus.main` và `menus.admin` trong `config.yml`.
Menu admin có các thao tác trực tiếp cho quân đội, thiên tai/boss, thời gian/ngày,
spawn protection, teleport, tiện ích người chơi, nhóm/quyền và lịch sử chết.
Khôi phục inventory và xóa lính qua GUI đều có bước xác nhận.
Sau khi chỉnh `permission.yml` thủ công, chạy `/vnreload`.
Nhóm/quyền không hỗ trợ wildcard, phủ định hoặc kế thừa; quyền nhóm và quyền
cá nhân được cộng lại, vì vậy xóa quyền cá nhân không thể phủ định quyền được
cấp bởi nhóm. Nhóm mặc định áp dụng cho người mới và `/vnuser reset`; đổi
`default-group` không tự chuyển người đã có nhóm được lưu.

### Lính đánh thuê (`armies.yml`)

Trong hồ sơ quản trị người chơi, chọn **Give item RPG** để mở danh mục theo
nhóm: vật phẩm người chơi (quest, skill, boss, shop), trang bị lính hoặc lệnh
triệu hồi. Sách kỹ năng được lọc theo Chủng tộc → Class → sách; vật phẩm boss
chỉ hiện các item đặc trưng/hiếm, bỏ nguyên liệu phổ thông và item trùng hoàn
toàn. Lệnh triệu hồi đi theo Class → Bậc và dùng icon giống menu thuê lính.
Trang bị lính đi theo Class → vật liệu → sao → set → món; tên và icon class/vật
liệu lấy từ cấu hình quân đội, còn icon từng món là chính item gear được tạo.
Chọn món để cấp riêng hoặc **Cấp nguyên set**. Luồng này hỗ trợ người nhận
offline và xếp các món tràn inventory vào kho nhận bền vững.

Trang bị lính có đủ sáu bậc vật liệu (da, đồng, sắt, vàng, kim cương, netherite)
và 1–5 sao. Vật liệu không tồn tại cho kiểu vũ khí được giữ nguyên, còn sức mạnh
được tăng theo metadata vật liệu/sao. Cấu hình `gear-system.item-materials`
trong `armies.yml` chọn chất liệu riêng theo class. Tay phụ chỉ được tạo nếu class
có món hợp lệ; mặc định không còn tự gán khiên cho mọi lớp. Xạ Thủ dùng cung và
bùa may mắn; Sát Thủ/Chiến binh song kiếm/song rìu; Tu sĩ rừng dùng đinh ba và
sách phép; Pháp sư băng dùng cầu tuyết và sách băng; Vệ Binh/Kỵ sĩ dùng kiếm/giáo
và khiên; Y Sư, Pháp sư lửa và Trinh Sát có bộ tay phụ hỗ trợ theo vai trò.

Tên gear ghi rõ vai trò, chẳng hạn **Giáo Kỵ Sĩ**, **Sách Y Thuật**, **Sách Băng
Thuật** và **Sách Hỏa Thuật**. Kỵ sĩ dùng spear material riêng (`IRON_SPEAR` và
các bậc spear tương ứng), không dùng trident. Sát Thủ và Chiến binh có một mẫu
kiếm/rìu dùng được ở tay chính hoặc tay phụ; trang bị hai bản vẫn cộng chỉ số.
Tooltip sách Tu Sĩ Rừng hiển thị số Vex tối đa được cộng theo vật liệu/sao; sách
Băng ghi % sát thương, % hồi chiêu và số giây làm chậm cộng thêm cho Bão Tuyết,
Băng Nộ, Tường Băng; sách Y Thuật ghi % lượng hồi máu và % hồi chiêu; sách Hỏa
Thuật ghi % sát thương lửa và số giây cháy cộng thêm cho Hỏa Cầu/Thiêu Đốt.

Trinh Sát tự dùng **Soi Sáng** khi ánh sáng quanh lính thấp, kể cả khi không có
mục tiêu. Mũi tên rơi theo vòng quanh vị trí lính; đuốc do kỹ năng tạo cách nhau
ít nhất 3 block theo cấu hình `minimum-torch-spacing-blocks`.

`plugins/VnMineHardcore/armies.yml` là file tinh chỉnh AI và cấu hình lính: giới
hạn sở hữu đội quân (mặc định 10), giới hạn triệu hồi đồng thời (mặc định 3),
tầm tìm mục tiêu, chu kỳ AI, chỉ số, kỹ năng, EXP,
thời gian hồi phục, vũ khí ban đầu và vật phẩm nâng bậc. Mười class mặc định:
Xạ Thủ (`archer`), Vệ Binh (`guardian`), Y Sư (`medic`), Cuồng Chiến
(`berserker`), Trinh Sát (`ranger`), Thánh Kỵ (`paladin`), Pháp Sư Lửa
(`pyromancer`), Tu sĩ rừng (`druid`), Sát Thủ (`assassin`) và Pháp Sư Băng
(`frost_mage`); mỗi class có bộ skill được cấu hình riêng. Lính tự chọn vật phẩm
tay chính/tay phụ theo danh sách ưu tiên từng class trong `armies.yml`, rồi dùng
vũ khí mặc định nếu không tìm thấy món phù hợp; trang bị được giữ qua nghỉ, chết
và triệu hồi lại. Vệ Binh dùng model Husk để hiển thị item ở cả hai tay và tìm
khiên; Cuồng Chiến có thể lấy rìu thứ hai cho tay phụ. Loadout lính thường vẫn
được điều khiển bởi `starting-weapon`, `starting-offhand` và danh sách ưu tiên;
ví dụ Tu sĩ rừng hiện ưu tiên emerald. Y Sư ưu tiên potion hồi máu ở tay chính
và có thể ném splash/lingering
potion có lợi từ tay phụ cho đồng đội cần hiệu ứng/hồi máu mà không tiêu hao bình.
Gear GUI dùng ô riêng cho tay chính và tay phụ. Mô tả skill và action bar dùng
tên tiếng Việt.
AI dùng danh sách ưu tiên theo từng chế độ trong `ai.priorities`; mặc định chỉ
chọn và tấn công mục tiêu trong tầm nhìn (`ai.require-line-of-sight`); kỹ năng
đánh mục tiêu kiểm tra lại tầm nhìn ngay lúc thi triển để tránh gây sát thương/
hiệu ứng xuyên vật cản. Lính không chọn dân làng/thực thể thụ động. Khi gọi toàn đội,
lính bậc cao nhất rồi level
cao nhất được ưu tiên cho tối đa 3 vị trí triển khai; giới hạn sở hữu và triển
khai đều chỉnh được trong `armies.yml`.
Xạ Thủ có `VOLLEY` (số tên, tốc độ, độ tỏa, sát thương); Trinh Sát dùng nỏ,
phát hiện mục tiêu trong 48 block và bắn thường xa 32 block theo mặc định
(gấp đôi Xạ Thủ), tiêu thụ đạn trong kho nếu có và dùng Spectral Arrow miễn phí
khi hết đạn. Skill `VOLLEY` và `ILLUMINATION` có tầm 20.8 block (hơn 30% so với
tầm bắn 16 block của Xạ Thủ). Sát thương cơ bản và sát thương tên của Trinh Sát
bằng 70% Xạ Thủ theo cấu hình mặc định. Skill `ILLUMINATION` bắn loạt tên lửa
lên trời, tạo đuốc không thể phá trong thời hạn quanh mục tiêu (mặc định 30
giây); chỉ dùng khi trời tối hoặc ánh sáng tại Trinh Sát không vượt quá mức
thấp cấu hình (mặc định 7). Số tên tăng thêm mỗi 5 level. Có thể chỉnh điều
kiện qua `classes.ranger.skill-settings.ILLUMINATION.darkness-only` và
`low-light-threshold`. Các thông số nằm trong
`classes.ranger` và `classes.ranger.skill-settings.ILLUMINATION`. Vệ Binh
(`guardian`) dùng Husk và có sáu kỹ năng tanker tự kích hoạt: `ATTACK` cộng
damage bằng 5% HP tối đa ở mỗi đòn thứ ba và làm chậm quái phía trước;
`GUARDIAN_LINK` (**Hộ Vệ**) là nội tại: Vệ Binh nhận mặc định 40% sát thương
thay cho chủ nhân trong 5 block; `GUARDIAN_CHARGE` (**Xung Phong**) tự lao
4–8 block và hất lùi, cự ly tối đa tăng 1 block mỗi 5 Level; `GUARDIAN_SHIELD`
(**Thánh Thể**) cấp lớp máu hấp thụ sát thương cho Vệ Binh, chủ và đồng đội
cùng Bukkit Scoreboard Team/lính cùng đội quân, đồng thời giảm sát thương nhận
vào. `GUARDIAN_QUAKE` (**Vây Hãm**) tạo vòng block giả vây mục tiêu trong 5 giây
và làm chậm; `GUARDIAN_ULTIMATE` (**Hộ Giá**) tự kích hoạt khi HP chủ còn 50%
trở xuống để dịch chuyển về bảo vệ, hất lùi/khiêu khích quái và miễn damage cho
chủ trong 3 giây. Block của Vây Hãm chỉ hiển thị phía client, không sửa địa hình
thật; hiệu ứng làm chậm được áp dụng phía server.

Chỉ số Vệ Binh tách riêng theo Bậc và Level: mặc định mỗi Bậc thêm 6 HP, 0.5
giáp và 0.75 damage; mỗi Level thêm 5 HP và 0.75 damage, cứ 5 Level thêm 0.5
giáp. Cầm vũ khí tay chính tăng 10% damage tổng; cầm khiên tay phụ thêm 8 HP
và tăng lượng lớp máu hấp thụ của `GUARDIAN_SHIELD`. Mô tả lính hiển thị chỉ số
tổng cùng các thành phần Bậc/Level/trang bị bằng màu riêng. Tinh chỉnh chỉ số trong
`classes.guardian`; thiết lập bán kính, thời lượng, cooldown và hiệu ứng từng
skill trong `classes.guardian.skill-settings.<SKILL>`. Thuê qua menu hoặc
`/vnarmy hire guardian <bậc>`; dùng quyền hiện có `vnmine.command.army`, không
có permission mới. Y Sư có `CLEANSE_OWNER` (cooldown và danh sách hiệu ứng được
gỡ). Các thông số skill khác nằm trong
`classes.<class>.skill-settings.<SKILL>` và có thể chỉnh riêng.
Pháp Sư Băng dùng snowball bay thẳng làm đòn đánh cơ bản và tám skill: `ICE_SPIKE`,
`BLIZZARD`, `FROST_NOVA`, `ICE_WALL`, `FROST_SHIELD`, `FROST_STEP`,
`FROST_WALKER`, `ICE_AGE`. Tùy chỉnh tại `classes.frost_mage.basic-attack`,
`classes.frost_mage.skill-settings` và `classes.frost_mage.level-scaling` trong
`armies.yml`. Mặc định sát thương snowball/gai băng là 3.0, cộng 0.1 mỗi level;
cooldown giảm 0.1 giây mỗi level (tối thiểu 1 giây), tầm tăng 1 block tại mỗi
mốc 5 level. Blizzard gây sát thương và Slowness theo nhịp; Băng Ngục
(`ICE_WALL`) dựng lồng PACKED_ICE 3×3, cao 2 block quanh mục tiêu, gây 1 sát
thương mỗi giây (+0.1 mỗi level) khi mục tiêu còn bên trong và giảm 50% tốc độ
sau khi thoát. Thời gian lồng và làm chậm mặc định 3 giây, tăng 0.1 giây mỗi
level. Frost Nova và Frost Walker tiếp tục dùng băng tạm. Ice Age chỉ đóng
băng/làm chậm mục tiêu và tạo particle, không đổi địa hình. Frost Step chỉ
lướt khi đường phía trước không bị block che.
Pháp Sư Lửa (`pyromancer`) dùng sáu skill `FLAME_BOLT`, `IGNITE`, `FLAME_NOVA`,
`FIRE_WALL`, `METEOR_STRIKE`, `PHOENIX_REBIRTH`; cấu hình tại
`classes.pyromancer.skill-settings` trong `armies.yml`. Hỏa Cầu gây sát thương
và giảm tốc độ đúng 20% trong 3 giây; Thiêu Đốt gây DoT mỗi 2 giây trong 8 giây
và lây có giới hạn; Bão Lửa (`FLAME_NOVA`) tạo hình nón lửa 3D xoay ngược chiều
kim đồng hồ quanh pháp sư, với bán kính đáy 5 block và chiều cao 3 block ở level 1.
Bán kính tăng 1 block mỗi 5 level và chiều cao tăng thêm 0.5 block cho mỗi block
bán kính tăng; khi lên cao, bán kính nón giảm 1 block mỗi block. Skill tạo 10 hạt
lửa ở level 1 (cộng 1 hạt mỗi level) và gây 1 sát thương mỗi giây trong 3 giây
lên mọi mục tiêu hợp lệ trong thể tích nón (sát thương/thời gian tăng 0.1 mỗi
level); Hỏa Ngục (`FIRE_WALL`) dựng lồng
MAGMA_BLOCK 3×3, cao 2 block quanh mục tiêu; Mưa Lửa (`METEOR_STRIKE`) đánh dấu
vòng lửa đỏ 2 giây rồi thả 3 fireball lần lượt theo thứ tự ngẫu nhiên, tăng 1
quả mỗi 3 level và tăng bán kính khu vực 1 block mỗi 5 level. Fireball gây
damage cấu hình nhưng không phá block. Phượng Hoàng tự kích
hoạt khi máu còn 65% trở xuống: hồi 30% máu tối đa, miễn sát thương 2 giây,
rồi lao vào mục tiêu gây sát thương AoE và đốt. Damage, cooldown, tầm, kích
thước vùng, mục tiêu tối đa, delay, số lượng/tốc độ fireball và thời lượng đều
chỉnh riêng theo từng skill; cooldown mặc định lần lượt là 2/6/10/12/25/90 giây.
Pháp Sư Lửa và Pháp Sư Băng không ném potion; Pháp Sư Băng dùng snowball, còn
Pháp Sư Lửa cầm Fire Charge để phóng Fireball và ưu tiên Fire Charge trong kho.
Các lồng hoàn nguyên block sau khi hết hạn, khi lính bị thu hồi/hạ hoặc khi
plugin reload/stop; các skill chỉ gây sát thương lên mục tiêu hợp lệ theo luật
PvP/đồng minh hiện hành. Lính có vũ khí hợp lệ ở tay chính gây thêm 30% damage;
chỉnh hệ số tại `combat.main-hand-damage-multiplier` trong `armies.yml`.
Mặc định lính và VEX không gây sát thương lên người chơi hoặc quân của người
chơi khác chỉ vì phát hiện họ hay bị họ tấn công. Ngoại lệ: sau khi chủ nhân
trực tiếp đánh đúng mục tiêu, lính có thể tấn công mục tiêu đó trong 15 giây.
Đặt `pvp.allow-owner-commanded-targets: false` trong `armies.yml` để chặn hoàn
toàn sát thương PvP từ lính và VEX.
Scaling skill chỉnh tại `progression.skill-power-per-level`,
`progression.max-skill-power-multiplier` và
`progression.skill-cooldown-reduction-at-max-level-seconds`.
Cuồng Chiến thiên về áp sát/khiêu khích; Trinh Sát bắn loạt và hỗ trợ chủ;
Thánh Kỵ đỡ đòn; Pháp Sư Lửa gây sát thương/đốt, khống chế và có ultimate hồi sinh;
Tu sĩ rừng khống chế,
hồi máu, thanh tẩy; Sát Thủ hút máu và cuồng nộ khi nguy cấp; Pháp Sư Băng
bắn snowball và dùng tám kỹ năng băng. Dùng ID class trong lệnh
`/vnarmy hire <loại> <bậc>` hoặc menu GUI.
Tu sĩ rừng gọi tối đa một VEX mỗi lần cast; cứ mỗi 5 level tăng sức chứa thêm
1 VEX, bắt đầu từ 1 ở level 1 (mặc định tối đa 7 ở level 30). Tùy chỉnh giới
hạn và thứ tự ưu tiên AI tại `classes.druid.skill-settings.SUMMON_VEXES` trong
`armies.yml`. VEX ưu tiên mục tiêu Tu sĩ rừng đang đánh, kẻ tấn công Tu sĩ rừng,
kẻ tấn công chủ nhân, rồi đi theo Tu sĩ rừng. Chủ nhân thấy thanh HP riêng của từng VEX dạng ô đầy/rỗng, không kèm số HP;
thanh màu xanh lá có biểu tượng đổi theo trạng thái tấn công, theo Tu sĩ hoặc
đứng yên. Khi không có mục tiêu để đánh, VEX quay về và được giữ trong phạm vi
5 block quanh Tu sĩ rừng bằng cách bay theo từ từ (`follow-radius-blocks`); khi cho Tu sĩ rừng nghỉ, toàn
bộ VEX do lính đó triệu hồi cũng bị xóa. Màu và biểu tượng tùy chỉnh tại `status-display.vex-health-color` và
`status-display.vex-action-icons` trong `armies.yml`. Kỵ sĩ tự lên ngựa plugin quản lý;
slot gear riêng lưu giáp ngựa tốt nhất và
`classes.paladin.horse-health`/`classes.paladin.horse-speed` cấu hình máu/tốc độ
ngựa (tốc độ mặc định bằng mức phi nước đại tối đa). Khi ngựa chết, Kỵ sĩ tiếp
tục đi bộ, ngựa không tự hồi sinh kể cả sau khi khởi động lại; cho Kỵ sĩ nghỉ
rồi xuất chiến lại để tạo ngựa thay thế. Lính truy đuổi mục tiêu chủ nhân vừa
đánh khi mục tiêu còn trong tầm nhìn; lệnh tấn công trực tiếp hết hiệu lực sau
15 giây nếu không được làm mới. Kỵ sĩ được gọi lại riêng lẻ vẫn tự tạo và gắn
ngựa plugin quản lý.
Token phải khớp class và bậc, rồi bị tiêu thụ khi thuê. Token boss có class riêng
qua `army-token-class` trong từng `bosses/<id>.yml`; bậc token bằng level boss.
Token chung từ lệnh admin vẫn dùng được cho mọi class.

```text
/vnarmy gui
/vnarmy list [user]                             # user khác cần quyền admin
/vnarmy hire <loại> <bậc>
/vnarmy recall [ID]
/vnarmy rest [ID]
/vnarmy rename <ID> <Tên mới>
/vnarmy attack | defend | protect
/vnarmy gear <ID>
/vnarmy mute <ID>
/vnarmy remove <ID>
/vnarmy remove <user> <ID>                     # admin
/vnarmy clear <user>                           # admin
/vnarmy give <người chơi> token <bậc>              # admin
/vnarmy give <người chơi> army <loại> <bậc>        # admin
/vnarmy token give <người chơi> <loại> <bậc>       # token theo lớp, tương thích cũ
```

Hồ sơ và trang bị được lưu trong `plugins/VnMineHardcore/mercenaries.yml`.
Lính đang triển khai được gọi lại khi chủ nhân vào game; khi nghỉ, thoát game
hoặc server dừng, entity được thu hồi nhưng hồ sơ/trang bị vẫn giữ. `recall`
đối soát UUID và PersistentData tag trước khi tạo entity thay thế để hạn chế
nhân bản. AI ưu tiên hành động theo `ai.priorities` trong `armies.yml`: ATTACK
ưu tiên mục tiêu chủ đã đánh, mối đe dọa lên chủ, hostile gần, tìm kiếm, quay về
chủ và tuần tra; DEFEND ưu tiên hostile gần, tìm kiếm và tuần tra; PROTECT ưu
tiên áp sát chủ, đánh mob đang nhắm chủ, trả đũa và tìm hostile gần. Thứ tự có
thể chỉnh; mặc định chỉ chọn mục tiêu nhìn thấy, và không chủ động chọn dân làng,
thực thể thụ động, người chơi, lính hoặc đệ của chủ khác.

GUI cho phép chọn lớp và bậc trước khi thuê; tooltip nêu mô tả skill tiếng Việt,
thông số/cooldown, yêu cầu XP/vật liệu, loại vũ khí/vật phẩm ở tay chính và tay
phụ, cùng sát thương tổng và từng thành phần. Công thức dùng màu trắng cho sát
thương cơ bản, xanh dương cho phần từ vũ khí, đỏ cho phần tăng theo cấp và xanh
lá cho hệ số phần trăm; sát thương hiển thị là trước khi trừ giáp/kháng của mục tiêu.
Thanh trạng thái hiển thị ba dòng chỉ dành cho chủ nhân: vạch HP,
`❤: HP hiện tại/tối đa ✦ EXP: hiện tại/yêu cầu`, rồi
`[Tên chủ] Tên lính [Bậc X] [Level: Y]`; người chơi khác vẫn
chỉ thấy tên lính. Bật/tắt tại `status-display.enabled`, độ dài vạch HP chỉnh tại
`status-display.bar-length`; `status-display.see-through` điều khiển khả năng nhìn xuyên tường.
`experience-orb-pickup.enabled` và
`experience-orb-pickup.radius-blocks` điều khiển việc thu EXP cho chủ.
Menu đội quân mở chi tiết từng lính, đổi chế
độ, mute, xóa, cho nghỉ/triệu hồi và mở kho 36 ô cùng trang bị. Vũ khí và giáp
được tự nhận diện; vũ khí/giáp của lính không mất độ bền, cung dùng vô hạn loại
mũi tên đặt trong kho và thức ăn được tiêu thụ để hồi máu. Lính lên cấp nhờ hạ
quái hoặc tham gia hạ mục tiêu cùng chủ/đồng đội; chỉ số và sức mạnh skill tăng
theo cấu hình. Sức mạnh skill tăng tuyến tính (mặc định tối đa 2.5 lần), cooldown
giảm đều tối đa 3 giây ở cấp 30. Lính nhặt EXP orb gần nhất cho chủ online; orb
không đủ điều kiện vẫn tồn tại bình thường. Level tối đa là 30. Đạt cấp 30 có thể nhấn **Nâng bậc** trong GUI
chi tiết; mỗi bậc nâng tiêu thụ vật phẩm khai báo ở
`classes.<class>.tier-upgrades.<bậc-đích>.materials`, tối đa thêm 3 bậc so với
bậc gốc khi thuê.

Lính bị hạ sẽ không mất hồ sơ/trang bị mà chuyển sang **Hồi phục** (mặc định
300 giây); `/vnarmy list` hiển thị Xuất chiến/Nghỉ ngơi/Hồi phục cùng bậc, cấp,
EXP hiện tại/yêu cầu và tên riêng. `/vnarmy rename <ID> <Tên mới>` đổi tên.
`recall` và `rest` tác động cả đội; thêm ID để chỉ gọi/nghỉ một lính. Gọi cả
đội chỉ triển khai tối đa số lính cấu hình và ưu tiên bậc cao, sau đó level cao.
Lệnh thuê liệt kê rõ token, cấp kinh nghiệm và từng vật phẩm đang thiếu.

Token boss có class theo `army-token-class` trong file boss và bậc bằng level
boss, rơi với xác suất mặc định 30% (`bosses/_settings.yml`, khóa
`army-token-drop.chance`) khi có người chơi gây sát thương boss. EXP boss là
một quỹ chung, mặc định 50 EXP ở level 1 và 150 EXP ở level 10, nội suy tuyến
tính giữa hai mốc; quỹ chia theo tỷ lệ sát thương giữa những người chơi đã gây
sát thương. Có thể chỉnh hai mốc tại `experience-pool.level-1` và
`experience-pool.level-10` trong `bosses/_settings.yml`. Điểm mùa cũng được
chia theo tỷ lệ sát thương giữa tất cả người đóng góp; ngưỡng sát thương chỉ
còn áp dụng cho vật phẩm boss. Pool EXP tiến trình lính mỗi kill (mặc định 5,
`progression.experience-per-kill`) được chia đều giữa các lính đang triển khai
của chủ; phần dư do chia số nguyên được cộng lần lượt theo ID ổn định. Chủ nhân
nhận EXP rơi của mob theo vanilla; khi lính gây kill, EXP được cấp trực tiếp
cho chủ để không phụ thuộc khoảng cách nhặt orb.
`give ... army` cấp lính trực tiếp và vẫn tôn trọng giới hạn quân số;
`give ... token` cấp token chung bậc.

Gear lính đánh thuê có class, vật liệu (leather, copper, iron, gold, diamond,
netherite), 1-5 sao, set và slot riêng; chỉ mercenary có thể trang bị. Chỉ số
được tính từ `classes.<class>.gear-stats`, hệ số vật liệu/sao, bonus set đủ
2/4/6 món và giới hạn trong `gear-system`. Người chơi có quyền army mở GUI gear
boss bằng `/vnarmy item give`; admin có thể cấp trực tiếp bằng
`/vnarmy item give <player> <class> <material> <stars> <set> <slot|set>`.
Slot hợp lệ: `helmet`, `chestplate`, `leggings`, `boots`, `mainhand`, `offhand`
hoặc `set`. Quyền cấp gear dùng node admin hiện có
`vnmine.command.army.admin`.

Tên hiển thị trên entity dùng tiền tố `[Tên chủ]` xanh lá sáng, đậm với chủ;
người chơi khác thấy tiền tố xanh da trời sáng, đậm. ProtocolLib cung cấp metadata
riêng theo người xem. Dữ liệu
đội quân/kho đồ lưu tại `plugins/VnMineHardcore/mercenaries.yml`. AI không chọn
mục tiêu bị che khuất khỏi tầm nhìn. `ai.priorities` và `ai.wander-radius-blocks`
chỉnh thứ tự hành động/phạm vi tuần tra. Kỵ sĩ sử dụng ngựa do plugin quản lý,
không chiếm mount của người chơi/lính khác; ô gear riêng lưu giáp ngựa tốt nhất.
`classes.paladin.horse-health`/`classes.paladin.horse-speed` chỉnh HP/tốc độ
ngựa. Khi ngựa chết, Kỵ sĩ đi bộ cho đến khi nghỉ và được xuất chiến lại.

`plugins/VnMineHardcore/tooltips.yml` định nghĩa nhắc nhở chức năng định kỳ:
`settings.interval-seconds` đặt khoảng thời gian và `settings.mode` chọn
`random` hoặc `sequential`; `tooltips.players` chỉ hiển thị cho người chơi,
`tooltips.admins` chỉ hiển thị cho OP/người có quyền quản trị. Tooltip hướng dẫn
bấm `F` hoặc dùng `/vnmenu` để mở menu chính. Dùng `/vnreload` sau khi chỉnh cấu hình.

Để tránh xung đột, chỉ dùng một hệ thống làm nguồn cấp quyền cho node
`vnmine.*`; permission từ plugin khác có thể cộng thêm quyền. Chỉ cấp
`vnmine.command.groups.manage`/`vnmine.command.users.manage` cho nhân viên đáng
tin cậy. OP luôn có toàn quyền và không thể bị giới hạn bởi group.

## Cấu hình (config.yml)

Mưa được điều khiển trong `environment.rain-control`: mặc định mỗi lần bắt đầu
mưa có 40% xác suất được chấp nhận; thời lượng tối đa là 5 phút và plugin chọn
ngẫu nhiên từ 1 đến 5 phút để mưa có thể tạnh sớm. Có thể chỉnh
`chance-percent`, `max-duration-minutes`,
`random-duration.enabled` và `random-duration.minimum-minutes`.

Sát thương môi trường được lọc theo biome trong
`environment.temperature.biomes`: `hot` mặc định gồm sa mạc, badlands và
savanna; `cold` gồm biome tuyết/băng, taiga và ocean lạnh; `acid-rain` gồm
rừng, jungle và taiga. Nether luôn nóng. Mưa axit chỉ gây sát thương khi đang
mưa, người chơi ở biome trong danh sách `acid-rain` và không có mái che; sát
thương nước chỉ áp dụng ở biome trong danh sách `cold`. Có thể tùy chỉnh các
danh sách bằng tên biome vanilla hoặc khóa `namespace:key`; danh sách rỗng sẽ
tắt nhóm tương ứng. Biome thuộc nhiều danh sách có thể nhận đồng thời các loại
sát thương tương ứng.

### Rename - Đổi tên hiển thị sau khi chết

```yaml
rename:
  # Bật/tắt tự động đổi tên hiển thị sau khi chết
  enabled: true

  # Định dạng tên hiển thị.
  # Các placeholder có sẵn:
  #   <name>   - Tên gốc của người chơi (ví dụ: hoanbh)
  #   <count>  - Số lần chết (số thường, không padding, ví dụ: 1, 15, 200)
  #   000      - Dãy số 0: đại diện cho số lần chết có padding (thêm số 0 ở đầu).
  #              Số lượng chữ số 0 = độ dài tối thiểu của số.
  #              Ví dụ với số lần chết = 3:
  #                000 -> 003
  #                0000 -> 0003
  #                00 -> 03
  #              Ví dụ với số lần chết = 15:
  #                000 -> 015
  #                0000 -> 0015
  #
  # Ví dụ các định dạng mẫu:
  #   "<name> đã chết 000 lần"  → hoanbh đã chết 001 lần
  #   "<name>_000"              → hoanbh_001
  #   "000 | <name>"            → 001 | hoanbh
  #   "<name> (<count>)"        → hoanbh (1)
  #   "0000"                    → 0001 (chỉ hiện số)
  name-structure: "<name> ☠ <count>"
```

**Lưu ý:** Chức năng này chỉ thay đổi **tên hiển thị** trong chat và tab list (khi bấm phím Tab), **không** thay đổi tên thật của người chơi (`player.getName()`).

### Death & Respawn - Hồi sinh sau khi chết

Sau khi chết, người chơi có thể chọn hồi sinh hoặc thoát game bằng hai nút chat
hoặc lệnh `/vnrespawn` và `/vnleave`. Sau khi chọn hồi sinh,
`death.respawn-protection` mặc định chờ 10 giây; trong thời gian này người chơi
bị giới hạn trong bán kính 10 block quanh vị trí hồi sinh và không thể tương
tác, mở/sửa inventory, nhặt hoặc thả đồ, hay tấn công. Tiêu đề màn hình đếm
ngược cho biết khi nào các thao tác được mở lại. Người chơi được miễn nhiễm
sát thương trong lúc chờ và thêm số giây đặt ở
`death.respawn-protection.damage-immunity-seconds` sau khi được tự do tương
tác. Sau khi hết thời gian giới hạn di chuyển/tương tác, người chơi được di
chuyển tự do ngay cả khi miễn nhiễm sát thương vẫn còn. Quái vật không thể
chọn họ làm mục tiêu trong toàn bộ thời gian miễn nhiễm. Chạy `/vnreload` sau
khi thay đổi các thiết lập này.

Lịch sử chết lưu tối đa 10 snapshot đầy đủ gồm 36 ô kho, giáp và tay phụ trong
`plugins/VnMineHardcore/death-history.yml`. Admin có thể dùng
`/vndeathhistory` để chọn người chơi online hoặc `/vndeathhistory <player>` để
mở trực tiếp lịch sử. Chọn lần chết sẽ hiển thị bước xác nhận; khôi phục sẽ thay
thế inventory hiện tại của người chơi đang online bằng dữ liệu đã lưu. Số lượng
snapshot có thể điều chỉnh từ 1 đến 10 bằng `death.history.max-records-per-player`.

```yaml
death:
  respawn-protection:
    enabled: true
    delay-seconds: 10
    movement-radius-blocks: 10
    damage-immunity-seconds: 10
```

### Grave structures - Cấu trúc mộ

`graves-structure.yml` chứa cả cấu trúc `full` và `mini`. Đặt
`mini-graves: true` để dùng mộ mini, hoặc `false` để dùng mộ full. Khi nâng
cấp, plugin tự bổ sung các cấu trúc/khóa còn thiếu vào file hiện có và giữ
nguyên các giá trị tùy chỉnh. Bia mộ full dùng `PLAYER_WALL_HEAD` hướng về thân
mộ để đầu dựng thẳng trên mặt bia. Nếu người chơi chết trong nước, mộ được đặt
trên mặt nước và dùng block hỗ trợ tạm khi cần; block hỗ trợ được dọn cùng mộ.
Có thể tắt tính năng hoặc chọn vật liệu block hỗ trợ đặc trong
`graves.water-surface-placement` của `config.yml`.
`/quatanthu` chỉ cấp túi khi người chơi chưa từng nhận quà và hiện không sở hữu
lính đánh thuê, kể cả lính đang nghỉ hoặc hồi phục.

### Admin testing

`/fly` bật/tắt bay khi người quản trị vẫn ở Survival. `/nodame` bật/tắt miễn
nhiễm sát thương cho người chạy lệnh; quái vẫn có thể chọn người đó làm mục tiêu.
Trạng thái `/nodame` sẽ tự tắt khi người chơi thoát máy chủ.

### Spawn protection

`/vnspawn protect <X>` tạo khu vực hình tròn bán kính X block theo mặt phẳng
X/Z quanh block hiện tại; khu vực áp dụng ở mọi độ cao nhưng chỉ trong world
được tạo. Người chơi mặc định không thể phá hoặc đặt block bên trong. OP và
người có `vnmine.spawnprotection.bypass` vẫn được phá/đặt; vụ nổ, lửa, dòng
chất lỏng và block do thiên tai không thể làm thay đổi vùng này. Dữ liệu và bộ
đếm ID được lưu tại `plugins/VnMineHardcore/spawns-protection.yml`, nên vẫn còn
sau khi khởi động lại.

```text
/vnspawn set
/vnspawn go world_the_end
/vnspawn protect 25
/vnspawn list
/vnspawn remove 1
/tp Steve world_the_end
/vnevent stop
```

Lệnh `/tp <user> <world>` đưa người chơi đang trực tuyến đến spawn của world.
`/vnspawn set` đặt spawn của world hiện tại theo vị trí và hướng người gọi;
`/vnspawn go <world>` đưa người gọi đến spawn của world đang tải. Tên world
được gợi ý bằng Tab.
`/vnevent stop` hủy cảnh báo hoặc thiên tai đang chạy, dọn các task/thực thể
do thiên tai tạo và khôi phục thời tiết, thời gian cùng hiệu ứng đang được theo
dõi. Các lệnh quản trị cần node riêng theo chức năng, được cấp từ
`permission.yml` (nhóm `admin` mặc định có toàn bộ quyền plugin).

### Thiên tai Chorus Explosion

Chorus Explosion mặc định tắt (`enabled: false`) trong
`plugins/VnMineHardcore/disasters/chorus-explosion.yml`. Khi được bật, điều
kiện `worlds` và bộ lọc trên từng action hỗ trợ tên world thật (ví dụ
`world_the_end`) cũng như tên dimension (ví dụ `the_end`); cấu hình mặc định
chỉ cho phép thiên tai này ảnh hưởng `world_the_end`.

### Boss level & skills

`bosses/_settings.yml` configures level ranges and spawn success chance by
in-game day. The default spawn success chance grows from 15% on day 0 to 100%
on day 1000. Boss level thresholds are day 0 (level 1), 100 (levels 1-2),
200 (1-3), 400 (1-4), 600 (2-5), 900 (2-6), 1200 (2-7), 1500 (3-8),
1800 (3-9), and 2500 (3-10). The applicable threshold is the last `min-day`
not greater than the current day, and the boss randomly chooses a level inside
that threshold's range. The day is read from the first loaded world; at day 500,
an automatically spawned boss can therefore be at most level 4 with these
settings. `/vnboss <id> <level> ...` deliberately uses the requested level.
In each boss file,
`hp-by-level` maps levels to explicit maximum HP; missing levels use the nearest
lower configured level, then scale the base `hp` by 1.5 per additional level.
Skill sections under `skills` can be enabled individually and tune their
base damage, projectile count multiplier, cooldown, area, velocity, and spread.
Configured skill damage scales by boss level; projectile skills use
`boss-level * multiply` projectiles, capped at 40 per cast.
The creeper's `charged-explosion` skill calls a visual lightning strike,
damages nearby living entities using `damage` and `radius` without self-damage, then
keeps the creeper charged for `duration-seconds`. Its explosion power is
configured separately for normal and charged states; boss creeper explosions
do not break blocks and the boss survives each explosion. Values are in
`bosses/creeper_boss.yml`.
Available skill IDs are `arrow-barrage` (skeleton), `fireball-volley`
(dragon/ghast), `stomp` (giant), `ground-slam` (zombie), `charged-explosion`
(creeper), `shadow-strike` (enderman), `dive-bomb` (phantom), `charge`
(ravager), `venom-web` (spider), `shockwave` (vindicator), `potion-volley`
(witch), and `skull-volley` (wither).

Eight bosses also support the extended skills and traits configured in their
individual `bosses/*.yml` files: Enderman (`void-snatch`, `end-rift`,
`hydro-phobic-aura`), Ravager (`siege-charge`, `roar-weakness`,
`thorns-armor`), Phantom (`dive-bomb`, `phantom-minions`, `nocturnal-cloak`),
Ghast (`fireball-volley`, `fiery-drag`, `fireball-shield`), Spider
(`cobweb-trap`, `venom-pounce`, `brood-release`), Vindicator (`axe-spin`,
`axe-throw`, `enrage`), Witch (`potion-rain`, `disarm-magic`,
`witchcraft-drain`), and Giant (`ground-slam`, `boulder-toss`, `giant-armor`).
Terrain-changing skills are controlled by `terrain-enabled`, and temporary
blocks are restored after their configured duration or when the encounter ends.
Ravager block breaking uses the `break-blocks`, `max-break-blocks`, and
`breakable-blocks` allowlist settings.
Spider skill damage is limited to the configured skill radius from the spider;
`max-player-hit-damage` in `spider_boss.yml` caps each skill hit to a player
before armor and the global mob-damage multiplier (default 5, valid 0-100).
Spider melee uses native mob attacks rather than an additional repeated
scripted hit.

Version 2.0.0 adds global boss controls in `bosses/_settings.yml`: randomized
HP by level (level 1: 2000-4000, level 10: 45000-50000), configurable scaling
for selected boss types, flight-height and combat-distance limits, weighted
target selection, projectile dodging, three tactical phases, and coordination
of summoned minions. Global `max-player-hit-damage` defaults to 6 damage per
hit; the spider-specific cap can further limit its skill hits. Default boss
lifetime is 1800 seconds (30 minutes); legacy boss files at 1200 seconds are
migrated. Boss kills can also drop a randomized mercenary gear set by boss
level using `mercenary-gear-drops`.

`reward-damage-threshold-percent` in `bosses/_settings.yml` defaults to 50.
Each player's actual damage is accumulated against the boss's maximum HP; only
players who deal strictly more than the configured percentage qualify for
boss item drops. All players who deal positive damage receive boss XP and
leaderboard points; both pools are split by each player's share of total damage.
Contributions remain eligible if the boss dies from another cause, but
`/vnboss kill` and lifetime expiry always end the encounter without points or loot.

Global boss skill cast feedback is configured in `bosses/_settings.yml`:
`skill-cast-feedback.console-log` logs successful casts to the console,
`skill-cast-feedback.broadcast-to-ops` sends notices only to online OPs, and
`skill-cast-feedback.broadcast-to-players` broadcasts translated skill names
to online non-operator players. OP and regular-player broadcasts are separate;
all options default to `true`. Bosses are persistent and
glowing for visibility. Grounded mobs use native AI/pathfinding; the Giant
uses slow custom movement because its vanilla AI does not reliably pursue targets.

```yaml
# bosses/_settings.yml
level-progression:
  enabled: true
  thresholds:
    - {min-day: 0, min-level: 1, max-level: 1}
    - {min-day: 100, min-level: 1, max-level: 2}
    - {min-day: 200, min-level: 1, max-level: 3}
    - {min-day: 400, min-level: 1, max-level: 4}
    - {min-day: 600, min-level: 2, max-level: 5}
    - {min-day: 900, min-level: 2, max-level: 6}
    - {min-day: 1200, min-level: 2, max-level: 7}
    - {min-day: 1500, min-level: 3, max-level: 8}
    - {min-day: 1800, min-level: 3, max-level: 9}
    - {min-day: 2500, min-level: 3, max-level: 10}

# Example in an individual bosses/<boss>.yml
hp-by-level:
  1: 120
  2: 180
skills:
  charged-explosion:
    enabled: true
    damage: 12.0
    radius: 6.0
    duration-seconds: 5
    explosion-power: 3.0
    charged-explosion-power: 6.0
    explosion-cooldown-seconds: 4
  arrow-barrage:
    enabled: true
    damage: 6.0
    multiply: 5
    cooldown-seconds: 12
    radius: 7.0
    velocity: 1.5
    spread: 1.4
```

### Loot boss theo level và bảng xếp hạng

Mỗi file `bosses/<id>.yml` có mục `level-drops` riêng cho boss đó, đầy đủ từ
level 1 đến level 10. Mỗi cấp có vũ khí và sách di vật; mỗi boss cũng có cấu
hình `armor-set` tạo mũ, áo giáp, quần và ủng với xác suất mặc định 45% cho
từng món. Vật liệu chuyển từ sắt ở level 1 sang kim cương ở level 4 và
netherite ở level 7. Enchant của vũ khí và giáp tăng theo cấp; cấp thấp bắt đầu
dưới mức tự nhiên, còn cấp cao có thể over-enchant đến cấp 10. Tên/lore theo
từng boss được quản lý tập trung trong `plugins/VnMineHardcore/story.yml`.
Mỗi item dùng `story-id` để liên kết tới `artifacts.<story-id>.lore`; có thể
thêm chương mới bằng cách thêm ID duy nhất và danh sách lore vào file này.
Định nghĩa chapter có ID ổn định được nạp riêng từ
`plugins/VnMineHardcore/story/chapters.yml`; mỗi mục cần `order` dương duy nhất,
`title` và `summary` không rỗng. `pages` chứa 1-12 trang sách, mỗi trang tối đa
256 ký tự; nếu bỏ trống, sách dùng `summary` làm trang mặc định. Danh sách câu truyện cũ trong `story.yml` được
giữ lại để tương thích; artifact và `story-id` không cần di chuyển khi thêm chapter.
Khi cập nhật plugin, các giá trị server hiện có được giữ nguyên; chỉ khóa mặc định
còn thiếu mới được thêm. Không có migration tự động hoặc xóa dữ liệu khỏi `story.yml`.
Khóa YAML trùng trong `story.yml` hoặc `story/chapters.yml` làm file story tương ứng
bị từ chối khi nạp và được ghi log; cần sửa ID trùng trong file trước khi khởi động lại.

### Tạo nhân vật RPG

Mục **Nhân vật RPG** trong menu chính (mở bằng phím F mặc định hoặc `/vnmenu`)
mở trang hồ sơ nếu bạn đã có nhân vật; người chơi mới được đưa tới GUI chọn chủng tộc
rồi class. Trang hồ sơ hiển thị race, class, cấp, EXP và đại lục khởi đầu, đồng thời
có nút mở nhật ký nhiệm vụ hoặc quay lại hub. GUI tạo nhân vật hiển thị mô tả, biểu
tượng và màn hình xác nhận trước khi lưu. Mỗi UUID chỉ tạo được một nhân vật; thao
tác tạo lặp sẽ bị từ chối, không ghi đè race/class hay tiến trình đã lưu.

Mục **Sổ truyện** trong menu chính hiển thị chapter từ
`plugins/VnMineHardcore/story/chapters.yml` theo thứ tự cấu hình, có phân trang
và đường quay lại hub. Bấm chapter để mở cuốn sách chứa nội dung đầy đủ; NPC
Người giữ ký ức cũng mở danh mục này. Chapter hiện chưa có điều kiện mở khóa riêng.

Có thể tắt tính năng bằng `rpg.character-creation.enabled: false` trong
`plugins/VnMineHardcore/config.yml`.
Quản trị viên có thể tùy chỉnh các lựa chọn tại
`plugins/VnMineHardcore/races.yml` và `plugins/VnMineHardcore/player-classes.yml`.
Mỗi ID phải duy nhất, dùng chữ thường ASCII, chữ số và dấu gạch nối; class phải
tham chiếu tới race tồn tại, tên/mô tả không rỗng và `icon` phải là Material hợp lệ.
Mỗi race cần ít nhất một class; mỗi màn hình hiện hỗ trợ tối đa 28 lựa chọn.
Các file này chỉ được tạo mặc định khi chưa tồn tại, vì vậy cập nhật plugin không
ghi đè tùy chỉnh của server. Thay đổi nội dung có hiệu lực sau khi khởi động lại.
Hồ sơ được lưu trong `plugins/VnMineHardcore/data/player_profiles.db`.

Chọn race/class hiện chỉ lưu dữ liệu hồ sơ cơ bản; chưa áp dụng kỹ năng, chỉ số
chiến đấu hoặc giới hạn đổi class. Các hệ thống đó sẽ được triển khai ở các phần RPG sau.

### Bản đồ đại lục

Mục **Bản đồ đại lục** trong menu chính hiển thị Nhân giới mở mặc định và Yêu giới
khóa cho tới khi profile active hoàn thành quest `first-steps`. Mỗi card cho biết
điều kiện truy cập, world và trạng thái spawn (bao gồm khi đang dùng world dự phòng).
Nhấn card đã đủ điều kiện để dịch chuyển; thao tác kiểm tra lại profile/quest,
world/spawn chính, trạng thái chết/respawn grace và combat theo cấu hình.
Ẩn mục này bằng `rpg.world-map.enabled: false` trong
`plugins/VnMineHardcore/config.yml`; tắt travel hoặc cấm travel khi combat tại
`rpg.portal-travel.enabled` và `rpg.portal-travel.block-in-combat`.
Unlock được lưu trong SQLite chỉ sau khi Paper xác nhận dịch chuyển thành công;
dịch chuyển lỗi không đổi trạng thái unlock. Nhân giới có thể tới trước khi tạo profile,
nhưng cần profile active để ghi tiến trình. Yêu giới yêu cầu profile active hoàn
thành `first-steps`.
Realm được quản lý bằng các file `plugins/VnMineHardcore/worlds/*.yml`; Yêu giới
mặc định dùng world `yeu_realm`,
phải do quản trị viên tạo/nạp. Plugin không tự tạo world. Fallback chỉ hiển thị
điểm dự phòng, không dùng fallback làm đích dịch chuyển. Điều kiện `default` hoặc `completed-quest`
được khai báo trong realm YAML; các realm khác không được tự hiển thị như đang khóa.

Các cấp 1–2 dùng hậu tố `-common` với tên và câu chuyện giản dị hơn; từ cấp 3
trở lên dùng tuyến truyện phát triển theo sức mạnh của di vật.
Lore hỗ trợ `{level}` để hiển thị cấp vật phẩm; mỗi dòng không quá 30 ký tự,
tối đa 6 dòng. Cung Skeleton level 1 có thể rơi với Power III, Flame I và
Infinity I; xác suất rơi 65%, Infinity vẫn cần mang ít nhất một mũi tên;
trong file boss có thể tùy chỉnh `material`, `name`,
`min-amount`, `max-amount`, `chance`, `enchantments` (hỗ trợ over-enchant)
hoặc `book-enchantments` (sách có enchant để áp dụng vào item). Boss tự nhiên
chỉ lên level theo ngưỡng đang cấu hình trong `bosses/_settings.yml`;
`/vnboss` vẫn cho phép gọi boss ở level được chọn. Khi boss xuất hiện, server
broadcast loot có thể rơi ở level đó và tỉ lệ từng món. Mọi boss tồn tại mặc định 30 phút;
bộ đếm roll boss kế tiếp tạm dừng trong khi cảnh báo hoặc boss đang hoạt động.

`scoreboard.yml` cấu hình chu kỳ reset (`reset.every-game-days`, mặc định 100
ngày trong game), sidebar, thời gian title top 1, chu kỳ nhắc và pool khoảng 20
vật phẩm cho từng hạng top 1–10. Khi mùa kết thúc, mỗi hạng nhận đúng một món
được chọn ngẫu nhiên; lựa chọn được lưu cùng phần thưởng đang chờ để không bị
đổi khi nhận lại. Mỗi boss tạo tổng quỹ `10 × level` điểm mùa, chia theo tỉ lệ
sát thương giữa tất cả người đóng góp. Điểm và quà
đang chờ được lưu trong `scoreboard-data.yml`. Người chơi nhận quà bằng
`/reward`; nếu inventory không đủ chỗ, quà vẫn chờ và có thể nhận lại sau khi
dọn túi. Boss không nhận sát thương từ môi trường (bao gồm sát thương rơi) hoặc
skill/đòn đánh do chính boss tạo ra; vật phẩm thông thường vẫn theo ngưỡng đóng
góp, còn token triệu hồi có thể rơi khi có người chơi gây sát thương.
Chạy `/vnreload` để nạp cấu hình.

### Disasters - Thiên tai

Thiên tai không nằm trong `config.yml`. Cấu hình chung ở
`plugins/VnMineHardcore/disasters/_settings.yml`; mỗi loại thiên tai có file
riêng `plugins/VnMineHardcore/disasters/<id>.yml`.

`chat-description` có thể tiếp tục tồn tại trong file thiên tai để ghi chú cấu
hình, nhưng không còn được phát trong chat. Thông báo chỉ hiển thị world bị
ảnh hưởng, lấy từ `conditions.worlds` và chuyển thành tên các world đang tải;
cùng điều kiện đó cũng giới hạn những world mà action của sự kiện có thể tác
động. Các file cũ được bổ sung khóa còn thiếu nhưng giữ nguyên giá trị tùy chỉnh.

Trọng số `chance` của các thiên tai đang bật mặc định bằng nhau (5). Hệ thống
lọc trước các loại phù hợp với world/giờ hiện tại rồi mới roll trọng số, tránh
thiên tai bị thiên lệch do điều kiện dimension. `chorus-explosion` vẫn mặc định
tắt để tránh dịch chuyển người chơi ngoài ý muốn.

### Time Control - Thời lượng ngày và đêm

Trong `config.yml`, `time-control.global.day-duration-minutes` và
`time-control.global.night-duration-minutes` đặt thời lượng toàn cục theo phút
thực tế cho mọi world trừ Nether. Có thể ghi đè riêng trong
`time-control.worlds.<world-name>`; mỗi giá trị bị bỏ trống sẽ kế thừa giá trị
global tương ứng. Ví dụ:

```yaml
time-control:
  enabled: true
  global:
    day-duration-minutes: 10
    night-duration-minutes: 10
  worlds:
    world_resource:
      day-duration-minutes: 6
    world_the_end:
      night-duration-minutes: 20
```

Thiết lập mặc định 10 phút cho mỗi pha giữ tốc độ mặc định của Minecraft.
Đặt `time-control.enabled: false` để giữ chu kỳ ngày/đêm vanilla. Có thể đổi
hai giá trị global bằng `/vntime set day <phút>` và
`/vntime set night <phút>`; thay đổi được lưu ngay vào `config.yml`. Các world
đã có override riêng vẫn giữ thời lượng đã cấu hình. Bỏ tham số world trong
lệnh để chỉnh global; thêm tên world đang tải để chỉ chỉnh world đó:
`/vntime set day 6 world_resource`.
Lệnh `/vnday set <ngày>` chỉ dành cho người chơi và đặt đồng hồ thời gian đầy
đủ của world hiện tại về bình minh ngày được chọn (ngày 0 là lúc tạo world).
Nether luôn được loại khỏi chức năng quản lý thời gian: plugin không thay đổi
thời gian hoặc gamerule của world Nether. Các world khác không có world clock
sẽ được bỏ qua và ghi cảnh báo.

### Phiên bản tương thích

Dự án nhắm tới Paper 26.3 và Java 25+ cho server. Mã nguồn biên dịch với
Paper API `26.3.build.175-stable`; `api-version` trong `plugin.yml` là `26.3`.

`config.yml` và các file boss/disaster giữ nguyên giá trị tùy chỉnh khi plugin
cập nhật. Vì vậy, nếu máy chủ đã có file
`plugins/VnMineHardcore/disasters/chorus-explosion.yml` từ phiên bản trước,
hãy đặt `enabled: false` thủ công nếu muốn áp dụng mặc định mới; giá trị
`enabled: true` được admin cấu hình có chủ ý sẽ không bị ghi đè.

### Block Drop, Tool Break & Explosion - Hiệu ứng khi đào

Phần `block-drop-settings` cho phép đặt tỷ lệ 0-100 riêng cho từng block và
cúp. Ba công tắc cấp cao (`replace-enabled`, `break-enabled`,
`explode-enabled`) có thể tắt riêng từng loại hiệu ứng; từng rule cũng có
công tắc `enabled`. `drop-replace.replace-block` là vật phẩm thay cho drop
vanilla khi trúng tỷ lệ. `explode` hỗ trợ cấu hình sức mạnh, phá block, lửa,
sát thương người chơi, thông báo và tỷ lệ. Chạy `/vnreload` sau khi sửa config.

## Yêu cầu

- Paper 26.3
- Java 25+ (runtime server)
- Paper API `26.3.build.175-stable` dùng làm compile baseline

## Cài đặt

1. Tải file `.jar` từ bản phát hành (Release)
2. Đặt file vào thư mục `plugins/` của server
3. Khởi động lại server
4. Chỉnh sửa `plugins/VnMineHardcore/config.yml` theo nhu cầu
5. Gõ `/vnreload` để áp dụng thay đổi

## Changelog

### v2.25.0
- Đổi gear Kỵ Sĩ sang spear, đặt tên vũ khí/sách theo vai trò, hiển thị và áp dụng bonus sách phép theo vật liệu/sao, gộp gear hai tay thành một mẫu dùng được cho cả hai tay.
- Trinh Sát tự soi sáng khi thiếu sáng, không cần mục tiêu; bố trí mũi tên theo vòng và chặn đuốc tạm trong phạm vi tối thiểu.
- Build thành công bằng JDK 25; test suite chưa chạy. JAR cài tại `/home/ubuntu/DATA/VNMINE/plugins/VnMineHardcore-2.25.0.jar`; SHA-256 `20dca1d90bd0e5bde57ee7ed5a2f0a98413a8bce482e75c06b8f16fbc2333964`. JAR 2.24.0 được sao lưu trong `.old-builds`.

### v2.24.0
- Chia sách kỹ năng theo chủng tộc → class, lọc danh mục boss còn vật phẩm hiếm và thống nhất icon gear/token với menu quân đội.

### v1.9.1
- Đóng gói lại JAR từ resource hiện tại để khai báo đầy đủ các lệnh trong `plugin.yml`, tránh lỗi không tìm thấy command khi khởi động.

### v1.10.0
- Thêm `/vngroup` và `/vnuser` để quản lý group, phân quyền từng lệnh và quyền riêng theo người chơi trong `permission.yml`.
- Nhóm `user` mặc định chỉ dùng lệnh cơ bản; nhóm `admin` có đầy đủ quyền plugin.
- Bảo vệ cả block đặt, lửa, dòng chất lỏng, vụ nổ và thay đổi block do thiên tai; OP hoặc người có `vnmine.spawnprotection.bypass` được phá/đặt trong vùng.

### v1.9.0
- Thêm `/vnspawn set` để đặt spawn world hiện tại và `/vnspawn go <world>` để tới spawn của world đang tải; hỗ trợ gợi ý Tab tên world.

### v1.8.0
- Thêm `/tp <user> <world>` và nhóm lệnh `/vnspawn protect|list|remove`; lưu khu bảo vệ trong `spawns-protection.yml`.
- Thêm `/vnevent stop` để dừng cảnh báo hoặc thiên tai đang hoạt động.
- Động đất hất người chơi ngoài trời theo phương ngang và dọc; lực có thể chỉnh trong file thiên tai.
- Mỗi file thiên tai có `chat-description`; chat lúc cảnh báo/bắt đầu hiển thị tác động, gợi ý phòng tránh và world bị ảnh hưởng.
- Sửa so khớp world/dimension cho điều kiện thiên tai; Chorus Explosion tắt mặc định.
- Cập nhật phiên bản Paper mục tiêu trong tài liệu lên 26.x.

### v1.7.0
- `/vntime set <day|night> <phút> [world]`: bỏ `[world]` để đổi mặc định toàn cục,
  hoặc ghi tên world đang tải để đặt override riêng.
- `/vnday set <ngày>` đặt world hiện tại về bình minh của ngày đó; ngày 0 là lúc
  thế giới được tạo.
- Mỗi boss có loot quý riêng cho level 1–10, lore truyền thuyết tối đa 4 dòng
  và số dòng enchant tăng dần theo level; HP cũng được cấu hình đến level 10.
  Ở cấu hình mặc định của phiên bản v1.7, boss tự nhiên có thể đạt level 10 từ
  ngày 400.
- Thời gian tồn tại mặc định của mọi boss là 20 phút; không roll boss mới trong
  thời gian đang cảnh báo hoặc boss khác còn hoạt động.

### v1.6.0
- Thêm `/scoreboard reset` để xóa điểm mùa và phần thưởng boss đang chờ nhận.
- Thêm `/vntime set day <phút>` và `/vntime set night <phút>`; tự lưu cấu hình
  global và giữ nguyên override riêng từng world.
- `/vnhelp` chỉ liệt kê các lệnh bạn có quyền sử dụng; nhấn Tab để gợi ý tham số.

## Build từ mã nguồn

```bash
cd VnMineHardcore
mvn clean package
```

File `.jar` sẽ được tạo trong thư mục `target/`.
