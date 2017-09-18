package com.vortex.compiler.erro;

import com.vortex.compiler.logic.LogicToken;
import com.vortex.compiler.content.Token;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 01/10/2016
 */
public class Erro {

    public final ErroType type;
    public final String text;
    public final int startPos, endPos;

    public Erro(ErroType type, String text, int startPos, int endPos){
        this.type = type;
        this.text = text;
        this.startPos = startPos;
        this.endPos = endPos;
    }

    public Erro(ErroType type, String text, Token token){
        this.type = type;
        this.text = text;
        this.startPos = token.fileStartpos();
        this.endPos = token.fileEndpos();
    }

    public Erro(ErroType type, String text, LogicToken logicToken){
        this.type = type;
        this.text = text;
        this.startPos = logicToken.getToken().fileStartpos();
        this.endPos = logicToken.getToken().fileEndpos();
    }

    public boolean isNoTyped(){
        return type == null;
    }

    public boolean isLexer(){
        return type == ErroType.LEXER;
    }

    public boolean isWarning(){
        return type == ErroType.WARNING;
    }

    public boolean isErro(){
        return type == ErroType.ERRO;
    }

    @Override
    public String toString() {
        return text+" at : {"+startPos+","+endPos+"}";
    }
}
