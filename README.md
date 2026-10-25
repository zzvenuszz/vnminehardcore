# 🔥 VnMineHardcore

**Hell Difficulty Plugin for Paper 26.3**

Plugin Paper 26.3 bổ sung các hệ thống sinh tồn khó hơn. Người chơi không bị ban khi chết.

---

## 📋 Table of Contents

- [Features](#-features)
- [Requirements](#-requirements)
- [Installation](#-installation)
- [Commands](#-commands)
- [Permissions](#-permissions)
- [Configuration](#-configuration)
- [Disaster Config Guide](#-disaster-config-guide)
- [Boss Config Guide](#-boss-config-guide)
- [Log Files](#-log-files)
- [Changelog](#-changelog)

---

## 🎯 Features

### ☠️ Death System
- Ghi nhận số lần chết, broadcast và âm thanh Wither; không ban người chơi
- Combat tag 30 giây để hiển thị trạng thái giao chiến; thoát game không bị phạt

### 💀 Death Penalty System
- Mỗi lần chết cộng 1 stack, tối đa 5; mặc định mỗi stack giảm max HP 10%
- Áp dụng giảm damage gây ra, tăng damage nhận vào, giảm regen, tăng drain đói/khát và giảm max thirst theo các mức trong config
- Penalty bắt đầu hồi sau respawn và tự gỡ sau 300 giây theo thời gian thực

### 🏷 Death Rename System
- **Tự động đổi tên hiển thị** trong CHAT và TAB LIST sau khi chết
- **Placeholder hỗ trợ**: `<name>`, `<count>`, `000` (padding số 0)
- **Ví dụ**: `<name> ☠ <count>` → `hoanbh ☠ 3`
- Không ảnh hưởng đến tên thật của người chơi

### ⚔️ Combat
- **Damage multipliers**: Mob x2, Fall x2, Fire x2, Drowning x2, Explosion x1.5, PvP x1.25
- Mobs có thêm máu (x1.5) + armor ngẫu nhiên
- **Natural Regen**: Có thể tắt hoàn toàn hoặc giảm % (configurable regen-multiplier)
- **Combat Tag**: 30 giây mặc định; chỉ hiển thị trạng thái giao chiến
- Ender Pearl gây 1 HP sát thương theo config mặc định
- Táo vàng enchanted (Notch Apple) phát nổ
- Friendly mobs không kích hoạt combat tag
- **Witch Speed Boost**: Witch có Speed II vĩnh viễn
- **Creeper Explosion x2**: Bán kính nổ Creeper gấp đôi
- **Skeleton Arrow Poison**: Mũi tên Skeleton gây Poison + Slowness

### 🍔 Hunger System
- Mỗi 180 giây giảm 1 food; đói gây 1 HP sát thương mỗi nhịp
- Ăn hồi 50% (configurable), không saturation
- Ngộ độc thức ăn sống: thịt thối, cá nóc, khoai tây độc...
- **Nghẹn** khi ăn >3 lần trong 10s
- Suspicious Stew: hiệu ứng ngẫu nhiên

### 💧 Thirst System
- Thanh khát (action bar) với màu sắc & trạng thái
- Mỗi 120 giây giảm 1 điểm; hết khát gây 2 HP sát thương mỗi nhịp
- Uống từ: chai nước, xô nước, nguồn nước (sông/biển/vạc)
- **Natural Water**: Uống nước sông/biển = damage mỗi giây + Nausea + Hunger
- Tất cả thông số đều configurable

### 🌍 Environment
- **Nhiệt độ**: Nóng ở sa mạc (burn), lạnh ở tuyết (slow)
- **Sương mù**: Darkness effect configurable (interval, duration, amplifier)
- **Chặn ngủ**: Damage khi cố ngủ
- **Cây chậm**: 25% chậm hơn
- **Công cụ hao mòn**: 1.5x nhanh hơn
- **Chặn bay** (mặc định tắt; permission bypass nếu bật)
- **Sợ hầm**: Mining Fatigue + Darkness dưới y=30 (giảm khi cầm đuốc)
- **Chóng mặt**: Nausea + Slowness trên y=200 (giảm khi đeo elytra)
- **Nước lạnh**: Damage khi ở dưới nước lạnh
- **Mưa axit**: Damage khi ở ngoài trời mưa
- **Kiểm soát mưa**: Mặc định 40% xác suất bắt đầu mưa, thời lượng ngẫu nhiên 1–5 phút; cấu hình tại `environment.rain-control`

### 🏘️ Villager Trading System
- **Quản lý giao dịch dân làng theo biome**
- **Chặn random nghề** khi đặt workstation
- **Region system**: Mỗi vùng 500x500 blocks có data trade riêng
- Cùng biome nhưng khác vùng → data trade khác nhau

### 🪤 Spawner Control
- **Giảm tỷ lệ spawn** từ lồng farm (mặc định 30%)
- **Tăng HP** cho mob từ spawner (x3)
- **Tăng damage** cho mob từ spawner (x2, dạng Strength effect)

### ⛏️ Ore Control
- **Giảm tỷ lệ quặng** theo từng world
- Hỗ trợ tất cả loại quặng (overworld, deepslate, nether)
- Cấu hình riêng cho từng loại quặng
- Áp dụng khi tạo world mới

### 🌊 World Interaction
- **Fix cobblestone/stone generation**: Không sinh khoáng sản ngẫu nhiên khi lava + nước
- Hỗ trợ blackstone/cobbled deepslate generation

### 👹 Boss Events (13 types)
12/13 boss bật mặc định; `ender_dragon` tắt mặc định. Trạng thái được cấu hình trong file riêng:

| ID | Name | Entity Type | HP | Damage |
|----|------|-------------|-----|--------|
| `wither` | §c§lWither Huyền Thoại | Wither | 600 | x2.0 |
| `ender_dragon` | §5§lRồng Tử Thần | Ender Dragon | 1000 | x3.0 |
| `ghast` | §e§lGhast Khổng Lồ | Ghast | 100 | x2.5 |
| `zombie_boss` | §2§lZombie Boss | Zombie | 150 | x1.5 |
| `skeleton_boss` | §8§lSkeleton Boss | Skeleton | 120 | x2.0 |
| `spider_boss` | §0§lSpider Boss | Spider | 130 | x1.8 |
| `creeper_boss` | §a§lCreeper Boss | Creeper | 200 | x3.0 |
| `enderman_boss` | §5§lEnderman Boss | Enderman | 180 | x2.2 |
| `witch_boss` | §9§lWitch Boss | Witch | 140 | x2.0 |
| `ravager_boss` | §4§lRavager Boss | Ravager | 250 | x2.5 |
| `vindicator_boss` | §c§lVindicator Boss | Vindicator | 160 | x2.0 |
| `phantom_boss` | §8§lPhantom Boss | Phantom | 100 | x1.5 |
| `giant_boss` | §6§lKhổng Lồ | Giant | 400 | x2.5 |
| `giant_boss` | §c§lGiant Boss | Giant | cấu hình trong file boss | cấu hình trong file boss |

**Manual trigger**: `/vnboss <id> <warning> <duration>`

**Boss AI Features:**
- **Priority targeting**: Player → Friendly mob → Hostile mob → Wander
- Boss attacks and explosions do not destroy blocks
- **Smart movement**: Tránh kẹt, teleport khi cần
- **Unique behaviors**: Mỗi boss có AI đặc trưng
- **Immunities**: Configurable (sunlight-burn, fire, fall-damage, wither, poison)
- **Drops**: Configurable với chance, min/max amount
- **Level loot**: Mỗi boss có rare drops riêng theo level, lore/enchant/over-enchant và sách enchant; broadcast danh sách cùng tỉ lệ khi spawn
- **Boss leaderboard**: Sidebar sát thương trực tiếp; top 5 đóng góp chia `10 × level` điểm theo tỉ lệ damage
- **Mùa thưởng**: Reset theo ngày trong game; top 10 nhận quà một lần qua `/reward`, nhắc người chơi còn quà

### 🌋 Disasters (14 types)
Mỗi thiên tai có công tắc `enabled` riêng trong file cấu hình. `chorus-explosion` **tắt mặc định**:

| ID | Name | Dimension | Effect |
|----|------|-----------|--------|
| `blood-moon` | 🌕 Blood Moon | Overworld | Quái spawn x10, Strength + Speed |
| `meteor` | ☄️ Meteor Shower | Overworld | Sao băng rơi, explosion damage |
| `mega-storm` | 🌊 Mega Storm | Overworld | Sét đánh, mưa axit, mù |
| `solar-flare` | 🔥 Solar Flare | Overworld | Burn + blindness ngoài trời |
| `plague` | 🦠 Plague | Overworld | Poison + Weakness + Hunger |
| `tornado` | 🌪️ Tornado | Overworld | Bị ném lên cao, phá block |
| `eclipse` | 📉 Solar Eclipse | Overworld | Ban ngày → đêm, quái spawn |
| `earthquake` | 🌍 Earthquake | Overworld | Block rơi, rung chuyển |
| `inferno-storm` | 🔥 Inferno Storm | Nether | Lửa địa ngục, spawn Ghast/Magma |
| `soul-eruption` | 💀 Soul Eruption | Nether | Soul sand nổ, Wither effect |
| `lava-geyser` | 🌋 Lava Geyser | Nether | Cột lava phun trào |
| `end-surge` | 👁️ End Surge | The End | Spawn Shulker/Endermite |
| `void-storm` | 🌌 Void Storm | The End | Blindness + Darkness + damage |
| `chorus-explosion` | 🌀 Chorus Explosion | `world_the_end` | Damage + random teleport (mặc định tắt) |

**Manual trigger**: `/vnevent <id> <warning> <duration>`

Earthquake also launches exposed players horizontally and vertically. Tune
`velocity-y`, `velocity-x-range`, and `velocity-z-range` in
`disasters/earthquake.yml`.

At warning and start, chat also shows each event's editable effect/prevention
summary (`chat-description`) and the loaded world names affected. Edit that
summary and `conditions.worlds` in the event's own
`plugins/VnMineHardcore/disasters/<id>.yml` file.

**Disable specific disasters:**
```yaml
enabled: false  # in plugins/VnMineHardcore/disasters/chorus-explosion.yml
```

---

## 📥 Requirements

| Requirement | Version |
|-------------|---------|
| **Server** | Paper 26.3 |
| **Java runtime** | 25+ |
| **Compile API** | Paper API `26.3.build.175-stable` |
| **Required plugin** | ProtocolLib `5.5.0-SNAPSHOT` dev-build with Minecraft 26.3 support ([official builds](https://github.com/dmulloy2/ProtocolLib/releases/tag/dev-build)) |

---

## 📥 Installation

1. Download `VnMineHardcore.jar` from the [releases page](#)
2. Install the required ProtocolLib `5.5.0-SNAPSHOT` dev-build, then place both JAR files in `plugins/`
3. Restart your server (or use `/plugman load VnMineHardcore`)
4. Edit `plugins/VnMineHardcore/config.yml` to your liking
5. Use `/vnreload` to reload config without restarting

---

## 🎮 Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/vnstats` | View your stats (player only: deaths, survival time, mob kills) | `vnmine.command.stats` |
| `/vnhardcore` | View plugin status | `vnmine.command.status` |
| `/vn death reset <player>` | Reset a player's death count (online or previously joined player) | `vnmine.command.death.reset` |
| `/vnevent [<id> <warning-seconds> <duration-seconds>\|stop]` | List, trigger, or stop disasters | `vnmine.command.disasters` |
| `/vnboss <id> <level> <warning-seconds> <duration-seconds>` | Manually trigger a boss | `vnmine.command.bosses` |
| `/vnboss kill` | End the active boss without kill points or drops | `vnmine.command.bosses` |
| `/reward` | Claim pending boss rewards; inventory must have enough space | `vnmine.command.reward` |
| `/scoreboard reset` | Clear season scores and pending rewards; aliases: `/vnscoreboard reset`, `/sb reset` | `vnmine.command.scoreboard.reset` |
| `/vntime set <day\|night> <minutes> [world]` | Set global day/night length or a loaded world's override | `vnmine.command.time.manage` |
| `/vnday set <day>` | Set the current world to dawn of the specified in-game day | `vnmine.command.day.manage` |
| `/tp <user> <world>` | Teleport an online player to the selected world's spawn | `vnmine.command.teleport.other` |
| `/vnspawn set\|go\|protect\|list\|remove` | Manage world spawn and protected regions | `vnmine.command.spawn.manage` |
| `/vnrespawn`, `/vnleave` | Choose respawn or leave after death | `vnmine.command.respawn`, `vnmine.command.leave` |
| `/fly`, `/nodame`, `/spawn` | Player-only admin utilities | `vnmine.command.fly`, `.nodame`, `.spawn` |
| `/gm0`, `/gm1`, `/gm2`, `/gm3` | Switch game mode | `vnmine.command.gamemode` |
| `/vnreload` | Reload config and plugin systems | `vnmine.command.reload` |
| `/vnhelp`, `/vn help` | Show command help | `vnmine.command.help`, `.utility` |
| `/vngroup ...` | Create/delete groups and grant/revoke group permissions | `vnmine.command.groups.manage` |
| `/vnuser ...` | Inspect users, assign groups, manage personal permission grants | `vnmine.command.users.manage` |
| `/vnarmy ...` | Hire/manage mercenaries; rename with `/vnarmy rename <ID> <Tên mới>` | `vnmine.command.army` |
| `/vnnpc ...` | Create, rename, move, inspect, list, or remove RPG NPCs | `vnmine.command.npc.admin` |
| `/quatanthu` | Nhận túi quà tân thủ và chọn 1 token triệu hồi lính đánh thuê bậc 1 | — |

### `/vnevent` Usage

```
/vnevent                                    - List all disaster IDs
/vnevent blood-moon 30 60                  - Blood Moon: 30s warning, 60s duration
/vnevent meteor 10 30                      - Meteor Shower: 10s warning, 30s duration
/vnevent earthquake 5 20                   - Earthquake: 5s warning, 20s duration
/vnevent inferno-storm 15 40               - Inferno Storm: 15s warning, 40s duration
/vntime set day 20                         - Set default day length to 20 minutes
/vntime set night 15 world_resource        - Set night length for world_resource
/vnday set 100                             - Set the current world to dawn of day 100
```

`/vnevent` takes `<id> <warning-seconds> <duration-seconds>` or `stop`. `/vnboss` takes
`<id> <level> <warning-seconds> <duration-seconds>`. Tab completion suggests
valid IDs, world names, and common values for each parameter. Omitting
`[world]` changes the global default; specifying a loaded world stores an
override for that world. `/vnday` is player-only and changes the current
world's full time to the start of the requested day (day 0 is world creation).
For spawn protections, `X` is a positive integer radius measured horizontally
from the player's current block; the protection spans every Y level in that
world. Players cannot place or break blocks in the protected area by default.
Explosions, fire, liquids and disaster block changes are also prevented from
damaging protected blocks. OPs and players with
`vnmine.spawnprotection.bypass` may place/break blocks there; this bypass does
not allow disasters or explosions to damage the area. Protection data is stored
in `plugins/VnMineHardcore/spawns-protection.yml`.
The `/tp` and `/vnspawn` command parameters also provide Tab completion.

### Groups and permissions

`plugins/VnMineHardcore/permission.yml` stores groups and user assignments.
The default `user` group has basic play commands; `admin` has every plugin
permission. Use `/vngroup list|info|create|delete|permission` to manage groups,
and `/vnuser info|group|permission|reset` for players. Permission grants use
exact Bukkit permission nodes; wildcards, explicit denies and group inheritance
are not supported. Group grants and per-user grants are additive. OPs and the
console always have all plugin permissions. The default group is assigned to
new players; changing it does not move players with a saved assignment.
Run `/vnreload` after manual edits.

For predictable access, use only one permission manager as the source of truth
for `vnmine.*` nodes; grants from another plugin can combine with these grants.
Limit `vnmine.command.groups.manage` and `vnmine.command.users.manage` to trusted
staff, and remember OP cannot be restricted by these groups. There are no deny
rules or inheritance, so remove a grant from every group/user source that adds it.

**All Disaster IDs:**
- `blood-moon` - 🌕 Blood Moon
- `meteor` - ☄️ Meteor Shower
- `mega-storm` - 🌊 Mega Storm
- `solar-flare` - 🔥 Solar Flare
- `plague` - 🦠 Plague
- `tornado` - 🌪️ Tornado
- `eclipse` - 📉 Solar Eclipse
- `earthquake` - 🌍 Earthquake
- `inferno-storm` - 🔥 Inferno Storm
- `soul-eruption` - 💀 Soul Eruption
- `lava-geyser` - 🌋 Lava Geyser
- `end-surge` - 👁️ End Surge
- `void-storm` - 🌌 Void Storm
- `chorus-explosion` - 🌀 Chorus Explosion

### `/vnboss` Usage

```
/vnboss                                    - List all boss IDs
/vnboss wither 1 30 120                    - Wither Boss Lv.1: 30s warning, 120s duration
/vnboss ender_dragon 1 60 180              - Ender Dragon Lv.1: 60s warning, 180s duration
/vnboss zombie_boss 2 20 90                - Zombie Boss Lv.2: 20s warning, 90s duration
```

**All Boss IDs:**
- `wither` - §c§lWither Huyền Thoại
- `ender_dragon` - §5§lRồng Tử Thần
- `ghast` - §e§lGhast Khổng Lồ
- `zombie_boss` - §2§lZombie Boss
- `skeleton_boss` - §8§lSkeleton Boss
- `spider_boss` - §0§lSpider Boss
- `creeper_boss` - §a§lCreeper Boss
- `enderman_boss` - §5§lEnderman Boss
- `witch_boss` - §9§lWitch Boss
- `ravager_boss` - §4§lRavager Boss
- `vindicator_boss` - §c§lVindicator Boss
- `phantom_boss` - §8§lPhantom Boss
- `giant_boss` - §6§lKhổng Lồ

---

## 🔐 Permissions

Access is split by command/function, with exact nodes in the command table
above and declarations in `plugin.yml`. The built-in `user` group receives
basic play commands; `admin` receives all VnMineHardcore permission nodes.
Operators and the console have all plugin permissions by default.

| Permission | Use |
|------------|-----|
| `vnmine.command.stats`, `.help`, `.utility` | Stats and basic help/utility commands |
| `vnmine.command.reward`, `.respawn`, `.leave` | Player reward and post-death actions |
| `vnmine.command.status`, `.reload` | Plugin status and configuration reload |
| `vnmine.command.disasters`, `.bosses` | Manually manage disasters and bosses |
| `vnmine.command.spawn`, `.spawn.manage`, `.teleport.other` | Spawn travel, protection, and teleporting another player |
| `vnmine.command.fly`, `.nodame`, `.gamemode` | Admin player-testing commands |
| `vnmine.command.time.manage`, `.day.manage`, `.scoreboard.reset` | World time and scoreboard administration |
| `vnmine.command.death.reset` | Reset player death statistics |
| `vnmine.command.groups.manage`, `.users.manage` | Manage permission groups and player assignments |
| `vnmine.command.army` | Hire and manage a player's mercenary army (default: true) |
| `vnmine.command.army.admin` | Grant summon tokens or mercenaries with `/vnarmy give` (default: op) |
| `vnmine.command.skillbook.admin` | Give configured RPG skill books via command or admin GUI (default: op) |
| `vnmine.command.progression.admin` | Grant character RPG EXP/levels from the admin GUI (default: op) |
| `vnmine.command.npc.admin` | Create, rename, move, inspect, list, and remove RPG NPCs (default: op) |
| `vnmine.spawnprotection.bypass` | Let a non-operator place/break blocks inside protected regions |

The old `vnmine.hardcore.admin` and `vnmine.hardcore.stats` nodes remain
registered for legacy integrations, but command checks use the granular nodes
above. Migrate old permission assignments to the corresponding command nodes;
`vnmine.hardcore.admin` still participates in the existing environment bypass.

---

## ⚙️ Configuration

### Mercenaries

Mercenary classes, skill sets, follow/target distances, unit caps, and tier costs
are configured in `plugins/VnMineHardcore/armies.yml`. Each hire consumes a
matching class-and-level summon token, the configured XP levels, and materials.
Boss kills that meet the configured contribution threshold can drop a generic
summon token whose level matches the boss. The chance is configurable under
`army-token-drop` in `plugins/VnMineHardcore/bosses/_settings.yml`. An operator
can grant a generic token or a specific mercenary:

```text
/vnarmy give <player> token <level>
/vnarmy give <player> army <class> <level>
```

Players can use `/vnarmy gui`, `/vnarmy hire <class> <level>`, `/vnarmy list`,
`/vnarmy recall`, `/vnarmy rest`, `/vnarmy attack`, `/vnarmy defend`, and
`/vnarmy protect`. Use `/vnarmy gear <ID>` to open the selected unit's gear
inventory; changes are saved when it closes and kept when the unit rests,
the owner logs out, or the server stops. `recall` reconciles the stored unit ID
with entities carrying its persistent tag before creating a replacement.
`armies.yml` supports per-class skill lists and per-skill cooldown, target,
range, damage/healing amount and potion settings. `/vnarmy give ... army`
directly adds a unit but still enforces the configured unit cap. Admin grant
commands require `vnmine.command.army.admin`; player commands use
`vnmine.command.army`.

File: `plugins/VnMineHardcore/config.yml`

All parameters have sensible defaults and are documented with **bilingual comments** (Tiếng Việt + English).
Existing configs migrate bundled death-penalty defaults once; values different from the previous defaults are preserved.

### RPG Profile Database

Player profiles are stored in `plugins/VnMineHardcore/data/player_profiles.db`.
Existing profiles are loaded into the player's session on join and removed from
the in-memory cache on quit. Reconnecting or restarting the server does not
create or overwrite an existing profile.
Backups are kept under `plugins/VnMineHardcore/data/backups/`. The defaults in
`config.yml` create a backup at startup, before profile writes/schema migrations,
and every six hours; only the 12 newest snapshots are retained. `/vnreload`
applies changes to these settings without restarting the server.

| Setting | Default | Valid range | Purpose |
|---|---:|---:|---|
| `rpg.profile-database.backup-interval-hours` | `6` | 1-168 | Periodic backup interval |
| `rpg.profile-database.backup-retention-count` | `12` | 1-100 | Number of recent backups to retain |
| `rpg.profile-database.write-retries` | `2` | 0-3 | Additional retries for transient SQLite busy/locked errors |
| `rpg.profile-database.write-retry-delay-ms` | `50` | 10-250 | Delay between write retries |

Invalid values use the documented defaults and are reported in the server log.
Only transient busy/locked database errors are retried; constraint and data
errors are returned immediately. The persistence layer also supports unique
world unlocks and idempotent quest start, step updates, and completion. Tutorial
completion and its character-XP reward are committed together in SQLite, so
retries or server restarts cannot award the same XP twice.

Character progression uses the profile's RPG `level` and `exp` fields, separate
from vanilla Minecraft XP and mercenary XP. By default, level 1 requires 100
character XP to reach level 2; each later level costs 50 more XP than the
previous one, up to level 30. Each level gained awards one unspent skill point.
These values can be adjusted under `rpg.progression` in `config.yml`; invalid
values are logged and replaced with the documented defaults. The character
profile GUI shows current level, progress toward the next level, and unspent
skill points.

The SQLite schema is now version 6. During the transactional v2-to-v3 migration,
existing profile XP is applied to the level curve, preserving any remainder;
existing level is retained and the resulting level-ups grant their corresponding
skill points. The v3-to-v4 migration adds persistent learned-skill/rank records;
v4-to-v5 adds persistent quick-slot assignments; v5-to-v6 adds persisted mana
and its regeneration timestamp. Each migration is transactional, and a
pre-migration database backup is created.

### RPG Skills: Skill Books

Skill definitions and skill-book contents are stored in
`plugins/VnMineHardcore/skills/skill-definitions.yml` and
`plugins/VnMineHardcore/skills/skill-books.yml`. The bundled MVP contains nine
skills for the existing `swordsman`, `guardian`, and `mage` classes. Definitions
are validated at startup; invalid schema versions, duplicate YAML keys, unknown
class/skill IDs, invalid materials, or inconsistent book links are reported and
prevent the skill system from starting.

An administrator can issue a configured book to an online player:

```text
/vnskillbook give <player> <book-id>
```

This command requires `vnmine.command.skillbook.admin` (default: operators).
The admin GUI also provides **Quản trị → RPG → Give sách kỹ năng**. Select an
online player, then a configured book. It uses the same book generation as the
command and reports an error if the player is offline or their inventory is full.

The **Quản trị → RPG → Quản lý EXP / level** menu provides +100/+500/+1000
character EXP and +1/+5 level actions. EXP follows the configured progression
curve and awards skill points for each level gained; direct level grants also
award skill points and stop at the configured maximum level. This is character
RPG progression, not vanilla Minecraft XP. The separate
`vnmine.command.progression.admin` permission (default: operators) controls
these actions. Both RPG admin tools can be hidden with
`menus.admin.rpg-tools.enabled: false`.

Players right-click the book in their main hand to learn its skill. A successful
learn consumes one book and stores rank 1 in SQLite. Wrong-class, insufficient-
level, already-learned, or database-failure cases do not consume the book.
Each character level gained grants one unspent skill point. Open `/vnskills tree`
to see class skills and current ranks; click a skill for details, then click the
upgrade button to spend one point when its next-rank level requirement is met.
The “Quản lý kỹ năng” button in the main menu opens the same tree; the tree has
a button to return to the main menu, and skill details has a button to return
to the tree. Players can also use `/vnskills tree`. Players open the Quick Cast
GUI with `/vnskills` (permission
`vnmine.command.skills`, default: everyone). Click an empty slot to choose a
learned active skill; left-click an assigned skill to cast it and right-click
to unassign it. While the GUI is open, the number keys 1-9 cast the matching
quick slot. `/vnskills list` prints the learned skill list. Raising an active
skill rank costs one skill point, requires
the skill book unlock, and requires character level `required-level + current
rank`. The skill-point spend and rank increase are committed in one SQLite
transaction.

Pressing Q opens Quick Cast by default, including while a container is open. Its
menu contains one row for quick slots 1-9; Minecraft still displays the
player's inventory below plugin inventories. Pressing a number key casts that
slot and closes Quick Cast whether the skill succeeds or reports a failure.
Using Q on an item slot cancels that drop and opens Quick Cast. Dragging an item
outside a chest or container uses the normal mouse-drop behavior and does not
open the menu. Administrators can disable either `rpg.skills.quick-cast.enabled` or
`rpg.skills.quick-cast.intercept-drop-key` in `config.yml` to retain normal Q
behavior. Skill definitions specify `cast-type` (`active` or `passive`) and
`cooldown-seconds` (active: 1-120; passive: 0). Cooldowns reset when a player
logs out or the server restarts.

The initial cast handlers cover `blade-flare`, `guard-break`, `shield-wall`,
`taunt`, `fire-bolt`, and `arcane-field`; passive skills cannot be assigned.
Damage continues through normal Bukkit damage events, preserving world and
combat protections. Skill casts spend each definition's `mana-cost`. Maximum
mana and regeneration are configured under `rpg.skills.mana` in `config.yml`;
mana state and its regeneration timestamp are stored in SQLite, including
elapsed-time regeneration after a restart. Skill rank upgrades spend skill
points; learning books and casting do not.

`battle-focus` is implemented for a swordsman who has learned its skill book:
it increases direct melee and sweep-attack damage against hostile monsters by
the configured percentage per rank. It does not affect PvP, projectile damage,
or active-skill damage. Set
`rpg.skills.passives.battle-focus.damage-bonus-per-rank` (default `0.03`,
valid `0.0`-`0.10`) in `config.yml` to tune it. Its learned rank starts at 1;
further ranks can be bought with one skill point each when the character meets
the same next-rank level requirement shown in the skill details GUI.
`fortify` is implemented for a guardian who learned its skill book: each
hostile-mob hit adds a stack, up to three, and existing stacks reduce the next
hostile-mob hit by 2% per stack per rank. Stacks expire after five seconds
without a hostile-mob hit. The reduction, stack cap, and expiry duration are
configurable under `rpg.skills.passives.fortify` in `config.yml`; stack count
is capped at three and damage reduction is capped at 90%. Fortify ranks use
skill points and the same next-rank level requirement as active skills.
`mana-flow` is implemented for a mage who learned its skill book: the first
successful active-skill cast starts a chain; each following successful cast
within the configured window restores mana per rank, up to the character's
maximum. A gap longer than the window starts a new chain, and chain state is
not persisted across logout/restart. Configure
`rpg.skills.passives.mana-flow.mana-per-chain-cast-per-rank` (default `1`,
valid `0`-`10`) and `chain-window-seconds` (default `5`, valid `1`-`30`) in
`config.yml`. Mana Flow ranks can be upgraded with skill points under the
normal next-rank level requirement.

### RPG Portals

Administrators create cuboid portals without a WorldEdit dependency. Run
`/vnportal wand` to receive the plugin's marked wooden axe, then left-click a
block for `pos1` and right-click another block for `pos2`. The equivalent
`/vnportal pos1` and `/vnportal pos2` commands select the block being targeted.
Create a portal from the selected region and set its destination while
standing at the desired safe arrival point:

```text
/vnportal create <id> <name...>
/vnportal setspawn <id>
/vnportal link <portalA> <portalB>
/vnportal unlink <id>
/vnportal enable <id>
/vnportal disable <id>
/vnportal info <id>
/vnportal list
/vnportal validate
/vnportal remove <id>
```

Portal IDs are unique, lowercase slugs (2-32 characters: `a-z`, `0-9`, `_`,
`-`); names may contain spaces and are limited to 64 characters. Linking is
two-way. Both portals need a configured arrival point; a player entering a
portal's selected region is sent to the linked portal's arrival point. The
destination must be in a loaded world/chunk, have two passable blocks above a
solid floor, and travel is blocked while dead, waiting to respawn, or combat-
tagged by default. `/vnportal validate` reports missing worlds, destinations,
or links. Portal data is stored separately in
`plugins/VnMineHardcore/portals.yml`.

The `/vnportal` command requires `vnmine.command.portal.admin` (default:
operators). Portal behavior can be adjusted under `rpg.portals` in
`config.yml`: `enabled` (default `true`), `max-portals` (default `100`,
range `1-500`), `max-selection-blocks` (default `20000`, range
`1-250000`), `travel-cooldown-seconds` (default `3`, range `0-300`), and
`particle-ring.enabled` (default `true`). Combat blocking uses
`rpg.portal-travel.block-in-combat` (default `true`). No WorldEdit plugin is
required; the selection axe is identified with plugin data, not by material
alone.

### RPG World: Human Realm

The starter-realm definition is stored at
`plugins/VnMineHardcore/worlds/human-realm.yml`. It defines the stable content ID,
display name, Paper world name, spawn behavior, fallback world, and safe-zone
radius. The bundled defaults use the already-loaded `world`, its configured
Paper spawn, and a 32-block horizontal safe zone.

The plugin never creates or deletes worlds. If the configured realm is not
loaded, the spawn resolver uses the configured fallback world's spawn; if
neither is loaded, it logs the issue and returns no destination. The resolver
does not teleport players automatically. `/vnreload` reloads this definition,
and an invalid replacement leaves the last valid definition active.

The safe zone is a circular area around the resolved spawn. It blocks player
damage, attacks started inside the zone (including projectiles), hostile mob
spawns, and block/environmental damage handled by spawn protection. The existing
`vnmine.spawnprotection.bypass` permission bypasses build protection only; it
does not bypass combat or hostile-spawn protection. No new permission or
command is required.

### Tutorial Quest

The first tutorial quest is configured in
`plugins/VnMineHardcore/quests/tutorial/first-steps.yml`. It starts automatically
for active character profiles and guides players to the resolved Human Realm
spawn. The only supported objective type in this slice is
`visit-realm-spawn`; its horizontal radius defaults to 8 blocks and can be
configured from 1 to 64. The quest ID and objective ID are persisted in SQLite,
so reconnecting does not restart or duplicate progress.
While the objective is active, an action bar shows the resolved spawn location
and distance; an End Rod particle trail is shown when the player is in the same
world and within 48 blocks. Configure `quest.objective.waypoint-enabled`
(default `true`), `waypoint-update-interval-ticks` (default `20`, range `5-200`),
and `waypoint-particle-range-blocks` (default `48`, range `8-64`) in the quest
definition. Older definitions that omit these fields use the defaults.

The quest reward is configured with `quest.reward.type: character-exp` and
`quest.reward.amount` (default `25`, valid range `0-100000`). It is recorded in
the character's persistent RPG experience field, not vanilla Minecraft XP or an
inventory item. Completion and reward claim are one SQLite transaction; failure
to apply the reward rolls the quest status back so completion can safely retry.
An existing completion with `reward_claimed: false` is granted the configured
reward once on the next join, covering players who completed the tutorial before
this reward was added.

The quest can be disabled with `quest.enabled`; `/vnreload` reloads its
definition, while invalid YAML keeps the last valid definition. There is no
player command or permission for quest progress. Players open the read-only quest
journal from slot 20 in the main menu (opened with F or `/vnmenu`). It displays
the current status, checkpoint, guidance, destination, completion time, and
whether the character-XP reward was claimed. The refresh button synchronizes
saved progress; the journal can be hidden with `rpg.quest-journal.enabled`
(default `true`) in `config.yml`. Start/reminder feedback and completion
confirmation are also sent as in-game messages.

### RPG NPCs

NPC dialogue templates are defined in
`plugins/VnMineHardcore/npcs/npc-definitions.yml` and validated at startup.
The schema supports seven roles: `tutorial`, `quest-giver`, `trainer`,
`merchant`, `story`, `gatekeeper`, and `healer`. Each definition has a stable
lowercase ID, display name (up to 64 characters), and 1-20 dialogue lines
(up to 256 characters each). Optional `actions` add up to four clickable GUI
buttons: `open-quest-journal`, `open-skill-tree`, or `give-skill-book` with a
configured `book-id`. For example:

```yaml
actions:
  - type: open-skill-tree
    label: "Xem skill tree"
  - type: give-skill-book
    label: "Nhận bí kíp"
    book-id: blade-flare-manual
```

The book action does not grant another copy if that skill is learned or the
same book is already in the player's inventory. The file can contain up to 100 definitions;
duplicate YAML keys, unknown role types, and invalid definitions prevent the
plugin from starting so the invalid data is not silently ignored. Runtime NPCs
are stored separately in `plugins/VnMineHardcore/npcs.yml`; their entity UUID,
role, display name, world, and location are saved and reconciled as chunks load.

The bundled file currently provides tutorial-guide, class-trainer, and
story-keeper dialogue templates. Create a runtime NPC where you are standing:

```text
/vnnpc create <id> <type> [display name...]
/vnnpc rename <id> <display name...>
/vnnpc move <id>
/vnnpc info <id>
/vnnpc list
/vnnpc remove <id>
```

IDs use lowercase letters, numbers, and single hyphens. The seven supported
types are `tutorial`, `quest-giver`, `trainer`, `merchant`, `story`,
`gatekeeper`, and `healer`; omitted names use the matching template name or a
title-cased role. Names are limited to 64 characters and support `&` color
codes. Create and move require a player location; other commands may be run
from console. Runtime NPCs are stationary, silent, invulnerable vanilla
villagers and are protected from the custom villager-trade system. Right-click
opens a 54-slot dialogue GUI showing up to 20 configured lines and configured
action buttons. Quest journal and skill tree buttons open their existing RPG
interfaces; a skill-book button grants the configured book for learning.
Dialogue lookup first tries a definition
whose ID matches the runtime NPC ID and role, then uses the first definition of
the same role. Add role-specific definition entries to provide distinct
dialogue. NPC roles without a matching definition show a setup message instead
of silently using unrelated dialogue.
`rpg.npc.max-runtime-npcs` sets the runtime limit (default `100`, range `1-100`).
The command requires `vnmine.command.npc.admin` (default: operators). Admin GUI,
quest acceptance/reward actions, waypoint particles, and custom skins remain
future work.

Custom `merchant` NPCs can have an individual shop. Use `/vnnpc edit <id>` or
Shift-right-click the NPC to open its editor. Shift-click a system-created RPG
item in the player's inventory to add it; left-click a listed item to edit its
price, right-click to remove it, and click **Lưu cửa hàng** to save the draft to
`npcs.yml`. The price screen has configurable small, medium, and large `-`/`+`
buttons (defaults `1`, `10`, and `100` VNCOIN). Only system-created RPG items
can be listed. Prices and offer counts are bounded by
`economy.merchant.custom.max-price` and `max-offers` in `config.yml`.

Merchant purchases debit VNCOIN and create a persistent pending item delivery in
the same database transaction. If the player disconnects during a purchase, the
item is reconciled on next join. If the inventory is full, free a slot and run
`/vncoin` or rejoin to retry delivery; keep a delivery item in place until the
server confirms it. `system-merchant` NPCs use the shared catalog under
`economy.merchant.offers`, while custom merchants use their saved per-NPC list.

### Death Section
```yaml
death:
  broadcast-death: true     # Broadcast death to all players
  play-sound: true          # Play wither death sound
```

### Death Penalty Section
```yaml
death-penalty:
  enabled: true                     # Enable death penalty system
  recovery-seconds: 300             # Survival time to recover stats
  max-penalty-stack: 5              # Max penalty stack
  stats:
    max-hp-per-death: 0.1           # Max HP reduction per death (10%)
    max-thirst-per-death: 0.1       # Max thirst reduction per death (10%)
    damage-per-death: 0.1           # Damage dealt reduction per death (10%)
    incoming-damage-per-death: 0.1  # Incoming damage increase per death (10%)
    regen-per-death: 0.1            # Regen reduction per death (10%)
    food-drain-per-death: 0.1       # Food drain increase per death (10%)
    thirst-drain-per-death: 0.1     # Thirst drain increase per death (10%)
```

### Combat Section
```yaml
combat:
  mob-damage-multiplier: 2.0           # Mob damage x2
  fall-damage-multiplier: 2.0          # Fall damage x2
  fire-damage-multiplier: 2.0          # Fire damage x2
  drowning-damage-multiplier: 2.0      # Drowning x2
  explosion-damage-multiplier: 1.5     # Explosion x1.5
  pvp-damage-multiplier: 1.25          # PvP x1.25
  mob-extra-hp-multiplier: 1.5         # Mobs +50% HP
  disable-natural-regen: false         # Disable natural regen
  regen-multiplier: 0.5                # Regen multiplier (if regen enabled)
  combat-tag-duration-seconds: 30     # Combat tag duration
  ender-pearl-damage: 1.0              # Ender pearl damage (HP)
```

### Rename Section
```yaml
rename:
  enabled: true                     # Enable rename feature
  name-structure: "<name> ☠ <count>"  # Display name format
```

### Villager Trading Section
```yaml
villager-trading:
  enabled: true                     # Enable villager trading system
  disable-random-villager: true     # Prevent random profession
  region-size: 500                  # Region size (blocks)
```

### Spawner Control Section
```yaml
spawner-control:
  enabled: true                     # Enable spawner control
  spawn-rate-reduction: 0.3         # Spawn rate (30% of original)
  hp-multiplier: 3.0               # HP multiplier
  damage-multiplier: 2.0           # Damage multiplier
```

### Ore Control Section
```yaml
ore-control:
  enabled: false                    # Enable ore control
  worlds:
    world:
      DIAMOND_ORE: 0.08            # Diamond ore rate (%)
      IRON_ORE: 0.60               # Iron ore rate (%)
      # ... other ores
```

### Thirst - Natural Water
```yaml
thirst:
  natural-water:
    enabled: true                     # Enable natural water effects
    damage-per-second: 2.0           # Damage per second (1♥)
    duration-seconds: 10             # Total effect duration
    nausea-amplifier: 0              # Nausea effect level
```

### Logging Section
```yaml
logging:
  console-debug: false          # Enable debug console logs
  log-deaths: true              # Log deaths
  log-disasters: true           # Log disasters
```

Boss settings are stored in `plugins/VnMineHardcore/bosses/<id>.yml` and
`bosses/_settings.yml`. Disaster settings are stored in
`plugins/VnMineHardcore/disasters/<id>.yml` and `disasters/_settings.yml`;
they are not configured in the main `config.yml`.

### Time Control

`time-control.global` configures default day and night lengths in real-world
minutes. Use `/vntime set day <minutes>` or `/vntime set night <minutes>` to
change and save these defaults. Add a loaded world name at the end to set only
that world's override, for example `/vntime set day 6 world_resource`.
`/vnday set <day>` sets the current world to dawn of that in-game day.
Nether worlds do not use the custom day/night clock.

For all current keys and bilingual comments, refer to the generated
`plugins/VnMineHardcore/config.yml`.

---

## 📖 Disaster Config Guide

### Cấu trúc file disaster

Mỗi disaster được cấu hình trong file riêng tại `plugins/VnMineHardcore/disasters/<id>.yml`.

### Xác suất thiên tai theo ngày

Trong `plugins/VnMineHardcore/disasters/_settings.yml`, `success-probability` quyết định xác suất thiên tai bắt đầu **sau khi** hệ thống đã chọn loại thiên tai. `chance` trong file từng disaster vẫn chỉ là trọng số chọn loại, không phải xác suất thành công.

```yaml
success-probability:
  default-percent: 100
  day-thresholds:
    - min-day: 1
      percent: 100
    - min-day: 10
      percent: 75
    - min-day: 30
      percent: 50
```

Có thể thêm tùy ý số dòng ngưỡng. Hệ thống áp dụng `percent` của `min-day` lớn nhất không vượt quá ngày hiện tại; nếu chưa tới ngưỡng nào, dùng `default-percent`. Ngày lấy từ thời gian đã trôi qua của world đầu tiên được load, bắt đầu từ ngày 1. Giá trị được giới hạn trong khoảng 0–100: 0% luôn bỏ qua thiên tai đã chọn và bắt đầu lại thời gian chờ; 100% luôn cho phép thiên tai đã chọn xảy ra.

Khu vực an toàn kiểm tra toàn bộ cột phía trên người chơi đến giới hạn chiều cao của world, vì vậy mái hang ở xa vẫn được nhận diện. `safe-zone.roof-height` cũ vẫn được đọc để tương thích nhưng không còn giới hạn độ cao kiểm tra.

### Action Types

| Action Type | Mô tả | Parameters |
|-------------|-------|------------|
| `damage` | Gây sát thương | `damage`, `ignore-armor`, `radius` |
| `potion_effect` | Gây hiệu ứng | `effects[]` (type, duration-ticks, amplifier) |
| `spawn_mobs` | Spawn quái | `mobs{}`, `count-per-target`, `radius` |
| `lightning_strike` | Sét đánh | `count-per-target`, `damage-multiplier`, `explosion-power`, `explosion-fire`, `delay-ticks` |
| `explosion` | Phát nổ | `explosion-power`, `explosion-fire`, `explosion-break-blocks` |
| `set_fire` | Đốt cháy | `fire-ticks`, `radius` |
| `place_block` | Đặt block | `block-type`, `count-per-target`, `place-height` |
| `falling_block` | Block rơi | `block-fall-chance`, `blast-resistance-factor`, `blocks-per-target` |
| `velocity` | Đẩy bay | `velocity-y`, `velocity-x-range`, `velocity-z-range` |
| `set_time` | Đặt thời gian | `time` |
| `set_weather` | Đặt thời tiết | `storm`, `thunder`, `duration-ticks` |
| `clear_weather` | Xóa thời tiết | — |
| `broadcast` | Thông báo | `message` |
| `action_bar` | Action bar | `message` |
| `teleport_random` | Teleport ngẫu nhiên | `radius` |
| `play_sound` | Phát âm thanh | `sound`, `volume`, `pitch` |
| `block_velocity` | Nhấc block lên | `velocity-y`, `damage-on-land`, `damage-amount`, `replace-with-air` |
| `block_explosion` | Phá block dạng nổ | `power`, `drop-items` |
| `block_replace` | Thay thế block | `from-material`, `to-material`, `radius` |
| `block_ignite` | Đốt block | `radius`, `fire-ticks` |
| `block_liquid` | Đặt chất lỏng | `liquid-type` (WATER/LAVA), `radius` |
| `block_fertilize` | Thúc cây trồng | `radius`, `bonemeal-chance` |
| `entity_pull` | Kéo entity | `pull-strength`, `radius` |
| `entity_push` | Đẩy entity | `push-strength`, `radius` |
| `entity_freeze` | Đóng băng | `duration-ticks`, `radius` |
| `entity_dismount` | Hất khỏi thú cưỡi | `radius` |
| `entity_mount` | Bắt lên thú cưỡi | `mount-type`, `radius` |
| `item_drop` | Rơi item | `material`, `amount`, `radius` |
| `world_time` | Đặt thời gian | `time` |
| `world_weather` | Đặt thời tiết | `storm`, `thunder`, `duration-ticks` |

### Target Types

| Target Type | Mô tả | Ví dụ |
|-------------|-------|-------|
| `player` | Người chơi (weighted random) | `player: { weight: 100 }` |
| `all_players` | Tất cả người chơi | `all_players: { weight: 100 }` |
| `friendly_mobs` | Sinh vật thân thiện | `friendly_mobs: { weight: 50, radius: 15 }` |
| `hostile_mobs` | Sinh vật thù địch | `hostile_mobs: { weight: 30, radius: 12 }` |
| `all_mobs` | Tất cả sinh vật | `all_mobs: { weight: 100, radius: 20 }` |
| `ground` | Block mặt đất dưới chân | `ground: { weight: 100, radius: 5 }` |
| `trees` | Block gỗ, lá cây | `trees: { weight: 70, radius: 8 }` |
| `blocks` | Block rắn bất kỳ | `blocks: { weight: 50, radius: 10 }` |
| `surface_blocks` | Block trên bề mặt | `surface_blocks: { weight: 50, radius: 10 }` |
| `random_blocks:MAT1,MAT2,...` | Block ngẫu nhiên từ danh sách | `random_blocks:IRON_BLOCK,DIAMOND_BLOCK: { weight: 100, radius: 30 }` |
| `random:target1,target2,...` | Random từ array target | `random:player,ground,trees: { weight: 100 }` |

### Chain Actions

Mỗi action có thể kích hoạt nhiều **chain-actions** trên cùng vị trí target. Chain-actions thực thi ngay sau action chính.

```yaml
- type: lightning_strike
  targets:
    random_blocks:IRON_BLOCK:
      weight: 100
      radius: 30
  count-per-target: 1
  damage-multiplier: 2.0
  explosion-power: 1.5
  explosion-fire: true
  chain-actions:
    - type: explosion
      power: 1.5
      fire: true
      break-blocks: false
    - type: set_fire
      fire-ticks: 100
      radius: 4
    - type: damage
      damage: 8.0
      radius: 5
    - type: potion_effect
      radius: 5
      effects:
        - type: SLOWNESS
          duration-ticks: 200
          amplifier: 1
```

### Ví dụ Config Nâng Cao

#### Tornado (Lốc xoáy)
```yaml
actions:
  # Cuốn player lên
  - type: velocity
    targets:
      player: { weight: 100, radius: 20 }
    velocity-y: 2.5
    velocity-x-range: -1.5-1.5
    velocity-z-range: -1.5-1.5
    chain-actions:
      - type: damage
        damage: 1.0
        radius: 3
        ignore-armor: true

  # Cuốn mob lên
  - type: velocity
    targets:
      friendly_mobs: { weight: 50, radius: 15 }
      hostile_mobs: { weight: 30, radius: 12 }
    velocity-y: 2.0

  # Nhấc block mặt đất + cây cối
  - type: block_velocity
    targets:
      ground: { weight: 100, radius: 5 }
      trees: { weight: 70, radius: 8 }
    velocity-y: 3.0
    damage-on-land: true
    damage-amount: 4.0
    replace-with-air: true
    count-per-target: 3

  # Sét đánh ưu tiên block sắt
  - type: lightning_strike
    targets:
      random_blocks:IRON_BLOCK,IRON_ORE: { weight: 100, radius: 30 }
      trees: { weight: 60, radius: 20 }
      player: { weight: 10, radius: 15 }
    count-per-target: 1
    damage-multiplier: 2.0
    explosion-power: 1.5
    explosion-fire: true
    chain-actions:
      - type: explosion
        power: 1.5
        fire: true
      - type: set_fire
        fire-ticks: 100
        radius: 4
      - type: damage
        damage: 8.0
        radius: 5
      - type: potion_effect
        radius: 5
        effects:
          - type: SLOWNESS
            duration-ticks: 200
            amplifier: 1
```

#### Mega Storm (Bão lớn)
```yaml
actions:
  # Sét đánh khắp nơi
  - type: lightning_strike
    targets:
      random_blocks:IRON_BLOCK,LIGHTNING_ROD: { weight: 100, radius: 50 }
      trees: { weight: 80, radius: 30 }
      player: { weight: 20, radius: 20 }
    count-per-target: 3
    damage-multiplier: 1.5
    explosion-power: 2.0
    explosion-fire: true
    chain-actions:
      - type: set_fire
        fire-ticks: 200
        radius: 3

  # Mưa axit gây damage
  - type: damage
    targets:
      all_players: { weight: 100 }
      friendly_mobs: { weight: 50, radius: 20 }
    damage: 1.0
    ignore-armor: true

  # Gió mạnh đẩy bay
  - type: velocity
    targets:
      player: { weight: 100, radius: 30 }
      all_mobs: { weight: 50, radius: 20 }
    velocity-y: 1.0
    velocity-x-range: -2.0-2.0
    velocity-z-range: -2.0-2.0
```

---

## 📖 Boss Config Guide

### Cấu trúc file boss

Mỗi boss được cấu hình trong file riêng tại `plugins/VnMineHardcore/bosses/<id>.yml`.

### Ví dụ Config Boss

```yaml
# plugins/VnMineHardcore/bosses/creeper_boss.yml
enabled: true
entity-type: CREEPER
display-name: "§a§lCreeper Boss"
hp: 200.0
damage-multiplier: 3.0
chance: 10
duration-seconds: 1200
warning-seconds: 60

# Immunities (miễn nhiễm)
immunities:
  sunlight-burn: true      # Không cháy dưới nắng
  fire: true               # Miễn nhiễm lửa
  fall-damage: false       # Có thể bị damage ngã
  wither: false            # Có thể bị wither
  poison: false            # Có thể bị poison
  potion-effects:          # Miễn nhiễm potion cụ thể
    - SLOWNESS
    - WEAKNESS

# Drops (vật phẩm rơi ra khi chết)
drops:
  DIAMOND:
    min-amount: 1
    max-amount: 3
    chance: 0.8            # 80%
  GUNPOWDER:
    min-amount: 8
    max-amount: 16
    chance: 1.0            # 100%
  TNT:
    min-amount: 2
    max-amount: 5
    chance: 0.5            # 50%
```

### Boss Config Parameters

| Parameter | Mô tả | Giá trị mặc định |
|-----------|-------|-----------------|
| `enabled` | Bật/tắt boss | `true` |
| `entity-type` | Loại entity (EntityType name) | `WITHER` |
| `display-name` | Tên hiển thị (có màu) | `§c§lBoss` |
| `hp` | Máu tối đa | `100.0` |
| `damage-multiplier` | Hệ số nhân sát thương | `1.0` |
| `chance` | Tỷ lệ xuất hiện (weight) | `10` |
| `duration-seconds` | Thời gian boss tồn tại mặc định (20 phút) | `1200` |
| `warning-seconds` | Thời gian cảnh báo | `60` |

### Immunities

| Immunity | Mô tả |
|----------|-------|
| `sunlight-burn` | Không cháy dưới ánh nắng (Zombie, Skeleton) |
| `fire` | Miễn nhiễm lửa và dung nham |
| `fall-damage` | Miễn nhiễm sát thương ngã |
| `wither` | Miễn nhiễm hiệu ứng Wither |
| `poison` | Miễn nhiễm hiệu ứng Poison |
| `potion-effects` | Danh sách PotionEffectType bị miễn nhiễm |

### Drops

| Parameter | Mô tả |
|-----------|-------|
| `min-amount` | Số lượng tối thiểu |
| `max-amount` | Số lượng tối đa |
| `chance` | Tỷ lệ rơi (0.0 - 1.0) |

### Creeper Boss: charged-explosion

Skill `charged-explosion` trong `bosses/creeper_boss.yml` gọi sét hiệu ứng,
gây sát thương `damage` lên sinh vật trong `radius` block, rồi giữ trạng thái
charged trong `duration-seconds`. Boss creeper không tự nhận sát thương,
không tự biến mất hoặc phá block; `explosion-power` và
`charged-explosion-power` cấu hình sức mạnh vụ nổ thường và charged. Các giá
trị đều có thể tùy chỉnh trong file boss.

Điểm leaderboard chỉ được trao khi boss bị hạ trước khi hết thời gian tồn tại.
Boss hết hạn hoặc bị kết thúc bằng `/vnboss kill` không cho điểm; lệnh admin
này cũng không làm rơi loot. Skill mở rộng của các boss Enderman, Ravager,
Phantom, Ghast, Spider, Vindicator, Witch và Giant được bật/tùy chỉnh trong
file boss tương ứng. Các thay đổi block tạm thời được khôi phục sau thời hạn
đã cấu hình hoặc khi encounter kết thúc; block trong vùng spawn protection
không bị thay đổi.

Thông báo skill cast được cấu hình trong `bosses/_settings.yml`:
`skill-cast-feedback.console-log` bật log console, còn
`skill-cast-feedback.broadcast-to-ops` chỉ gửi thông báo đến OP đang online;
cả hai mặc định bật. Boss dạng mob dùng AI/pathfinding mặc định để di chuyển
và tìm mục tiêu; hệ thống custom chỉ điều phối target, skill và boundary
recovery để không ép vận tốc mỗi vòng.

### Rare drops theo level và bảng xếp hạng boss

Mỗi file `bosses/<id>.yml` có `level-drops` riêng cho level 1–10. Mỗi level
có hai di vật hiếm với truyền thuyết riêng; lore được liên kết qua `story-id`
đến `story.yml`, tối đa 30 ký tự mỗi dòng và 6 dòng mỗi món. Số dòng enchant
tăng theo level. Cung Skeleton level 1 luôn rơi cung có Flame I và Infinity I;
Infinity vẫn yêu cầu người chơi mang theo ít nhất một mũi tên. Loot có thể tùy
chỉnh bằng các trường:
`material`, `name`, `lore`, `min-amount`, `max-amount`,
`chance`, `enchantments` (kể cả over-enchant) và `book-enchantments` (lưu
enchant để người chơi dùng sách lên item). Boss tự nhiên tăng cấp theo ngày
trong game, đạt khoảng level 10 từ ngày 400; boss được gọi thủ công có thể
chọn level bằng `/vnboss`. Khi boss spawn, loot ở level đó và loot thường được
broadcast kèm xác suất riêng từng món.
Mặc định, mỗi món loot thường và loot theo level được roll độc lập với xác
suất 30%. Từng món trong bộ giáp boss và từng ô gear lính cũng có xác suất mặc
định 30%; token boss có xác suất mặc định 30%. Có thể thay đổi các tỉ lệ này
trong file boss hoặc `bosses/_settings.yml`. Các giá trị đã có trong cấu hình
server được giữ nguyên khi cập nhật; chỉ các cấu hình mới hoặc khóa còn thiếu
mới dùng mặc định này.
Thời gian tồn tại mặc định của mọi boss là 20 phút. Đồng hồ chờ roll boss tự
động tạm dừng trong lúc cảnh báo hoặc khi đang có boss hoạt động; bộ đếm chỉ
tiếp tục sau khi sự kiện hiện tại kết thúc.

`scoreboard.yml` cấu hình sidebar, chu kỳ reset theo ngày trong game, title
người thắng, chu kỳ nhắc và vật phẩm cho từng hạng từ top 1 đến top 10. Top 5
người gây sát thương được chia `10 × level` điểm theo phần damage của họ.
Điểm, phần thưởng chờ nhận và mùa hiện tại được lưu trong
`scoreboard-data.yml`. Người chơi dùng `/reward` để nhận; nếu túi thiếu chỗ,
phần thưởng vẫn được giữ để thử lại. `/vnreload` áp dụng cấu hình mới.

---

## 📊 Log Files

Located in `plugins/VnMineHardcore/`:

| File | Description |
|------|-------------|
| `deaths.log` | Detailed death records |
| `disasters.log` | Disaster event logs |
| `stats.yml` | Player statistics |
| `death-penalty.yml` | Death penalty data (stacks, recovery) |
| `permission.yml` | Permission groups and user assignments |
| `npcs.yml` | Runtime RPG NPC records and spawn locations |

---

## 📝 Changelog

### v1.10.0
- Added `/vngroup` and `/vnuser` with persistent `permission.yml` groups and granular per-command permissions.
- Split plugin command access into function-specific permission nodes; added `vnmine.spawnprotection.bypass`.
- Spawn protection now blocks placement, fire, liquids, explosions and disaster block changes, with OP/permission bypass for player block placement and breaking.

### v1.9.1
- Fixed the packaged `plugin.yml` resource so all registered command executors, including `/vnspawn` and `/tp`, are declared in the built JAR.

### v1.9.0
- Added `/vnspawn set` and `/vnspawn go <world>` for setting the current world's spawn and travelling to a loaded world's spawn; world-name Tab completion is available.

### v1.8.0
- Added `/tp <user> <world>` and `/vnspawn protect|list|remove`; protection areas persist in `spawns-protection.yml`
- Added `/vnevent stop` for cancelling a warning or active disaster immediately
- Earthquakes now launch exposed players with configurable horizontal and vertical velocity
- Each disaster file configures its chat effect/prevention summary; warnings and starts show affected world names
- Fixed disaster world filters to recognize world names and dimension names; Chorus Explosion is disabled by default
- Updated the documented server target to Paper 26.x

### v1.7.0
- Added world-specific `/vntime set <day|night> <minutes> [world]` and `/vnday set <day>`
- Added boss-specific rare relics for levels 1–10, with escalating enchantment lines and short legends
- Extended per-boss HP and natural boss-level progression through level 10
- Set the default boss lifetime to 20 minutes and pause new boss rolls during an active/warning event

### v1.6.0
- Added `/scoreboard reset` to clear season scores and pending rewards
- Added `/vntime set day <minutes>` and `/vntime set night <minutes>`; changes are saved and preserve per-world overrides
- `/vnhelp` now lists every plugin command; tab completion suggests valid IDs and common values for command parameters

### v1.5.0
- Creeper Boss gọi sét tự gây sát thương cho bản thân và sinh vật xung quanh, trở nên charged tạm thời
- Creeper Boss nổ mạnh hơn khi charged, không tự bị xóa và không phá block

### v1.4.0
- Boss có loot quý cấu hình riêng theo level và từng file boss; hỗ trợ item lore, enchant thường/over-enchant, sách enchant có thể áp dụng lên item; broadcast loot và tỉ lệ khi boss spawn
- Bảng xếp hạng damage trực tiếp và điểm mùa: top 5 chia 10 × level điểm theo damage
- Reset theo ngày trong game, thưởng cấu hình cho top 10, nhận qua `/reward`, xử lý inventory đầy, nhắc nhận thưởng và title top 1

### v1.3.0
- **Multi-Target System**: Disasters can now target players, mobs (friendly/hostile/all), blocks (ground, trees, blocks, surface, random_blocks), and random combinations
- **Chain Actions**: Each action can trigger multiple sub-actions (explosion, fire, damage, potion effects) on the same target
- **New Action Types**: block_velocity, block_explosion, block_replace, block_ignite, block_liquid, block_fertilize, entity_pull, entity_push, entity_freeze, entity_dismount, entity_mount, item_drop
- **New Target Types**: friendly_mobs, hostile_mobs, all_mobs, ground, trees, blocks, surface_blocks, random_blocks:MATERIALS, random:TARGETS
- **Death Penalty System**: Stat reduction on death with recovery timer
- **Death Rename System**: Auto-rename display name in chat/tab after death
- **Villager Trading System**: Biome-based villager trading management
- **Spawner Control**: Reduce spawn rate, increase HP/damage for spawner mobs
- **Ore Control**: Per-world ore rate configuration
- **World Interaction**: Lava+water cobblestone/stone fix
- **Environment Expansion**: Cold water damage, acid rain, torch reduces claustrophobia, elytra reduces vertigo
- **Combat Expansion**: Witch Speed boost, Creeper explosion x2, Skeleton arrow poison
- **Logging System**: Configurable debug, death, ban, disaster logging
- **New Command**: `/vn` - Main command with help system
- **Documentation**: Updated README with disaster config guide, boss config guide, multi-target system

### v1.2.0
- **New Bosses**: Added 9 new boss types (Zombie, Skeleton, Spider, Creeper, Enderman, Witch, Ravager, Vindicator, Phantom)
- **Total Bosses**: 12 types with unique AI behaviors
- **Boss AI**: Smart targeting (Player → Friendly mob → Hostile mob → Wander), stuck detection
- **Boss Bar**: Single boss bar showing name + HP (no more spam)
- **Disaster Config**: Added enable/disable flags for each disaster type
- **Disaster Messages**: Fully customizable messages with placeholders
- **Disaster Display Names**: Customizable names with color codes
- **Documentation**: Updated README with all 12 boss IDs and 14 disaster IDs
- **Config**: All bosses and disasters enabled by default

### v1.1.0
- **New Command**: `/vnboss` - Manually trigger boss events (wither, ender_dragon, giant, ghast)
- **New Disasters**: Added 7 new disasters (Inferno Storm, Soul Eruption, Lava Geyser, End Surge, Void Storm, Chorus Explosion, Earthquake)
- **Total Disasters**: 14 types across Overworld, Nether, and The End
- **Earthquake Enhancement**: Added screen shake effect (Nausea + Slowness + Sound)
- **Boss System**: Added `triggerBoss()` method for manual boss spawning
- **Documentation**: Updated README with all disaster IDs and boss commands
- **Config**: All config parameters have bilingual comments (VI/EN)

### v1.0.0
- Initial release
- Death tracking and persistent death statistics
- Combat system with damage multipliers
- Combat tag with configurable duration
- Hunger system with raw food poisoning & choking
- Thirst system with natural water effects
- Environment system (temperature, fog, claustrophobia, vertigo)
- 7 disaster types (Blood Moon, Meteor, Storm, Solar Flare, Plague, Tornado, Eclipse)
- Manual disaster trigger (/vnevent)
- Boss event system (Wither, Ender Dragon, Giant, Ghast)
- Regeneration multiplier (configurable)
- Configurable fog (interval, duration, amplifier)
- Bilingual config comments (VI/EN)

---

## 🛠 Support

- **GitHub Issues**: [Report bugs](https://github.com/noobsclan101/vnminehardcore/issues)

---

**VnMineHardcore** - *Chơi là chết, sống là quý!*
