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
| `/vntext ...` | Create and manage persistent, oriented, colored text displays | `vnmine.command.text.manage` |
| `/vnquest admin` | Open the quest definition manager GUI | `vnmine.command.quest.admin` |
| `/vnquestdialog admin` | Open the reusable quest-dialog manager | `vnmine.command.quest.admin` |
| `/vnquest journal` | Open active quests and completed quest history, with details, rewards, tracking, and cancel controls | No permission |
| `/vnquest detail <id>` | Open details for an active or completed quest; also used to open a quest named on the scoreboard | No permission |
| `/vnplayeradmin` | Open player administration for online players or search a previously joined offline player | `vnmine.command.playeradmin` |
| `/quatanthu` | Nhận túi quà tân thủ và chọn 1 token triệu hồi lính đánh thuê bậc 1 | — |

The player administration GUI supports ban/unban, kick, permanent or timed
mute, clearing an online player's inventory, restoring one of their ten most
recent death inventories through the durable delivery queue, completed quest
history/reset, quest/skill/race/class/profile resets, and RPG EXP/level or
RPG item grants. The paginated item registry includes quest materials,
implemented skill books, configured boss drops and armor, merchant shop items,
plus mercenary gear and summon tokens; grants preserve each item's system metadata. Offline player
grants use the durable delivery queue, including when the target inventory is
full. Offline player search accepts names already
cached by Paper; chat prompts show an example and accept `!cancel`. A full RPG
profile reset preserves the VNCOIN wallet and starts character onboarding again.
Quest history is paginated. Moderation and reset actions are logged with actor,
target, and action under `[PlayerAdmin]` / `[Quest][Admin]`.

Open player administration from **Main Menu → Admin → Người chơi**, or directly
with `/vnplayeradmin`. That screen lists online players and has an offline-name
search button at the end of the bottom row. Select a player to open the profile
actions. The bottom-center button returns to the admin menu.
From a player's profile, choose **Manage Player Quests** to view completed
quests, reopen one for that player, or reset all quest progress. In the quest
editor, toggle **Lặp lại** on before using the `-1` and `+1` daily-limit
buttons; a limit of `0` means unlimited repeats.

Use **Quản lý quest → Edit quest** to set `repeatable.enabled` and adjust
`repeatable.daily-limit` from 0 to 10. A value of 0 means unlimited completed
runs per UTC day. Each completion records a unique run and reward transaction
in SQLite; the default for existing and newly created quests is disabled.

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
| `vnmine.command.skillbook.admin` | Give configured RPG skill books and configure race/class starter books (default: op) |
| `vnmine.command.progression.admin` | Grant character RPG EXP/levels from the admin GUI (default: op) |
| `vnmine.command.playeradmin` | Open player moderation, history, reset, inventory, and grant controls (default: op) |
| `vnmine.command.npc.admin` | Create, rename, move, inspect, list, and remove RPG NPCs (default: op) |
| `vnmine.command.text.manage` | Create, edit, move, list, inspect, and remove world text displays with `/vntext` (default: op) |
| `vnmine.command.quest.admin` | Manage quest definitions and quest dialogs through `/vnquest admin` or `/vnquestdialog admin` (default: op) |
| `vnmine.spawnprotection.bypass` | Let a non-operator place/break blocks inside protected regions |

The old `vnmine.hardcore.admin` and `vnmine.hardcore.stats` nodes remain
registered for legacy integrations, but command checks use the granular nodes
above. Migrate old permission assignments to the corresponding command nodes;
`vnmine.hardcore.admin` still participates in the existing environment bypass.

---

## ⚙️ Configuration

