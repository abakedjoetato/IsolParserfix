# AI REPLIT SESSION PROMPT — ISOLATION CONTINUATION & COMPLETION SEQUENCE

## OBJECTIVE

This session resumes from a previously interrupted full-system data isolation integration. The previous agent session successfully:

- Enhanced isolation-aware repositories:
  - FactionRepository  
  - LinkedPlayerRepository  
  - GameServerRepository  
- Implemented GuildIsolationManager with FilterContext  
- Built DataBoundary utility for unified filtering  
- Created DataCleanupTool for orphaned record removal  
- Added IsolationBootstrap for startup scoping enforcement  
- Built initial isolation-aware admin commands  

You must continue this work from where it left off, with no assumptions or trial-and-error. There is no retained memory—treat this as a fresh environment but **pick up exactly where the task was interrupted**.

---

## PHASE 0 — PROJECT INIT (REQUIRED)

1. **Unzip** the uploaded `.zip` file from the attached assets  
2. **Move** all contents from the unzipped directory to the **project root**  
3. **Clean up**:
   - Remove any nested or duplicate folders (e.g., `project/`, `DeadsideBot/`)  
   - Delete empty folders or broken symbolic links  

4. **Scan and log** the following:
   - Main class (`main()` entrypoint)  
   - All existing repository classes  
   - Isolation-related utilities and managers  
   - Config files (`.env` or `config.properties`)  
   - All command files tied to isolation logic  

5. Detect or create a `.env` or `config.properties` file  

6. Load secrets from Replit:
   - `BOT_TOKEN`  
   - `MONGO_URI`  

7. Start the bot using **JDA 5.x** and confirm:
   - Discord connection success  
   - MongoDB connection success  
   - No runtime errors

> If Phase 0 fails, halt and log startup error context.

---

## PHASE 1 — ISOLATION LAYER COMPLETION

### Task:

You must complete the isolation refactor for the following components, continuing the design pattern already implemented:

- [ ] `PlayerRepository`  
- [ ] `AlertRepository`  
- [ ] `BountyRepository`  
- [ ] `CurrencyRepository`  

Each of these must include:

- Full guild/server ID scoping  
- Isolation-aware fetch, store, delete, and update methods  
- Support for `FilterContext`, `DataBoundary`, and `GuildIsolationManager`  
- Defensive coding: no operation may proceed without validated isolation guards  
- Inline documentation for each method’s isolation logic

---

## PHASE 2 — COMMAND AND SYSTEM VERIFICATION

- [ ] Verify that all admin and public commands call updated repository methods  
- [ ] Review `/link`, `/faction`, `/server`, `/bounty`, `/currency`, `/alert`, and `/stats`  
- [ ] Confirm that **every command execution**:
  - Filters by current guild and game server  
  - Pulls from isolated collections  
  - Cannot impact unrelated guilds or servers  
- [ ] Check embed outputs and messages to ensure reflected data is scoped

---

## VALIDATION CRITERIA

This session is only complete when:

- [✓] All repositories are implemented with full isolation compliance  
- [✓] All commands route through these repositories correctly  
- [✓] No shared, global, or unscoped data leaks are possible  
- [✓] Startup, runtime, and MongoDB writes are clean and contextually validated  
- [✓] All tests can confirm data boundaries are functioning live

---

## EXECUTION POLICY — STRICT

- All work must be done in a single, uninterrupted batch  
- No speculative commits, checkpoints, logs, or assumptions allowed  
- Do not output results or partial code until all validation criteria are passed  
- Trial and error is prohibited  
- This task is only complete once all isolation paths are hardened and functional
