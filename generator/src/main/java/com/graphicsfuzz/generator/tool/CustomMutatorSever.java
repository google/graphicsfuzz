/*
 * Copyright 2018 The GraphicsFuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.graphicsfuzz.generator.tool;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.fuzzer.FuzzedIntoACornerException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Server that can accept shaders from a libFuzzer custom mutator and send back a mutated shader.
 */
public class CustomMutatorSever {
  public static void main(String[] args) {
    try {
      // TODO(metzman): If we don't switch from TCP, allow this to be configurable.
      runServer(8666);
    } catch (IOException | ParseTimeoutException | InterruptedException exception) {
      exception.printStackTrace();
      System.exit(1);
    }
  }

  private static void runServer(int port)
      throws IOException, ParseTimeoutException, InterruptedException {
    byte[] headerBuff = new byte[28];
    ServerSocket serverSocket = new ServerSocket(port);
    Socket socket = serverSocket.accept();
    InputStream inputStream = socket.getInputStream();
    OutputStream outputStream = socket.getOutputStream();
    while (true) {
      // TODO(metzman) Figure out a better way to handle waiting for the header to arrive.
      while (inputStream.available() < headerBuff.length) {
        ;
      }
      inputStream.read(headerBuff, 0, headerBuff.length);
      ByteBuffer headerByteBuffer = ByteBuffer.wrap(headerBuff);
      headerByteBuffer.order(ByteOrder.LITTLE_ENDIAN);

      long size1 = headerByteBuffer.getLong();
      // Support two sizes for now so that one message format can be used between CustomMutate and
      // CustomCrossOver.
      long size2 = headerByteBuffer.getLong();
      assert size2 == 0;

      // Java won't allow us to create an array with a "long" size. Therefore, we must convert it to
      // int before creating the array. This is probably a non-issue because libFuzzer is unlikely
      // to ever give us a shader larger than Integer.MAX_VALUE (e.g. ~2G).
      assert size1 <= Integer.MAX_VALUE && size2 <= Integer.MAX_VALUE;

      // GraphicsFuzz can't do anything with this unfortunately.
      long maxOutSize = headerByteBuffer.getLong();

      int libfuzzerSeed = headerByteBuffer.getInt();

      // Read the Shader.
      byte[] inputShaderBuff = new byte[((int) size1) + ((int) size2)];
      socket.getInputStream().read(inputShaderBuff, 0, inputShaderBuff.length);
      String inputShader = new String(inputShaderBuff);

      try {
        // Now mutate the shader.
        TranslationUnit tu = ParseHelper.parse(inputShader, ShaderKind.FRAGMENT);
        Mutate.mutate(tu, new RandomWrapper(libfuzzerSeed));
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(byteArrayOutputStream, true, "UTF-8")) {
          PrettyPrinterVisitor.emitShader(
              tu,
              Optional.empty(),
              stream,
              PrettyPrinterVisitor.DEFAULT_INDENTATION_WIDTH,
              PrettyPrinterVisitor.DEFAULT_NEWLINE_SUPPLIER,
              false);
          String outputShader =
              new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);

          // Get shader size as a little endian uint64_t.
          ByteBuffer lengthByteBuffer = ByteBuffer.allocate(Long.BYTES);
          lengthByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
          lengthByteBuffer.putLong((long) outputShader.length());
          byte[] length = new byte[Long.BYTES];
          lengthByteBuffer.position(0);
          lengthByteBuffer.get(length);
          outputStream.write(length);

          outputStream.write(outputShader.getBytes());
        }
      } catch (GlslParserException | FuzzedIntoACornerException exception) {
        exception.printStackTrace();
        System.out.println(new String(inputShaderBuff));
        // Tell libFuzzer we will "send" it a 0-length shader.
        for (int idx = 0; idx < Long.BYTES; idx++) {
          outputStream.write(0);
        }
      }
    }
  }
}
