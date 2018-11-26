#!/usr/bin/python

import os
import sys

if len(sys.argv) != 2:
  sys.stderr.write("Usage: " + sys.argv[0] + " <shader>\n")
  exit(1)

filename = sys.argv[1]

if not os.path.isfile(filename):
  sys.stderr.write("Input file " + filename + " does not exist.\n")
  exit(1)

text = open(filename, "r").read()

# Pretend that an internal error occurred if the file doesn't contain 'floor'.
if (not "floor" in text):
  sys.stderr.write("Internal error: something went wrong inlining 'floor'.\n")
  exit(2)

# Pretend that a fatal error occurred if the file contains '[i]' at least twice.
if (text.count("[i]") > 1):
  sys.stderr.write("Fatal error: too much indexing.\n")
  exit(2)

# Pretend that compilation succeeded.
sys.stdout.write("Compilation succeeded [not really!]\n")
exit(0)
