# Spec: DAVy sync-path efficiency and correctness audit

Status: proposed
Target: `com.davy` 1.1.4 (versionCode 13), branch `main` at 304fdd4
Baseline measurement: 08 September 2026, Pixel 7 Pro (cheetah), Android 17

## 1. Why

DAVy is one of the heaviest mobile-radio consumers on the test device despite being a
sync client with a single Nextcloud account. Almost all of its battery cost is radio
time spent re-downloading data that has not changed.

### Measured baseline

All figures from `adb shell dumpsys batterystats --charged com.davy`, taken before any
reinstall, covering 10h 44m on battery starting 08 September 2026 06:44.

| Metric | Value |
|---|---|
| Estimated power use, `com.davy` (uid u0a632) | 95.7 mAh |
| Device computed drain, same window | 1730 mAh |
| DAVy share of device drain | 5.5 % |
| Of DAVy's 95.7 mAh: mobile radio | 92.7 mAh |
| Of DAVy's 95.7 mAh: CPU | 0.397 mAh |
| Of DAVy's 95.7 mAh: wakelocks | 2.36 mAh |
| Mobile received / sent | 10.69 MB / 1.21 MB (24,308 packets) |
| Wi-Fi received / sent | 2.57 MB / 229.78 KB |
| Mobile radio active | 23m 18s, 33 wakeups |
| Sync job launches | 16 (26 successful finishes, 2 canceled) |
| Mean bytes received per job launch | ~668 KB |

Account under test: one Nextcloud account `Rog@cloud.it-baer.net`, one calendar and
three address books. `adb shell content query` reports **157 raw contacts** with
`account_type=com.davy.addressbook`.

The app is on the battery-optimization allowlist (`dumpsys deviceidle whitelist` lists
`com.davy`; `am get-standby-bucket com.davy` returns 5, EXEMPTED), so Doze does not
throttle it. Nothing external limits the hourly wakeups.

For a DAV client whose collections did not change, a sync should cost a few KB. 668 KB
per run is roughly two orders of magnitude above that.

## 2. Findings

Each finding was read in the source at the stated location. F1 through F6 are confirmed
by code inspection, and the battery figures that motivate them are measured. The
attribution of the 668 KB specifically to F1 and F2 is inference consistent with the
measurement, not a per-request capture: the release build strips Timber logging, and a
forced job run produced no app-level log output.

### F1 - Contact counting downloads every vCard (critical)

