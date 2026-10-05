# OBS 透明词岛（Windows x64）

本地修改版 `0.12.0-spout.1`，基于上游 `v0.12.0`（`34988f7`）。
保留原版桌面词岛，并新增独立的 Spout2 视频发送器。
上一版 `0.10.0-spout.1` 基于 `ef6467ec195536619240efdfcb7870ce7270c909`。

## 使用

1. 将 Windows 插件 ZIP 导入 SPW，启用插件。
2. 在插件设置中开启 **OBS / Spout2 输出**。
3. 给需要使用的 OBS 安装 [obs-spout2-plugin 1.12.0](https://github.com/Off-World-Live/obs-spout2-plugin/releases/tag/1.12.0)，然后重启 OBS。
4. OBS 添加 **Spout2 Capture**，发送器选择 `SPW Lyrics Island`。
5. 合成模式选择 **Premultiplied Alpha（预乘 Alpha）**。画布空白处透明，无需色度键。

只需 OBS 接收插件，无需另装整套 Spout 软件。视频不包含音频；在 OBS 单独配置音乐音频源。
本机接收插件安装在 `F:\OBS Studio\OBS-Studio`；验收场景集合为 `SPW Spout2 验收`。

## 输出行为与设置

- 默认关闭；默认名称 `SPW Lyrics Island`、60 FPS、1280×512 透明画布。
- 词岛保持收起状态并居中；封面、频谱、翻译、逐字动画、颜色和形状沿用原设置。
- 桌面隐藏、暂停隐藏、全屏隐藏、鼠标避让、拖动及悬停展开不影响 OBS 输出。
- 暂停保留当前内容，无歌曲显示原版空闲状态；关闭独立 Spout2 开关才能停止输出。
- 关闭“显示词岛”或按 Ctrl+Shift+D 只隐藏桌面词岛；开启 Spout2 时实时频谱继续采集，OBS 画面不受影响。
- 背景播放进度（注水 / 边缘细线）、封面取色、固定宽度、MiSans 默认字体和自定义歌词字体同样作用于 OBS 画面。
- “重置设置”会同时把 Spout2 设置恢复默认（输出关闭），需要时重新打开开关。
- 可选 30/60 FPS，低性能模式约 15 FPS。目标帧率是上限，不保证重负载下达到。
- 画布宽度 320–3840、高度 128–2160；歌词长度变化不会改变发送纹理尺寸。
- 最多 2 倍绘制缩放，超出画布的内容等比适配；屏幕 DPI 不决定输出分辨率。
- 显卡编号是 DXGI 索引，`-1` 自动。本机 OBS 的 RTX 4070 对应 `0`。双方应使用同一显卡。
- 名称使用可打印 ASCII 字符；名称冲突时 Spout 分配后缀，请在 OBS 选择实际名称。

初始化或设备故障只停止该次输出并提示；修正设置后重新开关输出即可重试。

## 构建与验证

要求 JDK 21、Visual Studio 2022 C++ Build Tools、Windows SDK、CMake。
通过 `JAVA_HOME` 指定 JDK 21 即可，无需更改系统默认 Java。

```powershell
.\gradlew.bat pluginWindows spoutChecks --no-daemon
cmake -S native/spout -B build/native-check -A x64 -DSPW_SPOUT_PROBE=ON
cmake --build build/native-check --config Release
python src/test/python/native_checks.py
.\gradlew.bat spoutSoak -PsoakSeconds=660 --no-daemon
```

输出在 `build/distributions`。源码包包含原生桥、固定版本 SDK 必需源码及许可。
`spoutChecks` 检查真实离屏绘制；`native_checks.py` 逐像素检查真实共享纹理。
`spoutSoak` 使用合成播放状态，不能替代真实 SPW 回调验收；发送器为 `SPW Spout Verification`。
运行时每 10 秒向标准输出记录 `[SPW Spout]`：实际绘制 FPS、发送计数、丢弃数、绘制最大值/P95 和上传最大值。

## 0.12.0 合并说明

- 上游 0.11 起歌词布局改为“当前行同步测量 + 后台预热后续行”。`SpoutRenderer` 每帧调用 `prepareLyrics`，
  并使用自己的 `IslandPanel` 预热线程；关闭或重配置输出时释放该线程。
- 上游 0.12 的 `IslandWindow.tick()` 在桌面隐藏时提前返回。Spout2 帧在该返回之前生成，
  帧间隔取桌面调度与 Spout2 目标帧率的较小值，因此全屏 / 暂停 / 关闭显示时仍按设定帧率输出。
- 原生桥 `spw-spout.dll` 与接口未变；`0.10.0-spout.1` 中的 `SystemUiFont` 空字体快速路径已被上游同等实现取代，不再单独修改。

## 实现边界

Java2D 在 Swing 事件线程绘制独立 `IslandPanel`，产生预乘 BGRA8 像素。
后台线程通过 JNA 调用 `spw-spout.dll` 上传 D3D11 共享纹理；不是全程 GPU 零拷贝。
最多三个可复用像素数组和一帧待发送数据，忙时替换旧待发送帧。
关闭、重配置、退出时均在原生所属线程释放设备与发送器。Linux 不加载原生桥。
