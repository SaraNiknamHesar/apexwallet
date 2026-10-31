# ISO study mapping

This is the subset the code actually reads and writes. It is not the official ISO 20022 XSD.

| XML | Domain | Transform | Test |
|---|---|---|---|
| Stmt/Id | statement id | trim, used as the import key | second import is a no-op |
| Ntry/NtryRef | entry reference | required before Amt | missing Amt fails |
| Ntry/Amt | minor units | parsed as a long, not a decimal | sum of a valid file |
| GrpHdr/MsgId | pain.001 batch id | escaped text | requireStudySchema |
| EndToEndId | client reference | escaped text | pain001 test |
| InstdAmt | minor units | written as a long | pain001 test |
| IBAN | account number | ISO 13616 mod 97 | GB82 example and a transposed digit |

Ignored on purpose: party names, addresses, charges, and purpose codes. They are not in the domain model yet. A DOCTYPE is rejected.
