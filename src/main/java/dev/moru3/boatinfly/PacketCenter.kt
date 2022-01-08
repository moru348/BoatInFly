package dev.moru3.boatinfly

import com.google.gson.Gson
import dev.moru3.boatinfly.BoatInFly.Companion.test
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.concurrent.atomic.AtomicBoolean

class PacketCenter(private val main: BoatInFly): Listener {
    private val registerListeners = mutableMapOf<Player, MutableMap<String, MutableList<(Player, Any, AtomicBoolean)->Unit>>>()
    fun registerListener(id: String, player: Player, runnable: (Player, Any, AtomicBoolean) -> Unit) {
        if(registerListeners[player]==null) {
            newPipeline(player)
        }
        registerListeners[player] = (registerListeners[player]?:mutableMapOf()).also {
            it[id] = (it[id]?:mutableListOf()).apply { add(runnable) }
        }
    }
    val gson = Gson()
    private fun newPipeline(player: Player) {
        val channelDuplexHandler = object : ChannelDuplexHandler() {
            override fun channelRead(channelHandlerContext: ChannelHandlerContext, packet: Any) {
                val isCanceled = AtomicBoolean(false)
                registerListeners[player]?.values?.forEach s@{ list ->
                    if(isCanceled.get()) { return@s };list.forEach { runnable -> if(isCanceled.get()) { return@s };runnable.invoke(player, packet, isCanceled) } }
                if(isCanceled.get()) { return }
                super.channelRead(channelHandlerContext, packet)
            }

            override fun write(ctx: ChannelHandlerContext?, msg: Any?, promise: ChannelPromise?) {
                if(player.name=="moru3_48") {
                    if(!msg.toString().startsWith("net.minecraft.server.v1_16_R3.PacketPlayOutChat")) {
                        test--
                        if (test >= 0) {
                            Bukkit.broadcastMessage(msg.toString())
                        }
                    }
                }
                super.write(ctx, msg, promise)
            }
        }
        player.javaClass.getMethod("getHandle").invoke(player)
            .run { this::class.java.getField("playerConnection").also { it.isAccessible = true}.get(this) }
            .run { this::class.java.getField("networkManager").also { it.isAccessible = true}.get(this) }
            .run { this::class.java.getField("channel").also { it.isAccessible = true}.get(this) }
            .run { this::class.java.getMethod("pipeline").also { it.isAccessible = true}.invoke(this) }
            .also {
                if(it::class.java.getMethod("get", String::class.java)
                        .also { it.isAccessible = true}.invoke(it, "${player.name}-runformoney_reloaded")!=null) {
                    it::class.java.getMethod("remove", String::class.java).invoke(it, "${player.name}-runformoney_reloaded")
                }
            }.run {
                this::class.java.getMethod("addBefore", String::class.java, String::class.java, ChannelHandler::class.java)
                    .also { it.isAccessible = true}
                    .invoke(this,"packet_handler", "${player.name}-runformoney_reloaded", channelDuplexHandler)
            }
    }
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        newPipeline(event.player)
    }
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        removeAllListeners(event.player)
    }
    fun removeListener(id: String, player: Player) {
        registerListeners[player]?.remove(id)
    }
    fun removeAllListeners(player: Player) {
        registerListeners.remove(player)
    }
    init {
        Bukkit.getPluginManager().registerEvents(this, main)
        Bukkit.getOnlinePlayers().forEach {
            newPipeline(it)
        }
    }
}