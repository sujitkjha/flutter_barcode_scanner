package com.amolg.flutterbarcodescanner;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;

import java.util.Map;

import io.flutter.embedding.android.FlutterActivity;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.embedding.engine.plugins.lifecycle.FlutterLifecycleAdapter;


package com.example.flutter_barcode_scanner;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.mlkit.vision.barcode.common.Barcode;

import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

public class FlutterBarcodeScannerPlugin implements MethodChannel.MethodCallHandler, PluginRegistry.ActivityResultListener, 
        EventChannel.StreamHandler, FlutterPlugin, ActivityAware {

    private static final String CHANNEL = "flutter_barcode_scanner";
    private static final int RC_BARCODE_CAPTURE = 9001;
    private static final String TAG = FlutterBarcodeScannerPlugin.class.getSimpleName();

    private static Activity activity;
    private static MethodChannel.Result pendingResult;
    private static EventChannel.EventSink barcodeStream;

    private Map<String, Object> arguments;
    private String lineColor = "#DC143C"; // Default line color
    private boolean isShowFlashIcon = false;
    private boolean isContinuousScan = false;

    private FlutterPluginBinding pluginBinding;
    private ActivityPluginBinding activityBinding;
    private Application applicationContext;
    private Lifecycle lifecycle;
    private LifeCycleObserver observer;
    private EventChannel eventChannel;
    private MethodChannel channel;

    public FlutterBarcodeScannerPlugin() {}

    private FlutterBarcodeScannerPlugin(Activity activity, PluginRegistry.Registrar registrar) {
        FlutterBarcodeScannerPlugin.activity = activity;
    }

    /**
     * Plugin registration for old embedding.
     */
    public static void registerWith(PluginRegistry.Registrar registrar) {
        if (registrar.activity() == null) return;
        Activity activity = registrar.activity();
        Application applicationContext = (Application) registrar.context().getApplicationContext();

        FlutterBarcodeScannerPlugin instance = new FlutterBarcodeScannerPlugin(activity, registrar);
        instance.createPluginSetup(registrar.messenger(), applicationContext, activity, registrar, null);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        pendingResult = result;

        if (call.method.equals("scanBarcode")) {
            if (!(call.arguments instanceof Map)) {
                result.error("INVALID_ARGUMENT", "Arguments must be a map", null);
                return;
            }
            arguments = (Map<String, Object>) call.arguments;
            lineColor = (String) arguments.getOrDefault("lineColor", "#DC143C");
            isShowFlashIcon = (boolean) arguments.getOrDefault("isShowFlashIcon", false);
            isContinuousScan = (boolean) arguments.getOrDefault("isContinuousScan", false);

            startBarcodeScannerActivityView((String) arguments.get("cancelButtonText"), isContinuousScan);
        } else {
            result.notImplemented();
        }
    }

    private void startBarcodeScannerActivityView(String buttonText, boolean isContinuousScan) {
        try {
            Intent intent = new Intent(activity, BarcodeCaptureActivity.class);
            intent.putExtra("cancelButtonText", buttonText);
            if (isContinuousScan) {
                activity.startActivity(intent);
            } else {
                activity.startActivityForResult(intent, RC_BARCODE_CAPTURE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting barcode scanner: " + e.getMessage());
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            if (resultCode == CommonStatusCodes.SUCCESS && data != null) {
                try {
                    Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                    pendingResult.success(barcode != null ? barcode.getRawValue() : "-1");
                } catch (Exception e) {
                    pendingResult.success("-1");
                }
            } else {
                pendingResult.success("-1");
            }
            pendingResult = null;
            return true;
        }
        return false;
    }

    @Override
    public void onListen(Object args, EventChannel.EventSink eventSink) {
        barcodeStream = eventSink;
    }

    @Override
    public void onCancel(Object args) {
        barcodeStream = null;
    }

    public static void onBarcodeScanReceiver(final Barcode barcode) {
        if (barcode != null && barcode.getDisplayValue() != null) {
            activity.runOnUiThread(() -> barcodeStream.success(barcode.getRawValue()));
        }
    }

    @Override
    public void onAttachedToEngine(FlutterPluginBinding binding) {
        pluginBinding = binding;
    }

    @Override
    public void onDetachedFromEngine(FlutterPluginBinding binding) {
        pluginBinding = null;
    }

    @Override
    public void onDetachedFromActivity() {
        clearPluginSetup();
    }

    private void createPluginSetup(BinaryMessenger messenger, Application applicationContext, 
                                   Activity activity, PluginRegistry.Registrar registrar, 
                                   ActivityPluginBinding activityBinding) {

        this.applicationContext = applicationContext;
        FlutterBarcodeScannerPlugin.activity = activity;

        eventChannel = new EventChannel(messenger, "flutter_barcode_scanner_receiver");
        eventChannel.setStreamHandler(this);

        channel = new MethodChannel(messenger, CHANNEL);
        channel.setMethodCallHandler(this);

        if (registrar != null) {
            observer = new LifeCycleObserver(activity);
            applicationContext.registerActivityLifecycleCallbacks(observer);
            registrar.addActivityResultListener(this);
        } else {
            activityBinding.addActivityResultListener(this);
            lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(activityBinding);
            observer = new LifeCycleObserver(activity);
            lifecycle.addObserver(observer);
        }
    }

    private void clearPluginSetup() {
        activity = null;
        if (activityBinding != null) {
            activityBinding.removeActivityResultListener(this);
            activityBinding = null;
        }
        if (lifecycle != null) {
            lifecycle.removeObserver(observer);
            lifecycle = null;
        }
        if (applicationContext != null) {
            applicationContext.unregisterActivityLifecycleCallbacks(observer);
            applicationContext = null;
        }
    }

    private static class LifeCycleObserver implements Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
        private final Activity activity;

        LifeCycleObserver(Activity activity) {
            this.activity = activity;
        }

        @Override public void onActivityDestroyed(Activity activity) {
            if (this.activity == activity) {
                ((Application) activity.getApplicationContext()).unregisterActivityLifecycleCallbacks(this);
            }
        }
        
        @Override public void onActivityStopped(Activity activity) {}
        @Override public void onCreate(@NonNull LifecycleOwner owner) {}
        @Override public void onStart(@NonNull LifecycleOwner owner) {}
        @Override public void onResume(@NonNull LifecycleOwner owner) {}
        @Override public void onPause(@NonNull LifecycleOwner owner) {}
        @Override public void onStop(@NonNull LifecycleOwner owner) {}
        @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
        @Override public void onActivityStarted(Activity activity) {}
        @Override public void onActivityResumed(Activity activity) {}
        @Override public void onActivityPaused(Activity activity) {}
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    }
}
