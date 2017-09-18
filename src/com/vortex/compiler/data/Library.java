package com.vortex.compiler.data;

import com.vortex.compiler.content.Parser;
import com.vortex.compiler.content.StringFile;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.space.NameSpace;
import com.vortex.compiler.logic.typedef.Typedef;
import javafx.beans.property.DoubleProperty;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 02/10/2016
 */
public class Library {

    public final String name;
    public final int[] version;
    public final Dependence[] dependences;
    protected final ArrayList<StringFile> strFiles = new ArrayList<>();
    protected final ArrayList<Typedef> typedefs = new ArrayList<>();
    protected final ArrayList<NameSpace> nameSpaces = new ArrayList<>();
    protected boolean cancel;

    public Library(String name, int[] version, Dependence[] dependences) {
        this.name = name;
        this.version = version;
        this.dependences = dependences;
    }

    public ArrayList<StringFile> getFiles() {
        return (ArrayList<StringFile>) strFiles.clone();
    }

    public void fileAdd(StringFile strFile) {
        this.strFiles.add(strFile);
    }

    public void fileAdd(StringFile... strFiles) {
        for (StringFile strFile : strFiles) {
            fileAdd(strFile);
        }
    }

    public void fileAdd(ArrayList<StringFile> strFiles) {
        this.strFiles.addAll(strFiles);
    }

    public void fileRemove(String filePath) {
        StringFile strFile = getFile(filePath);
        if (strFile != null) {
            fileRemove(strFile);
        }
    }

    public void fileRemove(StringFile strFile) {
        strFiles.remove(strFile);
    }

    public StringFile getFile(String filePath) {
        filePath = filePath.replaceAll("\\\\","/");
        for (StringFile strFile : strFiles) {
            if (filePath.equals(strFile.getFilePath())) {
                return strFile;
            }
        }
        return null;
    }

    public boolean isBuildable() {
        for (StringFile strFile : strFiles) {
            if (strFile.hasErros()) return false;
        }
        return true;
    }

    public void projectReload(boolean makeFile, DoubleProperty progress) {
        filesRead(progress, 1 / 6d);
        filesPreload(progress, 1 / 6d);
        filesLoad(progress, 1 / 6d);
        filesInternalLoad(progress, 1 / 6d);
        filesCrossLoad(progress, 1 / 6d);
        if (makeFile) filesMake(progress, 1 / 6d);
        progressSet(progress, 1);
    }

    public String[] projectExport(DoubleProperty progress, boolean asLib) {
        if (this != DataBase.defaultLibrary) {
            filesRead(progress, 1 / 7d);
            filesPreload(progress, 1 / 7d);
            filesLoad(progress, 1 / 7d);
            filesInternalLoad(progress, 1 / 7d);
            filesCrossLoad(progress, 1 / 7d);
            filesMake(progress, 1 / 7d);
        } else {
            progressSet(progress, 1 / 7d * 6d);
        }

        return filesBuild(progress, 1 / 7d, asLib);
    }

    public void filesRead(DoubleProperty progress, double value) {
        double progressVal = value / (strFiles.size() + 1);
        clear();

        //Parser
        for (StringFile strFile : strFiles) {
            strFile.clearReadyData();
            Parser.parseTypedefs(strFile, strFile.getToken());
            typedefs.addAll(strFile.typedefs);
            nameSpaces.add(strFile.workspace.getNameSpace());

            progressAdd(progress, progressVal);
        }

        dataBaseInsert();
        progressAdd(progress, progressVal);
    }

    public void filesPreload(DoubleProperty progress, double value) {
        double progressVal = value / (strFiles.size() + typedefs.size() + 1);

        //Workspace Load ( remove usings errados )
        for (StringFile strFile : strFiles) {
            strFile.workspace.load();

            if (progress != null) progress.add(progressVal);
        }

        //Preload
        for (Typedef typedef : typedefs) {
            typedef.preload();
            progressAdd(progress, progressVal);
        }

        progressAdd(progress, progressVal);
    }

    public void filesLoad(DoubleProperty progress, double value) {
        double progressVal = value / (typedefs.size() + 1);

        //Load
        for (Typedef typedef : typedefs) {
            typedef.load();
            progressAdd(progress, progressVal);
        }

        progressAdd(progress, progressVal);
    }

    public void filesInternalLoad(DoubleProperty progress, double value) {
        double progressVal = value / (typedefs.size() + 1);

        //Main  Load
        for (StringFile strFile : strFiles) {
            if (strFile.isMain() && strFile.mainMethod != null) strFile.mainMethod.mainLoad(strFile.workspace);
        }

        //Internal Load
        for (Typedef typedef : typedefs) {
            typedef.internalLoad();
            progressAdd(progress, progressVal);
        }

        progressAdd(progress, progressVal);
    }

