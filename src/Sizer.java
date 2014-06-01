/* -*- mode: jde; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Sizer - computes the size of a .hex file
  Part of the Arduino project - http://www.arduino.cc/

  Copyright (c) 2006 David A. Mellis

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
  
  $Id$
*/

package processing.app.debug;

import processing.app.Base;

import java.io.*;
import java.util.*;

public class Sizer implements MessageConsumer {
  private String buildPath, sketchName;
  private String firstLine;
  private long code_size, data_size;
  private RunnerException exception;

  public Sizer(String buildPath, String sketchName) {
    this.buildPath = buildPath;
    this.sketchName = sketchName;
  }
  
  public long computeSize() throws RunnerException {
    String avrBasePath = Base.getAvrBasePath();
    String sizeCommand = Base.getBoardPreferences().get("build.command.objdump");
    if (sizeCommand == null) sizeCommand = "avr-objdump";
    String commandSize[] = new String[] {
      avrBasePath + sizeCommand,
      "-h",
      buildPath + File.separator + sketchName + ".elf"
    };

    try {
      exception = null;
      code_size = -1;
      data_size = 0;
      firstLine = null;
      Process process = Runtime.getRuntime().exec(commandSize);
      MessageSiphon in = new MessageSiphon(process.getInputStream(), this);
      MessageSiphon err = new MessageSiphon(process.getErrorStream(), this);
      boolean running = true;
      while(running) {
        try {
          in.join();
          err.join();
          process.waitFor();
          running = false;
        } catch (InterruptedException intExc) { }
      }
    } catch (Exception e) {
      // The default Throwable.toString() never returns null, but apparently
      // some sub-class has overridden it to do so, thus we need to check for
      // it.  See: http://www.arduino.cc/cgi-bin/yabb2/YaBB.pl?num=1166589459
      exception = new RunnerException(
        (e.toString() == null) ? e.getClass().getName() : e.toString());
    }
    
    if (exception != null)
      throw exception;
      
    if (code_size == -1)
      throw new RunnerException(firstLine);
      
    return code_size+1;
  }
 
  public long getDataSize() {
    return data_size;
  } 

  public void message(String s) {
    if (firstLine == null) firstLine = s;
    try {
      String field[] = s.trim().split("\\s+", 4);
      Integer.parseInt(field[0]);
      int n = Integer.parseInt(field[2], 16);
      if (field[1].equals(".text")) {
          code_size += n;
          //System.out.println("Sizer (code): " + field[1] + " " + field[2]);
      } else if (field[1].equals(".data")
        || field[1].equals(".bss")
        || field[1].equals(".noinit")
        || field[1].equals(".usbdescriptortable")
        || field[1].equals(".usbbuffers")
        || field[1].equals(".dmabuffers")) {
          data_size += n;
          //System.out.println("Sizer (data): " + field[1] + " " + field[2]);
      }
    } catch (Exception e) {
    }
  }
}
