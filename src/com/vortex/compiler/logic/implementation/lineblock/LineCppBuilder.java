package com.vortex.compiler.logic.implementation.lineblock;

import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.Operator;
import com.vortex.compiler.logic.Type;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.Constructor;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.header.variable.Params;
import com.vortex.compiler.logic.typedef.Pointer;
import com.vortex.compiler.logic.typedef.Typedef;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 30/10/2016
 */
public class LineCppBuilder {
    private CppBuilder cBuilder;
    private Typedef typedef;
    private int blockIdent;

    LineCppBuilder(CppBuilder cBuilder, LineBlock lineBlock, int blockIdent) {
        this.cBuilder = cBuilder;
        this.cBuilder.toSource();
        this.typedef = lineBlock.getContainer();
        this.blockIdent = blockIdent;
        if (lineBlock.getReturnType().isStruct()) {
            cBuilder.addDependence(lineBlock.getReturnType().typedef);
        }
        build(lineBlock);
    }

    private void build(LineBlock lineBlock) {
        if (lineBlock.autoCastPointer != null && !lineBlock.autoCastConsumed) {
            lineBlock.autoCastConsumed = true;
            cBuilder.cast(lineBlock.autoCastPointer, lineBlock);
        } else if (lineBlock.isInner()) {
            cBuilder.add("(").add(lineBlock.lastCall.innerCall()).add(")");
        } else if (lineBlock.isTypedefExpression()) {
            cBuilder.add(lineBlock.getReturnType());
        } else if (lineBlock.isLambda()) {
            lineBlock.getLastCall().lambdaBlock().build(cBuilder, blockIdent + 1);
        } else if (lineBlock.isOperation()) {
            if (lineBlock.isTernaryOperation()) {           //ternary
                cBuilder.add((LineBlock) lineBlock.calls.get(0)).add(" ? ")
                        .add((LineBlock) lineBlock.calls.get(2)).add(" : ")
                        .add((LineBlock) lineBlock.calls.get(4));
            } else if (lineBlock.isCastOperation()) {       //cast
                cBuilder.cast(lineBlock.calls.get(0).getCall().getReturnType(), lineBlock.calls.get(1).getBlock());
            } else {
                if (lineBlock.isIncrementOperation()) {     //increment
                    buildIncrementeOperation(lineBlock);
                } else if (lineBlock.isUnaryOperation()) {  //unary
                    buildUnaryOperation(lineBlock);
                } else if (lineBlock.isSetOperation()) {    //set
                    buildSetOperation(lineBlock);
                } else {                                    //commun
                    buildOperation(lineBlock);
                }
            }
        } else if (lineBlock.isSequence()) {
            buildPath(lineBlock);
        }
    }

    private void buildIncrementeOperation(LineBlock lineBlock) {
        LineBlock block = lineBlock.calls.get(lineBlock.calls.get(0).isBlock() ? 0 : 1).getBlock();
        LineCall precedded = lineBlock.calls.get(0).getCall();
        LineCall sucedded = lineBlock.calls.get(1).getCall();

        if (precedded != null) {    //++a
            Operator operator = precedded.getOperator();
            if (block.isIndexer() || block.isProperty() || block.isStaticField()) {
                cBuilder.add("opPUnary<").add(block.getReturnType()).add(">(")
                        .type(precedded.operatorCall(), block.getReturnType()).add(", ").add(block).add(")");
            } else {
                if (block.getReturnType().isLang()) {
                    cBuilder.add(operator == Operator.increment ? "++" : "--").add(block);
                } else {
                    cBuilder.add("opcPUnary<").add(block.getReturnType()).add(">(")
                            .type(precedded.operatorCall(), block.getReturnType()).add(", ").add(block).add(")");
                }
            }
        } else {                    //a++
            Operator operator = sucedded.getOperator();
            if (block.isIndexer() || block.isProperty() || block.isStaticField()) {
                cBuilder.add("opSUnary<").add(block.getReturnType()).add(">(")
                        .type(sucedded.operatorCall(), block.getReturnType()).add(", ").add(block).add(")");
            } else {
                if (block.getReturnType().isLang()) {
                    cBuilder.add(block).add(operator == Operator.increment ? "++" : "--");
                } else {
                    cBuilder.add("opcSUnary<").add(block.getReturnType()).add(">(")
                            .type(sucedded.operatorCall(), block.getReturnType()).add(", ").add(block).add(")");
                }
            }
        }
    }

    private void buildUnaryOperation(LineBlock lineBlock) {
        LineCall call = lineBlock.calls.get(0).getCall();
        LineBlock block = lineBlock.calls.get(1).getBlock();
        if (block.getReturnType().isLang()) {
            cBuilder.add(call.getOperator().value).add(block);
        } else {
            cBuilder.path(block.getReturnType()).add("::").add(call.getOperator()).add("(").add(block).add(")");
        }
    }

