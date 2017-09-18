package com.vortex.compiler.content;

import com.vortex.compiler.data.Library;
import com.vortex.compiler.erro.ErroType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 01/10/2016
 */
public class Lexer {
    public static Charset charset = Charset.forName("UTF-8");

    public static final String letters = "_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String numbers = "0123456789";
    public static final String digits = letters + numbers;
    public static final String syntaxe = ".,;()[]{}";
    public static final String operator = "<>=+-/%&|!*?:~^#";
    public static final String space = " \t\n\u000B\f\r";

    /**
     * Faz a leitura do arquivo com uma determinada codificação, e cria um arquivo lógico interligado a bibilhoteca
     *
     * @param library Biblioteca do arquivo
     * @param file Arquivo
     * @return StringFile representando o valor do arquivo
     *
     * @throws IOException Caso algum erro de IO ocorra
     */
    public static StringFile read(Library library, File file) throws IOException {
        StringFile strFile = new StringFile(library, file.getAbsolutePath());
        String value = read(file);
        strFile.setFileValue(value);
        return strFile;
    }

    /**
     * Faz a leitura do arquivo com uma determinada codificação
     *
     * @param file Arquivo
     * @return String contendo o conteúdo do arquivo
     *
     * @throws IOException Caso algum erro de IO ocorra
     */
    public static String read(File file) throws IOException {
        String str = new String(Files.readAllBytes(file.toPath()), charset);
        return str.isEmpty() ? " " : str.replaceAll("\\r", "");
    }

    /*
     * Funções de instancia. Para cada StringFile deve haver um Lexer , permitindo o uso desta classe em vários procesos
     */
    private StringBuilder fileParse;
    private ArrayList<Integer> fileReverse;
    private String parseValue;
    private StringFile strFile;
    private int atualChar;

    public Lexer(StringFile strFile, String parseValue) {
        this.strFile = strFile;
        this.parseValue = parseValue;
    }

    public int last() {
        return get(-1);
    }

    public int atual() {
        return get(0);
    }

    public int next() {
        return get(1);
    }

    public int get(int relative) {
        if (atualChar + relative >= 0 && atualChar + relative < parseValue.length())
            return parseValue.charAt(atualChar + relative);
        return -1;
    }

    public boolean isDigit() {
        return (digits.indexOf(atual()) > -1) || (atual() == ':' && (last() == ':' ^ next() == ':'));
    }

    public boolean isOperator() {
        return operator.indexOf(atual()) > -1;
    }

    public boolean isSpace() {
        return space.indexOf(atual()) > -1;
    }

    public boolean isSyntaxe() {
        return syntaxe.indexOf(atual()) > -1;
    }

    public void append(int value) {
        fileParse.append((char)value);
        fileReverse.add(atualChar);
    }

    /**
     * Percorre o conteúdo de um arquivo removendo espaços desnessesários, marcando e ignorando caracteres inesperados
     */
    public void lexer() {
        fileParse = new StringBuilder();
        fileReverse = new ArrayList<>();

        atualChar = 0;
        int context = 0;
        boolean inverseBar = false;
        boolean lastIsDigit = false, lastIsOperator = false, firstChar;
        int chr;
        while ((chr = atual()) != -1) {
            firstChar = false;

            if (context == 0) {
                if (chr == '/' && next() == '/') {          // Line Comment
                    context = 5;
                    atualChar += 2;
                    inverseBar = false;
                    continue;
                } else if (chr == '/' && next() == '*') {   /* Block Comment */
                    context = 6;
                    atualChar += 2;
                    inverseBar = false;
                    continue;
                } else if (chr == '"') {
                    context = 1;
                    firstChar = true;
                } else if (chr == '\'') {
                    context = 2;
                    firstChar = true;
                } else if (isDigit()) {
                    if (lastIsDigit) append(' ');
                    if (lastIsOperator && chr == ':') append(' ');
                    context = 3;
                } else if (isOperator()) {
                    if (lastIsOperator) append(' ');
                    context = 4;
                } else if (isSyntaxe()) {
                    lastIsDigit = lastIsOperator = false;
                    append(chr);
                } else if (!isSpace()) {
                    strFile.addErro(ErroType.LEXER, "unexpected character", atualChar, atualChar + 1);
                }
            }
            if (context == 1) {
                append(chr);
                if (!inverseBar && chr == '"' && !firstChar) {
                    lastIsDigit = lastIsOperator = false;
                    context = 0;
                }
            } else if (context == 2) {
                append(chr);
                if (!inverseBar && chr == '\'' && !firstChar) {
                    lastIsDigit = lastIsOperator = false;
                    context = 0;
                }
            } else if (context == 3) {
                if (!isDigit()) {
                    lastIsDigit = true;
                    lastIsOperator = false;
                    atualChar -= 1;
                    context = 0;
                } else {
                    append(chr);
                }
            } else if (context == 4) {
                if (!isOperator()) {
                    lastIsDigit = false;
                    lastIsOperator = true;
                    atualChar -= 1;
                    context = 0;
                } else {
                    append(chr);
                }
            } else if (context == 5) {
                if (chr == '\n') context = 0;
            } else if (context == 6) {
                if (chr == '*' && next() == '/') {
                    atualChar += 1;
                    context = 0;
                }
            }
            inverseBar = (chr == '\\' && !inverseBar);
            atualChar += 1;
        }

        if (fileParse.length() == 0) {
            fileParse.append(' ');
            fileReverse.add(0);
            strFile.addErro(ErroType.LEXER, "logically empty file", 0, 0);
        }
        strFile.parseValue = fileParse.toString();
        strFile.reverseValue = fileReverse.stream().mapToInt(i -> i).toArray();

        fileParse = null;
        fileReverse = null;
    }
}
