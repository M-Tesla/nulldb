# NullDB

> **No Spring. No Hibernate. No abstractions. Just bytes, pages, and raw I/O.**

NullDB is a raw, high-performance relational database storage engine built from absolute scratch in Java. No frameworks. No high-level abstractions. Just touching the metal.

It is an educational and experimental engine designed to open the black box of modern database infrastructure. It bypasses the JVM Garbage Collector, manipulates physical disk bytes directly, and handles concurrent network multiplexing using a strict binary protocol over raw TCP sockets.

---

## Architecture Pillars

This engine is sustained by five core engineering decisions:

- **Cache Line Alignment** — Memory pages are strictly fixed at 4 KB blocks to perfectly align with CPU cache lines and standard SSD block sizes, eliminating random read bottlenecks and guaranteeing predictable I/O geometry.

- **Off-Heap Memory & Zero-GC** — Total isolation from the JVM Garbage Collector. All page buffers are allocated directly at the OS level via `ByteBuffer.allocateDirect()`, preventing Stop-The-World pauses under any load profile.

- **ACID Durability via Write-Ahead Log** — A sequential, append-only WAL (`nulldb.log`) records every mutation before it touches a data page. Transactions are aggressively flushed to physical storage via `FileChannel.force()` — a direct `fsync(2)` syscall — before the network client is acknowledged. Power loss cannot produce a corrupt state.

- **Logical Deletion via Tombstone Pattern** — Physical data movement on `DELETE` is avoided entirely to prevent disk fragmentation and CPU overhead. Deleted records are marked with a sentinel Tombstone flag (`id = -1L`) and reclaimed asynchronously by the background worker.

- **Network Multiplexing via Java NIO** — A single thread handles thousands of simultaneous TCP connections using non-blocking `SocketChannel` and the `Selector` API, eliminating the thread-per-client overhead. The wire protocol is a compact binary frame — no HTTP, no JSON, no text parsing.

---

## Prerequisites

NullDB interacts closely with the operating system's file system and memory architecture.

| Requirement | Version | Notes |
|---|---|---|
| JDK | 21+ | Required for `ByteBuffer` direct allocation and NIO APIs |
| OS | Linux (kernel 5.x+) | Built and tested on **Pop!\_OS 22.04** and **Ubuntu 22.04 / 24.04** |
| Build Tool | Gradle 8+ | Wrapper included (`./gradlew`) |

> **Windows is not supported and is not recommended.**
> `FileChannel.force()` maps to `fsync(2)` on Linux. On Windows, the equivalent behavior is inconsistent, the NIO `Selector` lacks `epoll` support, and file descriptor performance degrades significantly. Use WSL2 with Ubuntu as a bare minimum; native Linux is required for any meaningful performance measurement.

---

## Getting Started

### 1. Clone the Repository

````bash
git clone https://github.com/NullPointer-Labs/nulldb.git
cd nulldb
````

### 2. Build the Engine

````bash
./gradlew clean build
````

````text
BUILD SUCCESSFUL in 578ms
3 actionable tasks: 3 executed
````

### 3. Start the Server

````bash
java -jar build/libs/nulldb-1.0-SNAPSHOT.jar
````

The engine will initialize its storage files (`nulldb.db`, `nulldb.log`), start the background checkpoint worker, and bind the TCP server on port `5432`:

````text
Jun 02, 2026 6:38:07 PM core.NullDbServer main
INFO: Booting Null_Pointer_Engine...
Jun 02, 2026 6:38:07 PM storage.DiskManager <init>
INFO: DiskManager initialized. Target file: nulldb.db
Jun 02, 2026 6:38:07 PM recovery.WalManager <init>
INFO: WalManager initialized. Target file: nulldb.log
Jun 02, 2026 6:38:07 PM core.BackgroundWorker start
INFO: Background Worker started. Automatic Checkpoint interval: 30s.
Jun 02, 2026 6:38:07 PM storage.BTreeIndex <init>
INFO: BTreeIndex initialized. Max tuples per leaf page: 63
Jun 02, 2026 6:38:07 PM network.NioSocketServer start
INFO: NullDB Engine started. Listening on TCP port: 5432
````

Every 30 seconds, the background worker wakes up and flushes all dirty pages from the off-heap buffer pool to disk:

````text
Jun 02, 2026 6:38:37 PM core.BackgroundWorker performCheckpoint
INFO: [BACKGROUND] Waking up. Starting automatic Checkpoint...
Jun 02, 2026 6:38:37 PM memory.BufferPoolManager flushAllPages
INFO: Flushing all dirty pages to disk checkpoint...
Jun 02, 2026 6:38:37 PM core.BackgroundWorker performCheckpoint
INFO: [BACKGROUND] Checkpoint finished. Flushed pages in 1ms. Going back to sleep.
````

Incoming client connections are logged as they are accepted by the NIO Selector:

````text
Jun 02, 2026 6:39:06 PM network.NioSocketServer acceptConnection
INFO: New client connection accepted from: /127.0.0.1:39268
````

