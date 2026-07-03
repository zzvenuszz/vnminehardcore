# 📘 Hướng Dẫn Build Plugin Paper 26.1.2 - Kinh Nghiệm Thực Tế

## 1. Môi Trường

- **OS**: Linux (Amazon Linux 2023)
- **JDK**: OpenJDK 25 (Zulu25.34+17-CA)
- **Maven**: 3.9.x
- **Server**: Paper 26.1.2 (Minecraft 26.2)
- **Paper API**: `1.21.4-R0.1-SNAPSHOT` (tương thích ngược với Paper 26.1.2)

## 2. Cấu Hình pom.xml (ĐÃ KIỂM NGHIỆM)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.yourgroup</groupId>
    <artifactId>YourPluginName</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>21</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <repositories>
        <repository>
            <id>papermc</id>
            <url>https://repo.papermc.io/repository/maven-public/</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>io.papermc.paper</groupId>
            <artifactId>paper-api</artifactId>
            <version>1.21.4-R0.1-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>YourPluginName</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                </configuration>
            </plugin>
        </plugins>
        <resources>
            <resource>
                <directory>src/main/resources</directory>
                <filtering>false</filtering>
            </resource>
        </resources>
    </build>
</project>
```

## 3. Cấu Hình plugin.yml

```yaml
name: YourPluginName
version: 1.0.0
main: com.yourpackage.YourMainClass
api-version: '1.21'          # QUAN TRỌNG: dùng '1.21' chứ không phải '26.1.2'
author: YourName
description: Your description
```

## 4. CÁC LỖI THƯỜNG GẶP & CÁCH KHẮC PHỤC

### Lỗi 1: JDK 25 Compiler Bug với `Set.of()`
**Triệu chứng**: 
```
Fatal error compiling: Cannot load from object array because "this.hashes" is null
```
**Nguyên nhân**: JDK 25 có bug với `Set.of()` khi số lượng phần tử > 10.
**Cách fix**: KHÔNG dùng `Set.of()`. Thay bằng:
```java
// ❌ KHÔNG DÙNG:
private static final Set<String> BIOMES = Set.of("a", "b", "c", ...);

// ✅ DÙNG:
private static final Set<String> BIOMES = Collections.unmodifiableSet(
    new HashSet<>(Arrays.asList("a", "b", "c", ...))
);
```

### Lỗi 2: `addBan()` ambiguous
**Triệu chứng**:
```
error: reference to addBan is ambiguous
```
**Nguyên nhân**: Paper API có overloaded method với Instant và Date.
**Cách fix**: Cast null sang kiểu cụ thể:
```java
// Cho name ban (dùng Instant):
nameBanList.addBan(name, reason, (java.time.Instant) null, source);

// Cho IP ban (dùng Date):
ipBanList.addBan(ip, reason, (java.util.Date) null, source);
```

### Lỗi 3: Paper API version không tìm thấy
**Triệu chứng**: `Could not find artifact io.papermc.paper:paper-api:jar:26.1.2`
**Nguyên nhân**: Version 26.1.2 không publish trên Maven repo.
**Cách fix**: Dùng `1.21.4-R0.1-SNAPSHOT` (tương thích ngược):
```xml
<version>1.21.4-R0.1-SNAPSHOT</version>
```

### Lỗi 4: Tất cả player bị ban khi reload plugin
**Triệu chứng**: Mọi người đều bị "Banned" sau khi reload plugin.
**Nguyên nhân**: Plugin cũ đã ban họ, ban còn tồn đọng.
**Cách fix**: Clear ban cũ khi startup:
```java
public BanManager(JavaPlugin plugin) {
    clearAllBans(); // Gọi trong constructor
}

public void clearAllBans() {
    BanList nameBanList = Bukkit.getBanList(BanList.Type.NAME);
    for (BanEntry entry : nameBanList.getBanEntries()) {
        if ("YourPluginName".equals(entry.getSource())) {
            nameBanList.pardon(entry.getTarget());
        }
    }
    // Tương tự cho IP ban list
}
```

### Lỗi 5: `Material.WATER_BOTTLE` không tồn tại
**Triệu chứng**: `cannot find symbol: variable WATER_BOTTLE`
**Nguyên nhân**: Paper 1.21.4 API không có Material.WATER_BOTTLE.
**Cách fix**: Dùng `Material.POTION` và kiểm tra PotionMeta:
```java
// Kiểm tra nước uống được:
public static boolean canDrink(ItemStack item) {
    if (item == null) return false;
    Material type = item.getType();
    return type == Material.WATER_BUCKET || type == Material.POTION;
}
```

### Lỗi 6: Maven cache bị lỗi "ZipFile invalid LOC header"
**Triệu chứng**:
```
error: error reading /home/ubuntu/.m2/repository/.../xxx.jar; ZipFile invalid LOC header (bad signature)
```
**Nguyên nhân**: Các file JAR trong Maven local repository bị hỏng do tải xuống không hoàn chỉnh hoặc bị gián đoạn.
**Cách fix**: Xóa toàn bộ Maven cache và build lại từ đầu:
```bash
# Xóa toàn bộ cache
rm -rf /home/ubuntu/.m2/repository

