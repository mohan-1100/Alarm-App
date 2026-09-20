package com.mohan.alarm

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mohan.alarm.ui.theme.AlarmTheme
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

// Data structure to hold all the settings
data class AlarmItem(
    val id: Int,
    var hour: Int,
    var minute: Int,
    var isEnabled: Boolean,
    var ringtoneUri: String? = null,
    var repeatDays: Set<Int> = emptySet(),
    var vibrate: Boolean = true,
    var label: String = "morning",
    var targetObject: String = "Cup"
) {
    fun getFormattedTime(): String {
        val amPm = if (hour < 12) "am" else "pm"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%02d:%02d %s", displayHour, minute, amPm)
    }

    fun getRepeatText(): String {
        return when {
            repeatDays.isEmpty() -> "Once"
            repeatDays.size == 7 -> "Daily"
            else -> {
                val daysMap = mapOf(2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat", 1 to "Sun")
                repeatDays.sorted().joinToString(", ") { daysMap[it] ?: "" }
            }
        }
    }
}

// ==========================================
// Persistent Storage Helper Functions
// ==========================================
fun saveAlarmsToPrefs(context: Context, alarms: List<AlarmItem>) {
    val prefs = context.getSharedPreferences("AlarmPrefs", Context.MODE_PRIVATE)
    val jsonArray = JSONArray()
    for (alarm in alarms) {
        val jsonObj = JSONObject().apply {
            put("id", alarm.id)
            put("hour", alarm.hour)
            put("minute", alarm.minute)
            put("isEnabled", alarm.isEnabled)
            put("ringtoneUri", alarm.ringtoneUri)
            put("repeatDays", JSONArray(alarm.repeatDays))
            put("vibrate", alarm.vibrate)
            put("label", alarm.label)
            put("targetObject", alarm.targetObject)
        }
        jsonArray.put(jsonObj)
    }
    prefs.edit().putString("alarms_data", jsonArray.toString()).apply()
}

fun loadAlarmsFromPrefs(context: Context): List<AlarmItem> {
    val prefs = context.getSharedPreferences("AlarmPrefs", Context.MODE_PRIVATE)
    val jsonString = prefs.getString("alarms_data", null)
    val list = mutableListOf<AlarmItem>()

    if (jsonString != null) {
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)

                val repeatDaysArray = jsonObj.optJSONArray("repeatDays")
                val repeatDays = mutableSetOf<Int>()
                if (repeatDaysArray != null) {
                    for (j in 0 until repeatDaysArray.length()) {
                        repeatDays.add(repeatDaysArray.getInt(j))
                    }
                }

                list.add(
                    AlarmItem(
                        id = jsonObj.getInt("id"),
                        hour = jsonObj.getInt("hour"),
                        minute = jsonObj.getInt("minute"),
                        isEnabled = jsonObj.getBoolean("isEnabled"),
                        ringtoneUri = if (jsonObj.isNull("ringtoneUri")) null else jsonObj.getString("ringtoneUri"),
                        repeatDays = repeatDays,
                        vibrate = jsonObj.getBoolean("vibrate"),
                        label = jsonObj.getString("label"),
                        targetObject = jsonObj.getString("targetObject")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return list
}
// ==========================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlarmTheme {
                // Integrated Permission Handler
                StartupPermissionHandler()
                MainScreenManager()
            }
        }
    }
}

/**
 * Reusable Composable that handles Camera and Notification permissions sequentially at app startup.
 * Now updated to also handle Android 14+ Full Screen Intent permission.
 */
