package com.apexwallet.iso20022;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecureXmlTest {

    @Test
    void rejectsDoctypeAndAcceptsAPlainDocument() {
        String attack = """
                <?xml version="1.0"?>
                <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///c:/windows/win.ini">]>
                <Document>&xxe;</Document>
                """;
        assertThrows(Exception.class, () -> SecureXml.parse(attack));
        assertDoesNotThrow(() -> SecureXml.parse("<Document><Amt>10</Amt></Document>"));
    }
}
