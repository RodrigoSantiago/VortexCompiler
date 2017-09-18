package com.vortex.compiler.logic.space;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.content.TokenSplitter;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.Acess;
import com.vortex.compiler.logic.LogicToken;
import com.vortex.compiler.logic.header.Method;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.header.variable.GenericStatement;
import com.vortex.compiler.logic.typedef.Typedef;
import com.vortex.compiler.logic.typedef.Pointer;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 01/10/2016
 */
public class Workspace {

    public NameSpace nameSpace;
    public ArrayList<Using> usings;
    public GenericStatement generics;

    public Workspace(NameSpace nameSpace, ArrayList<Using> usings) {
        this.nameSpace = nameSpace;
        this.usings = usings;
    }

    public void load() {
        for (Using using : usings) {
            using.load();
        }
    }

    public void crossLoad() {
        for (Using using : usings) {
            if (!using.isWrong())
                using.crossLoad();
        }
    }

    public NameSpace getNameSpace() {
        return this.nameSpace;
    }

    public ArrayList<Using> getUsings() {
        return usings;
    }

    public void setGenerics(GenericStatement generics) {
        this.generics = generics;
    }

    public Typedef getTypedef(Token token) {
        return findTypedef(token.toString());
    }

    public Typedef getTypedef(String typedefName) {
        return findTypedef(typedefName);
    }

    public boolean verifyName(Typedef typedef) {
        for (Typedef type : DataBase.typedefs.values()) {
            if (typedef != type && (typedef.fullName.equalsIgnoreCase(type.fullName) && type.type == typedef.type)) {
                return false;
            }
        }
        return true;
    }

    public Typedef findTypedef(String typedefName) {
        if (typedefName == null || typedefName.isEmpty()) return null;

        Typedef typedef = DataBase.typedefFind((typedefName.startsWith("::") ? nameSpace.library.name : "") + typedefName);
        if (typedef != null) {
            return typedef;
        } else if (typedefName.contains("::")) {
            return null;
        }

        for (Using using : usings) {
            if (using.isWrong() || using.isStatic() || using.getTypedef() == null) continue;
            if (using.getTypedef().getName().equals(typedefName)) {
                typedef = using.getTypedef();
                return typedef;
            }
        }

        typedef = DataBase.typedefFind(nameSpace.fullName + "::" + typedefName);
        if (typedef != null) {
            return typedef;
        }

        for (Using using : usings) {
            if (using.isWrong() || using.isStatic() || using.getNameSpace() == null) continue;
            typedef = DataBase.typedefFind(using.getNameSpace().fullName + "::" + typedefName);
            if (typedef != null) {
                return typedef;
            }
        }
        return DataBase.typedefFind(DataBase.defaultLibrary.name + "::" + typedefName);
    }

    public Pointer getPointer(LogicToken logicToken, Token pointerToken, boolean staticFind) {
        return getPointer(logicToken, pointerToken, null, staticFind, false, true);
    }

    public Pointer getPointer(LogicToken logicToken, Token pointerToken, GenericStatement genericStatement, boolean staticFind) {
        return getPointer(logicToken, pointerToken, genericStatement, staticFind, false, true);
    }

