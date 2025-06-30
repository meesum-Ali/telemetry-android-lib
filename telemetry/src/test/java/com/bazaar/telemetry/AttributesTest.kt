package com.bazaar.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

class AttributesTest {

    @Test
    fun testAttributesBuilder() {
        val attributes = Attributes.builder()
            .put("stringKey", "stringValue")
            .put("intKey", 123)
            .put("longKey", 456L)
            .put("doubleKey", 78.9)
            .put("booleanKey", true)
            .build()

        assertEquals("stringValue", attributes.getString("stringKey"))
        assertEquals(123, attributes.getInt("intKey"))
        assertEquals(456L, attributes.getLong("longKey"))
        assertEquals(78.9, attributes.getDouble("doubleKey"), 0.0)
        assertEquals(true, attributes.getBoolean("booleanKey"))
    }

    @Test
    fun testEmptyAttributes() {
        val attributes = Attributes.empty()
        assertEquals(null, attributes.getString("nonExistentKey"))
    }

    @Test
    fun testPutAll() {
        val initialAttributes = Attributes.builder()
            .put("key1", "value1")
            .build()

        val newAttributes = Attributes.builder()
            .put("key2", "value2")
            .putAll(initialAttributes)
            .build()

        assertEquals("value1", newAttributes.getString("key1"))
        assertEquals("value2", newAttributes.getString("key2"))
    }

    @Test
    fun testToOtelAttributes() {
        val attributes = Attributes.builder()
            .put("stringKey", "stringValue")
            .put("intKey", 123)
            .put("longKey", 456L)
            .put("doubleKey", 78.9)
            .put("booleanKey", true)
            .build()

        val otelAttributes = attributes.toOtelAttributes()

        assertEquals("stringValue", otelAttributes.get(io.opentelemetry.api.common.AttributeKey.stringKey("stringKey")))
        assertEquals(123L, otelAttributes.get(io.opentelemetry.api.common.AttributeKey.longKey("intKey"))) // Ints are converted to Longs
        assertEquals(456L, otelAttributes.get(io.opentelemetry.api.common.AttributeKey.longKey("longKey")))
        assertEquals(78.9, otelAttributes.get(io.opentelemetry.api.common.AttributeKey.doubleKey("doubleKey")), 0.0)
        assertEquals(true, otelAttributes.get(io.opentelemetry.api.common.AttributeKey.booleanKey("booleanKey")))
    }
}
