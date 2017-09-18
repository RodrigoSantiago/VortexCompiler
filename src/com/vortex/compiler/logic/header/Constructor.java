package com.vortex.compiler.logic.header;

import com.vortex.compiler.content.Token;
import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.variable.Params;
import com.vortex.compiler.logic.implementation.Stack;
import com.vortex.compiler.logic.implementation.line.LineConstructorCall;
import com.vortex.compiler.logic.typedef.Typedef;
import com.vortex.compiler.logic.typedef.Pointer;

import static com.vortex.compiler.logic.Type.*;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 07/10/2016
 */
public class Constructor extends Header {

    //Leitura
    public Token nameToken, parametersToken, contentToken;

    //Conteudo interno
    public Params params;

    //Implementacao
    public Stack stack;
    public Constructor constrSuper;
    public LineConstructorCall constrCall;

    //Escrita
    private Constructor originalConstructor;

    private Constructor() {
    }

    public Constructor(Typedef container, Token token, Token[] tokens) {
        super(container, token, tokens, CONSTRUCTOR, true, true, false, false, false);
        originalConstructor = this;

        //[0-modificadores][1-nome][2-()][3-;|{}]
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

            if (stage == 1 && SmartRegex.simpleName(sToken)) {
                nameToken = sToken;
                if (!sToken.compare(container.getName())) {
                    addCleanErro("constructors should have the same name as typedef", sToken);
                }
                stage = 2;
            } else if (stage == 2 && sToken.isClosedBy("()")) {
                parametersToken = sToken;
                stage = 3;
            } else if (stage == 3 && (sToken.isClosedBy("{}") || sToken.compare(";"))) {
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
            params = new Params(parametersToken, this);
        }
    }

    @Override
    public void load() {
        if (params != null) params.load(null, isStatic());

        if (!hasImplementation()) {
            addCleanErro("constructors should implement", contentToken);
        }
    }

    @Override
    public void make() {
        if (hasImplementation()) {
            stack = new Stack(contentToken.byNested(), this, null, params, Pointer.voidPointer, !isStatic());
            stack.load();

            if (stack.lines.size() > 0) {
                if (stack.lines.get(0) instanceof LineConstructorCall) {
                    constrCall = (LineConstructorCall) stack.lines.get(0);
                    Constructor constructor = constrCall.innerConstructorCall;
                    if (constructor != null) {
                        if (constructor.getSuperConstructor() != this) {
                            constrSuper = constructor.originalConstructor;
                        } else {
                            addCleanErro("cyclic constructor call", constrCall.getToken());
                        }
                    }
                }
            }

            if (constrSuper == null) {
                if (getContainer().parents.size() > 0) {
                    Constructor constructors[] = getContainer().parents.get(0).findConstructor();
                    if (constructors.length == 1) constrSuper = constructors[0];
                }
            }
        }
    }

    @Override
    public void build(CppBuilder cBuilder) {
        build(cBuilder, getContainer().getPointer());
    }

    public void build(CppBuilder cBuilder, Pointer pointer) {
        Typedef cOrigem = pointer.typedef;
        boolean stolen = getContainer() != cOrigem;
        boolean empty = params.isEmpty();
        boolean copy = params.size() == 1 && params.pointers.get(0).fullEquals(pointer);

        if (isStatic()) {
            //Source
            cBuilder.toSource();

            stack.build(cBuilder, 1);
        } else if (pointer.isStruct()) {

            //Header
            cBuilder.toHeader();
            if (empty) {
                cBuilder.add("\t").add(cOrigem.unicFullName).add("(InitKey init);").ln();
            } else if (copy) {
                cBuilder.add("\t").add(cOrigem.unicFullName).add("(InitKey init, ").add(params).add(");").ln();
            } else {
                cBuilder.add("\t").add(cOrigem.unicFullName).add("(").add(params).add(");").ln();
            }

            //Source
            cBuilder.toSource();
            cBuilder.ln();
            if (empty) {
                cBuilder.path(pointer).add("::").add(cOrigem.unicFullName).add("(InitKey init)");
            } else if (copy) {
                cBuilder.path(pointer).add("::").add(cOrigem.unicFullName).add("(InitKey init, ").add(params).add(")");
            } else {
                cBuilder.path(pointer).add("::").add(cOrigem.unicFullName).add("(").add(params).add(")");
            }

            if (stolen) {
                cBuilder.add(" ").begin(1);
            } else if (constrCall != null) {
                cBuilder.add(" : ").add(cOrigem.unicFullName).add("(").add(constrCall.innerConstructorCall.params, constrCall.args).add(") ").begin(1);
            } else {
                cBuilder.add(" ").begin(1)
                        .add("\tinitInstance();").ln();
            }

            stack.build(cBuilder, 1);

            cBuilder.end();
        } else {
            //Header
            cBuilder.toHeader();
            cBuilder.add("\t").add(pointer).add(" build(").add(params).add(");").ln();

            //Source
            cBuilder.toSource();
            cBuilder.ln()
                    .add(cOrigem.generics)
                    .add(pointer).add(" ").path(pointer).add("::build(").add(params).add(") ").begin(1);
            if (stolen) {
                cBuilder.add("\t").path(pointer.typedef.parents.get(0)).add("::").add("build(").args(params).add(");").ln()
                        .add("\tinitInstance();").ln();
            } else if (constrCall != null) {
                if (constrCall.path.typedef != cOrigem){
                    cBuilder.add("\t").path(constrCall.path).add("::").add("build(").add(constrCall.innerConstructorCall.params, constrCall.args).add(");").ln()
                            .add("\tinitInstance();").ln();
                } else {
                    cBuilder.add("\tbuild(").add(constrCall.innerConstructorCall.params, constrCall.args).add(");").ln();
                }
            } else {
                cBuilder.add("\tinitInstance();").ln();
            }

            stack.build(cBuilder, 1);

            cBuilder.add("\treturn this;").ln()
                    .end();
        }
    }

    public boolean hasImplementation() {
        return contentToken != null && contentToken.isClosedBy("{}");
    }
    public boolean hasVarArgs() {
        return params.hasVarArgs();
    }

    public Constructor byGenerics(Pointer[] replacement) {
        if (isStatic() || !params.hasGenerics()) return this;

        Constructor constructor = new Constructor();
        constructor.type = type;

        constructor.originalConstructor = originalConstructor;

        constructor.strFile = strFile;
        constructor.token = token;
        constructor.wrong = wrong;
        constructor.typedef = typedef;

        constructor.acessToken = acessToken;
        constructor.modifierToken = modifierToken;
        constructor.staticToken = staticToken;
        constructor.volatileToken = volatileToken;

        constructor.acessValue = acessValue;
        constructor.staticValue = staticValue;
        constructor.finalValue = finalValue;
        constructor.abstractValue = abstractValue;
        constructor.volatileValue = volatileValue;

        constructor.constrSuper = constrSuper;

        constructor.nameToken = nameToken;
        constructor.parametersToken = parametersToken;
        constructor.contentToken = contentToken;

        constructor.params = params.byGenerics(replacement);
        return constructor;
    }

    public boolean isSameSignature(Constructor other) {
        if (staticValue && other.staticValue) {
            return true;
        } else if (staticValue == other.staticValue) {
            return this.params.isSameSignature(other.params);
        } else {
            return false;
        }
    }

    public Constructor getSuperConstructor() {
        return (constrSuper == null || constrSuper == this) ? this : constrSuper.getSuperConstructor();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj == this) return true;
        if (obj instanceof Constructor) {
            return this == ((Constructor) obj).originalConstructor || originalConstructor == obj;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return nameToken + "(" + params + ")";
    }

}
