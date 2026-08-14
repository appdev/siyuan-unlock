# GitHub Release Update Check Implementation Plan

> **For agentic workers:** Use the global `workflow` skill's existing-plan execution entry. Review this plan against current evidence; when it is sound, enter execution directly. Only when material problems are found should `workflow` return to research, ideation, and planning to supplement this same plan before continuing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore SiYuan's update-check notifications while sourcing every update channel from `appdev/siyuan-unlock` GitHub Releases and keeping automatic package download disabled.

**Architecture:** Keep the upstream frontend, API, notification, semantic-version selection, caching, and digest validation unchanged. Narrow `patches/siyuan/disable-update.patch` so it disables only automatic package download, then patch `updater_release.go` to route Stable/Beta/Alpha through the existing GitHub provider using the SiYuan Unlock repository.

**Tech Stack:** Git unified patches, Go 1.24+, SiYuan v3.8.0 kernel tests, GitHub REST Releases API, `gh`, `git apply`.

## Global Constraints

- The update API is `https://api.github.com/repos/appdev/siyuan-unlock/releases?per_page=100`.
- The fallback release-page prefix is `https://github.com/appdev/siyuan-unlock/releases/tag/v`.
- Stable accepts only final releases; Beta accepts final/Beta/RC; Alpha accepts final/Alpha/Beta/RC.
- Restore both the About-page manual check and the Help-notebook delayed check.
- Keep `DownloadInstallPkg` disabled at default and runtime; never download or install an update package.
- Do not add a release server, proxy, GitHub token, dependency, or workflow branch.
- Preserve all unrelated working-tree changes and do not commit unless the user separately authorizes a commit.

---

### Task 1: Add the update-source regression test and narrow the update patch

**Files:**
- Modify: `patches/siyuan/disable-update.patch`
- Test within patched upstream: `kernel/model/updater_release_test.go`
- Reference: `docs/superpowers/specs/2026-08-14-github-release-update-check-design.md`

**Interfaces:**
- Consumes: upstream `getUpdateRelease(force bool) (*updateRelease, error)`, `getGitHubUpdateRelease(channel string, force bool)`, `cachedGitHubReleases`, `githubReleasesCacheTime`, and `conf.UpdateChannelStable`.
- Produces: restored `/api/system/checkUpdate`; restored delayed `CheckUpdate(true)`; all-channel GitHub release routing; SiYuan Unlock API and fallback page constants; unchanged forced `DownloadInstallPkg=false` behavior.

- [x] **Step 1: Prepare an exact clean upstream fixture**

Clone tag `v3.8.0` into a `mktemp -d` directory and record its exact commit:

```bash
fixture_dir=$(mktemp -d /tmp/siyuan-update-check.XXXXXX)
git clone --depth=1 --branch v3.8.0 https://github.com/siyuan-note/siyuan.git "$fixture_dir/siyuan"
git -C "$fixture_dir/siyuan" rev-parse HEAD
```

Expected commit: `251596fc0de2f9528c00c224252fd073a99973f4`.

- [x] **Step 2: Write the failing behavioral test in the temporary upstream fixture**

Use `apply_patch` to add `time` to `kernel/model/updater_release_test.go` imports and append this test:

```go
func TestStableUpdateUsesUnlockGitHubRelease(t *testing.T) {
	originalConf := Conf
	Conf = NewAppConf()
	Conf.System = conf.NewSystem()
	Conf.System.UpdateChannel = conf.UpdateChannelStable

	githubReleasesLock.Lock()
	originalReleases := cachedGitHubReleases
	originalCacheTime := githubReleasesCacheTime
	cachedGitHubReleases = []*githubRelease{{TagName: "v9.9.9"}}
	githubReleasesCacheTime = time.Now().Unix()
	githubReleasesLock.Unlock()

	t.Cleanup(func() {
		Conf = originalConf
		githubReleasesLock.Lock()
		cachedGitHubReleases = originalReleases
		githubReleasesCacheTime = originalCacheTime
		githubReleasesLock.Unlock()
	})

	release, err := getUpdateRelease(false)
	if err != nil {
		t.Fatal(err)
	}
	if "9.9.9" != release.Version {
		t.Fatalf("unexpected stable version: %q", release.Version)
	}
	const wantURL = "https://github.com/appdev/siyuan-unlock/releases/tag/v9.9.9"
	if wantURL != release.ReleaseURL {
		t.Fatalf("unexpected stable release URL: %q", release.ReleaseURL)
	}
}
```

