package com.vortex.compiler.logic.implementation.block;

import com.vortex.compiler.content.Parser;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.implementation.lineblock.LineBlock;
import com.vortex.compiler.logic.typedef.Pointer;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 15/10/2016
 */
public class BlockCase extends Block {
    public static final String keyword = "case";

    public Token keywordToken, valueToken;
    public LineBlock valueLine;

    int labelIndex;

    public BlockCase(Block container, Token token, Token[] tokens) {
        super(container, token);

        Token sContent = null;
        Token sCondition = null;
        Token eCondition = null;
        boolean lastHasErro = false;
        int stage = 0;
        for (Token sToken : tokens) {
            lastHasErro = false;
            if (stage == 0 && sToken.compare(keyword)) {
                keywordToken = sToken;
                stage = 1;
            } else if (stage == 1) {
                if (sToken.compare(":")) {
                    if (sCondition != null) {
                        valueToken = sCondition.byAdd(eCondition);
                    } else {
                        addCleanErro("empty value", keywordToken);
                    }
                    stage = 2;
                } else if (sCondition == null) {
                    sCondition = sToken;
                    eCondition = sToken;
                } else {
                    eCondition = sToken;
                }
            } else if (stage == 2) {
                sContent = sToken;
                stage = 3;
                break;
            } else {
                lastHasErro = true;
                addCleanErro("unexpected token", sToken);
            }
        }
        if (stage == 3) {
            contentToken = sContent.byAdd(tokens[tokens.length - 1]);
        } else {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
        }
    }

    @Override
    public void load() {
        labelIndex = getStack().requestIndex();

        if (valueToken != null) {
            valueLine = new LineBlock(this, valueToken, true, false);
            valueLine.load();
            valueLine.requestGetAcess();
            if (getCommandContainer() instanceof BlockSwitch) {
                BlockSwitch bSwitch = (BlockSwitch) getCommandContainer();
                bSwitch.cases.add(this);
                if (bSwitch.valueLine != null && bSwitch.valueLine.getReturnType() != Pointer.voidPointer) {
                    valueLine.setAutoCasting(bSwitch.valueLine.getReturnType(), false);
                }
            } else {
                addCleanErro("case statements should be inside a switch", keywordToken);
            }
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
