package eu.jailbreaker.coloredchat

import net.md_5.bungee.api.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
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
import kotlin.math.abs
import kotlin.math.roundToInt

private val colors: MutableList<ColorEntry> = mutableListOf()
private val gradients: MutableList<GradientEntry> = mutableListOf()

private val hexPattern = Pattern.compile("#[a-fA-F0-9]{6}")
private val colorPattern = Pattern.compile("&[a-zA-Z0-9]{2}")
private val gradientPattern = Pattern.compile("~[a-zA-Z0-9]{2}")

@Author("JailBreaker")
@ApiVersion(ApiVersion.Target.v1_15)
@LoadOrder(PluginLoadOrder.POSTWORLD)
@Website("https://jailbreaker.eu")
@Plugin(name = "ColoredChat", version = "0.0.2")
@Description("More beautiful chat messages and signs with ALL color codes")
@Commands(org.bukkit.plugin.java.annotation.command.Command(name = "coloredchat", permission = "chat.colored.modify"))
class ColoredChat : JavaPlugin(), Listener {
    internal lateinit var configFile: File

    override fun onEnable() {
        saveDefaultConfig()
        configFile = Paths.get(dataFolder.toString(), "config.yml").toFile()
        loadColors()
        loadGradients()
        server.pluginManager.registerEvents(this, this)
        getCommand("coloredchat")?.setExecutor(ColoredChatCommand(this))
    }

    private fun loadGradients() {
        val section = config.getConfigurationSection("gradients") ?: return
        for (name in section.getKeys(false)) {
            val fromHex = section.getString("$name.fromHex") ?: continue
            val toHex = section.getString("$name.toHex") ?: continue
            if (!hexPattern.matcher(fromHex).find() || !hexPattern.matcher(toHex).find()) {
                throw IllegalArgumentException("Wrong hex pattern for gradient $name.")
            }
            val identifier = section.getString("$name.identifier") ?: continue
            if (!gradientPattern.matcher(identifier).find()) {
                throw IllegalArgumentException("Wrong gradient pattern for $name.")
            }
            if (gradients.any { it.name.equals(name, true) }) {
                throw AlreadyExistsException("Gradient with name $name already exists")
            }
            gradients.add(GradientEntry(name, identifier, fromHex, toHex))
            server.logger.log(Level.INFO, "registered gradient $name")
        }
        server.logger.log(Level.INFO, "loaded ${gradients.size} gradients")
    }

    private fun loadColors() {
        val section = config.getConfigurationSection("colors") ?: return
        for (name in section.getKeys(false)) {
            val hex = section.getString("$name.hex") ?: continue
            if (!hexPattern.matcher(hex).find()) {
                throw IllegalArgumentException("Wrong hex pattern for color $name.")
            }
            val identifier = section.getString("$name.identifier") ?: continue
            if (!colorPattern.matcher(identifier).find()) {
                throw IllegalArgumentException("Wrong color pattern for $name.")
            }
            if (colors.any { it.name.equals(name, true) }) {
                throw AlreadyExistsException("Color with name $name already exists")
            }
            colors.add(ColorEntry(name, identifier, hex))
            server.logger.log(Level.INFO, "registered color code $name")
        }
        server.logger.log(Level.INFO, "loaded ${colors.size} colors")
    }

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        event.message = ColorFormatter.formatAll(event.message)
    }

    @EventHandler
    fun onSignChange(event: SignChangeEvent) {
        event.getLine(0)?.let { event.setLine(0, ColorFormatter.formatAll(it)) }
        event.getLine(1)?.let { event.setLine(1, ColorFormatter.formatAll(it)) }
        event.getLine(2)?.let { event.setLine(2, ColorFormatter.formatAll(it)) }
        event.getLine(3)?.let { event.setLine(3, ColorFormatter.formatAll(it)) }
    }

    private fun hasColorPermission(formatter: Player, color: ColorEntry): Boolean =
        formatter.hasPermission("chat.color.*") || formatter.hasPermission("chat.color.${color.name}")

    private fun hasGradientPermission(formatter: Player, gradient: GradientEntry): Boolean =
        formatter.hasPermission("chat.color.*") || formatter.hasPermission("chat.gradient.${gradient.name}")
}

