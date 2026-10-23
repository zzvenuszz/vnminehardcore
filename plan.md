# Kế hoạch RPG mở rộng – Class, Skill, Quick Cast, Portal, NPC, Tutorial

> Bản kế hoạch này thay thế kế hoạch cũ để làm mốc phát triển tiếp theo. Mục tiêu là thiết kế một hệ thống RPG rõ ràng, dễ triển khai từng phase, không phụ thuộc vào kỹ thuật nguy hiểm hoặc chưa được kiểm chứng như packet keybind toàn cục hay skin NPC tùy biến phức tạp.

## 1. Mục tiêu tổng thể

1. Người chơi chọn class và học skill theo class đó.
2. Skill được học từ skill book, NPC trainer và quest milestone.
3. Quick cast cho phép cast skill nhanh bằng Q + slot 1–9, hoặc click trên GUI.
4. Player lên cấp, nhận skill point và phân bổ trong giao diện skill tree.
5. Admin tạo portal, đặt tên, ID, link cổng và validate qua lệnh.
6. NPC đóng vai trò tutorial, trainer, story, quest, merchant và gatekeeper.
7. Tutorial có particle, đường dẫn, waypoint và hướng dẫn rõ ràng.

## 2. Nguyên tắc thiết kế

- Dùng profile SQLite hiện có làm dữ liệu gốc cho `level`, `exp`, `skill points`, `learned skills`, `quick slots`.
- Không trộn `character EXP` với Minecraft XP vanilla hoặc EXP mercenary.
- Tất cả skill cần có định danh dạng `snake-case`/`kebab-case`: `fire-bolt`, `shield-wall`, `piercing-arrow`.
- Tách riêng 3 khái niệm: `skill unlock`, `skill rank`, `quick slot`.
- Quick cast ưu tiên GUI + click + phím số; không ép packet keybind chung nếu chưa xác thực rõ ràng.
- Portal không phụ thuộc WorldEdit; dùng tool `wooden axe` local của plugin.
- NPC sẽ triển khai theo kiểu stable vanilla entity, không giả định skin custom là có thể làm dễ dàng.
- Mỗi hệ thống phải có validation, fallback và log rõ ràng khi dữ liệu không hợp lệ.

## 3. Mô hình cấp độ và skill point

### 3.1 Cấp độ
- Level bắt đầu từ 1.
- Cap MVP: 30 level.
- EXP cần để lên level kế tiếp: `requiredExp(level) = 100 + 50 * (level - 1)`.
- Nếu nhận EXP lớn, hệ thống xử lý lên nhiều cấp trong một lần.
- Mỗi lần lên cấp nhận +1 skill point chưa dùng.
- Skill point không dùng cho race, class, mercenary, inventory hoặc server pvp.

### 3.2 Skill point đặt ở đâu
- Có 3 nơi tiếp cận chính:
  - Từ hub F: menu `Profile RPG`.
  - Từ lệnh `/vnskills`.
  - Từ NPC trainer hoặc portal station.
- GUI chính gồm:
  - `Overview`: level, exp, skill point còn lại, class đang sử dụng.
  - `Skill Tree`: danh sách skill đã học/chưa học/khóa.
  - `Quick Slots`: 1–9, mỗi slot gán 1 skill.
  - `Skill Details`: mô tả cooldown, mana, target, range, lore, điều kiện học.

### 3.3 Quy tắc phân bổ
- Mỗi skill có `rank` từ 0 đến 5.
- Rank 0 = chưa học.
- Rank 1 trở lên = đã học và có thể nâng cấp.
- Nâng rank phải tiêu tốn 1 skill point, không được phép âm.
- Skill point chỉ trừ khi DB cập nhật thành công.
- Nếu có lỗi DB, không trừ điểm và không coi như học thành công.

## 4. Class chính và skill đề xuất

Dưới đây là danh sách class phù hợp với thế giới VNMINE. 3 class đầu tiên sẽ là vertical slice đầu tiên để triển khai nhanh và có thể test kỹ. Các class còn lại dùng sau khi hệ thống core ổn định.

