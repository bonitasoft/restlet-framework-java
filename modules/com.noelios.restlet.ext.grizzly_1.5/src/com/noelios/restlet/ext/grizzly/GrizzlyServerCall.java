/*
 * Copyright 2005-2007 Noelios Consulting.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License (the "License"). You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL HEADER in each file and
 * include the License file at http://www.opensource.org/licenses/cddl1.txt If
 * applicable, add the following below this CDDL HEADER, with the fields
 * enclosed by brackets "[]" replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */

package com.noelios.restlet.ext.grizzly;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.logging.Level;

import org.restlet.Server;
import org.restlet.data.Response;
import org.restlet.resource.Representation;

import com.noelios.restlet.http.HttpServerCall;
import com.sun.grizzly.util.ByteBufferInputStream;
import com.sun.grizzly.util.OutputWriter;
import com.sun.grizzly.util.SSLOutputWriter;
import com.sun.grizzly.util.SSLByteBufferInputStream;

/**
 * HTTP server call specialized for Grizzly.
 * 
 * @author Jerome Louvel (contact@noelios.com)
 */
public class GrizzlyServerCall extends HttpServerCall {
    /** The underlying socket channel. */
    private SocketChannel socketChannel;

    /** Recycled unsecure stream. */
    private ByteBufferInputStream unsecureHeadStream = new ByteBufferInputStream();

    /** Recycled secure stream. */
    private ByteBufferInputStream secureHeadStream = new SSLByteBufferInputStream();

    /** Recycled stream. */
    private ByteBufferInputStream headStream;

    /**
     * Constructor.
     * 
     * @param server
     *            The parent server.
     * @param byteBuffer
     *            The NIO byte buffer.
     * @param key
     *            The NIO selection key.
     * @param confidential
     *            Indicates if the call is confidential.
     */
    public GrizzlyServerCall(Server server, ByteBuffer byteBuffer,
            SelectionKey key, boolean confidential) {
        super(server);
        setConfidential(confidential);

        try {
            if (confidential) {
                headStream = secureHeadStream;
            } else {
                headStream = unsecureHeadStream;
            }

            headStream.setSelectionKey(key);
            headStream.setByteBuffer(byteBuffer);
            this.socketChannel = (SocketChannel) key.channel();

            // Read the request header
            readRequestHead(headStream);
        } catch (IOException ioe) {
            getLogger().log(Level.WARNING, "Unable to parse the HTTP request",
                    ioe);
        }
    }

    @Override
    public void writeResponseHead(Response response) throws IOException {
        ByteArrayOutputStream headStream = new ByteArrayOutputStream(4096);
        writeResponseHead(headStream);
        ByteBuffer buffer = ByteBuffer.wrap(headStream.toByteArray());
        if (isConfidential()) {
            SSLOutputWriter.flushChannel(getSocketChannel(), buffer);
        } else {
            OutputWriter.flushChannel(getSocketChannel(), buffer);
        }
        buffer.clear();
    }

    /**
     * Recycles the call and its stream.
     * 
     * @return The recycled call.
     */
    public GrizzlyServerCall recycle() {
        headStream.recycle();
        socketChannel = null;
        return this;
    }

    @Override
    public void writeResponseBody(Representation entity) throws IOException {
        entity.write(getResponseChannel());
    }

    @Override
    public ReadableByteChannel getRequestChannel() {
        return getSocketChannel();
    }

    @Override
    public InputStream getRequestStream() {
        return null;
    }

    @Override
    public WritableByteChannel getResponseChannel() {
        return getSocketChannel();
    }

    @Override
    public OutputStream getResponseStream() {
        return null;
    }

    /**
     * Return the underlying socket channel.
     * 
     * @return The underlying socket channel.
     */
    public SocketChannel getSocketChannel() {
        return this.socketChannel;
    }
}
