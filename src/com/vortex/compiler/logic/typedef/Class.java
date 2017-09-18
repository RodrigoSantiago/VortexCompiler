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
public class Class extends Typedef {
    public static final String keyword = "class";

    public Class(Workspace workspace, Token token, Token[] tokens) {
        super(workspace, token, tokens, keyword, CLASS, true, true, true);
    }

    @Override
    public void load() {
        if (loaded) return;
        super.load();

        Pointer mainParent = null;
        for (int i = 0; i < parents.size(); i++) {
            Typedef parent = parents.get(i).typedef;
            if (parent.type == CLASS) {
                if (mainParent != null) {
                    addCleanErro("classes should only inherit a single class", parentsTokens.get(i));
                    parentsTokens.remove(i);
                    parents.remove(i--);
                } else if (parent.isFinal()) {
                    addCleanErro("classes should not inherit a final class", parentsTokens.get(i));
                    parentsTokens.remove(i);
                    parents.remove(i--);
                } else {
                    mainParent = parents.get(i);
                }
            } else if (parent.type != INTERFACE) {
                addCleanErro("classes should only inherit classes or interfaces", parentsTokens.get(i));
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

        if (mainParent != null) {
            int index = parents.indexOf(mainParent);
            if (index != 0) {
                Token mainToken = parentsTokens.get(index);
                parents.remove(index);
                parentsTokens.remove(index);
                parents.add(0, mainParent);
                parentsTokens.add(0, mainToken);
            }
        } else if (this != DataBase.defObject) {
            parents.add(0, DataBase.defObjectPointer);
            parentsTokens.add(0, nameToken);
        }
    }

    @Override
    public void internalLoad() {
        internalRead(false, false, false, true);

        for (Constructor constructor : constructors) {
            if (constructor.isStatic()) {
                if (!constructor.params.isEmpty()) {
                    addCleanErro("static constructors should not have parameters", constructor.parametersToken);
                }
                if (constructor.getAcess() == Acess.DEFAULT) {
                    constructor.fixAcess(Acess.PUBLIC);
                } else if (constructor.getAcess() != Acess.PUBLIC) {
                    addCleanErro("static constructors should be public", constructor.acessToken);
                    constructor.fixAcess(Acess.PUBLIC);
                }
            }
        }

        for (Method method : methods) {
            if (method.isAbstract()) {
                if (!isAbstract()) {
                    addCleanErro("abstract methods should only exist in abstract classes", method.modifierToken);
                }
                if (method.hasImplementation()) {
                    addCleanErro("abstract methods should not implement", method.contentToken);
                }
                if (method.isStatic()) {
                    addCleanErro("invalid modifier cobination", method.staticToken);
                    addCleanErro("invalid modifier cobination", method.modifierToken);
                    method.fixAbstract(false);
                } else if (method.getAcess() == Acess.PRIVATE) {
                    addCleanErro("invalid modifier cobination", method.acessToken);
                    addCleanErro("invalid modifier cobination", method.modifierToken);
                    method.fixAcess(Acess.PUBLIC);
                }
            } else if (!method.hasImplementation()) {
                addCleanErro("methods should implement", method.contentToken);
            }
        }

        for (Property property : properties) {
            if (property.isAbstract()) {
                if (!isAbstract()) {
                    addCleanErro("abstract properties should only exist in abstract classes", property.modifierToken);
                }
                if (!property.canBeAbstract()) {
                    addCleanErro("abstract properties should not implement", property.contentToken);
                }
                if (property.isStatic()) {
                    addCleanErro("invalid modifier cobination", property.staticToken);
                    addCleanErro("invalid modifier cobination", property.modifierToken);
                    property.fixAbstract(false);
                } else if (property.getAcess() == Acess.PRIVATE) {
                    addCleanErro("invalid modifier cobination", property.acessToken);
                    addCleanErro("invalid modifier cobination", property.modifierToken);
                    property.fixAcess(Acess.PUBLIC);
                }
            } else if (!property.canBeNonAbstract()) {
                addCleanErro("properties should implement", property.contentToken);
            }
        }

        for (Indexer indexer : indexers) {
            if (indexer.isAbstract()) {
                if (!isAbstract()) {
                    addCleanErro("abstract indexers should only exist in abstract classes", indexer.modifierToken);
                }
                if (!indexer.canBeAbstract()) {
                    addCleanErro("abstract indexers should not implement", indexer.contentToken);
                }
                if (indexer.getAcess() == Acess.PRIVATE) {
                    addCleanErro("invalid modifier cobination", indexer.acessToken);
                    addCleanErro("invalid modifier cobination", indexer.modifierToken);
                    indexer.fixAcess(Acess.PUBLIC);
                }
            } else if (!indexer.canBeNonAbstract()) {
                addCleanErro("indexers should implement", indexer.contentToken);
            }
        }

        if (destructor != null) {
            if (destructor.getAcess() == Acess.DEFAULT) {
                destructor.fixAcess(Acess.PUBLIC);
            } else if (destructor.getAcess() != Acess.PUBLIC) {
                addCleanErro("destructors should be public", destructor.acessToken);
                destructor.fixAcess(Acess.PUBLIC);
            }
        }
    }

    @Override
    public void crossLoad() {
        if (!verifyMethods() || !verifyIndexers() || !verifyProperties() || !verifyFields()) return;

        if (constructors.size() == 0 && parents.size() > 0) {
            Constructor[] constructors = parents.get(0).findConstructor();
            if (constructors.length == 0) {
                addCleanErro("this class should call a parent constructor", nameToken);
            }
        }

        if (isAbstract()) return;

        for (Pointer parent : parents) {
            if (parent.isInterface() || parent.typedef.isAbstract()) {
                for (Method m : parent.typedef.getAllMethods()) {
                    m = m.byGenerics(parent.generics);
                    if (m.isAbstract() && getPointer().getImplMethod(m) == null) {
                        addCleanErro("class should implement all abstract methods", nameToken);
                        return;
                    }
                }
                for (Indexer i : parent.typedef.getAllIndexers()) {
                    i = i.byGenerics(parent.generics);
                    if (i.isAbstract() && getPointer().getImplIndexer(i) == null) {
                        addCleanErro("class should implement all abstract indexers", nameToken);
                        return;
                    }
                }
                for (Property p : parent.typedef.getAllProperties()) {
                    p = p.byGenerics(parent.generics);
                    if (p.isAbstract() && getPointer().getImplProperty(p) == null) {
                        addCleanErro("class should implement all abstract properties", nameToken);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void internalMake() {
        super.internalMake();
        if (this != DataBase.defObject) {
            for (Constructor constructor : this.constructors) {
                if (constructor.hasImplementation() && constructor.getSuperConstructor() == constructor) {
                    addCleanErro("this constructor should call a super constructor", constructor.getToken().byHeader());
                    break;
                }
            }
        }
    }

    @Override
    public void build(CppBuilder cBuilder) {
        Pointer pointer = getPointer();
        boolean hasGenerics = !generics.isEmpty();

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

        //Generic Static Fixer
        if (hasGenerics) {
            cBuilder.ln()
                    .add("class ").specialPath(pointer).add(" {").ln()
                    .add("public :").ln()
                    .add("\tstatic InitKey init;").ln();

            for (Variable variable : variables) {
                if (variable.isStatic()) variable.buildHeader(cBuilder);
            }

            for (Property property : properties) {
                if (property.isStatic()) property.autoBuildHeader(cBuilder);
            }

            cBuilder.add("};").ln();
        }

        //Class Statement
        cBuilder.ln()
                .add(generics)
                .add("class ").add(unicFullName);

        boolean objParent = parents.size() > 0 && parents.get(0).fullEquals(DataBase.defObjectPointer);

        if (parents.size() == 1 && objParent) {
            cBuilder.add(" : public virtual ").path(parents.get(0));
        } else if (parents.size() == 1 && !objParent) {
            cBuilder.add(" : public ").path(parents.get(0));
        } else if (parents.size() == 2 && objParent) {
            cBuilder.add(" : public ").path(parents.get(1));
        } else if (parents.size() >= 2) {
            int sPos = objParent ? 1 : 0;
            for (int i = sPos; i < parents.size(); i++) {
                cBuilder.add(i == sPos ? " : " : ", ").add("public virtual ").path(parents.get(i));
            }
        }
        if (hasGenerics) cBuilder.add(", public ").specialPath(pointer);
        cBuilder.add(" {").ln()
                .add("public:").ln()
                .dynamicFixer(pointer).ln();

        //Init vars
        if (hasGenerics) {
            cBuilder.add("\tusing ").specialPath(pointer).add("::init;").ln();
        } else {
            cBuilder.add("\tstatic InitKey init;").ln();
        }

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

        if (hasGenerics) {
            cBuilder.add("#ifndef S_").add(unicFullName).ln()
                    .add("#define S_").add(unicFullName).ln().ln();

            cBuilder.add("#ifndef H_").add(unicFullName).ln()
                    .add("#define gen_").add(unicFullName).ln()
                    .add("#endif").ln();
        }

        //Dependences
        cBuilder.markSource();

        cBuilder.ln();

        //Static Init and Vars
        if (hasGenerics) {
            cBuilder.add("#ifdef gen_").add(unicFullName).ln();
            cBuilder.add("InitKey ").specialPath(pointer).add("::init;").ln();
            for (Variable variable : variables) {
                if (variable.isStatic()) variable.buildSource(cBuilder);
            }
            for (Property property : properties) {
                if (property.isStatic()) property.autoBuildSource(cBuilder);
            }
            cBuilder.add("#endif").ln();
        } else {
            cBuilder.add("InitKey ").path(pointer).add("::init;").ln();
            for (Variable variable : variables) {
                if (variable.isStatic()) variable.build(cBuilder);
            }
            for (Property property : properties) {
                if (property.isStatic()) property.autoBuild(cBuilder);
            }
        }

        //Variables statement fixer
        for (Variable variable : variables) {
            if (!variable.isStatic()) {
                variable.build(cBuilder);
            } else if (hasGenerics) {
                variable.buildUsing(cBuilder);
            }
        }
        for (Property property : properties) {
            if (!property.isStatic()) {
                property.autoBuild(cBuilder);
            } else if (hasGenerics) {
                property.autoBuildUsing(cBuilder);
            }
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
        if (this != DataBase.defWrapper) {
            for (Variable variable : variables) {
                if (!variable.isStatic()) variable.buildInit(cBuilder);
            }
            for (Property property : properties) {
                if (!property.isStatic()) property.buildInit(cBuilder);
            }
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

        if (destructor != null) {
            destructor.build(cBuilder);
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
        if (hasGenerics) cBuilder.add("#include \"").file(this, ".cpp").add("\"").ln();
        cBuilder.add("#endif").ln();

        //------------//
        //---Source---//
        //------------//
        cBuilder.toSource();
        if (hasGenerics) cBuilder.add("#endif").ln();

        //Dependences
        cBuilder.headerDependences(this);
        cBuilder.sourceDependences(this);
    }

    @Override
    public String toString() {
        return "class : [name:" + getName() + "] [acess:" + getAcess() + "] "
                + (isFinal() ? "(final) " : "")
                + (isAbstract() ? "(abstract)" : "")
                + (generics.pointers.size() > 0 ? "[generics: " + generics.pointers + "]" : "");
    }
}
