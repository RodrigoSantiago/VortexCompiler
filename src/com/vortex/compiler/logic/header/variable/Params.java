package com.vortex.compiler.logic.header.variable;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.content.TokenSplitter;
import com.vortex.compiler.logic.LogicToken;
import com.vortex.compiler.logic.typedef.Pointer;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 08/10/2016
 */
public class Params {

    public ArrayList<Token> originaltypeTokens = new ArrayList<>();
    public ArrayList<Token> typeTokens = new ArrayList<>();
    public ArrayList<Token> nameTokens = new ArrayList<>();
    public ArrayList<Token> finalTokens = new ArrayList<>();
    public ArrayList<Pointer> pointers = new ArrayList<>();
    public boolean varArgValue;

    private Token token;
    private LogicToken logicToken;
    private boolean reserveValue, extraGenerics;
    private Pointer varArgPointer;

    private Params() {
    }

    public Params(Token token, LogicToken logicToken) {
        this(token, logicToken, false);
    }

    public Params(Token token, LogicToken logicToken, boolean reserveValue) {
        this.token = token;
        this.logicToken = logicToken;
        this.reserveValue = reserveValue;
        token = token.byNested();
        if (!token.isEmpty()) {
            Token tokens[] = TokenSplitter.split(token, true);

            //[0-final][(0,1)-tipo][2-.][3-.][4-.][(2,5)-nome][(3),]
            Token typeToken = null;
            Token nameToken = null;
            Token finalToken = null;
            Token variableToken = null;
            boolean lastHasErro = false;
            int stage = 0;
            for (Token sToken : tokens) {
                lastHasErro = false;
                if (stage == 0 && SmartRegex.isModifier(sToken)) {
                    if (sToken.compare("final")) {
                        finalToken = sToken;
                        stage = 1;
                    } else {
                        logicToken.addCleanErro("invalid modifier", sToken);
                    }
                } else if ((stage == 0 || stage == 1) && SmartRegex.pointer(sToken)) {
                    typeToken = sToken;
                    stage = 2;
                } else if (stage == 2 && sToken.compare(".")) {
                    variableToken = sToken;
                    stage = 3;
                } else if (stage == 3 && sToken.compare(".")) {
                    stage = 4;
                } else if (stage == 4 && sToken.compare(".")) {
                    variableToken = variableToken.byAdd(sToken);
                    stage = 5;
                } else if ((stage == 2 || stage == 5) && SmartRegex.variableStatement(sToken)) {
                    nameToken = sToken;
                    stage = 6;
                } else if (stage == 6 && sToken.compare(",")) {
                    if (variableToken != null) {
                        logicToken.addCleanErro("unexpected vararg", variableToken);
                    }
                    addParam(nameToken, typeToken, finalToken);
                    nameToken = typeToken = finalToken = variableToken = null;
                    stage = 0;
                } else {
                    lastHasErro = true;
                    logicToken.addCleanErro("unexpected token", sToken);

                    //Força separação de parâmetros
                    if (sToken.compare(",")) {
                        nameToken = typeToken = finalToken = variableToken = null;
                        stage = 0;
                    }
                }
            }
            if (stage != 6) {
                if (!lastHasErro) logicToken.addCleanErro("unexpected end of tokens", token);
            } else {
                if (variableToken != null) {
                    varArgValue = true;
                }
                addParam(nameToken, typeToken, finalToken);
            }
            typeTokens = (ArrayList<Token>) originaltypeTokens.clone();
        }
    }

    private void addParam(Token nameToken, Token typeToken, Token finalToken) {
        if (reserveValue && nameToken.compare("value")) {
            logicToken.addCleanErro("value is a reserved varible name", nameToken);
        } else if (nameTokens.contains(nameToken)) {
            logicToken.addCleanErro("repeated varible name", nameToken);
        } else {
            nameTokens.add(nameToken);
            finalTokens.add(finalToken);
            originaltypeTokens.add(typeToken);
            if (SmartRegex.isKeyword(nameToken)) {
                logicToken.addCleanErro("illegal name", nameToken);
            }
        }
    }

