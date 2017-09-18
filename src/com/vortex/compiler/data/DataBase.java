package com.vortex.compiler.data;

import com.vortex.compiler.content.StringFile;
import com.vortex.compiler.logic.space.NameSpace;
import com.vortex.compiler.logic.typedef.Typedef;
import com.vortex.compiler.logic.typedef.Pointer;

import java.util.HashMap;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 02/10/2016
 */
public class DataBase {
    public static final Object masterLock = new Object();

    public static Library defaultLibrary;
    public static HashMap<String, Library> libraries = new HashMap<>();
    public static HashMap<String, NameSpace> nameSpaces = new HashMap<>();
    public static HashMap<String, Typedef> typedefs = new HashMap<>();

    public static Typedef defObject, defEnum, defWrapper, defException, defLockable, defArray, defIterable, defIterator, defList,
            defBool, defByte, defShort, defInt, defLong, defFloat, defDouble, defChar, defString, defFunction;

    public static Pointer defObjectPointer, defEnumPointer, defLockablePointer, defArrayPointer, defIterablePointer, defIteratorPointer,
            defListPointer, defWrapperPointer, defExceptionPointer, defBoolPointer, defBytePointer, defShortPointer,
            defIntPointer, defLongPointer, defFloatPointer, defDoublePointer, defCharPointer, defStringPointer,
            defFunctionPointer;

    public static void setDefaultLibrary(Library library) {
        libraryAdd(library);
        defaultLibrary = library;

        defObject = typedefFind(library.name + "::Object");         //Classe base
        defArray = typedefFind(library.name + "::Array");           //Classe usada nos vetores ( Tipo[] )
        defEnum = typedefFind(library.name + "::Enum");             //Classe base para 'enums'
        defWrapper = typedefFind(library.name + "::Wrapper");       //Classe base para 'structs'
        defException = typedefFind(library.name + "::Exception");   //Classe base para 'throws'
        defLockable = typedefFind(library.name + "::Lockable");     //Interface base para blocos 'lock'
        defIterable = typedefFind(library.name + "::Iterable");     //Interface base para blocos 'for' (foreach)
        defIterator = typedefFind(library.name + "::Iterator");

        defBool = typedefFind(library.name + "::bool");
        defByte = typedefFind(library.name + "::byte");
        defShort = typedefFind(library.name + "::short");
        defInt = typedefFind(library.name + "::int");
        defLong = typedefFind(library.name + "::long");
        defFloat = typedefFind(library.name + "::float");
        defDouble = typedefFind(library.name + "::double");
        defChar = typedefFind(library.name + "::char");
        defString = typedefFind(library.name + "::string");
        defFunction = typedefFind(library.name + "::function");

        defObjectPointer = new Pointer(defObject);
        defEnumPointer = new Pointer(defEnum, defObjectPointer);
        defWrapperPointer = new Pointer(defWrapper, defObjectPointer);
        defExceptionPointer = new Pointer(defException);
        defLockablePointer = new Pointer(defLockable);
        defArrayPointer = new Pointer(defArray, defObjectPointer);
        defIterablePointer = new Pointer(defIterable, defObjectPointer);
        defIteratorPointer= new Pointer(defIterator, defObjectPointer);
        defListPointer = new Pointer(defList, defObjectPointer);

        defBoolPointer = new Pointer(defBool);
        defBytePointer = new Pointer(defByte);
        defShortPointer = new Pointer(defShort);
        defIntPointer = new Pointer(defInt);
        defLongPointer = new Pointer(defLong);
        defFloatPointer = new Pointer(defFloat);
        defDoublePointer = new Pointer(defDouble);
        defCharPointer = new Pointer(defChar);
        defStringPointer = new Pointer(defString);
        defFunctionPointer = new Pointer(defFunction, Pointer.voidPointer);
    }

