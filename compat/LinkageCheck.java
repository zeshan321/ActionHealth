import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Checks that every Bukkit and BungeeCord chat class, method and field that a plugin jar
 * references directly also exists in a server or API jar. Resolution walks superclasses
 * and interfaces like the JVM does. Reflection is not checked, because it is guarded at runtime.
 * <p>
 * Usage: java -cp asm.jar:asm-tree.jar LinkageCheck.java plugin.jar target.jar [target.jar ...]
 * Each target is checked separately. Exit code 1 means at least one target has missing references.
 */
public class LinkageCheck {

    private static final String[] CHECKED_PREFIXES = {"org/bukkit/", "net/md_5/bungee/"};

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: LinkageCheck plugin.jar target.jar [target.jar ...]");
            System.exit(2);
        }

        Map<String, Set<String>> refs = collectReferences(Paths.get(args[0]));
        System.out.println("Checking " + refs.size() + " Bukkit/BungeeCord references from " + args[0]);

        boolean failed = false;
        for (int i = 1; i < args.length; i++) {
            Path target = Paths.get(args[i]);
            try (ZipFile zip = new ZipFile(target.toFile())) {
                Resolver resolver = new Resolver(zip);
                boolean hasBungee = zip.getEntry("net/md_5/bungee/api/chat/BaseComponent.class") != null;
                List<String> missing = new ArrayList<>();

                for (Map.Entry<String, Set<String>> entry : refs.entrySet()) {
                    String ref = entry.getKey();
                    // API-only jars do not bundle BungeeCord chat. Server jars do.
                    if (!hasBungee && ref.substring(2).startsWith("net/md_5/")) continue;
                    if (!resolver.resolves(ref)) {
                        missing.add(ref + "  <-  " + String.join(", ", entry.getValue()));
                    }
                }

                String name = target.getFileName().toString();
                if (missing.isEmpty()) {
                    System.out.println("OK      " + name + (hasBungee ? "" : " (Bukkit API only)"));
                } else {
                    failed = true;
                    System.out.println("MISSING " + name);
                    missing.forEach(m -> System.out.println("          " + m));
                }
            }
        }

        System.exit(failed ? 1 : 0);
    }

    /**
     * Returns references encoded as "C owner", "M owner.name desc" or "F owner.name desc",
     * mapped to the plugin classes that use them.
     */
    private static Map<String, Set<String>> collectReferences(Path jar) throws IOException {
        Map<String, Set<String>> refs = new HashMap<>();

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                if (!entry.getName().endsWith(".class")) continue;

                try (InputStream in = zip.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(in);
                    String user = reader.getClassName();
                    reader.accept(new Collector(user, refs), 0);
                }
            }
        }

        return refs;
    }

    private static boolean checked(String internalName) {
        for (String prefix : CHECKED_PREFIXES) {
            if (internalName.startsWith(prefix)) return true;
        }

        return false;
    }

    private static class Collector extends ClassVisitor {
        private final String user;
        private final Map<String, Set<String>> refs;

        Collector(String user, Map<String, Set<String>> refs) {
            super(Opcodes.ASM9);
            this.user = user;
            this.refs = refs;
        }

        void add(String ref) {
            refs.computeIfAbsent(ref, k -> new TreeSet<>()).add(user.substring(user.lastIndexOf('/') + 1));
        }

        void type(Type type) {
            if (type.getSort() == Type.ARRAY) type = type.getElementType();
            if (type.getSort() == Type.OBJECT && checked(type.getInternalName())) add("C " + type.getInternalName());
            if (type.getSort() == Type.METHOD) {
                type(type.getReturnType());
                for (Type arg : type.getArgumentTypes()) type(arg);
            }
        }

        void internalName(String name) {
            if (name != null) type(name.startsWith("[") ? Type.getType(name) : Type.getObjectType(name));
        }

        void member(char kind, String owner, String name, String desc) {
            if (owner.startsWith("[")) return;
            internalName(owner);
            type(Type.getType(desc));
            if (checked(owner)) add(kind + " " + owner + "." + name + " " + desc);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            internalName(superName);
            if (interfaces != null) for (String i : interfaces) internalName(i);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            type(Type.getType(desc));
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            type(Type.getMethodType(desc));
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitTypeInsn(int opcode, String type) {
                    internalName(type);
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                    member('F', owner, name, desc);
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                    member('M', owner, name, desc);
                }

                @Override
                public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
                    type(Type.getMethodType(desc));
                    for (Object arg : bsmArgs) {
                        if (arg instanceof Handle) {
                            Handle handle = (Handle) arg;
                            boolean field = handle.getTag() <= Opcodes.H_PUTSTATIC;
                            member(field ? 'F' : 'M', handle.getOwner(), handle.getName(), handle.getDesc());
                        } else if (arg instanceof Type) {
                            type((Type) arg);
                        }
                    }
                }

                @Override
                public void visitLdcInsn(Object value) {
                    if (value instanceof Type) type((Type) value);
                }

                @Override
                public void visitMultiANewArrayInsn(String desc, int dims) {
                    type(Type.getType(desc));
                }

                @Override
                public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                    internalName(type);
                }
            };
        }
    }

    private static class Resolver {
        private final ZipFile zip;
        private final Map<String, ClassNode> cache = new HashMap<>();

        Resolver(ZipFile zip) {
            this.zip = zip;
        }

        ClassNode load(String name) {
            if (cache.containsKey(name)) return cache.get(name);

            ClassNode node = null;
            try {
                ZipEntry entry = zip.getEntry(name + ".class");
                InputStream in = entry != null
                        ? zip.getInputStream(entry)
                        : ClassLoader.getSystemResourceAsStream(name + ".class");
                if (in != null) {
                    try (InputStream stream = in) {
                        node = new ClassNode();
                        new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            cache.put(name, node);
            return node;
        }

        boolean resolves(String ref) {
            char kind = ref.charAt(0);
            String rest = ref.substring(2);
            if (kind == 'C') return load(rest) != null;

            int dot = rest.indexOf('.');
            int space = rest.indexOf(' ');
            String owner = rest.substring(0, dot);
            String name = rest.substring(dot + 1, space);
            String desc = rest.substring(space + 1);
            return find(owner, name, desc, kind == 'F', new TreeSet<>());
        }

        private boolean find(String owner, String name, String desc, boolean field, Set<String> seen) {
            if (owner == null || !seen.add(owner)) return false;

            ClassNode node = load(owner);
            if (node == null) return false;

            if (field) {
                for (FieldNode f : node.fields) {
                    if (f.name.equals(name) && f.desc.equals(desc)) return true;
                }
            } else {
                for (MethodNode m : node.methods) {
                    if (m.name.equals(name) && m.desc.equals(desc)) return true;
                    // Signature polymorphic methods are not relevant here, so an exact match is required.
                }
            }

            if (find(node.superName, name, desc, field, seen)) return true;
            for (String i : node.interfaces) {
                if (find(i, name, desc, field, seen)) return true;
            }

            return false;
        }
    }
}
