/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.processors.kafka;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 *
 */
class StreamScanner {

    private final InputStream is;

    private final byte[] delimiter;

    private ByteBuffer buffer;

    private byte[] data;

    private final int maxAllowed;

    StreamScanner(InputStream is, String delimiter, int maxAllowed) {
        this(is, delimiter, 8192, maxAllowed);
    }

    /**
     *
     */
    StreamScanner(InputStream is, String delimiter, int initialBufferSize, int maxAllowed) {
        this.is = new BufferedInputStream(is);
        this.delimiter = delimiter.getBytes(StandardCharsets.UTF_8);
        this.buffer = ByteBuffer.allocate(initialBufferSize);
        this.maxAllowed = maxAllowed;
    }

    /**
     *
     */
    boolean hasNext() {
        this.data = null;
        int j = 0;
        boolean moreData = true;
        byte b;
        while (this.data == null) {
            this.expandBufferIfNecessary();
            try {
                b = (byte) this.is.read();
            } catch (IOException e) {
                throw new IllegalStateException("Failed while reading InputStream", e);
            }
            if (b == -1) {
                this.extractDataToken(0);
                moreData = false;
            } else {
                this.buffer.put(b);
                if (this.buffer.position() > this.maxAllowed) {
                    throw new IllegalStateException("Maximum allowed buffer size exceeded.");
                }
                if (this.delimiter[j] == b) {
                    if (++j == this.delimiter.length) {
                        this.extractDataToken(this.delimiter.length);
                        j = 0;
                    }
                } else {
                    j = 0;
                }
            }
        }
        return this.data.length > 0 || moreData;
    }

    private void expandBufferIfNecessary() {
        if (this.buffer.remaining() == 0) {
            this.buffer.flip();
            int pos = this.buffer.capacity();
            ByteBuffer bb = ByteBuffer.allocate(this.buffer.capacity() * 2);
            bb.put(this.buffer);
            this.buffer = bb;
            this.buffer.position(pos);
        }
    }

    private void extractDataToken(int lengthSubtract) {
        this.buffer.flip();
        this.data = new byte[this.buffer.limit() - lengthSubtract];
        this.buffer.get(this.data);
        this.buffer.clear();
    }

    /**
     *
     */
    byte[] next() {
        return this.data;
    }
}
