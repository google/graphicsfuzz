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
