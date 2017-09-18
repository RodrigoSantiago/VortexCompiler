package com.vortex.compiler.content;

import java.util.regex.Pattern;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 01/10/2016
 */
public class SmartRegex {
    public static final String[] acessTokens = new String[]{"public","internal","protected","private"};
    public static final String[] modifierTokens = new String[]{"public","internal","protected","private","final","abstract","static"};
    public static final String keywords = "("+ String.join("|", Keywords.KEYWORDS) + ")";
    public static final String acesses = "("+ String.join("|", acessTokens) + ")";
    public static final String modifiers = "("+ String.join("|", modifierTokens) + ")";
    public static final String operators = "([\\+\\-\\/\\*\\%\\~\\|\\&\\!\\=\\<\\>\\?\\:]+)|(is)|(isnot)";
    public static final String opOverloads = "(\\=)|(\\+)|(\\-)|(\\*)|(/)|(%)|(<)|(>)|(==)|(!=)|(>=)|(<=)|(>>)|(<<)|(>>>)|(\\&)|(\\|)|(\\^)|(\\~)|(\\!)|(\\+\\+)|(\\-\\-)|(cast)|(autocast)";

    public static final String simpleName = "[a-zA-Z_]+\\w*";
    public static final String spaceName = "(\\:\\:)?([a-zA-Z_]+\\w*::)*[a-zA-Z_]+\\w*";
    public static final String typedefStatement = "[a-zA-Z_]+\\w*(\\<.*\\>)?";
    public static final String methodStatement = "[a-zA-Z_]+\\w*(\\<.*\\>)?";
    public static final String typedefStatic = "(\\:\\:)?(\\w+::)*[a-zA-Z_]+\\w*";
    public static final String typedefParent = "(\\:\\:)?(\\w+::)*[a-zA-Z_]+\\w*(\\<.*\\>)?";
    public static final String pointer = "(\\:\\:)?(\\w+::)*[a-zA-Z_]+\\w*(\\<.*\\>)?(\\[\\])*";
    public static final String variableStatement = "[a-zA-Z_]+\\w*";


    /**
     * Observação importante :
     *
     * No caso de regexes que possuem multiplos '::' não é necessário verificar se cada parte é uma keyword
     * ou mesmo se começam com numero, logo porque ele vai ser usado para procurar Typedefs ou Headers ,
     * que por natureza já devem ser filtrados nesse ponto
     */

    /**
     * Verifica se o token corresponde ao regex, retorna falso caso seja nulo
     *
     * @param name CharSequence
     * @param regex String
     * @return true-false
     */
    public static boolean matches(CharSequence name, String regex){
        return name != null &&
                Pattern.matches(regex, name);
    }

