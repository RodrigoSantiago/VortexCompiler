package com.vortex.compiler.logic.implementation.block;

import com.vortex.compiler.content.Parser;
import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.content.TokenSplitter;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.Acess;
import com.vortex.compiler.logic.Type;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.implementation.line.Line;
import com.vortex.compiler.logic.implementation.line.LineFake;
import com.vortex.compiler.logic.implementation.lineblock.LineBlock;
import com.vortex.compiler.logic.typedef.Pointer;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 15/10/2016
 */
public class BlockFor extends Block {
    public static final String keyword = "for";

    public Token keywordToken, treeToken, startToken, conditionToken, loopToken,
            fTypeToken, fModifierToken, fNameToken, fLineToken;
    public boolean foreach;
    public int utfType;

    //For
    public Line startLine;
    public LineBlock conditionLine, loopLine;

    //Foreach
    public Field foreachField;
    public Pointer foreachType;
    public LineBlock foreachLine;

    public BlockFor(Block container, Token token, Token[] tokens) {
        super(container, token);
        loop = true;

        Token sContent = null;
        boolean lastHasErro = false;
        int stage = 0;
        for (Token sToken : tokens) {
            lastHasErro = false;
            if (stage == 0 && SmartRegex.simpleName(sToken) && !sToken.compare(keyword)) {
                labelToken = sToken;
                stage = 1;
            } else if (stage == 1 && sToken.compare(":")) {
                stage = 2;
            } else if ((stage == 0 || stage == 2) && sToken.compare(keyword)) {
                keywordToken = sToken;
                stage = 3;
            } else if (stage == 3 && sToken.isClosedBy("()")) {
                treeToken = sToken;
                readTreeTokens(Parser.getBlocks(TokenSplitter.split(treeToken.byNested(), true)));
                stage = 4;
            } else if (stage == 4) {
                if (sToken.isClosedBy("{}")) {
                    contentToken = sToken;
                    stage = -1;
                } else {
                    sContent = sToken;
                    stage = 5;
                    break;
                }
            } else {
                lastHasErro = true;
                addCleanErro("unexpected token", sToken);
            }
        }
        if (stage == 5) {
            contentToken = sContent.byAdd(tokens[tokens.length - 1]);
        } else if (stage != -1) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
        }
    }

    private void readTreeTokens(ArrayList<Token> tokenLines) {
        if (tokenLines.size() == 1) {
            foreach = true;

            Token[] tokens = TokenSplitter.split(tokenLines.get(0), true);

            boolean lastHasErro = false;
            int stage = 0;
            for (Token sToken : tokens) {
                lastHasErro = false;
                if (stage == 0 && SmartRegex.isModifier(sToken)) {
                    if (sToken.compare("final")) {
                        if (fModifierToken == null) {
                            fModifierToken = sToken;
                        } else {
                            addCleanErro("repeated modifier", sToken);
                        }
                    } else {
                        addCleanErro("invalid modifier", sToken);
                    }
                } else if (stage == 0 && SmartRegex.pointer(sToken)) {
                    fTypeToken = sToken;
                    stage = 1;
                } else if (stage == 1 && SmartRegex.simpleName(sToken)) {
                    fNameToken = sToken;
                    if (SmartRegex.isKeyword(sToken)) {
                        addCleanErro("illegal name", sToken);
                    }
                    stage = 2;
                } else if (stage == 2 && sToken.compare(":")) {
                    stage = 3;
                } else if (stage == 3 || stage == 4) {
                    if (fLineToken == null) {
                        fLineToken = sToken;
                    }
                    fLineToken = fLineToken.byAdd(sToken);
                    stage = 4;
                } else {
                    lastHasErro = true;
                    addCleanErro("unexpected token", sToken);
                }
            }
            if (stage != 4) {
                if (!lastHasErro) addCleanErro("unexpected end of tokens", treeToken.byLastChar());
            }
        } else {
            if (tokenLines.size() >= 1) {
                startToken = tokenLines.get(0);
                startToken = startToken.endsWith(";") ? startToken.subSequence(0, startToken.length() - 1) : startToken;
            }
            if (tokenLines.size() >= 2) {
                conditionToken = tokenLines.get(1);
                conditionToken = conditionToken.endsWith(";") ? conditionToken.subSequence(0, conditionToken.length() - 1) : conditionToken;
            }
            if (tokenLines.size() >= 3) {
                loopToken = tokenLines.get(2);
                loopToken = loopToken.endsWith(";") ? loopToken.subSequence(0, loopToken.length() - 1) : loopToken;
            }

            if (startToken == null) {
                addCleanErro("start statement expected", treeToken.byLastChar());
            } else if (conditionToken == null) {
                addCleanErro("condition statement expected", treeToken.byLastChar());
            }

            for (int i = 3; i < tokenLines.size(); i++) {
                addCleanErro("unexpected line", tokenLines.get(i));
            }
        }
    }

    @Override
    public void load() {
        if (!getStack().addLabel(labelToken)) {
            addCleanErro("repeated label", labelToken.byLastChar());
        }

        if (treeToken != null) {
            if (foreach) {
                //FOREACH

                if (fTypeToken != null && fNameToken != null) {
                    Pointer pointer = getStack().findPointer(fTypeToken);
                    if (pointer != null) {
                        foreachField = new Field(Type.LOCALVAR, fNameToken, getContainer(), fNameToken.toString(), pointer,
                                false, false, false,
                                fModifierToken == null, true, Acess.PUBLIC, Acess.PUBLIC);
                        if (!addField(foreachField)) {
                            addCleanErro("repeated variable name", fNameToken);
                        }
                    } else {
                        addErro("unknown typedef", fTypeToken);
                    }
                }
                if (fLineToken != null) {
                    foreachLine = new LineBlock(this, fLineToken, true, false);
                    foreachLine.load();
                    foreachLine.requestGetAcess();

                    Pointer fType = foreachLine.getReturnType();
                    if (fType.fullEquals(DataBase.defStringPointer)) {
                        if (foreachField != null) {
                            if (foreachField.getType().fullEquals(DataBase.defBytePointer)) {
                                foreachType = DataBase.defBytePointer;
                                utfType = 8;
                            } else if (foreachField.getType().fullEquals(DataBase.defCharPointer)) {
                                foreachType = DataBase.defCharPointer;
                                utfType = 16;
                            } else if (foreachField.getType().fullEquals(DataBase.defIntPointer)) {
                                foreachType = DataBase.defIntPointer;
                                utfType = 32;
                            } else {
                                addErro("incompatible type", fTypeToken);
                            }
                        }
                    } else if (fType.isDefault() || !fType.typedef.isInstanceOf(DataBase.defIterable)) {
                        addErro("foreach line should be a 'default::Iterable<?>'", foreachLine.getToken());
                    } else {
                        foreachType = fType.findMethod("createIterator")[0].getType().generics[0];
                        if (foreachField != null && foreachType.verifyAutoCasting(foreachField.getType()) != 0) {
                            addErro("incompatible type", fTypeToken);
                        }
                    }
                }
            } else {
                //NORMAL FOR

                if (startToken != null && !startToken.isEmpty()) {
                    Parser.parseCommands(this, startToken, true);
                    if (lines.size() > 0) {
                        startLine = lines.get(0);
                        lines.clear();
                    }
                }
                if (conditionToken != null && !conditionToken.isEmpty()) {
                    conditionLine = new LineBlock(this, conditionToken, true, false);
                    conditionLine.load();
                    conditionLine.requestGetAcess();
                    conditionLine.setAutoCasting(DataBase.defBoolPointer, false);
                }
                if (loopToken != null && !loopToken.isEmpty()) {
                    loopLine = new LineBlock(this, loopToken, true, false);
                    loopLine.load();
                    loopLine.requestGetAcess();
                }
            }
        }

        if (contentToken != null) {
            Parser.parseCommands(this, contentToken.isClosedBy("{}") ? contentToken.byNested() : contentToken, false);
        }
    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {
        LineFake lineFake = new LineFake(this, treeToken, "it" + indent + "->m_get()");
        lineFake.returnType = foreachType;
        if (utfType != 0) {
            cBuilder.idt(indent).add("auto it" + indent + " = ").add(foreachLine).add(".")
                    .nameMethod("createUTF").add(utfType).add("Iterator").add("();").ln()
                    .idt(indent).add("for ( ;it" + indent + "->").nameMethod("next").add("(); )").begin(indent + 1);
            if (labelToken != null) cBuilder.idt(indent + 1).nameContinue(labelToken).add(" : ;").ln();

            cBuilder.idt(indent + 1).add("auto ")
                    .nameField(foreachField.getName()).add(" = ").add(lineFake).add(";").ln();
        } else if (foreach) {
            //auto i = foreachLine;
            //for(; i->m_next();) {
            //    auto foreachField = i->m_get();
            //}
            //_delete(i);
            cBuilder.idt(indent).add("auto it" + indent + " = ").add(foreachLine).add("->").nameMethod("createIterator").add("();").ln()
                    .idt(indent).add("for ( ;it" + indent + "->").nameMethod("next").add("(); )").begin(indent + 1);
            if (labelToken != null) cBuilder.idt(indent + 1).nameContinue(labelToken).add(" : ;").ln();

            cBuilder.idt(indent + 1).add("auto ")
                    .nameField(foreachField.getName()).add(" = ");
            if (foreachField.getType().fullEquals(foreachType)) {
                cBuilder.add(lineFake).add(";").ln();
            } else {
                cBuilder.cast(foreachField.getType(), lineFake).add(";").ln();
            }
        } else {
            //for(startLine; conditionline; loopLine) {
            //
            //}
            cBuilder.idt(indent).add("for (");
            if (startLine != null) {
                cBuilder.add(startLine);
            }
            cBuilder.add(";");
            if (conditionLine != null) {
                cBuilder.add(" ").add(conditionLine);
            }
            cBuilder.add(";");
            if (loopLine != null) {
                cBuilder.add(" ").add(loopLine);
            }
            cBuilder.add(") ").begin(indent + 1);

            if (labelToken != null) cBuilder.idt(indent + 1).nameContinue(labelToken).add(" : ;").ln();
        }

        for (Line line : lines) {
            line.build(cBuilder, indent + 1);
        }

        cBuilder.idt(indent).end();
        if (labelToken != null) {
            cBuilder.idt(indent).nameBreak(labelToken).add(" : ;").ln();
        }

        if (foreach) {
            cBuilder.idt(indent).add("_delete(it" + indent + ");").ln();
        }
    }
}
