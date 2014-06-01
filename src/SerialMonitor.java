/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
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
*/

package processing.app;

import processing.app.debug.MessageConsumer;
import processing.core.*;
import static processing.app.I18n._;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class SerialMonitor extends JFrame implements MessageConsumer {
  private Serial serial;
  private String port;
  private JTextArea textArea;
  private JScrollPane scrollPane;
  private JTextField textField;
  private JButton sendButton;
  private JCheckBox autoscrollBox;
  private JComboBox lineEndings;
  private JComboBox serialRates;
  private int serialRate;

  public SerialMonitor(String port) {
    super(port);
  
    this.port = port;
  
    addWindowListener(new WindowAdapter() {
        public void windowClosing(WindowEvent e) {
          closeSerialPort();
        }
      });  
      
    // obvious, no?
    KeyStroke wc = Editor.WINDOW_CLOSE_KEYSTROKE;
    getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(wc, "close");
    getRootPane().getActionMap().put("close", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        closeSerialPort();
        setVisible(false);
      }});
  
    getContentPane().setLayout(new BorderLayout());
    
    Font consoleFont = Theme.getFont("console.font");
    Font editorFont = Preferences.getFont("editor.font");
    Font font = new Font(consoleFont.getName(), consoleFont.getStyle(), editorFont.getSize());

    textArea = new JTextArea(16, 40);
    textArea.setEditable(false);    
    textArea.setFont(font);
    
    // don't automatically update the caret.  that way we can manually decide
    // whether or not to do so based on the autoscroll checkbox.
    ((DefaultCaret)textArea.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
    
    scrollPane = new JScrollPane(textArea);
    
    getContentPane().add(scrollPane, BorderLayout.CENTER);
    
    JPanel pane = new JPanel();
    pane.setLayout(new BoxLayout(pane, BoxLayout.X_AXIS));
    pane.setBorder(new EmptyBorder(4, 4, 4, 4));

    textField = new JTextField(40);
    textField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        send(textField.getText());
        textField.setText("");
      }});

    sendButton = new JButton(_("Send"));
    sendButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        send(textField.getText());
        textField.setText("");
      }});
    
    pane.add(textField);
    pane.add(Box.createRigidArea(new Dimension(4, 0)));
    pane.add(sendButton);
    
    getContentPane().add(pane, BorderLayout.NORTH);
    
    pane = new JPanel();
    pane.setLayout(new BoxLayout(pane, BoxLayout.X_AXIS));
    pane.setBorder(new EmptyBorder(4, 4, 4, 4));
    
    autoscrollBox = new JCheckBox(_("Autoscroll"), true);
    
    lineEndings = new JComboBox(new String[] { _("No line ending"), _("Newline"), _("Carriage return"), _("Both NL & CR") });
    lineEndings.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
      	Preferences.setInteger("serial.line_ending", lineEndings.getSelectedIndex());
      }
    });
    if (Preferences.get("serial.line_ending") != null) {
      lineEndings.setSelectedIndex(Preferences.getInteger("serial.line_ending"));
    }
    lineEndings.setMaximumSize(lineEndings.getMinimumSize());
      
    String[] serialRateStrings = {
      "300","1200","2400","4800","9600","14400",
      "19200","28800","38400","57600","115200"
    };
    
    serialRates = new JComboBox();
    for (int i = 0; i < serialRateStrings.length; i++)
      serialRates.addItem(serialRateStrings[i] + " " + _("baud"));

    serialRate = Preferences.getInteger("serial.debug_rate");
    serialRates.setSelectedItem(serialRate + " " + _("baud"));
    serialRates.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        String wholeString = (String) serialRates.getSelectedItem();
        String rateString = wholeString.substring(0, wholeString.indexOf(' '));
        serialRate = Integer.parseInt(rateString);
        Preferences.set("serial.debug_rate", rateString);
        closeSerialPort();
        try {
          openSerialPort();
        } catch (SerialException e) {
          System.err.println(e);
        }
      }});
      
    serialRates.setMaximumSize(serialRates.getMinimumSize());

    pane.add(autoscrollBox);
    pane.add(Box.createHorizontalGlue());
    pane.add(lineEndings);
    pane.add(Box.createRigidArea(new Dimension(8, 0)));
    pane.add(serialRates);
    
    getContentPane().add(pane, BorderLayout.SOUTH);

    pack();
    
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    if (Preferences.get("last.screen.height") != null) {
      // if screen size has changed, the window coordinates no longer
      // make sense, so don't use them unless they're identical
      int screenW = Preferences.getInteger("last.screen.width");
      int screenH = Preferences.getInteger("last.screen.height");
      if ((screen.width == screenW) && (screen.height == screenH)) {
        String locationStr = Preferences.get("last.serial.location");
        if (locationStr != null) {
          int[] location = PApplet.parseInt(PApplet.split(locationStr, ','));
          setPlacement(location);
        }
      }
    }
  }
  
  protected void setPlacement(int[] location) {
    setBounds(location[0], location[1], location[2], location[3]);
  }

  protected int[] getPlacement() {
    int[] location = new int[4];

    // Get the dimensions of the Frame
    Rectangle bounds = getBounds();
    location[0] = bounds.x;
    location[1] = bounds.y;
    location[2] = bounds.width;
    location[3] = bounds.height;

    return location;
  }

  private void send(String s) {
    if (serial != null) {
      switch (lineEndings.getSelectedIndex()) {
        case 1: s += "\n"; break;
        case 2: s += "\r"; break;
        case 3: s += "\r\n"; break;
      }
      serial.write(s);
    }
  }
  
  public void openSerialPort() throws SerialException {
    if (serial != null) return;
    if (Base.isTeensyduino() == false) {
      serial = new Serial(port, serialRate);
    } else {
      // only do this weird stuff if we're sure it's teensy!
      if (Base.getBoardMenuPreferenceBoolean("serial.restart_cmd")) {
        RestartCommand r = new RestartCommand(port);
        serial = r.getSerial();
      }
      String fake = Base.getBoardMenuPreference("fake_serial");
      if (fake == null) {
        if (Base.getBoardMenuPreferenceBoolean("serial.safe_baud_rates_only")) {
          if (serialRate == 14400) serialRate = 19200;
          if (serialRate == 28800) serialRate = 38400;
        }
        if (serial == null) {
          serial = new Serial(port, serialRate);
        } else {
          serial.setBaud(serialRate);
        }
      } else {
        serial = new FakeSerial(fake);
      }
    }
    serial.addListener(this);
  }
  
  public void closeSerialPort() {
    if (serial != null) {
      int[] location = getPlacement();
      String locationStr = PApplet.join(PApplet.str(location), ",");
      Preferences.set("last.serial.location", locationStr);
      textArea.setText("");
      serial.dispose();
      serial = null;
    }
  }
  
  public void message(final String s) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        textArea.append(s);
        if (autoscrollBox.isSelected()) {
        	textArea.setCaretPosition(textArea.getDocument().getLength());
        }
      }});
  }
}

