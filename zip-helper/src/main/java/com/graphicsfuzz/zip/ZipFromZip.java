package com.graphicsfuzz.zip;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.archivers.zip.ZipMethod;
import org.apache.commons.io.IOUtils;

/**
 * An executable that copies and relocates files from one or more zips to an output zip,
 * retaining the permissions of the files.
 */
public final class ZipFromZip {

  private ZipFromZip() {}

  public static void main(String[] args) {

    try {
      mainHelper(args);
    } catch (Exception exception) {
      exception.printStackTrace();
      System.exit(1);
    }

  }

  public static void mainHelper(String[] args) throws IOException {

    if (args.length == 0) {
      throw new IllegalArgumentException(
          "Usage: ZipFromZip "
              + "outputZip <inputZip regexPathMatch replacementPath> ...[repeat triples]\n"
              + "\n"
              + "E.g. ZipFromZip  out.zip   in.zip bin/(.+) bin/Linux/$1   "
              + "in.zip lib/(.+) lib/Linux/$1\n"
      );
    }

    final File outputZipFile = new File(args[0]);

    Set<String> directoriesAdded = new HashSet<>();


    try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(outputZipFile)) {
      zos.setLevel(0);

      for (int i = 1; i < args.length; ++i) {
        final File inZip = new File(args[i]);
        ++i;
        Pattern regexIn = Pattern.compile(args[i]);
        ++i;
        String matchOut = args[i];

        try (ZipFile zis = new ZipFile(inZip)) {
          Enumeration<ZipArchiveEntry> entries = zis.getEntries();
          while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            //noinspection EmptyFinallyBlock
            try {
              Matcher matcher = regexIn.matcher(entry.getName());
              if (!matcher.matches()) {
                continue;
              }
              String newName = matcher.replaceFirst(matchOut);

              {
                // Add directories, if necessary:
                Path path = Paths.get(newName);
                for (int p = 1; p < path.getNameCount(); ++p) {
                  // 0..p, exclusive (every subpath, except the full path)
                  String subpath = path.subpath(0, p).toString() + "/";
                  if (!directoriesAdded.contains(subpath)) {
                    directoriesAdded.add(subpath);
                    zos.putArchiveEntry(new ZipArchiveEntry(subpath));
                    zos.closeArchiveEntry();
                  }
                }
              }

              ZipArchiveEntrySettableName newEntry = new ZipArchiveEntrySettableName(entry);
              newEntry.setName(newName);

              // If we are adding a directory, record it:
              if (newEntry.getName().endsWith("/")) {
                directoriesAdded.add(newEntry.getName());
              }

              zos.putArchiveEntry(newEntry);
              try {
                IOUtils.copy(zis.getInputStream(entry), zos);
              } finally {
                zos.closeArchiveEntry();
              }
            } finally {
              // close input entry: not needed with commons ZipFile.
            }
          }

        }


      }
    }
  }

}