    public static void clear() {
        nameSpaces.clear();
        typedefs.clear();
        libraries.clear();

        defObject = defEnum = defLockable = defArray = defIterable = defIterator = defList = defWrapper = defBool = defByte =
                defShort = defInt = defLong = defFloat = defDouble = defChar = defString = defException = defFunction = null;

        defObjectPointer = defEnumPointer = defLockablePointer = defArrayPointer = defIterablePointer = defIteratorPointer = defListPointer =
                defExceptionPointer = defBoolPointer = defBytePointer = defShortPointer = defIntPointer =
                        defLongPointer = defFloatPointer = defDoublePointer = defCharPointer = defStringPointer =
                                defWrapperPointer = defFunctionPointer = null;
    }

    /**
     * Adiciona bibilhoteca ao banco de dados lógico
     *
     * @param library Bibilhoteca
     */
    public static void libraryAdd(Library library) {
        if (!"".equals(library.name) && libraries.get(library.name) == null)
            libraries.put(library.name, library);
    }

    /**
     * Remove bibilhotecca do banco de dados lógico
     *
     * @param library Bibilhoteca
     */
    public static void libraryRemove(Library library) {
        if (library != null && libraries.get(library.name) == library) {
            library.clear();
            libraries.remove(library.name);
        }
    }

    /**
     * Remove bibilhotecca do banco de dados lógico
     *
     * @param name Nome da bibilhoteca
     */
    public static void libraryRemove(String name) {
        libraryRemove(libraryFind(name));
    }

    /**
     * Procura bibilhotecca do banco de dados pelo nome
     *
     * @param name Nome da bibilhoteca
     */
    public static Library libraryFind(String name) {
        return libraries.get(name);
    }

    /**
     * Adiciona um Typedef a bibilhoteca
     *
     * @param typedef Typedef
     */
    public static void typedefAdd(Typedef typedef) {
        if (!"".equals(typedef.fullName) && typedefs.get(typedef.fullName) == null)
            typedefs.put(typedef.fullName, typedef);
    }

    /**
     * Remove um Typedef da bibilhoteca
     *
     * @param typedef Typedef
     */
    public static void typedefRemove(Typedef typedef) {
        if (typedef != null && typedefs.get(typedef.fullName) == typedef) {
            typedefs.remove(typedef.fullName);
        }
    }

    /**
     * Procura um typedef do banco de dados pelo nome
     *
     * @param name Nome do typedef (deve ser completo, incluindo namespaces e bibilhoteca)
     */
    public static Typedef typedefFind(String name) {
        return typedefs.get(name);
    }

    /**
     * Adiciona um namespace a bibilhoteca
     *
     * @param nameSpace NameSpace
     */
    public static void namespaceAdd(NameSpace nameSpace) {
        if (!"".equals(nameSpace.fullName) && nameSpaces.get(nameSpace.fullName) == null)
            nameSpaces.put(nameSpace.fullName, nameSpace);
    }

    /**
     * Remove um namespace da bibilhoteca
     *
     * @param nameSpace NameSpace
     */
    public static void namespaceRemove(NameSpace nameSpace) {
        if (nameSpace != null && nameSpaces.get(nameSpace.fullName) == nameSpace) {
            nameSpaces.remove(nameSpace.fullName);
        }
    }

    /**
     * Procura um namespace do banco de dados pelo nome
     *
     * @param nameSpace NameSpace
     */
    public static NameSpace namespaceFind(String nameSpace) {
        return nameSpaces.get(nameSpace);
    }

    /**
     * Procura um arquivo dentro de todoas as bibilhotecas do  projeto
     *
     * @param pathName Caminho para oarquivo
     * @return StringFile que representa o arquivo, ou null caso nenhum seja encontrado
     */
    public static StringFile stringfileFind(String pathName) {
        for (Library library : libraries.values()) {
            StringFile strFile = library.getFile(pathName);
            if (strFile != null) {
                return strFile;
            }
        }
        return null;
    }

    public static void debug() {
        for (Library value : libraries.values()) {
            System.out.println(value);
        }
    }
}