`player-admin.enabled` controls the `/vnplayeradmin` GUI (default `true`);
`player-admin.death-snapshot-limit` controls how many recent death inventories
are shown (default `10`, range `1-10`); and
`player-admin.mute.max-seconds` caps timed mutes (default `31536000`, range
`1-31536000`). Quest repeatability and each quest's daily repeat limit are
stored per definition in `quests/quest-definitions.yml`.

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
/vnskillbook config
```

This command requires `vnmine.command.skillbook.admin` (default: operators).
The admin GUI also provides **Quản trị → RPG → Give sách kỹ năng**. Select an
online player, then a configured book. It uses the same book generation as the
command and reports an error if the player is offline or their inventory is full.
Use **Quản trị → RPG → Sách skill khởi đầu** or `/vnskillbook config` to assign a
starter book to a race/class pair. The selector only offers books whose skill
belongs to that class. Mappings are saved in
`plugins/VnMineHardcore/starter-skill-books.yml`; bundled defaults cover the
currently playable Human swordsman/guardian and Elf mage. After a new player
finishes onboarding and creates a character, the configured book is queued as
a durable item delivery. If their inventory is full, the book remains pending
until space is available; the tutorial quest waits for the delivery to complete.
If the inventory was full, leave a slot open and trigger delivery again by
rejoining or using `/vncoin`. Older profiles do not receive a retroactive
starter book. Set or remove mappings before onboarding if a class should
receive a different book or no book.
For troubleshooting, set `logging.console-debug: true`; onboarding emits
`[RPG-DEBUG][Profile] starter-book-*` and delivery reconciliation emits
`[RPG-DEBUG][Delivery]` with the stable transaction ID, queue state, retry,
and callback result. Turn debug logging off after collecting the needed log.

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

### New-player onboarding

With `rpg.onboarding.enabled: true`, an account without an RPG profile is asked
to choose a starting continent after profile data loads. The realm is offered
only when its configured world and spawn are available and its tutorial NPC
exists in that same world. Configure `tutorial-npc-id` and
`starting-selectable` in each `plugins/VnMineHardcore/worlds/*.yml` file, then
create the matching runtime NPC in the configured world, for example:

```text
/vnnpc create tutorial-guide tutorial Người dẫn đường
```

The player sees a world hint while in a different world and receives ground
navigation particles after entering the tutorial NPC's world. Interacting with
that exact NPC opens race/class selection. The chosen realm ID is saved on the
player data and its world is stored as the profile's starting world. Existing
profiles skip onboarding. If no realm is correctly configured, character
creation remains gated and the player/admin receives a setup message; set
`rpg.onboarding.enabled: false` to temporarily restore the previous direct
character-selection flow.

The Human Realm defaults to selectable and expects `tutorial-guide` in `world`.
The Yêu Realm defaults to not selectable because it is quest-locked and expects
`tutorial-yeu-realm` in `yeu_realm`; its world must already be loaded and the
runtime NPC must be created before an admin enables `starting-selectable`.
The onboarding objective appears in the quest scoreboard. The legacy first-step
spawn quest is not started for a profile whose configured starting world differs
from the quest's target world.

### Tutorial Quest

The legacy first tutorial quest is configured in
`plugins/VnMineHardcore/quests/tutorial/first-steps.yml`. It starts automatically
for active character profiles and guides players to the resolved Human Realm
spawn. The only supported objective type in this slice is
`visit-realm-spawn`; its horizontal radius defaults to 8 blocks and can be
configured from 1 to 64. The quest ID and objective ID are persisted in SQLite,
so reconnecting does not restart or duplicate progress.
While the objective is active, a shared ticker recalculates a bounded ground
navigation route from the player's current location to the target. End Rod
chevrons form repeated `>>>` arrows on the walkable surface and a particle ring
marks the target on the ground. The route avoids solid terrain and updates as
the player moves. Open the quest
journal from slot 20 in the main menu and click the tracking toggle in slot 11 to
enable or disable tracking; that choice is saved on the player. While the
tutorial is active, the sidebar also shows its current objective. The compass in
slot 17 shows a hover description and enables tracking when clicked. Administrators
can use `waypoint-enabled` as a global switch, `tracking-enabled-by-default`
(default `true`), `target-marker-enabled` (default `true`),
`waypoint-update-interval-ticks` (default `10`, range `5-200`), and
`waypoint-particle-range-blocks` (default `48`, range `8-64`) in the quest
definition. Tracking currently applies to the single tutorial quest.

The quest reward supports `quest.reward.type: character-exp` (amount `0-100000`)
or `vncoin` (amount `1-1000000`); the default remains `character-exp` with `25`.
Both rewards and completion are recorded in one SQLite transaction. VNCOIN uses
a stable quest/player transaction ID, so reconnects and duplicate completion
callbacks cannot credit it twice. If a wallet would exceed its balance limit,
the entire completion transaction rolls back and can retry later. An existing
completion with `reward_claimed: false` receives its configured reward once on
next join.

The quest can be disabled with `quest.enabled`; `/vnreload` reloads its
definition, while invalid YAML keeps the last valid definition. There is no
player command or permission for quest progress. Players open the read-only quest
journal from slot 20 in the main menu (opened with F or `/vnmenu`). It displays
the current status, checkpoint, guidance, destination, completion time, and
whether the configured reward was claimed. The refresh button synchronizes
saved progress; the journal can be hidden with `rpg.quest-journal.enabled`
(default `true`) in `config.yml`. Start/reminder feedback and completion
confirmation are also sent as in-game messages.

### Class Availability

`player-classes.yml` records each class as `available`, `locked`, or `planned`.
Only `available` classes that also have at least one implemented RPG skill appear
in character creation; `locked` and `planned` entries stay hidden. If an older
server copy has no `availability` key, the plugin auto-detects it from supported
skills. The bundled catalog currently enables `swordsman`, `guardian`, and
`mage`; the other listed classes are marked `planned` until their skills and
player-facing setup are complete.

Class entries may optionally declare `parent-class-id: <class-id>` to describe
an advanced class lineage. The parent must exist and belong to the same race;
startup rejects missing, cross-race, or cyclic parent links. Skill-tree display,
book eligibility, learning, upgrading, quick-slot assignment, passive activation,
and casting recognize skills from configured ancestors. Child classes stay out
of character creation and appear under **Tiến chức class** in the skill tree
when their inheritance lineage has at least one implemented skill. `required-level` controls the
minimum promotion level (default `1`, allowed `1..10000`). Promotion requires
an explicit confirmation, keeps learned skills, and records each transition in
SQLite schema v13. The current class is persisted in the profile. The optional
`required-quest-id` condition must refer to a completed quest.
`required-item` and `required-item-amount` can require a vanilla Material stack
in the player's inventory; it is checked at confirmation and is not consumed.
`required-rpg-item-id` and `required-rpg-item-amount` check an exact RPG quest
item ID from the player's inventory, also without consuming it.
`required-npc-id` can require a prior interaction with a configured NPC; the
visited-ID set is saved in the player's persistent data. The promotion menu
also shows the base class and chronological promotion history. Boss/system item
IDs and class stat modifiers are still pending.

### RPG NPCs

NPC dialogue templates are defined in
`plugins/VnMineHardcore/npcs/npc-definitions.yml` and validated at startup.
The schema supports eleven roles: `tutorial`, `quest-giver`, `hunter`,
`blacksmith`, `alchemist`, `trainer`, `merchant`, `system-merchant`, `story`,
`gatekeeper`, and `healer`. Each definition has a stable
lowercase ID, display name (up to 64 characters), and 1-20 dialogue lines
(up to 256 characters each). Optional `actions` add up to four clickable GUI
buttons: `open-quest-journal`, `open-skill-tree`, `open-story`, `open-merchant`,
`give-skill-book` with a configured `book-id`, or `accept-quest` with a
configured `quest-id`. For example:

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
Story NPC records also persist their selected `story-chapter-id`.

The bundled file provides tutorial-guide, class-trainer, coin-merchant,
story-keeper, hunter, village-watch, and blacksmith dialogue templates. The story keeper
opens the chapter list. The hunter, village-watch, and blacksmith offer configured
kill/collection quests; create runtime NPCs with matching IDs and roles to use them.
Create a runtime NPC where you are standing:

```text
/vnnpc create <id> <type> [display name...]
/vnnpc rename <id> <display name...>
/vnnpc move <id>
/vnnpc info <id>
/vnnpc list
/vnnpc remove <id>
```

IDs use lowercase letters, numbers, and single hyphens. The supported
types are `tutorial`, `quest-giver`, `hunter`, `blacksmith`, `alchemist`, `trainer`,
`merchant`, `system-merchant`, `story`, `gatekeeper`, and `healer`; omitted names use the matching template name or a
title-cased role. Names are limited to 64 characters and support `&` color
codes. Create and move require a player location; other commands may be run
from console. Runtime NPCs are stationary, silent, invulnerable vanilla
villagers and are protected from the custom villager-trade system. Right-click
opens the NPC interaction menu. A `story` NPC plays its assigned chapter as a
typewriter dialog in the action bar, prefixed with `[Tên NPC:]`; dialogue text
does not go to chat or item lore. Admins can shift-right-click a story NPC,
choose **Chọn chapter cốt truyện**, then select its chapter. New story NPCs
default to the first configured chapter. Quest-giver NPCs show available,
active, completed, and unavailable quests as clickable actions. Selecting a
quest plays its configured state dialog in the action bar. When an available
quest dialog finishes, a GUI opens with the quest details and **Nhận nhiệm vụ**
confirmation; an in-progress item turn-in dialog similarly opens the submit
confirmation GUI. No dialogue content is sent to chat. Quest journal and skill tree buttons open
their RPG interfaces; a skill-book button grants the configured book. An
`accept-quest` action uses the same dialog flow and only accepts from its
configured giver NPC ID.
Nearby NPCs can show player-specific virtual markers: yellow `!` means at least
one quest at that NPC is currently receivable; aqua `?` marks an unread story
chapter or the tutorial NPC for the selected starting continent. A chapter is
marked read only after its action-bar dialog finishes. Markers are reconciled
every 20 ticks by default, only for NPCs tracked by the client and within
48 blocks; change or disable them with `rpg.npc.markers.enabled`,
`rpg.npc.markers.update-interval-ticks` (5-100), and
`rpg.npc.markers.tracking-range-blocks` (8-128). Debug transition logs use
`[RPG-DEBUG][NPCMarker]` and `[RPG-DEBUG][NPC]` when
`logging.console-debug: true`.
Quest definitions live in `quests/quest-definitions.yml`; the initial objective
type is `kill-mob`, progress persists in SQLite, and VNCOIN rewards are paid
once on completion. Create the sample quest NPCs at your chosen locations:

```text
/vnnpc create hunter hunter Thợ săn
/vnnpc create village-watch quest-giver Đội trưởng tuần tra
/vnnpc create blacksmith blacksmith Thợ rèn
```

Current sample quests ask players to kill zombies, skeletons, or spiders, or
collect special iron shards dropped by iron golems while a player has the
blacksmith quest active. Quest materials carry a plugin PDC ID; ordinary raw
iron does not count. The scoreboard shows the selected active quest and
progress. The quest editor can assign four optional dialog IDs: available,
in-progress, completed/reward, and unavailable/requirements unmet. Open
**/vnquest admin → Quản lý quest-dialog** or use `/vnquestdialog admin` to
create, edit, and delete reusable dialog text. Enter pages separated by `|`,
for example `Chào người lữ khách.|Hãy giúp ta tìm quặng.`. Dialogs persist in
`plugins/VnMineHardcore/quests/quest-dialogs.yml`; deletion is blocked while a
quest references that dialog. Bindings are stored in `quest-definitions.yml`.
The completed dialog appears after an NPC item turn-in awards the quest and on
later visits to the giver after completion. The unavailable dialog is used when
the player does not meet quest requirements or has reached the active quest cap.
The quest journal already supports reward previews; system-boss objectives,
quest waypoint tracking, and per-quest daily repeatability are implemented.
Generalized region/portal/crafting objectives and co-op reward attribution remain planned.
In the active-quest journal, each quest has a separate on/off tracking button;
the particle route has its own persistent toggle. Routes point to an NPC only
while the current objective is to visit that NPC or submit items to the quest
giver. Other objectives do not show an unrelated NPC route. Quest completion
shows a title and subtitle with the quest name and rewards.
Dialogue lookup first tries a definition whose ID matches the runtime NPC ID,
then uses the first definition of the same role. If neither exists, the NPC
still opens a short Vietnamese fallback dialogue; quest actions matching its
runtime ID are still shown. Add a matching entry in `npc-definitions.yml` when
you want custom dialogue and role-specific actions.
`rpg.npc.max-runtime-npcs` sets the runtime limit (default `100`, range `1-100`).
The command requires `vnmine.command.npc.admin` (default: operators). An NPC
can also be managed from **Menu chính → Admin → Quản lý NPC**. The GUI supports
creating an NPC at the admin's current location, editing its display name and
role, moving it to the current location, and deleting it after confirmation.
Creation asks for ID, role, and display name in chat; each prompt shows valid
formats and examples, and `!cancel` returns to the GUI. Editing arbitrary
dialogue templates, objective-specific reward actions, and custom skins remain
future work.

