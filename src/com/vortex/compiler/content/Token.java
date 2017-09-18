package com.vortex.compiler.content;

import java.util.regex.Pattern;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 01/10/2016
 */
public class Token implements CharSequence {
    final protected StringFile strFile;
    final private int startPos;
    final private int endPos;

    public Token(StringFile strFile, int startPos, int endPos) {
        this.strFile = strFile;
        this.startPos = startPos;
        this.endPos = endPos;
    }

    public Token(Token token, int startPos, int endPos) {
        this.strFile = token.strFile;
        this.startPos = startPos;
        this.endPos = endPos;
    }

    public StringFile getStringFile(){
        return strFile;
    }
    /**
     * Posição inicial relativo ao 'conteúdo convertido'
     */
    public int getStartPos() {
        return startPos;
    }

    /**
     * Posição final relativa ao 'conteúdo convertido'
     */
    public int getEndPos() {
        return endPos;
    }

    /**
     * Posição inicial relativo ao 'conteúdo real'
     */
    public int fileStartpos() {
        return strFile.reverseValue[startPos];
    }

    /**
     * Posição final relativa ao 'conteúdo real'
     */
    public int fileEndpos() {
        return startPos == endPos ?
                strFile.reverseValue[startPos] :
                strFile.reverseValue[endPos - 1] + 1;
    }

    /**
     * Verifica se o token está vazio
     *
     * @return true-false
     */
    public boolean isEmpty() {
        return startPos == endPos || startPos - endPos == 1 && charAt(0) == ' ';
    }

