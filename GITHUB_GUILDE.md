# Hướng dẫn xử lý lỗi Git/GitHub

## Các lỗi thường gặp và cách sửa

### 1. Lỗi `cannot exec '.git/hooks/pre-commit': Permission denied`

**Nguyên nhân:**
- File `.git/hooks/pre-commit` (hoặc các hook khác) bị thay thế bằng **thư mục** thay vì file script
- Thường xảy ra do lỗi hệ thống, script tự động, hoặc virus

**Cách chẩn đoán:**
```bash
# Kiểm tra xem hook có phải là thư mục không
ls -la .git/hooks/pre-commit

# Nếu kết quả hiển thị "drwxr-xr-x" (directory) thay vì "-rwxr-xr-x" (file) → bị lỗi
```

**Cách sửa:**

#### Phương án 1: Xóa thư mục hook và khởi tạo lại (Nhanh)
```bash
# Xóa tất cả các thư mục trong .git/hooks
sudo find .git/hooks -maxdepth 1 -type d ! -name 'hooks' -exec rm -rf {} +

# Hoặc xóa từng thư mục cụ thể
sudo rm -rf .git/hooks/pre-commit
sudo rm -rf .git/hooks/prepare-commit-msg
sudo rm -rf .git/hooks/commit-msg

# Khởi tạo lại git (tạo lại .git/hooks với file .sample)
git init
```

#### Phương án 2: Clone lại repo từ remote (Chắc chắn nhất)
```bash
# Nếu repo bị corrupt nặng, clone lại từ remote
cd /path/to/parent
mv old_repo old_repo_backup
git clone https://github.com/username/repo.git new_repo

# Copy code đã sửa từ backup vào repo mới
cp -r old_repo_backup/src new_repo/src

# Commit và push
cd new_repo
git add -A
git commit -m "Your message"
git push origin main
```

### 2. Lỗi `unknown object type 5 at offset` / `pack checksum mismatch`

**Nguyên nhân:**
- File `.git/objects/pack/*.pack` bị hỏng (corrupt)
- Thường xảy ra do mất điện, gián đoạn khi đang pull/push, hoặc lỗi disk

**Cách chẩn đoán:**
```bash
# Kiểm tra tính toàn vẹn của git objects
git fsck
# Nếu thấy lỗi "pack checksum mismatch" hoặc "unknown object type" → bị corrupt
```

**Cách sửa:**

#### Phương án 1: Clone lại repo (Khuyến nghị)
```bash
# Backup code hiện tại
cd /path/to/parent
mv repo repo_backup

# Clone lại từ remote
git clone https://github.com/username/repo.git repo

# Copy code đã sửa từ backup
cp -r repo_backup/src repo/src
cp -r repo_backup/pom.xml repo/

# Commit và push
cd repo
git add -A
git commit -m "Restore code after git corruption"
git push origin main
```

#### Phương án 2: Xóa pack file và fetch lại (Nếu muốn giữ lại git history)
```bash
# Xóa pack file corrupt
rm -rf .git/objects/pack/

# Fetch lại từ remote
git fetch origin
git reset --hard origin/main
```

### 3. Lỗi `fatal: unable to read <hash>`

**Nguyên nhân:**
- Object trong `.git/objects` bị thiếu hoặc hỏng
- Thường đi kèm với lỗi pack corrupt

**Cách sửa:**
- Làm theo Phương án 1 ở mục 2 (Clone lại repo)

## Quy trình phòng ngừa

### 1. Backup thường xuyên
```bash
# Trước khi thực hiện thay đổi lớn
git add -A
git commit -m "Backup before major changes"
git push origin main
```

### 2. Kiểm tra trạng thái git trước khi commit
```bash
# Kiểm tra git status
git status

# Kiểm tra git hooks
ls -la .git/hooks/ | grep -v ".sample"

# Nếu thấy thư mục (drwxr-xr-x) thay vì file (-rwxr-xr-x) → xử lý ngay
```

### 3. Sử dụng `git fsck` định kỳ
```bash
# Kiểm tra tính toàn vẹn của repository
git fsck

# Nếu có lỗi → clone lại ngay
```

## Workflow an toàn khi gặp lỗi

```
1. Phát hiện lỗi
   ↓
2. Kiểm tra mức độ nghiêm trọng
   - git fsck
   - ls -la .git/hooks/
   ↓
3. Quyết định phương án
   - Nhẹ: Xóa hooks và git init
   - Nặng: Clone lại từ remote
   ↓
4. Thực hiện sửa
   ↓
5. Kiểm tra lại
   - git status
   - git log --oneline -3
   ↓
6. Push lên remote
```

## Các lệnh hữu ích

```bash
# Xem trạng thái git
git status

# Xem log commit gần nhất
git log --oneline -5

# Kiểm tra tính toàn vẹn
git fsck

# Xem cấu hình git
git config --list --local

# Xem remote URL
git remote -v

# Force push (CHỈ DÙNG khi chắc chắn)
git push --force origin main
```

## Lưu ý quan trọng

1. **KHÔNG** dùng `git push --force` nếu không chắc chắn
2. **LUÔN** backup code trước khi sửa lỗi git
3. **KHÔNG** xóa thư mục `.git` nếu chưa backup
4. **NÊN** clone lại từ remote thay vì sửa lỗi phức tạp
5. **KIỂM TRA** git hooks trước mỗi commit

## Khi nào cần clone lại repo?

- Git objects bị corrupt (`pack checksum mismatch`, `unknown object type`)
- Không thể commit/push do lỗi hooks kéo dài
- Mất nhiều thời gian sửa lỗi hơn là clone lại
- Không có uncommitted changes quan trọng

## Khi nào chỉ cần xóa hooks?

- Chỉ có lỗi `cannot exec '.git/hooks/*': Permission denied`
- Git objects còn lành lặn (`git fsck` không báo lỗi)
- Chỉ có 1-2 thư mục hook bị lỗi