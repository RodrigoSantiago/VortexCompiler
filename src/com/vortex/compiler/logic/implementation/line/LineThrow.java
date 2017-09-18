package com.vortex.compiler.logic.implementation.line;

import com.vortex.compiler.content.Token;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.implementation.block.Block;
import com.vortex.compiler.logic.implementation.lineblock.LineBlock;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 15/10/2016
 */
public class LineThrow extends Line {
    public static final String keyword = "throw";

    //Escrita
    public Token keywordToken, valueToken;
    //Lógica
    public LineBlock valueLine;

    public LineThrow(Block container, Token token, Token[] tokens) {
        super(container, token);

        Token sContent = null;
        Token eContent = null;
        boolean lastHasErro = false;
        int stage = 0;
        for (Token sToken : tokens) {
            lastHasErro = false;
            if (stage == 0 && sToken.compare(keyword)) {
                keywordToken = sToken;
                stage = 1;
            } else if (stage == 1 && sToken.compare(";")) {
                stage = -1;
            } else if (stage == 1) {
                if (sContent == null) sContent = sToken;
                eContent = sToken;
            } else {
                lastHasErro = true;
                addCleanErro("unexpected token", sToken);
            }
        }
        if (stage != -1) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
        }
        if (sContent != null) valueToken = sContent.byAdd(eContent);
    }

    @Override
    public void load() {
        if (valueToken != null) {
            valueLine = new LineBlock(getCommandContainer(), valueToken, true, false);
            valueLine.load();
            valueLine.requestGetAcess();
            valueLine.setAutoCasting(DataBase.defExceptionPointer, false);
        } else {
            addCleanErro("exception expected", token);
        }
    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {
        cBuilder.idt(indent).add("throw ").add(valueLine).add(";").ln();
    }
}