    /**
     * Verifica se o string contem as mesmas letras que este token
     *
     * @param other String para comparacao
     * @return true-false
     */
    public boolean compare(CharSequence other) {
        int len = length();
        if (len == other.length()) {
            for (int i = 0; i < len; i++) {
                if (charAt(i) != other.charAt(i))
                    return false;
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Verifica se o conteudo inteiro deste token corresponde ao regex
     *
     * @param regex String contendo o regex
     * @return true-false
     */
    public boolean matches(String regex) {
        return regex != null && Pattern.matches(regex, this);
    }

    /**
     * Verifica se o conteudo inicial deste token corresponde ao conteudo dos caracteres passados
     *
     * @param chars Caracteres
     * @return true-false
     */
    public boolean startsWith(CharSequence chars) {
        if (chars == null || chars.length() > length()) return false;
        for (int i = 0; i < chars.length(); i++) {
            if (charAt(i) != chars.charAt(i)) return false;
        }
        return true;
    }

    public boolean startsWith(char chr){
        return length() > 0 && charAt(0) == chr;
    }

    /**
     * Verifica se o conteudo finaal deste token corresponde ao conteudo dos caracteres passados
     *
     * @param chars Caracteres
     * @return true-false
     */
    public boolean endsWith(CharSequence chars) {
        if (chars == null || chars.length() > length()) return false;
        int sPos = length() - chars.length();
        for (int i = sPos; i < length(); i++) {
            if (charAt(i) != chars.charAt(i - sPos)) return false;
        }
        return true;
    }

    public boolean endsWith(char chr){
        return length() > 0 && charAt(length()-1) == chr;
    }

    /**
     * Retorna a posicao da primeira ocorrencia relativa a posicao inicial (startPos)
     *
     * @param chr Caractere
     * @return -1  caso nao ache nenhuma correspondencia
     */
    public int indexOf(char chr) {
        for (int i = 0; i < length(); i++) {
            if (charAt(i) == chr) return i;
        }
        return -1;
    }

    public int indexOf(CharSequence chars) {
        if (chars == null || chars.length() > length()) return -1;
        int lastCombination = -1;
        int fLen = length() - chars.length();
        for (int i = 0; i <= fLen; i+=1) {
            boolean found = true;
            for (int j = 0; j < chars.length(); j++) {
                if (charAt(i + j) != chars.charAt(j)) {
                    found = false;
                    break;
                }
            }
            if (found) {
                lastCombination = i;
                break;
            }
        }
        return lastCombination;
    }

    /**
     * Retorna a posição da ultima ocorrencia relativa a posição inicial (startPos)
     *
     * @param chr Caractere
     * @return -1  caso nao ache nenhuma correspondencia
     */
    public int lastIndexOf(char chr) {
        for (int i = length() - 1; i >= 0; i--) {
            if (charAt(i) == chr) return i;
        }
        return -1;
    }

    public int lastIndexOf(CharSequence chars) {
        if (chars == null || chars.length() > length()) return -1;
        int lastCombination = -1;
        for (int i = length() - chars.length(); i >= 0; i--) {
            boolean found = true;
            for (int j = 0; j < chars.length(); j++) {
                if (charAt(i + j) != chars.charAt(j)) {
                    found = false;
                    break;
                }
            }
            if (found) {
                lastCombination = i;
                break;
            }
        }
        return lastCombination;
    }

    @Override
    public int length() {
        return endPos - startPos;
    }

    @Override
    public char charAt(int index) {
        return strFile.parseValue.charAt(index + startPos);
    }

    @Override
    public Token subSequence(int start, int end) {
        return new Token(strFile, startPos + start, startPos + end);
    }

    public Token subSequence(int start) {
        return new Token(strFile, startPos + start, endPos);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        return strFile.parseValue.substring(startPos, endPos);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj == this) return true;
        if (obj instanceof Token) {
            Token other = ((Token) obj);
            if (other.strFile == strFile && other.startPos == startPos && other.endPos == endPos) {
                return true;
            } else {
                int len = length();
                if (len == other.length()) {
                    for (int i = 0; i < len; i++) {
                        if (strFile.parseValue.charAt(i + startPos) !=
                                other.strFile.parseValue.charAt(i + other.startPos))
                            return false;
                    }
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    //Utilitários externos
    public Token byNested(){
        return subSequence(1, length()-1);
    }

    public Token byAdd(Token other){
        return new Token(strFile, startPos, other.endPos);
    }

    public Token byLastChar(){
        return length() <= 1 ? this : new Token(strFile, endPos - 1, endPos);
    }

    public Token byHeader(){
        int index = indexOf('{');
        if (index != -1) {
            return new Token(strFile, startPos, startPos + index);
        } else {
            return this;
        }
    }

    public Token byContent() {
        int hasC = indexOf('{');
        if (hasC == -1) return null;
        int hasD = lastIndexOf('}');
        if (hasD == -1) return null;
        return subSequence(hasC + 1, hasD);
    }

    public boolean isClosedBy(String dualChar) {
        return startsWith(dualChar.charAt(0)) && endsWith(dualChar.charAt(1));
    }

    /**
     * Verifica se este token e fechado por parentesis validos( fora de string e aninhados corretamente )
     *
     * @return true-false
     */
    @Deprecated
    public boolean isNestedClosed(char startChar, char endChar) {
        if (!startsWith(startChar) || !endsWith(endChar)) return false;

        int pos = 0;        //Posicao de aninhamento
        int context = 0;    //Contexto de String/Char
        boolean inverseBar = false; //Estado de barra invertida
        char chr;
        for (int i = startPos; i < endPos; i++) {
            chr = strFile.parseValue.charAt(i);
            if (context == 0) {
                if (chr == '"') {
                    context = 1;
                } else if (chr == '\'') {
                    context = 2;
                }
            } else {
                if (context == 1 && chr == '"' && !inverseBar) {
                    context = 0;
                } else if (context == 2 && chr == '\'' && !inverseBar) {
                    context = 0;
                }
            }
            inverseBar = (chr == '\\' && !inverseBar);
            if (context > 0) continue;

            if (chr == startChar) pos += 1;
            if (chr == endChar) {
                pos -= 1;
                if (pos == 0) return i == endPos - 1;
            }
        }
        return pos == 0;
    }
}