`getServerContactCount()` at
[CardDAVSyncService.kt:425](../app/src/main/java/com/davy/data/sync/CardDAVSyncService.kt#L425)
builds its `addressbook-query` REPORT with `createQueryAllRequest()`
([line 432](../app/src/main/java/com/davy/data/sync/CardDAVSyncService.kt#L432)). That
request body asks for `<c:address-data/>`, meaning the complete vCard of every contact
in the collection. The function then uses only `.size` of the parsed result and
discards everything else.

It is called at
[line 258](../app/src/main/java/com/davy/data/sync/CardDAVSyncService.kt#L258), guarded
by `if (!hasChanged && hasLocalContacts)` - that is, it runs **precisely when the ctag
says nothing changed**, which is the normal case on almost every sync.

### F2 - Deletion detection downloads every vCard (critical)

`deleteRemovedContacts()` at
[CardDAVSyncService.kt:1001](../app/src/main/java/com/davy/data/sync/CardDAVSyncService.kt#L1001)
issues the same `createQueryAllRequest()` REPORT at
[line 1119](../app/src/main/java/com/davy/data/sync/CardDAVSyncService.kt#L1119) in
order to compare server filenames (SOURCE_IDs) against local ones. It uses the hrefs
and ignores the vCard bodies.

It is called unconditionally at
[line 281](../app/src/main/java/com/davy/data/sync/CardDAVSyncService.kt#L281), as
Step 3, before the ctag download gate and entirely independent of it.

**Combined effect of F1 and F2:** every contacts sync downloads each address book in
full twice, even when the ctag proves nothing changed. With three address books that is
six full collection downloads per hour. The ctag check at
[line 253](../app/src/main/java/com/davy/data/sync/CardDAVSyncService.kt#L253) saves
nothing in practice.

### F3 - The correct request already exists and is never used

`AddressBookQuery.createETagOnlyRequest()` at
[AddressBookQuery.kt:87](../app/src/main/java/com/davy/data/remote/carddav/AddressBookQuery.kt#L87)
requests `<d:getetag/>` only. `grep -rn createETagOnlyRequest app/src` returns exactly
one line, the definition. It has no callers.

### F4 - The WebCal job runs a full CalDAV sync as a side effect (high)

[SyncManager.kt:114](../app/src/main/java/com/davy/sync/SyncManager.kt#L114) maps the
`"webcal"` service type to `INPUT_SYNC_TYPE = "calendar"` plus `forceWebCal = true`. In
`SyncWorker.syncCalendars()`, `calDAVSyncService.syncAccount(account)` is called
unconditionally at
[SyncWorker.kt:288](../app/src/main/java/com/davy/sync/SyncWorker.kt#L288); the
`forceWebCal` branch at
[SyncWorker.kt:403](../app/src/main/java/com/davy/sync/SyncWorker.kt#L403) only *adds*
the WebCal subscription pass on top.

`buildSyncSignature()` at
[SyncWorker.kt:150](../app/src/main/java/com/davy/sync/SyncWorker.kt#L150) includes
`forceWebCal`, so the calendar job and the webcal job produce different signatures and
do not deduplicate against each other.

Result: with the default `calendar_sync_interval` and `webcal_sync_interval` of 60 each
(`AccountSyncConfigurationManager`), the full CalDAV account sync runs twice per hour.
`dumpsys jobscheduler` confirms three distinct periodic jobs for uid u0a632 (#2194,
#2202, #2207), each with a ~60 minute minimum latency, which matches the 33 measured
radio wakeups.

### F5 - Concurrent sync requests are discarded, not deferred (correctness)

[SyncWorker.kt:79-82](../app/src/main/java/com/davy/sync/SyncWorker.kt#L79) drops a
colliding invocation and returns `Result.success()`. The work is not retried and not
queued.

`adb shell dumpsys content` shows this happening to real user edits: the address book
account `Kontakte (Rog@cloud.it-baer.net) #1` recorded
`already-in-progress Source=LOCAL Extras=[upload=true]` at 2026-09-05 23:48:13 and
23:38:10. Those are local contact changes whose upload sync was thrown away. The same
dump shows failure counters of 10, 20 and 28 across the three address book accounts.
**These are not server errors.** They are self-inflicted lock collisions, and F4 makes
them more likely by running two overlapping calendar syncs every hour.

### F6 - WebCal ignores its own refresh interval (minor)

`syncAccountSubscriptions(accountId, force = true)` at
[WebCalSyncService.kt:274](../app/src/main/java/com/davy/sync/webcal/WebCalSyncService.kt#L274)
selects `getSyncEnabledByAccountId()` instead of
`getAccountSubscriptionsNeedingRefresh()` when `force` is set, and the webcal job always
passes `force = true`. Every subscription is therefore polled hourly regardless of its
configured refresh interval.

Conditional GET itself is implemented correctly: `syncSubscription()` sends
`If-None-Match` when an ETag is stored
([WebCalSyncService.kt:68](../app/src/main/java/com/davy/sync/webcal/WebCalSyncService.kt#L68)),
so the cost per skipped poll is a 304 response. Low priority.

### F7 - CalDAV is correct (no action)

`CalDAVSyncService` uses a WebDAV `sync-collection` REPORT with a stored sync-token
([CalDAVSyncService.kt:241](../app/src/main/java/com/davy/data/sync/CalDAVSyncService.kt#L241))
and only falls back to a full PROPFIND when `lastSyncedAt` is older than 7 days. This is
the correct pattern and is left alone.

## 3. Decisions

Confirmed with the maintainer before writing this spec:

- **D1** - Keep the contact-count safety net, but build it from an ETag-only request.
  The net exists because a Nextcloud ctag update can lag behind a change; removing it
  entirely would risk missing contacts until the next unrelated change.
- **D2** - Keep deletion detection running on every sync, not gated by the ctag, but
  ETag-only. A deletion whose ctag update is delayed must still be caught.
- **D3** - D1 and D2 consume **one shared** ETag-only REPORT per address book per sync,
  not two.
- **D4** - Done means both a MockWebServer unit test and a fresh on-device batterystats
  measurement.

## 4. Work items

### W1 - One shared ETag-only server listing for CardDAV

**File:** `app/src/main/java/com/davy/data/sync/CardDAVSyncService.kt`

Add one private suspend function that performs a single `addressbook-query` REPORT built
from `AddressBookQuery.createETagOnlyRequest()` (F3) and returns the parsed server
entries as href/ETag pairs. It must carry the same `Depth: 1`, content type and Basic
auth headers as the existing calls, and the same 401/403 notification handling that
`downloadContacts()` performs at
[CardDAVSyncService.kt:477](../app/src/main/java/com/davy/data/sync/CardDAVSyncService.kt#L477),
including the Cloudflare-block detection added in c61755e.

Call it once per address book per sync, before Step 3. Pass the result into both
consumers.

**Acceptance:** exactly one REPORT is issued per address book per sync in the no-change
case, and its body contains `<d:getetag/>` and does not contain `<c:address-data/>`.

### W2 - Rewire the two consumers

**File:** same.

- `getServerContactCount()`
  ([line 425](../app/src/main/java/com/davy/data/sync/CardDAVSyncService.kt#L425)) loses
  its own HTTP call and becomes a count over the shared listing, or is removed and its
  call site at
  [line 258](../app/src/main/java/com/davy/data/sync/CardDAVSyncService.kt#L258) reads
  the size directly. The count-mismatch behavior at
  [lines 259-262](../app/src/main/java/com/davy/data/sync/CardDAVSyncService.kt#L259) is
  unchanged: a mismatch still sets `hasChanged = true` and forces the download.
- `deleteRemovedContacts()`
  ([line 1001](../app/src/main/java/com/davy/data/sync/CardDAVSyncService.kt#L1001))
  loses its REPORT at
  [line 1119](../app/src/main/java/com/davy/data/sync/CardDAVSyncService.kt#L1119) and
  takes the shared listing as a parameter. Its href-to-SOURCE_ID comparison logic is
  unchanged.

`downloadContacts()`
([line 458](../app/src/main/java/com/davy/data/sync/CardDAVSyncService.kt#L458)) keeps
`createQueryAllRequest()` - it genuinely needs the vCard bodies, and it only runs when
the ctag changed or on first sync.

`ContactSyncWorker.performFullSync()`
([ContactSyncWorker.kt:153](../app/src/main/java/com/davy/data/sync/ContactSyncWorker.kt#L153))
also uses `createQueryAllRequest()`, correctly, because it is a full sync. Leave it.

**Acceptance:** contacts still download on a real change, server-side deletions are still
detected, and a count mismatch still forces a download. No behavior change other than
payload size.

### W3 - Stop the WebCal job from re-running the CalDAV sync

**File:** `app/src/main/java/com/davy/sync/SyncWorker.kt`

In `syncCalendars()`, skip the `calDAVSyncService` work when `forceWebCal` is true, so
the webcal job performs only the WebCal subscription pass at
[line 403](../app/src/main/java/com/davy/sync/SyncWorker.kt#L403).

This is a deliberate behavior change: today a user who sets `calendar_sync_interval = 0`
(manual only) but keeps WebCal enabled still gets CalDAV synced hourly as an accident.
After this change they do not, which is what the setting says.

**Acceptance:** with both intervals at 60, the CalDAV account sync runs once per hour,
not twice. Verify from Timber output of a debug build, or from the reduction in job
completions in batterystats.

### W4 - Defer colliding syncs instead of dropping them

**File:** `app/src/main/java/com/davy/sync/SyncWorker.kt`

Replace the `return Result.success()` on signature collision at
[line 81](../app/src/main/java/com/davy/sync/SyncWorker.kt#L81) with `Result.retry()`, so
WorkManager re-runs the request under its existing exponential backoff
(`BackoffPolicy.EXPONENTIAL`, `MIN_BACKOFF_MILLIS`, already configured in `SyncManager`)
rather than discarding a user's pending upload.

Guard against a retry storm: only retry when the invocation carries local changes to push
(`push_only`, or a non-default `calendarId` / `addressBookId`); a colliding *periodic*
invocation can still return success, because the run already in flight covers it.

**Acceptance:** an `already-in-progress` situation no longer loses a local edit. Test by
editing a contact while a sync is running and confirming the change reaches the server
without waiting for the next hourly run.

### W5 - Tests

**File:** new, `app/src/test/java/com/davy/data/sync/CardDAVSyncRequestBodyTest.kt`

`mockwebserver:4.12.0` is already declared as a `testImplementation` dependency in
[app/build.gradle.kts:237](../app/build.gradle.kts#L237), and the unit test source set
already exists under `app/src/test/java/com/davy/`.

Required cases:

1. **No-change sync issues no `address-data` request.** Serve a ctag equal to the stored
   one and an ETag-only REPORT response whose entry count matches the local count. Assert
   that every request body recorded by MockWebServer during the sync contains no
   `<c:address-data/>`.
2. **Exactly one REPORT per address book.** Assert the recorded REPORT count for the
   no-change case is 1, proving D3.
3. **Changed ctag still downloads.** Serve a different ctag; assert a request body
   containing `<c:address-data/>` is issued.
4. **Count mismatch still forces a download.** Serve an unchanged ctag but an ETag-only
   listing with one more entry than local; assert the full download follows.
5. **Server-side deletion still detected** from the ETag-only listing alone.

Existing tests must keep passing: `./gradlew :app:testDebugUnitTest`.

### W6 - On-device verification

1. Build and install the changed build over the existing `com.davy` on the Pixel 7 Pro.
   Do not use `com.davy.debug`: it has no configured account.
2. Reset the counters immediately after install:
   `adb shell dumpsys batterystats --reset`.
3. Leave the device on mobile data for a comparable window, ideally overnight to match
   the mostly-screen-off baseline.
4. Re-read `adb shell dumpsys batterystats --charged com.davy` and compare against
   section 1.

**Pass criteria:**

- Mobile bytes received drop by at least 90 % relative to 10.69 MB, normalized to the
  number of sync job launches in the new window.
- Mobile radio mAh for u0a632 drops correspondingly from 92.7 mAh.
- Sync job launches per hour are not higher than the baseline.
- Contacts and calendar still reconcile: a contact added on the server appears on the
  device within one sync interval, and a contact deleted on the server is removed.

Record the raw numbers. Do not report success from the diff alone.

## 5. Out of scope

- The CalDAV sync-token path (F7). It is already correct.
- The 7-day forced full PROPFIND. Reasonable as a deletion backstop; revisit only if
  measurement shows it dominating.
- WebCal `force = true` ignoring per-subscription refresh intervals (F6). Cheap, since it
  costs a 304 per subscription. Track separately.
- Photo handling and vCard parsing. Not implicated by any measurement here.
- Any change to the Android sync-adapter registration or to the account authenticator.

## 6. Risks and rollback

| Risk | Mitigation |
|---|---|
| Some CardDAV servers reject or mishandle an ETag-only `addressbook-query` | W5 case 1 covers the happy path; on a non-200 or an empty parse, fall back to the current full-payload request for that address book and log it. Nextcloud is the only server measured here. |
| W3 changes behavior for users who relied on the accidental CalDAV sync | Call it out in the changelog. The new behavior matches what the setting claims. |
| W4 retry loop under a persistently stuck sync | The push-only guard plus WorkManager's exponential backoff bound it. Verify no growth in job launches in W6. |
| Reinstalling for W6 wipes the baseline counters | The baseline in section 1 was captured before any reinstall and is preserved here. |

Rollback is a revert of the commits. No schema, no persisted format and no protocol state
changes; the stored ctag and sync-token semantics are untouched.

## 7. Open question

**Should the three periodic jobs become one?**

Today `AccountSyncConfigurationManager.scheduleServiceSpecificSync()` schedules calendar,
contacts and webcal as three independent periodic jobs, each defaulting to 60 minutes,
giving three radio wakeups per hour where one would do. Merging them into a single
per-account job would cut wakeups by two thirds on top of the payload savings.

The cost is user-facing: the separate `calendar_sync_interval`, `contact_sync_interval`
and `webcal_sync_interval` preferences would have to collapse into one interval, or the
merged job would need to run each service on its own schedule internally. That is a
settings and migration decision, not a bug fix, so it is deliberately left out of W1
through W6.

W3 already removes the duplicated CalDAV work, which is the expensive half of the problem.
Decide on merging after W6 shows what the remaining wakeup cost actually is.
