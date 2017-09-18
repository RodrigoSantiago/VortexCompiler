package com.vortex.compiler.logic.typedef;

import com.vortex.compiler.content.Token;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.Acess;
import com.vortex.compiler.logic.Operator;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.*;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.space.Workspace;

import java.util.ArrayList;

import static com.vortex.compiler.logic.Type.*;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 03/10/2016
 */
public class Struct extends Typedef {
    public static final String keyword = "struct";

    public Pointer wrapperParent;
    public Token wrapperToken;

    private ArrayList<Struct> varDependences = new ArrayList<>();

    public Struct(Workspace workspace, Token token, Token[] tokens) {
        super(workspace, token, tokens, keyword, STRUCT, false, false, false);
    }

    @Override
    public void load() {
        if (loaded) return;
        super.load();

        for (int i = 0; i < parents.size(); i++) {
            Typedef parent = parents.get(i).typedef;
            if (wrapperParent != null) {
                addCleanErro("struct should have only a single wrapper", parentsTokens.get(i));
                parentsTokens.remove(i);
                parents.remove(i--);
            } else if (parent.type != CLASS) {
                addCleanErro("invalid wrapper", parentsTokens.get(i));
                parentsTokens.remove(i);
                parents.remove(i--);
            } else {
                wrapperParent = parents.get(i);
                wrapperToken = parentsTokens.get(i);
            }
        }
        if (wrapperParent == null) {
            wrapperParent = new Pointer(DataBase.defWrapper, getPointer());
            wrapperToken = nameToken;
        }

        parents.clear();
        parentsTokens.clear();
    }

    @Override
    public void internalLoad() {
        internalRead(false, false, true, false);

        for (Constructor constructor : constructors) {
            if (constructor.isStatic()) {
                if (!constructor.params.isEmpty()) {
                    addCleanErro("static constructors should not have parameters", constructor.parametersToken);
                }
                if (constructor.getAcess() == Acess.DEFAULT) {
                    constructor.fixAcess(Acess.PUBLIC);
                } else if (constructor.getAcess() != Acess.PUBLIC) {
                    addCleanErro("static constructors should be public", constructor.acessToken);
                }
            }
        }

        for (Method method : methods) {
            if (method.isAbstract()) {
                addCleanErro("structs should not have abstract methods", method.modifierToken);
            } else if (!method.hasImplementation()) {
                addCleanErro("methods should implement", method.contentToken);
            }
            method.fixFinal(false);
            method.fixAbstract(false);
        }

        for (Indexer indexer : indexers) {
            if (indexer.isAbstract()) {
                addCleanErro("structs should not have abstract indexers", indexer.modifierToken);
            } else if (!indexer.canBeNonAbstract()) {
                addCleanErro("indexers should implement", indexer.contentToken);
            }
            indexer.fixFinal(false);
            indexer.fixAbstract(false);
        }

        for (Property property : properties) {
            if (property.isAbstract()) {
                addCleanErro("structs should not have abstract properties", property.modifierToken);
            } else if (!property.canBeNonAbstract()) {
                addCleanErro("properties should implement", property.contentToken);
            }
            property.fixFinal(false);
            property.fixAbstract(false);
        }

        for (OpOverload operator : operators) {
            if (!operator.hasImplementation()) {
                addCleanErro("operators should implement", operator.contentToken);
            }
            if (!operator.params.pointers.get(0).fullEquals(getPointer()) && this != DataBase.defFunction) {
                if (operator.params.size() > 1) {
                    if (operator.params.pointers.get(1).fullEquals(getPointer())) {
                        operator.reverse = true;
                    } else {
                        addCleanErro("the first or second parameter should match the struct", operator.parametersToken);
                    }
                } else {
                    addCleanErro("the first parameter should match the struct", operator.parametersToken);
                }
            }
            if ((operator.getOperator().equals("++") || operator.getOperator().equals("--")) &&
                    !operator.getType().fullEquals(getPointer())) {
                addCleanErro("increment operators should return the same type as struct", operator.params.nameTokens.get(0));
            }
        }

        for (Variable variable : variables) {
            if (!variable.isWrong() && variable.getInitType().isStruct()) {
                Struct other = (Struct) variable.getInitType().typedef;
                if (other.varDependences.contains(this)) {
                    addCleanErro("cyclic struct dependences", variable);
                }
                varDependences.add(other);
                varDependences.addAll(other.varDependences);
            }
        }
    }

    @Override
    public void crossLoad() {
        if (wrapperParent != null) {
            Field field = wrapperParent.findField("value");
            if (field == null || !field.getType().fullEquals(getPointer()) || !field.isGettable()
                    || field.getAcessGet() != Acess.PUBLIC || field.isStatic()) {
                addCleanErro("wrappers should have a public instance gettable field named 'value'", wrapperToken);
            }
            Constructor[] constructors = wrapperParent.findConstructor(getPointer());
            if (constructors.length != 1 || constructors[0].getAcess() != Acess.PUBLIC) {
                addCleanErro("wrappers should have a public constructor with this struct as parameter", wrapperToken);
            }
        }
    }

