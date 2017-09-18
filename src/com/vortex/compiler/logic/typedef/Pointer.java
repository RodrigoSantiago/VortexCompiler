package com.vortex.compiler.logic.typedef;

import com.vortex.compiler.content.Token;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.Type;
import com.vortex.compiler.logic.header.*;
import com.vortex.compiler.logic.header.variable.Field;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 02/10/2016
 */
public class Pointer {
    public static Pointer nullPointer = new Pointer(); // NULL - valido pra TUDO(exceto struct)
    public static Pointer voidPointer = new Pointer(); // VOID - valido pra NADA

    public final Typedef typedef;           //Typedef
    public final Pointer[] generics;        //Ponteiros para os genericos
    public final int genIndex;              //Index do generico para si mesmo( classes ou metodos )
    public final Token genName;             //Index do generico para si mesmo( classes ou metodos )
    public final boolean innerGeneric;      //Tipo de index de generico(classe ou metodo)
    public final boolean isStatic;          //Se referencia a uma classe diretamente(Class.membrosStaticos)

    private Pointer() {
        typedef = null;
        generics = new Pointer[0];
        genIndex = -1;
        genName = null;
        innerGeneric = false;
        isStatic = false;
    }

    private Pointer(boolean isStatic, int genIndex, Token genName, boolean innerGeneric, Typedef typedef, Pointer... generics) {
        this.typedef = typedef;
        this.generics = generics;
        this.genIndex = genIndex;
        this.genName = genName;
        this.innerGeneric = innerGeneric;
        this.isStatic = isStatic;
    }

    public Pointer(Typedef typedef, Pointer... generics) {
        this.typedef = typedef;
        this.generics = generics;
        genIndex = -1;
        genName = null;
        innerGeneric = false;
        isStatic = false;
    }

    public Pointer(Typedef typedef, ArrayList<Pointer> generics) {
        this.typedef = typedef;
        this.generics = generics.toArray(new Pointer[generics.size()]);
        genIndex = -1;
        genName = null;
        innerGeneric = false;
        isStatic = false;
    }

    public Pointer(int genIndex, Token genName, Typedef typedef, ArrayList<Pointer> generics) {
        this.genIndex = genIndex;
        this.genName = genName;
        this.typedef = typedef;
        this.generics = generics.toArray(new Pointer[generics.size()]);
        innerGeneric = false;
        isStatic = false;
    }

    public Pointer(int genIndex, Token genName, Typedef typedef, Pointer... generics) {
        this.genIndex = genIndex;
        this.genName = genName;
        this.typedef = typedef;
        this.generics = generics;
        innerGeneric = false;
        isStatic = false;
    }

    public Pointer(int genIndex, Token genName, boolean innerGeneric, Typedef typedef, Pointer... generics) {
        this.genIndex = genIndex;
        this.genName = genName;
        this.typedef = typedef;
        this.generics = generics;
        this.innerGeneric = innerGeneric;
        isStatic = false;
    }

    /**
     * Cria um array para o ponteiro atual
     *
     * @param plus Numero de subniveis
     * @return Pointer
     */
    public Pointer byArray(int plus) {
        if (plus == 0) return this;

        Pointer typePointer = this;
        for (int i = 0; i < plus; i++) {
            typePointer = new Pointer(DataBase.defArray, typePointer);
        }
        return typePointer;
    }

    /**
     * Substitui os genericos internos pelos tipos definidos(modo de classe)
     *
     * @param replacement Pointers para substituicao
     * @return Pointer
     */
    public Pointer byGenerics(Pointer[] replacement) {
        if (this.isDefault()) return this;

        if (!innerGeneric && genIndex > -1) {
            return replacement[genIndex];
        }

        Pointer[] generics = new Pointer[this.generics.length];
        for (int i = 0; i < this.generics.length; i++) {
            if (!this.generics[i].innerGeneric && this.generics[i].genIndex > -1) {
                generics[i] = replacement[this.generics[i].genIndex];
            } else {
                generics[i] = this.generics[i].byGenerics(replacement);
            }
        }
        return new Pointer(genIndex, genName, innerGeneric, typedef, generics);
    }

