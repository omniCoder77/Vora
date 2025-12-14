# VORA — FAILURE CONTRACTS & RESILIENCE STRATEGY

> **Document Status:** RFC-004 (Draft)
> **Author:** System Architect
> **Context:** Defining the "Blast Radius" of failures.

This document defines the **SLA (Service Level Agreement)** between internal components. It answers the question: *"When Service X fails, does Service Y crash, stall, or degrade gracefully?"*

---

## 1. GLOBAL RESILIENCE PHILOSOPHY

1.  **Ingestion is durable:** Once the client gets a `200 OK` on a chunk, we never lose those bytes.
2.  **Playback is king:** If the Transcoder or Analytics stack dies, existing videos must still play.
3.  **Fail Fast:** Don't hang threads waiting for a dead database. Timeout quickly and return error.
4.  **Async Recovery:** Systems recover state automatically upon restart; no manual DB surgery required.

---

## 2. COMPONENT FAILURE CONTRACTS

### A. Metadata Service (Postgres DB)
**Scenario:** The primary PostgreSQL database becomes unreachable (connection timeout/refused).

*   **Impact on Uploads:** **BLOCKED.**
    *   New uploads cannot be initialized (cannot create `video_id`).
    *   Ongoing TUS uploads *might* continue (if TUS state is separate), but cannot be "Completed" (webhook to Metadata will fail).
*   **Impact on Playback:** **DEGRADED.**
    *   **Cache Hit:** Works (Varnish serves content).
    *   **Cache Miss:** Fails. Gateway cannot validate Video ID or authorize request.
*   **Impact on Processing:** **PAUSED.**
    *   Workflows cannot update status to `READY`.
*   **The Contract:**
    > "If Metadata DB is down, the platform is effectively Read-Only (Cached). No writes, no new sessions."

### B. Upload Service (TUS)
**Scenario:** The Service crashes or restarts during a massive file upload.

*   **Impact on User:** Minimal (Client-side retry).
    *   Upload request fails with `5xx` or socket hangup.
    *   Client (TUS-JS) automatically retries with `HEAD` request to determine last offset.
*   **Data Integrity:** **PRESERVED.**
    *   Chunks already written to MinIO are safe.
    *   Information about offset is stored in `.info` files in MinIO/Local Volume.
*   **The Contract:**
    > "We guarantee resumability. A crash is just a network interruption to the client."

### C. Temporal (Workflow Engine)
**Scenario:** The Temporal Cluster (or History Service) goes down.

*   **Impact on Uploads:** **SAFE.**
    *   Uploads complete, but the video stays in `UPLOADED` state. It does not transition to `PROCESSING`.
*   **Impact on Processing:** **STALLED.**
    *   Currently running transcodes finish, but the next step in the chain won't trigger.
*   **Impact on Playback:** **SAFE.**
    *   Existing `READY` videos play fine.
*   **Recovery:**
    *   When Temporal restarts, it reads the event history and resumes workflows exactly where they stopped.
*   **The Contract:**
    > "Processing is eventually consistent. Downtime adds latency to 'Time-to-Publish', but never corrupts state."

### D. Transcoding Worker (FFmpeg)
**Scenario:** FFmpeg segfaults, runs out of memory (OOM), or the pod crashes.

*   **Impact on Workflow:** **RETRYING.**
    *   Temporal detects the activity failure.
    *   **Retry Policy:** Exponential backoff (1s, 2s, 4s...) up to Max Attempts (e.g., 5).
*   **Impact on User:** None (Invisible delay).
*   **Edge Case (Poison Pill):**
    *   If a specific file crashes *every* worker (e.g., corrupted header), Retries exhaust.
    *   Workflow transitions to `FAILED`.
    *   User sees "Processing Failed" in dashboard.
*   **The Contract:**
    > "Workers are expendable cattle. We expect them to die. One dead worker does not stop the factory."

### E. MinIO (Object Storage)
**Scenario:** MinIO Cluster becomes Read-Only or Unreachable.

*   **Impact on Everything:** **CATASTROPHIC (System Outage).**
    *   Uploads fail (cannot write chunks).
    *   Processing fails (cannot read source/write target).
    *   Playback fails (Gateway cannot fetch segments).
*   **Mitigation:**
    *   Varnish Cache will serve popular content for `TTL` duration.
*   **The Contract:**
    > "MinIO is the Hard Dependency. If Storage is down, Vora is down."

### F. Varnish (Caching Layer)
**Scenario:** Varnish Service crashes or is bypassed.

*   **Impact on Playback:** **HIGH LATENCY / RISK OF CRASH.**
    *   Requests fall through to the Streaming Gateway.
    *   **Thundering Herd:** If traffic is high, the Gateway + MinIO will likely fall over due to load.
*   **Recovery:**
    *   Varnish restarts with an empty cold cache. Performance is slow until cache warms up.
*   **The Contract:**
    > "The system functions without Cache strictly for low-traffic scenarios. In production, Cache death is an emergency."

### G. Analytics (ClickHouse / Ingest)
**Scenario:** ClickHouse is down or the Analytics Ingest service crashes.

*   **Impact on Playback:** **NONE.**
    *   Client sends events, Ingest returns `5xx` (or drops packet).
    *   Client *must* ignore these errors. **Playback never buffers waiting for analytics.**
*   **Data Loss:**
    *   Acceptable. We lose a few minutes of watch-time stats.
*   **The Contract:**
    > "Analytics is 'Fire-and-Forget'. It is a non-critical path. Better to lose data than to stop video."

---

## 3. DEPENDENCY MATRIX

| Component    | Depends On  | Criticality  | Failure Behavior                         |
|:-------------|:------------|:-------------|:-----------------------------------------|
| **Upload**   | MinIO       | **Critical** | Returns `500` to client.                 |
| **Upload**   | Metadata DB | **High**     | Cannot start *new* sessions.             |
| **Metadata** | Postgres    | **Critical** | API goes `503`.                          |
| **Gateway**  | Metadata    | **Medium**   | Cannot authorize/locate uncached videos. |
| **Gateway**  | MinIO       | **Critical** | Cannot stream.                           |
| **Worker**   | Temporal    | **Critical** | Stops picking up tasks.                  |
| **Worker**   | MinIO       | **Critical** | Fails all tasks.                         |

---

## 4. CIRCUIT BREAKER CONFIGURATION (Resilience4j)

To enforce these contracts, we implement Circuit Breakers in the code.

1.  **Gateway $\to$ Metadata Service**
    *   If Metadata is slow (>2s) or throwing 500s:
    *   **Open Circuit:** Fail fast immediately. Don't queue requests.
    *   **Fallback:** If possible, verify JWT signature locally and try to guess MinIO path (Risky, but keeps playback alive).

2.  **Metadata $\to$ Temporal**
    *   If Temporal is down:
    *   **Fallback:** Save video state as `UPLOADED`. Run a cron job later to "Sweep" videos stuck in `UPLOADED` and trigger workflows when Temporal is back.

---

## 5. RECOVERY PROCEDURES (Runbook Snippets)

*   **Stuck "PROCESSING" Videos:**
    *   *Problem:* Temporal history corrupted or lost.
    *   *Fix:* Script finds videos in `PROCESSING` > 24 hours. Resets state to `UPLOADED` and re-triggers workflow.

*   **Orphaned MinIO Data:**
    *   *Problem:* Uploads started but never finished (Client died).
    *   *Fix:* MinIO Lifecycle Rule: Delete objects in `bucket-raw/` older than 24h if not finalized.