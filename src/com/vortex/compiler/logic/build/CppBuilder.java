package com.vortex.compiler.logic.build;

import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.*;
import com.vortex.compiler.logic.header.*;
import com.vortex.compiler.logic.header.variable.*;
import com.vortex.compiler.logic.implementation.line.*;
import com.vortex.compiler.logic.implementation.lineblock.*;
import com.vortex.compiler.logic.typedef.*;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 02/11/2016
 */
public class CppBuilder {

    private StringBuilder tBuilder, sBuilder, hBuilder;
    private int headerPos, sourcePos;

    private ArrayList<Typedef> tDependences, hDependences, sDependences, dDependences;
    private ArrayList<BlockData> blockBegin;
    private HashMap<Integer, ArrayList<Pointer>> blockVars;

    public CppBuilder() {
        hDependences = new ArrayList<>();
        sDependences = new ArrayList<>();
        dDependences = new ArrayList<>();
        sBuilder = new StringBuilder();
        hBuilder = new StringBuilder();
        blockBegin = new ArrayList<>();
        blockVars = new HashMap<>();
        toHeader();
    }

    public CppBuilder(CppBuilder cppBuilder) {
        hDependences = cppBuilder.hDependences;
        sDependences = cppBuilder.sDependences;
        dDependences = cppBuilder.dDependences;
        sBuilder = new StringBuilder();
        hBuilder = new StringBuilder();
        blockBegin = new ArrayList<>();
        blockVars = new HashMap<>();
        toHeader();
    }

    public void toHeader() {
        tBuilder = hBuilder;
        tDependences = hDependences;
    }

    public void toSource() {
        tBuilder = sBuilder;
        tDependences = sDependences;
    }

    public void reset() {
        hBuilder.setLength(0);
        sBuilder.setLength(0);
        hDependences.clear();
        sDependences.clear();
        dDependences.clear();
    }

    public String getHeader() {
        return hBuilder.toString();
    }

    public String getSource() {
        return sBuilder.toString();
    }

    public void markHeader() {
        headerPos = hBuilder.length();
    }

    public void markSource() {
        sourcePos = sBuilder.length();
    }

    /**
     * Adiciona, se permitido, este ponteiro nas dependencias diretas
     *
     * @param pointer Ponteiro
     */
    public void directDependence(Pointer pointer) {
        if (pointer.isStruct() && !dDependences.contains(pointer.typedef)) {
            dDependences.add(pointer.typedef);
            for (Pointer gen : pointer.generics) {
                directDependence(gen);
            }
        }
    }

    /**
     * Escreve todas as dependencias no 'Header'
     *
     * @param container Typedef atual
     */
    public CppBuilder headerDependences(Typedef container) {
        CppBuilder innerBuilder = new CppBuilder(this);
        innerBuilder.toHeader();

        //Direct Dependences
        dDependences.remove(container);
        for (Typedef typedef : dDependences) {
            if (!typedef.isLangImplement()) {
                sDependences.remove(typedef);
                hDependences.remove(typedef);
                innerBuilder.add("#include \"").file(typedef, ".h").add("\"").ln();
            }
        }

        //Header Dependences
        hDependences.remove(container);
        for (Typedef typedef : hDependences) {
            if (!typedef.isLangImplement()) {
                if (container.isInstanceOf(typedef)) {
                    sDependences.remove(typedef);
                    innerBuilder.add("#include \"").file(typedef, ".h").add("\"").ln();
                } else {
                    innerBuilder.add(typedef.generics).add("class ").add(typedef.unicFullName).add(";").ln();
                }
            }
        }
        hBuilder.insert(headerPos, innerBuilder.getHeader());
        return this;
    }

