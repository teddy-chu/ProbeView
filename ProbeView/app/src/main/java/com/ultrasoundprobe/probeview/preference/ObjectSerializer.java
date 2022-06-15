package com.ultrasoundprobe.probeview.preference;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ObjectSerializer {

    public static String serialize(Object value) throws IOException {
        if (value == null) return "";

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);

        objectOutputStream.writeObject(value);
        objectOutputStream.close();

        return encodeBytes(byteArrayOutputStream.toByteArray());
    }

    public static Object deserialize(String value) throws IOException, ClassNotFoundException {
        if (value == null || value.length() == 0)
            return null;

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(decodeBytes(value));
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);

        return objectInputStream.readObject();
    }

    public static String encodeBytes(byte[] values) {
        StringBuilder stringBuilder = new StringBuilder();

        for (byte value : values) {
            stringBuilder.append((char)(((value >> 4) & 0x0f) + ((int)'a')));
            stringBuilder.append((char)((value & 0x0f) + ((int)'a')));
        }

        return stringBuilder.toString();
    }

    public static byte[] decodeBytes(String value) {
        byte[] bytes = new byte[value.length() / 2];

        for (int i = 0; i < value.length(); i+=2) {
            bytes[i / 2] = (byte)((value.charAt(i) - 'a') << 4);
            bytes[i / 2] += (value.charAt(i + 1) - 'a');
        }

        return bytes;
    }
}