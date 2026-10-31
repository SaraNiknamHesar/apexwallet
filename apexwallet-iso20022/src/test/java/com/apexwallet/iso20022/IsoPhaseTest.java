package com.apexwallet.iso20022;

import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsoPhaseTest {

    @Test
    void ibanAcceptsTheKnownExampleAndRejectsATransposition() {
        assertTrue(IbanCheck.valid("GB82WEST12345698765432"));
        assertFalse(IbanCheck.valid("GB82WEST12345698765433"));
        assertEquals("GB82WEST12345698765432", IbanCheck.generate("GB", "WEST12345698765432"));
        assertFalse(IbanCheck.valid("GB82WEST1234569876543"));
    }

    @Test
    void camtParsesAValidStatementAndRejectsABrokenOneAndXxe() throws Exception {
        CamtStatement.Statement statement = CamtStatement.parse("""
                <Document><BkToCstmrStmt><Stmt><Id>STMT-1</Id>
                <Ntry><NtryRef>E1</NtryRef><Amt>10</Amt></Ntry>
                <Ntry><NtryRef>E2</NtryRef><Amt>5</Amt></Ntry>
                </Stmt></BkToCstmrStmt></Document>
                """);
        assertEquals(15, statement.entries().values().stream().mapToLong(Long::longValue).sum());
        assertThrows(IllegalArgumentException.class, () -> CamtStatement.parse("<Document><Stmt><Id>X</Id></Stmt></Document>"));
        assertThrows(Exception.class, () -> CamtStatement.parse("<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///c:/windows/win.ini\">]><Document>&xxe;</Document>"));
    }

    @Test
    void theSameStatementImportsOnce() throws Exception {
        CamtStatement.Statement statement = CamtStatement.parse("""
                <Document><Stmt><Id>STMT-9</Id><Ntry><NtryRef>E1</NtryRef><Amt>7</Amt></Ntry></Stmt></Document>
                """);
        StatementImport imports = new StatementImport();
        assertTrue(imports.importStatement(statement));
        assertFalse(imports.importStatement(statement));
        assertEquals(7, imports.postedMinor());
        assertEquals(1, imports.imports());
    }

    @Test
    void pain001StudySubsetValidates() {
        String xml = Pain001.batch("MSG-1", List.of("E2E-1", "E2E-2"), 100);
        Pain001.requireStudySchema(xml);
        assertThrows(IllegalArgumentException.class, () -> Pain001.requireStudySchema("<Document></Document>"));
    }

    @Test
    void aHundredMegabyteStatementIsSummedWithoutRetainingEntries() throws Exception {
        Path file = Files.createTempFile("camt", ".xml");
        long entries = 0;
        byte[] header = "<Document><Stmt><Id>BIG</Id>".getBytes(StandardCharsets.UTF_8);
        byte[] entry = "<Ntry><NtryRef>R</NtryRef><Amt>1</Amt></Ntry>".getBytes(StandardCharsets.UTF_8);
        byte[] footer = "</Stmt></Document>".getBytes(StandardCharsets.UTF_8);
        long written = header.length;
        try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(file))) {
            out.write(header);
            long target = 100L * 1024 * 1024;
            while (written < target) {
                out.write(entry);
                written += entry.length;
                entries++;
            }
            out.write(footer);
        }
        assertTrue(Files.size(file) >= 100L * 1024 * 1024);
        try (InputStream in = Files.newInputStream(file)) {
            assertEquals(entries, CamtStatement.streamSum(in));
        }
        Files.deleteIfExists(file);
    }
}
