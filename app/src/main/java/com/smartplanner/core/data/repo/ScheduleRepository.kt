package com.smartplanner.core.data.repo

import com.smartplanner.core.ai.ConfidenceGating
import com.smartplanner.core.ai.TextParseService
import com.smartplanner.core.data.db.SmartDatabase
import com.smartplanner.core.data.entity.Course
import com.smartplanner.core.data.entity.ImportBatch
import com.smartplanner.core.data.entity.PendingParse
import com.smartplanner.core.data.entity.RoutineRule
import com.smartplanner.core.data.entity.ScheduleItem
import com.smartplanner.core.data.model.ChangeType
import com.smartplanner.core.data.model.Fixedness
import com.smartplanner.core.data.model.ItemStatus
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.data.model.PrecisionLevel
import com.smartplanner.core.data.model.Priority
import com.smartplanner.core.data.model.ScheduleMode
import com.smartplanner.core.data.prefs.UserPreferences
import com.smartplanner.core.scheduler.ScheduleEngine
import com.smartplanner.core.scheduler.model.ConflictFinding
import com.smartplanner.core.scheduler.model.DayPlanInput
import com.smartplanner.core.scheduler.model.FlexibleTask
import com.smartplanner.core.scheduler.model.Intensity
import com.smartplanner.core.scheduler.model.TimeBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 日计划行（展示用）。对齐 HomeScreen 的 DayRowCard。
 */
data class DayRow(
    val itemId: Long?,
    val title: String,
    val startMinute: Int?,
    val endMinute: Int?,
    val type: ItemType,
    val status: ItemStatus,
    val rest: Boolean = false,
    val coversRoutine: Boolean = false,
    val needsReview: Boolean = false,
    val location: String? = null,
)

/**
 * 核心仓库：连接数据层与调度引擎。
 * 负责：加载当日锚点 → 调度引擎生成计划 → 产出 DayRow 流 → 写回状态。
 */