### 4.1 Swordsman – Kiếm sư
- Chức năng: cận chiến mạnh, áp sát, phản công.
- Skill 1: `blade-flare`
  - Mô tả: chém theo hướng nhìn trong một vùng cone ngắn.
  - Hiệu ứng: sát thương vật lý cao, 2–3 mục tiêu gần nhất trong cone.
  - Cooldown: 6s
  - Mana: 12
  - Required level: 2
  - Lore: “Đường kiếm chém ra như tia lửa, đủ để chặn cả đợt công kích.”
- Skill 2: `guard-break`
  - Mô tả: lùi một bước, khóa mục tiêu trong thời gian ngắn và phản đánh.
  - Hiệu ứng: giảm giáp mục tiêu, tạo stun cực ngắn.
  - Cooldown: 8s
  - Mana: 14
  - Required level: 4
- Skill 3 (passive): `battle-focus`
  - Mô tả: sau mỗi 3 đòn đánh trúng, tăng tốc độ đánh và sát thương trong 3s.
  - Required level: 1
  - Lore: “Một kiếm sư không chỉ giỏi đánh, mà còn biết chờ thời điểm thích hợp.”

### 4.2 Guardian – Hộ vệ
- Chức năng: bền, ngăn đòn, giữ tuyến.
- Skill 1: `shield-wall`
  - Mô tả: tạo lớp khiên chắn quanh người trong 4s.
  - Hiệu ứng: giảm sát thương nhận, chặn vài hit.
  - Cooldown: 10s
  - Mana: 15
  - Required level: 2
- Skill 2: `taunt`
  - Mô tả: gây thu hút kẻ địch trong vùng quanh mình.
  - Hiệu ứng: mục tiêu mất hướng, giảm tốc độ tấn công.
  - Cooldown: 12s
  - Mana: 10
  - Required level: 4
- Skill 3 (passive): `fortify`
  - Mô tả: mỗi lần nhận sát thương, tăng giáp tạm thời lên 3 stack.
  - Required level: 1
  - Lore: “Lớp chắn không cho ai tiến lên trước người giữ cổng.”

### 4.3 Archer – Cung thủ
- Chức năng: tầm xa, bắn xuyên, cắt đường trốn.
- Skill 1: `piercing-arrow`
  - Mô tả: bắn mũi tên xuyên 1 mục tiêu đầu tiên.
  - Hiệu ứng: sát thương cao, giảm tốc độ mục tiêu còn lại.
  - Cooldown: 7s
  - Mana: 13
  - Required level: 2
- Skill 2: `dash-shot`
  - Mô tả: bay lùi và bắn 2 viên đạn theo hướng nhìn.
  - Hiệu ứng: khoảng cách rút lui + shot + tăng tốc độ.
  - Cooldown: 9s
  - Mana: 14
  - Required level: 4
- Skill 3 (passive): `eagle-eye`
  - Mô tả: tăng tầm bắn, giảm độ lệch và tăng crit nhẹ.
  - Required level: 1
  - Lore: “Mắt chim có thể đọc gió, và từ đó đánh trúng kẻ thù.”

### 4.4 Mage – Pháp sư
- Chức năng: sát thương diện rộng, burn, control.
- Skill 1: `fire-bolt`
  - Mô tả: phóng tia lửa vào mục tiêu.
  - Hiệu ứng: burn và sát thương trực tiếp.
  - Cooldown: 5s
  - Mana: 12
  - Required level: 2
- Skill 2: `arcane-field`
  - Mô tả: tạo vùng lửa sóng trong 5s.
  - Hiệu ứng: giảm tốc và gây damage theo thời gian.
  - Cooldown: 11s
  - Mana: 18
  - Required level: 4
- Skill 3 (passive): `mana-flow`
  - Mô tả: hồi mana nhanh hơn khi cast liên tục.
  - Required level: 1
  - Lore: “Một pháp sư bình tĩnh luôn biết khi nào nên đốt cháy bầu trời.”

### 4.5 Sage – Tiên tri / Hỗ trợ
- Chức năng: hồi máu, buff, sustain.
- Skill 1: `heal-pulse`
  - Mô tả: hồi máu cho đồng minh gần nhất.
  - Hiệu ứng: hồi máu + xóa trạng thái hạ độc.
  - Cooldown: 9s
  - Mana: 15
  - Required level: 2
