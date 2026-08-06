package kr.ac.sunmoon.hunminjeongeum_server.logic

import java.util.concurrent.CopyOnWriteArrayList

class Room(

) {
    private val clients = CopyOnWriteArrayList<ClientConnection>()

    fun getSize(): Int{
        return clients.size
    }

    fun add(client: ClientConnection){
        clients.add(client)
    }

    fun getClients(): CopyOnWriteArrayList<ClientConnection>{
        return clients
    }

    fun remove(client: ClientConnection){
        clients.remove(client)
    }


}
