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
import com.graphicsfuzz.common.typing.DuplicateVariableException;
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
      // TODO(381): If we don't switch from TCP, allow this to be configured from the command
      // line.
      runServer(8666);
    } catch (IOException | ParseTimeoutException | InterruptedException exception) {
      exception.printStackTrace();
      System.exit(1);
    }
  }

  private static void runServer(int port)
      throws IOException, ParseTimeoutException, InterruptedException {
    final ServerSocket serverSocket = new ServerSocket(port);
    final Socket socket = serverSocket.accept();
    final InputStream inputStream = socket.getInputStream();
    final OutputStream outputStream = socket.getOutputStream();
    // Header is composed of
    // uint64_t size;
    // uint32_t seed;
    // uint8_t isFragment;
    final int headerSize = Long.BYTES + Integer.BYTES + Byte.BYTES;
    final byte[] headerBuff = new byte[headerSize];
    while (true) {
      // TODO(381): Figure out a better way to handle waiting for the header to arrive.
      while (inputStream.available() < headerBuff.length) {
        ;
      }
      inputStream.read(headerBuff, 0, headerBuff.length);
      final ByteBuffer headerByteBuffer = ByteBuffer.wrap(headerBuff);
      headerByteBuffer.order(ByteOrder.LITTLE_ENDIAN);

      // Java won't allow us to create an array with a "long" size. But the size of a
      // shader on the client can technically be up to SIZE_T_MAX. Therefore, we must convert it to
      // int before creating the array. This is probably a non-issue because libFuzzer is unlikely
      // to ever give us a shader larger than Integer.MAX_VALUE (e.g. ~2G).
      final long shaderSize = headerByteBuffer.getLong();
      assert shaderSize <= Integer.MAX_VALUE;

      final int seed = headerByteBuffer.getInt();
      final byte isFragmentByte = headerByteBuffer.get();
      final ShaderKind shaderKind = isFragmentByte == 1 ? ShaderKind.FRAGMENT : ShaderKind.VERTEX;

      // Read the shader.
      byte[] inputShaderBuff = new byte[(int) shaderSize];
      socket.getInputStream().read(inputShaderBuff, 0, inputShaderBuff.length);
      final String inputShader = new String(inputShaderBuff);

      try {
        // Mutate the shader.
        final TranslationUnit tu = ParseHelper.parse(inputShader, shaderKind);
        Mutate.mutate(tu, new RandomWrapper(seed));
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (final PrintStream stream = new PrintStream(byteArrayOutputStream, true, "UTF-8")) {
          PrettyPrinterVisitor.emitShader(
              tu,
              Optional.empty(),
              stream,
              PrettyPrinterVisitor.DEFAULT_INDENTATION_WIDTH,
              PrettyPrinterVisitor.DEFAULT_NEWLINE_SUPPLIER,
              false);
          final String outputShader =
              new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);

          // Get shader size as a little endian uint64_t then write it to the socket.
          final ByteBuffer lengthByteBuffer = ByteBuffer.allocate(Long.BYTES);
          lengthByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
          lengthByteBuffer.putLong((long) outputShader.length());
          final byte[] length = new byte[Long.BYTES];
          lengthByteBuffer.position(0);
          lengthByteBuffer.get(length);
          outputStream.write(length);

          outputStream.write(outputShader.getBytes());
        }
      } catch (GlslParserException
          | FuzzedIntoACornerException
          | DuplicateVariableException exception) {
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