    private void buildSetOperation(LineBlock lineBlock) {
        LineBlock firstBlock = lineBlock.calls.get(0).getBlock();
        LineCall operatorLine = lineBlock.calls.get(1).getCall();
        LineBlock secondBlock = lineBlock.calls.get(2).getBlock();

        Operator operator = operatorLine.getOperator().getInnerOperator();
        if (operator == Operator.set) {     // a = b
            if (firstBlock.isIndexer() || firstBlock.isProperty() || firstBlock.isStaticField()) {
                cBuilder.add(firstBlock).add(secondBlock).add(")");
            } else {
                cBuilder.add(firstBlock).add(" = ").add(secondBlock);
            }
        } else {                            // a += b
            if (firstBlock.isIndexer() || firstBlock.isProperty() || firstBlock.isStaticField()) {
                //opSet(operation, a, (cast) b)
                cBuilder.add("opSet<").add(firstBlock.getReturnType()).add(", ").add(secondBlock.getReturnType()).add(">(")
                        .type(operatorLine.operatorCall(),firstBlock.getReturnType(), secondBlock.getReturnType())
                        .add(", ").add(firstBlock).add(", ").add(secondBlock).add(")");
            } else {
                if (firstBlock.getReturnType().isLang()) {
                    if (operator == Operator.div && firstBlock.getReturnType().isInteger() &&
                            secondBlock.getReturnType().isInteger()) {
                        cBuilder.add("Operators").add("::l").add(Operator.div)
                                .add("<").add(firstBlock.getReturnType()).add(", ").add(secondBlock.getReturnType()).add(">")
                                .add("(").add(firstBlock).add(", ").add(secondBlock).add(")");
                    } else {
                        cBuilder.add(firstBlock).add(" ").add(operatorLine.getOperator().value).add(" ").add(secondBlock);
                    }
                } else {
                    cBuilder.add("opcSet<").add(firstBlock.getReturnType()).add(", ").add(secondBlock.getReturnType()).add(">(")
                            .type(operatorLine.operatorCall(), firstBlock.getReturnType(), secondBlock.getReturnType())
                            .add(", ").add(firstBlock).add(", ").add(secondBlock).add(")");
                }
            }
        }
    }

    private void buildOperation(LineBlock lineBlock) {
        LineBlock firstBlock = lineBlock.calls.get(0).getBlock();
        LineCall operatorLine = lineBlock.calls.get(1).getCall();
        LineBlock secondBlock = lineBlock.calls.get(2).getBlock();

        Operator operator = operatorLine.getOperator();
        Pointer opOrigem = operatorLine.operatorCall() != null ? operatorLine.operatorCall().getContainer().getPointer() : null;
        if (opOrigem == null || opOrigem.isLang()) {
            if (operator == Operator.div && firstBlock.getReturnType().isInteger() && secondBlock.getReturnType().isInteger()) {
                cBuilder.add("Operators").add("::").add(operator)
                        .add("<").add(firstBlock.getReturnType()).add(", ").add(secondBlock.getReturnType()).add(">")
                        .add("(").add(firstBlock).add(", ").add(secondBlock).add(")");
            } else if (operator.isInstanceLogical()) {
                if (firstBlock.getReturnTrueType() == Pointer.nullPointer) {
                    cBuilder.add(operator == Operator.is ? "false" : "true");
                } else {
                    cBuilder.add("Operators::").add(operator).add("<").add(secondBlock).add(">(").add(firstBlock).add(")");
                }
            } else {
                cBuilder.add(firstBlock).add(" ").add(operator.value).add(" ").add(secondBlock);
            }
        } else {
            cBuilder.path(opOrigem).add("::").add(operator).add("(").add(firstBlock).add(", ").add(secondBlock).add(")");
        }
    }

    private void buildPath(LineBlock lineBlock) {
        LineCall prevCall = null;

        int size = lineBlock.calls.size();
        for (int i = 0; i < size; i++) {
            LineCall call = lineBlock.calls.get(i).getCall();
            buildCall(prevCall, prevCall = call);
        }
    }

