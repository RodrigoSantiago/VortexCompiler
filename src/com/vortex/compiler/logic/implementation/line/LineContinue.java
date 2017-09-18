package com.vortex.compiler.logic.implementation.line;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.implementation.block.Block;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 15/10/2016
 */
public class LineContinue extends Line {
    public static final String keyword = "continue";

    //Escrita
    public Token keywordToken, labelToken;

    public LineContinue(Block container, Token token, Token[] tokens) {
        super(container, token);

        boolean lastHasErro = false;
        int stage = 0;
        for (Token sToken : tokens) {
            lastHasErro = false;
            if (stage == 0 && sToken.compare(keyword)) {
                keywordToken = sToken;
                stage = 1;
            } else if (stage == 1 && SmartRegex.simpleName(sToken) && !SmartRegex.isKeyword(sToken)) {
                labelToken = sToken;
                stage = 2;
            } else if ((stage == 1 || stage == 2) && sToken.compare(";")) {
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
        if (!getCommandContainer().isOnLoop()) {
            addCleanErro("continue should be in a loop", token);
        }
        if (labelToken != null && !getCommandContainer().isOnLabel(labelToken)) {
            addCleanErro("label not found", labelToken);
        }
    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {
        cBuilder.idt(indent);
        if (labelToken != null) {
            cBuilder.add("goto ").nameContinue(labelToken).add(";").ln();
        } else {
            cBuilder.add("continue;").ln();
        }
    }
}
