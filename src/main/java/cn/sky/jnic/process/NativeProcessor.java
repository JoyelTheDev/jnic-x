package cn.sky.jnic.process;

import cn.sky.jnic.Jnic;
import cn.sky.jnic.crypto.ChaCha20;
import cn.sky.jnic.crypto.DatKeyGen;
import cn.sky.jnic.generator.CGenerator;
import cn.sky.jnic.utils.MatcherUtils;
import cn.sky.jnic.utils.asm.ClassWrapper;
import cn.sky.jnic.utils.asm.MethodWrapper;
import lombok.Getter;
import org.objectweb.asm.Opcodes;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;

public class NativeProcessor {
    @Getter
    private final Jnic jnic;
    private final CGenerator generator;
    private final List<String> generatedNativeMethods = new ArrayList<>();
    private final Set<ClassWrapper> processedClasses = new HashSet<>();
    @Getter
    private DatKeyGen.KeyMaterial keyMaterial;

    public NativeProcessor(Jnic jnic) {
        this.jnic = jnic;
        this.generator = new CGenerator(this);
    }

    public boolean isNative(String owner, String name, String desc) {
        ClassWrapper classWrapper = jnic.getClasses().get(owner);
        if (classWrapper == null)
            return false;
        if (!shouldProcessClass(classWrapper))
            return false;

        for (MethodWrapper mw : classWrapper.getMethods()) {
            if (mw.getOriginalName().equals(name) && mw.getOriginalDescriptor().equals(desc)) {
                return shouldProcessMethod(mw);
            }
        }
        return false;
    }

    public void process() {
        Jnic.getLogger().info("Starting native processing...");

        for (ClassWrapper classWrapper : jnic.getClasses().values()) {
            if (!shouldProcessClass(classWrapper)) continue;

            boolean classModified = false;
            List<MethodWrapper> methods = new ArrayList<>(classWrapper.getMethods());
            for (MethodWrapper methodWrapper : methods) {
                if (shouldProcessMethod(methodWrapper)) {
                    processMethod(classWrapper, methodWrapper);
                    classModified = true;
                }
            }
            if (classModified) processedClasses.add(classWrapper);
        }

        generator.finalizeGeneration();

        try (InputStream is = getClass().getResourceAsStream("/jni.h")) {
            if (is != null) {
                Files.copy(is, new File(jnic.getTmpdir(), "jni.h").toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Jnic.getLogger().warn("jni.h not found in resources. Compilation might fail if system headers are missing.");
            }
        } catch (IOException e) {
            Jnic.getLogger().error("Failed to extract jni.h: " + e.getMessage());
        }

        File cFile = new File(jnic.getTmpdir(), Jnic.getInstance().getTempC().toString() + ".c");

        if (cFile.exists()) {
            ZigCompiler.compile(cFile, jnic.getTmpdir(), jnic.getConfig().getTargets());

            File[] files = jnic.getTmpdir().listFiles();
            if (files != null) {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                     DataOutputStream dos = new DataOutputStream(baos)) {

                    List<File> libsToPack = new ArrayList<>();
                    for (File lib : files) {
                        String name = lib.getName();
                        if (name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib")) {
                            libsToPack.add(lib);
                        }
                    }

                    dos.writeInt(libsToPack.size());
                    for (File lib : libsToPack) {
                        byte[] nameBytes = lib.getName().getBytes(StandardCharsets.UTF_8);
                        dos.writeInt(nameBytes.length);
                        dos.write(nameBytes);

                        byte[] content = Files.readAllBytes(lib.toPath());
                        dos.writeInt(content.length);
                        dos.write(content);

                        Jnic.getLogger().info("Packed library: " + lib.getName());
                    }

                    byte[] plaintext = baos.toByteArray();

                    keyMaterial = DatKeyGen.generate(jnic.getTempOut());
                    byte[] data = ChaCha20.crypt(plaintext, keyMaterial.key(), keyMaterial.nonce());

                    jnic.getResources().put("cn/sky/jnic/" + jnic.getTempOut().toString() + ".dat", data);
                    Jnic.getLogger().info("Generated ChaCha20-encrypted dat file with " + libsToPack.size() + " libraries.");

                } catch (IOException e) {
                    Jnic.getLogger().error("Failed to pack native libraries: " + e.getMessage());
                }
            }

        } else {
            Jnic.getLogger().error("Native source file not found: " + cFile.getAbsolutePath());
        }

        HashMap<String, ClassWrapper> temp = new HashMap<>();
        for (ClassWrapper classWrapper : processedClasses) {
            injectLoader(classWrapper, temp);
        }
        jnic.getClasses().putAll(temp);
    }