    /**
     * Escreve todas as dependencias na 'Source'
     *
     * @param container Typedef atual
     */
    public CppBuilder sourceDependences(Typedef container) {
        CppBuilder innerBuilder = new CppBuilder(this);
        innerBuilder.toSource();

        if (!container.isFake())
            innerBuilder.add("#include \"").file(container, ".h").add("\"").ln();

        //Source Dependences
        sDependences.remove(container);
        for (Typedef typedef : sDependences) {
            if (!typedef.isLangImplement()) {
                innerBuilder.add("#include \"").file(typedef, ".h").add("\"").ln();
            }
        }
        sBuilder.insert(sourcePos, innerBuilder.getSource());
        return this;
    }

    /**
     * Adiciona uma dependencia
     *
     * @param typedef Dependencia
     */
    public void addDependence(Typedef typedef) {
        if  (!tDependences.contains(typedef)) tDependences.add(typedef);
    }


    /**
     * Adiciona tabulações (indentação)
     *
     * @param indent Numerp de tabulações
     */
    public CppBuilder idt(int indent) {
        for (; indent > 0; indent -= 1) add("\t");
        return this;
    }

    /**
     * Adiciona uma quebra de linha (\n)
     */
    public CppBuilder ln() {
        return add("\n");
    }

    /**
     * Marca como começo de bloco, adicionando uma chave ('{')
     *
     * @param indent Identação (numeros superiores a 0 causa \n)
     */
    public CppBuilder begin(int indent) {
        assert tBuilder == sBuilder : "out of source";

        add("{");

        if (indent > 0) ln();

        blockBegin.add(new BlockData(sBuilder.length(), indent));
        blockVars.put(sBuilder.length(), new ArrayList<>());
        return this;
    }

    /**
     * Marca o fim do bloco e insere variaveis de stack, adicionando uma string logo apos a inserção  da chave ('}')
     */
    public CppBuilder end(String eStr) {
        assert tBuilder == sBuilder : "out of source";

        BlockData data = blockBegin.remove(blockBegin.size() - 1);
        ArrayList<Pointer> vars = blockVars.remove(data.pos);
        if (vars.size() > 0) {
            CppBuilder iBuilder = new CppBuilder(this);
            iBuilder.toSource();
            for (int i = 0; i < vars.size(); i++) {
                iBuilder.idt(data.indent)
                        .path(vars.get(i)).add(" t_").add(blockBegin.size()).add("_").add(i).add(";").ln();
            }
            tBuilder.insert(data.pos, iBuilder.getSource());
        }

        return add("}").add(eStr);
    }

    public CppBuilder end() {
        return end("\n");
    }

    /**
     * Cria uma instancia do tipo do ponteiro selecionado. Se o ponteiro  for do tipo stack ele irá adicionar a lista
     *
     * @param pointer Pointer com tipo da instancia
     * @param stack Modificação de stack/heap
     */
    public CppBuilder constructor(Pointer pointer, boolean stack) {
        if (pointer.isStruct()) {
            path(pointer);
        } else if (stack) {
            if (blockBegin.size() > 0) {
                ArrayList<Pointer> vars = blockVars.get(blockBegin.get(blockBegin.size() - 1).pos);
                vars.add(pointer);
                add("t_").add(blockBegin.size() - 1).add("_").add(vars.size() - 1);
            } else {
                path(pointer).add("()");
            }
            add(".build");
        } else {
            add("(new ").path(pointer).add("())->build");
        }
        return this;
    }

    public CppBuilder nameField(CharSequence name) {
        return add("f_").add(name.toString());
    }

    public CppBuilder nameMethod(CharSequence name) {
        return add("m_").add(name.toString());
    }

    public CppBuilder nameProperty(CharSequence name) {
        return add("p_").add(name.toString());
    }

    public CppBuilder namePropertyGet(CharSequence name) {
        return add("get").add(name.toString());
    }

    public CppBuilder namePropertySet(CharSequence name) {
        return add("set").add(name.toString());
    }

    public CppBuilder nameBreak(CharSequence name) {
        return add("break").add(name.toString());
    }

    public CppBuilder nameContinue(CharSequence name) {
        return add("continue").add(name.toString());
    }

    public CppBuilder nameCase(int caseLabel) {
        return add("caselabel").add(caseLabel);
    }

