# VnMineHardcore

Plugin hardcore cực kỳ khó nhằn cho Minecraft Paper 1.21+.

## Tính năng chính

- ☠ **Chết = Ban vĩnh viễn** (Username + IP)
- ⚔ **Sát thương nhân hệ số** (quái, ngã, lửa, nổ, PvP...)
- 🍔 **Hệ thống đói** (hao hụt nhanh, ngộ độc thức ăn sống, nghẹn khi ăn nhanh)
- 💧 **Hệ thống khát nước** (uống từ nguồn, chai, xô; nước tự nhiên gây độc)
- 🌍 **Môi trường khắc nghiệt** (nhiệt độ, sương mù, chặn ngủ, chóng mặt, sợ hầm)
- 🌋 **Thiên tai tự động** (Blood Moon, Meteor, Storm, Solar Flare, Plague, Tornado, Eclipse)
- 🏷 **Đổi tên hiển thị sau khi chết** (tùy chỉnh format trong config)

## Lệnh

| Lệnh | Mô tả | Quyền |
|------|-------|-------|
| `/vnstats` | Xem thống kê cá nhân (số lần chết, thời gian sống, quái giết) | `vnmine.hardcore.stats` |
| `/vnhardcore` | Xem trạng thái plugin | `vnmine.hardcore.admin` |
| `/vnhardcore unban <player>` | Bỏ ban người chơi | `vnmine.hardcore.admin` |
| `/vn death reset <player>` | Reset số lần chết của player về 0 | `vnmine.hardcore.admin` |
| `/vnevent` | Gọi thiên tai thủ công (xem danh sách) | `vnmine.hardcore.admin` |
| `/vnevent <id> <warning(s)> <duration(s)>` | Gọi thiên tai cụ thể | `vnmine.hardcore.admin` |
| `/vnreload` | Tải lại file cấu hình config.yml | `vnmine.hardcore.admin` |
| `/vnhelp` | Hiển thị hướng dẫn tất cả lệnh | `vnmine.hardcore.stats` |

## Cấu hình (config.yml)

### Rename - Đổi tên hiển thị sau khi chết

```yaml
rename:
  # Bật/tắt tự động đổi tên hiển thị sau khi chết
  enabled: false

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
  name-structure: "<name> đã chết 000 lần"
```

**Lưu ý:** Chức năng này chỉ thay đổi **tên hiển thị** trong chat và tab list (khi bấm phím Tab), **không** thay đổi tên thật của người chơi (`player.getName()`).

### Disasters - Thiên tai

```yaml
disasters:
  # [VI] Bật hệ thống thiên tai tự động
  # [EN] Enable automatic disaster system
  enabled: true

  # [VI] Thời gian tối thiểu giữa các lần kiểm tra thiên tai (giây)
  # [EN] Minimum seconds between disaster checks
  min-interval-seconds: 1200

  # [VI] Thời gian cảnh báo trước khi thiên tai xảy ra (giây)
  # [EN] Warning time in seconds before a disaster strikes
  warning-seconds: 60

  # [VI] Các thiên tai được cấu hình theo object
  # [EN] Disasters configured as objects
  types:
    # [VI] Blood Moon - Trăng máu, tăng độ khó sinh sản quái vật
    # [EN] Blood Moon - Blood red moon, increases mob spawn difficulty
    blood-moon:
      # [VI] Bật/tắt thiên tai này
      # [EN] Enable/disable this disaster
      enabled: true
      # [VI] Tên hiển thị
      # [EN] Display name
      name: "Trăng Máu"
      # [VI] Thời gian kéo dài (giây)
      # [EN] Duration in seconds
      duration-seconds: 300
      # [VI] Tỷ lệ spawn quái vật tăng gấp (1.0 = tắt, 2.0 = gấp đôi)
      # [EN] Mob spawn rate multiplier (1.0 = off, 2.0 = double)
      spawn-multiplier: 2.0

    # [VI] Mega Storm - Bão lớn, gió mạnh và sét
    # [EN] Mega Storm - Large storm with strong winds and lightning
    mega-storm:
      enabled: true
      name: "Bão Rực Rỡ"
      duration-seconds: 180
      # [VI] Tần suất sét (giây)
      # [EN] Lightning frequency (seconds)
      lightning-frequency-seconds: 5
      # [VI] Sát thương từ sét (tim)
      # [EN] Lightning damage (hearts)
      lightning-damage: 4.0

    # [VI] Solar Flare - Tia nắng, gây thiêu đốt
    # [EN] Solar Flare - Solar rays causing burning
    solar-flare:
      enabled: true
      name: "Tia Nắng"
      duration-seconds: 120
      # [VI] Sát thương mỗi giây (tim)
      # [EN] Damage per second (hearts)
      damage-per-second: 1.0
```

## Yêu cầu

- Minecraft Paper 1.21+
- Java 21+

## Cài đặt

1. Tải file `.jar` từ bản phát hành (Release)
2. Đặt file vào thư mục `plugins/` của server
3. Khởi động lại server
4. Chỉnh sửa `plugins/VnMineHardcore/config.yml` theo nhu cầu
5. Gõ `/vnreload` để áp dụng thay đổi

## Build từ mã nguồn

```bash
cd VnMineHardcore
mvn clean package
```

File `.jar` sẽ được tạo trong thư mục `target/`.