package com.impactiq.rag;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/** Float32 little-endian encoding for embeddings stored in a BLOB column. */
public final class VectorCodec {

    private VectorCodec() {
    }

    public static byte[] encode(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    public static float[] decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length % Float.BYTES != 0) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / Float.BYTES];
        for (int index = 0; index < vector.length; index++) {
            vector[index] = buffer.getFloat();
        }
        return vector;
    }

    /** L2-normalises in place so cosine similarity becomes a dot product. Returns null for a zero vector. */
    public static float[] normalize(List<Float> values) {
        float[] vector = new float[values.size()];
        double norm = 0;
        for (int index = 0; index < vector.length; index++) {
            vector[index] = values.get(index);
            norm += (double) vector[index] * vector[index];
        }
        if (norm == 0) {
            return null;
        }
        float scale = (float) (1.0 / Math.sqrt(norm));
        for (int index = 0; index < vector.length; index++) {
            vector[index] *= scale;
        }
        return vector;
    }

    public static double dot(float[] left, float[] right) {
        if (left == null || right == null || left.length != right.length) {
            return 0;
        }
        double sum = 0;
        for (int index = 0; index < left.length; index++) {
            sum += (double) left[index] * right[index];
        }
        return sum;
    }
}
