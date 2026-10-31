@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package dev.vela.v12

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val model: VelaViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            val ui by model.ui.collectAsStateWithLifecycle()
            val preference by model.dark.collectAsStateWithLifecycle()
            val dark=systemDark(preference)
            var pendingDevice by remember { mutableStateOf<WheelDevice?>(null) }
            val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                if(result.values.all { it } && model.ble.hasPermissions()) {
                    pendingDevice?.let(model::connect) ?: model.scan()
                } else model.ble.permissionDenied()
                pendingDevice=null
            }
            fun permitted(device: WheelDevice?) {
                if(model.ble.hasPermissions()) { if(device==null) model.scan() else model.connect(device) }
                else { pendingDevice=device; permission.launch(model.ble.permissions()) }
            }
            SideEffect { WindowCompat.getInsetsController(window,window.decorView).apply {
                isAppearanceLightStatusBars=!dark; isAppearanceLightNavigationBars=!dark
            } }
            VelaTheme(dark) {
                VelaApp(ui,dark,Actions(scan={permitted(null)},connect={permitted(it)},
                    disconnect=model::disconnect,stopScan=model.ble::stopScan,forget=model::forget,
                    readAll=model.ble::readAll,read=model.ble::read,write=model.ble::apply,
                    theme={model.setDark(!dark)},permissions={startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}))
            }
        }
    }
    override fun onStop() { super.onStop(); if(!isChangingConfigurations) model.ble.onBackground() }
}

internal data class Actions(
    val scan: () -> Unit = {}, val connect: (WheelDevice) -> Unit = {}, val disconnect: () -> Unit = {},
    val stopScan: () -> Unit = {}, val forget: () -> Unit = {}, val readAll: () -> Unit = {},
    val read: (V12Protocol.Read) -> Unit = {}, val write: (V12Protocol.Write) -> Unit = {},
    val theme: () -> Unit = {}, val permissions: () -> Unit = {}
)

@Composable internal fun VelaApp(ui: VelaUi, dark: Boolean, actions: Actions, initialTab: Int = 0) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var selected by remember { mutableStateOf<WheelDevice?>(null) }
    var command by remember { mutableStateOf<V12Protocol.Write?>(null) }
    var forget by remember { mutableStateOf(false) }
    fun primary() { if(ui.saved!=null) actions.connect(ui.saved) else { tab=3; actions.scan() } }
    Scaffold(containerColor=MaterialTheme.colorScheme.background,contentWindowInsets=WindowInsets(0,0,0,0),topBar={
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start=20.dp,end=12.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(IonShapes.m)).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome,null,Modifier.size(23.dp),tint=MaterialTheme.colorScheme.primary)
            }
            Text("Ion",fontSize=26.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(start=10.dp).weight(1f))
            IconButton(onClick={tab=3}) { Icon(if(ui.connected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth,"Подключение колеса",tint=MaterialTheme.colorScheme.primary) }
            IconButton(onClick=actions.theme) { Icon(if(dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,"Сменить тему") }
        }
    },bottomBar={
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=16.dp,vertical=8.dp)) { Dock(tab) { tab=it } }
    }) { inset ->
        AnimatedContent(targetState=tab,modifier=Modifier.fillMaxSize().padding(inset),transitionSpec={
            (fadeIn(tween(180,30))+slideInHorizontally(tween(240)) { if(targetState>initialState) it/18 else -it/18 }) togetherWith fadeOut(tween(120))
        },label="navigation") { route ->
            when(route) {
                0 -> RideScreen(ui,if(ui.connected) ({tab=3}) else (::primary))
                1 -> DataScreen(ui,actions)
                2 -> ControlScreen(ui,actions) { command=it }
                else -> WheelScreen(ui,actions,{selected=it},{forget=true})
            }
        }
    }
    selected?.let { d -> AlertDialog(onDismissRequest={selected=null},icon={Icon(Icons.Rounded.Bluetooth,null)},title={Text("Подключить это колесо?")},
        text={Text("${d.name}\nПосле проверки V12 HT приложение запомнит колесо. Выбирай только своё устройство.")},
        confirmButton={TextButton(onClick={selected=null;actions.connect(d)}) { Text("Подключить") }},dismissButton={TextButton(onClick={selected=null}) { Text("Отмена") }}) }
    command?.let { c -> AlertDialog(onDismissRequest={command=null},icon={Icon(Icons.Rounded.Tune,null)},title={Text("Применить изменение?")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) { Text(c.label,fontWeight=FontWeight.SemiBold)
            if(c.kind in listOf(V12Protocol.Control.VOLUME,V12Protocol.Control.MUTE)) Text("Изменение звука может затронуть предупреждения колеса. Не отключай их перед поездкой.",color=MaterialTheme.colorScheme.error)
            if(c.kind == V12Protocol.Control.LOCK) Text("Заблокированное колесо может не поехать до разблокировки.",color=MaterialTheme.colorScheme.error)
            if(c.kind.tier == V12Protocol.Tier.STATIONARY) Text("Убедись, что колесо неподвижно.")
            Text("После отправки Ion перечитает значение${if(c.readBack==null) " — для этой команды подтверждение колесом недоступно" else ""}. Неподтверждённая команда не будет повторена автоматически.") }},
        confirmButton={TextButton(onClick={command=null;actions.write(c)},enabled=ui.canWrite(c)) { Text("Применить") }},dismissButton={TextButton(onClick={command=null}) { Text("Отмена") }}) }
    if(forget) AlertDialog(onDismissRequest={forget=false},title={Text("Забыть колесо?")},text={Text("Соединение будет закрыто. Для следующего подключения нужно будет выбрать устройство заново.")},
        confirmButton={TextButton(onClick={forget=false;actions.forget()}) {Text("Забыть")}},dismissButton={TextButton(onClick={forget=false}) {Text("Отмена")}})
}