class AlreadyExistsException(message: String) : Exception(message)

data class ColorEntry(
    @JvmField
    val name: String,
    @JvmField
    val identifier: String,
    @JvmField
    val hex: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val color = other as? ColorEntry ?: return false
        return color.name == name
    }

    override fun hashCode(): Int = name.hashCode()
}

data class GradientEntry(
    @JvmField
    val name: String,
    @JvmField
    val identifier: String,
    @JvmField
    val fromHex: String,
    @JvmField
    val toHex: String
) {
    fun format(
        content: String,
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false,
        strikethrough: Boolean = false,
        magic: Boolean = false
    ): String {
        var result = content
        org.bukkit.ChatColor.values().filter { it.isFormat }.forEach { result = result.replace(it.toString(), "") }

        val length = result.length
        val fromRGB = Color.decode(fromHex)
        val toRGB = Color.decode(toHex)

        var rStep = abs((fromRGB.red - toRGB.red).toDouble() / length)
        var gStep = abs((fromRGB.green - toRGB.green).toDouble() / length)
        var bStep = abs((fromRGB.blue - toRGB.blue).toDouble() / length)

        if (fromRGB.red > toRGB.red) {
            rStep = -rStep
        }
        if (fromRGB.green > toRGB.green) {
            gStep = -gStep
        }
        if (fromRGB.blue > toRGB.blue) {
            bStep = -bStep
        }

        var finalColor = Color(fromRGB.rgb)
        result = result.replace("", "<§>")

        for (index in 0..length) {
            val red = normalizePart((finalColor.red + rStep).roundToInt())
            val green = normalizePart((finalColor.green + gStep).roundToInt())
            val blue = normalizePart((finalColor.blue + bStep).roundToInt())

            finalColor = Color(red, green, blue)

            var formats = ""
            if (bold) {
                formats += ChatColor.BOLD
            }
            if (italic) {
                formats += ChatColor.ITALIC
            }
            if (underline) {
                formats += ChatColor.UNDERLINE
            }
            if (strikethrough) {
                formats += ChatColor.STRIKETHROUGH
            }
            if (magic) {
                formats += ChatColor.MAGIC
            }
            result = result.replaceFirst("<§>".toRegex(), "${ChatColor.of(finalColor)}$formats")
        }
        return result
    }

    private fun normalizePart(input: Int): Int = if (input > 255) 255 else if (input < 0) 0 else input

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val gradient = other as? GradientEntry ?: return false
        return gradient.name == name
    }

    override fun hashCode(): Int = name.hashCode()
}

object ColorFormatter {
    @JvmStatic
    fun formatAll(message: String): String {
        val result = formatHex(formatColor(formatGradient(message)))
        return ChatColor.translateAlternateColorCodes('&', result)
    }

    @JvmStatic
    fun formatGradient(message: String): String {
        var result = message
        var matcher = gradientPattern.matcher(result)
        while (matcher.find()) {
            val group = matcher.group()
            val entry = gradients.firstOrNull { it.identifier.equals(group, true) } ?: continue
            val before = result.substring(0, matcher.start())
            val after = result.substring(matcher.end())
            result = before + entry.format(after)
            matcher = gradientPattern.matcher(result)
        }
        return result
    }

    @JvmStatic
    fun formatColor(message: String): String {
        var result = message
        var matcher = colorPattern.matcher(result)
        while (matcher.find()) {
            val group = matcher.group()
            val entry = colors.firstOrNull { it.identifier.equals(group, true) } ?: continue
            val color = ChatColor.of(Color.decode(entry.hex))
            val before = result.substring(0, matcher.start())
            val after = result.substring(matcher.end())
            result = before + color + after
            matcher = colorPattern.matcher(result)
        }
        return result
    }

