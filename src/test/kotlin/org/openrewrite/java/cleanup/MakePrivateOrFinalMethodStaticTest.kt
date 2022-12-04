package org.openrewrite.java.cleanup

import org.junit.jupiter.api.Test
import org.openrewrite.java.Assertions
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest

class MakePrivateOrFinalMethodStaticTest : RewriteTest {
    override fun defaults(spec: RecipeSpec) {
        spec.recipe(MakePrivateOrFinalMethodStatic())
    }

    // Assert that private methods with no access to instance data
    // are converted to static by the recipe.
    @Test
    fun methodsWithStaticAccess() = rewriteRun(
            Assertions.java(
                    """
                package com.twin.rewrite;
                class A {
                    private static final String strField = "test";
                    static String getStrField() {
                        return strField;
                    }
                    private void privateFunc() {
                        System.out.println("privateFunc" + strField);
                    }
                    public final void finalFunc() {
                        System.out.println("finalFunc" + getStrField());
                    }
                    private final void privateFinalFunc() {
                        System.out.println("privateFinalFunc" + A.getStrField());
                    }
                }
            """,
                    """
                package com.twin.rewrite;
                class A {
                    private static final String strField = "test";
                    static String getStrField() {
                        return strField;
                    }
                    private static void privateFunc() {
                        System.out.println("privateFunc" + strField);
                    }
                    public static void finalFunc() {
                        System.out.println("finalFunc" + getStrField());
                    }
                    private static void privateFinalFunc() {
                        System.out.println("privateFinalFunc" + A.getStrField());
                    }
                }
            """
            )
    )

    // Assert that private/final methods with instance fields access
    // are stay the same.
    @Test
    fun methodsWithInstanceFieldAccess() = rewriteRun(
            Assertions.java(
                    """
                package com.twin.rewrite;
                class A {
                    private final String strField = "test";
                    private void privateFunc() {
                        System.out.println("privateFunc" + strField);
                    }
                    public final void finalFunc() {
                        System.out.println("finalFunc" + strField);
                    }
                }
            """
            )
    )


    // Assert that private/final methods with instance method access
    // are stay the same.
    @Test
    fun methodWithInstanceMethodAccess() = rewriteRun(
            Assertions.java(
                    """
                package com.twin.rewrite;
                class A {
                    private final String strField = "test";
                    public String getStrField() {
                        return strField;
                    }
                    private void privateFunc() {
                        System.out.println("privateFunc" + getStrField());
                    }
                    public final void finalFunc() {
                        System.out.println("finalFunc" + getStrField());
                    }
                }
            """
            )
    )

    // Assert that private/final methods with parent class instance fields access
    // are stay the same.
    @Test
    fun methodsWithParentClassInstanceFieldAccess() = rewriteRun(
        Assertions.java(
            """
                package com.twin.rewrite;
                class SuperA {
                    protected String parentStr = "parent test";
                }
                class A extends SuperA {
                    private void privateFunc() {
                        System.out.println("privateFunc" + parentStr);
                    }
                    public final void finalFunc() {
                        System.out.println("finalFunc" + parentStr);
                    }
                }
            """
        )
    )

    // Assert that private/final methods with parent class instance fields access
    // are stay the same.
    @Test
    fun methodsWithParentClassInstanceMethodAccess() = rewriteRun(
        Assertions.java(
            """
                package com.twin.rewrite;
                class SuperA {
                    private String parentStr = "parent test";
                    protected String getParentStr() {
                        return parentStr;
                    }
                }
                class A extends SuperA {
                    private void privateFunc() {
                        System.out.println("privateFunc" + getParentStr());
                    }
                    public final void finalFunc() {
                        System.out.println("finalFunc" + getParentStr());
                    }
                }
            """
        )
    )

    // Assert that private/final methods with nonstatic inner class creation
    // are stay the same.
    @Test
    fun methodWithNonStaticInnerClassCreation() = rewriteRun(
        Assertions.java(
            """
                package com.twin.rewrite;
                class A {
                    private void privateFunc() {
                        NonStaticInnerA innerA = new NonStaticInnerA();
                    }
                    private class NonStaticInnerA {
                    }
                }
            """
        )
    )

    // Assert that private/final methods with nonstatic parent inner class creation
    // are stay the same.
    @Test
    fun methodWithNonStaticParentInnerClassCreation() = rewriteRun(
        Assertions.java(
            """
                package com.twin.rewrite;
                class SuperA {
                    class NonStaticInnerSuperA {
                    }
                }
                
                class A extends SuperA {
                    private void privateFunc() {
                        NonStaticInnerSuperA innerSuperA = new NonStaticInnerSuperA();
                    }
                }
            """
        )
    )

