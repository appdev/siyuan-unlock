# SiYuan v3.8.0 Patch Compatibility Implementation Plan

> **For agentic workers:** Use the global `workflow` skill's existing-plan execution entry. Review this plan against current evidence; when it is sound, enter execution directly. Only when material problems are found should `workflow` return to research, ideation, and planning to supplement this same plan before continuing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the downstream SiYuan patch sequence apply cleanly to upstream `v3.8.0` without changing its existing behavior.

**Architecture:** Keep the release workflows and their patch order unchanged. Regenerate only the stale hunks in three patch files against upstream commit `251596fc0de2f9528c00c224252fd073a99973f4`, then verify the complete sequence on a clean checkout.

**Tech Stack:** Git unified patches, `git apply`, GitHub CLI, shell diagnostics

## Global Constraints

- Preserve the behavior of all four existing downstream patches.
- Do not change workflow logic, release inputs, dependencies, or the selected upstream version.
- Do not modify `patches/siyuan/mock-vip-user.patch`; it already applies to `v3.8.0`.
- Do not push commits, publish assets, or trigger GitHub Actions.

---

### Task 1: Regenerate the incompatible v3.8.0 patch hunks

**Files:**
- Modify: `patches/siyuan/disable-update.patch`
- Modify: `patches/siyuan/default-config.patch`
- Modify: `patches/siyuan/hide-account-entry.patch`
- Verify unchanged: `patches/siyuan/mock-vip-user.patch`

**Interfaces:**
- Consumes: upstream `siyuan-note/siyuan` tag `v3.8.0` at commit `251596fc0de2f9528c00c224252fd073a99973f4`
- Produces: four unified patch files that can be applied sequentially by the existing release workflows

- [ ] **Step 1: Reproduce the failing patch check**

Clone the exact upstream version into a disposable directory and run the current
patches with the same ordering used by Actions:

```bash
verification_dir=$(mktemp -d)
git clone --depth=1 --branch v3.8.0 https://github.com/siyuan-note/siyuan.git "$verification_dir/siyuan"
git -C "$verification_dir/siyuan" rev-parse HEAD
git -C "$verification_dir/siyuan" apply --check "$PWD/patches/siyuan/disable-update.patch"
```

Expected: `HEAD` is `251596fc0de2f9528c00c224252fd073a99973f4`, and the final command fails with `kernel/conf/system.go: patch does not apply`.

- [ ] **Step 2: Update `disable-update.patch` for the new system defaults context**

Adjust only the `kernel/conf/system.go` hunk so it retains the new
`UpdateChannel` initializer while changing `DownloadInstallPkg`:

```diff
 		NetworkProxy:       &NetworkProxy{},
-		DownloadInstallPkg: true,
+		DownloadInstallPkg: false,
 		UpdateChannel:      UpdateChannelStable,
```

Keep the existing `kernel/model/updater.go`, `kernel/api/system.go`, and
`kernel/model/mount.go` changes semantically identical; refresh their hunk
offsets and blob metadata only if produced by a clean upstream diff.

- [ ] **Step 3: Update `default-config.patch` for the new LAN sync default**

Adjust the `kernel/conf/sync.go` hunk so it retains upstream's new LAN default:

```diff
 		Mode:                1,
-		GenerateConflictDoc: false,
-		Provider:            ProviderSiYuan,
+		GenerateConflictDoc: true,
+		Provider:            ProviderS3,
 		Interval:            30,
 		LAN:                 &LANSync{MaxConcurrentReqs: 16},
```

Preserve the existing `kernel/conf/account.go` and
`kernel/conf/appearance.go` changes; refresh only their upstream context and
blob metadata where necessary.

- [ ] **Step 4: Update `hide-account-entry.patch` for the TypeScript import context**

Regenerate the `app/src/layout/topBar.ts` hunks against v3.8.0. Remove
`openSetting` while retaining the new type-only `App` import:

```diff
 import {MenuItem} from "../menus/Menu";
 import {setMode} from "../util/assets";
-import {openSetting} from "../config";
 import {openSearch} from "../search/spread";
 import type {App} from "../index";
```

Keep the `toolbarVIP` branch behavior unchanged by removing only its
`openSetting(app, "sync")` block. Preserve the existing `syncTab.ts` behavior
that omits `registerAccountGroup(tab)`; refresh its hunk offsets as needed.

- [ ] **Step 5: Verify each patch and the complete sequence**

Use a fresh v3.8.0 checkout, first checking and then applying each patch in the
workflow order:

```bash
verification_dir=$(mktemp -d)
git clone --depth=1 --branch v3.8.0 https://github.com/siyuan-note/siyuan.git "$verification_dir/siyuan"
for patch_file in \
  disable-update.patch \
  default-config.patch \
  mock-vip-user.patch \
  hide-account-entry.patch; do
  git -C "$verification_dir/siyuan" apply --check "$PWD/patches/siyuan/$patch_file"
  git -C "$verification_dir/siyuan" apply "$PWD/patches/siyuan/$patch_file"
done
git -C "$verification_dir/siyuan" diff --check
git -C "$verification_dir/siyuan" status --short
```

Expected: every check and application succeeds; `git diff --check` prints
nothing; status shows only files intentionally changed by the four patches.

- [ ] **Step 6: Review the frozen repository diff**

```bash
git diff --check
git status --short
git diff -- patches/siyuan/disable-update.patch patches/siyuan/default-config.patch patches/siyuan/hide-account-entry.patch
git diff --exit-code -- patches/siyuan/mock-vip-user.patch
```

Expected: no whitespace errors; only the plan plus the three intended patch
files are uncommitted; `mock-vip-user.patch` has no diff.

- [ ] **Step 7: Commit the compatibility fix**

```bash
git add \
  patches/siyuan/disable-update.patch \
  patches/siyuan/default-config.patch \
  patches/siyuan/hide-account-entry.patch
git commit -m "fix: update release patches for v3.8.0"
```

Do not push or rerun workflows.
