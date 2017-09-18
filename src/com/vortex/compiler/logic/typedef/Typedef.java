package com.vortex.compiler.logic.typedef;

import com.vortex.compiler.content.Parser;
import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.content.TokenSplitter;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.Acess;
import com.vortex.compiler.logic.Document;
import com.vortex.compiler.logic.LogicToken;
import com.vortex.compiler.logic.Type;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.*;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.header.variable.GenericStatement;
import com.vortex.compiler.logic.space.Workspace;

import java.util.ArrayList;
import java.util.HashMap;

import static com.vortex.compiler.logic.Type.*;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 01/10/2016
 */
public class Typedef extends LogicToken implements Document {
    public final Type type;
    public final String fullName;
    public final String unicFullName;
    public final Workspace workspace;

    //Leitura
    public Token acessToken, modifierToken, keywordToken, nameToken, genericsToken, contentToken;
    public ArrayList<Token> parentsTokens = new ArrayList<>();

    //Carregamento especial
    protected boolean loaded;
    protected ArrayList<Typedef> preloadTypedefs = new ArrayList<>();
    protected ArrayList<Typedef> preloadParents = new ArrayList<>();

    //Conteudo Interno
    protected String nameValue;
    protected Acess acessValue;
    protected boolean abstractValue, finalValue;
    protected Pointer pointerValue, defPointerValue;

    //Listas Internas
    public GenericStatement generics = new GenericStatement();
    public ArrayList<Pointer> parents = new ArrayList<>();
    public ArrayList<Header> headers = new ArrayList<>();
    public HashMap<String, Field> fields = new HashMap<>();

    public Destructor destructor;
    public ArrayList<Constructor> constructors = new ArrayList<>();
    public ArrayList<Property> properties = new ArrayList<>();
    public ArrayList<Method> methods = new ArrayList<>();
    public ArrayList<Indexer> indexers = new ArrayList<>();
    public ArrayList<OpOverload> operators = new ArrayList<>();
    public ArrayList<Variable> variables = new ArrayList<>();
    public ArrayList<Enumeration> enumerations = new ArrayList<>();
    public ArrayList<NativeHeader> nativeHeaders = new ArrayList<>();

    //Sub Listas Completas (Componentes herdados + componentes staticos)
    Constructor staticConstructor;
    private ArrayList<Constructor> allConstructors;
    private ArrayList<Method> allMethods;
    private ArrayList<Property> allProperties;
    private ArrayList<Indexer> allIndexers;
    private HashMap<String, Field> allFields;

    private boolean fake;

    public Typedef(Workspace workspace, Token token) {
        this.token = token;
        this.strFile = token.getStringFile();
        this.type = CLASS;
        this.workspace = workspace;
        this.typedef = this;

        loaded = true;

        acessValue = Acess.DEFAULT;
        nameValue = "default";
        nameToken = token;

        fullName = workspace.getNameSpace().fullName + "::" + nameValue;
        unicFullName = fullName.replaceAll("_", "_u").replaceAll("::", "__");

        pointerValue = new Pointer(typedef);
        defPointerValue = new Pointer(typedef);

        fake = true;
    }

