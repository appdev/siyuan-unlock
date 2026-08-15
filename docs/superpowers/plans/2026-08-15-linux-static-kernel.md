# Linux Static Kernel Implementation Plan

> **For agentic workers:** Use the global `workflow` skill's existing-plan execution entry. Review this plan against current evidence; when it is sound, enter execution directly. Only when material problems are found should `workflow` return to research, ideation, and planning to supplement this same plan before continuing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish Linux desktop packages whose `SiYuan-Kernel` is a static PIE and requires no system musl loader.

**Architecture:** Retain the existing musl cross-compilers, add Linux-only Go static PIE flags through matrix values, and reject Linux kernels containing ELF `INTERP` or `NEEDED` entries before packaging. Use the existing full desktop matrix for release validation, with all six current desktop assets downloaded first for exact-name rollback.

**Tech Stack:** GitHub Actions YAML, Go 1.26, musl cross-compilers, GNU `readelf`, `file`, `objdump`, `gh`, Ruby YAML parser, actionlint.

## Global Constraints

- Change Linux kernel linkage only; application behavior and non-Linux build flags remain unchanged.
- Preserve the current v3.8.0 tag and Release.
- The full desktop dispatch may replace three Linux, two macOS, and one Windows asset.
- Never upload new assets until all six existing desktop assets are stored locally with matching GitHub digests.
- A released Linux kernel must contain neither an ELF interpreter nor a dynamic `NEEDED` dependency.

---

### Task 1: Freeze rollback assets and reproduce the failure

**Files:**
- Modify later: `.codex/memory/task-20260815-linux-static-kernel.md`
- External backup: a validated `mktemp -d` directory outside the repository

**Interfaces:**
- Consumes: public GitHub Release `v3.8.0` and its six desktop assets.
- Produces: exact rollback files, asset IDs/digests, and a dynamic-linkage RED result.

- [x] **Step 1: Record the frozen Git and Release identities**

Run:

```bash
git status --short --branch
git rev-parse HEAD
git ls-remote origin refs/heads/master refs/tags/v3.8.0
gh release view v3.8.0 --json url,tagName,assets
```

Expected: clean tracked tree apart from this task's uncommitted documents;
`master`, tag SHA, six desktop asset IDs, names, sizes, and digests are captured.

- [x] **Step 2: Download all six desktop rollback assets**

Create an exact temporary directory with `mktemp -d`, then download these names:

```text
siyuan-3.8.0-linux-arm64.tar.gz
siyuan-3.8.0-linux.AppImage
siyuan-3.8.0-linux.tar.gz
siyuan-3.8.0-mac-arm64.dmg
siyuan-3.8.0-mac.dmg
siyuan-3.8.0-win.exe
```

Run `shasum -a 256` for every file and compare each value with the Release
digest before continuing.

- [x] **Step 3: Run the failing ELF regression check**

Extract `resources/kernel/SiYuan-Kernel` from the current amd64 tar and run:

```bash
file SiYuan-Kernel
objdump -p SiYuan-Kernel | grep -E 'INTERP|NEEDED'
go version -m SiYuan-Kernel | grep -E -- '-buildmode=|-ldflags='
```

Expected RED: `file` reports `dynamically linked`, `objdump` finds `INTERP` and
`NEEDED libc.so`, and Go build information reports `-buildmode=exe` without
`-extldflags -static-pie`.

### Task 2: Restore static PIE flags and add the release guard

**Files:**
- Modify: `.github/workflows/desktop-release.yml:18-65`
- Modify: `.github/workflows/desktop-release.yml:178-190`

**Interfaces:**
- Consumes: matrix properties `goos`, `goarch`, `kernel_path`, and `build_args`.
- Produces: matrix property `build_mode` and a pre-packaging Linux ELF gate.

- [x] **Step 1: Run a structural test and confirm it fails**

Use a read-only shell assertion that requires exactly three Linux
`build_mode: "-buildmode=pie"` values, three
`-extldflags -static-pie` linker values, and guard checks for both `INTERP` and
`NEEDED`.

Expected RED: the current workflow fails the assertion because all four
requirements are absent.

- [x] **Step 2: Add matrix-specific build modes and linker flags**

For each Linux matrix entry use:

```yaml
build_mode: "-buildmode=pie"
build_args: "-s -w -extldflags -static-pie -X github.com/siyuan-note/siyuan/kernel/util.Mode=prod"
```

For macOS and Windows use:

```yaml
build_mode: ""
```

Keep their existing `build_args` unchanged.

- [x] **Step 3: Pass the matrix build mode to Go**

Change the kernel command to:

```yaml
run: go build ${{ matrix.config.build_mode }} --tags "fts5 sqlcipher" -o "${{ matrix.config.kernel_path }}" -v -ldflags "${{ matrix.config.build_args }}"
```

- [x] **Step 4: Add the Linux ELF guard before Electron packaging**

Add this Linux-only step after `Building Kernel`:

