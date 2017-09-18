package com.vortex.compiler.content;

import com.vortex.compiler.data.DataBase;
import com.vortex.compiler.erro.ErroType;
import com.vortex.compiler.logic.Type;
import com.vortex.compiler.logic.header.*;
import com.vortex.compiler.logic.implementation.block.Block;
import com.vortex.compiler.logic.implementation.block.*;
import com.vortex.compiler.logic.implementation.line.*;
import com.vortex.compiler.logic.implementation.lineblock.LineBlock;
import com.vortex.compiler.logic.space.*;
import com.vortex.compiler.logic.typedef.*;
import com.vortex.compiler.logic.typedef.Class; //-> java.lang.Class
import com.vortex.compiler.logic.typedef.Enum;  //-> java.lang.Enum

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 01/10/2016
 */
public class Parser {

    /**
     * Depois de fazer a divisão pelo 'Splitter' procura os blocos relacionados a localização e typedefs
     *
     * @param strFile Arquivo lógico para leitura
     * @param token Token geral do arquivo
     */
    public static void parseTypedefs(StringFile strFile, Token token) {
        ArrayList<Token> tokenBlocks = getBlocks(TokenSplitter.split(token, false, TokenSplitter.STATEMENT));

        NameSpace nameSpace = null;
        ArrayList<Using> usings = new ArrayList<>();

        boolean usingsDone = false;
        for (Token tokenBlock : tokenBlocks) {
            if (tokenBlock.compare(";")) {
                strFile.addErro(ErroType.WARNING, "unnecessary semicolon", tokenBlock);
                continue;
            }

            Token[] tokens = TokenSplitter.split(tokenBlock, true, TokenSplitter.STATEMENT);

            if (tokens.length > 0 && tokens[0].compare(NameSpace.keyword)) {  //NAMESPACE
                if (nameSpace == null) {
                    nameSpace = new NameSpace(tokenBlock, tokens);
                } else {
                    strFile.addErro(ErroType.ERRO, "unexpected namespace", tokenBlock);
                }
                continue;
            } else if (nameSpace == null) {
                nameSpace = new NameSpace(new Token(strFile, 0, 0), null);
            }
            if (tokens.length > 0 && tokens[0].compare(Using.keyword)) {      //USING
                if (!usingsDone) {
                    usings.add(new Using(tokenBlock, tokens));
                } else {
                    strFile.addErro(ErroType.ERRO, "usings statements must be made before typedefs", tokenBlock);
                }
                continue;
            }

            Typedef typedef = null;
            if (strFile.isMain()) {
                if (strFile.mainMethod == null &&
                        (SmartRegex.headerRegex(tokens, SmartRegex.pointer, SmartRegex.methodStatement, "(...)") > 0
                        || SmartRegex.headerRegex(tokens, "void") > 0           //Debug
                        || SmartRegex.parenBeforeSplitters(tokens))) {          //Debug
                    usingsDone = true;
                    strFile.mainMethod = new Method(DataBase.defObject, tokenBlock, tokens);
                    strFile.mainMethod.setMain(true);
                    continue;
                }
            } else {
                for (Token sToken : tokens) {
                    if (sToken.compare(Class.keyword)) {        //CLASS
                        typedef = new Class(new Workspace(nameSpace, usings), tokenBlock, tokens);
                        break;
                    }
                    if (sToken.compare(Interface.keyword)) {    //INTERFACE
                        typedef = new Interface(new Workspace(nameSpace, usings), tokenBlock, tokens);
                        break;
                    }
                    if (sToken.compare(Enum.keyword)) {         //ENUM
                        typedef = new Enum(new Workspace(nameSpace, usings), tokenBlock, tokens);
                        break;
                    }
                    if (sToken.compare(Struct.keyword)) {       //STRUCT
                        typedef = new Struct(new Workspace(nameSpace, usings), tokenBlock, tokens);
                        break;
                    }
                }
            }

            if (typedef != null) {
                usingsDone = true;
                strFile.typedefs.add(typedef);
            } else {
                strFile.addErro(ErroType.ERRO, "invalid statement", tokenBlock);
            }
        }
        if (nameSpace == null) {
            nameSpace = new NameSpace(new Token(strFile, 0, 0), null);
        }

        strFile.workspace = new Workspace(nameSpace, usings);
    }

