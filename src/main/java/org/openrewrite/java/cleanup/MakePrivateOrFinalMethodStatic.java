package org.openrewrite.java.cleanup;

import static java.util.Collections.emptyList;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.ClassDeclaration;
import org.openrewrite.java.tree.J.Identifier;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.J.Modifier;
import org.openrewrite.java.tree.J.Modifier.Type;
import org.openrewrite.java.tree.J.NewClass;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaType.Class;
import org.openrewrite.java.tree.JavaType.FullyQualified.Kind;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

public class MakePrivateOrFinalMethodStatic extends Recipe {

    // List of method matchers for methods which are used for serialization.
    private static final List<MethodMatcher> ignoredSerializationMethods = Arrays.asList(
        new MethodMatcher("* writeObject(java.io.ObjectOutputStream)"),
        new MethodMatcher("* readObject(java.io.ObjectInputStream)"),
        new MethodMatcher("* readObjectNoData()"));
    private static final String ACCESS_INSTANCE_DATA = "ACCESS_INSTANCE_DATA";
    private static final String METHOD_ENCLOSING_CLASS = "METHOD_ENCLOSING_CLASS";

    @Override
    public String getDisplayName() {
        return "Make final or private methods with no instance access static";
    }

    @Override
    public String getDescription() {
        return "Recipe to make private or final methods static if they don't access instance data.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-2325");
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public MethodDeclaration visitMethodDeclaration(MethodDeclaration method,
                final ExecutionContext executionContext) {
                final ClassDeclaration enclosingClass = getCursor().firstEnclosingOrThrow(
                    ClassDeclaration.class);
                // Method is not a potential candidate. Ignore method.
                if (!isMethodPotentialCandidate(method, enclosingClass)) {
                    return super.visitMethodDeclaration(method, executionContext);
                }
                // Put class declaration as a message to a method declaration cursor,
                // to indicate that method is a potential candidate
                // and also to check that method access its own class instance data.
                getCursor().putMessage(METHOD_ENCLOSING_CLASS, enclosingClass);

                // Visit subtree to check whether method access instance data.
                method = super.visitMethodDeclaration(method, executionContext);

                // Method is accessing instance data. Ignoring method.
                if (getCursor().getMessage(ACCESS_INSTANCE_DATA, false)) {
                    return method;
                }
                // If method has final modifier replace it with static modifier.
                if (method.hasModifier(Modifier.Type.Final)) {
                    return method.withModifiers(ListUtils.map(method.getModifiers(),
                        mod -> mod.getType() == Modifier.Type.Final ? mod.withType(
                            Modifier.Type.Static) : mod));
                }
                // Add static modifier to the method.
                // TODO replace with JavaTemplate
                final Modifier staticModifier = new Modifier(Tree.randomId(),
                    Space.build(" ", emptyList()), Markers.EMPTY,
                    Modifier.Type.Static, Collections.emptyList());
                return method.withModifiers(
                    ListUtils.concat(method.getModifiers(), staticModifier));
            }

            @Override
            public Identifier visitIdentifier(final Identifier identifier,
                final ExecutionContext executionContext) {
                final Identifier visitedIdentifier = super.visitIdentifier(identifier, executionContext);
                if (identifier.getFieldType() == null) {
                    // Identifier field type is null, ignoring identifier.
                    return visitedIdentifier;
                }
                if (!(identifier.getFieldType().getOwner() instanceof JavaType.Class)) {
                    // Identifier owner is not a Class, ignoring identifier.
                    return visitedIdentifier;
                }
                if (identifier.getFieldType().hasFlags(Flag.Static)) {
                    // Identifier is static, ignoring identifier.
                    return visitedIdentifier;
                }

                final Cursor enclosingMethodCursor = getEnclosingMethod(getCursor());
                if (enclosingMethodCursor == null) {
                    // Identifier is not part of the method body. Ignore identifier.
                    return visitedIdentifier;
                }
                final ClassDeclaration methodEnclosingClass = enclosingMethodCursor.getMessage(
                    METHOD_ENCLOSING_CLASS);
                if (methodEnclosingClass == null) {
                    // Enclosing method wasn't considered as a candidate. Ignore identifier.
                    return visitedIdentifier;
                }
                if (isTheSameClassOrChild((Class) methodEnclosingClass.getType(),
                    (Class) identifier.getFieldType().getOwner())) {
                    // Enclosing method class is the same or a child of identifier owner class.
                    // Mark method as it access instance data.
                    enclosingMethodCursor.putMessage(ACCESS_INSTANCE_DATA, true);
                }
                return visitedIdentifier;
            }

            @Override
            public MethodInvocation visitMethodInvocation(MethodInvocation method,
                final ExecutionContext executionContext) {
                final MethodInvocation visitedMethod = super.visitMethodInvocation(method,
                    executionContext);
                final JavaType.Method methodType = visitedMethod.getMethodType();
                if (methodType == null) {
                    // Method type is null, ignoring method.
                    return visitedMethod;
                }
                // Called method is static, ignoring method.
                if (methodType.hasFlags(Flag.Static)) {
                    return visitedMethod;
                }
                final Cursor enclosingMethodCursor = getEnclosingMethod(getCursor());
                if (enclosingMethodCursor == null) {
                    // Identifier is not part of the method body. Ignore method.
                    return visitedMethod;
                }
                final ClassDeclaration methodEnclosingClass = enclosingMethodCursor.getMessage(
                    METHOD_ENCLOSING_CLASS);
                if (methodEnclosingClass == null) {
                    // Enclosing method wasn't considered as a candidate. Ignore method.
                    return visitedMethod;
                }
                if (isTheSameClassOrChild((Class) methodEnclosingClass.getType(),
                    (JavaType.Class) methodType.getDeclaringType())) {
                    // Enclosing method class is the same or a child of invoking method class .
                    // Mark method as it access instance data.
                    enclosingMethodCursor.putMessage(ACCESS_INSTANCE_DATA, true);
                }
                return visitedMethod;
            }