class ScheduleRepository(
    private val db: SmartDatabase,
    private val prefs: UserPreferences,
    private val onParseQueueRequest: suspend () -> Unit,
) {
    private val engine = ScheduleEngine()

    /** 最近一次调度的冲突（供 observeConflicts 暴露）。 */
    private val _conflicts = MutableStateFlow<List<ConflictFinding>>(emptyList())
    val conflictsFlow: StateFlow<List<ConflictFinding>> = _conflicts.asStateFlow()

    /** 活跃的固定/课程/临时/作息条目（已确认非 PENDING）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDayPlan(dateEpoch: Long): Flow<List<DayRow>> = callbackFlow {
        // 初始推空列表，等首次调度后推真实数据
        trySend(emptyList())

        val itemsFlow = db.scheduleItemDao().observeDay(dateEpoch)
        val routinesFlow = db.routineRuleDao().observeActive()
        val coursesFlow = db.courseDao().observeActive()
        val modeFlow = prefs.scheduleMode
        val freeRatioFlow = prefs.freeRatio

        val collectJob = launch {
            combine(
                itemsFlow, routinesFlow, coursesFlow, modeFlow, freeRatioFlow,
            ) { items, routines, courses, mode, freeRatio ->
                planDay(dateEpoch, items, routines, courses, mode, freeRatio)
            }.flowOn(Dispatchers.Default).collect { trySend(it) }
        }

        awaitClose { collectJob.cancel() }
    }.distinctUntilChanged()

    fun observeScheduleMode(): Flow<ScheduleMode> = prefs.scheduleMode
    fun observeFreeRatio(): Flow<Float> = prefs.freeRatio
    fun observeConfidence(): Flow<Pair<Float, Float>> = combine(prefs.confidenceLow, prefs.confidenceHigh) { l, h -> l to h }
    fun observeDnd(): Flow<Pair<Int, Int>> = combine(prefs.dndStart, prefs.dndEnd) { s, e -> s to e }

    fun observeConflicts(): Flow<List<ConflictFinding>> = conflictsFlow

    fun observeBatches(): Flow<List<ImportBatch>> = db.importBatchDao().observeAll()
    fun observePendingItems(): Flow<List<ScheduleItem>> = db.scheduleItemDao().observePending()
    fun observePendingParse(): Flow<List<PendingParse>> = db.pendingParseDao().observeAll()

    fun observeActiveRoutines(): Flow<List<RoutineRule>> = db.routineRuleDao().observeActive()
    fun observeActiveCourses(): Flow<List<Course>> = db.courseDao().observeActive()

    private suspend fun planDay(
        dateEpoch: Long,
        items: List<ScheduleItem>,
        routines: List<RoutineRule>,
        courses: List<Course>,
        mode: ScheduleMode,
        freeRatio: Float,
    ): List<DayRow> = withContext(Dispatchers.Default) {
        val dow = LocalDate.ofEpochDay(dateEpoch).dayOfWeek.value

        // 活跃事项 = 非 CANCELLED（含 PENDING 待安排、DONE/SKIPPED 历史显示）
        val active = items.filter { it.status != ItemStatus.CANCELLED }
        // 参与调度的 = 未完成/未跳过（PENDING/SCHEDULED/IN_PROGRESS）
        val schedulable = active.filter {
            it.status in setOf(ItemStatus.PENDING, ItemStatus.SCHEDULED, ItemStatus.IN_PROGRESS)
        }

        // 锚点 = 有起止时间的可调度事项 + 匹配星期课程 + 匹配星期作息
        val anchors = buildList {
            schedulable.filter { it.startMinute != null && it.endMinute != null }.forEach { add(it.toTimeBlock()) }
            courses.filter { it.weekday == dow }.forEach { add(it.toTimeBlock()) }
            routines.filter { it.weekdays.split(",").map { w -> w.trim().toIntOrNull() }.contains(dow) }.forEach { add(it.toTimeBlock()) }
        }.filterNotNull()

        // 灵活任务 = 无固定时间的 TODO/GOAL_TASK（待引擎排入空闲）
        val flex = schedulable.filter {
            it.type in setOf(ItemType.TODO, ItemType.GOAL_TASK) && it.estMinutes != null
        }.map { it.toFlexibleTask() }

        val result = engine.planDay(
            DayPlanInput(
                dateEpochDay = dateEpoch,
                anchors = anchors,
                flexibleTasks = flex,
                dayStart = 0,
                dayEnd = 24 * 60,
            ),
            mode = mode,
            freeRatioThreshold = freeRatio,
        )

        // 缓存冲突供 observeConflicts
        _conflicts.value = result.conflicts

        // itemId → ScheduleItem 映射，用于回填真实状态
        val itemMap = items.associateBy { it.id }

        // 引擎产出条目 → DayRow（回填真实 status / needsReview）
        val engineRows = result.entries.map { e ->
            val item = e.sourceItemId?.let { itemMap[it] }
            DayRow(
                itemId = e.sourceItemId,
                title = e.title,
                startMinute = e.startMinute,
                endMinute = e.endMinute,
                type = e.type,
                status = item?.status ?: ItemStatus.SCHEDULED,
                rest = e.rest,
                coversRoutine = e.coversRoutine,
                needsReview = item?.needsReview ?: false,
                location = e.location,
            )
        }

        // 已完成/跳过/超期但有起止时间的事项也要显示（引擎未处理）
        val historical = active.filter {
            it.status in setOf(ItemStatus.DONE, ItemStatus.SKIPPED, ItemStatus.OVERDUE) &&
                it.startMinute != null
        }.map { it.toDayRow() }

        (engineRows + historical).sortedBy { it.startMinute ?: Int.MAX_VALUE }
    }

    /** ScheduleItem → DayRow（用于已完成/跳过的历史条目）。 */
    private fun ScheduleItem.toDayRow() = DayRow(
        itemId = id,
        title = title,
        startMinute = startMinute,
        endMinute = endMinute,
        type = type,
        status = status,
        rest = type == ItemType.REST_BUFFER,
        coversRoutine = false,
        needsReview = needsReview,
        location = location,
    )

    /** ScheduleItem → TimeBlock。 */
    private fun ScheduleItem.toTimeBlock(): TimeBlock? {
        val s = startMinute ?: return null
        val e = endMinute ?: return null
        val itemType = this.type
        val fxd = when {
            itemType == ItemType.COURSE -> Fixedness.HARD
            itemType == ItemType.FIXED -> Fixedness.HARD
            itemType == ItemType.TEMP_ACTIVITY -> Fixedness.SOFT
            itemType == ItemType.ROUTINE -> Fixedness.FLEXIBLE
            else -> fixedness
        }
        val prio = when (itemType) {
            ItemType.COURSE, ItemType.FIXED -> Priority.HARD_COURSE_MEETING_DEADLINE
            ItemType.TEMP_ACTIVITY -> Priority.TEMP_ACTIVITY
            ItemType.ROUTINE -> Priority.ROUTINE
            else -> priority
        }
        return TimeBlock(
            id = "item-$id",
            title = title,
            startMinute = s,
            endMinute = e,
            priority = prio,
            fixedness = fxd,
            type = itemType,
            location = location,
            aiNoSchedule = aiNoSchedule,
            sourceItemId = id,
        )
    }

    private fun Course.toTimeBlock() = TimeBlock(
        id = "course-$id",
        title = title,
        startMinute = startMinute,
        endMinute = endMinute,
        priority = Priority.HARD_COURSE_MEETING_DEADLINE,
        fixedness = Fixedness.HARD,
        type = ItemType.COURSE,
        location = classroom,
        aiNoSchedule = false,
        sourceItemId = null,
    )

    private fun RoutineRule.toTimeBlock() = TimeBlock(
        id = "routine-$id",
        title = title,
        startMinute = startMinute,
        endMinute = endMinute,
        priority = Priority.ROUTINE,
        fixedness = Fixedness.FLEXIBLE,
        type = ItemType.ROUTINE,
        location = null,
        aiNoSchedule = aiNoSchedule,
        sourceItemId = null,
    )

    private fun ScheduleItem.toFlexibleTask() = FlexibleTask(
        id = "item-$id",
        title = title,
        estMinutes = estMinutes ?: 30,
        priority = priority,
        deadlineEpochDay = deadlineEpoch,
        intensity = Intensity.MEDIUM,
        sourceItemId = id,
    )

    // ---------- 动作 ----------

    suspend fun markDone(id: Long) = withContext(Dispatchers.IO) {
        db.scheduleItemDao().updateStatus(id, ItemStatus.DONE)
    }

    suspend fun skip(id: Long) = withContext(Dispatchers.IO) {
        db.scheduleItemDao().updateStatus(id, ItemStatus.SKIPPED)
    }

    suspend fun postpone(id: Long) = withContext(Dispatchers.IO) {
        // 简化实现：标记为 SKIPPED 并添加为新的临时活动
        val item = db.scheduleItemDao().findById(id) ?: return@withContext
        db.scheduleItemDao().updateStatus(id, ItemStatus.SKIPPED)
        db.scheduleItemDao().insert(
            item.copy(id = 0, status = ItemStatus.PENDING, scheduleDateEpoch = item.scheduleDateEpoch?.plus(1))
        )
    }

    suspend fun addTempActivity(dateEpoch: Long, title: String, start: Int, end: Int) = withContext(Dispatchers.IO) {
        db.scheduleItemDao().insert(
            ScheduleItem(
                type = ItemType.TEMP_ACTIVITY,
                title = title,
                precision = PrecisionLevel.EXACT,
                fixedness = Fixedness.SOFT,
                priority = Priority.TEMP_ACTIVITY,
                startMinute = start,
                endMinute = end,
                status = ItemStatus.SCHEDULED,
                scheduleDateEpoch = dateEpoch,
            )
        )
    }

    suspend fun addTodo(title: String, estMinutes: Int) = withContext(Dispatchers.IO) {
        db.scheduleItemDao().insert(
            ScheduleItem(
                type = ItemType.TODO,
                title = title,
                precision = PrecisionLevel.ANYTIME,
                fixedness = Fixedness.FLEXIBLE,
                priority = Priority.FLEXIBLE,
                estMinutes = estMinutes,
                status = ItemStatus.PENDING,
                scheduleDateEpoch = LocalDate.now().toEpochDay(),
            )
        )
    }

    suspend fun setScheduleMode(mode: ScheduleMode) = prefs.setMode(mode)
    suspend fun setFreeRatio(v: Float) = prefs.setFreeRatio(v)
    suspend fun setDnd(start: Int, end: Int) = prefs.setDnd(start, end)
    suspend fun setConfidence(low: Float, high: Float) = prefs.setConfidence(low, high)

    suspend fun regenerateDay(dateEpoch: Long) { /* 调度已随 flow 自动触发，此处可做手动强制刷新 */ }

    // ---------- 导入 ----------

    suspend fun importCsv(text: String): Long = importText(text, "IMPORT_CSV", "csv-local-1")

    /** 通用导入：按 kind 调用对应解析器，统一入待确认批次。 */
    suspend fun importText(text: String, kind: String, parseVersion: String = "auto-1"): Long = withContext(Dispatchers.IO) {
        val parseService = TextParseService()
        val parsed = parseService.parse(text, kind)
        val now = System.currentTimeMillis().toString()
        val batchId = db.importBatchDao().insert(
            ImportBatch(sourceType = kind, parseVersion = parseVersion, parsedCount = parsed.size, createdAt = now)
        )
        val items = parsed.map { p ->
            ScheduleItem(
                type = p.type,
                title = p.title,
                precision = p.precision,
                fixedness = p.fixedness,
                priority = p.priority,
                startMinute = p.startMinute,
                endMinute = p.endMinute,
                location = p.location,
                estMinutes = p.estMinutes,
                deadlineEpoch = p.deadlineEpochDay,
                scheduleDateEpoch = p.deadlineEpochDay ?: LocalDate.now().toEpochDay(),
                importBatchId = batchId,
                confidence = p.confidence,
                needsReview = p.confidence < 0.85f,
                status = ItemStatus.PENDING,
                dayOfWeek = p.weekday,
            )
        }
        db.scheduleItemDao().insertAll(items)
        batchId
    }

    suspend fun confirmBatch(batchId: Long) = withContext(Dispatchers.IO) {
        val items = db.scheduleItemDao().findByBatch(batchId)
        items.forEach { item ->
            val decision = ConfidenceGating.decide(item.confidence ?: 1f, 0.70f, 0.85f)
            val newStatus = when (decision) {
                com.smartplanner.core.ai.GatingDecision.WRITE -> ItemStatus.SCHEDULED
                com.smartplanner.core.ai.GatingDecision.WRITE_REVIEW -> ItemStatus.SCHEDULED
                com.smartplanner.core.ai.GatingDecision.PENDING -> ItemStatus.PENDING
            }
            db.scheduleItemDao().update(item.copy(status = newStatus, needsReview = (decision == com.smartplanner.core.ai.GatingDecision.WRITE_REVIEW)))
        }
        db.importBatchDao().confirm(batchId)
    }

    suspend fun undoBatch(batchId: Long) = withContext(Dispatchers.IO) {
        db.scheduleItemDao().deletePendingByBatch(batchId)
        db.importBatchDao().delete(batchId)
    }

    suspend fun quickNote(payload: String) = withContext(Dispatchers.IO) {
        val parseService = TextParseService()
        val parsed = parseService.parse(payload, "QUICK_NOTE")
        if (parsed.isNotEmpty()) {
            val now = System.currentTimeMillis().toString()
            val batchId = db.importBatchDao().insert(
                ImportBatch(sourceType = "QUICK_NOTE", parseVersion = "llm-v1", parsedCount = parsed.size, createdAt = now)
            )
            val items = parsed.map { p ->
                ScheduleItem(
                    type = p.type, title = p.title, precision = p.precision,
                    fixedness = p.fixedness, priority = p.priority,
                    startMinute = p.startMinute, endMinute = p.endMinute,
                    location = p.location, estMinutes = p.estMinutes,
                    deadlineEpoch = p.deadlineEpochDay,
                    scheduleDateEpoch = p.deadlineEpochDay ?: LocalDate.now().toEpochDay(),
                    importBatchId = batchId, confidence = p.confidence,
                    needsReview = p.confidence < 0.85f, status = ItemStatus.PENDING,
                    dayOfWeek = p.weekday,
                )
            }
            db.scheduleItemDao().insertAll(items)
        } else {
            // 解析失败，入离线队列
            db.pendingParseDao().insert(PendingParse(payload = payload, kind = "QUICK_NOTE"))
            onParseQueueRequest()
        }
    }

    // ---------- 作息/课程 CRUD ----------

    suspend fun addRoutine(title: String, weekdays: Set<Int>, start: Int, end: Int, aiNoSchedule: Boolean) = withContext(Dispatchers.IO) {
        db.routineRuleDao().insert(
            RoutineRule(title = title, weekdays = weekdays.joinToString(","), startMinute = start, endMinute = end, aiNoSchedule = aiNoSchedule)
        )
    }

    /** 手动添加单条事项（直接入正式日程，不经批次确认）。 */
    suspend fun addManualItem(
        type: ItemType,
        title: String,
        startMinute: Int?,
        endMinute: Int?,
        location: String?,
        estMinutes: Int?,
        dateEpoch: Long,
    ) = withContext(Dispatchers.IO) {
        val fixedness = when (type) {
            ItemType.COURSE, ItemType.FIXED -> Fixedness.HARD
            ItemType.TEMP_ACTIVITY -> Fixedness.SOFT
            else -> Fixedness.FLEXIBLE
        }
        val priority = when (type) {
            ItemType.COURSE, ItemType.FIXED -> Priority.HARD_COURSE_MEETING_DEADLINE
            ItemType.TEMP_ACTIVITY -> Priority.TEMP_ACTIVITY
            ItemType.ROUTINE -> Priority.ROUTINE
            else -> Priority.FLEXIBLE
        }
        val precision = if (startMinute != null && endMinute != null) PrecisionLevel.EXACT else PrecisionLevel.ANYTIME
        db.scheduleItemDao().insert(
            ScheduleItem(
                type = type,
                title = title,
                precision = precision,
                fixedness = fixedness,
                priority = priority,
                startMinute = startMinute,
                endMinute = endMinute,
                location = location,
                estMinutes = estMinutes,
                scheduleDateEpoch = dateEpoch,
                status = ItemStatus.SCHEDULED,
            )
        )
    }

    suspend fun deleteRoutine(id: Long) = withContext(Dispatchers.IO) {
        db.routineRuleDao().delete(id)
    }

    suspend fun addCourse(title: String, weekday: Int, start: Int, end: Int, classroom: String?) = withContext(Dispatchers.IO) {
        db.courseDao().insert(Course(title = title, weekday = weekday, startMinute = start, endMinute = end, classroom = classroom))
    }

    suspend fun archiveCourse(id: Long) = withContext(Dispatchers.IO) {
        db.courseDao().archive(id)
    }
}
