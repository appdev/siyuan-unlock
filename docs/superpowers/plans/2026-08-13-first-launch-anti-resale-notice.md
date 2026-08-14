# First-launch Anti-resale Notice Implementation Plan

> **For agentic workers:** Use the global `workflow` skill's existing-plan execution entry. Review this plan against current evidence; when it is sound, enter execution directly. Only when material problems are found should `workflow` return to research, ideation, and planning to supplement this same plan before continuing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a non-dismissible five-second anti-resale notice once per installed PC, Android, or iOS client.

**Architecture:** Add a dedicated downstream patch with focused Node/Go tests and a Dialog-based UI module. Call the same module from desktop and mobile startup, persist acknowledgement through authenticated kernel APIs in `<HomeDir>/.config/siyuan/siyuan-unlock.json`, and apply the patch only in desktop, Android, and iOS release workflows.

**Tech Stack:** TypeScript, Go, SiYuan `Dialog`, installation-scoped JSON state, Node.js 24 and Go test runners, Git unified patches, GitHub Actions YAML

## Approved persistence correction (2026-08-14)

The original `localStorage` assumption is invalid on desktop because each
kernel launch and parallel workspace uses a random HTTPS port, producing a new
origin. Replace the browser-storage implementation with installation-scoped
kernel persistence. Add model and API regression tests proving that the state
survives workspace changes; keep read failures as unread and keep the dialog
open when acknowledgement cannot be written. This correction supersedes only
the persistence mechanism below; the approved copy, eligibility, five-second
lock, workflows, and interaction remain unchanged.

## Global Constraints

- Use the approved Simplified Chinese copy verbatim for every interface language.
- Cover packaged Windows, macOS, Linux, Android, and iOS clients only.
- Exclude browser, Docker/Web, publish mode, and HarmonyOS.
- The dialog cannot be dismissed through its close icon, scrim, Escape, mobile back, or generic dialog cleanup.
- Keep acknowledgement disabled for at least five elapsed wall-clock seconds.
- Write the versioned acknowledgement only after an enabled `我已知晓` click succeeds.
- Keep the dialog open when persistence fails.
- Require the official repository identity and a non-forked Actions Secret in every release-capable workflow.
- Never print, persist locally, or commit the official build marker value.
- Do not push, publish, or trigger release workflows.

---

### Task 1: Add the official-repository build marker and workflow guard

**Files:**
- Modify: `.github/workflows/release-cron.yml`
- Modify: `.github/workflows/desktop-release.yml`
- Modify: `.github/workflows/release-android.yml`
- Modify: `.github/workflows/release-ios.yml`
- Modify: `.github/workflows/release-docker.yml`
- Modify: `.github/workflows/auto_aur_release_stable.yml`
- External configuration: repository Actions Secret `SIYUAN_UNLOCK_OFFICIAL_BUILD`

**Interfaces:**
- Consumes: `github.repository` and `secrets.SIYUAN_UNLOCK_OFFICIAL_BUILD`
- Produces: an early failing guard in all six release-capable workflows

- [ ] **Step 1: Add the failing guard before build or publish work**

Add this as the first step in every release-capable job, using `shell: bash` so
the desktop matrix behaves identically on Linux, macOS, and Windows:

```yaml
- name: Verify official repository
  shell: bash
  env:
    OFFICIAL_BUILD_MARKER: ${{ secrets.SIYUAN_UNLOCK_OFFICIAL_BUILD }}
  run: |
    if [[ "${GITHUB_REPOSITORY}" != "appdev/siyuan-unlock" || -z "${OFFICIAL_BUILD_MARKER}" ]]; then
      echo "::error::This release workflow can only run in the official repository."
      exit 1
    fi
```

Remove the existing job-level `if: github.repository ==
'appdev/siyuan-unlock'` from `release-cron.yml`; otherwise a fork is reported
as skipped/successful instead of explicitly failing through the guard.

- [ ] **Step 2: Verify the guard fails without a marker**

Extract the guard body into a local shell invocation with
`GITHUB_REPOSITORY=someone/siyuan-unlock` and an empty marker.

Expected: exit code `1` and the official-repository error. Repeat with
`GITHUB_REPOSITORY=appdev/siyuan-unlock` and an empty marker; expected exit code
is also `1`.

