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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
            val dark=preference ?: isSystemInDarkTheme()
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
private val Lilac=Color(0xFFC9BDFF)
private val Acid=Color(0xFFD7F5A5)
private val Ink=Color(0xFF221C35)
private val Round=RoundedCornerShape(28.dp)
@Composable internal fun VelaTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors=if(dark) darkColorScheme(primary=Lilac,onPrimary=Ink,primaryContainer=Color(0xFF3A3156),onPrimaryContainer=Color(0xFFEAE2FF),
        tertiary=Acid,onTertiary=Color(0xFF263519),secondaryContainer=Color(0xFF302D3B),onSecondaryContainer=Color(0xFFE7E0F2),
        background=Color(0xFF101014),onBackground=Color(0xFFF4F0FA),surface=Color(0xFF101014),onSurface=Color(0xFFF4F0FA),
        surfaceContainerLow=Color(0xFF19181F),surfaceContainer=Color(0xFF222029),surfaceContainerHigh=Color(0xFF2B2834),
        onSurfaceVariant=Color(0xFFC7C0D0),outline=Color(0xFF938A9F),outlineVariant=Color(0xFF45404F),
        errorContainer=Color(0xFF4B232B),onErrorContainer=Color(0xFFFFD9DE))
    else lightColorScheme(primary=Color(0xFF66509F),onPrimary=Color.White,primaryContainer=Color(0xFFE8DEFF),onPrimaryContainer=Ink,
        tertiary=Color(0xFF4C6328),onTertiary=Color.White,secondaryContainer=Color(0xFFECE5F4),onSecondaryContainer=Ink,
        background=Color(0xFFFAF8FE),onBackground=Ink,surface=Color(0xFFFAF8FE),onSurface=Ink,
        surfaceContainerLow=Color(0xFFF5F1FA),surfaceContainer=Color(0xFFF0EBF6),surfaceContainerHigh=Color(0xFFE7E0F0),onSurfaceVariant=Color(0xFF554D61))
    MaterialExpressiveTheme(colorScheme=colors,motionScheme=MotionScheme.expressive(),
        shapes=Shapes(medium=RoundedCornerShape(20.dp),large=Round,extraLarge=RoundedCornerShape(36.dp)),content=content)
}
@Composable internal fun VelaApp(ui: VelaUi, dark: Boolean, actions: Actions, initialTab: Int = 0) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var selected by remember { mutableStateOf<WheelDevice?>(null) }
    var command by remember { mutableStateOf<V12Protocol.Write?>(null) }
    var forget by remember { mutableStateOf(false) }
    fun primary() { if(ui.saved!=null) actions.connect(ui.saved) else { tab=3; actions.scan() } }
    Scaffold(containerColor=MaterialTheme.colorScheme.background,contentWindowInsets=WindowInsets(0,0,0,0),topBar={
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start=20.dp,end=12.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome,null,Modifier.size(23.dp),tint=MaterialTheme.colorScheme.primary)
            }
            Text("Ion",fontSize=26.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(start=10.dp).weight(1f))
            IconButton(onClick={tab=3}) { Icon(if(ui.connected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth,"Подключение колеса",tint=MaterialTheme.colorScheme.primary) }
            IconButton(onClick=actions.theme) { Icon(if(dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,"Сменить тему") }
        }
    },bottomBar={
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=16.dp,vertical=8.dp)) {
            NavigationBar(Modifier.clip(RoundedCornerShape(30.dp)),containerColor=MaterialTheme.colorScheme.surfaceContainer,tonalElevation=0.dp,windowInsets=WindowInsets(0,0,0,0)) {
                listOf("Езда" to Icons.Rounded.Explore,"Данные" to Icons.Rounded.DataObject,"Пульт" to Icons.Rounded.Tune,"Связь" to Icons.Rounded.Bluetooth).forEachIndexed { i,(label,icon) ->
                    NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(icon,null,Modifier.size(23.dp))},label={Text(label,fontSize=14.sp,maxLines=1)},
                        colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primaryContainer))
                }
            }
        }
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
            Text("Убедись, что колесо неподвижно. После отправки Ion перечитает значение. Неподтверждённая команда не будет повторена автоматически.") }},
        confirmButton={TextButton(onClick={command=null;actions.write(c)},enabled=ui.canWrite) { Text("Применить") }},dismissButton={TextButton(onClick={command=null}) { Text("Отмена") }}) }
    if(forget) AlertDialog(onDismissRequest={forget=false},title={Text("Забыть колесо?")},text={Text("Соединение будет закрыто. Для следующего подключения нужно будет выбрать устройство заново.")},
        confirmButton={TextButton(onClick={forget=false;actions.forget()}) {Text("Забыть")}},dismissButton={TextButton(onClick={forget=false}) {Text("Отмена")}})
}
private val PagePadding=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=24.dp)
@Composable private fun Heading(title: String, subtitle: String) {
    Column(Modifier.padding(bottom=8.dp)) {
        Text(title,fontSize=30.sp,fontWeight=FontWeight.Medium,modifier=Modifier.semantics { heading() })
        Text(subtitle,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp))
    }
}
@Composable private fun Status(ui: VelaUi) {
    val live=ui.connected
    val color by animateColorAsState(if(live) Acid else MaterialTheme.colorScheme.secondaryContainer,tween(300),label="linkColor")
    Surface(color=color,contentColor=if(live) Color(0xFF283716) else MaterialTheme.colorScheme.onSecondaryContainer,shape=RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            if(ui.busy) CircularProgressIndicator(Modifier.size(14.dp),strokeWidth=2.dp) else Icon(if(live) Icons.Rounded.CheckCircle else Icons.Rounded.BluetoothDisabled,null,Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp)); AnimatedContent(if(live) "На связи" else if(ui.busy) "Проверка" else "Офлайн",label="status") { Text(it,fontSize=14.sp,fontWeight=FontWeight.Medium) }
        }
    }
}
@Composable private fun Banner(text: String, icon: ImageVector = Icons.Rounded.Info, error: Boolean = false) {
    Surface(shape=RoundedCornerShape(20.dp),color=if(error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor=if(error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null,Modifier.size(21.dp)); Spacer(Modifier.width(12.dp)); Text(text,fontSize=14.sp,modifier=Modifier.weight(1f))
        }
    }
}
@Composable private fun RideScreen(ui: VelaUi, connect: () -> Unit) {
    val t=ui.wheel.telemetry
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth().padding(bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("ТВОЁ КОЛЕСО",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,letterSpacing=1.sp); Text("V12 HT",fontSize=30.sp,fontWeight=FontWeight.Medium) }
            Status(ui)
        } }
        if(ui.wheel.phase==Phase.ERROR) item { Banner(ui.wheel.message,error=true) }
        if(t!=null && !ui.fresh) item { Banner(if(ui.connected) "Показания устарели · BLE остаётся подключён" else "Показания устарели · связь потеряна",Icons.Rounded.Update,error=true) }
        item { Surface(color=Lilac,contentColor=Ink,shape=RoundedCornerShape(topStart=36.dp,topEnd=36.dp,bottomEnd=36.dp,bottomStart=14.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Text("Скорость",fontSize=16.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f)); Icon(Icons.Rounded.NearMe,null,Modifier.size(25.dp)) }
                Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.Bottom) {
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val value=t?.speed?.fmt(1) ?: "—"; val size=minOf(88f,maxWidth.value/(value.length*0.60f*LocalDensity.current.fontScale)).coerceAtLeast(32f)
                        Text(value,fontSize=size.sp,lineHeight=(size*1.12f).sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-3).sp,maxLines=1)
                    }
                    Text("км/ч",fontSize=18.sp,modifier=Modifier.padding(bottom=14.dp))
                }
                HorizontalDivider(color=Ink.copy(alpha=.15f))
                Row(Modifier.fillMaxWidth().padding(top=14.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(if(ui.fresh) Icons.Rounded.Sensors else Icons.Rounded.Bluetooth,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if(ui.fresh) "Живые данные · Bluetooth LE" else if(ui.busy) ui.wheel.message else if(ui.connected) "Ожидает новых данных · Bluetooth LE" else "Ожидает подключения",fontSize=14.sp,modifier=Modifier.weight(1f))
                }
            }
        } }
        item { Surface(onClick=connect,enabled=!ui.busy,color=MaterialTheme.colorScheme.surfaceContainer,shape=Round) {
            Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) {
                    Icon(if(ui.connected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth,null,tint=MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)) {
                    Text(if(ui.connected) "Соединение активно" else if(ui.busy) "Подключаюсь…" else if(ui.saved!=null) "Подключить своё колесо" else "Найти колесо",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                    Text(if(ui.connected) "${ui.wheel.packets} пакетов · V12 HT" else ui.saved?.name ?: "Выбрать свою V12 HT по Bluetooth",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp));Icon(Icons.Rounded.ChevronRight,null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
            }
        } }
        item { Text("Сейчас на колесе",fontSize=20.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=6.dp)) }
        item { MetricPair(first={ m -> Metric("Батарея",t?.battery?.takeIf { it in 0.0..100.0 }?.roundToInt()?.toString() ?: "—","%",Icons.Rounded.BatteryFull,m,true) },
            second={m -> Metric("Напряжение",t?.voltage?.fmt(1) ?: "—","В",Icons.Rounded.ElectricBolt,m)}) }
        item { MetricPair(first={m -> Metric("MOS",t?.mosTemp?.toString() ?: "—","°C",Icons.Rounded.Thermostat,m)},
            second={m -> Metric("Ток",t?.current?.fmt(1) ?: "—","А",Icons.Rounded.Waves,m)}) }
        item { Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(22.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Rounded.Speed,null,tint=MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column {
                Text("Мотор  ${t?.motorTemp?.let { "$it °C" } ?: "—"}",fontSize=16.sp)
                Text("Остальные поля — во вкладке «Данные»",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        } }
    }
}
@Composable private fun MetricPair(first: @Composable (Modifier)->Unit, second: @Composable (Modifier)->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(maxWidth<300.dp || LocalDensity.current.fontScale>1.2f) Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {first(Modifier.fillMaxWidth());second(Modifier.fillMaxWidth())}
        else Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {first(Modifier.weight(1f));second(Modifier.weight(1f))}
    }
}
@Composable private fun Metric(label: String,value: String,unit: String,icon: ImageVector,modifier: Modifier,accent: Boolean=false) {
    Surface(modifier,color=if(accent) Acid else MaterialTheme.colorScheme.surfaceContainer,contentColor=if(accent) Color(0xFF263619) else MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {Text(label,fontSize=14.sp,modifier=Modifier.weight(1f));Icon(icon,null,Modifier.size(20.dp))}
            Row(Modifier.padding(top=18.dp),verticalAlignment=Alignment.Bottom) {Text(value,fontSize=34.sp,fontWeight=FontWeight.Medium,maxLines=1);Text(unit,fontSize=16.sp,modifier=Modifier.padding(start=5.dp,bottom=4.dp))}
        }
    }
}

