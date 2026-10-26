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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class PlayerProfileDatabaseManager {
    public static final int SCHEMA_VERSION = 13;
    public static final long MAX_WALLET_BALANCE = 2_147_483_647L;
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        .withZone(ZoneOffset.UTC);

    private final File databaseFile;
    private final File backupDirectory;
    private final ThreadPoolExecutor gameplayDatabaseExecutor = new ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(256), runnable -> {
            Thread thread = new Thread(runnable, "VnMineHardcore-ProfileDB");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    private String recoveryMessage;
    private int backupRetentionCount;
    private int writeRetries;
    private int writeRetryDelayMillis;
    private volatile CharacterProgression.Rules progressionRules = CharacterProgression.Rules.defaults();
    private volatile Logger diagnosticsLogger;
    private volatile BooleanSupplier diagnosticsEnabled = () -> false;

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

    /** Run profile database work away from the Paper server thread, in submission order. */
    public <T> CompletableFuture<T> executeGameplayDatabaseTask(Callable<T> task) {
        return executeGameplayDatabaseTask("gameplay", task);
    }

    public void configureGameplayDatabaseDiagnostics(Logger logger, BooleanSupplier enabled) {
        diagnosticsLogger = logger;
        diagnosticsEnabled = enabled == null ? () -> false : enabled;
    }

    /** Run named profile database work away from the server thread and emit optional timing diagnostics. */
    public <T> CompletableFuture<T> executeGameplayDatabaseTask(String operation, Callable<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        String taskName = operation == null || operation.isBlank() ? "gameplay" : operation;
        long enqueuedAt = System.nanoTime();
        try {
            gameplayDatabaseExecutor.execute(() -> {
                long startedAt = System.nanoTime();
                try {
                    T value = task.call();
                    long finishedAt = System.nanoTime();
                    logGameplayDatabaseTask(taskName, (startedAt - enqueuedAt) / 1_000_000L,
                        (finishedAt - startedAt) / 1_000_000L, null);
                    result.complete(value);
                } catch (Throwable failure) {
                    long finishedAt = System.nanoTime();
                    logGameplayDatabaseTask(taskName, (startedAt - enqueuedAt) / 1_000_000L,
                        (finishedAt - startedAt) / 1_000_000L, failure);
                    result.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException exception) {
            Logger logger = diagnosticsLogger;
            if (logger != null) {
                logger.log(java.util.logging.Level.SEVERE, "[RPG-DB][REJECTED] operation=" + taskName
                    + ", queued=" + gameplayDatabaseExecutor.getQueue().size()
                    + ", reason=queue-full-or-shutting-down", exception);
            }
            result.completeExceptionally(new SQLException(
                "The profile database work queue is full or shutting down.", exception));
        }
        return result;
    }

    private void logGameplayDatabaseTask(String operation, long queueWaitMillis, long databaseMillis,
                                        Throwable failure) {
        Logger logger = diagnosticsLogger;
        if (logger == null) return;
        if (failure != null) {
            logger.log(java.util.logging.Level.SEVERE, "[RPG-DB][ERROR] operation=" + operation
                + ", queueWaitMs=" + queueWaitMillis + ", dbMs=" + databaseMillis
                + ", queued=" + gameplayDatabaseExecutor.getQueue().size(), failure);
            return;
        }
        if (databaseMillis >= 100 || queueWaitMillis >= 250) {
            logger.warning("[RPG-PERF][DB] operation=" + operation + ", queueWaitMs=" + queueWaitMillis
                + ", dbMs=" + databaseMillis + ", queued=" + gameplayDatabaseExecutor.getQueue().size());
        } else if (diagnosticsEnabled.getAsBoolean()) {
            logger.info("[RPG-DB][OK] operation=" + operation + ", queueWaitMs=" + queueWaitMillis
                + ", dbMs=" + databaseMillis + ", queued=" + gameplayDatabaseExecutor.getQueue().size());
        }
    }

    public int getPendingGameplayDatabaseTasks() {
        return gameplayDatabaseExecutor.getQueue().size();
    }

    public void shutdownGameplayDatabaseExecutor() {
        gameplayDatabaseExecutor.shutdown();
        try {
            if (!gameplayDatabaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                gameplayDatabaseExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            gameplayDatabaseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
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
        int configuredVersion = currentVersion;
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
                    } else if (version == 6) {
                        migrateV6ToV7(connection);
                        version = 7;
                        setSchemaVersion(connection, version);
                    } else if (version == 7) {
                        migrateV7ToV8(connection);
                        version = 8;
                        setSchemaVersion(connection, version);
                    } else if (version == 8) {
                        migrateV8ToV9(connection);
                        version = 9;
                        setSchemaVersion(connection, version);
                    } else if (version == 9) {
                        migrateV9ToV10(connection);
                        version = 10;
                        setSchemaVersion(connection, version);
                    } else if (version == 10) {
                        migrateV10ToV11(connection);
                        version = 11;
                        setSchemaVersion(connection, version);
                    } else if (version == 11) {
                        migrateV11ToV12(connection);
                        version = 12;
                        setSchemaVersion(connection, version);
                    } else if (version == 12) {
                        migrateV12ToV13(connection);
                        version = 13;
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

    public ClassPromotionResult promoteClass(String uuid, String expectedClass, String targetClass,
                                              int requiredLevel, String requiredQuestId, String changedAt) throws SQLException {
        if (expectedClass == null || expectedClass.isBlank() || targetClass == null || targetClass.isBlank()
                || expectedClass.equals(targetClass) || requiredLevel < 1 || changedAt == null) {
            throw new IllegalArgumentException("Class promotion request is invalid.");
        }
        return executeWriteTransaction(connection -> {
            int level;
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT selected_class, level FROM player_profiles WHERE uuid = ? AND is_active = 1")) {
                query.setString(1, uuid);
                try (ResultSet result = query.executeQuery()) {
                    if (!result.next()) return ClassPromotionResult.PROFILE_NOT_FOUND;
                    if (!expectedClass.equals(result.getString("selected_class"))) {
                        return ClassPromotionResult.CLASS_CHANGED;
                    }
                    level = result.getInt("level");
                }
            }
            if (level < requiredLevel) return ClassPromotionResult.REQUIREMENTS_NOT_MET;
            if (requiredQuestId != null && !requiredQuestId.isBlank()) {
                try (PreparedStatement questQuery = connection.prepareStatement(
                        "SELECT 1 FROM player_quest_progress WHERE uuid = ? AND quest_id = ? " +
                            "AND status = 'completed'")) {
                    questQuery.setString(1, uuid);
                    questQuery.setString(2, requiredQuestId);
                    try (ResultSet questResult = questQuery.executeQuery()) {
                        if (!questResult.next()) return ClassPromotionResult.REQUIREMENTS_NOT_MET;
                    }
                }
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE player_profiles SET selected_class = ?, updated_at = ? " +
                        "WHERE uuid = ? AND selected_class = ? AND is_active = 1")) {
                update.setString(1, targetClass);
                update.setString(2, changedAt);
                update.setString(3, uuid);
                update.setString(4, expectedClass);
                if (update.executeUpdate() != 1) return ClassPromotionResult.CLASS_CHANGED;
            }
            try (PreparedStatement history = connection.prepareStatement(
                    "INSERT INTO player_class_history (uuid, from_class, to_class, changed_at) VALUES (?, ?, ?, ?)")) {
                history.setString(1, uuid);
                history.setString(2, expectedClass);
                history.setString(3, targetClass);
                history.setString(4, changedAt);
                history.executeUpdate();
            }
            return ClassPromotionResult.PROMOTED;
        });
    }

    public List<ClassHistoryEntry> getClassHistory(String uuid) throws SQLException {
        List<ClassHistoryEntry> history = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT from_class, to_class, changed_at FROM player_class_history WHERE uuid = ? ORDER BY id")) {
            query.setString(1, uuid);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) history.add(new ClassHistoryEntry(result.getString("from_class"),
                    result.getString("to_class"), result.getString("changed_at")));
            }
        }
        return List.copyOf(history);
    }

    public enum ClassPromotionResult { PROMOTED, PROFILE_NOT_FOUND, CLASS_CHANGED, REQUIREMENTS_NOT_MET }
    public record ClassHistoryEntry(String fromClass, String toClass, String changedAt) { }

    public SkillLearnResult learnSkill(String uuid, String skillId, Set<String> eligibleClasses,
                                       int requiredLevel, String learnedAt) throws SQLException {
        if (requiredLevel < 1 || eligibleClasses == null || eligibleClasses.isEmpty()) {
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
                    if (!eligibleClasses.contains(resultSet.getString("selected_class"))
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

    public boolean rollbackNewSkillLearn(String uuid, String skillId) throws SQLException {
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM player_skill_progress WHERE uuid = ? AND skill_id = ? AND rank = 1")) {
                statement.setString(1, uuid);
                statement.setString(2, skillId);
                return statement.executeUpdate() == 1;
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
                                            Set<String> eligibleClasses) throws SQLException {
        if (slot < 1 || slot > 9 || eligibleClasses == null || eligibleClasses.isEmpty()) {
            throw new IllegalArgumentException("slot must be between 1 and 9.");
        }
        return executeWriteTransaction(connection -> {
            try (PreparedStatement profileQuery = connection.prepareStatement(
                    "SELECT selected_class FROM player_profiles WHERE uuid = ? AND is_active = 1")) {
                profileQuery.setString(1, uuid);
                try (ResultSet resultSet = profileQuery.executeQuery()) {
                    if (!resultSet.next()) return QuickSlotResult.PROFILE_NOT_FOUND;
                    if (!eligibleClasses.contains(resultSet.getString("selected_class"))) {
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

    public SkillUpgradeResult upgradeSkill(String uuid, String skillId, Set<String> eligibleClasses,
                                           int requiredLevel, int maxRank) throws SQLException {
        if (requiredLevel < 1 || maxRank < 1 || maxRank > 5
                || eligibleClasses == null || eligibleClasses.isEmpty()) {
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
                    if (!eligibleClasses.contains(resultSet.getString("selected_class"))) {
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

    public SkillCastResult spendManaForSkillCast(String uuid, String skillId, Set<String> eligibleClasses, int cost,
                                                 int maxMana, int regenerationPerSecond, long nowMillis)
        throws SQLException {
        if (cost < 0 || cost > 1000 || eligibleClasses == null || eligibleClasses.isEmpty()) {
            throw new IllegalArgumentException("Mana cost must be between 0 and 1000.");
        }
        validateManaSettings(maxMana, regenerationPerSecond, nowMillis);
        return executeWriteTransaction(connection -> {
            String selectedClass;
            int rank;
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT p.selected_class, s.rank FROM player_profiles p "
                        + "LEFT JOIN player_skill_progress s ON s.uuid = p.uuid AND s.skill_id = ? "
                        + "WHERE p.uuid = ? AND p.is_active = 1")) {
                query.setString(1, skillId);
                query.setString(2, uuid);
                try (ResultSet result = query.executeQuery()) {
                    if (!result.next()) {
                        return new SkillCastResult(SkillCastStatus.PROFILE_NOT_FOUND, 0, null);
                    }
                    selectedClass = result.getString("selected_class");
                    rank = result.getInt("rank");
                    if (result.wasNull()) rank = 0;
                }
            }
            if (!eligibleClasses.contains(selectedClass)) {
                return new SkillCastResult(SkillCastStatus.CLASS_MISMATCH, 0, null);
            }
            if (rank < 1) {
                return new SkillCastResult(SkillCastStatus.SKILL_NOT_LEARNED, 0, null);
            }

            ManaState current = refreshMana(connection, uuid, maxMana, regenerationPerSecond, nowMillis);
            if (current == null) {
                return new SkillCastResult(SkillCastStatus.PROFILE_NOT_FOUND, 0, null);
            }
            if (current.currentMana() < cost) {
                return new SkillCastResult(SkillCastStatus.NOT_ENOUGH_MANA, rank, current);
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE player_profiles SET mana = mana - ? WHERE uuid = ? AND is_active = 1 AND mana >= ?")) {
                update.setInt(1, cost);
                update.setString(2, uuid);
                update.setInt(3, cost);
                if (update.executeUpdate() != 1) {
                    return new SkillCastResult(SkillCastStatus.NOT_ENOUGH_MANA, rank, current);
                }
            }
            return new SkillCastResult(SkillCastStatus.SPENT, rank,
                new ManaState(current.currentMana() - cost, maxMana));
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
            try (PreparedStatement count = connection.prepareStatement(
                    "SELECT COUNT(*) FROM player_quest_progress WHERE uuid = ? AND status = 'active'")) {
                count.setString(1, uuid);
                try (ResultSet resultSet = count.executeQuery()) {
                    if (resultSet.next() && resultSet.getInt(1) >= 3) return false;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid);
                statement.setString(2, questId);
                statement.setString(3, currentStep);
                statement.setString(4, startedAt);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public QuestStartResult startQuestProgressWithLimit(String uuid, String questId, String currentStep,
            String startedAt, int activeLimit) throws SQLException {
        if (uuid == null || uuid.isBlank() || questId == null || questId.isBlank()
                || currentStep == null || currentStep.isBlank() || startedAt == null || startedAt.isBlank()
                || activeLimit < 1 || activeLimit > 3) {
            throw new IllegalArgumentException("Quest start fields are invalid.");
        }
        return executeWriteTransaction(connection -> {
            boolean restartAbandoned = false;
            try (PreparedStatement checkExisting = connection.prepareStatement(
                    "SELECT status FROM player_quest_progress WHERE uuid = ? AND quest_id = ?")) {
                checkExisting.setString(1, uuid);
                checkExisting.setString(2, questId);
                try (ResultSet resultSet = checkExisting.executeQuery()) {
                    if (resultSet.next()) {
                        if (!"abandoned".equals(resultSet.getString("status")))
                            return QuestStartResult.ALREADY_EXISTS;
                        restartAbandoned = true;
                    }
                }
            }
            try (PreparedStatement count = connection.prepareStatement(
                    "SELECT COUNT(*) FROM player_quest_progress WHERE uuid = ? AND status = 'active'")) {
                count.setString(1, uuid);
                try (ResultSet resultSet = count.executeQuery()) {
                    if (resultSet.next() && resultSet.getInt(1) >= activeLimit)
                        return QuestStartResult.ACTIVE_LIMIT_REACHED;
                }
            }
            if (restartAbandoned) {
                try (PreparedStatement restart = connection.prepareStatement(
                        "UPDATE player_quest_progress SET status = 'active', current_step = ?, " +
                        "started_at = ?, completed_at = NULL, reward_claimed = 0, " +
                        "objective_progress = 0, tracked = 0 WHERE uuid = ? AND quest_id = ? " +
                        "AND status = 'abandoned'")) {
                    restart.setString(1, currentStep);
                    restart.setString(2, startedAt);
                    restart.setString(3, uuid);
                    restart.setString(4, questId);
                    return restart.executeUpdate() == 1 ? QuestStartResult.STARTED
                        : QuestStartResult.ALREADY_EXISTS;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO player_quest_progress " +
                    "(uuid, quest_id, status, current_step, started_at, reward_claimed) " +
                    "VALUES (?, ?, 'active', ?, ?, 0) ON CONFLICT(uuid, quest_id) DO NOTHING")) {
                insert.setString(1, uuid);
                insert.setString(2, questId);
                insert.setString(3, currentStep);
                insert.setString(4, startedAt);
                return insert.executeUpdate() == 1 ? QuestStartResult.STARTED : QuestStartResult.ALREADY_EXISTS;
            }
        });
    }

    public boolean abandonQuestProgress(String uuid, String questId, String abandonedAt) throws SQLException {
        if (uuid == null || uuid.isBlank() || questId == null || questId.isBlank()
                || abandonedAt == null || abandonedAt.isBlank()) {
            throw new IllegalArgumentException("Quest abandon fields are invalid.");
        }
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE player_quest_progress SET status = 'abandoned', completed_at = ?, tracked = 0 " +
                    "WHERE uuid = ? AND quest_id = ? AND status = 'active'")) {
                statement.setString(1, abandonedAt);
                statement.setString(2, uuid);
                statement.setString(3, questId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean setQuestTracked(String uuid, String questId) throws SQLException {
        if (uuid == null || uuid.isBlank() || questId == null || questId.isBlank()) {
            throw new IllegalArgumentException("Quest tracking identifiers are required.");
        }
        return executeWriteTransaction(connection -> {
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT 1 FROM player_quest_progress WHERE uuid = ? AND quest_id = ? AND status = 'active'")) {
                check.setString(1, uuid);
                check.setString(2, questId);
                try (ResultSet resultSet = check.executeQuery()) {
                    if (!resultSet.next()) return false;
                }
            }
            try (PreparedStatement clear = connection.prepareStatement(
                    "UPDATE player_quest_progress SET tracked = 0 WHERE uuid = ?")) {
                clear.setString(1, uuid);
                clear.executeUpdate();
            }
            try (PreparedStatement track = connection.prepareStatement(
                    "UPDATE player_quest_progress SET tracked = 1 WHERE uuid = ? AND quest_id = ? " +
                    "AND status = 'active'")) {
                track.setString(1, uuid);
                track.setString(2, questId);
                return track.executeUpdate() == 1;
            }
        });
    }

    public boolean clearQuestTracking(String uuid, String questId) throws SQLException {
        if (uuid == null || uuid.isBlank() || questId == null || questId.isBlank()) {
            throw new IllegalArgumentException("Quest tracking identifiers are required.");
        }
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE player_quest_progress SET tracked = 0 " +
                    "WHERE uuid = ? AND quest_id = ? AND status = 'active' AND tracked = 1")) {
                statement.setString(1, uuid);
                statement.setString(2, questId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public Optional<String> getTrackedQuestId(String uuid) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT quest_id FROM player_quest_progress WHERE uuid = ? AND status = 'active' " +
                 "AND tracked = 1 LIMIT 1")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.ofNullable(resultSet.getString("quest_id")) : Optional.empty();
            }
        }
    }

    public QuestObjectiveProgressResult incrementQuestObjectiveProgress(
            String uuid, String questId, String expectedStep, int increment, int target, String updatedAt)
            throws SQLException {
        if (uuid == null || uuid.isBlank() || questId == null || questId.isBlank()
                || expectedStep == null || expectedStep.isBlank()
                || increment < 1 || increment > 1000 || target < 1 || target > 100_000
                || updatedAt == null || updatedAt.isBlank()) {
            throw new IllegalArgumentException("Quest objective progress fields are invalid.");
        }
        return executeWriteTransaction(connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE player_quest_progress SET objective_progress = MIN(objective_progress + ?, ?), " +
                    "started_at = COALESCE(started_at, ?) " +
                    "WHERE uuid = ? AND quest_id = ? AND status = 'active' AND current_step = ?")) {
                update.setInt(1, increment);
                update.setInt(2, target);
                update.setString(3, updatedAt);
                update.setString(4, uuid);
                update.setString(5, questId);
                update.setString(6, expectedStep);
                if (update.executeUpdate() != 1) {
                    return new QuestObjectiveProgressResult(false, null, 0, false);
                }
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT current_step, objective_progress, tracked FROM player_quest_progress " +
                    "WHERE uuid = ? AND quest_id = ? AND status = 'active'")) {
                select.setString(1, uuid);
                select.setString(2, questId);
                try (ResultSet resultSet = select.executeQuery()) {
                    if (!resultSet.next()) return new QuestObjectiveProgressResult(false, null, 0, false);
                    return new QuestObjectiveProgressResult(true, resultSet.getString("current_step"),
                        resultSet.getInt("objective_progress"), resultSet.getInt("tracked") == 1);
                }
            }
        });
    }

    public Map<String, QuestProgress> getActiveQuestProgress(String uuid) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT quest_id, status, current_step, started_at, completed_at, reward_claimed, " +
                 "objective_progress, tracked FROM player_quest_progress WHERE uuid = ? AND status = 'active' " +
                 "ORDER BY started_at, quest_id")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<String, QuestProgress> progress = new LinkedHashMap<>();
                while (resultSet.next()) {
                    progress.put(resultSet.getString("quest_id"), new QuestProgress(
                        resultSet.getString("status"), resultSet.getString("current_step"),
                        resultSet.getString("started_at"), resultSet.getString("completed_at"),
                        resultSet.getInt("reward_claimed") == 1,
                        resultSet.getInt("objective_progress"), resultSet.getInt("tracked") == 1));
                }
                return Map.copyOf(progress);
            }
        }
    }

    public boolean advanceQuestObjective(String uuid, String questId, String expectedStep,
            String nextStep, String updatedAt) throws SQLException {
        if (uuid == null || uuid.isBlank() || questId == null || questId.isBlank()
                || expectedStep == null || expectedStep.isBlank() || nextStep == null || nextStep.isBlank()
                || updatedAt == null || updatedAt.isBlank()) {
            throw new IllegalArgumentException("Quest objective step fields are invalid.");
        }
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE player_quest_progress SET current_step = ?, objective_progress = 0 " +
                    "WHERE uuid = ? AND quest_id = ? AND status = 'active' AND current_step = ?")) {
                statement.setString(1, nextStep);
                statement.setString(2, uuid);
                statement.setString(3, questId);
                statement.setString(4, expectedStep);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public Set<String> getCompletedQuestIds(String uuid) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT quest_id FROM player_quest_progress WHERE uuid = ? AND status = 'completed'")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                Set<String> completed = new LinkedHashSet<>();
                while (resultSet.next()) completed.add(resultSet.getString("quest_id"));
                return Set.copyOf(completed);
            }
        }
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
        String sql = "UPDATE player_quest_progress SET status = 'completed', current_step = ?, " +
            "completed_at = ?, tracked = 0 " +
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
            "completed_at = ?, reward_claimed = 1, tracked = 0 " +
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

    public boolean completeQuestAndAwardWallet(String uuid, String questId, String currentStep,
            long coinReward, String completedAt) throws SQLException {
        validateQuestWalletReward(uuid, questId, coinReward, completedAt);
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE player_quest_progress SET status = 'completed', current_step = ?, " +
                    "completed_at = ?, reward_claimed = 1, tracked = 0 " +
                    "WHERE uuid = ? AND quest_id = ? AND status = 'active' AND reward_claimed = 0")) {
                statement.setString(1, currentStep);
                statement.setString(2, completedAt);
                statement.setString(3, uuid);
                statement.setString(4, questId);
                if (statement.executeUpdate() != 1) return false;
            }
            applyQuestWalletReward(connection, uuid, questId, coinReward, completedAt);
            return true;
        });
    }

    public boolean completeQuestAndAwardRewards(String uuid, String questId, String currentStep,
            int experienceReward, long coinReward, List<byte[]> itemRewards, String completedAt)
            throws SQLException {
        return completeQuestAndAwardRewards(uuid, questId, currentStep, experienceReward, coinReward,
            itemRewards, null, completedAt);
    }

    public boolean completeQuestAndAwardRewards(String uuid, String questId, String currentStep,
            int experienceReward, long coinReward, List<byte[]> itemRewards, String successorQuestId,
            String completedAt) throws SQLException {
        if (uuid == null || uuid.isBlank() || questId == null || questId.isBlank() || questId.length() > 64
                || experienceReward < 0 || experienceReward > 100_000 || coinReward < 0
                || coinReward > MAX_WALLET_BALANCE || itemRewards == null || itemRewards.size() > 45
                || completedAt == null || completedAt.isBlank()
                || (successorQuestId != null && (successorQuestId.length() > 64
                    || !successorQuestId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")))) {
            throw new IllegalArgumentException("Quest reward fields are invalid or outside supported bounds.");
        }
        for (byte[] itemData : itemRewards) {
            if (itemData == null || itemData.length == 0 || itemData.length > 1_048_576) {
                throw new IllegalArgumentException("Quest reward item is missing or larger than 1 MiB.");
            }
        }
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE player_quest_progress SET status = 'completed', current_step = ?, " +
                    "completed_at = ?, reward_claimed = 1, tracked = 0 " +
                    "WHERE uuid = ? AND quest_id = ? AND status = 'active' AND reward_claimed = 0")) {
                statement.setString(1, currentStep);
                statement.setString(2, completedAt);
                statement.setString(3, uuid);
                statement.setString(4, questId);
                if (statement.executeUpdate() != 1) return false;
            }
            if (experienceReward > 0) awardProfileExperience(connection, uuid, experienceReward, completedAt);
            if (coinReward > 0) applyQuestWalletReward(connection, uuid, questId, coinReward, completedAt);
            for (int index = 0; index < itemRewards.size(); index++) {
                String transactionId = "quest-item:" + uuid + ":" + questId + ":" + index;
                try (PreparedStatement delivery = connection.prepareStatement(
                        "INSERT INTO player_merchant_deliveries(transaction_id, uuid, item_data, created_at) " +
                        "VALUES (?, ?, ?, ?)")) {
                    delivery.setString(1, transactionId);
                    delivery.setString(2, uuid);
                    delivery.setBytes(3, itemRewards.get(index));
                    delivery.setString(4, completedAt);
                    delivery.executeUpdate();
                }
            }
            if (successorQuestId != null) {
                try (PreparedStatement pending = connection.prepareStatement(
                        "INSERT OR IGNORE INTO player_quest_successors_pending " +
                        "(uuid, source_quest_id, successor_quest_id, created_at) VALUES (?, ?, ?, ?)")) {
                    pending.setString(1, uuid);
                    pending.setString(2, questId);
                    pending.setString(3, successorQuestId);
                    pending.setString(4, completedAt);
                    pending.executeUpdate();
                }
            }
            return true;
        });
    }

    public List<PendingQuestSuccessor> getPendingQuestSuccessors(String uuid) throws SQLException {
        List<PendingQuestSuccessor> pending = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT source_quest_id, successor_quest_id FROM player_quest_successors_pending " +
                 "WHERE uuid = ? ORDER BY created_at, source_quest_id, successor_quest_id")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) pending.add(new PendingQuestSuccessor(
                    resultSet.getString("source_quest_id"), resultSet.getString("successor_quest_id")));
            }
        }
        return List.copyOf(pending);
    }

    public boolean removePendingQuestSuccessor(String uuid, String sourceQuestId,
            String successorQuestId) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM player_quest_successors_pending WHERE uuid = ? AND source_quest_id = ? " +
                 "AND successor_quest_id = ?")) {
            statement.setString(1, uuid);
            statement.setString(2, sourceQuestId);
            statement.setString(3, successorQuestId);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean claimCompletedQuestWalletReward(String uuid, String questId,
            long coinReward, String claimedAt) throws SQLException {
        validateQuestWalletReward(uuid, questId, coinReward, claimedAt);
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE player_quest_progress SET reward_claimed = 1 " +
                    "WHERE uuid = ? AND quest_id = ? AND status = 'completed' AND reward_claimed = 0")) {
                statement.setString(1, uuid);
                statement.setString(2, questId);
                if (statement.executeUpdate() != 1) return false;
            }
            applyQuestWalletReward(connection, uuid, questId, coinReward, claimedAt);
            return true;
        });
    }

    private void validateQuestWalletReward(String uuid, String questId, long amount, String timestamp) {
        if (uuid == null || uuid.isBlank() || questId == null || questId.isBlank()
                || questId.length() > 64 || amount < 1 || amount > MAX_WALLET_BALANCE
                || timestamp == null || timestamp.isBlank()) {
            throw new IllegalArgumentException("Quest VNCOIN reward fields are invalid.");
        }
    }

    private void applyQuestWalletReward(Connection connection, String uuid, String questId,
            long amount, String timestamp) throws SQLException {
        String transactionId = "quest-reward:" + uuid + ":" + questId;
        WalletTransactionResult result = applyWalletTransaction(connection, uuid, amount, transactionId,
            "quest", null, "Phần thưởng nhiệm vụ " + questId, timestamp);
        if (result.status() != WalletTransactionStatus.APPLIED
                && result.status() != WalletTransactionStatus.DUPLICATE) {
            throw new SQLException("Could not apply quest VNCOIN reward " + questId
                + ": wallet status=" + result.status());
        }
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
                 "SELECT status, current_step, started_at, completed_at, reward_claimed, " +
                     "objective_progress, tracked " +
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
                    resultSet.getInt("reward_claimed") == 1,
                    resultSet.getInt("objective_progress"),
                    resultSet.getInt("tracked") == 1
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
                                boolean rewardClaimed, int objectiveProgress, boolean tracked) {
        public QuestProgress(String status, String currentStep, String startedAt, String completedAt,
                             boolean rewardClaimed) {
            this(status, currentStep, startedAt, completedAt, rewardClaimed, 0, false);
        }
    }

    public record PendingQuestSuccessor(String sourceQuestId, String successorQuestId) {
    }

    public record QuestObjectiveProgressResult(boolean active, String currentStep, int progress, boolean tracked) {
    }

    public enum QuestStartResult { STARTED, ALREADY_EXISTS, ACTIVE_LIMIT_REACHED }

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

    public enum SkillCastStatus {
        SPENT,
        NOT_ENOUGH_MANA,
        PROFILE_NOT_FOUND,
        CLASS_MISMATCH,
        SKILL_NOT_LEARNED
    }

    public record SkillCastResult(SkillCastStatus status, int rank, ManaState manaState) {
    }

    public record SkillProgress(String skillId, int rank, String learnedAt) {
    }

    public record ManaState(int currentMana, int maxMana) {
    }

    public record ManaRestoreResult(ManaState state, int restoredAmount) {
    }

    public enum WalletTransactionStatus {
        APPLIED,
        DUPLICATE,
        INSUFFICIENT_FUNDS,
        BALANCE_LIMIT
    }

    public record WalletTransactionResult(WalletTransactionStatus status, long balance, long appliedDelta) {
    }

    public enum DailyQuestRewardStatus {
        APPLIED,
        DUPLICATE,
        DAILY_LIMIT,
        BALANCE_LIMIT
    }

    public record DailyQuestRewardResult(DailyQuestRewardStatus status, int completedRuns, long balance) {
    }

    public record MerchantDelivery(String transactionId, byte[] itemData) {
        public MerchantDelivery {
            if (transactionId == null || transactionId.isBlank() || itemData == null || itemData.length == 0) {
                throw new IllegalArgumentException("Merchant delivery data is invalid.");
            }
            itemData = itemData.clone();
        }

        @Override
        public byte[] itemData() {
            return itemData.clone();
        }
    }

    public record PendingMerchantSale(String transactionId, String uuid, byte[] itemData, int itemAmount,
            long price, String source, String actorUuid, String reason, String createdAt, String state) {
        public PendingMerchantSale {
            if (transactionId == null || transactionId.isBlank() || uuid == null || uuid.isBlank()
                    || itemData == null || itemData.length == 0 || itemAmount < 1 || price < 1
                    || source == null || source.isBlank() || reason == null || createdAt == null
                    || !("PREPARED".equals(state) || "TAGGED".equals(state))) {
                throw new IllegalArgumentException("Pending merchant sale data is invalid.");
            }
            itemData = itemData.clone();
        }

        @Override public byte[] itemData() { return itemData.clone(); }
    }

    public enum MerchantSaleCompletionStatus { PAID, ALREADY_PAID, REFUNDED }

    public record MerchantSaleCompletion(MerchantSaleCompletionStatus status, long balance) {
    }

    public record WalletLedgerEntry(long id, String transactionId, long delta, long balanceAfter,
                                    String source, String actorUuid, String reason, String createdAt) {
    }

    public record WalletSourceStats(String source, long transactions, long issued, long spent) {
    }

    public record WalletEconomyReport(long issued, long spent, long transactionCount,
                                      List<WalletSourceStats> sources) {
        public WalletEconomyReport {
            sources = List.copyOf(sources);
        }
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
                "objective_progress INTEGER NOT NULL DEFAULT 0 CHECK (objective_progress >= 0), " +
                "tracked INTEGER NOT NULL DEFAULT 0 CHECK (tracked IN (0, 1)), " +
                "PRIMARY KEY (uuid, quest_id), " +
                "FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)");
            createQuestSuccessorTables(statement);
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
            createWalletTables(statement);
            createMerchantDeliveryTables(statement);
            createMerchantSaleTables(statement);
            createDailyQuestRunTables(statement);
            createClassHistoryTables(statement);
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

    private void migrateV6ToV7(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            createWalletTables(statement);
        }
    }

    private void migrateV7ToV8(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            createMerchantDeliveryTables(statement);
        }
    }

    private void migrateV8ToV9(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            createDailyQuestRunTables(statement);
        }
    }

    private void migrateV9ToV10(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!columnExists(connection, "player_quest_progress", "objective_progress")) {
                statement.execute("ALTER TABLE player_quest_progress ADD COLUMN " +
                    "objective_progress INTEGER NOT NULL DEFAULT 0 CHECK (objective_progress >= 0)");
            }
            if (!columnExists(connection, "player_quest_progress", "tracked")) {
                statement.execute("ALTER TABLE player_quest_progress ADD COLUMN " +
                    "tracked INTEGER NOT NULL DEFAULT 0 CHECK (tracked IN (0, 1))");
            }
        }
    }

    private void migrateV10ToV11(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            createQuestSuccessorTables(statement);
        }
    }

    private void migrateV11ToV12(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            createMerchantSaleTables(statement);
        }
    }

    private void migrateV12ToV13(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            createClassHistoryTables(statement);
        }
    }

    private static void createClassHistoryTables(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS player_class_history (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, from_class TEXT NOT NULL, " +
            "to_class TEXT NOT NULL, changed_at TEXT NOT NULL, " +
            "FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_player_class_history_uuid_id " +
            "ON player_class_history(uuid, id)");
    }

    private static void createWalletTables(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS player_wallets (" +
            "uuid TEXT PRIMARY KEY, balance INTEGER NOT NULL DEFAULT 0 " +
            "CHECK (balance BETWEEN 0 AND 2147483647), updated_at TEXT NOT NULL)");
        statement.execute("CREATE TABLE IF NOT EXISTS player_wallet_ledger (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, transaction_id TEXT NOT NULL UNIQUE, " +
            "uuid TEXT NOT NULL, delta INTEGER NOT NULL CHECK (delta != 0), " +
            "balance_after INTEGER NOT NULL CHECK (balance_after BETWEEN 0 AND 2147483647), " +
            "source TEXT NOT NULL, actor_uuid TEXT, reason TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_player_wallet_ledger_uuid_id " +
            "ON player_wallet_ledger(uuid, id DESC)");
    }

    private static void createMerchantDeliveryTables(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS player_merchant_deliveries (" +
            "transaction_id TEXT PRIMARY KEY, uuid TEXT NOT NULL, item_data BLOB NOT NULL, " +
            "created_at TEXT NOT NULL, delivered_at TEXT)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_player_merchant_deliveries_uuid_pending " +
            "ON player_merchant_deliveries(uuid, delivered_at)");
    }

    private static void createMerchantSaleTables(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS player_merchant_sales_pending (" +
            "transaction_id TEXT PRIMARY KEY, uuid TEXT NOT NULL, item_data BLOB NOT NULL, " +
            "item_amount INTEGER NOT NULL CHECK (item_amount BETWEEN 1 AND 100000), " +
            "price INTEGER NOT NULL CHECK (price BETWEEN 1 AND 1000000), " +
            "source TEXT NOT NULL, actor_uuid TEXT, reason TEXT NOT NULL, created_at TEXT NOT NULL, " +
            "state TEXT NOT NULL CHECK (state IN ('PREPARED', 'TAGGED'))) ");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_player_merchant_sales_uuid " +
            "ON player_merchant_sales_pending(uuid, created_at)");
    }

    private static void createDailyQuestRunTables(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS player_daily_quest_runs (" +
            "uuid TEXT NOT NULL, quest_id TEXT NOT NULL, reset_date TEXT NOT NULL, " +
            "run_number INTEGER NOT NULL CHECK (run_number BETWEEN 1 AND 10), " +
            "source_id TEXT NOT NULL, completed_at TEXT NOT NULL, " +
            "PRIMARY KEY (uuid, quest_id, reset_date, run_number), " +
            "UNIQUE (uuid, quest_id, source_id), " +
            "FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_daily_quest_runs_player_date " +
            "ON player_daily_quest_runs(uuid, reset_date, quest_id)");
    }

    private static void createQuestSuccessorTables(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS player_quest_successors_pending (" +
            "uuid TEXT NOT NULL, source_quest_id TEXT NOT NULL, successor_quest_id TEXT NOT NULL, " +
            "created_at TEXT NOT NULL, PRIMARY KEY (uuid, source_quest_id, successor_quest_id), " +
            "FOREIGN KEY (uuid) REFERENCES player_profiles(uuid) ON DELETE CASCADE)");
    }

    /**
     * Atomically awards one repeatable VNCOIN quest run, bounded by the per-player daily limit.
     * The source ID makes the completion callback idempotent across retries and reloads.
     */
    public DailyQuestRewardResult awardDailyRepeatableQuestWalletReward(
            String uuid, String questId, String resetDate, String sourceId,
            int dailyLimit, long rewardAmount, String completedAt) throws SQLException {
        validateDailyQuestReward(uuid, questId, resetDate, sourceId, dailyLimit, rewardAmount, completedAt);
        return executeWriteTransaction(connection -> {
            int completedRuns;
            try (PreparedStatement duplicate = connection.prepareStatement(
                    "SELECT run_number, reset_date FROM player_daily_quest_runs " +
                    "WHERE uuid = ? AND quest_id = ? AND source_id = ?")) {
                duplicate.setString(1, uuid);
                duplicate.setString(2, questId);
                duplicate.setString(3, sourceId);
                try (ResultSet resultSet = duplicate.executeQuery()) {
                    if (resultSet.next()) {
                        int currentRuns = countDailyQuestRuns(connection, uuid, questId,
                            resultSet.getString("reset_date"));
                        return new DailyQuestRewardResult(DailyQuestRewardStatus.DUPLICATE,
                            currentRuns, readWalletBalance(connection, uuid));
                    }
                }
            }
            completedRuns = countDailyQuestRuns(connection, uuid, questId, resetDate);
            if (completedRuns >= dailyLimit) {
                return new DailyQuestRewardResult(DailyQuestRewardStatus.DAILY_LIMIT,
                    completedRuns, readWalletBalance(connection, uuid));
            }

            int runNumber = completedRuns + 1;
            String transactionId = "daily-quest-reward:" + uuid + ":" + questId + ":" + sourceId;
            WalletTransactionResult walletResult = applyWalletTransaction(connection, uuid, rewardAmount,
                transactionId, "quest", null, "Thưởng nhiệm vụ lặp lại " + questId, completedAt);
            if (walletResult.status() == WalletTransactionStatus.BALANCE_LIMIT) {
                return new DailyQuestRewardResult(DailyQuestRewardStatus.BALANCE_LIMIT,
                    completedRuns, walletResult.balance());
            }
            if (walletResult.status() != WalletTransactionStatus.APPLIED) {
                throw new SQLException("Repeatable quest reward transaction was not applied: "
                    + walletResult.status());
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO player_daily_quest_runs " +
                    "(uuid, quest_id, reset_date, run_number, source_id, completed_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?)")) {
                insert.setString(1, uuid);
                insert.setString(2, questId);
                insert.setString(3, resetDate);
                insert.setInt(4, runNumber);
                insert.setString(5, sourceId);
                insert.setString(6, completedAt);
                if (insert.executeUpdate() != 1) {
                    throw new SQLException("Could not record repeatable quest run " + questId + ".");
                }
            }
            return new DailyQuestRewardResult(DailyQuestRewardStatus.APPLIED,
                runNumber, walletResult.balance());
        });
    }

    public int getDailyQuestRunCount(String uuid, String questId, String resetDate) throws SQLException {
        if (uuid == null || uuid.isBlank() || !isQuestIdValid(questId) || !isDateValid(resetDate)) {
            throw new IllegalArgumentException("Daily quest lookup fields are invalid.");
        }
        try (Connection connection = openConnection()) {
            return countDailyQuestRuns(connection, uuid, questId, resetDate);
        }
    }

    private int countDailyQuestRuns(Connection connection, String uuid, String questId, String resetDate)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM player_daily_quest_runs " +
                "WHERE uuid = ? AND quest_id = ? AND reset_date = ?")) {
            statement.setString(1, uuid);
            statement.setString(2, questId);
            statement.setString(3, resetDate);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) throw new SQLException("Could not count daily quest runs.");
                return resultSet.getInt(1);
            }
        }
    }

    private void validateDailyQuestReward(String uuid, String questId, String resetDate, String sourceId,
            int dailyLimit, long rewardAmount, String completedAt) {
        if (uuid == null || !uuid.matches("(?i)[0-9a-f-]{36}")
                || !isQuestIdValid(questId) || !isDateValid(resetDate)
                || sourceId == null || !sourceId.matches("[a-zA-Z0-9_-]{1,40}")
                || dailyLimit < 1 || dailyLimit > 10 || rewardAmount < 1
                || rewardAmount > MAX_WALLET_BALANCE || completedAt == null || completedAt.isBlank()) {
            throw new IllegalArgumentException("Repeatable daily quest reward fields are invalid.");
        }
    }

    private boolean isQuestIdValid(String questId) {
        return questId != null && questId.matches("[a-z0-9]+(?:-[a-z0-9]+){0,7}")
            && questId.length() <= 48;
    }

    private boolean isDateValid(String date) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        try {
            LocalDate.parse(date);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    public long getWalletBalance(String uuid) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT balance FROM player_wallets WHERE uuid = ?")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("balance") : 0L;
            }
        }
    }

    public WalletTransactionResult applyWalletTransaction(String uuid, long delta, String transactionId,
            String source, String actorUuid, String reason, String createdAt) throws SQLException {
        validateWalletTransaction(uuid, delta, transactionId, source, reason, createdAt);
        return executeWriteTransaction(connection -> applyWalletTransaction(connection, uuid, delta,
            transactionId, source, actorUuid, reason, createdAt));
    }

    public WalletTransactionResult purchaseWalletItem(String uuid, long price, String transactionId,
            String source, String actorUuid, String reason, String createdAt, byte[] itemData) throws SQLException {
        if (price < 1 || price > MAX_WALLET_BALANCE) {
            throw new IllegalArgumentException("Merchant item price is outside supported bounds.");
        }
        validateWalletTransaction(uuid, -price, transactionId, source, reason, createdAt);
        if (itemData == null || itemData.length == 0 || itemData.length > 1_048_576) {
            throw new IllegalArgumentException("Merchant delivery item data is missing or exceeds 1 MiB.");
        }
        return executeWriteTransaction(connection -> {
            WalletTransactionResult result = applyWalletTransaction(connection, uuid, -price, transactionId,
                source, actorUuid, reason, createdAt);
            if (result.status() == WalletTransactionStatus.APPLIED) {
                try (PreparedStatement delivery = connection.prepareStatement(
                        "INSERT INTO player_merchant_deliveries(transaction_id, uuid, item_data, created_at) " +
                        "VALUES (?, ?, ?, ?)")) {
                    delivery.setString(1, transactionId);
                    delivery.setString(2, uuid);
                    delivery.setBytes(3, itemData);
                    delivery.setString(4, createdAt);
                    delivery.executeUpdate();
                }
            }
            return result;
        });
    }

    public void createMerchantSaleIntent(String uuid, String transactionId, byte[] itemData, int itemAmount,
            long price, String source, String actorUuid, String reason, String createdAt) throws SQLException {
        validateWalletTransaction(uuid, price, transactionId, source, reason, createdAt);
        if (itemData == null || itemData.length == 0 || itemData.length > 1_048_576
                || itemAmount < 1 || itemAmount > 100_000) {
            throw new IllegalArgumentException("Merchant sale escrow item data/amount is invalid.");
        }
        executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO player_merchant_sales_pending(transaction_id, uuid, item_data, item_amount, " +
                    "price, source, actor_uuid, reason, created_at, state) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PREPARED')")) {
                statement.setString(1, transactionId);
                statement.setString(2, uuid);
                statement.setBytes(3, itemData);
                statement.setInt(4, itemAmount);
                statement.setLong(5, price);
                statement.setString(6, source);
                if (actorUuid == null || actorUuid.isBlank()) statement.setNull(7, java.sql.Types.VARCHAR);
                else statement.setString(7, actorUuid);
                statement.setString(8, reason);
                statement.setString(9, createdAt);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public boolean markMerchantSaleTagged(String uuid, String transactionId) throws SQLException {
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE player_merchant_sales_pending SET state = 'TAGGED' " +
                    "WHERE transaction_id = ? AND uuid = ? AND state = 'PREPARED'")) {
                statement.setString(1, transactionId);
                statement.setString(2, uuid);
                if (statement.executeUpdate() == 1) return true;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT state FROM player_merchant_sales_pending WHERE transaction_id = ? AND uuid = ?")) {
                statement.setString(1, transactionId);
                statement.setString(2, uuid);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() && "TAGGED".equals(resultSet.getString("state"));
                }
            }
        });
    }

    public boolean cancelPreparedMerchantSale(String uuid, String transactionId) throws SQLException {
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM player_merchant_sales_pending WHERE transaction_id = ? AND uuid = ? " +
                    "AND state = 'PREPARED'")) {
                statement.setString(1, transactionId);
                statement.setString(2, uuid);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public List<PendingMerchantSale> getPendingMerchantSales(String uuid) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT transaction_id, item_data, item_amount, price, source, actor_uuid, reason, created_at, state " +
                 "FROM player_merchant_sales_pending WHERE uuid = ? ORDER BY created_at, transaction_id")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PendingMerchantSale> pending = new ArrayList<>();
                while (resultSet.next()) {
                    pending.add(new PendingMerchantSale(resultSet.getString("transaction_id"), uuid,
                        resultSet.getBytes("item_data"), resultSet.getInt("item_amount"),
                        resultSet.getLong("price"), resultSet.getString("source"),
                        resultSet.getString("actor_uuid"), resultSet.getString("reason"),
                        resultSet.getString("created_at"), resultSet.getString("state")));
                }
                return List.copyOf(pending);
            }
        }
    }

    public MerchantSaleCompletion completeMerchantSale(String uuid, String transactionId,
            List<byte[]> refundItems) throws SQLException {
        if (uuid == null || uuid.isBlank() || transactionId == null || transactionId.isBlank()
                || refundItems == null || refundItems.isEmpty() || refundItems.size() > 45
                || refundItems.stream().anyMatch(item -> item == null || item.length == 0
                    || item.length > 1_048_576)) {
            throw new IllegalArgumentException("Merchant sale completion data is invalid.");
        }
        return executeWriteTransaction(connection -> {
            String playerId;
            long price;
            String source;
            String actorUuid;
            String reason;
            String createdAt;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT uuid, price, source, actor_uuid, reason, created_at " +
                    "FROM player_merchant_sales_pending WHERE transaction_id = ? AND uuid = ?")) {
                statement.setString(1, transactionId);
                statement.setString(2, uuid);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        try (PreparedStatement ledger = connection.prepareStatement(
                                "SELECT balance_after FROM player_wallet_ledger WHERE transaction_id = ? AND uuid = ?")) {
                            ledger.setString(1, transactionId);
                            ledger.setString(2, uuid);
                            try (ResultSet applied = ledger.executeQuery()) {
                                if (applied.next()) return new MerchantSaleCompletion(
                                    MerchantSaleCompletionStatus.ALREADY_PAID,
                                    applied.getLong("balance_after"));
                            }
                        }
                        throw new SQLException("Pending merchant sale intent is missing: " + transactionId);
                    }
                    playerId = resultSet.getString("uuid");
                    price = resultSet.getLong("price");
                    source = resultSet.getString("source");
                    actorUuid = resultSet.getString("actor_uuid");
                    reason = resultSet.getString("reason");
                    createdAt = resultSet.getString("created_at");
                }
            }
            WalletTransactionResult wallet = applyWalletTransaction(connection, playerId, price,
                transactionId, source, actorUuid, reason, createdAt);
            if (wallet.status() == WalletTransactionStatus.APPLIED
                    || wallet.status() == WalletTransactionStatus.DUPLICATE) {
                deleteMerchantSaleIntent(connection, uuid, transactionId);
                return new MerchantSaleCompletion(wallet.status() == WalletTransactionStatus.APPLIED
                    ? MerchantSaleCompletionStatus.PAID : MerchantSaleCompletionStatus.ALREADY_PAID,
                    wallet.balance());
            }
            if (wallet.status() != WalletTransactionStatus.BALANCE_LIMIT) {
                throw new SQLException("Merchant sale wallet credit was rejected with status " + wallet.status());
            }
            try (PreparedStatement delivery = connection.prepareStatement(
                    "INSERT INTO player_merchant_deliveries(transaction_id, uuid, item_data, created_at) " +
                    "VALUES (?, ?, ?, ?) ON CONFLICT(transaction_id) DO NOTHING")) {
                for (int index = 0; index < refundItems.size(); index++) {
                    delivery.setString(1, transactionId + "-refund-" + index);
                    delivery.setString(2, uuid);
                    delivery.setBytes(3, refundItems.get(index));
                    delivery.setString(4, createdAt);
                    delivery.addBatch();
                }
                delivery.executeBatch();
            }
            deleteMerchantSaleIntent(connection, uuid, transactionId);
            return new MerchantSaleCompletion(MerchantSaleCompletionStatus.REFUNDED, wallet.balance());
        });
    }

    private void deleteMerchantSaleIntent(Connection connection, String uuid, String transactionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM player_merchant_sales_pending WHERE transaction_id = ? AND uuid = ?")) {
            statement.setString(1, transactionId);
            statement.setString(2, uuid);
            statement.executeUpdate();
        }
    }

    private void validateWalletTransaction(String uuid, long delta, String transactionId,
            String source, String reason, String createdAt) {
        if (uuid == null || uuid.isBlank() || transactionId == null || transactionId.isBlank()
                || transactionId.length() > 160 || source == null || source.isBlank()
                || source.length() > 64 || reason == null || reason.length() > 240
                || createdAt == null || createdAt.isBlank() || delta == 0
                || delta < -MAX_WALLET_BALANCE || delta > MAX_WALLET_BALANCE) {
            throw new IllegalArgumentException("Wallet transaction fields are invalid or outside supported bounds.");
        }
    }

    private WalletTransactionResult applyWalletTransaction(Connection connection, String uuid, long delta,
            String transactionId, String source, String actorUuid, String reason, String createdAt)
            throws SQLException {
        try (PreparedStatement duplicate = connection.prepareStatement(
                "SELECT uuid FROM player_wallet_ledger WHERE transaction_id = ?")) {
            duplicate.setString(1, transactionId);
            try (ResultSet resultSet = duplicate.executeQuery()) {
                if (resultSet.next()) {
                    return new WalletTransactionResult(WalletTransactionStatus.DUPLICATE,
                        readWalletBalance(connection, uuid), 0L);
                }
            }
        }
        long currentBalance = readWalletBalance(connection, uuid);
        if (delta < 0 && currentBalance < -delta) {
            return new WalletTransactionResult(WalletTransactionStatus.INSUFFICIENT_FUNDS, currentBalance, 0L);
        }
        if (delta > 0 && currentBalance > MAX_WALLET_BALANCE - delta) {
            return new WalletTransactionResult(WalletTransactionStatus.BALANCE_LIMIT, currentBalance, 0L);
        }
        long newBalance = currentBalance + delta;
        try (PreparedStatement wallet = connection.prepareStatement(
                "INSERT INTO player_wallets(uuid, balance, updated_at) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance, updated_at = excluded.updated_at")) {
            wallet.setString(1, uuid);
            wallet.setLong(2, newBalance);
            wallet.setString(3, createdAt);
            wallet.executeUpdate();
        }
        try (PreparedStatement ledger = connection.prepareStatement(
                "INSERT INTO player_wallet_ledger(transaction_id, uuid, delta, balance_after, " +
                "source, actor_uuid, reason, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ledger.setString(1, transactionId);
            ledger.setString(2, uuid);
            ledger.setLong(3, delta);
            ledger.setLong(4, newBalance);
            ledger.setString(5, source);
            if (actorUuid == null || actorUuid.isBlank()) ledger.setNull(6, java.sql.Types.VARCHAR);
            else ledger.setString(6, actorUuid);
            ledger.setString(7, reason);
            ledger.setString(8, createdAt);
            ledger.executeUpdate();
        }
        return new WalletTransactionResult(WalletTransactionStatus.APPLIED, newBalance, delta);
    }

    public List<MerchantDelivery> getPendingMerchantDeliveries(String uuid) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT transaction_id, item_data FROM player_merchant_deliveries " +
                 "WHERE uuid = ? AND delivered_at IS NULL ORDER BY created_at, transaction_id")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<MerchantDelivery> deliveries = new ArrayList<>();
                while (resultSet.next()) {
                    deliveries.add(new MerchantDelivery(resultSet.getString("transaction_id"),
                        resultSet.getBytes("item_data")));
                }
                return List.copyOf(deliveries);
            }
        }
    }

    public void enqueueMerchantDeliveries(String uuid, String transactionPrefix,
            List<byte[]> itemData, String createdAt) throws SQLException {
        if (uuid == null || uuid.isBlank() || transactionPrefix == null || transactionPrefix.isBlank()
                || itemData == null || itemData.isEmpty() || itemData.size() > 45
                || createdAt == null || createdAt.isBlank()) {
            throw new IllegalArgumentException("Pending item delivery fields are invalid.");
        }
        for (byte[] data : itemData) {
            if (data == null || data.length == 0 || data.length > 1_048_576)
                throw new IllegalArgumentException("Pending item delivery contains an invalid item.");
        }
        executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO player_merchant_deliveries(transaction_id, uuid, item_data, created_at) " +
                    "VALUES (?, ?, ?, ?) ON CONFLICT(transaction_id) DO NOTHING")) {
                for (int index = 0; index < itemData.size(); index++) {
                    statement.setString(1, transactionPrefix + ":" + index);
                    statement.setString(2, uuid);
                    statement.setBytes(3, itemData.get(index));
                    statement.setString(4, createdAt);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    public boolean markMerchantDeliveryDelivered(String uuid, String transactionId, String deliveredAt)
            throws SQLException {
        if (uuid == null || uuid.isBlank() || transactionId == null || transactionId.isBlank()
                || deliveredAt == null || deliveredAt.isBlank()) {
            throw new IllegalArgumentException("Merchant delivery acknowledgement fields are invalid.");
        }
        return executeWriteTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE player_merchant_deliveries SET delivered_at = ? " +
                    "WHERE transaction_id = ? AND uuid = ? AND delivered_at IS NULL")) {
                statement.setString(1, deliveredAt);
                statement.setString(2, transactionId);
                statement.setString(3, uuid);
                if (statement.executeUpdate() == 1) return true;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM player_merchant_deliveries WHERE transaction_id = ? AND uuid = ? " +
                    "AND delivered_at IS NOT NULL")) {
                statement.setString(1, transactionId);
                statement.setString(2, uuid);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        });
    }

    public WalletEconomyReport getWalletEconomyReport(String since) throws SQLException {
        if (since == null || since.isBlank()) throw new IllegalArgumentException("Report start time is required.");
        try (Connection connection = openConnection()) {
            long issued;
            long spent;
            long transactions;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COALESCE(SUM(CASE WHEN delta > 0 THEN delta ELSE 0 END), 0) AS issued, " +
                    "COALESCE(SUM(CASE WHEN delta < 0 THEN -delta ELSE 0 END), 0) AS spent, " +
                    "COUNT(*) AS transactions FROM player_wallet_ledger WHERE created_at >= ?")) {
                statement.setString(1, since);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) throw new SQLException("Could not calculate VNCOIN economy report.");
                    issued = resultSet.getLong("issued");
                    spent = resultSet.getLong("spent");
                    transactions = resultSet.getLong("transactions");
                }
            }
            List<WalletSourceStats> sources = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT source, COUNT(*) AS transactions, " +
                    "COALESCE(SUM(CASE WHEN delta > 0 THEN delta ELSE 0 END), 0) AS issued, " +
                    "COALESCE(SUM(CASE WHEN delta < 0 THEN -delta ELSE 0 END), 0) AS spent " +
                    "FROM player_wallet_ledger WHERE created_at >= ? GROUP BY source " +
                    "ORDER BY SUM(ABS(delta)) DESC, source ASC LIMIT 20")) {
                statement.setString(1, since);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        sources.add(new WalletSourceStats(resultSet.getString("source"),
                            resultSet.getLong("transactions"), resultSet.getLong("issued"),
                            resultSet.getLong("spent")));
                    }
                }
            }
            return new WalletEconomyReport(issued, spent, transactions, sources);
        }
    }

    public List<WalletLedgerEntry> getWalletHistory(String uuid, int limit) throws SQLException {
        int safeLimit = Math.max(1, Math.min(20, limit));
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT id, transaction_id, delta, balance_after, source, actor_uuid, reason, created_at " +
                 "FROM player_wallet_ledger WHERE uuid = ? ORDER BY id DESC LIMIT ?")) {
            statement.setString(1, uuid);
            statement.setInt(2, safeLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<WalletLedgerEntry> entries = new ArrayList<>();
                while (resultSet.next()) {
                    entries.add(new WalletLedgerEntry(resultSet.getLong("id"),
                        resultSet.getString("transaction_id"), resultSet.getLong("delta"),
                        resultSet.getLong("balance_after"), resultSet.getString("source"),
                        resultSet.getString("actor_uuid"), resultSet.getString("reason"),
                        resultSet.getString("created_at")));
                }
                return List.copyOf(entries);
            }
        }
    }

    private long readWalletBalance(Connection connection, String uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance FROM player_wallets WHERE uuid = ?")) {
            statement.setString(1, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("balance") : 0L;
            }
        }
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
