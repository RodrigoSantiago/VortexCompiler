package com.vortex.compiler.logic.header.variable;

import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.Acess;
import com.vortex.compiler.logic.Document;
import com.vortex.compiler.logic.LogicToken;
import com.vortex.compiler.logic.Type;
import com.vortex.compiler.logic.implementation.block.Block;
import com.vortex.compiler.logic.typedef.Typedef;
import com.vortex.compiler.logic.typedef.Pointer;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 09/10/2016
 */
public class Field extends LogicToken implements Document {
    public final Type type;

    public Block localOrigem;

    private String nameValue;
    private Pointer typeValue;
    private Acess acessGet, acessSet;
    private boolean staticValue, finalValue, abstractValue, setValue, getValue;

    public Field(Type type, Token token, Typedef container, String nameValue, Pointer typeValue,
                 boolean staticValue, boolean finalValue, boolean abstractValue,
                 boolean setValue, boolean getValue, Acess acessGet, Acess acessSet) {
        this.typedef = container;
        this.token = token;
        this.strFile = token.getStringFile();
        this.type = type;

        this.nameValue = nameValue;
        this.typeValue = typeValue;
        this.staticValue = staticValue;
        this.finalValue = finalValue;
        this.abstractValue = abstractValue;

        this.setValue = setValue;
        this.getValue = getValue;

        this.acessGet = acessGet;
        this.acessSet = acessSet;
    }

    public Field byGenerics(Pointer[] replacement) {
        if (isStatic() || !typeValue.hasGenericIndex()) return this;

        return new Field(type, token, typedef, nameValue, typeValue.byGenerics(replacement),
                staticValue, finalValue, abstractValue,
                setValue, getValue, acessGet, acessSet);
    }

    public Pointer getType() {
        return typeValue;
    }

    public String getName() {
        return nameValue;
    }

    /**
     * Nao ser statico impede de usar a variavel dentro de metodos staticos
     *
     * @return true-false
     */
    public boolean isStatic() {
        return staticValue;
    }

    public void setStatic(boolean staticValue) {
        this.staticValue = staticValue;
    }

    /**
     * Bloqueia set fora de construtores, embora, nao ter set bloqueia em todos os lugares
     *
     * @return true-false
     */
    public boolean isFinal() {
        return finalValue;
    }

    public void setFinal(boolean finalValue) {
        this.finalValue = finalValue;
    }

    /**
     * Campos abstratos nao podem ser chamados pelo 'super'
     *
     * @return true-false
     */
    public boolean isAbstract() {
        return abstractValue;
    }

    public void setAbstract(boolean abstractValue) {
        this.abstractValue = abstractValue;
    }

    /**
     * Set aceito em todos os lugares
     *
     * @return true-false
     */
    public boolean isSettable() {
        return setValue;
    }

    public void setSettable(boolean setValue) {
        this.setValue = setValue;
    }

    /**
     * Get aceito em todos os lugares
     *
     * @return true-false
     */
    public boolean isGettable() {
        return getValue;
    }

    public void setGettable(boolean getValue) {
        this.getValue = getValue;
    }

    /**
     * Acesso para o get
     *
     * @return Acess
     */
    public Acess getAcessGet() {
        return acessGet;
    }

    public void setAcessGet(Acess acessGet) {
        this.acessGet = acessGet;
    }

    /**
     * Acesso para o set
     *
     * @return Acess
     */
    public Acess getAcessSet() {
        return acessSet;
    }

    public void setAcessSet(Acess acessSet) {
        this.acessSet = acessSet;
    }

    @Override
    public String toString() {
        return getName() + " : " + getType();
    }

    @Override
    public String getDocument() {
        return toString();
    }
}
