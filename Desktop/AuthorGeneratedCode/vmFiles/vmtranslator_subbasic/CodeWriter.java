//************************************************************************
// File: CodeWriter.java
// Package: vmtranslator
// Author: William L. Bahn
// Last mod: 19 Apr 2020
//
// This implements the VM Translator API provided by the authors of the
// Nand-to-Tetris project (Elements of Computing Systems)
//
// It is a VERY bare bones implementation that performs no error checking.
// It is also a (mostly) direct implementation of the logic behind the
// VM behaviors and is therefore very inefficient. This is intentional.
// It not only makes the logic easier to follow, but leaves lots of low
// hanging fruit for optimization.
//
// Copyright 2019, 2020
// Anyone is free to use/modify this program provided the author is 
// acknowledged as the source.
// Exception: You are NOT authorized to use this code in any manner that
// violates the requirements/expectations of any academic assignment.
//
// This version of the translator allows the selective option of using
// inline code or a subroutine for any instruction. This is controlled
// by the function useSubroutine(). You have to modify the switch()
// statement to comment out those command that should NOT use a subroutine.
// There is also a static variable (useSubroutines) that has the following
// settings:
//    useSubroutine = 0 // use NOT subroutines
//                  = 1 // use subroutines per the switch() statement
//                  = 2 // use ALL subroutines (that are available)
//
// Some commands have both the basic, direct implementation and an
// optimized version. Which of these is used depends on the static 
// boolean variable (useOptimized).
//
// Comments can be placed in the ASM file by setting the (instrumented)
// variable to true.
//
// The inline definitions of the commands is located at the bottom of the
// file. The subroutine definitions are located above that. At the very 
// bottom of the file are some code snippets (essentially macros) that
// are used repeatedly throughout the code.
//************************************************************************

