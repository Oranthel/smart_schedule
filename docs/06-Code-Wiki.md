# SmartPlanner — Code Wiki

> 面向学生与重度日程用户的端侧智能日程应用。**端侧规则引擎**处理确定性调度（冲突检测、灵活任务安排、休息插入、睡眠保护），**云端 LLM** 仅负责自然语言/OCR 解析，数据最小化上传，全程离线可用、AI 调整透明可撤销。
>
> 本文档为代码级 Wiki，覆盖项目整体架构、模块职责、关键类与函数、依赖关系及运行方式。所有源文件引用均可点击跳转。

---

## 目录

1. [项目概览](#1-项目概览)
2. [整体架构](#2-整体架构)
3. [工程结构](#3-工程结构)
4. [模块职责详解](#4-模块职责详解)
   - 4.1 [入口与 DI](#41-入口与-di)
   - 4.2 [core/scheduler 调度引擎](#42-corescheduler-调度引擎)
   - 4.3 [core/data 数据层](#43-coredata-数据层)
   - 4.4 [core/ai 解析模块](#44-coreai-解析模块)
   - 4.5 [core/notifications 通知模块](#45-corenotifications-通知模块)
   - 4.6 [ui 表现层](#46-ui-表现层)
5. [关键数据模型](#5-关键数据模型)
6. [调度引擎核心算法](#6-调度引擎核心算法)
7. [依赖关系](#7-依赖关系)
8. [项目运行方式](#8-项目运行方式)
9. [测试与持续集成](#9-测试与持续集成)
10. [关键设计决策](#10-关键设计决策)

---

## 1. 项目概览

| 项 | 值 |
|---|---|
| 应用名 | SmartPlanner（智能日程安排 App） |
| applicationId | `com.smartplanner` |
| versionName | 1.0 |
| 最低/目标 SDK | minSdk 26 / compileSdk & targetSdk 35 |
| JVM 版本 | 17（CI 使用 JDK 21 构建） |
| 主语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material3 |
| 架构模式 | 单 Activity + Navigation，MVVM + 手动 DI |
| 持久化 | Room（5 实体）+ DataStore（用户偏好） |
| v1.0 核心闭环 | 导入 → 识别确认 → 区分固定/灵活 → 首页执行 → 添加临时事项 → 冲突处理 → 休息插入 → 日/周视图一致 → 本地通知 → AI 调整可撤销 → 离线可用 |

---

## 2. 整体架构

采用**分层 + 单向数据流**架构，自上而下：

```
┌─────────────────────────────────────────────────────────────┐
│  UI 层 (ui/)  Jetpack Compose + ViewModel                    │
│  HomeScreen / ImportScreen / PlanScreen / WeekScreen         │
│  各 ViewModel 暴露 StateFlow<UiState>，调用 Repository 动作   │
└───────────────────────────┬─────────────────────────────────┘
                            │ 依赖注入
┌───────────────────────────▼─────────────────────────────────┐
│  DI 容器 (di/AppContainer)  手动单例，进程级                  │
│  Database / Preferences / Repository / Notifier / 入队       │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│  领域层 (core/)                                              │
│  ├─ scheduler/  端侧规则引擎（纯 Kotlin，可单测，离线可解释）  │
│  ├─ data/       Room 实体/DAO/Database/Repository/Prefs       │
│  ├─ ai/         CSV 本地解析 + 云端 LLM 解析（桩）+ 置信度门控 │
│  └─ notifications/  AlarmManager 定时 + BroadcastReceiver 通知 │
└─────────────────────────────────────────────────────────────┘
```

**核心数据流（首页为例）：**

```
Room/DAO (Flow) ─┐
DataStore (Flow) ─┼─> ScheduleRepository.observeDayPlan() ─> ScheduleEngine.planDay()
                 │        (callbackFlow + combine)              (Dispatchers.Default)
                 │                                                  │
                 └───────────────────────────────────> List<DayRow> ┘
                                                            │
                                          HomeViewModel.uiState (StateFlow)
                                                            │
                                                  HomeScreen (collectAsStateWithLifecycle)
```

Repository 把当日事项、课程、作息与偏好多流合并，喂给 `ScheduleEngine` 产出 `DayPlanResult`，再映射成展示用 `DayRow`。引擎为纯函数式实现，无 Android 依赖，保证可单测。

---

## 3. 工程结构

```
com.smartplanner/
├─ MainActivity.kt              单 Activity，装载 SmartRoot
├─ SmartPlannerApp.kt           Application，持有 AppContainer + 注册通知渠道
├─ di/AppContainer.kt           手动 DI：DB / Prefs / Repository / Notifier / WorkManager 入队
├─ core/
│  ├─ scheduler/                端侧规则引擎
│  │  ├─ ScheduleEngine.kt      日计划编排主入口
│  │  ├─ ConflictDetector.kt    A/B 类冲突检测
│  │  ├─ RestInserter.kt        休息插入 + 区间减法
│  │  └─ model/SchedulerModels.kt  引擎输入输出模型
│  ├─ data/
│  │  ├─ entity/Entities.kt     Room 实体（ScheduleItem/RoutineRule/Course/ImportBatch/PendingParse）
│  │  ├─ model/Enums.kt         枚举（ItemType/Fixedness/Priority/ConflictType…）
│  │  ├─ db/Daos.kt             5 个 DAO 接口
│  │  ├─ db/SmartDatabase.kt    RoomDatabase 单例
│  │  ├─ db/Converters.kt       枚举/Set 类型转换器
│  │  ├─ prefs/UserPreferences.kt  DataStore 偏好
│  │  └─ repo/ScheduleRepository.kt  仓库：连接数据层与引擎
│  ├─ ai/                       CsvParser / TextParseService / ParseClient / ParsedItem
│  │                            ConfidenceGating / ParseQueueWorker
│  └─ notifications/            NotificationChannels / ReminderNotifier / ReminderReceiver
├─ ui/
│  ├─ theme/                    Color / Theme / Type
│  ├─ navigation/SmartRoot.kt   底部导航 + NavHost（今日/导入/计划资料/周视图）
│  ├─ home/                     首页执行中心
│  ├─ imports/                  CSV 导入与确认
│  ├─ plan/                     计划资料（作息/课程 CRUD + 调度偏好）
│  └─ views/                    周视图
└─ res/                         strings / themes / 图标
```

完整文件树见 [README.md 工程结构段](file:///d:/app/smart_schedule/README.md)。

---

## 4. 模块职责详解

### 4.1 入口与 DI

#### [SmartPlannerApp](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/SmartPlannerApp.kt)
`Application` 子类。`onCreate()` 中：
- 构造并持有 `AppContainer`（进程级单例 DI 容器）。
- 调用 `NotificationChannels.ensure(this)` 注册三个通知渠道。

#### [MainActivity](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/MainActivity.kt)
单 Activity。从 `application` 取 `AppContainer`，`setContent` 中套用 `SmartPlannerTheme` 并装载 `SmartRoot(container)`。

#### [AppContainer](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/di/AppContainer.kt)
手动依赖容器（v1.0 不引入 Hilt/Koin），由 `SmartPlannerApp` 持有。

| 属性 | 类型 | 作用 |
|---|---|---|
| `database` | `SmartDatabase` | `lazy` 单例 Room 数据库 |
| `preferences` | `UserPreferences` | `lazy` DataStore 偏好 |
| `textParseService` | `TextParseService` | 解析服务（端侧 fallback + 云端 LLM） |
| `repository` | `ScheduleRepository` | 核心仓库；构造时注入 `enqueuePendingParse` 回调 |
| `reminderNotifier` | `ReminderNotifier` | 提醒调度器 |

关键方法 `enqueuePendingParse()`：用 `WorkManager.enqueueUniqueWork("parse_queue", APPEND_OR_REPLACE, ...)` 触发 `ParseQueueWorker`，约束 `NetworkType.CONNECTED`（联网后消费离线队列）。

### 4.2 core/scheduler 调度引擎

端侧确定性规则引擎，纯 Kotlin 无 Android 依赖，是项目最高价值、最可验证部分。

#### [ScheduleEngine](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/scheduler/ScheduleEngine.kt)

日计划编排主入口。`planDay(input, mode, freeRatioThreshold)` 处理顺序对齐 PRD 第八章：

1. **应用取消规则**：`input.cancellationIds` 中的锚点被滤除。
2. **冲突检测（A/B）**：委托 `ConflictDetector.detect()`，产出 `ConflictFinding` 列表。类型 C（排入睡眠）在放置阶段以"拒绝排入"实现，不产出冲突。
3. **覆盖作息**：非 ROUTINE 的占用方裁剪作息/睡眠段（`RestInserter.subtract`）。睡眠/用餐可被临时活动覆盖，但仅本次，规则次日恢复；若被裁剪则记 `COVERS_ROUTINE` 变更。AI 不压缩睡眠。
4. **计算空闲区间**：`freeIntervals()` 合并 busy 后取 `[dayStart, dayEnd]` 补集。
5. **放置灵活任务**：按 `priority 降序 → deadline 升序 → estMinutes 降序` 排序，首个能容纳的空闲块放入；记录 `TIME_ALLOCATED`。无整块空位但总剩余足够时发 `SPLIT_SUGGESTION`（须用户确认）+ `UNSCHEDULED`。`ANYTIME` 任务受保留空闲比例约束。
6. **插入休息**：`RestInserter.restMinutes()` 计算休息时长，紧随任务后有空位则插入，记 `REST_INSERTED`。
7. **产出变更说明**：汇总所有 `ChangeRecord`。

返回 `DayPlanResult`（条目、冲突、变更、未安排、空闲统计）。

#### [ConflictDetector](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/scheduler/ConflictDetector.kt)

附录 C 冲突检测：

- **类型 A（`A_FIXED_OVERLAP`）**：两个"不可移动"锚点（`HARD` 或 `TEMP_ACTIVITY`）时间相交。
- **类型 B（`B_BUFFER_INSUFFICIENT`）**：相邻锚点地点不同且间隔 `< locationBufferMinutes`（默认 15 分钟）。
- **类型 C**：AI 排入睡眠段——在引擎放置阶段直接拒绝，不产出 `ConflictFinding`。

内部 `Intervals` 对象提供半开区间 `[s, e)` 的 `overlap` / `overlapAmount` 工具。

#### [RestInserter](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/scheduler/RestInserter.kt)

休息/缓冲插入（PRD 第十章）：

- `restMinutes(est, intensity)`：`<45 → 5min`；`45..90 → 10min`；`>90 高强度 → 20min`；`>90 普通 → 10min`。
- `buildRestEntry(after, minutes)`：在任务后构造 `REST_BUFFER` 休息条目。
- `subtract(block, cutters)`：区间减法，从 block 中切掉 cutters，返回剩余子段（用于作息被覆盖后裁剪）。

#### [SchedulerModels](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/scheduler/model/SchedulerModels.kt)

引擎输入输出模型：

| 类型 | 说明 |
|---|---|
| `TimeBlock` | 已落座时间块（固定/课程/临时活动/作息/睡眠），`aiNoSchedule=true` 表示 AI 不得排入 |
| `FlexibleTask` | 待安排灵活任务（代办/目标拆分/可选） |
| `ScheduleEntry` | 引擎产出的日程条目（含 `rest`/`coversRoutine` 标记） |
| `ConflictFinding` | 冲突发现（类型 + 涉及项 + 原因） |
| `ChangeRecord` | 变更记录（`ChangeType` + 原因 + 前后值） |
| `DayPlanInput` | 引擎输入（锚点 + 灵活任务 + 日界 + 取消集 + 转场缓冲） |
| `DayPlanResult` | 引擎输出（条目 + 冲突 + 变更 + 未安排 + 空闲统计 + 模式） |
| `Intensity` | 任务强度（LOW/MEDIUM/HIGH） |

### 4.3 core/data 数据层

#### [Entities](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/data/entity/Entities.kt)

5 个 Room 实体：

| 实体 | 表名 | 职责 |
|---|---|---|
| `ScheduleItem` | `schedule_item` | 事项统一抽象（课程/固定/临时/作息/代办/目标拆分/休息）。索引 `(scheduleDateEpoch, status)` 与 `importBatchId` |
| `RoutineRule` | `routine_rule` | 重复作息规则（如睡眠段），`weekdays` 逗号分隔，`aiNoSchedule` 标记睡眠 |
| `Course` | `course` | 课程表（按 `weekday` 1..7 + 起止分钟） |
| `ImportBatch` | `import_batch` | 导入批次（来源/解析版本/数量/确认状态） |
| `PendingParse` | `pending_parse` | 离线解析队列项（payload + kind + attempts） |

`ScheduleItem` 关键字段：`type`/`precision`/`fixedness`/`priority`/`startMinute`/`endMinute`/`estMinutes`/`deadlineEpoch`/`scheduleDateEpoch`/`importBatchId`/`confidence`/`needsReview`/`status`/`aiNoSchedule`/`dayOfWeek`。

#### [Enums](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/data/model/Enums.kt)

核心枚举与优先级常量：

- `ItemType`：FIXED / ROUTINE / COURSE / TEMP_ACTIVITY / TODO / GOAL_TASK / REST_BUFFER
- `PrecisionLevel`：EXACT / RANGE / PART_OF_DAY / DATE / WEEK / MONTH / DEADLINE / ANYTIME
- `Fixedness`：HARD（不可自动移动）/ SOFT / FLEXIBLE / OPTIONAL
- `ItemStatus`：PENDING / SCHEDULED / IN_PROGRESS / DONE / SKIPPED / CANCELLED / OVERDUE / UNSCHEDULED
- `ScheduleMode`：CONSERVATIVE / BALANCED / ACTIVE
- `ConflictType`：A_FIXED_OVERLAP / B_BUFFER_INSUFFICIENT / C_PROTECTED_VIOLATION
- `ChangeType`：COVERS_ROUTINE / MOVED_FLEXIBLE / TIME_ALLOCATED / SPLIT_SUGGESTION / REST_INSERTED / UNSCHEDULED / STATUS_CHANGED

`Priority` 单例常量（高→低）：

```
CANCEL_RULE=100 > HARD_EXAM_TRANSPORT=95 > HARD_MEDICAL=93 >
HARD_COURSE_MEETING_DEADLINE=91 > HARD_USER_LOCKED=90 >
TEMP_ACTIVITY=70 > ROUTINE=50 > FLEXIBLE=30 > ANYTIME=10
```

#### [Daos](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/data/db/Daos.kt)

5 个 DAO 接口，全部返回 `Flow` 以支持响应式观察：

- `ScheduleItemDao`：`observeAll`/`observeDay(date)`/`observePending`/`findByBatch`/`findById`/`insert`/`insertAll`/`update`/`updateStatus`/`deletePendingByBatch`/`delete`
- `ImportBatchDao`：`observeAll`/`insert`/`confirm`/`delete`
- `PendingParseDao`：`observeAll`/`takeBatch(limit)`/`insert`/`delete`/`bumpAttempts`
- `RoutineRuleDao`：`observeActive`/`insert`/`delete`
- `CourseDao`：`observeActive`/`insert`/`archive`

#### [SmartDatabase](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/data/db/SmartDatabase.kt)

`RoomDatabase`，version=1，`exportSchema=false`。双重检查锁单例 `get(context)`，数据库文件 `smart_planner.db`。注册 `Converters`。

#### [Converters](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/data/db/Converters.kt)

Room 类型转换器：枚举 ↔ String、`Set<Int>`/`Set<Long>` ↔ 逗号分隔 String。

#### [UserPreferences](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/data/prefs/UserPreferences.kt)

基于 DataStore（name=`smart_preferences`）的偏好。暴露 `Flow`：

| 偏好 | 默认值 | 说明 |
|---|---|---|
| `scheduleMode` | BALANCED | 调度模式 |
| `freeRatio` | 0.20 | 保留空闲比例 |
| `dndStart` / `dndEnd` | 22:00 / 07:00 | 勿扰时段（分钟） |
| `nightInterrupt` | false | 夜间是否允许闹钟级打断 |
| `confidenceLow` / `confidenceHigh` | 0.70 / 0.85 | 置信度门控阈值 |

`set*` 系列方法为 `suspend`，通过 `edit` 写入。

#### [ScheduleRepository](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/data/repo/ScheduleRepository.kt)

核心仓库，连接数据层与调度引擎。

- **`observeDayPlan(dateEpoch)`**：`callbackFlow` 合并事项/作息/课程/模式/空闲比例五流，调用 `planDay()` 产出 `List<DayRow>`，`distinctUntilChanged` 去重。
- **`planDay()`**：构造锚点（今日确认事项 + 匹配星期课程 + 匹配星期作息）与灵活任务（代办/目标拆分），调用 `engine.planDay()` 后映射为 `DayRow`。
- **动作**：`markDone` / `skip` / `postpone`（标 SKIPPED 并复制为次日 PENDING）/ `addTempActivity` / `addTodo` / `setScheduleMode` / `setFreeRatio` / `setDnd` / `setConfidence`。
- **导入**：`importCsv(text)`（CSV 本地解析入待确认）/ `confirmBatch`（按置信度门控落库）/ `undoBatch`（整批撤销）/ `quickNote`（在线解析，失败入离线队列并触发 `onParseQueueRequest`）。
- **作息/课程 CRUD**：`addRoutine` / `deleteRoutine` / `addCourse` / `archiveCourse`。

私有扩展函数 `ScheduleItem.toTimeBlock()` / `Course.toTimeBlock()` / `RoutineRule.toTimeBlock()` / `ScheduleItem.toFlexibleTask()` 负责实体到引擎模型的映射，按类型推导 `Fixedness` 与 `Priority`。

### 4.4 core/ai 解析模块

AI 边界：仅解析，不调度、不直接写入正式日程，须置信度门控 + 用户确认。

#### [ParseClient](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/ai/ParseClient.kt)
解析客户端接口。`parse(payload, kind)` 返回 `List<ParsedItem>`。`kind` 取值 `QUICK_NOTE`/`IMPORT_TEXT`/`IMPORT_CSV`/`IMPORT_OCR`。数据最小化：仅上传 payload 片段，不含全量历史。

#### [CsvParser](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/ai/CsvParser.kt)
确定性 CSV 解析（v1.0 导入主路径，无需 LLM）。表头不区分大小写可省略，支持 `type,title,start,end,weekday,est,deadline,location,confidence`。按类型推导 `precision`/`fixedness`/`priority`，置信度缺省 1.0，解析失败行跳过。

#### [TextParseService](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/ai/TextParseService.kt)
`ParseClient` 实现。`IMPORT_CSV` 走 `CsvParser`；`QUICK_NOTE`/`IMPORT_TEXT`/`IMPORT_OCR` 走 `parseWithLlm`。**v1.0 LLM 为桩**：未配置端点时返回空并 Log，调用方应将原文入离线队列。真实接入点见 TODO 注释。

#### [ParsedItem](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/ai/ParsedItem.kt)
解析产出。带 `confidence` 与 `pendingQuestions`（待确认问题）。仅含解析结果，不直接写入正式日程。

#### [ConfidenceGating](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/ai/ConfidenceGating.kt)
置信度门控（附录 E）。`decide(confidence, low, high)`：`>=high → WRITE`；`>=low → WRITE_REVIEW`；否则 `PENDING`。带 `pendingQuestions` 的一律 `PENDING`。

#### [ParseQueueWorker](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/ai/ParseQueueWorker.kt)
`CoroutineWorker`。`doWork()` 批量取 `pending_parse`（limit 10）→ 调用解析服务 → 建 `ImportBatch` → 写入 PENDING 项 → 删除队列项。失败 `bumpAttempts`，`attempts >= 3` 丢弃。

### 4.5 core/notifications 通知模块

#### [NotificationChannels](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/notifications/NotificationChannels.kt)
三个渠道：`NORMAL`（LOW）、`IMPORTANT`（HIGH）、`ALARM`（HIGH + 震动模式）。`ensure(context)` 在 API ≥ O 上创建。

#### [ReminderNotifier](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/notifications/ReminderNotifier.kt)
提醒调度（PRD 第十四章）。`schedule(itemId, title, text, at, type, isAlarmLevel)`：用 `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, ...)` 定时。渠道选择：闹钟级→ALARM；课程/固定→IMPORTANT；其他→NORMAL。勿扰判断 `isInDnd()` 支持跨午夜（如 22:00→07:00），夜间仅"闹钟级 + 用户显式夜间开关"可打断，否则静默跳过。`cancel(itemId)` 取消。

#### [ReminderReceiver](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/notifications/ReminderReceiver.kt)
`BroadcastReceiver`。收到闹钟后用 `NotificationCompat` 发送通知，点击跳转 `MainActivity`。

### 4.6 ui 表现层

#### [SmartRoot](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/ui/navigation/SmartRoot.kt)
顶层根组合。`Scaffold` + `NavigationBar` 底部四 Tab + `NavHost`，四个目的地共享同一 `AppContainer`。路由枚举 `SmartRoute`：HOME（今日）/ IMPORT（导入）/ PLAN（计划资料）/ WEEK（周视图）。导航采用 `popUpTo(start, saveState)` + `launchSingleTop` + `restoreState` 保持状态。

#### 首页 [HomeScreen](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/ui/home/HomeScreen.kt) / [HomeViewModel](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/ui/home/HomeViewModel.kt)
执行中心。`HomeUiState` 含日期/时间线 rows/模式/空闲比例/下一项/冲突数。ViewModel `combine` 日计划/模式/空闲比例/冲突流，并按当前分钟计算 `nextRow`。动作：`markDone`/`skip`/`postpone`/`addTempActivity`/`addTodo`/`setMode`/`regenerate`。UI 含 `ModeSwitcher`（保守/平衡/进取分段按钮）、`NextUpCard`（下一项 + 空闲比例 + 冲突数）、`DayRowCard`（时间线卡片，含类型色点与完成/跳过/延后按钮）、`AddItemDialog`（快速添加临时活动或代办）。

#### 导入 [ImportScreen](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/ui/imports/ImportScreen.kt) / [ImportViewModel](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/ui/imports/ImportViewModel.kt)
`ImportUiState` 含批次/按批次分组的待确认项/离线队列长度/消息。动作：`importCsv`/`confirmBatch`/`undoBatch`/`quickNote`/`clearMessage`。

#### 计划资料 [PlanScreen](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/ui/plan/PlanScreen.kt) / [PlanViewModel](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/ui/plan/PlanViewModel.kt)
`PlanUiState` 含作息/课程/模式/空闲比例/勿扰/置信度。Tab 切换作息与课程。动作：`addRoutine`（含睡眠段标记）/`deleteRoutine`/`addCourse`/`archiveCourse`/`setMode`/`setFreeRatio`/`setDnd`/`setConfidence`。含 `parseHm`/`fmtHm` 时间工具与 `WEEKDAYS` 列表。

#### 周视图 [WeekScreen](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/ui/views/WeekScreen.kt) / [WeekViewModel](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/ui/views/WeekViewModel.kt)
`WeekUiState` 含本周一起 7 天的日计划。ViewModel 用 `weekStart.flatMapLatest` + `combine` 聚合 7 个 `observeDayPlan` 流。动作：`prevWeek`/`nextWeek`/`thisWeek`。

#### [Theme](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/ui/theme/Theme.kt) / [Color](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/ui/theme/Color.kt)
`SmartPlannerTheme` 支持动态色（Android 12+）与亮/暗色方案。色板：Blue/Orange/Green/Red/Gray。

---

## 5. 关键数据模型

### 5.1 事项统一抽象

`ScheduleItem` 用 `type` 区分七类事项，用 `fixedness`/`priority`/`precision` 三维度描述调度特性。这是整个系统的核心抽象——课程、固定、临时活动、作息、代办、目标拆分、休息缓冲共用一张表。

### 5.2 优先级体系（附录 A.3）

```
取消/调课 100 > 硬性固定(考试/交通 95, 医疗 93, 课程/会议/截止 91, 用户锁定 90)
            > 临时活动 70 > 基础作息(含睡眠/用餐) 50 > 灵活 30 > 可选 10
```

所有硬性固定事项最高优先级。睡眠/用餐 `protectedSegment=false`，可被临时活动挤占，但 AI 不得压缩睡眠（`aiNoSchedule=true` 仅约束 AI 不向睡眠段排灵活任务）。

### 5.3 时间表示

全程使用**自午夜起的分钟数**（`startMinute`/`endMinute`，0..1440），半开区间 `[s, e)`。日期用 `epochDay`（`Long`）。UI 层 `formatHm()`/`fmtHm()` 格式化为 `HH:mm`。

---

## 6. 调度引擎核心算法

`ScheduleEngine.planDay()` 是系统大脑，伪代码：

```
effective = anchors.filter { id ∉ cancellationIds }
conflicts = ConflictDetector.detect(effective, buffer)        // A/B 类

occupying = effective.filter { type ≠ ROUTINE }
routines  = effective.filter { type == ROUTINE }
for r in routines:
    pieces = RestInserter.subtract(r, occupying)              // 被覆盖裁剪
    if pieces总长 < r原长: 记 COVERS_ROUTINE
    routineRemains += pieces

busy = sort(occupying + routineRemains)
free = freeIntervals(busy, dayStart, dayEnd)                  // 补集
totalFree = Σ free

tasks = flexibleTasks.sortBy(priority↓, deadline↑, est↓)
for task in tasks:
    slot = firstFreeSlotWithSize(mutableFree, task.est)
    if slot == null:
        if priority > ANYTIME and totalFree-placed >= est:
            记 SPLIT_SUGGESTION
        if priority > ANYTIME: 记 UNSCHEDULED
        unscheduled += task; continue
    if priority <= ANYTIME:                                   // ANYTIME 受保留比例约束
        if totalFree - placed - est < ratio*totalFree:
            unscheduled += task; continue
    放入 entry; 记 TIME_ALLOCATED
    rest = RestInserter.restMinutes(est, intensity)
    if rest in 1..roomAfter: 插入休息 entry; 记 REST_INSERTED
    更新 mutableFree

all = sort(occupyingEntries + routineEntries + placed + restEntries)
return DayPlanResult(all, conflicts, changes, unscheduled, 空闲统计, mode)
```

关键不变量：
- **类型 C 冲突不产出**：灵活任务直接被"无空位"逻辑拒入睡眠段。
- **拆分仅建议**：`SPLIT_SUGGESTION` 须用户确认，不自动拆。
- **次日恢复**：作息覆盖仅影响当日实例，`RoutineRule` 不变。

---

## 7. 依赖关系

### 7.1 模块间依赖

```
ui.* ──> di.AppContainer ──> core.data.repo.ScheduleRepository
                              │
                              ├──> core.scheduler.ScheduleEngine ──> ConflictDetector, RestInserter
                              ├──> core.data.db.SmartDatabase ──> 5 DAO ──> 5 Entity
                              ├──> core.data.prefs.UserPreferences
                              ├──> core.ai.TextParseService ──> CsvParser, ConfidenceGating
                              └──> core.ai.ParseQueueWorker (WorkManager 触发)

core.notifications.ReminderNotifier ──> core.data.prefs.UserPreferences
core.notifications.ReminderReceiver ──> MainActivity (点击跳转)
```

### 7.2 第三方依赖（[app/build.gradle.kts](file:///d:/app/smart_schedule/app/build.gradle.kts)）

| 类别 | 依赖 | 版本 |
|---|---|---|
| AndroidX 核心 | core-ktx / lifecycle-runtime-ktx / lifecycle-viewmodel-compose / activity-compose | 1.13.1 / 2.8.7 / 2.8.7 / 1.9.3 |
| Compose | compose-bom + ui + ui-graphics + ui-tooling-preview + material3 + material-icons-extended + navigation-compose | BOM 2024.10.01 / navigation 2.8.4 |
| Room | room-runtime + room-ktx（KSP 编译 room-compiler） | 2.6.1 |
| DataStore | datastore-preferences | 1.1.1 |
| WorkManager | work-runtime-ktx | 2.9.1 |
| 网络 | retrofit + converter-moshi + moshi-kotlin | 2.11.0 / 1.15.1 |
| 协程 | kotlinx-coroutines-android | 1.8.1 |
| 测试 | junit / kotlinx-coroutines-test / robolectric / room-testing / androidx.test.core | 4.13.2 / 1.8.1 / 4.13 / 2.6.1 / 1.6.1 |

### 7.3 插件

- `com.android.application` 8.7.3
- `org.jetbrains.kotlin.android` + `org.jetbrains.kotlin.plugin.compose` 2.0.21
- `com.google.devtools.ksp` 2.0.21-1.0.28

### 7.4 Android 权限（[AndroidManifest.xml](file:///d:/app/smart_schedule/app/src/main/AndroidManifest.xml)）

`POST_NOTIFICATIONS`、`SCHEDULE_EXACT_ALARM`、`USE_EXACT_ALARM`、`RECEIVE_BOOT_COMPLETED`、`INTERNET`。注册 `ReminderReceiver`；WorkManager 通过 `androidx.startup` 默认初始化。

---

## 8. 项目运行方式

### 8.1 环境准备

- JDK 17+（CI 用 21）、Android SDK（compileSdk 35）、Gradle wrapper（无需本地装 Gradle）。
- 推荐直接装 Android Studio（自带 JDK 与 SDK Manager）。
- 复制 [local.properties.template](file:///d:/app/smart_schedule/local.properties.template) 为 `local.properties`，填入本机 `sdk.dir`（路径无中文空格）。
- [settings.gradle.kts](file:///d:/app/smart_schedule/settings.gradle.kts) 已预置国内 Maven 镜像（阿里/华为）；CI 环境自动切换官方源。

### 8.2 构建命令（Windows 用 `gradlew.bat`）

```powershell
.\gradlew :app:assembleDebug          # 构建 Debug APK
.\gradlew :app:installDebug           # 安装到已连接设备/模拟器
.\gradlew :app:testDebugUnitTest      # 运行单元测试
```

或在 Android Studio 中打开 `d:\app\smart_schedule`，Sync 后直接 Run。

### 8.3 纯云端构建（不需本地安装）

push 到 GitHub 后 [`.github/workflows/android.yml`](file:///d:/app/smart_schedule/.github/workflows/android.yml) 自动触发：
- `build` Job：运行单元测试 + 构建 Debug APK，产物 `debug-apk-*`（保留 30 天）+ 测试报告（保留 7 天）。
- `release` Job：仅 `main` 分支 push 触发，构建 Release APK（保留 90 天）。

在仓库 `Actions → 最新运行 → Artifacts` 下载。如需签名 Release APK，在仓库 `Settings → Secrets` 添加 `SIGNING_KEY` 等并在工作流增加签名步骤。

### 8.4 冒烟验证（PRD §21 闭环）

1. **导入**：「导入」页粘贴 CSV（含 `type,title,start,end,location` 行）→「导入并解析」→ 批次待确认 →「确认并入档」。
2. **计划资料**：添加睡眠段（如 23:00–07:00）、课程、设置调度模式与空闲比例。
3. **首页执行**：「今日」页见时间线与下一项卡片；「添加」快速加入临时活动或代办。
4. **冲突与覆盖**：临时活动覆盖睡眠/用餐见「作息被覆盖」变更说明；灵活任务自动避让固定事项与睡眠段。
5. **完成/撤销**：执行完成/跳过/延后；「导入」页可整批撤销已确认批次。
6. **周视图**：翻周查看，与日视图数据一致。
7. **离线**：断网仍可查看/执行；快速记录失败自动入离线队列，联网后 WorkManager 自动重试。

---

## 9. 测试与持续集成

### 9.1 单元测试

调度引擎是最高价值、最可验证部分，覆盖附录 C 全部冲突场景与睡眠覆盖语义。测试位于 [ScheduleEngineTest.kt](file:///d:/app/smart_schedule/app/src/test/java/com/smartplanner/core/scheduler/ScheduleEngineTest.kt)：

| 测试 | 覆盖场景 |
|---|---|
| `twoHardCoursesOverlap_producesTypeAConflict` | A 类：两个硬性固定相交 |
| `tempActivityCoversSleep_recordsCoverage_notConflict` | 临时活动覆盖睡眠：记 COVERS_ROUTINE，非冲突，睡眠被裁剪 |
| `flexibleTaskNotPlacedIntoSleep` | C 类：灵活任务不排入睡眠段 |
| `flexibleTaskPlacedInFreeSlot` | 灵活任务正常排入空闲 |
| `restInsertedAfterLongTask` | 休息插入（120min 中等 → 10min） |
| `anytimeTaskRespectsFreeRatio` | ANYTIME 受保留空闲比例约束 |
| `noSingleSlotButTotalEnough_emitsSplitSuggestion` | 无整块空位 → 拆分建议 + UNSCHEDULED |
| `differentLocationSmallGap_producesTypeBConflict` | B 类：转场缓冲不足 |
| `cancellationRemovesAnchor` | 取消规则生效 |
| `sleepNotCompressedByAiScheduling` | AI 不压缩睡眠 |

```powershell
.\gradlew :app:testDebugUnitTest
```

### 9.2 CI 工作流

[android.yml](file:///d:/app/smart_schedule/.github/workflows/android.yml) 触发条件：push `main`/`develop`、PR 到 `main`/`develop`、手动。步骤含 Checkout → JDK 21 → Gradle setup → `chmod +x gradlew` → 修 CRLF → `testDebugUnitTest` → `assembleDebug` → 上传 APK 与测试报告。

---

## 10. 关键设计决策

源自 [PRD v1.1 评审结论](file:///d:/app/smart_schedule/docs/01-评审报告.md)，已落地于代码：

1. **优先级**：取消/调课 100 > 硬性固定（课程/会议/截止）90 > 临时活动 70 > 基础作息 50 > 灵活 30 > 可选 10。所有硬性固定事项最高优先级（见 [Priority](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/data/model/Enums.kt)）。
2. **睡眠/用餐可被临时活动挤占**：`protectedSegment=false`，AI 不压缩睡眠（`aiNoSchedule=true` 仅约束 AI 不向睡眠段排灵活任务），作息规则次日恢复（见 [ScheduleEngine.planDay](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/scheduler/ScheduleEngine.kt)）。
3. **AI 边界**：端侧引擎处理确定性逻辑；云端 LLM 仅做自然语言/OCR 解析，置信度门控（0.70 待复核 / 0.85 直接写入），全程可撤销（见 [ConfidenceGating](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/ai/ConfidenceGating.kt)）。
4. **拆分仅建议**：目标拆分生成 `SPLIT_SUGGESTION`，须用户确认；时间分配可自动。
5. **夜间勿扰**：仅闹钟级提醒 + 用户显式夜间开关可打断睡眠时段（见 [ReminderNotifier](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/core/notifications/ReminderNotifier.kt)）。
6. **手动 DI**：v1.0 不引入 Hilt/Koin，保持轻量（见 [AppContainer](file:///d:/app/smart_schedule/app/src/main/java/com/smartplanner/di/AppContainer.kt)）。
7. **引擎纯函数化**：`ScheduleEngine` 无 Android 依赖，保证可单测、离线可解释。

### 后续版本（路线图）

- **v1.1**：目标拆分确认 UI、特殊日期管理、学期归档、桌面小组件、真实 LLM 端点接入、OCR 导入。
- **v1.2**：月视图、数据备份/导出、年视图、模式调优。

详见 [04-技术栈与路线图.md](file:///d:/app/smart_schedule/docs/04-技术栈与路线图.md)。
