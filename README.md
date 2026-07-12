# 🔥 VnMineHardcore

**Hell Difficulty Plugin for Paper 1.21.4**

Plugin nâng độ khó Minecraft lên mức khó gấp đôi. **Chết 1 lần = Ban vĩnh viễn!**

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
- **Chết = Ban vĩnh viễn** (Username + IP)
- Combat tag: Logout khi combat → bị ban
- Broadcast death + âm thanh wither
- Death counter + thống kê

### 💀 Death Penalty System
- **Giảm chỉ số mỗi lần chết**: Max HP, sát thương gây ra, tăng sát thương nhận vào
- **Phục hồi sau thời gian sống sót** (configurable, mặc định 300s)
- **Tối đa 5 stack** penalty, tránh penalty quá lớn
- **Các chỉ số bị ảnh hưởng**:
  - Max Health (giảm %)
  - Damage Dealt (giảm %)
  - Incoming Damage (tăng %)
  - Regen (giảm %)
  - Food Drain (tăng %)
  - Thirst Drain (tăng %)
  - Max Thirst (giảm)

### 🏷 Death Rename System
- **Tự động đổi tên hiển thị** trong CHAT và TAB LIST sau khi chết
- **Placeholder hỗ trợ**: `<name>`, `<count>`, `000` (padding số 0)
- **Ví dụ**: `<name> ☠ <count>` → `hoanbh ☠ 3`
- Không ảnh hưởng đến tên thật của người chơi

### ⚔️ Combat
- **Damage multipliers**: Mob x2, Fall x2, Fire x2, Drowning x2, Explosion x1.5, PvP x1.25
- Mobs có thêm máu (x1.5) + armor ngẫu nhiên
- **Natural Regen**: Có thể tắt hoàn toàn hoặc giảm % (configurable regen-multiplier)
- **Combat Tag**: Thời gian tag combat configurable. Thoát game khi đang combat = ban
- Ender Pearl gây sát thương 5♥
- Táo vàng enchanted (Notch Apple) phát nổ
- Friendly mobs không kích hoạt combat tag
- **Witch Speed Boost**: Witch có Speed II vĩnh viễn
- **Creeper Explosion x2**: Bán kính nổ Creeper gấp đôi
- **Skeleton Arrow Poison**: Mũi tên Skeleton gây Poison + Slowness

### 🍔 Hunger System
- Đói nhanh (mỗi 4s), chết đói 1♥/4s
- Ăn hồi 50% (configurable), không saturation
- Ngộ độc thức ăn sống: thịt thối, cá nóc, khoai tây độc...
- **Nghẹn** khi ăn >3 lần trong 10s
- Suspicious Stew: hiệu ứng ngẫu nhiên

### 💧 Thirst System
- Thanh khát (action bar) với màu sắc & trạng thái
- Drain mỗi 8s, mất máu khi hết khát
- Uống từ: chai nước, xô nước, nguồn nước (sông/biển/vạc)
- **Natural Water**: Uống nước sông/biển = damage mỗi giây + Nausea + Hunger
- Tất cả thông số đều configurable

### 🌍 Environment
- **Nhiệt độ**: Nóng ở sa mạc (burn), lạnh ở tuyết (slow)
- **Sương mù**: Darkness effect configurable (interval, duration, amplifier)
- **Chặn ngủ**: Damage khi cố ngủ
- **Cây chậm**: 25% chậm hơn
- **Công cụ hao mòn**: 1.5x nhanh hơn
- **Cấm bay** (trừ permission bypass)
- **Sợ hầm**: Mining Fatigue + Darkness dưới y=30 (giảm khi cầm đuốc)
- **Chóng mặt**: Nausea + Slowness trên y=200 (giảm khi đeo elytra)
- **Nước lạnh**: Damage khi ở dưới nước lạnh
- **Mưa axit**: Damage khi ở ngoài trời mưa

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

### 👹 Boss Events (12 types)
Tất cả boss đều **enabled by default**:

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

**Manual trigger**: `/vnboss <id> <warning> <duration>`

**Boss AI Features:**
- **Priority targeting**: Player → Friendly mob → Hostile mob → Wander
- **Block destruction**: Boss có thể phá hủy địa hình
- **Smart movement**: Tránh kẹt, teleport khi cần
- **Unique behaviors**: Mỗi boss có AI đặc trưng
- **Immunities**: Configurable (sunlight-burn, fire, fall-damage, wither, poison)
- **Drops**: Configurable với chance, min/max amount