    // Assert that private/final methods with static inner class creation
    // are changed to static by recipe.
    @Test
    fun methodWithStaticInnerClassCreation() = rewriteRun(
        Assertions.java(
            """
                package com.twin.rewrite;
                class A {
                    private void privateFunc() {
                        StaticInnerA innerA = new StaticInnerA();
                    }
                    private static class StaticInnerA {
                    }
                }
            """,
            """
                package com.twin.rewrite;
                class A {
                    private static void privateFunc() {
                        StaticInnerA innerA = new StaticInnerA();
                    }
                    private static class StaticInnerA {
                    }
                }
            """
        )
    )

    // Assert that private/final methods with static parent inner class creation
    // are changed to static by recipe.
    @Test
    fun methodWithStaticParentInnerClassCreation() = rewriteRun(
        Assertions.java(
            """
                package com.twin.rewrite;
                class SuperA {
                    static class StaticInnerSuperA {
                    }
                }
                
                class A extends SuperA {
                    private void privateFunc() {
                        StaticInnerSuperA innerSuperA = new StaticInnerSuperA();
                    }
                }
            """,
            """
                package com.twin.rewrite;
                class SuperA {
                    static class StaticInnerSuperA {
                    }
                }
                
                class A extends SuperA {
                    private static void privateFunc() {
                        StaticInnerSuperA innerSuperA = new StaticInnerSuperA();
                    }
                }
            """,
        )
    )

    // Assert that serializable methods of class with Serializable implementation
    // are stay the same.
    @Test
    fun serializableMethodsWithSerializableImplementation() = rewriteRun(
        Assertions.java(
            """
                package com.twin.rewrite;
                import java.io.Serializable;
                import java.io.ObjectOutputStream;
                class A implements Serializable {
                    private void writeObject(ObjectOutputStream out) {
                    }
                    private void readObject(java.io.ObjectInputStream in) {
                    }
                    private void readObjectNoData() {
                    }
                }
            """
        )
    )

    // Assert that serializable methods of class without Serializable implementation
    // are converted to static by the recipe.
    @Test
    fun serializableMethodsWithoutSerializableImplementation() = rewriteRun(
        Assertions.java(
            """
                package com.twin.rewrite;
                import java.io.ObjectOutputStream;
                class A {
                    private void writeObject(ObjectOutputStream out) {
                    }
                    private void readObject(java.io.ObjectInputStream in) {
                    }
                    private void readObjectNoData() {
                    }
                }
            """,
            """
                package com.twin.rewrite;
                import java.io.ObjectOutputStream;
                class A {
                    private static void writeObject(ObjectOutputStream out) {
                    }
                    private static void readObject(java.io.ObjectInputStream in) {
                    }
                    private static void readObjectNoData() {
                    }
                }
            """,

        )
    )

    // Assert that private/final methods with nonstatic parent inner class creation
    // are stay the same.
    @Test
    fun methodWithLambdaWithInstanceAccess() = rewriteRun(
        Assertions.java(
            """
                package com.twin.rewrite;
                
                interface Greeting {
                    String sayHello();
                }
                
                class A {
                    private String name = "A";
                    private void privateFunc() {
                        Greeting greeting = () -> "Hi " + name; 
                    }
                }
            """
        )
    )

    // Assert that private/final methods, which creates anonymous class with instance field access,
    // are stay the same.
    @Test
    fun methodWithAnonymousClassWithFieldAccess() = rewriteRun(
            Assertions.java(
                """
                package com.twin.rewrite;
                interface Greeting {
                    String sayHello();
                }
                class A {
                    private String str = "test";
                    private void privateFunc() {
                        Greeting greeting = new Greeting() {
                            @Override 
                            public String sayHello() {
                                return str;
                            }
                        };
                    }
                }
            """
            )
    )

    // Assert that private/final methods, which creates anonymous class with instance method access,
    // are stay the same.
    @Test
    fun methodWithAnonymousClassWithMethodAccess() = rewriteRun(
        Assertions.java(
            """
                package com.twin.rewrite;
                interface Greeting {
                    String sayHello();
                }
                class A {
                    private String str = "test";
                    private String getStr() {
                        return str;
                    }
                    private void privateFunc() {
                        Greeting greeting = new Greeting() {
                            @Override 
                            public String sayHello() {
                                return getStr();
                            }
                        };
                    }
                }
            """
        )
    )

    // Assert that private/final methods, which creates anonymous class with instance method access,
    // are stay the same.
    @Test
    fun methodWithAnonymousClassWithNoAccess() = rewriteRun(
        Assertions.java(
            """
                package com.twin.rewrite;
                interface Greeting {
                    String sayHello();
                }
                class A {
                    private void privateFunc() {
                        Greeting greeting = new Greeting() {
                            @Override 
                            public String sayHello() {
                                return "Hello there.";
                            }
                        };
                    }
                }
            """,
            """
                package com.twin.rewrite;
                interface Greeting {
                    String sayHello();
                }
                class A {
                    private static void privateFunc() {
                        Greeting greeting = new Greeting() {
                            @Override 
                            public String sayHello() {
                                return "Hello there.";
                            }
                        };
                    }
                }
            """
        )
    )
}