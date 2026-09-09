package cn.sky.jnic.generator;

import cn.sky.jnic.process.NativeProcessor;
import cn.sky.jnic.utils.asm.ClassWrapper;
import cn.sky.jnic.utils.asm.MethodWrapper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.Map;

import static cn.sky.jnic.generator.CTypeUtils.*;

public class CInstructionEmitter {

    private final CGeneratorContext ctx;
    private final NativeProcessor processor;

    CInstructionEmitter(CGeneratorContext ctx, NativeProcessor processor) {
        this.ctx = ctx;
        this.processor = processor;
    }

    String emit(AbstractInsnNode insn, Map<LabelNode, Integer> labelMap,
                int currentIndex, Type returnType) {

        if (insn instanceof FrameNode || insn instanceof LabelNode || insn instanceof LineNumberNode) {
            return "";
        }

        Frame<BasicValue> frame = ctx.currentFrames[currentIndex];
        if (frame == null) return "";

        int sp = getCompressedStackSize(frame);
        StringBuilder code = new StringBuilder();
        code.append("    { int sp = ").append(sp).append(";\n");

        int opcode = insn.getOpcode();

        switch (opcode) {
            case Opcodes.NOP -> {}

            case Opcodes.ACONST_NULL ->
                code.append("    stack[sp++].l = NULL;\n");

            case Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1,
                 Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5 ->
                code.append("    stack[sp++].i = ").append(opcode - Opcodes.ICONST_0).append(";\n");

            case Opcodes.LCONST_0, Opcodes.LCONST_1 ->
                code.append("    stack[sp++].j = ").append(opcode - Opcodes.LCONST_0).append("LL;\n");

            case Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2 ->
                code.append("    stack[sp++].f = ").append(opcode - Opcodes.FCONST_0).append(".0f;\n");

            case Opcodes.DCONST_0, Opcodes.DCONST_1 ->
                code.append("    stack[sp++].d = ").append(opcode - Opcodes.DCONST_0).append(".0;\n");

            case Opcodes.BIPUSH, Opcodes.SIPUSH ->
                code.append("    stack[sp++].i = ").append(((IntInsnNode) insn).operand).append(";\n");

            case Opcodes.LDC -> emitLdc(code, (LdcInsnNode) insn, returnType);

            case Opcodes.ILOAD -> code.append("    stack[sp++].i = locals[").append(((VarInsnNode) insn).var).append("].i;\n");
            case Opcodes.LLOAD -> code.append("    stack[sp++].j = locals[").append(((VarInsnNode) insn).var).append("].j;\n");
            case Opcodes.FLOAD -> code.append("    stack[sp++].f = locals[").append(((VarInsnNode) insn).var).append("].f;\n");
            case Opcodes.DLOAD -> code.append("    stack[sp++].d = locals[").append(((VarInsnNode) insn).var).append("].d;\n");
            case Opcodes.ALOAD -> code.append("    stack[sp++].l = (*env)->NewLocalRef(env, locals[").append(((VarInsnNode) insn).var).append("].l);\n");

            case Opcodes.ISTORE -> code.append("    locals[").append(((VarInsnNode) insn).var).append("].i = stack[--sp].i;\n");
            case Opcodes.LSTORE -> code.append("    locals[").append(((VarInsnNode) insn).var).append("].j = stack[--sp].j;\n");
            case Opcodes.FSTORE -> code.append("    locals[").append(((VarInsnNode) insn).var).append("].f = stack[--sp].f;\n");
            case Opcodes.DSTORE -> code.append("    locals[").append(((VarInsnNode) insn).var).append("].d = stack[--sp].d;\n");
            case Opcodes.ASTORE -> emitAstore(code, (VarInsnNode) insn);

            case Opcodes.GETSTATIC, Opcodes.PUTSTATIC, Opcodes.GETFIELD, Opcodes.PUTFIELD ->
                emitFieldAccess(code, (FieldInsnNode) insn, currentIndex, returnType);

            case Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD,
                 Opcodes.AALOAD, Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD ->
                emitArrayLoad(code, opcode, insn, currentIndex, returnType);

            case Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE,
                 Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE ->
                emitArrayStore(code, opcode, insn, currentIndex, returnType);

            case Opcodes.POP  -> emitPop(code, frame);
            case Opcodes.POP2 -> emitPop2(code, frame);
            case Opcodes.DUP  -> emitDup(code, frame);
            case Opcodes.DUP_X1 -> emitDupX1(code, insn, frame);
            case Opcodes.DUP_X2 -> emitDupX2(code, insn, frame);
            case Opcodes.DUP2   -> emitDup2(code, insn, frame);
            case Opcodes.SWAP ->
                code.append("    StackValue tmp_").append(Math.abs(insn.hashCode())).append(" = stack[sp-1];\n")
                    .append("    stack[sp-1] = stack[sp-2];\n")
                    .append("    stack[sp-2] = tmp_").append(Math.abs(insn.hashCode())).append(";\n");

            case Opcodes.IADD -> code.append("    sp--; __asm__(\"add %1, %0\" : \"+r\"(stack[sp-1].i) : \"r\"(stack[sp].i));\n");
            case Opcodes.LADD -> code.append("    sp--; stack[sp-1].j = stack[sp-1].j + stack[sp].j;\n");
            case Opcodes.FADD -> code.append("    sp--; stack[sp-1].f = stack[sp-1].f + stack[sp].f;\n");
            case Opcodes.DADD -> code.append("    sp--; stack[sp-1].d = stack[sp-1].d + stack[sp].d;\n");

            case Opcodes.ISUB -> code.append("    sp--; __asm__(\"sub %1, %0\" : \"+r\"(stack[sp-1].i) : \"r\"(stack[sp].i));\n");
            case Opcodes.LSUB -> code.append("    sp--; stack[sp-1].j = stack[sp-1].j - stack[sp].j;\n");
            case Opcodes.FSUB -> code.append("    sp--; stack[sp-1].f = stack[sp-1].f - stack[sp].f;\n");
            case Opcodes.DSUB -> code.append("    sp--; stack[sp-1].d = stack[sp-1].d - stack[sp].d;\n");

            case Opcodes.IMUL -> code.append("    sp--; __asm__(\"imul %1, %0\" : \"+r\"(stack[sp-1].i) : \"r\"(stack[sp].i));\n");
            case Opcodes.LMUL -> code.append("    sp--; stack[sp-1].j = stack[sp-1].j * stack[sp].j;\n");
            case Opcodes.FMUL -> code.append("    sp--; stack[sp-1].f = stack[sp-1].f * stack[sp].f;\n");
            case Opcodes.DMUL -> code.append("    sp--; stack[sp-1].d = stack[sp-1].d * stack[sp].d;\n");

            case Opcodes.IDIV -> emitIntDiv(code, "i", "j", false, currentIndex, returnType);
            case Opcodes.LDIV -> emitIntDiv(code, "j", "j", true, currentIndex, returnType);
            case Opcodes.FDIV -> code.append("    sp--; stack[sp-1].f = stack[sp-1].f / stack[sp].f;\n");
            case Opcodes.DDIV -> code.append("    sp--; stack[sp-1].d = stack[sp-1].d / stack[sp].d;\n");

            case Opcodes.IREM -> emitIntRem(code, "i", false, currentIndex, returnType);
            case Opcodes.LREM -> emitIntRem(code, "j", true, currentIndex, returnType);
            case Opcodes.FREM -> code.append("    sp--; stack[sp-1].f = (jfloat)fmod(stack[sp-1].f, stack[sp].f);\n");
            case Opcodes.DREM -> code.append("    sp--; stack[sp-1].d = fmod(stack[sp-1].d, stack[sp].d);\n");

            case Opcodes.INEG -> code.append("    __asm__(\"neg %0\" : \"+r\"(stack[sp-1].i));\n");
            case Opcodes.LNEG -> code.append("    stack[sp-1].j = -stack[sp-1].j;\n");
            case Opcodes.FNEG -> code.append("    stack[sp-1].f = -stack[sp-1].f;\n");
            case Opcodes.DNEG -> code.append("    stack[sp-1].d = -stack[sp-1].d;\n");

            case Opcodes.ISHL  -> code.append("    sp--; stack[sp-1].i = stack[sp-1].i << stack[sp].i;\n");
            case Opcodes.LSHL  -> code.append("    sp--; stack[sp-1].j = stack[sp-1].j << stack[sp].i;\n");
            case Opcodes.ISHR  -> code.append("    sp--; stack[sp-1].i = stack[sp-1].i >> stack[sp].i;\n");
            case Opcodes.LSHR  -> code.append("    sp--; stack[sp-1].j = stack[sp-1].j >> stack[sp].i;\n");
            case Opcodes.IUSHR -> code.append("    sp--; stack[sp-1].i = (jint)((unsigned int)stack[sp-1].i >> stack[sp].i);\n");
            case Opcodes.LUSHR -> code.append("    sp--; stack[sp-1].j = (jlong)((unsigned long long)stack[sp-1].j >> stack[sp].i);\n");

            case Opcodes.IAND -> code.append("    sp--; __asm__(\"and %1, %0\" : \"+r\"(stack[sp-1].i) : \"r\"(stack[sp].i));\n");
            case Opcodes.LAND -> code.append("    sp--; stack[sp-1].j = stack[sp-1].j & stack[sp].j;\n");
            case Opcodes.IOR  -> code.append("    sp--; __asm__(\"or %1, %0\" : \"+r\"(stack[sp-1].i) : \"r\"(stack[sp].i));\n");
            case Opcodes.LOR  -> code.append("    sp--; stack[sp-1].j = stack[sp-1].j | stack[sp].j;\n");
            case Opcodes.IXOR -> code.append("    sp--; __asm__(\"xor %1, %0\" : \"+r\"(stack[sp-1].i) : \"r\"(stack[sp].i));\n");
            case Opcodes.LXOR -> code.append("    sp--; stack[sp-1].j = stack[sp-1].j ^ stack[sp].j;\n");

            case Opcodes.IINC -> {
                IincInsnNode iinc = (IincInsnNode) insn;
                code.append("    __asm__(\"add %1, %0\" : \"+r\"(locals[").append(iinc.var).append("].i) : \"r\"(").append(iinc.incr).append("));\n");
            }

            case Opcodes.I2L -> code.append("    stack[sp-1].j = (jlong)stack[sp-1].i;\n");
            case Opcodes.I2F -> code.append("    stack[sp-1].f = (jfloat)stack[sp-1].i;\n");
            case Opcodes.I2D -> code.append("    stack[sp-1].d = (jdouble)stack[sp-1].i;\n");
            case Opcodes.L2I -> code.append("    stack[sp-1].i = (jint)stack[sp-1].j;\n");
            case Opcodes.L2F -> code.append("    stack[sp-1].f = (jfloat)stack[sp-1].j;\n");
            case Opcodes.L2D -> code.append("    stack[sp-1].d = (jdouble)stack[sp-1].j;\n");
            case Opcodes.F2I -> code.append("    stack[sp-1].i = (jint)stack[sp-1].f;\n");
            case Opcodes.F2L -> code.append("    stack[sp-1].j = (jlong)stack[sp-1].f;\n");
            case Opcodes.F2D -> code.append("    stack[sp-1].d = (jdouble)stack[sp-1].f;\n");
            case Opcodes.D2I -> code.append("    stack[sp-1].i = (jint)stack[sp-1].d;\n");
            case Opcodes.D2L -> code.append("    stack[sp-1].j = (jlong)stack[sp-1].d;\n");
            case Opcodes.D2F -> code.append("    stack[sp-1].f = (jfloat)stack[sp-1].d;\n");
            case Opcodes.I2B -> code.append("    stack[sp-1].i = (jbyte)stack[sp-1].i;\n");
            case Opcodes.I2C -> code.append("    stack[sp-1].i = (jchar)stack[sp-1].i;\n");
            case Opcodes.I2S -> code.append("    stack[sp-1].i = (jshort)stack[sp-1].i;\n");

            case Opcodes.LCMP  -> emitLcmp(code);
            case Opcodes.FCMPL, Opcodes.FCMPG -> emitFcmp(code, opcode, "f");
            case Opcodes.DCMPL, Opcodes.DCMPG -> emitFcmp(code, opcode, "d");

            case Opcodes.NEW       -> emitNew(code, (TypeInsnNode) insn, currentIndex, returnType);
            case Opcodes.NEWARRAY  -> emitNewarray(code, (IntInsnNode) insn, currentIndex, returnType);
            case Opcodes.ANEWARRAY -> emitAnewarray(code, (TypeInsnNode) insn, currentIndex, returnType);
            case Opcodes.ARRAYLENGTH -> emitArraylength(code, currentIndex, returnType);
            case Opcodes.ATHROW    -> emitAthrow(code, currentIndex, returnType);
            case Opcodes.CHECKCAST -> emitCheckcast(code, (TypeInsnNode) insn, currentIndex, returnType);
            case Opcodes.INSTANCEOF -> emitInstanceof(code, (TypeInsnNode) insn, returnType);

            case Opcodes.MONITORENTER -> code.append("    (*env)->MonitorEnter(env, stack[--sp].l);\n");
            case Opcodes.MONITOREXIT  -> code.append("    (*env)->MonitorExit(env, stack[--sp].l);\n");

            case Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT,
                 Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE -> emitIfZero(code, opcode, (JumpInsnNode) insn);

            case Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT,
                 Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE -> emitIfIcmp(code, opcode, insn, (JumpInsnNode) insn);

            case Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE -> emitIfAcmp(code, opcode, insn, (JumpInsnNode) insn);

            case Opcodes.GOTO    -> code.append("    goto L").append(((JumpInsnNode) insn).label.hashCode()).append(";\n");
            case Opcodes.IFNULL, Opcodes.IFNONNULL -> emitIfNull(code, opcode, insn, (JumpInsnNode) insn);

            case Opcodes.TABLESWITCH  -> emitTableSwitch(code, (TableSwitchInsnNode) insn);
            case Opcodes.LOOKUPSWITCH -> emitLookupSwitch(code, (LookupSwitchInsnNode) insn);

            case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESTATIC,
                 Opcodes.INVOKESPECIAL, Opcodes.INVOKEINTERFACE ->
                emitInvoke(code, opcode, (MethodInsnNode) insn, currentIndex, returnType);

            case Opcodes.RETURN  -> code.append("    (*env)->PopLocalFrame(env, NULL);\n    return;\n");
            case Opcodes.IRETURN -> code.append("    (*env)->PopLocalFrame(env, NULL);\n    return stack[--sp].i;\n");
            case Opcodes.LRETURN -> code.append("    (*env)->PopLocalFrame(env, NULL);\n    return stack[--sp].j;\n");
            case Opcodes.FRETURN -> code.append("    (*env)->PopLocalFrame(env, NULL);\n    return stack[--sp].f;\n");
            case Opcodes.DRETURN -> code.append("    (*env)->PopLocalFrame(env, NULL);\n    return stack[--sp].d;\n");
            case Opcodes.ARETURN -> code.append("    return (*env)->PopLocalFrame(env, stack[--sp].l);\n");

            default -> code.append("    // Unhandled opcode: ").append(opcode).append("\n");
        }

        code.append(" }\n");

        String result = code.toString();
        int braceCount = 0;
        for (char c : result.toCharArray()) {
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;
        }
        if (braceCount != 0) {
            throw new RuntimeException("Unbalanced braces at opcode " + opcode + " index " + currentIndex + ": " + braceCount);
        }
        return result;
    }

