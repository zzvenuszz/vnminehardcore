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

### 👹 Boss Events
- **Random boss spawn** với HP cao, damage mạnh
- Boss có **cảnh báo** trước khi xuất hiện (Boss Bar + Title + Sound)
- Rơi item quý hiếm khi bị tiêu diệt
- Các boss: Wither, Ender Dragon, Giant, Ghast
- **Manual trigger**: `/vnboss <id> <warning> <duration>`

### 🌋 Disasters (14 types)
Tất cả có **cảnh báo** qua Boss Bar + Title + Sound:

#### Overworld Disasters
| ID | Name | Effect |
|----|------|--------|
| `bloodmoon` | 🌕 Blood Moon | Quái spawn x10, Strength + Speed + armor |
| `meteor` | ☄️ Meteor Shower | Sao băng rơi, explosion damage |
| `storm` | 🌊 Mega Storm | Sét đánh, mưa axit, mù |
| `solarflare` | 🔥 Solar Flare | Ngoài nắng = burn + blindness |
| `plague` | 🦠 Plague | Poison + Weakness + Hunger + Nausea |
| `tornado` | 🌪️ Tornado | Bị ném lên cao, block bị phá |
| `eclipse` | 📉 Solar Eclipse | Ban ngày → đêm, quái spawn |
| `earthquake` | 🌍 Earthquake | Block rơi, rung chuyển, Nausea |

#### Nether Disasters
| ID | Name | Effect |
|----|------|--------|
| `inferno` | 🔥 Inferno Storm | Lửa địa ngục, spawn Ghast/Magma Cube |
| `souleruption` | 💀 Soul Eruption | Soul sand nổ, Wither effect |
| `lavageyser` | 🌋 Lava Geyser | Cột lava phun trào từ dưới đất |

#### The End Disasters
| ID | Name | Effect |
|----|------|--------|
| `endsurge` | 👁️ End Surge | Spawn Shulker/Endermite, Levitation |
| `voidstorm` | 🌌 Void Storm | Blindness + Darkness + damage |
| `chorusexplosion` | 🌀 Chorus Explosion | Damage + random teleport |

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

### `/vnboss` Usage

```
/vnboss                                    - List all boss IDs
/vnboss wither 30 120                      - Wither Boss: 30s warning, 120s duration
/vnboss ender_dragon 60 180                - Ender Dragon: 60s warning, 180s duration
/vnboss giant 20 90                        - Giant: 20s warning, 90s duration
```

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