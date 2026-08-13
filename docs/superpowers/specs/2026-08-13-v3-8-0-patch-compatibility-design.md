# SiYuan v3.8.0 Patch Compatibility Design

## Goal

Restore all release workflows by making the downstream patch set apply cleanly to
the upstream `siyuan-note/siyuan` `v3.8.0` tag while preserving the existing
downstream behavior.

## Scope

Update only the incompatible patch files:

- `patches/siyuan/disable-update.patch`
- `patches/siyuan/default-config.patch`
- `patches/siyuan/hide-account-entry.patch`

`patches/siyuan/mock-vip-user.patch` already applies to `v3.8.0` and remains
unchanged. Release workflows, upstream version selection, application code, and
release assets are outside this change.

## Approach

Regenerate the failing hunks against the exact upstream commit referenced by
`v3.8.0` (`251596fc0de2f9528c00c224252fd073a99973f4`). Preserve each patch's current
intent and include only the minimum upstream context needed by `git apply`.

The resulting patch sequence must continue to be applied in the workflow's
existing order:

1. `disable-update.patch`
2. `default-config.patch`
3. `mock-vip-user.patch`
4. `hide-account-entry.patch`

## Behavioral Requirements

- Automatic update package download remains disabled.
- The existing downstream sync, account, appearance, and account-entry defaults
  remain unchanged.
- Mock VIP user behavior remains unchanged.
- No workflow logic or release version is changed.

## Verification

Use the release workflow's real patch application behavior as the regression
test:

1. Record that the current patch sequence fails against a clean `v3.8.0`
   checkout.
2. Apply the updated patch sequence to another clean `v3.8.0` checkout using
   `git apply --check`, then apply the patches in order.
3. Inspect the resulting diff to confirm only the intended downstream changes
   are present.
4. Repeat the check on the exact upstream tag commit and review the final
   repository diff for unrelated changes or generated files.

## Risks and Boundaries

Passing `git apply` proves patch compatibility but does not prove every platform
build succeeds. Full GitHub-hosted iOS, Android, Docker, and desktop builds remain
unverified until the workflows are rerun. This change will not push commits,
publish assets, or trigger workflows.
