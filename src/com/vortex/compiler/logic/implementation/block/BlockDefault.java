package com.vortex.compiler.logic.implementation.block;

import com.vortex.compiler.content.Parser;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.build.CppBuilder;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 15/10/2016
 */
public class BlockDefault extends Block {
    public static final String keyword = "default";

    public Token keywordToken;

    int labelIndex;

    public BlockDefault(Block container, Token token, Token[] tokens) {
        super(container, token);

        Token sContent = null;
        boolean lastHasErro = false;
        int stage = 0;
        for (Token sToken : tokens) {
            lastHasErro = false;
            if(stage == 0 && sToken.compare(keyword)){
                keywordToken = sToken;
                stage = 1;
            } else if(stage == 1 && sToken.compare(":")){
                stage = 2;
            } else if (stage == 2) {
                sContent = sToken;
                stage = -1;
                break;
            } else {
                lastHasErro = true;
                addCleanErro("unexpected token", sToken);
            }
        }
        if (stage != -1) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
        } else {
            contentToken = sContent.byAdd(tokens[tokens.length - 1]);
        }
    }

    @Override
    public void load() {
        labelIndex = getStack().requestIndex();

        if (getCommandContainer() instanceof BlockSwitch) {
            BlockSwitch bSwitch = (BlockSwitch) getCommandContainer();
            if (bSwitch.blockDefault == null) {
                bSwitch.blockDefault = this;
            } else {
                addCleanErro("repeated default statement", keywordToken);
            }
        } else {
            addCleanErro("default-case statements should be inside a switch", keywordToken);
        }

        if (contentToken != null) {
            Parser.parseCommands(getCommandContainer(), contentToken, false);
        }
    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {
        cBuilder.idt(indent).nameCase(labelIndex).add(" : ").ln();
    }
}
