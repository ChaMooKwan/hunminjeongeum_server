package kr.ac.sunmoon.hunminjeongeum_server

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.ac.sunmoon.hunminjeongeum_server.logic.ServerConnection

@Composable
fun App() {
    //input/mainScreen
    inputPort(
        onConfirm = { port ->
            ServerConnection(port = port, maxClients = 7).start()
        }
    )
}

@Composable
fun inputPort(
    onConfirm: (Int) -> Unit
) {
    var port by remember { mutableStateOf("")}
    Column(
        modifier = Modifier.padding(16.dp),
    ){
        TextField(
            value = port,
            onValueChange = {
                port = it
            },
            label = { Text("포트 번호") }
        )
        Button(onClick = {
            port.toIntOrNull()?. let {onConfirm(it)}
        }){
            Text("확인")
        }
    }

}
