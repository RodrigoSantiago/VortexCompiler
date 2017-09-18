package com.vortex.compiler.logic.typedef;

import com.vortex.compiler.content.Token;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.Acess;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.*;
import com.vortex.compiler.logic.space.Workspace;

import static com.vortex.compiler.logic.Type.*;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 03/10/2016
 */
public class Enum extends Typedef {
    public static final String keyword = "enum";

    public Enum(Workspace workspace, Token token, Token[] tokens) {
        super(workspace, token, tokens, keyword, ENUM, false, false, false);
    }

    @Override
    public void load() {
        if (loaded) return;
        super.load();

        for (int i = 0; i < parents.size(); i++) {
            Typedef parent = parents.get(i).typedef;
            if (parent.type != INTERFACE) {
                addCleanErro("enuns should only inherit interfaces", parentsTokens.get(i));
                parentsTokens.remove(i);
                parents.remove(i--);
            } else {
                for (int j = i + 1; j < parents.size(); j++) {
                    if (parents.get(i).typedef == parents.get(j).typedef) {
                        addCleanErro("repeated parent", parentsTokens.get(j));
                        parentsTokens.remove(j);
                        parents.remove(j--);
                    }
                }
            }
        }

        parents.add(0, new Pointer(DataBase.defEnum));
        parentsTokens.add(0, nameToken);
    }

    @Override
    public void internalLoad() {
        internalRead(false, true, false, true);

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
            } else {
                if (constructor.getAcess() == Acess.DEFAULT) {
                    constructor.fixAcess(Acess.PRIVATE);
                } else if (constructor.getAcess() != Acess.PRIVATE) {
                    addCleanErro("enums constructors should be private", constructor.acessToken);
                }
            }
            if(!constructor.hasImplementation()){
                addCleanErro("constructors should implement", constructor.contentToken);
            }
        }

        for (Method method : methods) {
            if (method.isAbstract()) {
                addCleanErro("enums should not have abstract methods", method.modifierToken);
            } else if (!method.hasImplementation()) {
                addCleanErro("methods should implement", method.contentToken);
            }
            if (method.isFinal()) {
                addWarning("enums's methods are always final", method.modifierToken);
            }
            method.fixFinal(true);
            method.fixAbstract(false);
        }

        for (Property property : properties) {
            if (property.isAbstract()) {
                addCleanErro("enums should not have abstract properties", property.modifierToken);
            } else if (!property.canBeNonAbstract()) {
                addCleanErro("properties should implement", property.contentToken);
            }
            if (property.isFinal()) {
                addWarning("enums's properties are always final", property.modifierToken);
            }
            property.fixFinal(true);
            property.fixAbstract(false);
        }
    }

    @Override
    public void crossLoad() {
        if (!verifyMethods() || !verifyIndexers() || !verifyProperties() || !verifyFields()) return;

        for (Pointer parent : parents) {
            for (Method m : parent.typedef.getAllMethods()) {
                m = m.byGenerics(parent.generics);
                if (m.isAbstract() && getPointer().getImplMethod(m) == null) {
                    addCleanErro("enums should implement all abstract methods", nameToken);
                    return;
                }
            }
            for (Indexer id : parent.typedef.getAllIndexers()) {
                id = id.byGenerics(parent.generics);
                if (id.isAbstract() && getPointer().getImplIndexer(id) == null) {
                    addCleanErro("enums should implement all abstract indexers", nameToken);
                    return;
                }
            }
            for (Property p : parent.typedef.getAllProperties()) {
                p = p.byGenerics(parent.generics);
                if (p.isAbstract() && getPointer().getImplProperty(p) == null) {
                    addCleanErro("enums should implement all abstract properties", nameToken);
                    return;
                }
            }
        }
    }

    @Override
    public void internalMake() {
        super.internalMake();

        for (Constructor constructor : this.constructors) {
            if (constructor.hasImplementation() && constructor.getSuperConstructor() == constructor) {
                addCleanErro("this constructor should call a super constructor", constructor.getToken().byHeader());
                break;
            }
        }
    }

    @Override
    public void build(CppBuilder cBuilder) {
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
                .add("class ").add(unicFullName);

        if (parents.size() == 1) {
            cBuilder.add(" : public ").path(parents.get(0));
        } else if (parents.size() >= 2) {
            for (int i = 0; i < parents.size(); i++) {
                cBuilder.add(i == 0 ? " : " : ", ").add("public virtual ").path(parents.get(i));
            }
        }
        cBuilder.add(" {").ln()
                .add("public:").ln()
                .dynamicFixer(pointer).ln();

        //Init vars
        cBuilder.add("\tstatic InitKey init;").ln();

        //Init methods
        cBuilder.add("\tvoid initInstance();").ln();
        cBuilder.add("\tstatic void initClass();").ln();
        cBuilder.add("\tstatic inline void initCheck() {if (init.begin()) {initClass(); init.end();}}").ln();

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

        //Variables statement
        for (Variable variable : variables) {
            variable.build(cBuilder);
        }

        for (Property property : properties) {
            property.autoBuild(cBuilder);
        }

        for (Enumeration enumeration : enumerations) {
            enumeration.build(cBuilder);
        }

        //Methods statement fixer
        for (int i = 0; i < methods.size(); i++) {
            if (!methods.get(i).isOverrider()) {
                boolean first = true;
                for (int j = 0; j < i; j++) {
                    if (methods.get(i).getName().equals(methods.get(j).getName())) {
                        first = false;
                        break;
                    }
                }
                if (first) methods.get(i).buildUsing(cBuilder);
            }
        }

        //Static init
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
        int pos = 0;
        for (Enumeration enumeration : enumerations) {
            pos += enumeration.buildInit(cBuilder, pos);
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

        //Native Source
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
        return "enum : [name:" + getName() + "] [acess:" + getAcess() + "]";
    }
}
