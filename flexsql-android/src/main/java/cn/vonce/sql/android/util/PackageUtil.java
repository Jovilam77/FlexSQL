package cn.vonce.sql.android.util;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;

import dalvik.system.DexFile;

public class PackageUtil {

    private static final Logger logger = Logger.getLogger(PackageUtil.class.getName());

    public static List<String> getClasses(Context mContext, String packageName) {
        List<String> classes = new ArrayList<>();
        try {
            String packageCodePath = mContext.getPackageCodePath();
            DexFile df = new DexFile(packageCodePath);
            String regExp = "^" + packageName + ".\\w+$";
            Enumeration<String> enumeration = df.entries();
            while (enumeration.hasMoreElements()) {
                String className = enumeration.nextElement();
                if (className.matches(regExp)) {
                    classes.add(className);
                }
            }
        } catch (IOException e) {
            logger.warning("Failed to get classes from package '" + packageName + "': " + e.getMessage());
        }
        return classes;
    }

}