    /**
     * Substitui os genericos internos pelos tipos definidos(modo de metodo)
     *
     * @param replacement Pointers para substituicao
     * @return Pointer
     */
    public Pointer byInnerGenerics(Pointer[] replacement) {
        if (this.isDefault()) return this;

        if (innerGeneric && genIndex > -1) {
            return replacement[genIndex];
        }

        Pointer[] generics = new Pointer[this.generics.length];
        for (int i = 0; i < this.generics.length; i++) {
            if (this.generics[i].innerGeneric && this.generics[i].genIndex > -1) {
                generics[i] = replacement[this.generics[i].genIndex];
            } else {
                generics[i] = this.generics[i].byInnerGenerics(replacement);
            }
        }
        return new Pointer(genIndex, genName, innerGeneric, typedef, generics);
    }

    /**
     * Cria um ponteiro estatico
     *
     * @return Pointer(Static)
     */
    public Pointer byStatic() {
        return new Pointer(true, genIndex, genName, innerGeneric, typedef);
    }

    /**
     * Devolve o tipo principal em uma cadeia de array
     *
     * @return Pointer
     */
    public Pointer getArrayTrueType() {
        if (typedef == DataBase.defArray && generics.length > 0) {
            return generics[0].getArrayTrueType();
        } else {
            return this;
        }
    }

