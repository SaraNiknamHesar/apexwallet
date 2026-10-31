package com.apexwallet.iso20022;

import java.util.LinkedHashSet;
import java.util.Set;

/** Importing the same statement id again changes nothing. */
public final class StatementImport {

    private final Set<String> imported = new LinkedHashSet<>();
    private long postedMinor;

    public boolean importStatement(CamtStatement.Statement statement) {
        if (!imported.add(statement.id())) {
            return false;
        }
        for (long amount : statement.entries().values()) {
            postedMinor += amount;
        }
        return true;
    }

    public long postedMinor() {
        return postedMinor;
    }

    public int imports() {
        return imported.size();
    }
}
