package dev.nonlog.colorosnative.xposed

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

object Reflect {
    @JvmStatic
    fun findClass(name: String, loader: ClassLoader?): Class<*> =
        Class.forName(name, false, loader ?: ClassLoader.getSystemClassLoader())

    @JvmStatic
    fun findClassIfExists(name: String, loader: ClassLoader?): Class<*>? =
        runCatching { findClass(name, loader) }.getOrNull()

    @JvmStatic
    fun findField(clazz: Class<*>, name: String): Field {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                return c.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        throw NoSuchFieldException("${clazz.name}#$name")
    }

    @JvmStatic
    fun getObjectField(obj: Any, name: String): Any? = findField(obj.javaClass, name).get(obj)

    @JvmStatic
    fun setObjectField(obj: Any, name: String, value: Any?) {
        findField(obj.javaClass, name).set(obj, value)
    }

    @JvmStatic
    fun getIntField(obj: Any, name: String): Int = findField(obj.javaClass, name).getInt(obj)

    @JvmStatic
    fun setIntField(obj: Any, name: String, value: Int) {
        findField(obj.javaClass, name).setInt(obj, value)
    }

    @JvmStatic
    fun findMethodExact(clazz: Class<*>, name: String, vararg params: Class<*>): Method {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, *params).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                c = c.superclass
            }
        }
        throw NoSuchMethodException("${clazz.name}#$name(${params.joinToString { it.name }})")
    }

    @JvmStatic
    fun callMethod(obj: Any, name: String, vararg args: Any?): Any? {
        val m = resolveBestMethod(obj.javaClass, name, args)
        return m.invoke(obj, *args)
    }

    @JvmStatic
    fun callStaticMethod(clazz: Class<*>, name: String, vararg args: Any?): Any? {
        val m = resolveBestMethod(clazz, name, args)
        return m.invoke(null, *args)
    }

    @JvmStatic
    fun newInstance(clazz: Class<*>, vararg args: Any?): Any {
        val ctor = resolveBestConstructor(clazz, args)
        return ctor.newInstance(*args)
    }

    private fun resolveBestMethod(clazz: Class<*>, name: String, args: Array<out Any?>): Method {
        var c: Class<*>? = clazz
        while (c != null) {
            c.declaredMethods.firstOrNull { m ->
                m.name == name && m.parameterCount == args.size && paramsMatch(m.parameterTypes, args)
            }?.let { return it.apply { isAccessible = true } }
            c = c.superclass
        }
        throw NoSuchMethodException("${clazz.name}#$name(args=${args.size})")
    }

    private fun resolveBestConstructor(clazz: Class<*>, args: Array<out Any?>): Constructor<*> {
        clazz.declaredConstructors.firstOrNull { ctor ->
            ctor.parameterCount == args.size && paramsMatch(ctor.parameterTypes, args)
        }?.let { return it.apply { isAccessible = true } }
        throw NoSuchMethodException("${clazz.name}#<init>(args=${args.size})")
    }

    private fun paramsMatch(types: Array<Class<*>>, args: Array<out Any?>): Boolean {
        for (i in types.indices) {
            val arg = args[i]
            val t = types[i]
            if (arg == null) {
                if (t.isPrimitive) return false
            } else if (!box(t).isInstance(arg)) {
                return false
            }
        }
        return true
    }

    private fun box(c: Class<*>): Class<*> = when (c) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        else -> c
    }
}
