package eu.jailbreaker.coloredchat

import net.md_5.bungee.api.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.PluginLoadOrder
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.java.annotation.command.Commands
import org.bukkit.plugin.java.annotation.plugin.*
import org.bukkit.plugin.java.annotation.plugin.author.Author
import java.awt.Color
import java.io.File
import java.nio.file.Paths
import java.util.logging.Level
import java.util.regex.Pattern

@Author("JailBreaker")
@ApiVersion(ApiVersion.Target.v1_15)
@LoadOrder(PluginLoadOrder.POSTWORLD)
@Website("https://jailbreaker.eu")
@Plugin(name = "ColoredChat", version = "0.0.1")
@Description("More beautiful chat messages and signs with ALL color codes")
@Commands(org.bukkit.plugin.java.annotation.command.Command(name = "colors", permission = "chat.colored.modify"))
class ColoredChat : JavaPlugin(), Listener, CommandExecutor {
    private lateinit var configFile: File
    private val colors: MutableList<ColorEntry> = mutableListOf()
    private val pattern = Pattern.compile("#[a-fA-F0-9]{6}")

    override fun onEnable() {
        saveDefaultConfig()
        configFile = Paths.get(dataFolder.toString(), "config.yml").toFile()
        server.pluginManager.registerEvents(this, this)
        val section = config.getConfigurationSection("colors") ?: return
        for (name in section.getKeys(false)) {
            val hex = section.getString("$name.hex") ?: continue
            val identifier = section.getString("$name.identifier") ?: continue
            colors.add(ColorEntry(name, identifier, hex))
            server.logger.log(Level.INFO, "registered color code $name")
        }
        server.logger.log(Level.INFO, "loaded ${colors.size} colors")
        getCommand("colors")?.setExecutor(this)
    }

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        if (!event.player.hasPermission("chat.colored")) return
        event.message = formatChat(event.message)
    }

    @EventHandler
    fun onSignChange(event: SignChangeEvent) {
        if (!event.player.hasPermission("chat.colored")) return
        event.getLine(0)?.let { event.setLine(0, formatChat(it)) }
        event.getLine(1)?.let { event.setLine(1, formatChat(it)) }
        event.getLine(2)?.let { event.setLine(2, formatChat(it)) }
        event.getLine(3)?.let { event.setLine(3, formatChat(it)) }
    }

    fun formatChat(message: String): String {
        var result = message
        colors.filter { result.contains(it.identifier) }.forEach { result = result.replace(it.identifier, it.hex) }
        var matcher = pattern.matcher(result)
        while (matcher.find()) {
            val group = matcher.group()
            val color = ChatColor.of(Color.decode(group))
            val before = result.substring(0, matcher.start())
            val after = result.substring(matcher.end())
            result = before + color + after
            matcher = pattern.matcher(result)
        }
        return ChatColor.translateAlternateColorCodes('&', result)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when {
            args.size == 1 && args[0].equals("list", true) -> colors.forEach {
                sender.sendMessage(
                    "[Colors] '${it.name}' - '${it.identifier}' - ${ChatColor.of(Color.decode(it.hex))}Placeholder"
                )
            }
            args.size == 2 && args[0].equals("remove", true) -> {
                colors.firstOrNull { it.name.equals(args[1], true) }?.let {
                    colors.remove(it)
                    config.set("colors.${it.name}", null)
                    config.save(configFile)
                    sender.sendMessage("[Colors] You have removed the color '${args[1]}'")
                } ?: sender.sendMessage("[Colors] The specified color does not exist")
            }
            args.size == 4 && args[0].equals("add", true) -> {
                val entry = ColorEntry(args[1], args[2], args[3])
                if (colors.add(entry)) {
                    config.set("colors.${entry.name}.identifier", entry.identifier)
                    config.set("colors.${entry.name}.hex", entry.hex)
                    config.save(configFile)
                    sender.sendMessage("[Colors] The color '${args[1]}' was added successfully")
                } else {
                    sender.sendMessage("[Colors] The specified color does already exist")
                }
            }
            else -> {
                sender.sendMessage("[Colors] /colors list")
                sender.sendMessage("[Colors] /colors remove <Name>")
                sender.sendMessage("[Colors] /colors add <Name> <Identifier> <Hex>")
            }
        }
        return true
    }
}

data class ColorEntry(val name: String, val identifier: String, val hex: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val color = other as? ColorEntry ?: return false
        return color.name == name
    }

    override fun hashCode(): Int = name.hashCode()
}