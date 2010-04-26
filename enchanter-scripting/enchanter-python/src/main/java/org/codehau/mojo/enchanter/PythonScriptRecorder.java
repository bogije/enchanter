package org.codehau.mojo.enchanter;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

import org.codehaus.mojo.enchanter.ScriptRecorder;

/**
 * Generates a simple script for Python
 */
public class PythonScriptRecorder extends ScriptRecorder {

    private PrintWriter out;
    protected void endRecording() {
        out.println("conn.disconnect();");
        out.close();
    }

    protected void startRecording(String string, String host, int port, String username, String password) {
        try {
            out = new PrintWriter(new FileWriter(string));
        } catch (IOException e) {
            System.err.println("Unable to open file for writing: "+string);
            System.exit(1);
        }
        
        out.println("# Autogenerated script on "+new Date());
        out.println("conn.connect(\""+host+"\", "+port+", \""+username+"\", \""+password+"\");");
    }
    
    protected void writePrompt(String prompt, String response) {
        out.println("conn.waitFor(\""+prompt+"\");");
        out.println("conn.sendLine(\""+response+"\");");
    }
}