### 🌋 Disasters (14 types)
Tất cả disasters đều **enabled by default**:

| ID | Name | Dimension | Effect |
|----|------|-----------|--------|
| `bloodmoon` | 🌕 Blood Moon | Overworld | Quái spawn x10, Strength + Speed |
| `meteor` | ☄️ Meteor Shower | Overworld | Sao băng rơi, explosion damage |
| `storm` | 🌊 Mega Storm | Overworld | Sét đánh, mưa axit, mù |
| `solarflare` | 🔥 Solar Flare | Overworld | Burn + blindness ngoài trời |
| `plague` | 🦠 Plague | Overworld | Poison + Weakness + Hunger |
| `tornado` | 🌪️ Tornado | Overworld | Bị ném lên cao, phá block |
| `eclipse` | 📉 Solar Eclipse | Overworld | Ban ngày → đêm, quái spawn |
| `earthquake` | 🌍 Earthquake | Overworld | Block rơi, rung chuyển |
| `inferno` | 🔥 Inferno Storm | Nether | Lửa địa ngục, spawn Ghast/Magma |
| `souleruption` | 💀 Soul Eruption | Nether | Soul sand nổ, Wither effect |
| `lavageyser` | 🌋 Lava Geyser | Nether | Cột lava phun trào |
| `endsurge` | 👁️ End Surge | The End | Spawn Shulker/Endermite |
| `voidstorm` | 🌌 Void Storm | The End | Blindness + Darkness + damage |
| `chorusexplosion` | 🌀 Chorus Explosion | The End | Damage + random teleport |

**Manual trigger**: `/vnevent <id> <warning> <duration>`

**Disable specific disasters:**
```yaml
disasters:
  enabled-disasters:
    blood-moon: true
    meteor: true
    # ... etc
    chorus-explosion: false  # ← Disable this
```

---

## 📥 Requirements

| Requirement | Version |
|-------------|---------|
| **Server** | Paper 1.21.4 (26.2+) |
| **Java** | 21+ |
| **API** | Paper API 1.21.4 |

---

## 📥 Installation