This test exercises real provider selection and cache consumption. The expected URL is hand-derived and does not reuse production helpers.

- [x] **Step 3: Run the regression test and verify RED**

Run:

```bash
cd "$fixture_dir/siyuan/kernel"
go test ./model -run '^TestStableUpdateUsesUnlockGitHubRelease$' -count=1
```

Expected: FAIL because upstream Stable routing calls the SiYuan cloud provider instead of consuming the controlled GitHub cache, and its fallback prefix still targets `siyuan-note/siyuan`.

- [x] **Step 4: Modify the repository patch with the minimal implementation**

Use `apply_patch` on `patches/siyuan/disable-update.patch` to make these exact semantic changes:

1. Remove the entire `kernel/api/system.go` hunk so upstream `checkUpdate` continues parsing `showMsg` and calling `model.CheckUpdate(showMsg)`.
2. Remove the entire `kernel/model/mount.go` hunk so opening the Help notebook still waits 10 seconds and calls `CheckUpdate(true)`.
3. Keep the `kernel/conf/system.go` hunk setting the default `DownloadInstallPkg` to `false`.
4. Keep the `kernel/model/updater.go` hunk forcing `Conf.System.DownloadInstallPkg = false` before package-download decisions.
5. Add an `updater_release.go` hunk with these constants:

```go
githubReleasesURL      = "https://api.github.com/repos/appdev/siyuan-unlock/releases?per_page=100"
githubReleaseURLPrefix = "https://github.com/appdev/siyuan-unlock/releases/tag/v"
```

6. Replace only the Stable special case in `getUpdateRelease` so all valid channels use GitHub:

```go
func getUpdateRelease(force bool) (*updateRelease, error) {
	channel := Conf.System.UpdateChannel
	if !isValidUpdateChannel(channel) {
		return nil, errors.New("update channel is invalid")
	}
	return getGitHubUpdateRelease(channel, force)
}
```

7. Add the Step 2 regression test and its `time` import as hunks in the same shipped patch.

Do not alter `getStableUpdateRelease`; leaving the unused upstream function intact minimizes fork drift while the dispatch path no longer calls it.

- [x] **Step 5: Apply the updated patch to a fresh fixture and verify GREEN**

Create a second clean fixture, apply only `disable-update.patch`, and run the focused tests:

```bash
green_dir=$(mktemp -d /tmp/siyuan-update-check-green.XXXXXX)
git clone --depth=1 --branch v3.8.0 https://github.com/siyuan-note/siyuan.git "$green_dir/siyuan"
git -C "$green_dir/siyuan" apply "$PWD/patches/siyuan/disable-update.patch"
cd "$green_dir/siyuan/kernel"
go test ./model -run '^(TestStableUpdateUsesUnlockGitHubRelease|TestIsReleaseAllowed|TestSelectGitHubRelease|TestParseChecksumManifest|TestNormalizeSHA256)$' -count=1
```

Expected: PASS with zero failures. The regression test must return version `9.9.9` and the SiYuan Unlock fallback URL without making an external version request.

---

### Task 2: Verify the complete release patch stack and public Release contract

**Files:**
- Verify: `patches/siyuan/disable-update.patch`
- Verify: `patches/siyuan/default-config.patch`
- Verify: `patches/siyuan/mock-vip-user.patch`
- Verify: `patches/siyuan/hide-account-entry.patch`
- Verify: `patches/siyuan/first-launch-notice.patch`
- Review: `.github/workflows/desktop-release.yml`
- Review: `.github/workflows/release-android.yml`
- Review: `.github/workflows/release-ios.yml`
- Review: `.github/workflows/release-docker.yml`