    private int getCompressedStackSize(Frame<BasicValue> frame) {
        int size = 0;
        for (int i = 0; i < frame.getStackSize(); i++) {
            size++;
            if (frame.getStack(i).getSize() == 2) {
                if (i + 1 < frame.getStackSize()) {
                    BasicValue next = frame.getStack(i + 1);
                    if (next != null && next.equals(BasicValue.UNINITIALIZED_VALUE)) i++;
                }
            }
        }
        return size;
    }

    private void emitLdc(StringBuilder code, LdcInsnNode ldc, Type returnType) {
        int sid = Math.abs(ldc.hashCode());
        if (ldc.cst instanceof Integer) {
            code.append("    stack[sp++].i = ").append(ldc.cst).append(";\n");
        } else if (ldc.cst instanceof Float) {
            code.append("    stack[sp++].f = ").append(ldc.cst).append("f;\n");
        } else if (ldc.cst instanceof Long) {
            code.append("    stack[sp++].j = ").append(ldc.cst).append("LL;\n");
        } else if (ldc.cst instanceof Double) {
            code.append("    stack[sp++].d = ").append(ldc.cst).append(";\n");
        } else if (ldc.cst instanceof String str) {
            code.append("    {\n");
            code.append("        static jstring cached_").append(sid).append(" = NULL;\n");
            code.append("        if (cached_").append(sid).append(" == NULL) {\n");
            if (ctx.config.isStringEncryption()) {
                Obfuscator.EncryptedString enc = ctx.obfuscator.encryptStringData(str);
                code.append("            const unsigned char enc_").append(sid).append("[] = ").append(enc.cArrayLiteral()).append(";\n");
                code.append("            char* dec_").append(sid).append(" = decrypt_string_len(enc_").append(sid).append(", ").append(enc.length()).append(", ").append(enc.key()).append(");\n");
                code.append("            if (dec_").append(sid).append(" == NULL) {\n").append(returnDefault(returnType)).append("            }\n");
                code.append("            jstring tmp = (*env)->NewStringUTF(env, dec_").append(sid).append(");\n");
                code.append("            free(dec_").append(sid).append(");\n");
            } else {
                code.append("            jstring tmp = (*env)->NewStringUTF(env, ").append(ctx.obfuscator.encryptString(str)).append(");\n");
            }
            code.append("            if (tmp == NULL) {\n").append(returnDefault(returnType)).append("            }\n");
            code.append("            cached_").append(sid).append(" = (*env)->NewGlobalRef(env, tmp);\n");
            code.append("            (*env)->DeleteLocalRef(env, tmp);\n");
            code.append("            if (cached_").append(sid).append(" == NULL) {\n").append(returnDefault(returnType)).append("            }\n");
            code.append("        }\n");
            code.append("        stack[sp++].l = (*env)->NewLocalRef(env, cached_").append(sid).append(");\n");
            code.append("    }\n");
        } else if (ldc.cst instanceof Type type) {
            code.append("    {\n");
            code.append("        static jclass cached_").append(sid).append(" = NULL;\n");
            code.append("        if (cached_").append(sid).append(" == NULL) {\n");
            code.append("            jclass tmp = (*env)->FindClass(env, \"").append(type.getInternalName()).append("\");\n");
            code.append("            if (tmp == NULL) {\n").append(returnDefault(returnType)).append("            }\n");
            code.append("            cached_").append(sid).append(" = (*env)->NewGlobalRef(env, tmp);\n");
            code.append("            (*env)->DeleteLocalRef(env, tmp);\n");
            code.append("            if (cached_").append(sid).append(" == NULL) {\n").append(returnDefault(returnType)).append("            }\n");
            code.append("        }\n");
            code.append("        stack[sp++].l = (*env)->NewLocalRef(env, cached_").append(sid).append(");\n");
            code.append("    }\n");
        }
    }

