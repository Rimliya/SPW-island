<p align="center">
  <img src="docs/assets/README-hero.png" width="320" alt="灵动词岛">
</p>

<h1 align="center">灵动词岛 for SPW</h1>

> [!NOTE]
> 这是用于 **OBS Spout2 捕获** 的 fork，fork 自原作者仓库 [GaBoron/SPW-island](https://github.com/GaBoron/SPW-island)。
> 本地扩展版 `0.12.0-spout.1`：基于上游 0.12.0，保留 OBS Spout2 透明输出，桌面隐藏后仍可显示歌词。
> 本 fork 只构建和分发 Windows 包；安装和设置见 [OBS Spout2 使用说明](docs/obs-spout2.md)。

<p align="center">为 Salt Player for Windows 制作的桌面歌词词岛。</p>

<p align="center">
  <a href="https://github.com/Rimliya/SPW-island/releases/latest"><img src="https://img.shields.io/github/v/release/Rimliya/SPW-island?label=Release" alt="Release"></a>
  <a href="docs/compatibility.md"><img src="https://img.shields.io/badge/Windows-10%20%2F%2011-0078D4?logo=windows" alt="Windows 10 / 11"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0%20%2B%20AGPL--3.0-blue" alt="License"></a>
</p>

把 SPW 当前播放的歌词放到桌面上方，支持逐字歌词、翻译、AMLL 风格动效、专辑封面与取色、播放控制、实时频谱、鼠标穿透和自动隐藏。

原项目 QQ 交流群（非本 fork 维护，原作者侧）：`1054809039`

## 功能

- 逐字高亮、翻译、长音强调、辉光和歌词切换动效；
- 胶囊 / 刘海外观，支持圆角、宽度、透明度、字体和多种封面取色；
- 悬停展开上一首、播放 / 暂停、下一首和可拖动进度条；
- 九宫格拖动吸附，顶部和底部会自动调整展开方向；
- 支持 SPW 进程的四频段实时频谱、频谱取色和鼠标穿透；
- 支持悬停隐藏和全屏隐藏。

详细设置见 [使用与设置](docs/usage.md)，平台差异见 [兼容性与限制](docs/compatibility.md)。

## 安装

1. 前往 [Releases](https://github.com/Rimliya/SPW-island/releases/latest) 下载 `windows-x64` 插件 ZIP；
2. 在 SPW 的创意工坊 / 插件管理中导入；
3. 启用插件并播放一首已经有歌词的歌曲。

不要导入带 `-source` 后缀的源码包。

如果当前 SPW 没有导入入口，请看 [安装与更新](docs/installation.md)。

本插件只显示 SPW 已经加载的歌词，不负责联网搜索。需要自动获取逐字歌词时，可以搭配 [SPW-Lyrics](https://github.com/GaBoron/SPW-Lyrics)。

## 平台

| 平台 | 状态 |
| --- | --- |
| Windows 10 / 11 x64 | 支持 |
| Linux x64 | 本 fork 不再提供 |
| macOS | 暂无计划 |

## 文档

- [安装与更新](docs/installation.md)
- [使用与设置](docs/usage.md)
- [故障排查](docs/troubleshooting.md)
- [兼容性与限制](docs/compatibility.md)
- [开发指南](docs/development.md)

完整文档入口在 [`docs/README.md`](docs/README.md)。

## 反馈与贡献

上游项目本身的问题和功能建议请到 [上游 Issue](https://github.com/GaBoron/SPW-island/issues) 反馈。本 fork 只改动了 Windows 上的 OBS Spout2 输出、圆角默认值和若干链接，上游行为请以上游仓库为准。

较大的功能、UI 行为或平台改动建议先在 Issue 里把目标和范围说清楚，再开始完整实现；小型修复和文档修改可以直接提交 PR。项目的代码边界和贡献习惯写在 [开发指南](docs/development.md) 中。

## 许可与致谢

灵动词岛原创：Lyricify / WXRIW（XY Wang）。本项目依据 [CC BY-SA 4.0](https://github.com/WXRIW/Lyricify-App#lyricify-原创) 独立实现，并非 Lyricify 官方产品。

AMLL 动画移植模块采用 AGPL-3.0-only，其他程序采用 GPL-3.0-only；视觉、交互改编及文档采用 CC BY-SA 4.0。

详见 [NOTICE](NOTICE)、[第三方许可说明](THIRD_PARTY_NOTICES.md) 和 [LICENSE](LICENSE)。
