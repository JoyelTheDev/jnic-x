package cn.sky.jnic.generator;

import cn.sky.jnic.utils.asm.ClassWrapper;
import cn.sky.jnic.utils.asm.MethodWrapper;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;

import java.util.HashMap;
import java.util.Map;

import static cn.sky.jnic.generator.CTypeUtils.*;

public class CMethodEmitter {

    private final CGeneratorContext ctx;
    private final CInstructionEmitter instrEmitter;

    CMethodEmitter(CGeneratorContext ctx, CInstructionEmitter instrEmitter) {
        this.ctx = ctx;
        this.instrEmitter = instrEmitter;
    }

    public String emitMethod(ClassWrapper owner, MethodWrapper method) {
        Type returnType = Type.getReturnType(method.getOriginalDescriptor());
        Type[] argTypes = Type.getArgumentTypes(method.getOriginalDescriptor());

        String functionName = "native_"
                + Integer.toHexString(method.getOriginalName().hashCode())
                + "_"
                + Integer.toHexString(owner.getName().hashCode());

        StringBuilder proto = new StringBuilder();
        proto.append("JNIEXPORT ").append(getJNIType(returnType)).append(" JNICALL ").append(functionName)
                .append("(JNIEnv *env, jobject thiz");
        for (int i = 0; i < argTypes.length; i++) {
            proto.append(", ").append(getJNIType(argTypes[i])).append(" arg").append(i);
        }
        proto.append(")");

        ctx.functionPrototypes.append(proto).append(";\n");

        StringBuilder body = new StringBuilder();
        body.append(proto).append(" {\n");
        body.append(ctx.obfuscator.getAntiDebugCode());
        body.append(emitPrologue(method, returnType, argTypes));

        Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicInterpreter());
        try {
            ctx.currentFrames = analyzer.analyze(owner.getName(), method.getMethodNode());
        } catch (AnalyzerException e) {
            throw new RuntimeException("Stack analysis failed for " + method.getOriginalName(), e);
        }

        Map<LabelNode, Integer> labelMap = buildLabelMap(method.getMethodNode().instructions);

        ctx.currentTryCatchBlocks = method.getMethodNode().tryCatchBlocks;
        ctx.currentLabelMap = labelMap;
        ctx.currentClassName = owner.getName();
        ctx.currentMethodName = method.getOriginalName();
        ctx.currentClass = owner;

        body.append(emitInstructions(method.getMethodNode().instructions, labelMap, returnType));

        body.append("    (*env)->PopLocalFrame(env, NULL);\n");
        if (returnType.getSort() == Type.VOID) {
            body.append("    return;\n");
        } else {
            body.append("    return 0;\n");
        }
        body.append("}\n\n");

        String fullCode = body.toString();
        ctx.methodImplementations.append(fullCode);
        ctx.nativeEntries.add(new CGeneratorContext.NativeEntry(
                owner.getName(),
                method.getOriginalName(),
                method.getOriginalDescriptor(),
                functionName,
                method.isStatic()
        ));

        ctx.currentTryCatchBlocks = null;
        ctx.currentLabelMap = null;
        ctx.currentFrames = null;
        ctx.currentClassName = null;
        ctx.currentClass = null;

        return fullCode;
    }

    private String emitPrologue(MethodWrapper method, Type returnType, Type[] argTypes) {
        int maxStack  = method.getMethodNode().maxStack + 10;
        int maxLocals = method.getMethodNode().maxLocals + 10;

        StringBuilder sb = new StringBuilder();
        sb.append("    StackValue stack[").append(maxStack).append("];\n");
        sb.append("    memset(stack, 0, sizeof(stack));\n");
        sb.append("    StackValue locals[").append(maxLocals).append("];\n");
        sb.append("    memset(locals, 0, sizeof(locals));\n");

        int localIndex = 0;
        if (!method.isStatic()) {
            sb.append("    locals[").append(localIndex++).append("].l = thiz;\n");
        }
        for (int i = 0; i < argTypes.length; i++) {
            sb.append("    locals[").append(localIndex).append("].").append(getTypeField(argTypes[i]))
                    .append(" = arg").append(i).append(";\n");
            localIndex += argTypes[i].getSize();
        }

        sb.append("\n");
        sb.append("    log_debug(\"Enter: native env: %p, thiz: %p\\n\", env, thiz);\n");
        sb.append("    if ((*env)->PushLocalFrame(env, 256) < 0) {\n");
        if (returnType.getSort() == Type.VOID) {
            sb.append("        return;\n");
        } else {
            sb.append("        return 0;\n");
        }
        sb.append("    }\n");

        return sb.toString();
    }

    private Map<LabelNode, Integer> buildLabelMap(InsnList instructions) {
        Map<LabelNode, Integer> labelMap = new HashMap<>();
        int index = 0;
        for (AbstractInsnNode insn : instructions) {
            if (insn instanceof LabelNode ln) {
                labelMap.put(ln, index);
            }
            index++;
        }
        return labelMap;
    }

    private String emitInstructions(InsnList instructions, Map<LabelNode, Integer> labelMap, Type returnType) {
        StringBuilder sb = new StringBuilder();
        int currentIndex = 0;
        for (AbstractInsnNode insn : instructions) {
            for (Map.Entry<LabelNode, Integer> entry : labelMap.entrySet()) {
                if (entry.getValue() == currentIndex) {
                    sb.append("L").append(entry.getKey().hashCode()).append(":;\n");
                }
            }
            sb.append(instrEmitter.emit(insn, labelMap, currentIndex, returnType));
            currentIndex++;
        }
        return sb.toString();
    }
}
