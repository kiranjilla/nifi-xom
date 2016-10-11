package org.apache.nifi.processors.opcdaclient.util;

import java.math.BigDecimal;

import org.jinterop.dcom.common.JIException;
import org.jinterop.dcom.core.JIArray;
import org.jinterop.dcom.core.JICurrency;
import org.jinterop.dcom.core.JIString;
import org.jinterop.dcom.core.JIUnsignedByte;
import org.jinterop.dcom.core.JIUnsignedInteger;
import org.jinterop.dcom.core.JIUnsignedShort;
import org.jinterop.dcom.core.JIVariant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author <a href="mailto:justin.smith@summitsystemsinc.com">Justin Smith</a>
 */
public class JIVariantMarshaller {

	private static final Logger logger = LoggerFactory.getLogger(JIVariantMarshaller.class);

	public static Object toJavaType(JIVariant variant) throws JIException {
		int type = variant.getType();

		if ((type & JIVariant.VT_ARRAY) == JIVariant.VT_ARRAY) {
			JIArray array = variant.getObjectAsArray();

			return jIArrayToJavaArray(array, type);
		} else {

			switch (type) {

			case JIVariant.VT_I1:
				// This is a hack... (Maybe MS doesnt have a byte?)
				return Byte.valueOf((byte) variant.getObjectAsChar());
			case JIVariant.VT_I2:
				return Short.valueOf(variant.getObjectAsShort());
			case JIVariant.VT_I4:
				return Integer.valueOf(variant.getObjectAsInt());
			case JIVariant.VT_I8:
			case JIVariant.VT_INT:
				return Long.valueOf(variant.getObjectAsInt());
			case JIVariant.VT_DATE:
				return variant.getObjectAsDate();
			case JIVariant.VT_R4:
				return Float.valueOf(variant.getObjectAsFloat());
			case JIVariant.VT_R8:
				return Double.valueOf(variant.getObjectAsDouble());
			case JIVariant.VT_UI1:
				return Byte.valueOf(variant.getObjectAsUnsigned().getValue().byteValue());
			case JIVariant.VT_UI2:
				// return
				// Short.valueOf(variant.getObjectAsUnsigned().getValue().shortValue());
				return variant.getObjectAsUnsigned().getValue().toString();
			// return Junsigi.getValue().toString();
			case JIVariant.VT_UI4:
			case JIVariant.VT_UINT:
				return Integer.valueOf(variant.getObjectAsUnsigned().getValue().intValue());
			case JIVariant.VT_BSTR:
				return String.valueOf(variant.getObjectAsString2());
			case JIVariant.VT_BOOL:
				return Boolean.valueOf(variant.getObjectAsBoolean());
			case JIVariant.VT_CY:
				JICurrency currency = (JICurrency) variant.getObject();

				BigDecimal cyRetVal = currencyToBigDecimal(currency);

				return cyRetVal;
			default:
				final String value = variant.getObject().toString();
				logger.warn(String.format(DEFAULT_MSG, value, variant.getObject().getClass().getName(),
						Integer.toHexString(type)));
				return value;
			}
		}
	}

	private static BigDecimal currencyToBigDecimal(JICurrency currency) {
		BigDecimal cyRetVal = new BigDecimal(currency.getUnits() + ((double) currency.getFractionalUnits() / 10000));
		return cyRetVal;
	}

	public static final String DEFAULT_MSG = "Using default case for variant conversion: %s : %s : %s";

	public static String dumpValue(Object value) throws JIException {
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

	public static Object[] jIArrayToJavaArray(JIArray jIArray, int type) {

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
			logger.warn(
					String.format(DEFAULT_MSG, jIArray, jIArray.getArrayClass().getName(), Integer.toHexString(type)));
			return objArray;
		}
	}
}