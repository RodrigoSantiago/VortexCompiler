package com.vortex.compiler.logic.header;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.variable.GenericStatement;
import com.vortex.compiler.logic.header.variable.Params;
import com.vortex.compiler.logic.implementation.Stack;
import com.vortex.compiler.logic.space.Workspace;
import com.vortex.compiler.logic.typedef.Typedef;
import com.vortex.compiler.logic.typedef.Pointer;

import static com.vortex.compiler.logic.Type.*;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 07/10/2016
 */
public class Method extends Header {

    //Leitura
    public Token typeToken, nameToken, genericsToken, parametersToken, contentToken;

    //Conteudo Interno
    public Params params;
    public GenericStatement generics = new GenericStatement();

    private String nameValue;
    private Pointer typeValue;

    //Implementacao
    public Stack stack;

    //Escrita
    private Method originalMethod;
    private boolean isOverriden, isOverrider;
    private boolean main;

    private Method() {
    }

    public Method(Typedef container, Token token, Token[] tokens) {
        super(container, token, tokens, METHOD, true, true, true, true, false);
        originalMethod = this;

        //[0-modifiers][1-type][2-name|generics][3-(...)][4;|{...}]
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
            } else if (stage == 2) {
                if (SmartRegex.simpleName(sToken)) {
                    nameToken = sToken;
                    genericsToken = null;
                    if (SmartRegex.isKeyword(nameToken)) {
                        addCleanErro("illegal name", sToken);
                    } else if (nameToken.compare(container.getName())) {
                        addCleanErro("methods should not have the same name as typedef", sToken);
                    }
                    stage = 3;
                } else if (SmartRegex.methodStatement(sToken)) {
                    nameToken = sToken.subSequence(0, sToken.indexOf('<'));
                    genericsToken = sToken.subSequence(sToken.indexOf('<'), sToken.length());
                    if (SmartRegex.isKeyword(nameToken)) {
                        addCleanErro("illegal name", sToken);
                    } else if (nameToken.compare(container.getName())) {
                        addCleanErro("methods should not have the same name as typedef", sToken);
                    }
                    generics.read(this, genericsToken);
                    stage = 3;
                } else {
                    lastHasErro = true;
                    addCleanErro("unexpected token", sToken);
                }
            } else if (stage == 3 && sToken.isClosedBy("()")) {
                parametersToken = sToken;
                stage = 4;
            } else if (stage == 4 && (sToken.compare(";") || sToken.isClosedBy("{}"))) {
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
            nameValue = nameToken.toString();
            params = new Params(parametersToken, this);
        }
    }

    @Override
    public void load() {
        //Generics
        generics.load();

        //Parameters
        params.load(generics, isStatic());

        //Generic Unloaded
        for (int i = 0; i < generics.size(); i++) {
            if (!generics.wasUsed(i)) {
                addErro("all declared generics should be used in parameters", generics.nameTokens.get(i));
            }
        }

        //Return
        if (typeToken.compare("void")) {
            typeValue = Pointer.voidPointer;
        } else if (typeToken.compare("auto")) {
            addCleanErro("auto is not allowed here", typeToken);
            typeValue = Pointer.voidPointer;
        } else {
            typeValue = getWorkspace().getPointer(this, typeToken, generics, isStatic());
            if (typeValue == null) {
                addErro("unknown typedef", typeToken);
                typeValue = DataBase.defObjectPointer;
            }
        }
    }

    @Override
    public void make() {
        if (hasImplementation()) {
            stack = new Stack(contentToken.byNested(), this, generics, params, typeValue, false);
            stack.load();
        }
    }

    public void mainLoad(Workspace workspace) {
        if (genericsToken != null) {
            addCleanErro("generics are not allowed here", genericsToken);
        }

        if (parametersToken != null && !parametersToken.compare("()")) {
            addCleanErro("parameters are not allowed here", parametersToken);
        }

        typedef = new Typedef(workspace, getToken());

        //Return
        if (typeToken.compare("void")) {
            typeValue = DataBase.defIntPointer;
        } else if (typeToken.compare("auto")) {
            addCleanErro("main method should return int", typeToken);
        } else {
            typeValue = workspace.getPointer(this, typeToken, null, true);
            if (typeValue == null) {
                addCleanErro("unknown typedef", typeToken);
            } else if (!typeValue.fullEquals(DataBase.defIntPointer)) {
                addCleanErro("main method should return int", typeToken);
            }
        }
        typeValue = DataBase.defIntPointer;
    }

    @Override
    public void build(CppBuilder cBuilder) {
        if (isMain()) {
            //Source
            cBuilder.toSource();
            cBuilder.add("int main() ").begin(1);

            stack.build(cBuilder, 1);

            cBuilder.end();
        } else {
            Pointer pointer = getContainer().getPointer();

            //Header
            cBuilder.toHeader();

            if (!generics.isEmpty()) cBuilder.add("\t").add(generics);

            cBuilder.add("\t");

            if (isStatic()) {
                cBuilder.add("static ");
            } else if (!isFinal() && !getContainer().isFinal() && generics.isEmpty() ) {
                cBuilder.add("virtual ");
            }

            cBuilder.add(getType()).add(" ").nameMethod(getName()).add("(").add(params).add(")")
                    .add(isAbstract() ? " = 0;" : ";").ln();

            //Source
            cBuilder.toSource();
            if (hasImplementation()) {
                cBuilder.ln()
                        .add(getContainer().generics)
                        .add(generics)
                        .add(getType()).add(" ")
                        .path(pointer).add("::").nameMethod(getName()).add("(").add(params).add(") ").begin(1);

                if (isStatic()) {
                    cBuilder.add("\tinitClass();").ln();
                }

                stack.build(cBuilder, 1);

                cBuilder.end();
            }
        }
    }

    public void buildUsing(CppBuilder cBuilder) {
        //Header
        cBuilder.toHeader();
        for (Pointer parent : getContainer().parents) {
            for (Method pMethod : parent.typedef.getAllMethods()) {
                if (pMethod.getName().equals(getName())) {
                    cBuilder.add("\tusing ").path(parent).add("::").nameMethod(getName()).add(";").ln();
                    return;
                }
            }
        }
    }

    public Pointer getType() {
        return typeValue;
    }

    public String getName() {
        return nameValue;
    }

    public boolean hasImplementation() {
        return contentToken != null && contentToken.isClosedBy("{}");
    }

    public boolean hasVarArgs() {
        return params.hasVarArgs();
    }

    public Method byGenerics(Pointer[] replacement) {
        return byGenerics(replacement, false);
    }

    public Method byInnerGenerics(Pointer[] arguments) {
        if (params.hasGenerics() || typeValue.hasGenericIndex()) {
            return byGenerics(getCaptureList(arguments), true);
        } else {
            return this;
        }
    }

    private Method byGenerics(Pointer[] replacement, boolean inner) {
        if ((getContainer() != DataBase.defFunction) && !typeValue.hasGenericIndex() && !params.hasGenerics()) return this;

        Method method = new Method();
        method.type = type;

        method.originalMethod = originalMethod;

        method.strFile = strFile;
        method.token = token;
        method.wrong = wrong;
        method.typedef = typedef;

        method.acessToken = acessToken;
        method.modifierToken = modifierToken;
        method.staticToken = staticToken;
        method.volatileToken = volatileToken;

        method.acessValue = acessValue;
        method.staticValue = staticValue;
        method.finalValue = finalValue;
        method.abstractValue = abstractValue;
        method.volatileValue = volatileValue;

        method.typeToken = typeToken;
        method.nameToken = nameToken;
        method.genericsToken = genericsToken;
        method.parametersToken = parametersToken;
        method.contentToken = contentToken;
        method.generics = generics;
        method.nameValue = nameValue;

        if (getName().equals("run") && getContainer() == DataBase.defFunction && replacement.length > 0) {
            method.params = params.byLambdaGenerics(replacement);
            method.typeValue = replacement[0];
        } else if (inner) {
            method.params = params.byInnerGenerics(replacement);
            method.typeValue = typeValue.byInnerGenerics(replacement);
        } else {
            method.params = params.byGenerics(replacement);
            method.typeValue = typeValue.byGenerics(replacement);
        }

        return method;
    }

    public Method getOriginal() {
        return originalMethod;
    }

    public boolean isSameSignature(Method other) {
        return this.nameValue.equals(other.nameValue) && this.params.isSameSignature(other.params);
    }

    public boolean isCompatible(Method other) {
        //Mesmo nome
        if (!nameValue.equals(other.nameValue)) return false;
        //Mesmo valores genericos nos parametros
        if (!params.fullEquals(other.params)) return false;
        //Retornos genericos sobrecarregaveis
        if (other.typeValue.hasGenericIndex() || typeValue.hasGenericIndex()) {
            return typeValue.fullEquals(other.typeValue);
        } else {
            return (typeValue == other.typeValue && typeValue == Pointer.voidPointer) ||
                    typeValue.isInterface() || other.typeValue.isInterface() ||
                    typeValue.isInstanceOf(other.typeValue) || other.typeValue.isInstanceOf(typeValue);
        }
    }

    public boolean isOverridable(Method other) {
        //Mesmo nome
        if (!nameValue.equals(other.nameValue)) return false;
        //Mesmo valores genericos nos parametros
        if (!other.params.fullEquals(params)) return false;
        //Retornos genericos sobrecarregaveis
        if (other.typeValue.hasGenericIndex() || typeValue.hasGenericIndex()) {
            return typeValue.fullEquals(other.typeValue);
        } else {
            return (typeValue == other.typeValue && typeValue == Pointer.voidPointer) ||
                    typeValue.isInstanceOf(other.typeValue);
        }
    }

    public void setOverrider() {
        isOverrider = true;
        originalMethod.isOverrider = true;
    }

    public boolean isOverrider() {
        return isOverrider;
    }

    public void setOverriden() {
        isOverriden = true;
        originalMethod.isOverriden = true;
    }

    public boolean isOverriden() {
        return isOverriden;
    }

    public Pointer[] getCaptureList(Pointer[] arguments) {
        Pointer[] replacement = new Pointer[generics.size()];
        for (int i = 0; i < arguments.length; i++) {
            if (hasVarArgs() && i >= params.pointers.size() - 1) {
                nestedCapture(replacement, params.getVarArgPointer(), arguments[i]);
            } else {
                nestedCapture(replacement, params.pointers.get(i), arguments[i]);
            }
        }
        for (int i = 0; i < generics.size(); i++) {
            if (replacement[i] == null) replacement[i] = generics.defReplacement[i];
        }
        return replacement;
    }

    private void nestedCapture(Pointer[] replacement, Pointer param, Pointer arg) {
        if (param.genIndex != -1 && param.innerGeneric) {
            Pointer genPointer = generics.pointers.get(param.genIndex);
            if (arg.isInstanceOf(genPointer) &&
                    (replacement[param.genIndex] == null ||
                            replacement[param.genIndex].getDifference(genPointer) > arg.getDifference(genPointer))) {
                replacement[param.genIndex] = arg;
            }
        }
        for (int i = 0; i < param.generics.length; i++) {
            if (i < arg.generics.length) {
                nestedCapture(replacement, param.generics[i], arg.generics[i]);
            } else {
                break;
            }
        }
    }

    @Override
    public String toString() {
        return nameValue + "(" + params + ") : " + typeValue;
    }

    public boolean isMain() {
        return main;
    }

    public void setMain(boolean main) {
        this.main = main;
    }
}