/*
 * libjingle
 * Copyright 2015 Google Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.webrtc;

import android.test.ActivityTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.nio.ByteBuffer;
import java.util.Random;

import android.opengl.EGL14;
import android.opengl.Matrix;
import android.opengl.GLES20;

public class GlRectDrawerTest extends ActivityTestCase {
  // Resolution of the test image.
  private static final int WIDTH = 16;
  private static final int HEIGHT = 16;
  // Seed for random pixel creation.
  private static final int SEED = 42;
  // When comparing pixels, allow some slack for float arithmetic and integer rounding.
  final float MAX_DIFF = 1.0f;

  private static float normalizedByte(byte b) {
    return (b & 0xFF) / 255.0f;
  }

  private static float saturatedConvert(float c) {
    return 255.0f * Math.max(0, Math.min(c, 1));
  }

  // Assert RGB ByteBuffers are pixel perfect identical.
  private static void assertEquals(int width, int height, ByteBuffer actual, ByteBuffer expected) {
    actual.rewind();
    expected.rewind();
    assertEquals(actual.remaining(), width * height * 3);
    assertEquals(expected.remaining(), width * height * 3);
    for (int y = 0; y < height; ++y) {
      for (int x = 0; x < width; ++x) {
        final int actualR = actual.get() & 0xFF;
        final int actualG = actual.get() & 0xFF;
        final int actualB = actual.get() & 0xFF;
        final int expectedR = expected.get() & 0xFF;
        final int expectedG = expected.get() & 0xFF;
        final int expectedB = expected.get() & 0xFF;
        if (actualR != expectedR || actualG != expectedG || actualB != expectedB) {
          fail("ByteBuffers of size " + width + "x" + height + " not equal at position "
              + "(" +  x + ", " + y + "). Expected color (R,G,B): "
              + "(" + expectedR + ", " + expectedG + ", " + expectedB + ")"
              + " but was: " + "(" + actualR + ", " + actualG + ", " + actualB + ").");
        }
      }
    }
  }

  // Convert RGBA ByteBuffer to RGB ByteBuffer.
  private static ByteBuffer stripAlphaChannel(ByteBuffer rgbaBuffer) {
    rgbaBuffer.rewind();
    assertEquals(rgbaBuffer.remaining() % 4, 0);
    final int numberOfPixels = rgbaBuffer.remaining() / 4;
    final ByteBuffer rgbBuffer = ByteBuffer.allocateDirect(numberOfPixels * 3);
    while (rgbaBuffer.hasRemaining()) {
      // Copy RGB.
      for (int channel = 0; channel < 3; ++channel) {
        rgbBuffer.put(rgbaBuffer.get());
      }
      // Drop alpha.
      rgbaBuffer.get();
    }
    return rgbBuffer;
  }

  @SmallTest
  public void testRgbRendering() throws Exception {
    // Create EGL base with a pixel buffer as display output.
    final EglBase eglBase = new EglBase(EGL14.EGL_NO_CONTEXT, EglBase.ConfigType.PIXEL_BUFFER);
    eglBase.createPbufferSurface(WIDTH, HEIGHT);
    eglBase.makeCurrent();

    // Create RGB byte buffer plane with random content.
    final ByteBuffer rgbPlane = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 3);
    final Random random = new Random(SEED);
    random.nextBytes(rgbPlane.array());

    // Upload the RGB byte buffer data as a texture.
    final int rgbTexture = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, rgbTexture);
    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, WIDTH,
        HEIGHT, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, rgbPlane);
    GlUtil.checkNoGLES2Error("glTexImage2D");

    // Draw the RGB frame onto the pixel buffer.
    final GlRectDrawer drawer = new GlRectDrawer();
    final float[] identityMatrix = new float[16];
    Matrix.setIdentityM(identityMatrix, 0);
    drawer.drawRgb(rgbTexture, identityMatrix);

    // Download the pixels in the pixel buffer as RGBA. Not all platforms support RGB, e.g. Nexus 9.
    final ByteBuffer rgbaData = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4);
    GLES20.glReadPixels(0, 0, WIDTH, HEIGHT, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rgbaData);
    GlUtil.checkNoGLES2Error("glReadPixels");

    // Assert rendered image is pixel perfect to source RGB.
    assertEquals(WIDTH, HEIGHT, stripAlphaChannel(rgbaData), rgbPlane);

    drawer.release();
    GLES20.glDeleteTextures(1, new int[] {rgbTexture}, 0);
    eglBase.release();
  }

  @SmallTest
  public void testYuvRendering() throws Exception {
    // Create EGL base with a pixel buffer as display output.
    EglBase eglBase = new EglBase(EGL14.EGL_NO_CONTEXT, EglBase.ConfigType.PIXEL_BUFFER);
    eglBase.createPbufferSurface(WIDTH, HEIGHT);
    eglBase.makeCurrent();

    // Create YUV byte buffer planes with random content.
    final ByteBuffer[] yuvPlanes = new ByteBuffer[3];
    final Random random = new Random(SEED);
    for (int i = 0; i < 3; ++i) {
      yuvPlanes[i] = ByteBuffer.allocateDirect(WIDTH * HEIGHT);
      random.nextBytes(yuvPlanes[i].array());
    }

    // Generate 3 texture ids for Y/U/V.
    final int yuvTextures[] = new int[3];
    for (int i = 0; i < 3; i++)  {
      yuvTextures[i] = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D);
    }

    // Upload the YUV byte buffer data as textures.
    for (int i = 0; i < 3; ++i) {
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yuvTextures[i]);
      GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, WIDTH,
          HEIGHT, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, yuvPlanes[i]);
      GlUtil.checkNoGLES2Error("glTexImage2D");
    }

    // Draw the YUV frame onto the pixel buffer.
    final GlRectDrawer drawer = new GlRectDrawer();
    final float[] texMatrix = new float[16];
    Matrix.setIdentityM(texMatrix, 0);
    drawer.drawYuv(yuvTextures, texMatrix);

    // Download the pixels in the pixel buffer as RGBA. Not all platforms support RGB, e.g. Nexus 9.
    final ByteBuffer data = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4);
    GLES20.glReadPixels(0, 0, WIDTH, HEIGHT, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, data);
    GlUtil.checkNoGLES2Error("glReadPixels");

    // Compare the YUV data with the RGBA result.
    for (int y = 0; y < HEIGHT; ++y) {
      for (int x = 0; x < WIDTH; ++x) {
        // YUV color space. Y in [0, 1], UV in [-0.5, 0.5]. The constants are taken from the YUV
        // fragment shader code in GlRectDrawer.
        final float y_luma = normalizedByte(yuvPlanes[0].get());
        final float u_chroma = normalizedByte(yuvPlanes[1].get()) - 0.5f;
        final float v_chroma = normalizedByte(yuvPlanes[2].get()) - 0.5f;
        // Expected color in unrounded RGB [0.0f, 255.0f].
        final float expectedRed = saturatedConvert(y_luma + 1.403f * v_chroma);
        final float expectedGreen =
            saturatedConvert(y_luma - 0.344f * u_chroma - 0.714f * v_chroma);
        final float expectedBlue = saturatedConvert(y_luma + 1.77f * u_chroma);

        // Actual color in RGB8888.
        final int actualRed = data.get() & 0xFF;
        final int actualGreen = data.get() & 0xFF;
        final int actualBlue = data.get() & 0xFF;
        final int actualAlpha = data.get() & 0xFF;

        // Assert rendered image is close to pixel perfect from source YUV.
        assertTrue(Math.abs(actualRed - expectedRed) < MAX_DIFF);
        assertTrue(Math.abs(actualGreen - expectedGreen) < MAX_DIFF);
        assertTrue(Math.abs(actualBlue - expectedBlue) < MAX_DIFF);
        assertEquals(actualAlpha, 255);
      }
    }

    drawer.release();
    GLES20.glDeleteTextures(3, yuvTextures, 0);
    eglBase.release();
  }
}
