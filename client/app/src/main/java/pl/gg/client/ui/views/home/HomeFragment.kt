package pl.gg.client.ui.views.home

import android.view.MotionEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import pl.gg.client.R
import pl.gg.client.Config
import pl.gg.client.ui.BoldText
import pl.gg.client.ui.Title
import pl.gg.client.ui.base.ErrorAlertDialog
import pl.gg.client.ui.components.FullscreenProgressIndicator
import pl.gg.client.ui.functional.ClickMethod
import pl.gg.client.ui.functional.InterfaceScanner
import pl.gg.client.ui.functional.Motion
import pl.gg.client.ui.theme.*
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

class HomeViewModel : ViewModel(){

    var data by mutableStateOf<List<InterfaceScanner.NetworkResult>>(emptyList())
    private var port = 6886

    var connected by mutableStateOf(false)
    var isHostReachable by mutableStateOf(false)


    private var _inetServerAddress by mutableStateOf(Config.serverInetAddress)
    var inetServerAddress
        get() = _inetServerAddress
        set(value) {
            _inetServerAddress = value
            checkHost(value)
        }


    var progress by mutableStateOf(false)
    var hostsDialogVisibility by mutableStateOf(false)
    var availableHosts by mutableStateOf<List<String>>(emptyList())

    var ipRegex = Regex("\\b((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\\.|\$)){4}\\b")

    var errorDialogVisibility by mutableStateOf(false)
        private set
    var errorDialogText by mutableStateOf("")
        private set

    fun changeErrorDialog(visibility: Boolean, text: String = ""){
        errorDialogText = text
        errorDialogVisibility = visibility
    }

    init {
        data = InterfaceScanner.getNetworkInterfaces()
        checkHost()
    }

    fun checkHost(inetHostAddress: String? = inetServerAddress, callback: (Boolean) -> Unit = { serverReachableChange(it) }){
        inetHostAddress?.let {
            isHostReachable(it) {
                callback.invoke(it)
            }
        }
    }


    var resultCount = AtomicInteger()
    fun searchForHosts(callback: (List<String>) -> Unit){
        val availableHosts = mutableListOf<String>()
        for(i in 1..252){
            val host = "192.168.1.${i}"
            isHostReachable(host) {
                if (it) availableHosts.add(host)
                if(resultCount.incrementAndGet() >= 252){
                    callback.invoke(availableHosts)
                    resultCount.set(0)
                }
            }
        }
    }
    fun showHostDialog(hosts: List<String> = availableHosts) {
        availableHosts = hosts
        hostsDialogVisibility = true
    }

    fun serverReachableChange(value: Boolean) = viewModelScope.launch(Dispatchers.Main) {
        isHostReachable = value
        connected = value
    }

    private fun isHostReachable(address: String, callback: (Boolean) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val socket = Socket()
            val inetSocketAddress = InetSocketAddress(address, port)
            socket.connect(inetSocketAddress, 2000)
            callback.invoke(true)
        } catch (e: IOException) {
            //e.printStackTrace()
            callback.invoke(false)
        }
    }

    fun sendMsg(motion: Motion) = viewModelScope.launch(Dispatchers.IO) {
        if(!connected) return@launch
        try{
            val socket = Socket(inetServerAddress, port)
            val output = DataOutputStream(socket.getOutputStream())
            val data = motion.getMsg()
            output.writeUTF(data)
        } catch (e: Exception){
            connected = false
            e.printStackTrace()
        }
    }

}

@ExperimentalMaterialApi
@ExperimentalComposeUiApi
@Preview
@Composable
fun Home(viewModel: HomeViewModel = viewModel()){

    val scaffoldState = rememberBackdropScaffoldState(initialValue = if(viewModel.inetServerAddress.isNullOrEmpty()) BackdropValue.Revealed else BackdropValue.Concealed)

//    val scope = rememberCoroutineScope()
//    if(!viewModel.isHostReachable) scope. {
//        scaffoldState.reveal()
//    }

    ClieantSideTheme {
        Box(modifier = Modifier.fillMaxSize()){

            BackdropScaffold(
                scaffoldState = scaffoldState,
                gesturesEnabled = false,
                appBar = {
                    HomeAppBar(state = scaffoldState)
                },
                backLayerContent = {
                    HomeBackLayerContent()
                },
                frontLayerContent = {
                    HomeFrontLayer()
                })

            if(viewModel.progress){
                FullscreenProgressIndicator()
            }
            if(viewModel.hostsDialogVisibility){
                HostsDialog()
            }
        }
    }
}