    private void emitAstore(StringBuilder code, VarInsnNode insn) {
        int var = insn.var;
        int h = Math.abs(insn.hashCode());
        code.append("    {\n");
        code.append("        jobject tmp_").append(h).append(" = stack[--sp].l;\n");
        code.append("        if (locals[").append(var).append("].l != NULL && locals[").append(var).append("].l != tmp_").append(h).append(") {\n");
        code.append("            (*env)->DeleteLocalRef(env, locals[").append(var).append("].l);\n");
        code.append("        }\n");
        code.append("        locals[").append(var).append("].l = tmp_").append(h).append(";\n");
        code.append("    }\n");
    }

    private void emitFieldAccess(StringBuilder code, FieldInsnNode finsn, int currentIndex, Type returnType) {
        int opcode = finsn.getOpcode();
        Type fieldType = Type.getType(finsn.desc);
        boolean isStaticField = (opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC);
        boolean isPut = (opcode == Opcodes.PUTSTATIC || opcode == Opcodes.PUTFIELD);
        String h = String.valueOf(Math.abs(finsn.hashCode()));
        String typeName = getJNICallType(fieldType);
        String unionField = getTypeField(fieldType);

        code.append("    static jfieldID fid_").append(h).append(" = NULL;\n");
        if (isStaticField) {
            code.append("    static jclass fcls_").append(h).append(" = NULL;\n");
            code.append("    if (fid_").append(h).append(" == NULL) {\n");
            code.append("        jclass tmp = (*env)->FindClass(env, \"").append(finsn.owner).append("\");\n");
            code.append("        if (tmp == NULL) {\n").append(returnDefault(returnType)).append("        }\n");
            code.append("        fcls_").append(h).append(" = (*env)->NewGlobalRef(env, tmp);\n");
            code.append("        if (fcls_").append(h).append(" == NULL) { (*env)->DeleteLocalRef(env, tmp);\n").append(returnDefault(returnType)).append("        }\n");
            code.append("        fid_").append(h).append(" = (*env)->GetStaticFieldID(env, fcls_").append(h).append(", \"").append(finsn.name).append("\", \"").append(finsn.desc).append("\");\n");
            code.append("        (*env)->DeleteLocalRef(env, tmp);\n");
            code.append("    }\n");
        } else {
            code.append("    if (fid_").append(h).append(" == NULL) {\n");
            code.append("        jclass tmp = (*env)->FindClass(env, \"").append(finsn.owner).append("\");\n");
            code.append("        if (tmp == NULL) {\n").append(returnDefault(returnType)).append("        }\n");
            code.append("        fid_").append(h).append(" = (*env)->GetFieldID(env, tmp, \"").append(finsn.name).append("\", \"").append(finsn.desc).append("\");\n");
            code.append("        (*env)->DeleteLocalRef(env, tmp);\n");
            code.append("    }\n");
        }
        code.append("    if (fid_").append(h).append(" == NULL) {\n").append(returnDefault(returnType)).append("    }\n");

        if (isPut) {
            code.append("    ").append(getJNIType(fieldType)).append(" val_").append(h).append(" = stack[--sp].").append(unionField).append(";\n");
            code.append("    jobject obj_").append(h).append(" = ").append(isStaticField ? "NULL" : "stack[--sp].l").append(";\n");
            if (!isStaticField) {
                code.append("    if (obj_").append(h).append(" == NULL) {\n");
                code.append("        jclass npeCls = (*env)->FindClass(env, \"java/lang/NullPointerException\");\n");
                code.append("        if (npeCls != NULL) (*env)->ThrowNew(env, npeCls, \"Null pointer access\");\n");
                code.append(returnDefault(returnType)).append("    }\n");
            }
            String setFunc = isStaticField ? "SetStatic" + typeName + "Field" : "Set" + typeName + "Field";
            code.append("    (*env)->").append(setFunc).append("(env, ").append(isStaticField ? "fcls_" + h : "obj_" + h).append(", fid_").append(h).append(", val_").append(h).append(");\n");
        } else {
            code.append("    jobject obj_").append(h).append(" = ").append(isStaticField ? "NULL" : "stack[--sp].l").append(";\n");
            if (!isStaticField) {
                code.append("    if (obj_").append(h).append(" == NULL) {\n");
                code.append("        jclass npeCls = (*env)->FindClass(env, \"java/lang/NullPointerException\");\n");
                code.append("        if (npeCls != NULL) (*env)->ThrowNew(env, npeCls, \"Null pointer access\");\n");
                code.append(returnDefault(returnType)).append("    }\n");
            }
            String getFunc = isStaticField ? "GetStatic" + typeName + "Field" : "Get" + typeName + "Field";
            code.append("    ").append(getJNIType(fieldType)).append(" res_").append(h).append(" = (*env)->").append(getFunc).append("(env, ").append(isStaticField ? "fcls_" + h : "obj_" + h).append(", fid_").append(h).append(");\n");
            code.append("    stack[sp++].").append(unionField).append(" = res_").append(h).append(";\n");
        }
        code.append(generateExceptionHandling(currentIndex, returnType));
    }