    /**
     * Verifica se o token corresponde ao conteudo de 'value', retorna falso caso seja nulo
     *
     * @param name CharSequence
     * @param value CharSequence
     * @return true-false
     */
    public static boolean compare(CharSequence name, CharSequence value){
        if(name == null){
            return false;
        } else {
            int len = name.length();
            if (len == value.length()) {
                for (int i = 0; i < len; i++) {
                    if (name.charAt(i) != value.charAt(i))
                        return false;
                }
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Nomes simples
     *
     * Regex : [a-zA-Z_]+\w*
     * Exemplos validos : nome, teste123, qulquerNome , _nome
     *
     * @param name CharSequence
     * @return true-false
     */
    public static boolean simpleName(CharSequence name){
        return matches(name, simpleName);
    }

    /**
     * Nomes para nameSpace
     *
     * Regex : ([a-zA-Z_]+\w*\:\:)*[a-zA-Z_]+\w*
     * Exemplos validos : pacote, pacote::interno
     *
     * @param name CharSequence
     * @return true-false
     */
    public static boolean spaceName(CharSequence name){
        return matches(name, spaceName);
    }


    /**
     * Nomes para declaracao de typedefs
     *
     * Regex : [a-zA-Z_]+\w*(\<.*\>)?
     * Exemplos validos : ClasseA , ClasseB<T> , ClasseC<T : Integer>
     *
     * @param name CharSequence
     * @return true-false
     */
    public static boolean typedefStatement(CharSequence name){
        return matches(name, typedefStatement);
    }

    /**
     * Nomes para declaracao de metodos
     *
     * Regex : [a-zA-Z_]+\w*(\<.*\>)?
     * Exemplos validos : MetodoA , MetodoB<T> , MetodoC<T : Integer>
     *
     * @param name CharSequence
     * @return true-false
     */
    public static boolean methodStatement(CharSequence name){
        return matches(name, methodStatement);
    }

    /**
     * Nomes para declaracao de variaveis
     *
     * Regex : [a-zA-Z_]+\w
     * Exemplos validos : variavelA, variavelB123, _123
     *
     * @param name CharSequence
     * @return true-false
     */
    public static boolean variableStatement(CharSequence name){
        return matches(name, variableStatement);
    }

    /**
     * Nomes para importar typedefs como pais, e tambem em tipos genericos( que nao aceitam [] )
     *
     * Regex : (\w+::)*[a-zA-Z_]+\w*(\<.*\>)?
     * Exemplos validos : ClasseA , ClasseB<T> , ClasseC<T : Integer>
     *
     * @param name CharSequence
     * @return true-false
     */
    public static boolean typedefParent(CharSequence name){
        return matches(name, typedefParent);
    }

    /**
     * Nomes para tipos ( dentro de generics(uso) , retorno de metodos, tipos de variaveis e casting )
     *
     * Regex : (\w+::)*[a-zA-Z_]+\w*(\<.*\>)?(\[\])*
     * Exemplos validos : ClasseA , ClasseA[], pacote::ClasseA , pacote::ClasseA<T> , pacote::ClasseA<T>[]
     *
     * @param name CharSequence
     * @return true-false
     */
    public static boolean pointer(CharSequence name){
        return matches(name, pointer);
    }

    /**
     * Nomes para typedefs chamados de modo statico ( nao aceita genericos )
     *
     * Regex : ([a-zA-Z_]+\w*\:\:)*[a-zA-Z_]+\w*
     * Exemplos validos : Classe, pacote::classe , pacote::interno::Classe
     *
     * @param name CharSequence
     * @return true-false
     */
    public static boolean typedefStatic(CharSequence name){
        return matches(name, typedefStatic);
    }

    /**
     * Se possui nome valido para Operador
     *
     * =,+,-,*,/,%,>>,<<,<,>,==,>=,<=
     * @param name CharSequence
     * @return true-false
     */
    public static boolean isOperator(CharSequence name){
        return matches(name, operators);
    }

    /**
     * Se possui nome valido para Sobrecarga de Operadores
     *
     * =,+,-,*,/,%,>>,<<,<,>,==,>=,<=,cast,autocast
     * @param name CharSequence
     * @return true-false
     */
    public static boolean isOpOverload(CharSequence name){
        return matches(name, opOverloads);
    }

    /**
     * Se é uma das keywords reservadas da linguagem
     *
     * @param name CharSequence
     * @return true-false
     */
    public static boolean isKeyword(CharSequence name){
        return matches(name, keywords);
    }

    /**
     * Se possui nome válido para Acesso
     *
     * public, internal, protected, private
     *
     * @param name CharSequence
     * @return true-false
     */
    public static boolean isAcess(CharSequence name){
        return matches(name, acesses);
    }

    /**
     * Se é um modificador
     *
     * public , internal, protected, private, static, abstract , final, volatile
     *
     * @param name CharSequence
     * @return true-false
     */
    public static boolean isModifier(CharSequence name){
        return matches(name, modifiers);
    }

    /**
     * Verifica se os tokens correspondem aos valores apresentados.
     *
     * @param index Indice inicial
     * @param tokens Tokens
     * @param regexes regexes
     * @return true-false
     */
    public static boolean smartRegex(int index, Token[] tokens, String... regexes){
        if(tokens.length >= regexes.length + index ){
            for (int i = 0; i < regexes.length; i++) {
                Token token = tokens[i + index];
                if("(...)".equals(regexes[i])){
                    if (!token.isClosedBy("()")) return false;
                } else if("{...}".equals(regexes[i])){
                    if (!token.isClosedBy("{}")) return false;
                } else if("<...>".equals(regexes[i])){
                    if (!token.isClosedBy("<>")) return false;
                } else if("[...]".equals(regexes[i])){
                    if (!token.isClosedBy("[]")) return false;
                } else if(";|{...}".equals(regexes[i])) {
                    if (!token.compare(";") && !token.isClosedBy("{}")) return false;
                } else if (!token.matches(regexes[i])) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Verifica se os tokens correspondem aos valores apresentados.
     * Neste caso, ignora os primeiros tokens que sejam modificadores
     *
     * @param tokens Tokens
     * @param regexes regexes
     * @return  0 = Invalido
     *          1 = Totalmente Compativel
     *          2 = Tokens excedentes
     */
    public static int headerRegex(Token[] tokens, String... regexes) {
        int index = firstNonHeaderToken(tokens);
        if (index == -1 || tokens.length < regexes.length + index) {
            return 0;
        }
        for (int i = 0; i < regexes.length; i++) {
            Token token = tokens[i + index];
            if("(...)".equals(regexes[i])){
                if (!token.isClosedBy("()")) return 0;
            } else if("{...}".equals(regexes[i])){
                if (!token.isClosedBy("{}")) return 0;
            } else if("[...]".equals(regexes[i])){
                if (!token.isClosedBy("[]")) return 0;
            } else if("<...>".equals(regexes[i])){
                if (!token.isClosedBy("<>")) return 0;
            } else if(";|{...}".equals(regexes[i])) {
                if (!token.compare(";") && !token.isClosedBy("{}")) return 0;
            } else if (!token.matches(regexes[i])) {
                return 0;
            }
        }
        if (tokens.length == regexes.length + index) {
            return 1;       //Totalmente Compativel
        } else {
            return 2;       //Tokens excedentes
        }
    }

    /**
     * Retorna o id do primeiro token que nao é um modificador
     * (public, private, internal, protected, static, final, asbtract)
     *
     * @param values Tokens do header
     * @return -1 caso todas sejam headers
     */
    private static int firstNonHeaderToken(Token values[]){
        for (int i = 0; i < values.length; i++) {
            boolean one = false;
            for (String headertoken : modifierTokens) {
                if (values[i].compare(headertoken)) {
                    one = true;
                    break;
                }
            }
            if(!one) return i;
        }
        return -1;
    }

    public static boolean parenBeforeSplitters(Token[] tokens) {
        for(Token sToken : tokens){
            if(sToken.isClosedBy("()")) return true;
            if(sToken.isClosedBy("{}") || sToken.isClosedBy("[]")) return false;
            if(sToken.compare(".") || sToken.compare(",") || sToken.compare(";") || sToken.compare("=")) return false;
        }
        return false;
    }

    public static boolean brakeBeforeSplitters(Token[] tokens) {
        for(Token sToken : tokens){
            if(sToken.isClosedBy("{}")) return true;
            if(sToken.isClosedBy("()") || sToken.isClosedBy("[]")) return false;
            if(sToken.compare(".") || sToken.compare(",") || sToken.compare(";") || sToken.compare("=")) return false;
        }
        return false;
    }

    public static boolean bracketBeforeSplitters(Token[] tokens) {
        for(Token sToken : tokens){
            if(sToken.isClosedBy("[]")) return true;
            if(sToken.isClosedBy("{}") || sToken.isClosedBy("()")) return false;
            if(sToken.compare(".") || sToken.compare(",") || sToken.compare(";") || sToken.compare("=")) return false;
        }
        return false;
    }
}
