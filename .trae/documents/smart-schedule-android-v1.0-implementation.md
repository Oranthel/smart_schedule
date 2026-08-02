# 智能日程安排 App — v1.0 开发与环境配置计划

## Context（为什么做这件事）

用户在 PRD 评审阶段已确定技术栈为 **Android 原生（Kotlin + Jetpack Compose）**，现要求"进入开发环节"。环境探测确认：**本机（Windows 11 Home China）无任何 Android 工具链**（无 JDK / Gradle / Android SDK / Android Studio），仅有 `winget`。用户已选择"本机装完整 Android 工具链"，并要求：详细教配置环境、说明为什么要配置、是否需要 Docker、给出官网下载地址。

本计划分两部分：
- **Part A**：环境配置指南（用户执行安装；含"为什么"、Docker 结论、官网+国内镜像地址）。
- **Part B**：v1.0 Android 工程实现（我执行编码；可在安装期间并行开始，因代码编写不依赖工具链，仅构建/验证依赖）。

预期结果：本机构建并运行 v1.0 核心闭环（导入→识别确认→区分固定/灵活→首页执行→添加临时事项→冲突处理→休息→远近精度→日/周视图一致→本地通知→AI 调整透明可撤销→离线可用）。

---

## Part A：环境配置指南（用户执行）

### A.1 为什么要配置这些组件

| 组件 | 作用 | 为什么必需 |
|---|---|---|
| **JDK 17+**（推荐 21 LTS） | 运行 Gradle、编译 Kotlin/Java | Gradle 本身是 JVM 程序；Android Gradle Plugin(AGP) 8.x/9.x 强制要求 JDK 17+，否则报 "AGP requires Java 17" |
| **Android SDK** | 提供 Android 平台 API、build-tools、platform-tools(adb)、emulator | 编译 APK、连接真机/模拟器都依赖它；AGP 调用 build-tools 打包 |
| **Gradle（wrapper）** | 构建系统：编译、依赖管理、打包 APK、跑测试 | Android 工程的标准构建工具；wrapper 会自动下载指定版本，无需全局安装 |
| **Android Studio** | IDE：集成编辑器+调试+可视化+SDK Manager+模拟器 | 一站式环境，自带 JBR(JDK)，是获得 JDK+SDK+Gradle 工具链最简单的方式 |

一句话：**装 Android Studio 基本就齐了**（它自带 JDK 与 SDK Manager），额外单独装 JDK 21 只是为命令行 `gradlew` 构建和明确版本控制。

### A.2 需要 Docker 吗？

**不需要。** Docker 用于服务端/CI 的容器化隔离，本地 Android 应用开发用的是 JDK+SDK+Gradle+Android Studio 这套原生工具链，二者无关。后续若做 CI 自动构建可考虑 Docker，本地开发不引入。

### A.3 下载地址（官网 + 国内镜像，二选一）

> 国内强烈走镜像，否则 dl.google.com / services.gradle.org 下载极慢或中断。

**Android Studio 安装包**
- 官网：https://developer.android.com/studio （国内访问慢）
- 清华镜像（推荐）：https://mirrors.tuna.tsinghua.edu.cn/AndroidStudio/
- 华为镜像：https://mirrors.huaweicloud.com/androidstudio/

**JDK 21（Temurin / Eclipse Adoptium，可选——Android Studio 自带 JBR）**
- 官网：https://adoptium.net/temurin/releases/?version=21
- 华为镜像：https://mirrors.huaweicloud.com/openjdk/
- 微软 OpenJDK：https://learn.microsoft.com/java/openjdk/

### A.4 安装步骤（Windows 11）

1. **下载** Android Studio 安装包（走 A.3 镜像）。文件名形如 `android-studio-2025.3.x-windows.exe`。
2. **路径原则**：安装路径**无中文、无空格**；建议装非系统盘，如 `D:\Android\Android Studio`。SDK 单独放 `D:\Android\Sdk`。
3. **以管理员身份运行**安装程序 → 默认组件（Android Studio + Android Virtual Device）→ 选路径 → Install → Finish。
4. **首次启动**：选 "Do not import settings" → "Standard" 安装类型 → 选主题 → 确认 SDK 组件下载 → Finish（此时会下载 SDK，走 A.5 镜像加速）。
5. **（可选）单独装 JDK 21** 用于命令行构建：装到 `D:\Android\jdk-21`，无中文空格。

