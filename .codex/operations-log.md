# Operations Log

## 2026-05-25 10:21 CST - Codex

- Task: 屏蔽设置中的账号入口，维护 release 使用的 `patches/siyuan` 补丁。
- Level: L2. 影响前端设置入口和移动端菜单入口；不改后端账号接口和 mock 用户逻辑。
- Tools:
  - `find patches -maxdepth 3 -type f | sort`: 确认现有补丁目录包含 `patches/siyuan/{disable-update,default-config,mock-vip-user}.patch`。
  - `rg -n "mock-vip-user\\.patch" .github/workflows -S`: 确认四个 release 工作流都显式应用 siyuan 补丁。
- Decision: 新增独立 `patches/siyuan/hide-account-entry.patch`，而不是混入 mock 用户补丁，便于后续单独维护/回滚。
- Change summary:
  - 隐藏桌面设置侧栏账号 tab。
  - 清空设置搜索中的账号索引，避免搜索间接打开账号页。
  - 禁用桌面顶栏 VIP 图标跳转账号页。
  - 移除移动端右侧菜单顶部账号/登录入口渲染。
- Verification:
  - `git apply --check patches/siyuan/hide-account-entry.patch`: 通过。
  - `git apply --check patches/siyuan/disable-update.patch patches/siyuan/default-config.patch patches/siyuan/mock-vip-user.patch patches/siyuan/hide-account-entry.patch`: 通过，确认 release 中现有 siyuan 补丁顺序可继续应用。
  - `rg -n "hide-account-entry\\.patch" .github/workflows -S`: 四个 release 工作流均已接入。