- Skill 2: `ward-bloom`
  - Mô tả: tạo vòng lá bảo hộ quanh đồng minh.
  - Hiệu ứng: giảm damage + hồi máu theo thời gian.
  - Cooldown: 12s
  - Mana: 18
  - Required level: 4
- Skill 3 (passive): `ethereal-focus`
  - Mô tả: tăng mana max và giảm mana cost cho skill hỗ trợ.
  - Required level: 1
  - Lore: “Một bông hoa có thể làm lành cả chiến trường.”

### 4.6 Rogue – Đạo chích
- Chức năng: crit, lén, bỏ chạy.
- Skill 1: `shadow-step`
  - Mô tả: dịch chuyển ngắn theo hướng nhìn.
  - Hiệu ứng: gây damage khi có kẻ địch ở cuối đường.
  - Cooldown: 6s
  - Mana: 10
  - Required level: 2
- Skill 2: `venom-slip`
  - Mô tả: đâm độc và giảm tốc độ mục tiêu.
  - Hiệu ứng: poison + giảm tầm nhìn/đi lại.
  - Cooldown: 8s
  - Mana: 12
  - Required level: 4
- Skill 3 (passive): `backstab-rhythm`
  - Mô tả: tăng crit khi đánh từ phía sau.
  - Required level: 1
  - Lore: “Bóng tối không phải để trốn mà để gõ cửa đúng lúc.”

### 4.7 Brawler – Đấm thủ
- Chức năng: tấn công gần, áp sát, đánh xung kích.
- Skill 1: `ground-pound`
  - Mô tả: đập xuống đất làm rung cả vùng.
  - Hiệu ứng: damage AOE + knockback nhẹ.
  - Cooldown: 7s
  - Mana: 13
  - Required level: 2
- Skill 2: `rush-upheaval`
  - Mô tả: lao nhanh đến mục tiêu và va bật mạnh.
  - Hiệu ứng: burst damage + stun cực ngắn.
  - Cooldown: 10s
  - Mana: 14
  - Required level: 4
- Skill 3 (passive): `iron-rhythm`
  - Mô tả: giảm cooldown khi đánh trúng liên tục.
  - Required level: 1
  - Lore: “Đấm thủ không cần vẫy tay, họ chỉ cần lao tới.”

## 5. Skill book thiết kế

### 5.1 Mục tiêu
- Skill book là vật phẩm dùng để học kỹ năng.
- Skill book có thể nhận qua quest, NPC trainer, loot, merchant, event world.
- Skill book không phải là phần nâng cấp rank; skill point mới dùng cho nâng rank.

### 5.2 Cấu trúc item
- item type: `BOOK`, `ENCHANTED_BOOK`, `WRITTEN_BOOK`
- metadata chứa:
  - `book_id`
  - `skill_id`
  - `class_id`
  - `required_level`
  - `rarity`
  - `requires_quest`
- lore mẫu:
  - “Skill Book: Blade Flare”
  - “Class: Swordsman”
  - “Required Level: 2”
  - “Use to learn the skill”
  - “Ancient inscription from the Old Forge”

### 5.3 Cơ chế học skill
Khi dùng sách:
- kiểm tra class phù hợp
- kiểm tra player đủ level
- kiểm tra chưa học trước đó
- kiểm tra inventory có đủ điều kiện
- nếu hợp lệ: lưu `learned_skill` và item bị tiêu thụ/biến thành giấy rách
- nếu không hợp lệ: item không bị consume, hiển thị message rõ ràng

### 5.4 Lore và nội dung
- Mỗi skill book nên có lore gắn với class và thế giới: sư, linh hồn, forge, shrine, temple, moon, dark wood...
- Ví dụ:
  - `swordsman` books themed around forge, oath, old battlefield.
  - `guardian` books themed around keep, wall, shield, watchtower.
  - `mage` books themed around spell rune, mana pillar, eclipse.
  - `sage` books themed around herb, moonwell, recovery, sacred memory.

## 6. Quick cast hoạt động thế nào?

### 6.1 Design ưu tiên
- Khi nhấn `Q`, mở GUI `Quick Cast`.
- GUI có tối đa 9 ô, mỗi ô gắn 1 skill đã được assign.
- Người chơi có thể click skill trong GUI để cast ngay.
- Không bắt toàn bộ phím máy tính nằm ngoài GUI nếu plugin không có nền tảng rõ ràng.