    @JvmStatic
    fun formatHex(message: String): String {
        var result = message
        var matcher = hexPattern.matcher(result)
        while (matcher.find()) {
            val group = matcher.group()
            val color = ChatColor.of(Color.decode(group))
            val before = result.substring(0, matcher.start())
            val after = result.substring(matcher.end())
            result = before + color + after
            matcher = hexPattern.matcher(result)
        }
        return result
    }
}

class ColoredChatCommand(val plugin: ColoredChat) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when {
            args.size == 1 && args[0].equals("listColors", true) -> {
                colors.forEach {
                    sender.sendMessage(
                        "[Colors] '${it.name}' - '${it.identifier}' - ${ChatColor.of(Color.decode(it.hex))}Placeholder"
                    )
                }
            }
            args.size == 2 && args[0].equals("removeColor", true) -> {
                colors.firstOrNull { it.name.equals(args[1], true) }?.let {
                    colors.remove(it)
                    plugin.config.set("colors.${it.name}", null)
                    plugin.config.save(plugin.configFile)
                    sender.sendMessage("[Colors] You have removed the color '${args[1]}'")
                } ?: sender.sendMessage("[Colors] The specified color does not exist")
            }
            args.size == 4 && args[0].equals("addColor", true) -> {
                val entry = ColorEntry(args[1], args[2], args[3])
                if (colors.add(entry)) {
                    plugin.config.set("colors.${entry.name}.identifier", entry.identifier)
                    plugin.config.set("colors.${entry.name}.hex", entry.hex)
                    plugin.config.save(plugin.configFile)
                    sender.sendMessage("[Colors] The color '${args[1]}' was added successfully")
                } else {
                    sender.sendMessage("[Colors] The specified color does already exist")
                }
            }
            args.size == 1 && args[0].equals("listGradients", true) -> {
                gradients.forEach {
                    sender.sendMessage(
                        "[Gradients] '${it.name}' - '${it.identifier}' - ${it.format("PlaceHolder Text")}"
                    )
                }
            }
            args.size == 2 && args[0].equals("removeGradient", true) -> {
                gradients.firstOrNull { it.name.equals(args[1], true) }?.let {
                    gradients.remove(it)
                    plugin.config.set("gradients.${it.name}", null)
                    plugin.config.save(plugin.configFile)
                    sender.sendMessage("[Gradients] You have removed the color '${args[1]}'")
                } ?: sender.sendMessage("[Gradients] The specified color does not exist")
            }
            args.size == 5 && args[0].equals("addGradient", true) -> {
                val entry = GradientEntry(args[1], args[2], args[3], args[4])
                if (gradients.add(entry)) {
                    plugin.config.set("gradients.${entry.name}.identifier", entry.identifier)
                    plugin.config.set("gradients.${entry.name}.fromHex", entry.fromHex)
                    plugin.config.set("gradients.${entry.name}.toHex", entry.toHex)
                    plugin.config.save(plugin.configFile)
                    sender.sendMessage("[Gradients] The gradient '${args[1]}' was added successfully")
                } else {
                    sender.sendMessage("[Gradients] The specified gradient does already exist")
                }
            }
            else -> {
                sender.sendMessage("[Colors] /coloredchat listColors")
                sender.sendMessage("[Colors] /coloredchat removeColor <Name>")
                sender.sendMessage("[Colors] /coloredchat addColor <Name> <Identifier> <Hex>")

                sender.sendMessage("[Colors] /coloredchat listGradients")
                sender.sendMessage("[Colors] /coloredchat removeGradient <Name>")
                sender.sendMessage("[Colors] /coloredchat addGradient <Name> <Identifier> <FromHex> <ToHex>")
            }
        }
        return true
    }
}