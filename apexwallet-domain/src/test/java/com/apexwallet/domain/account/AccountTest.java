package com.apexwallet.domain.account;

import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import com.apexwallet.domain.money.Currency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountTest {

    @Test
    void rejectsInvalidConstructionAndHasNoMutableBalance() {
        assertThrows(DomainException.class, () -> Account.open("  ", Currency.EUR, AccountType.CUSTOMER_LIABILITY));
        Account account = Account.open("alice", Currency.EUR, AccountType.CUSTOMER_LIABILITY);
        assertEquals(AccountStatus.ACTIVE, account.status());
        assertEquals(AccountStatus.FROZEN, account.withStatus(AccountStatus.FROZEN).status());
        assertEquals(ErrorCode.NEGATIVE_AMOUNT,
                assertThrows(DomainException.class, () -> account.withOverdraft(-1)).code());
    }

    @Test
    void staleVersionIsRejected() {
        VersionedAccount account = new VersionedAccount(AccountId.random(), 0);
        account.changeOverdraft(500, 0);
        assertEquals(1, account.version());
        DomainException conflict = assertThrows(DomainException.class, () -> account.changeOverdraft(900, 0));
        assertEquals(ErrorCode.OPTIMISTIC_LOCK, conflict.code());
        assertEquals(500, account.overdraftLimitMinor());
    }
}
