package com.yourname.dune

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.graphics.drawscope.Stroke
import com.yourname.dune.data.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint as NativePaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.random.Random

private val Night = Color(0xFF151A25)
private val Night2 = Color(0xFF0F131C)
private val Sand = Color(0xFFE2A36A)
private val Gold = Color(0xFFD7A879)
private val Muted = Color(0xFFB8B1AB)


private object DuneAudio {
    private var tone: ToneGenerator? = null
    private fun player(): ToneGenerator = tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 45).also { tone = it }
    fun brush() { runCatching { player().startTone(ToneGenerator.TONE_PROP_BEEP2, 35) } }
    fun polished() { runCatching { player().startTone(ToneGenerator.TONE_PROP_ACK, 160) } }
    fun rescued() { runCatching { player().startTone(ToneGenerator.TONE_PROP_PROMPT, 260) } }
}

private data class SandParticle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val radius: Float, val rotation: Float
)

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  enableEdgeToEdge()
  WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
  val db = DuneDatabase.get(this)
  DustRecomputeWorker.schedule(this)
  setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Sand, background = Night2, surface = Night)) { DuneApp(db, UsageRepository(this)) } }
 }
}

@Composable private fun DuneApp(db: DuneDatabase, repo: UsageRepository) {
 val context = LocalContext.current
 val scope = rememberCoroutineScope()
 val prefs = remember { context.getSharedPreferences("dune_settings", Context.MODE_PRIVATE) }
 var allowed by remember { mutableStateOf(repo.hasUsageAccess()) }
 var preview by remember { mutableStateOf(false) }
 var apps by remember { mutableStateOf<List<TrackedAppEntity>>(emptyList()) }
 var selected by remember { mutableStateOf<TrackedAppEntity?>(null) }
 var filter by remember { mutableIntStateOf(30) }
 var notice by remember { mutableStateOf<String?>(null) }
 var settingsOpen by remember { mutableStateOf(false) }
 var detectorOpen by remember { mutableStateOf(false) }
 var guideOpen by remember { mutableStateOf(!prefs.getBoolean("onboarding_complete", false)) }
 var query by remember { mutableStateOf("") }
 var showBuried by remember { mutableStateOf(false) }
 var ambientEnabled by remember { mutableStateOf(prefs.getBoolean("ambient", false)) }
 var hapticsEnabled by remember { mutableStateOf(prefs.getBoolean("haptics", true)) }
 var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean("notifications", false)) }
 var reminderDays by remember { mutableIntStateOf(prefs.getInt("reminder_days", 30)) }
 val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
  allowed = repo.hasUsageAccess()
  if (allowed) scope.launch { val fresh=repo.loadTrackedApps(); val old=db.trackedApps().getSnapshot(); val merged=fresh.map { n -> old.find { it.packageName==n.packageName }?.let { n.copy(state=it.state, dustLevel=if(it.state=="RESCUED") 0f else n.dustLevel, rescueCount=it.rescueCount, buriedAt=it.buriedAt, wipeProgress=it.wipeProgress) } ?: n }; apps=merged; db.trackedApps().upsertAll(merged) }
 }
 LaunchedEffect(Unit) { db.trackedApps().getAll().collect { apps = it } }
 val owner = LocalLifecycleOwner.current
 DisposableEffect(owner, allowed) {
  val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME && allowed) scope.launch { val fresh=repo.loadTrackedApps(); val old=db.trackedApps().getSnapshot(); val merged=fresh.map { n -> old.find { it.packageName==n.packageName }?.let { n.copy(state=it.state, dustLevel=if(it.state=="RESCUED") 0f else n.dustLevel, rescueCount=it.rescueCount, buriedAt=it.buriedAt, wipeProgress=it.wipeProgress) } ?: n }; db.trackedApps().upsertAll(merged) } }
  owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
 }
 DisposableEffect(ambientEnabled) {
  var player: android.media.MediaPlayer? = null
  if (ambientEnabled) runCatching { player = android.media.MediaPlayer.create(context, com.yourname.dune.R.raw.desert_wind)?.apply { isLooping=true; setVolume(.22f,.22f); start() } }
  onDispose { runCatching { player?.stop(); player?.release() } }
 }
 Box(Modifier.fillMaxSize().background(Night2)) {
  DesertBackdrop()
  if (!allowed && !preview) {
   Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(26.dp), verticalArrangement = Arrangement.Center) {
    Text("DUNE", color = Gold, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp)
    Spacer(Modifier.height(18.dp)); Text("Your apps live\nin a desert.", color = Color.White, fontSize = 44.sp, lineHeight = 47.sp, fontWeight = FontWeight.ExtraBold)
    Spacer(Modifier.height(14.dp)); Text("The ones left untouched gather dust. Swipe to clean them, then bring them back with a tap.", color = Muted, fontSize = 15.sp, lineHeight = 24.sp)
    Spacer(Modifier.height(18.dp)); Text("DUNE only reads app-usage timestamps to estimate inactivity. It does not upload your app list.", color=Muted, fontSize=12.sp, lineHeight=18.sp)
    Spacer(Modifier.height(26.dp)); Button(onClick = { permission.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Sand, contentColor = Night)) { Text("GRANT USAGE ACCESS") }
    TextButton(onClick = { preview = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Explore preview mode", color = Gold) }
    Text("You can revoke Usage Access at any time in Android Settings.", color = Muted, fontSize = 11.sp)
   }
  } else {
   val shown = if (preview && apps.isEmpty()) demoApps() else apps
   val visible = shown.filter { if(showBuried) it.state=="BURIED" else it.state!="BURIED" && it.lastUsedDays>=filter }.filter { it.label.contains(query,true) || it.packageName.contains(query,true) }
   Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
     Column { Text("DUNE", color = Gold, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 3.sp); Text("Forgotten no more.", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold) }
     Row { IconButton(onClick={detectorOpen=true}) { Icon(Icons.Default.Explore, "Metal detector", tint=Sand) }; IconButton(onClick={showBuried=!showBuried}) { Icon(if(showBuried) Icons.Default.Unarchive else Icons.Default.Archive, if(showBuried) "Show forgotten apps" else "Show buried apps", tint=Sand) }; IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Default.Settings, "Settings", tint = Sand) } }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha=.045f)).padding(vertical=12.dp), verticalAlignment = Alignment.CenterVertically) {
     val stats = listOf(Triple(Icons.Default.Apps, shown.count { it.state!="BURIED" }.toString(), "TRACKED"), Triple(Icons.Default.HourglassBottom, shown.count { it.lastUsedDays >= 30 && it.state!="BURIED" }.toString(), "DUSTY"), Triple(Icons.Default.AutoAwesome, shown.count { it.state == "RESCUED" }.toString(), "RESCUED"))
     stats.forEachIndexed { i, st -> if (i > 0) Box(Modifier.width(1.dp).height(38.dp).background(Color.White.copy(alpha=.15f))); Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Icon(st.first, null, tint = Sand, modifier = Modifier.size(18.dp)); Text(st.second, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold); Text(st.third, color = Muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp) } }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search apps or packages…",color=Muted,fontSize=13.sp)},leadingIcon={Icon(Icons.Default.Search,null,tint=Sand)},trailingIcon={ if(query.isNotEmpty()) IconButton(onClick={query=""}) { Icon(Icons.Default.Close,"Clear search") } },shape=RoundedCornerShape(14.dp),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Sand,unfocusedBorderColor=Color.White.copy(alpha=.12f),focusedTextColor=Color.White,unfocusedTextColor=Color.White))
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
     Text(if(showBuried) "BURIED RELICS" else "THE FORGOTTEN", color = Gold, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.sp)
     if(!showBuried) Row { listOf(30,60,90).forEach { d -> FilterChip(selected = filter == d, onClick = { filter = d }, label = { Text("${d}d", fontSize = 10.sp) }) } }
    }
    if (visible.isEmpty()) {
     Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if(showBuried) "No buried relics." else "The dunes are quiet.", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text(if(query.isNotBlank()) "Try another search." else "Try Preview mode or a shorter inactivity filter.", color = Muted, fontSize = 13.sp) } }
    } else LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical=10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(visible, key={it.packageName}) { app -> AppCard(app) { selected = app } } }
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(if (preview) "PREVIEW MODE" else "LOCAL ONLY · NO CLOUD", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 8.sp); Text("${visible.size} SHOWN", color = Gold, fontFamily = FontFamily.Monospace, fontSize = 8.sp) }
   }
  }
  notice?.let { Surface(Modifier.align(Alignment.BottomCenter).padding(bottom=24.dp).fillMaxWidth(.9f), color=Color(0xFF292A30), shape=RoundedCornerShape(14.dp)) { Row(Modifier.padding(12.dp), verticalAlignment=Alignment.CenterVertically) { Text(it, color=Color.White, modifier=Modifier.weight(1f), fontSize=12.sp); TextButton(onClick={notice=null}) { Text("OK", color=Sand) } } } }
 }
 if(guideOpen && (allowed || preview)) AlertDialog(onDismissRequest={guideOpen=false;prefs.edit().putBoolean("onboarding_complete",true).apply()},containerColor=Night,
  title={Text("WELCOME TO THE DUNES",color=Gold,fontFamily=FontFamily.Monospace,letterSpacing=1.sp)},
  text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text("1 · FIND",color=Sand,fontWeight=FontWeight.Bold); Text("DUNE estimates when launchable apps were last used. Choose a 30, 60 or 90 day threshold.",color=Muted,fontSize=13.sp)
   Text("2 · BRUSH",color=Sand,fontWeight=FontWeight.Bold); Text("Open a relic and swipe across its sandy mask. Your polishing progress is saved locally.",color=Muted,fontSize=13.sp)
   Text("3 · RESCUE OR BURY",color=Sand,fontWeight=FontWeight.Bold); Text("Rescue opens the app. Bury only hides it inside DUNE; it never uninstalls anything.",color=Muted,fontSize=13.sp)
   Text("Usage access is optional for Preview mode. Your data stays on this phone.",color=Color.White,fontSize=12.sp)
  }},confirmButton={Button(onClick={guideOpen=false;prefs.edit().putBoolean("onboarding_complete",true).apply()},colors=ButtonDefaults.buttonColors(containerColor=Sand,contentColor=Night)){Text("ENTER THE DUNES")}})
 if(detectorOpen) AlertDialog(onDismissRequest={detectorOpen=false},containerColor=Night,
  title={Text("METAL DETECTOR",color=Gold,fontFamily=FontFamily.Monospace,letterSpacing=1.sp)},
  text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
   Text("The strongest signals are the apps that have been quiet the longest.",color=Muted,fontSize=12.sp)
   val candidates=(if(preview && apps.isEmpty()) demoApps() else apps).filter{it.state!="BURIED"}.sortedByDescending{it.lastUsedDays}.take(5)
   if(candidates.isEmpty()) Text("Grant Usage Access or enter Preview mode to scan for forgotten apps.",color=Muted,fontSize=13.sp)
   candidates.forEach { candidate -> Surface(color=Color.White.copy(alpha=.05f),shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().clickable { selected=candidate;detectorOpen=false }) { Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(candidate.label,color=Color.White,fontWeight=FontWeight.SemiBold); Text("${candidate.lastUsedDays} days since last use",color=Muted,fontSize=11.sp) }; Icon(Icons.Default.ChevronRight,null,tint=Sand) } } }
  }},confirmButton={TextButton(onClick={detectorOpen=false}){Text("CLOSE",color=Sand)}})
 if(settingsOpen) SettingsDialog(ambientEnabled,hapticsEnabled,notificationsEnabled,reminderDays,
  onAmbient={ambientEnabled=it;prefs.edit().putBoolean("ambient",it).apply()},
  onHaptics={hapticsEnabled=it;prefs.edit().putBoolean("haptics",it).apply()},
  onNotifications={enabled ->
   notificationsEnabled=enabled;prefs.edit().putBoolean("notifications",enabled).apply()
   if(enabled && android.os.Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
    (context as? ComponentActivity)?.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),7301)
   }
   DustRecomputeWorker.schedule(context)
  }, onDays={reminderDays=it;prefs.edit().putInt("reminder_days",it).apply();DustRecomputeWorker.schedule(context)},onClose={settingsOpen=false},onPrivacy={settingsOpen=false;notice="Your app-usage list and settings stay on this device. DUNE has no network permission."})
 selected?.let { app ->
  WipeDialog(app, onDismiss={selected=null}, onProgress={ p -> if (!app.packageName.startsWith("demo.")) scope.launch { db.trackedApps().upsert(app.copy(wipeProgress=p, dustLevel=1f-p)) } }, onRescue={
   if (app.packageName.startsWith("demo.")) notice = "Preview only — no app was launched."
   else if(app.state=="BURIED") { scope.launch { db.trackedApps().upsert(app.copy(state="ACTIVE",buriedAt=0L)) }; notice="${app.label} has been returned to the dunes." }
   else { val intent = context.packageManager.getLaunchIntentForPackage(app.packageName); if (intent != null) { DuneAudio.rescued(); context.startActivity(intent); scope.launch { db.trackedApps().upsert(app.copy(state="RESCUED", dustLevel=0f, lastUsedDays=0, rescueCount=app.rescueCount+1, buriedAt=0L)) } } else notice = "This app can't be launched." }; selected=null
  }, onBury={
   if (app.packageName.startsWith("demo.")) notice="Preview only — nothing was buried."
   else scope.launch { db.trackedApps().upsert(app.copy(state="BURIED",buriedAt=System.currentTimeMillis())); notice="${app.label} was buried in your archive. It is hidden, not uninstalled." }
   selected=null
  }, hapticsEnabled=hapticsEnabled)
 }
}