    private boolean shouldProcessClass(ClassWrapper classWrapper) {
        String className = classWrapper.getName();

        List<String> excludes = jnic.getConfig().getExclude();
        if (excludes != null) {
            for (String exclude : excludes) {
                if (MatcherUtils.match(className, exclude)) {
                    return false;
                }
            }
        }

        List<String> includes = jnic.getConfig().getInclude();
        if (includes != null && !includes.isEmpty()) {
            boolean included = false;
            for (String include : includes) {
                if (MatcherUtils.match(className, include)) {
                    included = true;
                    break;
                }
            }
            if (!included)
                return false;
        }

        return (classWrapper.getClassNode().access & Opcodes.ACC_INTERFACE) == 0;
    }

    private boolean shouldProcessMethod(MethodWrapper methodWrapper) {
        String name = methodWrapper.getOriginalName();
        if ("<init>".equals(name) || "<clinit>".equals(name)) {
            return false;
        }

        String desc = methodWrapper.getOriginalDescriptor();
        if ("findClass".equals(name) && "(Ljava/lang/String;)Ljava/lang/Class;".equals(desc)) {
            return false;
        }
        if ("getResourceAsStream".equals(name) && "(Ljava/lang/String;)Ljava/io/InputStream;".equals(desc)) {
            return false;
        }

        if (hasUnsupportedOpcodes(methodWrapper.getMethodNode())) {
            Jnic.getLogger().warn("Skipping method with unsupported opcodes: " + name);
            return false;
        }

        return (methodWrapper.getMethodNode().access & Opcodes.ACC_ABSTRACT) == 0 &&
                (methodWrapper.getMethodNode().access & Opcodes.ACC_NATIVE) == 0 &&
                methodWrapper.getMethodNode().instructions.size() > 0;
    }

    private boolean hasUnsupportedOpcodes(MethodNode methodNode) {
        for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            int opcode = insn.getOpcode();
            if (opcode < 0)
                continue;
            switch (opcode) {
                case Opcodes.MULTIANEWARRAY:
                case Opcodes.JSR:
                case Opcodes.RET:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    private void injectLoader(ClassWrapper classWrapper, HashMap<String, ClassWrapper> classes) {
        try {
            String loader = "cn/sky/jnic/JNICLoader";
            if (!jnic.getClasses().containsKey(loader)) {
                InputStream is = getClass().getResourceAsStream("/" + loader + ".class");
                if (is == null) {
                    throw new IOException("Could not find JNICLoader.class to inject!");
                }
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    try (DataInputStream dis = new DataInputStream(is)) {
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = dis.read(buffer)) != -1) {
                            baos.write(buffer, 0, read);
                        }
                    }
                    byte[] originalBytes = baos.toByteArray();

                    String placeholder = "000000000000000000000000000000000000";
                    String replacement = jnic.getTempOut().toString();
                    byte[] processedBytes = replacePlaceholderInBytes(
                            originalBytes,
                            placeholder.getBytes(StandardCharsets.UTF_8),
                            replacement.getBytes(StandardCharsets.UTF_8));

                    if (keyMaterial != null) {
                        processedBytes = patchKeyConstants(processedBytes, keyMaterial);
                    }

                    classes.put(loader, ClassWrapper.from(new ClassReader(processedBytes)));
                }
            }
        } catch (Exception e) {
            Jnic.getLogger().error("Failed to inject JNICLoader", e);
        }

