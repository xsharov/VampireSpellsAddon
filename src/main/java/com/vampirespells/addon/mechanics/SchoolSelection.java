package com.vampirespells.addon.mechanics;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

public final class SchoolSelection {

    public static final String ALL_SCHOOLS = "*";

    private static final String IRONS_SPELLS_NAMESPACE = "irons_spellbooks";

    private SchoolSelection() {
    }

    public static boolean isValidEntry(Object entry) {
        return entry instanceof String text && (isWildcard(text) || parseSchoolId(text) != null);
    }

    public static boolean contains(List<? extends String> entries, ResourceLocation schoolId) {
        if (entries == null || schoolId == null) {
            return false;
        }
        for (String entry : entries) {
            if (entry != null && (isWildcard(entry) || schoolId.equals(parseSchoolId(entry)))) {
                return true;
            }
        }
        return false;
    }

    static ResourceLocation parseSchoolId(String entry) {
        String id = entry.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            return null;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(
                id.indexOf(':') < 0 ? IRONS_SPELLS_NAMESPACE + ":" + id : id
        );
        return parsed == null || parsed.getPath().isEmpty() ? null : parsed;
    }

    private static boolean isWildcard(String entry) {
        return ALL_SCHOOLS.equals(entry.trim());
    }
}