@Composable private fun ReadAllButton(ui: VelaUi,actions: Actions,modifier: Modifier) {
    FilledTonalButton(onClick=actions.readAll,enabled=ui.connected && !ui.wheel.workPending,modifier=modifier.heightIn(min=48.dp)) {
        if(ui.wheel.workPending) CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp) else Icon(Icons.Rounded.Refresh,null,Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp)); Text(if(ui.wheel.workPending) "Читаю…" else "Прочитать всё",fontSize=14.sp)
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
        item { OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Найти поле или пакет")},leadingIcon={Icon(Icons.Rounded.Search,null)},shape=RoundedCornerShape(20.dp)) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Все поля","Телеметрия","Настройки","Raw / пакеты").forEachIndexed { i,label -> FilterChip(selected=mode==i,onClick={mode=i},label={Text(label,fontSize=14.sp)}) }
        } }
        val selectedReads=reads.filter { when(mode) {1 -> it==V12Protocol.Read.TELEMETRY;2 -> it==V12Protocol.Read.SETTINGS;else -> true} }
        items(selectedReads,key={it.name}) { read ->
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
    Surface(shape=Round,color=MaterialTheme.colorScheme.surfaceContainerLow) {
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
                        if(raw || rawMode) Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh,shape=RoundedCornerShape(16.dp)) {
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
            SelectionContainer { Text(field.value,fontSize=16.sp,fontWeight=FontWeight.Medium,modifier=Modifier.widthIn(max=155.dp),textAlign=TextAlign.End) }
        }
        if(field.note.isNotEmpty()) Text(field.note,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=5.dp))
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
}

