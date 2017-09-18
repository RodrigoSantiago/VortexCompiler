package com.vortex.compiler.logic.implementation.lineblock.data;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.content.TokenSplitter;
import com.vortex.compiler.logic.header.Constructor;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.implementation.lineblock.LineBlock;
import com.vortex.compiler.logic.implementation.lineblock.LineCall;
import com.vortex.compiler.logic.typedef.Pointer;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 24/11/2016
 */
public class DataConstructor extends Data {
    //[new][stack][name<>?][(params)|[params]][{startblock}]

    public Token keywordToken, keyword2Token, typedefToken, initToken;
    public ArrayList<Token> initTokens = new ArrayList<>();
    public ArrayList<Token> fieldTokens = new ArrayList<>();

    public Pointer pointer;
    public Constructor constructorCall;
    public boolean constructorStack;
    public ArrayList<LineBlock> args = new ArrayList<>();
    public ArrayList<LineBlock> initArgs = new ArrayList<>();
    public ArrayList<Field> initFields = new ArrayList<>();

    public DataConstructor(LineCall lineCall) {
        Token tokens[] = TokenSplitter.split(lineCall.getToken(), false);

        Token argsToken = null;
        boolean lastHasErro = false;
        int stage = 0;
        for (Token sToken : tokens) {
            lastHasErro = false;
            if (stage == 0 && sToken.compare("new")) {
                keywordToken = sToken;
                stage = 1;
            } else if (stage == 1 && sToken.compare("stack")) {
                keyword2Token = sToken;
                stage = 2;
            } else if ((stage == 1 || stage == 2) && SmartRegex.typedefStatic(sToken)) {
                typedefToken = sToken;
                stage = 3;
            } else if (stage == 3 && sToken.isClosedBy("<>")) {
                typedefToken = typedefToken.byAdd(sToken);
                stage = 4;
            } else if ((stage == 3 || stage == 4) && (sToken.isClosedBy("[]") || sToken.isClosedBy("()"))) {
                argsToken = sToken;
                stage = 5;
            }  else if (stage == 5 && sToken.isClosedBy("{}")) {
                initToken = sToken;
                stage = -1; //Finaliza
            } else {
                lastHasErro = true;
                lineCall.addCleanErro("unexpected token", sToken);
                if (stage == 3) stage = 4;
            }
        }
        if (stage != -1 && stage != 5) {
            if (!lastHasErro) lineCall.addCleanErro("unexpected end of tokens", lineCall.getToken().byLastChar());
            lineCall.setWrong();
        } else {
            constructorStack = (keyword2Token != null);

            //Ponteiro
            pointer = lineCall.getStack().findPointer(typedefToken);
            if (pointer == null) {
                lineCall.addErro("unknown typedef", typedefToken);
            } else {
                if (argsToken.isClosedBy("[]")) pointer = pointer.byArray(1);

                if (pointer.genIndex != -1) {
                    lineCall.addErro("generics should not be instantiated", typedefToken);
                }
                lineCall.returnType = pointer;

                ArrayList<Token> argsTokens = LineCall.splitParameters(argsToken);
                Pointer pointers[] = new Pointer[argsTokens.size()];
                for (int i = 0; i < argsTokens.size(); i++) {
                    LineBlock argLine = new LineBlock(lineCall.getCommandContainer(), argsTokens.get(i), lineCall.instance, false);
                    argLine.load();
                    argLine.requestGetAcess();
                    args.add(argLine);
                    pointers[i] = argLine.getReturnType();
                    if (argLine.isWrong()) lineCall.setWrong();
                }

                if (pointer.isInterface()) {
                    lineCall.addErro("interfaces shloud not be instantiated", typedefToken);
                } else if (pointer.isEnum()) {
                    lineCall.addErro("enuns shloud not be instantiated", typedefToken);
                } else if (pointer.isClass() && pointer.typedef.isAbstract()) {
                    lineCall.addErro("abstract classes shloud not be instantiated", typedefToken);
                } else if (pointer.isStruct() && keyword2Token != null) {
                    lineCall.addWarning("structs are always stack", keyword2Token);
                    constructorStack = false;
                }

                if (!lineCall.isWrong()) {
                    Constructor constructors[] = pointer.findConstructor(pointers);
                    if (constructors.length == 1) {
                        constructorCall = constructors[0];
                        LineBlock.requestPerfectParams(args, constructorCall.params);
                    } else if (constructors.length > 1) {
                        lineCall.addErro("ambiguous signature", typedefToken.byAdd(argsToken));
                    } else {
                        lineCall.addErro("unknown constructor signature", typedefToken.byAdd(argsToken));
                    }
                }

                if (initToken != null) {
                    initTokens = LineCall.splitParameters(initToken);
                    if (argsToken.isClosedBy("[]")) {
                        //init array
                        for (Token token : initTokens) {
                            LineBlock argLine = new LineBlock(lineCall.getCommandContainer(), token, lineCall.instance, false);
                            argLine.load();
                            argLine.requestGetAcess();
                            argLine.setAutoCasting(pointer.generics[0], true);
                            initArgs.add(argLine);
                        }
                    } else {
                        //init block
                        for (Token token : initTokens) {
                            Token[] subTokens = TokenSplitter.split(token, true);
                            Token fieldName = null;
                            Token value = null;
                            stage = 0;
                            for (Token subToken : subTokens) {
                                lastHasErro = false;
                                if (stage == 0 && SmartRegex.variableStatement(subToken)) {
                                    fieldName = subToken;
                                    stage = 1;
                                } else if (stage == 1 && SmartRegex.isOperator(subToken)) {
                                    if (!subToken.compare("=")) {
                                        lastHasErro = true;
                                        lineCall.addCleanErro("init blocks should use only the set operator", subToken);
                                    }
                                    stage = 2;
                                } else if (stage == 2) {
                                    value = subToken;
                                    stage = -1;
                                    break;
                                } else {
                                    lastHasErro = true;
                                    lineCall.addCleanErro("unexpected token", subToken);
                                }
                            }
                            if (stage != -1) {
                                if (!lastHasErro) lineCall.addCleanErro("unexpected end of tokens", token.byLastChar());
                            } else {
                                value = value.byAdd(token);
                                Field field = pointer.findField(fieldName);
                                if (field == null) {
                                    lineCall.addCleanErro("field not found", fieldName);
                                } else if (field.isStatic()) {
                                    lineCall.addCleanErro("static field should not be initialized here", fieldName);
                                } else {
                                    lineCall.getStack().hasSetAcess(lineCall, fieldName, field);

                                    LineBlock argLine = new LineBlock(lineCall.getCommandContainer(), value, lineCall.instance, false);
                                    argLine.load();
                                    argLine.requestGetAcess();
                                    argLine.setAutoCasting(field.getType(), false);

                                    fieldTokens.add(fieldName);
                                    initFields.add(field);
                                    initArgs.add(argLine);
                                }
                            }
                        }
                    }
                } else if (argsToken.compare("[]")){
                    lineCall.addCleanErro("array size expected", argsToken);
                }
            }
        }
    }
}
