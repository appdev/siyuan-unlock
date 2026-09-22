#!/usr/bin/env bash
set -euo pipefail

upstream="$(cd "${1:?Usage: bash scripts/check-account-free.sh <patched-upstream>}" && pwd)"
scripts="$(cd "$(dirname "$0")" && pwd)"

test -f "$upstream/kernel/model/unlock_cloud_account_test.go"
test -f "$upstream/kernel/api/unlock_cloud_account_test.go"
node "$scripts/check-account-ui.mjs" "$upstream"
cd "$upstream/kernel"
go test -tags "fts5 sqlcipher" ./model ./api \
  -run '^(TestUnlock|TestCloudAccount|TestCloudRepoAuthFailure|TestCloudUserRefresh|TestAPIContractSettingErrorsAndAdmission)' \
  -count=1