    private void emitArrayLoad(StringBuilder code, int opcode, AbstractInsnNode insn, int currentIndex, Type returnType) {
        code.append("    {\n");
        code.append("        jint idx = stack[--sp].i;\n");
        if (opcode == Opcodes.AALOAD) {
            code.append("        jobjectArray arr = (jobjectArray)stack[--sp].l;\n");
            code.append("        jobject val = NULL;\n");
            code.append("        if (arr != NULL) { val = (*env)->GetObjectArrayElement(env, arr, idx); }\n");
            code.append("        else { jclass c = (*env)->FindClass(env, \"java/lang/NullPointerException\"); if (c) (*env)->ThrowNew(env, c, \"Array is null\"); }\n");
            code.append("        stack[sp++].l = val;\n");
        } else {
            String valType, sf, fn, cast;
            switch (opcode) {
                case Opcodes.IALOAD -> { valType="jint"; sf="i"; fn="GetIntArrayRegion"; cast="jintArray"; }
                case Opcodes.LALOAD -> { valType="jlong"; sf="j"; fn="GetLongArrayRegion"; cast="jlongArray"; }
                case Opcodes.FALOAD -> { valType="jfloat"; sf="f"; fn="GetFloatArrayRegion"; cast="jfloatArray"; }
                case Opcodes.DALOAD -> { valType="jdouble"; sf="d"; fn="GetDoubleArrayRegion"; cast="jdoubleArray"; }
                case Opcodes.CALOAD -> { valType="jchar"; sf="i"; fn="GetCharArrayRegion"; cast="jcharArray"; }
                case Opcodes.SALOAD -> { valType="jshort"; sf="i"; fn="GetShortArrayRegion"; cast="jshortArray"; }
                default             -> { valType="jint"; sf="i"; fn="GetByteArrayRegion"; cast="jbyteArray"; }
            }
            code.append("        ").append(cast).append(" arr = (").append(cast).append(")stack[--sp].l;\n");
            code.append("        ").append(valType).append(" val = 0;\n");
            code.append("        if (arr != NULL) {\n");
            if (opcode == Opcodes.BALOAD) {
                code.append("            if ((*env)->IsInstanceOf(env, arr, (*env)->FindClass(env, \"[Z\"))) { jboolean b=0; (*env)->GetBooleanArrayRegion(env,(jbooleanArray)arr,idx,1,&b); val=b; }\n");
                code.append("            else { jbyte b=0; (*env)->GetByteArrayRegion(env,(jbyteArray)arr,idx,1,&b); val=b; }\n");
            } else {
                code.append("            (*env)->").append(fn).append("(env, arr, idx, 1, &val);\n");
            }
            code.append("        } else { jclass c=(*env)->FindClass(env,\"java/lang/NullPointerException\"); if(c) (*env)->ThrowNew(env,c,\"Array is null\"); }\n");
            code.append("        stack[sp++].").append(sf).append(" = val;\n");
        }
        code.append(generateExceptionHandling(currentIndex, returnType));
        code.append("    }\n");
    }

