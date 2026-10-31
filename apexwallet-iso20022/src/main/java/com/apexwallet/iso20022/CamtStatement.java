package com.apexwallet.iso20022;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A study subset of camt.053, not the official ISO XSD.
 * The parser keeps a running total. It does not retain every entry.
 */
public final class CamtStatement {

    private CamtStatement() {
    }

    public static Statement parse(String xml) throws Exception {
        return parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    public static Statement parse(InputStream xml) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader reader = factory.createXMLStreamReader(xml);
        String statementId = null;
        String current = null;
        Map<String, Long> entries = new LinkedHashMap<>();
        String entryRef = null;
        long retainedCeiling = 0;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                current = reader.getLocalName();
            } else if (event == XMLStreamConstants.CHARACTERS && current != null) {
                String text = reader.getText().trim();
                if (text.isEmpty()) {
                    continue;
                }
                if ("Id".equals(current) && statementId == null) {
                    statementId = text;
                } else if ("NtryRef".equals(current)) {
                    entryRef = text;
                } else if ("Amt".equals(current)) {
                    if (entryRef == null) {
                        throw new IllegalArgumentException("schema error: Amt without NtryRef");
                    }
                    entries.put(entryRef, Long.parseLong(text));
                    entryRef = null;
                }
                retainedCeiling = Math.max(retainedCeiling, text.length());
            }
        }
        reader.close();
        if (statementId == null || entries.isEmpty()) {
            throw new IllegalArgumentException("schema error: statement id and at least one Amt are required");
        }
        return new Statement(statementId, entries, retainedCeiling);
    }

    public static long streamSum(InputStream xml) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader reader = factory.createXMLStreamReader(xml);
        String current = null;
        long sum = 0;
        long count = 0;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                current = reader.getLocalName();
            } else if (event == XMLStreamConstants.CHARACTERS && "Amt".equals(current)) {
                String text = reader.getText().trim();
                if (!text.isEmpty()) {
                    sum += Long.parseLong(text);
                    count++;
                }
            }
        }
        reader.close();
        if (count == 0) {
            throw new IllegalArgumentException("schema error: no Amt");
        }
        return sum;
    }

    public record Statement(String id, Map<String, Long> entries, long retainedTextCeiling) {
        public Statement {
            entries = Map.copyOf(entries);
        }

        public List<String> refs() {
            return new ArrayList<>(entries.keySet());
        }
    }
}
