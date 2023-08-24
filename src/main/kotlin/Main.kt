import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sun.jna.Library
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.W32APIFunctionMapper
import com.sun.jna.win32.W32APITypeMapper
import kotlinx.coroutines.*
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.*
import java.awt.image.BufferedImage
import java.io.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import kotlin.math.min
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

///////////////////////////////
// Service

private fun log(text: String) {
    println(text)
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val dateTimeString = LocalDateTime.now().format(formatter)
    File("${States.path}/evaKotlinLogs.txt").appendText("\n$dateTimeString $text")
}

///////////////////////////////
// Design

@Composable
@Preview
fun app(exitFunction: () -> Unit) {

    Column {

        // State
        TimerText()

        // Draw text
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Draw text: ")
            CheckBoxDrawText()
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Crop vertical: ")
            CheckBoxCropVertical()
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Run open: ")
            CheckBoxRunOpen()
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Start on run: ")
            CheckBoxStartOnRun()
        }

        // Timer hours
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (Settings.hours > 0)
                    Settings.hours -= 1
            }) {
                Text("-")
            }
            Spacer(modifier = Modifier.width(8.dp))
            FieldClock(
                text = Settings.hours,
                onValueChange = { newText ->
                    newText.substring(0, min(2, newText.length)).apply {
                        if (this.matches("[0-9]*".toRegex()))
                            Settings.hours = this.toInt()
                    }
                },
                label = "Hours"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                Settings.hours += 1
            }) {
                Text("+")
            }

        }

        // Timer minutes
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                //editClock(Settings.minutes, -1, 0, 59)
                if (Settings.minutes > 0)
                    Settings.minutes -= 1
            }) {
                Text("-")
            }
            Spacer(modifier = Modifier.width(8.dp))
            FieldClock(
                text = Settings.minutes,
                onValueChange = { newText ->
                    newText.substring(0, min(2, newText.length)).apply {
                        if (this.matches("[0-9]*".toRegex()))
                            Settings.minutes = this.toInt()
                    }
                },
                label = "Minutes"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (Settings.minutes < 59)
                    Settings.minutes += 1
            }) {
                Text("+")
            }
        }

        // Commands
        Row {
            Button(onClick = {
                toStart()
            }) {
                Text("Start")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                toPause()
            }) {
                Text("Pause")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                toStop()
            }) {
                Text("Stop")
            }
            Spacer(modifier = Modifier.width(8.dp))
            ButtonNext()
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                exitFunction()
            }) {
                Text("Exit")
            }
        }

        TitleText()
    }

}

@Composable
fun CheckBoxDrawText() {
    Checkbox(
        checked = Settings.drawText,
        onCheckedChange = { Settings.drawText = it },
        enabled = true
    )
}

@Composable
fun CheckBoxCropVertical() {
    Checkbox(
        checked = Settings.cropVertical,
        onCheckedChange = { Settings.cropVertical = it },
        enabled = true
    )
}

@Composable
fun CheckBoxRunOpen() {
    Checkbox(
        checked = Settings.runOpen,
        onCheckedChange = { Settings.runOpen = it },
        enabled = true
    )
}

@Composable
fun CheckBoxStartOnRun() {
    Checkbox(
        checked = Settings.startOnRun,
        onCheckedChange = { Settings.startOnRun = it },
        enabled = true
    )
}


@Composable
fun ButtonNext() {
    Button(enabled = !States.loading,
        onClick = {
            nextImage()
        }) {
        Text("Next")
    }
}

@Composable
fun FieldClock(
    text: Int,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(value = text.toString(), onValueChange = onValueChange, label = { Text(label) })
}

@Composable
fun TimerText() {
    Text(text = "Time left: ${States.timeLeft}")
}

@Composable
fun TitleText() {
    Text(text = "Title image: ${States.title}")
}


////////////////////////////////////
// Get image

