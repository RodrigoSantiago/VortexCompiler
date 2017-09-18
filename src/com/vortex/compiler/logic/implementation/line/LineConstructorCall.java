package com.vortex.compiler.logic.implementation.line;

import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.Constructor;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.implementation.block.Block;
import com.vortex.compiler.logic.implementation.lineblock.LineBlock;
import com.vortex.compiler.logic.implementation.lineblock.LineCall;
import com.vortex.compiler.logic.typedef.Pointer;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 24/11/2016
 */
public class LineConstructorCall extends Line {
    public static final String keyword = "this";
    public static final String keyword2 = "super";

    //Escrita
    public Token keywordToken, argsToken;
    //Lógica
    public Pointer path;
    public Constructor innerConstructorCall;
    public ArrayList<LineBlock> args = new ArrayList<>();

    public LineConstructorCall(Block container, Token token, Token[] tokens) {
        super(container, token);

        boolean lastHasErro = false;
        int stage = 0;
        for (Token sToken : tokens) {
            lastHasErro = false;
            if (stage == 0 && (sToken.compare(keyword) || sToken.compare(keyword2))) {
                keywordToken = sToken;
                stage = 1;
            } else if (stage == 1 && sToken.isClosedBy("()")) {
                argsToken = sToken;
                stage = 2;
            } else if (stage == 2 && sToken.compare(";")) {
                stage = -1;
            } else {
                lastHasErro = true;
                addCleanErro("unexpected token", sToken);
            }
        }
        if (stage == 2) {
            addCleanErro("semicolon expected", token.byLastChar());
        } else if (stage != -1) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
            setWrong();
        }
    }

    @Override
    public void load() {
        ArrayList<Token> argsTokens = LineCall.splitParameters(argsToken);
        Pointer pointers[] = new Pointer[argsTokens.size()];
        for (int i = 0; i < argsTokens.size(); i++) {
            LineBlock argLine = new LineBlock(getCommandContainer(), argsTokens.get(i), false, false);
            argLine.load();
            argLine.requestGetAcess();
            args.add(argLine);
            pointers[i] = argLine.getReturnType();
            if (argLine.isWrong()) setWrong();
        }
        if (!isWrong()) {
            Field field = getCommandContainer().findField(!getStack().isStatic(), keywordToken);
            if (field == null) {
                addErro("invalid constructor call", token);
            } else {
                path = field.getType();
                Constructor constructors[] = field.getType().findConstructor(pointers);
                if (constructors.length == 1) {
                    innerConstructorCall = constructors[0];
                    LineBlock.requestPerfectParams(args, innerConstructorCall.params);
                } else if (constructors.length > 1) {
                    addErro("ambiguous signature", token);
                } else {
                    addErro("unknown constructor signature", token);
                }
            }
        }
    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {

    }
}