### 6.2 Phím bấm hợp lý
- `Q`: mở quick cast menu.
- `1`–`9`: cast skill gắn vào slot tương ứng khi GUI đang mở hoặc đang ở trạng thái skill-ready.
- `F`: đang dùng cho hub map/world switch, không nên cướp.
- `Shift + Q` hoặc `Ctrl + Q`: có thể dùng cho “toggle quick cast UI” nếu cần, nhưng nên đặt ở cấp config và không bắt cứng.
- `LMB`/`RMB`: dùng khi skill có target hoặc khi click trên GUI.

### 6.3 Không nên làm gì
- Không nên dùng packet keybind toàn cục để bắt phím trong giai đoạn đầu nếu chưa có kiểm chứng thực tế.
- Không nên xung đột với drop item, inventory mở, phiên bản game/GUI đang có.
- Không nên gắn `Q` cứng cho skill nếu server đã sử dụng `Q` làm drop item hoặc hành vi khác.

### 6.4 Fallback
- Nếu GUI quick cast bị lỗi, player vẫn có thể dùng `/vnskills` và `/vnskill bind <slot> <skill>`.
- Slot lưu vào SQLite profile, reload/logout không mất dữ liệu.
- Tại mỗi lần cast, plugin kiểm tra cooldown, mana, range, target validity và casting state.

### 6.5 UI quick cast
- Layout dạng 3x3 hoặc 2x5 tùy kích thước màn hình.
- Mỗi ô hiển thị:
  - tên skill
  - cooldown còn lại
  - mana cost
  - class
  - icon và màu trạng thái
- Trạng thái slot:
  - xanh: sẵn dùng
  - vàng: cooldown
  - xám: slot trống
  - đỏ: không đủ mana hoặc không thể cast

## 7. Skill point, rank và giao diện UI

### 7.1 Mục tiêu UI
- Giao diện phải rõ, ít chữ và dễ đọc trong gameplay.
- Mỗi skill hiển thị:
  - `Name`
  - `Class`
  - `Rank` / `Max Rank`
  - `Required Level`
  - `Mana Cost`
  - `Cooldown`
  - `Description`
- Format item GUI rõ ràng bằng màu:
  - xanh = đã học
  - vàng = có thể nâng cấp
  - đỏ = khóa
  - xám = không đủ điều kiện

### 7.2 Luồng nâng cấp
- Người chơi click skill trong `Skill Tree`.
- Nếu skill chưa học: chọn `Learn`.
- Nếu đã học và còn điểm: chọn `Upgrade`.
- Nếu skill không đủ điều kiện: hiển thị cảnh báo `level too low, missing class, insufficient skill point`.
- Hệ thống chỉ trừ skill point khi nâng cấp thực sự thành công.

## 8. Cổng dịch chuyển (portal)

### 8.1 Mục tiêu
Người chơi tạo cổng dịch chuyển bằng tay, đặt tên, ID, link tới cổng khác và có thể dùng trong thế giới RPG.

### 8.2 Thiết kế portal
Mỗi portal có dữ liệu:
- `id` (dạng slug, duy nhất)
- `name` (tên hiển thị)
- `world`
- `position`
- `bounds`
- `spawn_location`
- `link_id`
- `enabled`
- `required_level`
- `required_quest`
- `allowed_classes`

### 8.3 Cách xây portal
- Dùng item `WOODEN_AXE` làm `selection tool`.
- Người chơi click block A và B để chọn vùng cổng.
- `/vnportal create <id> "<name>"`: tạo portal từ vùng đã chọn.
- `/vnportal setspawn <id>`: đặt vị trí xuất hiện.
- `/vnportal link <portalA> <portalB>`: nối cổng A đến cổng B.
- `/vnportal unlink <portal>`: bỏ liên kết.
- `/vnportal validate`: kiểm tra ID trùng, world lỗi, link mất, target không hợp lệ.

