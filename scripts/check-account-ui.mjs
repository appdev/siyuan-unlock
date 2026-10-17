import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {resolve} from 'node:path';
import {runInNewContext} from 'node:vm';

// Exercise the real settings registration without mounting unrelated panels.
const upstream = process.argv[2];
assert.ok(upstream, 'Usage: node scripts/check-account-ui.mjs <patched-upstream>');
const load = path => stripTypeScriptTypes(readFileSync(resolve(upstream, path), 'utf8'))
    .replace(/^import .*;$/gm, '').replace(/^export /gm, '');
const source = load('app/src/config/tabs/syncTab.ts');
const groups = [];
const group = new Proxy({}, {get: () => () => {}});
const context = {
    window: {siyuan: {languages: {}, config: {system: {container: 'docker'}}}},
    getSyncProviderConfigKeywords: () => [],
    getSyncProviderConfigRowKeywords: () => [],
    mountSyncProvider() {},
    mountSyncAssetDownloadMode() {},
    mountLANSyncStatus() {},
    getLANSyncSearchAvailability() {},
    registerAccountGroup() { groups.push('account'); },
    tab: {group(id) { groups.push(id); return group; }},
};
runInNewContext(`${source}\nregisterSyncTab(tab);`, context);
assert.deepEqual(groups, ['sync', 'repo'], 'Sync settings must contain no account/login group');
console.log('PASS: sync settings register only sync and local repository groups');

const panel = {innerHTML: '', querySelector: () => null, querySelectorAll: () => [], addEventListener() {}};
const configRow = {...panel, classList: {toggle() {}}};
const syncContext = {
    window: {siyuan: {user: {userSiYuanOneTimePayStatus: 1}, languages: {_kernel: {214: 'LOGIN_REQUIRED'}},
        config: {sync: {provider: 2, s3: {}, webdav: {}}}}},
    getCloudURL: () => '',
    getHostCapabilities: () => ({importExport: false}),
    escapeHtmlTextAndAttr: value => String(value ?? '').replace(/[&<>"']/g, char => `&#${char.charCodeAt(0)};`),
    root: {querySelector: selector => selector === '#syncProviderConfigRow' ? configRow : panel},
};
const renderSource = load('app/src/util/needSubscribe.ts') + '\n' + load('app/src/config/tabs/syncUi.ts');
for (const provider of [2, 3]) {
    syncContext.window.siyuan.config.sync.provider = provider;
    runInNewContext(`${renderSource}\nrenderProviderConfig(root);`, {...syncContext});
    assert.match(configRow.innerHTML, /data-action="configureSync"/, 'Third-party sync configuration must be editable');
    assert.doesNotMatch(panel.innerHTML, /LOGIN_REQUIRED/);
}
syncContext.window.siyuan.user = null;
runInNewContext(`${renderSource}\nrenderProviderConfig(root);`, {...syncContext});
assert.match(panel.innerHTML, /LOGIN_REQUIRED/, 'Control case must detect the reported missing-user regression');
assert.equal(configRow.innerHTML, '', 'Missing-user control must hide the provider configuration row');
console.log('PASS: S3/WebDAV configuration renders for local unlock state; missing-user control is blocked');