    @Override
    public void build(CppBuilder cBuilder) {
        if (isLangImplement()) return;
        Pointer pointer = getPointer();

        //------------//
        //---Header---//
        //------------//
        cBuilder.toHeader();
        cBuilder.add("//").file(this, ".h").ln()
                .add("#ifndef H_").add(unicFullName).ln()
                .add("#define H_").add(unicFullName).ln()
                .add("#include \"defaultLang.h\"").ln();

        //Dependences
        cBuilder.markHeader();

        //Native Macros
        for (NativeHeader nativeHeader : nativeHeaders) {
            if (nativeHeader.isMacro()) nativeHeader.build(cBuilder);
        }

        //Class Statement
        cBuilder.ln()
                .add(generics)
                .add("class ").add(unicFullName).add(" {").ln()
                .add("public:").ln();

        //Init vars
        cBuilder.add("\tstatic InitKey init;").ln();

        //Init methods
        cBuilder.add("\tvoid initInstance();").ln();
        cBuilder.add("\tstatic void initClass();").ln();
        cBuilder.add("\tstatic inline void initCheck() {if (init.begin()) {initClass(); init.end();}}").ln();

        //Default Value
        cBuilder.add("\t").add(unicFullName).add("() {}").ln();
        cBuilder.add("\tstatic ").add(pointer).add(" defValue;").ln();
        cBuilder.add("\t").add(DataBase.defObjectPointer).add(" wrap();").ln();

        //Native Headers
        for (NativeHeader nativeHeader : nativeHeaders) {
            if (nativeHeader.isHeader()) nativeHeader.build(cBuilder);
        }

        //------------//
        //---Source---//
        //------------//
        cBuilder.toSource();
        cBuilder.add("//").file(this, ".cpp").ln();

        //Dependences
        cBuilder.markSource();

        cBuilder.ln();

        //Static Init and Vars
        cBuilder.add("InitKey ").path(pointer).add("::init;").ln();
        cBuilder.add(pointer).add(" ").path(pointer).add("::defValue = ").path(pointer).add("();").ln();
        for (Variable variable : variables) {
            variable.build(cBuilder);
        }
        for (Property property : properties) {
            property.autoBuild(cBuilder);
        }

        //Static init (source)
        cBuilder.toSource();
        cBuilder.ln()
                .add(generics)
                .add("void ").path(pointer).add("::initClass() ").begin(1);
        for (Pointer parent : parents) {
            cBuilder.add("\t").path(parent).add("::initCheck();").ln();
        }
        for (Variable variable : variables) {
            if (variable.isStatic()) variable.buildInit(cBuilder);
        }
        for (Property property : properties) {
            if (property.isStatic()) property.buildInit(cBuilder);
        }
        if (staticConstructor != null) {
            staticConstructor.build(cBuilder);
        }
        cBuilder.end();

        //Instance Init
        cBuilder.ln()
                .add(generics)
                .add("void ").path(pointer).add("::initInstance() ").begin(1)
                .add("\tinitCheck();").ln();
        for (Variable variable : variables) {
            if (!variable.isStatic()) variable.buildInit(cBuilder);
        }
        for (Property property : properties) {
            if (!property.isStatic()) property.buildInit(cBuilder);
        }
        cBuilder.end();

        //Wrap Value
        cBuilder.ln()
                .add(DataBase.defObjectPointer).add(" ").path(pointer).add("::wrap() ").begin(1)
                .add("\treturn ").constructor(wrapperParent, false).add("(*this);").ln()
                .end();

        //---------------//
        //-Source-Header-//
        //---------------//
        //Class Headers
        for (Constructor constructor : getAllConstructors()) {
            constructor.build(cBuilder, pointer);
        }

        for (Indexer indexer : indexers) {
            indexer.build(cBuilder);
        }

        for (Property property : properties) {
            property.build(cBuilder);
        }

        for (Method method : methods) {
            method.build(cBuilder);
        }

        //Operator Overloading
        OpOverload equalsOverload = null;
        OpOverload differentOverload = null;
        for (OpOverload opOverload : operators) {
            opOverload.build(cBuilder);
            if (equalsOverload == null &&
                    opOverload.getOperator().equals("==") &&
                    opOverload.getType().equals(DataBase.defBoolPointer) &&
                    opOverload.params.pointers.get(1).fullEquals(pointer)) {
                equalsOverload = opOverload;
            }
            if (equalsOverload == null && differentOverload == null &&
                    opOverload.getOperator().equals("!=") &&
                    opOverload.getType().equals(DataBase.defBoolPointer) &&
                    opOverload.params.pointers.get(1).fullEquals(pointer)) {
                differentOverload = opOverload;
            }
        }

        //Default operators
        //Header
        cBuilder.toHeader();
        if (equalsOverload != null) {
            cBuilder.add("\t").add(DataBase.defBoolPointer)
                    .add(" operator == (const ").path(pointer).add("& other)")
                    .add(" { return ").add(Operator.equals).add("(*this, other); }").ln();
        } else if (differentOverload != null) {
            cBuilder.add("\t").add(DataBase.defBoolPointer)
                    .add(" operator == (const ").path(pointer).add("& other)")
                    .add(" { return !").add(Operator.different).add("(*this, other); }").ln();
        } else {
            cBuilder.add("\t").add(DataBase.defBoolPointer)
                    .add(" operator == (const ").path(pointer).add("& other)")
                    .add(" { return false; }").ln();
        }

        //Native Source
        cBuilder.toSource();
        for (NativeHeader nativeHeader : nativeHeaders) {
            if (nativeHeader.isSource()) nativeHeader.build(cBuilder);
        }

        //------------//
        //---Header---//
        //------------//
        cBuilder.toHeader();
        cBuilder.add("};").ln();
        cBuilder.add("#endif").ln();

        //Dependences
        cBuilder.headerDependences(this);
        cBuilder.sourceDependences(this);
    }

    @Override
    public String toString() {
        return "struct : [name:" + getName() + "] [acess:" + getAcess() + "]";
    }
}
