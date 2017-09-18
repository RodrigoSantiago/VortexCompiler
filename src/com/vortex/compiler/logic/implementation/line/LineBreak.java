package com.vortex.compiler.logic.implementation.line;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.implementation.block.Block;
import com.vortex.compiler.logic.implementation.block.BlockSwitch;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 15/10/2016
 */
public class LineBreak extends Line {
    public static final String keyword = "break";

    //Escrita
    public Token keywordToken, labelToken;

    public LineBreak(Block container, Token token, Token[] tokens) {
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
        if (labelToken == null && (!getCommandContainer().isOnLoop() && !getCommandContainer().isCaseContainer())) {
            addCleanErro("break statement should be in a loop", token);
        }
        if (labelToken != null && !getCommandContainer().isOnLabel(labelToken)) {
            addCleanErro("label not found", labelToken);
        }
    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {
        cBuilder.idt(indent);
        if (labelToken != null) {
            cBuilder.add("goto ").nameBreak(labelToken).add(";").ln();
        } else if (getCommandContainer() instanceof BlockSwitch) {
            cBuilder.add("goto ").nameCase(((BlockSwitch) getCommandContainer()).getLabelIndex()).add(";").ln();
        } else {
            cBuilder.add("break;").ln();
        }
    }
}
