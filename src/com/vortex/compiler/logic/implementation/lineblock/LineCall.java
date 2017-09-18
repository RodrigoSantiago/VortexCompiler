package com.vortex.compiler.logic.implementation.lineblock;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.content.TokenSplitter;
import com.vortex.compiler.logic.Operator;
import com.vortex.compiler.logic.Type;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.Constructor;
import com.vortex.compiler.logic.header.Indexer;
import com.vortex.compiler.logic.header.Method;
import com.vortex.compiler.logic.header.OpOverload;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.implementation.block.BlockLambda;
import com.vortex.compiler.logic.implementation.line.Line;
import com.vortex.compiler.logic.implementation.lineblock.data.*;
import com.vortex.compiler.logic.typedef.Pointer;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 15/10/2016
 *
 *         Chamada de metodo, construtor, variavel ou valor direto
 */
public class LineCall extends Line implements InnerLine {

    public Data data;

    //Acesso a membros de instancia
    public final boolean instance;

    //Linha anterior e ponteiro alvo atual
    private LineCall previousLine;
    private Pointer contextPointer;
    private boolean justADot;

    //Modo Get e Set
    public boolean getMod, setMod;

    public LineCall(LineBlock lineContainer, Token token, boolean instance, Operator operator) {
        super(lineContainer.getCommandContainer(), token);
        this.instance = instance;
        if (operator == Operator.cast) {
            data = new DataOperator(this);
        } else {
            justADot = token.compare(".");
        }
    }

    public LineCall(LineBlock lineContainer, Token token, boolean instance) {
        this(lineContainer, token, instance, null);
    }

    public static ArrayList<Token> splitParameters(Token argsToken) {
        Token tokens[] = TokenSplitter.split(argsToken.byNested(), true, TokenSplitter.ARGUMENTS);
        ArrayList<Token> argsTokens = new ArrayList<>();
        Token startToken = null;
        Token endToken = null;
        for (Token sToken : tokens) {
            if (sToken.compare(",")) {
                if (startToken == null) {
                    argsTokens.add(sToken.subSequence(0, 0));
                } else {
                    argsTokens.add(startToken.byAdd(endToken));
                }
                startToken = endToken = null;
            } else {
                if (startToken == null) startToken = sToken;
                endToken = sToken;
            }
        }
        if (startToken != null) {
            argsTokens.add(startToken.byAdd(endToken));
        }
        if (tokens.length > 0 && tokens[tokens.length - 1].compare(",")) {
            argsTokens.add(tokens[tokens.length - 1].subSequence(0, 0));
        }
        return argsTokens;
    }