/** Floating pill dock (Lumen-style): bordered raised bar, selected tab fills with a morphing pill. */
@Composable private fun Dock(tab: Int, select: (Int)->Unit) {
    Surface(shape=RoundedCornerShape(IonShapes.dock),color=MaterialTheme.colorScheme.surfaceContainer,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.6f)),tonalElevation=0.dp) {
        Row(Modifier.fillMaxWidth().padding(6.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf("Езда" to Icons.Rounded.Explore,"Данные" to Icons.Rounded.DataObject,"Пульт" to Icons.Rounded.Tune,"Связь" to Icons.Rounded.Bluetooth)
                .forEachIndexed { i,(label,icon) ->
                    val selected=tab==i
                    val bg by animateColorAsState(if(selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        spring(stiffness=400f),label="dockBg")
                    val iconScale by animateFloatAsState(if(selected) 1.12f else 1f,spring(stiffness=600f,dampingRatio=0.85f),label="dockIcon")
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(bg).squashClick { select(i) }
                        .padding(vertical=9.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Icon(icon,null,Modifier.size(22.dp).graphicsLayer { scaleX=iconScale;scaleY=iconScale },
                            tint=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(label,fontSize=12.sp,maxLines=1,softWrap=false,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Medium,
                            color=if(selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
        }
    }
}

private val PagePadding=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=24.dp)

@Composable private fun RideScreen(ui: VelaUi, connect: () -> Unit) {
    val t=ui.wheel.telemetry; val ion=ionColors()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth().padding(bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("ТВОЁ КОЛЕСО",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,letterSpacing=1.sp)
                Text("V12 HT",fontSize=30.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-0.5).sp) }
            Status(ui)
        } }
        if(ui.wheel.phase==Phase.ERROR) item { Banner(ui.wheel.message,error=true) }
        // Hysteresis: a single poll hiccup stays silent; only sustained staleness shows a soft warning.
        if(ui.staleConfirmed) item { Banner("Показания не обновляются · BLE остаётся подключён",Icons.Rounded.Update,warning=true) }
        item { Surface(color=ion.hero,contentColor=ion.onHero,shape=RoundedCornerShape(topStart=36.dp,topEnd=36.dp,bottomEnd=36.dp,bottomStart=14.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Text("Скорость",fontSize=16.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f)); Icon(Icons.Rounded.NearMe,null,Modifier.size(25.dp)) }
                Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.Bottom) {
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val value=t?.speed?.fmt(1) ?: "—"; val size=minOf(88f,maxWidth.value/(value.length*0.60f*LocalDensity.current.fontScale)).coerceAtLeast(32f)
                        Text(value,fontSize=size.sp,lineHeight=(size*1.12f).sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-3).sp,maxLines=1)
                    }
                    Text("км/ч",fontSize=18.sp,modifier=Modifier.padding(bottom=14.dp))
                }
                HorizontalDivider(color=ion.onHero.copy(alpha=.15f))
                Row(Modifier.fillMaxWidth().padding(top=14.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(if(ui.fresh) Icons.Rounded.Sensors else Icons.Rounded.Bluetooth,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if(ui.fresh) "Живые данные · Bluetooth LE" else if(ui.busy) ui.wheel.message else if(ui.connected) "Ожидает новых данных · Bluetooth LE" else "Ожидает подключения",fontSize=14.sp,modifier=Modifier.weight(1f))
                }
            }
        } }
        item { Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(IonShapes.xl)) {
            Row(Modifier.fillMaxWidth().squashClick(onClick=connect,enabled=!ui.busy).padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(IonShapes.m)).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) {
                    Icon(if(ui.connected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth,null,tint=MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)) {
                    Text(if(ui.connected) "Соединение активно" else if(ui.busy) "Подключаюсь…" else if(ui.saved!=null) "Подключить своё колесо" else "Найти колесо",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                    Text(if(ui.connected) "${ui.wheel.packets} пакетов · V12 HT" else ui.saved?.name ?: "Выбрать свою V12 HT по Bluetooth",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp));Icon(Icons.Rounded.ChevronRight,null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
            }
        } }
        item { SectionHeader("Сейчас на колесе") }
        item { MetricPair(first={ m -> Metric("Батарея",t?.battery?.takeIf { it in 0.0..100.0 }?.roundToInt()?.toString() ?: "—","%",Icons.Rounded.BatteryFull,m,true) },
            second={m -> Metric("Напряжение",t?.voltage?.fmt(1) ?: "—","В",Icons.Rounded.ElectricBolt,m)}) }
        item { val odo=ui.wheel.records[V12Protocol.Read.STATISTICS]?.frame?.let(V12Protocol::statistics)?.takeIf { it.totalKm>0 }?.totalKm
            MetricPair(first={m -> Metric("Одометр",(odo ?: t?.takeIf { it.mileageKm>0 }?.mileageKm)?.fmt(1) ?: "—","км",Icons.Rounded.Route,m)},
            second={m -> Metric("Остаток пути",t?.takeIf { it.rangeKm>0 }?.rangeKm?.fmt(1) ?: "—","км",Icons.Rounded.Navigation,m)}) }
        item { MetricPair(first={m -> Metric("MOS",t?.mosTemp?.toString() ?: "—","°C",Icons.Rounded.Thermostat,m)},
            second={m -> Metric("Ток",t?.current?.fmt(1) ?: "—","А",Icons.Rounded.Waves,m)}) }
        item { Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(IonShapes.l)) {
            Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Rounded.Speed,null,tint=MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column {
                Text("Мотор  ${t?.motorTemp?.let { "$it °C" } ?: "—"}",fontSize=16.sp)
                Text("Остальные поля — во вкладке «Данные»",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        } }
    }
}