@ExperimentalMaterialApi
@Composable
fun HomeBackLayerContent(viewModel: HomeViewModel = viewModel()){

    Column(modifier = Modifier.padding(top = 10.dp)) {

        var speed by remember { mutableStateOf(Config.moveSpeed) }
        var newHost by remember { mutableStateOf("") }

        val colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            activeTickColor = Color.White
        )

        val outlineColors = TextFieldDefaults.outlinedTextFieldColors(
            textColor = Color.White,
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color.White,
            cursorColor = Color.White,
            placeholderColor = Color.White
        )

        if(!viewModel.isHostReachable && !viewModel.inetServerAddress.isNullOrEmpty()){
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Card(modifier = Modifier
                    .padding(end = 10.dp)
                    .clickable {
                        viewModel.checkHost()
                    }, backgroundColor = Color.Transparent, elevation = 0.dp) {
                    Column(horizontalAlignment = Alignment.End) {
                        BoldText(text = "Host is not reachable",
                            textAlign = TextAlign.End,
                            color = DarkOrange,
                            modifier = Modifier)

                        BoldText(text = "Click here to try again",
                            textAlign = TextAlign.End,
                            color = DarkOrange,
                            modifier = Modifier)
                    }
                }
            }
        }

        if(viewModel.errorDialogVisibility){
            ErrorAlertDialog(title = "Error", text = viewModel.errorDialogText) { viewModel.changeErrorDialog(false) }
        }

        Title(name = "Change host", fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, start = 10.dp, bottom = 3.dp))


        var isNewHostValid by remember { mutableStateOf(true) }
        Row(modifier = Modifier
            .height(IntrinsicSize.Min)) {
            OutlinedTextField(
                value = newHost,
                onValueChange = {
                    newHost = it
                },
                isError = !isNewHostValid,
                placeholder = { Text("192.168.1.x") },
                modifier = Modifier.padding(start = 10.dp),
                colors = outlineColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Image(Icons.Default.Done, contentDescription = null, colorFilter = ColorFilter.tint(Color.White), modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .clickable {
                    isNewHostValid = viewModel.ipRegex.matches(newHost)
                    if (!isNewHostValid) return@clickable

                    viewModel.progress = true

                    viewModel.checkHost(newHost) {
                        if (it) viewModel.inetServerAddress = newHost
                        else viewModel.changeErrorDialog(true, "Host is unreachable")
                        viewModel.serverReachableChange(it)
                        viewModel.progress = false
                    }
                })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Card(modifier = Modifier
                .padding(end = 10.dp)
                .clickable {
                    viewModel.progress = true

                    viewModel.searchForHosts {
                        viewModel.progress = false
                        if(it.isEmpty()){
                            viewModel.changeErrorDialog(true, "There is no hosts available")
                            return@searchForHosts
                        }
                        viewModel.showHostDialog(it)
                    }
                }, backgroundColor = Color.Transparent, elevation = 0.dp) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Search available hosts",
                        textAlign = TextAlign.End,
                        color = Hyperlink,
                        modifier = Modifier)
                }
            }
        }


        Title(name = "Mouse speed: x${speed.toInt()}", fontSize = 14.sp, modifier = Modifier.padding(10.dp))
        Slider(value = speed,
            valueRange = 1f..5f,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp),
            steps = 3,
            onValueChange = {
                speed = it
            }, onValueChangeFinished = {
                Config.moveSpeed = speed
            },
            colors = colors
        )
    }

}

