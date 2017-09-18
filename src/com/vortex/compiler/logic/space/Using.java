package com.vortex.compiler.logic.space;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.LogicToken;
import com.vortex.compiler.logic.header.Method;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.typedef.Typedef;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 01/10/2016
 */
public class Using extends LogicToken {
    public static final String keyword = "using";

    public Token keywordToken, staticToken, typedefToken, nameSpaceToken, headerToken;

    private Typedef typedefValue;
    private NameSpace nameSpaceValue;
    public ArrayList<Method> staticMethods = new ArrayList<>();
    public ArrayList<Field> staticFields = new ArrayList<>();
    private boolean staticValue;

    public Using(Token token, Token[] tokens) {
        this.token = token;
        this.strFile = token.getStringFile();

        //[0-using][1-namespace::class][2-;]        Mode 1
        //[0-using][1-namespace::][3-*][4-;]        Mode 2
        //[0-using][1-static][5-namespace::class][6-.][7-staticHeader][8-;] Mode 3
        //[0-using][1-static][5-namespace::class][6-.][7-*][8-;]            Mode 4
        boolean lastHasErro = false;
        int stage = 0;
        for (Token sToken : tokens) {
            lastHasErro = false;
            if (stage == 0 && sToken.compare("using")) {
                keywordToken = sToken;
                stage = 1;
            } else if (stage == 1) {
                if (sToken.compare("static")) {
                    staticToken = sToken;
                    stage = 5;
                } else if (SmartRegex.typedefStatic(sToken)) {
                    typedefToken = sToken;
                    stage = 2;
                } else if (sToken.matches("(::)?(\\w+::)*")) {
                    nameSpaceToken = sToken.subSequence(0, sToken.length() - 2);
                    stage = 3;
                } else {
                    lastHasErro = true;
                    addCleanErro("unexpected token", sToken);
                }
            } else if (stage == 3 && sToken.compare("*")) {
                stage = 4;
            } else if (stage == 5 && SmartRegex.typedefStatic(sToken)) {
                typedefToken = sToken;
                stage = 6;
            } else if (stage == 6 && sToken.compare(".")) {
                stage = 7;
            } else if (stage == 7 && SmartRegex.simpleName(sToken) || sToken.compare("*")) {
                headerToken = sToken;
                stage = 8;
            } else if ((stage == 2 || stage == 4 || stage == 8) && sToken.compare(";")) {
                stage = -1; //Finalizado
            } else {
                lastHasErro = true;
                addCleanErro("unexpected token", sToken);
            }
        }
        if (stage != -1) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
            if (stage != 2 && stage != 4 && stage != 8) {
                setWrong();
            }
        }
    }

    //Carregamento
    public void load() {
        if (staticToken != null) {
            staticValue = true;
        }

        if (nameSpaceToken != null) {
            if (nameSpaceToken.startsWith("::")) {
                nameSpaceValue = DataBase.namespaceFind(getStringFile().library.name + nameSpaceToken.toString());
            } else {
                nameSpaceValue = DataBase.namespaceFind(nameSpaceToken.toString());
            }
            if (nameSpaceValue == null) {
                addErro("unknown namespace", nameSpaceToken);
            }
        }

        if (typedefToken != null) {
            if (typedefToken.startsWith("::")) {
                typedefValue = DataBase.typedefFind(getStringFile().library.name + typedefToken.toString());
            } else {
                typedefValue = DataBase.typedefFind(typedefToken.toString());
            }
            if (typedefValue == null) {
                addErro("unknown typedef", typedefToken);
            }
        }
    }

    public void crossLoad() {
        if (typedefValue != null && staticValue) {
            if (headerToken != null) {
                if (headerToken.compare("*")) {
                    for(Field field : typedefValue.fields.values()) {
                        if (field.isStatic()) {
                            staticFields.add(field);
                        }
                    }
                    for (Method method : typedefValue.methods ){
                        if(method.isStatic()) {
                            staticMethods.add(method);
                        }
                    }
                    if(staticFields.size() == 0 && staticMethods.size() == 0){
                        addWarning("no static statement found", headerToken);
                    }
                } else {
                    Field field = typedefValue.getPointer().findField(headerToken);
                    if (field != null && field.isStatic()) {
                        staticFields.add(field);
                    }
                    for (Method method : typedefValue.methods) {
                        if (method.isStatic() && method.getName().equals(headerToken.toString())) {
                            staticMethods.add(method);
                        }
                    }
                    if (staticFields.size() == 0 && staticMethods.size() == 0) {
                        addErro("unknown static statement", headerToken);
                    }
                }
            }
        }
    }

    //Propriedades
    public NameSpace getNameSpace() {
        return nameSpaceValue;
    }

    public Typedef getTypedef() {
        return typedefValue;
    }

    public boolean isStatic() {
        return staticValue;
    }

    @Override
    public String toString() {
        if (isStatic()) {
            return "using : [static] [typedef][" + getTypedef() + "] [header][" + headerToken + "]";
        } else {
            if (getTypedef() != null) {
                return "using : [typedef][" + getTypedef().fullName + "]";
            } else {
                return "using : [namespace][" + getNameSpace().fullName + "][*]";
            }
        }
    }
}
