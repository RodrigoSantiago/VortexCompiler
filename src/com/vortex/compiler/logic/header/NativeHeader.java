package com.vortex.compiler.logic.header;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.Acess;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.typedef.Typedef;

import java.util.ArrayList;

import static com.vortex.compiler.logic.Type.*;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 28/10/2016
 */
public class NativeHeader extends Header {
    public static final String keyword = "native";

    //Leitura
    public Token keywordToken, contentToken, targetToken;

    //Conteudo Interno
    boolean headerValue, sourceValue, webValue, macroValue;

    public NativeHeader(Typedef container, Token token, Token[] tokens) {
        super(container, token, tokens, NATIVE, false, false, false, false, false);
        acessValue = Acess.PRIVATE;

        //[0-modificadores][1-native][2-()][3-;|{}]
        boolean lastHasErro = false;
        int stage = 0;
        for (Token sToken : tokens) {
            lastHasErro = false;

            //Modificadores
            if (stage == 0) {
                if (SmartRegex.isModifier(sToken)) {
                    continue;
                } else {
                    stage = 1;
                }
            }

            if (stage == 1 && sToken.compare(keyword)) {
                keywordToken = sToken;
                stage = 2;
            } else if (stage == 2 && sToken.isClosedBy("()")) {
                targetToken = sToken.byNested();
                if (targetToken.compare("header") || targetToken.compare("HEADER")) {
                    headerValue = true;
                } else if (targetToken.compare("source") || targetToken.compare("SOURCE")) {
                    sourceValue = true;
                } else if (targetToken.compare("web") || targetToken.compare("WEB")) {
                    webValue = true;
                } else if (targetToken.compare("macro") || targetToken.compare("MACRO")) {
                    macroValue = true;
                } else {
                    addCleanErro("invalid constant", sToken);
                }
                stage = 3;
            } else if (stage == 3 && (sToken.isClosedBy("{}") || sToken.compare(";"))) {
                contentToken = sToken;
                stage = -1;
            } else {
                lastHasErro = true;
                addCleanErro("unexpected token", sToken);
            }
        }
        if (stage != -1) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
            setWrong();
        }
    }

    @Override
    public void load() {
        if (!hasImplementation()) {
            addCleanErro("native headers should implement", contentToken);
        }
    }

    @Override
    public void make() {

    }

    @Override
    public void build(CppBuilder cBuilder) {
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
            int indent;
            if (isMacro()) {
                cBuilder.toHeader();
                indent = 0;
            } else if (isHeader()) {
                cBuilder.toHeader();
                indent = 1;
            } else if (isSource()) {
                cBuilder.toSource();
                indent = 0;
            } else {
                cBuilder.toSource();
                indent = 1;
            }

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

    public boolean hasImplementation() {
        return contentToken != null && contentToken.isClosedBy("{}");
    }

    public boolean isHeader() {
        return headerValue;
    }

    public boolean isSource() {
        return sourceValue;
    }

    public boolean isWeb() {
        return webValue;
    }

    public boolean isMacro() {
        return macroValue;
    }

    private int firstNonTab(String value){
        for (int i = 0; i < value.length(); i++){
            if (" \t\n\u000B\f\r".indexOf(value.charAt(i)) == -1){
                return i;
            }
        }
        return -1;
    }

    @Override
    public String toString() {
        return "native("
                + (headerValue ? "header" : sourceValue ? "source" : webValue ? "web" : macroValue ? "macro" : "?")
                + ")";
    }

}