package com.smartplanner.core.data.repo

import com.smartplanner.core.data.model.ChangeType
import com.smartplanner.core.data.model.ConflictType
import com.smartplanner.core.data.model.Fixedness
import com.smartplanner.core.data.model.ItemStatus
import com.smartplanner.core.data.model.ItemType
import com.smartplanner.core.data.model.PrecisionLevel
import com.smartplanner.core.data.model.Priority
import com.smartplanner.core.data.model.ScheduleMode
import com.smartplanner.core.data.entity.ChangeLog
import com.smartplanner.core.data.entity.Conflict
import com.smartplanner.core.data.entity.Course
import com.smartplanner.core.data.entity.ImportBatch
import com.smartplanner.core.data.entity.PendingParse
import com.smartplanner.core.data.entity.RoutineRule
import com.smartplanner.core.data.entity.ScheduleItem
import com.smartplanner.core.data.db.SmartDatabase
import com.smartplanner.core.data.prefs.UserPreferences
import com.smartplanner.core.scheduler.ScheduleEngine
import com.smartplanner.core.scheduler.model.DayPlanInput
import com.smartplanner.core.scheduler.model.FlexibleTask
import com.smartplanner.core.scheduler.model.TimeBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** 首页/视图用的展示行（合并规则展开 + 事项实例）。 */
data class DayRow(
    val itemId: Long?,
    val title: String,
    val startMinute: Int?,
    val endMinute: Int?,
    val type: ItemType,
    val status: ItemStatus,
    val priority: Int,
    val location: String?,
    val rest: Boolean = false,
    val aiNoSchedule: Boolean = false,
    val needsReview: Boolean = false,
)

/**
 * 仓储与编排层：CRUD + 引擎输入构建 + 重排 + 导入确认/撤销。
 * 行为遵循 PRD v1.1：睡眠/用餐可被临时活动覆盖、AI 不压缩睡眠、保留空闲比例、拆分仅建议。
 */
