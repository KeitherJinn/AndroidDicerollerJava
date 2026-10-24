# Dice Roller Java

原 DiceRoller Kotlin 1.2.1 的独立 Java 实现。应用源代码、测试均为 Java，构建脚本为 Groovy，界面使用原生 Android Views。依赖的 AndroidX 库可能内部使用 Kotlin；这不需要在本项目中编写 Kotlin。

## 功能

- `d20`、`2d6+3` 等表达式；全角输入规范化；严格范围校验；Long 结果计算。
- 普通、优势、劣势模式，后两者比较两整组骰子；批次投掷与安全取消。
- d4 / d6 / d8 / d10 / d12 / d20 / d100 快捷输入。
- 批次总和、均值、最小值、最大值；结果与骰子明细分页。
- 批次及单次历史复用、完整文本复制；持久化、损坏保护、备份恢复、失败重试。
- 系统动态色、紫罗兰、海洋、森林、琥珀配色；系统深浅模式；中英文。
- 大字体和窄屏自适应；宽屏双栏；旋转后的输入、分页和浏览位置保留。

## 项目与数据

- 应用 ID：`com.example.dicerollerjava`，版本 `1.0.1`，最低 Android 14（API 34）。
- QA 测试版：`com.example.dicerollerjava.uitest`，与日常使用数据隔离。
- 与 Kotlin 版并存，数据互不覆盖。历史文件格式兼容 v1/v2，但不会自动读取另一应用沙箱。
- 历史保留上限：100 批次 / 1,000 个结果 / 200,000 个骰值，以完整批次裁剪。

## 结构

- `DiceEngine`、不可变结果模型：解析、校验、随机投掷与批次约束。
- `HistoryRepository` / `HistoryStore`：原子 JSON 写入、严格读取、恢复与备份。
- `DiceRollerViewModel` / `RollerUiState`：可注入执行器的异步状态机和 SavedStateHandle。
- `MainActivity`、`ResultCards`、`Ui`、`FlowLayout`：界面、分页和自适应排版。
- `PresentationState`：按仍保留的批次和结果清理展开、分页状态，保留加载中的恢复数据。
- `ThemePalette` / `ThemeColors` / `ThemePreferences`：完整颜色角色和偏好存储。
- `ResultTextFormatter` 与 `res/values*`：完整导出与本地化资源。

## 本机离线构建

使用已安装的 Android SDK 和现有 Gradle 缓存；`local.properties` 记录本机 SDK 路径。首次在另一台电脑上构建可能需要自行准备依赖。

```powershell
.\gradlew.bat --offline :app:assembleDebug :app:assembleQa :app:assembleQaAndroidTest :app:testQaUnitTest :app:lintDebug :app:lintQa :app:lintRelease
```

安装包位于 `app/build/outputs/apk/debug/app-debug.apk`。交付包使用本机 debug 签名，可直接测试安装；商店发布需配置正式签名。

使用本机现有 Android 模拟器执行设备测试，不依赖额外测试软件：

```powershell
adb -s emulator-5556 install -r app/build/outputs/apk/qa/app-qa.apk
adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk
adb -s emulator-5556 shell am instrument -w -r com.example.dicerollerjava.uitest.test/androidx.test.runner.AndroidJUnitRunner
```

设备序列号应以 `adb devices` 的实际输出为准。测试中的数据变更仅允许在 QA 包名下执行。

## 1.0.1 更新

- 进程被回收后恢复浏览位置，首次新投掷仍能定位最新结果。
- 固定投掷栏显示当前模式；临时提示遵循系统辅助功能推荐阅读时长。
- 完整复制在一次导出内复用数字格式化器，不在并发调用间共享可变实例。
- 详情与分页只更新所属卡片；忙碌状态切换更新载入按钮，不重建整份历史。
- 历史清空、淘汰及保存状态时清理失效记录的界面状态；加载完成前保留待恢复状态。

`StructuralRiskUiTest` 在 QA 包中验证满历史和保存状态增长，耗时为测量记录，不作为固定时间阈值断言。真实进程回收需另外以外部 ADB `am kill` 验证，普通 Activity 重建不能替代。
