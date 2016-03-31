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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Assert;

import org.junit.Test;

public class StreamScannerTests {

    @Test
    public void validateWithMultiByteCharsNoDelimiter() {
        String data = "僠THIS IS MY NEW TEXT.僠IT HAS A NEWLINE.";
        ByteArrayInputStream is = new ByteArrayInputStream(data.getBytes());
        StreamScanner scanner = new StreamScanner(is, "(\\W)\\Z", 1000);
        assertTrue(scanner.hasNext());
        assertEquals(data, new String(scanner.next(), StandardCharsets.UTF_8));
        assertFalse(scanner.hasNext());
    }

    @Test
    public void validateWithComplexDelimiter() {
        String data = "THIS IS MY TEXT<MY DEIMITER>THIS IS MY NEW TEXT<MY DEIMITER>THIS IS MY NEWEST TEXT";
        ByteArrayInputStream is = new ByteArrayInputStream(data.getBytes());
        StreamScanner scanner = new StreamScanner(is, "<MY DEIMITER>", 1000);
        assertTrue(scanner.hasNext());
        assertEquals("THIS IS MY TEXT", new String(scanner.next(), StandardCharsets.UTF_8));
        assertTrue(scanner.hasNext());
        assertEquals("THIS IS MY NEW TEXT", new String(scanner.next(), StandardCharsets.UTF_8));
        assertTrue(scanner.hasNext());
        assertEquals("THIS IS MY NEWEST TEXT", new String(scanner.next(), StandardCharsets.UTF_8));
        assertFalse(scanner.hasNext());
    }

    @Test(expected = IllegalStateException.class)
    public void validateMaxBufferSize() {
        String data = "THIS IS MY TEXT<MY DEIMITER>THIS IS MY NEW TEXT<MY DEIMITER>THIS IS MY NEWEST TEXT";
        ByteArrayInputStream is = new ByteArrayInputStream(data.getBytes());
        StreamScanner scanner = new StreamScanner(is, "<MY DEIMITER>", 20);
        assertTrue(scanner.hasNext());
    }

    @Test
    public void verifyScannerHandlesNegativeOneByteInputs() {
        ByteArrayInputStream is = new ByteArrayInputStream(new byte[]{0, 0, 0, 0, -1, 0, 0, 0});
        StreamScanner scanner = new StreamScanner(is, "water", 2000, 20);
        assertTrue(scanner.hasNext());
        Assert.assertArrayEquals(scanner.next(), new byte[]{0, 0, 0, 0, -1, 0, 0, 0});
    }

    @Test
    public void verifyScannerHandlesNegativeOneByteDelimiter() {
        ByteArrayInputStream is = new ByteArrayInputStream(new byte[]{0, 0, 0, 0, -1, 0, 0, 0});
        StreamScanner scanner = new StreamScanner(is, new byte[]{-1}, 2000, 20);
        assertTrue(scanner.hasNext());
        Assert.assertArrayEquals(scanner.next(), new byte[]{0, 0, 0, 0});
        assertTrue(scanner.hasNext());
        Assert.assertArrayEquals(scanner.next(), new byte[]{0, 0, 0});
    }
}
