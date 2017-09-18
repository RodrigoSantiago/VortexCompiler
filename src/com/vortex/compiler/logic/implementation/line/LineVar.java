package com.vortex.compiler.logic.implementation.line;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.content.TokenSplitter;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.Acess;
import com.vortex.compiler.logic.Type;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.implementation.block.Block;
import com.vortex.compiler.logic.implementation.lineblock.LineBlock;
import com.vortex.compiler.logic.typedef.Pointer;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 15/10/2016
 */
public class LineVar extends Line {
    public static String keyword = "auto";

    //Escrita
    public Token modifierToken, typeToken;
    public ArrayList<Token> nameTokens = new ArrayList<>();
    public ArrayList<Token> valueTokens = new ArrayList<>();

    //Lógica
    public Pointer typeValue;
    public boolean finalValue;
    //Implementacao
    public ArrayList<LineBlock> initValues = new ArrayList<>();

    private boolean needColon;

    public LineVar(Block container, Token token, Token[] tokens, boolean needColon) {
        super(container, token);
        this.needColon = needColon;

        Token secToken = null;

        boolean lastHasErro = false;
        Token nameToken = null;
        Token valueToken = null;
        int stage = 0;
        for (Token sToken : tokens) {
            lastHasErro = false;

            if (stage == 0) {
                if (SmartRegex.isModifier(sToken)) {
                    if (sToken.compare("final")) {
                        if (modifierToken == null) {
                            modifierToken = sToken;
                        } else {
                            addCleanErro("repeated modifier", sToken);
                        }
                    } else {
                        addCleanErro("invalid modifier", sToken);
                    }
                } else if (SmartRegex.pointer(sToken)) {
                    typeToken = sToken;
                    stage = 1;
                } else {
                    lastHasErro = true;
                    addCleanErro("unexpected token", sToken);
                }
            } else if (stage == 1) {
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
            if (stage == 1 && SmartRegex.variableStatement(sToken)) {
                nameToken = sToken;
                valueToken = null;
                stage = 2;
            } else if (stage == 2) {
                if (sToken.compare(",")) {
                    addVar(nameToken, null);
                    stage = 1;
                } else if (sToken.compare(";")) {
                    addVar(nameToken, null);
                    stage = -1;
                } else if (sToken.compare("=")) {
                    stage = 3;
                } else {
                    lastHasErro = true;
                    addCleanErro("unexpected token", sToken);
                }
            } else if (stage == 3) {
                if (valueToken == null) valueToken = sToken;
                if (sToken.compare(",")) {
                    addVar(nameToken, new Token(strFile, valueToken.getStartPos(), sToken.getEndPos() - 1));
                    stage = 1;
                } else if (sToken.compare(";")) {
                    addVar(nameToken, new Token(strFile, valueToken.getStartPos(), sToken.getEndPos() - 1));
                    stage = -1;
                }
            } else {
                lastHasErro = true;
                addCleanErro("unexpected token", sToken);
            }
        }
        if (stage != -1) {
            if (stage == 3) {
                addVar(nameToken, valueToken == null ? null : valueToken.byAdd(tokens[tokens.length - 1]));
            }
            if (!lastHasErro && needColon) addCleanErro("unexpected end of tokens", token.byLastChar());
        }
        finalValue = SmartRegex.compare(modifierToken, "final");
    }

    private void addVar(Token nameToken, Token valueToken) {
        if (SmartRegex.isKeyword(nameToken)) {
            addCleanErro("illegal name", nameToken);
        }
        nameTokens.add(nameToken);
        valueTokens.add(valueToken);
    }

    @Override
    public void load() {
        if (typeToken == null) return;

        if (getCommandContainer().isCaseContainer()) {
            addErro("variables statement should not be in a switch", typeToken);
        }

        if (typeToken.compare("void")) {
            addErro("void is not allowed here", typeToken);
        } else {
            LineBlock initValue = null;
            if (typeToken.compare("auto")) {
                Token valueToken = valueTokens.get(0);
                if (valueToken != null) {
                    initValue = new LineBlock(getCommandContainer(), valueToken, true, false);
                    initValue.load();
                    initValue.requestGetAcess();
                    typeValue = initValue.getReturnType();
                    if (typeValue != null) {
                        if (initValue.getReturnType().isDefault()) {
                            addCleanErro("invalid initialization", valueToken);
                            typeValue = null;
                        } else if (!Acess.TesteAcess(this, typeValue.typedef)) {
                            addCleanErro("invalid initialization type, cannot acess", initValue);
                        }
                    }
                } else {
                    addErro("auto variables should be initialized in the first statement", nameTokens.get(0));
                }
            } else {
                typeValue = getStack().findPointer(typeToken);
                if (typeValue == null) {
                    addErro("unknown typedef", typeToken);
                }
            }

            if (typeValue != null) {
                for (int i = 0; i < nameTokens.size(); i++) {
                    Token nameToken = nameTokens.get(i);
                    Token valueToken = valueTokens.get(i);
                    if (valueToken != null) {
                        if (initValue == null) {
                            initValue = new LineBlock(getCommandContainer(), valueToken, true, false);
                            initValue.load();
                            initValue.requestGetAcess();
                        }
                    } else if (finalValue) {
                        addCleanErro("final variables should be initialized", nameToken);
                    }
                    Field field = new Field(Type.LOCALVAR, nameToken, getContainer(), nameToken.toString(),
                            typeValue,
                            false, false, false,
                            !finalValue, true, Acess.PUBLIC, Acess.PUBLIC);
                    field.localOrigem = getCommandContainer();
                    if (!getCommandContainer().addField(field)) {
                        addCleanErro("repeated variable name", nameToken);
                    }
                    initValues.add(initValue);
                    initValue = null;
                }
            }
        }
    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {
        cBuilder.idt(indent).add(typeValue);
        for (int i = 0; i < nameTokens.size(); i++) {
            cBuilder.add(i == 0 ? " " : ", ").nameField(nameTokens.get(i)).add(" = ");
            LineBlock initValue = initValues.get(i);
            if (initValue != null) {
                cBuilder.add(initValue);
            } else {
                cBuilder.value(typeValue);
            }
        }
        if (needColon) cBuilder.add(";").ln();
    }
}