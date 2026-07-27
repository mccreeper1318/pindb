package org.pindb.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void readsQuotedCsvValues() throws Exception {
        Path csv = tempDirectory.resolve("quoted.csv");
        Files.writeString(csv, "Name,Notes\nAlex,\"Hello, world\"\nSam,\"Line 1\nLine 2\"\n");
        List<List<String>> rows = CsvService.readCsv(csv);
        assertEquals("Hello, world", rows.get(1).get(1));
        assertEquals("Line 1\nLine 2", rows.get(2).get(1));
    }
}