fun nextImage() {
    CoroutineScope(Dispatchers.Default).launch {
        States.loading = true
        try {
            val (screenWidth, screenHeight) = Toolkit.getDefaultToolkit().screenSize.let { it.width to it.height }
            val res = getRandomWikiImageUrl(screenWidth, screenHeight)
            if (res != null) {
                val (url, title) = (res.first to res.second)
                //val curDir = System.getProperty("user.dir")
                //val homeDir = System.getProperty("user.home")
                //val pathToFile = "$homeDir/pic.${url.substringAfterLast(".")}"
                val pathToFile = "${States.path}/pic.png"
                log(url)
                if (downloadImage(url, pathToFile)) {
                    editImage(pathToFile, title, screenWidth, screenHeight)
                    setWallpaper(pathToFile)
                }
            }
        } catch (e: Exception) {
            log(e.toString())
        } finally {
            States.loading = false
        }
    }
}

private fun getRandomWikiImageUrl(screenWith: Int, screenHeight: Int): Pair<String, String>? {
    val urlWiki = "https://en.wikipedia.org/wiki/Special:RandomInCategory/Featured_pictures"
//    val urlWiki = "https://en.wikipedia.org/wiki/File:Ermina_Zaenah,_three-quarter_portrait_(c_1960)_-_restored_(without_name).jpg"
    val client = OkHttpClient()
    val request = Request.Builder().url(urlWiki).build()
    try {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful)
            return null
        val newURL = response.request.url.toString()
        log(newURL)
        if (newURL.endsWith("webm"))
            return getRandomWikiImageUrl(screenWith, screenHeight)
        val text = response.body!!.string()
        var urlImage: String? = null

        val regex0 = "<a href=\"([^\"]+)\"[^>]*>([0-9,]+) × ([0-9,]+) pixels</a>".toRegex()
        val matchResults = regex0.findAll(text)
        if (matchResults.any()) {
            //val ls1t: List<Triple<String,Long,Long>> = List<>()
            val lst: MutableList<Triple<String, Long, Long>> = mutableListOf()
            for (matchResult in matchResults) {
                //val (urlImage, width, height) = matchResult.groups.get()
                val url = matchResult.groups[1]!!.value
                val width = matchResult.groups[2]!!.value.replace(",", "")
                val height = matchResult.groups[3]!!.value.replace(",", "")
                lst.add(Triple(url, width.toLong(), height.toLong()))
            }
            val sortedValues = lst.sortedByDescending { it.second }
            for (triple in sortedValues) {
                if (triple.second >= screenWith && triple.third >= screenHeight)
                    urlImage = triple.first
                else {
                    if (urlImage == null)
                        urlImage = triple.first
                    break
                }
            }
        }

        if (urlImage == null) {
            val regex1 = "class=\"fullMedia\".*href=\"([^\"]+)\"".toRegex()
            val matchResult1 = regex1.find(text)
            if (matchResult1 != null)
                urlImage = matchResult1.groups[1]!!.value
        }

        if (urlImage == null)
            return null

        if (!urlImage.startsWith("http"))
            urlImage = "https:$urlImage"

        var regex2 = "(?s)Russian: </b></span>(.+?)</div>".toRegex()
        var matchResult2 = regex2.find(text)
        if (matchResult2 == null) {
            regex2 = "(?s)English: </b></span>(.+?)</div>".toRegex()
            matchResult2 = regex2.find(text)
        }
        if (matchResult2 == null) {
            regex2 = "(?s)class=\"description\">(.+?)</td>".toRegex() // (?s) - dot -all, +? - non-greedy
            matchResult2 = regex2.find(text)
        }
        if (matchResult2 == null) {
            regex2 = "(?s)>Title</td>(.+?)</td>".toRegex() // title
            matchResult2 = regex2.find(text)
        }
        var title = matchResult2?.groups?.get(1)?.value ?: ""
        title = title.replace("<[^<>]+>".toRegex(), "") // delete tags
        title = title.replace("&[^;]+;".toRegex(), "") // delete &-sequence
        title = title.replace("\\.$".toRegex(), "") // delete last dot
        title = title.replace("\r\n".toRegex(), "") // delete new line

        return Pair(urlImage, title)

    } catch (e: Exception) {
        log(e.printStackTrace().toString())
        return null
    }
}

