# VORA — SYSTEM FLOWS (AUTHORITATIVE VERSION)

## FLOW 1 — UPLOAD → PROCESS → PLAY (HAPPY PATH)

> **Goal:** A video is uploaded, processed, and becomes streamable with no failures.
**Actors**

1. Client (Web/Mobile)
2. Upload Service (TUS)
3. Metadata Service
4. Temporal Workflow Engine
5. Transcoding Worker (FFmpeg)
6. Object Storage (MinIO)
7. Streaming Gateway (HLS)
8. Cache (Varnish)

### Steps

1. **Client initializes upload**
    * Client requests a TUS upload session
    * Upload Service creates `upload_id`
    * Upload metadata persisted:
      ```bash
      upload_id
      status = UPLOADING
      bytes_uploaded = 0
      ```

2. **Client uploads video chunks**
    * Chunks are appended to a temporary object in MinIO
    * Upload Service tracks offset

3. **Upload completes**
    * Checksum verified
    * Temporary object finalized
    * Upload state:
      ```ini
      status = UPLOADED
      ```

4. **Video metadata created**
    * Metadata Service creates `video_id`
    * Associates:
      ```bash
      upload_id -> video_id
      video_state = CREATED
      ```

5. **Workflow started**
    * Metadata Service triggers Temporal workflow:
      ```bash
      VideoProcessingWorkflow(video_id)
      ```
    * Video state:
      ```bash
      PROCESSING
      ```

6. **Transcoding activities**
    * Workflow schedules activities:
        * Transcode 360p
        * Transcode 720p
        * Transcode 1080p
    * Each output written to MinIO

7. **HLS generation**
    * Per-rendition playlists generated
    * Master playlist generated

8. **Video becomes playable**
    * Metadata updated:
      ```ini
      video_state = READY
      ```

9. **Client plays video**
    * Client fetches master playlist
    * Segments delivered via Varnish

***

### Key properties

* Upload ≠ Video
* Video exists only after upload completes
* Workflow owns processing, not APIs

***

### FLOW 2 — FAILURE → RECOVERY (THE CORE FLOW)

> **Goal:** System remains correct under partial failure.

This flow is what makes Vora impressive.

***

### Scenario A — Upload interrupted mid-way

1. **Client starts upload**
   ```ini
   upload_state = UPLOADING
   ```

2. **Network dies at 40%**
    * Upload Service remains idle
    * Partial data preserved

3. **Client reconnects**
    * Uses same `upload_id`
    * Upload resumes from last offset

4. **Upload completes**
   ```ini
   upload_state = UPLOADED
   ```

✅ No duplicate uploads
✅ No corrupted data

***

### Scenario B — FFmpeg crashes at 67%

1. **Workflow is running**
   ```ini
   video_state = PROCESSING
   ```

2. **360p completed successfully**

3. **720p transcoding crashes**

4. **Temporal detects activity failure**
    * Retry policy triggers
    * Only **failed rendition** retried

5. **System state:**
   ```makefile
   renditions:
     360p = DONE
     720p = RETRYING
     1080p = PENDING
   ```

6. **Retry succeeds**

7. **Workflow continues**

8. **Video transitions to:**
   ```nginx
   READY
   ```

✅ No duplicate transcoding
✅ Work is not redone
✅ Exactly-once semantics via idempotency

### Scenario C — Workflow engine restarts

1. **Temporal crashes / restarts**
2. **Workflow state replayed**
3. **Completed activities are skipped**
4. **In-progress activities resumed**

✅ State recovered without manual intervention

***

### Scenario D — MinIO temporarily unavailable

* Transcoding activity fails
* Activity retries with backoff
* Workflow pauses, not corrupted

***

### Key properties

* All activities are **idempotent**
* Workflow state is **authoritative**
* Partial success is tracked explicitly

***

### FLOW 3 — PLAYBACK → CACHING → ANALYTICS

> **Goal:** High-throughput playback with observability.

***

### Actors

* Client
* Streaming Gateway
* Varnish
* MinIO
* Analytics Ingestor
* ClickHouse

***

### Steps

1. **Client requests master playlist**
    * Streaming Gateway receives request
    * Varnish cache checked

2. **Cache behavior**
    * Cache HIT → served immediately
    * Cache MISS → fetched from MinIO, cached

3. **Client requests video segments**
    * Same cache logic applies
    * Majority of traffic should be cache hits

4. **Playback events emitted**
    * Client emits:
      ```css
      play
      pause
      segment_fetched
      watch_time
      bitrate_selected
      ```

5. **Analytics ingestion**
    * Events sent asynchronously
    * Playback is never blocked

6. **Analytics storage**
    * Events stored in ClickHouse
    * Partitioned by time + video_id

7. **Analytics queries**
    * Drop-off per segment
    * Average watch time
    * Bitrate distribution

***

### Key properties

* Playback path is **read-only**
* Analytics is **event-driven**
* Cache drastically reduces origin load

***

### SYSTEM STATE MACHINES (IMPORTANT)

#### Upload State

```nginx
CREATED → UPLOADING → UPLOADED → FAILED
```

#### Video State

```nginx
CREATED
→ PROCESSING
→ PARTIALLY_READY
→ READY
→ FAILED
```

#### Rendition State

```nginx
PENDING → PROCESSING → DONE → FAILED
```