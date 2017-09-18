package com.vortex.compiler.logic.implementation.lineblock.data;

import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.header.Indexer;
import com.vortex.compiler.logic.implementation.lineblock.LineBlock;
import com.vortex.compiler.logic.implementation.lineblock.LineCall;
import com.vortex.compiler.logic.typedef.Pointer;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 24/11/2016
 */
public class DataIndexer extends Data {
    public Token indexerTokenBefore, indexerTokenAfter;
    public Indexer indexerCall;
    public ArrayList<LineBlock> args = new ArrayList<>();

    public DataIndexer(LineCall lineCall) {
        //Leitura
        indexerTokenBefore = lineCall.getToken().subSequence(0,1);
        indexerTokenAfter = lineCall.getToken().subSequence(lineCall.getToken().length() - 1);

        //Argumentos
        ArrayList<Token> argsTokens = LineCall.splitParameters(lineCall.getToken());
        Pointer[] pointers = new Pointer[argsTokens.size()];
        for (int i = 0; i < argsTokens.size(); i++) {
            Token arg = argsTokens.get(i);
            LineBlock argLine = new LineBlock(lineCall.getCommandContainer(), arg, lineCall.instance, false);
            argLine.load();
            argLine.requestGetAcess();
            pointers[i] = argLine.getReturnType();
            args.add(argLine);
            if (argLine.isWrong()) lineCall.setWrong();
        }

        //Assinatura
        if (!lineCall.isWrong()) {
            Indexer[] indexers = lineCall.findIndexer(pointers);
            if (indexers.length == 1) {
                indexerCall = indexers[0];
                lineCall.returnType = indexerCall.getType();
                LineBlock.requestPerfectParams(args, indexerCall.params);
                if (indexerCall.isAbstract()) {
                    if (lineCall.isFromSuperCall()) {
                        lineCall.addCleanErro("super abstract indexers should not be called", lineCall.getToken());
                    }
                }
            } else if (indexers.length > 1) {
                lineCall.addErro("ambiguous reference", lineCall.getToken());
            } else {
                lineCall.addErro("unknown indexer signature", lineCall.getToken());
            }
        }
    }
}
