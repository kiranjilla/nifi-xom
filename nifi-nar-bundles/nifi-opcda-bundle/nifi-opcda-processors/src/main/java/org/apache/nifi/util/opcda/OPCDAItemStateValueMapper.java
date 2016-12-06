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
 *
 * @author <a href="mailto:justin.smith@summitsystemsinc.com">Justin Smith</a>
 * @author <a href="mailto:sbabu@hortonworks.com">Sekhar Babu</a>
 * @author <a href="mailto:fdigirolomo@hortonworks.com">Frank DiGirolomo</a>
 */

package org.apache.nifi.util.opcda;

import org.jinterop.dcom.common.JIException;
import org.jinterop.dcom.core.*;

import java.math.BigDecimal;
import java.util.logging.Logger;

public class OPCDAItemStateValueMapper {

    private static Logger log = Logger.getLogger("OPCDAItemStateValueMapper");

    public static Object toJavaType(JIVariant variant) throws JIException {
        int type = variant.getType();

        if ((type & JIVariant.VT_ARRAY) == JIVariant.VT_ARRAY) {
            JIArray array = variant.getObjectAsArray();
            return jIArrayToJavaArray(array, type);
        } else {
            switch (type) {
                case JIVariant.VT_I1:
                    Byte b = (byte) variant.getObjectAsChar();
                    log.info("returning char: " + b.toString());
                    return b;
                case JIVariant.VT_I2:
                    return variant.getObjectAsShort();
                case JIVariant.VT_I4:
                    return variant.getObjectAsInt();
                case JIVariant.VT_I8:
                case JIVariant.VT_INT:
                    return (long) variant.getObjectAsInt();
                case JIVariant.VT_DATE:
                    return variant.getObjectAsDate();
                case JIVariant.VT_R4:
                    return variant.getObjectAsFloat();
                case JIVariant.VT_R8:
                    return variant.getObjectAsDouble();
                case JIVariant.VT_UI1:
                    return variant.getObjectAsUnsigned().getValue().byteValue();
                case JIVariant.VT_UI2:
                    return variant.getObjectAsUnsigned().getValue().toString();
                case JIVariant.VT_UI4:
                case JIVariant.VT_UINT:
                    return variant.getObjectAsUnsigned().getValue().intValue();
                case JIVariant.VT_BSTR:
                    return String.valueOf(variant.getObjectAsString2());
                case JIVariant.VT_BOOL:
                    return variant.getObjectAsBoolean();
                case JIVariant.VT_CY:
                    JICurrency currency = (JICurrency) variant.getObject();
                    return currencyToBigDecimal(currency);
                default:
                    return (variant.isByRefFlagSet() ? variant.getObject().toString() : "");
            }
        }
    }

    private static BigDecimal currencyToBigDecimal(JICurrency currency) {
        return new BigDecimal(currency.getUnits() + ((double) currency.getFractionalUnits() / 10000));
    }

    private static String dumpValue(Object value) throws JIException {
        String output = null;
        if (value instanceof JIVariant) {
            JIVariant variant = (JIVariant) value;
            dumpValue(variant.getObject());
        } else if (value instanceof JIUnsignedByte) {
            JIUnsignedByte Junsigb = (JIUnsignedByte) value;
            output = Junsigb.getValue().toString();
            // status = Junsigb.getEncapsulatedUnsigned().intValue()/64;
            // subStatus = Junsigb.getEncapsulatedUnsigned().intValue() % 64;
            // statusString = statusString.valueOf(status);
            // subStatusString = subStatusString.valueOf(subStatus);
        } else if (value instanceof JIUnsignedShort) {
            JIUnsignedShort Junsigs = (JIUnsignedShort) value;
            output = Junsigs.getValue().toString();
        } else if (value instanceof JIUnsignedInteger) {
            JIUnsignedInteger Junsigi = (JIUnsignedInteger) value;
            output = Junsigi.getValue().toString();
        } else if (value instanceof Double) {
            output = value.toString();
        } else if (value instanceof Float) {
            output = value.toString();
        } else if (value instanceof Byte) {
            output = value.toString();
        } else if (value instanceof Character) {
            output = value.toString();
        } else if (value instanceof Integer) {
            output = value.toString();
        } else if (value instanceof Long) {
            output = value.toString();
        } else if (value instanceof Boolean) {
            output = value.toString();
        } else if (value instanceof Short) {
            output = value.toString();
        } else {
            System.out.println(String.format("Unknown value type (%s): %s", value.getClass(), value.toString()));
            output = null;
        }

        return output;
    }

    private static Object[] jIArrayToJavaArray(JIArray jIArray, int type) {

        Object[] objArray = (Object[]) jIArray.getArrayInstance();
        int arrayLength = objArray.length;

        switch (type ^ JIVariant.VT_ARRAY) {
            // JInterop seems to be handling most of these to java types already...
            case JIVariant.VT_I1:
            case JIVariant.VT_I2:
            case JIVariant.VT_I4:
            case JIVariant.VT_I8:
            case JIVariant.VT_INT:
            case JIVariant.VT_DATE:
            case JIVariant.VT_R4:
            case JIVariant.VT_R8:
            case JIVariant.VT_UI1:
            case JIVariant.VT_UI2:
            case JIVariant.VT_UI4:
            case JIVariant.VT_UINT:
            case JIVariant.VT_BOOL:
                return objArray;
            case JIVariant.VT_CY:
                BigDecimal[] cyRetVal = new BigDecimal[arrayLength];
                for (int i = 0; i < arrayLength; i++) {
                    cyRetVal[i] = currencyToBigDecimal((JICurrency) objArray[i]);
                }
                return cyRetVal;
            case JIVariant.VT_BSTR:
                String[] strRetVal = new String[arrayLength];
                for (int i = 0; i < arrayLength; i++) {
                    strRetVal[i] = ((JIString) objArray[i]).getString();
                }
                return strRetVal;
            default:
                return objArray;
        }
    }
}