package com.dima16263690d.bleckshlng.client.render;

import java.util.ArrayDeque;
import java.util.Deque;
import java.nio.FloatBuffer;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;

/**
 * CPU-side pose stack modelled after the modern PoseStack API and implemented
 * with Java 8 / LWJGL 2 primitives available in Minecraft 1.12.2.
 *
 * Matrices use OpenGL column-major layout. Angles passed to rotate are degrees.
 */
public final class PoseStack112 {
    private final Deque<Pose> poses = new ArrayDeque<Pose>();

    public PoseStack112() {
        poses.addLast(new Pose());
    }

    public void pushPose() {
        poses.addLast(poses.peekLast().copy());
    }

    public void popPose() {
        if (poses.size() <= 1) {
            throw new IllegalStateException("Cannot pop the root pose");
        }

        poses.removeLast();
    }

    public Pose last() {
        return poses.peekLast();
    }

    public int depth() {
        return poses.size();
    }

    public boolean clear() {
        return poses.size() == 1;
    }

    public void setIdentity() {
        poses.peekLast().setIdentity();
    }

    public void translate(double x, double y, double z) {
        poses.peekLast().translate((float) x, (float) y, (float) z);
    }

    public void scale(float x, float y, float z) {
        poses.peekLast().scale(x, y, z);
    }

    public void rotate(float angleDegrees, float axisX, float axisY, float axisZ) {
        poses.peekLast().rotate(angleDegrees, axisX, axisY, axisZ);
    }

    public static final class Pose {
        private final float[] matrix;
        private final float[] normalMatrix;

        private Pose() {
            this.matrix = identity4();
            this.normalMatrix = identity3();
        }

        private Pose(Pose source) {
            this.matrix = source.matrix.clone();
            this.normalMatrix = source.normalMatrix.clone();
        }

        public Pose copy() {
            return new Pose(this);
        }

        private void setIdentity() {
            setIdentity4(this.matrix);
            setIdentity3(this.normalMatrix);
        }

        private void translate(float x, float y, float z) {
            float[] translation = identity4();
            translation[12] = x;
            translation[13] = y;
            translation[14] = z;
            multiply(translation);
        }

        private void scale(float x, float y, float z) {
            float[] scaling = identity4();
            scaling[0] = x;
            scaling[5] = y;
            scaling[10] = z;
            multiply(scaling);
        }

        private void rotate(float angleDegrees, float axisX, float axisY, float axisZ) {
            float length = (float) Math.sqrt(
                    axisX * axisX + axisY * axisY + axisZ * axisZ);

            if (length < 1.0E-7F) {
                return;
            }

            float x = axisX / length;
            float y = axisY / length;
            float z = axisZ / length;
            float radians = (float) Math.toRadians(angleDegrees);
            float c = (float) Math.cos(radians);
            float s = (float) Math.sin(radians);
            float t = 1.0F - c;

            float[] rotation = identity4();

            // Rotation matrix stored in OpenGL column-major order.
            rotation[0] = t * x * x + c;
            rotation[1] = t * x * y + s * z;
            rotation[2] = t * x * z - s * y;
            rotation[4] = t * x * y - s * z;
            rotation[5] = t * y * y + c;
            rotation[6] = t * y * z + s * x;
            rotation[8] = t * x * z + s * y;
            rotation[9] = t * y * z - s * x;
            rotation[10] = t * z * z + c;

            multiply(rotation);
        }

        private void multiply(float[] right) {
            float[] result = new float[16];

            for (int column = 0; column < 4; column++) {
                for (int row = 0; row < 4; row++) {
                    float value = 0.0F;

                    for (int k = 0; k < 4; k++) {
                        value += this.matrix[k * 4 + row] * right[column * 4 + k];
                    }

                    result[column * 4 + row] = value;
                }
            }

            System.arraycopy(result, 0, this.matrix, 0, 16);
            updateNormalMatrix();
        }

        private void updateNormalMatrix() {
            // Inverse-transpose of the upper-left 3x3 pose matrix.
            float a = matrix[0];
            float b = matrix[4];
            float c = matrix[8];
            float d = matrix[1];
            float e = matrix[5];
            float f = matrix[9];
            float g = matrix[2];
            float h = matrix[6];
            float i = matrix[10];

            float determinant = a * (e * i - f * h)
                    - b * (d * i - f * g)
                    + c * (d * h - e * g);

            if (Math.abs(determinant) < 1.0E-8F) {
                setIdentity3(this.normalMatrix);
                return;
            }

            float inverse = 1.0F / determinant;
            normalMatrix[0] = (e * i - f * h) * inverse;
            normalMatrix[1] = (f * g - d * i) * inverse;
            normalMatrix[2] = (d * h - e * g) * inverse;
            normalMatrix[3] = (c * h - b * i) * inverse;
            normalMatrix[4] = (a * i - c * g) * inverse;
            normalMatrix[5] = (b * g - a * h) * inverse;
            normalMatrix[6] = (b * f - c * e) * inverse;
            normalMatrix[7] = (c * d - a * f) * inverse;
            normalMatrix[8] = (a * e - b * d) * inverse;
        }

        public void transformPosition(float x, float y, float z, float[] output) {
            if (output == null || output.length < 3) {
                throw new IllegalArgumentException("Output must contain at least 3 floats");
            }

            output[0] = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
            output[1] = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
            output[2] = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];
        }

        public void transformNormal(float x, float y, float z, float[] output) {
            if (output == null || output.length < 3) {
                throw new IllegalArgumentException("Output must contain at least 3 floats");
            }

            float transformedX = normalMatrix[0] * x + normalMatrix[1] * y + normalMatrix[2] * z;
            float transformedY = normalMatrix[3] * x + normalMatrix[4] * y + normalMatrix[5] * z;
            float transformedZ = normalMatrix[6] * x + normalMatrix[7] * y + normalMatrix[8] * z;
            float length = (float) Math.sqrt(
                    transformedX * transformedX
                            + transformedY * transformedY
                            + transformedZ * transformedZ);

            if (length > 1.0E-7F) {
                transformedX /= length;
                transformedY /= length;
                transformedZ /= length;
            }

            output[0] = transformedX;
            output[1] = transformedY;
            output[2] = transformedZ;
        }

        /**
         * Multiplies the current OpenGL matrix by this pose. Call only on the
         * render thread and with the intended GL matrix mode already selected.
         */
        public void applyToOpenGL() {
            RenderSystem112.assertOnRenderThread();
            FloatBuffer buffer = GLAllocation.createDirectFloatBuffer(16);
            buffer.put(matrix);
            buffer.flip();
            GlStateManager.multMatrix(buffer);
        }

        public float[] getMatrix() {
            return matrix.clone();
        }

        public float[] getNormalMatrix() {
            return normalMatrix.clone();
        }

        private static float[] identity4() {
            float[] result = new float[16];
            setIdentity4(result);
            return result;
        }

        private static float[] identity3() {
            float[] result = new float[9];
            setIdentity3(result);
            return result;
        }

        private static void setIdentity4(float[] target) {
            for (int index = 0; index < 16; index++) {
                target[index] = 0.0F;
            }
            target[0] = 1.0F;
            target[5] = 1.0F;
            target[10] = 1.0F;
            target[15] = 1.0F;
        }

        private static void setIdentity3(float[] target) {
            for (int index = 0; index < 9; index++) {
                target[index] = 0.0F;
            }
            target[0] = 1.0F;
            target[4] = 1.0F;
            target[8] = 1.0F;
        }
    }
}
