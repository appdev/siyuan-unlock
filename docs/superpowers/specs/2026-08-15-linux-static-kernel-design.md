# Linux static kernel build design

## Goal

Make every released Linux `SiYuan-Kernel` a self-contained static PIE so that
users do not need a system musl loader. Preserve the existing application,
patches, supported architectures, and release asset names.

## Root cause

The released downstream amd64 kernel is a dynamically linked ELF with
interpreter `/lib/ld-musl-x86_64.so.1`, a `libc.so` dependency, Go build mode
`exe`, and no static external-linker flags. On distributions without that musl
loader the kernel cannot execute; Electron then times out while polling the
kernel API and reports the generic `Failed to obtain kernel service port`
message.

The official v3.8.0 kernel is `static-pie linked`. Its embedded Go build
information records `-buildmode=pie` and `-extldflags -static-pie`. The
repository's `scripts/linux-build.sh` uses the same flags, but the downstream
desktop release workflow does not.

## Design

Keep the current musl cross-compilers and change only the Linux matrix entries
in `.github/workflows/desktop-release.yml`:

- pass `-buildmode=pie` to `go build`;
- add `-extldflags -static-pie` to the Linux linker flags;
- leave macOS and Windows build modes and linker flags unchanged.

After building each Linux kernel, run an ELF guard with `readelf`:

- fail if the program headers contain `INTERP`;
- fail if the dynamic section contains `NEEDED`;
- print `file` and relevant `readelf` output as Actions evidence.

The guard runs before Electron packaging and release upload, so a dynamically
linked regression cannot replace a public asset.

## Verification

The existing v3.8.0 downstream kernel is the failing baseline: it contains both
`INTERP` and `NEEDED`. Before pushing, validate workflow syntax, shell syntax,
matrix-specific flags, and the final diff.

After pushing, manually dispatch the desktop release workflow for upstream tag
`v3.8.0` with the exact package manager declared by that tag. On success,
download the replaced amd64 tar, amd64 AppImage, and arm64 tar and inspect every
packaged kernel. Each must:

- be reported as static PIE;
- contain no musl interpreter string;
- contain neither `INTERP` nor `NEEDED`;
- record `-buildmode=pie` and `-extldflags -static-pie` in Go build information.

## Release and rollback

The existing desktop workflow has one six-platform matrix and therefore
rebuilds and replaces all six v3.8.0 desktop assets: three Linux packages, two
macOS DMGs, and one Windows installer. This full desktop dispatch is preferred
over adding a new Linux-only workflow interface because it keeps the production
release route unchanged.

Before dispatch, record the current asset IDs and digests and download all six
desktop assets as rollback copies. If the workflow fails before upload, the
existing assets remain. If it partially replaces assets or produces an invalid
Linux package, restore all six previous assets under their original names and
retain the same Release and tag.

## Non-goals

- Bundling a dynamic musl loader in tar or AppImage packages.
- Switching the kernel to dynamically linked glibc.
- Changing application behavior or non-Linux build outputs.
