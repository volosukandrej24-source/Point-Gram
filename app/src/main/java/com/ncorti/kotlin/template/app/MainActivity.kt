package com.ncorti.kotlin.template.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
const val SERVER_URL = "https://onrender.com"

val IosBlack = Color(0xFF000000)
val IosCard = Color(0xFF1C1C1E)
val IosCard2 = Color(0xFF2C2C2E)
val IosBlue = Color(0xFF0A84FF)
val IosMsgOut = Color(0xFF2B5278)
val IosMsgIn = Color(0xFF182533)
val IosPurple = Color(0xFFBF5AF2)
val IosGold = Color(0xFFFFD60A)
val IosSub = Color(0xFF8E8E93)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("pointgram", MODE_PRIVATE)
        setContent {
            PointGramApp(prefs)
        }
    }
}

suspend fun apiCall(path: String, method: String = "GET", body: JSONObject? = null): Pair<Int, String> = withContext(Dispatchers.IO) {
    val conn = (URL("$SERVER_URL$path").openConnection() as HttpURLConnection).apply {
        requestMethod = method
        setRequestProperty("Content-Type", "application/json")
        connectTimeout = 15000
        readTimeout = 15000
    }
    if (body != null) {
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
    }
    val code = conn.responseCode
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    val text = stream?.bufferedReader()?.readText() ?: "{}"
    Pair(code, text)
}
@Composable
fun PointGramApp(prefs: android.content.SharedPreferences) {
    var userJson by remember { mutableStateOf(prefs.getString("user", null)) }
    val scope = rememberCoroutineScope()

    if (userJson == null) {
        AuthScreen { loggedUser ->
            prefs.edit().putString("user", loggedUser.toString()).apply()
            userJson = loggedUser.toString()
        }
    } else {
        val me = remember(userJson) { JSONObject(userJson!!) }
        MainIosContainer(
            me = me,
            onUpdateMe = { updated ->
                prefs.edit().putString("user", updated.toString()).apply()
                userJson = updated.toString()
            },
            onLogout = {
                prefs.edit().remove("user").apply()
                userJson = null
            }
        )
    }
}
@Composable
fun AuthScreen(onSuccess: (JSONObject) -> Unit) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var stepCode by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().background(IosBlack).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(88.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF37AEE2), Color(0xFF1E96C8)))),
            contentAlignment = Alignment.Center
        ) { Text("✈️", fontSize = 40.sp) }
        Spacer(Modifier.height(20.dp))
        Text(if (!stepCode) "PointGram" else "Введите код", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (!stepCode) "Введите виртуальный номер (+1, +7 или +888)" else "Мы отправили код в Telegram-бота",
            color = IosSub, fontSize = 14.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        if (error.isNotEmpty()) {
            Text(error, color = Color(0xFFFF453A), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = if (!stepCode) phone else code,
            onValueChange = { if (!stepCode) phone = it else code = it },
            placeholder = { Text(if (!stepCode) "+79991234567" else "12345", color = IosSub) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = IosCard, unfocusedContainerColor = IosCard),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                scope.launch {
                    loading = true; error = ""
                    if (!stepCode) {
                        val (st, res) = apiCall("/api/auth/send-code", "POST", JSONObject().put("phone", phone))
                        if (st in 200..299) stepCode = true else error = JSONObject(res).optString("error", "Ошибка")
                    } else {
                        val (st, res) = apiCall("/api/auth/verify-code", "POST", JSONObject().put("phone", phone).put("code", code))
                        if (st in 200..299) onSuccess(JSONObject(res).getJSONObject("user"))
                        else error = JSONObject(res).optString("error", "Неверный код")
                    }
                    loading = false
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (loading) "Загрузка..." else if (!stepCode) "Продолжить" else "Войти", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
    }
}
@Composable
fun MainIosContainer(me: JSONObject, onUpdateMe: (JSONObject) -> Unit, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var users by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var channels by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var giftsCatalog by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var nftMarket by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var activeChat by remember { mutableStateOf<JSONObject?>(null) }
    var isChannelChat by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reloadAll() {
        scope.launch {
            val (st, res) = apiCall("/api/bootstrap/${me.getInt("id")}")
            if (st in 200..299) {
                val obj = JSONObject(res)
                onUpdateMe(obj.getJSONObject("me"))
                users = obj.getJSONArray("users").toList()
                channels = obj.getJSONArray("channels").toList()
                giftsCatalog = obj.getJSONArray("giftsCatalog").toList()
                nftMarket = obj.getJSONArray("nftMarket").toList()
            }
        }
    }

    LaunchedEffect(Unit) { reloadAll() }

    if (activeChat != null) {
        ChatScreen(
            me = me,
            peer = activeChat!!,
            isChannel = isChannelChat,
            giftsCatalog = giftsCatalog,
            onBack = { activeChat = null; reloadAll() }
        )
    } else {
        Scaffold(
            containerColor = IosBlack,
            bottomBar = {
                NavigationBar(containerColor = IosCard, contentColor = IosBlue) {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0; reloadAll() }, icon = { Text("💬", fontSize = 20.sp) }, label = { Text("Чаты") }, colors = NavigationBarItemDefaults.colors(selectedTextColor = IosBlue, unselectedTextColor = IosSub, indicatorColor = Color.Transparent))
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1; reloadAll() }, icon = { Text("💎", fontSize = 20.sp) }, label = { Text("Маркет") }, colors = NavigationBarItemDefaults.colors(selectedTextColor = IosBlue, unselectedTextColor = IosSub, indicatorColor = Color.Transparent))
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2; reloadAll() }, icon = { Text("👤", fontSize = 20.sp) }, label = { Text("Профиль") }, colors = NavigationBarItemDefaults.colors(selectedTextColor = IosBlue, unselectedTextColor = IosSub, indicatorColor = Color.Transparent))
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> ChatsTab(me, users, channels, onOpenUser = { activeChat = it; isChannelChat = false }, onOpenCh = { activeChat = it; isChannelChat = true }, onReload = { reloadAll() })
                    1 -> MarketTab(me, nftMarket, onReload = { reloadAll() })
                    2 -> ProfileTab(me, onReload = { reloadAll() }, onLogout = onLogout)
                }
            }
        }
    }
}

