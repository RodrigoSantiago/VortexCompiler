package com.vortex.compiler.logic.implementation.lineblock.data;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.content.TokenSplitter;
import com.vortex.compiler.logic.header.Method;
import com.vortex.compiler.logic.implementation.lineblock.LineBlock;
import com.vortex.compiler.logic.implementation.lineblock.LineCall;
import com.vortex.compiler.logic.typedef.Pointer;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 24/11/2016
 */
public class DataMethod extends Data {
    public Token nameToken;
    public Method methodCall;
    public Pointer[] captureList;
    public ArrayList<LineBlock> args = new ArrayList<>();

    public DataMethod(LineCall lineCall) {
        Token tokens[] = TokenSplitter.split(lineCall.getToken(), true);
        Token argsToken = null;
        boolean lastHasErro = false;
        int stage = 0;
        for (Token sToken : tokens) {
            lastHasErro = false;
            if (stage == 0 && SmartRegex.simpleName(sToken)) {
                nameToken = sToken;
                stage = 1;
            } else if (stage == 1 && sToken.startsWith("(") && sToken.endsWith(")")) {
                argsToken = sToken;
                stage = -1;
            } else {
                lastHasErro = true;
                lineCall.addCleanErro("unexpected token", sToken);
            }
        }
        if (stage != -1) {
            if (!lastHasErro) lineCall.addCleanErro("unexpected end of tokens", lineCall.getToken().byLastChar());
            lineCall.setWrong();
        } else {
            ArrayList<Token> argsTokens = LineCall.splitParameters(argsToken);
            Pointer[] pointers = new Pointer[argsTokens.size()];
            for (int i = 0; i < argsTokens.size(); i++) {
                Token arg = argsTokens.get(i);
                LineBlock argLine = new LineBlock(lineCall.getCommandContainer(), arg, lineCall.instance, false);
                argLine.load();
                argLine.requestGetAcess();
                args.add(argLine);
                pointers[i] = argLine.getReturnType();
                if (argLine.isWrong()) lineCall.setWrong();
            }

            if (!lineCall.isWrong()) {
                Method[] methods = lineCall.findMethod(nameToken, pointers);
                if (methods.length == 1) {
                    methodCall = methods[0];
                    lineCall.returnType = methodCall.getType();
                    LineBlock.requestPerfectParams(args, methodCall.params);
                    if (methodCall.isAbstract()) {
                        if (lineCall.isFromSuperCall()) {
                            lineCall.addCleanErro("super abstract methods should not be called", nameToken);
                        }
                    }
                    if (!methodCall.generics.isEmpty()){
                        captureList = methodCall.getOriginal().getCaptureList(pointers);
                    }
                } else if (methods.length > 1) {
                    lineCall.addErro("ambiguous reference", nameToken.byAdd(argsToken));
                } else {
                    lineCall.addErro("unknown method signature", nameToken.byAdd(argsToken));
                }
            }
        }
    }
}
