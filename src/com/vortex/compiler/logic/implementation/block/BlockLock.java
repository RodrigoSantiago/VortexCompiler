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
 *         Data: 25/10/2016
 */
public class BlockLock extends Block{
    public static final String keyword = "lock";

    public Token keywordToken, lockToken;
    public LineBlock lockLine;

    public BlockLock(Block container, Token token, Token[] tokens) {
        super(container, token);

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
                lockToken = sToken.byNested();
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
        if (lockToken != null) {
            lockLine = new LineBlock(this, lockToken, true, false);
            lockLine.load();
            lockLine.requestGetAcess();
            lockLine.setAutoCasting(DataBase.defLockablePointer, false);
        }
        if (contentToken != null) {
            Parser.parseCommands(this, contentToken.isClosedBy("{}") ? contentToken.byNested() : contentToken, false);
        }
    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {
        cBuilder.idt(indent).begin(indent + 1)
                .idt(indent + 1)
                .add("auto key = Lockey<").add(DataBase.defLockablePointer).add(">(").add(lockLine).add(");").ln();

        for (Line line : lines) {
            line.build(cBuilder, indent + 1);
        }

        cBuilder.idt(indent).end();
    }
}
