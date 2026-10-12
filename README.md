<div align="center">

# ME 拼接终端 · JEI 拼接窗口

**把 JEI 的搜索、物品列表、收藏和查询历史，变成 ME 拼接终端里可拖拽、可拼接的窗口**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A?style=flat-square)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.1%2B-DF8E2B?style=flat-square)](https://files.minecraftforge.net/)
[![JEI](https://img.shields.io/badge/JEI-15.19--15.49-4A90D9?style=flat-square)](https://github.com/mezz/JustEnoughItems)
[![Side](https://img.shields.io/badge/%E4%BB%85%E5%AE%A2%E6%88%B7%E7%AB%AF-Client%20only-6E5BA8?style=flat-square)](#安装)
[![License](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey?style=flat-square)](#许可证)

[下载](https://github.com/GeZiAsi/MEST_JEI_Forge_1.20.1/releases) ·
[问题反馈](https://github.com/GeZiAsi/MEST_JEI_Forge_1.20.1/issues) ·
[ME 拼接终端](https://github.com/GeZiAsi/MEST_Forge_1.20.1) ·
[English](#english)

</div>

| | |
| --- | --- |
| **作者** | [GeZiAsi](https://github.com/GeZiAsi)（格兹亚西）；代码由 Claude 辅助编写 |
| **前置** | [ME 拼接终端（Forge 1.20.1 移植版）](https://github.com/GeZiAsi/MEST_Forge_1.20.1)、[JEI](https://github.com/mezz/JustEnoughItems) 15.19 – 15.49 |
| **游戏版本** | Minecraft 1.20.1 · Forge 47.1 及以上 |
| **运行端** | 仅客户端，服务端无需安装 |
| **许可证** | [CC BY-NC-SA 4.0](#许可证)：可修改、可二次编译、可再分发，不可商用 |

ME 拼接终端把 AE2 的各种功能做成了可以自由拖动、拼接的面板。这个附属把 JEI 也拉了进来：
打开拼接终端时，JEI 贴在屏幕两侧的列表会自动隐藏，换成和终端风格一致的面板，能和 ME 列表、合成、样板编码等模块拼在同一个窗口里。
面板里的物品、提示和操作都交给 JEI 自己处理，用起来和 JEI 完全一样。

## 目录

- [模块](#模块)
- [外观](#外观)
- [和 JEI 一样的操作](#和-jei-一样的操作)
- [安装](#安装)
- [兼容性](#兼容性)
- [配置](#配置)
- [构建](#构建)
- [许可证](#许可证)

## 模块

| 模块 | 内容 |
| --- | --- |
| **JEI 搜索** | 搜索框和 JEI 配置按钮。默认拼在物品列表上方，也可以拆出来单独悬浮或拼到别的窗口 |
| **JEI 物品** | JEI 的物品列表，排序、隐藏规则都和 JEI 一致 |
| **JEI 收藏** | JEI 的收藏列表 |
| **JEI 历史** | JEI 的查询历史（最近查看过配方的物品），标题栏带 JEI 的历史开关。仅 JEI 15.4x 及以上 |

每个模块都和终端里的其他模块一样：可以拖动、缩放、拼接进任意窗口、整体悬浮、钉在最上层、存进布局预设、在布局编辑器里调整，也可以用终端左侧的模块按钮和面板快捷键显示 / 隐藏。

## 外观

- 外框就是终端窗口的外框；里面是一整块凹陷底，物品按 JEI 的方式紧密排列，不画格子。
- 凹陷底的颜色取自当前材质包里 AE2 终端贴图的格子颜色（原版 AE2 是 #8B8B8B，XingLuo AE2 GUI 扩展材质包是 #ADB0C4），换材质包会自动跟着变。
- 滚动条与 ME 列表同款，放在窗口内部并紧贴右边框。
- 第一次出现时，搜索 + 物品列表放在屏幕右侧、收藏放在左上、历史放在左下，和 JEI 原来的位置一致；之后随你拖放，位置由终端的布局保存。
- 收藏窗口默认是 3×5 的小窗口，收藏变多时自动变大：先加高到 10 行，再加宽到每行 5 个，再多就滚动。手动调整过大小后就按你的尺寸保留，可以超出这个限制。

只有打开拼接终端时才会替换 JEI 的界面。背包、工作台、熔炉、各种机器和其他模组的界面里，JEI 仍是原来的样子。

## 和 JEI 一样的操作

- **搜索**：直接修改 JEI 的搜索内容，支持 JEI 的全部搜索语法（`@模组`、`#提示`、`$标签`、`%创造物品栏`、`-排除`、`|` 或……）。和 JEI 搜索栏同步，在别的界面搜过的内容，打开终端时依然在。
  - 搜不到结果时文字变红；右键清空；↑ / ↓ 浏览搜索历史；Enter / Esc 退出输入；JEI 的"聚焦搜索栏"快捷键（默认 Ctrl+F）会聚焦这个搜索框。
- **查看配方**：左键 / R 查看配方，右键 / U 查看用途。
- **收藏**：鼠标悬停在物品上按 A 收藏，在收藏里按 A 取消。收藏就是 JEI 的收藏，保存在 JEI 的文件里。按住拖动收藏可以调整顺序。
- **拖到槽位**：把物品从面板拖到样板编码等虚拟槽位上即可放入。
- **作弊模式**：配置按钮左键打开 JEI 设置，Ctrl+左键切换作弊模式（按钮图标变红）；作弊模式下点击取物、拿着物品点列表删除，都与 JEI 相同。
- **其他**：编辑模式、显示 / 隐藏物品列表与收藏的快捷键、翻页键都照常生效。

## 安装

1. 安装 [ME 拼接终端](https://github.com/GeZiAsi/MEST_Forge_1.20.1/releases) 和 JEI。
2. 从 [Releases](https://github.com/GeZiAsi/MEST_JEI_Forge_1.20.1/releases) 下载本模组，放进**客户端**的 `mods` 目录。
3. 进游戏打开拼接终端，新模块会出现在屏幕两侧，按自己的习惯拖动、拼接即可。

服务端不需要安装；装在服务端也只是不起作用，不会出问题。升级时请先删掉旧版本的 jar。

## 兼容性

| 组件 | 版本 |
| --- | --- |
| ME 拼接终端 | Forge 1.20.1 移植版 0.0.3 及以上 |
| JEI | 15.19 – 15.49（逐一核对过 15.19.5、15.20.0、15.49.0） |
| 材质包 | 跟随当前 AE2 终端贴图配色，已在 XingLuo AE2 GUI 扩展材质包下测试 |

本模组复用了 JEI 自己的物品格、搜索过滤和收藏列表。JEI 各版本之间移动过位置的类通过反射查找；遇到无法识别的 JEI 版本时，相关部分会停用并在日志里写一条警告，不会让游戏崩溃。

## 配置

JEI 相关的一切设置都使用 JEI 自己的配置，本模组没有单独的选项。

`config/mestjei/windows.properties` 只记录各窗口是否已经摆放过，以及收藏窗口的自动尺寸。删掉它，下次打开终端时窗口会按默认位置重新摆放。

## 构建

需要 **JDK 17**（项目路径含中文时，Gradle 本身也必须用 JDK 17 运行）。编译所用的 ME 拼接终端 jar 放在 `libs/` 中。

```powershell
.\gradlew.bat build   # 产物在 build/libs/
```

<details>
<summary>自动化开发测试</summary>

`runClientDevtest` 会启动一个开发客户端：自动建测试世界、搭建 ME 网络、打开终端，逐项检查隐藏 JEI 原窗口、窗口摆放、搜索、查看配方 / 用途、收藏与收藏窗口自动尺寸、拖动排序收藏、查询历史、滚轮、作弊模式取物 / 删物、拖到虚拟槽位、离开终端后 JEI 恢复原样等功能。截图保存在 `run/devtest/screenshots/`，日志里用 `MESTJEI-DEVTEST` 过滤可以看到每项的 PASS / FAIL。

| 参数 | 作用 |
| --- | --- |
| `-PdevtestJei=<版本>` | 换成别的 JEI 版本运行，例如 `15.20.0.106` |
| `-PdevtestWidth/Height/GuiScale` | 窗口尺寸与 GUI 缩放 |

</details>

## 致谢

- [lhy512103](https://github.com/lhy512103) —— ME 拼接终端原作者。
- [mezz](https://github.com/mezz/JustEnoughItems) —— JEI。
- [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) —— 面板使用的 AE2 界面贴图。

## 许可证

本模组以 **[知识共享 署名-非商业性使用-相同方式共享 4.0 国际许可协议（CC BY-NC-SA 4.0）](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.zh-hans)** 发布，全文见 [LICENSE](LICENSE)。

- ✅ 可以复制、再分发本模组及其源代码；
- ✅ 可以修改、二次开发、重新编译，并分发修改后的版本；
- ⚠️ 需要署名原作者（GeZiAsi），注明是否做了修改，并附上本许可证链接；
- ⚠️ 修改后的版本必须以相同的许可证（CC BY-NC-SA 4.0）发布；
- ❌ 不得用于商业目的（包括付费售卖、付费下载、付费整合包专属内容等）。

例外：

- `libs/` 中的 ME 拼接终端 jar 是另一个模组，适用它自己的 MIT 许可证（随 jar 附带）。
- `src/devtest/` 中的开发测试代码部分改编自 ME 拼接终端的开发测试，这部分同时保留其 MIT 许可声明：Copyright (c) 2026 lhy512103，Copyright (c) 2026 GeZiAsi。

---

## English

**MEST JEI Panels** is a client-only add-on for the [ME Spliced Terminal](https://github.com/GeZiAsi/MEST_Forge_1.20.1) (Forge 1.20.1).
While the terminal is open, JEI's side lists are hidden and replaced by terminal modules that can be moved, resized and spliced like any other: **JEI Search**, **JEI Ingredients**, **JEI Bookmarks** and **JEI History** (JEI 15.4x+).
The panels reuse JEI's own grid, filter and bookmark list, so ingredients, tooltips, recipe lookup, bookmarking, cheat mode and ghost-ingredient dragging behave exactly as in JEI, with JEI's own settings.
Other screens keep JEI's normal UI.

- **Author:** [GeZiAsi](https://github.com/GeZiAsi); code written with the help of Claude.
- **Requires:** Minecraft 1.20.1, Forge 47.1+, ME Spliced Terminal (Forge 1.20.1 port) 0.0.3+, JEI 15.19–15.49.
- **License:** [CC BY-NC-SA 4.0](LICENSE) — you may share, modify and rebuild it, with attribution and under the same license, but not for commercial purposes. `libs/` and the adapted parts of `src/devtest/` keep their MIT notices (see above).
