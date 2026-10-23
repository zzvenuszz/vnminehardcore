package com.vnmine.hardcore.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.Test;

public class SkillManagerTest {
    @Test
    public void skillBooksAreUsedOnlyOnMainHandRightClick() {
        assertTrue(SkillManager.isSkillBookUseAction(EquipmentSlot.HAND, Action.RIGHT_CLICK_AIR));
        assertTrue(SkillManager.isSkillBookUseAction(EquipmentSlot.HAND, Action.RIGHT_CLICK_BLOCK));
        assertFalse(SkillManager.isSkillBookUseAction(EquipmentSlot.OFF_HAND, Action.RIGHT_CLICK_AIR));
        assertFalse(SkillManager.isSkillBookUseAction(EquipmentSlot.HAND, Action.LEFT_CLICK_AIR));
    }

    @Test
    public void quickCastOnlyTreatsQDropClickTypesAsKeyInput() {
        assertTrue(SkillManager.isQuickCastKeyClick(ClickType.DROP));
        assertTrue(SkillManager.isQuickCastKeyClick(ClickType.CONTROL_DROP));
        assertFalse(SkillManager.isQuickCastKeyClick(ClickType.LEFT));
        assertFalse(SkillManager.isQuickCastKeyClick(ClickType.RIGHT));
    }

    @Test
    public void quickBarUsesTheSingleTopRowForSlotsOneThroughNine() {
        for (int inventorySlot = 0; inventorySlot < 9; inventorySlot++) {
            assertEquals(inventorySlot + 1, SkillManager.quickBarSlot(inventorySlot));
        }
        assertEquals(-1, SkillManager.quickBarSlot(-1));
        assertEquals(-1, SkillManager.quickBarSlot(9));
    }

    @Test
    public void battleFocusAddsConfiguredBonusPerRank() {
        assertEquals(10.3, SkillManager.calculateBattleFocusDamage(10.0, 1, 0.03), 0.0001);
        assertEquals(11.5, SkillManager.calculateBattleFocusDamage(10.0, 5, 0.03), 0.0001);
    }

    @Test
    public void battleFocusLeavesDamageUnchangedWithoutRankOrValidBonus() {
        assertEquals(10.0, SkillManager.calculateBattleFocusDamage(10.0, 0, 0.03), 0.0001);
        assertEquals(10.0, SkillManager.calculateBattleFocusDamage(10.0, 1, 0.0), 0.0001);
        assertEquals(10.0, SkillManager.calculateBattleFocusDamage(10.0, 1, Double.NaN), 0.0001);
    }

    @Test
    public void fortifyReducesDamageByRankAndActiveStacks() {
        assertEquals(9.8, SkillManager.calculateFortifyDamage(10.0, 1, 1, 0.02), 0.0001);
        assertEquals(7.0, SkillManager.calculateFortifyDamage(10.0, 5, 3, 0.02), 0.0001);
    }

    @Test
    public void fortifyLeavesDamageUnchangedWithoutRankStacksOrValidReduction() {
        assertEquals(10.0, SkillManager.calculateFortifyDamage(10.0, 0, 3, 0.02), 0.0001);
        assertEquals(10.0, SkillManager.calculateFortifyDamage(10.0, 1, 0, 0.02), 0.0001);
        assertEquals(10.0, SkillManager.calculateFortifyDamage(10.0, 1, 1, Double.NaN), 0.0001);
    }

    @Test
    public void fortifyCapsDamageReductionToKeepHitsNonzero() {
        assertEquals(1.0, SkillManager.calculateFortifyDamage(10.0, 5, 3, 0.10), 0.0001);
    }

    @Test
    public void manaFlowRestoreScalesByRankAndIgnoresInvalidValues() {
        assertEquals(1, SkillManager.calculateManaFlowRestore(1, 1));
        assertEquals(5, SkillManager.calculateManaFlowRestore(5, 1));
        assertEquals(0, SkillManager.calculateManaFlowRestore(0, 1));
        assertEquals(0, SkillManager.calculateManaFlowRestore(1, 0));
    }

    @Test
    public void manaFlowRequiresAnotherSuccessfulCastInsideConfiguredWindow() {
        assertEquals(true, SkillManager.isManaFlowChain(1_000, 6_000, 5));
        assertEquals(false, SkillManager.isManaFlowChain(1_000, 6_001, 5));
        assertEquals(false, SkillManager.isManaFlowChain(0, 2_000, 5));
        assertEquals(false, SkillManager.isManaFlowChain(2_000, 1_000, 5));
    }
}
