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
package org.apache.nifi.processors.standard;

import java.io.IOException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;

import org.junit.Test;

public class TestTransformXml {

    @Test
    public void testStylesheetNotFound() throws IOException {
        final TestRunner controller = TestRunners.newTestRunner(TransformXml.class);
        controller.setProperty(TransformXml.XSLT_FILE_NAME, "/no/path/to/math.xsl");
        controller.assertNotValid();
    }

    @Test
    public void testNonXmlContent() throws IOException {
        final TestRunner runner = TestRunners.newTestRunner(new TransformXml());
        runner.setProperty(TransformXml.XSLT_FILE_NAME, "src/test/resources/TestTransformXml/math.xsl");

        final Map<String, String> attributes = new HashMap<>();
        runner.enqueue("not xml".getBytes(), attributes);

        runner.run();

        runner.assertAllFlowFilesTransferred(TransformXml.REL_FAILURE);
        final MockFlowFile original = runner.getFlowFilesForRelationship(TransformXml.REL_FAILURE).get(0);
        final String originalContent = new String(original.toByteArray(), StandardCharsets.UTF_8);

        original.assertContentEquals("not xml");
    }

    @Test
    public void testTransformMath() throws IOException {
        final TestRunner runner = TestRunners.newTestRunner(new TransformXml());
        runner.setProperty("header", "Test for mod");
        runner.setProperty(TransformXml.XSLT_FILE_NAME, "src/test/resources/TestTransformXml/math.xsl");

        final Map<String, String> attributes = new HashMap<>();
        runner.enqueue(Paths.get("src/test/resources/TestTransformXml/math.xml"), attributes);
        runner.run();

        runner.assertAllFlowFilesTransferred(TransformXml.REL_SUCCESS);
        final MockFlowFile transformed = runner.getFlowFilesForRelationship(TransformXml.REL_SUCCESS).get(0);
        final String expectedContent = new String(Files.readAllBytes(Paths.get("src/test/resources/TestTransformXml/math.html"))).trim();

        transformed.assertContentEquals(expectedContent);
    }

    @Test
    public void testTransformCsv() throws IOException {
        final TestRunner runner = TestRunners.newTestRunner(new TransformXml());
        runner.setProperty(TransformXml.XSLT_FILE_NAME, "src/test/resources/TestTransformXml/tokens.xsl");
        runner.setProperty("uuid_0", "${uuid_0}");
        runner.setProperty("uuid_1", "${uuid_1}");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("uuid_0", "uuid_0");
        attributes.put("uuid_1", "uuid_1");

        StringBuilder builder = new StringBuilder();
        builder.append("<data>\n");

        try(BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(new File("src/test/resources/TestTransformXml/tokens.csv"))))){


            String line = null;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            builder.append("</data>");
            String data = builder.toString();
            runner.enqueue(data.getBytes(), attributes);
            runner.run();

            runner.assertAllFlowFilesTransferred(TransformXml.REL_SUCCESS);
            final MockFlowFile transformed = runner.getFlowFilesForRelationship(TransformXml.REL_SUCCESS).get(0);
            final String transformedContent = new String(transformed.toByteArray(), StandardCharsets.ISO_8859_1);
            final String expectedContent = new String(Files.readAllBytes(Paths.get("src/test/resources/TestTransformXml/tokens.xml")));

            transformed.assertContentEquals(expectedContent);
        }
    }

    @Test
    public void testTransformExpressionLanguage() throws IOException {
        final TestRunner runner = TestRunners.newTestRunner(new TransformXml());
        runner.setProperty("header", "Test for mod");
        runner.setProperty(TransformXml.XSLT_FILE_NAME, "${xslt.path}");

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("xslt.path", "src/test/resources/TestTransformXml/math.xsl");
        runner.enqueue(Paths.get("src/test/resources/TestTransformXml/math.xml"), attributes);
        runner.run();

        runner.assertAllFlowFilesTransferred(TransformXml.REL_SUCCESS);
        final MockFlowFile transformed = runner.getFlowFilesForRelationship(TransformXml.REL_SUCCESS).get(0);
        final String expectedContent = new String(Files.readAllBytes(Paths.get("src/test/resources/TestTransformXml/math.html"))).trim();

        transformed.assertContentEquals(expectedContent);
    }

    @Test
    public void testTransformNoCache() throws IOException {
        final TestRunner runner = TestRunners.newTestRunner(new TransformXml());
        runner.setProperty("header", "Test for mod");
        runner.setProperty(TransformXml.CACHE_SIZE, "0");
        runner.setProperty(TransformXml.XSLT_FILE_NAME, "src/test/resources/TestTransformXml/math.xsl");
        runner.enqueue(Paths.get("src/test/resources/TestTransformXml/math.xml"));
        runner.run();

        runner.assertAllFlowFilesTransferred(TransformXml.REL_SUCCESS);
        final MockFlowFile transformed = runner.getFlowFilesForRelationship(TransformXml.REL_SUCCESS).get(0);
        final String expectedContent = new String(Files.readAllBytes(Paths.get("src/test/resources/TestTransformXml/math.html"))).trim();

        transformed.assertContentEquals(expectedContent);
    }

}