---

## Client Connection & Protocol

NullDB does not speak standard SQL over HTTP. It uses a custom binary TCP protocol to minimize serialization overhead. Each request is a fixed-width binary frame:

````text
 Byte 0        Bytes 1–8     Bytes 9–56
┌────────────┬─────────────┬──────────────────────────┐
│  OPCODE    │   KEY (ID)  │   PAYLOAD (48 bytes)     │
│  (1 byte)  │  (8 bytes)  │  (zero-padded UTF-8)     │
└────────────┴─────────────┴──────────────────────────┘
  Total frame size: 57 bytes
````

To interact with the engine, use the built-in CLI client:

````bash
java -cp build/libs/nulldb-1.0-SNAPSHOT.jar client.NullDbClient
````

````text
========================================
  NullDB Interactive CLI v1.0
  Type 'help' for commands or 'exit' to quit.
========================================
nulldb>
````

### Supported Commands

The CLI translates human-readable text into the binary opcodes the engine expects:

````text
nulldb> INSERT 1001 "Alice"
[OK] Inserted → id=1001 | lsn=1 | page=0

nulldb> SELECT 1001
[OK] id=1001 | payload="Alice" | timestamp=1736952721443

nulldb> UPDATE 1001 "Alice Wonderland"
[OK] Updated in-place → id=1001 | page=0 | offset=64

nulldb> DELETE 1001
[OK] Tombstone written → id=1001 (physical data retained until compaction)

nulldb> SCAN
[SCAN] Active records: 2
  → id=1002 | payload="Bob"
  → id=1003 | payload="Carol"
[DONE]
````

### Inspecting the WAL

To observe the Write-Ahead Log at the byte level and verify Tombstone entries, LSN ordering, and durability guarantees:

````bash
hexdump -C nulldb.log
````

````text
00000000  00 00 00 00 00 00 00 01  01 00 00 00 00 41 6c 69  |.............Ali|
00000010  63 65 00 00 00 00 00 00  00 00 00 00 00 00 00 00  |ce..............|
000000d0  00 00 00 00 00 00 00 04  03 00 00 00 00 00 00 00  |................|
````

The entry at `0xd0` is a `DELETE` opcode — the Tombstone marker. The original payload bytes remain intact on the data page. The engine reads this record on recovery and marks the tuple as logically dead.

---

## Project Structure

````text
nulldb/
├── build.gradle
├── settings.gradle
├── gradlew
├── nulldb.db                              ← Data file (4 kB pages)
├── nulldb.log                             ← Write-Ahead Log (append-only)
└── src/
    └── main/
        └── java/
            ├── client/
            │   └── NullDbClient.java      ← Interactive TCP CLI client
            ├── core/
            │   ├── NullDbServer.java      ← Main entry point; wires all components
            │   └── BackgroundWorker.java  ← Scheduled checkpoint daemon thread
            ├── memory/
            │   ├── BufferPoolManager.java ← Dirty/clean page tracking, LRU eviction
            │   ├── LruReplacer.java       ← LRU page eviction policy
            │   └── Page.java              ← 4 kB off-heap page abstraction
            ├── network/
            │   ├── NioSocketServer.java   ← Single-thread NIO Selector event loop
            │   ├── OpCode.java            ← Binary opcode constants
            │   └── ProtocolParser.java    ← Frame decoder → engine dispatch
            ├── recovery/
            │   ├── LogRecord.java         ← WAL entry schema (LSN, opcode, payload)
            │   └── WalManager.java        ← Sequential log writer with fsync flush
            └── storage/
                ├── BTreeIndex.java        ← Leaf-page B-Tree: insert/delete/update/scan
                ├── DiskManager.java       ← FileChannel I/O, page r/w, fsync
                └── Tuple.java             ← Fixed 64-byte record schema
````

---

## Disclaimer

NullDB is an **architectural study in extreme-performance systems engineering**. It was built to demonstrate, from first principles, the engineering decisions that underpin commercial database engines such as PostgreSQL, InnoDB, and RocksDB.

This is **not** a production-ready database. It does not implement a SQL parser, query planner, MVCC, multi-table joins, authentication, or TLS.

**Do not use NullDB to store data you cannot afford to lose.**

Pull requests, stress tests, and server crashes are genuinely welcome — especially for:

- **Page Split implementation** — the most impactful missing architectural piece
- **WAL recovery stress tests** — simulated power loss and replay correctness
- **Compaction** — background reclamation of Tombstone-occupied space
- **jmh benchmarks** — raw `ByteBuffer` I/O throughput vs. JDBC baselines

````bash
# Break things. Learn things. Send a PR.
git clone https://github.com/NullPointer-Labs/nulldb.git
cd nulldb && ./gradlew clean build
java -jar build/libs/nulldb-1.0-SNAPSHOT.jar
````

---

<div align="center">

Built with no frameworks and zero apologies by [NullPointer Labs](https://github.com/NullPointer-Labs)

*The knowledge is in the fundamentals.*

</div>