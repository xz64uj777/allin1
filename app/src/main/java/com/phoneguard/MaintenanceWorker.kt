package com.phoneguard

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class MaintenanceWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        ScanEngine.scan(applicationContext)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