### 8.4 Các lệnh đề xuất
```text
/vnportal wand
/vnportal pos1
/vnportal pos2
/vnportal create <id> "<name>"
/vnportal setspawn <id>
/vnportal link <idA> <idB>
/vnportal unlink <id>
/vnportal info <id>
/vnportal list
/vnportal enable <id>
/vnportal disable <id>
/vnportal remove <id>
/vnportal validate
```

### 8.5 Có dùng rìu gỗ như WorldEdit không?
- Có, nhưng chỉ là tool selection cục bộ của plugin, không dựa vào WorldEdit.
- Mục tiêu là hiệu quả và dễ test, không clone nguyên một plugin worldedit.
- Selection có thể lưu tạm theo UUID; nếu player mất kết nối, dữ liệu tạm được xoá hoặc reload.

### 8.6 UX portal
- Khi ở gần cổng, hiển thị particle ring.
- Khi active, có message dạng “Portal: Moon Gate -> Sun Gate”.
- Cổng có thể yêu cầu level hoặc quest để qua;
- Không cho teleport khi đang chết hoặc combat.

## 9. NPC hệ thống RPG

### 9.1 Số lượng NPC
- Có thể có hàng trăm NPC theo cấu hình, nhưng nên có giới hạn runtime `max-npcs-per-world` mặc định khoảng 100.
- Mỗi NPC có `id`, `type`, `name`, `world`, `location`, `interaction`, `dialogue`, `skin` nếu có.

### 9.2 Các loại NPC đề xuất
1. Tutorial NPC
   - Dẫn dắt người chơi mới.
2. Quest Giver
   - Nhận quest và check hoàn thành.
3. Trainer NPC
   - Học skill, mua skill book, luyện kỹ năng.
4. Merchant NPC
   - Bán vật phẩm, sách kỹ năng, vật liệu.
5. Story NPC
   - Cung cấp thoại, lore, nền truyện.
6. Gatekeeper NPC
   - Dẫn vào realm/area mới.
7. Healer NPC
   - Hồi phục, buff nhẹ, reset trạng thái.

### 9.3 GUI và quản lý NPC
- Có GUI admin để list + edit NPC.
- Có lệnh:
```text
/vnnpc create <id> <type>
/vnnpc rename <id> "<new name>"
/vnnpc move <id>
/vnnpc dialogue <id> <dialogue-id>
/vnnpc skin <id> <player-name|uuid>
/vnnpc info <id>
/vnnpc list
/vnnpc remove <id>
/vnnpc despawn <id>
```
- Có thể đổi tên của NPC hiển thị mà không đổi ID.
- GUI player: tùy loại NPC mở dialogue hoặc menu đa lựa chọn.

### 9.4 Skin NPC
- Skin thật/player skin là tính năng có thể làm được nhưng không nên ưu tiên ở vertical slice đầu.
- Không giả định NPC vanilla có thể set skin user theo ý muốn một cách đơn giản.
- Có thể dùng NPC template dạng villager, armor stand hoặc clickable entity để giữ ổn định.
- Skin custom nên đặt ở phase sau khi đã kiểm chứng kỹ thuật và không gây conflict với server.

## 10. Tutorial và waypoint

### 10.1 Mục tiêu tutorial
- Người chơi mới vào server được dẫn theo 5–7 bước ngắn:
  1. Chọn class.
  2. Nhận skill book.
  3. Học skill đầu tiên.
  4. Gán vào quick slot 1–3.
  5. Lên level 2 bằng quest.
  6. Đi qua portal đầu tiên.
  7. Nói chuyện với NPC trainer tiếp theo.

### 10.2 Waypoint và particle
- Dùng particle trail từ NPC đến điểm cần đến.
- Dấu liền ở đúng đường đi: `dust`, `portal`, `endrod`, `glow`, `trail`.
- Tại điểm chiến đấu/quest, hiện beacon/marker.
- Khi đi lệch hướng, hiển thị action bar: “Đi theo dải ánh sáng tới NPC huấn luyện”.
- Nếu có minimap hoặc map hiện có, thêm waypoint đó vào panel.

### 10.3 Cấu trúc quest tutorial mẫu
- `tutorial-01`: chào mừng và nhận class.
- `tutorial-02`: nhận skill book.
- `tutorial-03`: học 1 skill.
- `tutorial-04`: assign quick slot 1.
- `tutorial-05`: lên level 2.
- `tutorial-06`: mở portal và đi qua.
- `tutorial-07`: nói chuyện với NPC story.

