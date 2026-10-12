package com.yourname.dune.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class DustRecomputeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
 override suspend fun doWork(): Result = try {
  val repo = UsageRepository(applicationContext)
  if (repo.hasUsageAccess()) {
   val dao=DuneDatabase.get(applicationContext).trackedApps()
   val previous=dao.getSnapshot()
   val fresh=repo.loadTrackedApps()
   val merged=fresh.map { n -> previous.find { it.packageName==n.packageName }?.let { old ->
    n.copy(state=old.state, dustLevel=if(old.state=="RESCUED") 0f else n.dustLevel, rescueCount=old.rescueCount, buriedAt=old.buriedAt, wipeProgress=old.wipeProgress)
   } ?: n }
   dao.upsertAll(merged)
   sendDustReminder(merged)
  }
  Result.success()
 } catch (e: Exception) { Result.retry() }

 private fun sendDustReminder(apps:List<TrackedAppEntity>) {
  val prefs=applicationContext.getSharedPreferences("dune_settings",Context.MODE_PRIVATE)
  if(!prefs.getBoolean("notifications",false)) return
  if(Build.VERSION.SDK_INT>=33 && applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
  val threshold=prefs.getInt("reminder_days",30)
  val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  val channelId="dune_dust_reminders"
  if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel(channelId,"Dust reminders",NotificationManager.IMPORTANCE_DEFAULT).apply { description="Gentle reminders about apps you have not opened recently" })
  val eligible=apps.filter { it.state!="BURIED" && it.state!="RESCUED" && it.lastUsedDays>=threshold }.take(3)
  if(eligible.isEmpty()) return
  val now=System.currentTimeMillis()
  val last=prefs.getLong("last_dust_notification",0L)
  if(now-last<6L*24*60*60*1000) return
  val names=eligible.joinToString(", ") { it.label }
  val launch=applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
  val pending=PendingIntent.getActivity(applicationContext,0,launch ?: Intent(),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=NotificationCompat.Builder(applicationContext,channelId)
   .setSmallIcon(android.R.drawable.ic_menu_compass)
   .setContentTitle("A little dust has settled")
   .setContentText(if(eligible.size==1) "${eligible[0].label} has been quiet for ${eligible[0].lastUsedDays} days." else "${eligible.size} apps have been quiet for $threshold+ days: $names")
   .setStyle(NotificationCompat.BigTextStyle().bigText("Your forgotten apps are waiting in DUNE. Open the app to brush away the dust."))
   .setContentIntent(pending).setAutoCancel(true).build()
  NotificationManagerCompat.from(applicationContext).notify(4102,notification)
  prefs.edit().putLong("last_dust_notification",now).apply()
 }
 companion object {
  fun schedule(context: Context) {
   val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).setRequiresBatteryNotLow(true).build()
   val request=PeriodicWorkRequestBuilder<DustRecomputeWorker>(24,TimeUnit.HOURS,1,TimeUnit.HOURS).setConstraints(constraints).build()
   WorkManager.getInstance(context).enqueueUniquePeriodicWork("dust_recompute",ExistingPeriodicWorkPolicy.UPDATE,request)
  }
 }
}