class FakeSerial extends Serial {
       Socket sock=null;
       inputListener listener=null;
       int[] addrlist = {28541,4984,18924,16924,27183,31091};
       static Process gateway=null;
       static boolean gateway_shutdown_scheduled=false;

       public FakeSerial(String name) throws SerialException {
               super("fake serial");
               int attempt=1;
               do {
                       if (gateway_connect(name)) return;
                       if (attempt <= 2 && !gateway_start(name)) {
                               System.err.println("Error starting " + name);
                       }
                       delay_20ms();
               } while (++attempt < 4);
               throw new SerialException("no connection");
       }

        private boolean gateway_connect(String name) {
                int namelen = name.length();
                byte[] buf = new byte[namelen];
                byte[] namebuf = name.getBytes();
                InetAddress local;
                try {
                        byte[] loop = new byte[] {127, 0, 0, 1};
                        local = InetAddress.getByAddress("localhost", loop);
                } catch (Exception e) {
                        sock = null;
                        return false;
                }
                for (int i=0; i<addrlist.length; i++) {
                        try {
                                sock = new Socket();
                                InetSocketAddress addr = new InetSocketAddress(local, addrlist[i]);
                                sock.connect(addr, 50); // if none, should timeout instantly
                                                        // but windows will wait up to 1 sec!
                                input = sock.getInputStream();
                                output = sock.getOutputStream();
                        } catch (Exception e) {
                                sock = null;
                                return false;
                        }
                        // check for welcome message
                        try {
                                int wait = 0;
                                while (input.available() < namelen) {
                                        if (++wait > 6) throw new Exception();
                                        delay_20ms();
                                }
                                input.read(buf, 0, namelen);
                                String id = new String(buf, 0, namelen);
                                for (int n=0; n<namelen; n++) {
                                        if (buf[n] !=  namebuf[n]) throw new Exception();
                                }
                        } catch (Exception e) {
                                // mistakenly connected to some other program!
                                close_sock();
                                continue;
                        }
                        return true;
                }
                sock = null;
                return false;
        }