# Build lại (Maven sẽ tự động tải lại tất cả dependencies)
cd /path/to/plugin
timeout 15m mvn clean package
```

### Lỗi 7: `maven-resources-plugin` thiếu dependencies
**Triệu chứng**:
```
[ERROR] Failed to execute goal ...maven-resources-plugin:3.2.0 hoặc 3.3.x:resources:
A required class was missing: org/apache/commons/lang3/StringUtils
hoặc org/apache/commons/io/output/DeferredFileOutputStream
```
**Nguyên nhân**: Phiên bản maven-resources-plugin 3.2.0, 3.3.0 và 3.3.1 có lỗi với Maven 3.9.x, thiếu dependencies commons-lang3 hoặc commons-io.
**Cách fix**: **Bỏ hoàn toàn plugin này khỏi pom.xml**. Maven đã có sẵn resources plugin mặc định hoạt động tốt:
```xml
<!-- ❌ KHÔNG DÙNG - XÓA PHẦN NÀY -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-resources-plugin</artifactId>
    <version>3.2.0 hoặc 3.3.x</version>
    <dependencies>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
            <version>3.12.0</version>
        </dependency>
    </dependencies>
</plugin>

<!-- ✅ CHỈ DÙNG maven-compiler-plugin -->
<build>
    <finalName>YourPluginName</finalName>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>
                <release>21</release>
                <fork>true</fork>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## 5. LỆNH BUILD (ĐÃ KIỂM NGHIỆM)

### Cách 1: Maven (khuyên dùng)
```bash
cd /path/to/plugin
timeout 15m mvn clean package
```
File JAR sẽ ở: `target/YourPluginName.jar`

**Lưu ý**: Luôn dùng `timeout 15m` để tránh build treo quá lâu.

### Cách 2: Direct javac (khi Maven bị lỗi compiler)
```bash
# Lấy classpath từ Maven
mvn dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt

# Compile
CP=$(cat /tmp/cp.txt)
javac -XDstringConcat=inline -d target/classes -cp "$CP" src/main/java/**/*.java

# Copy resources
cp -r src/main/resources/* target/classes/

# Tạo JAR
cd target/classes && jar cf ../YourPluginName.jar .
```

## 6. KIỂM TRA SAU KHI BUILD

```bash
# Kiểm tra file JAR
ls -lh target/*.jar

# Tính SHA256
sha256sum target/*.jar

# Kiểm tra thời gian build
date -u "+%Y-%m-%d %H:%M:%S UTC"

# Kiểm tra nội dung JAR
jar tf target/YourPluginName.jar | head -20
```

## 7. CẤU TRÚC THƯ MỤC MẪU

```
YourPlugin/
├── pom.xml
├── README.md
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── yourpackage/
│   │   │           ├── YourPlugin.java          (Main class extends JavaPlugin)
│   │   │           ├── listeners/
│   │   │           │   └── YourListener.java     (implements Listener)
│   │   │           └── managers/
│   │   │               └── YourManager.java
│   │   └── resources/
│   │       ├── plugin.yml
│   │       └── config.yml                       (nếu có)
│   └── test/                                     (optional)
└── target/
    └── YourPlugin.jar                            (output)
```

## 8. LƯU Ý QUAN TRỌNG

1. **api-version**: Luôn dùng `'1.21'` trong plugin.yml (dấu nháy đơn bắt buộc)
2. **Java version**: Code với Java 21 features (pattern matching, records, sealed classes OK)
3. **Paper API**: Dùng `1.21.4-R0.1-SNAPSHOT` cho Paper 26.1.2
4. **Set.of()**: TRÁNH dùng với JDK 25 - thay bằng `Collections.unmodifiableSet(new HashSet<>(Arrays.asList(...)))`
5. **addBan()**: Cast null sang Instant hoặc Date tùy overload
6. **Clear bans**: Luôn clear ban cũ khi plugin startup để tránh false bans
7. **Console logs**: Thêm logger vào mọi listener để dễ debug
8. **Config**: Luôn có config.yml với saveDefaultConfig() để người dùng tùy chỉnh
9. **Maven cache**: Nếu gặp lỗi "ZipFile invalid LOC header", xóa toàn bộ cache: `rm -rf /home/ubuntu/.m2/repository`
10. **pom.xml**: KHÔNG dùng maven-resources-plugin (lỗi với Maven 3.9.x), chỉ dùng maven-compiler-plugin