    @Override
    public void load() {
        contextPointer = null;
        if (previousLine != null && !previousLine.isWrong()) {
            contextPointer = previousLine.getReturnType();
        }

        if (data != null || justADot) return;

        if (contextPointer == null) {
            if (token.compare("true") || token.compare("false")) {                  //Value (true-false)
                data = new DataValue(this, 0);
            } else if (token.compare("null")) {                                     //Value  (null)
                data = new DataValue(this, 1);
            } else if (token.matches("\\d+(l|L)?")) {                               //Value (1234567890L)
                data = new DataValue(this, 2);
            } else if (token.matches("0(x|X)[\\dABCDEFabcdef]+")) {                 //Value (0x12345678)
                data = new DataValue(this, 3);
            } else if (token.matches("(\\d*\\.\\d+((e|E)((\\-)|(\\+))?\\d+)?(D|d|F|f)?)|(\\d+(D|d|F|f))")) {//Value (12345.67890D)
                data = new DataValue(this, 4);
            } else if (token.startsWith("\"") && token.endsWith("\"")) {            //Value ("abc")
                data = new DataValue(this, 5);
            } else if (token.startsWith("\'") && token.endsWith("\'")) {            //Value ('abc')
                data = new DataValue(this, 6);
            } else if (SmartRegex.isOperator(token)) {                              //Operator
                data = new DataOperator(this);
            } else if (token.startsWith("new ")) {                                  //Constructor
                data = new DataConstructor(this);
            } else if (token.isClosedBy("()")) {                                    //Inner
                data = new DataInner(this);
            } else if (token.matches("[a-zA-Z_]+\\w*?\\(.*\\)")) {                  //Method
                data = new DataMethod(this);
            } else if (token.isClosedBy("[]")) {                                    //Indexer
                data = new DataIndexer(this);
            } else if (SmartRegex.pointer(token)) {
                if (findField(token) != null) {                                     //Field
                    data = new DataField(this);
                } else {                                                            //Static path
                    data = new DataStatic(this);
                }
            } else if (token.matches("\\(.*\\)\\-\\>.*")) {                         //Lambda
                data = new DataLambda(this);
            } else if (token.length() > 0) {
                addErro("invalid statement", token);
            }
        } else {
            if (token.matches("[a-zA-Z_]+\\w*?\\(.*\\)")) {                         //Method
                data = new DataMethod(this);
            } else if (token.isClosedBy("[]")) {                                    //Indexer
                data = new DataIndexer(this);
            } else if (SmartRegex.pointer(token)) {                                 //Field
                data = new DataField(this);
            } else if (token.length() > 0) {
                addErro("invalid statement", token.subSequence(0, 1));
            }
        }
    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {

    }

    @Override
    public Pointer getReturnType() {
        return data instanceof DataInner ? ((DataInner)data).innerCall.getReturnType() : super.getReturnType();
    }

    public Field findField(Token nameToken)  {
        return contextPointer == null ? getCommandContainer().findField(instance, nameToken) : contextPointer.findField(nameToken);
    }

    public Method[] findMethod(Token nameToken, Pointer[] pointers) {
        return contextPointer == null ? getStack().findMethod(instance, nameToken, pointers) : contextPointer.findMethod(nameToken, pointers);
    }

    public Indexer[] findIndexer(Pointer[] pointers) {
        return contextPointer == null ? getStack().findIndexer(instance, pointers) : contextPointer.findIndexer(pointers);
    }

    public void setPreviousLine(LineCall previousLine) {
        this.previousLine = previousLine;
    }

    public LineCall getPreviousLine() {
        return previousLine;
    }

    //Fast Verification
    public boolean isFromSuperCall() {
        return previousLine != null && previousLine.isSuperCall();
    }

    public boolean isJustADot() {
        return justADot;
    }

    public boolean isValue() {
        return data instanceof DataValue && ((DataValue)data).value != null;
    }

    public boolean isInner() {
        return data instanceof DataInner && ((DataInner)data).innerCall != null;
    }

    public boolean isMethod() {
        return data instanceof DataMethod && ((DataMethod)data).methodCall != null;
    }

    public boolean isIndexer() {
        return data instanceof DataIndexer && ((DataIndexer)data).indexerCall != null;
    }

    public boolean isField() {
        return data instanceof DataField && ((DataField)data).fieldCall != null;
    }

    public boolean isStaticField() {
        return isField() && ((DataField)data).fieldCall.isStatic();
    }

    public boolean isSuperCall() {
        return isField() && ((DataField)data).superCall;
    }

    public boolean isStatic() {
        return data instanceof DataStatic && ((DataStatic)data).staticCall != null;
    }

    public boolean isConstructor() {
        return data instanceof DataConstructor && ((DataConstructor)data).constructorCall != null;
    }

    public boolean isStackConstructor() {
        return data instanceof DataConstructor && ((DataConstructor)data).constructorStack;
    }

    public boolean isOperator() {
        return data instanceof DataOperator;
    }

    public boolean isLambda() {
        return data instanceof DataLambda;
    }

    public boolean isEmpty() {
        return token.length() == 0;
    }

    //Dynamic Operator Data
    public void setOperatorOverload(OpOverload operatorCall) {
        ((DataOperator)data).operatorCall = operatorCall;
    }

    public void setBetweenObjects() {
        ((DataOperator)data).betweenObjects = true;
    }

    //Direct Data
    public Pointer castingPointer() {
        return data instanceof DataOperator ? ((DataOperator) data).castingPointer : null;
    }

    public Constructor constructorCall() {
        return data instanceof DataConstructor ? ((DataConstructor) data).constructorCall : null;
    }

    public Field fieldCall() {
        return data instanceof DataField ? ((DataField) data).fieldCall : null;
    }

    public Indexer indexerCall() {
        return data instanceof DataIndexer ? ((DataIndexer) data).indexerCall : null;
    }

    public LineBlock innerCall() {
        return data instanceof DataInner ? ((DataInner) data).innerCall : null;
    }

    public Method methodCall() {
        return data instanceof DataMethod ? ((DataMethod) data).methodCall : null;
    }

    public OpOverload operatorCall() {
        return data instanceof DataOperator ? ((DataOperator) data).operatorCall : null;
    }

    public Pointer staticCall() {
        return data instanceof DataStatic ? ((DataStatic) data).staticCall : null;
    }

    public BlockLambda lambdaBlock() {
        return data instanceof DataLambda ? ((DataLambda) data).blockLambda : null;
    }

    public ArrayList<LineBlock> args() {
        if (data instanceof DataConstructor) return ((DataConstructor) data).args;
        else if (data instanceof DataMethod) return ((DataMethod) data).args;
        else if (data instanceof DataIndexer) return ((DataIndexer) data).args;
        else return null;
    }

    public ArrayList<Field> initFields() {
        return data instanceof DataConstructor ? ((DataConstructor) data).initFields : null;
    }

    public ArrayList<LineBlock> initArgs() {
        return data instanceof DataConstructor ? ((DataConstructor) data).initArgs : null;
    }

    public Pointer[] captureList() {
        return data instanceof DataMethod ? ((DataMethod) data).captureList : null;
    }

    public ArrayList<Pointer> valuePointers() {
        return data instanceof DataValue ? ((DataValue) data).valuePointers : null;
    }

    public String textValue() {
        return data instanceof DataValue ? ((DataValue) data).value : null;
    }

    public boolean isFieldProperty() {
        return isField() && fieldCall().type == Type.PROPERTY;
    }

    @Override
    public boolean isCall() {
        return true;
    }

    @Override
    public Operator getOperator() {
        return isOperator() ? ((DataOperator)data).operator : Operator.invalid;
    }

    @Override
    public LineCall getCall() {
        return this;
    }

    @Override
    public LineBlock getBlock() {
        return null;
    }

    @Override
    public String toString() {
        return token.toString();
    }
}
