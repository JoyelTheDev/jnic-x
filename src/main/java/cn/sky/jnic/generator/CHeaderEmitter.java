package cn.sky.jnic.generator;

public class CHeaderEmitter {

    static String emitHeaders() {
        StringBuilder sb = new StringBuilder();
        sb.append("#include \"jni.h\"\n");
        sb.append("#include <stdint.h>\n");
        sb.append("#include <stdlib.h>\n");
        sb.append("#include <string.h>\n");
        sb.append("#include <stdio.h>\n");
        sb.append("#include <math.h>\n");
        sb.append("#include <stdarg.h>\n\n");

        sb.append("typedef union {\n");
        sb.append("    jint i;\n");
        sb.append("    jlong j;\n");
        sb.append("    jfloat f;\n");
        sb.append("    jdouble d;\n");
        sb.append("    jobject l;\n");
        sb.append("} StackValue;\n\n");

        sb.append(emitHelperFunctions());
        return sb.toString();
    }

    private static String emitHelperFunctions() {
        return """
                #ifndef JNIC_DEBUG
                #define JNIC_DEBUG 0
                #endif
                #if JNIC_DEBUG
                void log_debug(const char* format, ...) {
                    FILE *f = fopen("native_debug.log", "a");
                    if (f) {
                        va_list args;
                        va_start(args, format);
                        vfprintf(f, format, args);
                        va_end(args);
                        fclose(f);
                    }
                }
                #else
                void log_debug(const char* format, ...) { (void)format; }
                #endif

                static JavaVM* g_jvm = NULL;
                static jclass g_cls_String = NULL;
                static jclass g_cls_StringBuilder = NULL;
                static jclass g_cls_Object = NULL;
                static jclass g_cls_Class = NULL;
                static jclass g_cls_System = NULL;
                static jclass g_cls_Math = NULL;
                static jclass g_cls_Arrays = NULL;
                static jclass g_cls_NullPointerException = NULL;
                static jclass g_cls_ArrayIndexOutOfBoundsException = NULL;
                static jclass g_cls_ArithmeticException = NULL;
                static jclass g_cls_ClassCastException = NULL;

                static jmethodID g_mid_String_length = NULL;
                static jmethodID g_mid_String_hashCode = NULL;
                static jmethodID g_mid_String_charAt = NULL;
                static jmethodID g_mid_StringBuilder_init = NULL;
                static jmethodID g_mid_StringBuilder_append_String = NULL;
                static jmethodID g_mid_StringBuilder_append_int = NULL;
                static jmethodID g_mid_StringBuilder_append_long = NULL;
                static jmethodID g_mid_StringBuilder_append_double = NULL;
                static jmethodID g_mid_StringBuilder_append_Object = NULL;
                static jmethodID g_mid_StringBuilder_toString = NULL;
                static jmethodID g_mid_Object_getClass = NULL;
                static jmethodID g_mid_Object_hashCode = NULL;
                static jmethodID g_mid_Object_toString = NULL;
                static jmethodID g_mid_Class_getName = NULL;
                static jmethodID g_mid_System_arraycopy = NULL;

                static int g_cache_initialized = 0;
                static int g_cache_initializing = 0;

                jclass get_or_cache_class(JNIEnv* env, jclass* cache, const char* name) {
                    jclass cls = *cache;
                    if (cls != NULL) return cls;
                    jclass tmp = (*env)->FindClass(env, name);
                    if (tmp == NULL) {
                        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                        return NULL;
                    }
                    cls = (*env)->NewGlobalRef(env, tmp);
                    (*env)->DeleteLocalRef(env, tmp);
                    *cache = cls;
                    return cls;
                }

                void init_global_cache(JNIEnv* env) {
                    if (env == NULL) return;
                    if (g_cache_initialized) return;
                    if (g_cache_initializing) return;
                    g_cache_initializing = 1;

                    jclass tmp;

                    #define CACHE_CLASS(var, name) \\
                        if ((var) == NULL) { \\
                            tmp = (*env)->FindClass(env, name); \\
                            if (tmp) { var = (*env)->NewGlobalRef(env, tmp); (*env)->DeleteLocalRef(env, tmp); } \\
                            else { if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env); } \\
                        }

                    CACHE_CLASS(g_cls_String, "java/lang/String");
                    CACHE_CLASS(g_cls_StringBuilder, "java/lang/StringBuilder");
                    CACHE_CLASS(g_cls_Object, "java/lang/Object");
                    CACHE_CLASS(g_cls_Class, "java/lang/Class");
                    CACHE_CLASS(g_cls_System, "java/lang/System");
                    CACHE_CLASS(g_cls_Math, "java/lang/Math");
                    CACHE_CLASS(g_cls_Arrays, "java/util/Arrays");
                    CACHE_CLASS(g_cls_NullPointerException, "java/lang/NullPointerException");
                    CACHE_CLASS(g_cls_ArrayIndexOutOfBoundsException, "java/lang/ArrayIndexOutOfBoundsException");
                    CACHE_CLASS(g_cls_ArithmeticException, "java/lang/ArithmeticException");
                    CACHE_CLASS(g_cls_ClassCastException, "java/lang/ClassCastException");

                    #undef CACHE_CLASS

                    if (g_cls_String) {
                        if (g_mid_String_length == NULL) g_mid_String_length = (*env)->GetMethodID(env, g_cls_String, "length", "()I");
                        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                        if (g_mid_String_hashCode == NULL) g_mid_String_hashCode = (*env)->GetMethodID(env, g_cls_String, "hashCode", "()I");
                        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                        if (g_mid_String_charAt == NULL) g_mid_String_charAt = (*env)->GetMethodID(env, g_cls_String, "charAt", "(I)C");
                        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                    }
                    if (g_cls_StringBuilder) {
                        if (g_mid_StringBuilder_init == NULL) g_mid_StringBuilder_init = (*env)->GetMethodID(env, g_cls_StringBuilder, "<init>", "()V");
                        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                        if (g_mid_StringBuilder_append_String == NULL) g_mid_StringBuilder_append_String = (*env)->GetMethodID(env, g_cls_StringBuilder, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
                        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                        if (g_mid_StringBuilder_append_int == NULL) g_mid_StringBuilder_append_int = (*env)->GetMethodID(env, g_cls_StringBuilder, "append", "(I)Ljava/lang/StringBuilder;");
                        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                        if (g_mid_StringBuilder_append_long == NULL) g_mid_StringBuilder_append_long = (*env)->GetMethodID(env, g_cls_StringBuilder, "append", "(J)Ljava/lang/StringBuilder;");
                        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                        if (g_mid_StringBuilder_append_double == NULL) g_mid_StringBuilder_append_double = (*env)->GetMethodID(env, g_cls_StringBuilder, "append", "(D)Ljava/lang/StringBuilder;");
                        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                        if (g_mid_StringBuilder_append_Object == NULL) g_mid_StringBuilder_append_Object = (*env)->GetMethodID(env, g_cls_StringBuilder, "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;");
                        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                        if (g_mid_StringBuilder_toString == NULL) g_mid_StringBuilder_toString = (*env)->GetMethodID(env, g_cls_StringBuilder, "toString", "()Ljava/lang/String;");
                        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                    }
                    if (g_cls_Object) {
                        if (g_mid_Object_getClass == NULL) g_mid_Object_getClass = (*env)->GetMethodID(env, g_cls_Object, "getClass", "()Ljava/lang/Class;");
                        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                        if (g_mid_Object_hashCode == NULL) g_mid_Object_hashCode = (*env)->GetMethodID(env, g_cls_Object, "hashCode", "()I");
                        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                        if (g_mid_Object_toString == NULL) g_mid_Object_toString = (*env)->GetMethodID(env, g_cls_Object, "toString", "()Ljava/lang/String;");
                        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                    }
                    if (g_cls_Class) {
                        if (g_mid_Class_getName == NULL) g_mid_Class_getName = (*env)->GetMethodID(env, g_cls_Class, "getName", "()Ljava/lang/String;");
                        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                    }
                    if (g_cls_System) {
                        if (g_mid_System_arraycopy == NULL) g_mid_System_arraycopy = (*env)->GetStaticMethodID(env, g_cls_System, "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V");
                        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                    }

                    g_cache_initialized = 1;
                    g_cache_initializing = 0;
                }

                char* decrypt_string_len(const unsigned char* encrypted, int len, int key) {
                    char* decrypted = (char*)malloc((size_t)len + 1);
                    if (decrypted == NULL) return NULL;
                    for (int i = 0; i < len; i++) decrypted[i] = (char)(encrypted[i] ^ key);
                    decrypted[len] = 0;
                    return decrypted;
                }

                void throw_npe(JNIEnv* env, const char* msg);
                void throw_aioobe(JNIEnv* env, const char* msg);
                void throw_arith(JNIEnv* env, const char* msg);

                jboolean inline_string_equals(JNIEnv *env, jobject s1, jobject s2) {
                    init_global_cache(env);
                    if (s1 == s2) return JNI_TRUE;
                    if (s1 == NULL) return JNI_FALSE;
                    if (s2 == NULL) return JNI_FALSE;
                    jclass stringCls = g_cls_String;
                    jclass localStringCls = NULL;
                    if (stringCls == NULL) {
                        localStringCls = (*env)->FindClass(env, "java/lang/String");
                        if (localStringCls == NULL) {
                            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
                            return JNI_FALSE;
                        }
                        stringCls = localStringCls;
                    }
                    if (!(*env)->IsInstanceOf(env, s2, stringCls)) {
                        if (localStringCls != NULL) (*env)->DeleteLocalRef(env, localStringCls);
                        return JNI_FALSE;
                    }
                    if (localStringCls != NULL) (*env)->DeleteLocalRef(env, localStringCls);
                    jint len1 = (*env)->GetStringLength(env, (jstring)s1);
                    jint len2 = (*env)->GetStringLength(env, (jstring)s2);
                    if (len1 != len2) return JNI_FALSE;
                    const jchar* c1 = (*env)->GetStringChars(env, (jstring)s1, NULL);
                    const jchar* c2 = (*env)->GetStringChars(env, (jstring)s2, NULL);
                    jboolean eq = JNI_TRUE;
                    for (int i = 0; i < len1; i++) {
                        if (c1[i] != c2[i]) { eq = JNI_FALSE; break; }
                    }
                    (*env)->ReleaseStringChars(env, (jstring)s1, c1);
                    (*env)->ReleaseStringChars(env, (jstring)s2, c2);
                    return eq;
                }

                jint inline_string_length(JNIEnv* env, jstring s) {
                    return s ? (*env)->GetStringLength(env, s) : 0;
                }

                jint inline_string_hashCode(JNIEnv* env, jstring s) {
                    if (s == NULL) return 0;
                    jint len = (*env)->GetStringLength(env, s);
                    if (len == 0) return 0;
                    const jchar* chars = (*env)->GetStringChars(env, s, NULL);
                    jint h = 0;
                    for (int i = 0; i < len; i++) h = 31 * h + chars[i];
                    (*env)->ReleaseStringChars(env, s, chars);
                    return h;
                }

                jchar inline_string_charAt(JNIEnv* env, jstring s, jint index) {
                    if (s == NULL) return 0;
                    jint len = (*env)->GetStringLength(env, s);
                    if (index < 0 || index >= len) { throw_aioobe(env, "String index out of range"); return 0; }
                    const jchar* chars = (*env)->GetStringChars(env, s, NULL);
                    jchar c = chars[index];
                    (*env)->ReleaseStringChars(env, s, chars);
                    return c;
                }

                jclass inline_object_getClass(JNIEnv* env, jobject obj) {
                    return obj ? (*env)->GetObjectClass(env, obj) : NULL;
                }

                void inline_system_arraycopy(JNIEnv* env, jobject src, jint srcPos, jobject dest, jint destPos, jint length) {
                    if (src == NULL || dest == NULL) { throw_npe(env, "arraycopy: null array"); return; }
                    init_global_cache(env);
                    if (g_cls_System != NULL && g_mid_System_arraycopy != NULL) {
                        (*env)->CallStaticVoidMethod(env, g_cls_System, g_mid_System_arraycopy, src, srcPos, dest, destPos, length);
                        return;
                    }
                    jclass sysCls = (*env)->FindClass(env, "java/lang/System");
                    if (sysCls == NULL) { if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env); return; }
                    jmethodID mid = (*env)->GetStaticMethodID(env, sysCls, "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V");
                    if (mid == NULL) { if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env); (*env)->DeleteLocalRef(env, sysCls); return; }
                    (*env)->CallStaticVoidMethod(env, sysCls, mid, src, srcPos, dest, destPos, length);
                    (*env)->DeleteLocalRef(env, sysCls);
                }

                jdouble inline_math_abs_d(jdouble a) { return fabs(a); }
                jfloat  inline_math_abs_f(jfloat a)  { return fabsf(a); }
                jint    inline_math_abs_i(jint a)     { return a < 0 ? -a : a; }
                jlong   inline_math_abs_l(jlong a)    { return a < 0 ? -a : a; }
                jdouble inline_math_max_d(jdouble a, jdouble b) { return a > b ? a : b; }
                jdouble inline_math_min_d(jdouble a, jdouble b) { return a < b ? a : b; }
                jint    inline_math_max_i(jint a, jint b)       { return a > b ? a : b; }
                jint    inline_math_min_i(jint a, jint b)       { return a < b ? a : b; }
                jdouble inline_math_sin(jdouble a)   { return sin(a); }
                jdouble inline_math_cos(jdouble a)   { return cos(a); }
                jdouble inline_math_tan(jdouble a)   { return tan(a); }
                jdouble inline_math_sqrt(jdouble a)  { return sqrt(a); }
                jdouble inline_math_pow(jdouble a, jdouble b) { return pow(a, b); }
                jdouble inline_math_log(jdouble a)   { return log(a); }
                jdouble inline_math_exp(jdouble a)   { return exp(a); }
                jdouble inline_math_floor(jdouble a) { return floor(a); }
                jdouble inline_math_ceil(jdouble a)  { return ceil(a); }
                jdouble inline_math_round(jdouble a) { return round(a); }

                void throw_npe(JNIEnv* env, const char* msg) {
                    init_global_cache(env);
                    jclass cls = get_or_cache_class(env, &g_cls_NullPointerException, "java/lang/NullPointerException");
                    if (cls) (*env)->ThrowNew(env, cls, msg);
                }
                void throw_aioobe(JNIEnv* env, const char* msg) {
                    init_global_cache(env);
                    jclass cls = get_or_cache_class(env, &g_cls_ArrayIndexOutOfBoundsException, "java/lang/ArrayIndexOutOfBoundsException");
                    if (cls) (*env)->ThrowNew(env, cls, msg);
                }
                void throw_arith(JNIEnv* env, const char* msg) {
                    init_global_cache(env);
                    jclass cls = get_or_cache_class(env, &g_cls_ArithmeticException, "java/lang/ArithmeticException");
                    if (cls) (*env)->ThrowNew(env, cls, msg);
                }

                """;
    }
}
