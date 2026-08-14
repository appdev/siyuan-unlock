# First-launch Anti-resale Notice Design

## Goal

Show a mandatory anti-resale notice once per installed desktop or mobile client
so users know that SiYuan Unlock is officially distributed free of charge and
can identify unauthorized paid distribution.

## Supported Clients

The notice applies to packaged PC and mobile clients:

- Windows, macOS, and Linux desktop clients
- Android and iOS clients

Browser and Docker-hosted web use do not show the notice. HarmonyOS is outside
the current release workflows and is not included.

## Notice Copy

The dialog uses Simplified Chinese only, regardless of the selected interface
language:

> 重要声明
>
> 本项目 SiYuan Unlock 为免费开源项目，官方始终免费提供，未授权任何个人或平台收费代售本项目、安装包、激活码或所谓售后服务。
>
> 如果您通过付费渠道获得本软件，请立即向交易平台申请退款并举报相关卖家，谨防后续收费及安全风险。
>
> 请仅从官方发布页面获取版本：
> https://github.com/appdev/siyuan-unlock/releases
>
> 感谢支持开源，共同抵制倒卖。

This wording avoids claiming that the AGPLv3 license prohibits charging for
distribution. It states the project's own free distribution policy and lack of
authorization for third-party resale.

## Architecture

Add a dedicated downstream patch, `patches/siyuan/first-launch-notice.patch`,
against the upstream SiYuan frontend. The patch introduces one small notice
module and calls it from the common startup flow after configuration and
language data are ready.

The module determines whether the current runtime is a packaged desktop client
or a supported mobile app. It returns without rendering on a normal browser,
the Docker/Web frontend, publish mode, or unsupported containers.

Desktop and mobile release workflows apply the new patch after the existing
SiYuan patches. The Docker workflow does not apply it.

## Device-level Persistence

Store a versioned acknowledgement in
`<HomeDir>/.config/siyuan/siyuan-unlock.json`, exposed through authenticated
kernel APIs. `HomeDir` is installation-scoped on desktop, Android, and iOS and
does not change with the kernel's random desktop port or the active workspace.
The frontend must not use origin-scoped WebView/Electron `localStorage` for
this state.

The acknowledgement is written only after the user clicks the enabled
acknowledgement button. Closing or terminating the application before that
point leaves the key absent, so the notice returns on the next launch. Clearing
application data or uninstalling the application resets the acknowledgement,
which is acceptable for an installation-scoped notice.

## Interaction

- Render the notice with the existing SiYuan `Dialog` component.
- Set `disableClose` and hide the close icon so the scrim and close control do
  not dismiss it.
- Prevent Escape and other global dialog-close paths from dismissing this
  specific dialog.
- Initially show a disabled `我已知晓（5）` button.
- Decrement the label once per second through `我已知晓（1）`.
- After five complete seconds, enable the button and label it `我已知晓`.
- Only clicking the enabled button writes the acknowledgement key and closes
  the dialog.
- Clear the timer if the dialog is destroyed during application teardown.
- Display the official release URL as a clickable external link and as visible
  text so users can inspect the destination.

## Error Handling

If reading the installation state fails, treat the notice as unread and show
it. If writing the acknowledgement fails, keep the dialog open and do not
silently mark it as read; the user can retry or will see it again after
restarting.

The countdown uses elapsed wall-clock time rather than trusting only an
interval count, preventing background timer throttling from shortening the
five-second minimum.

## Verification

- A focused test covers runtime eligibility, acknowledgement reads and writes,
  the five-second disabled state, button enablement, and failed persistence.
- `git apply --check` and sequential application validate the full downstream
  patch set against upstream `v3.8.0`.
- The frontend type check or production build validates imports and DOM code.
- Workflow inspection confirms the patch is included in desktop, Android, and
  iOS builds and excluded from Docker.
- Final diff review confirms no unrelated source or workflow behavior changed.

Full visual confirmation on actual desktop, Android, and iOS clients remains a
runtime verification step after the relevant Actions builds are run.

## Official-repository Build Marker

Create a repository-level GitHub Actions Secret named
`SIYUAN_UNLOCK_OFFICIAL_BUILD` in `appdev/siyuan-unlock` using `gh secret set`.
Generate its value locally from cryptographically random bytes, pipe it directly
to `gh`, and never print, persist, or commit the value.

Every release-capable workflow must begin with a guard step that requires both:

- `github.repository` is exactly `appdev/siyuan-unlock`.
- `secrets.SIYUAN_UNLOCK_OFFICIAL_BUILD` is non-empty.

The guard covers scheduled release orchestration, desktop, Android, iOS,
Docker, and AUR publishing. It exits non-zero with a concise message when the
repository identity or marker is absent. Repository secrets are not copied to
forks, so an unchanged fork cannot directly complete these workflows.

This is a workflow provenance guard, not DRM. A fork owner controls their own
repository and can edit or remove the workflow check; the project does not
claim that this mechanism prevents deliberate source modification.
