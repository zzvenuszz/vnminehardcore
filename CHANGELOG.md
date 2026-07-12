# Changelog

## [v3.0.0] - 2025-07-12

### Added
- Config tách rời: Boss và Disaster không còn nằm trong `config.yml`
- Thêm `bosses/*.yml`: Mỗi boss 1 file, thêm boss mới chỉ cần thêm file
- Thêm `disasters/*.yml`: Mỗi disaster 1 file, thêm disaster mới chỉ cần thêm file
- Action system: Config-driven cho disaster, cho phép custom hành vi hoàn toàn từ file
- Weighted target system trong disaster actions (player, mob, ground, trees,...)
- Condition system cho disaster (only-night, only-day, worlds, weather)
- Boss immunity config (sunlight_burn, fire, poison,...) trong boss file

### Changed
- `config.yml`: Giảm từ 1481 dòng xuống ~721 dòng (bỏ sections boss-events, disasters, safe-zone)
- `ConfigManager.java`: Thêm load từ file rời (`bosses/*.yml`, `disasters/*.yml`, `disasters/_settings.yml`)
- `BossEventManager.java`: Sửa `EntityCombustEvent` để tôn trọng boss immunity

### Removed
- Config section `boss-events` (khoảng 592 dòng) → chuyển sang `bosses/*.yml`
- Config section `disasters` (khoảng 239 dòng) → chuyển sang `disasters/*.yml` + `_settings.yml`
- Config section `disasters.safe-zone` → chuyển vào `disasters/_settings.yml`
- Config section `disasters.messages` → chuyển vào `disasters/_settings.yml`

### Technical Debt
- `BossEventManager.java`: ~760 dòng (giữ nguyên, logic spawn/drop/AI không đổi)
- `DisasterManager.java`: ~1082 dòng (cần refactor action engine nếu disaster mới)
- `ConfigManager.java`: ~530 dòng (thêm ~150 dòng cho external config loader)

### File Stats

#### Deleted from config.yml
| Feature | Issues (lines) removed |
|---------|----------------------|
| boss-events | ~592 dòng (1 section, 9 bosses) |
| disasters | ~239 dòng (1 section, 14 disasters) |
| disasters.safe-zone | ~15 dòng |
| disasters.messages | ~19 dòng |

#### Added to bosses/*.yml
| File | Features/Issues |
|------|----------------|
| `_settings.yml` | General settings (interval, spawn-radius) |
| `wither.yml` | 1 boss + immunity + drops (7 items) |
| `ender_dragon.yml` | 1 boss + immunity + drops (10 items) |
| `ghast.yml` | 1 boss + immunity + drops (9 items) |
| `zombie_boss.yml` | 1 boss + immunity + drops (10 items) |
| `skeleton_boss.yml` | 1 boss + immunity + drops (9 items) |
| `spider_boss.yml` | 1 boss + immunity + drops (8 items) |
| `creeper_boss.yml` | 1 boss + immunity + drops (8 items) |
| `enderman_boss.yml` | 1 boss + immunity + drops (8 items) |
| `witch_boss.yml` | 1 boss + immunity + drops (9 items) |
| `ravager_boss.yml` | 1 boss + immunity + drops (7 items) |
| `vindicator_boss.yml` | 1 boss + immunity + drops (7 items) |
| `phantom_boss.yml` | 1 boss + immunity + drops (8 items) |

#### Added to disasters/*.yml
| File | Features/Issues |
|------|----------------|
| `_settings.yml` | Global settings, safe-zone, messages |
| `blood-moon.yml` | 1 disaster + 3 actions (spawn, potion, actionbar) |
| `meteor.yml` | 1 disaster + 2 actions (lightning, explosion) |
| `mega-storm.yml` | 1 disaster + 4 actions (lightning, damage, potion, weather) |
| `solar-flare.yml` | 1 disaster + 3 actions (damage, fire, potion) |
| `plague.yml` | 1 disaster + 1 action (potion effects) |
| `tornado.yml` | 1 disaster + 1 action (velocity) |
| `eclipse.yml` | 1 disaster + 3 actions (time, potion, spawn) |
| `earthquake.yml` | 1 disaster + 2 actions (potion, falling_block) |
| `inferno-storm.yml` | 1 disaster + 4 actions (damage, fire, place_block, spawn) |
| `soul-eruption.yml` | 1 disaster + 4 actions (damage, potion, explosion, spawn) |
| `lava-geyser.yml` | 1 disaster + 3 actions (damage, fire, place_block) |
| `end-surge.yml` | 1 disaster + 2 actions (spawn, potion) |
| `void-storm.yml` | 1 disaster + 2 actions (damage, potion) |
| `chorus-explosion.yml` | 1 disaster + 2 actions (damage, teleport) |