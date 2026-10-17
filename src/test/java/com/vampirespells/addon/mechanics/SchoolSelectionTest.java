package com.vampirespells.addon.mechanics;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchoolSelectionTest {

    private static final ResourceLocation BLOOD = ResourceLocation.parse("irons_spellbooks:blood");
    private static final ResourceLocation FIRE = ResourceLocation.parse("irons_spellbooks:fire");
    private static final ResourceLocation ICE = ResourceLocation.parse("irons_spellbooks:ice");
    private static final ResourceLocation ADDON_SCHOOL = ResourceLocation.parse("example_addon:aqua");

    @Test
    void matchesFullIdsAndBareIronsSpellsNames() {
        List<String> entries = List.of("irons_spellbooks:blood", " Fire ");

        assertTrue(SchoolSelection.contains(entries, BLOOD));
        assertTrue(SchoolSelection.contains(entries, FIRE));
        assertFalse(SchoolSelection.contains(entries, ICE));
    }

    @Test
    void resolvesBareNamesOnlyInTheIronsSpellsNamespace() {
        assertFalse(SchoolSelection.contains(List.of("aqua"), ADDON_SCHOOL));
        assertTrue(SchoolSelection.contains(List.of("example_addon:aqua"), ADDON_SCHOOL));
    }

    @Test
    void wildcardSelectsEverySchoolAndAnEmptyListSelectsNone() {
        assertTrue(SchoolSelection.contains(List.of("*"), ICE));
        assertTrue(SchoolSelection.contains(List.of(" * "), ADDON_SCHOOL));
        assertFalse(SchoolSelection.contains(List.of(), BLOOD));
        assertFalse(SchoolSelection.contains(List.of("*"), null));
        assertFalse(SchoolSelection.contains(null, BLOOD));
    }

    @Test
    void acceptsOnlyParsableSchoolEntries() {
        assertTrue(SchoolSelection.isValidEntry("blood"));
        assertTrue(SchoolSelection.isValidEntry("irons_spellbooks:eldritch"));
        assertTrue(SchoolSelection.isValidEntry("*"));
        assertFalse(SchoolSelection.isValidEntry(""));
        assertFalse(SchoolSelection.isValidEntry("blood magic"));
        assertFalse(SchoolSelection.isValidEntry("irons_spellbooks:"));
        assertFalse(SchoolSelection.isValidEntry(5));
        assertFalse(SchoolSelection.isValidEntry(null));
    }
}