@Composable private fun ControlScreen(ui: VelaUi,actions: Actions,request: (V12Protocol.Write)->Unit) {
    val t=ui.wheel.telemetry; val s=ui.wheel.settings
    val enabled=ui.canWrite
    val settingsEnabled=enabled && ui.settingsFresh && s!=null
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Heading("Твой пульт","Свет, звук и ожидание · с проверкой результата") }
        item { Banner(when {ui.wheel.workPending -> "Запрос выполняется. Дождись результата."; !ui.connected -> "Подключи V12 HT, чтобы прочитать и изменить настройки."; !enabled -> "Запись доступна только на остановленном колесе: нужны 3 свежих пакета, без зарядки и обновления прошивки."; else -> "Колесо неподвижно. Каждое изменение — после подтверждения."},if(enabled) Icons.Rounded.VerifiedUser else Icons.Rounded.Lock) }
        ui.wheel.writeMessage?.let { message -> item { Banner(message,Icons.Rounded.FactCheck,error=message.contains("НЕ подтверждено") || message.contains("заблокирована")) } }
        item { SettingsGroup("Свет",Icons.Rounded.LightMode) {
            ToggleRow("Ближний свет",t?.lowBeam,enabled) { request(V12Protocol.Write(V12Protocol.Control.LIGHTS,if(it)1 else 0,if(t?.highBeam==true)1 else 0)) }
            ToggleRow("Дальний свет",t?.highBeam,enabled) { request(V12Protocol.Write(V12Protocol.Control.LIGHTS,if(t?.lowBeam==true)1 else 0,if(it)1 else 0)) }
            ToggleRow("Автосвет",s?.autoLight,settingsEnabled) { request(V12Protocol.Write(V12Protocol.Control.AUTO_LIGHT,if(it)1 else 0)) }
            if(s!=null && s.lowBrightness in 0..100 && s.highBrightness in 0..100) {
                var low by remember(s.lowBrightness,s.highBrightness) { mutableFloatStateOf(s.lowBrightness.toFloat()) }
                var high by remember(s.lowBrightness,s.highBrightness) { mutableFloatStateOf(s.highBrightness.toFloat()) }
                SettingSlider("Яркость ближнего",low,"${low.roundToInt()}%",0f..100f,settingsEnabled) {low=it}
                SettingSlider("Яркость дальнего",high,"${high.roundToInt()}%",0f..100f,settingsEnabled) {high=it}
                ApplyButton("Применить яркость",settingsEnabled && (low.roundToInt()!=s.lowBrightness || high.roundToInt()!=s.highBrightness)) {request(V12Protocol.Write(V12Protocol.Control.BRIGHTNESS,low.roundToInt(),high.roundToInt()))}
            } else MissingSetting("Яркость появится после чтения настроек")
        } }
        item { SettingsGroup("Звук",Icons.Rounded.VolumeUp) {
            ToggleRow("Без звука",s?.muted,settingsEnabled) {request(V12Protocol.Write(V12Protocol.Control.MUTE,if(it)1 else 0))}
            ToggleRow("Звук движения",s?.soundWave,settingsEnabled) {request(V12Protocol.Write(V12Protocol.Control.SOUND_WAVE,if(it)1 else 0))}
            if(s!=null && s.volume in 0..100) {
                var volume by remember(s.volume) { mutableFloatStateOf(s.volume.toFloat()) }
                SettingSlider("Громкость",volume,"${volume.roundToInt()}%",0f..100f,settingsEnabled) {volume=it}
                ApplyButton("Применить громкость",settingsEnabled && volume.roundToInt()!=s.volume) {request(V12Protocol.Write(V12Protocol.Control.VOLUME,volume.roundToInt()))}
            } else MissingSetting("Громкость появится после чтения настроек")
        } }
        item { SettingsGroup("Ожидание",Icons.Rounded.Schedule) {
            if(s!=null) {
                val current=s.standbySeconds/60f
                var minutes by remember(s.standbySeconds) { mutableFloatStateOf(current.coerceIn(1f,30f)) }
                Text("Сейчас: ${s.standbySeconds} с",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                SettingSlider("Таймер",minutes,"${minutes.roundToInt()} мин",1f..30f,settingsEnabled) {minutes=it}
                ApplyButton("Применить таймер",settingsEnabled && minutes.roundToInt()*60!=s.standbySeconds) {request(V12Protocol.Write(V12Protocol.Control.STANDBY,minutes.roundToInt()))}
            } else MissingSetting("Таймер появится после чтения настроек")
        } }
        item { OutlinedButton(onClick={actions.read(V12Protocol.Read.SETTINGS)},enabled=ui.connected && !ui.wheel.workPending,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) {Icon(Icons.Rounded.Refresh,null);Spacer(Modifier.width(8.dp));Text("Перечитать настройки",fontSize=14.sp)} }
        item { Text("Лимиты скорости, педали, калибровка, транспортный режим и прошивка здесь не меняются. Поля доступны для чтения во вкладке «Данные». Доступность команд зависит от прошивки.",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun SettingsGroup(title: String,icon: ImageVector,content: @Composable ColumnScope.()->Unit) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=Round) {
        Column(Modifier.fillMaxWidth().padding(20.dp).animateContentSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) {Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(21.dp))}
                Spacer(Modifier.width(12.dp));Text(title,fontSize=21.sp,fontWeight=FontWeight.Medium)
            };content()
        }
    }
}
@Composable private fun ToggleRow(title: String,value: Boolean?,enabled: Boolean,change: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=54.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=12.dp)) {Text(title,fontSize=16.sp);if(value==null) Text("Нет данных",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Switch(checked=value==true,onCheckedChange=change,enabled=enabled && value!=null)
    }
}
@Composable private fun SettingSlider(label: String,value: Float,text: String,range: ClosedFloatingPointRange<Float>,enabled: Boolean,change: (Float)->Unit) {
    Column(Modifier.padding(top=4.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text(label,fontSize=14.sp,modifier=Modifier.weight(1f));Text(text,fontSize=16.sp,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Medium)}
        Slider(value=value,onValueChange=change,enabled=enabled,valueRange=range,modifier=Modifier.fillMaxWidth())
    }
}
@Composable private fun ApplyButton(label: String,enabled: Boolean,click: ()->Unit) {FilledTonalButton(onClick=click,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(label,fontSize=14.sp)}}
@Composable private fun MissingSetting(text: String) { Text(text,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=12.dp)) }

