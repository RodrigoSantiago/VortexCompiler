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

import java.util.ArrayList;

import static com.vortex.compiler.logic.Type.*;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 07/10/2016
 */
public class Variable extends Header {

    //Leitura
    public Token typeToken;
    public ArrayList<Token> nameTokens = new ArrayList<>();
    public ArrayList<Token> valueTokens = new ArrayList<>();

    //Conteudo Interno
    private Pointer typeValue;
    private ArrayList<Field> fields = new ArrayList<>();

    //Implementacao
    public Stack stack;
    public ArrayList<LineBlock> initLines = new ArrayList<>();

    public Variable(Typedef container, Token token, Token[] tokens) {
        super(container, token, tokens, VARIABLE, true, true, false, true, true);

        //[0-modificadores][1-tipo][2-varName][3-***][3,][2-varName][3-***][3;]
        Token secToken = null;

        Token nameToken = null;
        Token valueToken = null;
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
            } else if (stage == 2){
                secToken = sToken.byAdd(token);
                break;
            } else {
                lastHasErro = true;
                addCleanErro("unexpected token", sToken);
            }
        }

        if (secToken == null) {
            tokens = new Token[0];
        } else {
            tokens = TokenSplitter.split(secToken, true, TokenSplitter.ARGUMENTS); // Secondary Split
        }

        for (Token sToken : tokens) {
            lastHasErro = false;

            if (stage == 2 && SmartRegex.variableStatement(sToken)) {
                nameToken = sToken;
                valueToken = null;
                stage = 3;
            } else if (stage == 3) {
                if (sToken.compare(",")) {
                    addVariable(nameToken, null);
                    stage = 2;
                } else if (sToken.compare(";")) {
                    addVariable(nameToken, null);
                    stage = -1;
                } else if (sToken.compare("=")) {
                    stage = 4;
                } else {
                    lastHasErro = true;
                    addCleanErro("unexpected token", sToken);
                }
            } else if (stage == 4) {
                if (valueToken == null) valueToken = sToken;
                if (sToken.compare(",")) {
                    addVariable(nameToken, new Token(strFile, valueToken.getStartPos(), sToken.getEndPos() - 1));
                    stage = 2;
                } else if (sToken.compare(";")) {
                    addVariable(nameToken, new Token(strFile, valueToken.getStartPos(), sToken.getEndPos() - 1));
                    stage = -1;
                }
            } else {
                lastHasErro = true;
                addCleanErro("unexpected token", sToken);
            }
        }
        if (stage != -1) {
            if (stage == 3 || stage == 4) {
                addVariable(nameToken, valueToken == null ? null : valueToken.byAdd(tokens[tokens.length - 1]));
            }
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
            if (typeToken == null || nameTokens.isEmpty()) {
                setWrong();
            }
        }
    }

    private void addVariable(Token nameToken, Token valueToken) {
        if (SmartRegex.isKeyword(nameToken)) {
            addCleanErro("illegal name", nameToken);
        }
        nameTokens.add(nameToken);
        valueTokens.add(valueToken);
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

        if (typeValue != null) {
            for (Token nameToken : nameTokens) {
                fields.add(new Field(VARIABLE, nameToken, getContainer(), nameToken.toString(), typeValue,
                        isStatic(), isFinal(), false,
                        true, true, getAcess(), getAcess()));
            }
        }
    }

    @Override
    public void make() {
        stack = new Stack(token, this, null, null, Pointer.voidPointer, !isStatic());
        for (int i = 0; i < valueTokens.size(); i++) {
            Token valueToken = valueTokens.get(i);
            Field field = fields.get(i);
            LineBlock lineBlock = null;
            if (valueToken != null) {
                lineBlock = new LineBlock(stack, valueToken, true, false);
                lineBlock.load();
                lineBlock.requestGetAcess();
                lineBlock.setAutoCasting(field.getType(), false);
                if (lineBlock.isStackConstructor()) {
                    addWarning("security breach, do not use stack constructors in instance variables", lineBlock.getToken());
                }
            }
            initLines.add(lineBlock);
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
        for (Token name : nameTokens) {
            if (isStatic()) {
                //Variavel estatica
                cBuilder.add("\tstatic ").add(isVolatile() ? "volatile " : "").add(typeValue).add(" ").nameField(name).add(";").ln();

                //Propriedade estatica (get/set)
                cBuilder.add("\tstatic ").add(typeValue).add(" ").namePropertyGet(name).add("() { initCheck(); return ").nameField(name).add("; }").ln();
                cBuilder.add("\tstatic void ").namePropertySet(name).add("(").add(typeValue).add(" value) { initCheck(); ").nameField(name).add(" = value; }").ln();

                cBuilder.add("\tstatic PropertyState<").add(typeValue).add("> ").nameProperty(name).add("();").ln();
            } else {
                cBuilder.add(isVolatile() ? "\tvolatile " : "\t").add(typeValue).add(" ").nameField(name).add(";").ln();
            }
        }

        cBuilder.directDependence(getInitType());
    }

    public void buildSource(CppBuilder cBuilder) {
        Pointer pointer = getContainer().getPointer();
        boolean hasGenerics = !getContainer().generics.isEmpty();

        //Source
        cBuilder.toSource();
        if (isStatic()) {
            for (Token name : nameTokens) {
                //Variavel estatica
                cBuilder.add(typeValue).add(" ").specialPath(hasGenerics, pointer).add("::").nameField(name).add(";").ln();

                //Propriedade estatica
                cBuilder.ln()
                        .add(getContainer().generics)
                        .add("PropertyState<").add(typeValue).add("> ").specialPath(hasGenerics, pointer).add("::").nameProperty(name)
                        .add("() ").begin(1)
                        .add("\treturn ").add("PropertyState<").add(typeValue).add(">(")
                        .add("[=]() -> ").add(typeValue).add("{ return ").namePropertyGet(name).add("(); }, ")
                        .add("[=](").add(typeValue).add(" value) -> void { ").namePropertySet(name).add("(value); });").ln()
                        .end();
            }
        }
    }

    public void buildInit(CppBuilder cBuilder) {
        //Source
        cBuilder.toSource();
        for (int i = 0; i < nameTokens.size(); i++) {
            LineBlock initLine = initLines.get(i);

            cBuilder.add("\t").nameField(nameTokens.get(i)).add(" = ");
            if (initLine != null) {
                cBuilder.add(initLine);
            } else {
                cBuilder.value(typeValue);
            }
            cBuilder.add(";").ln();
        }
    }

    public void buildUsing(CppBuilder cBuilder) {
        Pointer pointer = getContainer().getPointer();

        //Header
        cBuilder.toHeader();
        for (Token nameToken : nameTokens) {
            cBuilder.add("\tusing ").specialPath(pointer).add("::").nameField(nameToken).add(";").ln();
        }
    }

    public ArrayList<Field> getFields() {
        return fields;
    }

    public Pointer getInitType(){
        return typeValue;
    }

    @Override
    public void fixAcess(Acess acessValue) {
        super.fixAcess(acessValue);
        for (Field field : fields) {
            field.setAcessSet(acessValue);
            field.setAcessGet(acessValue);
        }
    }

    @Override
    public void fixFinal(boolean finalValue) {
        super.fixFinal(finalValue);
        for (Field field : fields) {
            field.setFinal(finalValue);
        }
    }

    @Override
    public void fixStatic(boolean staticValue) {
        super.fixStatic(staticValue);
        for (Field field : fields) {
            field.setFinal(staticValue);
        }
    }
}
