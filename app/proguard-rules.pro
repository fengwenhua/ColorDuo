# The module entry point is named only from META-INF/xposed/java_init.list and is
# instantiated reflectively by the framework, so it must survive shrinking.
-keep class io.github.fengwenhua.colorduo.ColorDuoModule { *; }

# The framework resolves hook targets and reads fields on the ROM's classes by
# name at runtime; nothing here is reachable from the entry point statically.
-keep class io.github.fengwenhua.colorduo.** { *; }

# The libxposed API is compile-only and supplied by the framework at runtime.
-dontwarn io.github.libxposed.**
