#!/usr/bin/env bash
set -euo pipefail

repo="$(cd "$(dirname "$0")/.." && pwd)"
upstream="$(cd "${1:?Usage: bash scripts/apply-qingyu-storage.sh <patched-upstream>}" && pwd)"
module=github.com/siyuan-note/dejavu

# The account guard is shared by upstream versions with different return values.
git -C "$upstream" apply --unidiff-zero "$repo/patches/siyuan/qingyu-storage.patch"
cd "$upstream/kernel"
go mod download "$module"
module_dir="$(go list -m -f '{{.Dir}}' "$module")"
if command -v cygpath >/dev/null; then
  module_dir="$(cygpath -u "$module_dir")"
fi
test -n "$module_dir"
mkdir qingyu-dejavu
cp -R "$module_dir/." qingyu-dejavu/
chmod -R u+w qingyu-dejavu
git -C "$upstream" apply --directory=kernel/qingyu-dejavu "$repo/patches/dejavu/qingyu-s3.patch"
go mod edit -replace "$module=./qingyu-dejavu"
