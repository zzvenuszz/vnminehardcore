# VnMineHardcore

Plugin Minecraft RPG hardcore bằng tiếng Việt, xây dựng trên Paper 26.3. Plugin kết hợp sinh tồn khó, nhân vật RPG, nhiệm vụ, kinh tế VNCOIN, NPC và công cụ quản trị world trong một bộ cài.

> **Tình trạng:** dự án đang phát triển tích cực. Một số nội dung quản trị được mở rộng theo từng phiên bản; hãy thử nghiệm bản mới trên máy staging và sao lưu dữ liệu trước khi nâng cấp production.
>
> **Nhóm phát triển:** plugin được phát triển bởi một người.

## Mục lục

- [Tổng quan](#tổng-quan)
- [Yêu cầu và cài đặt](#yêu-cầu-và-cài-đặt)
- [Bắt đầu hành trình RPG](#bắt-đầu-hành-trình-rpg)
- [Chức năng người chơi](#chức-năng-người-chơi)
- [Chức năng quản trị](#chức-năng-quản-trị)
- [Cấu hình](#cấu-hình)
- [Lệnh](#lệnh)
- [Permissions](#permissions)
- [Vận hành và xử lý sự cố](#vận-hành-và-xử-lý-sự-cố)
- [Phát triển và đóng góp](#phát-triển-và-đóng-góp)

## Tổng quan

VnMineHardcore bổ sung các hệ thống sau:

- **Sinh tồn hardcore:** tăng sát thương quái, khát nước, combat tag, thiên tai và quản lý boss. Cái chết được ghi nhận nhưng **không tự động ban người chơi**.
- **RPG:** đại lục/world, chủng tộc, class, level/EXP, kỹ năng và sách kỹ năng.
- **Nhiệm vụ và cốt truyện:** quest nhiều mục tiêu, quest lặp lại, hội thoại NPC, chương truyện, nhật ký quest, scoreboard và navigation particle.
- **Kinh tế:** ví VNCOIN, drop coin, cửa hàng hệ thống và merchant tùy chỉnh.
- **NPC và đội quân:** NPC theo vai trò, lính đánh thuê, trang bị theo class/vật liệu/bậc.
- **Công cụ admin:** menu admin, quản lý người chơi, cấu hình world, WorldEdit/WorldGuard tích hợp, Schema Build, text display và quyền theo nhóm.

Các tính năng phụ thuộc vào cấu hình server. README này mô tả cách dùng plugin; từng file YAML có cấu hình chi tiết và giá trị mặc định.

## Yêu cầu và cài đặt

### Yêu cầu

- Paper **26.3** trên Linux x86_64.
- Java tương thích với bản Paper đang chạy.
- ProtocolLib được cài trong `plugins/` trước VnMineHardcore.

### Cài mới

1. Tắt server.
2. Sao lưu thư mục world và `plugins/VnMineHardcore/` nếu đang nâng cấp.
3. Chép `VnMineHardcore.jar` và ProtocolLib vào `plugins/`.
4. Khởi động server một lần để sinh cấu hình mặc định.
5. Dùng `/vninit` để cấu hình onboarding và đại lục; dùng `/vnmenu` để mở menu chính.
6. Khởi động lại server nếu vừa cấu hình tạo world hoặc đổi môi trường/seed.

Không sửa hoặc xóa world folder khi server đang chạy. Khi đổi môi trường đại lục đã tạo, plugin giữ bản cũ trong thư mục backup có timestamp và tạo world theo cấu hình mới ở lần khởi động tiếp theo. Kiểm tra log `[World]` trước khi cho người chơi vào.

### Nâng cấp

Tắt server, sao lưu dữ liệu, thay JAR rồi khởi động lại. Không chép cấu hình mặc định đè lên file đã tùy chỉnh. Sau cập nhật, xem `CHANGELOG.md` và log khởi động để biết thay đổi cấu hình hoặc bước migration.

## Bắt đầu hành trình RPG

1. Admin dùng `/vninit` để đặt world bắt đầu, các đại lục, NPC tutorial, race/class khả dụng và luồng quest hướng dẫn. Nút **Quest đầu tiên: Bước chân đầu tiên** mở editor để sửa tên, mô tả, objective, thông báo, trạng thái bật/tắt và phần thưởng.
2. Tạo NPC loại `tutorial`, đặt đúng world, rồi chọn NPC đó ở trang setup tương ứng.
3. Người chơi dùng `/start` để bắt đầu hoặc tiếp tục hành trình.
4. Người chơi gặp NPC tutorial, chọn đại lục/race/class theo cấu hình và nhận sách skill khởi đầu cùng nhiệm vụ được thiết lập.
5. NPC và quest được quản lý tiếp trong `/vnnpc`, `/vnquest admin` và menu RPG.

`world_start` là world dành cho onboarding/reset. Các world đại lục được quản lý riêng bằng file `plugins/VnMineHardcore/worlds/*.yml`; không nhầm lẫn hai loại world này.

## Chức năng người chơi

### Nhân vật, kỹ năng và trang bị

- Xem thông tin RPG, race, class, level và kỹ năng trong menu RPG.
- Dùng `/vnskills` để mở kỹ năng đã học hoặc cây kỹ năng; Quick Cast có giao diện riêng.
- Kỹ năng có mana, action bar và hiệu ứng/particle tùy skill.
- Trang bị RPG có thể tăng chỉ số và hiệu quả kỹ năng theo class, vật liệu và bậc.

### Quest, story và tracking

- Nhận quest từ NPC phù hợp; quest có thể gồm giết quái, thu thập/nộp item, gặp NPC, boss và các objective được plugin hỗ trợ.
- Theo dõi tối đa số quest mà hệ thống cho phép; xem tiến độ ở scoreboard và nhật ký.
- Bật/tắt particle tracking trong GUI. Chỉ dẫn được cập nhật theo objective và vị trí; objective NPC có thể yêu cầu đi sang world khác trước.
- Chapter story và quest dialog chạy dần trên action bar, không gửi phần nội dung hội thoại vào chat.
- Xem phần thưởng trước khi nhận hoặc hoàn thành quest nếu quest cấu hình preview.

### VNCOIN và cửa hàng

- `/vncoin` xem số dư; coin rơi dưới dạng sunflower và có thể nhặt kể cả khi inventory đầy.
- Bán item tại merchant tùy theo giá cấu hình. Custom merchant chỉ mua đúng item đã thiết lập.
- Giá item RPG mặc định là 0 cho tới khi admin cấu hình giá. Với vật phẩm có nơi mua khác trả giá cao hơn, system merchant có thể đưa cảnh báo xác nhận.

### Sinh tồn và tiện ích

- `/vnstats` xem thống kê; `/vnrespawn` chọn hồi sinh sau khi chết; `/vnleave` thoát trong trạng thái chờ sau chết.
- Người chơi có thể dùng menu, quest, kỹ năng, đội quân hoặc portal nếu được cấp permission tương ứng.
- Cái chết không gây ban tự động; các hình phạt khác tùy cấu hình server.

## Chức năng quản trị

### Menu và người chơi

- `/vnmenu` → Admin mở công cụ quản trị.
- `/vnplayeradmin` mở danh sách người chơi online; nút cuối cho phép nhập tên người chơi offline.
- Tùy permission, admin có thể quản lý trạng thái tài khoản, inventory/death history, quest, class/race, skill, level/EXP và cấp item RPG.

### Quản lý đại lục và world

- `/vninit` có thể chạy lại để tiếp tục hoặc sửa cài đặt.
- Quest **Bước chân đầu tiên** có editor trong `/vninit` và mở nhanh từ editor NPC loại `tutorial`. Tại editor NPC Tutorial, **Quest khởi đầu tại NPC** cho phép chọn quest RPG theo giver NPC đã gán; quest này được giao sau dialog của NPC. Cấu hình tương tự nằm trong phần NPC Tutorial của `/vninit`.
- Menu **Quản lý đại lục/world** cho phép thêm đại lục bằng ID, tên hiển thị và tên world; sửa ID/tên/world, spawn, seed, environment và allowlist race/class; xóa có xác nhận. Khi đổi ID, ID cũ được giữ làm bí danh để profile/onboarding đang lưu tiếp tục được nhận diện.
- Khi đổi environment của một đại lục đã tạo, GUI cảnh báo rằng dữ liệu world cũ sẽ được archive và world mới được tạo khi restart. Đây không phải cài đặt của `world_start`.
- Đổi seed cũng yêu cầu tạo lại world theo thiết kế cấu hình. Sao lưu world trước khi xác nhận.
- Menu quản lý world riêng cho phép chỉnh rule như boss, thiên tai, thời gian, PvP, mob và tương tác nếu rule đó được hỗ trợ.

### Quest, NPC và merchant

- `/vnquest admin`: tạo, sửa, vô hiệu hóa, xóa quest; đặt điều kiện mở/hoàn thành, reward, repeat limit và quest nối tiếp. NPC giao quest, NPC objective, boss objective và quest tự giao theo race được chọn từ danh sách GUI đã lưu; chat chỉ dùng cho giá trị cần tạo mới như tên quest hoặc số lượng.
- `/vnquestdialog admin`: tạo hội thoại cho trạng thái quest.
- `/vnnpc create <id> <type>` và `/vnnpc edit <id>`: tạo/chỉnh NPC; shift + chuột phải mở editor theo loại NPC.
- NPC được hiển thị như người chơi; trong editor chọn **Cấu hình skin Player**, nhập username Minecraft Java, ví dụ `Notch`. Hệ thống lấy skin theo tài khoản Mojang; nhập `!default` để bỏ skin tùy chỉnh. Skin tải ở nền, được cache tới khi server tắt và tự thử lại sau timeout.
- “Player” là kiểu hiển thị. Loại chức năng của NPC vẫn là `merchant`, `quest-giver`, `story` hoặc `tutorial`; các luồng tương tác hiện tại tiếp tục hoạt động.
- Marker `!` (quest có thể nhận) và `?` (story chưa đọc/tutorial đang chờ) là hologram TextDisplay nhiều dòng do Paper quản lý, chỉ gửi tới người chơi đủ điều kiện trong cùng world và tầm cấu hình. Dấu `!` được ghép từ ký tự trang trí, thân và chấm đậm; scale hiển thị mặc định là 6 (gấp 3 lần scale 2 trước đây). Marker được dọn khi trạng thái quest/story thay đổi, NPC di chuyển, người chơi rời server hoặc plugin tắt. Có thể bật/tắt và chỉnh tầm, kích thước nền, chu kỳ tại `rpg.npc.markers` trong `config.yml`.
- Player model được gửi sau khi người chơi vào server ổn định; packet NPC ảo không đi qua listener của entity thật. Tên NPC có màu/format được hiển thị bằng TextDisplay riêng; tên mặc định của fake-player được để trống để không lộ profile name.
- Nội dung `/vntext`, chữ ghi lên bảng hiệu và tên NPC hỗ trợ mã màu/format legacy như `&6` (vàng), `&l` (đậm), `&o` (nghiêng), `&r` (đặt lại). Tên NPC có animation lặp vô hạn: `&rl:` (cả đoạn cùng màu), `&r1:` (trái sang phải), `&r2:` (phải sang trái), `&r3:` (từ giữa ra hai bên); `&r1`–`&r3` cũng nhận dạng khi bỏ dấu `:`. Hiệu ứng tác động từ mã đó đến mã rainbow kế tiếp hoặc hết tên.
- NPC tự xoay về người chơi gần nhất cùng world trong tầm `rpg.npc.look-at.range-blocks`; tắt bằng `rpg.npc.look-at.enabled`.
- Mô hình player và khoảng cách gửi tới người chơi được chỉnh trong `rpg.npc.player-model`; cần cài ProtocolLib (đã là dependency bắt buộc).
- Merchant editor cho phép đặt item RPG và cấu hình lượng/giá bằng nút tăng giảm.
- `/vnskillbook config`: gán sách skill khởi đầu cho race/class.

### WorldEdit, vùng bảo vệ và công trình

- `/vnregion wand` cấp búa chọn vùng. `//pos1`, `//pos2`, `//set`, `//replace`, `//copy`, `//paste`, `//undo` và các thao tác tích hợp phụ thuộc quyền WorldEdit.
- Vùng có thể đặt tên và có rule bảo vệ do plugin hỗ trợ; đây là hệ thống tích hợp, không phải phụ thuộc WorldEdit/WorldGuard.
- `/vnbuild select` chọn công trình Schema gần nhất. `/vnbuild` mở browser `.schem`; preview, xoay/lật, đặt, undo/redo và protect tùy workflow trong menu.
- `/vntext` tạo và chỉnh text display; hỗ trợ hướng mặt, màu, scale và góc xoay.

### Boss và thiên tai

- `/vnboss` và `/vnevent` quản lý sự kiện theo quyền admin.
- Lịch ngẫu nhiên chỉ được bắt đầu khi có người chơi ở world cho phép sự kiện. Nếu người chơi chỉ ở world đã tắt boss/thiên tai, lượt đó bị hủy và dùng interval kế tiếp thay vì roll liên tục.

## Cấu hình

Các file thường dùng trong `plugins/VnMineHardcore/`:

| Đường dẫn | Nội dung |
|---|---|
| `config.yml` | Sát thương, sinh tồn, lịch boss/thiên tai, onboarding và cấu hình chung. |
| `permission.yml` | Nhóm quyền, quyền mặc định và quyền riêng theo UUID. |
| `worlds/*.yml` | ID/tên đại lục, world, environment, seed, spawn, NPC tutorial, race/class khả dụng. |
| `managed-worlds.yml` | Manifest world plugin quản lý; không sửa thủ công khi chưa sao lưu. |
| `realm-id-aliases.yml` | Bí danh ID đại lục cũ sau khi đổi ID, giữ các profile/onboarding đang dở tương thích. |
| `quests/` | Quest thường, quest hướng dẫn và phần thưởng. |
| `npcs/` hoặc `npc-definitions.yml` | Định nghĩa NPC/dialog theo phiên bản cấu hình. Ưu tiên editor GUI/lệnh plugin. |
| `skills/`, `classes/`, `races/` | Định nghĩa skill, class và race nếu file tương ứng được sinh trong bản cài. |
| `shops/` | Bảng giá system/custom merchant nếu đã tạo qua GUI. |
| `schemas/` | File cấu trúc `.schem` cho Schema Build. |

Hãy dùng menu/command editor khi có; thao tác trực tiếp với YAML cần giữ đúng ID, kiểu dữ liệu và cấu trúc lồng nhau. Sau khi sửa file, dùng `/vnreload` nếu hệ thống đó hỗ trợ reload; các thay đổi environment/seed/world generation cần restart.

## Lệnh

`hoanbh` là tên ví dụ trong các lệnh cần chỉ định người chơi.

| Lệnh | Công dụng | Quyền |
|---|---|---|
| `/vnmenu` | Menu chính | `vnmine.command.menu` |
| `/start` | Bắt đầu/tiếp tục hành trình RPG | `vnmine.command.start` |
| `/vnquest [journal|detail <id>|admin]` | Nhật ký hoặc quản lý quest | `vnmine.command.quest`; admin: `vnmine.command.quest.admin` |
| `/vnskills [list|tree]` | Kỹ năng RPG | `vnmine.command.skills` |
| `/vncoin [balance|history]` | Ví VNCOIN | `vnmine.command.coin`; admin: `vnmine.command.vncoin.admin` |
| `/vnstats` | Thống kê cá nhân | `vnmine.command.stats` |
| `/vnhelp` | Trợ giúp theo quyền | `vnmine.command.help` |
| `/vnrespawn`, `/vnleave` | Chọn hồi sinh hoặc thoát sau khi chết | `vnmine.command.respawn`, `vnmine.command.leave` |
| `/reward` | Nhận thưởng boss đang chờ | `vnmine.command.reward` |
| `/vnarmy <subcommand>` | Quản lý lính đánh thuê cá nhân | `vnmine.command.army` |
| `/quatanthu` | Nhận quà tân thủ nếu đủ điều kiện | `vnmine.command.newbie-gift` |
| `/feed` | Hồi đầy thức ăn | `vnmine.command.feed` |
| `/heal` | Hồi đầy máu | `vnmine.command.heal` |
| `/vnhardcore` | Trạng thái plugin | `vnmine.command.status` |
| `/vnreload` | Reload cấu hình/hệ thống được hỗ trợ | `vnmine.command.reload` |
| `/vninit` | Wizard onboarding và đại lục | `vnmine.command.init` |
| `/vnplayeradmin` | Quản trị người chơi online/offline | `vnmine.command.playeradmin` |
| `/vnskillbook <give|config>` | Quản trị sách kỹ năng | `vnmine.command.skillbook.admin` |
| `/vnnpc <subcommand>` | NPC | `vnmine.command.npc.admin` |
| `/vnquestdialog admin` | Quest dialog | `vnmine.command.quest.admin` |
| `/vnportal <subcommand>` | Portal RPG | `vnmine.command.portal.admin` |
| `/vntext <subcommand>` | Text display | `vnmine.command.text.manage` |
| `/vnregion [wand]` | WorldEdit/vùng bảo vệ tích hợp | `vnmine.command.worldedit` |
| `/vnbuild [select]` | Schema Build | `vnmine.command.schemabuild` |
| `/vnevent [id ...|stop]` | Thiên tai | `vnmine.command.disasters` |
| `/vnboss <id> ...|kill` | Boss | `vnmine.command.bosses` |
| `/vntime set <day\|night> <phút> [world]` | Chu kỳ ngày/đêm | `vnmine.command.time.manage` |
| `/vnday set <ngày>` | Đặt ngày world hiện tại | `vnmine.command.day.manage` |
| `/vnspawn <subcommand>` | Spawn và vùng bảo vệ spawn | `vnmine.command.spawn.manage` |
| `/vndeathhistory [hoanbh]` | Lịch sử inventory khi chết | `vnmine.command.death.history` |
| `/vngroup`, `/vnuser` | Nhóm/quyền | `vnmine.command.groups.manage`, `vnmine.command.users.manage` |
| `/tp <hoanbh> <world>` | Teleport đến spawn world | `vnmine.command.teleport.other` |
| `/gm0` … `/gm3`, `/fly`, `/spawn` | Tiện ích admin | `vnmine.command.gamemode`, `.fly`, `.spawn` |
| `/vn` | Tiện ích và reset thống kê chết | `vnmine.command.utility`; reset: `vnmine.command.death.reset` |

Dùng `/vnhelp` để xem các lệnh được phép trong server cụ thể; một số alias hoặc subcommand có thể bị giới hạn theo cấu hình/permission.

## Permissions

Plugin dùng node quyền chính xác. Hệ thống nhóm nội bộ đọc `permission.yml`; OP và console luôn có quyền plugin. Có thể gán nhóm/quyền bằng `/vngroup` và `/vnuser`.

### Người chơi

Nhóm `user` mặc định được plugin thêm các quyền cơ bản: `vnmine.command.stats`, `help`, `utility`, `reward`, `respawn`, `leave`, `menu`, `quest`, `coin`, `skills`, `army`, `start`, `newbie-gift` và `vnmine.hardcore.stats`. Các node đầy đủ trong file bắt đầu bằng `vnmine.`. `/feed` và `/heal` không được cấp cho nhóm user mặc định.

### Quản trị theo chức năng

| Nhóm | Permission nodes tiêu biểu |
|---|---|
| Cấu hình server/world | `vnmine.command.init`, `vnmine.command.world.manage`, `vnmine.command.worldedit`, `vnmine.command.schemabuild`, `vnmine.command.spawn.manage` |
| NPC, quest, race/class | `vnmine.command.npc.admin`, `vnmine.command.quest.admin`, `vnmine.command.race.admin`, `vnmine.command.progression.admin`, `vnmine.command.skillbook.admin` |
| Người chơi và quyền | `vnmine.command.playeradmin`, `vnmine.command.groups.manage`, `vnmine.command.users.manage`, `vnmine.command.death.history`, `vnmine.command.death.reset` |
| Kinh tế | `vnmine.command.vncoin.admin`, `vnmine.command.vncoin.history.others` |
| Boss/thiên tai/thời gian | `vnmine.command.bosses`, `vnmine.command.disasters`, `vnmine.command.time.manage`, `vnmine.command.day.manage` |
| Tiện ích và vận hành | `vnmine.command.reload`, `vnmine.command.status`, `vnmine.command.gamemode`, `vnmine.command.feed`, `vnmine.command.heal`, `vnmine.command.fly`, `vnmine.command.spawn` |

Nhóm `admin` được tự bổ sung các quyền quản trị tích hợp khi plugin khởi tạo. Các quyền vẫn có thể thay đổi trong `permission.yml`; dùng `/vnreload` để nạp lại.

## Vận hành và xử lý sự cố

- Luôn sao lưu world trước khi đổi environment/seed hoặc xóa cấu hình đại lục.
- Nếu world không đổi sau khi sửa GUI, xác nhận đã bấm **Xác nhận**, kiểm tra `worlds/<id>.yml` và khởi động lại server. Đọc log `[World]` để biết world có được archive/tạo lại hay bị từ chối vì xung đột.
- Không gán cùng tên world cho nhiều cấu hình có environment hoặc seed khác nhau.
- Nếu GUI báo thiếu NPC, kiểm tra ID, loại NPC và world của NPC; NPC tutorial cần nằm đúng world được chọn.
- Nếu lệnh bị từ chối, kiểm tra permission node và nhóm UUID bằng `/vnuser info <player>`.
- Gửi báo cáo lỗi kèm phiên bản Paper/ProtocolLib/plugin, đoạn log liên quan và các bước tái hiện. Không gửi credential hoặc database người chơi công khai.

## Phát triển và đóng góp

- Mã nguồn Java/Maven nằm trong `VnMineHardcore/`.
- Dùng `mvn -DskipTests package` để đóng gói theo hướng dẫn dự án; chạy integration trên server thử nghiệm trước khi triển khai.
- Ghi thay đổi đáng chú ý trong `CHANGELOG.md` và cập nhật `plan.md` cho các phase nhiều bước.
- Dự án hiện được duy trì bởi **một người phát triển**; báo cáo rõ bước tái hiện và môi trường giúp xử lý nhanh hơn.
