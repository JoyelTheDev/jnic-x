package cn.sky.jnic.generator;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.BasicValue;

public class CTypeUtils {

    static String getJNIType(Type type) {
        return switch (type.getSort()) {
            case Type.VOID    -> "void";
            case Type.BOOLEAN -> "jboolean";
            case Type.CHAR    -> "jchar";
            case Type.BYTE    -> "jbyte";
            case Type.SHORT   -> "jshort";
            case Type.INT     -> "jint";
            case Type.FLOAT   -> "jfloat";
            case Type.LONG    -> "jlong";
            case Type.DOUBLE  -> "jdouble";
            default           -> "jobject";
        };
    }

    static String getJNICallType(Type type) {
        return switch (type.getSort()) {
            case Type.VOID    -> "Void";
            case Type.BOOLEAN -> "Boolean";
            case Type.CHAR    -> "Char";
            case Type.BYTE    -> "Byte";
            case Type.SHORT   -> "Short";
            case Type.INT     -> "Int";
            case Type.FLOAT   -> "Float";
            case Type.LONG    -> "Long";
            case Type.DOUBLE  -> "Double";
            default           -> "Object";
        };
    }

    static String getTypeField(Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> "i";
            case Type.FLOAT  -> "f";
            case Type.LONG   -> "j";
            case Type.DOUBLE -> "d";
            default          -> "l";
        };
    }

    static String getNewArrayFunc(int type) {
        return switch (type) {
            case Opcodes.T_BOOLEAN -> "NewBooleanArray";
            case Opcodes.T_CHAR    -> "NewCharArray";
            case Opcodes.T_FLOAT   -> "NewFloatArray";
            case Opcodes.T_DOUBLE  -> "NewDoubleArray";
            case Opcodes.T_BYTE    -> "NewByteArray";
            case Opcodes.T_SHORT   -> "NewShortArray";
            case Opcodes.T_INT     -> "NewIntArray";
            case Opcodes.T_LONG    -> "NewLongArray";
            default                -> "NewIntArray";
        };
    }

    static boolean isReferenceType(BasicValue value) {
        if (value == null) return false;
        Type type = value.getType();
        return type != null && (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY);
    }

    static String returnDefault(Type returnType) {
        if (returnType.getSort() == Type.VOID) return "        return;\n";
        return "        return 0;\n";
    }
}
