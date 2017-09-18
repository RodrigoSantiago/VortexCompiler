package com.vortex.compiler.logic.implementation.lineblock.data;

import com.vortex.compiler.content.Token;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.implementation.StringDecoder;
import com.vortex.compiler.logic.implementation.lineblock.LineCall;
import com.vortex.compiler.logic.typedef.Pointer;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 24/11/2016
 */
public class DataValue extends Data {
    public Token textToken;
    public String value;
    public ArrayList<Pointer> valuePointers = new ArrayList<>();

    /**
     *
     * @param lineCall Linha
     * @param type 0 - bool, 1 - null, 2 - Inteiro, 3 - hexadecimal, 4 - real, 5 - string, 6 - char
     */
    public DataValue(LineCall lineCall, int type) {
        textToken = lineCall.getToken();

        if (type == 0) {
            value = textToken.toString();
            valuePointers.add(DataBase.defBoolPointer);
            lineCall.returnType = valuePointers.get(0);
        } else if (type == 1) {
            value = textToken.toString();
            valuePointers.add(Pointer.nullPointer);
            lineCall.returnType = valuePointers.get(0);
        } else if (type == 2) {
            if (textToken.endsWith("l") || textToken.endsWith("L")) {
                valuePointers.add(DataBase.defLongPointer);
                try {
                    long number = Long.valueOf(textToken.subSequence(0, textToken.length() - 1).toString());
                    value = "" + number;
                } catch (NumberFormatException e) {
                    value = "0";
                    lineCall.addCleanErro("out of range", textToken);
                }
            } else {
                try {
                    long number = Long.valueOf(textToken.toString());
                    value = "" + number;
                    if (number <= 255) {
                        valuePointers.add(DataBase.defBytePointer);
                    }
                    if (number <= 65535) {
                        valuePointers.add(DataBase.defCharPointer);
                    }
                    if (number <= 32767) {
                        valuePointers.add(DataBase.defShortPointer);
                    }
                    if (number <= 2147483647) {
                        valuePointers.add(DataBase.defIntPointer);
                    }
                } catch (NumberFormatException e) {
                    value = "0";
                    lineCall.addCleanErro("out of range", textToken);
                }
                valuePointers.add(DataBase.defLongPointer);
                valuePointers.add(DataBase.defDoublePointer);
                valuePointers.add(DataBase.defFloatPointer);
            }
            lineCall.returnType = valuePointers.get(0);
        } else if (type == 3) {
            try {
                String text = textToken.subSequence(2).toString();
                long number;
                if (text.length() <= 2) {
                    number = Integer.parseInt(text, 16);
                } else if (text.length() <= 4) {
                    number = (short)Integer.parseInt(text, 16);
                } else if (text.length() <= 8) {
                    number = (int)Long.parseLong(text, 16);
                } else {
                    number = Long.parseLong(text, 16);
                }
                value = "" + number;

                if (number >= 0 && number <= 255) {
                    valuePointers.add(DataBase.defBytePointer);
                }
                if (number >= 0 && number <= 65535) {
                    valuePointers.add(DataBase.defCharPointer);
                }
                if (number >= -32768 && number <= 32767) {
                    valuePointers.add(DataBase.defShortPointer);
                }
                if (number >= -2147483648 && number <= 2147483647) {
                    valuePointers.add(DataBase.defIntPointer);
                }
            } catch (NumberFormatException e) {
                value = "0";
                lineCall.addCleanErro("out of range", textToken);
            }
            valuePointers.add(DataBase.defLongPointer);
            lineCall.returnType = valuePointers.get(0);
        } else if (type == 4) {
            if (textToken.endsWith("f") || textToken.endsWith("F")) {
                textToken = textToken.subSequence(0, textToken.length() - 1);
                valuePointers.add(DataBase.defFloatPointer);
            } else if (textToken.endsWith("d") || textToken.endsWith("D")) {
                textToken = textToken.subSequence(0, textToken.length() - 1);
                valuePointers.add(DataBase.defDoublePointer);
            } else {
                valuePointers.add(DataBase.defDoublePointer);
                valuePointers.add(DataBase.defFloatPointer);
            }
            try {
                Double.parseDouble(textToken.toString());
                value = textToken.toString();
            } catch (NumberFormatException e) {
                value = "0";
                lineCall.addCleanErro("out of range", textToken);
            }
            lineCall.returnType = valuePointers.get(0);
        } else if (type == 5) {
            valuePointers.add(DataBase.defStringPointer);
            value = StringDecoder.decodeString(lineCall, textToken.byNested());
            lineCall.returnType = valuePointers.get(0);
        } else if (type == 6) {
            valuePointers.add(DataBase.defCharPointer);
            value = StringDecoder.decodeString(lineCall, textToken.byNested());
            if (value.length() != 1) {
                lineCall.addCleanErro("invalid character", textToken);
                value = "";
            } else {
                value = Integer.toString(value.charAt(0));
                if (value.charAt(0) <= 255) {
                    valuePointers.add(DataBase.defBytePointer);
                }
                if (value.charAt(0) <= 32767) {
                    valuePointers.add(DataBase.defShortPointer);
                }
            }
            valuePointers.add(DataBase.defIntPointer);
            valuePointers.add(DataBase.defLongPointer);
            lineCall.returnType = valuePointers.get(0);
        }
    }
}