### A.5 配置国内镜像（关键，否则 SDK/Gradle/依赖下载卡死）

**(a) Gradle 分发镜像**（项目级，我会在工程里预先配好）：
`gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 指向腾讯/阿里，如：
`https\://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip`

**(b) Maven 依赖镜像**（项目级，我会在 `settings.gradle.kts` 预配）：
```
maven { url = uri("https://maven.aliyun.com/repository/google") }
maven { url = uri("https://maven.aliyun.com/repository/central") }
maven { url = uri("https://maven.huaweicloud.com/repository/maven/") }
```

**(c) SDK 下载镜像**（IDE 内一次性设置）：
`File → Settings → Appearance & Behavior → System Settings → Android SDK → SDK Update Sites`，添加：
- 腾讯：https://mirrors.cloud.tencent.com/AndroidSDK/
- 华为：https://mirrors.huaweicloud.com/android-sdk/

**(d) SDK 组件**（SDK Manager 内勾选安装）：
- SDK Platforms：最新稳定版（Android 14 / API 34 或更高）
- SDK Tools：Android SDK Build-Tools、Android SDK Platform-Tools、Android Emulator、Android Emulator hypervisor driver（installer）

### A.6 环境变量（命令行构建需要）

- `JAVA_HOME` = `D:\Android\jdk-21`（或 Android Studio 自带 JBR 路径，如 `D:\Android\Android Studio\jbr`）
- `ANDROID_HOME` = `D:\Android\Sdk`
- `PATH` 追加：`%JAVA_HOME%\bin`、`%ANDROID_HOME%\platform-tools`、`%ANDROID_HOME%\cmdline-tools\latest\bin`
- （可选）`GRADLE_USER_HOME` = `D:\Android\.gradle`，把 Gradle 缓存移出 C 盘

### A.7 验证环境就绪

在 PowerShell 执行（应都有版本输出）：
- `java -version`（≥17，推荐 21）
- `adb version`
- 打开 Android Studio → 新建 Empty Activity 项目 → 能 Sync + Run 到模拟器即说明工具链完整

---

## Part B：v1.0 Android 工程实现（我执行）

### B.1 工程参数

| 项 | 值 |
|---|---|
| Application ID | `com.smartplanner` |
| minSdk | 26（Android 8.0，覆盖 95%+ 设备，支持通知渠道） |
| targetSdk / compileSdk | 35（最新稳定，实现时按当年稳定版钉死） |
| Kotlin | 2.0+（稳定版） |
| AGP | 8.7+（稳定版） |
| Gradle wrapper | 8.9+（CN 镜像 distributionUrl） |
| Java/Kotlin target | JVM_17 |
| 构建脚本 | Kotlin DSL（.kts） |
| 模块 | 单 `:app` 模块 + 包分层（v1.0 简化；后续可拆多模块） |

### B.2 包结构（对齐 04 路线图）

```
com.smartplanner/
├─ core/
│  ├─ scheduler/     端侧规则引擎（纯 Kotlin，可单测）
│  ├─ data/          Room 实体+DAO+Repository+DataStore
│  ├─ ai/            LLM 客户端+离线队列+置信度门控
│  └─ notifications/ 通知/闹钟/勿扰/夜间开关（v1.0 仅基础通知）
├─ feature/
│  ├─ home/          首页日视图执行中心
│  ├─ import/        文本/CSV 导入与确认
│  ├─ plan/          基础作息/课程表/特殊日期/调度偏好
│  └─ views/         日视图/周视图
├─ design/           Compose 主题与通用组件
└─ MainActivity.kt   单 Activity + Navigation
```

### B.3 复用已有设计（不重写）

- **数据模型** → 直接落地 `03-技术附录.md` 附录 A（实体、枚举、优先级数值 100/90/70/50/30/10、`protected`/`ai_no_schedule` 字段）。
- **冲突判定** → 附录 C 三类（A 固定相交 / B 转场缓冲 / C 灵活排入睡眠段）。
- **AI 能力边界** → 附录 D（端侧引擎 vs 云端 LLM；模式×动作矩阵）。
- **置信度** → 附录 E（0.7/0.85 阈值）。
- **v1.0 清单** → `04-技术栈与路线图.md` 第 4 节。
- **行为规则** → `02-PRD-v1.1.md`（睡眠/用餐可被临时活动挤占、AI 不压缩睡眠、保留空闲≥20%、拆分须确认 vs 时间分配可自动、夜间仅闹钟级可打断 等）。

