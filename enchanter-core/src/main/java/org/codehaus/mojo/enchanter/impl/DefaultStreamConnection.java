package org.codehaus.mojo.enchanter.impl;

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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.OperationNotSupportedException;

import org.codehaus.mojo.enchanter.ConnectionLibrary;
import org.codehaus.mojo.enchanter.StreamConnection;
import org.codehaus.mojo.enchanter.StreamListener;

/**
 * Default implementation of StreamConnection connection and parsing methods
 */
public class DefaultStreamConnection
    implements StreamConnection
{

    private BufferedInputStream in;

    private PrintWriter out;

    private Map<String, Response> respondWith = new HashMap<String, Response>();

    private List<Prompt> waitFor = new ArrayList<Prompt>();

    List<StreamListener> streamListeners = new ArrayList<StreamListener>();

    private String endOfLine = "\r\n";

    private char lastChar;

    private boolean alive = true;

    private StringBuilder lastLine = new StringBuilder();

    private Thread timeoutThread;

    private int timeout = 200;

    private ConnectionLibrary connectionLibrary;

    private StreamListener debugStreamListener = null;

    public DefaultStreamConnection()
    {
    }

    public DefaultStreamConnection( ConnectionLibrary connLib )
    {
        this.connectionLibrary = connLib;
    }

    @Override
    public void connect( String host )
        throws IOException
    {
        try
        {
            connectionLibrary.connect( host );
        }
        catch ( OperationNotSupportedException e )
        {
            throw new RuntimeException( e );
        }
        setupStreams();
    }

    @Override
    public void connect( String host, int port )
        throws IOException
    {
        try
        {
            connectionLibrary.connect( host, port );
        }
        catch ( OperationNotSupportedException e )
        {
            throw new RuntimeException( e );
        }
        setupStreams();
    }

    @Override
    public void connect( String host, String username )
        throws IOException
    {
        try
        {
            connectionLibrary.connect( host, username );
        }
        catch ( OperationNotSupportedException e )
        {
            throw new RuntimeException( e );
        }
        setupStreams();
    }

    // no scope so that ExecConnectionLibrary can set up its password
    void setupStreams()
    {
        this.in = new BufferedInputStream( connectionLibrary.getInputStream() );
        this.out = new PrintWriter( connectionLibrary.getOutputStream() );
        for ( StreamListener listener : streamListeners )
        {
            listener.init( this.out );
        }
    }

    @Override
    public void connect( String host, int port, String username, final String password )
        throws IOException
    {
        try
        {
            connectionLibrary.connect( host, port, username, password );
        }
        catch ( OperationNotSupportedException e )
        {
            throw new RuntimeException( e );
        }
        setupStreams();
    }

    @Override
    public void connect( String host, int port, String username, final String password, String privateKeyPath )
        throws IOException
    {
        try
        {
            connectionLibrary.connect( host, port, username, password, privateKeyPath );
        }
        catch ( OperationNotSupportedException e )
        {
            throw new RuntimeException( e );
        }
        setupStreams();
    }

    @Override
    public void setEndOfLine( String eol )
    {
        this.endOfLine = eol;
    }

    public void setConnectionLibrary( ConnectionLibrary lib )
    {
        this.connectionLibrary = lib;
    }

    @Override
    public void disconnect()
        throws IOException
    {
        if ( timeoutThread != null )
        {
            timeoutThread.interrupt();
        }
        alive = false;
        connectionLibrary.disconnect();
    }

    @Override
    public void addStreamListener( StreamListener listener )
    {
        streamListeners.add( listener );
    }

    @Override
    public void removeStreamListener( StreamListener listener )
    {
        streamListeners.remove( listener );
    }

    @Override
    public void setDebug( boolean debug )
    {
        if ( this.debugStreamListener == null )
        {
            this.debugStreamListener = new DebugStreamListener();
        }

        if ( debug )
        {
            removeStreamListener( this.debugStreamListener ); // dont want duplicate
            addStreamListener( debugStreamListener );
        }
        else
        {
            removeStreamListener( this.debugStreamListener );
        }
    }

    @Override
    public void send( String text )
        throws IOException
    {
        print( text, false );

    }

    @Override
    public void sendLine( String text )
        throws IOException
    {
        print( text, true );
    }

    @Override
    public void sleep( int millis )
        throws InterruptedException
    {
        Thread.sleep( millis );
    }

    private void print( String text, boolean eol )
        throws IOException
    {
        text = text.replace( "^C", String.valueOf( (char) 3 ) );
        text = text.replace( "^M", endOfLine );
        if ( eol )
        {
            out.print( text + endOfLine );
            out.flush();
            getLine();
        }
        else
        {
            out.print( text );
            out.flush();
        }
        byte[] bytes = text.getBytes();
        for ( StreamListener listener : streamListeners )
        {
            listener.hasWritten( bytes );
        }

    }

    @Override
    public void respond( String prompt, String response )
    {
        if ( response == null )
        {
            respondWith.remove( prompt );
        }
        else
        {
            respondWith.put( prompt, new Response( prompt, response ) );
        }
    }

    @Override
    public boolean waitFor( String waitFor )
        throws IOException
    {
        return waitFor( waitFor, false );
    }

    @Override
    public boolean waitFor( String waitFor, boolean readLineOnMatch )
        throws IOException
    {
        prepare( new String[] { waitFor } );
        return ( readFromStream( readLineOnMatch ) == 0 );
    }

    @Override
    public int waitForMux( String... waitFor )
        throws IOException
    {
        return waitForMux( waitFor, false );
    }

    @Override
    public int waitForMux( String[] waitFor, boolean readLineOnMatch )
        throws IOException
    {
        prepare( waitFor );
        return readFromStream( readLineOnMatch );
    }

    @Override
    public void setTimeout( int timeout )
    {
        this.timeout = timeout;
    }

    @Override
    public int getTimeout()
    {
        return this.timeout;
    }

    protected void prepare( String[] text )
    {
        this.alive = true;
        for ( String val : text )
        {
            waitFor.add( new Prompt( val ) );
        }
        this.lastLine.setLength( 0 );
    }

    @Override
    public String lastLine()
    {
        return this.lastLine.toString();
    }

    @Override
    public String getLine()
        throws IOException
    {
        if ( waitFor( endOfLine, false ) )
        {
            return lastLine();
        }
        return null;
    }

    private int read( byte[] data )
        throws IOException
    {
        int length = 0;

        while ( alive )
        {
            if ( in.available() == 0 )
            {
                try
                {
                    Thread.sleep( 100 );
                }
                catch ( InterruptedException e )
                {

                }
            }
            else
            {
                length = in.read( data );
                if ( length > 0 )
                {
                    break;
                }
            }
        }

        return length;
    }

    public void clear()
        throws IOException
    {
        while ( in.available() > 0 )
        {
            in.read();
        }

    }

    public int readFromStream( boolean readLineOnMatch )
        throws IOException
    {
        int result = -1;
        byte[] data = new byte[1];
        int length = 0;
        boolean readTillEndOfLine = false;
        if ( timeout > 0 )
        {
            timeoutThread = new Thread()
            {
                public void run()
                {
                    try
                    {
                        this.setName( "TimeOut" );
                        sleep( timeout );
                    }
                    catch ( InterruptedException e )
                    {
                        return;
                    }
                    alive = false;
                }
            };
            timeoutThread.start();
        }
        outer:

        while ( alive && ( length = read( data ) ) >= 0 )
        {

            for ( int x = 0; x < length; x++ )
            {
                char c = (char) data[x];
                for ( StreamListener listener : streamListeners )
                {
                    listener.hasRead( (byte) data[x] );
                }
                if ( readTillEndOfLine && ( c == '\r' || c == '\n' ) )
                    break outer;

                int match = lookForMatch( c );
                if ( match != -1 )
                {
                    result = match;
                    if ( readLineOnMatch && ( c != '\r' && c != '\n' ) )
                    {
                        readTillEndOfLine = true;
                    }
                    else
                    {
                        break outer;
                    }
                }
                else
                {
                    lookForResponse( (char) data[x] );
                    lastChar = (char) data[x];
                }
            }
        }

        reset();
        return result;
    }

    int lookForMatch( char s )
    {
        if ( s != '\r' && s != '\n' )
            lastLine.append( s );
        for ( int m = 0; alive && m < waitFor.size(); m++ )
        {
            Prompt prompt = (Prompt) waitFor.get( m );
            if ( prompt.matchChar( s ) )
            {
                // the whole thing matched so, return the match answer
                if ( prompt.match() )
                {
                    return m;
                }
                else
                {
                    prompt.nextPos();
                }

            }
            else
            {
                // if the current character did not match reset
                prompt.resetPos();
                if ( s == '\n' || s == '\r' )
                {
                    lastLine.setLength( 0 );
                }
            }
        }
        return -1;
    }

    void lookForResponse( char s )
        throws IOException
    {
        for ( Response response : respondWith.values() )
        {
            if ( response.matchChar( s ) )
            {
                if ( response.match() )
                {
                    print( response.getResponse(), false );
                    response.resetPos();
                }
                else
                {
                    response.nextPos();
                }
            }
            else
            {
                response.resetPos();
            }
        }
    }

    void reset()
    {
        waitFor.clear();
        if ( timeout > 0 )
        {
            timeoutThread.interrupt();
        }
        alive = true;
    }

    static class Prompt
    {
        private String prompt;

        private int pos;

        public Prompt( String prompt )
        {
            this.prompt = prompt;
            this.pos = 0;
        }

        public boolean matchChar( char c )
        {
            return ( prompt.charAt( pos ) == c );
        }

        public boolean match()
        {
            return pos + 1 == prompt.length();
        }

        public String getPrompt()
        {
            return prompt;
        }

        public void nextPos()
        {
            this.pos++;
        }

        public void resetPos()
        {
            this.pos = 0;
        }

    }

    static class Response
        extends Prompt
    {
        private String response;

        public Response( String prompt, String response )
        {
            super( prompt );
            this.response = response;
        }

        public String getResponse()
        {
            return response;
        }
    }

    private class DebugStreamListener
        implements StreamListener
    {
        public void hasRead( byte b )
        {
            if ( b != '\r' )
                System.out.print( (char) b );
        }

        public void hasWritten( byte[] b )
        {
            // Not usually necessary
            // System.out.print(new String(b));
        }

        public void init( PrintWriter writer )
        {
        }
    }

}