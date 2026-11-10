package net.encode.wurmesp.util;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.encode.wurmesp.feature.hook.Hook;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

public class ReUtils {
    /** Fields already resolved, by class then name: ReflectionUtil.getField walks every declared
     *  field of the class and its superclasses on each call, far too much for a per-frame read. */
    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Field>> FIELDS = new ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Field>>();

    /** The named field of the class (or a superclass), made accessible; looked up once, then reused. */
    public static Field field(Class<?> cls, String fieldname) throws NoSuchFieldException {
        ConcurrentHashMap<String, Field> byName = FIELDS.get(cls);
        if (byName == null) {
            byName = new ConcurrentHashMap<String, Field>();
            ConcurrentHashMap<String, Field> raced = FIELDS.putIfAbsent(cls, byName);
            if (raced != null) {
                byName = raced;
            }
        }
        Field field = byName.get(fieldname);
        if (field == null) {
            field = ReflectionUtil.getField(cls, fieldname);
            field.setAccessible(true);
            byName.put(fieldname, field);
        }
        return field;
    }

    @SuppressWarnings("unchecked")
	public static <T> T getField(Object proxy, String fieldname) {
        Class<?> cls = proxy.getClass();
        Object returnedObject = null;
        try {
            returnedObject = field(cls, fieldname).get(proxy);
        }
        catch (ClassCastException | IllegalAccessException | IllegalArgumentException | NoSuchFieldException ex) {
            Logger.getLogger(Hook.class.getName()).log(Level.SEVERE, null, ex);
        }
        return (T)returnedObject;
    }
}