fun JSONArray.toList(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
@Composable
fun ChatsTab(me: JSONObject, users: List<JSONObject>, channels: List<JSONObject>, onOpenUser: (JSONObject) -> Unit, onOpenCh: (JSONObject) -> Unit, onReload: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var showNewCh by remember { mutableStateOf(false) }
    var chTitle by remember { mutableStateOf("") }
    var chUser by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(IosBlack)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Чаты", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = { showNewCh = true }) { Text("📢", fontSize = 20.sp) }
                IconButton(onClick = onReload) { Icon(Icons.Default.Refresh, contentDescription = null, tint = IosBlue) }
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Поиск людей и каналов", color = IosSub) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = IosCard, unfocusedContainerColor = IosCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                ChatRowItem(title = "Избранное", sub = "Заметки и тест подарков", emoji = "🔖", verified = false, premium = false) {
                    onOpenUser(JSONObject(me.toString()).put("first_name", "Избранное"))
                }
            }
            items(users.filter { it.getInt("id") != me.getInt("id") && (it.getString("first_name").contains(query, true) || it.optString("username").contains(query, true)) }) { u ->
                ChatRowItem(
                    title = u.getString("first_name"),
                    sub = if (u.optString("username").isNotEmpty()) "@${u.getString("username")}" else u.getString("phone"),
                    emoji = u.getString("first_name").take(1).uppercase(),
                    verified = u.optBoolean("is_verified"),
                    premium = u.optBoolean("is_premium")
                ) { onOpenUser(u) }
            }
            items(channels.filter { it.getString("title").contains(query, true) }) { ch ->
                ChatRowItem(
                    title = ch.getString("title"),
                    sub = if (ch.optString("username").isNotEmpty()) "@${ch.getString("username")} • Канал" else "Канал",
                    emoji = "📢",
                    verified = ch.optBoolean("is_verified"),
                    premium = false
                ) { onOpenCh(ch) }
            }
        }
    }
    if (showNewCh) {
        AlertDialog(
            onDismissRequest = { showNewCh = false },
            containerColor = IosCard,
            title = { Text("Новый канал", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(value = chTitle, onValueChange = { chTitle = it }, placeholder = { Text("Название") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = chUser, onValueChange = { chUser = it }, placeholder = { Text("Юзернейм") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        apiCall("/api/channels/create", "POST", JSONObject().put("title", chTitle).put("username", chUser).put("ownerId", me.getInt("id")))
                        showNewCh = false; chTitle = ""; chUser = ""; onReload()
                    }
                }) { Text("Создать", color = IosBlue) }
            }
        )
    }
}

