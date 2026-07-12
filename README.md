
### Docker 部署

<details>
<summary>Docker 部署文档</summary>  
</br>       
   
**部署 Docker 版本并不是必须的**    
   
#### 概述

在服务器上伺服思源最简单的方案是通过 Docker 部署。

* 镜像名称 `apkdv/siyuan-unlock`
* [镜像地址](https://hub.docker.com/r/apkdv/siyuan-unlock)

#### 文件结构

整体程序位于 `/opt/siyuan/` 下，基本上就是 Electron 安装包 resources 文件夹下的结构：

* appearance：图标、主题、多语言
* guide：帮助文档
* stage：界面和静态资源
* kernel：内核程序

#### 启动入口

入口点在构建 Docker 镜像时设置：`ENTRYPOINT ["/opt/siyuan/entrypoint.sh"]`。该脚本允许更改将在容器内运行的用户的 `PUID` 和 `PGID`。这对于解决从主机挂载目录时的权限问题尤为重要。`PUID` 和 `PGID` 可以作为环境变量传递，这样在访问主机挂载的目录时就能更容易地确保正确的权限。

使用 `docker run apkdv/siyuan-unlock` 运行容器时，请带入以下参数：

* `--workspace`：指定工作空间文件夹路径，在宿主机上通过 `-v` 挂载到容器中
* `--accessAuthCode`：指定访问授权码

更多的参数可参考 `serve --help`。下面是一条启动命令示例：

```bash
docker run -d \
  -v workspace_dir_host:workspace_dir_container \
  -p 6806:6806 \
  -e PUID=1001 -e PGID=1002 \
  apkdv/siyuan-unlock \
  serve \
  --workspace=workspace_dir_container \
  --accessAuthCode=xxx
```

* `PUID`: 自定义用户 ID（可选，如果未提供，默认为 `1000`）
* `PGID`: 自定义组 ID（可选，如果未提供，默认为 `1000`）
* `workspace_dir_host`：宿主机上的工作空间文件夹路径
* `workspace_dir_container`：容器内工作空间文件夹路径，和后面 `--workspace` 指定成一样的
  * 另外，也可以通过 `SIYUAN_WORKSPACE_PATH` 环境变量设置路径。如果两者都设置了，命令行的值将优先
* `accessAuthCode`：访问授权码，请**务必修改**，否则任何人都可以读写你的数据
  * 另外，也可以通过 `SIYUAN_ACCESS_AUTH_CODE` 环境变量设置授权码。如果两者都设置了，命令行的值将优先
  * 可通过设置环境变量 `SIYUAN_ACCESS_AUTH_CODE_BYPASS=true` 禁用访问授权码

为了简化，建议将 workspace 文件夹路径在宿主机和容器上配置为一致的，比如将 `workspace_dir_host` 和 `workspace_dir_container` 都配置为 `/siyuan/workspace`，对应的启动命令示例：

```bash
docker run -d \
  -v /siyuan/workspace:/siyuan/workspace \
  -p 6806:6806 \
  -e PUID=1001 -e PGID=1002 \
  apkdv/siyuan-unlock \
  serve \
  --workspace=/siyuan/workspace/ \
  --accessAuthCode=xxx
```

#### Docker Compose

对于使用 Docker Compose 运行思源的用户，可以通过环境变量 `PUID` 和 `PGID` 来自定义用户和组的 ID。下面是一个 Docker Compose 配置示例：

```yaml
version: "3.9"
services:
  main:
    image: apkdv/siyuan-unlock
    command: ['serve', '--workspace=/siyuan/workspace/', '--accessAuthCode=${AuthCode}']
    ports:
      - 6806:6806
    volumes:
      - /siyuan/workspace:/siyuan/workspace
    restart: unless-stopped
    environment:
      # A list of time zone identifiers can be found at https://en.wikipedia.org/wiki/List_of_tz_database_time_zones
      - TZ=${YOUR_TIME_ZONE}
      - PUID=${YOUR_USER_PUID}  # 自定义用户 ID
      - PGID=${YOUR_USER_PGID}  # 自定义组 ID
```

在此设置中：

* PUID “和 ”PGID "是动态设置并传递给容器的
* 如果没有提供这些变量，将使用默认的 `1000`

在环境中指定 `PUID` 和 `PGID` 后，就无需在组成文件中明确设置 `user` 指令（`user: '1000:1000'`）。容器将在启动时根据这些环境变量动态调整用户和组。

#### 用户权限

在图片中，“entrypoint.sh ”脚本确保以指定的 “PUID ”和 “PGID ”创建 “siyuan ”用户和组。因此，当主机创建工作区文件夹时，请注意设置文件夹的用户和组所有权，使其与计划使用的 `PUID` 和 `PGID` 匹配。例如

```bash
chown -R 1001:1002 /siyuan/workspace
```

如果使用自定义的 `PUID` 和 `PGID` 值，入口点脚本将确保在容器内创建正确的用户和组，并相应调整挂载卷的所有权。无需在 `docker run` 或 `docker-compose` 中手动传递 `-u`，因为环境变量会处理自定义。

#### 隐藏端口

使用 NGINX 反向代理可以隐藏 6806 端口，请注意：

* 配置 WebSocket 反代 `/ws`

#### 注意

* 请务必确认挂载卷的正确性，否则容器删除后数据会丢失
* 不要使用 URL 重写进行重定向，否则鉴权可能会有问题，建议配置反向代理

#### 限制

* 不支持桌面端和移动端应用连接，仅支持在浏览器上使用
* 不支持导出 PDF、HTML 和 Word 格式
* 不支持导入 Markdown 文件

</details>


<p align="center">
<img alt="SiYuan" src="https://b3log.org/images/brand/siyuan-128.png">
<br>
<em>重构你的思维</em>
<br><br>
<a title="Build Status" target="_blank" href="https://github.com/siyuan-note/appdev/actions/workflows/ci.yml"><img src="https://img.shields.io/github/actions/workflow/status/siyuan-note/appdev/cd.yml?style=flat-square"></a>
<a title="Releases" target="_blank" href="https://github.com/siyuan-note/appdev/releases"><img src="https://img.shields.io/github/release/siyuan-note/siyuan.svg?style=flat-square&color=9CF"></a>
<a title="Downloads" target="_blank" href="https://github.com/siyuan-note/appdev/releases"><img src="https://img.shields.io/github/downloads/siyuan-note/appdev/total.svg?style=flat-square&color=blueviolet"></a>
<br>
<a title="Docker Pulls" target="_blank" href="https://hub.docker.com/r/apkdv/siyuan-unlock"><img src="https://img.shields.io/docker/pulls/b3log/siyuan.svg?style=flat-square&color=green"></a>
<a title="Docker Image Size" target="_blank" href="https://hub.docker.com/r/apkdv/siyuan-unlock"><img src="https://img.shields.io/docker/image-size/b3log/siyuan.svg?style=flat-square&color=ff96b4"></a>
<a title="Hits" target="_blank" href="https://github.com/siyuan-note/appdev"><img src="https://hits.b3log.org/siyuan-note/siyuan.svg"></a>
<br>
<a title="AGPLv3" target="_blank" href="https://www.gnu.org/licenses/agpl-3.0.txt"><img src="http://img.shields.io/badge/license-AGPLv3-orange.svg?style=flat-square"></a>
<a title="Code Size" target="_blank" href="https://github.com/siyuan-note/appdev"><img src="https://img.shields.io/github/languages/code-size/siyuan-note/siyuan.svg?style=flat-square&color=yellow"></a>
<a title="GitHub Pull Requests" target="_blank" href="https://github.com/siyuan-note/appdev/pulls"><img src="https://img.shields.io/github/issues-pr-closed/siyuan-note/siyuan.svg?style=flat-square&color=FF9966"></a>
<br>
<a title="GitHub Commits" target="_blank" href="https://github.com/siyuan-note/appdev/commits/master"><img src="https://img.shields.io/github/commit-activity/m/siyuan-note/siyuan.svg?style=flat-square"></a>
<a title="Last Commit" target="_blank" href="https://github.com/siyuan-note/appdev/commits/master"><img src="https://img.shields.io/github/last-commit/siyuan-note/siyuan.svg?style=flat-square&color=FF9900"></a>
<br><br>
<a title="Twitter" target="_blank" href="https://twitter.com/b3logos"><img alt="Twitter Follow" src="https://img.shields.io/twitter/follow/b3logos?label=Follow&style=social"></a>
<a title="Discord" target="_blank" href="https://discord.gg/dmMbCqVX7G"><img alt="Chat on Discord" src="https://img.shields.io/discord/808152298789666826?label=Discord&logo=Discord&style=social"></a>
<br><br>
<a href="https://trendshift.io/repositories/3949" target="_blank"><img src="https://trendshift.io/api/badge/repositories/3949" alt="siyuan-note%2Fsiyuan | Trendshift" style="width: 250px; height: 55px;" width="250" height="55"/></a>
</p>


---

## 目录

* [💡 简介](#-简介)
* [🔮 特性](#-特性)
* [🏗️ 架构和生态](#️-架构和生态)
* [🚀 下载安装](#-下载安装)
* [Docker 部署](#docker-部署)

---

## 💡 简介

思源笔记是一款隐私优先的个人知识管理系统，支持细粒度块级引用和 Markdown 所见即所得。

![feature0.png](https://b3logfile.com/file/2024/01/feature0-1orBRlI.png)

![feature51.png](https://b3logfile.com/file/2024/02/feature5-1-uYYjAqy.png)


## 🔮 特性

大部分功能是免费的，即使是在商业环境下使用。

* 内容块
  * 块级引用和双向链接
  * 自定义属性
  * SQL 查询嵌入
  * 协议 `siyuan://`
* 编辑器
  * Block 风格
  * Markdown 所见即所得
  * 列表大纲
  * 块缩放聚焦
  * 百万字大文档编辑
  * 数学公式、图表、流程图、甘特图、时序图、五线谱等
  * 网页剪藏
  * PDF 标注双链
* 导出
  * 块引用和嵌入块 
  * 带 assets 文件夹的标准 Markdown
  * PDF、Word 和 HTML
  * 复制到微信公众号、知乎和语雀
* 数据库
  * 表格视图
* 闪卡间隔重复
* 接入 OpenAI 接口支持人工智能写作和问答聊天
* Tesseract OCR
* 模板片段
* JavaScript/CSS 代码片段
* Android/iOS/鸿蒙 App
* Docker 部署
* [API](API_zh_CN.md)
* 社区集市

部分功能需要付费会员才能使用，更多细节请参考[定价](https://b3log.org/siyuan/pricing.html)。

## 🏗️ 架构和生态

![思源笔记架构设计](https://b3logfile.com/file/2023/05/SiYuan_Arch-Sgu8vXT.png "思源笔记架构设计")

| Project                                                  | Description  | Forks                                                                           | Stars                                                                                | 
|----------------------------------------------------------|--------------|---------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| [lute](https://github.com/88250/lute)                    | 编辑器引擎        | ![GitHub forks](https://img.shields.io/github/forks/88250/lute)                 | ![GitHub Repo stars](https://img.shields.io/github/stars/88250/lute)                 |
| [chrome](https://github.com/siyuan-note/siyuan-chrome)   | Chrome/Edge 扩展 | ![GitHub forks](https://img.shields.io/github/forks/siyuan-note/siyuan-chrome)  | ![GitHub Repo stars](https://img.shields.io/github/stars/siyuan-note/siyuan-chrome)  |
| [bazaar](https://github.com/siyuan-note/bazaar)          | 社区集市         | ![GitHub forks](https://img.shields.io/github/forks/siyuan-note/bazaar)         | ![GitHub Repo stars](https://img.shields.io/github/stars/siyuan-note/bazaar)         |
| [dejavu](https://github.com/siyuan-note/dejavu)          | 数据仓库         | ![GitHub forks](https://img.shields.io/github/forks/siyuan-note/dejavu)         | ![GitHub Repo stars](https://img.shields.io/github/stars/siyuan-note/dejavu)         |
| [petal](https://github.com/siyuan-note/petal)            | 插件 API       | ![GitHub forks](https://img.shields.io/github/forks/siyuan-note/petal)          | ![GitHub Repo stars](https://img.shields.io/github/stars/siyuan-note/petal)          |
| [android](https://github.com/siyuan-note/siyuan-android) | Android App  | ![GitHub forks](https://img.shields.io/github/forks/siyuan-note/siyuan-android) | ![GitHub Repo stars](https://img.shields.io/github/stars/siyuan-note/siyuan-android) |
| [ios](https://github.com/siyuan-note/siyuan-ios)         | iOS App      | ![GitHub forks](https://img.shields.io/github/forks/siyuan-note/siyuan-ios)     | ![GitHub Repo stars](https://img.shields.io/github/stars/siyuan-note/siyuan-ios)     |
| [harmony](https://github.com/siyuan-note/siyuan-harmony)         | 鸿蒙 App       | ![GitHub forks](https://img.shields.io/github/forks/siyuan-note/siyuan-harmony)     | ![GitHub Repo stars](https://img.shields.io/github/stars/siyuan-note/siyuan-harmony)     |
| [riff](https://github.com/siyuan-note/riff)              | 间隔重复         | ![GitHub forks](https://img.shields.io/github/forks/siyuan-note/riff)           | ![GitHub Repo stars](https://img.shields.io/github/stars/siyuan-note/riff)           |

## 🚀 下载安装

[GitHub](https://github.com/appdev/siyuan-unlock/releases)

## Docker 部署

#### 概述

在服务器上伺服思源最简单的方案是通过 Docker 部署。

* 镜像名称 `apkdv/siyuan-unlock`
* [镜像地址](https://hub.docker.com/r/apkdv/siyuan-unlock)

#### 文件结构

整体程序位于 `/opt/siyuan/` 下，基本上就是 Electron 安装包 resources 文件夹下的结构：

* appearance：图标、主题、多语言
* guide：帮助文档
* stage：界面和静态资源
* kernel：内核程序

#### 启动入口

入口点在构建 Docker 镜像时设置：`ENTRYPOINT ["/opt/siyuan/entrypoint.sh"]`。该脚本允许更改将在容器内运行的用户的 `PUID` 和 `PGID`。这对于解决从主机挂载目录时的权限问题尤为重要。`PUID` 和 `PGID` 可以作为环境变量传递，这样在访问主机挂载的目录时就能更容易地确保正确的权限。

使用 `docker run apkdv/siyuan-unlock` 运行容器时，请带入以下参数：

* `--workspace`：指定工作空间文件夹路径，在宿主机上通过 `-v` 挂载到容器中
* `--accessAuthCode`：指定访问授权码

更多的参数可参考 `--help`。下面是一条启动命令示例：

```bash
docker run -d \
  -v workspace_dir_host:workspace_dir_container \
  -p 6806:6806 \
  -e PUID=1001 -e PGID=1002 \
  apkdv/siyuan-unlock \
  --workspace=workspace_dir_container \
  --accessAuthCode=xxx
```

* `PUID`: 自定义用户 ID（可选，如果未提供，默认为 `1000`）
* `PGID`: 自定义组 ID（可选，如果未提供，默认为 `1000`）
* `workspace_dir_host`：宿主机上的工作空间文件夹路径
* `workspace_dir_container`：容器内工作空间文件夹路径，和后面 `--workspace` 指定成一样的
  * 另外，也可以通过 `SIYUAN_WORKSPACE_PATH` 环境变量设置路径。如果两者都设置了，命令行的值将优先
* `accessAuthCode`：访问授权码，请**务必修改**，否则任何人都可以读写你的数据
  * 另外，也可以通过 `SIYUAN_ACCESS_AUTH_CODE` 环境变量设置授权码。如果两者都设置了，命令行的值将优先
  * 可通过设置环境变量 `SIYUAN_ACCESS_AUTH_CODE_BYPASS=true` 禁用访问授权码

为了简化，建议将 workspace 文件夹路径在宿主机和容器上配置为一致的，比如将 `workspace_dir_host` 和 `workspace_dir_container` 都配置为 `/siyuan/workspace`，对应的启动命令示例：

```bash
docker run -d \
  -v /siyuan/workspace:/siyuan/workspace \
  -p 6806:6806 \
  -e PUID=1001 -e PGID=1002 \
  apkdv/siyuan-unlock \
  --workspace=/siyuan/workspace/ \
  --accessAuthCode=xxx
```

#### Docker Compose

对于使用 Docker Compose 运行思源的用户，可以通过环境变量 `PUID` 和 `PGID` 来自定义用户和组的 ID。下面是一个 Docker Compose 配置示例：

```yaml
version: "3.9"
services:
  main:
    image: apkdv/siyuan-unlock
    command: ['--workspace=/siyuan/workspace/', '--accessAuthCode=${AuthCode}']
    ports:
      - 6806:6806
    volumes:
      - /siyuan/workspace:/siyuan/workspace
    restart: unless-stopped
    environment:
      # A list of time zone identifiers can be found at https://en.wikipedia.org/wiki/List_of_tz_database_time_zones
      - TZ=${YOUR_TIME_ZONE}
      - PUID=${YOUR_USER_PUID}  # 自定义用户 ID
      - PGID=${YOUR_USER_PGID}  # 自定义组 ID
```

在此设置中：

* PUID “和 ”PGID "是动态设置并传递给容器的
* 如果没有提供这些变量，将使用默认的 `1000`

在环境中指定 `PUID` 和 `PGID` 后，就无需在组成文件中明确设置 `user` 指令（`user: '1000:1000'`）。容器将在启动时根据这些环境变量动态调整用户和组。

#### 用户权限

在图片中，“entrypoint.sh ”脚本确保以指定的 “PUID ”和 “PGID ”创建 “siyuan ”用户和组。因此，当主机创建工作区文件夹时，请注意设置文件夹的用户和组所有权，使其与计划使用的 `PUID` 和 `PGID` 匹配。例如

```bash
chown -R 1001:1002 /siyuan/workspace
```

如果使用自定义的 `PUID` 和 `PGID` 值，入口点脚本将确保在容器内创建正确的用户和组，并相应调整挂载卷的所有权。无需在 `docker run` 或 `docker-compose` 中手动传递 `-u`，因为环境变量会处理自定义。

#### 隐藏端口

使用 NGINX 反向代理可以隐藏 6806 端口，请注意：

* 配置 WebSocket 反代 `/ws`

#### 注意

* 请务必确认挂载卷的正确性，否则容器删除后数据会丢失
* 不要使用 URL 重写进行重定向，否则鉴权可能会有问题，建议配置反向代理

#### 限制

* 不支持桌面端和移动端应用连接，仅支持在浏览器上使用
* 不支持导出 PDF、HTML 和 Word 格式
* 不支持导入 Markdown 文件