            @Override
            public NewClass visitNewClass(final NewClass newClass,
                final ExecutionContext executionContext) {
                final NewClass visitedNewClass = super.visitNewClass(newClass, executionContext);
                if (visitedNewClass.getClazz() == null) {
                    // Clazz is null. Ignoring new class expression.
                    return visitedNewClass;
                }
                Class type = (Class) visitedNewClass.getClazz().getType();
                if (type.getKind() != Kind.Class) {
                    // Not a class instantiation. Ignoring new class expression.
                    return visitedNewClass;
                }
                if (type.getOwningClass() == null) {
                    // Not an inner class. Ignoring new class expression.
                    return visitedNewClass;
                }
                if (type.hasFlags(Flag.Static)) {
                    // Static inner class. Ignoring new class expression.
                    return visitedNewClass;
                }

                final Cursor enclosingMethodCursor = getEnclosingMethod(getCursor());
                if (enclosingMethodCursor == null) {
                    // Identifier is not part of the method body. Ignore identifier.
                    return visitedNewClass;
                }
                final ClassDeclaration methodEnclosingClass = enclosingMethodCursor.getMessage(
                    METHOD_ENCLOSING_CLASS);
                if (methodEnclosingClass == null) {
                    // Enclosing method wasn't considered as a candidate. Ignore identifier.
                    return visitedNewClass;
                }
                if (isTheSameClassOrChild((Class) methodEnclosingClass.getType(),
                    (JavaType.Class) type.getOwningClass())) {
                    // Enclosing method class is the same or a child of instance of new inner class.
                    // Mark method as it access instance data.
                    enclosingMethodCursor.putMessage(ACCESS_INSTANCE_DATA, true);
                }
                return visitedNewClass;
            }

            /**
             * Checks whether method can be considered to become static. It should be either
             * private or final or both, and it shouldn't be neither native nor static.
             * In addition, method shouldn't be a serialization method unless
             * owner class doesn't implement java.io.Serializable.
             * @param method - method to check
             * @param enclosingClass - method class owner
             * @return true if method is a potential candidate to become static
             */
            private boolean isMethodPotentialCandidate(final MethodDeclaration method,
                final ClassDeclaration enclosingClass) {
                if (method.hasModifier(J.Modifier.Type.Static)) {
                    // Method is already static. Can't be a potential candidate.
                    return false;
                }
                if (method.hasModifier(Type.Native)) {
                    // Method is native. Can't be a potential candidate.
                    return false;
                }
                if (method.hasModifier(J.Modifier.Type.Final) || method.hasModifier(
                    J.Modifier.Type.Private)) {
                    // If method is serializable then it can't be a potential candidate.
                    return !isSerializableMethod(method, enclosingClass);
                }
                return false;
            }

            /**
             * Checks whether method is a serializable method and class implements
             * java.io.Serializable interface.
             * @param method - method to check
             * @param enclosingClass - class that owns the method
             * @return true if method is a serializable method
             */
            private boolean isSerializableMethod(final MethodDeclaration method,
                final @Nullable ClassDeclaration enclosingClass) {
                if (enclosingClass == null || enclosingClass.getType() == null) {
                    return false;
                }
                if (enclosingClass.getType().getInterfaces().stream().anyMatch(
                    intType -> intType.getFullyQualifiedName().equals("java.io.Serializable"))) {
                    for (final MethodMatcher serializableMethodMatcher : ignoredSerializationMethods) {
                        if (serializableMethodMatcher.matches(method, enclosingClass)) {
                            return true;
                        }
                    }
                }
                return false;
            }

            /**
             * Checks whether enclosing class and identifier(field or method) owner class
             * are the same or enclosing class is child of the identifier owner class.
             * @param enclosingClass class where identifier was referenced.
             * @param otherClass identifier owner class
             * @return true if enclosingClass is the same as the otherClass
             *         or a child of the otherClass.
             */
            private boolean isTheSameClassOrChild(@Nullable final JavaType.Class enclosingClass,
                final JavaType.Class otherClass) {
                JavaType.Class currentClass = enclosingClass;
                while (currentClass != null) {
                    if (currentClass.equals(otherClass)) {
                        return true;
                    }
                    currentClass = (JavaType.Class) currentClass.getSupertype();
                }
                return false;
            }

            /**
             * Finds and returns the cursor of enclosing method which is a potential candidate.
             * If no such cursor was found returns null.
             * @param cursor - current visitor' position cursor.
             * @return cursor of enclosing method which is a potential candidate.
             */
            private @Nullable Cursor getEnclosingMethod(final Cursor cursor) {
                Iterator<Cursor> pathAsCursors = cursor.getPathAsCursors(
                    c -> c.getValue() instanceof MethodDeclaration
                        && c.getMessage(METHOD_ENCLOSING_CLASS) != null);
                return pathAsCursors.hasNext() ? pathAsCursors.next() : null;
            }
        };
    }
}