private fun downloadImage(url: String, destination: String): Boolean {
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()
    try {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return false
        FileOutputStream(destination).use { output ->
            response.body?.byteStream()?.use { input ->
                input.copyTo(output)
            }
        }
        return true
    } catch (e: Exception) {
        log(e.printStackTrace().toString())
        return false
    }
}

////////////////////////////////////
// Edit image

private fun editImage(imagePath: String, title: String, screenWidth: Int, screenHeight: Int) {
    val file = File(imagePath)
    var image: BufferedImage = ImageIO.read(file)

    //val willCrop = !((image.width / screenWidth.toFloat()) < 1.4 && (image.height / screenHeight.toFloat()) > 1.5)
    val isHorizontal =
        ((image.width.toFloat() / image.height) > 1) && (image.width >= screenWidth) && (image.height >= screenHeight)
    val willCrop = Settings.cropVertical || isHorizontal
    if (willCrop) {
        image = scaleImageToFitScreen(image, screenWidth, screenHeight, true)
        image = cropImageToScreen(image, screenWidth, screenHeight)
    } else {
        image = scaleImageToFitScreen(image, screenWidth, screenHeight, false)
    }
    image = extendImageToFillScreen(image, screenWidth, screenHeight)
    if (Settings.drawText)
        image = addTextToImage(image, title)

    val outputFile = File(imagePath)
    ImageIO.write(image, "png", outputFile)
    States.title = title
    log(title)
}

private fun scaleImageToFitScreen(
    image: BufferedImage,
    screenWidth: Int,
    screenHeight: Int,
    willCrop: Boolean
): BufferedImage {
    val screenAspectRatio = screenWidth / screenHeight.toFloat()
    val originalWidth = image.width.toFloat()
    val originalHeight = image.height.toFloat()
    val imageAspectRatio = originalWidth / originalHeight
    val scaleFactor =
        if ((willCrop && screenAspectRatio < imageAspectRatio) || (!willCrop && imageAspectRatio < screenAspectRatio))
            screenHeight / originalHeight
        else screenWidth / originalWidth
    val newWidth = (originalWidth * scaleFactor).toInt()
    val newHeight = (originalHeight * scaleFactor).toInt()
    val scaledImage = image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)
    val bufferedImage = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
    val graphics = bufferedImage.createGraphics()
    graphics.drawImage(scaledImage, 0, 0, null)
    graphics.dispose()
    return bufferedImage
}

private fun cropImageToScreen(image: BufferedImage, screenWidth: Int, screenHeight: Int): BufferedImage {
    val x = if (image.width > screenWidth) image.width / 2 - screenWidth / 2 else 0
    val y = if (image.height > screenHeight) image.height / 2 - screenHeight / 2 else 0
    val width = if (image.width < screenWidth) image.width else screenWidth
    val height = if (image.height < screenHeight) image.height else screenHeight
    return cropImage(image, x, y, width, height)
}

private fun cropImage(image: BufferedImage, x: Int, y: Int, width: Int, height: Int): BufferedImage {
    return image.getSubimage(x, y, width, height)
}