    private void emitArrayStore(StringBuilder code, int opcode, AbstractInsnNode insn, int currentIndex, Type returnType) {
        code.append("    {\n");
        if (opcode == Opcodes.AASTORE) {
            code.append("        jobject val = stack[--sp].l;\n");
            code.append("        jint idx = stack[--sp].i;\n");
            code.append("        jobjectArray arr = (jobjectArray)stack[--sp].l;\n");
            code.append("        if (arr != NULL) { (*env)->SetObjectArrayElement(env, arr, idx, val); }\n");
            code.append("        else { jclass c=(*env)->FindClass(env,\"java/lang/NullPointerException\"); if(c) (*env)->ThrowNew(env,c,\"Array is null\"); }\n");
        } else {
            String valType, sf, fn, cast;
            switch (opcode) {
                case Opcodes.IASTORE -> { valType="jint"; sf="i"; fn="SetIntArrayRegion"; cast="jintArray"; }
                case Opcodes.LASTORE -> { valType="jlong"; sf="j"; fn="SetLongArrayRegion"; cast="jlongArray"; }
                case Opcodes.FASTORE -> { valType="jfloat"; sf="f"; fn="SetFloatArrayRegion"; cast="jfloatArray"; }
                case Opcodes.DASTORE -> { valType="jdouble"; sf="d"; fn="SetDoubleArrayRegion"; cast="jdoubleArray"; }
                case Opcodes.CASTORE -> { valType="jchar"; sf="i"; fn="SetCharArrayRegion"; cast="jcharArray"; }
                case Opcodes.SASTORE -> { valType="jshort"; sf="i"; fn="SetShortArrayRegion"; cast="jshortArray"; }
                default              -> { valType="jint"; sf="i"; fn="SetByteArrayRegion"; cast="jbyteArray"; }
            }
            code.append("        ").append(valType).append(" val = stack[--sp].").append(sf).append(";\n");
            code.append("        jint idx = stack[--sp].i;\n");
            code.append("        jarray arr = (jarray)stack[--sp].l;\n");
            code.append("        if (arr != NULL) {\n");
            if (opcode == Opcodes.BASTORE) {
                code.append("            if ((*env)->IsInstanceOf(env,arr,(*env)->FindClass(env,\"[Z\"))) { jboolean b=(jboolean)val; (*env)->SetBooleanArrayRegion(env,(jbooleanArray)arr,idx,1,&b); }\n");
                code.append("            else { jbyte b=(jbyte)val; (*env)->SetByteArrayRegion(env,(jbyteArray)arr,idx,1,&b); }\n");
            } else {
                code.append("            (*env)->").append(fn).append("(env, (").append(cast).append(")arr, idx, 1, &val);\n");
            }
            code.append("        } else { jclass c=(*env)->FindClass(env,\"java/lang/NullPointerException\"); if(c) (*env)->ThrowNew(env,c,\"Array is null\"); }\n");
        }
        code.append(generateExceptionHandling(currentIndex, returnType));
        code.append("    }\n");
    }

    private void emitPop(StringBuilder code, Frame<BasicValue> frame) {
        BasicValue val = frame.getStack(frame.getStackSize() - 1);
        if (isReferenceType(val)) {
            code.append("    if (stack[sp-1].l != NULL) (*env)->DeleteLocalRef(env, stack[sp-1].l);\n");
        }
        code.append("    sp--;\n");
    }

    private void emitPop2(StringBuilder code, Frame<BasicValue> frame) {
        BasicValue val1 = frame.getStack(frame.getStackSize() - 1);
        if (val1.getSize() == 2) {
            code.append("    sp--;\n");
        } else {
            if (isReferenceType(val1)) code.append("    if (stack[sp-1].l != NULL) (*env)->DeleteLocalRef(env, stack[sp-1].l);\n");
            BasicValue val2 = frame.getStack(frame.getStackSize() - 2);
            if (isReferenceType(val2)) code.append("    if (stack[sp-2].l != NULL) (*env)->DeleteLocalRef(env, stack[sp-2].l);\n");
            code.append("    sp -= 2;\n");
        }
    }

    private void emitDup(StringBuilder code, Frame<BasicValue> frame) {
        BasicValue val = frame.getStack(frame.getStackSize() - 1);
        if (isReferenceType(val)) {
            code.append("    stack[sp].l = (*env)->NewLocalRef(env, stack[sp-1].l); sp++;\n");
        } else {
            code.append("    stack[sp] = stack[sp-1]; sp++;\n");
        }
    }

    private void emitDupX1(StringBuilder code, AbstractInsnNode insn, Frame<BasicValue> frame) {
        BasicValue val1 = frame.getStack(frame.getStackSize() - 1);
        code.append("    stack[sp] = stack[sp-1]; stack[sp-1] = stack[sp-2]; stack[sp-2] = stack[sp];\n");
        if (isReferenceType(val1)) code.append("    stack[sp].l = (*env)->NewLocalRef(env, stack[sp].l);\n");
        code.append("    sp++;\n");
    }

    private void emitDupX2(StringBuilder code, AbstractInsnNode insn, Frame<BasicValue> frame) {
        BasicValue val1 = frame.getStack(frame.getStackSize() - 1);
        BasicValue val2 = frame.getStack(frame.getStackSize() - 2);
        if (val2.getSize() == 2) {
            code.append("    stack[sp] = stack[sp-1]; stack[sp-1] = stack[sp-2]; stack[sp-2] = stack[sp];\n");
            if (isReferenceType(val1)) code.append("    stack[sp].l = (*env)->NewLocalRef(env, stack[sp].l);\n");
            code.append("    sp++;\n");
        } else {
            code.append("    stack[sp] = stack[sp-1]; stack[sp-1] = stack[sp-2]; stack[sp-2] = stack[sp-3]; stack[sp-3] = stack[sp];\n");
            if (isReferenceType(val1)) code.append("    stack[sp].l = (*env)->NewLocalRef(env, stack[sp].l);\n");
            code.append("    sp++;\n");
        }
    }

    private void emitDup2(StringBuilder code, AbstractInsnNode insn, Frame<BasicValue> frame) {
        BasicValue val1 = frame.getStack(frame.getStackSize() - 1);
        if (val1.getSize() == 2) {
            code.append("    stack[sp] = stack[sp-1]; sp++;\n");
        } else {
            BasicValue val2 = frame.getStack(frame.getStackSize() - 2);
            code.append("    stack[sp] = stack[sp-2];\n");
            if (isReferenceType(val2)) code.append("    stack[sp].l = (*env)->NewLocalRef(env, stack[sp].l);\n");
            code.append("    stack[sp+1] = stack[sp-1];\n");
            if (isReferenceType(val1)) code.append("    stack[sp+1].l = (*env)->NewLocalRef(env, stack[sp+1].l);\n");
            code.append("    sp += 2;\n");
        }
    }

