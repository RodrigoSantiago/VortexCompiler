package com.vortex.compiler.logic.implementation;

import com.vortex.compiler.content.Parser;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.Acess;
import com.vortex.compiler.logic.LogicToken;
import com.vortex.compiler.logic.Type;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.Header;
import com.vortex.compiler.logic.header.Indexer;
import com.vortex.compiler.logic.header.Method;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.header.variable.GenericStatement;
import com.vortex.compiler.logic.header.variable.Params;
import com.vortex.compiler.logic.implementation.block.Block;
import com.vortex.compiler.logic.implementation.line.Line;
import com.vortex.compiler.logic.space.Workspace;
import com.vortex.compiler.logic.typedef.Typedef;
import com.vortex.compiler.logic.typedef.Pointer;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 02/10/2016
 */
public class Stack extends Block {
    public Header header;
    public GenericStatement generics;

    private Pointer requestReturn;
    private boolean constructorMode;
    private ArrayList<Token> labels;
    private Pointer instanceThis, staticThis;

    private int caseLabelIndex;

    public Stack(Token token, Header header, GenericStatement generics, Params params, Pointer requestReturn, boolean constructorMode) {
        super(null, token);
        this.header = header;
        this.typedef = header.getContainer();
        this.generics = generics;
        this.requestReturn = requestReturn;
        this.constructorMode = constructorMode;

        this.instanceThis = typedef.getPointer();
        this.staticThis = typedef.getPointer().byStatic();

        if (!isStatic()) {
            //This
            fields.add(new Field(Type.LOCALVAR,
                    typedef.nameToken, typedef, "this", typedef.getPointer(),
                    false, false, false,
                    false, true, Acess.PUBLIC, Acess.PUBLIC));

            if (typedef.type == Type.CLASS && typedef.parents.size() > 0) {
                //Super
                fields.add(new Field(Type.LOCALVAR,
                        typedef.parents.get(0).typedef.nameToken, typedef, "super", typedef.parents.get(0),
                        false, false, false,
                        false, true, Acess.PUBLIC, Acess.PUBLIC));
            }
        }

        //Params
        if (params != null) {
            for (int i = 0; i < params.pointers.size(); i++) {
                fields.add(new Field(Type.LOCALVAR,
                        params.nameTokens.get(i), typedef, params.nameTokens.get(i).toString(),
                        params.pointers.get(i),
                        false, false, false,
                        params.finalTokens.get(i) == null, true, Acess.PUBLIC, Acess.PUBLIC));
            }
        }
    }

    @Override
    public void load() {
        Parser.parseCommands(this, token, false);
    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {
        for (Line line : lines) {
            line.build(cBuilder, indent);
        }
    }

    @Override
    public Stack getStack() {
        return this;
    }

    @Override
    public Pointer getRequestReturn() {
        return requestReturn;
    }

    public Pointer getContext() {
        return isStatic() ? staticThis : instanceThis;
    }

    public Workspace getWorkspace() {
        return typedef.workspace;
    }

    public Typedef getTypedef() {
        return typedef;
    }

    public Header getHeader() {
        return header;
    }

    public boolean isConstructor() {
        return constructorMode;
    }

    public boolean isStatic() {
        return header.isStatic();
    }

    public boolean hasAcess(LogicToken logicToken, Token token, Header header) {
        if (Acess.TesteAcess(typedef, header, header.getAcess())) {
            return true;
        } else {
            logicToken.addCleanErro("cannot acess", token);
            return false;
        }
    }

    public boolean hasGetAcess(LogicToken logicToken, Token token, Indexer indexer) {
        if (indexer.hasGet()) {
            if (!Acess.TesteAcess(typedef, indexer, indexer.getGetAcess())) {
                logicToken.addCleanErro("cannot acess", token);
            }
            return true;
        } else {
            logicToken.addCleanErro("cannot get", token);
            return false;
        }
    }

    public boolean hasSetAcess(LogicToken logicToken, Token token, Indexer indexer) {
        if (indexer.hasSet()) {
            if (!Acess.TesteAcess(typedef, indexer, indexer.getSetAcess())) {
                logicToken.addCleanErro("cannot acess", token);
            }
            return true;
        } else {
            logicToken.addCleanErro("cannot set", token);
            return false;
        }
    }

    public boolean hasGetAcess(LogicToken logicToken, Token token, Field field) {
        if (field.isGettable()) {
            if (!Acess.TesteAcess(typedef, field, field.getAcessGet())) {
                logicToken.addCleanErro("cannot acess", token);
            }
            return true;
        } else {
            logicToken.addCleanErro("cannot get", token);
            return false;
        }
    }

    public boolean hasSetAcess(LogicToken logicToken, Token token, Field field) {
        if (field.isSettable()
                && (!field.isFinal() ||
                (field.getContainer() == getContainer() &&
                        isConstructor() &&
                        isStatic() == field.isStatic() &&
                        field.type != Type.LOCALVAR))) {
            if (!Acess.TesteAcess(typedef, field, field.getAcessSet())) {
                logicToken.addCleanErro("cannot acess", token);
            }
            return true;
        } else {
            logicToken.addCleanErro("cannot set", token);
            return false;
        }
    }

    /**
     * Procura um campo no ponteiro atual ou em campos estaticos importados
     *
     * @param name CharSequence
     * @return Field
     */
    @Override
    public Field findField(boolean instance, CharSequence name) {
        String strName = name.toString();
        if ((strName.equals("this") || strName.equals("super")) && !instance) return null;

        for (int i = fields.size() - 1; i >= 0; i--) {
            Field field = fields.get(i);
            if (field.getName().equals(strName)) {
                return field;
            }
        }

        Field field = getContext().findField(name);
        if (field != null && (field.isStatic() || instance || field.type == Type.LOCALVAR)) {
            return field;
        } else {
            return getWorkspace().findField(name);
        }
    }

    /**
     * Procura e criaa um ponteiro com o token  passado, pelo workspace
     *
     * @param pointerToken Token
     * @return Pointer
     */
    public Pointer findPointer(Token pointerToken) {
        return getWorkspace().getPointer(this, pointerToken, generics, isStatic(), false, true);
    }

    /**
     * Procura um metodo no ponteiro atual ou em metodos estaticos importados
     *
     * @param name      CharSequence
     * @param arguments Argumentos
     * @return Method[]
     */
    public Method[] findMethod(boolean instance, CharSequence name, Pointer... arguments) {
        Method[] methods = getContext().findMethod(name, arguments);
        if (methods.length > 0) {
            if (methods.length == 1 && !methods[0].isStatic() && !instance) {
                return new Method[0];
            } else {
                return methods;
            }
        } else {
            return getWorkspace().findMethod(name, arguments);
        }
    }

    /**
     * Procuraa um indexador no ponteiro atual
     *
     * @param arguments Argumentos
     * @return Indexer[]
     */
    public Indexer[] findIndexer(boolean instance, Pointer... arguments) {
        if (!instance) {
            return new Indexer[0];
        } else {
            return getContext().findIndexer(arguments);
        }
    }

    /**
     * Adiciona um label Retorna false , caso o label ja exista
     *
     * @param label Label
     * @return true-false
     */
    public boolean addLabel(Token label) {
        if (label == null) return true;
        if (labels == null) {
            labels = new ArrayList<>();
        }
        if (labels.contains(label)) {
            return false;
        } else {
            labels.add(label);
            return true;
        }
    }

    public int requestIndex() {
        return caseLabelIndex++;
    }

    public Token getLabelTarget(CharSequence labelname) {
        for (Token label : labels) {
            if (label.compare(labelname)) {
                return label;
            }
        }
        return null;
    }
}