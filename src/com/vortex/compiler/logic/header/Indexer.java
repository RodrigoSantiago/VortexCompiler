package com.vortex.compiler.logic.header;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.content.TokenSplitter;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.Acess;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.header.variable.Params;
import com.vortex.compiler.logic.implementation.Stack;
import com.vortex.compiler.logic.typedef.Typedef;
import com.vortex.compiler.logic.typedef.Pointer;

import static com.vortex.compiler.logic.Type.*;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 20/10/2016
 */
public class Indexer extends Header {

    //Leitura
    public Token typeToken, parametersToken, contentToken, keywordToken, keywordTokenGet, keywordTokenSet,
            acessTokenGet, acessTokenSet, contentTokenGet, contentTokenSet;

    //Conteudo Interno
    public Params params;

    private Pointer typeValue;
    private Acess acessValueGet, acessValueSet;

    //Implementacao
    public Stack stackGet, stackSet;

    //Escrita
    private Indexer originalIndexer;
    private boolean isOverriden, isOverrider;

    private Indexer() {
    }

    public Indexer(Typedef container, Token token, Token[] tokens) {
        super(container, token, tokens, INDEXER, true, false, true, true, false);
        originalIndexer = this;

        //[0-modifiers][1-type][2-[params]][3-{[modifier][get|set][;|{}][modifier][get*|set*][;|{}]]
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
            } else if (stage == 2 && sToken.compare("this")) {
                keywordToken = sToken;
                stage = 3;
            } else if (stage == 2 && sToken.compare("this[]")) {
                keywordToken = sToken.subSequence(0, 4);
                parametersToken = sToken.subSequence(4);
                stage = 4;
            } else if (stage == 3 && sToken.isClosedBy("[]")) {
                parametersToken = sToken;
                stage = 4;
            } else if (stage == 4 && sToken.isClosedBy("{}")) {
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
            params = new Params(parametersToken, this, true);
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
            addCleanErro("get acess should be more restrict than the indexer", acessTokenGet);
            acessValueGet = acessValue;
        }
        if (acessValueSet == Acess.DEFAULT) {
            acessValueSet = acessValue;
        } else if (acessValue.isMostPrivate(acessValueSet)) {
            addCleanErro("set acess should be more restrict than the indexer", acessTokenSet);
            acessValueSet = acessValue;
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
    }

    @Override
    public void make() {
        if (isGetNonAbs()) {
            stackGet = new Stack(contentTokenGet.byNested(), this, null, params, typeValue, false);
            stackGet.load();
        }
        if (isSetNonAbs()) {
            stackSet = new Stack(contentTokenSet.byNested(), this, null, params, Pointer.voidPointer, false);
            stackSet.fields.add(new Field(LOCALVAR, keywordTokenSet, getContainer(), "value", typeValue,
                    false, false, false,
                    false, true, Acess.PUBLIC, Acess.PUBLIC));
            stackSet.load();
        }
    }

    @Override
    public void build(CppBuilder cBuilder) {
        Pointer pointer = getContainer().getPointer();

        //Header
        cBuilder.toHeader();
        //GET
        if (hasGet()) {
            cBuilder.add(isFinal() || getContainer().isFinal() ? "\t" : "\tvirtual ").add(getType())
                    .add(" indexG(").add(params).add(")").add(isAbstract() ? " = 0;" : ";").ln();
        }
        //SET
        if (hasSet()) {
            cBuilder.add(isFinal() || getContainer().isFinal() ? "\t" : "\tvirtual ").add("void")
                    .add(" indexS(").add(params, getType()).add(")").add(isAbstract() ? " = 0;" : ";").ln();
        }
        //STATE
        cBuilder.add(isFinal() || getContainer().isFinal() ? "\t" : "\tvirtual ").add("PropertyState<").add(getType()).add(">")
                .add(" indexGet(").add(params).add(")").add(isAbstract() ? " = 0;" : ";").ln();

        //Source
        cBuilder.toSource();
        //GET
        if (isGetNonAbs()) {
            cBuilder.ln()
                    .add(getContainer().generics)
                    .add(getType()).add(" ").path(pointer).add("::indexG(").add(params).add(") ").begin(1);

            stackGet.build(cBuilder, 1);

            cBuilder.end();
        }
        //SET
        if (isSetNonAbs()) {
            cBuilder.ln()
                    .add(getContainer().generics)
                    .add("void ").path(pointer).add("::indexS(").add(params, getType()).add(") ").begin(1);

            stackSet.build(cBuilder, 1);

            cBuilder.end();
        }
        //STATE
        if (!isAbstract()) {
            cBuilder.ln()
                    .add(getContainer().generics)
                    .add("PropertyState<").add(getType()).add("> ").path(pointer).add("::indexGet")
                    .add("(").add(params).add(")").begin(1)
                    .add("\treturn PropertyState<").add(getType()).add(">(");
            if (hasGet()) {
                cBuilder.add("[=]() -> ").add(getType()).add(" { return this->indexG(").args(params).add("); }, ");
            } else {
                cBuilder.add("nullptr, ");
            }
            if (hasSet()) {
                cBuilder.add("[=](").add(getType()).add(" value) -> void { return this->indexS(").args(params).add(params.isEmpty() ? "value" : ", value").add("); } ");
            } else {
                cBuilder.add("nullptr");
            }
            cBuilder.add(");").ln()
                    .end();
        }
    }

