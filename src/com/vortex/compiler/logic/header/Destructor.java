package com.vortex.compiler.logic.header;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.implementation.Stack;
import com.vortex.compiler.logic.typedef.Pointer;
import com.vortex.compiler.logic.typedef.Typedef;

import static com.vortex.compiler.logic.Type.*;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 07/10/2016
 */
public class Destructor extends Header {

    //Leitura
    public Token nameToken, parametersToken, contentToken;

    //Implementacao
    public Stack stack;

    public Destructor(Typedef container, Token token, Token[] tokens) {
        super(container, token, tokens, DESTRUCTOR, true, false, false, false, false);

        //[0-modificadores][1-~][2-nome][3-()][4-;|{}]
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

            if (stage == 1 && sToken.compare("~")) {
                stage = 2;
            } else if (stage == 2 && SmartRegex.simpleName(sToken)) {
                nameToken = sToken;
                if (!sToken.compare(container.getName())) {
                    addCleanErro("destructors should have the same name as typedef", sToken);
                }
                stage = 3;
            } else if (stage == 3 && sToken.isClosedBy("()")) {
                parametersToken = sToken;
                stage = 4;
            } else if (stage == 4 && (sToken.isClosedBy("{}") || sToken.compare(";"))) {
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
        }
    }

    @Override
    public void load() {
        if (!parametersToken.compare("()")) {
            addCleanErro("destructors should not have parameters", parametersToken);
        }
        if (!hasImplementation()) {
            addCleanErro("destructors should implement", contentToken);
        }
    }

    @Override
    public void make() {
        if (hasImplementation()) {
            stack = new Stack(contentToken.byNested(), this, null, null, Pointer.voidPointer, false);
            stack.load();
        }
    }

    @Override
    public void build(CppBuilder cBuilder) {
        Pointer pointer = getContainer().getPointer();

        //Header
        cBuilder.toHeader();
        cBuilder.add("\tvoid destroy();").ln();

        //Source
        cBuilder.toSource();
        cBuilder.ln()
                .add(getContainer().generics)
                .add("void ").path(pointer).add("::destroy() ").begin(1);

        stack.build(cBuilder, 1);

        if (getContainer().parents.size() > 0) {
            cBuilder.add("\t").path(getContainer().parents.get(0)).add("::").add("destroy();").ln();
        }
        cBuilder.end();
    }

    public boolean hasImplementation() {
        return contentToken != null && contentToken.isClosedBy("{}");
    }

    @Override
    public String toString() {
        return "~" + nameToken + "()";
    }

}