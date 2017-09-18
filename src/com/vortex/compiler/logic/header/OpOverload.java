package com.vortex.compiler.logic.header;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.Acess;
import com.vortex.compiler.logic.Operator;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.variable.Params;
import com.vortex.compiler.logic.implementation.Stack;
import com.vortex.compiler.logic.typedef.Typedef;
import com.vortex.compiler.logic.typedef.Pointer;

import static com.vortex.compiler.logic.Type.*;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 07/10/2016
 */
public class OpOverload extends Header {

    //Leitura
    public Token typeToken, operatorToken, parametersToken, contentToken;

    //Conteudo Interno
    public Params params;
    public boolean reverse;

    private Pointer typeValue;
    private String operatorValue;

    //Implementacao
    public Stack stack;

    public OpOverload(Typedef container, Token token, Token[] tokens) {
        super(container, token, tokens, OPERATOR, false, false, false, false, false);
        acessValue = Acess.PUBLIC;

        //[0-modificadores][1-type][2-operator][3-operatoroverload][4-()][5-;{}]
        boolean lastHasErro = false;
        int stage = 0;
        for (Token sToken : tokens) {
            lastHasErro = false;

            //Modificadores
            if (stage == 0) {
                if (SmartRegex.isModifier(sToken)) {
                    continue;
                } else {
                    stage = 1;
                }
            }

            if (stage == 1 && (SmartRegex.pointer(sToken) || sToken.compare("void"))) {
                typeToken = sToken;
                stage = 2;
            } else if (stage == 2 && sToken.compare("operator")) {
                stage = 3;
            } else if (stage == 3 && SmartRegex.isOpOverload(sToken)) {
                operatorToken = sToken;
                stage = 4;
            } else if (stage == 4 && sToken.startsWith("(") && sToken.endsWith(")")) {
                parametersToken = sToken;
                stage = 5;
            } else if (stage == 5 && (sToken.isClosedBy("{}") || sToken.compare(";"))) {
                contentToken = sToken;
                stage = -1;
            } else {
                lastHasErro = true;
                addCleanErro("unexpected token", sToken);
            }
        }
        if (stage != -1) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
            setWrong();
        } else {
            operatorValue = operatorToken.toString();
            params = new Params(parametersToken, this);
        }
    }

    @Override
    public void load() {
        if (typeToken.compare("void")) {
            addErro("void is not allowed here", typeToken);
            typeValue = DataBase.defObjectPointer;
        } else if (typeToken.compare("auto")) {
            addErro("auto is not allowed here", typeToken);
            typeValue = DataBase.defObjectPointer;
        } else {
            typeValue = getWorkspace().getPointer(this, typeToken, isStatic());
            if (typeValue == null) {
                addErro("unknown typedef", typeToken);
                typeValue = DataBase.defObjectPointer;
            }
        }

        params.load(null, isStatic());

        if (operatorValue.equals("cast") || operatorValue.equals("autocast") || operatorValue.equals("!") ||
                operatorValue.equals("++") || operatorValue.equals("--") || operatorValue.equals("~")) {
            if (params.isEmpty()) {
                addErro("this operators should have a parameter", parametersToken);
            } else if (params.size() > 1) {
                addErro("this operators should have only a single parameter", parametersToken);
            }
        } else if (operatorValue.equals("+") || operatorValue.equals("-")) {
            if (params.isEmpty()) {
                addErro("this operator should have at least one parameter", parametersToken);
            } else if (params.size() > 2) {
                addErro("this operator should have at most two parameters", parametersToken);
            }
        } else {
            if (params.size() < 2) {
                addErro("operators should have two parameters", parametersToken);
            } else if (params.size() > 2) {
                addErro("operators should have only two parameters", parametersToken);
            }
        }

        if (!isWrong()) {
            if ((operatorValue.equals("==") || operatorValue.equals("!=")) &&
                    !typeValue.equals(DataBase.defBoolPointer)) {
                addErro("this operators should return a bool", typeToken);
            } else if ((operatorValue.equals("cast") || operatorValue.equals("autocast")) &&
                    (typeValue.fullEquals(getContainer().getPointer()) ||
                            typeValue.fullEquals(getContainer().getPointer().getWrapper()))) {
                addErro("casting operators should not match the struct or wrapper", typeToken);
            }
        }
    }

    @Override
    public void make() {
        if (hasImplementation()) {
            stack = new Stack(contentToken.byNested(), this, null, params, typeValue, false);
            stack.load();
        }
    }

    @Override
    public void build(CppBuilder cBuilder) {
        Pointer pointer = getContainer().getPointer();
        Operator operator = Operator.fromToken(getOperator());

        //Header
        cBuilder.toHeader();
        cBuilder.add("\tstatic ").add(getType()).add(" ");

        if (getOperator().equals("cast") || getOperator().equals("autocast")) {
            cBuilder.add("castTo").namePointer(getType());
        } else {
            cBuilder.add(operator);
        }

        cBuilder.add("(").add(params).add(");").ln();

        //Source
        cBuilder.toSource();
        if (hasImplementation()) {
            cBuilder.ln()
                    .add(getType()).add(" ").path(pointer).add("::");

            if (getOperator().equals("cast") || getOperator().equals("autocast")) {
                cBuilder.add("castTo").namePointer(getType());
            } else {
                cBuilder.add(operator);
            }

            cBuilder.add("(").add(params).add(") ").begin(1);

            stack.build(cBuilder, 1);

            cBuilder.end();
        }

        if (operator.isIncrement()) {
            //Header
            cBuilder.toHeader();
            cBuilder.add("\t").add(getType()).add(" l").add(operator).add("();").ln();

            //Source
            cBuilder.toSource();
            cBuilder.ln()
                    .add(getType()).add(" ").path(pointer).add("::l").add(operator).add("() ").begin(1)
                    .add("\t").add(getType()).add(" oldValue = (*this);").ln()
                    .add("\t(*this) = ").add(operator).add("(*this);").ln()
                    .add("\treturn oldValue;").ln()
                    .end();
        }
    }

    public String getOperator() {
        return operatorValue;
    }

    public Pointer getType() {
        return typeValue;
    }

    public boolean hasImplementation() {
        return contentToken != null && contentToken.isClosedBy("{}");
    }

    public boolean isReverse() {
        return reverse;
    }

    public boolean isSameSignature(OpOverload other) {
        if (operatorValue.equals("cast") || operatorValue.equals("autocast")) {
            return operatorValue.equals(other.operatorValue) && typeValue.fullEquals(other.typeValue);
        } else {
            return operatorValue.equals(other.operatorValue) && params.isSameSignature(other.params);
        }
    }

    @Override
    public String toString() {
        return operatorValue + "(" + params + ") : " + typeValue;
    }

}