```yaml
- name: Verify Linux kernel is static PIE
  if: contains(matrix.config.goos, 'linux')
  shell: bash
  working-directory: ${{ github.workspace }}/siyuan-note/siyuan/kernel
  run: |
    set -euo pipefail
    kernel_path="${{ matrix.config.kernel_path }}"
    file "${kernel_path}"
    build_info="$(go version -m "${kernel_path}")"
    program_headers="$(readelf -lW "${kernel_path}")"
    dynamic_section="$(readelf -dW "${kernel_path}" 2>/dev/null || true)"
    printf '%s\n' "${build_info}"
    printf '%s\n' "${program_headers}"
    printf '%s\n' "${dynamic_section}"

    if grep -Fq ' INTERP ' <<<"${program_headers}"; then
      echo "::error::Linux kernel contains an ELF interpreter"
      exit 1
    fi
    if grep -Fq '(NEEDED)' <<<"${dynamic_section}"; then
      echo "::error::Linux kernel contains dynamic dependencies"
      exit 1
    fi
    grep -F $'build\t-buildmode=pie' <<<"${build_info}"
    grep -F -- '-extldflags -static-pie' <<<"${build_info}"
```

- [x] **Step 5: Run local GREEN checks**

Run the structural assertion from Step 1, then:

```bash
ruby -e 'require "yaml"; YAML.safe_load_file(".github/workflows/desktop-release.yml", aliases: true)'
go run github.com/rhysd/actionlint/cmd/actionlint@latest .github/workflows/desktop-release.yml
git diff --check
git diff -- .github/workflows/desktop-release.yml
```

Expected GREEN: structural assertions pass, YAML parses, actionlint exits zero,
the diff contains only the planned matrix/build/guard changes, and there are no
whitespace errors.

### Task 3: Commit, push, and dispatch the desktop release

**Files:**
- Commit: `.github/workflows/desktop-release.yml`
- Commit: `docs/superpowers/specs/2026-08-15-linux-static-kernel-design.md`
- Commit: `docs/superpowers/plans/2026-08-15-linux-static-kernel.md`
- Commit: `.codex/memory/task-20260815-linux-static-kernel.md`

**Interfaces:**
- Consumes: locally verified workflow and six validated rollback assets.
- Produces: pushed `master` commit and one desktop release Actions run.

- [x] **Step 1: Append pre-push verification to the task trace**

Record RED/GREEN commands, rollback directory, original asset digests, and any
non-blocking warnings without storing credentials or raw logs.

- [x] **Step 2: Review and commit the exact scope**

Run:

```bash
git status --short
git diff --check
git diff --stat
git diff
git add .github/workflows/desktop-release.yml \
  docs/superpowers/specs/2026-08-15-linux-static-kernel-design.md \
  docs/superpowers/plans/2026-08-15-linux-static-kernel.md \
  .codex/memory/task-20260815-linux-static-kernel.md
git commit -m "fix: statically link Linux kernels"
git push origin master
```

- [x] **Step 3: Verify the frozen push**

Confirm local `HEAD` equals `refs/heads/master` on origin and that
`refs/tags/v3.8.0` is unchanged.

- [x] **Step 4: Dispatch and identify the exact run**

Use the package manager from upstream v3.8.0:

```bash
gh workflow run desktop-release.yml \
  -f version=v3.8.0 \
  -f packageManager=pnpm@11.18.0
```

Resolve the new run whose `headSha` equals the pushed commit, then watch it to
completion with `gh run watch RUN_ID --exit-status`.

- [x] **Step 5: Handle failures without compounding them**

If Actions fails, inspect the exact failed job logs before changing code. If no
asset was replaced, preserve the current Release. If replacement was partial,
restore all six rollback files with their original names using
`gh release upload v3.8.0 ... --clobber` before attempting another dispatch.

### Task 4: Validate published artifacts and complete the trace

**Files:**
- Modify: `.codex/memory/task-20260815-linux-static-kernel.md`

**Interfaces:**
- Consumes: successful Actions run and replaced v3.8.0 desktop assets.
- Produces: published ELF evidence, final task trace, and a clean synchronized repository.

- [x] **Step 1: Verify Release completeness**

Confirm all eight v3.8.0 assets remain present, the six desktop assets have
successful upload state and SHA-256 digests, and Android/iOS assets were not
replaced.

- [x] **Step 2: Download and inspect all three new Linux packages**

Download the amd64 tar, amd64 AppImage, and arm64 tar to a fresh temporary
directory. Extract tar kernels directly. Locate the AppImage SquashFS `hsqs`
offset and extract `resources/kernel/SiYuan-Kernel` with `unsquashfs`.

For every extracted kernel run:

```bash
file SiYuan-Kernel
objdump -p SiYuan-Kernel
strings SiYuan-Kernel | grep '/lib.*/ld-musl' || true
go version -m SiYuan-Kernel
```

Expected: static PIE, no musl loader string, no `INTERP`, no `NEEDED`, and Go
build information contains both required flags.

- [x] **Step 3: Append verification and handoff evidence**

Record the pushed commit, run ID and conclusion, final Release digests, all
three ELF results, rollback disposition, and residual warnings in the task
trace. Commit and push this documentation-only update as:

```bash
git add .codex/memory/task-20260815-linux-static-kernel.md
git commit -m "docs: record Linux static kernel verification"
git push origin master
```

- [x] **Step 4: Perform the final frozen check**

Confirm the working tree is clean, local `HEAD` equals remote `master`, the
v3.8.0 tag is unchanged, the Actions conclusion is `success`, the Release has
eight assets, and the three published Linux kernels satisfy every ELF gate.

- [x] **Step 5: Remove temporary rollback and extraction data**

Only after the frozen check succeeds, delete the exact task-created temporary
directories and verify they no longer exist. Do not touch unrelated downloads,
applications, workspaces, or user configuration.
