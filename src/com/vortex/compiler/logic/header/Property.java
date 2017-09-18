package com.vortex.compiler.logic.header;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.content.TokenSplitter;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.Acess;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.implementation.Stack;
import com.vortex.compiler.logic.implementation.lineblock.LineBlock;
import com.vortex.compiler.logic.typedef.Typedef;
import com.vortex.compiler.logic.typedef.Pointer;

import static com.vortex.compiler.logic.Type.*;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 07/10/2016
 */
public class Property extends Header {

    //Leitura
    public Token typeToken, nameToken, contentToken, keywordTokenGet, keywordTokenSet,
            acessTokenGet, acessTokenSet, contentTokenGet, contentTokenSet, initToken;

    //Conteudo Interno
    private Field field;
    private String nameValue;
    private Pointer typeValue;
    private Acess acessValueGet, acessValueSet;

    //Implementacao
    public Stack stack, stackGet, stackSet;
    public LineBlock initLine;

    //Escrita
    private Property originalProperty;
    private boolean isOverriden, isOverrider;

    private Property() {
    }

    public Property(Typedef container, Token token, Token[] tokens) {
        super(container, token, tokens, PROPERTY, true, true, true, true, false);

        //[0-modifiers][1-type][2-name][3-{[modifier][get|set][;|{}][modifier][get*|set*][;|{}]]
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

            if (stage == 1 && SmartRegex.pointer(sToken)) {
                typeToken = sToken;
                stage = 2;
            } else if (stage == 2 && SmartRegex.simpleName(sToken)) {
                nameToken = sToken;
                if (SmartRegex.isKeyword(nameToken)) {
                    addCleanErro("illegal name", nameToken);
                }
                stage = 3;
            } else if (stage == 3 && sToken.isClosedBy("{}")) {
                contentToken = sToken;
                readGetSet();
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
        }
    }

    private void readGetSet() {
        Token tokens[] = TokenSplitter.split(contentToken.subSequence(1, contentToken.length() - 1));
        Token acessToken = null;
        Token nameToken = null;

        boolean lastHasErro = false;
        int stage = 0;
        for (Token sToken : tokens) {
            lastHasErro = false;

            if (stage == 0) {
                if (SmartRegex.isAcess(sToken)) {
                    if (acessToken == null) {
                        acessToken = sToken;
                    } else {
                        addCleanErro("repeated acess modifier", sToken);
                    }
                } else if (SmartRegex.isModifier(sToken)) {
                    addCleanErro("invalid modifier", sToken);
                } else if (sToken.compare("get") || sToken.compare("set")) {
                    nameToken = sToken;
                    stage = 1;
                } else {
                    lastHasErro = true;
                    addCleanErro("unexpected token", sToken);
                }
            } else if (sToken.compare(";") || sToken.isClosedBy("{}")) {
                if (nameToken.compare("get")) {
                    if (acessTokenGet == null) {
                        keywordTokenGet = nameToken;
                        contentTokenGet = sToken;
                        acessTokenGet = acessToken;
                    } else {
                        addCleanErro("repeated get", nameToken);
                    }
                    nameToken = null;
                    acessToken = null;
                    stage = 0;
                } else if (nameToken.compare("set")) {
                    if (contentTokenSet == null) {
                        keywordTokenSet = nameToken;
                        contentTokenSet = sToken;
                        acessTokenSet = acessToken;
                    } else {
                        addCleanErro("repeated set", nameToken);
                    }
                    nameToken = null;
                    acessToken = null;
                    stage = 0;
                } else {
                    lastHasErro = true;
                    addCleanErro("unexpected token", sToken);
                }
            } else {
                lastHasErro = true;
                addCleanErro("unexpected token", sToken);
            }
        }

        if (stage != 0) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", contentToken.byLastChar());
        } else if (contentTokenGet == null && contentTokenSet == null) {
            addCleanErro("get or set members expected", contentToken);
        }

        acessValueGet = Acess.fromToken(acessTokenGet);
        acessValueSet = Acess.fromToken(acessTokenSet);
        if (acessValueGet == Acess.DEFAULT) {
            acessValueGet = acessValue;
        } else if (acessValue.isMostPrivate(acessValueGet)) {
            addCleanErro("get acess should be more restrict than the property", acessTokenGet);
            acessValueGet = acessValue;
        }

