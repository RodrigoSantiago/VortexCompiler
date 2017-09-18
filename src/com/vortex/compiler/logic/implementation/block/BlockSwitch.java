package com.vortex.compiler.logic.implementation.block;

import com.vortex.compiler.content.Parser;
import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.implementation.line.Line;
import com.vortex.compiler.logic.implementation.lineblock.LineBlock;
import com.vortex.compiler.logic.typedef.Pointer;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 15/10/2016
 */
public class BlockSwitch extends Block {
    public static final String keyword = "switch";

    public Token keywordToken, valueToken;
    public LineBlock valueLine;

    ArrayList<BlockCase> cases = new ArrayList<>();
    BlockDefault blockDefault;
    int labelIndex;

    public BlockSwitch(Block container, Token token, Token[] tokens) {
        super(container, token);
        caseContainer = true;

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
                valueToken = sToken.byNested();
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
        if (labelToken != null) {
            addCleanErro("label is not allowed here", labelToken);
        }
        labelIndex = getStack().requestIndex();

        if (valueToken != null) {
            valueLine = new LineBlock(getCommandContainer(), valueToken, true, false);
            valueLine.load();
            valueLine.requestGetAcess();
            if (valueLine.getReturnType() == Pointer.voidPointer) {
                addCleanErro("invalid return type", valueLine.getToken());
            }
            if (valueLine.isValue()) {
                addCleanErro("switch should not be a constant value", valueLine.getToken());
            }
        }
        if (contentToken != null) {
            Parser.parseCommands(this, contentToken.isClosedBy("{}") ? contentToken.byNested() : contentToken, false);

            //Undefined case
            BlockCase firstCase = cases.size() == 0 ? null : cases.get(0);
            for (Line line : lines) {
                if (line == firstCase) {
                    break;
                } else {
                    line.addCleanErro("undefined case", line.getToken());
                }
            }
        }
    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {
        cBuilder.idt(indent).begin(indent + 1)
                .idt(indent + 1).add(valueLine.getReturnType()).add(" sVal = ").add(valueLine).add(";").ln();

        for (BlockCase aCase : cases) {
            cBuilder.idt(indent + 1)
                    .add("if (sVal == ").add(aCase.valueLine).add(") goto ").nameCase(aCase.labelIndex).add(";").ln();
        }
        if (blockDefault != null) {
            cBuilder.idt(indent + 1).add("goto ").nameCase(blockDefault.labelIndex).add(";").ln();
        } else {
            cBuilder.idt(indent + 1).add("goto ").nameCase(labelIndex).add(";").ln();
        }

        for (Line line : lines) {
            line.build(cBuilder, indent + 1);
        }

        cBuilder.idt(indent).end();

        cBuilder.idt(indent).nameCase(labelIndex).add(" : ;").ln();
    }

    public int getLabelIndex() {
        return labelIndex;
    }
}