@Composable private fun SettingsDialog(ambient:Boolean,haptics:Boolean,notifications:Boolean,days:Int,onAmbient:(Boolean)->Unit,onHaptics:(Boolean)->Unit,onNotifications:(Boolean)->Unit,onDays:(Int)->Unit,onClose:()->Unit,onPrivacy:()->Unit) {
 AlertDialog(onDismissRequest=onClose,containerColor=Night,title={Text("DUNE SETTINGS",color=Gold,fontFamily=FontFamily.Monospace,letterSpacing=2.sp)},text={
  Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
   SettingSwitch("Desert wind ambience","Loop a quiet local soundscape",ambient,onAmbient)
   SettingSwitch("Haptic feedback","Feel each brush stroke",haptics,onHaptics)
   SettingSwitch("Dust reminders","Local notification when apps go unused",notifications,onNotifications)
   Text("REMIND AFTER",color=Gold,fontSize=10.sp,fontFamily=FontFamily.Monospace)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(30,60,90).forEach { n -> FilterChip(selected=days==n,onClick={onDays(n)},label={Text("${n} days")}) } }
   Text("Buried apps are only hidden inside DUNE. Android does not uninstall them.",color=Muted,fontSize=12.sp,lineHeight=17.sp)
   TextButton(onClick=onPrivacy) { Icon(Icons.Default.Lock,null,tint=Sand); Spacer(Modifier.width(6.dp)); Text("Privacy & data",color=Sand) }
  }
 },confirmButton={TextButton(onClick=onClose){Text("DONE",color=Sand)}})
}
@Composable private fun SettingSwitch(title:String,subtitle:String,checked:Boolean,onChecked:(Boolean)->Unit) {
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title,color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=14.sp); Text(subtitle,color=Muted,fontSize=11.sp,lineHeight=15.sp) }; Switch(checked=checked,onCheckedChange=onChecked) }
}

