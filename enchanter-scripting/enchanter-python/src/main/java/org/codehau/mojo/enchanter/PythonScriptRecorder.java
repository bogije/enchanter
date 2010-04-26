package org.codehau.mojo.enchanter;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

import org.codehaus.mojo.enchanter.ScriptRecorder;

/**
 * Generates a simple script for Python
 */
public class PythonScriptRecorder
    extends ScriptRecorder
{

    private PrintWriter out;

    protected void endRecording()
    {
        out.println( "conn.disconnect();" );
        out.close();
    }

    protected void startRecording( String string, String host, int port, String username, String password )
    {
        try
        {
            out = new PrintWriter( new FileWriter( string ) );
        }
        catch ( IOException e )
        {
            System.err.println( "Unable to open file for writing: " + string );
            System.exit( 1 );
        }

        out.println( "# Autogenerated script on " + new Date() );
        out.println( "conn.connect(\"" + host + "\", " + port + ", \"" + username + "\", \"" + password + "\");" );
    }

    protected void writePrompt( String prompt, String response )
    {
        out.println( "conn.waitFor(\"" + prompt + "\");" );
        out.println( "conn.sendLine(\"" + response + "\");" );
    }
}
