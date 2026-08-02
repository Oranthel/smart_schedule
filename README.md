# 智能日程安排 App（SmartPlanner）

面向学生与重度日程用户的端侧智能日程应用。**端侧规则引擎**处理确定性调度（冲突检测、灵活任务安排、休息插入、睡眠保护），**云端 LLM** 仅负责自然语言/OCR 解析，数据最小化上传，全程离线可用、AI 调整透明可撤销。

v1.0 已实现核心闭环：导入 → 识别确认 → 区分固定/灵活 → 首页执行 → 添加临时事项 → 冲突处理 → 休息插入 → 日/周视图一致 → 本地通知 → AI 调整可撤销 → 离线可用。

---

## 技术栈

| 层 | 选型 |
|---|---|
| UI | Kotlin + Jetpack Compose + Material3，单 Activity + Navigation |
| 架构 | MVVM + 手动 DI（`AppContainer`），不引入 Hilt/Koin |
| 持久化 | Room（8 实体）+ DataStore（用户偏好） |
| 调度 | 纯 Kotlin 端侧规则引擎 `ScheduleEngine`（可单测） |
| 后台 | WorkManager（离线解析队列消费）+ AlarmManager（提醒） |
| 网络 | Retrofit + Moshi（云端 LLM 解析，v1.0 为桩） |
| 最低/目标 | minSdk 26 / compileSdk & targetSdk 35 / JVM 17 |

> 不需要 Docker。本地开发仅需 JDK 17+ / Android SDK / Gradle wrapper，推荐直接装 Android Studio（自带 JDK 与 SDK Manager）。

---

## 工程结构

```
com.smartplanner/
├─ MainActivity.kt              单 Activity，装载 SmartRoot
├─ SmartPlannerApp.kt           Application，持有 AppContainer + 注册通知渠道
├─ di/AppContainer.kt           手动 DI：DB / Prefs / Repository / Notifier / WorkManager 入队
├─ core/
│  ├─ scheduler/                端侧规则引擎（ScheduleEngine / ConflictDetector / RestInserter）
│  │  └─ model/                 引擎输入输出模型（DayPlanInput/Result/TimeBlock…）
│  ├─ data/
│  │  ├─ entity/                Room 实体（ScheduleItem/RoutineRule/Course/Goal/…）
│  │  ├─ model/                 枚举（ItemType/Fixedness/Priority/ScheduleMode/ConflictType…）
│  │  ├─ db/                    Database / DAO / TypeConverters
│  │  ├─ prefs/                 UserPreferences（DataStore）
│  │  └─ repo/                  ScheduleRepository（CRUD + 重排 + 导入确认/撤销）
│  ├─ ai/                       CsvParser / TextParseService / ParseClient / ConfidenceGating / ParseQueueWorker
│  └─ notifications/            NotificationChannels / ReminderNotifier / ReminderReceiver
├─ ui/
│  ├─ theme/                    Compose 主题（Color/Theme/Type）
│  ├─ navigation/SmartRoot.kt   底部导航 + NavHost（今日/导入/计划资料/周视图）
│  ├─ home/                     首页执行中心（时间线 + 下一项 + 快速添加 + 完成/跳过/延后）
│  ├─ imports/                  CSV 导入与确认（置信度门控 + 整批撤销）
│  ├─ plan/                     计划资料（作息/课程 CRUD + 调度偏好）
│  └─ views/                    周视图
└─ res/                         strings / themes / 图标
```

---

## 快速开始

### 1. 配置开发环境

详见 [docs/05-环境配置.md](docs/05-环境配置.md)。要点：
- 安装 Android Studio（官网 https://developer.android.com/studio ；国内镜像 https://mirrors.tuna.tsinghua.edu.cn/AndroidStudio/ ）
- 配置国内 Maven 镜像（已在 `settings.gradle.kts` 预置阿里/华为源）
- 设置 `JAVA_HOME` / `ANDROID_HOME` 环境变量

### 2. 构建与运行

```powershell
# 在项目根目录执行（Windows 用 gradlew.bat）
.\gradlew :app:assembleDebug          # 构建 Debug APK
.\gradlew :app:installDebug           # 安装到已连接设备/模拟器
.\gradlew :app:testDebugUnitTest      # 运行单元测试
```

或在 Android Studio 中打开 `d:\app`，Sync 后直接 Run。

### 3. 冒烟验证（PRD §21 闭环）

1. **导入**：进入「导入」页，粘贴 CSV（含 `type,title,start,end,location` 行），点「导入并解析」→ 批次待确认 → 「确认并入档」。
2. **计划资料**：在「计划资料」页添加睡眠段（23:00–07:00，勾选睡眠段）、课程、设置调度模式与空闲比例。
3. **首页执行**：「今日」页见时间线与下一项卡片；点「添加」快速加入临时活动或代办。
4. **冲突与覆盖**：临时活动覆盖睡眠/用餐时见「作息被覆盖」变更说明（次日恢复）；灵活任务自动避让固定事项与睡眠段。
5. **完成/撤销**：对事项执行完成/跳过/延后；在「导入」页可整批撤销已确认批次。
6. **周视图**：「周视图」页翻周查看，与日视图数据一致。
7. **离线**：断网后仍可查看/执行；快速记录失败自动入离线队列，联网后 WorkManager 自动重试。

---

## 单元测试

调度引擎是最高价值、最可验证的部分，覆盖附录 C 全部冲突场景与睡眠覆盖语义：

```powershell
.\gradlew :app:testDebugUnitTest
```

测试位于 `app/src/test/java/com/smartplanner/core/scheduler/ScheduleEngineTest.kt`，覆盖：
- A 类：固定事项相交冲突
- B 类：转场缓冲不足
- C 类：灵活任务拒绝排入睡眠段
- 临时活动覆盖睡眠/用餐、保留空闲比例、休息插入

---

## 关键设计决策（PRD v1.1 评审结论）

- **优先级**：取消/调课 100 > 硬性固定（课程/会议/截止）90 > 临时活动 70 > 基础作息 50 > 灵活 30 > 可选 10。所有硬性固定事项最高优先级。
- **睡眠/用餐可被临时活动挤占**：`protectedSegment=false`，AI 不压缩睡眠（`aiNoSchedule=true` 仅约束 AI 不向睡眠段排灵活任务），作息规则次日恢复。
- **AI 边界**：端侧引擎处理确定性逻辑；云端 LLM 仅做自然语言/OCR 解析，置信度门控（0.70 待复核 / 0.85 直接写入），全程可撤销。
- **拆分仅建议**：目标拆分生成 `SPLIT_SUGGESTION`，须用户确认；时间分配可自动。
- **夜间勿扰**：仅闹钟级提醒 + 用户显式夜间开关可打断睡眠时段。

---

## 文档索引

| 文档 | 内容 |
|---|---|
| [docs/01-评审报告.md](docs/01-评审报告.md) | PRD 评审与决策记录 |
| [docs/02-PRD-v1.1.md](docs/02-PRD-v1.1.md) | 修订版 PRD 主体 |
| [docs/03-技术附录.md](docs/03-技术附录.md) | 数据模型 / 冲突定义 / AI 边界 / 置信度阈值 |
| [docs/04-技术栈与路线图.md](docs/04-技术栈与路线图.md) | 技术栈选型与版本迭代路线 |
| [docs/05-环境配置.md](docs/05-环境配置.md) | Android 开发环境配置指南 |

---

## 后续版本（路线图）

- **v1.1**：目标拆分确认 UI、特殊日期管理、学期归档、桌面小组件
- **v1.2**：真实 LLM 端点接入、OCR 导入、月视图、数据备份/导出