@Composable private fun DesertBackdrop() {
 val context = LocalContext.current
 var tiltX by remember { mutableFloatStateOf(0f) }
 var tiltY by remember { mutableFloatStateOf(0f) }
 val hazeShift by androidx.compose.animation.core.rememberInfiniteTransition(label="haze").animateFloat(initialValue=0f,targetValue=1f,animationSpec=androidx.compose.animation.core.infiniteRepeatable(animation=androidx.compose.animation.core.tween(5200,easing=androidx.compose.animation.core.LinearEasing),repeatMode=androidx.compose.animation.core.RepeatMode.Reverse),label="haze-shift")
 DisposableEffect(context) {
  val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
  val listener = object : SensorEventListener {
   private val rotation = FloatArray(9)
   private val orientation = FloatArray(3)
   override fun onSensorChanged(event: SensorEvent) {
    SensorManager.getRotationMatrixFromVector(rotation, event.values)
    SensorManager.getOrientation(rotation, orientation)
    tiltX = (orientation[2] / .65f).coerceIn(-1f, 1f)
    tiltY = (orientation[1] / .65f).coerceIn(-1f, 1f)
   }
   override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
  }
  if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
  onDispose { manager.unregisterListener(listener) }
 }
 Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Night,Night2)))) {
  Canvas(Modifier.fillMaxSize()) {
   drawCircle(Brush.radialGradient(listOf(Color(0x33CB7B48),Color.Transparent), Offset(size.width*.85f,size.height*.2f), size.width*.75f), size.width*.75f, Offset(size.width*.85f,size.height*.2f))
   fun dune(y:Float, amp:Float, color:Color, parallax:Float) {
    val shift = tiltX * parallax
    val p=Path().apply {
     moveTo(-size.width*.25f+shift,size.height*y)
     cubicTo(size.width*.18f+shift,size.height*(y-amp),size.width*.45f+shift,size.height*(y+amp),size.width*.75f+shift,size.height*(y-amp*.25f))
     cubicTo(size.width*1.1f+shift,size.height*(y-amp),size.width*1.18f+shift,size.height*(y+amp),size.width*1.25f+shift,size.height*(y+amp))
     lineTo(size.width*1.25f,size.height*1.2f); lineTo(-size.width*.25f,size.height*1.2f); close()
    }
    drawPath(p,color)
   }
   // Subtle heat-haze ribbons drift over the horizon without expensive shader effects.
   repeat(5) { i ->
    val y=size.height*(.48f+i*.018f) + (hazeShift-.5f)*8f
    val path=Path().apply { moveTo(size.width*.06f,y); cubicTo(size.width*.28f,y-3f,size.width*.42f,y+4f,size.width*.63f,y); cubicTo(size.width*.78f,y-3f,size.width*.88f,y+2f,size.width*.98f,y-1f) }
    drawPath(path,Color(0xFFFFD7A2).copy(alpha=.035f),style=Stroke(width=1.1f))
   }
   dune(.69f,.08f,Color(0xFFA46C50).copy(alpha=.7f),8.dp.toPx())
   dune(.78f,.055f,Color(0xFFC18156),18.dp.toPx())
   dune(.88f,.045f,Sand,32.dp.toPx())
  }
 }
}

