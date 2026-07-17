package com.vampirespells.addon.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RayLocalizationTest {

    private static final String GUIDE_KEY = "spell.irons_spellbooks.ray_of_siphoning.guide";
    private static final Path LANG_DIRECTORY = Path.of(
            "src", "main", "resources", "assets", "irons_spellbooks", "lang"
    );
    private static final Map<String, SemanticMarkers> EXPECTED_LOCALES = Map.ofEntries(
            Map.entry("en_us", new SemanticMarkers("night", "hunger")),
            Map.entry("es_es", new SemanticMarkers("noche", "hambre")),
            Map.entry("es_mx", new SemanticMarkers("noche", "hambre")),
            Map.entry("fr_fr", new SemanticMarkers("nuit", "faim")),
            Map.entry("it_it", new SemanticMarkers("notte", "fame")),
            Map.entry("ja_jp", new SemanticMarkers("夜", "飢え")),
            Map.entry("ko_kr", new SemanticMarkers("밤", "굶주림")),
            Map.entry("pl_pl", new SemanticMarkers("nocy", "głód")),
            Map.entry("pt_br", new SemanticMarkers("noite", "fome")),
            Map.entry("ru_ru", new SemanticMarkers("ночи", "голод")),
            Map.entry("uk_ua", new SemanticMarkers("ночі", "голод")),
            Map.entry("vi_vn", new SemanticMarkers("màn đêm", "cơn đói")),
            Map.entry("zh_cn", new SemanticMarkers("夜", "饥渴")),
            Map.entry("zh_hk", new SemanticMarkers("夜", "飢渴")),
            Map.entry("zh_tw", new SemanticMarkers("夜", "飢渴"))
    );

    @Test
    void providesValidMinimalGuideOverridesForEverySupportedLocale() throws IOException {
        assertTrue(Files.isDirectory(LANG_DIRECTORY), "Iron's Spells language directory is missing");

        Set<String> actualLocales;
        try (Stream<Path> files = Files.list(LANG_DIRECTORY)) {
            actualLocales = files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .collect(Collectors.toUnmodifiableSet());
        }
        assertEquals(EXPECTED_LOCALES.keySet(), actualLocales);

        for (Map.Entry<String, SemanticMarkers> entry : EXPECTED_LOCALES.entrySet()) {
            Path languageFile = LANG_DIRECTORY.resolve(entry.getKey() + ".json");
            JsonElement parsed;
            try (Reader reader = Files.newBufferedReader(languageFile, StandardCharsets.UTF_8)) {
                parsed = JsonParser.parseReader(reader);
            }

            assertTrue(parsed.isJsonObject(), () -> languageFile + " must contain a JSON object");
            JsonObject translations = parsed.getAsJsonObject();
            assertEquals(Set.of(GUIDE_KEY), translations.keySet(),
                    () -> languageFile + " must contain only the Ray guide override");
            assertTrue(translations.get(GUIDE_KEY).isJsonPrimitive(),
                    () -> languageFile + " guide must be a string");

            String guide = translations.get(GUIDE_KEY).getAsString();
            assertFalse(guide.isBlank(), () -> languageFile + " guide must not be blank");
            String normalized = guide.toLowerCase(Locale.ROOT);
            SemanticMarkers markers = entry.getValue();
            assertTrue(normalized.contains(markers.night()),
                    () -> languageFile + " is missing the localized night-creatures meaning");
            assertTrue(normalized.contains(markers.hunger()),
                    () -> languageFile + " is missing the localized hunger meaning");
        }
    }

    private record SemanticMarkers(String night, String hunger) {
    }
}
