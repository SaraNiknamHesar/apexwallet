package com.apexwallet.iso20022;

import java.util.List;

public final class Pain001 {

    private Pain001() {
    }

    public static String batch(String messageId, List<String> endToEndIds, long amountMinor) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<Document><CstmrCdtTrfInitn><GrpHdr><MsgId>").append(escape(messageId)).append("</MsgId></GrpHdr>");
        for (String id : endToEndIds) {
            xml.append("<PmtInf><CdtTrfTxInf><PmtId><EndToEndId>")
                    .append(escape(id))
                    .append("</EndToEndId></PmtId><Amt><InstdAmt>")
                    .append(amountMinor)
                    .append("</InstdAmt></Amt></CdtTrfTxInf></PmtInf>");
        }
        xml.append("</CstmrCdtTrfInitn></Document>");
        return xml.toString();
    }

    public static void requireStudySchema(String xml) {
        if (!xml.contains("<MsgId>") || !xml.contains("<EndToEndId>") || !xml.contains("<InstdAmt>")) {
            throw new IllegalArgumentException("schema error: pain.001 study subset is incomplete");
        }
        if (xml.contains("<!DOCTYPE")) {
            throw new IllegalArgumentException("schema error: doctype is not allowed");
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