    public Typedef(Workspace workspace, Token token, Token[] tokens, String keyword, Type type,
                   boolean accGeneric, boolean accAbstract, boolean accFinal) {
        this.token = token;
        this.strFile = token.getStringFile();
        this.type = type;
        this.workspace = workspace;
        this.typedef = this;

        //[0-acess][0-modifier][0-keyword][1-name<generics>][2-:]([3-parent<generics>][4,])*[2,4-{}]
        boolean lastHasErro = false;
        int stage = 0;
        for (Token sToken : tokens) {
            lastHasErro = false;
            if (stage == 0) {
                if (sToken.compare(keyword)) {              //Keyword
                    keywordToken = sToken;
                    stage = 1;
                } else if (SmartRegex.isAcess(sToken)) {    //Acess Modifier
                    if (acessToken == null) {
                        acessToken = sToken;
                    } else {
                        addCleanErro("repeated acess modifier", sToken);
                    }
                } else if (sToken.compare("abstract")) {    //Abstract Modifier
                    if (accAbstract) {
                        if (modifierToken == null) {
                            modifierToken = sToken;
                        } else {
                            addCleanErro("repeated modifier", sToken);
                        }
                    } else {
                        addCleanErro("invalid modifier", sToken);
                    }
                } else if (sToken.compare("final")) {       //Final Modifier
                    if (accFinal) {
                        if (modifierToken == null) {
                            modifierToken = sToken;
                        } else {
                            addCleanErro("repeated modifier", sToken);
                        }
                    } else {
                        addCleanErro("invalid modifier", sToken);
                    }
                } else if (SmartRegex.isModifier(sToken)) { //Modifiers
                    addCleanErro("invalid modifier", sToken);
                } else {
                    lastHasErro = true;
                    addCleanErro("unexpected token", sToken);
                }
            } else if (stage == 1) {
                if (SmartRegex.simpleName(sToken)) {        //Name
                    nameToken = sToken;
                    genericsToken = null;
                    if (SmartRegex.isKeyword(nameToken)) {
                        addCleanErro("illegal name", nameToken);
                    }
                    stage = 2;
                } else if (SmartRegex.typedefStatement(sToken)) {   //Name-Generic Statement
                    nameToken = sToken.subSequence(0, sToken.indexOf('<'));
                    genericsToken = sToken.subSequence(sToken.indexOf('<'), sToken.length());
                    if (SmartRegex.isKeyword(nameToken)) {
                        addCleanErro("illegal name", nameToken);
                    }
                    if (!accGeneric) {
                        addCleanErro("generics are not allowed here", genericsToken);
                        genericsToken = null;
                    } else {
                        generics.read(this, genericsToken);
                    }
                    stage = 2;
                } else {
                    lastHasErro = true;
                    addCleanErro("unexpected token", sToken);
                }
            } else if (stage == 2 && sToken.compare(":")) {
                stage = 3;
            } else if (stage == 3 && SmartRegex.typedefParent(sToken)) {
                parentsTokens.add(sToken);
                stage = 4;
            } else if (stage == 4 && sToken.compare(",")) {
                stage = 3;
            } else if ((stage == 2 || stage == 4) && sToken.isClosedBy("{}")) {
                contentToken = sToken;
                stage = -1;
            } else {
                lastHasErro = true;
                addCleanErro("unexpected token", sToken);
            }
        }
        if (stage != -1) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
        }

        if (nameToken != null) {
            nameValue = nameToken.toString();
        } else {
            nameValue = "";
            nameToken = keywordToken;   //Evitar null pointers
        }
        acessValue = Acess.fromToken(acessToken);
        if (!acessValue.isSimple()) {
            addCleanErro("this access modifier is not allowed here", acessToken);
            acessValue = Acess.DEFAULT;
        }
        abstractValue = SmartRegex.compare(modifierToken, "abstract");
        finalValue = SmartRegex.compare(modifierToken, "final");

