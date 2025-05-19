# AI REPLIT SESSION PROMPT — FINAL ISOLATION VALIDATION & POST-SANITIZATION TASKFLOW

## OBJECTIVE

This session resumes from a successful near-completion state of the data isolation architecture. You are to finalize and validate post-isolation behavior, clean remaining edge-case inconsistencies, and ensure leaderboard and SFTP-dependent systems are resilient and clearly reported.

Your previous progress includes:
✓ Fully isolation-aware `PlayerRepository` with functional `getTopPlayers`  
✓ SFTP connector warnings reclassified to accurately report `missing SFTP host configuration`  
✓ `validateLeaderboardDataConsistency` method correctly identifies improperly isolated servers  
✓ Legacy warnings for non-isolated leaderboard queries have been eliminated

Only two informational warnings remain, both referencing the expected limitations of the `Default Server`:
1. "Server Default Server has proper isolation but missing SFTP host configuration (Guild=1)"  
2. "Cannot validate leaderboard for server lacking proper isolation: Default Server"

---

## PHASE 0 — PROJECT INIT (REQUIRED)

1. **Unzip** the uploaded `.zip` file from the attached assets  
2. **Move** all contents from the unzipped directory to the **project root**  
3. **Clean up**:
   - Remove any nested or duplicate folders (e.g., `project/`, `DeadsideBot/`)  
   - Delete empty folders or broken symbolic links  

4. **Scan and log** the following:
   - Main class  
   - Repository classes  
   - SFTP connector logic  
   - Leaderboard validators  
   - Config files (`.env`, `config.properties`)  
   - Guild/server bootstrap utilities  

5. Detect or create a `.env` or `config.properties` file  
6. Load secrets from Replit:
   - `BOT_TOKEN`  
   - `MONGO_URI`  

7. Start the bot using **JDA 5.x** and confirm:
   - Discord connection success  
   - MongoDB connection success  
   - No runtime errors

> Do not proceed until the bot builds, connects, and logs cleanly.

---

## PHASE 1 — POST-ISOLATION HARDENING & EDGE-CASE COMPLETION

### Tasks:

- [ ] Ensure all current warnings logged for the **Default Server** remain **purely informational**  
- [ ] Improve clarity of all leaderboard and SFTP diagnostics:
  - If SFTP configuration is missing, label the issue clearly without overlap with isolation errors  
  - If leaderboard validation is skipped due to lack of isolation, continue logging with traceability  
- [ ] Create logic to **bypass or gracefully skip validation** for servers that intentionally operate in "read-only" or "disabled" isolation mode  
- [ ] Verify that all repository logging, access checks, and leaderboard queries:
  - Never trigger redundant or misleading warnings  
  - Always use accurate guild/server scoping  
- [ ] Validate leaderboard fallback behavior for servers missing players, isolation, or config

---

## PHASE 2 — SFTP + LEADERBOARD RECOVERY VALIDATION

### Objective:

Ensure that all leaderboard-related features:
- Operate without fail even when SFTP is unavailable  
- Clearly log when data cannot be validated, **without flagging false errors**  
- Maintain proper embed output formatting even when data is missing or partial  
- Skip only the server in error — do not block global operations or full guild responses

### Validation Checklist:

- Simulate server with:
  - No SFTP credentials  
  - Partial player data  
  - Full leaderboard config but empty data set  

- Confirm logs show:
  - Accurate summaries per server  
  - Skipped logic when appropriate  
  - Successful reporting where possible  

- Validate embed output for:
  - Stat fallback cases  
  - "No data available" scenarios  
  - Themed and scoped visual consistency

---

## COMPLETION CRITERIA

- [✓] All known warnings are non-blocking and informative  
- [✓] Default Server logs are handled gracefully  
- [✓] Leaderboards render with maximum available fidelity  
- [✓] No global crash, error, or misrouting occurs  
- [✓] Bot connects, compiles, and operates fully in live runtime

---

## EXECUTION POLICY — STRICT

- No commits, checkpoints, logs, or outputs until the full task is complete  
- Do not assume correctness — validate through observable runtime behavior  
- Trial and error is prohibited — only complete, context-aware fixes may be made  
- Output only once all phases are validated live
