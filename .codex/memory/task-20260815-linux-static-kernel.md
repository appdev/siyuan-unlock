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
