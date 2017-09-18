package com.vortex.compiler.logic.header.variable;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.content.TokenSplitter;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.LogicToken;
import com.vortex.compiler.logic.typedef.Pointer;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 09/10/2016
 */
public class GenericStatement {
    public ArrayList<Token> nameTokens = new ArrayList<>();
    public ArrayList<Token> typeTokens = new ArrayList<>();
    public ArrayList<Pointer> pointers = new ArrayList<>();
    public Pointer[] defReplacement;

    private boolean[] useds;
    private LogicToken container;

    public void read(LogicToken logicToken, Token genericToken) {
        this.container = logicToken;
        Token[] tokens = TokenSplitter.split(genericToken.byNested(), true, TokenSplitter.STATEMENT);

        //[0-nome][1-,]
        //[0-nome][1-:][2-typedef][3,]
        Token nameToken = null;
        Token typeToken = null;
        boolean lastHasErro = false;
        int stage = 0;
        for (Token sToken : tokens) {
            lastHasErro = false;
            if (stage == 0 && SmartRegex.simpleName(sToken)) {
                nameToken = sToken;
                typeToken = null;
                stage = 1;
            } else if (stage == 1 && sToken.compare(":")) {
                stage = 2;
            } else if (stage == 2 && SmartRegex.typedefParent(sToken)) {
                typeToken = sToken;
                stage = 3;
            } else if ((stage == 1 || stage == 3) && sToken.compare(",")) {
                addGeneric(nameToken, typeToken);
                stage = 0;
            } else {
                lastHasErro = true;
                container.addCleanErro("unexpected token", sToken);
            }
        }
        if (stage == 1 || stage == 3) {
            addGeneric(nameToken, typeToken);
        } else {
            if (!lastHasErro) container.addCleanErro("unexpected end of tokens", genericToken.byLastChar());
        }
    }

    private void addGeneric(Token nameToken, Token typeToken) {
        if (!nameTokens.contains(nameToken)) {
            nameTokens.add(nameToken);
            typeTokens.add(typeToken);
            if (SmartRegex.isKeyword(nameToken)) {
                container.addCleanErro("illegal name", nameToken);
            }
        } else {
            container.addCleanErro("repeated generic name", nameToken);
        }
    }

    public void load() {
        defReplacement = new Pointer[nameTokens.size()];
        useds = new boolean[nameTokens.size()];

        for (int i = 0; i < nameTokens.size(); i++) {
            Token genericName = nameTokens.get(i);
            Token genericType = typeTokens.get(i);
            Pointer pointer;
            if (genericType == null) {
                pointer = DataBase.defObjectPointer;
            } else {
                pointer = container.getContainer().workspace.getPointer(container, genericType, true);
                if (pointer == null) {
                    container.addCleanErro("unknown typedef", genericType);
                    pointer = DataBase.defObjectPointer;
                } else if (pointer.isStruct()) {
                    container.addCleanErro("generics should not be derived from structures", genericType);
                    pointer = DataBase.defObjectPointer;
                } else if (pointer.isEnum()) {
                    container.addCleanErro("generics should not be derived from enums", genericType);
                    pointer = DataBase.defObjectPointer;
                } else if (pointer.isClass() && pointer.typedef.isFinal()) {
                    container.addCleanErro("generics should not be derived from final classes", genericType);
                    pointer = DataBase.defObjectPointer;
                }
            }
            pointers.add(pointer);
            defReplacement[i] = new Pointer(i, genericName, pointer.typedef, pointer.generics);
        }
    }

    public Pointer findPointer(Token nameToken) {
        int pos = nameTokens.indexOf(nameToken);
        Pointer pointer = (pos == -1) ? null : pointers.get(pos);
        if (pointer != null) {
            useds[pos] = true;
            return new Pointer(pos, nameTokens.get(pos), pointer.typedef, pointer.generics);
        } else {
            return null;
        }
    }

    public int size() {
        return nameTokens.size();
    }

    public boolean isEmpty() {
        return nameTokens.size() == 0;
    }

    public boolean wasUsed(int pos) {
        return useds[pos];
    }

}
