package com.graphicsfuzz.zip;

import java.io.File;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

/**
 * ZipArchiveEntry from commons compress has a protected setName method; we expose it as a public
 * method.
 */
public class ZipArchiveEntrySettableName extends ZipArchiveEntry {
  public ZipArchiveEntrySettableName(String name) {
    super(name);
  }

  public ZipArchiveEntrySettableName(ZipEntry entry) throws ZipException {
    super(entry);
  }

  public ZipArchiveEntrySettableName(ZipArchiveEntry entry) throws ZipException {
    super(entry);
  }

  public ZipArchiveEntrySettableName() {
  }

  public ZipArchiveEntrySettableName(File inputFile, String entryName) {
    super(inputFile, entryName);
  }

  public void setName(String name) {
    super.setName(name);
  }
}