@ExperimentalMaterialApi
@Composable
fun HomeAppBar(viewModel: HomeViewModel = viewModel(), state: BackdropScaffoldState){

    val scope = rememberCoroutineScope()

    Row (modifier = Modifier
        .padding(10.dp)
        .height(IntrinsicSize.Min)) {

        Image(painterResource(id = R.drawable.ic_remote), null, modifier = Modifier
            .fillMaxHeight()
            .padding(5.dp), colorFilter = ColorFilter.tint(Color.White))

        Column(modifier = Modifier
            .padding(start = 10.dp)
            .fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            if (!viewModel.inetServerAddress.isNullOrEmpty()) BoldText(viewModel.inetServerAddress ?: "")
            if (viewModel.connected) Text("Connected", color = DarkGreen)
            else Text("Disconnected", color = DarkRed)
        }

        Image(painterResource(id = R.drawable.ic_settings), null, alignment = Alignment.TopEnd, modifier = Modifier
            .height(25.dp)
            .fillMaxWidth()
            .clickable {
                scope.launch {
                    if (state.isRevealed) {
                        state.conceal()
                    } else {
                        state.reveal()
                    }
                }

//                showSettingsDialog = true
            }, colorFilter = ColorFilter.tint(Color.White))
    }
}

@ExperimentalComposeUiApi
@Preview
@Composable
fun HomeFrontLayer(viewModel: HomeViewModel = viewModel()){
    Column(
        Modifier
            .fillMaxSize()
            .alpha(if (viewModel.connected) 1f else 0.5f)
            .padding(top = 10.dp)) {

        var start = 0f to 0f
        var lastMove = 0f to 0f

        if(!viewModel.connected){

        }

        Column(modifier = Modifier
            .fillMaxSize()
            .pointerInteropFilter {
                when (it.action) {
                    MotionEvent.ACTION_DOWN -> {
                        start = it.rawX to it.rawY
                        lastMove = it.rawX to it.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val move = it.rawX - lastMove.first to it.rawY - lastMove.second
                        lastMove = it.rawX to it.rawY
                        viewModel.sendMsg(Motion.MoveBy(move.first, move.second))
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isAClick(start, it.rawX to it.rawY)) viewModel.sendMsg(
                            Motion.Click(
                                ClickMethod.LEFT
                            )
                        )
                        start = 0f to 0f
                        lastMove = 0f to 0f
                    }
                    else -> return@pointerInteropFilter false
                }
                true
            }
            .padding(10.dp)){

            Card(backgroundColor = Color.Gray, modifier = Modifier
                .weight(1f)
                .fillMaxWidth()){

            }

            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
                Arrangement.SpaceBetween) {
                Button(
                    onClick = { /* viewModel.sendMsg(Motion.Click(ClickMethod.LEFT)) */ },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray),
                    modifier = Modifier
                        .weight(1f)
                        .pointerInteropFilter {
                            when (it.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    viewModel.sendMsg(Motion.Click(ClickMethod.LEFT_DOWN))
                                }
                                MotionEvent.ACTION_UP -> {
                                    viewModel.sendMsg(Motion.Click(ClickMethod.LEFT_UP))
                                }
                                else -> return@pointerInteropFilter false
                            }
                            true
                        }
                ){}

//                Spacer(modifier = Modifier.fillMaxWidth(0.2f))
                Button(
                    onClick = {viewModel.sendMsg(Motion.Click(ClickMethod.RIGHT))},
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray),
                    modifier = Modifier.weight(1f)
                ){}
            }

        }
    }
}

@Composable
fun HostsDialog(viewModel: HomeViewModel = viewModel()){
    Dialog(onDismissRequest = { viewModel.hostsDialogVisibility = false }) {
        Card (elevation = 0.dp, shape = RoundedCornerShape(10.dp), backgroundColor = Color.White) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                for (host in viewModel.availableHosts) {
                    Row(modifier = Modifier.clickable {
                        viewModel.inetServerAddress = host
                        viewModel.hostsDialogVisibility = false
                    }) {
                        Text(text = host, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "Select"
                        )
                    }

                }
            }
        }
    }
}

private fun isAClick(start: Pair<Float, Float>, end: Pair<Float, Float>): Boolean {
    val differenceX = Math.abs(start.first - end.first)
    val differenceY = Math.abs(start.second - end.second)
    return !(differenceX > 5 /* =5 */ || differenceY > 5)
}