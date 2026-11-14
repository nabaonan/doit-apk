# doit DOIT
# 保留行号与泛型信息，便于真机崩溃栈定位
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Room / 协程
-keep class androidx.room.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