## 11. Giai đoạn triển khai đề xuất

### Trạng thái hiện tại
- Phase 1 đã hoàn thành: character EXP, level-up, skill point và hiển thị thông tin progression.
- Phase 2 đã hoàn thành: registry skill/book YAML, học sách theo class/level và lưu learned skill trong SQLite.
- Phase 3 đã triển khai phần code và test: quick-cast GUI, slot 1–9, sáu skill chủ động, cooldown, mana hồi theo thời gian và skill tree nâng rank bằng skill point.
- Đã triển khai Tâm Kiếm cho swordsman: cộng sát thương đánh thường/đánh quét lên quái theo rank, mặc định +3% mỗi rank; học ở rank 1 và có thể nâng bằng skill point khi đạt cấp yêu cầu.
- Đã triển khai Thành Đồng cho guardian: bị quái thù địch đánh cộng stack, tối đa 3; mỗi stack giảm 2% sát thương mỗi rank và hết hạn sau 5 giây không bị quái đánh.
- Đã triển khai Mạch Pháp cho mage: sau cast chủ động thành công thứ hai trở đi trong chuỗi (tối đa 5 giây giữa cast), hồi thêm 1 mana mỗi rank; không vượt max mana.
- SQLite hiện tại là schema v6; migration v4→v5 bổ sung bảng quick slots và v5→v6 bổ sung mana, đều được kiểm thử.
- Phase 4 đã triển khai phần code và test: chọn vùng bằng rìu gỗ riêng, tạo/quản lý portal, đặt điểm đến, link hai chiều, kiểm tra điểm đến an toàn và particle marker.
- Paper smoke test đã xác nhận plugin và ProtocolLib khởi động trên Paper 26.3, `/vnportal list` và `/vnportal validate` hoạt động qua RCON; người dùng xác nhận đã hoàn thành các nhóm gameplay test trên client ngày 2026-10-23.
- Đã chuẩn bị Paper 26.3-209, ProtocolLib 5.5.0-SNAPSHOT và plugin 2.2.0 trong `TEST/`; server đã được người dùng chấp nhận EULA, khởi động thành công và listen trên `*:25565`.
- Playit agent v1.0.10 đã được claim; secret được giữ trong `.playit.toml` tại root dự án (permission 600, Git ignored). `TEST/run.sh` dùng lại agent đó và khởi chạy Paper. Người dùng xác nhận đã kết nối thành công qua tunnel `signaling-cheers.tun.ply.gg` ngày 2026-10-23.
- Bốn nhóm gameplay test còn mở trước đây đã được người dùng xác nhận hoàn thành ngày 2026-10-23.
- Kiểm chứng tự động ngày 2026-10-22: toàn bộ 99 Maven tests đạt; nhóm test trọng điểm portal/NPC/skill đạt 22/22. Kiểm thử Paper gameplay tiếp tục trên test server trong `TEST/`.

### Phase 0 – Cốt lõi dữ liệu và design lock
- [x] Chốt class, skill, naming convention và schema skill/book.
- [x] Thiết kế/validate schema NPC/dialogue: bảy loại vai trò, ID ổn định, display name, giới hạn nội dung thoại, kiểm tra khóa YAML trùng và giới hạn registry.
- [x] Thiết kế/validate các điểm còn lại của portal/NPC runtime trước khi triển khai tương ứng; schema, giới hạn dữ liệu và hành vi registry được kiểm tra bằng code cùng unit test. Kiểm thử tích hợp runtime vẫn theo dõi riêng ở Phase 4–5.

### Phase 1 – Level/EXP + skill point
- [x] Tăng level, EXP, skill point; hiển thị trong GUI profile.
- [x] Lưu trong SQLite và migration an toàn.
- [x] Cho phép lên cấp và nhận điểm chưa dùng.

### Phase 2 – Skill book + learning system
- [x] Tạo skill book item và lore.
- [x] Học skill theo class, level và điều kiện.
- [x] Lưu learned skill và rank ban đầu.
- [x] Admin có `/vnskillbook give`; người chơi xem skill bằng `/vnskills list`.