    public void load(GenericStatement extraGenerics, boolean isStatic) {
        this.extraGenerics = extraGenerics != null && !extraGenerics.isEmpty();
        for (int i = 0; i < typeTokens.size(); i++) {
            Pointer pointer = logicToken.getContainer().workspace.getPointer(logicToken, typeTokens.get(i), extraGenerics, isStatic);
            if (pointer != null) {
                if (varArgValue && i == typeTokens.size() - 1) {
                    varArgPointer = pointer;
                    pointers.add(pointer.byArray(1));
                } else {
                    pointers.add(pointer);
                }
            } else {
                logicToken.addCleanErro("unknown typedef", typeTokens.get(i));
                nameTokens.remove(i);
                typeTokens.remove(i);
                finalTokens.remove(i--);
            }
        }
    }

    public Params byGenerics(Pointer[] replacement) {
        if (!hasGenerics()) return this;
        Params params = new Params();

        params.token = token;
        params.nameTokens = nameTokens;
        params.typeTokens = typeTokens;
        params.finalTokens = finalTokens;
        params.varArgValue = varArgValue;
        params.varArgPointer = varArgPointer == null ? null : varArgPointer.byGenerics(replacement);
        for (Pointer variable : pointers) params.pointers.add(variable.byGenerics(replacement));
        return params;
    }

    public Params byInnerGenerics(Pointer[] innerGenerics) {
        if (!hasGenerics()) return this;
        Params params = new Params();

        params.token = token;
        params.nameTokens = nameTokens;
        params.typeTokens = typeTokens;
        params.finalTokens = finalTokens;
        params.varArgValue = varArgValue;
        params.varArgPointer = varArgPointer == null ? null : varArgPointer.byInnerGenerics(innerGenerics);
        for (Pointer variable : pointers) params.pointers.add(variable.byInnerGenerics(innerGenerics));
        return params;
    }

    public Params byLambdaGenerics(Pointer[] replacement) {
        Params params = new Params();

        params.token = token;
        for (int i = 1; i < replacement.length; i++) {
            params.nameTokens.add(token);
            params.typeTokens.add(token);
            params.finalTokens.add(null);
            params.pointers.add(replacement[i].byGenerics(replacement));
        }
        return params;
    }

    public boolean hasGenerics() {
        for (Pointer variable : pointers) {
            if (variable.hasGenericIndex()) return true;
        }
        return false;
    }

    public int size() {
        return pointers.size();
    }

    public boolean isEmpty() {
        return pointers.isEmpty();
    }

    public boolean isReserveValue() {
        return reserveValue;
    }

    public boolean hasVarArgs() {
        return varArgValue;
    }

    public Pointer getVarArgPointer() {
        return varArgPointer;
    }

    public boolean isSameSignature(Params params) {
        if (pointers.size() != params.pointers.size()) {
            return false;
        }
        for (int i = 0; i < pointers.size(); i++) {
            if (!pointers.get(i).canBeEqual(params.pointers.get(i))) {
                return false;
            }

        }
        return true;
    }

    public int[] compare(Pointer[] arguments) {
        if (arguments.length != pointers.size()) return null;
        if (arguments.length == 0) return new int[0];

        int[] compared = new int[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            compared[i] = arguments[i].getDifference(pointers.get(i));

            if (compared[i] == -1) return null;
        }
        return compared;
    }

    public int[] compareVarArgs(Pointer[] arguments) {
        if (arguments.length < pointers.size() - 1) return null;
        if (arguments.length == 0) return new int[0];

        int[] compared = new int[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            compared[i] = arguments[i].getDifference(i >= pointers.size() - 1 ? getVarArgPointer() : pointers.get(i));

            if (compared[i] == -1) return null;
        }
        return compared;
    }

    public boolean fullEquals(Params obj) {
        if (extraGenerics || obj.extraGenerics) return false;

        if (pointers.size() == obj.pointers.size()) {
            for (int i = 0; i < pointers.size(); i++) {
                Pointer myPointer = pointers.get(i);
                Pointer otherPointer = obj.pointers.get(i);
                if (!myPointer.fullEquals(otherPointer)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        if (isEmpty()) return "";
        String value = pointers.get(0).toString();
        for (int i = 1; i < pointers.size(); i++) {
            value += "," + pointers.get(i);
        }
        return value;
    }
}
