package functional.sockets

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import functional.MouseAction
import functional.MouseClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.impl.Log
import java.awt.MouseInfo
import java.awt.Robot
import java.awt.event.MouseEvent
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.lang.Exception
import java.net.ServerSocket
import java.net.Socket

class MouseSocketsServer(
    val handler : MouseSocketsHandler
) {

    private val port = 6886
    private val server = ServerSocket(port)

    suspend fun runServer() = withContext(Dispatchers.IO) {
        Log.info("Server: " + server.inetAddress.toString() + " " + server.localSocketAddress)
        while(true){
            try{
                Log.info("Waiting for message")
                val socket = server.accept()
                val data = DataInputStream(BufferedInputStream(socket.getInputStream()))
                val message = data.readUTF()
                Log.info("Message appear: " + message)
                handler.onNewMessage(message)
                handleMessage(message)

            }catch (e : Exception){
                Log.error(e.message ?: "Socket Error")
            }
        }
    }

    data class Point(
        @SerializedName("x")
        val x: Float,
        @SerializedName("y")
        val y: Float
    )
    private val gson = Gson()
    private fun handleMessage(data: String) {
        val action = when(data){
            MouseClick.LEFT.msg -> MouseAction.Click(MouseClick.LEFT)
            MouseClick.RIGHT.msg -> MouseAction.Click(MouseClick.RIGHT)
            else -> {
                val point = gson.fromJson<Point>(data, object : TypeToken<Point>(){}.type)
                MouseAction.MoveBy(point.x, point.y)
            }
        }
        handleAction(action)
    }


    private val robot = Robot()
    private fun moveMouse(x: Float, y: Float){
        val actualPosition = MouseInfo.getPointerInfo().location
        val nextPosition = Point(actualPosition.x + x, actualPosition.y + y)
        robot.mouseMove(nextPosition.x.toInt(), nextPosition.y.toInt())
    }
    private fun mouseClick(mouseClick: MouseClick){
        when(mouseClick){
            MouseClick.RIGHT -> {
                robot.mousePress(MouseEvent.BUTTON3_DOWN_MASK)
                robot.mouseRelease(MouseEvent.BUTTON3_DOWN_MASK)
            }
            MouseClick.LEFT -> {
                robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK)
                robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK)
            }
        }
    }

    private fun handleAction(action: MouseAction){
        when(action){
            is MouseAction.Click -> mouseClick(action.action)
            is MouseAction.MoveBy -> moveMouse(action.x, action.y)
        }
    }

    var count = 0

    suspend fun sendTestMessage(serverInetAddress: String) = withContext(Dispatchers.IO) {
        try{
            val socket = Socket(serverInetAddress, port)
            val output = DataOutputStream(socket.getOutputStream())
            val data = "Test ${count++}"
            output.writeUTF(data)
        } catch (e: Exception){
            Log.error(e.message ?: "Send Message Error")
        }
    }

}