    /**
     * Depois de fazer a divisão pelo 'Splitter' procura os blocos relacionado a headers (vars, properties, methods e etc)
     *
     * @param typedef Typedefe para leitura
     * @param token Token geral do typedef
     * @param aEnums Habilita procura por enumeradores
     * @param aOperators Habilita procura por operadores
     * @param aDestructors Habilita procura por destrutores
     */
    public static void parseHeaders(Typedef typedef, Token token, boolean aEnums, boolean aOperators, boolean aDestructors) {
        ArrayList<Token> tokenBlocks = getBlocks(TokenSplitter.split(token, false, TokenSplitter.STATEMENT));
        Header lastHeader = null;
        for (Token tokenBlock : tokenBlocks) {
            Header header = null;
            boolean hasErro = false;
            Token[] tokens = TokenSplitter.split(tokenBlock, true, TokenSplitter.STATEMENT);

            //Empty [;]
            if (tokenBlock.compare(";")) {
                typedef.addWarning("unnecessary semicolon", tokenBlock);
                hasErro = true;
            }
            //Nativo [modifiers][native][()][...]
            else if (SmartRegex.headerRegex(tokens, "native") > 0) {
                header = new NativeHeader(typedef, tokenBlock, tokens);
            }
            //Enumerador [modifiers*][name][()]?([,][name])*
            else if (aEnums &&
                    (SmartRegex.headerRegex(tokens, SmartRegex.simpleName, "(...)", "(\\;)|(\\,)") > 0 ||
                            SmartRegex.headerRegex(tokens, SmartRegex.simpleName, ",") > 0)) {
                header = new Enumeration(typedef, tokenBlock, tokens);
            }
            //Construtor [modifiers][name][()][...]
            else if (SmartRegex.headerRegex(tokens, SmartRegex.simpleName, "(...)") > 0) {
                header = new Constructor(typedef, tokenBlock, tokens);
            }
            //Destrutor [modifiers][~][name][()][...]
            else if (SmartRegex.headerRegex(tokens, "~", SmartRegex.simpleName) > 0) {
                if (aDestructors) {
                    header = new Destructor(typedef, tokenBlock, tokens);
                } else {
                    typedef.addCleanErro("destructors are not allowed here", tokenBlock.byHeader());
                    hasErro = true;
                }
            }
            //Operador [modifiers][pointer][operator][operatoroverload][()][...]
            else if (SmartRegex.headerRegex(tokens, SmartRegex.pointer, "operator") > 0) {
                if (aOperators) {
                    header = new OpOverload(typedef, tokenBlock, tokens);
                } else {
                    typedef.addCleanErro("operators are not allowed here", tokenBlock.byHeader());
                    hasErro = true;
                }
            }
            //Indexador [modifiers][pointer][[]][{}]
            else if (SmartRegex.headerRegex(tokens, SmartRegex.pointer, "this", "[...]") > 0
                    || SmartRegex.headerRegex(tokens, SmartRegex.pointer, "this\\[\\]") > 0
                    || SmartRegex.bracketBeforeSplitters(tokens)) {     //Debug
                header = new Indexer(typedef, tokenBlock, tokens);
            }
            //Propriedade [modifiers][pointer][name][{}]
            else if (SmartRegex.headerRegex(tokens, SmartRegex.pointer, SmartRegex.simpleName, "{...}") > 0
                    || SmartRegex.brakeBeforeSplitters(tokens)) {       //Debug
                header = new Property(typedef, tokenBlock, tokens);
            }
            //Metodo [modifiers][pointer][methodname][()][...]
            else if (SmartRegex.headerRegex(tokens, SmartRegex.pointer, SmartRegex.methodStatement, "(...)") > 0
                    || SmartRegex.headerRegex(tokens, "void") > 0       //Debug
                    || SmartRegex.parenBeforeSplitters(tokens)) {       //Debug
                header = new Method(typedef, tokenBlock, tokens);
            }
            //Variavel [modifiers][pointer][name]([=][...])?([,][name])* (e tbm nao corresponde a nenhum outro header)
            else if (SmartRegex.headerRegex(tokens, SmartRegex.pointer, SmartRegex.variableStatement) > 0) {
                header = new Variable(typedef, tokenBlock, tokens);
            }

            //Any header
            if (header != null) {
                typedef.headers.add(header);
            }
            //Property init
            else if (lastHeader != null && lastHeader.type == Type.PROPERTY && SmartRegex.smartRegex(0, tokens, "=")) {
                ((Property) lastHeader).setInitToken(tokenBlock);
            }
            //Invalid header
            else if (!hasErro) {
                typedef.addCleanErro("invalid statement", tokenBlock.byHeader());
            }

            lastHeader = header;
        }
    }

