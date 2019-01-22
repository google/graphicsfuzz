/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.imagetools;

import com.graphicsfuzz.common.util.ImageColorComponents;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class CheckColorComponents {

  public static void main(String[] args) throws ArgumentParserException, IOException {

    final ArgumentParser parser = ArgumentParsers.newArgumentParser("CheckColorComponents")
        .defaultHelp(true)
        .description("Exits with code 0 if and only if the given image uses only the given color "
            + "components as the RGBA values of its pixels.");

    // Required arguments
    parser.addArgument("image")
        .help("Path to PNG image")
        .type(File.class);
    parser.addArgument("components")
        .type(Integer.class)
        .nargs("+")
        .help("Allowed components, each in range 0..255.");

    final Namespace ns = parser.parseArgs(args);

    final File image = ns.get("image");
    final List<Integer> components = ns.get("components");

    if (components.stream().anyMatch(item -> item < 0 || item > 255)) {
      System.err.println("Error: given component list " + components + " includes elements not "
          + "in range 0..255.");
    }

    if (!ImageColorComponents.containsOnlyGivenComponentValues(ImageIO.read(image), components)) {
      System.exit(1);
    }
  }

}
