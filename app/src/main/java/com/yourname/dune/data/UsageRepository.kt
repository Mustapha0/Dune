package com.yourname.dune.data
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class UsageRepository(private val context: Context) {
 fun hasUsageAccess(): Boolean {
  val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
  val mode = if (android.os.Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
  else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
  return mode == AppOpsManager.MODE_ALLOWED
 }
 suspend fun loadTrackedApps(): List<TrackedAppEntity> = withContext(Dispatchers.IO) {
  if (!hasUsageAccess()) return@withContext emptyList()
  val pm = context.packageManager
  val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
  val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -365) }.timeInMillis
  val end = System.currentTimeMillis()
  // Android only keeps daily buckets for about a week; coarser buckets reach further back.
  val recent = HashMap<String, Long>()
  listOf(UsageStatsManager.INTERVAL_DAILY, UsageStatsManager.INTERVAL_WEEKLY, UsageStatsManager.INTERVAL_MONTHLY, UsageStatsManager.INTERVAL_YEARLY).forEach { interval ->
   usage.queryUsageStats(interval, start, end)?.forEach { st ->
    if (st.lastTimeUsed > (recent[st.packageName] ?: 0L)) recent[st.packageName] = st.lastTimeUsed
   }
  }
  val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  val resolved = if (android.os.Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0)) else @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
  val now = System.currentTimeMillis()
  resolved.mapNotNull { ri ->
   val info = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
   val pkg = info.packageName
   if (pkg == context.packageName || (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || !info.enabled) return@mapNotNull null
   val last = recent[pkg] ?: 0L
   val days = if (last <= 0L) 365 else ((now - last) / 86_400_000L).toInt().coerceAtLeast(0)
   val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(pkg)
   TrackedAppEntity(pkg, label, days, dustLevel = (days / 120f).coerceIn(0f, 1f))
  }.distinctBy { it.packageName }.sortedByDescending { it.lastUsedDays }
 }
}
