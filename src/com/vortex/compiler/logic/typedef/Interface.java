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
public class Interface extends Typedef {
    public static final String keyword = "interface";

    public Interface(Workspace workspace, Token token, Token[] tokens) {
        super(workspace, token, tokens, keyword, INTERFACE, true, false, false);
    }

    @Override
    public void load() {
        if (loaded) return;
        super.load();

        for (int i = 0; i < parents.size(); i++) {
            Typedef parent = parents.get(i).typedef;
            if (parent.type != INTERFACE) {
                addCleanErro("interfaces should only inherit interfaces", parentsTokens.get(i));
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

        parents.add(0, DataBase.defObjectPointer);
        parentsTokens.add(0, nameToken);
    }

    @Override
    public void internalLoad() {
        internalRead(true, false, false, false);

        for (Method method : methods) {
            if (method.isStatic()) {
                if (method.isAbstract()) {
                    addCleanErro("invalid modifier cobination", method.staticToken);
                    addCleanErro("invalid modifier cobination", method.modifierToken);
                    method.fixAbstract(false);
                }
                if (!method.hasImplementation()) {
                    addCleanErro("methods should implement", method.contentToken);
                }
            } else {
                if (method.isFinal()) {
                    addCleanErro("interfaces should not have final methods", method.modifierToken);
                } else if (method.hasImplementation()) {
                    addCleanErro("abstract methods should not implement", method.contentToken);
                } else if (method.getAcess() != Acess.DEFAULT && method.getAcess() != Acess.PUBLIC) {
                    addCleanErro("interfaces should have only public methods", method.acessToken);
                }
                if (method.isAbstract()) {
                    addWarning("interfaces methods are always abstract", method.modifierToken);
                }
                if (method.getAcess() == Acess.PUBLIC) {
                    addWarning("interfaces methods are always public", method.acessToken);
                }
                method.fixFinal(false);
                method.fixAbstract(true);
                method.fixAcess(Acess.PUBLIC);
            }
        }

        for (Property property : properties) {
            if (property.isStatic()) {
                if (property.isAbstract()) {
                    addCleanErro("invalid modifier cobination", property.staticToken);
                    addCleanErro("invalid modifier cobination", property.modifierToken);
                    property.fixAbstract(false);
                }
                if (!property.canBeNonAbstract()) {
                    addCleanErro("properties should implement", property.contentToken);
                }
            } else {
                if (property.isFinal()) {
                    addCleanErro("interfaces should not have final properties", property.modifierToken);
                } else if (!property.canBeAbstract()) {
                    addCleanErro("abstract properties should not implement", property.contentToken);
                } else if (property.getAcess() == Acess.DEFAULT || property.getAcess() == Acess.PUBLIC) {
                    if (property.hasGet() &&
                            property.getGetAcess() != Acess.DEFAULT && property.getGetAcess() != Acess.PUBLIC) {
                        addCleanErro("interface properties 'get' should be public", property.contentTokenGet);
                    }
                    if (property.hasSet() &&
                            property.getSetAcess() != Acess.DEFAULT && property.getSetAcess() != Acess.PUBLIC) {
                        addCleanErro("interface properties 'set' should be public", property.contentTokenSet);
                    }
                } else {
                    addErro("interfaces should have only public properties", property.acessToken);
                }
                if (property.isAbstract()) {
                    addWarning("interfaces properties are always abstract", property.modifierToken);
                }
                if (property.getAcess() == Acess.PUBLIC &&
                        property.getSetAcess() == Acess.PUBLIC && property.getGetAcess() == Acess.PUBLIC) {
                    addWarning("interfaces properties are always public", property.acessToken);
                }
                property.fixFinal(false);
                property.fixAbstract(true);
                property.fixAcess(Acess.PUBLIC);
            }
        }

        for (Indexer indexer : indexers){
            if (indexer.isFinal()) {
                addCleanErro("interfaces should not have final indexers", indexer.modifierToken);
            } else if (!indexer.canBeAbstract()) {
                addCleanErro("abstract indexers should not implement", indexer.contentToken);
            } else if (indexer.getAcess() == Acess.DEFAULT || indexer.getAcess() == Acess.PUBLIC) {
                if (indexer.hasGet() &&
                        indexer.getGetAcess() != Acess.DEFAULT && indexer.getGetAcess() != Acess.PUBLIC) {
                    addCleanErro("interface indexers 'get' should be public", indexer.contentTokenGet);
                }
                if (indexer.hasSet() &&
                        indexer.getSetAcess() != Acess.DEFAULT && indexer.getSetAcess() != Acess.PUBLIC) {
                    addCleanErro("interface indexers 'set' should be public", indexer.contentTokenSet);
                }
            } else {
                addErro("interfaces should have only public indexers", indexer.acessToken);
            }
            if (indexer.isAbstract()) {
                addWarning("interfaces indexers are always abstract", indexer.modifierToken);
            }
            if (indexer.getAcess() == Acess.PUBLIC &&
                    indexer.getSetAcess() == Acess.PUBLIC && indexer.getGetAcess() == Acess.PUBLIC) {
                addWarning("interfaces indexers are always public", indexer.acessToken);
            }
            indexer.fixFinal(false);
            indexer.fixAbstract(true);
            indexer.fixAcess(Acess.PUBLIC);
        }
    }

    @Override
    public void crossLoad() {
        if (verifyMethods()) if(verifyProperties()) verifyIndexers();
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
                variable.buildHeader(cBuilder);
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

        boolean objParent = parents.get(0).fullEquals(DataBase.defObjectPointer);

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
                variable.buildSource(cBuilder);
            }
            for (Property property : properties) {
                if (property.isStatic()) property.autoBuildSource(cBuilder);
            }
            cBuilder.add("#endif").ln();
        } else {
            cBuilder.add("InitKey ").path(pointer).add("::init;").ln();
            for (Variable variable : variables) {
                variable.build(cBuilder);
            }
            for (Property property : properties) {
                if (property.isStatic()) property.autoBuild(cBuilder);
            }
        }

        //Variables statement/fixer
        if (hasGenerics) {
            for (Variable variable : variables) {
                variable.buildUsing(cBuilder);
            }
            for (Property property : properties) {
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

        //---------------//
        //-Source-Header-//
        //---------------//
        //Class Headers
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
        return "interface : [name:" + getName() + "] [acess:" + getAcess() + "]"
                + (generics.pointers.size() > 0 ? " [generics:" + generics + "]" : "");
    }
}
