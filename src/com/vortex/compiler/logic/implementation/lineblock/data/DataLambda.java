package com.vortex.compiler.logic.implementation.lineblock.data;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.content.TokenSplitter;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.header.variable.Params;
import com.vortex.compiler.logic.implementation.block.BlockLambda;
import com.vortex.compiler.logic.implementation.lineblock.LineCall;
import com.vortex.compiler.logic.typedef.Pointer;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 11/01/2017
 */
public class DataLambda extends Data {
    public Token pointerToken, parametersToken, contentToken;
    public Params params;
    public Pointer returnTypePointer;
    public BlockLambda blockLambda;

    public DataLambda(LineCall lineCall) {
        Token tokens[] = TokenSplitter.split(lineCall.getToken(), true);
        boolean lastHasErro = false;
        int stage = 0;
        for (Token sToken : tokens) {
            lastHasErro = false;
            if (stage == 0 && sToken.isClosedBy("()")) {
                parametersToken = sToken;
                stage = 1;
            } else if (stage == 1 && sToken.compare("->")) {
                stage = 2;
            } else if (stage == 2 && SmartRegex.pointer(sToken)) {
                pointerToken = sToken;
                stage = 3;
            } else if ((stage == 2 || stage == 3) && sToken.isClosedBy("{}")) {
                contentToken = sToken;
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
            params = new Params(parametersToken, lineCall);
            params.load(lineCall.getStack().generics, lineCall.getStack().isStatic());

            if (pointerToken == null) {
                returnTypePointer = Pointer.voidPointer;
            } else {
                returnTypePointer = lineCall.getStack().findPointer(pointerToken);
                if (returnTypePointer == null) {
                    lineCall.addCleanErro("unknown typedef", pointerToken);
                    returnTypePointer = Pointer.voidPointer;
                }
            }
            Pointer[] generics = new Pointer[params.pointers.size() + 1];
            generics[0] = returnTypePointer;
            for (int i = 1; i < generics.length; i++) {
                generics[i] = params.pointers.get(i - 1);
            }
            lineCall.returnType = new Pointer(DataBase.defFunction, generics);

            blockLambda = new BlockLambda(lineCall.getCommandContainer(), contentToken, returnTypePointer, params, lineCall.returnType);
            blockLambda.load();
        }
    }
}