    private void emitIntDiv(StringBuilder code, String field, String cmpField, boolean isLong, int idx, Type ret) {
        code.append("    sp--;\n");
        code.append("    if (stack[sp].").append(field).append(" == 0) {\n");
        code.append("        jclass c = (*env)->FindClass(env, \"java/lang/ArithmeticException\"); if (c) (*env)->ThrowNew(env, c, \"/ by zero\");\n");
        code.append(generateExceptionHandling(idx, ret));
        code.append("    } else { stack[sp-1].").append(field).append(" = stack[sp-1].").append(field).append(" / stack[sp].").append(field).append("; }\n");
    }

    private void emitIntRem(StringBuilder code, String field, boolean isLong, int idx, Type ret) {
        code.append("    sp--;\n");
        code.append("    if (stack[sp].").append(field).append(" == 0) {\n");
        code.append("        jclass c = (*env)->FindClass(env, \"java/lang/ArithmeticException\"); if (c) (*env)->ThrowNew(env, c, \"/ by zero\");\n");
        code.append(generateExceptionHandling(idx, ret));
        code.append("    } else { stack[sp-1].").append(field).append(" = stack[sp-1].").append(field).append(" % stack[sp].").append(field).append("; }\n");
    }

    private void emitLcmp(StringBuilder code) {
        code.append("    sp--;\n");
        code.append("    if (stack[sp-1].j > stack[sp].j) stack[sp-1].i = 1;\n");
        code.append("    else if (stack[sp-1].j == stack[sp].j) stack[sp-1].i = 0;\n");
        code.append("    else stack[sp-1].i = -1;\n");
    }

    private void emitFcmp(StringBuilder code, int opcode, String field) {
        int nan = (opcode == Opcodes.FCMPG || opcode == Opcodes.DCMPG) ? 1 : -1;
        code.append("    sp--;\n");
        code.append("    if (stack[sp-1].").append(field).append(" > stack[sp].").append(field).append(") stack[sp-1].i = 1;\n");
        code.append("    else if (stack[sp-1].").append(field).append(" == stack[sp].").append(field).append(") stack[sp-1].i = 0;\n");
        code.append("    else if (stack[sp-1].").append(field).append(" < stack[sp].").append(field).append(") stack[sp-1].i = -1;\n");
        code.append("    else stack[sp-1].i = ").append(nan).append(";\n");
    }

    private void emitNew(StringBuilder code, TypeInsnNode insn, int idx, Type ret) {
        int h = Math.abs(insn.hashCode());
        code.append("    jclass cls_").append(h).append(" = (*env)->FindClass(env, \"").append(insn.desc).append("\");\n");
        code.append("    if (cls_").append(h).append(" == NULL) {\n").append(returnDefault(ret)).append("    }\n");
        code.append("    jobject obj_").append(h).append(" = (*env)->AllocObject(env, cls_").append(h).append(");\n");
        code.append("    stack[sp++].l = obj_").append(h).append(";\n");
        code.append("    if ((*env)->ExceptionCheck(env)) { jthrowable ex=(*env)->ExceptionOccurred(env); (*env)->ExceptionClear(env); (*env)->Throw(env,ex); stack[sp-1].l=NULL; }\n");
        code.append(generateExceptionHandling(idx, ret));
    }

    private void emitNewarray(StringBuilder code, IntInsnNode insn, int idx, Type ret) {
        int h = Math.abs(insn.hashCode());
        code.append("    jsize len_").append(h).append(" = stack[--sp].i;\n");
        code.append("    stack[sp++].l = (*env)->").append(getNewArrayFunc(insn.operand)).append("(env, len_").append(h).append(");\n");
        code.append(generateExceptionHandling(idx, ret));
    }

    private void emitAnewarray(StringBuilder code, TypeInsnNode insn, int idx, Type ret) {
        int h = Math.abs(insn.hashCode());
        code.append("    jsize len_").append(h).append(" = stack[--sp].i;\n");
        code.append("    jclass cls_").append(h).append(" = (*env)->FindClass(env, \"").append(insn.desc).append("\");\n");
        code.append("    if (cls_").append(h).append(" == NULL) {\n").append(returnDefault(ret)).append("    }\n");
        code.append("    stack[sp++].l = (*env)->NewObjectArray(env, len_").append(h).append(", cls_").append(h).append(", NULL);\n");
        code.append("    (*env)->ExceptionCheck(env);\n");
    }

    private void emitArraylength(StringBuilder code, int idx, Type ret) {
        code.append("    if (stack[sp-1].l == NULL) {\n");
        code.append("        jclass c = (*env)->FindClass(env, \"java/lang/NullPointerException\"); if (c) (*env)->ThrowNew(env, c, \"Array is null\");\n");
        code.append(generateExceptionHandling(idx, ret));
        code.append("    } else {\n");
        code.append("        stack[sp-1].i = (*env)->GetArrayLength(env, (jarray)stack[sp-1].l);\n");
        code.append("    }\n");
    }

    private void emitAthrow(StringBuilder code, int idx, Type ret) {
        code.append("    {\n");
        code.append("        jobject ex = stack[--sp].l;\n");
        code.append("        (*env)->Throw(env, (jthrowable)ex);\n");
        code.append("    }\n");
        code.append(generateExceptionHandling(idx, ret));
    }

    private void emitCheckcast(StringBuilder code, TypeInsnNode insn, int idx, Type ret) {
        int h = Math.abs(insn.hashCode());
        code.append("    if (stack[sp-1].l != NULL) {\n");
        code.append("        static jclass cls_").append(h).append(" = NULL;\n");
        code.append("        if (cls_").append(h).append(" == NULL) {\n");
        code.append("            jclass tmp = (*env)->FindClass(env, \"").append(insn.desc).append("\");\n");
        code.append("            if (tmp == NULL) {\n").append(returnDefault(ret)).append("            }\n");
        code.append("            cls_").append(h).append(" = (*env)->NewGlobalRef(env, tmp); (*env)->DeleteLocalRef(env, tmp);\n");
        code.append("            if (cls_").append(h).append(" == NULL) {\n").append(returnDefault(ret)).append("            }\n");
        code.append("        }\n");
        code.append("        if (!(*env)->IsInstanceOf(env, stack[sp-1].l, cls_").append(h).append(")) {\n");
        code.append("            jclass castEx = (*env)->FindClass(env, \"java/lang/ClassCastException\");\n");
        code.append("            if (castEx != NULL) (*env)->ThrowNew(env, castEx, \"").append(insn.desc).append("\");\n");
        code.append(generateExceptionHandling(idx, ret));
        code.append("        }\n");
        code.append("    }\n");
    }