    public CppBuilder nameGeneric(CharSequence name) {
        return add("_").add(name.toString());
    }

    public CppBuilder namePointer(Pointer pointer) {
        // _ -> _u ,  :: -> __
        add(pointer.typedef.unicFullName);

        if (pointer.generics.length > 0) {
            add("_s");
            for (int i = 0; i < pointer.generics.length; i++) {
                if (i != 0) add("_d");
                namePointer(pointer.generics[i]);
            }
            add("_b");
        }
        return this;
    }


    /**
     * Adiciona o nome de arquivo relativo com a extensão provida
     *
     * @param typedef Typedef que dará origem ao nome
     * @param ext Extensão
     */
    public CppBuilder file(Typedef typedef, String ext) {
        if (typedef.type == Type.STRUCT) {
            add("struct_");
        } else if (typedef.type == Type.ENUM) {
            add("enum_");
        } else if (typedef.type == Type.INTERFACE) {
            add("interface_");
        } else if (typedef.type == Type.CLASS) {
            add("class_");
        }
        return add(typedef.unicFullName.toLowerCase()).add(ext.toLowerCase());
    }

    /**
     * Escreve um Typedef, não expressa se é ou não ponteiro. Esta função também adiciona o typedef na dependencia atual
     *
     * @param pointer Ponteiro
     */
    public CppBuilder path(Pointer pointer) {
        if (pointer == Pointer.nullPointer) return add("nullptr");
        if (pointer == Pointer.voidPointer) return add("void");
        if (pointer.typedef == DataBase.defFunction) {
            add("default__function<");
            add(pointer.generics[0]).add("(");
            for (int i = 1; i < pointer.generics.length; i++) {
                if (i != 1) add(", ");
                add(pointer.generics[i]);
            }
            return add(")>");
        }

        if (pointer.genName == null) {
            add(pointer.typedef.unicFullName);
        } else {
            nameGeneric(pointer.genName);
        }

        if (pointer.generics.length > 0) {
            add("<");
            for (int i = 0; i < pointer.generics.length; i++) {
                if (i != 0) add(", ");
                add(pointer.generics[i]);
            }
            add(">");
        }

        if (!tDependences.contains(pointer.typedef)) tDependences.add(pointer.typedef);
        return this;
    }

    /**
     * Escreve um Typedef, não expressa se é ou não ponteiro. Neste caso, escreve o typedef de modo especial, para fixar
     * o erro de classes com genericos(templates)
     *
     * @param pointer Ponteiro
     */
    public CppBuilder specialPath(Pointer pointer) {
        add("s_").add(pointer.typedef.unicFullName);

        if (!tDependences.contains(pointer.typedef)) tDependences.add(pointer.typedef);
        return this;
    }

    public CppBuilder specialPath(boolean hasGenerics, Pointer pointer) {
        if (hasGenerics) {
            return specialPath(pointer);
        } else {
            return path(pointer);
        }
    }

    /**
     * Coloca todos os parâmetros em ordem (sub chamada de metodo/construtor)
     *
     * @param params Parâmetros
     */
    public CppBuilder args(Params params) {
        for (int i = 0; i < params.pointers.size(); i++) {
            if (i != 0) add(", ");
            nameField(params.nameTokens.get(i));
        }
        return this;
    }

    /**
     * Expressa um tipo de lambda unico para fixar erros de operadores de modificação
     *
     * @param opOverload Função de operador
     */
    public CppBuilder type(OpOverload opOverload, Pointer... extraPointer) {
        Operator op = Operator.fromToken(opOverload.getOperator());
        if (opOverload.getContainer().isLangImplement()) {
            if (op.isUnary()) {
                return add("&Operators::").add(op).add("<").add(extraPointer[0]).add(">");
            } else {
                return add("&Operators::").add(op).add("<").add(extraPointer[0]).add(", ").add(extraPointer[1]).add(">");
            }
        } else {
            //float(*)(float, float)
            return add("static_cast<").add(opOverload.getType()).add("(*)(").add(opOverload.params).add(")>(")
                    .path(opOverload.getContainer().getPointer()).add("::").add(op).add(")");
        }
    }

