package go;

import android.app.Application;
import java.util.logging.Logger;

public class LoadJNI
{
    private static Logger log;
    public static final Object ctx;

    static {
        LoadJNI.log = Logger.getLogger("GoLoadJNI");
        System.loadLibrary("gojni");
        Object applicationContext = null;
        try {
            applicationContext = ((Application)Class.forName("android.app.AppGlobals").getMethod("getInitialApplication", (Class<?>[])new Class[0]).invoke(null, new Object[0])).getApplicationContext();
        }
        catch (Exception obj) {
            LoadJNI.log.warning("Global context not found: " + obj);
        }
        finally {
            ctx = applicationContext;
        }
    }
}