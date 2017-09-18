package com.vortex.compiler.content;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 01/10/2016
 */
public class TokenSplitter {
    public static final int STATEMENT = 0;      //Declaracão de genericos(typedef e metodo)
    public static final int ARGUMENTS = 1;      //Argumentos (chamada de metodo, construtor, array)

    public static final String letters = "_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String numbers = "0123456789";
    public static final String digits = letters + numbers;
    public static final String openers = "<([{";
    public static final String closers = ">)]}";
    public static final String operator = "<>=+-/%&|!*?:~^";
    public static final String space = " \t\n\u000B\f\r";   // Todos foram convertidos para ' '
    public static final String invalidGeneric = "=+-/%&|!*?~^.;(){}:";    //Validos : digitos|space|<>|,|
    public static final String invalidGenericS = "=+-/%&|!*?~^.;(){}";    //Validos : digitos|space|<>|,|:|

    public Token token;
    public Token lastToken;

    private int atualPos;
    private String genericExp;
    private boolean insideArguments;

    /*  Dentro de um metodo e possivel causar uma ambiguidade, ex : metodo( a<b,c>(d) );
        So sera considerado generico se houver a palavra NEW
        ex : metodo( new a<b,c>(d) ); um parametro, com generico
        ex : metodo( a<b,c>(d) ); 2 parametros booleanos
    */
    private int lastNew = -1;

    public TokenSplitter(Token token) {
        this.token = token;
        genericExp = invalidGeneric;
    }

    public TokenSplitter(Token token, int exception) {
        this.token = token;
        if (exception == -1) {
            genericExp = invalidGeneric;
        } else if (exception == STATEMENT) {
            genericExp = invalidGenericS;
        } else {
            genericExp = invalidGeneric;
            insideArguments = true;
        }
    }

    /**
     * Realiza o split em todos os tokens e cria um array de subtokens
     *
     * @param token       Token para divisao
     * @param retainNames Se deixa nomes e arrays juntos ( nome<>, nome[], nome<>[] )
     * @param exception   Mudanca especial para Declaracoes e para Linha de argumentos
     * @return Tokens[]
     */
    public static Token[] split(Token token, boolean retainNames, int exception) {
        ArrayList<Token> tokens = new ArrayList<>();
        TokenSplitter spl = new TokenSplitter(token, exception);
        if (retainNames) {
            boolean lastIsName = false, lastHasGen = false, lastHasArray = false;
            for (Token sToken = spl.getNext(); sToken != null; sToken = spl.getNext()) {
                if (lastIsName && sToken.startsWith("<") && sToken.endsWith(">") && !lastHasArray && !lastHasGen) {
                    lastHasGen = true;
                    tokens.set(tokens.size() - 1, tokens.get(tokens.size() - 1).byAdd(sToken));
                } else if (lastIsName && sToken.compare("[]")) {
                    lastHasArray = true;
                    tokens.set(tokens.size() - 1, tokens.get(tokens.size() - 1).byAdd(sToken));
                } else {
                    lastIsName = SmartRegex.simpleName(sToken);
                    lastHasArray = false;
                    lastHasGen = false;
                    tokens.add(sToken);
                }
            }
        } else {
            for (Token sToken = spl.getNext(); sToken != null; sToken = spl.getNext()) {
                tokens.add(sToken);
            }
        }
        return tokens.toArray(new Token[tokens.size()]);
    }

    public static Token[] split(Token token, boolean retainNames) {
        return split(token, retainNames, -1);
    }

    public static Token[] split(Token token) {
        return split(token, false, -1);
    }