    private void emitInstanceof(StringBuilder code, TypeInsnNode insn, Type ret) {
        int h = Math.abs(insn.hashCode());
        code.append("    {\n");
        code.append("        static jclass cls_").append(h).append(" = NULL;\n");
        code.append("        if (cls_").append(h).append(" == NULL) {\n");
        code.append("            jclass tmp = (*env)->FindClass(env, \"").append(insn.desc).append("\");\n");
        code.append("            if (tmp == NULL) {\n").append(returnDefault(ret)).append("            }\n");
        code.append("            cls_").append(h).append(" = (*env)->NewGlobalRef(env, tmp); (*env)->DeleteLocalRef(env, tmp);\n");
        code.append("            if (cls_").append(h).append(" == NULL) {\n").append(returnDefault(ret)).append("            }\n");
        code.append("        }\n");
        code.append("        stack[sp-1].i = (*env)->IsInstanceOf(env, stack[sp-1].l, cls_").append(h).append(");\n");
        code.append("    }\n");
    }

    private void emitIfZero(StringBuilder code, int opcode, JumpInsnNode insn) {
        int target = insn.label.hashCode();
        String op = switch (opcode) {
            case Opcodes.IFEQ -> "== 0";
            case Opcodes.IFNE -> "!= 0";
            case Opcodes.IFLT -> "< 0";
            case Opcodes.IFGE -> ">= 0";
            case Opcodes.IFGT -> "> 0";
            default            -> "<= 0";
        };
        code.append("    if (stack[--sp].i ").append(op).append(") { goto L").append(target).append("; }\n");
    }

    private void emitIfIcmp(StringBuilder code, int opcode, AbstractInsnNode insn, JumpInsnNode jinsn) {
        int h = Math.abs(insn.hashCode());
        int target = jinsn.label.hashCode();
        String op = switch (opcode) {
            case Opcodes.IF_ICMPEQ -> "==";
            case Opcodes.IF_ICMPNE -> "!=";
            case Opcodes.IF_ICMPLT -> "<";
            case Opcodes.IF_ICMPGE -> ">=";
            case Opcodes.IF_ICMPGT -> ">";
            default                 -> "<=";
        };
        code.append("    jint v2_").append(h).append(" = stack[--sp].i;\n");
        code.append("    jint v1_").append(h).append(" = stack[--sp].i;\n");
        code.append("    if (v1_").append(h).append(" ").append(op).append(" v2_").append(h).append(") { goto L").append(target).append("; }\n");
    }

    private void emitIfAcmp(StringBuilder code, int opcode, AbstractInsnNode insn, JumpInsnNode jinsn) {
        int h = Math.abs(insn.hashCode());
        int target = jinsn.label.hashCode();
        code.append("    jobject v2_").append(h).append(" = stack[--sp].l;\n");
        code.append("    jobject v1_").append(h).append(" = stack[--sp].l;\n");
        String cmp = (opcode == Opcodes.IF_ACMPEQ) ? "" : "!";
        code.append("    if (").append(cmp).append("(*env)->IsSameObject(env, v1_").append(h).append(", v2_").append(h).append(")) { goto L").append(target).append("; }\n");
        code.append("    if (v1_").append(h).append(" != NULL) (*env)->DeleteLocalRef(env, v1_").append(h).append(");\n");
        code.append("    if (v2_").append(h).append(" != NULL) (*env)->DeleteLocalRef(env, v2_").append(h).append(");\n");
    }

    private void emitIfNull(StringBuilder code, int opcode, AbstractInsnNode insn, JumpInsnNode jinsn) {
        int h = Math.abs(insn.hashCode());
        int target = jinsn.label.hashCode();
        code.append("    jobject vnull_").append(h).append(" = stack[--sp].l;\n");
        code.append("    if (vnull_").append(h).append(opcode == Opcodes.IFNULL ? " == " : " != ").append("NULL) { goto L").append(target).append("; }\n");
        code.append("    if (vnull_").append(h).append(" != NULL) (*env)->DeleteLocalRef(env, vnull_").append(h).append(");\n");
    }

    private void emitTableSwitch(StringBuilder code, TableSwitchInsnNode insn) {
        code.append("    switch (stack[--sp].i) {\n");
        for (int i = 0; i < insn.labels.size(); i++) {
            code.append("        case ").append(insn.min + i).append(": goto L").append(insn.labels.get(i).hashCode()).append("; break;\n");
        }
        code.append("        default: goto L").append(insn.dflt.hashCode()).append("; break;\n");
        code.append("    }\n");
    }

    private void emitLookupSwitch(StringBuilder code, LookupSwitchInsnNode insn) {
        code.append("    switch (stack[--sp].i) {\n");
        for (int i = 0; i < insn.labels.size(); i++) {
            code.append("        case ").append(insn.keys.get(i)).append(": goto L").append(insn.labels.get(i).hashCode()).append("; break;\n");
        }
        code.append("        default: goto L").append(insn.dflt.hashCode()).append("; break;\n");
        code.append("    }\n");
    }