@Composable private fun AppCard(app:TrackedAppEntity,onClick:()->Unit) {
 Column(Modifier.fillMaxWidth().shadow(6.dp,RoundedCornerShape(19.dp)).clip(RoundedCornerShape(19.dp)).background(Color(0xFF1A202B).copy(alpha=.96f)).clickable(onClick=onClick).padding(12.dp)) {
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top) { AppGlyph(app,66.dp); Text(if(app.state=="BURIED") "ARCHIVE" else "${app.lastUsedDays}d",color=Sand,fontFamily=FontFamily.Monospace,fontSize=10.sp) }
  Spacer(Modifier.height(10.dp)); Text(app.label,color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
  Text(when(app.state){"RESCUED"->"Recently rescued";"BURIED"->"Buried · tap to restore";else->"Gathering dust"},color=Muted,fontSize=11.sp,maxLines=1)
  Spacer(Modifier.height(10.dp)); LinearProgressIndicator(progress={app.dustLevel.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),color=Sand,trackColor=Color.White.copy(alpha=.08f))
 }
}
@Composable private fun AppGlyph(app:TrackedAppEntity,glyphSize:Dp) {
 val context=LocalContext.current
 val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue=null, app.packageName) { value=withContext(Dispatchers.IO) { loadPackageIcon(context,app.packageName) } }
 val hues=listOf(Color(0xFF9D5C42),Color(0xFF725B91),Color(0xFF3D7A78),Color(0xFFB17B43))
 val hue=hues[app.packageName.hashCode().and(3)]
 val iconBitmap=icon
 Box(Modifier.size(glyphSize).shadow(9.dp,RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(hue,Night)))) {
  if(iconBitmap!=null) androidx.compose.foundation.Image(bitmap=iconBitmap,contentDescription=app.label,modifier=Modifier.fillMaxSize().padding(8.dp))
  else Text(app.label.firstOrNull()?.uppercase() ?: "?",color=Color.White,fontSize=(glyphSize.value*.43f).sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.Center))
  if(app.dustLevel>.55f) Canvas(Modifier.fillMaxSize()) { val p=Path().apply { moveTo(0f,size.height*.78f); quadraticTo(size.width*.45f,size.height*.62f,size.width,size.height*.78f); lineTo(size.width,size.height); lineTo(0f,size.height); close() }; drawPath(p,Color(0xBBA46C50)) }
 }
}
private fun loadPackageIcon(context:Context,packageName:String): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
 if(packageName.startsWith("demo.")) return@runCatching null
 val drawable=context.packageManager.getApplicationIcon(packageName)
 val w=drawable.intrinsicWidth.takeIf{it>0}?:96; val h=drawable.intrinsicHeight.takeIf{it>0}?:96
 val bitmap=Bitmap.createBitmap(w.coerceAtMost(256),h.coerceAtMost(256),Bitmap.Config.ARGB_8888)
 val canvas=NativeCanvas(bitmap); drawable.setBounds(0,0,canvas.width,canvas.height); drawable.draw(canvas)
 bitmap.asImageBitmap()
 }.getOrNull()

