package com.mo.fkLTY.hook

import android.content.Intent

/**
 * metis `com.oplus.textintent.distribution.bean.TextIntent` 的反射封装。
 *
 * 该 bean 是 Kotlin 属性类,公开 getter/setter(getPackageName/setPackageName 等),
 * 优先走公开方法(无需 setAccessible);方法缺失时回落到私有字段(getDeclaredField 沿继承链查找),
 * 与旧模块纯字段访问相比多一层容错。字段/方法在 17.31.438 已逐一核实(02 号文档 §2.1)。
 */
class ReflectedTextIntent(val instance: Any) {

    private val accessors = accessorsFor(instance.javaClass)

    val packageName: String?
        get() = accessors.getPackageName?.invoke(instance) as? String

    fun setPackageName(value: String) {
        accessors.setPackageName?.invoke(instance, value)
            ?: accessors.fieldPackageName?.set(instance, value)
    }

    val intent: Intent?
        get() = accessors.getIntent?.invoke(instance) as? Intent

    val textType: Int?
        get() = (accessors.getTextType?.invoke(instance) as? Number)?.toInt()

    val className: String
        get() = instance.javaClass.name

    private class Accessors(cls: Class<*>) {
        val getPackageName: java.lang.reflect.Method? = method(cls, "getPackageName")
        val setPackageName: java.lang.reflect.Method? = method(cls, "setPackageName", String::class.java)
        val getIntent: java.lang.reflect.Method? = method(cls, "getIntent")
        val getTextType: java.lang.reflect.Method? = method(cls, "getTextType")

        val fieldPackageName: java.lang.reflect.Field? = field(cls, "packageName")

        companion object {
            private fun method(cls: Class<*>, name: String, vararg params: Class<*>): java.lang.reflect.Method? =
                try {
                    cls.getMethod(name, *params)
                } catch (_: Throwable) {
                    null
                }

            private fun field(cls: Class<*>, name: String): java.lang.reflect.Field? {
                var c: Class<*>? = cls
                while (c != null) {
                    try {
                        return c.getDeclaredField(name).apply { isAccessible = true }
                    } catch (_: NoSuchFieldException) {
                        c = c.superclass
                    } catch (_: Throwable) {
                        return null
                    }
                }
                return null
            }
        }
    }

    companion object {
        private val cache = java.util.concurrent.ConcurrentHashMap<Class<*>, Accessors>()

        private fun accessorsFor(cls: Class<*>): Accessors = cache.computeIfAbsent(cls) { Accessors(it) }
    }
}
