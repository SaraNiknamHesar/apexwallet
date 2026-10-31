# Study SLO

These numbers are a study target for the local demo, not a production commitment.

- Availability: the local process answers `/actuator/health` when it is up.
- Latency: under the in-process fee benchmark, p99 stays within the same order of magnitude as p50. The test is `LaterPhaseTest.feeBenchmarkReportsPercentilesAfterWarmup`.
- Correctness: reconciliation mismatches stay at zero after a normal run. One planted mismatch must be reported.
- Saturation: the Postgres pool is 10 connections with a 3 second wait. A pool of 1 fails the next borrower with "connection is not available".

Load scenario: `mvn -pl apexwallet-app -am test` runs 500 concurrent ledger transfers on Postgres when Docker is running. That is the reproducible script.
