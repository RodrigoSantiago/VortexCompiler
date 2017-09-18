package com.vortex.compiler.logic.implementation.lineblock;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.content.TokenSplitter;
import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.logic.LogicToken;
import com.vortex.compiler.logic.Operator;
import com.vortex.compiler.logic.Type;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.OpOverload;
import com.vortex.compiler.logic.header.variable.Params;
import com.vortex.compiler.logic.implementation.block.Block;
import com.vortex.compiler.logic.implementation.block.BlockLambda;
import com.vortex.compiler.logic.implementation.line.Line;
import com.vortex.compiler.logic.typedef.Pointer;
import javafx.util.Pair;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 15/10/2016
 *
 *         Conjunto de LineCalls ou outras LineBlocks
 */
public class LineBlock extends Line implements InnerLine {

    public ArrayList<InnerLine> calls = new ArrayList<>();
    public LineCall lastCall;

    protected boolean requestedGet, requestedSet;
    protected boolean sequence, operation, typedefExpression;
    protected boolean unaryOperation, incrementOperation, setOperation, ternaryOperation, castOperation;

    protected Pointer autoCastPointer;
    protected boolean autoCastConsumed;
    protected ArrayList<Integer> reSetPos = new ArrayList<>();

    private boolean needColon, instance;

    public LineBlock(Block container, Token token, boolean instance, boolean needColon) {
        super(container, token);
        this.needColon = needColon;
        this.instance = instance;

        //SemiColon
        if (token.byLastChar().compare(";")) {
            if (!needColon) {
                addCleanErro("semicolon unexpected", token.byLastChar());
            }
            token = token.subSequence(0, token.length() - 1);
        } else if (needColon) {
            addCleanErro("semicolon expected", token.byLastChar());
        }

        Token[] tokens = TokenSplitter.split(token, false);

        //Espressao especial
        if (SmartRegex.pointer(token)) {
            if (getCommandContainer().findField(instance, token) == null) {
                Pointer pointer = getStack().findPointer(token);
                if (pointer != null) {
                    if (pointer.isStruct()) pointer = pointer.getWrapper();
                    returnType = pointer;
                    typedefExpression = true;
                    return;
                }
            }
        }

        ArrayList<Pair<Token, Operator>> splitList = splitByOperators(tokens);

        if (operation) {
            for (Pair<Token, Operator> value : splitList) {
                if (value.getValue() != null) {
                    calls.add(new LineCall(this, value.getKey(), instance, value.getValue()));
                } else {
                    calls.add(new LineBlock(getCommandContainer(), value.getKey(), instance, false));
                }
            }
        } else if (sequence) {
            int savePos = -1;
            int stage = 0;
            for (int i = 0; i < tokens.length; i++) {
                Token sToken = tokens[i];
                if (stage == 0 && sToken.compare(";")) {
                    addCleanErro("unexpected token", sToken);
                } else if (stage == 0 && sToken.compare("new")) {
                    savePos = i;
                    stage = 2;
                } else if (stage == 0 && sToken.matches(SmartRegex.simpleName)) {
                    savePos = i;
                    stage = 1;
                } else if (stage == 1 && sToken.isClosedBy("()")) {
                    calls.add(new LineCall(this, tokens[savePos].byAdd(sToken), instance));
                    stage = 0;
                } else if (stage == 2 && sToken.compare("stack")) {
                    stage = 3;      //new stack
                } else if ((stage == 2 || stage == 3) && SmartRegex.typedefStatic(sToken)) {
                    stage = 4;      //new [stack] typedefName
                } else if (stage == 4 && sToken.isClosedBy("<>")) {
                    stage = 5;      //new [stack] typedefName <>
                } else if ((stage == 4 || stage == 5) && sToken.isClosedBy("()")) {
                    if (i + 1 < tokens.length && tokens[i + 1].isClosedBy("{}")) {              //initToken
                        calls.add(new LineCall(this, tokens[savePos].byAdd(tokens[++i]), instance));
                    } else {
                        calls.add(new LineCall(this, tokens[savePos].byAdd(sToken), instance));
                    }
                    stage = 0;
                } else if ((stage == 4 || stage == 5 || stage == 6)) {
                    if (sToken.isClosedBy("{}")) {  //arrayInitToken
                        calls.add(new LineCall(this, tokens[savePos].byAdd(sToken), instance));
                        stage = 0;
                    } else if (sToken.isClosedBy("[]")) {
                        stage = 6;
                    } else {
                        if (stage == 6) {
                            calls.add(new LineCall(this, tokens[savePos].byAdd(tokens[--i]), instance));
                            stage = 0;
                        } else {
                            calls.add(new LineCall(this, tokens[savePos], instance));
                            i = savePos;
                            stage = 0;
                        }
                    }
                } else if (stage == 0 && sToken.isClosedBy("()")) {
                    savePos = i;
                    stage = 7;      //(...)
                } else if (stage == 7 && sToken.compare("->")) {
                    stage = 8;      //(...) ->
                } else if (stage == 8 && SmartRegex.pointer(sToken)) {
                    stage = 9;      //(...) -> pointer
                } else if ((stage == 8 || stage == 9) && sToken.isClosedBy("{}")) { //(...) -> [pointer] {...}
                    calls.add(new LineCall(this, tokens[savePos].byAdd(sToken), instance));
                    stage = 0;
                } else {
                    if (stage != 0) {
                        calls.add(new LineCall(this, tokens[savePos], instance));
                        i = savePos;
                        stage = 0;
                    } else {
                        calls.add(new LineCall(this, sToken, instance));
                    }
                }
                if (i == tokens.length - 1) {
                    if (stage == 6) {
                        calls.add(new LineCall(this, tokens[savePos].byAdd(sToken), instance));
                    } else if (stage != 0) {
                        calls.add(new LineCall(this, tokens[savePos], instance));
                        i = savePos;
                        stage = 0;
                    }
                }
            }
        }
    }

