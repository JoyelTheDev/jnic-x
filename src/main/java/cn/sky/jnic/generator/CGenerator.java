package cn.sky.jnic.generator;

import cn.sky.jnic.Jnic;
import cn.sky.jnic.process.NativeProcessor;
import cn.sky.jnic.utils.asm.ClassWrapper;
import cn.sky.jnic.utils.asm.MethodWrapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CGenerator {

    private final NativeProcessor processor;
    private final CGeneratorContext ctx;
    private final CMethodEmitter methodEmitter;
    private final StringBuilder globalCode = new StringBuilder();

    public CGenerator(NativeProcessor processor) {
        this.processor = processor;
        this.ctx = new CGeneratorContext(processor.getJnic().getConfig());

        CInstructionEmitter instrEmitter = new CInstructionEmitter(ctx, processor);
        this.methodEmitter = new CMethodEmitter(ctx, instrEmitter);

        globalCode.append(CHeaderEmitter.emitHeaders());
    }

    public String generateMethod(ClassWrapper owner, MethodWrapper method) {
        return methodEmitter.emitMethod(owner, method);
    }

    public void finalizeGeneration() {
        globalCode.append("\n// Forward Declarations\n");
        globalCode.append(ctx.functionPrototypes);
        globalCode.append("\n");
        globalCode.append(ctx.methodImplementations);

        globalCode.append(emitJniOnLoad());
        globalCode.append(emitRegisterNatives());

        File outFile = new File(
                Jnic.getInstance().getTmpdir(),
                Jnic.getInstance().getTempC().toString() + ".c"
        );
        try (FileWriter writer = new FileWriter(outFile)) {
            writer.write(globalCode.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String emitJniOnLoad() {
        StringBuilder sb = new StringBuilder();
        sb.append("JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {\n");
        sb.append("    g_jvm = vm;\n");
        sb.append("    JNIEnv* env;\n");
        sb.append("    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) == JNI_OK) {\n");
        sb.append("        init_global_cache(env);\n");
        sb.append("    }\n");
        sb.append("    return JNI_VERSION_1_6;\n");
        sb.append("}\n\n");
        return sb.toString();
    }

    private String emitRegisterNatives() {
        StringBuilder sb = new StringBuilder();
        sb.append("JNIEXPORT void JNICALL Java_cn_sky_jnic_JNICLoader_registerNatives(JNIEnv *env, jclass loader, jclass target) {\n");
        sb.append("    log_debug(\"JNICLoader_registerNatives called. env=%p, target=%p\\n\", env, target);\n");
        sb.append("    if (target == NULL) { log_debug(\"target is NULL\\n\"); return; }\n\n");

        sb.append("    jclass cls_class = (*env)->GetObjectClass(env, target);\n");
        sb.append("    jmethodID mid_getName = (*env)->GetMethodID(env, cls_class, \"getName\", \"()Ljava/lang/String;\");\n");
        sb.append("    if (mid_getName == NULL) { log_debug(\"mid_getName is NULL\\n\"); return; }\n\n");

        sb.append("    jstring nameStr = (jstring)(*env)->CallObjectMethod(env, target, mid_getName);\n");
        sb.append("    if (nameStr == NULL) { log_debug(\"nameStr is NULL\\n\"); return; }\n\n");

        sb.append("    const char *className = (*env)->GetStringUTFChars(env, nameStr, 0);\n");
        sb.append("    if (className == NULL) { log_debug(\"className is NULL\\n\"); return; }\n");
        sb.append("    log_debug(\"Registering natives for class: %s\\n\", className);\n\n");

        Map<String, List<CGeneratorContext.NativeEntry>> classGroups = new HashMap<>();
        for (CGeneratorContext.NativeEntry entry : ctx.nativeEntries) {
            classGroups.computeIfAbsent(entry.className, k -> new ArrayList<>()).add(entry);
        }

        boolean first = true;
        for (Map.Entry<String, List<CGeneratorContext.NativeEntry>> group : classGroups.entrySet()) {
            String internalName  = group.getKey();
            String dotName       = internalName.replace('/', '.');
            String safeClassName = internalName.replace('/', '_').replace('$', '_');
            List<CGeneratorContext.NativeEntry> methods = group.getValue();

            if (!first) sb.append("    else ");
            else first = false;

            sb.append("if (strcmp(className, \"").append(dotName).append("\") == 0) {\n");
            sb.append("        JNINativeMethod methods_").append(safeClassName).append("[] = {\n");
            for (CGeneratorContext.NativeEntry m : methods) {
                sb.append("            {\"").append(m.methodName).append("\", \"")
                        .append(m.signature).append("\", (void *)&").append(m.cFunctionName).append("},\n");
            }
            sb.append("        };\n");
            sb.append("        if ((*env)->RegisterNatives(env, target, methods_").append(safeClassName)
                    .append(", ").append(methods.size()).append(") < 0) {\n");
            sb.append("            (*env)->ExceptionDescribe(env);\n");
            sb.append("            (*env)->ExceptionClear(env);\n");
            sb.append("        }\n");
            sb.append("    }\n");
        }

        sb.append("\n    (*env)->ReleaseStringUTFChars(env, nameStr, className);\n");
        sb.append("}\n");
        return sb.toString();
    }
}
