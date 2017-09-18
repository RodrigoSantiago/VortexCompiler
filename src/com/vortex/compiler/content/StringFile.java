package com.vortex.compiler.content;

import com.vortex.compiler.data.Library;
import com.vortex.compiler.logic.LogicToken;
import com.vortex.compiler.erro.Erro;
import com.vortex.compiler.erro.ErroType;
import com.vortex.compiler.logic.header.Method;
import com.vortex.compiler.logic.space.Workspace;
import com.vortex.compiler.logic.typedef.Typedef;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 01/10/2016
 */
public class StringFile {

    public Library library;
    public Workspace workspace;
    public ArrayList<Typedef> typedefs = new ArrayList<>();
    public Method mainMethod;

    public ArrayList<Erro> erros = new ArrayList<>();
    public ArrayList<Erro> warnings = new ArrayList<>();
    public ArrayList<Erro> lexerErros = new ArrayList<>();

    String filePath;        //Arquivo que o objeto  representa
    String fileValue;       //Conteúdo do arquivo inteiro, ou um novo valor alterado
    String parseValue;      //Conteúdo do arquivo inteiro convertido para facilitar a leitura lógica
    int[] reverseValue;    //Indices invertidos para localizar a posição do parseValue no fileValue

    private boolean main;

    public StringFile(Library library, String filePath) {
        this.library = library;
        this.filePath = filePath.replaceAll("\\\\", "/");
    }

    public StringFile(Library library, String filePath, String fileValue) {
        this.library = library;
        this.filePath = filePath.replaceAll("\\\\", "/");
        setFileValue(fileValue);
    }

    public StringFile(StringFile strFile) {
        this.library = strFile.library;
        this.filePath = strFile.filePath;
        copyValues(strFile);
        copyReadyData(strFile);
    }

    /**
     * Copia os valores de texto de outro StringFile , incluindo os erros de lexer
     *
     * @param other StringFile do qual será copiado os valores
     */
    public void copyValues(StringFile other) {
        fileValue = other.fileValue;
        parseValue = other.parseValue;
        reverseValue = other.reverseValue;
        main = other.main;
        lexerErros.clear();
        lexerErros.addAll(other.lexerErros);
    }

    /**
     * Copia valoreslógicos de outro StringFile, incluindo erros e alertas
     *
     * @param other StringFile do qual será copiado os valores
     */
    public void copyReadyData(StringFile other) {
        workspace = other.workspace;
        mainMethod = other.mainMethod;

        typedefs.clear();
        typedefs.addAll(other.typedefs);

        erros.clear();
        erros.addAll(other.erros);

        warnings.clear();
        warnings.addAll(other.warnings);
    }

    /**
     * Limpa todos os dados lógicos
     */
    public void clearReadyData() {
        workspace = null;
        mainMethod = null;
        typedefs.clear();
        erros.clear();
        warnings.clear();
    }

    /**
     * Verifica se um caractere na posição  referida está dentro dos caracteres lógicos
     *
     * @param pos Posição para verificação
     * @return true-false
     */
    public boolean isComment(int pos) {
        for (int i = 0; i < reverseValue.length; i++) {
            if (reverseValue[i] == pos) {
                return false;
            }
            if (reverseValue[i] > pos) {
                return true;
            }
        }
        return true;
    }

    /**
     * Verifica se um caractere na posição referida está dentro de comentários
     * Aprimorado para posições consecutivas
     *
     * @param sPos Posição inicial
     * @param ePos Posição final
     * @return true-false
     */
    public boolean isComment(int sPos, int ePos) {
        if (sPos == ePos) return isComment(sPos);
        for (int i = 0; i < reverseValue.length; i++) {
            if (reverseValue[i] == sPos) {
                return false;
            }
            if (reverseValue[i] == ePos - 1) {
                return false;
            }
            if (reverseValue[i] > ePos - 1) {
                return true;
            }
        }
        return true;
    }

    public int convertPos(int filePos) {
        for (int i = 0; i < reverseValue.length; i++) {
            if (reverseValue[i] == filePos) {
                return i;
            }
            if (reverseValue[i] > filePos) {
                return Math.max(0, i - 1);
            }
        }
        return -1;
    }

    //##################
    //#  Propriedades  #
    //##################

    public void setMain(boolean main) {
        this.main = main;
    }

    public boolean isMain() {
        return this.main;
    }

    public void setFileValue(String fileValue) {
        this.fileValue = fileValue;
        lexerErros.clear();
        new Lexer(this, fileValue).lexer();
    }

    public Token getToken() {
        return new Token(this, 0, parseValue.length());
    }

    public String getFileValue() {
        return this.fileValue;
    }

    public String getParseValue() {
        return this.parseValue;
    }

    public int[] getReverseValue() {
        return this.reverseValue;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFilePath() {
        return this.filePath;
    }

    public boolean hasErros() {
        return erros.size() > 0 || lexerErros.size() > 0;
    }

    public boolean hasWarning() {
        return warnings.size() > 0;
    }

    public void addErro(Erro erro) {
        if (erro.isWarning()) {
            warnings.add(erro);
        } else if (erro.isLexer()) {
            lexerErros.add(erro);
        } else if (erro.isErro()) {
            erros.add(erro);
        }
    }

    public void addErro(ErroType type, String text, int startPos, int endPos) {
        if (type == ErroType.LEXER) {
            addErro(new Erro(type, text, startPos, endPos));
        } else {
            addErro(new Erro(type, text, new Token(this, startPos, endPos)));
        }
    }

    public void addErro(ErroType type, String text, Token token) {
        addErro(new Erro(type, text, token));
    }

    public void addErro(ErroType type, String text, LogicToken logicToken) {
        addErro(new Erro(type, text, logicToken));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj == this) return true;
        if (obj instanceof StringFile) {
            StringFile other = ((StringFile) obj);
            return (other.filePath == null && filePath == null) || (filePath != null && filePath.equals(other.filePath));
        }
        return false;
    }

    @Override
    public String toString() {
        String ret = "StringFile - " + filePath + " \n        " + workspace;
        for (Typedef typedef : typedefs) {
            ret += "\n        " + typedef;
        }
        return ret;
    }
}