    /**
     * Cria um Pointer atrasves de um token
     *
     * @param logicToken   Gerenciador de erros logicos
     * @param pointerToken token  completo
     * @param innerGeneric genericos extras
     * @param staticFind   se nao deve procurar nos genericos
     * @param parentFind   se nao deve procurar nos genericos( nao transmissivel para internos )
     * @return Pointer
     */
    public Pointer getPointer(LogicToken logicToken, Token pointerToken, GenericStatement innerGeneric,
                              boolean staticFind, boolean parentFind, boolean genericVerify) {
        TokenSplitter splitter = new TokenSplitter(pointerToken);
        Token nameToken = splitter.getNext();
        Token genericToken = null;

        Pointer basePointer = null;
        ArrayList<Pointer> generics = new ArrayList<>();
        int array = 0;

        //Procurar nome nos genericos
        if (nameToken.indexOf("::") == -1 && !parentFind) {
            Pointer pointer = null;
            if (innerGeneric != null) {
                pointer = innerGeneric.findPointer(nameToken);
                if (pointer != null) {
                    basePointer = new Pointer(pointer.genIndex, pointer.genName, true, pointer.typedef, pointer.generics);
                }
            }
            if (pointer == null && this.generics != null && !staticFind) {
                pointer = this.generics.findPointer(nameToken);
                if (pointer != null) {
                    basePointer = pointer;
                }
            }
        }

        //Procurar nome nos typedefs importados
        if (basePointer == null) {
            Typedef typedef = getTypedef(nameToken);
            if (typedef == null) {
                return null;
            } else {
                if (typedef == DataBase.defFunction) {
                    basePointer = DataBase.defFunctionPointer;
                } else {
                    basePointer = typedef.getDefPointer();
                    if (typedef == DataBase.defArray || typedef == DataBase.defIterable || typedef == DataBase.defIterator || typedef == DataBase.defList) {
                        genericVerify = false;
                    }
                }
            }
        }

        if (!Acess.TesteAcess(logicToken, basePointer.typedef)) {
            logicToken.addCleanErro("cannot acess", nameToken);
        }

        //Preparar genericos internos e array
        Token sToken = splitter.getNext();
        int stage = 0;
        while (sToken != null) {
            if (stage == 0 && sToken.startsWith("<") && sToken.endsWith(">")) {
                if (basePointer.genIndex != -1) {
                    logicToken.addCleanErro("generics types should not have generics arguments", sToken);
                    stage = 1;
                    sToken = splitter.getNext();
                    continue;
                }
                genericToken = sToken;
                Token tokens[] = TokenSplitter.split(sToken.subSequence(1, sToken.length() - 1), true);
                boolean nLastHasErro = false;
                int nStage = 0;
                for (Token nToken : tokens) {
                    nLastHasErro = false;
                    if (nStage == 0 && SmartRegex.pointer(nToken)) {
                        Pointer pointer = getPointer(logicToken, nToken, innerGeneric, staticFind, false, genericVerify);
                        if (basePointer.typedef == DataBase.defFunction) {
                            if (pointer == null) {
                                if (!nToken.compare("void")) {
                                    logicToken.addCleanErro("unknown typedef", nToken);
                                } else if (generics.size() > 0) {   //< ..., void , ... >
                                    logicToken.addCleanErro("unexpected void keyword", nToken);
                                } else {                            //<void, ... >
                                    generics.add(basePointer.generics[0]);
                                }
                            } else {
                                generics.add(pointer);
                            }
                        } else if (generics.size() == basePointer.generics.length) {
                            logicToken.addCleanErro("unexpected generic", nToken);
                        } else if (pointer == null) {
                            logicToken.addCleanErro("unknown typedef", nToken);
                            generics.add(basePointer.generics[generics.size()]);
                        } else if (genericVerify && !pointer.isInstanceOf(basePointer.generics[generics.size()])) {
                            logicToken.addCleanErro("typedef does not matches the type of generic", nToken);
                            generics.add(basePointer.generics[generics.size()]);
                        } else {
                            generics.add(pointer);
                        }

                        nStage = 1;
                    } else if (nStage == 1 && nToken.compare(",")) {
                        nStage = 0;
                    } else {
                        nLastHasErro = true;
                        logicToken.addCleanErro("unexpected token", nToken);
                    }
                }
                if (nStage == 0) {
                    if (!nLastHasErro) logicToken.addCleanErro("unexpected end of tokens", sToken.byLastChar());
                }
                stage = 1;
            } else if (sToken.compare("[]")) {
                stage = 1;
                array ++;
            } else {
                logicToken.addCleanErro("unexpected token", sToken);      //Realmente Improvavel
            }
            sToken = splitter.getNext();
        }

        if (genericToken == null) {
            return basePointer.byArray(array);
        } else if (generics.size() < basePointer.generics.length) {
            logicToken.addCleanErro("generic expected", genericToken.byLastChar());
            while (generics.size() < basePointer.generics.length) {
                generics.add(basePointer.generics[generics.size()]);
            }
        }

        return new Pointer(basePointer.genIndex, basePointer.genName, basePointer.typedef, generics).byArray(array);
    }

    public Field findField(CharSequence name) {
        for (int i = usings.size() - 1; i >= 0; i--) {
            Using using = usings.get(i);
            if (using.isWrong()) continue;

            for (Field field : using.staticFields) {
                if (field.getName().equals(name)) return field;
            }

        }
        return null;
    }

    public Method[] findMethod(CharSequence name, Pointer[] arguments) {
        ArrayList<Method> methods = new ArrayList<>();
        int[] minParamDifference = new int[arguments.length];
        for (int i = 0; i < minParamDifference.length; i++) {
            minParamDifference[i] = 99999;
        }

        for (int i = usings.size() - 1; i >= 0; i--) {
            Using using = usings.get(i);
            if (using.isWrong()) continue;

            for (Method method : using.staticMethods) {
                if (method.getName().equals(name.toString()) && method.params.size() == arguments.length) {

                    method = method.byInnerGenerics(arguments);
                    int[] paramDifference = method.params.compare(arguments);
                    if (paramDifference != null) {
                        boolean hasOneMin = false, hasAllMin = true;
                        for (int j = 0; j < paramDifference.length; j++) {
                            if (paramDifference[j] <= minParamDifference[j]) {
                                if (paramDifference[j] < minParamDifference[j]) {
                                    hasOneMin = true;
                                }
                                minParamDifference[j] = paramDifference[j];
                            } else {
                                hasAllMin = false;
                            }
                        }

                        if (hasAllMin) {        //Corresponde perfeitamente
                            methods.clear();
                            methods.add(method);
                        } else if (hasOneMin) { //Possui um furo no metodo que melhor corresponde
                            if (methods.size() > 0 && methods.get(0) != null) {
                                methods.add(0, null);
                            }
                            methods.add(method);
                        }
                    }
                }
            }
        }
        return methods.toArray(new Method[methods.size()]);
    }

    @Override
    public String toString() {
        return "workspace : [namespace][" + getNameSpace() + "] [usings]" + getUsings();
    }

}