@Composable
fun ChatRowItem(title: String, sub: String, emoji: String, verified: Boolean, premium: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(IosBlue), contentAlignment = Alignment.Center) {
            Text(emoji, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                if (verified) Text(" ✓", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                if (premium) Text(" ★", color = IosPurple, fontWeight = FontWeight.Bold)
            }
            Text(sub, color = IosSub, fontSize = 14.sp)
        }
    }
    HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.5.dp, modifier = Modifier.padding(start = 78.dp))
}
@Composable
fun ChatScreen(me: JSONObject, peer: JSONObject, isChannel: Boolean, giftsCatalog: List<JSONObject>, onBack: () -> Unit) {
    var messages by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var showGifts by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadMsgs() {
        scope.launch {
            val url = if (isChannel) "/api/messages/channel/${peer.getInt("id")}" else "/api/messages/direct/${me.getInt("id")}/${peer.getInt("id")}"
            val (st, res) = apiCall(url)
            if (st in 200..299) messages = JSONArray(res).toList()
        }
    }

    LaunchedEffect(peer) { loadMsgs() }

    Column(Modifier.fillMaxSize().background(IosBlack)) {
        Row(Modifier.fillMaxWidth().background(IosCard).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Назад", color = IosBlue, fontSize = 17.sp) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (isChannel) peer.getString("title") else peer.getString("first_name"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(if (isChannel) "Канал" else peer.optString("phone", "в сети"), color = IosSub, fontSize = 12.sp)
            }
            if (!isChannel) {
                IconButton(onClick = { showGifts = true }) { Text("🎁", fontSize = 22.sp) }
            } else { Spacer(Modifier.width(48.dp)) }
        }

        LazyColumn(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { m ->
                val isOut = m.optInt("sender_id") == me.getInt("id") && !isChannel
                val giftStr = m.optString("gift_json", "")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isOut) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.widthIn(max = 270.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (giftStr.isNotEmpty() && giftStr != "null") IosPurple.copy(alpha = 0.35f) else if (isOut) IosMsgOut else IosMsgIn)
                            .padding(12.dp)
                    ) {
                        if (isChannel) Text(m.optString("sender_name", "Автор"), color = IosBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (giftStr.isNotEmpty() && giftStr != "null") {
                            val g = JSONObject(giftStr)
                            Text(g.optString("emoji", "🎁"), fontSize = 40.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                            Text(g.optString("name", "Подарок"), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                            Text("${g.optString("rarity")} • ⭐️ ${g.optInt("price")}", color = Color.LightGray, fontSize = 12.sp)
                        } else {
                            Text(m.optString("text"), color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().background(IosCard).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text("Сообщение...", color = IosSub) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = IosCard2, unfocusedContainerColor = IosCard2),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                if (text.isNotBlank()) {
                    val msgText = text; text = ""
                    scope.launch {
                        val body = JSONObject().put("sender_id", me.getInt("id")).put("text", msgText)
                        if (isChannel) body.put("channel_id", peer.getInt("id")) else body.put("receiver_id", peer.getInt("id"))
                        apiCall("/api/messages/send", "POST", body)
                        loadMsgs()
                    }
                }
            }) { Icon(Icons.Default.Send, contentDescription = null, tint = IosBlue) }
        }
    }

    if (showGifts) {
        AlertDialog(
            onDismissRequest = { showGifts = false },
            containerColor = IosCard,
            title = { Text("🎁 Отправить подарок", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    giftsCatalog.forEach { g ->
                        Button(
                            onClick = {
                                scope.launch {
                                    apiCall("/api/shop/send-gift", "POST", JSONObject().put("senderId", me.getInt("id")).put("receiverId", peer.getInt("id")).put("giftId", g.getString("id")))
                                    showGifts = false; loadMsgs()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = IosCard2),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("${g.getString("emoji")} ${g.getString("name")} — ${g.getInt("price")} ⭐️", color = Color.White) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showGifts = false }) { Text("Закрыть", color = IosBlue) } }
        )
    }
}
@Composable
fun MarketTab(me: JSONObject, nftMarket: List<JSONObject>, onReload: () -> Unit) {
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LazyColumn(Modifier.fillMaxSize().background(IosBlack).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Маркет", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("⭐️ ${me.optInt("stars_balance")}", color = IosGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(msg, color = IosBlue, fontSize = 14.sp)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(IosPurple.copy(alpha = 0.35f), IosBlue.copy(alpha = 0.35f)))).padding(16.dp)) {
                Text("★ PointGram Premium", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Фиолетовая звезда и статус в профиле", color = Color.LightGray, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val (st, res) = apiCall("/api/shop/buy-premium", "POST", JSONObject().put("userId", me.getInt("id")))
                            msg = if (st in 200..299) "🎉 Premium активирован!" else JSONObject(res).optString("error", "Ошибка")
                            onReload()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Подключить за 200 ⭐️", fontWeight = FontWeight.SemiBold) }
            }
        }
        items(nftMarket) { item ->
            val type = item.getString("type")
            val value = item.getString("value")
            val price = item.getInt("price")
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(IosCard).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (type == "number") "📱 Анонимный номер" else "💎 NFT-юзернейм", color = IosSub, fontSize = 12.sp)
                    Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        scope.launch {
                            val (st, res) = apiCall("/api/shop/buy-nft", "POST", JSONObject().put("userId", me.getInt("id")).put("type", type).put("value", value).put("price", price))
                            msg = if (st in 200..299) "🎉 Куплено: $value!" else JSONObject(res).optString("error", "Ошибка")
                            onReload()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IosCard2),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("$price ⭐️", color = IosGold, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
@Composable
fun ProfileTab(me: JSONObject, onReload: () -> Unit, onLogout: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(me.optString("first_name")) }
    var username by remember { mutableStateOf(me.optString("username")) }
    var bio by remember { mutableStateOf(me.optString("bio")) }
    val scope = rememberCoroutineScope()

    val giftsArr = me.optJSONArray("gifts") ?: JSONArray()
    val nftUsers = me.optJSONArray("nft_usernames") ?: JSONArray()
    val nftNums = me.optJSONArray("nft_numbers") ?: JSONArray()

    LazyColumn(Modifier.fillMaxSize().background(IosBlack).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onLogout) { Text("Выйти", color = Color(0xFFFF453A), fontSize = 16.sp) }
                TextButton(onClick = { editing = !editing }) { Text(if (editing) "Готово" else "Изм.", color = IosBlue, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
            Box(Modifier.size(92.dp).clip(CircleShape).background(IosBlue), contentAlignment = Alignment.Center) {
                Text(me.optString("first_name", "U").take(1).uppercase(), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(me.optString("first_name"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (me.optBoolean("is_verified")) Text(" ✓", color = Color(0xFF38BDF8), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (me.optBoolean("is_premium")) Text(" ★", color = IosPurple, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text("${me.optString("phone")} • ⭐️ ${me.optInt("stars_balance")} Stars", color = IosSub, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
        }

        if (editing) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(IosCard).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Имя") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Юзернейм (без @)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("О себе") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            scope.launch {
                                apiCall("/api/profile/update", "POST", JSONObject().put("id", me.getInt("id")).put("first_name", name).put("username", username).put("bio", bio))
                                editing = false; onReload()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Сохранить профиль") }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(IosCard).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("О себе", color = IosSub, fontSize = 12.sp)
                Text(if (me.optString("bio").isNotEmpty()) me.optString("bio") else "Нажмите «Изм.», чтобы добавить описание", color = Color.White, fontSize = 15.sp)
                HorizontalDivider(color = IosCard2)
                Text("Юзернейм", color = IosSub, fontSize = 12.sp)
                Text(if (me.optString("username").isNotEmpty()) "@${me.optString("username")}" else "Не задан", color = IosBlue, fontSize = 15.sp)
            }
            Spacer(Modifier.height(16.dp))
        }

        if (nftUsers.length() > 0 || nftNums.length() > 0) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(IosCard).padding(14.dp)) {
                    Text("💎 Коллекционные NFT", color = IosSub, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    for (i in 0 until nftUsers.length()) Text("• ${nftUsers.getString(i)}", color = IosPurple, fontWeight = FontWeight.Bold)
                    for (i in 0 until nftNums.length()) Text("• ${nftNums.getString(i)}", color = IosGold, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(IosCard).padding(14.dp)) {
                Text("🎁 Витрина подарков (${giftsArr.length()})", color = IosSub, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                if (giftsArr.length() == 0) {
                    Text("Пока нет подарков", color = IosSub, fontSize = 14.sp)
                } else {
                    for (i in 0 until giftsArr.length()) {
                        val g = giftsArr.getJSONObject(i)
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(g.optString("emoji", "🎁"), fontSize = 26.sp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(g.optString("name"), color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("${g.optString("rarity")} • от ${g.optString("from")}", color = IosSub, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