package vmtranslator_subbasic;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class CodeWriter
{
   private int useSubroutines = 1; // 0 = none, 1 = some; 2 = all
   private boolean useOptimized = true;
   private boolean instrumented = true;

   private int romAddress;
   private int labelIndex;
   private PrintWriter asmFile = null;
   private String vmName = null;
   private String functionName = "";
   private int snippetLength = 30;
   private int asmLineLengthLimit = 60;
   
   //========================================================================
   // API IMPLEMENTATION   
   //========================================================================

   public CodeWriter(String filename) throws java.io.IOException
   {
      asmFile = new PrintWriter(new FileWriter(filename));
      header("ASM File: " + filename, "*", 0);
      
      romAddress = 0;
   }
   
   public void writeInit()
   {
      header("BOOTSTRAP CODE:", "*", 0);
      
      functionName = "..BOOT..";
      labelIndex = 0;
      
      // Initialize Stack Pointer to 256
      asm("@ 256");
      asm("D = A");
      asm("@ SP");
      asm("M = D");
      writeCall("Sys.init", 0);
   }
   
   public void setFileName(String fileName)
   {
      header("Translation of: " + fileName, "*", 0);

      // Set vmFileName to the bare file name
      int lastSep = fileName.lastIndexOf(File.separator);
      int lastDot = fileName.lastIndexOf(".");
      
      if (lastDot < 0)
         lastDot = fileName.length();
         
      vmName = fileName.substring(lastSep+1, lastDot);
      labelIndex = 0;
   }

   public void writePushPop(String command, String segment, int index)
   {
      if (command.equals("C_PUSH"))
      {
         vm("PUSH: <" + segment + ", " + index + ">");

         if (useSubroutine("push_" + segment))
            callPushPop(command, segment, index);
         else
            inlinePushPop(command, segment, index);
      }

      if (command.equals("C_POP"))
      {
         vm("POP: <" + segment + ", " + index + ">");

         if (useSubroutine("pop_" + segment))
            callPushPop(command, segment, index);
         else
            inlinePushPop(command, segment, index);
      }
   }
   
   public void writeArithmetic(String command)
   {
      vm("ARITHMETIC: " + command);

      if (useSubroutine(command))
         callArithmetic(command);
      else
         inlineArithmetic(command);
   }

   public void writeLabel(String label)
   {
      vm("LABEL: " + label);
      
      if (useSubroutine("label"))
         callLabel(label);
      else
         inlineLabel(label);
   }
   
   public void writeGoto(String label)
   {
      vm("GOTO: " + label);
      
      if (useSubroutine("goto"))
         callGoto(label);
      else
         inlineGoto(label);
   }
   
   public void writeIf(String label)
   {
      vm("IF-GOTO: " + label);
      
      if (useSubroutine("if-goto"))
         callIf(label);
      else
         inlineIf(label);
   }
   
   public void writeFunction(String functionName, int nLcls)
   {
      this.functionName = functionName;
      
      header("FUNCTION: " + functionName + "(" + nLcls + " locals)", "=", 0);
      
      if (useSubroutine("function"))
         callFunction(functionName, nLcls);
      else
         inlineFunction(functionName, nLcls);
   }

   public void writeCall(String functionName, int nArgs)
   {
      vm("CALL: " + functionName + "(" + nArgs + " args)");

      if (useSubroutine("call"))
         callCall(functionName, nArgs);
      else
         inlineCall(functionName, nArgs);
   }
      
   public void writeReturn()
   {
      vm("RETURN: ");

      if (useSubroutine("return"))
         callReturn();
      else
         inlineReturn();
   }

   public void close()
   {
      writeSubroutines();
      asmFile.close();
      asmFile = null;
   }

   //========================================================================
   // API HELPER METHODS   
   //========================================================================

   private String nextLocalLabel()
   {
      return functionName + ":" + labelIndex++;
   }
   
   private String staticLabel(int index)
   {
      return vmName + "." + index;
   }
   
   private String vmLabel(String label)
   {
      return functionName + "$" + label;
   }
   
   //========================================================================
   // INSTRUMENTATION HELPER METHODS   
   //========================================================================

   private void asm(String s)
   {
      if (instrumented)
      {
         if (isInstruction(s))
         {
            for (int i = s.length(); i < asmLineLengthLimit - 15; i = i + 1)
               s = s + " ";
            s = String.format("%s// ROM[%05d]", s, romAddress);

            romAddress = romAddress + 1;
         }
         asmFile.println(s);
      }
      else
         if (!isComment(s))
            asmFile.println(s);
   }

   private void asm()
   {
      asm("");
   }

   private void vm(String s)
   {
      asm();
      asm(extend("// ", "-", snippetLength + 10));
      asm("// " + s);
      asm(extend("// ", "-", snippetLength + 10));
   }

   private void header(String s, String c, int margin)
   {
      asm();
      asm(extend("// ", c, asmLineLengthLimit - margin));
      asm("// " + s);
      asm(extend("// ", c, asmLineLengthLimit - margin));
      asm();
   }
   
   private void snippet(String s)
   {
      asm(extend("// ", "-", snippetLength));

      if (s.length() > 0)
         asm("// " + s.trim());
   }
   
   private boolean isComment(String s)
   {
      String temp = s.trim();

      return (0 == temp.length()) || temp.startsWith("//");
   }
   
   private boolean isInstruction(String s)
   {
      s = s.trim();
      if (0 == s.length())
         return false;
      if (s.startsWith("//"))
         return false;
      if (s.startsWith("("))
         return false;
      return true;
   }
   
   private String extend(String s, String c, int length)
   {
      while (s.length() < length)
         s += c;
      return s;
   }

   //========================================================================
   // SUBROUTINE HELPER METHODS   
   //========================================================================

   private boolean useSubroutine(String command)
   {
      if (useSubroutines == 0) return false;
      if (useSubroutines == 2) return true;
      
      // Comment out commands that do NOT use subroutines
      switch (command)
      {
         // Push Commands
         case "push_constant":
         case "push_pointer":
         case "push_temp":
         case "push_local":
         case "push_argument":
         case "push_this":
         case "push_that":
         case "push_static":

         // Pop Commands
         case "pop_constant":
         case "pop_pointer":
         case "pop_temp":
         case "pop_local":
         case "pop_argument":
         case "pop_this":
         case "pop_that":
         case "pop_static":

         // ALU Commands
         case "add":
         case "sub":
         //case "neg":
         case "eq":
         case "lt":
         case "gt":
         case "and":
         case "or":
         //case "not":
         
         // Program Flow Commands
         case "goto":
         case "label":
         case "if-goto":

         // Function Calling Commands
         case "function":
         case "call":
         case "return":
         
            return true;         
      }
      
      return false;
   }
   
   private String subroutineName(String command)
   {
      return ":" + command;
   }
   
   //========================================================================
   // SUBROUTINE CODE GENERATION METHODS   
   //========================================================================

   private void writeSubroutines()
   {
      writeSubroutinesPushPop();
      writeSubroutinesArithmetic();
      writeSubroutinesProgramFlow();
      writeSubroutinesFunctionCalling();
   }
   
   private void writeSubroutinesPushPop()
   {
      header("SUBROUTINES: PUSH", "=", 0);
      
      writeSubroutinePush("constant");
      writeSubroutinePush("pointer");
      writeSubroutinePush("temp");
      writeSubroutinePush("local");
      writeSubroutinePush("argument");
      writeSubroutinePush("this");
      writeSubroutinePush("that");
      writeSubroutinePush("static");

      header("SUBROUTINES: POP", "=", 0);

      writeSubroutinePop("constant");
      writeSubroutinePop("pointer");
      writeSubroutinePop("temp");
      writeSubroutinePop("local");
      writeSubroutinePop("argument");
      writeSubroutinePop("this");
      writeSubroutinePop("that");
      writeSubroutinePop("static");
   }
   
   private void writeSubroutinesArithmetic()
   {
      header("SUBROUTINES: ARITHMETIC", "=", 0);

      writeSubroutineArithmetic("add");
      writeSubroutineArithmetic("sub");
      writeSubroutineArithmetic("neg");
      writeSubroutineArithmetic("eq");
      writeSubroutineArithmetic("lt");
      writeSubroutineArithmetic("gt");
      writeSubroutineArithmetic("and");
      writeSubroutineArithmetic("or");
      writeSubroutineArithmetic("not");
   }
   
   private void writeSubroutinesProgramFlow()
   {
      header("SUBROUTINES: PROGRAM FLOW", "=", 0);

      writeSubroutineLabel();
      writeSubroutineGoto();
      writeSubroutineIf();
   }
   
   private void writeSubroutinesFunctionCalling()
   {
      header("SUBROUTINES: FUNCTION CALLING", "=", 0);

      writeSubroutineFunction();
      writeSubroutineCall();
      writeSubroutineReturn();
   }
   
   private void writeSubroutinePush(String segment)
   {
      switch (segment)
      {
         case "local":
         case "argument":
         case "this":
         case "that":
            subroutineEntryPoint("push_" + segment);
            pushIndirect(segment);
            returnFromSubroutine();
         break;
      }
   }
   
   private void writeSubroutinePop(String segment)
   {
      switch (segment)
      {
         case "local":
         case "argument":
         case "this":
         case "that":
            subroutineEntryPoint("pop_" + segment);
            popIndirect(segment);
            returnFromSubroutine();
         break;
      }
   }
   
   private void writeSubroutineArithmetic(String command)
   {
      subroutineEntryPoint(command);
      inlineArithmetic(command);
      returnFromSubroutine();
   }

   private void writeSubroutineLabel()
   {
   }
   
   private void writeSubroutineGoto()
   {
   }
   
   private void writeSubroutineIf()
   {
   }
   
   private void writeSubroutineFunction()
   {
   }
   
   private void writeSubroutineCall()
   {
      subroutineEntryPoint("call");
      inlineCall_A();
      
      snippet("push nArgs");
      asm("@ R13");
      asm("A = M");
      asm("D = A");
      pushD();
      
      inlineCall_B();
      returnFromSubroutine();
   }

   private void writeSubroutineReturn()
   {
      subroutineEntryPoint("return");
      inlineReturn();
      returnFromSubroutine();
   }

   //========================================================================
   // SUBROUTINE CODE GENERATION HELPER METHODS   
   //========================================================================

   private void subroutineEntryPoint(String subroutineName)
   {
      header("SUBROUTINE: " + subroutineName, "=", 0);
      asm("( " + subroutineName(subroutineName) + " )");
   }
   
   private void callSubroutine(String subroutineName)
   {
      String subroutineReturnLabel = nextLocalLabel();

      snippet("call");
      asm("@ " + subroutineReturnLabel);
      asm("D = A");
      asm("@ R15");
      asm("M = D");
      asm("@ " + subroutineName(subroutineName));
      asm("0; JMP");
      asm("( " + subroutineReturnLabel + ")");
   }

   private void returnFromSubroutine()
   {
      snippet("return");
      asm("@ R15");
      asm("A = M");
      asm("0; JMP");
   }

   //========================================================================
   // SUBROUTINE CALLS FOR ALL COMMANDS
   //========================================================================
   
   private void callPushPop(String command, String segment, int index)
   {
      if (command.equals("C_PUSH"))
      {
         switch (segment)
         {
            case "constant":
               inlinePushPop(command, segment, index);
               break;            
            case "pointer":
               inlinePushPop(command, segment, index);
               break;            
            case "temp":
               inlinePushPop(command, segment, index);
               break;            
            case "argument":
            case "local":
            case "this":
            case "that":
               snippet("R13 = index");
               asm("@ " + index);
               asm("D = A");
               asm("@ R13");
               asm("M = D");
               callSubroutine("push_" + segment);
               break;
            case "static":
               inlinePushPop(command, segment, index);
               break;            
            default:
         }
      }

      if (command.equals("C_POP"))
      {
         switch (segment)
         {
            case "constant":
               inlinePushPop(command, segment, index);
               break;  
                         
            case "pointer":
               inlinePushPop(command, segment, index);
               break;            
               
            case "temp":
               inlinePushPop(command, segment, index);
               break;            
               
            case "argument":
            case "local":
            case "this":
            case "that":
               snippet("R13 = index");
               asm("@ " + index);
               asm("D = A");
               asm("@ R13");
               asm("M = D");
               callSubroutine("pop_" + segment);
               break;
               
            case "static":
               inlinePushPop(command, segment, index);
               break;            
               
            default:
         }
      }
   }
   
   private void callArithmetic(String command)
   {
      callSubroutine(command);
   }
   
   private void callLabel(String label)
   {
      inlineLabel(label);
   }
   
   private void callGoto(String label)
   {
      inlineGoto(label);
   }
   
   private void callIf(String label)
   {
      inlineIf(label);
   }
   
   private void callFunction(String functionName, int nLcls)
   {
      this.functionName = functionName;
      
      asm("( " + this.functionName + " )");
      
      snippet("clear local segment");
      asm("D = 0");
      for (int i = 0; i < nLcls; i++)
         pushD();
   }
   
   private void callCall(String functionName, int nArgs)
   {
      String thisReturnAddress = nextLocalLabel();

      snippet("R13 = nArgs");
      asm("@ " + nArgs);
      asm("D = A");
      asm("@ R13");
      asm("M = D");

      snippet("Push RA"); 
      asm("@ " + thisReturnAddress);
      asm("D = A");
      pushD();

      callSubroutine("call");
      
      snippet("goto functionName");
      asm("@ " + functionName);
      asm("0; JMP");

      snippet("( RA )");
      asm("( " + thisReturnAddress + ") ");
   }
   
   private void callReturn()
   {
      callSubroutine("return");
   }

   //========================================================================
   // INLINE CODE FOR ALL COMMANDS
   //========================================================================
   
   private void inlinePushPop(String command, String segment, int index)
   {
      if (command.equals("C_PUSH"))
      {
         switch (segment)
         {
            case "constant":
               asm("@ " + index);
               asm("D = A");
               pushD();
               break;            
      
            case "pointer":
               asm("@ " + (3 + index));
               asm("D = M");
               pushD();
               break;
               
            case "temp":
               asm("@ " + (5 + index));
               asm("D = M");
               pushD();
               break;

            case "local":
            case "argument":
            case "this":
            case "that":
               snippet("R13 = index");
               asm("@ " + index);
               asm("D = A");
               asm("@ R13");
               asm("M = D");
   
               pushIndirect(segment);
               break;

            case "static":
               snippet("D = static[index]");
               asm("@ " + staticLabel(index));
               asm("D = M");
               
               pushD();
               break;               
         }
      }

      if (command.equals("C_POP"))
      {
         switch (segment)
         {
            case "constant":
               popD();
               break;            
      
            case "pointer":
               popD();
               snippet("");
               asm("@ " + (3 + index));
               asm("M = D");
               break;
               
            case "temp":
               popD();
               snippet("");
               asm("@ " + (5 + index));
               asm("M = D");
               break;

            case "local":
            case "argument":
            case "this":
            case "that":
               snippet("R13 = index");
               asm("@ " + index);
               asm("D = A");
               asm("@ R13");
               asm("M = D");
               
               popIndirect(segment);
               break;

            case "static":
               popD();
               snippet("static[index] = D");
               asm("@ " + staticLabel(index));
               asm("M = D");
               break;               
         }
      }
  
   }

   private void pushIndirect(String segment)
   {
      String base = "";
      
      switch(segment)
      {
         case "local"   : base = "LCL"; break;
         case "argument": base = "ARG"; break;
         case "this"    : base = "THIS"; break;
         case "that"    : base = "THAT"; break;
      }
      
      snippet("R13 = &segment[N] = segment+N");
      asm("@ R13");
      asm("D = M");
      asm("@ " + base);
      asm("A = M");
      asm("D = D+A");
      asm("@ R13");
      asm("M = D");
      
      snippet("D = *R13");  
      asm("@ R13");
      asm("A = M");
      asm("D = M");
      
      pushD();
   }

   private void popIndirect(String segment)
   {
      String base = "";
      
      switch(segment)
      {
         case "local"   : base = "LCL"; break;
         case "argument": base = "ARG"; break;
         case "this"    : base = "THIS"; break;
         case "that"    : base = "THAT"; break;
      }
      
      popD();
      
      snippet("R14 = D");
      asm("@ R14");
      asm("M = D");
      
      snippet("R13 = &segment[N] = segment+N");
      asm("@ R13");
      asm("D = M");
      asm("@ " + base);
      asm("A = M");
      asm("D = D+A");
      asm("@ R13");
      asm("M = D");
      
      snippet("D = R14");
      asm("@ R14");
      asm("D = M");
      
      snippet("*R13 = D");
      asm("@ R13");
      asm("A = M");
      asm("M = D");
   }

   private void inlineArithmetic(String command)
   {
      // Arithmetic commands
      if (command.equals("neg"))
      {
         if (useOptimized)
         {
            asm("@ SP");
            asm("A = M-1");
            asm("M = -M");
         }
         else
         {
            popD();
            
            snippet("D = -D");
            asm("D = -D");
            
            pushD();
         }
      }

      if (command.equals("add"))
      {
         popD();
         
         snippet("R14 = D");
         asm("@ R14");
         asm("M = D");
         
         popD();
         
         snippet("D += R14");
         asm("@ R14");
         asm("D = D+M");
         
         pushD();
      }
      
      if (command.equals("sub"))
      {
         popD();

         snippet("R14 = D");
         asm("@ R14");
         asm("M = D");
         
         popD();
         
         snippet("D -= R14");
         asm("@ R14");
         asm("D = D-M");
         
         pushD();
      }
      
      // Logical commands
      if (command.equals("not"))
      {
         if (useOptimized)
         {
            asm("@ SP");
            asm("A = M-1");
            asm("M = !M");
         }
         else
         {
            popD();
            
            snippet("D = !D");
            asm("D = !D");
            
            pushD();
         }
      }
      
      if (command.equals("and"))
      {
         popD();
         
         snippet("R14 = D");
         asm("@ R14");
         asm("M = D");
         
         popD();
         
         snippet("D &= R14");
         asm("@ R14");
         asm("D = D&M");
         
         pushD();
      }

      if (command.equals("or"))
      {
         popD();
         
         snippet("R14 = D");
         asm("@ R14");
         asm("M = D");
         
         popD();
         
         snippet("D |= R14");
         asm("@ R14");
         asm("D = D|M");
         
         pushD();
      }
      
      // Relational commands
      if (command.equals("eq"))
      {
         String label_TRUE = nextLocalLabel();
         String label_END = nextLocalLabel();
         
         popD();
         
         snippet("R14 = D");
         asm("@ R14");
         asm("M = D");
         
         popD();
         
         snippet("D -= R14");
         asm("@ R14");
         asm("D = D-M");
         
         snippet("D == 0");
         asm("@ " + label_TRUE);
         asm("D; JEQ");
         asm("D = 0");
         asm("@ " + label_END);
         asm("0; JMP");
         asm("(" + label_TRUE + ")");
         asm("D = -1");
         asm("(" + label_END + ")");
         
         pushD();
      }
      
      if (command.equals("lt"))
      {
         String label_TRUE = nextLocalLabel();
         String label_END = nextLocalLabel();
         
         popD();
         
         snippet("R14 = D");
         asm("@ R14");
         asm("M = D");
         
         popD();
         
         snippet("D -= R14");
         asm("@ R14");
         asm("D = D-M");
         
         snippet("D == 0");
         asm("@ " + label_TRUE);
         asm("D; JLT");
         asm("D = 0");
         asm("@ " + label_END);
         asm("0; JMP");
         asm("(" + label_TRUE + ")");
         asm("D = -1");
         asm("(" + label_END + ")");
         
         pushD();
      }
      
      if (command.equals("gt"))
      {
         String label_TRUE = nextLocalLabel();
         String label_END = nextLocalLabel();
         
         popD();
         
         snippet("R14 = D");
         asm("@ R14");
         asm("M = D");
         
         popD();
         
         snippet("D -= R14");
         asm("@ R14");
         asm("D = D-M");
         
         snippet("D == 0");
         asm("@ " + label_TRUE);
         asm("D; JGT");
         asm("D = 0");
         asm("@ " + label_END);
         asm("0; JMP");
         asm("(" + label_TRUE + ")");
         asm("D = -1");
         asm("(" + label_END + ")");
         
         pushD();
      }
   }
   
   private void inlineLabel(String label)
   {
      asm("( " + vmLabel(label) + " )");
   }
   
   private void inlineGoto(String label)
   {
      asm("@ " + vmLabel(label));
      asm("0; JMP");
   }
   
   private void inlineIf(String label)
   {
      popD();
      snippet("");
      asm("@ " + vmLabel(label));
      asm("D; JNE");
   }
   
   private void inlineFunction(String functionName, int nLcls)
   {
      this.functionName = functionName;
      
      asm("( " + this.functionName + " )");
      
      snippet("clear local segment"); 
      asm("D = 0");
      for (int i = 0; i < nLcls; i++)
         pushD();
   }

   private void inlineCall(String functionName, int nArgs)
   {
      String thisReturnAddress = nextLocalLabel();

      snippet("Push RA");
      asm("@ " + thisReturnAddress);
      asm("D = A");
      pushD();

      inlineCall_A();
      
      snippet("push constant <nArgs>");
      asm("@ " + nArgs);
      asm("D = A");
      pushD();

      inlineCall_B();
      
      snippet("goto functionName");
      asm("@ " + functionName);
      asm("0; JMP");
      snippet("( RA )"); 
      asm("( " + thisReturnAddress + ") ");
   }

   private void inlineCall_A()
   {
      snippet("push LCL");
      asm("@ LCL");
      asm("D = M");
      pushD();
      
      snippet("push ARG");
      asm("@ ARG");
      asm("D = M");
      pushD();
      
      snippet("push THIS");
      asm("@ THIS");
      asm("D = M");
      pushD();

      snippet("push THAT");
      asm("@ THAT");
      asm("D = M");
      pushD();
      
      snippet("push SP");
      asm("@ SP");
      asm("D = M");
      pushD();
   }
   
   private void inlineCall_B()
   {
      snippet("sub");
      inlineArithmetic("sub");

      snippet("push constant 5");
      asm("@ " + 5);
      asm("D = A");
      pushD();
      
      snippet("sub");
      inlineArithmetic("sub");

      snippet("pop ARG");
      popD();
      asm("@ ARG");
      asm("M = D");
      
      snippet("push SP");
      asm("@ SP");
      asm("D = M");
      pushD();
      
      snippet("pop LCL");
      popD();
      asm("@ LCL");
      asm("M = D");
   }
   
   private void inlineReturn()
   {
      snippet("FRAME = LCL");
      asm("@ LCL");
      asm("D = M");
      asm("@ R14");
      asm("M = D");

      snippet("RA = *(FRAME-5)");
      asm("@ 5");
      asm("D = A");
      asm("@ R14");
      asm("A = M");
      asm("A = A-D");
      asm("D = M");
      asm("@ R13");
      asm("M = D");

      snippet("*ARG = pop()");
      popD();
      asm("@ ARG");
      asm("A = M");
      asm("M = D");

      snippet("SP = ARG+1");
      asm("@ ARG");
      asm("D = M");
      asm("D = D+1");
      asm("@ SP");
      asm("M = D");

      snippet("THAT = *(FRAME-1)");
      asm("@ 1");
      asm("D = A");
      asm("@ R14");
      asm("A = M");
      asm("A = A-D");
      asm("D = M");
      asm("@ THAT");
      asm("M = D");

      snippet("THIS = *(FRAME-2)");
      asm("@ 2");
      asm("D = A");
      asm("@ R14");
      asm("A = M");
      asm("A = A-D");
      asm("D = M");
      asm("@ THIS");
      asm("M = D");

      snippet("ARG = *(FRAME-3)");
      asm("@ 3");
      asm("D = A");
      asm("@ R14");
      asm("A = M");
      asm("A = A-D");
      asm("D = M");
      asm("@ ARG");
      asm("M = D");

      snippet("LCL = *(FRAME-4)");
      asm("@ 4");
      asm("D = A");
      asm("@ R14");
      asm("A = M");
      asm("A = A-D");
      asm("D = M");
      asm("@ LCL");
      asm("M = D");

      snippet("goto RET");
      asm("@ R13");
      asm("A = M");
      asm("0; JMP");

   }
   
   //========================================================================
   // CODE MACROS   
   //========================================================================

   //* Only affects A 
   private void pushD()
   {
      if (useOptimized)
      {
         snippet("push D");
         asm("@ SP");
         asm("AM = M+1");
         asm("A = A-1");
         asm("M = D");
      }
      else
      {
         asm("@ SP");
         asm("A = M");
         asm("M = D");
         asm("@ SP");
         asm("M = M+1");
      }
   }
   
   //* Affects both D and A
   private void popD()
   {
      if (useOptimized)
      {
         snippet("pop D");
         asm("@ SP");
         asm("AM = M-1");
         asm("D = M");
      }
      else
      {
         asm("@ SP");
         asm("A = M");
         asm("A = A-1");
         asm("D = M");
         asm("@ SP");
         asm("M = M-1");
      }
      
   }
  
   //========================================================================
   
}