        private void close_sock() {
                try {
                        sock.close();
                } catch (Exception e) { }
                sock = null;
        }

        private void delay_20ms() {
                try {
                        Thread.sleep(20);
                } catch (Exception e) { }
        }

        public void dispose() {
                if (listener != null) {
                        listener.interrupt();
                        listener.consumer = null;
                        listener = null;
                }
                if (sock != null) {
                        try {
                                sock.close();
                        } catch (Exception e) { }
                        sock = null;
                }
               dispose_gateway();
        }

       public static void dispose_gateway() {
               if (gateway != null) {
                       gateway.destroy();
                       gateway = null;
               }
       }


        private boolean gateway_start(String cmd) {
                String path = Base.getHardwarePath() + "/tools/";
                try {
                        gateway = Runtime.getRuntime().exec(path + cmd);
                       if (!gateway_shutdown_scheduled) {
                               Runtime.getRuntime().addShutdownHook(new Thread() {
                                       public void run() {
                                               FakeSerial.dispose_gateway();
                                       }
                               });
                               gateway_shutdown_scheduled = true;
                       }
                } catch (Exception e) {
                       gateway = null;
                        return false;
                }
                return true;
        }

       public void addListener(MessageConsumer c) {
               if (sock == null) return;
               if (listener != null) listener.interrupt();
               listener = new inputListener();
               listener.input = input;
               listener.consumer = c;
               listener.start();
       }

       public void write(byte bytes[]) {
               if (output == null) return;
               if (bytes.length > 0) {
                       try {
                               output.write(bytes, 0, bytes.length);
                       } catch (IOException e) { }
               }
       }
       public void setDTR(boolean state) {
       }
       static public ArrayList<String> list() {
               return new ArrayList<String>();
       }
}

class RestartCommand {
       private Process restarter=null;
       private Serial s=null;
       public RestartCommand(String port) {
               if (Base.getBoardMenuPreference("fake_serial") == null) {
                       try {
                               s = new Serial(port, 150);
                       } catch (SerialException e) {
                               s = null;
                       }
               } else {
                       String path = Base.getHardwarePath() + "/tools/";
                       try {
                               restarter = Runtime.getRuntime().exec(path + "teensy_restart");
                       } catch (Exception e) {
                       }
               }
       }
       public Serial getSerial() {
               return s;
       }
}

class inputListener extends Thread {
       MessageConsumer consumer;
       InputStream input;

       public void run() {
               byte[] buffer = new byte[1024];
               int num, errcount=0;
               try {
                       while (true) {
                               num = input.read(buffer);
                               if (num <= 0) break;
                               consumer.message(new String(buffer, 0, num));
                       }
               } catch (Exception e) { }
       }
}