    public Token getNext() {
        if (eof()) return null;

        if (charAt(atualPos) == ' ') {
            atualPos++;
        }

        int startPos = atualPos;
        /*
        Apenas o primeiro tipo de opener. Por exemplo :
         ( [ )   - Neste caso ele compreende que existe apenas um opener e closer validos
         ((][)]) - Neste caso ele compreende que existe apenas 2 openers e closers validos

        Armazena o numero relativo a cada um :
           1 - <
           2 - (
           3 - [
           4 - {
         */
        int ctxOpener = -1;
        int oPos = -1;      //Posicao na do primeiro opener
        int nPos = 0;       //Posicao de aninhamento
        /*
        Contexto
        0 = Nenhum
        1 = String
        2 = Character
        3 = Alfanumerico
        4 = Operador
         */
        int context = 0;
        boolean inverseBar = false;

        int chr;
        for (; atualPos < token.length(); atualPos += 1) {
            chr = charAt(atualPos);
            if (context == 0) {
                if (chr == '"') {
                    context = 1;
                } else if (chr == '\'') {
                    context = 2;
                } else if (isDigit(chr) || is2Colon(atualPos) || isDotNumber(atualPos)) {
                    context = 3;
                } else if (isOperator(chr)) {
                    context = 4;
                } else if (nPos <= 0 && openers.indexOf(chr) == -1) {
                    break;
                }
            } else if (context == 1) {
                if (chr == '"' && !inverseBar) {
                    context = 0;
                    if (nPos <= 0) break;
                }
            } else if (context == 2) {
                if (chr == '\'' && !inverseBar) {
                    context = 0;
                    if (nPos <= 0) break;
                }
            } else if (context == 3) {
                if (!isDigit(chr) && !is2Colon(atualPos) && !isDotNumber(atualPos)) {
                    context = 0;
                    atualPos -= 1;
                    if (nPos <= 0) {
                        break;
                    } else {
                        continue;
                    }
                }
            } else if (context == 4) {
                if (!isOperator(chr)) {
                    context = 0;
                    atualPos -= 1;
                    if (nPos <= 0) {
                        break;
                    } else {
                        continue;
                    }
                }
            }
            //Ignora dentro de Strings e Caracteres
            if (context != 1 && context != 2) {
                if (ctxOpener == -1) {
                    if ((ctxOpener = openers.indexOf(chr)) != -1) {
                        oPos = atualPos;
                        nPos = 1;
                    }
                } else {
                    //Invalida o generico para operador
                    if (ctxOpener == 0 && ((!is2Colon(atualPos) && genericExp.indexOf(chr) != -1) ||
                            (chr == ',' && nPos == 1 && (insideArguments && lastNew != 1)))) {
                        atualPos = oPos;
                        context = 4;
                        ctxOpener = -1;
                        oPos = -1;
                        nPos = 0;
                    } else {
                        if (openers.indexOf(chr) == ctxOpener) {
                            nPos += 1;
                        } else if (closers.indexOf(chr) == ctxOpener) {
                            nPos -= 1;
                            if (nPos <= 0) break;
                        }

                    }
                }
            } else {
                inverseBar = (chr == '\\' && !inverseBar);
            }
            //Invalida o generico para operador quando chega-se ao fim do token mas nao foi fechado
            if (atualPos == token.length() - 1 && ctxOpener == 0) {
                atualPos = oPos;
                context = 4;
                ctxOpener = -1;
                oPos = -1;
                nPos = 0;
            }
        }

        if (atualPos == token.length()) {
            lastToken = token.subSequence(startPos, atualPos);
        } else {
            lastToken = token.subSequence(startPos, ++atualPos);
        }

        if (lastToken.compare("new") || lastToken.compare("stack")) {
            lastNew = 0;
        } else {
            if (lastNew != -1) lastNew += 1;
        }
        return lastToken;
    }

    /**
     * Retorna o ultimo token
     *
     * @return Token
     */
    public Token get() {
        return lastToken == null ? getNext() : lastToken;
    }

    public boolean eof() {
        if (atualPos >= token.length()) {
            return true;
        } else if (atualPos == token.length() - 1 && charAt(atualPos) == ' ') {
            atualPos += 1;
            return true;
        }
        return false;
    }

    public int charAt(int relativePos) {
        return relativePos >= 0 && relativePos < token.length() ? token.strFile.parseValue.charAt(token.getStartPos() + relativePos) : -1;
    }

    private boolean isDigit(int chr) {
        return (chr >= 48 && chr <= 57) || (chr >= 65 && chr <= 90) || (chr >= 97 && chr <= 122) || chr == '_';
    }

    private boolean isOperator(int chr) {
        return chr == '<' || chr == '>' || chr == '=' || chr == '+' || chr == '-' || chr == '*' || chr == '/' ||
                chr == '%' || chr == '&' || chr == '|' || chr == '!' || chr == '?' || chr == ':' ||
                chr == '~' || chr == '^';
    }

    private boolean isNumber(int chr) {
        return (chr >= 48 && chr <= 57);
    }

    private boolean is2Colon(int index) {
        return charAt(index) == ':' && (charAt(index + 1) == ':' ^ charAt(index - 1) == ':');
    }

    private boolean isDotNumber(int index) {
        int chr = charAt(index), chrB = charAt(index - 1);
        return (chr == '.' && isNumber(charAt(index + 1))) ||
                ((chr == '-' || chr == '+') && (chrB == 'e' || chrB == 'E') && isNumber(charAt(index - 2)));
    }
}