### B.4 实现阶段（每阶段可独立验证）

1. **脚手架**：Gradle 文件、wrapper（CN 镜像）、settings.gradle.kts（阿里 Maven 镜像）、Manifest、MainActivity + Compose 主题、`local.properties` 模板。→ 验证：Android Studio 打开能 Sync + 构建 + 启动空界面。
2. **数据层 `core/data`**：Room 实体（ScheduleItem/RoutineRule/Course/Goal/ChangeLog/ImportBatch/Conflict/PendingParse）+ DAO + Database + DataStore 偏好（调度模式、保留空闲比例、勿扰时段、置信度阈值）+ Repository。→ 验证：Room 单测。
3. **调度引擎 `core/scheduler`**（纯 Kotlin，最高价值）：优先级计算、冲突 A/B/C 检测、灵活任务安排 + 保留空闲比例、休息/缓冲插入（第十章规则）、精度展示规则、平衡模式行为、ChangeLog 生成。→ 验证：**调度引擎单测（核心）**，覆盖附录 C 全部场景与睡眠覆盖语义。
4. **AI 客户端 `core/ai`**：LLM 接口（Retrofit，可换实现）、置信度门控、离线队列（Room `pending_parse_queue`）+ WorkManager 批量解析、数据最小化 payload。→ 验证：mock 客户端单测。
5. **通知 `core/notifications`**：通知渠道、普通/重要提醒、勿扰、睡眠勿扰例外（仅闹钟级+显式夜间开关）。→ 验证：手动。
6. **首页 `feature/home`**：时间线、下一项倒计时、完成/跳过/延后、快速添加四入口、调整说明查看。→ 验证：手动。
7. **导入 `feature/import`**：文本/CSV 解析、确认页、待确认列表、去重、整批撤销。→ 验证：手动 + 解析单测。
8. **计划资料 `feature/plan`**：基础作息 CRUD（三档范围）、课程表管理、学期归档、特殊日期、导入资料管理、调度偏好。→ 验证：手动。
9. **视图 `feature/views`**：日视图 + 周视图，精度展示 + 四视图一致性（先行落地这两视图）。→ 验证：手动。
10. **联通与冒烟**：跑通 PRD §21 闭环；写 `docs/05-环境配置.md` 归档 Part A。

### B.5 关键文件（首批）

- `settings.gradle.kts`、`build.gradle.kts`（root）、`app/build.gradle.kts`
- `gradle/wrapper/gradle-wrapper.properties`（CN 镜像）、`gradlew.bat`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/smartplanner/MainActivity.kt`
- `app/src/main/java/com/smartplanner/core/scheduler/*.kt`（引擎主体）
- `app/src/test/java/com/smartplanner/core/scheduler/*Test.kt`（引擎单测）
- `app/src/main/java/com/smartplanner/core/data/*.kt`（实体/DAO/DB）

---

## 验证（端到端）

1. **环境**：A.7 三条命令 + Android Studio 跑空项目成功。
2. **单测**：`gradlew :app:testDebugUnitTest`（调度引擎、解析、DAO）全绿——这是我在无真机时能依赖的核心验证。
3. **构建**：`gradlew :app:assembleDebug` 产出 APK。
4. **运行**：装模拟器/真机，冒烟跑 PRD §21 闭环：导入 CSV → 确认 → 首页见日程 → 加临时活动覆盖睡眠（见"覆盖作息"标记、次日恢复）→ 灵活任务自动避让固定事项与睡眠 → 休息插入 → 完成事项 → 查看 AI 调整说明并撤销 → 断网仍可查看/执行。
5. **验收点**：对齐 `02-PRD-v1.1.md` 第二十章 v1.1 修订补充项。

---

## 沙箱与协作说明

- 我的 Shell 运行在沙箱内，网络/系统路径受限；**Android Studio 的 GUI 构建由你在本机执行**。我负责写代码与单测，并尽量通过 `gradlew` 验证（若沙箱放行网络与工具链路径）。
- 代码编写不依赖工具链，可与你的环境安装**并行推进**。
- 所有版本号在实现时钉死为当年稳定版；本计划给出的版本为下限/推荐。
