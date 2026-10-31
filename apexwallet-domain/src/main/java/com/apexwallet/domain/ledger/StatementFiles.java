package com.apexwallet.domain.ledger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** UTF-8 statement file. Ordering is the statement's order, so a re-read is deterministic. */
public final class StatementFiles {

    private StatementFiles() {
    }

    public static void write(AccountStatement statement, Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("account=" + statement.accountId());
            writer.newLine();
            writer.write("currency=" + statement.currency().isoCode());
            writer.newLine();
            for (StatementLine line : statement.lines()) {
                writer.write(line.bookedAt()
                        + "|" + line.sequence()
                        + "|" + line.direction()
                        + "|" + line.amountMinor()
                        + "|" + line.runningBalanceMinor()
                        + "|" + line.transactionId());
                writer.newLine();
            }
            writer.write("closing=" + statement.closingBalanceMinor());
            writer.newLine();
        }
    }

    public static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
