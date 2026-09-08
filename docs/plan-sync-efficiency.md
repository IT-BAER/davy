# Execution plan: sync-path efficiency and correctness

Companion to [spec-sync-efficiency.md](spec-sync-efficiency.md). The spec says what to
change and why. This plan says in what order, with which gate after each step, and where
the work can safely stop.

Target release: 1.1.5, versionCode 14 (bug-fix only, no schema or protocol state change).

## 1. Constraints that shape the ordering

These are facts about this repo and this device, checked before planning. They dictate
the phase order more than the work items do.

**The final gate is an overnight device measurement.** W6 compares against a baseline
taken over 10h 44m of mostly screen-off time. A one-hour spot check cannot confirm it.
That means one long wait, so every traffic-reducing change must land *before* the
measurement, not in two separate cycles.

**The baseline cannot be re-taken.** `dumpsys batterystats` resets on reinstall. The
numbers in spec section 1 were captured before any install and are the only baseline
that exists. They are preserved in the spec; do not overwrite that section.

**CI already runs the tests.** `.github/workflows/release.yml` runs
`./gradlew --build-cache lintRelease` (line 141) and
`./gradlew --build-cache testReleaseUnitTest` (line 144) on the release path. A broken
unit test blocks a release, so the tests written in Phase 1 are a real gate, not
decoration. Note the task is `testReleaseUnitTest`, not the debug variant.

