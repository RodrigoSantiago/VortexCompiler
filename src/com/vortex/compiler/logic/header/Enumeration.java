package com.vortex.compiler.logic.header;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.Acess;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.implementation.Stack;
import com.vortex.compiler.logic.implementation.lineblock.LineBlock;
import com.vortex.compiler.logic.implementation.lineblock.LineCall;
import com.vortex.compiler.logic.typedef.Pointer;
import com.vortex.compiler.logic.typedef.Typedef;

import java.util.ArrayList;

import static com.vortex.compiler.logic.Type.*;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 07/10/2016
 */
public class Enumeration extends Header {

    //Leitura
    public ArrayList<Token> nameTokens = new ArrayList<>();
    public ArrayList<Token> constructorsTokens = new ArrayList<>();

    //Conteudo interno
    private ArrayList<Field> fields = new ArrayList<>();

    //Implementacao
    public Stack stack;
    public ArrayList<ArrayList<LineBlock>> constructorsArgs = new ArrayList<>();
    public ArrayList<Constructor> constructorsCalls = new ArrayList<>();

    public Enumeration(Typedef container, Token token, Token[] tokens) {
        super(container, token, tokens, NUM, false, false, false, false, false);

        //[0-modificadores*][1-nome][2,][1-nome][2-;]
        //[0-modificadores*][1-nome][2-()][3,][1-nome][2-()][3;]
        Token nameToken = null;
        Token constructorToken = null;
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
                constructorToken = null;
                stage = 2;
            } else if (stage == 2 && sToken.isClosedBy("()")) {
                constructorToken = sToken;
                stage = 3;
            } else if (stage == 2 || stage == 3) {
                if (sToken.compare(",")) {
                    addEnumeration(nameToken, constructorToken);
                    stage = 1;
                } else if (sToken.compare(";")) {
                    addEnumeration(nameToken, constructorToken);
                    stage = -1;
                } else {
                    lastHasErro = true;
                    addCleanErro("unexpected token", sToken);
                }
            } else {
                lastHasErro = true;
                addCleanErro("unexpected token", sToken);
            }
        }
        if (stage != -1) {
            if (stage == 2 || stage == 3) {
                addEnumeration(nameToken, constructorToken);
            }
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
        }
    }

    private void addEnumeration(Token nameToken, Token constructorToken) {
        nameTokens.add(nameToken);
        constructorsTokens.add(constructorToken);
        if (SmartRegex.isKeyword(nameToken)) {
            addCleanErro("illegal name", nameToken);
        }
    }

    @Override
    public void load() {
        for (Token tokenName : nameTokens) {
            fields.add(new Field(NUM, tokenName, getContainer(), tokenName.toString(), getContainer().getPointer(),
                    true, true, false,
                    false, true, Acess.PUBLIC, Acess.PUBLIC));
        }
    }

    @Override
    public void make() {
        if (fields.size() > 0) {
            stack = new Stack(token, this, null, null, Pointer.voidPointer, false);

            for (int i = 0; i < nameTokens.size(); i++) {
                Token nameToken = nameTokens.get(i);
                Token constructorToken = constructorsTokens.get(i);
                Field field = fields.get(i);

                Constructor constructor = null;
                ArrayList<LineBlock> args = new ArrayList<>();
                if (constructorToken != null) {
                    ArrayList<Token> argTokens = LineCall.splitParameters(constructorToken);
                    Pointer pointers[] = new Pointer[argTokens.size()];
                    for (int j = 0; j < argTokens.size(); j++) {
                        LineBlock argLine = new LineBlock(stack, argTokens.get(j), true, false);
                        argLine.load();
                        argLine.requestGetAcess();
                        args.add(argLine);
                        pointers[j] = argLine.getReturnType();
                    }

                    Constructor constructors[] = field.getType().findConstructor(pointers);
                    if (constructors.length == 1) {
                        constructor = constructors[0];
                        LineBlock.requestPerfectParams(args, constructor.params);
                    } else if (constructors.length > 1) {
                        addErro("ambiguous signature", constructorToken);
                    } else {
                        addErro("unknown constructor signature", constructorToken);
                    }
                } else {
                    Constructor constructors[] = field.getType().findConstructor();
                    if (constructors.length == 1) {
                        constructor = constructors[0];
                        LineBlock.requestPerfectParams(args, constructor.params);
                    } else if (constructors.length > 1) {
                        addErro("ambiguous signature", nameToken);
                    } else {
                        addErro("unknown constructor signature", nameToken);
                    }
                }
                constructorsArgs.add(args);
                constructorsCalls.add(constructor);
            }
        }
    }

    @Override
    public void build(CppBuilder cBuilder) {
        Pointer pointer = getContainer().getPointer();

        //Header
        cBuilder.toHeader();
        for (Token name : nameTokens) {
            //Variavel estatica
            cBuilder.add("\tstatic ").add(pointer).add(" ").nameField(name).add(";").ln();

            //Propriedade estatica (get)
            cBuilder.add("\tstatic ").add(pointer).add(" ").namePropertyGet(name).add("() { initCheck(); return ").nameField(name).add("; }").ln();
        }

        //Source
        cBuilder.toSource();
        for (Token name : nameTokens) {
            //Variavel estatica
            cBuilder.add(pointer).add(" ").path(pointer).add("::").nameField(name).add(";").ln();
        }
    }

    public int buildInit(CppBuilder cBuilder, int pos) {
        Pointer pointer = getContainer().getPointer();

        //Source
        cBuilder.toSource();
        for (int i = 0; i < nameTokens.size(); i++) {
            Token nameToken = nameTokens.get(i);
            ArrayList<LineBlock> args = constructorsArgs.get(i);
            cBuilder.add("\t").nameField(nameToken).add(" = ").constructor(pointer, false).add("(").add(constructorsCalls.get(i).params, args).add(");").ln();
            cBuilder.add("\t").nameField(nameToken).add("->").nameField("name").add(" = ").path(DataBase.defStringPointer).add("(u\"").add(nameToken.toString()).add("\");").ln();
            cBuilder.add("\t").nameField(nameToken).add("->").nameField("id").add(" = ").add(pos++).add(";").ln();
        }
        return pos;
    }

    public ArrayList<Field> getFields() {
        return fields;
    }

}