- [ ] **Step 3: Create the repository-local marker with `gh`**

Generate the value locally and pipe it directly to GitHub without placing it in
a shell variable, argument, file, command output, or repository content:

```bash
openssl rand -hex 32 | gh secret set SIYUAN_UNLOCK_OFFICIAL_BUILD \
  --repo appdev/siyuan-unlock --app actions
```

Expected: `gh` confirms the secret was set without displaying its value.

- [ ] **Step 4: Verify only the marker name and update time**

```bash
gh secret list --repo appdev/siyuan-unlock --app actions | \
  rg '^SIYUAN_UNLOCK_OFFICIAL_BUILD\b'
```

Expected: exactly one matching secret name; its value remains unavailable.

- [ ] **Step 5: Verify the positive guard path locally**

Run the guard body with `GITHUB_REPOSITORY=appdev/siyuan-unlock` and a disposable
non-empty local placeholder value, not the real GitHub Secret.

Expected: exit code `0`.

- [ ] **Step 6: Verify workflow coverage**

```bash
rg -l 'SIYUAN_UNLOCK_OFFICIAL_BUILD' .github/workflows/*.yml | sort
```

Expected: the six files listed in this task and no unrelated workflow. Inspect
each file to confirm the guard is the first step and `release-cron.yml` no
longer silently skips forks.

- [ ] **Step 7: Commit the provenance guard separately**

```bash
git add .github/workflows/release-cron.yml \
  .github/workflows/desktop-release.yml \
  .github/workflows/release-android.yml \
  .github/workflows/release-ios.yml \
  .github/workflows/release-docker.yml \
  .github/workflows/auto_aur_release_stable.yml
git commit -m "ci: require official repository build marker"
```

Do not push or trigger any workflow.

---

### Task 2: Complete the approved v3.8.0 compatibility prerequisite

**Files:**
- Follow: `docs/superpowers/plans/2026-08-13-v3-8-0-patch-compatibility.md`
- Modify: `patches/siyuan/disable-update.patch`
- Modify: `patches/siyuan/default-config.patch`
- Modify: `patches/siyuan/hide-account-entry.patch`

**Interfaces:**
- Consumes: upstream SiYuan tag `v3.8.0` at `251596fc0de2f9528c00c224252fd073a99973f4`
- Produces: an existing four-patch sequence that cleanly applies before the new notice patch

- [ ] **Step 1: Execute the existing approved compatibility plan**

Use `docs/superpowers/plans/2026-08-13-v3-8-0-patch-compatibility.md` as the
authoritative test-first procedure. Confirm that all four existing patches
apply in order to a clean `v3.8.0` checkout before adding the notice patch.

- [ ] **Step 2: Commit the compatibility prerequisite**

```bash
git add patches/siyuan/disable-update.patch \
  patches/siyuan/default-config.patch \
  patches/siyuan/hide-account-entry.patch
git commit -m "fix: update release patches for v3.8.0"
```

Expected: the compatibility fix is isolated from the notice feature.

---

### Task 3: Define and test notice eligibility, countdown, and persistence

**Files:**
- Create in patch: `app/src/boot/firstLaunchNoticeState.ts`
- Create in patch: `app/src/boot/firstLaunchNoticeState.test.ts`
- Create: `patches/siyuan/first-launch-notice.patch`

**Interfaces:**
- Produces: `NOTICE_STORAGE_KEY`, `NOTICE_DELAY_MS`, `isFirstLaunchNoticeEligible(runtime)`, `getNoticeRemainingSeconds(startedAt, now)`, `hasAcknowledgedNotice(storage)`, and `acknowledgeNotice(storage)`
- Consumes later: UI module in Task 4

- [ ] **Step 1: Write focused failing tests in a clean patched-source workspace**

Create `app/src/boot/firstLaunchNoticeState.test.ts` before its implementation.
Use Node's built-in `node:test` and `node:assert/strict` modules to assert:

```ts
assert.equal(isFirstLaunchNoticeEligible({browser: false, mobileApp: false, container: "desktop", publish: false}), true);
assert.equal(isFirstLaunchNoticeEligible({browser: true, mobileApp: true, container: "android", publish: false}), true);
assert.equal(isFirstLaunchNoticeEligible({browser: true, mobileApp: true, container: "ios", publish: false}), true);
assert.equal(isFirstLaunchNoticeEligible({browser: true, mobileApp: false, container: "docker", publish: false}), false);
assert.equal(isFirstLaunchNoticeEligible({browser: true, mobileApp: true, container: "harmony", publish: false}), false);
assert.equal(isFirstLaunchNoticeEligible({browser: false, mobileApp: false, container: "desktop", publish: true}), false);
assert.equal(getNoticeRemainingSeconds(1_000, 1_000), 5);
assert.equal(getNoticeRemainingSeconds(1_000, 5_999), 1);
assert.equal(getNoticeRemainingSeconds(1_000, 6_000), 0);
```

Use a minimal `Storage` substitute to verify that missing/read-failure values
are treated as unread, the exact versioned key is read and written, and a
write failure returns `false` without marking acknowledgement.

- [ ] **Step 2: Run the test and verify RED**

```bash
node --test app/src/boot/firstLaunchNoticeState.test.ts
```

Expected: FAIL because `firstLaunchNoticeState.ts` does not exist.

- [ ] **Step 3: Implement the pure state module**

Use these fixed values and signatures:

```ts
export const NOTICE_STORAGE_KEY = "siyuanUnlockAntiResaleNoticeV1";
export const NOTICE_DELAY_MS = 5_000;

export interface NoticeRuntime {
    browser: boolean;
    mobileApp: boolean;
    container: string;
    publish: boolean;
}
```

Eligibility is `!publish && (!browser || (mobileApp && ["android", "ios"].includes(container)))`.
Remaining seconds is the non-negative ceiling of the unelapsed portion of
`NOTICE_DELAY_MS`. Storage helpers catch access errors; acknowledgement writes
`"acknowledged"` and returns `true` only when `setItem` succeeds.

- [ ] **Step 4: Run the test and verify GREEN**

```bash
node --test app/src/boot/firstLaunchNoticeState.test.ts
```

Expected: all eligibility, countdown, read, and write cases PASS.

---

### Task 4: Render the locked notice and integrate client startup

**Files:**
- Create in patch: `app/src/boot/firstLaunchNotice.ts`
- Modify in patch: `app/src/boot/onGetConfig.ts`
- Modify in patch: `app/src/index.ts`
- Modify in patch: `app/src/mobile/index.ts`
- Extend: `patches/siyuan/first-launch-notice.patch`

**Interfaces:**
- Consumes: Task 3 state helpers, SiYuan `Dialog`, `isBrowser()`, and `isInMobileApp()`
- Produces: `openFirstLaunchNotice(): Promise<void>`, resolved immediately when ineligible/already acknowledged and only after successful acknowledgement otherwise

- [ ] **Step 1: Implement the approved dialog content and behavior**

Create a `Dialog` titled `重要声明`, with width `92vw` on mobile and `520px`
otherwise, `disableClose: true`, and the approved copy plus the visible
clickable URL `https://github.com/appdev/siyuan-unlock/releases`.

Capture the bound original `dialog.destroy` function, then replace the public
instance method with a no-op while the notice is locked. This makes existing
Escape, mobile-back, and generic dialog-cleanup paths unable to remove it
without changing global `Dialog` semantics. Only the acknowledgement handler
calls the captured original destroy function.

Set the button to `disabled` with label `我已知晓（5）`. Record `Date.now()` at
render time and update from `getNoticeRemainingSeconds(startedAt, Date.now())`.
Enable and relabel it only when the helper returns zero. On click, call
`acknowledgeNotice(localStorage)`; if it returns `false`, leave the dialog open.
If it returns `true`, clear the interval, destroy through the captured original
function, and resolve the promise.

- [ ] **Step 2: Integrate desktop startup without allowing changelog overlap**

Remove `openChangelog()` from the asynchronous layout callback in
`app/src/boot/onGetConfig.ts`. In `app/src/index.ts`, immediately after
`await onGetConfig(response.data.start, this)`, call:

```ts
await openFirstLaunchNotice();
openChangelog();
```