**`CardDAVSyncService` is not unit-testable as it stands.** Its constructor
([CardDAVSyncService.kt:42](../app/src/main/java/com/davy/data/sync/CardDAVSyncService.kt#L42))
takes 12 dependencies including `Context`, `ContactContentProviderAdapter` and
`AndroidAccountManager`. Driving `syncAddressBook()` from a JVM unit test would mean
mocking all 12 and stubbing ContentProvider access. This is the single biggest planning
constraint and it forces a decision, below.

**Release notes are maintained in two locales only.** The repo has 15 `values-*`
directories, but `whats_new_v1_1_4` exists in only 2 string files. A 1.1.5 release needs
`whats_new_v1_1_5` in the same two, plus a `CHANGELOG.md` entry carrying both the
`<en-US>` and `<de-DE>` blocks that the release workflow reads.

## 2. Test seam (decided: option B)

The spec's W5 assumes a MockWebServer test can observe the request bodies a sync issues.
Given the 12-dependency constructor, there are three ways to get there.

| Option | What it tests | Cost |
|---|---|---|
| **A. Test `AddressBookQuery` only** | That `createETagOnlyRequest()` emits the right XML | Trivial, but proves nothing about whether the sync path *uses* it. Does not guard the actual regression. |
| **B. Extract the ETag listing into a small collaborator** (recommended) | The real HTTP request the sync issues, against MockWebServer, with `httpClient` as the only dependency | One new small class, injected into `CardDAVSyncService`. Real regression guard on the wire format. |
| **C. Mock all 12 dependencies and drive `syncAddressBook()`** | The full sync flow end to end | Highest fidelity, but a large brittle test that will break on unrelated constructor changes. |

**Option B is chosen.** It changes W1's shape: instead of "add one private suspend
function" it becomes "add one small injectable class", whose only dependency is
`httpClient`. That is the only structure this plan adds, and it exists specifically to
make the fix checkable. Options A and C leave either no guard or an expensive one.

## 3. Phases

Each phase has an exit gate. Do not start the next phase until the gate passes. Any phase
can be the stopping point, with the branch left in a working state.

### Phase 0 - Branch and baseline

Create a working branch off `main` at 304fdd4. Confirm the tree is clean apart from the
untracked `gradlew.bat`, and confirm `main` still builds.

**Exit gate:** branch exists, `./gradlew :app:assembleDebug` succeeds. Already confirmed
once today with exit code 0.

### Phase 1 - Tests first, watched failing

Write the W5 cases before any production change, following the `tdd` skill.

Of the five cases in W5, two describe new behavior and must fail now: "no
`address-data` request in the no-change case" and "exactly one REPORT per address book".
The other three ("changed ctag still downloads", "count mismatch still forces a
download", "server-side deletion still detected") describe behavior that must survive
the change, and should pass against current code.

**Exit gate:** the two new-behavior tests fail for the stated reason, not from a wiring
error, and the three regression tests pass. Record the failure output.

### Phase 2 - W1 and W2, CardDAV ETag-only listing

Implement the shared ETag-only listing and rewire `getServerContactCount()` and
`deleteRemovedContacts()` to consume it. Leave `downloadContacts()` and
`ContactSyncWorker.performFullSync()` on `createQueryAllRequest()`, which is correct for
both.

Carry over the 401/403 notification handling including Cloudflare detection, as spec W1
requires. Losing that would silently undo commit c61755e.

**Exit gate:** all five W5 tests pass. `./gradlew :app:testReleaseUnitTest` passes whole.
The diff is readable in one pass and touches at most `CardDAVSyncService.kt` plus the one
new class.

### Phase 3 - W3, stop the WebCal job re-running CalDAV

Skip the `calDAVSyncService` work in `syncCalendars()` when `forceWebCal` is true.

This is behavior change, not just efficiency: a user with `calendar_sync_interval = 0`
loses an accidental hourly CalDAV sync they may have been relying on without knowing.
It goes in the changelog.

Verification here is weaker than Phase 2 and that is worth stating plainly. A unit test
would have to construct `SyncWorker`, which has the same dependency problem. The
practical check is a debug build with Timber output, watching the calendar sync run once
per hour instead of twice. `com.davy.debug` currently has no configured account, so this
needs an account set up on the debug package first, or the check folds into Phase 6.

**Exit gate:** either the debug-build log shows one CalDAV account sync per hour, or the
check is explicitly deferred to Phase 6 and recorded as deferred.

### Phase 4 - W4, defer colliding syncs

Change the signature-collision path from `Result.success()` to `Result.retry()`, guarded
so that only invocations carrying local changes retry and a colliding periodic invocation
still returns success.

This is the one change that could make things worse, by adding job launches. Phase 6's
pass criteria include "sync job launches per hour are not higher than the baseline"
specifically to catch that.

**Exit gate:** the guard logic is covered by a focused test, and the existing suite still
passes.

### Phase 5 - Static analysis and full local gate

Run `./gradlew :app:detekt`, `./gradlew --build-cache lintRelease` and
`./gradlew --build-cache testReleaseUnitTest`, matching what CI will run.

**Exit gate:** all three clean, or every remaining warning is pre-existing on `main` and
named as such.

### Phase 6 - Device measurement

1. Build and install over the existing `com.davy` on the Pixel 7 Pro. Not
   `com.davy.debug`, which has no account.
2. `adb shell dumpsys batterystats --reset` immediately after install.
3. Leave on mobile data overnight, to match the mostly screen-off baseline window.
4. Re-read `adb shell dumpsys batterystats --charged com.davy` and compare against spec
   section 1.

Also confirm correctness, not only cost: add a contact on the server and check it arrives
within one interval; delete one on the server and check it disappears; edit one on the
device during a running sync and check it reaches the server (the W4 case).

**Exit gate:** all four pass criteria in spec W6 met, with raw numbers recorded. If bytes
drop but a reconciliation check fails, the release does not proceed.

### Phase 7 - Release preparation

Bump `versionCode` to 14 and `versionName` to 1.1.5 in
[app/build.gradle.kts:40](../app/build.gradle.kts#L40). Add the `CHANGELOG.md` entry with
both `<en-US>` and `<de-DE>` blocks in the existing Keep a Changelog shape, and add
`whats_new_v1_1_5` to the same two string files that carry `whats_new_v1_1_4`.

**Exit gate:** the release workflow's expectations are satisfied. Do not tag or dispatch
the workflow without your explicit go-ahead; that publishes to Google Play.

## 4. Why this order

W1 and W2 first because they are the largest measured cost, the best understood, and the
only part with a strong automated guard. If the plan stops anywhere, it should stop after
Phase 2 with the main problem fixed.

W3 next because it is a small change that compounds with W1 and W2: it removes a whole
duplicate calendar sync per hour, and it reduces the collision pressure that W4 addresses.
Doing it before W4 means W4 is fixing a rarer condition rather than papering over a
self-inflicted one.

W4 last among the code changes because it is the only one that can increase work, and it
should be measured with everything else already in place.

All of them before the single overnight measurement, because the measurement is the
expensive step and splitting it into per-change cycles would cost several nights for
attribution nobody needs. The trade-off is accepted deliberately: if the measurement
disappoints, attribution between W1/W2 and W3 requires a second run.

## 5. Delegation and review

**No implementation subagents.** Phase 2 is one file plus one small class; Phases 3 and 4
are a few lines each. Every diff here is readable in one pass, which is exactly the case
where delegating costs more than it saves.

**One review at the end, not per phase.** The branch does cross the independent-review
gate: W4 touches concurrency, and W1/W2 change a wire-format request against a real
server. That earns a single `two-axis-review` over the whole branch after Phase 5,
against this plan and the spec. It does not earn a review after each phase.

**One correction round.** Critical or important findings get one fix pass and one scoped
re-review. Minor findings are recorded, not looped on.

## 6. Session boundaries

Phases 0 to 5 fit one working session. Phase 6 contains an overnight wait and will almost
certainly land in a new session.

What has to survive that boundary: the branch name and its HEAD commit, the spec section 1
baseline numbers, the install timestamp and the exact time of the `batterystats --reset`,
and whether the Phase 3 check was deferred. A handoff written at the end of Phase 5 should
carry those five items and nothing else.

## 7. Risk register

| Risk | Phase | Handling |
|---|---|---|
| Nextcloud handles ETag-only `addressbook-query` differently than expected | 2 | Fall back to the full-payload request for that address book on a non-200 or empty parse, and log it. Nextcloud is the only server measured. |
| The Phase 2 rewrite drops the Cloudflare 401/403 handling from c61755e | 2 | Named explicitly as a Phase 2 exit condition. |
| W3 removes a CalDAV sync someone depended on | 3 | Changelog entry. The new behavior matches what the setting claims. |
| W4 increases job launches | 4, 6 | Push-only guard, plus an explicit Phase 6 pass criterion on launches per hour. |
| The overnight window is not comparable (Wi-Fi instead of mobile, different screen-off ratio) | 6 | Normalize per sync job launch rather than comparing absolute MB, and record the screen-off time from the same dump. |
| Measurement improves but reconciliation breaks | 6 | Correctness checks are part of the exit gate, not a follow-up. |

Rollback at any point is a revert of the branch commits. No database migration, no
persisted format change, and the stored ctag and sync-token semantics are untouched, so
reverting needs no data repair.

## 8. Decisions and what is still open

Settled before execution:

- **Test seam: option B.** One small injectable collaborator, MockWebServer-tested
  directly. See section 2.
- **W4 ships in 1.1.5.** One overnight measurement covers all four work items, and the
  dropped-local-edit bug is fixed now rather than next cycle. The Phase 6 criterion "job
  launches per hour not higher than baseline" is the guard; if it regresses, revert that
  single commit rather than the branch.

Still open:

- **Merging the three periodic jobs into one.** Deliberately deferred, see spec section 7.
  Decide after Phase 6 shows the residual wakeup cost.
