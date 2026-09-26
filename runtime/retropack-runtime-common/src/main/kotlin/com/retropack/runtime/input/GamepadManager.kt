package com.retropack.runtime.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent

/**
 * Controller hardware detection and discovery manager for RetroPack.
 *
 * Implements Feature 4 specifications:
 * - Listens for physical Bluetooth/USB HID gamepad connections and disconnections.
 * - Categorizes and profiles connected hardware (Xbox, PlayStation DualSense/DualShock,
 *   Nintendo Switch Pro / Joy-Cons, 8BitDo, Razer Kishi).
 */
class GamepadManager(private val context: Context) : InputManager.InputDeviceListener {

    enum class ControllerType {
        XBOX,
        PLAYSTATION,
        NINTENDO_SWITCH,
        EIGHT_BIT_DO,
        RAZER_KISHI,
        GENERIC_GAMEPAD,
        KEYBOARD
    }

    data class DiscoveredGamepad(
        val deviceId: Int,
        val name: String,
        val descriptor: String,
        val type: ControllerType,
        val hasAnalogSticks: Boolean,
        val hasTriggers: Boolean,
        val vendorId: Int = 0,
        val productId: Int = 0
    )

    private val inputManager: InputManager? by lazy {
        context.getSystemService(Context.INPUT_SERVICE) as? InputManager
    }

    var onControllersChanged: ((List<DiscoveredGamepad>) -> Unit)? = null
    var onControllerConnected: ((DiscoveredGamepad) -> Unit)? = null
    var onControllerDisconnected: ((Int) -> Unit)? = null

    private val connectedGamepads = mutableMapOf<Int, DiscoveredGamepad>()

    fun start() {
        inputManager?.registerInputDeviceListener(this, null)
        refreshConnectedControllers()
    }

    fun stop() {
        inputManager?.unregisterInputDeviceListener(this)
        connectedGamepads.clear()
    }

    fun getConnectedGamepads(): List<DiscoveredGamepad> {
        refreshConnectedControllers()
        return connectedGamepads.values.toList()
    }

    fun refreshConnectedControllers() {
        val manager = inputManager ?: return
        val currentIds = manager.inputDeviceIds
        val activeIds = mutableSetOf<Int>()

        for (id in currentIds) {
            val device = manager.getInputDevice(id) ?: continue
            if (isGamepadOrJoystick(device)) {
                activeIds.add(id)
                if (!connectedGamepads.containsKey(id)) {
                    val gamepad = inspectDevice(device)
                    connectedGamepads[id] = gamepad
                    onControllerConnected?.invoke(gamepad)
                }
            }
        }

        // Remove disconnected
        val removed = connectedGamepads.keys.filter { it !in activeIds }
        for (id in removed) {
            connectedGamepads.remove(id)
            onControllerDisconnected?.invoke(id)
        }

        onControllersChanged?.invoke(connectedGamepads.values.toList())
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        val device = inputManager?.getInputDevice(deviceId) ?: return
        if (isGamepadOrJoystick(device)) {
            val gamepad = inspectDevice(device)
            connectedGamepads[deviceId] = gamepad
            onControllerConnected?.invoke(gamepad)
            onControllersChanged?.invoke(connectedGamepads.values.toList())
        }
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        if (connectedGamepads.containsKey(deviceId)) {
            connectedGamepads.remove(deviceId)
            onControllerDisconnected?.invoke(deviceId)
            onControllersChanged?.invoke(connectedGamepads.values.toList())
        }
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        val device = inputManager?.getInputDevice(deviceId) ?: return
        if (isGamepadOrJoystick(device)) {
            val gamepad = inspectDevice(device)
            connectedGamepads[deviceId] = gamepad
            onControllersChanged?.invoke(connectedGamepads.values.toList())
        }
    }

    private fun isGamepadOrJoystick(device: InputDevice): Boolean {
        val sources = device.sources
        val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        val isJoystick = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        val isDpad = (sources and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
        return !device.isVirtual && (isGamepad || isJoystick || isDpad)
    }

    private fun inspectDevice(device: InputDevice): DiscoveredGamepad {
        val name = device.name ?: "Unknown Controller"
        val descriptor = device.descriptor ?: "gamepad_${device.id}"
        val type = classifyController(name)

        var hasAnalog = false
        var hasTriggers = false

        val motionRanges = device.motionRanges
        for (range in motionRanges) {
            val axis = range.axis
            if (axis == MotionEvent.AXIS_X || axis == MotionEvent.AXIS_Y ||
                axis == MotionEvent.AXIS_Z || axis == MotionEvent.AXIS_RZ) {
                hasAnalog = true
            }
            if (axis == MotionEvent.AXIS_LTRIGGER || axis == MotionEvent.AXIS_RTRIGGER ||
                axis == MotionEvent.AXIS_BRAKE || axis == MotionEvent.AXIS_GAS) {
                hasTriggers = true
            }
        }

        val vendorId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) device.vendorId else 0
        val productId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) device.productId else 0

        return DiscoveredGamepad(
            deviceId = device.id,
            name = name,
            descriptor = descriptor,
            type = type,
            hasAnalogSticks = hasAnalog,
            hasTriggers = hasTriggers,
            vendorId = vendorId,
            productId = productId
        )
    }

    companion object {
        fun classifyController(name: String): ControllerType {
            val lower = name.lowercase()
            return when {
                lower.contains("xbox") || lower.contains("microsoft") -> ControllerType.XBOX
                lower.contains("dualsense") || lower.contains("dualshock") || lower.contains("playstation") || lower.contains("sony") -> ControllerType.PLAYSTATION
                lower.contains("pro controller") || lower.contains("joy-con") || lower.contains("nintendo") -> ControllerType.NINTENDO_SWITCH
                lower.contains("8bitdo") -> ControllerType.EIGHT_BIT_DO
                lower.contains("razer") || lower.contains("kishi") -> ControllerType.RAZER_KISHI
                lower.contains("keyboard") -> ControllerType.KEYBOARD
                else -> ControllerType.GENERIC_GAMEPAD
            }
        }
    }
}