    /**
     * Escreve o valor padrão  do tipo do ponteiro
     *
     * @param pointer Ponteiro
     */
    public CppBuilder value(Pointer pointer) {
        if (pointer.isStruct()) {
            if (pointer.isLang()) add("0");
            else if (pointer.typedef == DataBase.defFunction) path(pointer).add("()");
            else path(pointer).add("::defValue");
        } else {
            add("nullptr");
        }
        return this;
    }

    /**
     * Adiciona String
     *
     * @param string String
     */
    public CppBuilder add(String string) {
        tBuilder.append(string);
        return this;
    }

    /**
     * Adiciona número
     *
     * @param number Número
     */
    public CppBuilder add(int number) {
        tBuilder.append(number);
        return this;
    }

    /**
     * Adiciona ponteiro (com todas as referencias)
     *
     * @param pointer Ponteiro
     */
    public CppBuilder add(Pointer pointer) {
        path(pointer);

        if (!pointer.isDefault() && !pointer.isStruct() && pointer.genName == null) {
            add("*");
        }
        return this;
    }

    /**
     * Cria a linha de declaração de parâmetros
     *
     * @param params Parâmetros
     */
    public CppBuilder add(Params params) {
        for (int i = 0; i < params.pointers.size(); i++) {
            if (i != 0) add(", ");
            add(params.pointers.get(i)).add(" ").nameField(params.nameTokens.get(i));
        }
        return this;
    }

    /**
     * Cria a linha de declaração de parâmetros com um valor extra (value)
     *
     * @param params Parâmetros
     * @param extraValue Tipo do valor extra
     */
    public CppBuilder add(Params params, Pointer extraValue) {
        for (int i = 0; i < params.pointers.size(); i++) {
            if (i != 0) add(", ");
            add(params.pointers.get(i)).add(" ").nameField(params.nameTokens.get(i));
        }
        if (!params.isEmpty()) add(", ");
        add(extraValue).add(" ").nameField("value");

        return this;
    }

