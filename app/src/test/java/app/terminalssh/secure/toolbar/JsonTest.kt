package app.terminalssh.secure.toolbar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonTest {

    @Test fun parsesFlatObject() {
        val value = Json.parse("""{"a":"x","b":12,"c":true,"d":null}""") as JsonValue.Obj
        assertEquals("x", (value["a"] as JsonValue.Str).value)
        assertEquals(12.0, (value["b"] as JsonValue.Num).value)
        assertEquals(true, (value["c"] as JsonValue.Bool).value)
        assertEquals(JsonValue.Null, value["d"])
    }

    @Test fun parsesNestedStructures() {
        val value = Json.parse("""{"rows":[[{"id":"a"},{"id":"b"}],[]]}""") as JsonValue.Obj
        val rows = value["rows"] as JsonValue.Arr
        assertEquals(2, rows.items.size)
        val first = rows.items[0] as JsonValue.Arr
        assertEquals("a", ((first.items[0] as JsonValue.Obj)["id"] as JsonValue.Str).value)
        assertEquals(0, (rows.items[1] as JsonValue.Arr).items.size)
    }

    @Test fun decodesStringEscapes() {
        val value = Json.parse(""""a\tb\nc\"d\\e\u0041f"""") as JsonValue.Str
        assertEquals("a\tb\nc\"d\\eAf", value.value)
    }

    @Test fun roundTripsUntidyStrings() {
        val original = JsonValue.Str("line1\nline2\t\"quoted\"\\slash\u0001ctrl")
        val reparsed = Json.parse(original.encode())
        assertEquals(original, reparsed)
    }

    @Test fun preservesLargeColourNumbers() {
        // 0xFF2A2F38 is a 32-bit colour; it must survive as an exact Long, not 4.28E9.
        val obj = Json.obj("colour" to Json.num(0xFF2A2F38L))
        val reparsed = Json.parse(obj.encode()) as JsonValue.Obj
        assertEquals(0xFF2A2F38L, (reparsed["colour"] as JsonValue.Num).value.toLong())
    }

    @Test fun negativeAndFractionalNumbersParse() {
        val arr = Json.parse("[-5,-0.5,1e3]") as JsonValue.Arr
        assertEquals(-5.0, (arr.items[0] as JsonValue.Num).value)
        assertEquals(-0.5, (arr.items[1] as JsonValue.Num).value)
        assertEquals(1000.0, (arr.items[2] as JsonValue.Num).value)
    }

    @Test fun rejectsMalformedDocuments() {
        assertNull(Json.parse(""))
        assertNull(Json.parse("{"))
        assertNull(Json.parse("{\"a\":1"))            // unterminated object
        assertNull(Json.parse("{a:1}"))                 // unquoted key
        assertNull(Json.parse("[1,2,]"))                // trailing comma
        assertNull(Json.parse("\"unterminated"))
        assertNull(Json.parse("{} trailing"))           // trailing content
        assertNull(Json.parse("nul"))
    }

    @Test fun rejectsExcessiveContainerNestingWithoutRecursingUnboundedly() {
        val acceptedDepth = "[".repeat(Json.MAX_NESTING_DEPTH) + "0" + "]".repeat(Json.MAX_NESTING_DEPTH)
        val excessiveDepth = "[".repeat(Json.MAX_NESTING_DEPTH + 1) + "0" + "]".repeat(Json.MAX_NESTING_DEPTH + 1)
        assertTrue(Json.parse(acceptedDepth) is JsonValue.Arr)
        assertNull(Json.parse(excessiveDepth))
    }

    @Test fun emptyContainersRoundTrip() {
        assertEquals("{}", Json.obj().encode())
        assertEquals("[]", Json.arr(emptyList()).encode())
        assertTrue(Json.parse("{}") is JsonValue.Obj)
    }
}
