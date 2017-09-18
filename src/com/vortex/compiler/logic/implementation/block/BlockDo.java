package com.vortex.compiler.logic.implementation.block;

import com.vortex.compiler.content.Parser;
import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.implementation.line.Line;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 15/10/2016
 */
public class BlockDo extends Block {
    public static final String keyword = "do";

    public Token keywordToken;

    public BlockWhile blockWhile;

    public BlockDo(Block container, Token token, Token[] tokens) {
        super(container, token);
        loop = true;

        Token sContent = null;
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
            } else if (stage == 3) {
                if (sToken.isClosedBy("{}")) {
                    contentToken = sToken;
                    stage = -1;
                } else {
                    sContent = sToken;
                    stage = 4;
                    break;
                }
            } else {
                lastHasErro = true;
                addCleanErro("unexpected token", sToken);
            }
        }
        if (stage == 4) {
            contentToken = sContent.byAdd(tokens[tokens.length - 1]);
        } else if (stage != -1) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
        }
    }

    @Override
    public void load() {
        if (!getStack().addLabel(labelToken)) {
            addCleanErro("repeated label", labelToken.byLastChar());
        }
        if (blockWhile == null) {
            addCleanErro("while expected", token.byLastChar());
        } else {
            blockWhile.doBlock = this;
            blockWhile.load();
            if (blockWhile.contentToken != null && !blockWhile.contentToken.compare(";")) {
                addCleanErro("unexpected block", blockWhile.contentToken);
            }
        }
        if (contentToken != null) {
            Parser.parseCommands(this, contentToken.isClosedBy("{}") ? contentToken.byNested() : contentToken, false);
        }
    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {
        cBuilder.idt(indent).add("do ").begin(indent + 1);

        for (Line line : lines) {
            line.build(cBuilder, indent + 1);
        }

        if (labelToken != null) {
            cBuilder.idt(indent + 1).nameContinue(labelToken).add(" : ;").ln();
        }

        cBuilder.idt(indent).end()
                .idt(indent).add("while (").add(blockWhile.conditionLine).add(");").ln();

        if (labelToken != null) {
            cBuilder.idt(indent).nameBreak(labelToken).add(" : ;").ln();
        }
    }

    public void setBlockWhile(BlockWhile blockWhile) {
        this.blockWhile = blockWhile;
    }

}