### Phase 3 – Quick cast + mana + skill tree (đã triển khai và gameplay test)
- [x] Mở `Quick Cast` GUI bằng `/vnskills`; Q mở GUI nếu nhân vật RPG đang hoạt động.
- [x] Gán/bỏ gán skill vào slot 1–9; lưu bền vững trong SQLite.
- [x] Cast bằng click GUI hoặc phím số khi GUI đang mở.
- [x] Thêm cast handler cho `blade-flare`, `guard-break`, `shield-wall`, `taunt`, `fire-bolt`, `arcane-field`; passive không thể gán.
- [x] Thêm cooldown cấu hình trong `skill-definitions.yml`; cooldown runtime reset khi logout/restart.
- [x] Kiểm tra class, learned skill, cooldown, phạm vi tia và vật cản; damage áp dụng qua event Bukkit và skill chỉ nhắm quái thù địch.
- [x] Thêm mana lưu bền vững, hồi theo thời gian có giới hạn max mana; không dùng vanilla XP/thanh đói thay thế.
- [x] Thêm GUI skill tree/chi tiết skill, hiển thị rank/điều kiện/điểm và nâng rank bằng skill point trong transaction nguyên tử.
- [x] Triển khai hiệu ứng passive `battle-focus` cho swordsman đã học sách: tăng sát thương đánh thường và sweep attack lên quái theo cấu hình mỗi rank; không ảnh hưởng PvP, projectile hoặc skill damage.
- [x] Nạp rank passive vào cache khi người chơi vào server/plugin khởi động, cập nhật sau khi học sách và xóa cache khi thoát; lỗi tải hồ sơ được log/báo rõ.
- [x] Hiển thị hiệu ứng Tâm Kiếm trong skill tree/chi tiết, cấu hình mức cộng ở `rpg.skills.passives.battle-focus.damage-bonus-per-rank` (mặc định 3%, giới hạn 0–10%).
- [x] Thêm unit test cho công thức Tâm Kiếm/Thành Đồng/Mạch Pháp, điều kiện chuỗi và giới hạn mana restore; toàn bộ 86 Maven tests đạt.
- [x] Cho phép nâng rank passive đã triển khai bằng skill point trong GUI theo điều kiện cấp tiếp theo; cập nhật cache rank sau transaction thành công.
- [x] Kiểm thử điều kiện cấp và trừ đúng 1 skill point khi nâng Tâm Kiếm; focused suite 31 test đạt, toàn bộ suite 80 test đạt.
- [x] Triển khai Thành Đồng cho guardian: chỉ nhận stack từ đòn của quái thù địch (kể cả projectile do quái bắn); stack hiện có giảm đòn tiếp theo, reset sau thời gian cấu hình; hỗ trợ nâng rank/cache.
- [x] Thêm cấu hình song ngữ cho mức giảm sát thương, stack cap và thời gian hết hạn; giảm tối đa 90% để không vô hiệu hóa sát thương hoàn toàn.
- [x] Thêm kiểm thử công thức giảm sát thương theo rank/stack và giới hạn an toàn; toàn bộ 83 Maven tests đạt.
- [x] Triển khai Mạch Pháp cho mage đã học sách: hồi mana thêm theo rank sau các cast active thành công tiếp diễn trong cửa sổ combo; chuỗi không lưu khi logout/restart.
- [x] Thêm thao tác khôi phục mana trong transaction SQLite, giới hạn ở max mana; lỗi DB được log và báo rõ sau cast.
- [x] Cho phép nâng rank Mạch Pháp bằng skill point, cập nhật cache rank sau khi nâng và hiển thị hiệu ứng/cấu hình trong GUI.
- [x] Thêm cấu hình song ngữ cho mana/cast/rank và cửa sổ chuỗi; kiểm thử helper và thao tác khôi phục mana; toàn bộ 86 Maven tests đạt.
- [x] Kiểm thử gameplay bằng Paper: Q/drop conflict, cast thực tế, bảo vệ vùng an toàn và cân bằng chỉ số.