        if (acessValueSet == Acess.DEFAULT) {
            acessValueSet = acessValue;
        } else if (acessValue.isMostPrivate(acessValueSet)) {
            addCleanErro("set acess should be more restrict than the property", acessTokenSet);
            acessValueSet = acessValue;
        }
    }

    public void setInitToken(Token initToken) {
        if (!hasSet() || isAbstract()) {
            addCleanErro("cannot set", initToken);
        } else {
            this.initToken = initToken;
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
        //finalValue - semantica diferente (nao sobrescrevivel)
        field = new Field(PROPERTY, nameToken, getContainer(), nameToken.toString(), typeValue,
                staticValue, false, isAbstract(),
                hasSet(), hasGet(), acessValueGet, acessValueSet);
    }

    @Override
    public void make() {
        if (isGetNonAbs()) {
            stackGet = new Stack(contentTokenGet.byNested(), this, null, null, typeValue, false);
            stackGet.load();
        }
        if (isSetNonAbs()) {
            stackSet = new Stack(contentTokenSet.byNested(), this, null, null, Pointer.voidPointer, false);
            stackSet.fields.add(new Field(LOCALVAR, keywordTokenSet, getContainer(), "value", typeValue,
                    false, false, false,
                    false, true, Acess.PUBLIC, Acess.PUBLIC));
            stackSet.load();
        }
        stack = new Stack(nameToken, this, null, null, Pointer.voidPointer, !isStatic());
        if (initToken != null) {
            initLine = new LineBlock(stack, initToken.subSequence(initToken.indexOf("=") + 1), true, true);
            initLine.load();
            initLine.requestGetAcess();
            initLine.setAutoCasting(typeValue, false);
        }
    }

    @Override
    public void build(CppBuilder cBuilder) {
        buildHeader(cBuilder);
        buildSource(cBuilder);
    }

    public void buildHeader(CppBuilder cBuilder) {
        //Header
        cBuilder.toHeader();
        //GET
        if (hasGet()) {
            cBuilder.add("\t");

            if (isStatic()) {
                cBuilder.add("static ");
            } else if (!isFinal() && !getContainer().isFinal()) {
                cBuilder.add("virtual ");
            }

            cBuilder.add(getType()).add(" ").namePropertyGet(getName()).add("()")
                    .add(isAbstract() ? " = 0;" : ";").ln();
        }
        //SET
        if (hasSet()) {
            cBuilder.add("\t");

            if (isStatic()) {
                cBuilder.add("static ");
            } else if (!isFinal() && !getContainer().isFinal()) {
                cBuilder.add("virtual ");
            }

            cBuilder.add("void ").namePropertySet(getName()).add("(").add(getType()).add(" ").nameField("value").add(")")
                    .add(isAbstract() ? " = 0;" : ";").ln();
        }
        //STATE
        cBuilder.add("\t");

        if (isStatic()) {
            cBuilder.add("static ");
        } else if (!isFinal() && !getContainer().isFinal()) {
            cBuilder.add("virtual ");
        }

        cBuilder.add("PropertyState<").add(getType()).add("> ").nameProperty(getName()).add("()")
                .add(isAbstract() ? " = 0;" : ";").ln();
    }

    public void buildSource(CppBuilder cBuilder) {
        Pointer pointer = getContainer().getPointer();

        //Source
        cBuilder.toSource();
        //GET
        if (isGetNonAbs()) {
            cBuilder.ln()
                    .add(getContainer().generics)
                    .add(getType()).add(" ").path(pointer).add("::").namePropertyGet(getName()).add("() ").begin(1);

            if (isStatic()) {
                cBuilder.add("\tinitClass();").ln();
            }

            stackGet.build(cBuilder, 1);

            cBuilder.end();
        }
        //SET
        if (isSetNonAbs()) {
            cBuilder.ln()
                    .add(getContainer().generics)
                    .add("void ").path(pointer).add("::").namePropertySet(getName())
                    .add("(").add(getType()).add(" ").nameField("value").add(") ").begin(1);

            if (isStatic()) {
                cBuilder.add("\tinitClass();").ln();
            }

            stackSet.build(cBuilder, 1);

            cBuilder.end();
        }
        //AUTO
        if (isAuto() && !isAbstract()) {
            //GET
            cBuilder.ln()
                    .add(getContainer().generics)
                    .add(getType()).add(" ").path(pointer).add("::").namePropertyGet(getName()).add("() ").begin(1);

            if (isStatic()) {
                cBuilder.add("\tinitClass();").ln();
            }

            cBuilder.add("\treturn ").nameField(getName()).add(";").ln()
                    .end();
            //SET
            cBuilder.ln()
                    .add(getContainer().generics)
                    .add("void ").path(pointer).add("::").namePropertySet(getName())
                    .add("(").add(getType()).add(" ").nameField("value").add(") ").begin(1);

            if (isStatic()) {
                cBuilder.add("\tinitClass();").ln();
            }

            cBuilder.nameField(getName()).add(" = ").nameField("value").add(";").ln()
                    .end();
        }
        //STATE
        if (!isAbstract()) {
            cBuilder.ln()
                    .add(getContainer().generics)
                    .add("PropertyState<").add(getType()).add("> ").path(pointer).add("::").nameProperty(getName())
                    .add("()").begin(1)
                    .add("\treturn PropertyState<").add(getType()).add(">(");
            if (hasGet()) {
                cBuilder.add("[=]() -> ").add(getType()).add(" { return ").namePropertyGet(getName()).add("(); }, ");
            } else {
                cBuilder.add("nullptr, ");
            }
            if (hasSet()) {
                cBuilder.add("[=](").add(getType()).add(" value) -> void { return ").namePropertySet(getName()).add("(value); }");
            } else {
                cBuilder.add("nullptr");
            }
            cBuilder.add(");").ln()
                    .end();
        }
    }

    public void buildInit(CppBuilder cBuilder) {
        //Source
        cBuilder.toSource();
        if (initLine != null) {
            cBuilder.add("\t").nameProperty(getName()).add("() = ").add(initLine).ln();
        }
    }

    public void autoBuild(CppBuilder cBuilder) {
        autoBuildHeader(cBuilder);
        autoBuildSource(cBuilder);
    }

    public void autoBuildHeader(CppBuilder cBuilder) {
        //Header
        cBuilder.toHeader();
        //AUTO var
        if (isAuto() && !isAbstract()) {
            cBuilder.add(isStatic() ? "\tstatic" : "\t")
                    .add("volatile ").add(getType()).add(" ").nameField(getName()).add(";").ln();

            cBuilder.directDependence(getType());
        }
    }

    public void autoBuildSource(CppBuilder cBuilder) {
        Pointer pointer = getContainer().getPointer();
        boolean hasGenerics = !getContainer().generics.isEmpty();

        //Source
        cBuilder.toSource();
        if (!isAbstract() && isAuto()) {
            if (isStatic()) {
                cBuilder.add(getType()).add(" ").specialPath(hasGenerics, pointer).add("::").nameField(getName()).add(";").ln();
            }
        }
    }

    public void autoBuildUsing(CppBuilder cBuilder) {
        //Header
        cBuilder.toHeader();
        if (!isAbstract() && isAuto()) {
            cBuilder.add("\tusing ").specialPath(getContainer().getPointer()).add("::").nameField(getName()).add(";").ln();
        }
    }

    public Pointer getType() {
        return typeValue;
    }

    public String getName() {
        return nameValue;
    }

    public Field getField() {
        return field;
    }

    public Acess getGetAcess() {
        return acessValueGet;
    }

    public Acess getSetAcess() {
        return acessValueSet;
    }

    public boolean canBeAbstract() {
        return (isGetAbstract() || !hasGet()) && (isSetAbstract() || !hasSet());
    }

    public boolean canBeNonAbstract() {
        return ((isGetNonAbs() || !hasGet()) && (isSetNonAbs() || !hasSet())) || isAuto();
    }

    public boolean hasGet() {
        return contentTokenGet != null;
    }

    public boolean hasSet() {
        return contentTokenSet != null;
    }

    public boolean isGetAbstract() {
        return (hasGet() && contentTokenGet.compare(";"));
    }

    public boolean isSetAbstract() {
        return (hasSet() && contentTokenSet.compare(";"));
    }

    public boolean isGetNonAbs() {
        return (hasGet() && contentTokenGet.isClosedBy("{}"));
    }

    public boolean isSetNonAbs() {
        return (hasSet() && contentTokenSet.isClosedBy("{}"));
    }

    public boolean isAuto() {
        return isGetAbstract() && isSetAbstract();
    }

    public Property byGenerics(Pointer[] replacement) {
        if (isStatic() || !typeValue.hasGenericIndex()) return this;

        Property property = new Property();

        property.originalProperty = originalProperty == null ? this : originalProperty;

        property.strFile = strFile;
        property.token = token;
        property.wrong = wrong;
        property.typedef = typedef;

        property.type = type;

        property.acessToken = acessToken;
        property.modifierToken = modifierToken;
        property.staticToken = staticToken;
        property.volatileToken = volatileToken;

        property.acessValue = acessValue;
        property.staticValue = staticValue;
        property.finalValue = finalValue;
        property.abstractValue = abstractValue;
        property.volatileValue = volatileValue;

        property.typeToken = typeToken;
        property.nameToken = nameToken;
        property.contentToken = contentToken;
        property.keywordTokenGet = keywordTokenGet;
        property.keywordTokenSet = keywordTokenSet;
        property.acessTokenGet = acessTokenGet;
        property.acessTokenSet = acessTokenSet;
        property.contentTokenGet = contentTokenGet;
        property.contentTokenSet = contentTokenSet;

        property.field = field.byGenerics(replacement);
        property.nameValue = nameValue;
        property.typeValue = typeValue.byGenerics(replacement);
        property.acessValueGet = acessValueGet;
        property.acessValueSet = acessValueSet;

        return property;
    }

    public boolean isSameSignature(Property other) {
        return nameValue.equals(other.nameValue);
    }

    public boolean isCompatible(Property other) {
        //Mesmo nome
        if (!nameValue.equals(other.nameValue)) return false;
        //Retornos genericos sobrecarregaveis
        if(other.typeValue.hasGenericIndex() || typeValue.hasGenericIndex()) {
            return typeValue.fullEquals(other.typeValue);
        } else {
            return other.typeValue.isInterface() || typeValue.isInterface() ||
                    typeValue.isInstanceOf(other.typeValue) || other.typeValue.isInstanceOf(typeValue);
        }
    }

    public boolean isOverridable(Property other) {
        //Mesmo nome
        if(!isSameSignature(other)) return false;
        //Possuindo get e set compativeis
        if (!(hasGet() || !other.hasGet()) || !(hasSet() || !other.hasSet())) return false;
        //Retornos genericos sobrecarregaveis
        if(other.typeValue.hasGenericIndex() || typeValue.hasGenericIndex()) {
            return typeValue.fullEquals(other.typeValue);
        } else {
            return typeValue.isInstanceOf(other.typeValue);
        }
    }

    public void setOverrider() {
        isOverrider = true;
        if (originalProperty != null)
            originalProperty.isOverrider = true;
    }

    public void setOverriden() {
        isOverriden = true;
        if (originalProperty != null)
            originalProperty.isOverriden = true;
    }

    public boolean isOverrider() {
        return isOverrider;
    }

    public boolean isOverriden() {
        return isOverriden;
    }

    @Override
    public void fixAcess(Acess acessValue) {
        super.fixAcess(acessValue);
        if (hasSet()) {
            field.setAcessSet(acessValue);
            acessValueSet = acessValue;
        }
        if (hasGet()) {
            field.setAcessGet(acessValue);
            acessValueGet = acessValue;
        }
    }

    @Override
    public void fixFinal(boolean finalValue) {
        super.fixFinal(finalValue);
        field.setFinal(finalValue);
    }

    @Override
    public void fixStatic(boolean staticValue) {
        super.fixStatic(staticValue);
        field.setStatic(staticValue);
    }

    @Override
    public String toString() {
        return nameValue + "{" + (hasGet() ? "get;" : "") + (hasSet() ? "set;" : "") + "} : " + typeValue;
    }
}
