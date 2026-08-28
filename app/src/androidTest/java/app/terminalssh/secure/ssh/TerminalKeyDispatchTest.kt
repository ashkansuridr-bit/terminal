package app.terminalssh.secure.ssh

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import org.connectbot.terminal.VTermKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalKeyDispatchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val output = CopyOnWriteArrayList<ByteArray>()
    private val outputLatch = AtomicReference<CountDownLatch?>()
    private lateinit var emulator: TerminalEmulator

    @Before
    fun setUp() {
        instrumentation.runOnMainSync {
            emulator = TerminalEmulatorFactory.create(
                initialRows = 24,
                initialCols = 80,
                onKeyboardInput = {
                    output += it.copyOf()
                    outputLatch.get()?.countDown()
                },
                onResize = {},
                onClipboardCopy = {},
            )
        }
    }

    @Test
    fun controlLettersReachThePtyAsAsciiControlBytes() {
        val expected = mapOf(
            'C' to 0x03,
            'D' to 0x04,
            'L' to 0x0C,
            'A' to 0x01,
            'E' to 0x05,
            'Z' to 0x1A,
        )

        expected.forEach { (letter, byte) ->
            assertArrayEquals(byteArrayOf(byte.toByte()), dispatchCharacter(letter.lowercaseChar(), CTRL))
        }
    }

    @Test
    fun altAndCombinedModifiersReachThePty() {
        assertArrayEquals(byteArrayOf(ESC, 'x'.code.toByte()), dispatchCharacter('x', ALT))
        assertArrayEquals(byteArrayOf(ESC, 0x03), dispatchCharacter('c', CTRL or ALT))
        assertArrayEquals(byteArrayOf(ESC, 0x03), dispatchCharacter('c', CTRL or ALT or SHIFT))
    }

    @Test
    fun softKeyboardCtrlAndAltUseAndClearLatchedModifiers() {
        val modifiers = TerminalInputController()
        val keyboard = keyboardHandler(modifiers)

        modifiers.toggle(TerminalModifier.CTRL)
        assertArrayEquals(byteArrayOf(0x03), captureOutput {
            keyboard.type("c")
        })
        assertEquals(TerminalModifiers(), modifiers.modifiers.value)

        modifiers.toggle(TerminalModifier.ALT)
        assertArrayEquals(byteArrayOf(ESC, 'x'.code.toByte()), captureOutput {
            keyboard.type("x")
        })
        assertEquals(TerminalModifiers(), modifiers.modifiers.value)

    }

    @Test
    fun tabEscapeAndArrowKeysUseTerminalSequences() {
        assertArrayEquals(byteArrayOf(0x09), dispatchKey(VTermKey.TAB))
        assertArrayEquals(byteArrayOf(ESC), dispatchKey(VTermKey.ESCAPE))
        assertArrayEquals("\u001B[A".encodeToByteArray(), dispatchKey(VTermKey.UP))
        assertArrayEquals("\u001B[B".encodeToByteArray(), dispatchKey(VTermKey.DOWN))
        assertArrayEquals("\u001B[D".encodeToByteArray(), dispatchKey(VTermKey.LEFT))
        assertArrayEquals("\u001B[C".encodeToByteArray(), dispatchKey(VTermKey.RIGHT))
    }

    @Test
    fun modifiedArrowsUseXtermModifierParameters() {
        assertArrayEquals("\u001B[1;2A".encodeToByteArray(), dispatchKey(VTermKey.UP, SHIFT))
        assertArrayEquals("\u001B[1;3A".encodeToByteArray(), dispatchKey(VTermKey.UP, ALT))
        assertArrayEquals("\u001B[1;5A".encodeToByteArray(), dispatchKey(VTermKey.UP, CTRL))
        assertArrayEquals("\u001B[1;7A".encodeToByteArray(), dispatchKey(VTermKey.UP, CTRL or ALT))
        assertArrayEquals("\u001B[1;8A".encodeToByteArray(), dispatchKey(VTermKey.UP, CTRL or ALT or SHIFT))
    }

    @Test
    fun arrowsHonorApplicationCursorMode() {
        writeRemote("\u001B[?1h")
        assertArrayEquals("\u001BOA".encodeToByteArray(), dispatchKey(VTermKey.UP))
        assertArrayEquals("\u001BOH".encodeToByteArray(), dispatchKey(VTermKey.HOME))

        writeRemote("\u001B[?1l")
        assertArrayEquals("\u001B[A".encodeToByteArray(), dispatchKey(VTermKey.UP))
        assertArrayEquals("\u001B[H".encodeToByteArray(), dispatchKey(VTermKey.HOME))
    }

    @Test
    fun terminalProtocolRepliesDoNotConsumeToolbarModifiers() {
        val modifiers = TerminalInputController()
        modifiers.toggle(TerminalModifier.CTRL)
        writeRemote("\u001B[5n")
        assertEquals(TerminalModifiers(ctrl = true), modifiers.modifiers.value)
    }

    private fun dispatchKey(key: Int, modifiers: Int = 0): ByteArray = captureOutput {
        emulator.dispatchKey(modifiers, key)
    }

    private fun dispatchCharacter(character: Char, modifiers: Int): ByteArray = captureOutput {
        emulator.dispatchCharacter(modifiers, character.code)
    }

    private fun writeRemote(sequence: String) {
        val bytes = sequence.encodeToByteArray()
        instrumentation.runOnMainSync { emulator.writeInput(bytes, 0, bytes.size) }
    }

    private fun captureOutput(action: () -> Unit): ByteArray {
        output.clear()
        val latch = CountDownLatch(1)
        outputLatch.set(latch)
        instrumentation.runOnMainSync(action)
        latch.await(OUTPUT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        instrumentation.waitForIdleSync()
        outputLatch.compareAndSet(latch, null)
        return output.fold(ByteArray(0)) { combined, bytes -> combined + bytes }
    }

    private fun keyboardHandler(modifiers: TerminalInputController): KeyboardHandlerProbe {
        // termlib marks this handler internal in Kotlin metadata, but this is the only seam
        // that proves IME commits consume ModifierManager state before reaching libvterm.
        val type = Class.forName("org.connectbot.terminal.KeyboardHandler")
        val constructor = type.declaredConstructors.single { it.parameterTypes.size == 10 }
        val instance = constructor.newInstance(
            emulator,
            modifiers,
            null,
            null,
            null,
            null,
            null,
            null,
            KEYBOARD_DEFAULT_ARGUMENT_MASK,
            null,
        )
        val onTextInput = type.getMethod("onTextInput", ByteArray::class.java)
        return KeyboardHandlerProbe { bytes -> onTextInput.invoke(instance, bytes) }
    }

    private fun interface KeyboardHandlerProbe {
        fun type(bytes: ByteArray)

        fun type(text: String) = type(text.encodeToByteArray())
    }

    private companion object {
        const val SHIFT = TerminalInputController.VTERM_MOD_SHIFT
        const val ALT = TerminalInputController.VTERM_MOD_ALT
        const val CTRL = TerminalInputController.VTERM_MOD_CTRL
        const val ESC = 0x1B.toByte()
        const val KEYBOARD_DEFAULT_ARGUMENT_MASK = 252
        const val OUTPUT_TIMEOUT_MS = 2_000L
    }
}
