package com.amolg.flutterbarcodescanner;

import androidx.annotation.NonNull;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.mlkit.vision.barcode.common.Barcode;
import java.util.Map;

/**
 * FlutterBarcodeScannerPlugin
 */
public class FlutterBarcodeScannerPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware, EventChannel.StreamHandler {
    private static final String CHANNEL = "flutter_barcode_scanner";
    private static final String TAG = FlutterBarcodeScannerPlugin.class.getSimpleName();
    private static final int RC_BARCODE_CAPTURE = 9001;

    private MethodChannel channel;
    private EventChannel eventChannel;
    private Activity activity;
    private Application applicationContext;
    private Result pendingResult;
    private Map<String, Object> arguments;
    private LifeCycleObserver observer;

    public static String lineColor = "";
    public static boolean isShowFlashIcon = false;
    public static boolean isContinuousScan = false;
    static EventChannel.EventSink barcodeStream;

    public FlutterBarcodeScannerPlugin() {
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        channel = new MethodChannel(binding.getBinaryMessenger(), CHANNEL);
        channel.setMethodCallHandler(this);

        eventChannel = new EventChannel(binding.getBinaryMessenger(), "flutter_barcode_scanner_receiver");
        eventChannel.setStreamHandler(this);

        applicationContext = (Application) binding.getApplicationContext();
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        eventChannel.setStreamHandler(null);
        channel = null;
        eventChannel = null;
        applicationContext = null;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        pendingResult = result;

        if (call.method.equals("scanBarcode")) {
            if (!(call.arguments instanceof Map)) {
                throw new IllegalArgumentException("Plugin not passing a map as parameter: " + call.arguments);
            }
            arguments = (Map<String, Object>) call.arguments;
            lineColor = (String) arguments.get("lineColor");
            isShowFlashIcon = (boolean) arguments.get("isShowFlashIcon");
            if (lineColor == null || lineColor.isEmpty()) {
                lineColor = "#DC143C";
            }
            if (arguments.get("scanMode") != null) {
                if ((int) arguments.get("scanMode") == BarcodeCaptureActivity.SCAN_MODE_ENUM.DEFAULT.ordinal()) {
                    BarcodeCaptureActivity.SCAN_MODE = BarcodeCaptureActivity.SCAN_MODE_ENUM.QR.ordinal();
                } else {
                    BarcodeCaptureActivity.SCAN_MODE = (int) arguments.get("scanMode");
                }
            } else {
                BarcodeCaptureActivity.SCAN_MODE = BarcodeCaptureActivity.SCAN_MODE_ENUM.QR.ordinal();
            }

            isContinuousScan = (boolean) arguments.get("isContinuousScan");

            startBarcodeScannerActivityView((String) arguments.get("cancelButtonText"), isContinuousScan);
        }
    }

    private void startBarcodeScannerActivityView(String buttonText, boolean isContinuousScan) {
        Intent intent = new Intent(activity, BarcodeCaptureActivity.class).putExtra("cancelButtonText", buttonText);
        if (isContinuousScan) {
            activity.startActivity(intent);
        } else {
            activity.startActivityForResult(intent, RC_BARCODE_CAPTURE);
        }
    }

    @Override
    public void onListen(Object o, EventChannel.EventSink eventSink) {
        barcodeStream = eventSink;
    }

    @Override
    public void onCancel(Object o) {
        barcodeStream = null;
    }

    public static void onBarcodeScanReceiver(final Barcode barcode) {
        if (barcode != null && !barcode.displayValue.isEmpty()) {
            barcodeStream.success(barcode.rawValue);
        }
    }

    @Override
    public void onAttachedToActivity(ActivityPluginBinding binding) {
        activity = binding.getActivity();
        observer = new LifeCycleObserver(activity);
        applicationContext.registerActivityLifecycleCallbacks(observer);
        binding.addActivityResultListener((requestCode, resultCode, data) -> {
            if (requestCode == RC_BARCODE_CAPTURE) {
                if (resultCode == CommonStatusCodes.SUCCESS) {
                    if (data != null) {
                        Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                        String barcodeResult = barcode != null ? barcode.rawValue : "-1";
                        pendingResult.success(barcodeResult);
                    } else {
                        pendingResult.success("-1");
                    }
                } else {
                    pendingResult.success("-1");
                }
                pendingResult = null;
                arguments = null;
                return true;
            }
            return false;
        });
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        if (observer != null && applicationContext != null) {
            applicationContext.unregisterActivityLifecycleCallbacks(observer);
        }
        activity = null;
    }

    private static class LifeCycleObserver implements Application.ActivityLifecycleCallbacks {
        private final Activity activity;

        LifeCycleObserver(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
        }

        @Override
        public void onActivityResumed(Activity activity) {
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @Override
        public void onActivityStopped(Activity activity) {
            if (this.activity == activity && activity.getApplicationContext() != null) {
                ((Application) activity.getApplicationContext()).unregisterActivityLifecycleCallbacks(this);
            }
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

 
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }
    }
}