        InsnList il = new InsnList();
        il.add(new LdcInsnNode("jnic"));
        il.add(new LdcInsnNode(Type.getObjectType(classWrapper.getName())));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "cn/sky/jnic/JNICLoader", "load",
                "(Ljava/lang/String;Ljava/lang/Class;)V", false));

        MethodNode clinit = classWrapper.getMethodNode("<clinit>", "()V");
        if (clinit == null) {
            clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            classWrapper.addMethod(clinit);
        } else {
            if (clinit.instructions.size() > 0) {
                clinit.instructions.remove(clinit.instructions.getLast());
            }
        }

        clinit.instructions.add(il);
        clinit.instructions.add(new InsnNode(Opcodes.RETURN));
    }

    private void processMethod(ClassWrapper owner, MethodWrapper method) {
        Jnic.getLogger().info("Processing method: " + owner.getName() + "." + method.getOriginalName());

        handleInvokeDynamic(owner, method);

        String cCode = generator.generateMethod(owner, method);

        method.getMethodNode().access |= Opcodes.ACC_NATIVE;
        method.getMethodNode().instructions.clear();
        method.getMethodNode().tryCatchBlocks.clear();
        method.getMethodNode().localVariables.clear();

        generatedNativeMethods.add(owner.getName() + "_" + method.getOriginalName());
    }

    private void handleInvokeDynamic(ClassWrapper owner, MethodWrapper method) {
        InsnList instructions = method.getMethodNode().instructions;
        List<InvokeDynamicInsnNode> indyNodes = new ArrayList<>();

        for (AbstractInsnNode insn = instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof InvokeDynamicInsnNode) {
                indyNodes.add((InvokeDynamicInsnNode) insn);
            }
        }

        for (InvokeDynamicInsnNode indy : indyNodes) {
            String helperName = "indy_wrapper_" + Math.abs(indy.hashCode());

            MethodNode helper = new MethodNode(Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, helperName, indy.desc, null,
                    null);

            InsnList il = helper.instructions;
            Type[] args = Type.getArgumentTypes(indy.desc);
            int varIndex = 0;
            for (Type arg : args) {
                il.add(new VarInsnNode(arg.getOpcode(Opcodes.ILOAD), varIndex));
                varIndex += arg.getSize();
            }

            il.add(indy.clone(null));

            Type returnType = Type.getReturnType(indy.desc);
            il.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));

            owner.addMethod(helper);

            instructions.set(indy,
                    new MethodInsnNode(Opcodes.INVOKESTATIC, owner.getName(), helperName, indy.desc, false));
        }
    }

    private byte[] patchKeyConstants(byte[] classBytes, DatKeyGen.KeyMaterial km) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, 0);

        java.util.Map<String, Integer> patches = new java.util.HashMap<>();
        int[] kp = km.keyParts(),   km2 = km.keyMasks();
        int[] np = km.nonceParts(), nm  = km.nonceMasks();
        for (int i = 0; i < 8; i++) {
            patches.put("K"  + i, kp[i]);
            patches.put("KM" + i, km2[i]);
        }
        for (int i = 0; i < 3; i++) {
            patches.put("N"  + i, np[i]);
            patches.put("NM" + i, nm[i]);
        }

        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                           String signature, Object value) {
                Object newValue = patches.containsKey(name) ? patches.get(name) : value;
                return super.visitField(access, name, descriptor, signature, newValue);
            }

            @Override
            public org.objectweb.asm.MethodVisitor visitMethod(int access, String name,
                    String descriptor, String signature, String[] exceptions) {
                org.objectweb.asm.MethodVisitor mv =
                        super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"<clinit>".equals(name)) return mv;

                return new org.objectweb.asm.MethodVisitor(Opcodes.ASM9, mv) {
                    private Integer pendingPatch = null;

                    @Override
                    public void visitLdcInsn(Object cst) {
                        super.visitLdcInsn(cst);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fname, String fdesc) {
                        if (opcode == Opcodes.PUTSTATIC && patches.containsKey(fname)) {
                        }
                        super.visitFieldInsn(opcode, owner, fname, fdesc);
                    }
                };
            }
        }, 0);

        byte[] firstPass = cw.toByteArray();
        ClassReader cr2 = new ClassReader(firstPass);
        ClassWriter cw2 = new ClassWriter(cr2, ClassWriter.COMPUTE_MAXS);

        cr2.accept(new ClassVisitor(Opcodes.ASM9, cw2) {
            @Override
            public org.objectweb.asm.MethodVisitor visitMethod(int access, String name,
                    String descriptor, String signature, String[] exceptions) {
                if (!"<clinit>".equals(name)) {
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
                org.objectweb.asm.tree.MethodNode mn =
                    new org.objectweb.asm.tree.MethodNode(Opcodes.ASM9, access, name,
                        descriptor, signature, exceptions);
                return new org.objectweb.asm.MethodVisitor(Opcodes.ASM9, mn) {
                    @Override
                    public void visitEnd() {
                        super.visitEnd();
                        AbstractInsnNode[] insns = mn.instructions.toArray();
                        for (int i = 0; i < insns.length - 1; i++) {
                            AbstractInsnNode cur  = insns[i];
                            AbstractInsnNode next = insns[i + 1];
                            if (next instanceof org.objectweb.asm.tree.FieldInsnNode fin
                                    && fin.opcode == Opcodes.PUTSTATIC
                                    && patches.containsKey(fin.name)) {
                                int newVal = patches.get(fin.name);
                                org.objectweb.asm.tree.AbstractInsnNode replacement =
                                    new org.objectweb.asm.tree.LdcInsnNode(newVal);
                                mn.instructions.set(cur, replacement);
                            }
                        }
                        mn.accept(super.mv);
                    }
                };
            }
        }, 0);

        return cw2.toByteArray();
    }

    private byte[] replacePlaceholderInBytes(byte[] original, byte[] placeholder, byte[] replacement) {
        List<Integer> positions = findBytePositions(original, placeholder);

        if (positions.isEmpty()) {
            return original;
        }

        int newLength = original.length + (replacement.length - placeholder.length) * positions.size();
        byte[] result = new byte[newLength];

        int srcPos = 0;
        int dstPos = 0;

        for (int pos : positions) {
            System.arraycopy(original, srcPos, result, dstPos, pos - srcPos);
            dstPos += pos - srcPos;

            System.arraycopy(replacement, 0, result, dstPos, replacement.length);
            dstPos += replacement.length;

            srcPos = pos + placeholder.length;
        }

        System.arraycopy(original, srcPos, result, dstPos, original.length - srcPos);

        return result;
    }

    private List<Integer> findBytePositions(byte[] data, byte[] pattern) {
        List<Integer> positions = new ArrayList<>();
        if (pattern.length == 0) return positions;

        for (int i = 0; i <= data.length - pattern.length; i++) {
            boolean found = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                positions.add(i);
            }
        }
        return positions;
    }
}