    /**
     * Depois de fazer a divisão pelo 'Splitter' procura os blocos relacionado a linhas de comando e blocos internos
     *  @param container Bloco para leitura
     * @param token Token geral do bloco
     * @param forMod Se esta em um for
     */
    public static void parseCommands(Block container, Token token, boolean forMod) {
        ArrayList<Line> lines = container.lines;
        ArrayList<Token> tokenBlocks = getBlocks(TokenSplitter.split(token, false));

        BlockDo lastDo = null;
        boolean lastIsIf = false, lastIsTry = false;
        for (Token tokenBlock : tokenBlocks) {
            if (tokenBlock.compare(";")) {
                container.addWarning("unnecessary semicolon", tokenBlock);
                continue;
            }

            Token[] tokens = TokenSplitter.split(tokenBlock, true);
            if (tokens.length <= 0) continue;

            Token keyword, keyword2;
            if (tokens.length > 2 && !SmartRegex.isKeyword(tokens[0]) &&
                    SmartRegex.simpleName(tokens[0]) && tokens[1].compare(":")) {
                keyword = tokens[2];
                keyword2 = tokens.length > 3 ? tokens[3] : null;
            } else {
                keyword = tokens[0];
                keyword2 = tokens.length > 1 ? tokens[1] : null;
            }

            boolean lineIf = false, lineTry = false;
            Line line;
            if (forMod) {
                if (!tokens[0].compare("new") && tokens.length > 1 && !tokens[1].matches("(is)|(isnot)") &&
                        SmartRegex.headerRegex(tokens, SmartRegex.pointer, SmartRegex.variableStatement) > 0) {
                    line = new LineVar(container, tokenBlock, tokens, false);    //VARIABLE
                } else {
                    line = new LineBlock(container, tokenBlock, true, false);    //LINEBLOCK
                }
            } else if (SmartRegex.smartRegex(0, tokens, "(this)|(super)", "(...)")) {   //CONSTRUCTOR CALL
                line = new LineConstructorCall(container, tokenBlock, tokens);
            } else if (keyword.isClosedBy("{}")) {                       //BLOCK
                line = new BlockBlock(container, tokenBlock, tokens);
            } else if (keyword.compare("case")) {                           //CASE
                line = new BlockCase(container, tokenBlock, tokens);
            } else if (keyword.compare("default")) {                        //DEFAULT
                line = new BlockDefault(container, tokenBlock, tokens);
            } else if (keyword.compare("if")) {                             //IF
                line = new BlockIf(container, tokenBlock, tokens);
                lineIf = true;
            } else if (keyword.compare("else") && SmartRegex.compare(keyword2, "if")) { //ELSE IF
                line = new BlockElseIf(container, tokenBlock, tokens, lastIsIf);
                lineIf = true;
            } else if (keyword.compare("else")) {                           //ELSE
                line = new BlockElse(container, tokenBlock, tokens, lastIsIf);
            } else if (keyword.compare("try")) {                            //TRY
                line = new BlockTry(container, tokenBlock, tokens);
                lineTry = true;
            } else if (keyword.compare("catch")) {                          //CATCH
                line = new BlockCatch(container, tokenBlock, tokens, lastIsTry);
                lineTry = true; // ???
            } else if (keyword.compare("for")) {                            //FOR
                line = new BlockFor(container, tokenBlock, tokens);
            } else if (keyword.compare("switch")) {                         //SWITCH
                line = new BlockSwitch(container, tokenBlock, tokens);
            } else if (keyword.compare("do")) {                             //DO
                line = new BlockDo(container, tokenBlock, tokens);
            } else if (keyword.compare("while")) {                          //WHILE
                line = new BlockWhile(container, tokenBlock, tokens);
            } else if (keyword.compare("native")) {                         //NATIVE
                line = new BlockNative(container, tokenBlock, tokens);
            } else if (keyword.compare("lock")) {                           //LOCK
                line = new BlockLock(container, tokenBlock, tokens);
            } else if (tokens[0].compare("assert")) {                       //ASSERT
                line = new LineAssert(container, tokenBlock, tokens);
            } else if (tokens[0].compare("break")) {                        //BREAK
                line = new LineBreak(container, tokenBlock, tokens);
            } else if (tokens[0].compare("continue")) {                     //CONTINUE
                line = new LineContinue(container, tokenBlock, tokens);
            } else if (tokens[0].compare("return")) {                       //RETURN
                line = new LineReturn(container, tokenBlock, tokens);
            } else if (tokens[0].compare("throw")) {                        //THROW
                line = new LineThrow(container, tokenBlock, tokens);
            } else if (tokens[0].compare("delete")) {                       //DELETE
                line = new LineDelete(container, tokenBlock, tokens);
            } else if (!tokens[0].compare("new") && tokens.length > 1 && !tokens[1].matches("(is)|(isnot)") &&
                    SmartRegex.headerRegex(tokens, SmartRegex.pointer, SmartRegex.variableStatement) > 0) {
                line = new LineVar(container, tokenBlock, tokens, true);    //VARIABLE
            } else {
                line = new LineBlock(container, tokenBlock, true, true);    //LINEBLOCK
            }

            if (line instanceof BlockWhile) {
                if (lastDo != null) {
                    lastDo.setBlockWhile((BlockWhile) line);
                    line = null;
                }
            }
            if (lastDo != null) {
                lines.add(lastDo);
                lastDo.load();
                lastDo = null;
            }
            if (line instanceof BlockDo) {
                lastDo = (BlockDo) line;
            } else if (line != null) {
                lines.add(line);
                line.load();
                if (line instanceof LineBlock) {
                    LineBlock lineBlock = (LineBlock) line;
                    if (!lineBlock.isWrong() && (lineBlock.isTypedefExpression() || lineBlock.isField() ||
                            (lineBlock.isOperation() && !lineBlock.isSetOperation() && !lineBlock.isIncrementOperation()))) {
                        lineBlock.addErro("not a statement", lineBlock.getToken());
                    } else {
                        lineBlock.requestGetAcess();
                    }
                } else if (line instanceof LineConstructorCall) {
                    LineConstructorCall constCall = (LineConstructorCall) line;
                    if (!constCall.isWrong() && (container != container.getStack() ||
                            lines.size() != 1 || !container.getStack().isConstructor())) {
                        constCall.addErro("unexpected constructor call", constCall.getToken());
                    }
                }
            }

            lastIsIf = lineIf;
            lastIsTry = lineTry;
        }
        //After loop
        if (lastDo != null) {
            lines.add(lastDo);
            lastDo.load();
        }
    }