@Composable private fun DataScreen(ui: VelaUi,actions: Actions) {
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val reads=V12Protocol.Read.entries
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Heading("Под капотом","Все поддержанные чтения · расшифровка и raw") }
        item { BoxWithConstraints(Modifier.fillMaxWidth()) {
            if(maxWidth<320.dp || LocalDensity.current.fontScale>1.2f) {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {Status(ui); ReadAllButton(ui,actions,Modifier.fillMaxWidth())}
            } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                Status(ui); ReadAllButton(ui,actions,Modifier.weight(1f))
            }
        } }
        if(!ui.fresh) item { Banner(if(ui.wheel.records.isEmpty()) "Подключи колесо. Здесь появятся реальные ответы, без подставных значений." else if(ui.connected) "Показаны последние ответы. BLE подключён, ожидаю свежую телеметрию." else "Показаны последние ответы. Сейчас колесо отключено.",Icons.Rounded.SensorsOff) }
        item { OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Найти поле или пакет")},leadingIcon={Icon(Icons.Rounded.Search,null)},shape=RoundedCornerShape(IonShapes.l)) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Все поля","Телеметрия","Настройки","Raw / пакеты").forEachIndexed { i,label -> FilterChip(selected=mode==i,onClick={mode=i},label={Text(label,fontSize=14.sp)}) }
        } }
        val selectedReads=reads.filter { when(mode) {1 -> it==V12Protocol.Read.TELEMETRY;2 -> it==V12Protocol.Read.SETTINGS;else -> true} }
        items(selectedReads,key={it.name},contentType={it.command}) { read ->
            val record=ui.wheel.records[read]
            val fields=record?.frame?.let(V12Protocol::fields).orEmpty().filter { query.isBlank() || (it.key+it.label+it.value).contains(query,true) }
            if(query.isBlank() || fields.isNotEmpty() || read.label.contains(query,true)) {
                PacketCard(read,record,ui.now,fields,mode==3,ui.connected && !ui.wheel.workPending) {actions.read(read)}
            }
        }
        item { Text("Каталог: ${reads.size} запросов. Батарея, версии и некоторые служебные ответы могут отличаться по прошивке или не отвечать. Неизвестные байты остаются raw — без догадок об их смысле.",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun PacketCard(read: V12Protocol.Read,record: PacketRecord?,now: Long,fields: List<V12Protocol.Field>,rawMode: Boolean,enabled: Boolean,refresh: ()->Unit) {
    var expanded by rememberSaveable(read) { mutableStateOf(read in listOf(V12Protocol.Read.TELEMETRY,V12Protocol.Read.SETTINGS)) }
    var raw by rememberSaveable(read) { mutableStateOf(false) }
    Surface(shape=RoundedCornerShape(IonShapes.xl),color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            Row(Modifier.fillMaxWidth().clickable(enabled=!rawMode) {expanded=!expanded}.padding(start=18.dp,top=10.dp,bottom=10.dp,end=6.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(read.label,fontSize=18.sp,fontWeight=FontWeight.Medium)
                    val age=record?.at?.let { " · ${((now-it).coerceAtLeast(0)/1000)} с назад" }.orEmpty()
                    Text((record?.status ?: "Ещё не запрошено")+age,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick=refresh,enabled=enabled) { Icon(Icons.Rounded.Refresh,"Перечитать ${read.label}") }
                if(!rawMode) Icon(if(expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,"Развернуть пакет",Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
            }
            AnimatedVisibility(expanded || rawMode) {
                Column(Modifier.padding(horizontal=18.dp).padding(bottom=16.dp)) {
                    if(!rawMode) fields.forEach { field -> FieldRow(field) }
                    if(record?.frame==null) Text("Ответ ещё не получен",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        if(fields.isEmpty() && !rawMode) Text("Смысл полей ещё не подтверждён. Полный ответ доступен ниже.",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(!rawMode) TextButton(onClick={raw=!raw}) { Icon(Icons.Rounded.Code,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(if(raw || rawMode) "Свернуть raw" else "Показать все байты") }
                        if(raw || rawMode) Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh,shape=RoundedCornerShape(IonShapes.s)) {
                            SelectionContainer { Text(V12Protocol.raw(record.frame),fontFamily=FontFamily.Monospace,fontSize=14.sp,modifier=Modifier.fillMaxWidth().padding(12.dp).horizontalScroll(rememberScrollState())) }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun FieldRow(field: V12Protocol.Field) {
    Column(Modifier.fillMaxWidth().padding(vertical=12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(field.label,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
            SelectionContainer { Text(field.value,fontSize=16.sp,fontWeight=FontWeight.Medium,fontFamily=FontFamily.Monospace,modifier=Modifier.widthIn(max=155.dp),textAlign=TextAlign.End) }
        }
        if(field.note.isNotEmpty()) Text(field.note,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=5.dp))
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
}

@Composable private fun ControlScreen(ui: VelaUi,actions: Actions,request: (V12Protocol.Write)->Unit) {
    val t=ui.wheel.telemetry; val s=ui.wheel.settings
    val safe=ui.safeWrite
    val stopped=ui.stoppedWrite
    val settingsSafe=safe && ui.settingsFresh && s!=null
    val settingsStopped=stopped && ui.settingsFresh && s!=null
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Heading("Твой пульт","Свет и звук — в любой момент; режимы и педали — на стоянке") }
        item { Banner(when {
            ui.wheel.workPending -> "Запрос выполняется. Дождись результата."
            !ui.connected -> "Подключи V12 HT, чтобы читать и менять настройки."
            !safe -> "Колесо сообщает об обновлении прошивки — запись заблокирована."
            !stopped -> "Свет и звук — всегда при связи. Режимы, педали и служебное — только на остановленном колесе."
            else -> "Свет и звук — в любой момент, остальные — на стоянке. Каждое изменение с перечитыванием."
        }, if(safe) Icons.Rounded.VerifiedUser else Icons.Rounded.Lock, warning=!safe) }
        ui.wheel.writeMessage?.let { message -> item { Banner(message,Icons.Rounded.FactCheck,error=message.contains("НЕ подтверждено") || message.contains("заблокирована")) } }
        item { SectionHeader("Свет") }
        item { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            SwitchRow(Icons.Rounded.Lightbulb,"Ближний свет",t?.lowBeam,enabled=safe) { request(V12Protocol.Write(V12Protocol.Control.LIGHTS,if(it)1 else 0,if(t?.highBeam==true)1 else 0)) }
            SwitchRow(Icons.Rounded.Highlight,"Дальний свет",t?.highBeam,enabled=safe) { request(V12Protocol.Write(V12Protocol.Control.LIGHTS,if(t?.lowBeam==true)1 else 0,if(it)1 else 0)) }
            SwitchRow(Icons.Rounded.AutoAwesome,"Автосвет",s?.autoLight,enabled=settingsSafe) { request(V12Protocol.Write(V12Protocol.Control.AUTO_LIGHT,if(it)1 else 0)) }
            BlindSwitchRow(Icons.Rounded.WbSunny,"Дневной свет (DRL)","Колесо не сообщает состояние этой настройки",safe) { request(V12Protocol.Write(V12Protocol.Control.DRL,if(it)1 else 0)) }
            SliderCard {
                if(s!=null && s.lowBrightness in 0..100 && s.highBrightness in 0..100) {
                    var low by remember(s.lowBrightness,s.highBrightness) { mutableFloatStateOf(s.lowBrightness.toFloat()) }
                    var high by remember(s.lowBrightness,s.highBrightness) { mutableFloatStateOf(s.highBrightness.toFloat()) }
                    SettingSlider("Яркость ближнего",low,"${low.roundToInt()}%",0f..100f,settingsSafe) {low=it}
                    SettingSlider("Яркость дальнего",high,"${high.roundToInt()}%",0f..100f,settingsSafe) {high=it}
                    ApplyButton("Применить яркость",safe && (low.roundToInt()!=s.lowBrightness || high.roundToInt()!=s.highBrightness)) {request(V12Protocol.Write(V12Protocol.Control.BRIGHTNESS,low.roundToInt(),high.roundToInt()))}
                } else MissingSetting("Яркость появится после чтения настроек")
            }
        } }
        item { SectionHeader("Звук") }
        item { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            SwitchRow(Icons.Rounded.VolumeOff,"Без звука",s?.muted,enabled=settingsSafe) {request(V12Protocol.Write(V12Protocol.Control.MUTE,if(it)1 else 0))}
            SwitchRow(Icons.Rounded.MusicNote,"Звук движения",s?.soundWave,enabled=settingsSafe) {request(V12Protocol.Write(V12Protocol.Control.SOUND_WAVE,if(it)1 else 0))}
            SliderCard {
                if(s!=null && s.volume in 0..100) {
                    var volume by remember(s.volume) { mutableFloatStateOf(s.volume.toFloat()) }
                    SettingSlider("Громкость",volume,"${volume.roundToInt()}%",0f..100f,settingsSafe) {volume=it}
                    ApplyButton("Применить громкость",safe && volume.roundToInt()!=s.volume) {request(V12Protocol.Write(V12Protocol.Control.VOLUME,volume.roundToInt()))}
                } else MissingSetting("Громкость появится после чтения настроек")
            }
            Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(IonShapes.row),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("Проиграть звук",fontSize=14.sp,modifier=Modifier.weight(1f))
                    (1..4).forEach { n -> OutlinedButton(onClick={request(V12Protocol.Write(V12Protocol.Control.PLAY_SOUND,n))},enabled=safe,
                        modifier=Modifier.heightIn(min=44.dp),contentPadding=PaddingValues(horizontal=14.dp)) {Text("$n",fontSize=14.sp)} }
                }
            }
        } }
        item { SectionHeader("Режимы езды · на стоянке") }
        item { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            SwitchRow(Icons.Rounded.DirectionsBike,"Классический режим",s?.classicMode,enabled=settingsStopped) {request(V12Protocol.Write(V12Protocol.Control.CLASSIC,if(it)1 else 0))}
            SwitchRow(Icons.Rounded.RocketLaunch,"Fancier mode",s?.fancier,enabled=settingsStopped) {request(V12Protocol.Write(V12Protocol.Control.FANCIER,if(it)1 else 0))}
            SwitchRow(Icons.Rounded.CallSplit,"Split mode",s?.splitMode,enabled=settingsStopped) {request(V12Protocol.Write(V12Protocol.Control.SPLIT_MODE,if(it)1 else 0))}
            SliderCard {
                if(s!=null) {
                    var accel by remember(s.splitAccel) { mutableFloatStateOf(s.splitAccel.coerceIn(0,100).toFloat()) }
                    var brake by remember(s.splitBrake) { mutableFloatStateOf(s.splitBrake.coerceIn(0,100).toFloat()) }
                    SettingSlider("Split · ускорение",accel,"${accel.roundToInt()}%",0f..100f,settingsStopped) {accel=it}
                    SettingSlider("Split · торможение",brake,"${brake.roundToInt()}%",0f..100f,settingsStopped) {brake=it}
                    ApplyButton("Применить split",settingsStopped && (accel.roundToInt()!=s.splitAccel || brake.roundToInt()!=s.splitBrake)) {request(V12Protocol.Write(V12Protocol.Control.SPLIT_ACCEL_BRAKE,accel.roundToInt(),brake.roundToInt()))}
                } else MissingSetting("Split появится после чтения настроек")
            }
        } }
        item { SectionHeader("Педали и пороги · на стоянке") }
        item { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            SliderCard {
                if(s!=null) {
                    var sens by remember(s.comfortable) { mutableFloatStateOf(s.comfortable.coerceIn(0,100).toFloat()) }
                    SettingSlider("Чувствительность педалей",sens,"${sens.roundToInt()}%",0f..100f,settingsStopped) {sens=it}
                    ApplyButton("Применить чувствительность",settingsStopped && sens.roundToInt()!=s.comfortable) {request(V12Protocol.Write(V12Protocol.Control.PEDAL_SENS,sens.roundToInt()))}
                    var tilt by remember(s.pedalTilt) { mutableFloatStateOf(s.pedalTilt.toFloat()) }
                    SettingSlider("Наклон педалей",tilt,"${(tilt*10).roundToInt()/10.0}°",-10f..10f,settingsStopped) {tilt=it}
                    ApplyButton("Применить наклон",settingsStopped && kotlin.math.abs(tilt-s.pedalTilt)>=0.05f) {request(V12Protocol.Write(V12Protocol.Control.PEDAL_TILT,(tilt*10).roundToInt()/10))}
                    var alarm1 by remember(s.alarm1) { mutableFloatStateOf(s.alarm1.coerceIn(0.0,70.0).toFloat()) }
                    var alarm2 by remember(s.alarm2) { mutableFloatStateOf(s.alarm2.coerceIn(0.0,70.0).toFloat()) }
                    SettingSlider("Порог сигнала 1",alarm1,"${alarm1.roundToInt()} км/ч",0f..70f,settingsStopped) {alarm1=it}
                    SettingSlider("Порог сигнала 2",alarm2,"${alarm2.roundToInt()} км/ч",0f..70f,settingsStopped) {alarm2=it}
                    ApplyButton("Применить пороги",settingsStopped && (alarm1.roundToInt()!=s.alarm1.roundToInt() || alarm2.roundToInt()!=s.alarm2.roundToInt())) {request(V12Protocol.Write(V12Protocol.Control.ALARMS,alarm1.roundToInt(),alarm2.roundToInt()))}
                } else MissingSetting("Педали и пороги появятся после чтения настроек")
            }
        } }
        item { SectionHeader("Служебное · на стоянке") }
        item { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            SwitchRow(Icons.Rounded.TouchApp,"Кнопка ручки",s?.handleEnabled,enabled=settingsStopped) {request(V12Protocol.Write(V12Protocol.Control.HANDLE_BUTTON,if(it)1 else 0))}
            SwitchRow(Icons.Rounded.LocalShipping,"Транспортный режим",s?.transport,enabled=settingsStopped) {request(V12Protocol.Write(V12Protocol.Control.TRANSPORT,if(it)1 else 0))}
            BlindSwitchRow(Icons.Rounded.Lock,"Блокировка колеса","Колесо не сообщает состояние этой настройки",stopped) { request(V12Protocol.Write(V12Protocol.Control.LOCK,if(it)1 else 0)) }
            SliderCard {
                if(s!=null) {
                    val current=s.standbySeconds/60f
                    var minutes by remember(s.standbySeconds) { mutableFloatStateOf(current.coerceIn(1f,30f)) }
                    Text("Сейчас: ${s.standbySeconds} с",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    SettingSlider("Таймер ожидания",minutes,"${minutes.roundToInt()} мин",1f..30f,settingsStopped) {minutes=it}
                    ApplyButton("Применить таймер",settingsStopped && minutes.roundToInt()*60!=s.standbySeconds) {request(V12Protocol.Write(V12Protocol.Control.STANDBY,minutes.roundToInt()))}
                } else MissingSetting("Таймер появится после чтения настроек")
            }
        } }
        item { OutlinedButton(onClick={actions.read(V12Protocol.Read.SETTINGS)},enabled=ui.connected && !ui.wheel.workPending,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) {Icon(Icons.Rounded.Refresh,null);Spacer(Modifier.width(8.dp));Text("Перечитать настройки",fontSize=14.sp)} }
        item { Text("Лимиты скорости, калибровка и прошивка здесь не меняются. Показания — во вкладке «Данные». Блокировка, DRL, вентилятор и звуковые сигналы отправляются без подтверждения колесом — проверяй результат на месте.",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun SliderCard(content: @Composable ColumnScope.()->Unit) {
    Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(IonShapes.row),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp),content=content)
    }
}

@Composable private fun WheelScreen(ui: VelaUi,actions: Actions,select: (WheelDevice)->Unit,forget: ()->Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Heading("Всегда своё","Один раз выбрать. Дальше — одно нажатие.") }
        item { Status(ui) }
        if(ui.wheel.phase==Phase.ERROR) item {Banner(ui.wheel.message,error=true)}
        ui.saved?.let { d -> item { Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(IonShapes.xl)) {
            Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(IonShapes.m)).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) {
                        Icon(Icons.Rounded.Bookmark,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(21.dp))}
                    Spacer(Modifier.width(12.dp));Column {
                        Text(d.name,fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                        Text("V12 HT · адрес …${d.address.takeLast(8)}",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(onClick={if(ui.connected) actions.disconnect() else actions.connect(d)},enabled=!ui.busy,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) {Text(if(ui.connected) "Отключить" else "Подключить",fontSize=16.sp)}
                TextButton(onClick=forget,enabled=!ui.busy) {Text("Забыть колесо",fontSize=14.sp)}
            }
        } } }
        item { Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(IonShapes.xl)) {
            Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Rounded.Radar,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(12.dp));Text(if(ui.saved==null) "Найти свою V12 HT" else "Другое колесо",fontSize=20.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))}
                Text(ui.wheel.message,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(ui.busy || ui.wheel.phase==Phase.SCANNING) LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick=if(ui.wheel.phase==Phase.SCANNING) actions.stopScan else actions.scan,enabled=!ui.busy,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) {Text(if(ui.wheel.phase==Phase.SCANNING) "Остановить поиск" else "Поиск устройств",fontSize=14.sp)}
                if(ui.connected && ui.saved==null) TextButton(onClick=actions.disconnect) {Text("Отключить")}
            }
        } }
        items(ui.wheel.devices,key={it.address},contentType={"device"}) { d -> Surface(onClick={select(d)},enabled=!ui.busy && !ui.connected,shape=RoundedCornerShape(IonShapes.l),color=MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bluetooth,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)) {Text(d.name,fontSize=16.sp,fontWeight=FontWeight.Medium);Text("…${d.address.takeLast(8)} · ${d.rssi} dBm",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Rounded.ChevronRight,null)
            }
        } }
        item { Banner("Колесо сохраняется только после проверки V12 HT и получения телеметрии. Адрес хранится локально; сам по себе запуск приложения не подключается к BLE.",Icons.Rounded.BookmarkBorder) }
        item { Text("Перед подключением закрой официальное приложение. При уходе из Ion связь останавливается; фонового режима пока нет.",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        item {TextButton(onClick=actions.permissions) {Text("Разрешения Bluetooth",fontSize=14.sp)}}
    }
}
private fun Double.fmt(digits: Int)=String.format(Locale.forLanguageTag("ru"),"%.${digits}f",this)
