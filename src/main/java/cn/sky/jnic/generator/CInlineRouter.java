package cn.sky.jnic.generator;

import org.objectweb.asm.Type;

public class CInlineRouter {

    static String getInlineImplementation(String ownerClass, String methodName, String methodDesc,
                                          String methodHash, Type returnType) {
        StringBuilder code = new StringBuilder();

        if ("java/lang/String".equals(ownerClass)) {
            if ("equals".equals(methodName) && "(Ljava/lang/Object;)Z".equals(methodDesc)) {
                code.append("    jobject str_other_").append(methodHash).append(" = stack[--sp].l;\n");
                code.append("    jobject str_this_").append(methodHash).append(" = stack[--sp].l;\n");
                code.append("    if (str_this_").append(methodHash).append(" == NULL) { throw_npe(env, \"String.equals on null\"); }\n");
                code.append("    else { stack[sp++].i = inline_string_equals(env, str_this_").append(methodHash)
                        .append(", str_other_").append(methodHash).append("); }\n");
                return code.toString();
            }
            if ("length".equals(methodName) && "()I".equals(methodDesc)) {
                code.append("    jstring str_").append(methodHash).append(" = (jstring)stack[--sp].l;\n");
                code.append("    stack[sp++].i = inline_string_length(env, str_").append(methodHash).append(");\n");
                return code.toString();
            }
            if ("hashCode".equals(methodName) && "()I".equals(methodDesc)) {
                code.append("    jstring str_").append(methodHash).append(" = (jstring)stack[--sp].l;\n");
                code.append("    stack[sp++].i = inline_string_hashCode(env, str_").append(methodHash).append(");\n");
                return code.toString();
            }
            if ("charAt".equals(methodName) && "(I)C".equals(methodDesc)) {
                code.append("    jint idx_").append(methodHash).append(" = stack[--sp].i;\n");
                code.append("    jstring str_").append(methodHash).append(" = (jstring)stack[--sp].l;\n");
                code.append("    stack[sp++].i = inline_string_charAt(env, str_").append(methodHash)
                        .append(", idx_").append(methodHash).append(");\n");
                return code.toString();
            }
        }

        if ("java/lang/Object".equals(ownerClass)) {
            if ("getClass".equals(methodName) && "()Ljava/lang/Class;".equals(methodDesc)) {
                code.append("    jobject obj_").append(methodHash).append(" = stack[--sp].l;\n");
                code.append("    stack[sp++].l = inline_object_getClass(env, obj_").append(methodHash).append(");\n");
                return code.toString();
            }
        }

        if ("java/lang/Math".equals(ownerClass)) {
            if ("abs".equals(methodName)) {
                if ("(D)D".equals(methodDesc)) { code.append("    stack[sp-1].d = inline_math_abs_d(stack[sp-1].d);\n"); return code.toString(); }
                if ("(F)F".equals(methodDesc)) { code.append("    stack[sp-1].f = inline_math_abs_f(stack[sp-1].f);\n"); return code.toString(); }
                if ("(I)I".equals(methodDesc)) { code.append("    stack[sp-1].i = inline_math_abs_i(stack[sp-1].i);\n"); return code.toString(); }
                if ("(J)J".equals(methodDesc)) { code.append("    stack[sp-1].j = inline_math_abs_l(stack[sp-1].j);\n"); return code.toString(); }
            }
            if ("max".equals(methodName)) {
                if ("(DD)D".equals(methodDesc)) { code.append("    sp--; stack[sp-1].d = inline_math_max_d(stack[sp-1].d, stack[sp].d);\n"); return code.toString(); }
                if ("(II)I".equals(methodDesc)) { code.append("    sp--; stack[sp-1].i = inline_math_max_i(stack[sp-1].i, stack[sp].i);\n"); return code.toString(); }
            }
            if ("min".equals(methodName)) {
                if ("(DD)D".equals(methodDesc)) { code.append("    sp--; stack[sp-1].d = inline_math_min_d(stack[sp-1].d, stack[sp].d);\n"); return code.toString(); }
                if ("(II)I".equals(methodDesc)) { code.append("    sp--; stack[sp-1].i = inline_math_min_i(stack[sp-1].i, stack[sp].i);\n"); return code.toString(); }
            }
            if ("sin".equals(methodName)   && "(D)D".equals(methodDesc)) { code.append("    stack[sp-1].d = inline_math_sin(stack[sp-1].d);\n");   return code.toString(); }
            if ("cos".equals(methodName)   && "(D)D".equals(methodDesc)) { code.append("    stack[sp-1].d = inline_math_cos(stack[sp-1].d);\n");   return code.toString(); }
            if ("tan".equals(methodName)   && "(D)D".equals(methodDesc)) { code.append("    stack[sp-1].d = inline_math_tan(stack[sp-1].d);\n");   return code.toString(); }
            if ("sqrt".equals(methodName)  && "(D)D".equals(methodDesc)) { code.append("    stack[sp-1].d = inline_math_sqrt(stack[sp-1].d);\n");  return code.toString(); }
            if ("log".equals(methodName)   && "(D)D".equals(methodDesc)) { code.append("    stack[sp-1].d = inline_math_log(stack[sp-1].d);\n");   return code.toString(); }
            if ("exp".equals(methodName)   && "(D)D".equals(methodDesc)) { code.append("    stack[sp-1].d = inline_math_exp(stack[sp-1].d);\n");   return code.toString(); }
            if ("floor".equals(methodName) && "(D)D".equals(methodDesc)) { code.append("    stack[sp-1].d = inline_math_floor(stack[sp-1].d);\n"); return code.toString(); }
            if ("ceil".equals(methodName)  && "(D)D".equals(methodDesc)) { code.append("    stack[sp-1].d = inline_math_ceil(stack[sp-1].d);\n");  return code.toString(); }
            if ("round".equals(methodName) && "(D)J".equals(methodDesc)) { code.append("    stack[sp-1].j = (jlong)inline_math_round(stack[sp-1].d);\n"); return code.toString(); }
            if ("pow".equals(methodName)   && "(DD)D".equals(methodDesc)) { code.append("    sp--; stack[sp-1].d = inline_math_pow(stack[sp-1].d, stack[sp].d);\n"); return code.toString(); }
        }

        if ("java/lang/System".equals(ownerClass)) {
            if ("arraycopy".equals(methodName) && "(Ljava/lang/Object;ILjava/lang/Object;II)V".equals(methodDesc)) {
                code.append("    jint len_").append(methodHash).append(" = stack[--sp].i;\n");
                code.append("    jint destPos_").append(methodHash).append(" = stack[--sp].i;\n");
                code.append("    jobject dest_").append(methodHash).append(" = stack[--sp].l;\n");
                code.append("    jint srcPos_").append(methodHash).append(" = stack[--sp].i;\n");
                code.append("    jobject src_").append(methodHash).append(" = stack[--sp].l;\n");
                code.append("    inline_system_arraycopy(env, src_").append(methodHash)
                        .append(", srcPos_").append(methodHash)
                        .append(", dest_").append(methodHash)
                        .append(", destPos_").append(methodHash)
                        .append(", len_").append(methodHash).append(");\n");
                return code.toString();
            }
        }

        return null;
    }
}
