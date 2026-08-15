# Task trace: Linux static kernel

- Type: `task_start`
- Risk: `L3`
- Started: 2026-08-15 Asia/Shanghai
- Objective: restore static PIE Linux kernels, verify locally, commit and push,
  dispatch desktop release Actions, and inspect the replaced Linux artifacts.
- Authorized external actions: commit, push `master`, trigger the desktop
  release workflow, and replace all six v3.8.0 desktop release assets through
  that workflow.
- Memory tooling: `codex-memory` CLI is unavailable; this project-local note is
  the fallback trace.

## Context and decision

- Downstream v3.8.0 amd64 kernel: dynamic ELF, interpreter
  `/lib/ld-musl-x86_64.so.1`, `NEEDED libc.so`, build mode `exe`.
- Official v3.8.0 amd64 kernel: static PIE, build mode `pie`, linker flag
  `-extldflags -static-pie`.
- Decision: retain musl cross-compilers, restore static PIE flags only for Linux,
  and add an ELF guard before packaging/upload.
- Dispatch decision: use the existing full six-platform desktop matrix. Back up
  all three Linux, two macOS, and one Windows v3.8.0 assets before dispatch so a
  partial or invalid replacement can be rolled back without changing the tag or
  Release.
- Rollback evidence, implementation, verification, Actions results, and handoff
  will be appended during execution.

## Pre-push verification

- Frozen pre-change `master`: `e3a078e14606b934a4975aa58efaf0bfcf3ecda7`.
- Frozen `v3.8.0` tag: `a2c9ffae95426c378cb427994f1c664a479e6688`.
- Rollback directory: `/tmp/siyuan-linux-static-rollback.uJ0FCU`.
- All six desktop rollback downloads matched the Release SHA-256 digests:
  - Linux arm64 tar: `53442b8ecfd74c23a5ed109474b8e257e13c7d79ddacb18552e20f928bfe2093`
  - Linux AppImage: `d7ada2cccc883e2b419283c12fc25cf434ac1e115a445878690161af2174997c`
  - Linux amd64 tar: `f8c69fb321b8a247103f914d761ef8de4d100ce7f0d5d46069202d253dcf115a`
  - macOS arm64 DMG: `5064a424a8aa074417d4611c34c5b764a3d90a73bdc6474b65704dd37352e8ca`
  - macOS x64 DMG: `d3c03fe6f6a3bec2c574fd51f338310da06021a4fe66b7019420a282145f013d`
  - Windows installer: `ffe7a3aae128356bc4f8d161e8727e936616de5ce75227fad3d206548bd6177d`
- RED artifact evidence: the published amd64 kernel was dynamically linked,
  used `/lib/ld-musl-x86_64.so.1`, contained `INTERP` and `NEEDED libc.so`, and
  recorded `-buildmode=exe` without static external-linker flags.
- RED structural test: the workflow contained zero Linux static build modes,
  zero Linux static linker flags, and no `INTERP` or `NEEDED` guard.
- GREEN local checks after the workflow edit:
  - structural counts: 3 Linux PIE modes, 3 Linux static linker flags, one
    `INTERP` guard, and one `NEEDED` guard;
  - Ruby YAML parse passed;
  - actionlint v1.7.12 passed;
  - `git diff --check` passed.

## Publish and artifact verification

- Implementation commit: `47be6bfe46dd6b9bc2034b3bee7032ec259824ba`
  (`fix: statically link Linux kernels`), pushed to `origin/master`.
- Desktop release run: `31869974256`, conclusion `success`, head SHA matched
  the implementation commit. All six matrix jobs succeeded. Each of the three
  Linux jobs passed `Verify Linux kernel is static PIE` before packaging.
- Release `v3.8.0` remained complete with eight assets. Android and iOS were
  unchanged:
  - Android asset `RA_kwDOKhxsGc4epr8-`, SHA-256
    `85892774f936f21f9bca419644ec4faa4665a99d96559ca5cc6e2c6dc60c814d`;
  - iOS asset `RA_kwDOKhxsGc4eptFP`, SHA-256
    `3c6b564a58fdb2ece0b4ff1ea6c35aae00924dc8a8414b464cab27fa0a93ec68`.
- Replaced desktop asset SHA-256 digests:
  - Linux arm64 tar: `509b74e9421076c9028bb5eb901d74457c747be5e945b7a69f6fde2af643e8ed`
  - Linux AppImage: `38848f5b1a3faa956f15e1fbcda5bcdcf21ab735ee229c21a30a52c6e243e520`
  - Linux amd64 tar: `39c60e3cfd3e8d22813144d2f7984713c498e6749016c9dcbba81dcaa7ec8724`
  - macOS arm64 DMG: `51f212438026a35b842e9013a048362a2ac347f50f66061e646685c01f5c1fae`
  - macOS x64 DMG: `0bc920ad2bd0e64e8e92e844692cf2e7d5da009c70f0a44d3b2797f54c6c90a1`
  - Windows installer: `e09d12514648a851f5a635f4bf1a4f9bf9173b284189196a918e67162ec89324`
- Fresh downloads of all three Linux packages matched the Release digests.
  Their packaged kernels passed the post-publish gate:
  - amd64 tar kernel: x86-64 static PIE, SHA-256
    `8725b3e0a4e89b040a9a1774ffd73dd9b0fd1474ded3120ad70aa3c5dc3b5f11`;
  - amd64 AppImage kernel: x86-64 static PIE, the same kernel SHA-256
    `8725b3e0a4e89b040a9a1774ffd73dd9b0fd1474ded3120ad70aa3c5dc3b5f11`;
  - arm64 tar kernel: AArch64 static PIE, SHA-256
    `b047aa7c81427939f77376b5aac9d40451f3147861fc2e7a77510b8aeadfad8d`.
- Every packaged kernel had no ELF `INTERP`, no `NEEDED` dependency, no musl
  or glibc dynamic-loader string, and recorded both `-buildmode=pie` and
  `-extldflags -static-pie` in Go build metadata.
- Residual warning: GitHub currently forces Node.js 20-based actions to run on
  Node.js 24. This warning did not affect any build or upload result.
- Rollback assets remain in `/tmp/siyuan-linux-static-rollback.uJ0FCU` until
  the final documentation commit and frozen-state check succeed.