    public void filesCrossLoad(DoubleProperty progress, double value) {
        double progressVal = value / (typedefs.size() + 1);

        //Crossload
        for (Typedef typedef : typedefs) {
            typedef.crossLoad();
            progressAdd(progress, progressVal);
        }

        progressAdd(progress, progressVal);
    }

    public void filesMake(DoubleProperty progress, double value) {
        double progressVal = value / (strFiles.size() + typedefs.size() + 1);

        //Workspace CrossLoad ( remove usings staticos errados ) -> apenass logico, sera usado no makefiles
        for (StringFile strFile : strFiles) {
            strFile.workspace.crossLoad();
            progressAdd(progress, progressVal);
        }

        //Main make
        for (StringFile strFile : strFiles) {
            if (strFile.isMain() && strFile.mainMethod != null && !strFile.mainMethod.isWrong()) {
                strFile.mainMethod.make();
            }
        }

        //internal make
        for (Typedef typedef : typedefs) {
            typedef.internalMake();
            progressAdd(progress, progressVal);
        }

        progressAdd(progress, progressVal);
    }

    public String[] filesBuild(DoubleProperty progress, double value, boolean asLib) {
        if (isBuildable()) {
            double progressVal = value / (typedefs.size() + 1);
            int filesCount = typedefs.size() * 2 + (asLib ? 0 : 1);

            String[] values = new String[filesCount];

            CppBuilder cBuilder = new CppBuilder();
            //Build
            for (int i = 0; i < typedefs.size(); i++) {
                Typedef typedef = typedefs.get(i);
                cBuilder.reset();
                typedef.build(cBuilder);
                values[i * 2] = cBuilder.getHeader();
                values[i * 2 + 1] = cBuilder.getSource();

                progressAdd(progress, progressVal);
            }

            if (!asLib) {
                for (StringFile strFile : strFiles) {
                    if (strFile.isMain() && strFile.mainMethod != null) {
                        cBuilder.reset();
                        //Source
                        cBuilder.toSource();
                        cBuilder.add("//main.cpp").ln();

                        cBuilder.markSource();
                        cBuilder.ln()
                                .add("int global_id = 0;").ln();

                        strFile.mainMethod.build(cBuilder);

                        //Dependences
                        cBuilder.sourceDependences(strFile.mainMethod.getContainer());

                        values[typedefs.size() * 2] = cBuilder.getSource();
                        break;
                    }
                }
            }

            progressSet(progress, 1);

            return values;
        } else {
            return null;
        }
    }

    public void filesClearMake(){
        //todo - implementar
        /*for (Typedef typedef : typedefs) {
            typedef.clearMake();
        }*/
    }

    public void dataBaseInsert() {
        for (Typedef typedef : typedefs) {
            DataBase.typedefAdd(typedef);
        }
        for (NameSpace nameSpace : nameSpaces) {
            DataBase.namespaceAdd(nameSpace);
        }
    }

    public void dataBaseRemove() {
        for (Typedef typedef : typedefs) {
            DataBase.typedefRemove(typedef);
        }
        for (NameSpace nameSpace : nameSpaces) {
            DataBase.namespaceRemove(nameSpace);
        }
    }

    public void clear(){
        dataBaseRemove();
        typedefs.clear();
        nameSpaces.clear();
    }

    public static void LibrariesReload(DoubleProperty progress, Library... libraries)  {
        for (Library lib : libraries) {
            lib.filesRead(progress, 1 / (6d * libraries.length));
        }
        for (Library lib : libraries) {
            lib.filesPreload(progress, 1 / (6d * libraries.length));
        }
        for (Library lib : libraries) {
            lib.filesLoad(progress, 1 / (6d * libraries.length));
        }
        for (Library lib : libraries) {
            lib.filesInternalLoad(progress, 1 / (6d * libraries.length));
        }
        for (Library lib : libraries) {
            lib.filesCrossLoad(progress, 1 / (6d * libraries.length));
        }
        for (Library lib : libraries) {
            lib.filesMake(progress, 1 / (6d * libraries.length));
        }
        progressSet(progress, 1);
    }

    private void progressAdd(DoubleProperty progress, double value) {
        if (progress != null) progress.add(value);
    }

    private static void progressSet(DoubleProperty progress, double value) {
        if (progress != null) progress.set(value);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        String ret = " Library - " + name + "  " + Arrays.toString(version) + " ";
        for (StringFile strFile : strFiles) {
            ret += "\n    " + strFile;
        }
        return ret;
    }
}
