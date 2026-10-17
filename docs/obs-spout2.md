# OBS 透明词岛（Windows x64）

本地修改版 `1.0.0-gpu.3`（GPU 实验分支），基于上游 `v1.0.0`（`8ad331d`）。
保留原版桌面词岛，并新增独立的 Spout2 视频发送器。
上一版 `0.10.0-spout.1` 基于 `ef6467ec195536619240efdfcb7870ce7270c909`。

## 使用

1. 将 Windows 插件包 `.spmod` 导入 SPW，启用插件。
2. **OBS / Spout2 输出** 默认已开启；不需要时在同一设置页关闭即可。
3. 给需要使用的 OBS 安装 [obs-spout2-plugin 1.12.0](https://github.com/Off-World-Live/obs-spout2-plugin/releases/tag/1.12.0)，然后重启 OBS。
4. OBS 添加 **Spout2 Capture**，发送器选择 `SPW Lyrics Island`。
5. 合成模式选择 **Premultiplied Alpha（预乘 Alpha）**。画布空白处透明，无需色度键。

只需 OBS 接收插件，无需另装整套 Spout 软件。视频不包含音频；在 OBS 单独配置音乐音频源。

## 输出行为与设置

- 默认开启；默认名称 `SPW Lyrics Island`、30 FPS、1280×512 透明画布。
- 词岛保持收起状态并居中；封面、频谱、翻译、逐字动画、颜色和形状沿用原设置。
- 桌面隐藏、暂停隐藏、全屏隐藏、鼠标避让、拖动及悬停展开不影响 OBS 输出。
- 暂停保留当前内容，无歌曲显示原版空闲状态；关闭独立 Spout2 开关才能停止输出。
- 关闭“显示词岛”或按 Ctrl+Shift+D 只隐藏桌面词岛；开启 Spout2 时实时频谱继续采集，OBS 画面不受影响。
- 背景播放进度（注水 / 边缘细线）、封面取色、固定宽度、MiSans 默认字体和自定义歌词字体同样作用于 OBS 画面。
- “重置设置”会同时把 Spout2 设置恢复默认（输出开启、30 FPS）。
- 可选 30 FPS / 60 FPS，默认 30 FPS；低性能模式约 15 FPS。目标帧率是上限，不保证重负载下达到。
- 右键 / 托盘菜单新增「Spout2 输出」开关；关闭后离屏渲染、Skia 光栅化与发送线程全部停止，桌面词岛隐藏时频谱采集也会停止，可随时再打开。
- 双击词岛仍是播放 / 暂停；单击不会再切换输出。
- 画布宽度 320–3840、高度 128–2160；歌词长度变化不会改变发送纹理尺寸。
- 最多 2 倍绘制缩放，超出画布的内容等比适配；屏幕 DPI 不决定输出分辨率。
- 显卡编号是 DXGI 索引，`-1` 自动，`0` 为第一张 DXGI 显卡。插件与 OBS 必须使用同一张显卡。
- 名称使用可打印 ASCII 字符；名称冲突时 Spout 分配后缀，请在 OBS 选择实际名称。

初始化或设备故障只停止该次输出并提示；修正设置后重新开关输出即可重试。

## 构建与验证

要求 JDK 25、Visual Studio 2022 C++ Build Tools、Windows SDK、CMake。
通过 `JAVA_HOME` 指定 JDK 25 即可，无需更改系统默认 Java。

```powershell
.\gradlew.bat plugin --no-daemon
cmake -S native/spout -B build/native-check -A x64 -DSPW_SPOUT_PROBE=ON
cmake --build build/native-check --config Release
python src/test/python/native_checks.py
.\gradlew.bat spoutSoak -PsoakSeconds=660 --no-daemon
```

输出在 `build/distributions`。源码包包含原生桥、固定版本 SDK 必需源码及许可。
`spoutChecks` 检查真实离屏绘制；`native_checks.py` 逐像素检查真实共享纹理。
`spoutSoak` 使用合成播放状态，不能替代真实 SPW 回调验收；发送器为 `SPW Spout Verification`。
运行时每 10 秒向标准输出记录 `[SPW Spout]`：实际绘制 FPS、发送计数、丢弃数、绘制最大值/P95 和上传最大值。

## 合并说明

- 上游 1.0.0 迁移到 Workshop API dev23，插件包格式改为 `.spmod`，宿主需要 SPW 1.19.0+ / Java 25；本 fork 仍只构建 Windows x64。
- 上游 0.13 / 0.14 新增实验性液态玻璃背景、重复 GPU 适配器处理、强制逐字歌词与统一玻璃窗口。
  本 fork 原样接收这些功能；Spout2 使用独立的离屏 `IslandPanel` 记录动画与布局，再由 Skia 在发送线程直接光栅化到原生帧缓冲，与桌面窗口绘制互不影响。
- 上游 0.11 起歌词布局改为“当前行同步测量 + 后台预热后续行”。`SpoutPresentation` 每帧调用 `prepareLyrics`，
  并使用自己的 `IslandPanel` 预热线程；关闭或重配置输出时释放该线程。
- 上游 0.12 的 `IslandWindow.tick()` 在桌面隐藏时提前返回。Spout2 帧在该返回之前生成，
  帧间隔取桌面调度与 Spout2 目标帧率的较小值，因此全屏 / 暂停 / 关闭显示时仍按设定帧率输出。
- 原生桥 `spw-spout.dll` 与接口未变；`0.10.0-spout.1` 中的 `SystemUiFont` 空字体快速路径已被上游同等实现取代，不再单独修改。

## 实现边界

Swing 事件线程只推进动画、歌词布局与 `IslandPanel.gpuFrame()` 快照，不再逐帧做 Java2D 光栅化。
发送线程用 Skia `Surface.makeRasterDirect` 直接绘制到 JNA 帧缓冲（预乘 BGRA8，stride = 宽度 × 4），
再由 `spw-spout.dll` 上传 D3D11 共享纹理，省去一整帧的 CPU 拷贝；这不是 GPU 显存到显存的零拷贝
（Skiko 使用 D3D12，桥接层只接受 CPU 字节），因此上限仍是“一次光栅化 + 一次纹理上传”。
待发送帧采用最新帧覆盖策略；原生绘制失败或设置 `-Dspw.spout.renderer=software` 时自动回退到
`SpoutPresentation` 的 Java2D 兼容光栅路径，输出功能不中断。
关闭、重配置、退出时均在原生所属线程释放设备与发送器，并释放 Skia 场景与 Surface。
