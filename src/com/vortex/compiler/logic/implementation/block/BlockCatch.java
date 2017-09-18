package com.vortex.compiler.logic.implementation.block;

import com.vortex.compiler.content.Parser;
import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.Acess;
import com.vortex.compiler.logic.Type;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.implementation.line.Line;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 15/10/2016
 */
public class BlockCatch extends Block {
    public static final String keyword = "catch";

    public Token keywordToken, statementToken, nameToken;

    private boolean lastIsTry;

    public BlockCatch(Block container, Token token, Token[] tokens, boolean lastIsTry) {
        super(container, token);
        this.lastIsTry = lastIsTry;

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
                statementToken = sToken;
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
        if (!lastIsTry) {
            addCleanErro("catch block should be after try block", keywordToken);
        }
        if (statementToken != null) {
            nameToken = statementToken.byNested();
            if (nameToken.isEmpty()) {
                addErro("variable name expected", statementToken);
            } else {
                Field field = new Field(Type.LOCALVAR, nameToken, getContainer(), nameToken.toString(),
                        DataBase.defExceptionPointer,
                        false, false, false,
                        false, true, Acess.PUBLIC, Acess.PUBLIC);
                if (!addField(field)) {
                    addCleanErro("repeated variable name", statementToken);
                } else if (SmartRegex.isKeyword(statementToken)) {
                    addCleanErro("illegal name", statementToken);
                }
            }
        }
        if (contentToken != null) {
            Parser.parseCommands(this, contentToken.isClosedBy("{}") ? contentToken.byNested() : contentToken, false);
        }
    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {
        cBuilder.idt(indent)
                .add("catch (").add(DataBase.defExceptionPointer).add(" ").nameField(nameToken).add(") ").begin(indent + 1);

        for (Line line : lines) {
            line.build(cBuilder, indent + 1);
        }

        cBuilder.idt(indent).end();

    }
}
