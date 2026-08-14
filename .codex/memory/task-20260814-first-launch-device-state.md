# Task trace: first-launch device state and v3.8.0 republish

- Type: `task_start`
- Risk: `L3`
- Started: 2026-08-14 Asia/Shanghai
- Objective: persist the anti-resale acknowledgement once per installation across desktop ports and workspaces, republish `v3.8.0`, then test Android and macOS artifacts.
- Authorized external actions: delete and recreate GitHub Release/Tag `v3.8.0`, commit, push `master`, trigger release workflows, and download/test new artifacts.
- Frozen rollback commit: `2570ded9b64b7a29e02afd89044f604e1ee7f6bd` (old remote `v3.8.0` tag).
- Pre-change master: `2db6c9a6247b8e64f03590957e5b65c2a60e726a`.
- Existing Release: `RE_kwDOKhxsGc4WCl8B`, public, 8 assets, body `action release`.
- Memory tooling: `codex-memory` CLI unavailable; this project-local note is the approved fallback trace.

## Context and decision

- Root cause: desktop uses a random HTTPS kernel port, while the acknowledgement is stored in origin-scoped browser `localStorage`.
- Decision: move acknowledgement persistence behind kernel APIs and store it under the installation-level `HomeDir/.config/siyuan/` directory shared by all workspaces.
- Failure policy: a read failure is treated as unread; a write failure keeps the mandatory dialog open.
- Release containment: finish and verify the local patch before deleting the public Release; restore the old tag/release from the frozen SHA if rebuilding fails.

## Verification and handoff

- TDD red: Go model test failed with missing `FirstLaunchNoticeAcknowledged` and `AcknowledgeFirstLaunchNotice`; API test failed with missing handlers; Node test failed with missing installation API exports.
- TDD green: focused Go model/API tests and five Node state tests passed.
- Clean upstream verification root: `/tmp/siyuan-device-verify.WIjAu2/siyuan` at upstream `251596fc0de2f9528c00c224252fd073a99973f4` (`v3.8.0`).
- Complete downstream patch order applied with `git apply --check`; `git diff --check` passed.
- `pnpm run typecheck` passed.
- `pnpm run build` passed for app, mobile, desktop, and export (existing webpack asset-size warnings only).
- Focused `go test ./model ./api -run 'TestFirstLaunchNotice|TestGetGitHubUpdateReleaseForStableChannel' -count=1` passed.
- Broad `go test ./model ./api` was attempted and failed in unrelated existing macOS path-resolution tests (`/var` versus `/private/var`) plus unrelated baseline assertions; no first-launch test failed.

- Fix commit and release tag: `a2c9ffae95426c378cb427994f1c664a479e6688`.
- Recreated public `v3.8.0` Release: <https://github.com/appdev/siyuan-unlock/releases/tag/v3.8.0>, with all eight expected assets.
- Release workflows succeeded: orchestrator `31789509631`, Android `31789522994`, desktop `31789524269`, iOS `31789526797`, and Docker `31789525515` (Docker completed in `1h7m25s`).
- Downloaded Android arm64 APK and macOS arm64 DMG; local SHA-256 values matched GitHub Release digests:
  - APK: `85892774f936f21f9bca419644ec4faa4665a99d96559ca5cc6e2c6dc60c814d`
  - DMG: `5064a424a8aa074417d4611c34c5b764a3d90a73bdc6474b65704dd37352e8ca`
- Android runtime verification on `emulator-5554`:
  - removed a stale app-external test directory owned by the prior package UID, reinstalled version `3.8.0` (`versionCode=348`), and cold-launched successfully;
  - the notice appeared on first launch, acknowledgement created `HomeDir/.config/siyuan/siyuan-unlock.json` with `{"antiResaleNotice":1}`, and a subsequent cold launch did not show the notice;
  - no fatal, permission-denied, or log-directory errors remained after the clean reinstall.
- macOS runtime verification from the downloaded arm64 DMG:
  - `workspace-a` showed the complete Simplified Chinese notice and accepted `我已知晓`;
  - a cold launch with `workspace-b` did not show the notice, confirming installation-level persistence across workspaces;
  - the DMG, app, and kernel were stopped after verification, and the exact test-created marker in the real account HomeDir was removed.
- Handoff: Android remains installed on the emulator for optional manual inspection; downloaded artifacts and screenshots remain under `/tmp/siyuan-v3.8.0-test.V9GgU5/`.
