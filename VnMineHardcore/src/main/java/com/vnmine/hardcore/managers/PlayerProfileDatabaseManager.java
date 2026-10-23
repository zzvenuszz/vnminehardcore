package com.vnmine.hardcore.managers;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class PlayerProfileDatabaseManager {
    public static final int SCHEMA_VERSION = 6;
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        .withZone(ZoneOffset.UTC);

    private final File databaseFile;
    private final File backupDirectory;
    private String recoveryMessage;
    private int backupRetentionCount;
    private int writeRetries;
    private int writeRetryDelayMillis;
    private volatile CharacterProgression.Rules progressionRules = CharacterProgression.Rules.defaults();

    public PlayerProfileDatabaseManager(File dataFolder) {
        this(dataFolder, 12, 2, 50);
    }

    public PlayerProfileDatabaseManager(File dataFolder, int backupRetentionCount, int writeRetries,
                                        int writeRetryDelayMillis) {
        if (dataFolder == null) {
            throw new IllegalArgumentException("dataFolder cannot be null");
        }
        configureMaintenance(backupRetentionCount, writeRetries, writeRetryDelayMillis);
        File dataDirectory = new File(dataFolder, "data");
        this.databaseFile = new File(dataDirectory, "player_profiles.db");
        this.backupDirectory = new File(dataDirectory, "backups");
    }

    public File getDatabaseFile() {
        return databaseFile;
    }

    public String getRecoveryMessage() {
        return recoveryMessage;
    }

    public synchronized void configureMaintenance(int backupRetentionCount, int writeRetries,
                                                  int writeRetryDelayMillis) {
        if (backupRetentionCount < 1 || backupRetentionCount > 100) {
            throw new IllegalArgumentException("backupRetentionCount must be between 1 and 100.");
        }
        if (writeRetries < 0 || writeRetries > 3) {
            throw new IllegalArgumentException("writeRetries must be between 0 and 3.");
        }
        if (writeRetryDelayMillis < 10 || writeRetryDelayMillis > 250) {
            throw new IllegalArgumentException("writeRetryDelayMillis must be between 10 and 250.");
        }
        this.backupRetentionCount = backupRetentionCount;
        this.writeRetries = writeRetries;
        this.writeRetryDelayMillis = writeRetryDelayMillis;
    }

    public void configureCharacterProgression(int maxLevel, int baseExperience,
                                              int experiencePerLevel, int skillPointsPerLevel) {
        progressionRules = new CharacterProgression.Rules(
            maxLevel, baseExperience, experiencePerLevel, skillPointsPerLevel);
    }

    public CharacterProgression.Rules getCharacterProgressionRules() {
        return progressionRules;
    }

    public Optional<AdminProgressionResult> awardCharacterExperience(
            String uuid, int experienceAward, String updatedAt) throws SQLException {
        if (experienceAward < 1 || experienceAward > 100_000) {
            throw new IllegalArgumentException("experienceAward must be between 1 and 100000.");
        }
        return executeWriteTransaction(connection -> {
            PlayerProfile profile = readActiveProfileForAdminProgression(connection, uuid);
            if (profile == null) return Optional.empty();
            CharacterProgression.AwardResult result =
                awardProfileExperience(connection, uuid, experienceAward, updatedAt);
            return Optional.of(new AdminProgressionResult(profile.level(), result.progress().level(),
                result.progress().experience(), result.skillPointsGained()));
        });
    }

    public Optional<AdminProgressionResult> grantCharacterLevels(
            String uuid, int levels, String updatedAt) throws SQLException {
        if (levels < 1 || levels > 100) {
            throw new IllegalArgumentException("levels must be between 1 and 100.");
        }
        return executeWriteTransaction(connection -> {
            PlayerProfile profile = readActiveProfileForAdminProgression(connection, uuid);
            if (profile == null) return Optional.empty();
            int newLevel = Math.max(profile.level(),
                Math.min(progressionRules.maxLevel(), profile.level() + levels));
            int grantedLevels = newLevel - profile.level();
            int skillPointsGained;
            int newSkillPoints;
            try {
                skillPointsGained = Math.multiplyExact(grantedLevels, progressionRules.skillPointsPerLevel());
                newSkillPoints = Math.addExact(profile.unspentSkillPoints(), skillPointsGained);
            } catch (ArithmeticException exception) {
                throw new SQLException("Admin character level grant exceeds the supported skill-point range.", exception);
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE player_profiles SET level = ?, skill_points = ?, updated_at = ? " +
                        "WHERE uuid = ? AND is_active = 1")) {
                statement.setInt(1, newLevel);
                statement.setInt(2, newSkillPoints);
                statement.setString(3, updatedAt);
                statement.setString(4, uuid);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Could not save admin character level grant for " + uuid + ".");
                }
            }
            return Optional.of(new AdminProgressionResult(profile.level(), newLevel, profile.exp(),
                skillPointsGained));
        });
    }

    private PlayerProfile readActiveProfileForAdminProgression(Connection connection, String uuid)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT uuid, player_name, selected_race, selected_class, level, exp, skill_points, home_world, " +
                    "created_at, updated_at, is_active FROM player_profiles WHERE uuid = ? AND is_active = 1")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return null;
                return new PlayerProfile(resultSet.getString("uuid"), resultSet.getString("player_name"),
                    resultSet.getString("selected_race"), resultSet.getString("selected_class"),
                    resultSet.getInt("level"), resultSet.getInt("exp"), resultSet.getInt("skill_points"),
                    resultSet.getString("home_world"), resultSet.getString("created_at"),
                    resultSet.getString("updated_at"), true);
            }
        }
    }

    public void initialize() throws SQLException {
        ensureDirectories();
        recoverDatabaseIfNeeded();
        try (Connection connection = openConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON;");
                statement.execute("PRAGMA journal_mode = WAL;");
                statement.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);");
            }
        }
        migrateIfNeeded();
        try {
            backupDatabase();
        } catch (IOException exception) {
            throw new SQLException("Could not create the initial player profile database backup.", exception);
        }
    }

    private record LegacyProgress(String uuid, int level, int experience, int skillPoints) {
    }

    public int getSchemaVersion() throws SQLException {
        try (Connection connection = openConnection()) {
            return readSchemaVersion(connection);
        }
    }

    private void waitBeforeRetry(SQLException originalException) throws SQLException {
        try {
            Thread.sleep(writeRetryDelayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            SQLException interrupted = new SQLException("Interrupted while retrying a busy profile database.",
                exception);
            interrupted.addSuppressed(originalException);
            throw interrupted;
        }
    }

    private boolean isSqliteBusy(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            int primaryCode = current.getErrorCode() & 0xff;
            if (primaryCode == 5 || primaryCode == 6) {
                return true;
            }
            for (Throwable cause = current.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sqlCause) {
                    int causeCode = sqlCause.getErrorCode() & 0xff;
                    if (causeCode == 5 || causeCode == 6) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void pruneBackups() throws IOException {
        File[] backups = backupDirectory.listFiles((directory, name) ->
            name.startsWith("player_profiles.db.") && name.endsWith(".bak"));
        if (backups == null) {
            throw new IOException("Could not list player profile database backups.");
        }
        List<File> validBackups = new ArrayList<>();
        for (File backup : backups) {
            if (isDatabaseHealthy(backup)) {
                validBackups.add(backup);
            }
        }
        if (validBackups.size() <= backupRetentionCount) {
            return;
        }
        validBackups.sort(Comparator.comparingLong(File::lastModified)
            .thenComparing(File::getName));
        int deleteCount = validBackups.size() - backupRetentionCount;
        for (int index = 0; index < deleteCount; index++) {
            Files.delete(validBackups.get(index).toPath());
        }
    }

    public void migrateIfNeeded() throws SQLException {
        ensureDirectories();
        int currentVersion;
        boolean hasProfiles;
        try (Connection connection = openConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON;");
                statement.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);");
            }
            hasProfiles = tableExists(connection, "player_profiles");
            currentVersion = hasProfiles ? readSchemaVersion(connection) : 0;
        }

        if (!hasProfiles) {
            createFreshDatabase();
            return;
        }
        if (currentVersion == 0) {
            currentVersion = 1;
        }
        if (currentVersion > SCHEMA_VERSION) {
            throw new SQLException("Player profile database schema version " + currentVersion
                + " is newer than supported version " + SCHEMA_VERSION + ".");
        }
        if (currentVersion == SCHEMA_VERSION) {
            return;
        }

        try {
            backupDatabase();
        } catch (IOException exception) {
            throw new SQLException("Could not back up the player profile database before migration.", exception);
        }
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                int version = currentVersion;
                while (version < SCHEMA_VERSION) {
                    if (version == 1) {
                        migrateV1ToV2(connection);
                        version = 2;
                        setSchemaVersion(connection, version);
                    } else if (version == 2) {
                        migrateV2ToV3(connection);
                        version = 3;
                        setSchemaVersion(connection, version);
                    } else if (version == 3) {
                        migrateV3ToV4(connection);
                        version = 4;
                        setSchemaVersion(connection, version);
                    } else if (version == 4) {
                        migrateV4ToV5(connection);
                        version = 5;
                        setSchemaVersion(connection, version);
                    } else if (version == 5) {
                        migrateV5ToV6(connection);
                        version = 6;
                        setSchemaVersion(connection, version);
                    } else {
                        throw new SQLException("No player profile database migration exists from version "
                            + version + ".");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public synchronized void backupDatabase() throws IOException, SQLException {
        ensureDirectories();
        if (!databaseFile.exists()) {
            return;
        }
        Files.createDirectories(backupDirectory.toPath());
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA wal_checkpoint(TRUNCATE)")) {
            if (resultSet.next() && resultSet.getInt(1) != 0) {
                throw new SQLException("SQLite could not checkpoint the profile database before backup.",
                    "SQLITE_BUSY", 5);
            }
        }

        File temporaryFile = new File(backupDirectory, "player_profiles.db.backup.tmp");
        File backupFile = newBackupFile();
        try {
            Files.copy(databaseFile.toPath(), temporaryFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            moveReplacing(temporaryFile, backupFile);
            pruneBackups();
        } finally {
            Files.deleteIfExists(temporaryFile.toPath());
        }
    }

    private File newBackupFile() {
        String timestamp = BACKUP_TIMESTAMP.format(Instant.now());
        for (int sequence = 0; ; sequence++) {
            String fileName = "player_profiles.db." + timestamp + "-"
                + String.format(Locale.ROOT, "%04d", sequence) + ".bak";
            File candidate = new File(backupDirectory, fileName);
            if (!candidate.exists()) {
                return candidate;
            }
        }
    }

    public boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = openConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getTables(null, null, tableName, null)) {
                return resultSet.next();
            }
        }
    }

    public synchronized boolean createPlayerProfile(String uuid, String playerName, String selectedRace,
                                                     String selectedClass, int level, int exp, String homeWorld,
                                                     String createdAt) throws SQLException {
        boolean backupCreated = false;
        for (int attempt = 0; attempt <= writeRetries; attempt++) {
            try {
                if (!hasPlayerProfile(uuid) && !backupCreated) {
                    backupDatabase();
                    backupCreated = true;
                }
                return insertPlayerProfile(uuid, playerName, selectedRace, selectedClass, level, exp,
                    homeWorld, createdAt);
            } catch (IOException exception) {
                throw new SQLException("Could not back up the player profile database before saving.", exception);
            } catch (SQLException exception) {
                if (!isSqliteBusy(exception) || attempt == writeRetries) {
                    throw exception;
                }
                waitBeforeRetry(exception);
            }
        }
        throw new SQLException("Player profile save exhausted its retry attempts.");
    }

    private boolean insertPlayerProfile(String uuid, String playerName, String selectedRace, String selectedClass,
                                        int level, int exp, String homeWorld, String createdAt) throws SQLException {
        String sql = "INSERT INTO player_profiles " +
            "(uuid, player_name, selected_race, selected_class, level, exp, home_world, created_at, updated_at, is_active) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1) ON CONFLICT(uuid) DO NOTHING";
        return executeWriteTransactionOnce(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid);
                statement.setString(2, playerName);
                statement.setString(3, selectedRace);
                statement.setString(4, selectedClass);
                statement.setInt(5, level);
                statement.setInt(6, exp);
                statement.setString(7, homeWorld);
                statement.setString(8, createdAt);
                statement.setString(9, createdAt);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean hasPlayerProfile(String uuid) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT 1 FROM player_profiles WHERE uuid = ?")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public Optional<PlayerProfile> getPlayerProfile(String uuid) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT uuid, player_name, selected_race, selected_class, level, exp, skill_points, home_world, " +
                     "created_at, updated_at, is_active FROM player_profiles WHERE uuid = ?")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PlayerProfile(
                    resultSet.getString("uuid"),
                    resultSet.getString("player_name"),
                    resultSet.getString("selected_race"),
                    resultSet.getString("selected_class"),
                    resultSet.getInt("level"),
                    resultSet.getInt("exp"),
                    resultSet.getInt("skill_points"),
                    resultSet.getString("home_world"),
                    resultSet.getString("created_at"),
                    resultSet.getString("updated_at"),
                    resultSet.getInt("is_active") == 1
                ));
            }
        }
    }

    public SkillLearnResult learnSkill(String uuid, String skillId, String requiredClass,
                                       int requiredLevel, String learnedAt) throws SQLException {
        if (requiredLevel < 1) {
            throw new IllegalArgumentException("requiredLevel must be at least 1.");
        }
        return executeWriteTransaction(connection -> {
            try (PreparedStatement profileQuery = connection.prepareStatement(
                    "SELECT selected_class, level FROM player_profiles WHERE uuid = ? AND is_active = 1")) {
                profileQuery.setString(1, uuid);
                try (ResultSet resultSet = profileQuery.executeQuery()) {
                    if (!resultSet.next()) {
                        return SkillLearnResult.PROFILE_NOT_FOUND;
                    }
                    if (!requiredClass.equals(resultSet.getString("selected_class"))
                            || resultSet.getInt("level") < requiredLevel) {
                        return SkillLearnResult.REQUIREMENTS_NOT_MET;
                    }
                }
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO player_skill_progress (uuid, skill_id, rank, learned_at) " +
                        "VALUES (?, ?, 1, ?) ON CONFLICT(uuid, skill_id) DO NOTHING")) {
                insert.setString(1, uuid);
                insert.setString(2, skillId);
                insert.setString(3, learnedAt);
                return insert.executeUpdate() == 1
                    ? SkillLearnResult.LEARNED : SkillLearnResult.ALREADY_LEARNED;
            }
        });
    }

    public List<SkillProgress> getLearnedSkills(String uuid) throws SQLException {
        List<SkillProgress> skills = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT skill_id, rank, learned_at FROM player_skill_progress " +
                     "WHERE uuid = ? ORDER BY learned_at, skill_id")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    skills.add(new SkillProgress(resultSet.getString("skill_id"), resultSet.getInt("rank"),
                        resultSet.getString("learned_at")));
                }
            }
        }
        return List.copyOf(skills);
    }

    public Map<Integer, String> getQuickSkillSlots(String uuid) throws SQLException {
        Map<Integer, String> slots = new LinkedHashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT slot, skill_id FROM player_skill_slots WHERE uuid = ? ORDER BY slot")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    slots.put(resultSet.getInt("slot"), resultSet.getString("skill_id"));
                }
            }
        }
        return Map.copyOf(slots);
    }

    public QuickSlotResult assignQuickSkill(String uuid, int slot, String skillId,
                                            String requiredClass) throws SQLException {
        if (slot < 1 || slot > 9) {
            throw new IllegalArgumentException("slot must be between 1 and 9.");
        }
        return executeWriteTransaction(connection -> {
            try (PreparedStatement profileQuery = connection.prepareStatement(
                    "SELECT selected_class FROM player_profiles WHERE uuid = ? AND is_active = 1")) {
                profileQuery.setString(1, uuid);
                try (ResultSet resultSet = profileQuery.executeQuery()) {
                    if (!resultSet.next()) return QuickSlotResult.PROFILE_NOT_FOUND;
                    if (!requiredClass.equals(resultSet.getString("selected_class"))) {
                        return QuickSlotResult.REQUIREMENTS_NOT_MET;
                    }
                }
            }
            try (PreparedStatement learnedQuery = connection.prepareStatement(
                    "SELECT 1 FROM player_skill_progress WHERE uuid = ? AND skill_id = ?")) {
                learnedQuery.setString(1, uuid);
                learnedQuery.setString(2, skillId);
                try (ResultSet resultSet = learnedQuery.executeQuery()) {
                    if (!resultSet.next()) return QuickSlotResult.SKILL_NOT_LEARNED;
                }
            }
            try (PreparedStatement assignedQuery = connection.prepareStatement(
                    "SELECT slot FROM player_skill_slots WHERE uuid = ? AND skill_id = ?")) {
                assignedQuery.setString(1, uuid);
                assignedQuery.setString(2, skillId);
                try (ResultSet resultSet = assignedQuery.executeQuery()) {
                    if (resultSet.next() && resultSet.getInt("slot") != slot) {
                        return QuickSlotResult.ALREADY_ASSIGNED;
                    }
                }
            }
            try (PreparedStatement upsert = connection.prepareStatement(
                    "INSERT INTO player_skill_slots (uuid, slot, skill_id) VALUES (?, ?, ?) " +
                        "ON CONFLICT(uuid, slot) DO UPDATE SET skill_id = excluded.skill_id")) {
                upsert.setString(1, uuid);
                upsert.setInt(2, slot);
                upsert.setString(3, skillId);
                upsert.executeUpdate();
            }
            return QuickSlotResult.ASSIGNED;
        });
    }

    public boolean clearQuickSkillSlot(String uuid, int slot) throws SQLException {
        if (slot < 1 || slot > 9) {
            throw new IllegalArgumentException("slot must be between 1 and 9.");
        }
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM player_skill_slots WHERE uuid = ? AND slot = ?")) {
                statement.setString(1, uuid);
                statement.setInt(2, slot);
                return statement.executeUpdate() > 0;
            }
        });
    }

    public SkillUpgradeResult upgradeSkill(String uuid, String skillId, String requiredClass,
                                           int requiredLevel, int maxRank) throws SQLException {
        if (requiredLevel < 1 || maxRank < 1 || maxRank > 5) {
            throw new IllegalArgumentException("Skill upgrade requirements are invalid.");
        }
        return executeWriteTransaction(connection -> {
            int skillPoints;
            int playerLevel;
            try (PreparedStatement profileQuery = connection.prepareStatement(
                    "SELECT selected_class, level, skill_points FROM player_profiles " +
                        "WHERE uuid = ? AND is_active = 1")) {
                profileQuery.setString(1, uuid);
                try (ResultSet resultSet = profileQuery.executeQuery()) {
                    if (!resultSet.next()) return SkillUpgradeResult.PROFILE_NOT_FOUND;
                    if (!requiredClass.equals(resultSet.getString("selected_class"))) {
                        return SkillUpgradeResult.REQUIREMENTS_NOT_MET;
                    }
                    playerLevel = resultSet.getInt("level");
                    skillPoints = resultSet.getInt("skill_points");
                }
            }
            int currentRank;
            try (PreparedStatement skillQuery = connection.prepareStatement(
                    "SELECT rank FROM player_skill_progress WHERE uuid = ? AND skill_id = ?")) {
                skillQuery.setString(1, uuid);
                skillQuery.setString(2, skillId);
                try (ResultSet resultSet = skillQuery.executeQuery()) {
                    if (!resultSet.next()) return SkillUpgradeResult.SKILL_NOT_LEARNED;
                    currentRank = resultSet.getInt("rank");
                }
            }
            if (currentRank >= maxRank) return SkillUpgradeResult.MAX_RANK;
            if (playerLevel < requiredLevel + currentRank) return SkillUpgradeResult.REQUIREMENTS_NOT_MET;
            if (skillPoints < 1) return SkillUpgradeResult.NO_SKILL_POINTS;
            try (PreparedStatement spendPoint = connection.prepareStatement(
                    "UPDATE player_profiles SET skill_points = skill_points - 1 " +
                        "WHERE uuid = ? AND skill_points > 0")) {
                spendPoint.setString(1, uuid);
                if (spendPoint.executeUpdate() != 1) return SkillUpgradeResult.NO_SKILL_POINTS;
            }
            try (PreparedStatement updateSkill = connection.prepareStatement(
                    "UPDATE player_skill_progress SET rank = rank + 1 " +
                        "WHERE uuid = ? AND skill_id = ? AND rank = ? AND rank < ?")) {
                updateSkill.setString(1, uuid);
                updateSkill.setString(2, skillId);
                updateSkill.setInt(3, currentRank);
                updateSkill.setInt(4, maxRank);
                if (updateSkill.executeUpdate() != 1) {
                    throw new SQLException("Skill rank changed during upgrade; transaction was rolled back.");
                }
            }
            return SkillUpgradeResult.UPGRADED;
        });
    }

    public Optional<ManaState> getManaState(String uuid, int maxMana, int regenerationPerSecond,
                                            long nowMillis) throws SQLException {
        validateManaSettings(maxMana, regenerationPerSecond, nowMillis);
        return executeWriteTransaction(connection -> Optional.ofNullable(
            refreshMana(connection, uuid, maxMana, regenerationPerSecond, nowMillis)));
    }

    public ManaSpendResult spendMana(String uuid, int cost, int maxMana,
                                    int regenerationPerSecond, long nowMillis) throws SQLException {
        if (cost < 0 || cost > 1000) {
            throw new IllegalArgumentException("Mana cost must be between 0 and 1000.");
        }
        validateManaSettings(maxMana, regenerationPerSecond, nowMillis);
        return executeWriteTransaction(connection -> {
            ManaState current = refreshMana(connection, uuid, maxMana, regenerationPerSecond, nowMillis);
            if (current == null) return ManaSpendResult.PROFILE_NOT_FOUND;
            if (current.currentMana() < cost) return ManaSpendResult.NOT_ENOUGH_MANA;
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE player_profiles SET mana = mana - ? WHERE uuid = ? AND mana >= ?")) {
                update.setInt(1, cost);
                update.setString(2, uuid);
                update.setInt(3, cost);
                return update.executeUpdate() == 1
                    ? ManaSpendResult.SPENT : ManaSpendResult.NOT_ENOUGH_MANA;
            }
        });
    }

    public Optional<ManaRestoreResult> restoreMana(String uuid, int amount, int maxMana,
                                                    int regenerationPerSecond, long nowMillis)
        throws SQLException {
        if (amount < 0 || amount > 1000) {
            throw new IllegalArgumentException("Mana restore amount must be between 0 and 1000.");
        }
        validateManaSettings(maxMana, regenerationPerSecond, nowMillis);
        return executeWriteTransaction(connection -> {
            ManaState current = refreshMana(connection, uuid, maxMana, regenerationPerSecond, nowMillis);
            if (current == null) return Optional.empty();
            int restored = Math.min(amount, maxMana - current.currentMana());
            if (restored > 0) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE player_profiles SET mana = mana + ? " +
                            "WHERE uuid = ? AND is_active = 1 AND mana = ?")) {
                    update.setInt(1, restored);
                    update.setString(2, uuid);
                    update.setInt(3, current.currentMana());
                    if (update.executeUpdate() != 1) {
                        throw new SQLException("Mana changed during restore; transaction was rolled back.");
                    }
                }
            }
            return Optional.of(new ManaRestoreResult(
                new ManaState(current.currentMana() + restored, maxMana), restored));
        });
    }

    private ManaState refreshMana(Connection connection, String uuid, int maxMana,
                                  int regenerationPerSecond, long nowMillis) throws SQLException {
        int storedMana;
        long previousUpdate;
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT mana, mana_updated_at FROM player_profiles WHERE uuid = ? AND is_active = 1")) {
            query.setString(1, uuid);
            try (ResultSet resultSet = query.executeQuery()) {
                if (!resultSet.next()) return null;
                storedMana = resultSet.getInt("mana");
                previousUpdate = resultSet.getLong("mana_updated_at");
            }
        }
        long effectiveUpdate = previousUpdate <= 0 ? nowMillis : Math.max(previousUpdate, nowMillis);
        long elapsedSeconds = previousUpdate <= 0 || nowMillis <= previousUpdate
            ? 0 : (nowMillis - previousUpdate) / 1000;
        if (previousUpdate > 0 && nowMillis > previousUpdate) {
            effectiveUpdate = previousUpdate;
        }
        int mana = Math.min(storedMana, maxMana);
        if (regenerationPerSecond > 0 && mana < maxMana && elapsedSeconds > 0) {
            long neededSeconds = (maxMana - (long) mana + regenerationPerSecond - 1)
                / regenerationPerSecond;
            if (elapsedSeconds >= neededSeconds) {
                mana = maxMana;
            } else {
                mana += (int) (elapsedSeconds * regenerationPerSecond);
            }
            effectiveUpdate += elapsedSeconds * 1000;
        } else if (mana >= maxMana) {
            mana = maxMana;
            effectiveUpdate = Math.max(previousUpdate, nowMillis);
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE player_profiles SET mana = ?, mana_updated_at = ? WHERE uuid = ? AND is_active = 1")) {
            update.setInt(1, mana);
            update.setLong(2, effectiveUpdate);
            update.setString(3, uuid);
            if (update.executeUpdate() != 1) return null;
        }
        return new ManaState(mana, maxMana);
    }

    private static void validateManaSettings(int maxMana, int regenerationPerSecond, long nowMillis) {
        if (maxMana < 1 || maxMana > 1000 || regenerationPerSecond < 0
                || regenerationPerSecond > 100 || nowMillis < 0) {
            throw new IllegalArgumentException("Mana settings are outside supported bounds.");
        }
    }

    public boolean unlockWorld(String uuid, String worldId) throws SQLException {
        String sql = "INSERT INTO player_world_progress (uuid, world_id, unlocked) VALUES (?, ?, 1) " +
            "ON CONFLICT(uuid, world_id) DO UPDATE SET unlocked = 1 " +
            "WHERE player_world_progress.unlocked = 0";
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid);
                statement.setString(2, worldId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean isWorldUnlocked(String uuid, String worldId) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT unlocked FROM player_world_progress WHERE uuid = ? AND world_id = ?")) {
            statement.setString(1, uuid);
            statement.setString(2, worldId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt("unlocked") == 1;
            }
        }
    }

    public boolean startQuestProgress(String uuid, String questId, String currentStep, String startedAt)
        throws SQLException {
        String sql = "INSERT INTO player_quest_progress " +
            "(uuid, quest_id, status, current_step, started_at, reward_claimed) " +
            "VALUES (?, ?, 'active', ?, ?, 0) ON CONFLICT(uuid, quest_id) DO NOTHING";
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid);
                statement.setString(2, questId);
                statement.setString(3, currentStep);
                statement.setString(4, startedAt);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean updateQuestStep(String uuid, String questId, String currentStep) throws SQLException {
        String sql = "UPDATE player_quest_progress SET current_step = ? " +
            "WHERE uuid = ? AND quest_id = ? AND status = 'active'";
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, currentStep);
                statement.setString(2, uuid);
                statement.setString(3, questId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean completeQuest(String uuid, String questId, String currentStep, String completedAt)
        throws SQLException {
        String sql = "UPDATE player_quest_progress SET status = 'completed', current_step = ?, completed_at = ? " +
            "WHERE uuid = ? AND quest_id = ? AND status = 'active'";
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, currentStep);
                statement.setString(2, completedAt);
                statement.setString(3, uuid);
                statement.setString(4, questId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean completeQuestAndAwardExperience(
            String uuid, String questId, String currentStep, int experienceReward, String completedAt)
            throws SQLException {
        validateExperienceReward(experienceReward);
        String sql = "UPDATE player_quest_progress SET status = 'completed', current_step = ?, " +
            "completed_at = ?, reward_claimed = 1 " +
            "WHERE uuid = ? AND quest_id = ? AND status = 'active' AND reward_claimed = 0";
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, currentStep);
                statement.setString(2, completedAt);
                statement.setString(3, uuid);
                statement.setString(4, questId);
                if (statement.executeUpdate() != 1) return false;
            }
            awardProfileExperience(connection, uuid, experienceReward, completedAt);
            return true;
        });
    }

    public boolean claimCompletedQuestExperienceReward(
            String uuid, String questId, int experienceReward, String claimedAt) throws SQLException {
        validateExperienceReward(experienceReward);
        String sql = "UPDATE player_quest_progress SET reward_claimed = 1 " +
            "WHERE uuid = ? AND quest_id = ? AND status = 'completed' AND reward_claimed = 0";
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid);
                statement.setString(2, questId);
                if (statement.executeUpdate() != 1) return false;
            }
            awardProfileExperience(connection, uuid, experienceReward, claimedAt);
            return true;
        });
    }

    private CharacterProgression.AwardResult awardProfileExperience(
            Connection connection, String uuid, int experienceReward, String updatedAt) throws SQLException {
        int level;
        int experience;
        int skillPoints;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT level, exp, skill_points FROM player_profiles WHERE uuid = ? AND is_active = 1")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Could not award quest experience: profile is missing or inactive.");
                }
                level = resultSet.getInt("level");
                experience = resultSet.getInt("exp");
                skillPoints = resultSet.getInt("skill_points");
            }
        }
        CharacterProgression.AwardResult result;
        try {
            result = CharacterProgression.award(level, experience, skillPoints, experienceReward,
                progressionRules);
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Could not apply character progression for profile " + uuid + ".", exception);
        }
        String sql = "UPDATE player_profiles SET level = ?, exp = ?, skill_points = ?, updated_at = ? " +
            "WHERE uuid = ? AND is_active = 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, result.progress().level());
            statement.setInt(2, result.progress().experience());
            statement.setInt(3, result.progress().unspentSkillPoints());
            statement.setString(4, updatedAt);
            statement.setString(5, uuid);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Could not save character progression for profile " + uuid + ".");
            }
        }
        return result;
    }

    private void validateExperienceReward(int experienceReward) {
        if (experienceReward < 0 || experienceReward > 100_000) {
            throw new IllegalArgumentException("experienceReward must be between 0 and 100000.");
        }
    }

    public Optional<QuestProgress> getQuestProgress(String uuid, String questId) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT status, current_step, started_at, completed_at, reward_claimed " +
                     "FROM player_quest_progress WHERE uuid = ? AND quest_id = ?")) {
            statement.setString(1, uuid);
            statement.setString(2, questId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new QuestProgress(
                    resultSet.getString("status"),
                    resultSet.getString("current_step"),
                    resultSet.getString("started_at"),
                    resultSet.getString("completed_at"),
                    resultSet.getInt("reward_claimed") == 1
                ));
            }
        }
    }

    private <T> T executeWriteTransaction(SqlWork<T> operation) throws SQLException {
        for (int attempt = 0; attempt <= writeRetries; attempt++) {
            try {
                return executeWriteTransactionOnce(operation);
            } catch (SQLException exception) {
                if (!isSqliteBusy(exception) || attempt == writeRetries) {
                    throw exception;
                }
                waitBeforeRetry(exception);
            }
        }
        throw new SQLException("Player profile database write exhausted its retry attempts.");
    }

    private <T> T executeWriteTransactionOnce(SqlWork<T> operation) throws SQLException {
        try (Connection connection = openConnection();
             Statement transaction = connection.createStatement()) {
            transaction.execute("BEGIN IMMEDIATE");
            try {
                T result = operation.execute(connection);
                transaction.execute("COMMIT");
                return result;
            } catch (SQLException | RuntimeException exception) {
                try {
                    transaction.execute("ROLLBACK");
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }

    public record QuestProgress(String status, String currentStep, String startedAt, String completedAt,
                                boolean rewardClaimed) {
    }

    public enum SkillLearnResult {
        LEARNED,
        ALREADY_LEARNED,
        REQUIREMENTS_NOT_MET,
        PROFILE_NOT_FOUND
    }

    public enum QuickSlotResult {
        ASSIGNED,
        ALREADY_ASSIGNED,
        SKILL_NOT_LEARNED,
        REQUIREMENTS_NOT_MET,
        PROFILE_NOT_FOUND
    }

    public enum SkillUpgradeResult {
        UPGRADED,
        NO_SKILL_POINTS,
        MAX_RANK,
        SKILL_NOT_LEARNED,
        REQUIREMENTS_NOT_MET,
        PROFILE_NOT_FOUND
    }

    public enum ManaSpendResult {
        SPENT,
        NOT_ENOUGH_MANA,
        PROFILE_NOT_FOUND
    }

    public record SkillProgress(String skillId, int rank, String learnedAt) {
    }

    public record ManaState(int currentMana, int maxMana) {
    }

    public record ManaRestoreResult(ManaState state, int restoredAmount) {
    }

    public record AdminProgressionResult(int previousLevel, int currentLevel, int experience,
                                         int skillPointsGained) {
    }

    public record PlayerProfile(String uuid, String playerName, String selectedRace, String selectedClass,
                                int level, int exp, int unspentSkillPoints, String homeWorld,
                                String createdAt, String updatedAt, boolean active) {
        public PlayerProfile(String uuid, String playerName, String selectedRace, String selectedClass,
                             int level, int exp, String homeWorld, String createdAt, String updatedAt,
                             boolean active) {
            this(uuid, playerName, selectedRace, selectedClass, level, exp, 0,
                homeWorld, createdAt, updatedAt, active);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }

    public List<String> getPlayerProfileTableNames() throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection connection = openConnection();
             ResultSet resultSet = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private void ensureDirectories() throws SQLException {
        File directory = databaseFile.getParentFile();
        try {
            if (directory != null) {
                Files.createDirectories(directory.toPath());
            }
            if (backupDirectory != null) {
                Files.createDirectories(backupDirectory.toPath());
            }
        } catch (IOException exception) {
            throw new SQLException("Could not create player profile database directories.", exception);
        }
    }

    private void createFreshDatabase() throws SQLException {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                createCurrentTables(connection);
                setSchemaVersion(connection, SCHEMA_VERSION);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void createCurrentTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS player_profiles (" +
                "uuid TEXT PRIMARY KEY, player_name TEXT NOT NULL, selected_race TEXT, selected_class TEXT, " +
                "level INTEGER NOT NULL DEFAULT 1, exp INTEGER NOT NULL DEFAULT 0, " +
                "skill_points INTEGER NOT NULL DEFAULT 0 CHECK (skill_points >= 0), " +
                "mana INTEGER NOT NULL DEFAULT 100 CHECK (mana >= 0), mana_updated_at INTEGER NOT NULL DEFAULT 0, " +
                "home_world TEXT, " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, is_active INTEGER NOT NULL DEFAULT 1)");
            statement.execute("CREATE TABLE IF NOT EXISTS player_quest_progress (" +
                "uuid TEXT NOT NULL, quest_id TEXT NOT NULL, status TEXT NOT NULL, current_step TEXT, " +
                "started_at TEXT, completed_at TEXT, reward_claimed INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (uuid, quest_id), " +
                "FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)");
            statement.execute("CREATE TABLE IF NOT EXISTS player_world_progress (" +
                "uuid TEXT NOT NULL, world_id TEXT NOT NULL, unlocked INTEGER NOT NULL DEFAULT 0, " +
                "completed INTEGER NOT NULL DEFAULT 0, last_visited TEXT, PRIMARY KEY (uuid, world_id), " +
                "FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)");
            statement.execute("CREATE TABLE IF NOT EXISTS player_portal_progress (" +
                "uuid TEXT NOT NULL, portal_id TEXT NOT NULL, unlocked INTEGER NOT NULL DEFAULT 0, " +
                "last_used TEXT, PRIMARY KEY (uuid, portal_id), " +
                "FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)");
            statement.execute("CREATE TABLE IF NOT EXISTS player_skill_progress (" +
                "uuid TEXT NOT NULL, skill_id TEXT NOT NULL, rank INTEGER NOT NULL DEFAULT 1 " +
                "CHECK (rank BETWEEN 1 AND 5), learned_at TEXT NOT NULL, PRIMARY KEY (uuid, skill_id), " +
                "FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)");
            statement.execute("CREATE TABLE IF NOT EXISTS player_skill_slots (" +
                "uuid TEXT NOT NULL, slot INTEGER NOT NULL CHECK (slot BETWEEN 1 AND 9), skill_id TEXT NOT NULL, " +
                "PRIMARY KEY (uuid, slot), UNIQUE (uuid, skill_id), " +
                "FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)");
        }
    }

    private void migrateV1ToV2(Connection connection) throws SQLException {
        if (!columnExists(connection, "player_profiles", "selected_class")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE player_profiles ADD COLUMN selected_class TEXT");
            }
        }
        createCurrentTables(connection);
    }

    private void migrateV2ToV3(Connection connection) throws SQLException {
        if (!columnExists(connection, "player_profiles", "skill_points")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE player_profiles ADD COLUMN skill_points INTEGER NOT NULL DEFAULT 0 " +
                    "CHECK (skill_points >= 0)");
            }
        }
        backfillLegacyExperience(connection);
        createCurrentTables(connection);
    }

    private void migrateV3ToV4(Connection connection) throws SQLException {
        createCurrentTables(connection);
    }

    private void migrateV4ToV5(Connection connection) throws SQLException {
        createCurrentTables(connection);
    }

    private void migrateV5ToV6(Connection connection) throws SQLException {
        if (!columnExists(connection, "player_profiles", "mana")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE player_profiles ADD COLUMN mana INTEGER NOT NULL DEFAULT 100 " +
                    "CHECK (mana >= 0)");
            }
        }
        if (!columnExists(connection, "player_profiles", "mana_updated_at")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE player_profiles ADD COLUMN mana_updated_at INTEGER NOT NULL DEFAULT 0");
            }
        }
        createCurrentTables(connection);
    }

    private void backfillLegacyExperience(Connection connection) throws SQLException {
        List<LegacyProgress> legacyProfiles = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                 "SELECT uuid, level, exp, skill_points FROM player_profiles")) {
            while (resultSet.next()) {
                legacyProfiles.add(new LegacyProgress(resultSet.getString("uuid"), resultSet.getInt("level"),
                    resultSet.getInt("exp"), resultSet.getInt("skill_points")));
            }
        }

        for (LegacyProgress legacyProfile : legacyProfiles) {
            if (legacyProfile.level() < 1 || legacyProfile.experience() < 0 || legacyProfile.skillPoints() < 0) {
                throw new SQLException("Could not migrate invalid character progression for profile "
                    + legacyProfile.uuid() + ".");
            }

            CharacterProgression.AwardResult converted;
            try {
                converted = CharacterProgression.award(legacyProfile.level(), 0, legacyProfile.skillPoints(),
                    legacyProfile.experience(), progressionRules);
            } catch (IllegalArgumentException exception) {
                throw new SQLException("Could not migrate character experience for profile "
                    + legacyProfile.uuid() + ".", exception);
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE player_profiles SET level = ?, exp = ?, skill_points = ? WHERE uuid = ?")) {
                update.setInt(1, converted.progress().level());
                update.setInt(2, converted.progress().experience());
                update.setInt(3, converted.progress().unspentSkillPoints());
                update.setString(4, legacyProfile.uuid());
                if (update.executeUpdate() != 1) {
                    throw new SQLException("Could not save migrated character progression for profile "
                        + legacyProfile.uuid() + ".");
                }
            }
        }
    }

    private void setSchemaVersion(Connection connection, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO meta (key, value) VALUES ('schema_version', ?) " +
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            statement.setString(1, Integer.toString(version));
            statement.executeUpdate();
        }
    }

    private int readSchemaVersion(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT value FROM meta WHERE key = 'schema_version'");
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return 0;
            }
            try {
                return Integer.parseInt(resultSet.getString("value"));
            } catch (NumberFormatException exception) {
                throw new SQLException("Player profile database has an invalid schema_version value.", exception);
            }
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            return resultSet.next();
        }
    }

    private void recoverDatabaseIfNeeded() throws SQLException {
        if (!databaseFile.isFile() || isDatabaseHealthy(databaseFile)) {
            return;
        }
        File[] backups = backupDirectory.listFiles((directory, name) ->
            name.startsWith("player_profiles.db.") && name.endsWith(".bak"));
        if (backups != null) {
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed()
                .thenComparing(File::getName, Comparator.reverseOrder()));
            for (File backup : backups) {
                if (!isDatabaseHealthy(backup)) {
                    continue;
                }
                preserveCorruptDatabase();
                File temporaryFile = new File(databaseFile.getParentFile(), "player_profiles.db.restore.tmp");
                try {
                    Files.copy(backup.toPath(), temporaryFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    moveReplacing(temporaryFile, databaseFile);
                    recoveryMessage = "Player profile database was restored from " + backup.getName() + ".";
                    return;
                } catch (IOException exception) {
                    throw new SQLException("Could not restore player profile database from "
                        + backup.getName() + ".", exception);
                } finally {
                    try {
                        Files.deleteIfExists(temporaryFile.toPath());
                    } catch (IOException exception) {
                        throw new SQLException("Could not remove temporary profile database restore file.", exception);
                    }
                }
            }
        }
        throw new SQLException("Player profile database is corrupt and no valid backup was found. "
            + "The original database has been preserved for manual recovery.");
    }

    private boolean isDatabaseHealthy(File file) {
        if (!file.isFile()) {
            return false;
        }
        String url = "jdbc:sqlite:" + file.getAbsolutePath();
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA integrity_check")) {
            return resultSet.next() && "ok".equalsIgnoreCase(resultSet.getString(1));
        } catch (SQLException exception) {
            return false;
        }
    }

    private void preserveCorruptDatabase() throws SQLException {
        String suffix = ".corrupt." + BACKUP_TIMESTAMP.format(Instant.now());
        try {
            if (databaseFile.exists()) {
                Files.move(databaseFile.toPath(), new File(backupDirectory, databaseFile.getName() + suffix).toPath());
            }
            for (String sidecar : List.of(databaseFile.getName() + "-wal", databaseFile.getName() + "-shm")) {
                File sidecarFile = new File(databaseFile.getParentFile(), sidecar);
                if (sidecarFile.exists()) {
                    Files.move(sidecarFile.toPath(), new File(backupDirectory, sidecar + suffix).toPath());
                }
            }
        } catch (IOException exception) {
            throw new SQLException("Could not preserve the corrupt player profile database.", exception);
        }
    }

    private void moveReplacing(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void rollback(Connection connection, Exception originalException) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }

    private Connection openConnection() throws SQLException {
        String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON;");
            return connection;
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }
}