    private void emitInvoke(StringBuilder code, int opcode, MethodInsnNode minsn, int currentIndex, Type returnType) {
        String h = String.valueOf(Math.abs(minsn.hashCode()));
        Type callRet = Type.getReturnType(minsn.desc);
        Type[] argTypes = Type.getArgumentTypes(minsn.desc);
        boolean isStatic = (opcode == Opcodes.INVOKESTATIC);
        boolean isSpecial = (opcode == Opcodes.INVOKESPECIAL);

        String inlineCode = CInlineRouter.getInlineImplementation(minsn.owner, minsn.name, minsn.desc, h, returnType);
        if (inlineCode != null) {
            code.append(inlineCode);
            return;
        }

        code.append("    jvalue args_").append(h).append("[").append(Math.max(1, argTypes.length)).append("];\n");
        code.append("    memset(args_").append(h).append(", 0, sizeof(args_").append(h).append("));\n");
        for (int i = argTypes.length - 1; i >= 0; i--) {
            Type argType = argTypes[i];
            String field = getTypeField(argType);
            if (argType.getSort() == Type.OBJECT || argType.getSort() == Type.ARRAY) {
                code.append("    { jobject tmp = stack[--sp].l; args_").append(h).append("[").append(i).append("].l = tmp; }\n");
            } else {
                code.append("    args_").append(h).append("[").append(i).append("].").append(field).append(" = stack[--sp].").append(field).append(";\n");
            }
        }

        boolean isNativeTarget = processor.isNative(minsn.owner, minsn.name, minsn.desc);
        boolean canDirect = isStatic || isSpecial;
        if (!canDirect && isNativeTarget) {
            ClassWrapper ownerCW = processor.getJnic().getClasses().get(minsn.owner);
            if (ownerCW != null) {
                if (ownerCW.isFinal()) canDirect = true;
                else {
                    MethodNode mn = ownerCW.getMethodNode(minsn.name, minsn.desc);
                    if (mn != null && (mn.access & Opcodes.ACC_FINAL) != 0) canDirect = true;
                }
            }
        }

        if (canDirect && isNativeTarget && !minsn.name.startsWith("<") && !minsn.name.startsWith("indy_wrapper_")) {
            String cFunc = "native_" + Integer.toHexString(minsn.name.hashCode()) + "_" + Integer.toHexString(minsn.owner.hashCode());
            if (!isStatic) {
                code.append("    jobject obj_").append(h).append(" = stack[--sp].l;\n");
                code.append("    if (obj_").append(h).append(" == NULL) { jclass c=(*env)->FindClass(env,\"java/lang/NullPointerException\"); if(c) (*env)->ThrowNew(env,c,\"Null pointer access\"); }\n");
                code.append("    else {\n");
                code.append("        ");
                if (callRet.getSort() != Type.VOID) code.append(getJNIType(callRet)).append(" res_").append(h).append(" = ");
                code.append(cFunc).append("(env, obj_").append(h);
                for (int i = 0; i < argTypes.length; i++) code.append(", args_").append(h).append("[").append(i).append("].").append(getTypeField(argTypes[i]));
                code.append(");\n");
                if (callRet.getSort() != Type.VOID) code.append("        stack[sp++].").append(getTypeField(callRet)).append(" = res_").append(h).append(";\n");
                code.append("    }\n");
            } else {
                if (callRet.getSort() != Type.VOID) code.append("    ").append(getJNIType(callRet)).append(" res_").append(h).append(" = ");
                else code.append("    ");
                code.append(cFunc).append("(env, NULL");
                for (int i = 0; i < argTypes.length; i++) code.append(", args_").append(h).append("[").append(i).append("].").append(getTypeField(argTypes[i]));
                code.append(");\n");
                if (callRet.getSort() != Type.VOID) code.append("    stack[sp++].").append(getTypeField(callRet)).append(" = res_").append(h).append(";\n");
            }
            code.append(generateExceptionHandling(currentIndex, returnType));
        } else {
            code.append("    static jclass cls_").append(h).append(" = NULL;\n");
            code.append("    static jmethodID mid_").append(h).append(" = NULL;\n");
            code.append("    if (mid_").append(h).append(" == NULL) {\n");
            code.append("        jclass tmp = (*env)->FindClass(env, \"").append(minsn.owner).append("\");\n");
            code.append("        if (tmp == NULL) {\n").append(returnDefault(returnType)).append("        }\n");
            code.append("        cls_").append(h).append(" = (*env)->NewGlobalRef(env, tmp); (*env)->DeleteLocalRef(env, tmp);\n");
            code.append("        if (cls_").append(h).append(" == NULL) {\n").append(returnDefault(returnType)).append("        }\n");
            String midFunc = isStatic ? "GetStaticMethodID" : "GetMethodID";
            code.append("        mid_").append(h).append(" = (*env)->").append(midFunc).append("(env, cls_").append(h).append(", \"").append(minsn.name).append("\", \"").append(minsn.desc).append("\");\n");
            code.append("        if (mid_").append(h).append(" == NULL) {\n").append(returnDefault(returnType)).append("        }\n");
            code.append("    }\n");

            if (!isStatic) {
                code.append("    jobject obj_").append(h).append(" = stack[--sp].l;\n");
                code.append("    if (obj_").append(h).append(" == NULL) {\n");
                code.append("        jclass c=(*env)->FindClass(env,\"java/lang/NullPointerException\"); if(c) (*env)->ThrowNew(env,c,\"Null pointer access\");\n");
                code.append(returnDefault(returnType));
                code.append("    }\n");
            }

            String callType = getJNICallType(callRet);
            String callFunc = isStatic ? "CallStatic" + callType + "MethodA" : "Call" + callType + "MethodA";
            code.append("    ");
            if (callRet.getSort() != Type.VOID) {
                code.append(getJNIType(callType.equals("Object") ? Type.getObjectType("java/lang/Object") : callRet)).append(" res_").append(h).append(" = ");
            }
            code.append("(*env)->").append(callFunc).append("(env, ").append(isStatic ? "cls_" + h : "obj_" + h).append(", mid_").append(h).append(", args_").append(h).append(");\n");
            if (callRet.getSort() != Type.VOID) {
                code.append("    stack[sp++].").append(getTypeField(callRet)).append(" = res_").append(h).append(";\n");
            }
            code.append(generateExceptionHandling(currentIndex, returnType));
        }
    }

    String generateExceptionHandling(int index, Type returnType) {
        if (ctx.currentTryCatchBlocks == null || ctx.currentTryCatchBlocks.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("    if ((*env)->ExceptionCheck(env)) {\n");
            sb.append(returnType.getSort() == Type.VOID ? "        return;\n" : "        return 0;\n");
            sb.append("    }\n");
            return sb.toString();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("    if ((*env)->ExceptionCheck(env)) {\n");
        sb.append("        jthrowable ex = (*env)->ExceptionOccurred(env);\n");
        sb.append("        (*env)->ExceptionClear(env);\n");

        for (TryCatchBlockNode tcb : ctx.currentTryCatchBlocks) {
            Integer start   = ctx.currentLabelMap.get(tcb.start);
            Integer end     = ctx.currentLabelMap.get(tcb.end);
            Integer handler = ctx.currentLabelMap.get(tcb.handler);
            if (start != null && end != null && handler != null && index >= start && index < end) {
                if (tcb.type == null) {
                    sb.append("        stack[sp++].l = ex;\n");
                    sb.append("        goto L").append(tcb.handler.hashCode()).append(";\n");
                } else {
                    sb.append("        {\n");
                    sb.append("            jclass tc_cls = (*env)->FindClass(env, \"").append(tcb.type).append("\");\n");
                    sb.append("            if (tc_cls == NULL) { (*env)->ExceptionClear(env); }\n");
                    sb.append("            else {\n");
                    sb.append("                jboolean match = (*env)->IsInstanceOf(env, ex, tc_cls);\n");
                    sb.append("                (*env)->DeleteLocalRef(env, tc_cls);\n");
                    sb.append("                if (match) { stack[sp++].l = ex; goto L").append(tcb.handler.hashCode()).append("; }\n");
                    sb.append("            }\n");
                    sb.append("        }\n");
                }
            }
        }

        sb.append("        (*env)->Throw(env, ex);\n");
        sb.append(returnType.getSort() == Type.VOID ? "        return;\n" : "        return 0;\n");
        sb.append("    }\n");
        return sb.toString();
    }

    private static String returnDefault(Type returnType) {
        return CTypeUtils.returnDefault(returnType);
    }
}