private fun extendImageToFillScreen(image: BufferedImage, screenWidth: Int, screenHeight: Int): BufferedImage {
    val originalWidth = image.width
    val originalHeight = image.height
    return if (originalWidth < screenWidth || originalHeight < screenHeight) {
        val posX = screenWidth / 2 - originalWidth / 2
        val posY = screenHeight / 2 - originalHeight / 2
        val bufferedImage = BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = bufferedImage.createGraphics()
        // Average color
        var resR = 0L
        var resG = 0L
        var resB = 0L
        var color: Int
        var count = 0L
        if (originalHeight < screenHeight) {
            for (x in 0 until originalWidth) {
                color = image.getRGB(x, 0)
                resR += color and 0xff0000 shr 16
                resG += color and 0xff00 shr 8
                resB += color and 0xff
                count++
            }
            for (x in 0 until originalWidth) {
                color = image.getRGB(x, originalHeight - 1)
                resR += color and 0xff0000 shr 16
                resG += color and 0xff00 shr 8
                resB += color and 0xff
                count++
            }
        }
        if (originalWidth < screenWidth) {
            for (y in 0 until originalHeight) {
                color = image.getRGB(0, y)
                resR += color and 0xff0000 shr 16
                resG += color and 0xff00 shr 8
                resB += color and 0xff
                count++
            }
            for (y in 0 until originalHeight) {
                color = image.getRGB(originalWidth - 1, y)
                resR += color and 0xff0000 shr 16
                resG += color and 0xff00 shr 8
                resB += color and 0xff
                count++
            }
        }
        resR /= count
        resG /= count
        resB /= count
        //
        graphics.color = java.awt.Color(resR.toInt(), resG.toInt(), resB.toInt())
        graphics.fillRect(0, 0, screenWidth, screenHeight)
        graphics.drawImage(image, posX, posY, null)
        graphics.dispose()
        bufferedImage
    } else image
}

fun addTextToImage(image: BufferedImage, text: String): BufferedImage {
    if (text == "") return image
    val sizeFont = 20
    val graphics: Graphics = image.graphics
    graphics.color = java.awt.Color.RED
    graphics.font = Font("Arial", Font.BOLD, sizeFont)
    val fontMetrics: FontMetrics = graphics.fontMetrics

    var y = fontMetrics.height
    val tokens = text.split(" ")
    var curToken = 0
    while (curToken < tokens.size) {
        var subText = ""
        var subText2 = tokens[curToken]
        while (curToken < tokens.size && fontMetrics.stringWidth(subText2) <= image.width - 10) {
            subText = subText2
            curToken++
            if (curToken < tokens.size) subText2 = subText + " " + tokens[curToken]
        }
        if (subText == "") {
            subText = tokens[curToken]
            curToken++
        }
        val x = (image.width - fontMetrics.stringWidth(subText) - 10).let { if (it < 0) 0 else it }
        graphics.drawString(subText, x, y)
        y += sizeFont
    }
    graphics.dispose()
    return image
}


////////////////////////////////////
// Wallpaper

interface User32Extension : User32 {
    companion object {
        val INSTANCE: User32Extension =
            com.sun.jna.Native.load("user32", User32Extension::class.java, object : HashMap<String?, Any?>() {
                init {
                    put(Library.OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE)
                    put(Library.OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE)
                }
            })
    }

    @Suppress("FunctionName") // must be with upper case of first character
    fun SystemParametersInfo(
        uiAction: WinDef.UINT_PTR, uiParam: WinDef.UINT_PTR, pvParam: String?, fWinIni: WinDef.UINT_PTR
    ): Boolean
}

fun setWallpaper(imagePath: String) {
    User32Extension.INSTANCE.SystemParametersInfo(
        WinDef.UINT_PTR(0x0014), WinDef.UINT_PTR(0), imagePath, WinDef.UINT_PTR(0x01 or 0x02)
    )
}

////////////////////////////////////////////////////////
// Timer

@Composable
fun RunTimer() {
    LaunchedEffect(key1 = States.timerPause) {
        tick()
    }
}

suspend fun tick() {
    while (States.timeLeft > 0) {
        if (States.timerPause)
            break
        delay(1000)
        if (States.timeLeft > 0)
            States.timeLeft--
        if (States.timeLeft == 0) {
            States.timeLeft = Settings.hours * 60 * 60 + Settings.minutes * 60
            nextImage()
        }
    }
}

fun toStart() {
    States.timerPause = false
}

fun toPause() {
    States.timerPause = !States.timerPause
}

fun toStop() {
    States.timerPause = true
    States.timeLeft = Settings.hours * 60 * 60 + Settings.minutes * 60
}

