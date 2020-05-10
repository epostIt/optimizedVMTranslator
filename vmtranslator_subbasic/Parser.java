//************************************************************************
// File: Parser.java
// Package: vmtranslator
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

package vmtranslator_subbasic;

import java.util.Scanner;
import java.io.File;

public class Parser
{
   private int lineNumber;
   private String cmdType;
   private String currentCommand;
   private String nextCommand;
   private String[] commandParts;
   
   private Scanner input = null;
   
   public Parser(String fileName)
   {
      lineNumber = 0;      

      cmdType = null;
      currentCommand = null;
      nextCommand = null;
      commandParts = null;

      input = null;
      
      try
      {
         input = new Scanner(new File(fileName));
      }
      catch (java.io.FileNotFoundException e)
      {
         System.out.println(e.getMessage());
         input = null;
      }
      
      nextCommand = fetchNextCommand();
   }
   
   public boolean hasMoreCommands()
   {
      if (null != nextCommand)
         return true;
      
      if (null != input)
      {
         input.close();
         input = null;
      }
         
      return false;
   }
   
   public void advance()
   {
      // Analyze new currentCommand
      currentCommand = nextCommand;
      commandParts = currentCommand.split("\\s");

      commandParts[0] = commandParts[0].toLowerCase();
            
      cmdType = "C_UNKNOWN";
      if (commandParts[0].equals("push"))     cmdType = "C_PUSH";
      if (commandParts[0].equals("pop"))      cmdType = "C_POP";
      if (commandParts[0].equals("neg"))      cmdType = "C_ARITHMETIC";
      if (commandParts[0].equals("add"))      cmdType = "C_ARITHMETIC";
      if (commandParts[0].equals("sub"))      cmdType = "C_ARITHMETIC";
      if (commandParts[0].equals("not"))      cmdType = "C_ARITHMETIC";
      if (commandParts[0].equals("and"))      cmdType = "C_ARITHMETIC";
      if (commandParts[0].equals("or"))       cmdType = "C_ARITHMETIC";
      if (commandParts[0].equals("eq"))       cmdType = "C_ARITHMETIC";
      if (commandParts[0].equals("lt"))       cmdType = "C_ARITHMETIC";
      if (commandParts[0].equals("gt"))       cmdType = "C_ARITHMETIC";
      if (commandParts[0].equals("label"))    cmdType = "C_LABEL";
      if (commandParts[0].equals("goto"))     cmdType = "C_GOTO";
      if (commandParts[0].equals("if-goto"))  cmdType = "C_IF";
      if (commandParts[0].equals("call"))     cmdType = "C_CALL";
      if (commandParts[0].equals("return"))   cmdType = "C_RETURN";
      if (commandParts[0].equals("function")) cmdType = "C_FUNCTION";
      
      // Fetch nextCommand (if any)
      nextCommand = fetchNextCommand();
   }
   
   public String commandType()
   {
      return cmdType;
   }
   
   public String arg1()
   {
      if (cmdType.equals("C_ARITHMETIC"))
         return commandParts[0];

      return commandParts[1];
   }
   
   public int arg2()
   {
      return Integer.parseInt(commandParts[2]);
   }
   
   private String fetchNextCommand()
   {
      String line = null;
      
      if ( (null == input) || !(input.hasNext()) )
         return null;

      line = input.nextLine().trim();
      lineNumber = lineNumber + 1;

      if ( (0 != line.length()) && !(line.startsWith("//")) )
         return line;
         
      return fetchNextCommand();
   }
}