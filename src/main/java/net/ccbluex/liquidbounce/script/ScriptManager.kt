/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.script

import net.ccbluex.liquidbounce.file.FileManager.dir
import net.ccbluex.liquidbounce.script.remapper.Remapper
import net.ccbluex.liquidbounce.utils.ClientUtils.logger
import java.io.File
import java.io.FileFilter

object ScriptManager {

    val scripts = mutableListOf<Script>()

    val scriptsFolder = File(dir, "scripts")
    private const val scriptFileExtension = ".js"

    /**
     * Loads all scripts inside the scripts folder.
     */
    fun loadScripts() {
        if (!scriptsFolder.exists())
            scriptsFolder.mkdir()

        scriptsFolder.listFiles(FileFilter { it.name.endsWith(scriptFileExtension) })?.forEach(this@ScriptManager::loadScript)
    }

    /**
     * Unloads all scripts.
     */
    fun unloadScripts() = scripts.clear()

    /**
     * Loads a script from a file.
     */
    fun loadScript(scriptFile: File) {
        try {
            if (!Remapper.mappingsLoaded) {
                error("The mappings were not loaded, re-start and check your internet connection.")
            }

            val script = Script(scriptFile)
            script.initScript()
            scripts += script
        } catch (t: Throwable) {
            logger.error("[ScriptAPI] Failed to load script '${scriptFile.name}'.", t)
        }
    }

    /**
     * Enables all scripts.
     */
    fun enableScripts() = scripts.forEach { it.onEnable() }

    /**
     * Disables all scripts.
     */
    fun disableScripts() = scripts.forEach { it.onDisable() }

    /**
     * Imports a script.
     * @param file JavaScript file to be imported.
     */
    fun importScript(file: File) {
        val scriptFile = File(scriptsFolder, file.name)
        file.copyTo(scriptFile)

        loadScript(scriptFile)
        logger.info("[ScriptAPI] Successfully imported script '${scriptFile.name}'.")
    }

    /**
     * Deletes a script.
     * @param script Script to be deleted.
     */
    fun deleteScript(script: Script) {
        script.onDisable()
        scripts.remove(script)
        script.scriptFile.delete()

        logger.info("[ScriptAPI]  Successfully deleted script '${script.scriptFile.name}'.")
    }

    /**
     * Reloads all scripts.
     */
    fun reloadScripts() {
        disableScripts()
        unloadScripts()
        loadScripts()
        enableScripts()

        logger.info("[ScriptAPI]  Successfully reloaded scripts.")
    }
}