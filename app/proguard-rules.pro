# zhusheng does not use obfuscation/minification. The rules file is kept so the
# build configuration mirrors a standard Android project layout.

# Keep certificate related classes intact (reflection / framework use).
-keep class cc.wuersan008.zhusheng.** { *; }
