# The module entry point is named only from META-INF/xposed/java_init.list and is
# instantiated reflectively by the framework, so it must survive shrinking.
-keep,allowoptimization class io.github.fengwenhua.colorduo.ColorDuoModule { *; }

# The framework resolves hook targets and reads fields on the ROM's classes by
# name at runtime; nothing here is reachable from the entry point statically.
# The observer is reached directly only in debug/observe builds. Do not retain
# it through this broad compatibility rule after the normal entry branch folds.
-keep class !io.github.fengwenhua.colorduo.ColorDuoModule,!io.github.fengwenhua.colorduo.ColorOsObserver,!io.github.fengwenhua.colorduo.ColorOsObserver$*,io.github.fengwenhua.colorduo.** { *; }

# The libxposed API is compile-only and supplied by the framework at runtime.
-dontwarn io.github.libxposed.**
