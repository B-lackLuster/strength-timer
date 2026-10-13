# 力量计时

离线安卓力量训练计时器，支持 Android 8.0 及以上。

- 常用动作选择、自定义动作名称；30/45/60/90 秒快捷时长和自定义秒数。
- 每个动作可配置组数、组间小休息、动作后大休息（0 秒表示关闭）。
- 长按卡片左侧 ≡ 拖动排序，也可通过编辑 → 更多上下移动、复制、删除。
- 自定义大/小休息可作为独立项目插入并拖动；自动休息跟随所属动作。
- 自动切换阶段；暂停、继续、跳过、提前结束并保存。
- 完成一段训练计时记 1 组；跳过不记完成组。不自动测量动作重复次数。
- 训练历史显示每个动作完成组数、有效训练时长、跳过组数；保留最近 200 次。
- 通知栏控制、后台和锁屏计时、可开关提示音和振动。
- 系统强制终止后，重新打开恢复为暂停状态，不虚增训练次数。

小休息仅在两组之间执行；最后一组后直接执行大休息。最后一个动作的大休息也会执行，不需要可设为 0。独立休息块与动作自动休息会累加。

## 安装
GitHub Actions → Build Android APK → 成功运行 → strength-timer-apk，解压安装 APK。
本地交付目录 `dist/力量计时-1.0.0.apk` 同样可安装。
首次运行建议允许通知，便于锁屏暂停或跳过。部分手机需在系统设置允许应用后台运行。

## 云端构建
本机无需 Android SDK、Android Studio 或模拟器。GitHub Actions 使用 JDK 17、Gradle 8.9、Android Gradle Plugin 8.7.3、Android SDK 35。
推送 main 或手动运行工作流会执行 8 个纯 Java 计时测试、Android lint、签名 release 构建，以及 Android 15 模拟器的 3 项界面测试，输出测试报告和界面截图。

构建兼容性依据：[Android 官方 AGP 8.7 说明](https://developer.android.com/build/releases/agp-8-7-0-release-notes)。后台服务声明遵循 [Android 官方前台服务类型说明](https://developer.android.com/about/versions/14/changes/fgs-types-required)。

签名证书通过私有仓库 Actions secrets `KEYSTORE_BASE64` 与 `SIGNING_PASSWORD` 管理，后续安装包沿用同一签名，可覆盖升级。请备份签名证书；丢失后无法直接覆盖安装已有版本。
手机数据只保存在应用本地，不联网、不收集账号信息，卸载后数据会清除。

## 项目
`MainActivity.java`：计划编辑、拖动、计时和记录界面。
`Workout.java`：独立于 Android 的时间线和计时引擎。
`TimerService.java`：后台服务、单调时钟、通知和提示。
`Store.java`：计划、训练快照和历史记录。
