package com.vortex.compiler.data;

import com.vortex.compiler.content.StringFile;
import com.vortex.compiler.logic.typedef.Typedef;
import javafx.beans.property.DoubleProperty;

import java.util.ArrayList;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 30/12/2016
 */
public class VolatileLibrary extends Library {

    protected final Object filesLock = new Object();
    protected final Object compileLock = new Object();
    protected final ArrayList<StringFile> innerFiles = new ArrayList<>();

    public VolatileLibrary(String name, int[] version, Dependence[] dependences) {
        super(name, version, dependences);
    }

    public ArrayList<StringFile> getFiles() {
        synchronized (filesLock) {
            return (ArrayList<StringFile>) innerFiles.clone();
        }
    }

    @Override
    public void fileAdd(StringFile strFile) {
        synchronized (filesLock) {
            innerFiles.add(strFile);
        }
    }

    @Override
    public void fileAdd(StringFile... strFiles) {
        synchronized (filesLock) {
            for (StringFile strFile : strFiles) {
                innerFiles.add(strFile);
            }
        }
    }

    @Override
    public void fileAdd(ArrayList<StringFile> strFiles) {
        synchronized (filesLock) {
            for (StringFile strFile : strFiles) {
                innerFiles.add(strFile);
            }
        }
    }

    @Override
    public void fileRemove(String filePath) {
        StringFile strFile = getFile(filePath);
        if (strFile != null) {
            fileRemove(strFile);
        }
    }

    @Override
    public void fileRemove(StringFile strFile) {
        synchronized (filesLock) {
            innerFiles.remove(strFile);
        }
    }

    @Override
    public StringFile getFile(String filePath) {
        filePath = filePath.replaceAll("\\\\","/");
        synchronized (filesLock) {
            for (StringFile strFile : innerFiles) {
                if (filePath.equals(strFile.getFilePath())) {
                    return strFile;
                }
            }
        }
        return null;
    }

    @Override
    public boolean isBuildable() {
        synchronized (filesLock) {
            for (StringFile strFile : innerFiles) {
                if (strFile.hasErros()) return false;
            }
        }
        return true;
    }

    @Override
    public void projectReload(boolean makeFile, DoubleProperty progress) {
        synchronized (compileLock) {
            syncToFiles();
            super.projectReload(makeFile, progress);
            syncToInner();
        }
    }

    @Override
    public String[] projectExport(DoubleProperty progress, boolean asLib) {
        synchronized (compileLock) {
            syncToFiles();
            String[] textFiles;
            textFiles = super.projectExport(progress, asLib);

            syncToInner();
            return textFiles;
        }
    }

    public void projectReload(String[] filePaths, DoubleProperty progress) {
        synchronized (compileLock) {
            syncToFiles();
            for (int i = 0; i < filePaths.length; i++) {
                filePaths[i] = filePaths[i].replaceAll("\\\\", "/");
            }

            super.projectReload(false, progress);

            for (StringFile strFile : strFiles) {
                if (containsFile(strFile.getFilePath(), filePaths)) {
                    strFile.workspace.crossLoad();

                    if (strFile.isMain() && strFile.mainMethod != null && !strFile.mainMethod.isWrong()) {
                        strFile.mainMethod.make();
                    }

                    for (Typedef typedef : strFile.typedefs) {
                        typedef.internalMake();
                    }
                }
            }
            syncToInner();
        }
    }

    @Override
    public void filesClearMake() {
        //todo - implement
    }


    public void fileSetValue(String filePath, String fileValue) {
        StringFile strFile = getFile(filePath);
        if (strFile != null) {
            strFile.setFileValue(fileValue);
        }
    }

    private void syncToInner() {
        //compile files >>send data to>> inner files
        synchronized (filesLock) {
            for (int i = 0; i < strFiles.size(); i++) {
                StringFile strFile = strFiles.get(i);
                StringFile innerFile = getFile(strFile.getFilePath());
                if (innerFile == null) {
                    strFiles.remove(i--);
                } else {
                    innerFile.copyReadyData(strFile);
                }
            }
            for (StringFile innerFile : innerFiles) {
                if (!strFiles.contains(innerFile)) {
                    strFiles.add(new StringFile(innerFile));
                }
            }
        }
    }

    private void syncToFiles() {
        //inner files  >>send data to>> compile files
        synchronized (filesLock) {
            for (int i = 0; i < strFiles.size(); i++) {
                StringFile strFile = strFiles.get(i);
                StringFile innerFile = getFile(strFile.getFilePath());
                if (innerFile == null) {
                    strFiles.remove(i--);
                } else {
                    strFile.copyValues(innerFile);
                }
            }
            for (StringFile innerFile : innerFiles) {
                if (!strFiles.contains(innerFile)) {
                    strFiles.add(new StringFile(innerFile));
                }
            }
        }
    }

    private boolean containsFile(String pathName, String[] filePath) {
        for (String string : filePath) {
            if (pathName.endsWith(string)) {
                return true;
            }
        }
        return false;
    }
}
