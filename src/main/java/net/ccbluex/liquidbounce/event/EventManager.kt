/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.event
import net.ccbluex.liquidbounce.utils.ClientUtils.logger

object EventManager {

    private val registry = hashMapOf<Class<out Event>, MutableList<EventHook>>()

    /**
     * Register [listener]
     */
    fun registerListener(listener: Listenable) =
        listener.javaClass.declaredMethods.forEach { method ->
            if (method.isAnnotationPresent(EventTarget::class.java) && method.parameterTypes.size == 1) {
                if (!method.isAccessible)
                    method.isAccessible = true

                val eventClass = method.parameterTypes[0] as Class<out Event>
                val eventTarget = method.getAnnotation(EventTarget::class.java)

                val invokableEventTargets = registry.getOrDefault(eventClass, ArrayList())
                invokableEventTargets += EventHook(listener, method, eventTarget)
                registry[eventClass] = invokableEventTargets.sortedByDescending { it.priority }.toMutableList()
            }
        }

    /**
     * Unregister listener
     *
     * @param listenable for unregister
     */
    fun unregisterListener(listenable: Listenable) =
        registry.forEach { (_, targets) ->
            targets.removeIf { it.eventClass == listenable }
        }

    /**
     * Call event to listeners
     *
     * @param event to call
     */
     fun callEvent(event: Event) {
         val targets = registry[event.javaClass] ?: return

         for (invokableEventTarget in targets) {
             try {
                if (!invokableEventTarget.eventClass.handleEvents() && !invokableEventTarget.ignoreCondition)
                    continue

                invokableEventTarget.method.invoke(invokableEventTarget.eventClass, event)
            } catch (throwable: Throwable) {
                val className = invokableEventTarget.eventClass::class.java.simpleName
                val methodName = invokableEventTarget.method.name
                logger.error("Error in $className.$methodName handling ${event.javaClass.simpleName}", throwable)
            }

         }
     }

}