    public static void requestPerfectParams(ArrayList<LineBlock> args, Params params){
        for (int i = 0; i < args.size(); i++) {
            args.get(i).requestGetAcess();
            if (params.hasVarArgs() && i >= params.size() - 1 &&
                    args.get(i).getReturnType().getDifference(params.pointers.get(params.size() - 1)) == -1) {
                args.get(i).setAutoCasting(params.getVarArgPointer(), true);
            } else {
                args.get(i).setAutoCasting(params.pointers.get(i), true);
            }
        }
    }

    @Override
    public void load() {
        if (operation) {
            loadOperation();
        } else if (sequence) {
            loadSequence();
        } else if(!isTypedefExpression()) {
            returnType = Pointer.voidPointer;
        }
    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {
        cBuilder.idt(indent);
        new LineCppBuilder(cBuilder, this, indent);
        if (needColon) cBuilder.add(";").ln();
        autoCastConsumed = false;
    }

    private void loadOperation() {
        for (InnerLine call : calls) {
            call.load();
            if (((LogicToken) call).isWrong()) setWrong();
        }
        if (isWrong()) return;

        boolean instanceOperation = false;
        for (InnerLine call : calls) {
            if (call.isCall()) {
                instanceOperation = call.getOperator() != null && call.getOperator().isInstanceLogical();
            } else if (!instanceOperation && call.getBlock().isTypedefExpression()) {
                addErro("unexpected typedef expression", call.getToken());
            } else {
                instanceOperation = false;
            }
        }
        if (isWrong()) return;

        if (castOperation) {
            loadCastOperation();
        } else if (unaryOperation) {
            loadUnaryOperation();
        } else if (incrementOperation) {
            loadIncrementOperation();
        } else if (ternaryOperation) {
            loadTernaryOperation();
        } else if (setOperation) {
            loadSetOperation();
        } else {
            loadCommumOperation();
        }
    }

    //(cast) bloco
    private void loadCastOperation() {
        LineCall call = null;
        LineBlock block = null;

        boolean lastHasErro = false;
        int stage = 0;
        for (InnerLine line : calls) {
            if (stage == 0 && line.isCall()) {
                call = line.getCall();
                stage = 1;
            } else if (stage == 1 && line.isBlock()) {
                block = line.getBlock();
                stage = -1;
            } else {
                lastHasErro = true;
                addCleanErro(line.isCall() ? "unexpected operator" : "unexpected line", line.getToken());
            }
        }
        if (stage != -1) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
            setWrong();
        } else {
            if (call.castingPointer() != null) {
                block.requestGetAcess();
                if (!block.requestCast(call.castingPointer())) {
                    addCleanErro("incompatible type", block);
                }
                returnType = call.castingPointer();
            } else {
                addErro("unexpected value", call.getToken());
            }
        }
    }

