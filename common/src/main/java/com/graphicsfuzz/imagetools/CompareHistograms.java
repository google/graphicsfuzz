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

package com.graphicsfuzz.imagetools;

import com.graphicsfuzz.common.util.ImageUtil;
import java.io.File;
import java.io.FileNotFoundException;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class CompareHistograms {

  public static void main(String[] args) throws ArgumentParserException, FileNotFoundException {

    final ArgumentParser parser = ArgumentParsers.newArgumentParser("CompareHistograms")
        .defaultHelp(true)
        .description("Print chi-squared distance between the histograms of two given images.");

    // Required arguments
    parser.addArgument("image1")
        .help("Path of first image.")
        .type(File.class);
    parser.addArgument("image2")
        .help("Path of second image.")
        .type(File.class);

    final Namespace ns = parser.parseArgs(args);

    System.out.println(ImageUtil.compareHistograms((File) ns.get("image1"), ns.get("image2")));

  }

}