### Phase 4 – Portal system (đã triển khai và gameplay test)
- [x] Chọn vùng cuboid bằng wooden axe có dấu nhận diện riêng hoặc lệnh pos1/pos2; không phụ thuộc WorldEdit.
- [x] Tạo, lưu, liệt kê, xem, bật/tắt, xóa và validate portal trong `portals.yml`.
- [x] Đặt điểm đến, link/unlink hai chiều và kiểm tra world, chunk, world border, khoảng trống phía trên cùng block sàn an toàn.
- [x] Dịch chuyển khi người chơi đi vào vùng portal; chặn khi chết/respawn/combat, có cooldown và chống lặp nếu điểm đến nằm trong vùng nhận.
- [x] Thêm particle ring cấu hình được, giới hạn số portal/kích thước vùng, quyền admin và hướng dẫn sử dụng.
- [x] Paper smoke test: ProtocolLib 5.5.0-SNAPSHOT và VnMineHardcore 2.0.4 bật thành công; RCON xác nhận list/validate và chặn thao tác tạo portal từ console.
- [x] Chuẩn bị launcher Paper + Playit trong `TEST/run.sh`, với agent chính thức và log riêng; launcher dùng lại secret đã claim thay vì tạo agent mới.
- [x] Kiểm thử Paper: trigger theo vùng, teleport qua nhiều world/chunk, combat/death gate, particle và reload/restart persistence.

### Phase 5 – NPC + tutorial + quest flow
- [x] Tạo registry definition NPC YAML có schema version, bảy loại vai trò và mẫu thoại cho tutorial guide, class trainer, story keeper.
- [x] Nạp/validate registry khi plugin khởi động; file lỗi được log rõ và từ chối khởi động plugin.
- [x] Tạo nhiều NPC runtime bằng villager vanilla có tag/UUID ổn định; lưu/khôi phục theo chunk, lệnh `/vnnpc create|rename|move|remove|info|list`, quyền admin và giới hạn cấu hình 1-100.
- [x] Kiểm thử Paper: tạo nhiều NPC, restart/chunk unload-load, rename/move/remove và bảo đảm không trùng entity.
- [x] Nhấp phải NPC mở dialogue GUI; hiển thị tối đa 20 dòng theo ID/role definition, chặn kéo vật phẩm và có nút đóng.
- [x] Nối trainer/quest giver với skill book, quest flow và các hành động trong GUI: action cấu hình mở quest journal/skill tree hoặc cấp skill book; kiểm tra không cấp trùng sách đã học/đang có.
- [x] Thêm tutorial waypoint: action bar theo dõi vị trí/khoảng cách spawn và particle End Rod dẫn đường trong tầm cấu hình.
- [x] Kiểm thử gameplay waypoint bằng Paper trên client thực tế; server và tunnel public đã hoạt động.

## 12. Rủi ro và quyết định cốt lõi

- Không ép `packet keybind` nếu không có bằng chứng kỹ thuật tốt.
- Không giả định NPC custom skin hoạt động ổn định trên mọi môi trường.
- Không để logic cast bị nhồi vào listener quá lớn; cần tách `SkillEngine`, `SkillManager`, `PortalManager`, `NpcManager`.
- Mọi UI phải có fallback khi GUI không hoạt động hoặc item bị thiếu.
- Tất cả data config cần có comment tiếng Việt + tiếng Anh, schema rõ ràng.

## 13. Kết luận

Hệ thống RPG của VNMINE nên đi theo hướng “vừa chơi được, dễ test, dễ mở rộng.” Class là nền tảng và cho mỗi class 3–4 kỹ năng khác nhau. Skill book là cách học skill, skill point là cách nâng cấp, quick cast là cách dùng skill nhanh trong combat. Portal và NPC tạo ra không gian di chuyển, hướng dẫn và narrative; tutorial + particle giúp người chơi không bị lạc. Với roadmap này, mỗi phase đều có giá trị gameplay rõ ràng và không đòi hỏi rewrite toàn bộ hệ thống.

Các checklist Phase 0–5 hiện không còn mục chưa hoàn thành. Chưa có Phase 6 được định nghĩa. Template NPC tutorial và quest đã có, nhưng runtime `TEST/server/plugins/VnMineHardcore/npcs.yml` hiện chưa chứa NPC instance; bước tiếp theo là chọn world/vị trí để đặt NPC tutorial đầu tiên.