    /**
     * Adiciona linha de argumentos
     *
     * @param args Argumentos
     */
    public CppBuilder add(Params params, ArrayList<LineBlock> args) {
        if (params != null && params.hasVarArgs() &&
                args.get(args.size() - 1).getReturnType().getDifference(params.pointers.get(params.size() - 1)) == -1) {
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) add(", ");
                if (i == params.size() - 1) {
                    constructor(params.pointers.get(params.size() - 1), true).add("({");
                }
                add(args.get(i));
                if (i == args.size() - 1 && i >= params.size() - 1) {
                    add("})");
                }
            }
            if (args.size() < params.size()) {
                if (args.size() > 0) add(", ");
                constructor(params.pointers.get(params.size() - 1), true).add("()");
            }
        } else {
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) add(", ");
                add(args.get(i));
            }
        }
        return this;
    }

    /**
     * Adiciona linha de argumentos (usado especialmente em templates para metodos)
     *
     * @param args Argumentos
     */
    public CppBuilder add(Pointer[] args) {
        for (int i = 0; i < args.length; i++) {
            if (i > 0) add(", ");
            add(args[i]);
        }
        return this;
    }

    /**
     * Adiciona declaração de genéricos
     *
     * @param genericStatement Genéricos
     */
    public CppBuilder add(GenericStatement genericStatement) {
        if (!genericStatement.isEmpty()) {
            add("template<");
            for (int i = 0; i < genericStatement.pointers.size(); i++) {
                if (i != 0) add(", ");
                add("typename ").nameGeneric(genericStatement.nameTokens.get(i));
            }
            add(">").ln();
        }
        return this;
    }

    /**
     * Adiciona operador
     *
     * @param operator Operador
     */
    public CppBuilder add(Operator operator) {
        add(operator.toString());
        return this;
    }

    /**
     * Adiciona linha de comando (invoca a propria construção da linha)
     *
     * @param line Linha
     */
    public CppBuilder add(Line line) {
        line.build(this, 0);
        return this;
    }

    /**
     * Converte uma String em valores fáceis de compilação
     *
     * @param textValue Valor em texto
     */
    public CppBuilder convert(String textValue) {
        for (int i = 0; i < textValue.length(); i++) {
            char chr = textValue.charAt(i);
            if ('\\' == chr) {
                add("\\\\");
            } else if ('\'' == chr) {
                add("\\\'");
            } else if ('\"' == chr) {
                add("\\\"");
            } else if ('\b' == chr) {
                add("\\b");
            } else if ('\t' == chr) {
                add("\\t");
            } else if ('\n' == chr) {
                add("\\n");
            } else if ('\r' == chr) {
                add("\\r");
            } else if ('\f' == chr) {
                add("\\f");
            } else if (chr >= 32 && chr <= 127) {
                tBuilder.append(chr);
            } else {
                add("\\u").add(String.format("%04X", chr & 0x0FFFF));
            }
        }
        return this;
    }

    public CppBuilder dynamicFixer(Pointer pointer) {
        ArrayList<Pointer> allParents = new ArrayList<>();
        parentAdd(allParents, pointer);
        ln().add("\tASSIGN(").add(allParents.size());
        for (Pointer parent : allParents) {
            add(", ").path(parent);
        }
        return add(")").ln();
    }

    public CppBuilder cast(Pointer castPointer, Line block) {
        if (castPointer.isObject() && castPointer.fullEquals(block.getReturnTrueType())) {
            add("i_cast<").add(castPointer).add(">(").add(block).add(")");
        } else if (block.getReturnTrueType().isLang() && castPointer.isLang()) {    //long -> int
            add("s_cast<").add(castPointer).add(">(").add(block).add(")");
        } else if (block.getReturnTrueType().isLang() && !castPointer.isLang()) {   //int -> Object
            Pointer wrapper = block.getReturnTrueType().getWrapper();
            if (castPointer.fullEquals(wrapper)) {
                constructor(wrapper, true).add("(").add(block).add(")");
            } else {
                add("d_cast<").path(castPointer).add(">(").constructor(wrapper, true).add("(").add(block).add("))");
            }
        } else if (block.getReturnTrueType().isStruct()) {                          //Struct -> Any
            Pointer wrapper = block.getReturnTrueType().getWrapper();
            if (wrapper.isInstanceOf(castPointer)) {
                constructor(wrapper, true).add("(").add(block).add(")");
            } else {
                path(block.getReturnTrueType()).add("::castTo").namePointer(castPointer).add("(").add(block).add(")");
            }
        } else if (castPointer.isStruct()) {                                        //Class -> Struct
            Pointer wrapper = castPointer.getWrapper();
            if (block.getReturnTrueType().fullEquals(wrapper)) {
                add(block);
            } else {
                if (block.getReturnTrueType() == Pointer.nullPointer) {
                    add("nullptr");
                } else {
                    add("d_cast<").path(wrapper).add(">(").add(block).add(")");
                }
            }
            if (wrapper.typedef.fields.get("value").type == Type.PROPERTY) {
                add("->").namePropertyGet("value").add("()");
            } else {
                add("->").nameField("value");
            }
        } else {                                                                    //Class -> Class
            if (block.getReturnTrueType() == Pointer.nullPointer) {
                add("nullptr");
            } else {
                add("d_cast<").path(castPointer).add(">(").add(block).add(")");
            }
        }

        return this;
    }

    private void parentAdd(ArrayList<Pointer> list, Pointer pointer) {
        if (!list.contains(pointer)) {
            list.add(pointer);
        }
        for (Pointer parent : pointer.typedef.parents) {
            parentAdd(list, parent.byGenerics(pointer.generics));
        }
    }
    class BlockData {
        int pos;
        int indent;

        BlockData(int pos, int indent) {
            this.pos = pos;
            this.indent = indent;
        }
    }
}
