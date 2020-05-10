ReadMe.txt for William Bahn's VM Translator

This translator is built using the following package structure:

anonymous 
+--VM Translator
+--vmtranslator_subbasic
   +-- Parser
   +-- CodeWriter

It takes zero or one command line argument:

No arguments:    Compile all of the .vm files in the current directory
                 into a single .asm file named after the current directory.
				 The .asm file will the written to the current directlry.
			  
One file name:   The name of a single .vm file (including the extension)
                 in the current directory into a single .asm file named
			     the same as the file. The .asm file will be written to
				 the current directory.

One folder name: The name of a folder contained in the current directory.
                 Compiles all of the .vm files found there into a single
				 .asm file named after the folder containing the files.
				 The .asm file will be written into the folder containing
				 the .vm files.
				 
The details of how to run it depend on the specifics of your Java 
installaton.
