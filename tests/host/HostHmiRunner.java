import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/**
 * JDK 8 host tests only. Converted JXE classes expose the firmware API but can contain
 * code on abstract methods and non-JVM instructions. Never execute their method bodies.
 * Load real test/mod classes first; supply API-shaped stubs for firmware dependencies.
 * Stub constructors only initialise Object/super; other concrete methods throw.
 * No generated stub is written to disk or packaged into AAtoKombi.jar.
 */
public final class HostHmiRunner {
    private static byte[] apiStub(InputStream input) throws Exception {
        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM5, writer) {
            private String owner;
            private String parent;
            private boolean isInterface;
            private boolean defaultConstructor;

            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                owner = name;
                parent = superName;
                isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
                // Generated bodies have no branches; use the Java 1.3 verifier, as for the mod.
                super.visit(Opcodes.V1_3, access, name, signature, superName, interfaces);
            }

            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (name.equals("<clinit>")) return null;
                if (name.equals("<init>") && descriptor.equals("()V")) defaultConstructor = true;
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ((access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) return null;
                method.visitCode();
                if (name.equals("<init>")) {
                    method.visitVarInsn(Opcodes.ALOAD, 0);
                    method.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
                    method.visitInsn(Opcodes.RETURN);
                } else {
                    method.visitTypeInsn(Opcodes.NEW, "java/lang/UnsupportedOperationException");
                    method.visitInsn(Opcodes.DUP);
                    method.visitLdcInsn("Host HMI stub called: " + owner + "." + name);
                    method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/UnsupportedOperationException",
                            "<init>", "(Ljava/lang/String;)V", false);
                    method.visitInsn(Opcodes.ATHROW);
                }
                method.visitMaxs(0, 0);
                method.visitEnd();
                return null;
            }

            public void visitEnd() {
                // Allow stub-only superclass construction without depending on firmware code.
                if (!isInterface && !defaultConstructor) {
                    visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
                }
                super.visitEnd();
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return writer.toByteArray();
    }

    public static void main(String[] args) throws Throwable {
        if (args.length != 4) throw new IllegalArgumentException("<MIBHMI.jar> <mod classes> <test classes> <main>");
        try (final JarFile firmware = new JarFile(args[0]);
             URLClassLoader loader = new URLClassLoader(new URL[]{
                     new File(args[2]).toURI().toURL(), new File(args[1]).toURI().toURL()
             }, HostHmiRunner.class.getClassLoader()) {
                 protected Class<?> findClass(String name) throws ClassNotFoundException {
                     try {
                         return super.findClass(name);
                     } catch (ClassNotFoundException absentFromMod) {
                         JarEntry entry = firmware.getJarEntry(name.replace('.', '/') + ".class");
                         if (entry == null) throw absentFromMod;
                         try (InputStream input = firmware.getInputStream(entry)) {
                             byte[] bytes = apiStub(input);
                             return defineClass(name, bytes, 0, bytes.length);
                         } catch (Exception failure) {
                             throw new ClassNotFoundException(name, failure);
                         }
                     }
                 }
             }) {
            System.out.println("Host test with firmware API stubs: " + args[3]);
            try {
                loader.loadClass(args[3]).getMethod("main", String[].class).invoke(null, (Object)new String[0]);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }
}