**Interfaces:**
- Consumes: the patch produced by Task 1 and the existing workflow patch order.
- Produces: evidence that v3.8.0 accepts the full patch stack, the update module compiles, public release data is compatible, and no workflow-specific source override is needed.

- [x] **Step 1: Apply the complete workflow patch order to a clean v3.8.0 checkout**

From the repository root:

```bash
stack_dir=$(mktemp -d /tmp/siyuan-update-stack.XXXXXX)
git clone --depth=1 --branch v3.8.0 https://github.com/siyuan-note/siyuan.git "$stack_dir/siyuan"
git -C "$stack_dir/siyuan" apply "$PWD/patches/siyuan/disable-update.patch"
git -C "$stack_dir/siyuan" apply "$PWD/patches/siyuan/default-config.patch"
git -C "$stack_dir/siyuan" apply "$PWD/patches/siyuan/mock-vip-user.patch"
git -C "$stack_dir/siyuan" apply "$PWD/patches/siyuan/hide-account-entry.patch"
git -C "$stack_dir/siyuan" apply "$PWD/patches/siyuan/first-launch-notice.patch"
git -C "$stack_dir/siyuan" diff --check
```

Expected: every `git apply` exits 0 and `git diff --check` prints no errors.

- [x] **Step 2: Run the affected Go test surface on the fully patched tree**

Run:

```bash
cd "$stack_dir/siyuan/kernel"
go test ./model -run '^(TestStableUpdateUsesUnlockGitHubRelease|TestIsReleaseAllowed|TestSelectGitHubRelease|TestParseChecksumManifest|TestNormalizeSHA256|TestSHA256Hash)$' -count=1
```

Expected: PASS with zero failures.

- [x] **Step 3: Verify restored entry points and disabled downloads in the patched result**

Read the patched files and confirm these executable contracts:

```bash
rg -n 'model.CheckUpdate\(showMsg\)|CheckUpdate\(true\)|DownloadInstallPkg: false|Conf.System.DownloadInstallPkg = false' \
  "$stack_dir/siyuan/kernel/api/system.go" \
  "$stack_dir/siyuan/kernel/model/mount.go" \
  "$stack_dir/siyuan/kernel/conf/system.go" \
  "$stack_dir/siyuan/kernel/model/updater.go"
```

Expected: all four behaviors are present exactly once in their owning files. This check supplements, but does not replace, the behavioral Go regression test.

- [x] **Step 4: Verify the live public GitHub Release contract**

Run:

```bash
gh api repos/appdev/siyuan-unlock/releases/tags/v3.8.0 \
  --jq '[.tag_name,.draft,.prerelease,.html_url] | @tsv'
gh api repos/appdev/siyuan-unlock/releases/tags/v3.8.0 \
  --jq '.assets[] | select(.name == "siyuan-3.8.0-win.exe" or .name == "siyuan-3.8.0-mac.dmg" or .name == "siyuan-3.8.0-mac-arm64.dmg") | [.name,.state,.digest] | @tsv'
```

Expected:

```text
v3.8.0    false    false    https://github.com/appdev/siyuan-unlock/releases/tag/v3.8.0
siyuan-3.8.0-win.exe        uploaded    sha256:<64 hexadecimal characters>
siyuan-3.8.0-mac.dmg        uploaded    sha256:<64 hexadecimal characters>
siyuan-3.8.0-mac-arm64.dmg  uploaded    sha256:<64 hexadecimal characters>
```

- [x] **Step 5: Review the final repository diff and hand off without publishing**

Run:

```bash
git diff --check
git status --short
git diff -- patches/siyuan/disable-update.patch docs/superpowers/specs/2026-08-14-github-release-update-check-design.md docs/superpowers/plans/2026-08-14-github-release-update-check.md
```

Confirm that only the approved update-source patch and its design/plan documents changed, with no credentials, generated build output, or temporary fixture files in the repository. Do not commit, push, trigger Actions, or replace published assets without separate user authorization.
