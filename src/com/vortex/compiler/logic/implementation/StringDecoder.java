package com.vortex.compiler.logic.implementation;

import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.LogicToken;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 24/10/2016
 */
public class StringDecoder {

    private static final String octal = "01234567";
    private static final String hex = "0123456789ABCDEFabcdef";

    public static String decodeString(LogicToken logicToken, Token token) {
        StringBuilder builder = new StringBuilder();
        int state = 0;
        int sPos = 0;
        for (int i = 0; i < token.length(); i++) {
            char chr = token.charAt(i);
            if (state != 1 && chr == '\\') {
                if (state != 0) logicToken.addCleanErro("invalid character sequence", token.subSequence(sPos, i));
                state = 1;
            } else if (state == 0) {
                builder.append(chr);
            } else if (state == 1) {
                state = 0;
                sPos = i;
                if (chr == 'b') builder.append('\b');
                else if (chr == 't') builder.append('\t');
                else if (chr == 'n') builder.append('\n');
                else if (chr == 'f') builder.append('\f');
                else if (chr == 'r') builder.append('\r');
                else if (chr == '\"') builder.append('\"');
                else if (chr == '\'') builder.append('\'');
                else if (chr == '\\') builder.append('\\');
                else if (octal.indexOf(chr) > -1) state = 2;    //2 - 3
                else if (chr == 'u') state = 4;                 //4 - 5 - 6 - 7
                else if (chr == 'U') state = 8;                 //8 - 9 - 10 - 11 - 12 - 13 - 14 - 15
                else {
                    logicToken.addCleanErro("invalid character scape", token.subSequence(i - 1, i + 1));
                }
            } else if (state == 2 || state == 3) {
                if (octal.indexOf(chr) > -1) {
                    if (++state > 3) {
                        builder.append((char) Integer.parseInt(token.subSequence(sPos, i + 1).toString(), 8));
                        state = 0;
                    }
                } else {
                    logicToken.addCleanErro("invalid character sequence", token.subSequence(sPos, i));
                    state = 0;
                }
            } else if (state >= 4 && state <= 7) {
                if (hex.indexOf(chr) > -1) {
                    if (++state > 7) {
                        char[] cChars = hexTextToChar(token.subSequence(sPos + 1, i + 1).toString());
                        if (cChars != null) {
                            builder.append(cChars);
                        } else {
                            logicToken.addCleanErro("invalid unicode value", token.subSequence(sPos + 1, i + 1));
                        }
                        state = 0;
                    }
                } else {
                    logicToken.addCleanErro("invalid character sequence", token.subSequence(sPos, i));
                    state = 0;
                }
            } else if (state >= 8 && state <= 15) {
                if (hex.indexOf(chr) > -1) {
                    if (++state > 15) {
                        char[] cChars = hexTextToChar(token.subSequence(sPos + 1, i + 1).toString());
                        if (cChars != null) {
                            builder.append(cChars);
                        } else {
                            logicToken.addCleanErro("invalid unicode value", token.subSequence(sPos + 1, i + 1));
                        }
                        state = 0;
                    }
                } else {
                    logicToken.addCleanErro("invalid character sequence", token.subSequence(sPos, i));
                    state = 0;
                }
            } else {
                logicToken.addCleanErro("invalid character", token.subSequence(i, i + 1));
                state = 0;
            }
        }
        if (state != 0) {
            logicToken.addCleanErro("invalid character sequence", token.subSequence(sPos));
        }
        return builder.toString();
    }

    private static char[] hexTextToChar(String value) {
        try {
            return new String(new int[]{(int) Long.parseLong(value, 16)}, 0, 1).toCharArray();
        } catch (Exception e) {
            return null;
        }
    }
}