@Composable private fun WheelScreen(ui: VelaUi,actions: Actions,select: (WheelDevice)->Unit,forget: ()->Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PagePadding,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Heading("Всегда своё","Один раз выбрать. Дальше — одно нажатие.") }
        item { Status(ui) }
        if(ui.wheel.phase==Phase.ERROR) item {Banner(ui.wheel.message,error=true)}
        ui.saved?.let { d -> item { SettingsGroup("Сохранённое колесо",Icons.Rounded.Bookmark) {
            Text(d.name,fontSize=22.sp,fontWeight=FontWeight.Medium)
            Text("V12 HT · адрес …${d.address.takeLast(8)}",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick={if(ui.connected) actions.disconnect() else actions.connect(d)},enabled=!ui.busy,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) {Text(if(ui.connected) "Отключить" else "Подключить",fontSize=16.sp)}
            TextButton(onClick=forget,enabled=!ui.busy) {Text("Забыть колесо",fontSize=14.sp)}
        } } }
        item { Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=Round) {
            Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Rounded.Radar,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(12.dp));Text(if(ui.saved==null) "Найти свою V12 HT" else "Другое колесо",fontSize=20.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))}
                Text(ui.wheel.message,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(ui.busy || ui.wheel.phase==Phase.SCANNING) LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick=if(ui.wheel.phase==Phase.SCANNING) actions.stopScan else actions.scan,enabled=!ui.busy,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) {Text(if(ui.wheel.phase==Phase.SCANNING) "Остановить поиск" else "Поиск устройств",fontSize=14.sp)}
                if(ui.connected && ui.saved==null) TextButton(onClick=actions.disconnect) {Text("Отключить")}
            }
        } }
        items(ui.wheel.devices,key={it.address}) { d -> Surface(onClick={select(d)},enabled=!ui.busy && !ui.connected,shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
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
