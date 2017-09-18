package com.vortex.compiler.logic;

import com.vortex.compiler.content.Token;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.header.Header;
import com.vortex.compiler.logic.space.Workspace;
import com.vortex.compiler.logic.typedef.Typedef;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 05/10/2016
 */
public enum Acess {
    //PUBLIC - Acesso irestrito
    //PROTECTED - Restringe o uso para typedefs de biblioteca e linha de parentesco diferente
    //INTERN - Restringe uso para typedefs de biblioteca diferente
    //DEFAULT - Restringe o uso para typedefs de namespace diferente
    //PRIVATE - Restringe o uso para typedefs diferentes
    PUBLIC(true, 0), PROTECTED(false, 1), INTERNAL(true, 2), DEFAULT(true, 3), PRIVATE(false, 4);

    private final boolean simple;

    private final int privacy;

    Acess(boolean simple, int privacy) {
        this.simple = simple;
        this.privacy = privacy;
    }

    public String getKeyword() {
        return this.toString().toLowerCase();
    }
    /**
     * Modificadores simples podem ser usados na declaracao de typedefs
     *
     * @return true-false
     */
    public boolean isSimple() {
        return simple;
    }

    /**
     * Verifica se este acesso e mais fortemente fechado no encapsulamento do que 'acess'
     *
     * @param acess Outro acesso
     * @return true-false
     */
    public boolean isMostPrivate(Acess acess) {
        return this.privacy > acess.privacy;
    }

    /**
     * Retorna o acesso pelo token (default nao possui palavra-chave)
     *
     * @param token Referencial de texto
     * @return Acess (Default para null)
     */
    public static Acess fromToken(Token token) {
        if (token == null) return DEFAULT;
        switch (token.toString()) {
            case "public":
                return PUBLIC;
            case "protected":
                return PROTECTED;
            case "internal":
                return INTERNAL;
            case "private":
                return PRIVATE;
            default:
                return DEFAULT;
        }
    }

    /**
     * Retorna o acesso pela string (default nao possui palavra-chave)
     *
     * @param str Texto
     * @return Acess (Default para null)
     */
    public static Acess fromToken(String str) {
        if (str == null) return DEFAULT;
        switch (str) {
            case "public":
                return PUBLIC;
            case "protected":
                return PROTECTED;
            case "internal":
                return INTERNAL;
            case "private":
                return PRIVATE;
            default:
                return DEFAULT;
        }
    }

    /**
     * Verifica o acesso
     *
     * @param origem Origem
     * @param targetOrigem Alvo
     * @param targetAcess Acesso do  alvo
     * @return true-false
     */
    public static boolean TesteAcess(Typedef origem, Typedef targetOrigem, Acess targetAcess) {
        switch (targetAcess) {
            case PUBLIC:
                return true;
            case PROTECTED:
                if (origem.isInstanceOf(targetOrigem)) return true;
            case INTERNAL:
                if (origem.workspace.nameSpace.isSameProject(targetOrigem.workspace.nameSpace)) return true;
            case DEFAULT:
                if (origem.workspace.nameSpace.equals(targetOrigem.workspace.nameSpace)) return true;
            default: //PRIVATE
                return (origem == targetOrigem);
        }
    }

    public static boolean TesteAcess(Workspace workspace, Typedef targetOrigem, Acess targetAcess) {
        switch (targetAcess) {
            case PUBLIC:
            case PROTECTED:
                return true;
            case INTERNAL:
                if (workspace.nameSpace.isSameProject(targetOrigem.workspace.nameSpace)) return true;
            case DEFAULT:
            default:
                if (workspace.nameSpace.equals(targetOrigem.workspace.nameSpace)) return true;
        }
        return false;
    }

    public static boolean TesteAcess(LogicToken logicRequest, Typedef typeRequested) {
        return TesteAcess(logicRequest.getContainer(), typeRequested, typeRequested.getAcess());
    }

    public static boolean TesteAcess(LogicToken logicRequest, LogicToken typeRequested, Acess targetAcess){
        return TesteAcess(logicRequest.getContainer(), typeRequested.getContainer(), targetAcess);
    }

    public static boolean TesteAcess(LogicToken logicRequest, Header targetHeader) {
        return TesteAcess(logicRequest, targetHeader, targetHeader.getAcess());
    }
}
