//************************************************************************
// File: VMTranslator.java
// Package: anonymous
// Author: William L. Bahn
// Last mod: 01 Dec 2019
//
// This implements the VM Translator API provided by the authors of the
// Nand-to-Tetris project (Elements of Computing Systems)
//
// Copyright 2019
// Anyone is free to use/modify this program provided the author is 
// acknowledged as the source.
// Exception: You are NOT authorized to use this code in any manner that
// violates the requirements/expectations of any academic assignment.
//************************************************************************

// Three allowed inputs
      
// IF:   args[0] is the name of a VM file (i.e., "filename.vm")
// THEN: translate JUST that file into "filename.asm" 

// IF:   args[0] is the name of directory (i.e., "directory")
// THEN: translate ALL .vm files in that directory into "directory.asm" 

// IF:   args[0] is not supplied
// THEN: translate ALL .vm files in the current directory into "directory.asm"
      

import java.io.File;
import java.util.ArrayList;

import vmtranslator_subbasic.Parser;
import vmtranslator_subbasic.CodeWriter;

public class VMTranslator_subbasic
{
   public static void main(String[] args) throws Exception
   {
      Parser parser;
      CodeWriter codeWriter;

      ArrayList<String> vmFileList = BuildFileList(args);
      String asmFileName = BuildASMfilename(args);

      System.out.println();
      System.out.println("SOURCE FILES:");
      for (String file: vmFileList)
      {
         System.out.println("      " + file);
      }
      System.out.println();
      System.out.println("TARGET FILE: " + asmFileName);

      // Create the single ASM file output and generate bootstrap code
      System.out.println();
      System.out.println("CREATING: " + asmFileName);
      codeWriter = new CodeWriter(asmFileName);
      codeWriter.writeInit();
      
      System.out.println();
      for (String vmFileName: vmFileList)
      {
         System.out.println("TRANSLATING: " + vmFileName);
         String command;
         
         codeWriter.setFileName(vmFileName);
         parser = new Parser(vmFileName);
         
         while (parser.hasMoreCommands())
         {
            parser.advance();
            command = parser.commandType();

            if (command.equals("C_PUSH"))
               codeWriter.writePushPop(command, parser.arg1(), parser.arg2());
            if (command.equals("C_POP"))
               codeWriter.writePushPop(command, parser.arg1(), parser.arg2());
            if (command.equals("C_ARITHMETIC"))
               codeWriter.writeArithmetic(parser.arg1());
            if (command.equals("C_LABEL"))
               codeWriter.writeLabel(parser.arg1());
            if (command.equals("C_GOTO"))
               codeWriter.writeGoto(parser.arg1());
            if (command.equals("C_IF"))
               codeWriter.writeIf(parser.arg1());
            if (command.equals("C_CALL"))
               codeWriter.writeCall(parser.arg1(), parser.arg2());
            if (command.equals("C_RETURN"))
               codeWriter.writeReturn();
            if (command.equals("C_FUNCTION"))
               codeWriter.writeFunction(parser.arg1(), parser.arg2());
         }
      }   
      System.out.println();
      
      codeWriter.close();     
      System.out.println("DONE!");
   }

   private static ArrayList<String> BuildFileList(String[] args)
   {
      ArrayList<String> vmFileList = new ArrayList<>();
      String directoryName;
      
      if (0 == args.length)
      {
         directoryName = ".";
         File folder = new File(directoryName + File.separator);
         String[] files = folder.list();
         for (String file: files)
         {
            if (file.endsWith(".vm"))
               vmFileList.add(directoryName + File.separator + file);
         }
      }
      
      if (1 == args.length)
      {
         if (args[0].endsWith(".vm"))
            vmFileList.add(args[0]);
         else
         {
            directoryName = args[0];
            File folder = new File(directoryName);
            String[] files = folder.list();
            for (String file: files)
            {
               if (file.endsWith(".vm"))
                  vmFileList.add(directoryName + File.separator + file);
            }
         }
      }
      
      return vmFileList;
   }

   private static String BuildASMfilename(String[] args)
   {
      String currentDirectory;
      String ASMfilename = null;
      
      if (0 == args.length)
      {
         int index;
         
         currentDirectory = System.getProperty("user.dir");
         index = currentDirectory.lastIndexOf(File.separator);
         ASMfilename = currentDirectory.substring(index + 1) + ".asm";
      }
      
      if (1 == args.length)
      {
         ASMfilename = args[0];
         System.out.println("ASMfilename: [" + ASMfilename + "]");
         if (ASMfilename.endsWith(".vm"))
            ASMfilename = ASMfilename.replace(".vm", ".asm");
         else
            ASMfilename = ASMfilename + File.separator + ASMfilename + ".asm";
      }
      
      return ASMfilename;
   }
}