### World text displays

Operators with `vnmine.command.text.manage` can create a fixed text display at
their current position. The display definition and entity are persistent across
chunk unloads/restarts.

```
/vntext create <id> <facing> <rotation 0-360> <color> <scale> <text...>
/vntext edit <id> <facing> <rotation 0-360> <color> <scale> <text...>
/vntext move <id>
/vntext info <id>
/vntext list
/vntext remove <id>
```

Facing values are `UP`, `DOWN`, `NORTH`, `SOUTH`, `EAST`, and `WEST`. Colors
include Minecraft names such as `gold`, `red`, `aqua`, plus `orange` and hex
values like `#FFAA00`. Rotation is an independent angle around the text plane's
normal, from `0` to `360` degrees; it does not change which direction the text
faces. Scale must be from `0.1` to `32`; text can contain up to 256 characters.
The previous syntax without a rotation value is still accepted and uses `0`
degrees. For example:

```
/vntext create test-label UP 45 orange 9 KHU VỰC TEST
```

Definitions are saved in `plugins/VnMineHardcore/text-displays.yml`. The maximum
managed count is configured at `rpg.text-display.max-active` (default 100,
allowed range 1–500).

### Quest administration

`/vnquest admin` or **Menu chính → Admin → Quản lý nhiệm vụ** opens a paginated list of configured quests. Admins can create
a quest by entering a unique lowercase ID in chat, edit its display title and
NPC giver ID, enable or disable it, and delete it after a confirmation screen.
Chat input shows a valid example in color, supports `!cancel`, and reopens the
relevant editor after the response. Enabled quests are added to the matching runtime NPC dialogue by
giver ID; create the NPC with `/vnnpc create <id> <type> [name...]`.
The editor also supports AND requirements for minimum RPG level, completion of
another quest, and an exact held item (including RPG metadata). Requirements
are checked both when listing an NPC's quests and when accepting one. The
objective screen can edit the first kill-mob objective and add/remove up to 44
sequential objectives for killing mobs, collecting/turning in an exact item, or
killing a configured system boss. Exact item matching preserves metadata/PDC.
Items are submitted to the quest's giver NPC and consumed only after checking the
player's inventory; failed progress writes return items, using the persistent
delivery queue if the player disconnects. Boss objectives use the system boss ID marker and count players
who contributed damage to that encounter. Legacy `collect-rpg-item` objectives
remain supported as the first step. Progress is stored per step in SQLite and
advances only after its target count is reached. Objective changes/deletion are blocked while
players have that quest active, preventing progress indexes from shifting. The reward editor accepts up to 45 item
stacks and saves them to the quest definition; reward delivery uses the
persistent delivery queue. EXP and VNCOIN rewards have +/-1, +/-10, and +/-100
controls. Chat prompts accept `!cancel` and reopen the relevant editor.
An admin can set **Quest tự giao tiếp theo** in the quest editor. On successful
completion, the next quest is recorded in SQLite in the same transaction as the
completion reward. The system starts it when the player meets its configured
requirements and has an available quest slot; if all three slots are occupied,
the successor stays pending and is retried after a quest is completed, abandoned,
the player rejoins, or the player opens the quest journal. Links cannot point to themselves, form a cycle, or target
a missing quest. A pending link to a disabled definition is retained and logged
for admin repair instead of silently discarded.

