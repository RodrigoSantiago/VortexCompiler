package com.vortex.compiler.logic.implementation.block;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.build.CppBuilder;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 25/10/2016
 */
public class BlockNative extends Block {
    public static final String keyword = "native";

    public Token keywordToken, targetToken;

    boolean sourceValue, webValue;

    public BlockNative(Block container, Token token, Token[] tokens) {
        super(container, token);

        boolean lastHasErro = false;
        int stage = 0;
        for (Token sToken : tokens) {
            lastHasErro = false;
            if (stage == 0 && SmartRegex.simpleName(sToken) && !sToken.compare(keyword)) {
                labelToken = sToken;
                stage = 1;
            } else if (stage == 1 && sToken.compare(":")) {
                stage = 2;
            } else if ((stage == 0 || stage == 2) && sToken.compare(keyword)) {
                keywordToken = sToken;
                stage = 3;
            } else if (stage == 3 && sToken.isClosedBy("()")) {
                targetToken = sToken.byNested();
                if (targetToken.compare("source") || targetToken.compare("SOURCE")) {
                    sourceValue = true;
                } else if (targetToken.compare("web") || targetToken.compare("WEB")) {
                    webValue = true;
                } else if (targetToken.compare("header") || targetToken.compare("HEADER")) {
                    addCleanErro("this constant is not allowed here", targetToken);
                } else if (targetToken.compare("macro") || targetToken.compare("MACRO")) {
                    addCleanErro("this constant is not allowed here", targetToken);
                } else {
                    addCleanErro("invalid constant", sToken);
                }
                stage = 4;
            } else if (stage == 4 && sToken.isClosedBy("{}")) {
                contentToken = sToken;
                stage = -1;
            } else {
                lastHasErro = true;
                addCleanErro("unexpected token", sToken);
            }
        }
        if (stage != -1) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
        }
    }

    @Override
    public void load() {
        if (labelToken != null) {
            addCleanErro("label is not allowed here", labelToken);
        }
    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {
        String sLines[] = contentToken.getStringFile().getFileValue()
                .substring(contentToken.fileStartpos(), contentToken.fileEndpos())
                .replaceAll("\\s+$", "")
                .split("\\r?\\n");

        ArrayList<String> lines = new ArrayList<>(sLines.length);
        int stage = 0;
        if (sLines.length == 1) {
            lines.add(sLines[0].substring(1, sLines[0].length() - 1));
        } else {
            for (int i = 0; i < sLines.length; i++) {
                String line = sLines[i];
                if (stage == 0 && line.matches("\\{\\s*")) {
                    stage = 1;
                } else if (stage == 0) {
                    stage = 1;
                    lines.add(line.substring(line.indexOf("{") + 1));
                } else {
                    if (i == sLines.length - 1) {
                        if (!line.matches("\\s*\\}")) {
                            lines.add(line.substring(0, line.length() - 1));
                        }
                    } else {
                        lines.add(line);
                    }
                }
            }
        }
        if (lines.size() > 0) {
            int negativeIndent = firstNonTab(lines.get(0));
            for (String line : lines) {
                line = line.replaceAll("\\s+$", "");
                int positiveIndent = firstNonTab(line);
                cBuilder.idt(indent);
                if (positiveIndent == -1) {
                    cBuilder.add(line);
                } else if (positiveIndent < negativeIndent) {
                    cBuilder.add(line.substring(positiveIndent));
                } else {
                    cBuilder.add(line.substring(negativeIndent));
                }
                cBuilder.ln();
            }
        }
    }

    private int firstNonTab(String value){
        for (int i = 0; i < value.length(); i++){
            if (" \t\n\u000B\f\r".indexOf(value.charAt(i)) == -1){
                return i;
            }
        }
        return -1;
    }
}