    /**
     * Verifica se este ponteiro e genericos sao correspondentes aos tipos de declaracao do mesmo
     *
     * @return treu-false
     */
    public boolean verifyGenerics() {
        if (typedef == DataBase.defFunction) {
            return generics.length > 0;
        } else if (typedef == DataBase.defArray || typedef == DataBase.defIterable || typedef == DataBase.defIterator || typedef == DataBase.defList){
            return generics.length == 1;
        }
        if (generics.length != typedef.generics.size()) {
            return false;
        }
        for (int i = 0; i < generics.length; i++) {
            if (!generics[i].isInstanceOf(typedef.generics.pointers.get(i)) || !generics[i].verifyGenerics()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Verifica se possui indices de genericos(recursivo)
     *
     * @return true-false
     */
    public boolean hasGenericIndex() {
        if (genIndex != -1) return true;
        for (Pointer generic : generics) {
            if (generic.hasGenericIndex()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifica se este ponteiro herda do outro, verifica todos os genericos
     *
     * @param other Outro ponteiro
     * @return true-false
     */
    public boolean isInstanceOf(Pointer other) {
        if (other.isDefault()) return false;
        if (this == nullPointer) return true;
        if (this == voidPointer) return false;
        if (other.hasGenericIndex()) return other.fullEquals(this);
        if (this.equals(other)) return true;

        if (typedef != null) {
            for (Pointer parent : typedef.parents) {
                if (parent.byGenerics(generics).isInstanceOf(other)) return true;
            }
        }
        return false;
    }

    /**
     * Se este ponteiro tem o outro ponteiro em seus parentes ou em eh ele mesmo.
     * Ignora diferencas genericas
     *
     * @param other Outro ponteiro
     * @return true-false
     */
    public boolean isValid(Pointer other) {
        if (other.isDefault()) return false;
        if (this == nullPointer) return true;
        if (this == voidPointer) return false;

        if (this.fullEquals(other)) return true;

        if (typedef != null) {
            for (Pointer parent : typedef.parents) {
                if (parent.byGenerics(generics).isValid(other)) return true;
            }
        }
        return false;
    }

    /**
     * Verifica o grau de parentesnco
     * (Other)this
     *
     * @param other Parametro
     * @return -1 para imcompativeis e inteiros para compativeis
     */
    public int getDifference(Pointer other) {
        return getDifference(other, true);
    }

    private int getDifference(Pointer other, boolean findOperators) {
        if (isLang() && other.isLang()) {
            findOperators = true;
        }
        if (other.isDefault()) return -1;

        if (this == nullPointer) return other.isStruct() ? -1 : 0;
        if (this == voidPointer) return -1;
        if (other.hasGenericIndex()) {
            if (other.fullEquals(this)) return 0;
        } else if (this.equals(other)) {
            return 0;
        }

        int smaller = -1;
        for (Pointer parent : typedef.parents) {
            int dif = parent.getDifference(other) + 1;
            if ((dif < smaller || smaller == -1) && dif > 0) smaller = dif;
        }
        if (this.isStruct() && !other.hasGenericIndex() && findOperators) {
            for (OpOverload operator : typedef.operators) {
                if (operator.getOperator().equals("autocast")) {
                    int dif = operator.getType().getDifference(other, false) + 1;
                    if ((dif < smaller || smaller == -1) && dif > 0) smaller = dif;
                }
            }
            int dif = getWrapper().getDifference(other) + 1;
            if ((dif < smaller || smaller == -1) && dif > 0) smaller = dif;
        } else if (other.isStruct() && !this.hasGenericIndex()) {
            int dif = this.getDifference(other.getWrapper()) + 1;
            if ((dif < smaller || smaller == -1) && dif > 0) smaller = dif;
        }
        return smaller;
    }

    /**
     * Verifica se o ponteiro atual tem casting com o outro ponteiro
     * (Other)this
     * A : B
     * A -> B = 0
     * B -> A = 1
     * A -> C = -1
     *
     * @param other Pointer
     * @return -1 - Impossivel
     * 0 - Automatico
     * 1 - Nessesario casting explicito
     */
    public int verifyAutoCasting(Pointer other) {
        if (other == nullPointer || other == voidPointer) return -1;

        if (this.getDifference(other) >= 0) {
            return 0;
        } else if (isStruct()) {
            if (other.hasGenericIndex()) return -1;
            for (OpOverload operator : typedef.operators) {
                if (operator.getOperator().equals("cast")) {
                    int dif = operator.getType().getDifference(other);
                    if (dif >= 0) return 1;
                }
            }
            return -1;
        } else if (other.isStruct()) {
            if (hasGenericIndex()) return -1;
            //se eu sou Seu wrapper ou Object
            return (this.equals(DataBase.defObjectPointer) || other.getWrapper().equals(this)) ? 1 : -1;
        } else {
            return 1;
        }
    }

    /**
     * Verifica se este ponteiro pode ser igual ao outro
     *
     * @param other Pointer
     * @return true-false
     */
    public boolean canBeEqual(Pointer other) {
        if (this.equals(other)) return true;

        if (this.genIndex != -1 && (other.isValid(this) || (!other.isStruct() && this.isInterface()))) {
            return true;
        } else if (other.genIndex != -1 && (this.isValid(other) || (!this.isStruct() && other.isInterface()))) {
            return true;
        } else {
            if (this.typedef == other.typedef) {
                for (int i = 0; i < generics.length; i++) {
                    if (!this.generics[i].canBeEqual(other.generics[i])) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    public boolean isStruct() {
        return typedef != null && typedef.type == Type.STRUCT;
    }

    public boolean isObject() {
        return typedef != null && typedef.type != Type.STRUCT;
    }

    public boolean isClass() {
        return typedef != null && typedef.type == Type.CLASS;
    }

    public boolean isEnum() {
        return typedef != null && typedef.type == Type.ENUM;
    }

    public boolean isInterface() {
        return typedef != null && typedef.type == Type.INTERFACE;
    }

    //############################
    //#         Methods          #
    //############################

    /**
     * Devolve, se houver, algum metodo compativel e implementado
     *
     * @param method Metodo
     * @return Metodo  deste ponteiro ou Null
     */
    public Method getImplMethod(Method method) {
        for (Method aMethod : typedef.methods) {
            if (aMethod.isAbstract() || aMethod.isStatic()) continue;
            aMethod = aMethod.byGenerics(generics);
            if (aMethod.isOverridable(method)) return aMethod;
        }
        //Verifica se um dos parentes possui o metodo ( Note que o maximo eh uma classe como parent )
        if (typedef.parents.size() > 0) {
            Method aMethod = typedef.parents.get(0).getImplMethod(method);
            if (aMethod != null) return aMethod.byGenerics(generics);
        }
        return null;
    }

    public Method getIdenMethod(Method method) {
        for (Method aMethod : typedef.methods) {
            aMethod = aMethod.byGenerics(generics);
            if (!aMethod.isStatic() && aMethod.isSameSignature(method)) return aMethod;
        }
        for (Pointer parent : typedef.parents) {
            Method aMethod = parent.getIdenMethod(method);
            if (aMethod != null) return aMethod.byGenerics(generics);
        }
        return null;
    }

    //############################
    //#         Indexers         #
    //############################

    /**
     * Devolve, se houver, algum indexador compativel e implementado
     *
     * @param indexer Indexador
     * @return Indexador deste ponteiro ou Null
     */
    public Indexer getImplIndexer(Indexer indexer) {
        for (Indexer aIndexer : typedef.indexers) {
            if (aIndexer.isAbstract() || aIndexer.isStatic()) continue;
            aIndexer = aIndexer.byGenerics(generics);
            if (aIndexer.isOverridable(indexer)) return aIndexer;
        }
        //Verifica se um dos parentes possui o metodo ( Note que o maximo eh uma classe como parent )
        if (typedef.parents.size() > 0) {
            Indexer aIndexer = typedef.parents.get(0).getImplIndexer(indexer);
            if (aIndexer != null) return aIndexer.byGenerics(generics);
        }
        return null;
    }

    public Indexer getIdenIndexer(Indexer method) {
        for (Indexer aIndexer : typedef.indexers) {
            aIndexer = aIndexer.byGenerics(generics);
            if (!aIndexer.isStatic() && aIndexer.isSameSignature(method)) return aIndexer;
        }
        for (Pointer parent : typedef.parents) {
            Indexer aIndexer = parent.getIdenIndexer(method);
            if (aIndexer != null) return aIndexer.byGenerics(generics);
        }
        return null;
    }

    //############################
    //#        Properties        #
    //############################

    /**
     * Devolve, se houver, alguma propriedade compativel e implementada
     *
     * @param property Property
     * @return Property deste ponteiro ou Null
     */
    public Property getImplProperty(Property property) {
        for (Property aProperty : typedef.properties) {
            if (aProperty.isAbstract() || aProperty.isStatic()) continue;
            aProperty = aProperty.byGenerics(generics);
            if (aProperty.isOverridable(property)) return aProperty;
        }
        //Verifica se um dos parentes possui a propriedade ( Note que o maximo eh uma classe como parent )
        if (typedef.parents.size() > 0) {
            Property aProperty = typedef.parents.get(0).getImplProperty(property);
            if (aProperty != null) return aProperty.byGenerics(generics);
        }
        return null;
    }

    public Property getIdenProperty(Property property) {
        for (Property aProperty : typedef.properties) {
            if (!aProperty.isStatic() && aProperty.isSameSignature(property)) {
                return aProperty.byGenerics(generics);
            }
        }
        for (Pointer parent : typedef.parents) {
            Property aProperty = parent.getIdenProperty(property);
            if (aProperty != null) return aProperty.byGenerics(generics);
        }
        return null;
    }

    //############################
    //#         Finders          #
    //############################

    public Field findField(CharSequence name) {
        if (this.isDefault()) return null;

        Field field = typedef.getAllFields().get(name.toString());
        if (field != null && (field.isStatic() || !isStatic)) {
            return field.byGenerics(generics);
        }
        return null;
    }

    public Constructor[] findConstructor(Pointer... arguments) {
        if (this.isDefault()) return new Constructor[0];

        ArrayList<Constructor> constructors = new ArrayList<>();
        int[] minParamDifference = new int[arguments.length];
        for (int i = 0; i < minParamDifference.length; i++) {
            minParamDifference[i] = Integer.MAX_VALUE;
        }

        for (Constructor constructor : typedef.getAllConstructors()) {
            if (constructor.params.size() == arguments.length) {
                constructor = constructor.byGenerics(generics);

                int[] paramDifference = constructor.params.compare(arguments);
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
                        constructors.clear();
                        constructors.add(constructor);
                    } else if (hasOneMin) { //Possui um furo no construtor que melhor corresponde
                        if (constructors.size() > 0 && constructors.get(0) != null) {
                            constructors.add(0, null);
                        }
                        constructors.add(constructor);
                    }
                }
            }
        }

        if (constructors.size() == 0) {
            for (Constructor constructor : typedef.getAllConstructors()) {
                if (constructor.hasVarArgs() && constructor.params.size() - 1 <= arguments.length) {
                    constructor = constructor.byGenerics(generics);

                    int[] paramDifference = constructor.params.compareVarArgs(arguments);
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
                            constructors.clear();
                            constructors.add(constructor);
                        } else if (hasOneMin) { //Possui um furo no construtor que melhor corresponde
                            if (constructors.size() > 0 && constructors.get(0) != null) {
                                constructors.add(0, null);
                            }
                            constructors.add(constructor);
                        }
                    }
                }
            }
        }
        return constructors.toArray(new Constructor[constructors.size()]);
    }

    public Method[] findMethod(CharSequence name, Pointer... arguments) {
        if (this.isDefault()) return new Method[0];

        ArrayList<Method> methods = new ArrayList<>();
        int[] minParamDifference = new int[arguments.length];
        for (int i = 0; i < minParamDifference.length; i++) {
            minParamDifference[i] = Integer.MAX_VALUE;
        }

        for (Method method : typedef.getAllMethods()) {
            if ((method.isStatic() || !isStatic) && method.getName().equals(name.toString()) &&
                    (method.params.size() == arguments.length || method.getContainer() == DataBase.defFunction)) {
                method = method.byGenerics(generics).byInnerGenerics(arguments);
                int[] paramDifference = method.params.compare(arguments);
                if (paramDifference != null) {
                    boolean hasOneMin = false, hasAllMin = true, hasAllEquals = true;
                    for (int j = 0; j < paramDifference.length; j++) {
                        if (paramDifference[j] < minParamDifference[j]) {
                            hasOneMin = true;
                            hasAllEquals = false;
                            minParamDifference[j] = paramDifference[j];
                        } else if (paramDifference[j] > minParamDifference[j]) {
                            hasAllMin = false;
                            hasAllEquals = false;
                        }
                    }

                    if ((hasAllMin && hasOneMin) ||
                            method.params.size() == 0) {      //Corresponde perfeitamente
                        methods.clear();
                        methods.add(method);
                    } else if (hasOneMin || hasAllEquals) {//Possui um furo no metodo que melhor corresponde
                        if (methods.size() > 0 && methods.get(0) != null) {
                            methods.add(0, null);
                        }
                        methods.add(method);
                    }
                }
            }
        }

        if (methods.size() == 0) {
            for (Method method : typedef.getAllMethods()) {
                if (method.hasVarArgs() && (method.isStatic() || !isStatic) && method.getName().equals(name.toString())
                        && method.params.size() - 1 <= arguments.length) {
                    method = method.byGenerics(generics).byInnerGenerics(arguments);
                    int[] paramDifference = method.params.compareVarArgs(arguments);
                    if (paramDifference != null) {
                        boolean hasOneMin = false, hasAllMin = true, hasAllEquals = true;
                        for (int j = 0; j < paramDifference.length; j++) {
                            if (paramDifference[j] < minParamDifference[j]) {
                                hasOneMin = true;
                                hasAllEquals = false;
                                minParamDifference[j] = paramDifference[j];
                            } else if (paramDifference[j] > minParamDifference[j]) {
                                hasAllMin = false;
                                hasAllEquals = false;
                            }
                        }

                        if ((hasAllMin && hasOneMin) ||
                                method.params.size() == 0) {      //Corresponde perfeitamente
                            methods.clear();
                            methods.add(method);
                        } else if (hasOneMin || hasAllEquals) {//Possui um furo no metodo que melhor corresponde
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

    public Indexer[] findIndexer(Pointer... arguments) {
        if (this.isDefault() || isStatic) return new Indexer[0];

        ArrayList<Indexer> indexers = new ArrayList<>();
        int[] minParamDifference = new int[arguments.length];
        for (int i = 0; i < minParamDifference.length; i++) {
            minParamDifference[i] = Integer.MAX_VALUE;
        }

        for (Indexer indexer : typedef.getAllIndexers()) {
            if (indexer.params.size() == arguments.length) {
                indexer = indexer.byGenerics(generics);

                int[] paramDifference = indexer.params.compare(arguments);
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
                        indexers.clear();
                        indexers.add(indexer);
                    } else if (hasOneMin) { //Possui um furo no indexer que melhor corresponde
                        if (indexers.size() > 0 && indexers.get(0) != null) {
                            indexers.add(0, null);
                        }
                        indexers.add(indexer);
                    }
                }
            }
        }

        if (indexers.size() == 0) {
            for (Indexer indexer : typedef.getAllIndexers()) {
                if (indexer.hasVarArgs() && indexer.params.size() - 1 <= arguments.length) {
                    indexer = indexer.byGenerics(generics);

                    int[] paramDifference = indexer.params.compareVarArgs(arguments);
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
                            indexers.clear();
                            indexers.add(indexer);
                        } else if (hasOneMin) { //Possui um furo no indexer que melhor corresponde
                            if (indexers.size() > 0 && indexers.get(0) != null) {
                                indexers.add(0, null);
                            }
                            indexers.add(indexer);
                        }
                    }
                }
            }
        }
        return indexers.toArray(new Indexer[indexers.size()]);
    }

    public OpOverload[] findOperator(CharSequence name, Pointer... arguments) {
        if (this.isDefault() || isStatic) return new OpOverload[0];

        ArrayList<OpOverload> opOverloads = new ArrayList<>();
        int[] minParamDifference = new int[arguments.length];
        for (int i = 0; i < minParamDifference.length; i++) {
            minParamDifference[i] = Integer.MAX_VALUE;
        }

        for (OpOverload opOverload : typedef.operators) {
            if (opOverload.getOperator().equals(name.toString()) && opOverload.params.size() == arguments.length) {

                int[] paramDifference = opOverload.params.compare(arguments);
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
                        opOverloads.clear();
                        opOverloads.add(opOverload);
                    } else if (hasOneMin) { //Possui um furo no operador que melhor corresponde
                        if (opOverloads.size() > 0 && opOverloads.get(0) != null) {
                            opOverloads.add(0, null);
                        }
                        opOverloads.add(opOverload);
                    }
                }
            }
        }
        return opOverloads.toArray(new OpOverload[opOverloads.size()]);
    }

    //############################
    //#         Lang             #
    //############################

    public Pointer getWrapper() {
        if (isStruct()) {
            if (typedef == DataBase.defFunction) {
                return new Pointer(DataBase.defWrapper, this);
            } else {
                return ((Struct) typedef).wrapperParent;
            }
        }
        return null;
    }

    public boolean isDefault() {
        return this == nullPointer || this == voidPointer;
    }

    public boolean isLang() {
        return typedef == DataBase.defByte || typedef == DataBase.defChar ||
                typedef == DataBase.defShort || typedef == DataBase.defInt || typedef == DataBase.defLong ||
                typedef == DataBase.defFloat || typedef == DataBase.defDouble || typedef == DataBase.defBool;
    }

    public boolean isInteger() {
        return typedef == DataBase.defByte || typedef == DataBase.defChar ||
                typedef == DataBase.defShort || typedef == DataBase.defInt || typedef == DataBase.defLong;
    }

    public boolean isReal() {
        return typedef == DataBase.defFloat || typedef == DataBase.defDouble;
    }

    public boolean equals(Pointer obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj == nullPointer || obj == voidPointer) return false;

        return obj.typedef == this.typedef && Arrays.equals(obj.generics, this.generics);
    }

    public boolean fullEquals(Pointer obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj == nullPointer || obj == voidPointer) return false;

        if (obj.typedef == this.typedef && genIndex == obj.genIndex && innerGeneric == obj.innerGeneric) {
            if (generics.length == obj.generics.length) {
                for (int i = 0; i < generics.length; i++) {
                    Pointer myGeneric = generics[i];
                    Pointer otherGeneric = obj.generics[i];
                    if (!myGeneric.fullEquals(otherGeneric)) {
                        return false;
                    }
                }
            } else {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj == nullPointer || obj == voidPointer) return false;

        if (obj instanceof Pointer) {
            return ((Pointer) obj).typedef == this.typedef && Arrays.equals(((Pointer) obj).generics, this.generics);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        if (this == nullPointer) return "(null)";
        if (this == voidPointer) return "(void)";
        if (typedef == DataBase.defArray) {
            return generics[0] + "[]";
        } else {
            String gen = "<";
            for (Pointer generic : generics) gen += generic;
            return (typedef == null ? "null" : typedef.getName()) +
                    (generics.length > 0 ? gen + ">" : "");
        }
    }
}