Players can open **Hồ sơ RPG → Nhiệm vụ** or `/vnquest journal` to switch between
active quests and completed quest history. Selecting an active quest opens its
full objective/checkpoint list and reward preview, with separate controls to
select the tracked quest, enable or disable its navigation particles, or
abandon it. Particle visibility is saved per player and independent from quest
tracking. Abandonment requires confirmation; abandoned quests may be accepted
again. At most three quests can be active at once. The scoreboard lists each
active quest's current objective step and progress. Legacy Bukkit scoreboard
rows do not support click events, so use `/vnquest detail <id>` or select the
quest in the RPG journal to open details. Tracking draws a live ground route to
the quest-giver NPC in the same world; chevrons follow terrain instead of
cutting straight through obstacles. When the NPC is in another world, the
action bar tells the player which world to visit. `rpg.quests.tracking.particle-enabled`
is the server-wide particle switch and `update-interval-ticks` controls refresh
frequency. The per-player GUI switch disables particles without disabling the
quest or its tracking selection. Particles already sent to the client fade out
naturally after being disabled.

Quest definitions continue to be stored in `quests/quest-definitions.yml`.
The current objective set includes mob and system-boss kills, RPG/exact-item
collection, exact-item hand-in, and visiting a configured NPC ID. The objective
editor accepts sequential steps, so a quest can request multiple configured
hand-ins. In **Quản lý quest → Mục tiêu quest**, use **Thêm mục tiêu gặp NPC**
and enter an existing NPC ID; **Đặt bước đầu: gặp NPC** can make it the first
step. Hand-in objectives use the exact system item held by the admin when
configured. Block interaction, fishing, taming, enchanting, brewing, smelting,
crafting, item consumption/breaking/mending, shearing, entity damage, and
inventory-open objectives can use Paper events but are not implemented yet.
Vote objectives need an external vote-plugin integration; custom/cuboid
objectives need a stable extension API and region model. Cross-player party
credit outside boss contributions remains planned.