    /**
     * Divide o token em sub tokens.
     * O padrao de corte pode seguir tres modelos :
     * 1 - Ponto-e-virgula apos chave, quando iniciado por expressão lambda
     * 2 - Ponto-e-virgula apos chave, quando iniciado por "inicializador de vetor"
     * 3 - Fechar aninhamento com chave
     * 3 - Ponto-e-virgula fora de aninhamento
     * 4 - Finalizar o conteudo sem encontrar ponto de corte para os ultimos caracteres
     *
     * Observacao : Use esta função unicamente para :
     * - Namespace, using e typedefs
     * - Headers
     * - Stack
     * @param tokens Token com conteudo
     * @return ArrayList contendo todos os tokens
     */
    public static ArrayList<Token> getBlocks(Token[] tokens) {
        ArrayList<Token> textBlocks = new ArrayList<>();
        Token sToken = null;
        boolean prevStartBlock = false;
        int prevLambdaBlock = 999;
        for (int i = 0; i < tokens.length; i++) {
            Token token = tokens[i];
            if (sToken == null) {
                sToken = token;
            }
            if (token.compare(";")) {
                textBlocks.add(sToken.byAdd(token));
                sToken = null;
            } else if (token.isClosedBy("{}") &&
                    ((!prevStartBlock && prevLambdaBlock > 1) || i + 1 >= tokens.length || !tokens[i + 1].matches("(\\,)|(\\;)"))) {
                textBlocks.add(sToken.byAdd(token));
                sToken = null;
            }
            prevStartBlock = token.isClosedBy("[]") || token.isClosedBy("()");
            prevLambdaBlock += 1;
            if (token.compare("->")) {
                prevLambdaBlock = 0;
            }
        }
        if (sToken != null) {
            textBlocks.add(sToken.byAdd(tokens[tokens.length - 1]));
        }
        return textBlocks;
    }
}
