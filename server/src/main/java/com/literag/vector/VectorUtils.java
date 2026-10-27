package com.literag.vector;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

public final class VectorUtils {

    private VectorUtils() {
    }

    public static byte[] toBytes(float[] vector) {
        if (vector == null) {
            return null;
        }
        ByteBuffer buf = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector) {
            buf.putFloat(v);
        }
        return buf.array();
    }

    public static float[] toFloats(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        FloatBuffer fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        float[] out = new float[fb.remaining()];
        fb.get(out);
        return out;
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    public static float[] normalize(float[] v) {
        double norm = 0;
        for (float x : v) {
            norm += (double) x * x;
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            return v;
        }
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = (float) (v[i] / norm);
        }
        return out;
    }

    public static boolean sameDim(List<float[]> vectors, Integer expected) {
        if (expected == null || expected <= 0) {
            return true;
        }
        Integer dim = vectors.isEmpty() ? null : vectors.get(0).length;
        return dim != null && dim.equals(expected);
    }
}
