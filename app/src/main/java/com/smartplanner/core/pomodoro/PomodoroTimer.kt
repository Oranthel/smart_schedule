package com.smartplanner.core.pomodoro

/**
 * 番茄钟阶段。
 * - [WORK]：专注工作阶段。
 * - [SHORT_BREAK]：短休息阶段。
 * - [LONG_BREAK]：长休息阶段。
 */
enum class Phase { WORK, SHORT_BREAK, LONG_BREAK }

/**
 * 番茄钟状态快照。
 *
 * - [phase]：当前所处阶段。
 * - [remainingSeconds]：当前阶段剩余秒数。
 * - [running]：是否正在计时（已 start 且未 pause）。
 * - [completedCount]：累计完成的 WORK 阶段数。
 * - [justCompletedWork]：本次 [PomodoroTimer.tick] 是否刚刚完成一个 WORK 阶段，
 *   UI 可据此触发持久化（写一条 [com.smartplanner.core.data.entity.PomodoroRecord]）。
 */
data class PomodoroState(
    val phase: Phase,
    val remainingSeconds: Int,
    val running: Boolean,
    val completedCount: Int,
    val justCompletedWork: Boolean = false,
)

/**
 * 标准番茄钟计时器（纯 Kotlin，无 Android 依赖，便于单元测试）。
 *
 * 默认节奏：25 分钟工作 + 5 分钟短休息，每完成 4 次工作后进入 15 分钟长休息。
 * 休息结束后自动回到 WORK 阶段。
 *
 * 使用方式：外部每秒调用一次 [tick] 驱动递减，并通过 [state] 读取最新状态。
 */
class PomodoroTimer(
    private val workMinutes: Int = 25,
    private val shortBreakMinutes: Int = 5,
    private val longBreakMinutes: Int = 15,
    private val longBreakEvery: Int = 4,
) {
    private val workSeconds: Int = workMinutes * 60
    private val shortBreakSeconds: Int = shortBreakMinutes * 60
    private val longBreakSeconds: Int = longBreakMinutes * 60

    private var currentPhase: Phase = Phase.WORK
    private var remainingSeconds: Int = workSeconds
    private var running: Boolean = false
    private var completedWorkCount: Int = 0
    // 仅在完成 WORK 的那次 tick 置 true，供 state() 读取后由下次 tick 复位
    private var justCompletedWork: Boolean = false

    /** 开始计时（若已运行则无操作）。 */
    fun start() {
        running = true
    }

    /** 暂停计时（保留剩余秒数与阶段）。 */
    fun pause() {
        running = false
    }

    /** 重置为初始 WORK 阶段，清零已完成计数。 */
    fun reset() {
        currentPhase = Phase.WORK
        remainingSeconds = workSeconds
        running = false
        completedWorkCount = 0
        justCompletedWork = false
    }

    /**
     * 跳过当前阶段，直接进入下一阶段。
     * - 跳过 WORK：不计为完成（不递增 completedCount），进入短休息。
     * - 跳过休息：回到 WORK 阶段。
     */
    fun skip() {
        justCompletedWork = false
        when (currentPhase) {
            Phase.WORK -> {
                currentPhase = Phase.SHORT_BREAK
                remainingSeconds = shortBreakSeconds
            }
            Phase.SHORT_BREAK, Phase.LONG_BREAK -> {
                currentPhase = Phase.WORK
                remainingSeconds = workSeconds
            }
        }
    }

    /**
     * 每秒驱动一次：递减剩余秒数，到 0 时切换阶段。
     *
     * WORK 完成时：递增 [completedWorkCount]，若累计达到 [longBreakEvery] 的倍数则进入长休息，
     * 否则进入短休息；并在返回的 state 中置 [PomodoroState.justCompletedWork] = true。
     * 休息结束：回到 WORK 阶段。
     *
     * 未运行时调用仅返回当前状态，不递减。
     */
    fun tick(): PomodoroState {
        // 每次 tick 先复位记录标记，仅本次完成 WORK 时重新置 true
        justCompletedWork = false
        if (!running) return state()

        remainingSeconds -= 1
        if (remainingSeconds <= 0) {
            when (currentPhase) {
                Phase.WORK -> {
                    completedWorkCount += 1
                    justCompletedWork = true
                    val isLongBreak = completedWorkCount % longBreakEvery == 0
                    currentPhase = if (isLongBreak) Phase.LONG_BREAK else Phase.SHORT_BREAK
                    remainingSeconds = if (isLongBreak) longBreakSeconds else shortBreakSeconds
                }
                Phase.SHORT_BREAK, Phase.LONG_BREAK -> {
                    currentPhase = Phase.WORK
                    remainingSeconds = workSeconds
                }
            }
        }
        return state()
    }

    /** 读取当前状态快照（不改变内部状态）。 */
    fun state(): PomodoroState = PomodoroState(
        phase = currentPhase,
        remainingSeconds = remainingSeconds,
        running = running,
        completedCount = completedWorkCount,
        justCompletedWork = justCompletedWork,
    )
}
