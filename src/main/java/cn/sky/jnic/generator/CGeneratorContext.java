package cn.sky.jnic.generator;

import cn.sky.jnic.config.Config;
import cn.sky.jnic.utils.asm.ClassWrapper;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CGeneratorContext {

    static class NativeEntry {
        String className;
        String methodName;
        String signature;
        String cFunctionName;
        boolean isStatic;

        NativeEntry(String c, String m, String s, String cf, boolean stat) {
            className = c;
            methodName = m;
            signature = s;
            cFunctionName = cf;
            isStatic = stat;
        }
    }

    final Config config;
    final Obfuscator obfuscator;

    final List<NativeEntry> nativeEntries = new ArrayList<>();
    final StringBuilder functionPrototypes = new StringBuilder();
    final StringBuilder methodImplementations = new StringBuilder();

    List<TryCatchBlockNode> currentTryCatchBlocks;
    Map<LabelNode, Integer> currentLabelMap;
    Frame<BasicValue>[] currentFrames;
    String currentClassName;
    String currentMethodName;
    ClassWrapper currentClass;

    CGeneratorContext(Config config) {
        this.config = config;
        this.obfuscator = new Obfuscator(config);
    }
}
