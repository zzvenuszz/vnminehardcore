package com.vnmine.hardcore.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PlayerProfileDatabaseManagerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void initializesExpectedTablesAndCurrentSchemaVersion() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();

        manager.initialize();

        assertEquals(PlayerProfileDatabaseManager.SCHEMA_VERSION, manager.getSchemaVersion());
        assertTrue(manager.tableExists("meta"));
        assertTrue(manager.tableExists("player_profiles"));
        assertTrue(columnExists(manager.getDatabaseFile(), "skill_points"));
        assertTrue(columnExists(manager.getDatabaseFile(), "mana"));
        assertTrue(columnExists(manager.getDatabaseFile(), "mana_updated_at"));
        assertTrue(manager.tableExists("player_quest_progress"));
        assertTrue(manager.tableExists("player_world_progress"));
        assertTrue(manager.tableExists("player_portal_progress"));
        assertTrue(manager.tableExists("player_skill_progress"));
        assertTrue(manager.tableExists("player_skill_slots"));
    }

    @Test
    public void migratesLegacyProfileTableAndPreservesExistingData() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        createLegacyDatabase(manager.getDatabaseFile(), 1);

        manager.initialize();

        assertEquals(PlayerProfileDatabaseManager.SCHEMA_VERSION, manager.getSchemaVersion());
        assertTrue(columnExists(manager.getDatabaseFile(), "selected_class"));
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery(
                 "SELECT player_name, selected_race FROM player_profiles WHERE uuid = 'legacy-player'")) {
            assertTrue(resultSet.next());
            assertEquals("Legacy", resultSet.getString("player_name"));
            assertEquals("human", resultSet.getString("selected_race"));
        }
    }

    @Test
    public void migratesV2ProfilesAndConvertsExistingExperienceToLevelsAndPoints() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        createLegacyDatabase(manager.getDatabaseFile(), 2, 1, 375);

        manager.initialize();

        var profile = manager.getPlayerProfile("legacy-player").orElseThrow();
        assertEquals(PlayerProfileDatabaseManager.SCHEMA_VERSION, manager.getSchemaVersion());
        assertEquals(3, profile.level());
        assertEquals(125, profile.exp());
        assertEquals(2, profile.unspentSkillPoints());
        assertNotNull(latestBackup(manager));
    }

    @Test
    public void migrationIsAtomicWhenSchemaVersionCannotBeUpdated() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        createLegacyDatabase(manager.getDatabaseFile(), 1);
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_schema_update BEFORE UPDATE ON meta " +
                "WHEN NEW.key = 'schema_version' BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        }

        assertThrows(SQLException.class, manager::initialize);

        assertEquals(1, manager.getSchemaVersion());
        assertFalse(columnExists(manager.getDatabaseFile(), "selected_class"));
    }

    @Test
    public void v2ToV3MigrationIsAtomicWhenVersionUpdateFails() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        createLegacyDatabase(manager.getDatabaseFile(), 2, 1, 375);
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_v3_schema_update BEFORE UPDATE ON meta " +
                "WHEN NEW.key = 'schema_version' AND NEW.value = '3' " +
                "BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        }

        assertThrows(SQLException.class, manager::initialize);

        assertEquals(2, manager.getSchemaVersion());
        assertFalse(columnExists(manager.getDatabaseFile(), "skill_points"));
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery(
                 "SELECT level, exp FROM player_profiles WHERE uuid = 'legacy-player'")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt("level"));
            assertEquals(375, resultSet.getInt("exp"));
        }
    }

    @Test
    public void v3ToV4MigrationIsAtomicWhenVersionUpdateFails() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        createLegacyDatabase(manager.getDatabaseFile(), 3);
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_v4_schema_update BEFORE UPDATE ON meta " +
                "WHEN NEW.key = 'schema_version' AND NEW.value = '4' " +
                "BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        }

        assertThrows(SQLException.class, manager::initialize);

        assertEquals(3, manager.getSchemaVersion());
        assertFalse(manager.tableExists("player_skill_progress"));
    }

    @Test
    public void v4ToV5MigrationIsAtomicWhenVersionUpdateFails() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        createLegacyDatabase(manager.getDatabaseFile(), 4);
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_v5_schema_update BEFORE UPDATE ON meta " +
                "WHEN NEW.key = 'schema_version' AND NEW.value = '5' " +
                "BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        }

        assertThrows(SQLException.class, manager::initialize);

        assertEquals(4, manager.getSchemaVersion());
        assertFalse(manager.tableExists("player_skill_slots"));
    }

    @Test
    public void v5ToV6ManaMigrationIsAtomicWhenVersionUpdateFails() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        createLegacyDatabase(manager.getDatabaseFile(), 5);
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_v6_schema_update BEFORE UPDATE ON meta " +
                "WHEN NEW.key = 'schema_version' AND NEW.value = '6' " +
                "BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        }

        assertThrows(SQLException.class, manager::initialize);

        assertEquals(5, manager.getSchemaVersion());
        assertFalse(columnExists(manager.getDatabaseFile(), "mana"));
        assertFalse(columnExists(manager.getDatabaseFile(), "mana_updated_at"));
    }

    @Test
    public void refusesDatabaseCreatedByNewerPluginVersion() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        createLegacyDatabase(manager.getDatabaseFile(), PlayerProfileDatabaseManager.SCHEMA_VERSION + 1);

        SQLException exception = assertThrows(SQLException.class, manager::initialize);

        assertTrue(exception.getMessage().contains("newer than supported"));
        assertEquals(PlayerProfileDatabaseManager.SCHEMA_VERSION + 1, manager.getSchemaVersion());
    }

    @Test
    public void createsConsistentBackupContainingCommittedProfile() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z"));

        manager.backupDatabase();

        File backup = latestBackup(manager);
        assertNotNull(backup);
        try (Connection connection = open(backup);
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery(
                 "SELECT player_name FROM player_profiles WHERE uuid = '" + uuid + "'")) {
            assertTrue(resultSet.next());
            assertEquals("Ares", resultSet.getString("player_name"));
        }
    }

    @Test
    public void adminExperienceGrantUsesConfiguredProgressionAndAwardsSkillPoints() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666667";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "created"));

        var result = manager.awardCharacterExperience(uuid, 250, "admin-award").orElseThrow();
        var profile = manager.getPlayerProfile(uuid).orElseThrow();

        assertEquals(1, result.previousLevel());
        assertEquals(3, result.currentLevel());
        assertEquals(2, result.skillPointsGained());
        assertEquals(3, profile.level());
        assertEquals(0, profile.exp());
        assertEquals(2, profile.unspentSkillPoints());
    }

    @Test
    public void adminLevelGrantPreservesExperienceAndCapsAtConfiguredMaximum() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        manager.configureCharacterProgression(5, 100, 50, 2);
        String uuid = "11111111-2222-3333-4444-555555666668";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 3, 40,
            "human-realm", "created"));

        var result = manager.grantCharacterLevels(uuid, 5, "admin-level-grant").orElseThrow();
        var profile = manager.getPlayerProfile(uuid).orElseThrow();

        assertEquals(3, result.previousLevel());
        assertEquals(5, result.currentLevel());
        assertEquals(4, result.skillPointsGained());
        assertEquals(5, profile.level());
        assertEquals(40, profile.exp());
        assertEquals(4, profile.unspentSkillPoints());
    }

    @Test
    public void skillLearningChecksProfileRequirementsAndPersistsExactlyOnce() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 2, 0,
            "human-realm", "2026-10-21T00:00:00Z");

        assertEquals(PlayerProfileDatabaseManager.SkillLearnResult.REQUIREMENTS_NOT_MET,
            manager.learnSkill(uuid, "shield-wall", Set.of("guardian"), 1, "learned"));
        assertEquals(PlayerProfileDatabaseManager.SkillLearnResult.REQUIREMENTS_NOT_MET,
            manager.learnSkill(uuid, "battle-focus", Set.of("swordsman"), 3, "learned"));
        assertEquals(PlayerProfileDatabaseManager.SkillLearnResult.LEARNED,
            manager.learnSkill(uuid, "blade-flare", Set.of("swordsman"), 1, "2026-10-21T00:01:00Z"));
        assertEquals(PlayerProfileDatabaseManager.SkillLearnResult.ALREADY_LEARNED,
            manager.learnSkill(uuid, "blade-flare", Set.of("swordsman"), 1, "2026-10-21T00:02:00Z"));
        assertEquals(List.of(new PlayerProfileDatabaseManager.SkillProgress(
            "blade-flare", 1, "2026-10-21T00:01:00Z")), manager.getLearnedSkills(uuid));
        assertEquals(PlayerProfileDatabaseManager.SkillLearnResult.PROFILE_NOT_FOUND,
            manager.learnSkill("missing", "blade-flare", Set.of("swordsman"), 1, "learned"));
    }

    @Test
    public void quickSkillSlotsPersistAndEnforceLearnedClassAndUniqueAssignment() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 2, 0,
            "human-realm", "2026-10-21T00:00:00Z");
        manager.learnSkill(uuid, "blade-flare", Set.of("swordsman"), 1, "learned");
        manager.learnSkill(uuid, "guard-break", Set.of("swordsman"), 2, "learned");

        assertEquals(PlayerProfileDatabaseManager.QuickSlotResult.ASSIGNED,
            manager.assignQuickSkill(uuid, 1, "blade-flare", Set.of("swordsman")));
        assertEquals(PlayerProfileDatabaseManager.QuickSlotResult.ALREADY_ASSIGNED,
            manager.assignQuickSkill(uuid, 2, "blade-flare", Set.of("swordsman")));
        assertEquals(PlayerProfileDatabaseManager.QuickSlotResult.SKILL_NOT_LEARNED,
            manager.assignQuickSkill(uuid, 2, "battle-focus", Set.of("swordsman")));
        assertEquals(PlayerProfileDatabaseManager.QuickSlotResult.ASSIGNED,
            manager.assignQuickSkill(uuid, 1, "guard-break", Set.of("swordsman")));
        assertEquals(PlayerProfileDatabaseManager.QuickSlotResult.ASSIGNED,
            manager.assignQuickSkill(uuid, 9, "blade-flare", Set.of("swordsman")));
        assertEquals(Map.of(1, "guard-break", 9, "blade-flare"), manager.getQuickSkillSlots(uuid));
        assertTrue(manager.clearQuickSkillSlot(uuid, 1));
        assertFalse(manager.clearQuickSkillSlot(uuid, 1));
        assertEquals(Map.of(9, "blade-flare"), manager.getQuickSkillSlots(uuid));
    }

    @Test
    public void skillUpgradeSpendsPointAndIncrementsRankAtomically() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z");
        manager.learnSkill(uuid, "blade-flare", Set.of("swordsman"), 1, "learned");
        assertEquals(PlayerProfileDatabaseManager.SkillUpgradeResult.REQUIREMENTS_NOT_MET,
            manager.upgradeSkill(uuid, "blade-flare", Set.of("swordsman"), 1, 3));
        manager.startQuestProgress(uuid, "training", "started", "2026-10-21T00:00:00Z");
        manager.completeQuestAndAwardExperience(uuid, "training", "done", 100,
            "2026-10-21T00:01:00Z");

        assertEquals(PlayerProfileDatabaseManager.SkillUpgradeResult.UPGRADED,
            manager.upgradeSkill(uuid, "blade-flare", Set.of("swordsman"), 1, 3));
        assertEquals(2, manager.getLearnedSkills(uuid).getFirst().rank());
        assertEquals(0, manager.getPlayerProfile(uuid).orElseThrow().unspentSkillPoints());
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement()) {
            statement.execute("UPDATE player_profiles SET level = 3 WHERE uuid = '" + uuid + "'");
        }
        assertEquals(PlayerProfileDatabaseManager.SkillUpgradeResult.NO_SKILL_POINTS,
            manager.upgradeSkill(uuid, "blade-flare", Set.of("swordsman"), 1, 3));
        assertEquals(PlayerProfileDatabaseManager.SkillUpgradeResult.MAX_RANK,
            manager.upgradeSkill(uuid, "blade-flare", Set.of("swordsman"), 1, 2));
    }

    @Test
    public void battleFocusUpgradeRequiresNextLevelAndSpendsOneSkillPoint() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 3, 0,
            "human-realm", "2026-10-21T00:00:00Z");
        manager.learnSkill(uuid, "battle-focus", Set.of("swordsman"), 3, "learned");
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement()) {
            statement.execute("UPDATE player_profiles SET skill_points = 1 WHERE uuid = '" + uuid + "'");
        }

        assertEquals(PlayerProfileDatabaseManager.SkillUpgradeResult.REQUIREMENTS_NOT_MET,
            manager.upgradeSkill(uuid, "battle-focus", Set.of("swordsman"), 3, 5));
        assertEquals(1, manager.getPlayerProfile(uuid).orElseThrow().unspentSkillPoints());
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement()) {
            statement.execute("UPDATE player_profiles SET level = 4 WHERE uuid = '" + uuid + "'");
        }

        assertEquals(PlayerProfileDatabaseManager.SkillUpgradeResult.UPGRADED,
            manager.upgradeSkill(uuid, "battle-focus", Set.of("swordsman"), 3, 5));
        assertEquals(2, manager.getLearnedSkills(uuid).getFirst().rank());
        assertEquals(0, manager.getPlayerProfile(uuid).orElseThrow().unspentSkillPoints());
    }

    @Test
    public void manaRegeneratesOverTimeAndCannotBeOverspent() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z");
        long now = 1_000_000L;

        assertEquals(new PlayerProfileDatabaseManager.ManaState(100, 100),
            manager.getManaState(uuid, 100, 5, now).orElseThrow());
        assertEquals(PlayerProfileDatabaseManager.ManaSpendResult.SPENT,
            manager.spendMana(uuid, 40, 100, 5, now));
        assertEquals(new PlayerProfileDatabaseManager.ManaState(90, 100),
            manager.getManaState(uuid, 100, 5, now + 6_000).orElseThrow());
        assertEquals(PlayerProfileDatabaseManager.ManaSpendResult.NOT_ENOUGH_MANA,
            manager.spendMana(uuid, 101, 100, 5, now + 6_000));
        assertEquals(new PlayerProfileDatabaseManager.ManaState(90, 100),
            manager.getManaState(uuid, 100, 0, now + 6_000).orElseThrow());
    }

    @Test
    public void manaRestoreIsTransactionalAndCannotExceedMaximum() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        manager.createPlayerProfile(uuid, "Ares", "human", "mage", 1, 0,
            "human-realm", "created");
        long now = System.currentTimeMillis();
        manager.getManaState(uuid, 100, 0, now);
        assertEquals(PlayerProfileDatabaseManager.ManaSpendResult.SPENT,
            manager.spendMana(uuid, 90, 100, 0, now));

        PlayerProfileDatabaseManager.ManaRestoreResult partial =
            manager.restoreMana(uuid, 5, 100, 0, now).orElseThrow();
        assertEquals(5, partial.restoredAmount());
        assertEquals(new PlayerProfileDatabaseManager.ManaState(15, 100), partial.state());

        PlayerProfileDatabaseManager.ManaRestoreResult capped =
            manager.restoreMana(uuid, 100, 100, 0, now).orElseThrow();
        assertEquals(85, capped.restoredAmount());
        assertEquals(new PlayerProfileDatabaseManager.ManaState(100, 100), capped.state());
        assertTrue(manager.restoreMana("missing", 1, 100, 0, now).isEmpty());
    }

    @Test
    public void backsUpExistingStateBeforeCreatingAnotherProfile() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String firstUuid = "11111111-2222-3333-4444-555555666666";
        String secondUuid = "22222222-3333-4444-5555-666666777777";
        assertTrue(manager.createPlayerProfile(firstUuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z"));

        assertTrue(manager.createPlayerProfile(secondUuid, "Boreas", "human", "mage", 1, 0,
            "human-realm", "2026-10-21T01:00:00Z"));

        File backup = latestBackup(manager);
        assertNotNull(backup);
        try (Connection connection = open(backup);
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT COUNT(*) FROM player_profiles")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    @Test
    public void retriesBusyDatabaseAndRetainsOnlyConfiguredBackupCount() throws Exception {
        class BusyOnceManager extends PlayerProfileDatabaseManager {
            private boolean failNextBackup;
            private int backupCalls;

            private BusyOnceManager(File dataFolder) {
                super(dataFolder, 2, 1, 10);
            }

            private void failNextBackup() {
                failNextBackup = true;
            }

            @Override
            public synchronized void backupDatabase() throws IOException, SQLException {
                backupCalls++;
                if (failNextBackup) {
                    failNextBackup = false;
                    throw new SQLException("database is locked", "SQLITE_BUSY", 5);
                }
                super.backupDatabase();
            }
        }

        BusyOnceManager manager = new BusyOnceManager(temporaryFolder.newFolder("plugin-data"));
        manager.initialize();
        manager.failNextBackup();

        assertTrue(manager.createPlayerProfile("11111111-2222-3333-4444-555555666666",
            "Ares", "human", "swordsman", 1, 0, "human-realm", "2026-10-21T00:00:00Z"));
        assertEquals(3, manager.backupCalls);

        Thread.sleep(10);
        manager.backupDatabase();
        Thread.sleep(10);
        manager.backupDatabase();

        File[] backups = new File(manager.getDatabaseFile().getParentFile(), "backups")
            .listFiles((directory, name) -> name.startsWith("player_profiles.db.") && name.endsWith(".bak"));
        assertNotNull(backups);
        assertEquals(2, backups.length);
    }

    @Test
    public void rollsBackProfileWriteWhenDatabaseRejectsIt() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_profile_insert BEFORE INSERT ON player_profiles " +
                "BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        }

        assertThrows(SQLException.class, () -> manager.createPlayerProfile(
            "11111111-2222-3333-4444-555555666666", "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z"));

        assertFalse(manager.hasPlayerProfile("11111111-2222-3333-4444-555555666666"));
    }

    @Test
    public void unlocksWorldExactlyOnceWithoutCreatingDuplicateProgressRows() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z"));

        assertFalse(manager.isWorldUnlocked(uuid, "demon-realm"));
        assertTrue(manager.unlockWorld(uuid, "demon-realm"));
        assertFalse(manager.unlockWorld(uuid, "demon-realm"));
        assertTrue(manager.isWorldUnlocked(uuid, "demon-realm"));
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery(
                 "SELECT COUNT(*) FROM player_world_progress WHERE uuid = '" + uuid +
                     "' AND world_id = 'demon-realm'")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    @Test
    public void questProgressAndCompletionAreIdempotent() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z"));

        assertTrue(manager.startQuestProgress(uuid, "first-trial", "find-guide", "started"));
        assertFalse(manager.startQuestProgress(uuid, "first-trial", "find-guide", "duplicate-start"));
        assertTrue(manager.updateQuestStep(uuid, "first-trial", "speak-to-guide"));
        assertTrue(manager.completeQuest(uuid, "first-trial", "speak-to-guide", "completed"));
        assertFalse(manager.completeQuest(uuid, "first-trial", "speak-to-guide", "duplicate-completion"));
        assertFalse(manager.updateQuestStep(uuid, "first-trial", "after-completion"));

        PlayerProfileDatabaseManager.QuestProgress progress =
            manager.getQuestProgress(uuid, "first-trial").orElseThrow();
        assertEquals("completed", progress.status());
        assertEquals("speak-to-guide", progress.currentStep());
        assertEquals("started", progress.startedAt());
        assertEquals("completed", progress.completedAt());
        assertFalse(progress.rewardClaimed());
    }

    @Test
    public void questCompletionFailureRollsBackAndKeepsQuestActive() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z"));
        assertTrue(manager.startQuestProgress(uuid, "first-trial", "find-guide", "started"));
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_quest_completion BEFORE UPDATE ON player_quest_progress " +
                "WHEN NEW.status = 'completed' BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        }

        assertThrows(SQLException.class,
            () -> manager.completeQuest(uuid, "first-trial", "speak-to-guide", "completed"));

        assertEquals("active", manager.getQuestProgress(uuid, "first-trial").orElseThrow().status());
    }

    @Test
    public void questCheckpointAndExperienceRewardSurviveRestartAndAreAppliedOnce() throws Exception {
        File dataFolder = temporaryFolder.newFolder("quest-reward-data");
        PlayerProfileDatabaseManager manager = new PlayerProfileDatabaseManager(dataFolder);
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "created"));
        assertTrue(manager.startQuestProgress(uuid, "first-steps", "reach-human-realm-spawn", "started"));
        assertTrue(manager.updateQuestStep(uuid, "first-steps", "reach-human-realm-spawn"));

        PlayerProfileDatabaseManager restartedManager = new PlayerProfileDatabaseManager(dataFolder);
        restartedManager.initialize();
        assertEquals("reach-human-realm-spawn",
            restartedManager.getQuestProgress(uuid, "first-steps").orElseThrow().currentStep());
        assertTrue(restartedManager.completeQuestAndAwardExperience(
            uuid, "first-steps", "completed", 25, "completed"));
        assertFalse(restartedManager.completeQuestAndAwardExperience(
            uuid, "first-steps", "completed", 25, "duplicate"));
        assertFalse(restartedManager.claimCompletedQuestExperienceReward(
            uuid, "first-steps", 25, "duplicate-claim"));

        PlayerProfileDatabaseManager afterCompletionRestart = new PlayerProfileDatabaseManager(dataFolder);
        afterCompletionRestart.initialize();
        assertEquals(25, afterCompletionRestart.getPlayerProfile(uuid).orElseThrow().exp());
        assertEquals("completed",
            afterCompletionRestart.getQuestProgress(uuid, "first-steps").orElseThrow().status());
        assertTrue(afterCompletionRestart.getQuestProgress(uuid, "first-steps").orElseThrow().rewardClaimed());
    }

    @Test
    public void questRewardCanGrantMultipleLevelsAndSkillPointsExactlyOnce() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.configureCharacterProgression(30, 100, 50, 1);
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "created"));
        assertTrue(manager.startQuestProgress(uuid, "first-trial", "complete-trial", "started"));

        assertTrue(manager.completeQuestAndAwardExperience(uuid, "first-trial", "completed", 375, "completed"));
        assertFalse(manager.completeQuestAndAwardExperience(uuid, "first-trial", "completed", 375, "duplicate"));

        var profile = manager.getPlayerProfile(uuid).orElseThrow();
        assertEquals(3, profile.level());
        assertEquals(125, profile.exp());
        assertEquals(2, profile.unspentSkillPoints());
    }

    @Test
    public void completedQuestCanClaimAnUnclaimedExperienceRewardOnlyOnce() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "created"));
        assertTrue(manager.startQuestProgress(uuid, "first-steps", "reach-human-realm-spawn", "started"));
        assertTrue(manager.completeQuest(uuid, "first-steps", "completed", "completed"));

        assertTrue(manager.claimCompletedQuestExperienceReward(uuid, "first-steps", 25, "claimed"));
        assertFalse(manager.claimCompletedQuestExperienceReward(uuid, "first-steps", 25, "duplicate"));
        assertEquals(25, manager.getPlayerProfile(uuid).orElseThrow().exp());
        assertTrue(manager.getQuestProgress(uuid, "first-steps").orElseThrow().rewardClaimed());
    }

    @Test
    public void experienceRewardFailureRollsBackQuestCompletionAndRewardClaim() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "created"));
        assertTrue(manager.startQuestProgress(uuid, "first-steps", "reach-human-realm-spawn", "started"));
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_experience_reward BEFORE UPDATE ON player_profiles " +
                "WHEN NEW.exp > OLD.exp BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        }

        assertThrows(SQLException.class, () -> manager.completeQuestAndAwardExperience(
            uuid, "first-steps", "completed", 25, "completed"));

        assertEquals("active", manager.getQuestProgress(uuid, "first-steps").orElseThrow().status());
        assertFalse(manager.getQuestProgress(uuid, "first-steps").orElseThrow().rewardClaimed());
        assertEquals(0, manager.getPlayerProfile(uuid).orElseThrow().exp());
    }

    @Test
    public void restoresCorruptDatabaseFromLatestValidBackupAndPreservesOriginal() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z"));
        manager.backupDatabase();
        Files.writeString(manager.getDatabaseFile().toPath(), "corrupt database");

        manager.initialize();

        assertTrue(manager.hasPlayerProfile(uuid));
        assertNotNull(manager.getRecoveryMessage());
        File[] preserved = new File(manager.getDatabaseFile().getParentFile(), "backups")
            .listFiles((directory, name) -> name.contains(".corrupt."));
        assertTrue(preserved != null && preserved.length > 0);
    }

    @Test
    public void doesNotReplaceCorruptDatabaseWhenNoBackupExists() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        File databaseFile = manager.getDatabaseFile();
        Files.createDirectories(databaseFile.getParentFile().toPath());
        byte[] original = "corrupt database".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(databaseFile.toPath(), original);

        assertThrows(SQLException.class, manager::initialize);

        assertTrue(Arrays.equals(original, Files.readAllBytes(databaseFile.toPath())));
    }

    @Test
    public void migrationIsIdempotentAndExistingProfilesAreNotOverwritten() throws Exception {
        PlayerProfileDatabaseManager manager = newManager();
        manager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(manager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "2026-10-21T00:00:00Z"));

        manager.migrateIfNeeded();
        manager.initialize();

        assertEquals(PlayerProfileDatabaseManager.SCHEMA_VERSION, manager.getSchemaVersion());
        assertFalse(manager.createPlayerProfile(uuid, "Ares", "demon", "berserker", 1, 0,
            "demon-realm", "2026-10-21T01:00:00Z"));
        try (Connection connection = open(manager.getDatabaseFile());
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery(
                 "SELECT selected_race, selected_class, home_world FROM player_profiles WHERE uuid = '" + uuid + "'")) {
            assertTrue(resultSet.next());
            assertEquals("human", resultSet.getString("selected_race"));
            assertEquals("swordsman", resultSet.getString("selected_class"));
            assertEquals("human-realm", resultSet.getString("home_world"));
        }
    }

    @Test
    public void profileLoadsAfterReconnectAndDatabaseManagerRestartWithoutDuplicates() throws Exception {
        File dataFolder = temporaryFolder.newFolder("plugin-data");
        PlayerProfileDatabaseManager firstManager = new PlayerProfileDatabaseManager(dataFolder);
        firstManager.initialize();
        String uuid = "11111111-2222-3333-4444-555555666666";
        assertTrue(firstManager.createPlayerProfile(uuid, "Ares", "human", "swordsman", 1, 0,
            "human-realm", "created"));
        PlayerProfileDatabaseManager.PlayerProfile savedProfile =
            firstManager.getPlayerProfile(uuid).orElseThrow();

        assertEquals(savedProfile, firstManager.getPlayerProfile(uuid).orElseThrow());

        PlayerProfileDatabaseManager restartedManager = new PlayerProfileDatabaseManager(dataFolder);
        restartedManager.initialize();
        assertEquals(savedProfile, restartedManager.getPlayerProfile(uuid).orElseThrow());
        assertFalse(restartedManager.createPlayerProfile(uuid, "Changed", "demon", "berserker",
            99, 999, "demon-realm", "overwritten"));
        assertEquals(savedProfile, restartedManager.getPlayerProfile(uuid).orElseThrow());
        assertTrue(restartedManager.getPlayerProfile("missing-player").isEmpty());
    }

    private PlayerProfileDatabaseManager newManager() throws Exception {
        return new PlayerProfileDatabaseManager(temporaryFolder.newFolder("plugin-data"));
    }

    private void createLegacyDatabase(File databaseFile, int schemaVersion) throws Exception {
        createLegacyDatabase(databaseFile, schemaVersion, 1, 0);
    }

    private void createLegacyDatabase(File databaseFile, int schemaVersion, int level, int experience)
        throws Exception {
        Files.createDirectories(databaseFile.getParentFile().toPath());
        try (Connection connection = open(databaseFile);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("INSERT INTO meta (key, value) VALUES ('schema_version', '" + schemaVersion + "')");
            String selectedClassColumn = schemaVersion >= 2 ? "selected_class TEXT, " : "";
            String skillPointsColumn = schemaVersion >= 3 ? "skill_points INTEGER NOT NULL DEFAULT 0, " : "";
            statement.execute("CREATE TABLE player_profiles (" +
                "uuid TEXT PRIMARY KEY, player_name TEXT NOT NULL, selected_race TEXT, " +
                selectedClassColumn + "level INTEGER NOT NULL DEFAULT 1, exp INTEGER NOT NULL DEFAULT 0, " +
                skillPointsColumn + "home_world TEXT, " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, is_active INTEGER NOT NULL DEFAULT 1)");
            if (schemaVersion >= 2) {
                statement.execute("INSERT INTO player_profiles " +
                    "(uuid, player_name, selected_race, selected_class, level, exp, home_world, created_at, updated_at) " +
                    "VALUES ('legacy-player', 'Legacy', 'human', 'swordsman', " + level + ", " + experience +
                    ", 'human-realm', 'created', 'updated')");
            } else {
                statement.execute("INSERT INTO player_profiles " +
                    "(uuid, player_name, selected_race, level, exp, home_world, created_at, updated_at) " +
                    "VALUES ('legacy-player', 'Legacy', 'human', " + level + ", " + experience +
                    ", 'human-realm', 'created', 'updated')");
            }
        }
    }

    private boolean columnExists(File databaseFile, String columnName) throws Exception {
        try (Connection connection = open(databaseFile);
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("PRAGMA table_info(player_profiles)")) {
            while (resultSet.next()) {
                if (columnName.equals(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private Connection open(File databaseFile) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
    }

    private File latestBackup(PlayerProfileDatabaseManager manager) {
        File backupsDirectory = new File(manager.getDatabaseFile().getParentFile(), "backups");
        File[] backups = backupsDirectory.listFiles((directory, name) ->
            name.startsWith("player_profiles.db.") && name.endsWith(".bak"));
        if (backups == null || backups.length == 0) {
            return null;
        }
        return Arrays.stream(backups).max(java.util.Comparator.comparing(File::getName)).orElse(null);
    }
}