    public Pointer getType() {
        return typeValue;
    }

    public boolean canBeAbstract() {
        return (isGetAbstract() || !hasGet()) && (isSetAbstract() || !hasSet());
    }

    public boolean canBeNonAbstract() {
        return (isGetNonAbs() || !hasGet()) && (isSetNonAbs() || !hasSet());
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
        return (hasGet() && !contentTokenGet.compare(";"));
    }

    public boolean isSetNonAbs() {
        return (hasSet() && !contentTokenSet.compare(";"));
    }

    public Acess getGetAcess() {
        return acessValueGet;
    }

    public Acess getSetAcess() {
        return acessValueSet;
    }
    public boolean hasVarArgs() {
        return params.hasVarArgs();
    }

    public Indexer byGenerics(Pointer[] replacement) {
        if (isStatic() || !typeValue.hasGenericIndex()) return this;

        Indexer indexer = new Indexer();

        indexer.originalIndexer = originalIndexer;

        indexer.strFile = strFile;
        indexer.token = token;
        indexer.wrong = wrong;
        indexer.typedef = typedef;

        indexer.type = type;

        indexer.acessToken = acessToken;
        indexer.modifierToken = modifierToken;
        indexer.staticToken = staticToken;
        indexer.volatileToken = volatileToken;

        indexer.acessValue = acessValue;
        indexer.staticValue = staticValue;
        indexer.finalValue = finalValue;
        indexer.abstractValue = abstractValue;
        indexer.volatileValue = volatileValue;

        indexer.typeToken = typeToken;
        indexer.parametersToken = parametersToken;
        indexer.contentToken = contentToken;
        indexer.keywordToken = keywordToken;
        indexer.keywordTokenGet = keywordTokenGet;
        indexer.keywordTokenSet = keywordTokenSet;
        indexer.acessTokenGet = acessTokenGet;
        indexer.acessTokenSet = acessTokenSet;
        indexer.contentTokenGet = contentTokenGet;
        indexer.contentTokenSet = contentTokenSet;

        indexer.params = params.byGenerics(replacement);
        indexer.typeValue = typeValue.byGenerics(replacement);
        indexer.acessValueGet = acessValueGet;
        indexer.acessValueSet = acessValueSet;

        return indexer;
    }

    public boolean isSameSignature(Indexer other) {
        return params.isSameSignature(other.params);
    }

    public boolean isCompatible(Indexer other) {
        //Mesmo valores genericos nos parametros
        if (!other.params.fullEquals(params)) return false;
        //Retornos genericos sobrecarregaveis
        if (other.typeValue.hasGenericIndex() || typeValue.hasGenericIndex()) {
            return typeValue.fullEquals(other.typeValue);
        } else {
            return typeValue.isInterface() || other.typeValue.isInterface() ||
                    typeValue.isInstanceOf(other.typeValue) || other.typeValue.isInstanceOf(typeValue);
        }
    }

    public boolean isOverridable(Indexer other) {
        //Possuindo get e set compativeis
        if (!(hasGet() || !other.hasGet()) || !(hasSet() || !other.hasSet())) return false;
        //Mesmo valores genericos nos parametros
        if (!other.params.fullEquals(params)) return false;
        //Retornos genericos sobrecarregaveis
        if (other.typeValue.hasGenericIndex() || typeValue.hasGenericIndex()) {
            return typeValue.fullEquals(other.typeValue);
        } else {
            return typeValue.isInstanceOf(other.typeValue);
        }
    }

    public void setOverrider() {
        isOverrider = true;
        originalIndexer.isOverrider = true;
    }

    public void setOverriden() {
        isOverriden = true;
        originalIndexer.isOverriden = true;
    }

    public boolean isOverrider() {
        return isOverrider;
    }

    public boolean isOverriden() {
        return isOverriden;
    }

    @Override
    public void fixAcess(Acess acessValue) {
        this.acessValue = acessValue;
        if (hasSet()) acessValueSet = acessValue;
        if (hasGet()) acessValueGet = acessValue;
    }

    @Override
    public String toString() {
        return "this[" + params + "] : "+typeValue;
    }

}
