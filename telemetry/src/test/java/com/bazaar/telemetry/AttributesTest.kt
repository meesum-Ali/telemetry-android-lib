package com.bazaar.telemetry

import io.opentelemetry.api.common.AttributeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttributesTest {

    @Test
    fun `builder should correctly add attributes of different types`() {
        val attributes = Attributes.builder()
            .put("stringKey", "stringValue")
            .put("intKey", 123)
            .put("longKey", 456L)
            .put("doubleKey", 78.9)
            .put("boolKey", true)
            .build()

        assertEquals("stringValue", attributes.getString("stringKey"))
        assertEquals(123, attributes.getInt("intKey"))
        assertEquals(456L, attributes.getLong("longKey"))
        assertEquals("doubleKey value mismatch", 78.9, attributes.getDouble("doubleKey")!!, 0.001)
        assertEquals("boolKey value mismatch", true, attributes.getBoolean("boolKey"))
    }

    @Test
    fun `builder putAll should merge attributes correctly`() {
        val initialAttributes = Attributes.builder()
            .put("key1", "value1")
            .put("key2", 100)
            .build()

        val newAttributes = Attributes.builder()
            .put("key2", 200) // Overwrite
            .put("key3", "value3")
            .build()

        val merged = Attributes.builder()
            .putAll(initialAttributes)
            .putAll(newAttributes)
            .build()

        assertEquals("value1", merged.getString("key1"))
        assertEquals(200, merged.getInt("key2"))
        assertEquals("value3", merged.getString("key3"))
    }

    @Test
    fun `builder build should create correct Attributes object`() {
        val builder = Attributes.builder()
            .put("testKey", "testValue")
        val attributes = builder.build()
        assertEquals("testValue", attributes.getString("testKey"))
    }

    @Test
    fun `getters should retrieve correct values`() {
        val attributes = Attributes.builder()
            .put("stringKey", "value")
            .put("intKey", 10)
            .put("longKey", 20L)
            .put("doubleKey", 30.5)
            .put("boolKey", false)
            .build()

        assertEquals("value", attributes.getString("stringKey"))
        assertEquals(10, attributes.getInt("intKey"))
        assertEquals(20L, attributes.getLong("longKey"))
        assertEquals("doubleKey mismatch in getters",30.5, attributes.getDouble("doubleKey")!!, 0.001)
        assertEquals(false, attributes.getBoolean("boolKey"))
    }

    @Test
    fun `getters should return null for non-existent keys`() {
        val attributes = Attributes.empty()
        assertNull(attributes.getString("nonExistent"))
        assertNull(attributes.getInt("nonExistent"))
        assertNull(attributes.getLong("nonExistent"))
        assertNull(attributes.getDouble("nonExistent"))
        assertNull(attributes.getBoolean("nonExistent"))
    }

    @Test
    fun `getters should return null for type mismatches`() {
        val attributes = Attributes.builder()
            .put("stringKey", "value")
            .put("intKey", 10)
            .build()

        assertNull(attributes.getInt("stringKey"))
        assertNull(attributes.getString("intKey"))
        assertNull(attributes.getBoolean("intKey"))
    }

    @Test
    fun `toOtelAttributes should convert correctly`() {
        val attributes = Attributes.builder()
            .put("string.key", "stringValue")
            .put("int.key", 123) // Will be converted to Long in Otel
            .put("long.key", 456L)
            .put("double.key", 78.9)
            .put("bool.key", true)
            .build()

        val otelAttributes = attributes.toOtelAttributes()

        assertEquals("stringValue", otelAttributes.get(AttributeKey.stringKey("string.key")))
        assertEquals(123L, otelAttributes.get(AttributeKey.longKey("int.key"))) // Note: Otel uses Long for integers
        assertEquals(456L, otelAttributes.get(AttributeKey.longKey("long.key")))
        assertEquals("otel double conversion mismatch", 78.9, otelAttributes.get(AttributeKey.doubleKey("double.key"))!!, 0.001)
        assertEquals(true, otelAttributes.get(AttributeKey.booleanKey("bool.key")))
    }

    @Test
    fun `empty factory should create empty Attributes`() {
        val attributes = Attributes.empty()
        val otelAttributes = attributes.toOtelAttributes() // Access internal map via conversion
        assertTrue(otelAttributes.isEmpty)
    }

    @Test
    fun `builder factory should return a new Builder instance`() {
        val builder1 = Attributes.builder()
        val builder2 = Attributes.builder()
        assertTrue(builder1 !== builder2) // Check they are different instances
    }
}
