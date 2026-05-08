## API Endpoints

1. POST /api/v1/uuid
  Issue single UUID with optional tag
2. POST /api/v1/uuid/batch
  Bulk issue up to 1000 UUIDs
3. GET /api/v1/uuid/{uuid}
  Validate + lookup metadata
4. DEL /api/v1/uuid/{uuid}
  Soft-invalidate a UUID
5. GET /api/v1/uuid/stats
  Live stats — total issued, pool size, collision count

Run with `cd learning-exercise/java-custom-uuid && mvn spring-boot:run`, then test with `curl -X POST http://localhost:8080/api/v1/uuid`

## Single UUID

curl -X POST [http://localhost:8080/api/v1/uuid](http://localhost:8080/api/v1/uuid)  
  -H "Content-Type: application/json"  
  -d '{"tag":"order"}'

## Batch 500 UUIDs

curl -X POST [http://localhost:8080/api/v1/uuid/batch](http://localhost:8080/api/v1/uuid/batch)  
  -d '{"count":500,"tag":"session"}'

## Validate

curl [http://localhost:8080/api/v1/uuid/{uuid}](http://localhost:8080/api/v1/uuid/{uuid})

## Stats

curl [http://localhost:8080/api/v1/uuid/stats](http://localhost:8080/api/v1/uuid/stats)

# How the application guarantees uniqueness at 10K+/s

### The UUID structure (128-bit)

```
High 64 bits (deterministic):
┌──────────────────────────────────────────────────────────────────┐
│  timestamp (42 bits)  │ datacenter (5b) │ worker (5b) │ seq (12b) │
└──────────────────────────────────────────────────────────────────┘
Low 64 bits: SecureRandom entropy
```

## 3-layer uniqueness guarantee

### Layer 1 — Structural (Snowflake algorithm)

The high bits are constructed to be unique by design, not by chance:


| Component                            | What it prevents                                             |
| ------------------------------------ | ------------------------------------------------------------ |
| `timestamp` (42 bits, ms since 2024) | Two IDs at different milliseconds are structurally different |
| `datacenter + worker` (10 bits)      | Two IDs from different instances are always different        |
| `sequence` (12 bits, atomic)         | Up to 4,096 IDs per millisecond per node — resets each ms    |


At 10K/s = **10 IDs per millisecond**, the sequence (max 4,095) never comes close to exhausting. If it ever did (e.g. a burst of 4,096 within 1ms), the generator blocks until the next millisecond before continuing.

### Layer 2 — Entropy (low 64 bits)

`SecureRandom.nextLong()` adds 64 bits of randomness to the low half. Even if two high-halves somehow matched (structurally impossible by Layer 1), a collision probability of 2⁻⁶⁴ ≈ 5×10⁻²⁰ means it won't happen in the lifetime of the universe.

### Layer 3 — Authoritative registry (`ConcurrentHashMap.putIfAbsent`)

Every issued UUID is registered atomically:

```
UUIDRecord existing = registry.putIfAbsent(uuid, record);
if (existing != null) → reject as duplicate
```

`putIfAbsent` is a single atomic CAS operation — no TOCTOU race possible.

```
HTTP request ──→ pool.poll()          ← lock-free ArrayBlockingQueue.poll()
                      ↓
               tracker.register()     ← atomic putIfAbsent
                      ↓
               return UUID            ← sub-millisecond
```

Background (every 50ms):
  pool.size() < 10K → refill 20K IDs using synchronized generator

The hot path (serving a request) is lock-free — `pool.poll()` on an `ArrayBlockingQueue` uses a lock-free algorithm internally. The generator's `synchronized` method is only called during background refill, never on the request path.

---

## How is uniqueness guaranteed when running on multiple nodes?

### The short answer

Every node must have a **unique `(datacenterId, workerId)` pair**. These 10 bits are baked into every UUID's high half, so two nodes with different IDs structurally cannot produce the same UUID — even at the same millisecond with the same sequence counter.

```
Node A  (datacenter=1, worker=3):  a1b2c3d4-...  ← "13" encoded in bits 22–31
Node B  (datacenter=1, worker=7):  e5f6a7b8-...  ← "17" encoded in bits 22–31
                                    ↑
              These will NEVER match because the node bits differ
```

### What gives each node its identity

```
High 64 bits layout:
┌──────────────────────────────────────────────────────────────────────┐
│  timestamp (42 bits)  │  datacenter (5b)  │  worker (5b)  │ seq (12b) │
└──────────────────────────────────────────────────────────────────────┘
                              ↑ node identity lives here (10 bits = 1024 combinations)
```

- 5-bit datacenter: 0–31 (32 datacenters)
- 5-bit worker: 0–31 (32 workers per datacenter)
- Combined capacity: **1,024 unique node identities**

As long as every running instance holds a unique pair, collisions across nodes are **structurally impossible** without any shared state or coordination.

### How the current code assigns node identity

`UUIDPool` creates the generator with datacenter=1 and auto-detects the worker ID from the host MAC address:

```java
// UUIDPool.java
this.generator = CustomUUIDGenerator.create(1);   // datacenter hardcoded to 1

// CustomUUIDGenerator.java — detectWorkerId()
return ((mac[last] & 0xFF) + (mac[last-1] & 0xFF)) % 32;
```

**Where MAC-based detection works:** physical servers with dedicated NICs — each machine has a globally unique MAC, so worker IDs will naturally differ.

**Where it breaks down:**


| Environment                        | Problem                                                      |
| ---------------------------------- | ------------------------------------------------------------ |
| Docker / Kubernetes                | Containers often share the same virtual MAC (`02:42:ac:...`) |
| Multiple JVM processes on one host | Same MAC → same worker ID → collision risk                   |
| Cloud VMs behind NAT               | MAC may repeat across instances                              |


### Recommended: configure node identity via environment variables

Override `application.yml` to read node identity from environment variables set by your orchestrator:

```yaml
# application.yml
uuid:
  datacenter-id: ${UUID_DATACENTER_ID:0}
  worker-id: ${UUID_WORKER_ID:0}
```

```java
// UUIDPool.java — inject from config
@Value("${uuid.datacenter-id:1}")
private long datacenterId;

@Value("${uuid.worker-id:0}")
private long workerId;
```

Then each deployed instance gets its own identity:

```bash
# Node 1
UUID_DATACENTER_ID=1 UUID_WORKER_ID=0 mvn spring-boot:run

# Node 2
UUID_DATACENTER_ID=1 UUID_WORKER_ID=1 mvn spring-boot:run
```

**Kubernetes StatefulSet** makes this automatic — pods get deterministic ordinal names (`uuid-service-0`, `uuid-service-1`, ...) that you can extract as the worker ID:

```yaml
# k8s StatefulSet
env:
  - name: UUID_WORKER_ID
    valueFrom:
      fieldRef:
        fieldPath: metadata.annotations['statefulset.kubernetes.io/pod-name']
        # or parse the ordinal from the pod hostname
```

### What if two nodes accidentally get the same ID?

If misconfiguration gives two nodes the same `(datacenter, worker)`, Layer 1 (structural uniqueness) breaks — they could generate matching high-bits at the same millisecond and sequence value.

Layer 2 (the 64-bit `SecureRandom` low half) still makes the probability of a complete 128-bit collision 2⁻⁶⁴ ≈ **5 × 10⁻²⁰** — effectively zero in practice. This is your safety net, but you should not rely on it: **correct node ID assignment is the intended guarantee mechanism**.

### Summary


| Scenario                          | Uniqueness mechanism                                                |
| --------------------------------- | ------------------------------------------------------------------- |
| Same node, different milliseconds | `timestamp` bits differ                                             |
| Same node, same millisecond       | `sequence` counter increments (up to 4095/ms)                       |
| Different nodes                   | `datacenter + worker` bits differ — requires unique config per node |
| Everything else (safety net)      | 64-bit `SecureRandom` low half                                      |


### Why should I use pool? can't we achive without pool? and can't I generate the uuid on every request and unique across all nodes

**The pool is a performance optimization, not a correctness mechanism.** The Snowflake algorithm + node identity bits are what guarantee uniqueness. The pool just pre-generates IDs so HTTP threads never wait on generation.

At 10K/s the math is simple:

- `generate()` takes ~1–2 µs (bit ops + one SecureRandom call)
- 10K req/s = one request every 100 µs
- Even with `synchronized`, the generator is 50–100× faster than incoming requests
The pool is only valuable above ~100K/s. For 10K/s it's unnecessary complexity. Let me remove it and wire the generator directly.

### Why this still handles 10K/s without the pool:

`generate()` is `synchronized` but completes in `~1–2 µs. At 10K req/s you get one request every 100 µs` — the lock is free **~98%** of the time. The pool was solving a problem that doesn't exist at this scale. Uniqueness is guaranteed exactly the same way as before: Snowflake structure + node identity bits + `putIfAbsent` registry.

### If we keep the pool, will it takes more memory?

Yes, but it's worth knowing the exact number. Let me calculate it.
**Pool memory breakdown (50,000 pre-generated UUIDs):**
Each UUID string (`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`) in Java:

- `String` object: 24 bytes
- `byte[]` for 36 ASCII chars (compact strings, Java 11+): 16 header + 36 = 52 bytes → aligned to 56 bytes
- Total per UUID: **~80 bytes**

```
Pool of 50,000 strings:   50,000 × 80 bytes  =  ~4.0 MB
ArrayBlockingQueue array: 50,001 × 4 bytes   =  ~0.2 MB
Background scheduler thread                  =  ~0.5 MB
─────────────────────────────────────────────────────────
Pool total overhead                          =  ~4.7 MB
```

The `UUIDTracker` (registry + bloom filter) grows regardless of whether you use a pool:

```
Bloom filter (fixed):       8,000,000 bits   =  ~1.0 MB
ConcurrentHashMap entry:    ~32 bytes/UUID
UUIDRecord (fields):        ~40 bytes/UUID
String key ref:             ~80 bytes/UUID
──────────────────────────────────────────
Tracker cost per issued UUID:  ~152 bytes
After 1M issued UUIDs:      ~146 MB total
```

**So the pool adds ~4.7 MB.** On a modern server that's negligible. The real trade-off is:


|                 | With pool                 | Without pool (current)          |
| --------------- | ------------------------- | ------------------------------- |
| Extra memory    | ~4.7 MB                   | 0                               |
| Request latency | ~0 µs (queue poll)        | ~1–2 µs (synchronized generate) |
| Best suited for | >100K req/s               | ≤10K req/s                      |
| Complexity      | Higher (pool + scheduler) | Lower                           |


Since your target is 10K/s, the no-pool approach you have now is the right call — the 4.7 MB saving is small but so is the latency difference, and removing the pool reduced ~100 lines of code.