1. Download `VnMineHardcore.jar` from the [releases page](#)
2. Place the JAR file in your server's `plugins/` directory
3. Restart your server (or use `/plugman load VnMineHardcore`)
4. Edit `plugins/VnMineHardcore/config.yml` to your liking
5. Use `/vnreload` to reload config without restarting

---

## 🎮 Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/vnstats` | View your stats (deaths, survival time, mob kills) | `vnmine.hardcore.stats` |
| `/vnhardcore` | View plugin status | `vnmine.hardcore.admin` |
| `/vnhardcore unban <player>` | Unban a player | `vnmine.hardcore.admin` |
| `/vnevent` | List available disaster IDs | `vnmine.hardcore.admin` |
| `/vnevent <id> <warning> <duration>` | Manually trigger a disaster | `vnmine.hardcore.admin` |
| `/vnboss` | List available boss IDs | `vnmine.hardcore.admin` |
| `/vnboss <id> <warning> <duration>` | Manually trigger a boss | `vnmine.hardcore.admin` |
| `/vnreload` | Reload config.yml | `vnmine.hardcore.admin` |
| `/vnhelp` | Show command help | `vnmine.hardcore.stats` |
| `/vn` | Main command. Use `/vn help` for details | `vnmine.hardcore.stats` |

### `/vnevent` Usage

```
/vnevent                                    - List all disaster IDs
/vnevent bloodmoon 30 60                   - Blood Moon: 30s warning, 60s duration
/vnevent meteor 10 30                      - Meteor Shower: 10s warning, 30s duration
/vnevent earthquake 5 20                   - Earthquake: 5s warning, 20s duration
/vnevent inferno 15 40                     - Inferno Storm: 15s warning, 40s duration
```

**All Disaster IDs:**
- `bloodmoon` - 🌕 Blood Moon
- `meteor` - ☄️ Meteor Shower
- `storm` - 🌊 Mega Storm
- `solarflare` - 🔥 Solar Flare
- `plague` - 🦠 Plague
- `tornado` - 🌪️ Tornado
- `eclipse` - 📉 Solar Eclipse
- `earthquake` - 🌍 Earthquake
- `inferno` - 🔥 Inferno Storm
- `souleruption` - 💀 Soul Eruption
- `lavageyser` - 🌋 Lava Geyser
- `endsurge` - 👁️ End Surge
- `voidstorm` - 🌌 Void Storm
- `chorusexplosion` - 🌀 Chorus Explosion

### `/vnboss` Usage

```
/vnboss                                    - List all boss IDs
/vnboss wither 30 120                      - Wither Boss: 30s warning, 120s duration
/vnboss ender_dragon 60 180                - Ender Dragon: 60s warning, 180s duration
/vnboss zombie_boss 20 90                  - Zombie Boss: 20s warning, 90s duration
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

---

## 🔐 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `vnmine.hardcore.stats` | View statistics (/vnstats, /vnhelp) | `true` |
| `vnmine.hardcore.admin` | Admin commands (status, unban, reload, trigger disaster/boss) | `op` |
| `vnmine.hardcore.bypass` | Bypass flight block | — |

---

## ⚙️ Configuration

File: `plugins/VnMineHardcore/config.yml`

All parameters have sensible defaults and are documented with **bilingual comments** (Tiếng Việt + English).

### Death Section
```yaml
death:
  ban-on-death: true        # Ban player on death
  ban-ip: true              # Also ban IP address
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
    max-hp-per-death: 1.0           # Max HP reduction per death (%)
    max-thirst-per-death: 1.0       # Max thirst reduction per death (%)
    damage-per-death: 0.5           # Damage dealt reduction per death (%)
    incoming-damage-per-death: 0.5  # Incoming damage increase per death (%)
    regen-per-death: 0.5            # Regen reduction per death (%)
    food-drain-per-death: 0.5       # Food drain increase per death (%)
    thirst-drain-per-death: 0.5     # Thirst drain increase per death (%)
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
  disable-natural-regen: true          # Disable natural regen
  regen-multiplier: 1.0               # Regen % (if regen enabled)
  combat-tag-duration-seconds: 30     # Combat tag duration
  ender-pearl-damage: 10.0            # Ender pearl damage (5♥)
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

### Environment - Fog
```yaml
environment:
  fog:
    enabled: true                     # Enable fog (Darkness effect)
    interval-ticks: 80               # Interval between fog (4s)
    effect-duration-ticks: 100       # Effect duration (5s)
    effect-amplifier: 0              # Darkness amplifier (0 = mild)
```

### Boss Events - Enable/Disable
```yaml
boss-events:
  bosses:
    wither:
      enabled: true                   # Enable/disable this boss
    ender_dragon:
      enabled: true
    ghast:
      enabled: true
    # ... all 12 bosses
```

### Disasters - Enable/Disable
```yaml
disasters:
  enabled-disasters:
    blood-moon: true
    meteor: true
    # ... all 14 disasters
    chorus-explosion: true
```

### Disasters - Custom Messages
```yaml
disasters:
  messages:
    warning-title: "§4§l⚠ CẢNH BÁO THIÊN TAI ⚠"
    warning-subtitle: "§c{name}\n§e§lSẽ xảy ra trong {time} giây!"
    warning-broadcast: "§4§l⚠ {name} §r§cđang đến gần!"
    countdown-broadcast: "§4§l⚠ {name} §csẽ xảy ra trong §4§l{time}§c giây!"
    active-broadcast: "§4§l{name} - {message} (§e{duration}s§4)"
    end-broadcast: "§a§l✅ {name} đã kết thúc!"
```

### Logging Section
```yaml
logging:
  console-debug: false          # Enable debug console logs
  log-deaths: true              # Log deaths
  log-bans: true                # Log bans
  log-disasters: true           # Log disasters
```

For the **full configuration** with bilingual comments, see `plugins/VnMineHardcore/config.yml`.

---

## 📖 Disaster Config Guide

### Cấu trúc file disaster

Mỗi disaster được cấu hình trong file riêng tại `plugins/VnMineHardcore/disasters/<id>.yml`.

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
duration-seconds: 120
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
| `duration-seconds` | Thời gian tồn tại | `120` |
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

---

## 📊 Log Files

Located in `plugins/VnMineHardcore/`:

| File | Description |
|------|-------------|
| `deaths.log` | Detailed death records |
| `bans.log` | Ban records with IP and UUID |
| `disasters.log` | Disaster event logs |
| `stats.yml` | Player statistics |
| `death-penalty.yml` | Death penalty data (stacks, recovery) |

---

## 📝 Changelog

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
- **Boss AI**: Smart targeting (Player → Friendly mob → Hostile mob → Wander), block destruction, stuck detection
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
- Death system with permanent ban
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

- **GitHub Issues**: [Report bugs](#)
- **Discord**: [Join our server](#)

---

## 📜 License

This project is licensed under the MIT License.

---

**VnMineHardcore** - *Chơi là chết, sống là quý!*