Custom `merchant` NPCs can have an individual shop. Use `/vnnpc edit <id>` or
Shift-right-click the NPC to open its editor. Shift-click a system-created RPG
item in the player's inventory to add it; left-click a listed item to edit its
price and right-click to remove it. **Cấu hình giá mua vào** opens a second
54-slot editor for buy orders. Add an RPG item, then set the quantity to hand in
and VNCOIN paid with `-1/-10/-100` and `+1/+10/+100` buttons. Save each list to
`npcs.yml`. Only system-created RPG items can be listed. Prices and offer counts are bounded by
`economy.merchant.custom.max-price` and `max-offers` in `config.yml`.

Clicking a merchant offer opens a confirmation screen that shows the quantity
and exact VNCOIN price; payment starts only after the player confirms. Merchant
purchases debit VNCOIN and create a persistent pending item delivery in the same
database transaction. If the player disconnects during a purchase, the
item is reconciled on next join. If the inventory is full, free a slot and run
`/vncoin` or rejoin to retry delivery; keep a delivery item in place until the
server confirms it. `system-merchant` NPCs use the shared catalog under
`economy.merchant.offers`, while custom merchants use their saved per-NPC list.
System and custom merchants show **Bán vật phẩm cho shop**. Players review the
exact item count and VNCOIN, confirm the sale, then matching items are removed
and the wallet ledger is credited through a durable SQLite sale intent. The
system first persists a transaction ID and temporarily locks the exact item
quantity; after the tagged inventory is saved and consumed, SQLite credits the
wallet once. Pending sales reconcile on player join or when `/vncoin` is opened.
If the wallet is at its balance limit, the sale is canceled and the item is
placed in the persistent merchant delivery queue. Escrow-locked items cannot
be moved or used, and are suppressed from death drops while their transaction
is pending. Debug and recovery records use the `[Economy][MerchantSale]` and
`[RPG-DEBUG][VNCOIN][MerchantSale]` log prefixes. This recovery flow still
needs server restart/crash testing before it can be considered verified.
A custom merchant only accepts RPG items
with a buy order configured at that exact NPC; unconfigured items have no sale
entry there. The system merchant only buys vanilla Materials listed in
`merchant-prices.yml`; an unlisted Material is not purchasable. The initial
allowlist includes the requested rates (10 iron ingots = 1 VNCOIN, 1 diamond =
10 VNCOIN, 1 Netherite upgrade template = 500 VNCOIN, a stack of raw meat = 10
VNCOIN, and a stack of cooked meat = 15 VNCOIN), plus conservative values for
selected farmable items. Use `/vnreload` (`vnmine.command.reload`) to reload the
allowlist. A malformed file leaves the last valid price table active. RPG skill
books, configured boss drops, quest-generated RPG drops/rewards, and generated
mercenary gear use per-item `sell-value`; `0` means the system merchant will
not buy them, and plugin-created items never fall back to their base Material
price. Restart after changing `skills/skill-books.yml`, quest definitions, or
`armies.yml`; boss sell values are reloaded by `/vnreload`. In
`quests/quest-definitions.yml`, set `objective.sell-value` for a
`collect-rpg-item` objective. For reward stacks, `reward.item-sell-values` is a
list aligned with `reward.items`, where each value is VNCOIN per item; existing
quest files receive default zero values automatically at startup. In
`armies.yml`, `gear-system.sell-value` controls generated mercenary gear, while
boss gear continues to use the per-boss `mercenary-gear-drops.sell-value`. A
system-merchant quote warns when the player
has enough of the same exact RPG item to satisfy a custom NPC's better per-item
rate. Confirming still sells at the system rate; cancel to use the named NPC and
world shown in the warning. Any other plugin-tagged item without a valid
explicit value remains unavailable to the system merchant.

Ground item nameplates display the best matching configured merchant rate as
`[quantity = VNCOIN]`; items with no configured sale rate show “Không có giá thu mua” instead of implying a universal fallback price.

Admins can run `/vncoin stats` to see the total VNCOIN issued, spent, net change,
and the busiest ledger sources for the configured reporting window. Set
`economy.inflation.window-days` in `config.yml` (default `30`, valid `1-365`)
to change that window. The command uses the existing
`vnmine.command.vncoin.admin` permission.

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
