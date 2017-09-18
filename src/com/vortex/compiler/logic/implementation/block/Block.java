package com.vortex.compiler.logic.implementation.block;

import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.implementation.line.Line;
import com.vortex.compiler.logic.typedef.Pointer;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 15/10/2016
 */
public abstract class Block extends Line {
    public ArrayList<Field> fields = new ArrayList<>();
    public ArrayList<Line> lines = new ArrayList<>();

    public Token contentToken;
    public Token labelToken;
    boolean loop;
    boolean caseContainer;

    public Block(Block container, Token token){
        super(container, token);
    }

    //Linhas
    public ArrayList<Line> getLines() {
        return lines;
    }

    /**
     * Verifica se este bloco é ou está dentro de um loop
     *
     * @return true-false
     */
    public boolean isOnLoop() {
        return loop || (getCommandContainer() != null && getCommandContainer().isOnLoop());
    }

    /**
     * Verifica se este bloco é um switch
     *
     * @return true-false
     */
    public boolean isCaseContainer(){
        return caseContainer;
    }

    /**
     * Verifica se este bloco ou um dos seus containers contem um label especifico
     *
     * @param labelToken Label
     * @return true-false
     */
    public boolean isOnLabel(Token labelToken) {
        return labelToken.equals(this.labelToken) ||
                (getCommandContainer() != null && getCommandContainer().isOnLabel(labelToken));
    }

    /**
     * Adiciona uma nova variavel ao bloco
     *
     * @param field Variavel
     * @return true Caso não exista uma variavel com nome especificado e falso caso exista
     */
    public boolean addField(Field field) {
        boolean hasField = hasField(field.getName());
        fields.add(field);
        return !hasField;
    }

    /**
     * Verifica se existe alguma variavel declarada com o nome especificado
     *
     * @param name Nome para busca
     * @return true Caso exista uma variavel com nome especificado e falso caso contrário
     */
    public boolean hasField(String name) {
        for(Field field : fields) {
            if (field.getName().equals(name)) {
                return true;
            }
        }
        return getCommandContainer() != null && getCommandContainer().hasField(name);
    }

    /**
     * Procura e retorna uma variavel com o nome especificado
     *
     * @param instance Se procura a penas em instancia
     * @param name Nome para busca
     * @return Variavel
     */
    public Field findField(boolean instance, CharSequence name) {
        String strName = name.toString();
        for (int i = fields.size() - 1; i >= 0; i--) {
            Field field = fields.get(i);
            if (field.getName().equals(strName)) {
                return field;
            }
        }
        return getCommandContainer() == null ? null : getCommandContainer().findField(instance, name);
    }

    /**
     * Valor de retorno do qual será necessário em uma linha 'return'
     *
     * @return Pointer
     */
    public Pointer getRequestReturn() {
        return getCommandContainer().getRequestReturn();
    }

}