@Composable private fun WipeDialog(app:TrackedAppEntity,onDismiss:()->Unit,onRescue:()->Unit,onBury:()->Unit,onProgress:(Float)->Unit,hapticsEnabled:Boolean) {
 var progress by remember(app.packageName) { mutableFloatStateOf(app.wipeProgress.coerceIn(0f,1f)) }
 val context=LocalContext.current
 val vibrator=remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator }
 val mask=remember(app.packageName) { Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888).apply {
  val c=NativeCanvas(this); val p=NativePaint(3).apply { shader=android.graphics.LinearGradient(0f,0f,480f,480f,intArrayOf(0xFFE7C08C.toInt(),0xFFC18156.toInt(),0xFFA46C50.toInt()),null,android.graphics.Shader.TileMode.CLAMP) }
  c.drawRect(0f,0f,480f,480f,p); p.shader=null; p.color=0x55F5D7A4
  repeat(600) { c.drawCircle(Random.nextFloat()*480,Random.nextFloat()*480,Random.nextFloat()*1.4f,p) }
 } }
 DisposableEffect(mask) { onDispose { mask.recycle() } }
 AlertDialog(onDismissRequest=onDismiss,containerColor=Color(0xFF171B24),
  title={ Column { Text("RESTORE THE RELIC",color=Gold,fontFamily=FontFamily.Monospace,fontSize=10.sp,letterSpacing=2.sp); Text(app.label,color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold) } },
  text={ Column(horizontalAlignment=Alignment.CenterHorizontally) {
   Text("Swipe away the years of sand.",color=Muted,fontSize=13.sp); Spacer(Modifier.height(14.dp))
   WipeCanvas(app,mask,progress,Modifier.fillMaxWidth().aspectRatio(1f)) { delta ->
    progress=(progress+delta).coerceAtMost(1f); onProgress(progress)
    DuneAudio.brush()
    if (progress >= .72f && progress - delta < .72f) DuneAudio.polished()
    if(hapticsEnabled) runCatching { vibrator?.vibrate(android.os.VibrationEffect.createOneShot(10,30)) }
   }
   Spacer(Modifier.height(13.dp)); LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),color=Sand,trackColor=Color.White.copy(alpha=.08f))
   Text("${(progress*100).toInt()}% POLISHED",color=Gold,fontFamily=FontFamily.Monospace,fontSize=10.sp)
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
    TextButton(onClick={progress=(progress+.18f).coerceAtMost(1f);onProgress(progress)}) { Text("✦ POLISH BOOST",color=Muted,fontSize=10.sp) }
    TextButton(onClick=onBury) { Text(if(app.state=="BURIED") "ALREADY BURIED" else "BURY APP",color=Color(0xFFD28C7C),fontSize=10.sp) }
   }
  } },
  confirmButton={ Button(onClick=onRescue,enabled=app.state=="BURIED" || progress>=.72f,colors=ButtonDefaults.buttonColors(containerColor=Sand,contentColor=Night)) { Text(if(app.state=="BURIED") "UNBURY APP" else "✦ Rescue ${app.label.take(14)}",maxLines=1) } },
  dismissButton={ TextButton(onClick=onDismiss) { Text("Later",color=Muted) } }
 )
}


