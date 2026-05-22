package com.matrix.env;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MatrixModule implements IXposedHookLoadPackage {

    private static final String TAG = "AppsMatrix";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {

        String pkg = lpparam.packageName;

        XposedBridge.log(TAG + ": handleLoadPackage -> " + pkg);

        if (shouldSkipPackage(pkg)) {

            XposedBridge.log(TAG + ": skipped package -> " + pkg);

            return;
        }

        JSONObject matrix = loadMatrixFor(pkg);

        if (matrix == null) {

            matrix = createDefaultDutchProfile();

            XposedBridge.log(
                    TAG + ": using default NL profile for -> " + pkg
            );

        } else {

            XposedBridge.log(
                    TAG + ": using custom profile for -> " + pkg
            );
        }

        // Telephony
        hookMethod(lpparam, "getSimOperator",
                matrix, "sim_operator");

        hookMethod(lpparam, "getSimOperatorName",
                matrix, "sim_operator_name");

        hookMethod(lpparam, "getSimCountryIso",
                matrix, "sim_country");

        hookMethod(lpparam, "getNetworkOperator",
                matrix, "network_operator");

        hookMethod(lpparam, "getNetworkOperatorName",
                matrix, "network_operator_name");

        hookMethod(lpparam, "getNetworkCountryIso",
                matrix, "network_country");

        // Locale
        hookLocale(lpparam, matrix);

        // Timezone
        hookTimeZone(lpparam, matrix);

        XposedBridge.log(
                TAG + ": hooks installed -> " + pkg
        );
    }

    private boolean shouldSkipPackage(String pkg) {

        if (pkg == null) {
            return true;
        }

        // Core Android/system
        if (
                pkg.equals("android") ||
                pkg.startsWith("com.android") ||
                pkg.startsWith("com.google.android") ||
                pkg.startsWith("com.miui") ||
                pkg.startsWith("com.qualcomm") ||
                pkg.startsWith("com.mediatek") ||
                pkg.startsWith("de.robv.android.xposed")
        ) {
            return true;
        }

        String lower = pkg.toLowerCase();

        // Safaricom
        if (
                lower.contains("safaricom") ||
                lower.contains("mpesa") ||
                lower.contains("m-pesa")
        ) {
            return true;
        }

        // Banking/payment/security
        if (
                lower.contains("bank") ||
                lower.contains("finance") ||
                lower.contains("wallet") ||
                lower.contains("paypal") ||
                lower.contains("gpay") ||
                lower.contains("wise") ||
                lower.contains("revolut") ||
                lower.contains("binance") ||
                lower.contains("crypto") ||
                lower.contains("payment")
        ) {
            return true;
        }

        return false;
    }

    private JSONObject createDefaultDutchProfile() {

        try {

            JSONObject obj = new JSONObject();

            // KPN Netherlands
            obj.put("sim_operator", "20408");
            obj.put("sim_operator_name", "KPN");

            obj.put("sim_country", "nl");

            obj.put("network_operator", "20408");
            obj.put("network_operator_name", "KPN");

            obj.put("network_country", "nl");

            obj.put("locale_language", "en");
            obj.put("locale_country", "NL");

            obj.put("timezone", "Europe/Amsterdam");

            return obj;

        } catch (Throwable t) {

            XposedBridge.log(
                    TAG + ": default profile error -> "
                            + t.getMessage()
            );

            return null;
        }
    }

    private JSONObject loadMatrixFor(String packageName) {

        try {

            java.io.InputStream is =
                    getClass()
                            .getClassLoader()
                            .getResourceAsStream(
                                    "assets/matrix.json"
                            );

            if (is == null) {
                return null;
            }

            java.io.ByteArrayOutputStream baos =
                    new java.io.ByteArrayOutputStream();

            byte[] tmp = new byte[1024];

            int len;

            while ((len = is.read(tmp)) != -1) {

                baos.write(tmp, 0, len);
            }

            is.close();

            JSONObject root =
                    new JSONObject(
                            new String(
                                    baos.toByteArray(),
                                    "UTF-8"
                            )
                    );

            if (root.has(packageName)) {

                return root.getJSONObject(packageName);
            }

        } catch (Throwable t) {

            XposedBridge.log(
                    TAG + ": matrix load error -> "
                            + t.getMessage()
            );
        }

        return null;
    }

    private void hookMethod(
            XC_LoadPackage.LoadPackageParam lpparam,
            final String methodName,
            final JSONObject matrix,
            final String configKey
    ) {

        // No-arg overload
        try {

            XposedHelpers.findAndHookMethod(
                    "android.telephony.TelephonyManager",
                    lpparam.classLoader,
                    methodName,
                    new XC_MethodHook() {

                        @Override
                        protected void afterHookedMethod(
                                MethodHookParam param
                        ) {

                            try {

                                String val =
                                        matrix.getString(configKey);

                                param.setResult(val);

                            } catch (Throwable ignored) {
                            }
                        }
                    }
            );

        } catch (Throwable t) {

            XposedBridge.log(
                    TAG + ": hook fail -> "
                            + methodName
                            + " : "
                            + t.getMessage()
            );
        }

        // subscription overload
        try {

            XposedHelpers.findAndHookMethod(
                    "android.telephony.TelephonyManager",
                    lpparam.classLoader,
                    methodName,
                    int.class,
                    new XC_MethodHook() {

                        @Override
                        protected void afterHookedMethod(
                                MethodHookParam param
                        ) {

                            try {

                                String val =
                                        matrix.getString(configKey);

                                param.setResult(val);

                            } catch (Throwable ignored) {
                            }
                        }
                    }
            );

        } catch (Throwable ignored) {
        }
    }

    private void hookLocale(
            XC_LoadPackage.LoadPackageParam lpparam,
            final JSONObject matrix
    ) {

        try {

            final String lang =
                    matrix.getString("locale_language");

            final String country =
                    matrix.getString("locale_country");

            final Locale spoofed =
                    new Locale(lang, country);

            XposedHelpers.findAndHookMethod(
                    "java.util.Locale",
                    lpparam.classLoader,
                    "getDefault",
                    new XC_MethodHook() {

                        @Override
                        protected void afterHookedMethod(
                                MethodHookParam param
                        ) {

                            param.setResult(spoofed);
                        }
                    }
            );

            XposedBridge.log(TAG + ": locale hooked");

        } catch (Throwable t) {

            XposedBridge.log(
                    TAG + ": locale hook fail -> "
                            + t.getMessage()
            );
        }
    }

    private void hookTimeZone(
            XC_LoadPackage.LoadPackageParam lpparam,
            final JSONObject matrix
    ) {

        try {

            final String tz =
                    matrix.getString("timezone");

            final TimeZone spoofedTz =
                    TimeZone.getTimeZone(tz);

            // java.util.TimeZone
            XposedHelpers.findAndHookMethod(
                    "java.util.TimeZone",
                    lpparam.classLoader,
                    "getDefault",
                    new XC_MethodHook() {

                        @Override
                        protected void afterHookedMethod(
                                MethodHookParam param
                        ) {

                            param.setResult(spoofedTz);
                        }
                    }
            );

            // java.util.Calendar
            XposedHelpers.findAndHookMethod(
                    "java.util.Calendar",
                    lpparam.classLoader,
                    "getInstance",
                    new XC_MethodHook() {

                        @Override
                        protected void afterHookedMethod(
                                MethodHookParam param
                        ) {

                            try {

                                Calendar cal =
                                        (Calendar) param.getResult();

                                if (cal != null) {

                                    cal.setTimeZone(spoofedTz);

                                    param.setResult(cal);
                                }

                            } catch (Throwable ignored) {
                            }
                        }
                    }
            );

            // ICU timezone for Chromium/WebView
            try {

                XposedHelpers.findAndHookMethod(
                        "android.icu.util.TimeZone",
                        lpparam.classLoader,
                        "getDefault",
                        new XC_MethodHook() {

                            @Override
                            protected void afterHookedMethod(
                                    MethodHookParam param
                            ) {

                                try {

                                    Object icuTz =
                                            XposedHelpers.callStaticMethod(
                                                    XposedHelpers.findClass(
                                                            "android.icu.util.TimeZone",
                                                            lpparam.classLoader
                                                    ),
                                                    "getTimeZone",
                                                    tz
                                            );

                                    param.setResult(icuTz);

                                } catch (Throwable ignored) {
                                }
                            }
                        }
                );

            } catch (Throwable ignored) {
            }

            XposedBridge.log(
                    TAG + ": timezone hooked -> " + tz
            );

        } catch (Throwable t) {

            XposedBridge.log(
                    TAG + ": timezone hook fail -> "
                            + t.getMessage()
            );
        }
    }
}