    private void buildCall(LineCall prevCall, LineCall call) {
        if (call.isJustADot()) {                //Dot
            if (prevCall.isStatic() || (prevCall.isField() && prevCall.fieldCall().getName().equals("super"))) {
                cBuilder.add("::");
            } else if (prevCall.getReturnType().isStruct()){
                cBuilder.add(".");
            } else {
                cBuilder.add("->");
            }
        } else if (call.isValue()) {            //Value
            if (call.getReturnType().equals(DataBase.defStringPointer)) {           //String
                cBuilder.path(DataBase.defStringPointer).add("(\"").convert(call.textValue()).add("\")");
            } else if (call.getReturnType().equals(Pointer.nullPointer)) {          //Null
                cBuilder.add("nullptr");
            } else {                                                                //Number, Bool
                if (call.getReturnType().equals(DataBase.defDoublePointer)) {
                    cBuilder.add(call.textValue()).add("L");
                } else if (call.getReturnType().equals(DataBase.defFloatPointer)) {
                    cBuilder.add(call.textValue()).add("F");
                } else if (call.getReturnType().equals(DataBase.defLongPointer)) {
                    cBuilder.add(call.textValue()).add("L");
                } else {
                    cBuilder.add(call.textValue());
                }
            }
        } else if (call.isInner()) {            //Inner
            cBuilder.add("(").add(call.innerCall()).add(")");
        } else if (call.isStatic()) {           //Static reference
            cBuilder.path(call.staticCall());
        } else if (call.isField()) {            //Field
            Field field = call.fieldCall();

            if (field.getName().equals("this")) {
                //This
                cBuilder.add(field.getType().isStruct() ? "(*this)" : "this");
            } else if (field.getName().equals("super")) {
                //Super
                cBuilder.path(field.getType());
            } else if (field.type == Type.PROPERTY || field.isStatic()) {
                //Property/Static
                if (prevCall == null && field.isStatic() && field.getContainer() != typedef) {
                    cBuilder.path(field.getContainer().getPointer()).add("::");
                }
                if (call.getMod && call.setMod) {
                    cBuilder.nameProperty(field.getName()).add("()");
                } else if (call.getMod) {
                    cBuilder.namePropertyGet(field.getName()).add("()");
                } else {
                    cBuilder.namePropertySet(field.getName()).add("(");
                }
            } else {
                //Instance

                //Correção do bug em C++(classes template nao enchergam variaveis de instancia de parents)
                if (prevCall == null && !typedef.generics.isEmpty() && field.getContainer() != typedef) {
                    cBuilder.add("this->");
                }
                cBuilder.nameField(field.getName());
            }
        } else if (call.isIndexer()) {          //Indexer
            if (prevCall != null) {
                if (prevCall.isStatic() || (prevCall.isField() && prevCall.fieldCall().getName().equals("super"))) {
                    cBuilder.add("::");
                } else if (prevCall.getReturnType().isStruct()){
                    cBuilder.add(".");
                } else {
                    cBuilder.add("->");
                }
            }
            if (call.getMod && call.setMod) {
                cBuilder.add("indexGet(").add(call.indexerCall().params, call.args()).add(")");
            } else if (call.getMod) {
                cBuilder.add("indexG(").add(call.indexerCall().params, call.args()).add(")");
            } else {
                cBuilder.add("indexS(").add(call.indexerCall().params, call.args());
                if (call.args().size() > 0) cBuilder.add(", ");
            }
        } else if (call.isMethod()) {           //Method
            if (prevCall == null && call.methodCall().getContainer() != typedef) {
                cBuilder.path(call.methodCall().getContainer().getPointer()).add("::");
            }
            cBuilder.nameMethod(call.methodCall().getName()).add("(").add(call.methodCall().params, call.args()).add(")");
        } else if (call.isConstructor()) {      //Constructor
            boolean stack = call.isStackConstructor();

            if (call.getReturnType().isStruct()) {                      //Struct
                if (call.args().isEmpty()) {
                    cBuilder.constructor(call.getReturnType(), false).add("(InitKey::key)");
                } else if (call.args().size() == 1 && call.args().get(0).getReturnType().fullEquals(call.getReturnType())) {
                    cBuilder.constructor(call.getReturnType(), false).add("(InitKey::key, ").add(call.constructorCall().params, call.args()).add(")");
                } else {
                    cBuilder.constructor(call.getReturnType(), false).add("(").add(call.constructorCall().params, call.args()).add(")");
                }
            } else {                                                    //Classes
                if (call.initArgs().size() > 0) {
                    if (call.initFields().size() > 0) {                 //new Class() {field1 = value1, field2 = value2}
                        cBuilder.add("[](").add(call.getReturnType()).add(" value");
                        for (int i = 0; i < call.initFields().size(); i++) {
                            cBuilder.add(", ").add(call.initFields().get(i).getType()).add(" arg").add(i);
                        }
                        cBuilder.add(") -> ").add(call.getReturnType()).add(" { ");
                        for (int i = 0; i < call.initFields().size(); i++) {
                            Field field = call.initFields().get(i);
                            if (field.type == Type.PROPERTY) {
                                cBuilder.add("value->").namePropertySet(field.getName()).add("(arg").add(i).add(");");
                            } else {
                                cBuilder.add("value->").nameField(field.getName()).add(" = arg").add(i).add(";");
                            }
                        }
                        cBuilder.add("return value; } (");
                        cBuilder.constructor(call.getReturnType(), stack).add("(").add(call.constructorCall().params, call.args()).add(")");
                        for (int i = 0; i < call.initArgs().size(); i++) {
                            cBuilder.add(", ").add(call.initArgs().get(i));
                        }
                        cBuilder.add(")");
                    } else {                                            //new array[]{value1, value2, value3, value4}
                        if (call.args().size() > 0) {
                            cBuilder.constructor(call.getReturnType(), stack)
                                    .add("({").add(null, call.args()).add("}, ")
                                    .add("{").add(null, call.initArgs()).add("})");
                        } else {
                            cBuilder.constructor(call.getReturnType(), stack)
                                    .add("({").add(call.initArgs().size()).add("}, ")
                                    .add("{").add(null, call.initArgs()).add("})");
                        }
                    }
                } else {
                    cBuilder.constructor(call.getReturnType(), stack).add("(").add(call.constructorCall().params, call.args()).add(")");
                }
            }
        }
    }
}