    // bloco = bloco
    private void loadSetOperation() {
        LineBlock setBlock = null;
        LineCall call = null;
        LineBlock getBlock = null;

        boolean lastHasErro = false;
        int stage = 0;
        for (InnerLine line : calls) {
            if (stage == 0 && line.isBlock()) {
                setBlock = line.getBlock();
                stage = 1;
            } else if (stage == 1 && line.isCall()) {
                call = line.getCall();
                stage = 2;
            } else if (stage == 2 && line.isBlock()) {
                getBlock = line.getBlock();
                stage = -1;
            } else {
                lastHasErro = true;
                addCleanErro(line.isCall() ? "unexpected operator" : "unexpected line", line.getToken());
            }
        }
        if (stage != -1) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
            setWrong();
        } else {
            Operator operator = call.getOperator().getInnerOperator();
            setBlock.requestSetAcess();
            getBlock.requestGetAcess();

            if (operator == Operator.set) {
                getBlock.setAutoCasting(setBlock.getReturnType(), false);
                returnType = setBlock.getReturnType();
            } else {
                setBlock.requestGetAcess();
                OpOverload opOverload = opReturn(false, setBlock.getReturnType(), call.getToken(),
                        operator.value, setBlock.getReturnType(), getBlock.getReturnType());
                if (opOverload != null) {
                    call.setOperatorOverload(opOverload);
                    returnType = opOverload.getType();

                    //Especial autocasting
                    setAutoCasting(setBlock.getReturnType(), false);
                    setBlock.autoCastConsumed = true;
                }  else {
                    setWrong();
                }
            }
        }
    }

    // + bloco
    private void loadUnaryOperation() {
        LineCall call = null;
        LineBlock block = null;

        boolean lastHasErro = false;
        int stage = 0;
        for (InnerLine line : calls) {
            if (stage == 0 && line.isCall()) {
                call = line.getCall();
                stage = 1;
            } else if (stage == 1 && line.isBlock()) {
                block = line.getBlock();
                stage = -1;
            } else {
                lastHasErro = true;
                addCleanErro(line.isCall() ? "unexpected operator" : "unexpected line", line.getToken());
            }
        }
        if (stage != -1) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
            setWrong();
        } else {
            OpOverload opOverload = opReturn(false, block.getReturnType(), call.getToken(),
                    call.getOperator().value, block.getReturnType());
            block.requestGetAcess();

            if (opOverload != null) {
                call.setOperatorOverload(opOverload);
                if (!opOverload.getContainer().isLangImplement()) {
                    block.setAutoCasting(opOverload.params.pointers.get(0), true);
                }
                returnType = opOverload.getType();
            } else {
                setWrong();
            }
        }
    }

    // ++ bloco , bloco ++
    private void loadIncrementOperation() {
        LineCall precedded = null;
        LineBlock block = null;
        LineCall sucedded = null;

        boolean lastHasErro = false;
        int stage = 0;
        for (InnerLine line : calls) {
            if (stage == 0 && line.isCall()) {
                precedded = line.getCall();
                stage = 1;
            } else if (stage == 0 && line.isBlock()) {
                block = line.getBlock();
                stage = 2;
            } else if (stage == 1 && line.isBlock()) {
                block = line.getBlock();
                stage = -1;
            } else if (stage == 2 && line.isCall()) {
                sucedded = line.getCall();
                stage = -1;
            } else {
                lastHasErro = true;
                addCleanErro(line.isCall() ? "unexpected operator" : "unexpected line", line.getToken());
            }
        }
        if (stage != -1) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
            setWrong();
        } else {
            LineCall call = (sucedded != null ? sucedded : precedded);
            OpOverload opOverload = opReturn(false, block.getReturnType(), call.getToken(),
                    call.getOperator().value, block.getReturnType());
            block.requestGetAcess();
            block.requestSetAcess();

            if (opOverload != null) {
                call.setOperatorOverload(opOverload);
                if (!block.getReturnType().isLang()) {
                    block.setAutoCasting(opOverload.params.pointers.get(0), true);
                }
                returnType = opOverload.getType();
            } else {
                setWrong();
            }
        }
    }

    //bloco + bloco
    private void loadCommumOperation() {
        LineBlock firstBlock = null;
        LineCall call = null;
        LineBlock secondBlock = null;

        boolean lastHasErro = false;
        int stage = 0;
        for (InnerLine line : calls) {
            if (stage == 0 && line.isBlock()) {
                firstBlock = line.getBlock();
                stage = 1;
            } else if (stage == 1 && line.isCall()) {
                call = line.getCall();
                stage = 2;
            } else if (stage == 2 && line.isBlock()) {
                secondBlock = line.getBlock();
                stage = -1;
            } else {
                lastHasErro = true;
                addCleanErro(line.isCall() ? "unexpected operator" : "unexpected line", line.getToken());
            }
        }
        if (stage != -1) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
            setWrong();
        } else {
            Operator operator = call.getOperator();
            firstBlock.requestGetAcess();
            secondBlock.requestGetAcess();

            if ((operator == Operator.equals || operator == Operator.different) &&
                    !firstBlock.getReturnType().isStruct() && !secondBlock.getReturnType().isStruct()) {
                call.setBetweenObjects();
                returnType = DataBase.defBoolPointer;
            } else if ((operator == Operator.equals || operator == Operator.different) &&
                    firstBlock.getReturnType().typedef == DataBase.defFunction &&
                    secondBlock.getReturnType().typedef == DataBase.defFunction) {
                secondBlock.setAutoCasting(firstBlock.getReturnType(), false);
                returnType = DataBase.defBoolPointer;
            } else if (operator.isLogical()) {          // or, and
                firstBlock.setAutoCasting(DataBase.defBoolPointer, true);
                secondBlock.setAutoCasting(DataBase.defBoolPointer, true);
                returnType = DataBase.defBoolPointer;
            } else if (operator.isInstanceLogical()) {  // is, isnot
                if (!secondBlock.isTypedefExpression()) {
                    addErro("typedef expression expected", secondBlock.getToken());
                }
                returnType = DataBase.defBoolPointer;
            } else {
                OpOverload opOverload = opReturn(true, firstBlock.getReturnType(), call.getToken(),
                        operator.value, firstBlock.getReturnType(), secondBlock.getReturnType());
                if (opOverload != null) {
                    call.setOperatorOverload(opOverload);
                    if (!opOverload.getContainer().isLangImplement()
                            || !firstBlock.getReturnType().isLang()
                            || !secondBlock.getReturnType().isLang()) {
                        firstBlock.setAutoCasting(opOverload.params.pointers.get(0), true);
                        secondBlock.setAutoCasting(opOverload.params.pointers.get(1), true);
                    }
                    returnType = opOverload.getType();
                } else {
                    setWrong();
                }
            }
        }
    }

    //bloco ? bloco : bloco
    private void loadTernaryOperation() {
        LineBlock conditionBlock = null, firstBlock = null, secondBlock = null;
        boolean lastHasErro = false;
        int stage = 0;
        for (InnerLine line : calls) {
            lastHasErro = false;
            if (stage == 0 && line.isBlock()) {
                conditionBlock = line.getBlock();
                stage = 1;
            } else if (stage == 1 && line.isCall() && line.getOperator() == Operator.tIF) {
                stage = 2;
            } else if (stage == 2 && line.isBlock()) {
                firstBlock = line.getBlock();
                stage = 3;
            } else if (stage == 3 && line.isCall() && line.getOperator() == Operator.tElse) {
                stage = 4;
            } else if (stage == 4 && line.isBlock()) {
                secondBlock = line.getBlock();
                stage = -1;
            } else {
                lastHasErro = true;
                addCleanErro(line.isCall() ? "unexpected operator" : "unexpected line", line.getToken());
            }
        }
        if (stage != -1) {
            if (!lastHasErro) addCleanErro("unexpected end of tokens", token.byLastChar());
            setWrong();
        } else if (!conditionBlock.isWrong() && !firstBlock.isWrong() && !secondBlock.isWrong()) {
            firstBlock.requestGetAcess();
            secondBlock.requestGetAcess();
            conditionBlock.requestGetAcess();

            conditionBlock.setAutoCasting(DataBase.defBoolPointer, false);

            if (secondBlock.getReturnType().verifyAutoCasting(firstBlock.getReturnType()) == 0) {
                secondBlock.setAutoCasting(firstBlock.getReturnType(), true);
                returnType = firstBlock.getReturnType();
            } else if (firstBlock.getReturnType().verifyAutoCasting(secondBlock.getReturnType()) == 0) {
                firstBlock.setAutoCasting(secondBlock.getReturnType(), true);
                returnType = secondBlock.getReturnType();
            } else if (!firstBlock.getReturnType().isStruct() && !secondBlock.getReturnType().isStruct()){
                secondBlock.setAutoCasting(DataBase.defObjectPointer, true);
                secondBlock.setAutoCasting(DataBase.defObjectPointer, true);
                returnType = DataBase.defObjectPointer;
            } else {
                addErro("incompatible type", firstBlock.getToken());
                addErro("incompatible type", secondBlock.getToken());
            }
        }
    }

    //a.b.c().d[].e
    private void loadSequence() {
        //[0-indexer/demais][1-dot -> 2/indexer -> 1][2-demais]
        LineCall lCall = null;
        int stage = 0;
        for (InnerLine innerLine : calls) {
            LineCall call = innerLine.getCall();
            call.setPreviousLine(lCall);
            call.load();
            if (call.isWrong()) {
                setWrong();
            } else if (stage == 0) {
                if (call.isIndexer()) {
                    lCall = call;
                    stage = 1;
                } else if (!call.isJustADot()) {
                    lCall = call;
                    stage = 1;
                } else {
                    addErro("invalid line", call.getToken());
                }
            } else if (stage == 1) {
                if (call.isIndexer()) {
                    lCall = call;
                    stage = 1;
                } else if (call.isJustADot()) {
                    stage = 2;
                } else {
                    addErro("invalid line", call.getToken());
                }
            } else if (!call.isIndexer() && !call.isJustADot()) {
                lCall = call;
                stage = 1;
            } else {
                addErro("invalid line", call.getToken());
            }
        }

        if (isWrong()) return;

        if (lCall != null) {
            lastCall = lCall;
            returnType = lastCall.getReturnType();
        } else if (calls.size() > 0) {
            addErro("invalid line", calls.get(calls.size() - 1).getCall().getToken());
        }

        for (int i = 0; i < calls.size(); i++) {
            LineCall call = calls.get(i).getCall();
            if (call != null && call != lastCall) {
                call.getMod = true;
                if (call.isConstructor()) {
                    getStack().hasAcess(this, call.getToken(), call.constructorCall());
                } else if (call.isMethod()) {
                    getStack().hasAcess(this, call.getToken(), call.methodCall());
                } else if (call.isField()) {
                    getStack().hasGetAcess(this, call.getToken(), call.fieldCall());
                } else if (call.isIndexer()) {
                    getStack().hasGetAcess(this, call.getToken(), call.indexerCall());
                } else if (call.isInner()) {
                    call.innerCall().requestGetAcess();
                }

                if (call.isFieldProperty() || call.isIndexer()) {
                    reSetPos.add(i);
                }
            }
        }

        if (!isWrong() && calls.size() == 1 && calls.get(0).isCall() && calls.get(0).getCall().isSuperCall()) {
            addErro("super should not be used as object", calls.get(0).getCall().getToken());
        }
    }

    public void requestSetAcess() {
        if (requestedSet || isWrong()) return;
        requestedSet = true;

        if (lastCall != null) {
            lastCall.setMod = true;

            if (lastCall.isField()) {
                if (lastCall.fieldCall().type == Type.LOCALVAR &&
                        getCommandContainer() != lastCall.fieldCall().localOrigem &&
                        getCommandContainer() instanceof BlockLambda) {
                    this.addCleanErro("cannot set", lastCall.getToken());
                } else {
                    getStack().hasSetAcess(this, lastCall.getToken(), lastCall.fieldCall());
                }
            } else if (lastCall.isIndexer()) {
                getStack().hasSetAcess(this, lastCall.getToken(), lastCall.indexerCall());
            } else {
                if (!isWrong()) addCleanErro("unexpected line", token);
            }

            for (int pos : reSetPos){
                LineCall oldCall = calls.get(pos).getCall();
                addWarning("temporary value", oldCall.getToken());
            }
        }
    }

    public void requestGetAcess() {
        if (requestedGet || isWrong()) return;
        requestedGet = true;

        if (isTypedefExpression()) {
            if (!isWrong()) addErro("unexpected type expression", token);
        } else if (lastCall != null) {
            lastCall.getMod = true;

            if (lastCall.isConstructor()) {
                getStack().hasAcess(this, lastCall.getToken(), lastCall.constructorCall());
            } else if (lastCall.isMethod()) {
                getStack().hasAcess(this, lastCall.getToken(), lastCall.methodCall());
            } else if (lastCall.isField()) {
                getStack().hasGetAcess(this, lastCall.getToken(), lastCall.fieldCall());
            } else if (lastCall.isIndexer()) {
                getStack().hasGetAcess(this, lastCall.getToken(), lastCall.indexerCall());
            } else if (lastCall.isInner()) {
                lastCall.innerCall().requestGetAcess();
            } else if (!lastCall.isValue() && !lastCall.isLambda()) {
                if (!isWrong()) addCleanErro("unexpected line", token);
            }
        }
    }

    public boolean requestCast(Pointer pointer) {
        if (returnType.verifyAutoCasting(pointer) != -1) {
            return true;
        } else {
            if (isValue()) {
                for (Pointer value : lastCall.valuePointers()) {
                    if (value.getDifference(pointer) > -1) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public void setAutoCasting(Pointer pointer, boolean param) {
        if (isInner()) {
            lastCall.innerCall().setAutoCasting(pointer, param);
        } else {
            if (isValue() && autoCastPointer == null) {
                Pointer closerValue = null;
                int min = -1;
                for (Pointer value : lastCall.valuePointers()) {
                    int dif = value.getDifference(pointer);
                    if (dif > -1 && (dif < min || min == -1)) {
                        min = dif;
                        closerValue = value;
                    }
                }

                if (closerValue != null) {
                    returnType = closerValue;
                }
            }

            if (!isWrong()) {
                switch (returnType.verifyAutoCasting(pointer)) {
                    case -1:
                        addCleanErro("incompatible type", token);
                        break;
                    case 1:
                        addCleanErro("automatic type casting not found", token);
                        break;
                }
            }

            if (returnType.isDefault()
                    || (isLangValue() && param)
                    || (!returnType.isValid(pointer))
                    || ((returnType.hasGenericIndex() || pointer.hasGenericIndex()) && param)) {
                autoCastPointer = pointer;
            }
        }
    }

    public LineCall getLastCall() {
        if (lastCall != null && lastCall.isInner()) {
            LineCall lineCall = lastCall.innerCall().getLastCall();
            return lineCall == null ? lastCall : lineCall;
        } else {
            return lastCall;
        }
    }

    public boolean isTypedefExpression() {
        return typedefExpression;
    }

    public boolean isEmpty() {
        return calls.size() == 0;
    }

    public boolean isOperation() {
        return operation;
    }

    public boolean isSequence() {
        return sequence;
    }

    public boolean isSetOperation() {
        return setOperation;
    }

    public boolean isIncrementOperation() {
        return incrementOperation;
    }

    public boolean isTernaryOperation() {
        return ternaryOperation;
    }

    public boolean isCastOperation() {
        return castOperation;
    }

    public boolean isUnaryOperation() {
        return unaryOperation;
    }

    public boolean isInner() {
        if (sequence) {
            if (lastCall != null) {
                return lastCall.isInner();
            }
        }
        return false;
    }

    public boolean isValue() {
        if (sequence) {
            if (lastCall != null) {
                if (lastCall.isValue()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isLangValue() {
        return isValue() && !getReturnType().equals(DataBase.defBoolPointer)
                && !getReturnType().equals(DataBase.defStringPointer);
    }

    public boolean isMethod() {
        if (sequence) {
            if (lastCall != null) {
                if (lastCall.isMethod()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isIndexer() {
        if (sequence) {
            if (lastCall != null) {
                if (lastCall.isIndexer()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isProperty() {
        if (sequence) {
            if (lastCall != null) {
                if (lastCall.isField()) {
                    return lastCall.fieldCall().type == Type.PROPERTY;
                }
            }
        }
        return false;
    }

    public boolean isStaticField() {
        if (sequence) {
            if (lastCall != null) {
                if (lastCall.isStaticField()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isField() {
        if (sequence) {
            if (lastCall != null) {
                if (lastCall.isField()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isLambda() {
        if (sequence) {
            if (lastCall != null) {
                if (lastCall.isLambda()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isStatic() {
        if (sequence) {
            if (lastCall != null) {
                return lastCall.isStatic();
            }
        }
        return false;
    }

    public boolean isConstructor() {
        if (sequence) {
            if (lastCall != null) {
                if (lastCall.isConstructor()) {
                    return true;
                }
            }
        }
        return false;
    }

    //Aceita inners
    public boolean isStackConstructor() {
        if (sequence) {
            if (lastCall != null) {
                if (lastCall.isConstructor()) {
                    return lastCall.isStackConstructor();
                } else if (lastCall.isInner()) {
                    return lastCall.innerCall().isStackConstructor();
                }
            }
        }
        return false;
    }

    @Override
    public Pointer getReturnTrueType() {
        if (lastCall != null && lastCall.isInner()) {
            return lastCall.innerCall().getReturnType();
        } else {
            return returnType;
        }
    }

    @Override
    public Pointer getReturnType() {
        if (lastCall != null && lastCall.isInner()) {
            return lastCall.innerCall().getReturnType();
        } else {
            return autoCastPointer != null ? autoCastPointer : returnType;
        }
    }

    @Override
    public boolean isCall() {
        return false;
    }

    @Override
    public Operator getOperator() {
        return null;
    }

    @Override
    public LineCall getCall() {
        return null;
    }

    @Override
    public LineBlock getBlock() {
        return this;
    }

    @Override
    public String toString() {
        String val = "(";
        for (InnerLine innerLine : calls) {
            val += innerLine.getToken();
        }
        return val + ")";
    }

    private OpOverload opReturn(boolean findReverse, Pointer toFind, Token token, String value, Pointer... arguments) {
        OpOverload opOverload[] = toFind.findOperator(value, arguments);
        if (findReverse) {
            if (opOverload.length != 1 && arguments.length == 2) {
                opOverload = arguments[1].findOperator(value, arguments);
            }
            if (opOverload.length == 0) {
                addCleanErro("operator not found", token);
            } else if (opOverload.length > 1) {
                addCleanErro("ambiguous signature", token);
            } else {
                getStack().hasAcess(this, token, opOverload[0]);
                return opOverload[0];
            }
        } else {
            if (opOverload.length == 0) {
                addCleanErro("operator not found", token);
            } else if (opOverload.length > 1) {
                addCleanErro("ambiguous signature", token);
            } else {
                getStack().hasAcess(this, token, opOverload[0]);
                return opOverload[0];
            }
        }
        return null;
    }

    private ArrayList<Pair<Token,Operator>> splitByOperators(Token[] toSplit) {
        if (toSplit.length == 0) {
            return new ArrayList<>();
        }
        ArrayList<Integer> ids = new ArrayList<>();

        Operator operator = null;
        Operator lOperator = Operator.invalid;
        boolean noCast = false;
        for (int i = 0; i < toSplit.length; i++) {
            Token sToken = toSplit[i];
            Operator opValue = splitFindOperator(sToken, noCast, i + 1 < toSplit.length ? toSplit[i + 1] : null);
            if (opValue != null) {
                if (lOperator != null && !lOperator.isIncrement()) {
                    if (opValue == Operator.add) opValue = Operator.positive;
                    if (opValue == Operator.sub) opValue = Operator.negative;
                }

                if (operator == null || opValue.priority < operator.priority) {
                    operator = opValue;
                    ids.clear();
                    ids.add(i);
                } else if (operator.priority == opValue.priority) {
                    operator = opValue;
                    ids.add(i);
                }
                lOperator = opValue;
            } else {
                lOperator = null;
            }
            if (opValue == null || opValue != Operator.cast) {
                noCast = true;
            }
        }

        int foundIndex;
        ArrayList<Pair<Token,Operator>> list = new ArrayList<>();

        if (operator == null) {
            foundIndex = -1;
            sequence = true;
        } else {
            operation = true;
            if (operator.isSet()) {
                setOperation = true;
                foundIndex = ids.get(0);
            } else if (operator.isIncrement()) {
                incrementOperation = true;
                foundIndex = ids.get(0);
            } else if (operator == Operator.cast) {
                castOperation = true;
                foundIndex = ids.get(0);
            } else if (operator.isUnary()) {
                unaryOperation = true;
                foundIndex = ids.get(0);
            } else if (operator.isTernary()) {
                ternaryOperation = true;
                foundIndex = -2;
            } else {
                foundIndex = ids.get(ids.size() - 1);
            }
        }

        if (foundIndex == 0) {
            list.add(new Pair<>(toSplit[foundIndex], operator));
            if (toSplit.length > 1) {
                list.add(new Pair<>(toSplit[foundIndex + 1].byAdd(toSplit[toSplit.length - 1]), null));
            }
        } else if (foundIndex > 0) {
            list.add(new Pair<>(toSplit[0].byAdd(toSplit[foundIndex - 1]), null));
            list.add(new Pair<>(toSplit[foundIndex], operator));
            if (toSplit.length > foundIndex + 1) {
                list.add(new Pair<>(toSplit[foundIndex + 1].byAdd(toSplit[toSplit.length - 1]), null));
            }
        } else if (foundIndex == -1) {
            list.add(new Pair<>(toSplit[0].byAdd(toSplit[toSplit.length - 1]), null));
        } else if (foundIndex == -2) {
            int lastPos = -1;
            for (int id : ids) {
                if (lastPos < id - 1) {
                    list.add(new Pair<>(toSplit[lastPos + 1].byAdd(toSplit[id - 1]), null));
                }
                list.add(new Pair<>(toSplit[id], operator));
                lastPos = id;
            }
            if (lastPos < toSplit.length - 1) {
                list.add(new Pair<>(toSplit[lastPos + 1].byAdd(toSplit[toSplit.length - 1]), null));
            }
        }
        return list;
    }

    private Operator splitFindOperator(Token sToken, boolean noCast, Token nToken) {
        if (SmartRegex.isOperator(sToken)) {
            Operator operator = Operator.fromToken(sToken);
            return operator == Operator.invalid ? null : operator;
        }
        if (!noCast && sToken.matches("\\(" + SmartRegex.pointer + "\\)")) {
            if (nToken == null || Operator.fromToken(nToken) != Operator.invalid || nToken.compare(".") || nToken.isClosedBy("[]")) {
                return null;
            } else {
                return Operator.cast;
            }
        }
        return null;
    }
}