class ScheduleRepository(
    private val db: SmartDatabase,
    private val prefs: UserPreferences,
    private val engine: ScheduleEngine = ScheduleEngine(),
    private val onPendingParseEnqueued: () -> Unit = {},
) {
    private val itemDao = db.scheduleItemDao()
    private val routineDao = db.routineRuleDao()
    private val courseDao = db.courseDao()
    private val goalDao = db.goalDao()
    private val changeDao = db.changeLogDao()
    private val batchDao = db.importBatchDao()
    private val conflictDao = db.conflictDao()
    private val pendingDao = db.pendingParseDao()

    // ---------- Flows ----------

    fun observeDayItems(dateEpoch: Long): Flow<List<ScheduleItem>> = itemDao.observeDay(dateEpoch)
    fun observeActiveRoutines() = routineDao.observeActive()
    fun observeActiveCourses() = courseDao.observeActive()
    fun observeGoals() = goalDao.observeAll()
    fun observeConflicts() = conflictDao.observeOpen()
    fun observeChanges() = changeDao.observeRecent()
    fun observeBatches() = batchDao.observeAll()
    fun observePendingParse() = pendingDao.observeAll()
    fun observeUnscheduled() = itemDao.observeByStatus(ItemStatus.UNSCHEDULED)
    fun observePendingItems() = itemDao.observeByStatus(ItemStatus.PENDING)

    fun observeScheduleMode(): Flow<ScheduleMode> = prefs.mode
    fun observeFreeRatio(): Flow<Float> = prefs.freeRatio

    /** 合并展开的日视图（规则展开 + 事项实例），按起始时间排序。 */
    fun observeDayPlan(dateEpoch: Long): Flow<List<DayRow>> =
        combine(observeDayItems(dateEpoch), observeActiveRoutines(), observeActiveCourses()) { items, routines, courses ->
            val date = LocalDate.ofEpochDay(dateEpoch)
            val wd = date.dayOfWeek.value
            val rows = mutableListOf<DayRow>()

            // 作息规则展开
            routines.filter { it.active && wd in it.weekdays && dateInRange(date, it) && date.toEpochDay() !in it.exceptions }
                .forEach { r ->
                    rows += DayRow(null, r.title, r.startMinute, r.endMinute, ItemType.ROUTINE, ItemStatus.SCHEDULED, Priority.ROUTINE, null, aiNoSchedule = r.aiNoSchedule)
                }
            // 课程展开
            courses.filter { !it.archived && it.weekday == wd }
                .forEach { c ->
                    rows += DayRow(null, c.title, c.startMinute, c.endMinute, ItemType.COURSE, ItemStatus.SCHEDULED, Priority.HARD_COURSE_MEETING_DEADLINE, c.classroom)
                }
            // 事项实例（过滤 TODO 原项：已安排为 GOAL_TASK 在时间线显示，原 TODO 不重复）
            items.filter { it.type != ItemType.TODO }.forEach { it -> rows += it.toRow() }
            rows.sortedWith(compareBy({ it.startMinute ?: Int.MAX_VALUE }, { it.priority }))
        }

    // ---------- 重排 ----------

    /** 重新生成某日：构建输入 → 跑引擎 → 持久化放置项/变更/冲突。 */
    suspend fun regenerateDay(dateEpoch: Long) {
        val date = LocalDate.ofEpochDay(dateEpoch)
        val wd = date.dayOfWeek.value
        val mode = prefs.mode.firstSafe(ScheduleMode.BALANCED)
        val freeRatio = prefs.freeRatio.firstSafe(0.20f)

        val routines = routineDao.getActive().filter { it.active && wd in it.weekdays && dateInRange(date, it) && date.toEpochDay() !in it.exceptions }
        val courses = courseDao.getActive().filter { !it.archived && it.weekday == wd }
        val dayItems = itemDao.observeDay(dateEpoch).firstSafe(emptyList())
        val anchors = mutableListOf<TimeBlock>()

        routines.forEach { r -> anchors += TimeBlock("rule:r:${r.id}", r.title, r.startMinute, r.endMinute, Priority.ROUTINE, Fixedness.FLEXIBLE, ItemType.ROUTINE, aiNoSchedule = r.aiNoSchedule, sourceItemId = r.id) }
        courses.forEach { c -> anchors += TimeBlock("rule:c:${c.id}", c.title, c.startMinute, c.endMinute, Priority.HARD_COURSE_MEETING_DEADLINE, Fixedness.HARD, ItemType.COURSE, location = c.classroom, sourceItemId = c.id) }
        dayItems.filter { it.type == ItemType.TEMP_ACTIVITY || it.type == ItemType.FIXED }.forEach { a ->
            val s = a.startMinute ?: return@forEach; val e = a.endMinute ?: (s + 60)
            anchors += TimeBlock("item:${a.id}", a.title, s, e, a.priority, Fixedness.SOFT, a.type, location = a.location, sourceItemId = a.id)
        }

        val flexible = dayItems.filter { it.type == ItemType.TODO && it.estMinutes != null && it.status in setOf(ItemStatus.PENDING, ItemStatus.SCHEDULED, ItemStatus.UNSCHEDULED) }
            .map { FlexibleTask("item:${it.id}", it.title, it.estMinutes!!, it.priority.coerceAtLeast(Priority.FLEXIBLE), it.deadlineEpoch, sourceItemId = it.id) }

        // 清理旧的引擎生成项（保留 DONE/SKIPPED 的放置项）
        dayItems.filter { (it.type == ItemType.GOAL_TASK || it.type == ItemType.REST_BUFFER) && it.status == ItemStatus.SCHEDULED }
            .forEach { itemDao.delete(it) }

        val result = engine.planDay(
            DayPlanInput(dateEpochDay = dateEpoch, anchors = anchors, flexibleTasks = flexible, dayStart = 0, dayEnd = 24 * 60),
            mode = mode, freeRatioThreshold = freeRatio,
        )

        // 持久化放置项
        result.entries.filter { it.type == ItemType.GOAL_TASK || it.type == ItemType.REST_BUFFER }.forEach { e ->
            itemDao.insert(ScheduleItem(
                type = e.type, title = e.title, precision = PrecisionLevel.EXACT,
                fixedness = Fixedness.FLEXIBLE, priority = e.priority,
                dateEpoch = dateEpoch, startMinute = e.startMinute, endMinute = e.endMinute,
                status = ItemStatus.SCHEDULED, estMinutes = if (e.type == ItemType.GOAL_TASK) e.endMinute - e.startMinute else null,
                parentId = e.sourceItemId,
            ))
        }
        // 变更记录
        result.changes.forEach { c -> changeDao.insert(ChangeLog(trigger = "regenerate:${dateEpoch}", changeType = c.changeType, reason = c.reason, before = c.before, after = c.after, affectedIds = c.affectedSourceIds, reversible = c.changeType != ChangeType.UNSCHEDULED)) }
        // 冲突
        result.conflicts.forEach { cf -> conflictDao.insert(Conflict(type = cf.type, itemIds = cf.itemIds.mapNotNull { it.substringAfterLast(":").toLongOrNull() }.toSet(), dateEpoch = dateEpoch, severity = 1)) }
        // 标记无法安排的代办
        result.unscheduled.filter { it.priority > Priority.ANYTIME }.forEach { u ->
            u.sourceItemId?.let { sid -> itemDao.getById(sid)?.let { itemDao.setStatus(it.id, ItemStatus.UNSCHEDULED) } }
        }
    }

    // ---------- CRUD ----------

    suspend fun addTempActivity(dateEpoch: Long, title: String, start: Int, end: Int, location: String? = null): Long {
        val id = itemDao.insert(ScheduleItem(
            type = ItemType.TEMP_ACTIVITY, title = title, precision = PrecisionLevel.EXACT,
            fixedness = Fixedness.SOFT, priority = Priority.TEMP_ACTIVITY,
            dateEpoch = dateEpoch, startMinute = start, endMinute = end, location = location,
            status = ItemStatus.SCHEDULED,
        ))
        regenerateDay(dateEpoch)
        return id
    }

    suspend fun addTodo(title: String, estMinutes: Int, deadlineEpoch: Long? = null): Long {
        val today = LocalDate.now().toEpochDay()
        val id = itemDao.insert(ScheduleItem(
            type = ItemType.TODO, title = title, precision = PrecisionLevel.ANYTIME,
            fixedness = Fixedness.FLEXIBLE, priority = Priority.FLEXIBLE,
            dateEpoch = today, estMinutes = estMinutes, deadlineEpoch = deadlineEpoch, status = ItemStatus.UNSCHEDULED,
        ))
        regenerateDay(today)
        return id
    }

    suspend fun markDone(id: Long) = updateStatusLinked(id, ItemStatus.DONE)
    suspend fun skip(id: Long) = updateStatusLinked(id, ItemStatus.SKIPPED)

    /** 完成或跳过：联动放置项与原待办（parentId），再重排当日。 */
    private suspend fun updateStatusLinked(id: Long, status: ItemStatus) {
        val placed = itemDao.getById(id) ?: return
        itemDao.setStatus(placed.id, status)
        placed.parentId?.let { pid -> itemDao.getById(pid)?.let { itemDao.setStatus(pid, status) } }
        placed.dateEpoch?.let { d -> if (d == LocalDate.now().toEpochDay()) regenerateDay(d) }
    }

    suspend fun postpone(id: Long) {
        val placed = itemDao.getById(id) ?: return
        val tomorrow = (placed.dateEpoch ?: LocalDate.now().toEpochDay()) + 1
        val target = placed.parentId?.let { itemDao.getById(it) } ?: placed
        itemDao.update(target.copy(dateEpoch = tomorrow, status = ItemStatus.UNSCHEDULED))
        itemDao.delete(placed)
        regenerateDay(LocalDate.now().toEpochDay())
    }

    // ---------- 导入 ----------

    /** CSV 导入：本地解析 → 入待确认项（PENDING），返回批次 id。 */
    suspend fun importCsv(text: String): Long {
        val parsed = com.smartplanner.core.ai.CsvParser.parse(text)
        val batchId = batchDao.insert(ImportBatch(sourceType = "CSV", rawRef = null, parseVersion = "csv-local-1", parsedCount = parsed.size, confirmed = false))
        val low = prefs.confidenceLow.firstSafe(0.70f)
        val high = prefs.confidenceHigh.firstSafe(0.85f)
        val items = parsed.map { p ->
            val decision = com.smartplanner.core.ai.ConfidenceGating.decide(p, low, high)
            ScheduleItem(
                type = p.type, title = p.title, precision = p.precision, fixedness = p.fixedness, priority = p.priority,
                startMinute = p.startMinute, endMinute = p.endMinute, location = p.location,
                estMinutes = p.estMinutes, deadlineEpoch = p.deadlineEpochDay,
                importBatchId = batchId, confidence = p.confidence,
                needsReview = decision == com.smartplanner.core.ai.GatingDecision.WRITE_REVIEW,
                status = if (decision == com.smartplanner.core.ai.GatingDecision.PENDING) ItemStatus.PENDING else ItemStatus.PENDING,
            )
        }
        itemDao.insertAll(items)
        return batchId
    }

    /** 确认导入批次：按类型落库（课程/作息→规则表；临时/代办→事项实例），再重排今日。 */
    suspend fun confirmBatch(batchId: Long) {
        val batch = batchDao.observeAll().firstSafe(emptyList()).firstOrNull { it.id == batchId } ?: return
        val pending = itemDao.observeByStatus(ItemStatus.PENDING).firstSafe(emptyList()).filter { it.importBatchId == batchId }
        val today = LocalDate.now().toEpochDay()
        for (p in pending) {
            when (p.type) {
                ItemType.COURSE -> {
                    courseDao.insert(Course(
                        title = p.title, weekday = 1, startMinute = p.startMinute ?: (8 * 60),
                        endMinute = p.endMinute ?: (p.startMinute ?: (8 * 60)) + 60,
                        weekStart = 1, weekEnd = 20, classroom = p.location, semesterId = "default",
                    ))
                    itemDao.delete(p)
                }
                ItemType.ROUTINE -> {
                    val isSleep = p.title.contains("睡") || p.title.contains("眠")
                    routineDao.insert(RoutineRule(
                        title = p.title, weekdays = setOf(1, 2, 3, 4, 5, 6, 7),
                        startMinute = p.startMinute ?: (23 * 60), endMinute = p.endMinute ?: (7 * 60),
                        protectedSegment = false, aiNoSchedule = isSleep, sourceRef = "import:$batchId",
                    ))
                    itemDao.delete(p)
                }
                ItemType.TEMP_ACTIVITY, ItemType.FIXED -> itemDao.update(p.copy(status = ItemStatus.SCHEDULED, dateEpoch = p.dateEpoch ?: today))
                ItemType.TODO -> itemDao.update(p.copy(status = ItemStatus.UNSCHEDULED))
                else -> {}
            }
        }
        batchDao.update(batch.copy(confirmed = true))
        regenerateDay(today)
    }

    /** 整批撤销：删除该批次所有待确认项。 */
    suspend fun undoBatch(batchId: Long) {
        itemDao.deleteByBatch(batchId)
        val batch = batchDao.observeAll().firstSafe(emptyList()).firstOrNull { it.id == batchId } ?: return
        batchDao.update(batch.copy(confirmed = false))
    }

    /** 快速记录：在线解析；离线/失败则入队（附录 D.4）。 */
    suspend fun quickNote(payload: String) {
        val service = com.smartplanner.core.ai.TextParseService()
        try {
            val parsed = service.parse(payload, "QUICK_NOTE")
            if (parsed.isEmpty()) enqueueParse(payload, "QUICK_NOTE")
            else {
                val batchId = batchDao.insert(ImportBatch(sourceType = "QUICK_NOTE", parseVersion = "llm-1", parsedCount = parsed.size, confirmed = true))
                itemDao.insertAll(parsed.map { it.toItem(batchId) })
                regenerateDay(LocalDate.now().toEpochDay())
            }
        } catch (_: Exception) {
            enqueueParse(payload, "QUICK_NOTE")
        }
    }

    suspend fun enqueueParse(payload: String, kind: String) {
        pendingDao.insert(PendingParse(payload = payload, kind = kind))
        onPendingParseEnqueued()
    }

    suspend fun setScheduleMode(m: ScheduleMode) = prefs.setMode(m)
    suspend fun setFreeRatio(v: Float) = prefs.setFreeRatio(v)

    fun observeDnd(): Flow<Pair<Int, Int>> = combine(prefs.dndStart, prefs.dndEnd) { s, e -> s to e }
    fun observeConfidence(): Flow<Pair<Float, Float>> = combine(prefs.confidenceLow, prefs.confidenceHigh) { l, h -> l to h }
    suspend fun setDnd(start: Int, end: Int) = prefs.setDnd(start, end)
    suspend fun setConfidence(low: Float, high: Float) = prefs.setConfidence(low, high)

    // ---------- 计划资料 CRUD ----------
    suspend fun addRoutine(title: String, weekdays: Set<Int>, start: Int, end: Int, isSleep: Boolean): Long {
        val id = routineDao.insert(RoutineRule(
            title = title, weekdays = weekdays, startMinute = start, endMinute = end,
            protectedSegment = false, aiNoSchedule = isSleep,
        ))
        regenerateDay(LocalDate.now().toEpochDay())
        return id
    }

    suspend fun deleteRoutine(id: Long) = routineDao.deleteById(id)

    suspend fun addCourse(title: String, weekday: Int, start: Int, end: Int, classroom: String?): Long {
        val id = courseDao.insert(Course(
            title = title, weekday = weekday, startMinute = start, endMinute = end,
            weekStart = 1, weekEnd = 20, classroom = classroom, semesterId = "default",
        ))
        regenerateDay(LocalDate.now().toEpochDay())
        return id
    }

    suspend fun archiveCourse(id: Long) = courseDao.archive(id)

    // ---------- helpers ----------

    private fun dateInRange(date: LocalDate, r: RoutineRule): Boolean {
        val d = date.toEpochDay()
        if (r.validFromEpoch != null && d < r.validFromEpoch) return false
        if (r.validToEpoch != null && d > r.validToEpoch) return false
        return true
    }

    private fun ScheduleItem.toRow() = DayRow(
        itemId = id, title = title, startMinute = startMinute, endMinute = endMinute,
        type = type, status = status, priority = priority, location = location,
        rest = type == ItemType.REST_BUFFER, needsReview = needsReview,
    )

    private fun com.smartplanner.core.ai.ParsedItem.toItem(batchId: Long) = ScheduleItem(
        type = type, title = title, precision = precision, fixedness = fixedness, priority = priority,
        startMinute = startMinute, endMinute = endMinute, location = location,
        estMinutes = estMinutes, deadlineEpoch = deadlineEpochDay,
        importBatchId = batchId, confidence = confidence, needsReview = confidence < 0.85f,
        status = ItemStatus.PENDING,
    )
}

/** Flow 首值快捷取（避免每处写 first()）。 */
private suspend fun <T> Flow<T>.firstSafe(default: T): T =
    try { this.first() } catch (_: Exception) { default }
