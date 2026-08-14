# GitHub Release 更新检查设计

## 背景

当前 `patches/siyuan/disable-update.patch` 同时禁用了 `/api/system/checkUpdate`、打开帮助文档后的延迟检查以及自动下载安装包。用户因此无法从应用内得知 SiYuan Unlock 是否发布了新版本。

上游 SiYuan v3.8.0 的稳定通道通过思源云端版本接口获取版本信息，Beta/Alpha 通道才直接查询 `siyuan-note/siyuan` 的 GitHub Releases API。该数据流会把 SiYuan Unlock 用户引向上游版本和发布页面，不能直接恢复。

## 目标

- 恢复“关于”页的手动检查更新。
- 恢复打开帮助文档 10 秒后的自动检查提醒。
- Stable、Beta、Alpha 通道都只查询 `appdev/siyuan-unlock` 的 GitHub Releases。
- 新版本通知中的链接指向 SiYuan Unlock 的对应 GitHub Release。
- 保持自动下载安装包关闭，不在后台下载或退出时安装更新。
- PC、Android 和 iOS 共用同一版本来源；Docker 沿用工作流当前补丁但不产生安装包下载行为。

## 非目标

- 不实现应用内一键升级。
- 不新增独立更新服务器、代理或 GitHub Token。
- 不改变 Release 的标签规范、资产命名或发布工作流。
- 不改变上游的版本通道筛选和语义版本比较规则。

## 方案

继续复用上游 `updater_release.go` 已有的 GitHub Release 解析、缓存、通道筛选、资产 digest 校验和通知逻辑，仅在补丁层修改数据源与禁用边界。

### 版本数据流

1. 用户点击“检查更新”，或打开帮助文档 10 秒后，调用原有 `model.CheckUpdate(true)`。
2. `getUpdateRelease` 校验当前更新通道后，所有通道都进入 `getGitHubUpdateRelease`，不再调用思源云端稳定版接口。
3. GitHub API 请求地址改为：

   `https://api.github.com/repos/appdev/siyuan-unlock/releases?per_page=100`

4. 沿用上游规则选择最高版本：
   - Stable：只接受正式版本。
   - Beta：接受正式版、Beta 和 RC。
   - Alpha：接受正式版、Alpha、Beta 和 RC。
5. 使用语义版本比较当前内核版本与 Release 标签。
6. 有新版本时显示该 Release 返回的 `html_url`；缺失时回退到：

   `https://github.com/appdev/siyuan-unlock/releases/tag/v<version>`

### 下载行为

保留现有补丁对 `DownloadInstallPkg` 的关闭逻辑。检查更新仅产生“已是最新版本”或“发现新版本”的提示，不下载资产、不准备安装包，也不在退出时触发安装。

Android、iOS、Linux 和 Docker 本来就不属于上游自动安装支持范围；Windows 和 macOS 由补丁强制关闭该能力。

### 失败处理

- GitHub 请求失败、限流、返回空列表或没有匹配通道的版本时，沿用上游日志与无更新提示行为，不回退到思源云端。
- Draft Release 被忽略。
- 非法语义版本标签被忽略。
- GitHub Release 页面 URL 来自 API 响应，避免手工拼接与实际标签不一致；只有缺失时才使用项目 URL 前缀回退。

## 修改范围

主要修改 `patches/siyuan/disable-update.patch`：

- 删除对 `kernel/api/system.go` 中 `checkUpdate` 的禁用。
- 删除对 `kernel/model/mount.go` 中延迟检查的禁用。
- 保留 `kernel/conf/system.go` 和 `kernel/model/updater.go` 中关闭自动下载的补丁。
- 新增对 `kernel/model/updater_release.go` 的补丁，将 GitHub 仓库 URL 改为 `appdev/siyuan-unlock`，并让 Stable 通道也走 GitHub Release 选择逻辑。
- 视测试需要补充 `kernel/model/updater_release_test.go` 的补丁，验证稳定通道不会重新进入思源云端数据流。

现有 PC、Android、iOS 和 Docker 工作流继续应用同一个补丁文件，无需新增工作流分支。

## 验证

1. 在干净的 SiYuan v3.8.0 源码上按工作流顺序应用全部补丁，确认无冲突。
2. 运行更新 Release 选择相关 Go 单元测试。
3. 用受控 GitHub Release 数据验证 Stable/Beta/Alpha 筛选和通知 URL。
4. 静态确认补丁后的 `/api/system/checkUpdate` 仍调用 `model.CheckUpdate`，帮助文档延迟检查仍存在。
5. 静态或单元测试确认 `DownloadInstallPkg` 默认及运行时均为 `false`。
6. 运行与补丁影响相称的 Go 编译或测试，确保更新模块可构建。

## 风险与约束

- GitHub 未认证 API 受公共限流约束；上游已有 6 小时缓存和请求合并，可降低请求量。
- 本项目 Release 必须继续使用可解析的语义版本标签，例如 `v3.8.0`。
- 若未来需要恢复自动下载，桌面资产名目前与上游规则兼容，但必须另行评审安装安全和回滚策略；本设计不启用该行为。
