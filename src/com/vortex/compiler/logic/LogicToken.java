package com.vortex.compiler.logic;

import com.vortex.compiler.content.StringFile;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.erro.Erro;
import com.vortex.compiler.logic.typedef.Typedef;

import static com.vortex.compiler.erro.ErroType.*;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 01/10/2016
 */
public abstract class LogicToken {

    protected StringFile strFile;
    protected Token token;
    protected boolean wrong;
    protected Typedef typedef;

    /**
     * Token representando este objeto
     *
     * @return Token
     */
    public Token getToken() {
        return token;
    }

    /**
     * Acesso rapido a stringFile do token
     *
     * @return StringFile
     */
    public StringFile getStringFile() {
        return strFile;
    }

    /**
     * Retorna o acesso logico ( Typedef )
     *
     * @return Typedef
     */
    public Typedef getContainer() {
        return typedef;
    }

    /**
     * Retorna verdadeira caso algum erro fatal for encontrado neste objeto
     *
     * @return true-false
     */
    public boolean isWrong() {
        return wrong;
    }

    /**
     * modifica o estatus de erro fatal para verdadeiro
     */
    public void setWrong() {
        wrong = true;
    }

    /**
     * Adiciona um Erro e modifica o estado para Erro Fatal ( wrong )
     *
     * @param erro Erro
     */
    public void addErro(Erro erro) {
        strFile.addErro(erro);
        setWrong();
    }

    public void addErro(String text, int startPos, int endPos) {
        strFile.addErro(ERRO, text, startPos, endPos);
        setWrong();
    }

    public void addErro(String text, Token token) {
        if (token.startsWith("{") && token.length() > 2) {
            strFile.addErro(ERRO, text, token.subSequence(0, 1));
        } else {
            strFile.addErro(ERRO, text, token);
        }
        setWrong();
    }

    public void addErro(String text, LogicToken logicToken) {
        strFile.addErro(ERRO, text, logicToken);
        setWrong();
    }

    /**
     * Adiciona um Erro mas nao modifica o estado  atual
     *
     * @param erro Erro
     */
    public void addCleanErro(Erro erro) {
        strFile.addErro(erro);
    }

    public void addCleanErro(String text, int startPos, int endPos) {
        strFile.addErro(ERRO, text, startPos, endPos);
    }

    public void addCleanErro(String text, Token token) {
        if (token.startsWith("{") && token.length() > 2) {
            strFile.addErro(ERRO, text, token.subSequence(0, 1));
        } else {
            strFile.addErro(ERRO, text, token);
        }
    }

    public void addCleanErro(String text, LogicToken logicToken) {
        strFile.addErro(ERRO, text, logicToken);
    }

    /**
     * Adiciona um Warning mas nao modifica o estado  atual
     *
     * @param erro Erro
     */
    public void addWarning(Erro erro) {
        strFile.addErro(erro);
    }

    public void addWarning(String text, int startPos, int endPos) {
        strFile.addErro(WARNING, text, startPos, endPos);
    }

    public void addWarning(String text, Token token) {
        if (token.startsWith("{") && token.length() > 2) {
            strFile.addErro(WARNING, text, token.subSequence(0, 1));
        } else {
            strFile.addErro(WARNING, text, token);
        }
    }

    public void addWarning(String text, LogicToken logicToken) {
        strFile.addErro(WARNING, text, logicToken);
    }

}
