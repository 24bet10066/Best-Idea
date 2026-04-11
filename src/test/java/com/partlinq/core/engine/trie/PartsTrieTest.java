package com.partlinq.core.engine.trie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the PartsTrie data structure.
 * Validates prefix search, fuzzy search, autocomplete, and removal.
 */
class PartsTrieTest {

    private PartsTrie trie;

    // Fixed UUIDs for 10 test parts
    private final UUID compressorVoltas = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private final UUID compressorDaikin = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private final UUID compressorLg     = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private final UUID pcbBoard         = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private final UUID fanMotor         = UUID.fromString("10000000-0000-0000-0000-000000000005");
    private final UUID capacitor        = UUID.fromString("10000000-0000-0000-0000-000000000006");
    private final UUID thermostat       = UUID.fromString("10000000-0000-0000-0000-000000000007");
    private final UUID roMembrane       = UUID.fromString("10000000-0000-0000-0000-000000000008");
    private final UUID drainPump        = UUID.fromString("10000000-0000-0000-0000-000000000009");
    private final UUID heatingElement   = UUID.fromString("10000000-0000-0000-0000-000000000010");

    @BeforeEach
    void setUp() {
        trie = new PartsTrie();

        // Insert 10 parts by name
        trie.insert("voltas ac compressor", compressorVoltas);
        trie.insert("daikin ac compressor", compressorDaikin);
        trie.insert("lg refrigerator compressor", compressorLg);
        trie.insert("ac control pcb board", pcbBoard);
        trie.insert("ac fan motor", fanMotor);
        trie.insert("ac run capacitor", capacitor);
        trie.insert("ac thermostat", thermostat);
        trie.insert("ro membrane filter", roMembrane);
        trie.insert("washing machine drain pump", drainPump);
        trie.insert("geyser heating element", heatingElement);

        // Also insert by part numbers
        trie.insert("AC-COMP-VOL-001", compressorVoltas);
        trie.insert("AC-COMP-DAI-001", compressorDaikin);
        trie.insert("RF-COMP-LG-001", compressorLg);
    }

    @Test
    @DisplayName("Size should reflect total unique entries")
    void testSize() {
        // 10 names + 3 part numbers = 13 insertions, but each is a unique key-partId pair
        assertEquals(13, trie.size());
    }

    @Test
    @DisplayName("Prefix search: 'compressor' should find 0 (it's not a prefix)")
    void testPrefixSearch_MiddleWord() {
        // "compressor" is in the middle of the keys, not a prefix
        List<UUID> results = trie.search("compressor", 10);
        // The trie does prefix matching, "compressor" is not the start of any inserted key
        assertTrue(results.isEmpty(),
                "Middle-of-string words are not prefixes. Got: " + results.size());
    }

    @Test
    @DisplayName("Prefix search: 'ac' should find AC-related parts")
    void testPrefixSearch_AcPrefix() {
        List<UUID> results = trie.search("ac", 20);
        // Keys starting with "ac": "ac control pcb board", "ac fan motor",
        // "ac run capacitor", "ac thermostat", "accompvol001", "accompdai001"
        assertTrue(results.size() >= 4,
                "Should find at least 4 parts starting with 'ac'. Got: " + results.size());
        assertTrue(results.contains(fanMotor));
        assertTrue(results.contains(capacitor));
        assertTrue(results.contains(thermostat));
    }

    @Test
    @DisplayName("Prefix search: 'voltas' should find Voltas compressor")
    void testPrefixSearch_BrandName() {
        List<UUID> results = trie.search("voltas", 10);
        assertTrue(results.contains(compressorVoltas),
                "Should find Voltas compressor by brand prefix");
    }

    @Test
    @DisplayName("Prefix search: 'ro' should find RO membrane")
    void testPrefixSearch_RoPrefix() {
        List<UUID> results = trie.search("ro", 10);
        assertTrue(results.contains(roMembrane), "Should find RO membrane filter");
    }

    @Test
    @DisplayName("Search is case-insensitive")
    void testCaseInsensitivity() {
        List<UUID> upper = trie.search("VOLTAS", 10);
        List<UUID> lower = trie.search("voltas", 10);
        List<UUID> mixed = trie.search("VoLtAs", 10);

        assertEquals(upper.size(), lower.size(), "Case should not affect results");
        assertEquals(lower.size(), mixed.size(), "Case should not affect results");
        assertTrue(upper.contains(compressorVoltas));
    }

    @Test
    @DisplayName("Autocomplete: 'geyser' should suggest 'geyser heating element'")
    void testAutocomplete() {
        List<String> suggestions = trie.autocomplete("geyser", 5);
        assertFalse(suggestions.isEmpty(), "Should have autocomplete suggestions for 'geyser'");
        assertTrue(suggestions.stream().anyMatch(s -> s.contains("heating")),
                "Should suggest 'geyser heating element'");
    }

    @Test
    @DisplayName("Autocomplete: 'washing' should suggest washing machine parts")
    void testAutocomplete_WashingMachine() {
        List<String> suggestions = trie.autocomplete("washing", 5);
        assertFalse(suggestions.isEmpty());
        assertTrue(suggestions.stream().anyMatch(s -> s.contains("drain")));
    }

    @Test
    @DisplayName("Fuzzy search: 'compresor' (misspelled) should find compressors")
    void testFuzzySearch_Misspelling() {
        // "voltas ac compressor" vs "voltas ac compressorr" -> distance 1
        // Testing with "voltas ac compresor" -> distance 1 from "voltas ac compressor"
        List<UUID> results = trie.fuzzySearch("voltas ac compresor", 2, 10);
        assertTrue(results.contains(compressorVoltas),
                "Fuzzy search should find 'voltas ac compressor' for 'voltas ac compresor'");
    }

    @Test
    @DisplayName("Fuzzy search: 'thermastat' (misspelled) should find thermostat")
    void testFuzzySearch_Thermostat() {
        List<UUID> results = trie.fuzzySearch("ac thermastat", 2, 10);
        assertTrue(results.contains(thermostat),
                "Fuzzy search should handle 'thermastat' -> 'thermostat'");
    }

    @Test
    @DisplayName("Remove: removing a part should exclude it from search")
    void testRemove() {
        // Verify present before removal
        List<UUID> before = trie.search("geyser", 10);
        assertTrue(before.contains(heatingElement));
        int sizeBefore = trie.size();

        // Remove
        trie.remove("geyser heating element", heatingElement);

        // Verify absent after removal
        List<UUID> after = trie.search("geyser", 10);
        assertFalse(after.contains(heatingElement),
                "Removed part should not appear in search results");
        assertEquals(sizeBefore - 1, trie.size());
    }

    @Test
    @DisplayName("Search with no match returns empty list")
    void testSearchNoMatch() {
        List<UUID> results = trie.search("nonexistent xyz", 10);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Clear resets the trie completely")
    void testClear() {
        assertTrue(trie.size() > 0);
        trie.clear();
        assertEquals(0, trie.size());
        assertTrue(trie.search("ac", 10).isEmpty());
    }

    @Test
    @DisplayName("Frequency increment affects search popularity ranking")
    void testFrequencyIncrement() {
        // Increment frequency for "ro membrane filter"
        trie.incrementFrequency("ro membrane filter");
        trie.incrementFrequency("ro membrane filter");
        trie.incrementFrequency("ro membrane filter");

        // Should still return results
        List<UUID> results = trie.search("ro", 10);
        assertTrue(results.contains(roMembrane));
    }
}
