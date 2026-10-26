/* Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;
final class Refl {
 static int getInt(Object f,Object h,int v){return v;} static boolean getBool(Object f,Object h,boolean v){return v;}
 static float getFloat(Object f,Object h,float v){return v;}
 static Class<?> cls(String n,ClassLoader c){return Object.class;} static Object method(Class<?> c,String n,Class<?>... args){return n;}
}