        fullName = workspace.getNameSpace().fullName + "::" + nameValue;
        unicFullName = fullName.replaceAll("_", "_u").replaceAll("::", "__");
    }

    /**
     * Precarregamento para evitar referência cíclicas em parentes e genéricos
     */
    public final void preload() {
        for (Token parentsToken : parentsTokens) {
            cyclicAnalyzer(parentsToken, true, true);
        }

        for (Token typeToken : generics.typeTokens) {
            if (typeToken != null) {
                cyclicAnalyzer(typeToken, true, false);
            }
        }
    }

    /**
     * Carregamento principal de genéricos e herança
     */
    public void load() {
        loaded = true;

        if (!fullName.isEmpty()) {
            if (workspace.getTypedef(fullName) != this) {
                addCleanErro("repeated typdef statement(same name)", nameToken);
            } else if (!workspace.verifyName(this)) {
                addCleanErro("repeated typdef statement(same type and same name ignoring case)", nameToken);
            }
        }

        for (int i = 0; i < generics.typeTokens.size(); i++) {
            Token typeToken = generics.typeTokens.get(i);
            if (typeToken != null) {
                if (cyclicAnalyzer(typeToken, false, false)) {
                    generics.typeTokens.set(i, null);
                }
            }
        }

        generics.load();

        workspace.setGenerics(generics);

        pointerValue = new Pointer(typedef, generics.defReplacement);
        defPointerValue = new Pointer(typedef, generics.pointers);

        for (int i = 0; i < parentsTokens.size(); i++) {
            Token parentToken = parentsTokens.get(i);
            if (!cyclicAnalyzer(parentToken, false, true)) {
                Pointer pointer = workspace.getPointer(this, parentToken, null, false, true, false);
                if (pointer != null) {
                    parents.add(pointer);
                } else {
                    addCleanErro("unknown typedef", parentToken);
                    parentsTokens.remove(i--);
                }
            } else {
                parentsTokens.remove(i--);
            }
        }

    }

    /**
     * Leitura dos componentes do typedef e verificacao sintaxica e logica dentro de cada tipo especifico
     */
    public void internalLoad() {
    }

    /**
     * Verificacao cruzada dos componentes do typedef, suas herancas e de suas obrigações
     */
    public void crossLoad() {
    }

    /**
     * Leitura e processamento dos conteudos executaveis dentro de cada bloco
     */
    public void internalMake() {
        for (Header header : headers) {
            if (!header.isWrong()) {
                header.make();
            }
        }
    }

    /**
     * Escrita direta em um arquivo CPP virtual
     *
     *  @param cBuilder CppBuilder
     */
    public void build(CppBuilder cBuilder) {
    }

    /**
     * Leitura interna dos componentes
     *
     * @param dAllowVars      Desabilita Variaveis de instancia(interface)
     * @param allowEnums      Habilita Enumeradores(Enum)
     * @param allowOperators  Habilita Operadores(Struct)
     * @param allowDestructor Habilita Destrutores(Class)
     */
    protected void internalRead(boolean dAllowVars, boolean allowEnums, boolean allowOperators, boolean allowDestructor) {
        for (int i = 0; i < parents.size(); i++) {
            if (!parents.get(i).verifyGenerics()) {
                addCleanErro("invalid generics", parentsTokens.get(i));
                parents.set(i, parents.get(i).typedef.getDefPointer());
            }
        }

        if (contentToken != null && contentToken.isClosedBy("{}")) {
            Parser.parseHeaders(this, contentToken.byContent(), allowEnums, allowOperators, allowDestructor);
        }

        for (Header header : headers) {
            if (header.isWrong()) continue;
            header.load();
            if (header.isWrong()) continue;

            if (header.type == NATIVE) {
                nativeHeaders.add((NativeHeader) header);
            } else if (header.type == DESTRUCTOR) {
                if (destructor == null) {
                    destructor = (Destructor) header;
                } else {
                    addCleanErro("repeated destructor", header.getToken().byHeader());
                }
            } else if (header.type == CONSTRUCTOR) {
                boolean unicSignature = true;
                Constructor newConstructor = (Constructor) header;
                if (dAllowVars && !newConstructor.isStatic()) {
                    addCleanErro("instance constructors are not allowed here", newConstructor.getToken().byHeader());
                } else {
                    for (Constructor constructor : constructors) {
                        if (newConstructor.isSameSignature(constructor)) {
                            addCleanErro("repeated signature", newConstructor.getToken().byHeader());
                            unicSignature = false;
                            break;
                        }
                    }
                    if (unicSignature) constructors.add(newConstructor);
                    if (newConstructor.isStatic()) staticConstructor = newConstructor;
                }
            } else if (header.type == METHOD) {
                boolean unicSignature = true;
                Method newMethod = (Method) header;
                for (Method method : methods) {
                    if (newMethod.isSameSignature(method)) {
                        addCleanErro("repeated signature", newMethod.getToken().byHeader());
                        unicSignature = false;
                        break;
                    }
                }
                if (unicSignature) methods.add(newMethod);
            } else if (header.type == OPERATOR) {
                boolean unicSignature = true;
                OpOverload newOperator = (OpOverload) header;
                for (OpOverload operator : operators) {
                    if (newOperator.isSameSignature(operator)) {
                        addCleanErro("repeated signature", newOperator.getToken().byHeader());
                        unicSignature = false;
                        break;
                    }
                }
                if (unicSignature) operators.add(newOperator);
            } else if (header.type == INDEXER) {
                boolean unicSignature = true;
                Indexer newIndexer = (Indexer) header;
                for (Indexer indexer : indexers) {
                    if (newIndexer.isSameSignature(indexer)) {
                        addCleanErro("repeated signature", newIndexer.getToken().byHeader());
                        unicSignature = false;
                        break;
                    }
                }
                if (unicSignature) indexers.add(newIndexer);
            } else if (header.type == PROPERTY) {
                Property property = (Property) header;
                Field newField = property.getField();
                Field field = fields.get(newField.getName());
                if (field == null) {
                    fields.put(newField.getName(), newField);
                    properties.add(property);
                } else {
                    addCleanErro("repeated signature", newField.getToken());
                }
            } else if (header.type == VARIABLE) {
                Variable variable = (Variable) header;
                if (dAllowVars && !variable.isStatic()) {
                    addCleanErro("instance variables are not allowed here", variable.getToken());
                } else {
                    variables.add(variable);
                    for (Field newField : variable.getFields()) {
                        Field field = fields.get(newField.getName());
                        if (field == null) {
                            fields.put(newField.getName(), newField);
                        } else {
                            addCleanErro("repeated signature", newField.getToken());
                        }
                    }
                }
            } else if (header.type == NUM) {
                Enumeration enumeration = (Enumeration) header;
                enumerations.add(enumeration);
                for (Field newField : enumeration.getFields()) {
                    Field field = fields.get(newField.getName());
                    if (field == null) {
                        fields.put(newField.getName(), newField);
                    } else {
                        addCleanErro("repeated signature", newField.getToken());
                    }
                }
            }
        }
    }

    /**
     * Ponteiro para classe atual
     *
     * @return Pointer
     */
    public Pointer getPointer() {
        if (pointerValue == null) {
            load();
        }
        return pointerValue;
    }

    /**
     * Ponteiro padrao para classe atual (com genericos implicitos)
     *
     * @return Pointer
     */
    public Pointer getDefPointer() {
        if (defPointerValue == null) {
            load();
        }
        return defPointerValue;
    }

    public String getName() {
        return nameValue;
    }

    public Acess getAcess() {
        return acessValue;
    }

    public boolean isAbstract() {
        return abstractValue;
    }

    public boolean isFinal() {
        return finalValue;
    }

    public boolean isLangImplement() {
        return this == DataBase.defByte || this == DataBase.defChar ||
                this == DataBase.defShort || this == DataBase.defInt || this == DataBase.defLong ||
                this == DataBase.defFloat || this == DataBase.defDouble || this == DataBase.defBool ||
                this == DataBase.defFunction;
    }

    public HashMap<String, Field> getAllFields() {
        if (allFields == null) {
            allFields = new HashMap<>();
            allFields.putAll(fields);
            for (Pointer parent : parents) {
                for (Field parentField : parent.typedef.getAllFields().values()) {
                    if (!parentField.isStatic() && allFields.get(parentField.getName()) == null) {
                        allFields.put(parentField.getName(), parentField.byGenerics(parent.generics));
                    }
                }
            }
        }
        return allFields;
    }

    public ArrayList<Indexer> getAllIndexers() {
        if (allIndexers == null) {
            allIndexers = new ArrayList<>();
            allIndexers.addAll(indexers);
            for (Pointer parent : parents) {
                for (Indexer parentIndexer : parent.typedef.getAllIndexers()) {
                    boolean unic = true;
                    for (Indexer indexer : allIndexers) {
                        if (indexer.isSameSignature(parentIndexer)) {
                            unic = false;
                            break;
                        }
                    }
                    if (unic) allIndexers.add(parentIndexer);
                }
            }
        }
        return allIndexers;
    }

    public ArrayList<Constructor> getAllConstructors() {
        if (allConstructors == null) {
            allConstructors = new ArrayList<>();
            boolean hasEmpty = false;
            for (Constructor constructor : constructors) {
                if (!constructor.isStatic()) {
                    allConstructors.add(constructor);
                    if (constructor.params.isEmpty()) hasEmpty = true;
                }
            }
            if (allConstructors.isEmpty() && parents.size() > 0) {
                allConstructors.addAll(parents.get(0).typedef.getAllConstructors());
            }
            if (!hasEmpty && type == STRUCT) {
                allConstructors.add(DataBase.defObject.constructors.get(0));
            }
        }
        return allConstructors;
    }

    public ArrayList<Method> getAllMethods() {
        if (allMethods == null) {
            allMethods = new ArrayList<>();
            allMethods.addAll(methods);
            for (Pointer parent : parents) {
                for (Method parentMethod : parent.typedef.getAllMethods()) {
                    if (!parentMethod.isStatic()) {
                        boolean unic = true;
                        for (Method method : allMethods) {
                            if (method.isSameSignature(parentMethod)) {
                                unic = false;
                                break;
                            }
                        }
                        if (unic) allMethods.add(parentMethod);
                    }
                }
            }
        }
        return allMethods;
    }

    public ArrayList<Property> getAllProperties() {
        if (allProperties == null) {
            allProperties = new ArrayList<>();
            allProperties.addAll(properties);
            for (Pointer parent : parents) {
                for (Property parentProperty : parent.typedef.getAllProperties()) {
                    if (!parentProperty.isStatic()) {
                        boolean unic = true;
                        for (Property property : allProperties) {
                            if (property.isSameSignature(parentProperty)) {
                                unic = false;
                                break;
                            }
                        }
                        if (unic) allProperties.add(parentProperty);
                    }
                }
            }
        }
        return allProperties;
    }

    /**
     * Verifica se este typedef herda do outro, ou eh igual a este (analise por ponteiro)
     *
     * @param other Typedef
     * @return true-false
     */
    public boolean isInstanceOf(Typedef other) {
        if (this == other) return true;
        for (Pointer parent : parents) {
            if (parent.typedef.isInstanceOf(other)) return true;
        }
        return false;
    }

    /**
     * Verifica se este typedef possui o outro na heranca, ou eh igual a este (analise por typedef)
     *
     * @param other Typedef outro
     * @return true-false
     */
    private boolean hasUsedOnParents(Typedef other) {
        if (other == this) return true;
        for (Typedef typedef : preloadParents) {
            if (typedef.hasUsedOnParents(other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifica se este typedef possui o outro na heranca ou nos genericos, ou eh igual a este
     *
     * @param other Typedef outro
     * @return true-false
     */
    private boolean hasUsed(Typedef other) {
        if (other == this) return true;
        for (Typedef typedef : preloadTypedefs) {
            if (typedef.hasUsed(other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifica se o typedef provindo do token tem acesso ciclico
     *
     * @param typedefToken Token
     * @param readMode     Modo leitura (inclui os typedefs na lista)
     * @param parentMode   Modo heranca (ignora o primeiro typedef)
     * @return true-false
     */
    private boolean cyclicAnalyzer(Token typedefToken, boolean readMode, boolean parentMode) {
        TokenSplitter splitter = new TokenSplitter(typedefToken);
        Token nameToken = splitter.getNext();

        //Procurar nome nos typedefs importados
        Typedef typedef = workspace.getTypedef(nameToken);

        if (typedef == null) {
            return false;
        } else if ((!parentMode && typedef.hasUsed(this)) || (parentMode && typedef.hasUsedOnParents(this))) {
            if (readMode) {
                return false;
            } else {
                addCleanErro("cyclic reference", nameToken);
                return true;
            }
        }

        if (readMode) {
            if (!preloadTypedefs.contains(typedef)) {
                preloadTypedefs.add(typedef);
                if (parentMode) {
                    preloadParents.add(typedef);
                    return false;
                }
            }
        }

        if (parentMode) return false;

        //Preparar genericos internos e array
        Token sToken = splitter.getNext();
        if (sToken != null) {
            if (sToken.startsWith("<") && sToken.endsWith(">")) {
                Token tokens[] = TokenSplitter.split(sToken.byNested(), true);
                int stage = 0;
                for (Token nToken : tokens) {
                    if (stage == 0 && SmartRegex.pointer(nToken)) {
                        if (cyclicAnalyzer(nToken, readMode, false)) return true;
                        stage = 1;
                    } else if (stage == 1 && nToken.compare(",")) {
                        stage = 0;
                    } else {
                        if (!readMode) addCleanErro("unexpected token", nToken);
                    }
                }
            }
        }
        return false;
    }

    /**
     * Verifica campos do typedef atual e conflitos nas herancas
     *
     * @return true-false
     */
    protected boolean verifyFields() {
        if (parents.size() == 0) return true;

        for (Field iField : parents.get(0).typedef.getAllFields().values()) {
            if (!iField.isStatic()) {
                //Conflito de herancas
                if (iField.type != PROPERTY) {
                    for (int j = 1; j < parents.size(); j++) {
                        Field jField = parents.get(j).findField(iField.getName());
                        if (jField != null && !jField.isStatic()) {
                            addCleanErro("parents fields signature conflict", parentsTokens.get(0));
                            addCleanErro("parents fields signature conflict", parentsTokens.get(j));
                            return false;
                        }
                    }
                }
                //Override
                Field myField = fields.get(iField.getName());
                if (myField != null && !myField.isStatic() &&
                        (iField.type != PROPERTY || myField.type != PROPERTY)) {
                    addCleanErro("invalid override", myField);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Verifica metodos do typedef atual e conflitos na heranca
     *
     * @return true-false
     */
    protected boolean verifyMethods() {

        for (int i = 0; i < parents.size() - 1; i++) {         //Navegar pelos parents
            Pointer iParent = parents.get(i);
            for (int j = i + 1; j < parents.size(); j++) {     //Navegar aninhadamente
                for (Method iMethod : iParent.typedef.getAllMethods()) {
                    iMethod = iMethod.byGenerics(getPointer().generics);
                    Method jMethod = parents.get(j).getIdenMethod(iMethod);

                    if (jMethod != null) jMethod = jMethod.byGenerics(getPointer().generics);

                    if (jMethod != null && !iMethod.isStatic() && !jMethod.isStatic() &&
                            !iMethod.isCompatible(jMethod)) {
                        addCleanErro("parents methods signature conflict", parentsTokens.get(i));
                        addCleanErro("parents methods signature conflict", parentsTokens.get(j));
                        return false;
                    }
                }
            }
        }

        //Verifica se os metodos atuais fazem o override validamente
        for (Pointer parent : parents) {
            for (Method myMethod : methods) {
                if (myMethod.isWrong()) continue;
                Method otherMethod = parent.getIdenMethod(myMethod);
                if (otherMethod == null) continue;
                otherMethod = otherMethod.byGenerics(getPointer().generics);

                otherMethod.setOverriden();
                myMethod.setOverrider();

                if (myMethod.isAbstract()) {
                    myMethod.addErro("abstract methods cannot override", myMethod.getToken().byHeader());
                } else if (myMethod.isStatic()) {
                    myMethod.addErro("invalid override, incompatible target", myMethod.getToken().byHeader());
                } else if (!myMethod.isOverridable(otherMethod)) {
                    myMethod.addErro("invalid override, incompatible types", myMethod.getToken().byHeader());
                } else if (!Acess.TesteAcess(this, otherMethod)) {
                    myMethod.addErro("invalid override, cannot acess", myMethod.getToken().byHeader());
                } else if (myMethod.getAcess().isMostPrivate(otherMethod.getAcess())) {
                    myMethod.addErro("invalid override, incompatible acess", myMethod.getToken().byHeader());
                } else if (otherMethod.isFinal()) {
                    myMethod.addErro("final methods should not be overriden", myMethod.getToken().byHeader());
                }
            }
        }
        return true;
    }

    /**
     * Verifica indexadores do typedef atual e conflitos na heranca
     *
     * @return true-false
     */
    protected boolean verifyIndexers() {

        for (int i = 0; i < parents.size() - 1; i++) {         //Navegar pelos parents
            Pointer iParent = parents.get(i);
            for (int j = i + 1; j < parents.size(); j++) {     //Navegar aninhadamente
                Pointer jParent = parents.get(j);
                for (Indexer iIndexer : iParent.typedef.getAllIndexers()) {
                    iIndexer = iIndexer.byGenerics(getPointer().generics);
                    Indexer jIndexer = jParent.getIdenIndexer(iIndexer);

                    if (jIndexer != null) jIndexer = jIndexer.byGenerics(getPointer().generics);

                    if (jIndexer != null && !iIndexer.isCompatible(jIndexer)) {
                        addCleanErro("parents indexers signature conflict", parentsTokens.get(i));
                        addCleanErro("parents indexers signature conflict", parentsTokens.get(j));
                        return false;
                    }
                }
            }
        }

        //Verifica se os indexers atuais fazem o override validamente
        for (Pointer parent : parents) {
            for (Indexer myIndexer : indexers) {
                if (myIndexer.isWrong()) continue;
                Indexer otherIndexer = parent.getIdenIndexer(myIndexer);
                if (otherIndexer == null) continue;
                otherIndexer = otherIndexer.byGenerics(getPointer().generics);

                otherIndexer.setOverriden();
                myIndexer.setOverrider();

                if (myIndexer.isAbstract()) {
                    myIndexer.addErro("abstract indexers cannot override", myIndexer.getToken().byHeader());
                } else if (!myIndexer.isOverridable(otherIndexer)) {
                    myIndexer.addErro("invalid override, incompatible types", myIndexer.getToken().byHeader());
                } else if (!Acess.TesteAcess(this, otherIndexer)
                        || !Acess.TesteAcess(this, otherIndexer, otherIndexer.getGetAcess())
                        || !Acess.TesteAcess(this, otherIndexer, otherIndexer.getSetAcess())) {
                    myIndexer.addErro("invalid override, cannot acess", myIndexer.getToken().byHeader());
                } else if (myIndexer.getAcess().isMostPrivate(otherIndexer.getAcess())) {
                    myIndexer.addErro("invalid override, incompatible acess", myIndexer.getToken().byHeader());
                } else if (otherIndexer.isFinal()) {
                    myIndexer.addErro("final indexers should not be overriden", myIndexer.getToken().byHeader());
                }
            }
        }
        return true;
    }

    /**
     * Verifica propriedades do typedef atual e conflitos na heranca
     *
     * @return true-false
     */
    protected boolean verifyProperties() {

        for (int i = 0; i < parents.size() - 1; i++) {          //Navegar pelos parents
            Pointer parentI = parents.get(i);
            for (int j = i + 1; j < parents.size(); j++) {      //Navegar aninhadamente
                for (Property iProperty : parentI.typedef.getAllProperties()) {
                    iProperty = iProperty.byGenerics(getPointer().generics);
                    Property jProperty = parents.get(j).getIdenProperty(iProperty);

                    if (jProperty != null) jProperty = jProperty.byGenerics(getPointer().generics);

                    if (jProperty != null && !iProperty.isStatic() && !jProperty.isStatic() &&
                            !iProperty.isCompatible(jProperty)) {
                        addCleanErro("conflict on parents properties signature", parentsTokens.get(i));
                        addCleanErro("conflict on parents properties signature", parentsTokens.get(j));
                        return false;
                    }
                }
            }
        }

        //Verifica se as properties atuais fazem o override validamente
        for (Pointer parent : parents) {
            for (Property myProperty : properties) {
                if (myProperty.isWrong()) continue;
                Property otherProperty = parent.getIdenProperty(myProperty);
                if (otherProperty == null) continue;
                otherProperty = otherProperty.byGenerics(getPointer().generics);

                otherProperty.setOverriden();
                myProperty.setOverrider();

                if (myProperty.isAbstract()) {
                    myProperty.addErro("abstract properties cannot override", myProperty.getToken().byHeader());
                } else if (myProperty.isStatic()) {
                    myProperty.addErro("invalid override, incompatible target", myProperty.getToken().byHeader());
                } else if (!myProperty.isOverridable(otherProperty)) {
                    myProperty.addErro("invalid override, incompatible override", myProperty.getToken().byHeader());
                } else if (!Acess.TesteAcess(this, otherProperty)
                        || !Acess.TesteAcess(this, otherProperty, otherProperty.getGetAcess())
                        || !Acess.TesteAcess(this, otherProperty, otherProperty.getSetAcess())) {
                    myProperty.addErro("invalid override, cannot acess", myProperty.getToken().byHeader());
                } else if (myProperty.getAcess().isMostPrivate(otherProperty.getAcess())) {
                    myProperty.addErro("invalid override, incompatible acess", myProperty.getToken().byHeader());
                } else if (myProperty.hasGet() && myProperty.getGetAcess().isMostPrivate(otherProperty.getGetAcess())) {
                    myProperty.addErro("invalid override, incompatible acess", myProperty.acessTokenGet);
                } else if (myProperty.hasSet() && myProperty.getSetAcess().isMostPrivate(otherProperty.getSetAcess())) {
                    myProperty.addErro("invalid override, incompatible acess", myProperty.acessTokenSet);
                } else if (otherProperty.isFinal()) {
                    myProperty.addErro("final properties should not be overriden", myProperty.getToken().byHeader());
                }
            }
        }
        return true;
    }

    public boolean isFake() {
        return fake;
    }

    @Override
    public String toString() {
        return "typedef : [name][" + getName() + "] [acess][" + getAcess() + "] "
                + (isFinal() ? "[final] " : "")
                + (isAbstract() ? "[abstract] " : "")
                + ("[generics] " + generics);
    }

    @Override
    public String getDocument() {
        return toString();
    }
}