@Composable private fun WipeCanvas(app:TrackedAppEntity,mask:Bitmap,progress:Float,modifier:Modifier,onWipe:(Float)->Unit) {
 var last by remember { mutableStateOf<Offset?>(null) }
 val particles = remember { mutableStateListOf<SandParticle>() }
 var frameTick by remember { mutableIntStateOf(0) }
 val latestOnWipe by rememberUpdatedState(onWipe)
 val hue=listOf(Color(0xFF5B8E91),Color(0xFF8171A8),Color(0xFFD17B4D),Color(0xFF5D9A70))[app.packageName.hashCode().and(3)]

 LaunchedEffect(Unit) {
  var previous = 0L
  while (true) {
   val now = withFrameNanos { it }
   val dt = if (previous == 0L) 0.016f else ((now - previous) / 1_000_000_000f).coerceIn(0f, .04f)
   previous = now
   for (p in particles) {
    p.vy += 520f * dt
    p.x += p.vx * dt
    p.y += p.vy * dt
    p.life -= dt * 1.7f
   }
   particles.removeAll { it.life <= 0f }
   frameTick++
  }
 }

 Box(modifier.clip(RoundedCornerShape(26.dp)).background(Night2)) {
  Canvas(Modifier.fillMaxSize().pointerInput(mask) {
   detectDragGestures(onDragStart={last=it},onDragEnd={last=null},onDragCancel={last=null},onDrag={change,delta->
    change.consume()
    val here=change.position
    val prev=last ?: (here-delta)
    val c=NativeCanvas(mask)
    val p=NativePaint(3).apply {
     color=android.graphics.Color.TRANSPARENT
     style=NativePaint.Style.STROKE; strokeWidth=76f
     strokeCap=NativePaint.Cap.ROUND; strokeJoin=NativePaint.Join.ROUND
     xfermode=PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    val sx=mask.width.toFloat()/size.width; val sy=mask.height.toFloat()/size.height
    c.drawLine(prev.x*sx,prev.y*sy,here.x*sx,here.y*sy,p)
    p.xfermode=null
    last=here
    repeat(Random.nextInt(3, 8)) {
     if (particles.size >= 200) particles.removeAt(0)
     particles += SandParticle(
      x=here.x, y=here.y,
      vx=Random.nextFloat()*150f-75f + delta.x*.35f,
      vy=Random.nextFloat()*-170f-20f + delta.y*.15f,
      life=Random.nextFloat()*.55f+.25f,
      radius=Random.nextFloat()*3.5f+1.2f,
      rotation=Random.nextFloat()*360f
     )
    }
    latestOnWipe(.012f+delta.getDistance()/size.width*.055f)
   })
  }) {
   drawRect(Brush.radialGradient(listOf(hue.copy(alpha=.5f),Night2),center=center,radius=size.width*.8f))
   drawRoundRect(Brush.linearGradient(listOf(hue.copy(alpha=.5f),Night)),topLeft=Offset(size.width*.17f,size.height*.17f),size=androidx.compose.ui.geometry.Size(size.width*.66f,size.height*.66f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()))
   drawContext.canvas.nativeCanvas.drawBitmap(mask,null,android.graphics.RectF(0f,0f,size.width,size.height),android.graphics.Paint(3).apply { alpha=((1f-progress)*255).toInt().coerceIn(0,255) })
   drawCircle(Color.White.copy(alpha=(progress-.75f).coerceAtLeast(0f)*.6f),radius=size.width*.25f,center=Offset(size.width*.5f,size.height*.35f),style=Stroke(width=2.dp.toPx()))
   val tick = frameTick // read so particle motion redraws every frame
   particles.forEach { particle ->
    val alpha = particle.life.coerceIn(0f,1f)
    rotate(particle.rotation, pivot=Offset(particle.x, particle.y)) {
     drawCircle(Sand.copy(alpha=alpha), radius=particle.radius, center=Offset(particle.x,particle.y))
     drawCircle(Color(0xFFFFD8A5).copy(alpha=alpha*.55f), radius=particle.radius*.45f, center=Offset(particle.x-particle.radius*.25f,particle.y-particle.radius*.25f))
    }
   }
  }
  Text(app.label.firstOrNull()?.uppercase() ?: "?",color=Color.White,fontSize=100.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.Center))
 }
}
private fun demoApps()=listOf(
 TrackedAppEntity("demo.camera","Camera",97,dustLevel=.88f),
 TrackedAppEntity("demo.music","Music",63,dustLevel=.66f),
 TrackedAppEntity("demo.notes","Notes",34,dustLevel=.36f),
 TrackedAppEntity("demo.reader","Reader",112,dustLevel=.96f)
)
