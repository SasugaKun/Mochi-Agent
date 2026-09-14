package com.mochiagent.app.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mochiagent.app.service.TaskWorker
import com.mochiagent.app.service.LoopWorker
import com.mochiagent.app.util.DebugLog

/**
 * Receives a scheduled task's alarm and hands execution off to [TaskWorker]. Kept trivial: the
 * receiver must return fast, so the actual LLM run happens in WorkManager (reliable, constraint-
 * aware) which in turn keeps a foreground service alive for the duration. The completed run
 * advances the task's nextRunAt, which re-drives [AutomationScheduler] to arm the next alarm.
 */
class AutomationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE_TASK -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, 0L)
                DebugLog.d("AutomationAlarmReceiver", "fired task=$taskId")
                TaskWorker.enqueue(context.applicationContext, taskId, scheduledAt)
            }
            ACTION_FIRE_LOOP -> {
                val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: return
                val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, 0L)
                DebugLog.d("AutomationAlarmReceiver", "fired loop=$conversationId")
                LoopWorker.enqueue(context.applicationContext, conversationId, scheduledAt)
            }
        }
    }

    companion object {
        const val ACTION_FIRE_TASK = "com.mochiagent.app.automation.TASK_FIRE"
        const val ACTION_FIRE_LOOP = "com.mochiagent.app.automation.LOOP_FIRE"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_SCHEDULED_AT = "scheduled_at"
    }
}
