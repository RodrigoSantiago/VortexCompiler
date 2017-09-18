package com.vortex.compiler.logic.implementation.block;

import com.vortex.compiler.content.Parser;
import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.implementation.line.Line;
import com.vortex.compiler.logic.implementation.lineblock.LineBlock;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 15/10/2016
 */
public class BlockWhile extends Block {
    public static final String keyword = "while";

    public Token keywordToken, conditionToken;
    public LineBlock conditionLine;

    public BlockDo doBlock;

    public BlockWhile(Block container, Token token, Token[] tokens) {
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
            } else if (stage == 3 && sToken.isClosedBy("()")) {
                conditionToken = sToken.byNested();
                stage = 4;
            } else if (stage == 4) {
                if (sToken.isClosedBy("{}")) {
                    contentToken = sToken;
                    stage = -1;
                } else {
                    sContent = sToken;
                    stage = 5;
                    break;
                }
            } else {
                lastHasErro = true;
                addCleanErro("unexpected token", sToken);
            }
        }
        if (stage == 5) {
            contentToken = sContent.byAdd(tokens[tokens.length - 1]);
        } else if (stage != -1) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
        }
    }

    @Override
    public void load() {
        if (doBlock != null) {
            if (labelToken != null) {
                addCleanErro("label is not allowed here", labelToken);
            }
            if (contentToken != null && !contentToken.compare(";")) {
                addCleanErro("unexpected content", contentToken);
            }
        } else {
            if (labelToken != null && !getStack().addLabel(labelToken)) {
                addCleanErro("repeated label", labelToken.byLastChar());
            }
            if (contentToken != null) {
                Parser.parseCommands(this,
                        contentToken.isClosedBy("{}") ? contentToken.byNested() : contentToken, false);
            }
        }
        if (conditionToken != null) {
            conditionLine = new LineBlock(this, conditionToken, true, false);
            conditionLine.load();
            conditionLine.requestGetAcess();
            conditionLine.setAutoCasting(DataBase.defBoolPointer, false);
        }
    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {
        cBuilder.idt(indent).add("while (").add(conditionLine).add(") ").begin(indent + 1);

        for (Line line : lines) {
            line.build(cBuilder, indent + 1);
        }

        if (labelToken != null) {
            cBuilder.idt(indent + 1).nameContinue(labelToken).add(" : ;").ln();
        }

        cBuilder.idt(indent).end();

        if (labelToken != null) {
            cBuilder.idt(indent).nameBreak(labelToken).add(" : ;").ln();
        }
    }
}