@Composable
fun StartupPermissionHandler() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    var showRationaleDialog by remember { mutableStateOf(false) }
    var showFullScreenRationale by remember { mutableStateOf(false) }
    var isPermanentDenial by remember { mutableStateOf(false) }

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.CAMERA)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val deniedPermissions = results.filter { !it.value }.keys
        if (deniedPermissions.isNotEmpty()) {
            val hasPermanentDenial = deniedPermissions.any { permission ->
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            }
            isPermanentDenial = hasPermanentDenial
            showRationaleDialog = true
        } else {
            // Check for Full Screen Intent permission on Android 14+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (!nm.canUseFullScreenIntent()) {
                    showFullScreenRationale = true
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val permissionsNotGranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsNotGranted.isNotEmpty()) {
            launcher.launch(permissionsToRequest)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Even if runtime permissions are granted, check Full Screen Intent for Android 14+
            val nm = context.getSystemService(NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                showFullScreenRationale = true
            }
        }
    }

    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showRationaleDialog = false },
            title = { Text("Permissions Required") },
            text = {
                Text("Notifications are required to ring the alarm, and the Camera is required to scan objects to turn it off.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRationaleDialog = false
                        if (isPermanentDenial) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } else {
                            launcher.launch(permissionsToRequest)
                        }
                    }
                ) {
                    Text(if (isPermanentDenial) "Open Settings" else "Grant Permissions")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationaleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFullScreenRationale) {
        AlertDialog(
            onDismissRequest = { showFullScreenRationale = false },
            title = { Text("Full Screen Access Needed") },
            text = {
                Text("This permission is needed to wake the screen and show the alarm interface when the alarm goes off on Android 14+.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFullScreenRationale = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFullScreenRationale = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MainScreenManager() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf("Dashboard") }
    var alarmToEdit by remember { mutableStateOf<AlarmItem?>(null) }
    val alarmScheduler = remember { AlarmScheduler(context) }

    val alarmList = remember {
        val savedAlarms = loadAlarmsFromPrefs(context)
        val initialList = if (savedAlarms.isEmpty()) {
            emptyList<AlarmItem>()
        } else {
            savedAlarms
        }
        mutableStateListOf(*initialList.toTypedArray())
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (currentScreen == "Dashboard") {
            AlarmDashboardScreen(
                alarmList = alarmList,
                onAddClick = {
                    alarmToEdit = null
                    currentScreen = "EditAlarm"
                },
                onEditClick = { alarm ->
                    alarmToEdit = alarm
                    currentScreen = "EditAlarm"
                }
            )
        } else if (currentScreen == "EditAlarm") {
            EditAlarmScreen(
                initialAlarm = alarmToEdit,
                onSave = { updatedAlarm ->
                    if (alarmToEdit == null) {
                        val newId = (alarmList.maxOfOrNull { it.id } ?: 0) + 1
                        val newAlarm = updatedAlarm.copy(id = newId)
                        alarmList.add(newAlarm)
                        alarmScheduler.schedule(newAlarm.id, newAlarm.hour, newAlarm.minute, newAlarm.label, newAlarm.targetObject, newAlarm.ringtoneUri, newAlarm.vibrate)
                    } else {
                        val index = alarmList.indexOfFirst { it.id == updatedAlarm.id }
                        if (index != -1) {
                            alarmList[index] = updatedAlarm
                        }
                        alarmScheduler.schedule(updatedAlarm.id, updatedAlarm.hour, updatedAlarm.minute, updatedAlarm.label, updatedAlarm.targetObject, updatedAlarm.ringtoneUri, updatedAlarm.vibrate)
                    }

                    saveAlarmsToPrefs(context, alarmList)
                    Toast.makeText(context, "Alarm Saved", Toast.LENGTH_SHORT).show()
                    currentScreen = "Dashboard"
                },
                onDelete = { alarmToDelete ->
                    alarmScheduler.cancel(alarmToDelete.id)
                    alarmList.removeIf { it.id == alarmToDelete.id }
                    saveAlarmsToPrefs(context, alarmList)
                    Toast.makeText(context, "Alarm Deleted", Toast.LENGTH_SHORT).show()
                    currentScreen = "Dashboard"
                },
                onCancel = { currentScreen = "Dashboard" }
            )
        }
    }
}

@Composable
fun AlarmDashboardScreen(
    alarmList: List<AlarmItem>,
    onAddClick: () -> Unit,
    onEditClick: (AlarmItem) -> Unit
) {
    val context = LocalContext.current
    val alarmScheduler = remember { AlarmScheduler(context) }
    var updateTrigger by remember { mutableIntStateOf(0) }
    var nextAlarmText by remember { mutableStateOf("Calculating...") }

    LaunchedEffect(updateTrigger, alarmList.toList()) {
        while(true) {
            val activeAlarms = alarmList.filter { it.isEnabled }
            if (activeAlarms.isEmpty()) {
                nextAlarmText = "All alarms are off"
            } else {
                val now = Calendar.getInstance()
                var minDiffInMillis = Long.MAX_VALUE

                for (alarm in activeAlarms) {
                    val alarmCalendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, alarm.hour)
                        set(Calendar.MINUTE, alarm.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (alarmCalendar.before(now)) {
                        alarmCalendar.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    val diff = alarmCalendar.timeInMillis - now.timeInMillis
                    if (diff < minDiffInMillis) minDiffInMillis = diff
                }

                val totalMinutes = minDiffInMillis / (1000 * 60)
                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60

                nextAlarmText = when {
                    hours > 0 && minutes > 0 -> "Alarm in $hours hours $minutes minutes"
                    hours > 0 -> "Alarm in $hours hours"
                    minutes > 0 -> "Alarm in $minutes minutes"
                    else -> "Alarm in less than a minute"
                }
            }
            delay(10000)
        }
    }

    Scaffold(
        containerColor = Color.Black,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = Color(0xFF202B36),
                contentColor = Color(0xFF8AB4F8),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Alarm")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(text = "Alarm", fontSize = 32.sp, color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = nextAlarmText, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White)
            Spacer(modifier = Modifier.height(32.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(alarmList) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        onToggleChange = { isChecked ->
                            alarm.isEnabled = isChecked
                            updateTrigger++

                            saveAlarmsToPrefs(context, alarmList)

                            if (isChecked) {
                                alarmScheduler.schedule(alarm.id, alarm.hour, alarm.minute, alarm.label, alarm.targetObject, alarm.ringtoneUri, alarm.vibrate)
                            } else {
                                alarmScheduler.cancel(alarm.id)
                            }
                        },
                        onCardClick = { onEditClick(alarm) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAlarmScreen(
    initialAlarm: AlarmItem?,
    onSave: (AlarmItem) -> Unit,
    onDelete: (AlarmItem) -> Unit,
    onCancel: () -> Unit
) {
    val calendar = Calendar.getInstance()

    var hour by remember { mutableIntStateOf(initialAlarm?.hour ?: calendar.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(initialAlarm?.minute ?: calendar.get(Calendar.MINUTE)) }
    var ringtoneUri by remember { mutableStateOf(initialAlarm?.ringtoneUri) }
    var repeatDays by remember { mutableStateOf(initialAlarm?.repeatDays ?: emptySet()) }
    var vibrate by remember { mutableStateOf(initialAlarm?.vibrate ?: true) }
    var label by remember { mutableStateOf(initialAlarm?.label ?: "") }
    var targetObject by remember { mutableStateOf(initialAlarm?.targetObject ?: "Cup") }

    val timePickerState = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = false
    )

    var showLabelDialog by remember { mutableStateOf(false) }
    var showRepeatDialog by remember { mutableStateOf(false) }
    var showObjectDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            ringtoneUri = uri.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(top = 48.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Discard", tint = Color.White)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (initialAlarm == null) "Add alarm" else "Edit alarm",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Configure your time",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            IconButton(onClick = {
                val finalAlarm = AlarmItem(
                    id = initialAlarm?.id ?: 0,
                    hour = timePickerState.hour,
                    minute = timePickerState.minute,
                    isEnabled = true,
                    ringtoneUri = ringtoneUri,
                    repeatDays = repeatDays,
                    vibrate = vibrate,
                    label = label,
                    targetObject = targetObject
                )
                onSave(finalAlarm)
            }) {
                Icon(Icons.Default.Check, contentDescription = "Save", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimePicker(
                state = timePickerState,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Color(0xFF1E1E1E),
                    selectorColor = Color(0xFF6BA5FF),
                    timeSelectorSelectedContainerColor = Color(0xFF6BA5FF),
                    timeSelectorSelectedContentColor = Color.Black,
                    timeSelectorUnselectedContainerColor = Color(0xFF1E1E1E),
                    timeSelectorUnselectedContentColor = Color.White
                )
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                SettingsRow(
                    title = "Ringtone",
                    value = if (ringtoneUri == null) "Default" else "Custom Audio",
                    onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) }
                )
                Divider(color = Color(0xFF3A3A3A), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                val repeatText = AlarmItem(0,0,0,true, repeatDays = repeatDays).getRepeatText()
                SettingsRow(
                    title = "Repeat",
                    value = repeatText,
                    onClick = { showRepeatDialog = true }
                )
                Divider(color = Color(0xFF3A3A3A), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Vibrate when alarm sounds", color = Color.White, fontSize = 16.sp)
                    Switch(
                        checked = vibrate,
                        onCheckedChange = { vibrate = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF6BA5FF)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showLabelDialog = true }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Label", color = Color.White, fontSize = 16.sp)
                    Text(text = label.ifEmpty { "None" }, color = Color.White, fontSize = 16.sp)
                }

                Divider(color = Color(0xFF3A3A3A), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                SettingsRow(
                    title = "Object to Scan",
                    value = targetObject,
                    onClick = { showObjectDialog = true }
                )
            }
        }

        if (initialAlarm != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onDelete(initialAlarm) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF5252)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delete alarm",
                    color = Color(0xFFFF5252),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showObjectDialog) {
        val objectOptions = listOf("Bottle", "Cup")
        AlertDialog(
            onDismissRequest = { showObjectDialog = false },
            title = { Text("Select Object to Scan") },
            text = {
                LazyColumn {
                    items(objectOptions) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    targetObject = option
                                    showObjectDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(text = option, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showLabelDialog) {
        var tempLabel by remember { mutableStateOf(label) }
        AlertDialog(
            onDismissRequest = { showLabelDialog = false },
            title = { Text("Alarm Label") },
            text = {
                OutlinedTextField(
                    value = tempLabel,
                    onValueChange = { tempLabel = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    label = tempLabel
                    showLabelDialog = false
                }) { Text("OK", color = Color(0xFF8AB4F8)) }
            },
            dismissButton = {
                TextButton(onClick = { showLabelDialog = false }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    if (showRepeatDialog) {
        var tempDays by remember { mutableStateOf(repeatDays) }
        val daysOfWeek = listOf(2 to "Monday", 3 to "Tuesday", 4 to "Wednesday", 5 to "Thursday", 6 to "Friday", 7 to "Saturday", 1 to "Sunday")

        AlertDialog(
            onDismissRequest = { showRepeatDialog = false },
            title = { Text("Repeat") },
            text = {
                LazyColumn {
                    items(daysOfWeek) { (calendarDay, name) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                tempDays = if (tempDays.contains(calendarDay)) {
                                    tempDays - calendarDay
                                } else {
                                    tempDays + calendarDay
                                }
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = tempDays.contains(calendarDay),
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF6BA5FF))
                            )
                            Text(text = name, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    repeatDays = tempDays
                    showRepeatDialog = false
                }) { Text("OK", color = Color(0xFF8AB4F8)) }
            }
        )
    }
}

@Composable
fun SettingsRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = Color.White, fontSize = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, color = Color.Gray, fontSize = 14.sp)
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.padding(start = 4.dp).size(20.dp)
            )
        }
    }
}

@Composable
fun AlarmCard(alarm: AlarmItem, onToggleChange: (Boolean) -> Unit, onCardClick: () -> Unit) {
    var isChecked by remember { mutableStateOf(alarm.isEnabled) }
    val contentAlphaColor = if (isChecked) Color.White else Color.Gray

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = Modifier.fillMaxWidth().clickable { onCardClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = alarm.getFormattedTime().substring(0, 5),
                        fontSize = 32.sp,
                        color = contentAlphaColor
                    )
                    Text(
                        text = alarm.getFormattedTime().substring(5),
                        fontSize = 16.sp,
                        color = contentAlphaColor,
                        modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                val description = "${alarm.getRepeatText()} | ${alarm.label}"
                Text(text = description, fontSize = 14.sp, color = Color.Gray)
            }

            Switch(
                checked = isChecked,
                onCheckedChange = {
                    isChecked = it
                    onToggleChange(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF6BA5FF),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF333333)
                )
            )
        }
    }
}