Import both functions in `app/src/index.ts`. This ensures the mandatory notice
is topmost and acknowledged before the changelog can appear.

- [ ] **Step 3: Integrate mobile startup in the same sequence**

In `app/src/mobile/index.ts`, after `await initFramework(...)`, call
`await openFirstLaunchNotice()` before the existing `openChangelog()` call.
The eligibility helper excludes mobile browsers and HarmonyOS while allowing
Android and iOS app containers.

- [ ] **Step 4: Generate the downstream patch**

Generate `patches/siyuan/first-launch-notice.patch` from a clean upstream
`v3.8.0` checkout containing only the state module, its focused test, the UI
module, and the three startup integration edits. Inspect the patch to ensure it
does not include dependencies, built assets, or unrelated formatting.

- [ ] **Step 5: Re-run focused tests**

```bash
node --test app/src/boot/firstLaunchNoticeState.test.ts
```

Expected: PASS with no failed test cases.

---

### Task 5: Apply the notice patch only to packaged client workflows

**Files:**
- Modify: `.github/workflows/desktop-release.yml`
- Modify: `.github/workflows/release-android.yml`
- Modify: `.github/workflows/release-ios.yml`
- Verify unchanged: `.github/workflows/release-docker.yml`

**Interfaces:**
- Consumes: `patches/siyuan/first-launch-notice.patch`
- Produces: packaged desktop and mobile builds containing the notice, with Docker/Web builds unchanged

- [ ] **Step 1: Add the patch after the existing patch sequence**

Add this command after `hide-account-entry.patch` in desktop, Android, and iOS:

```bash
git apply "${workspace}/siyuan-note/patches/siyuan/first-launch-notice.patch"
```

Use the workflow's existing path expression style for Android and iOS. Do not
add the command to `.github/workflows/release-docker.yml`.

- [ ] **Step 2: Verify workflow routing**

```bash
rg -n "first-launch-notice.patch" .github/workflows
```

Expected: exactly one match each in desktop, Android, and iOS workflows, and no
match in the Docker workflow.

---

### Task 6: Verify the complete patch sequence and frontend build

**Files:**
- Test: all five `patches/siyuan/*.patch` files
- Test: upstream patched `app/`

**Interfaces:**
- Consumes: completed Tasks 1–5
- Produces: evidence that the release inputs patch and build successfully against upstream `v3.8.0`

- [ ] **Step 1: Check and apply all patches in workflow order**

```bash
verification_dir=$(mktemp -d)
git clone --depth=1 --branch v3.8.0 https://github.com/siyuan-note/siyuan.git "$verification_dir/siyuan"
for patch_file in disable-update.patch default-config.patch mock-vip-user.patch hide-account-entry.patch first-launch-notice.patch; do
  git -C "$verification_dir/siyuan" apply --check "$PWD/patches/siyuan/$patch_file"
  git -C "$verification_dir/siyuan" apply "$PWD/patches/siyuan/$patch_file"
done
git -C "$verification_dir/siyuan" diff --check
```

Expected: every check and application succeeds and `diff --check` is silent.

- [ ] **Step 2: Run focused tests and the production frontend build**

```bash
cd "$verification_dir/siyuan/app"
corepack enable
corepack prepare pnpm@11.18.0 --activate
pnpm install --no-frozen-lockfile
node --test src/boot/firstLaunchNoticeState.test.ts
pnpm run build
```

Expected: focused tests pass and all production frontend webpack targets build.

- [ ] **Step 3: Review the final repository diff**

```bash
git diff --check
git status --short
git diff -- patches/siyuan .github/workflows docs/superpowers
```

Expected: no generated assets or unrelated edits; the feature is limited to
the notice patch, three client workflows, and approved documentation.

- [ ] **Step 4: Commit the feature**

```bash
git add patches/siyuan/first-launch-notice.patch \
  .github/workflows/desktop-release.yml \
  .github/workflows/release-android.yml \
  .github/workflows/release-ios.yml \
  docs/superpowers/specs/2026-08-13-first-launch-anti-resale-notice-design.md \
  docs/superpowers/plans/2026-08-13-first-launch-anti-resale-notice.md
git commit -m "feat: show first-launch anti-resale notice"
```

Do not push or trigger Actions.
