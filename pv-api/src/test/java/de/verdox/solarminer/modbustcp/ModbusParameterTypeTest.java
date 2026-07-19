package de.verdox.solarminer.modbustcp;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModbusParameterTypeTest {

    @Test
    void decodesSignedAndUnsignedInvalidSentinels() {
        assertNull(parse("uint16nan", hex(0xFF, 0xFF)));
        assertNull(parse("int32nan", hex(0x80, 0x00, 0x00, 0x00)));
        assertNull(parse("uint32nan", hex(0xFF, 0xFF, 0xFF, 0xFF)));
        assertNull(parse("uint64nan", hex(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF)));
        assertEquals(42L, ((Number) parse("uint32nan", hex(0x00, 0x00, 0x00, 0x2A))).longValue());
        assertEquals(-42L, ((Number) parse("int64", hex(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xD6))).longValue());
    }

    @Test
    void decodesSwappedModbusWords() {
        assertEquals(0x12345678L, ((Number) parse("uint32s", hex(0x56, 0x78, 0x12, 0x34))).longValue());
        assertEquals(1.0f, ((Number) parse("float32s", hex(0x00, 0x00, 0x3F, 0x80))).floatValue());
        assertNull(parse("float32nans", hex(0x00, 0x00, 0x7F, 0xC0)));
    }

    private Object parse(String type, ByteBuffer buffer) {
        return ModbusParameterType.findById(type).parser().apply(buffer);
    }

    private ByteBuffer hex(int... values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length).order(ByteOrder.BIG_ENDIAN);
        for (int value : values) buffer.put((byte) value);
        return buffer.flip();
    }
}