////////////////////////////////////
// View model

object Settings {
    var hours: Int by mutableStateOf(0)
    var minutes: Int by mutableStateOf(1)
    var drawText: Boolean by mutableStateOf(false)
    var runOpen: Boolean by mutableStateOf(false)
    var cropVertical: Boolean by mutableStateOf(false)
    var startOnRun: Boolean by mutableStateOf(false)
}

object States {
    var isOpen: Boolean by mutableStateOf(true)
    var timeLeft: Int by mutableStateOf(600)
    var timerPause: Boolean by mutableStateOf(true)
    var loading: Boolean by mutableStateOf(false)
    var title: String by mutableStateOf("")
    val path = System.getProperty("user.home") + "/evaKotlin"

    init {
        val file = File(path)
        if (!file.exists())
            file.mkdir()
    }
}

////////////////////////////////////
// Main

fun main() = application {

    // Execute only on start
    remember {
        readSettings()
        States.isOpen = Settings.runOpen
        toStop()
        States.timerPause = !Settings.startOnRun
    }

    val exitFunction = remember { { toExit(::exitApplication) } }
    RunTimer()

    Tray(
        icon = if (States.loading) TrayIconLoading else TrayIconDefault,
        menu = {
            Item(
                "Open",
                onClick = { States.isOpen = true }
            )
            Item(
                "Next",
                onClick = { nextImage() }
            )
            Item(
                "Exit",
                onClick = {  }
            )
        },
        onAction = { States.isOpen = true }
    )

    if (States.isOpen) {
        Window(
            onCloseRequest = { States.isOpen = false },
            title = "Eva Kotlin",
            state = rememberWindowState(width = 500.dp, height = 500.dp)
        ) {
            app(exitFunction)
        }
    }
}

fun toExit(exitFunction: () -> Unit) {
    saveSettings()
    exitFunction()
}

fun saveSettings() {
    //val homeDir = System.getProperty("user.home")
    val pathToFile = "${States.path}/evaKotlin.ini"
    val mapSettings: Map<String, String> = mapOf(
        "hours" to Settings.hours.toString(),
        "minutes" to Settings.minutes.toString(),
        "drawText" to Settings.drawText.toString(),
        "runOpen" to Settings.runOpen.toString(),
        "cropVertical" to Settings.cropVertical.toString(),
        "startOnRun" to Settings.startOnRun.toString()
    )
    val jsonText = Json.encodeToString(mapSettings)
    File(pathToFile).writeText(jsonText)
}

fun readSettings() {
    println("read")
    //val homeDir = System.getProperty("user.home")
    val pathToFile = "${States.path}/evaKotlin.ini"
    val file = File(pathToFile)
    if (file.exists()) {
        val jsonText = file.readText()
        val mapSettings = try {
            Json.decodeFromString<Map<String, String>>(jsonText)
        } catch (e: SerializationException) {
            mapOf()
        }
        Settings.hours = mapSettings["hours"]?.let { (it as? String)?.toInt() } ?: 0
        Settings.minutes = mapSettings["minutes"]?.let { (it as? String)?.toInt() } ?: 15
        Settings.drawText = mapSettings["drawText"]?.let { (it as? String)?.toBoolean() } ?: false
        Settings.runOpen = mapSettings["runOpen"]?.let { (it as? String)?.toBoolean() } ?: true
        Settings.cropVertical = mapSettings["cropVertical"]?.let { (it as? String)?.toBoolean() } ?: false
        Settings.startOnRun = mapSettings["startOnRun"]?.let { (it as? String)?.toBoolean() } ?: false
    }
}

object TrayIconDefault : Painter() {
    override val intrinsicSize = Size(16f, 16f)
    override fun DrawScope.onDraw() {
        drawOval(Color(0xFF00A500))
    }
}

object TrayIconLoading : Painter() {
    override val intrinsicSize = Size(16f, 16f)
    override fun DrawScope.onDraw() {
        drawOval(Color(0xFFFFA500))
    }
}