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
- [Changelog](#-changelog)

---

## 🎯 Features

### ☠️ Death System
- **Chết = Ban vĩnh viễn** (Username + IP)
- Combat tag: Logout khi combat → bị ban
- Broadcast death + âm thanh wither
- Death counter + thống kê

### ⚔️ Combat
- **Damage multipliers**: Mob x2, Fall x2, Fire x2, Drowning x2, Explosion x1.5, PvP x1.25
- Mobs có thêm máu (x1.5) + armor ngẫu nhiên
- **Natural Regen**: Có thể tắt hoàn toàn hoặc giảm % (configurable regen-multiplier)
- **Combat Tag**: Thời gian tag combat configurable. Thoát game khi đang combat = ban
- Ender Pearl gây sát thương 5♥
- Táo vàng enchanted (Notch Apple) phát nổ
- Friendly mobs không kích hoạt combat tag

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
- **Sợ hầm**: Mining Fatigue + Darkness dưới y=30
- **Chóng mặt**: Nausea + Slowness trên y=200

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
| `vnmine.hardcore.admin` | Admin commands (status, unban, reload, trigger disaster) | `op` |
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

For the **full configuration** with bilingual comments, see `plugins/VnMineHardcore/config.yml`.

---

## 📊 Log Files

Located in `plugins/VnMineHardcore/`:

| File | Description |
|------|-------------|
| `deaths.log` | Detailed death records |
| `bans.log` | Ban records with IP and UUID |
| `disasters.log` | Disaster event logs |
| `stats.yml` | Player statistics |

---

## 📝 Changelog

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