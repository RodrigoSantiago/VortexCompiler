package com.vortex.compiler.logic.implementation.line;

import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.LogicToken;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.implementation.block.Block;
import com.vortex.compiler.logic.implementation.Stack;
import com.vortex.compiler.logic.implementation.lineblock.LineBlock;
import com.vortex.compiler.logic.typedef.Pointer;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 13/10/2016
 */
public abstract class Line extends LogicToken {
    public Pointer returnType = Pointer.voidPointer;

    private Stack stack;
    private Block container;

    /**
     * Configura os valores padrões da linha
     *
     * @param container  Bloco que contem esta llinha
     * @param token      Token que representa a linha
     */
    protected Line(Block container, Token token) {
        this.token = token;
        this.strFile = token.getStringFile();
        if (container != null) {
            this.container = container;
            this.stack = container.getStack();
            this.typedef = stack.getTypedef();
        }
    }

    /**
     * Tipo de retorno da linha atual
     *
     * @return Retorno da linha
     */
    public Pointer getReturnType() {
        return returnType;
    }

    /**
     * Tipo de retorno da linha atual ignorando castings
     *
     * @return Retorno verdadeiro da linha
     */
    public Pointer getReturnTrueType() {
        return getReturnType();
    }

    /**
     * Bloco que contém esta linha
     *
     * @return Bloco
     */
    public Block getCommandContainer() {
        return container;
    }

    /**
     * Stack que contém esta linha
     *
     * @return Stack
     */
    public Stack getStack() {
        return stack;
    }

    /**
     * Carrega os dados cruzados e faz a verificação sintaxica
     */
    public abstract void load() ;

    /**
     * Escreve em um arquivo de output (C++) um código equivalente aos dados desta linha
     *
     * @param cBuilder Arquivo de output
     * @param indent Identação
     */
    public abstract void build(CppBuilder cBuilder, int indent) ;
}
