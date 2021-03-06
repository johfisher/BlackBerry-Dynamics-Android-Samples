/*
 * (c) 2018 BlackBerry Limited. All rights reserved.
 */
package com.good.automated.general.utils;

import static android.provider.Settings.ACTION_WIFI_SETTINGS;
import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.view.KeyEvent.KEYCODE_MENU;
import static com.good.automated.general.utils.Duration.AUTHORIZE_CALLBACK;
import static com.good.automated.general.utils.Duration.KNOWN_WIFI_CONNECTION;
import static com.good.automated.general.utils.Duration.SCREEN_ROTATION;
import static com.good.automated.general.utils.Duration.UI_ACTION;
import static com.good.automated.general.utils.Duration.UI_WAIT;
import static com.good.automated.general.utils.Duration.WAIT_FOR_SCREEN;
import static com.good.automated.general.utils.Duration.of;
import static com.googlecode.eyesfree.utils.LogUtils.TAG;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.fingerprint.FingerprintManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityWindowInfo;

import com.good.automated.general.helpers.BBDActivationHelper;
import com.good.automated.general.utils.uitools.networking.UiNetworkManagerFactory;
import com.good.automated.test.screens.BBDPermissionUI;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * AbstractUIAutomatorUtils is a helper class that allow us to interact with system settings of OS Android
 * Most of methods in this class are common for different Android APIs
 * <p>
 * Non-default implementations of some methods have to be overridden in sub-classes
 */
public abstract class AbstractUIAutomatorUtils {

    protected static UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry
            .getInstrumentation());
    GDTestSettings settings;
    private boolean fingerprintSupported;

    public AbstractUIAutomatorUtils() {
        this.uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        this.settings = GDTestSettings.getInstance();
        this.settings.initialize(InstrumentationRegistry.getContext(), getAppPackageName());
    }

    public abstract void launchDateSettings();

    @Deprecated
    public abstract boolean switchOffWindowAnimationScale();

    @Deprecated
    public abstract boolean switchOffTransitionAnimationScale();

    @Deprecated
    public abstract boolean switchOffAnimatorDurationScale();

    public abstract void launchActionSettings(String action);

    public UiDevice getUiDevice() {
        return uiDevice;
    }

    /**
     * @param packageName app package name
     */
    public void launchApp(String packageName) {
        Context context = InstrumentationRegistry.getTargetContext();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            Log.d(TAG, "Cannot find App with package name: " + packageName);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);

        uiDevice.wait(Until.hasObject(By.pkg(packageName)), of(UI_WAIT));

        GDSDKStateReceiver.getInstance();
    }

    /**
     * Performs force stop (via UI) and launch of application by passed native ID.
     *
     * @param appNativeId native id of an app to terminate
     * @return true - if force stop during relaunch was successful
     * false - otherwise
     */
    public boolean relaunchApp(String appNativeId) {
        return relaunchApp(Boolean.FALSE, appNativeId);
    }

    /**
     * Performs force stop and launch of application by passed native ID.
     *
     * @param immediately pass true to terminate app immediately (with adb) / false - via UI
     * @param appNativeId native id of an app to terminate
     * @return true - if force stop during relaunch was successful
     * false - otherwise
     */
    public boolean relaunchApp(boolean immediately, String appNativeId) {

        boolean forceStopSuccess = false;

        if (immediately) {
            terminateAppADB(appNativeId);
            Log.d(TAG, "Terminated app " + appNativeId + " with an ADB command.");
        } else {
            forceStopSuccess = forceStopApp(appNativeId);
            Log.d(TAG, "Terminated app " + appNativeId + " via device system UI.");
        }
        waitForUI(Duration.of(Duration.WAIT_FOR_SCREEN));
        launchApp(appNativeId);

        return forceStopSuccess;
    }

    /**
     * Performs application force stop with ADB shell command.
     *
     * @param appNativeId native id of app to stop
     */
    public void terminateAppADB(String appNativeId) {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().
                executeShellCommand("am force-stop " + appNativeId);
        // Wait to ensure force stop is finished.
        UIAutomatorUtilsFactory.getUIAutomatorUtils()
                .waitForUI(Duration.of(Duration.WAIT_FOR_SCREEN));
    }

    /**
     * Helper method that return UiObject which was created in .xml file by provided ID
     *
     * @param packageName container package ID
     * @param id          id of UI element
     * @return UiObject
     */
    public UiObject getUIObjectById(String packageName, String id) {
        return getUIObjectById(packageName, id, Duration.of(UI_WAIT));
    }

    /**
     * Helper method that return UiObject which was created in runtime by provided ID with a delay
     *
     * @param id    id of UI element
     * @param delay delay to wait for object appearance
     * @return UiObject
     */
    public UiObject getUIObjectById(String id, long delay) {
        long startTime = System.currentTimeMillis();
        long stopTime = startTime + Long.valueOf(delay);

        UiObject uiObject = null;

        do {
            uiObject = uiDevice.findObject(new UiSelector().resourceId(id));
            if (uiObject != null && uiObject.exists()) {
                Log.d(TAG, "UiObject with packageName: " + id + " was found");
                break;
            } else if (stopTime <= System.currentTimeMillis()) {
                Log.d(TAG, "UiObject with packageName: " + id + " wasn't found during " + delay + "ms");
                break;
            }
        } while (uiObject == null || !uiObject.exists());

        return uiObject;
    }

    /**
     * Helper method that return UiObject which was created in .xml file by provided ID with a delay
     *
     * @param packageName container package ID
     * @param id          id of UI element
     * @param delay       delay to wait for object appearance
     * @return UiObject
     */
    public UiObject getUIObjectById(String packageName, String id, long delay) {
        return getUIObjectById(computeResourceId(packageName, id), delay);
    }

    /**
     * Helper method which launches the app under test and waits for it to be on the screen
     * After launching the app, it also register a broadcast receiver to get GD SDK authorization
     * states notifications such as authorized/locked/wipped
     */
    public void launchAppUnderTest() {
        launchApp(getAppPackageName());
    }

    /**
     * Helper method which launches the Activity under test and waits for it to be on the screen
     */
    public void launchSpecificActivity(Class activityClass, String packageName) {
        Context context = InstrumentationRegistry.getTargetContext();
        Intent intent = new Intent(context, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        uiDevice.wait(Until.hasObject(By.pkg(packageName)), Duration.of(UI_WAIT));
        uiDevice.waitForIdle(Duration.of(UI_WAIT));
    }

    /**
     * @param applicationId ID of app on GC (is set in settings.json)
     * @return true if successful, otherwise false
     * <p>
     * Added second attempt to force stop app for cases when two buttons are swapped
     */
    public boolean forceStopApp(String applicationId) {
        launchAppSettings(applicationId);

        UiObject forceStopButton = findByResourceId("com.android.settings:id/right_button");

        if (forceStopButton.exists()) {
            Log.d(TAG, "com.android.settings:id/right_button was found on system UI");
        } else {
            //Possible timing issue in opening system Setting UI
            waitForUI(of(UI_WAIT));
            Log.d(TAG, "Second attempt to find com.android.settings:id/right_button on system UI");
            forceStopButton = findByResourceId("com.android.settings:id/right_button");
        }
        try {
            if (forceStopButton.getText().toLowerCase().contains("force stop")) {
                //This is classic placing of Force stop button on system UI
                return performForceStopAction("right_button");
            } else {
                //Mirror placing of Force stop button on system UI
                return performForceStopAction("left_button");
            }
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "com.android.settings:id/right_button wasn't found on system UI");
        }
        return false;
    }

    /**
     * Helper method to check if software keyboard is present on screen
     *
     * @return true if keyboard was shown and button Back was pressed, otherwise false
     */
    public boolean isKeyboardShown() throws RemoteException {
        for (AccessibilityWindowInfo accessibilityWindowInfo : InstrumentationRegistry.getInstrumentation().getUiAutomation().getWindows()) {
            if (accessibilityWindowInfo.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                Log.d(TAG, "Keyboard is shown");
                return true;
            }
        }
        Log.e(TAG, "Keyboard isn't shown");
        return false;
    }

    /**
     * Helper method to check and close software keyboard if it is present on screen in landscape mode
     *
     * @return true if keyboard was shown and button Back was pressed, otherwise false
     */
    public boolean hideKeyboard() throws RemoteException {
        if (isKeyboardShown()) {
            Log.d(TAG, "Try to press back button");
            return pressBack();
        }
        Log.e(TAG, "Probably keyboard wasn't shown");
        return false;
    }

    /**
     * Helper method to check and close software keyboard if it is present on screen in landscape mode
     * * @return true if keyboard was shown in landscape mode and button Back was pressed, otherwise false
     */
    public boolean hideKeyboardInLandscape() throws RemoteException {
        if (!isNaturalOrientation()) {
            Log.d(TAG, "Device is in Landscape mode");
            return hideKeyboard();
        }
        Log.e(TAG, "Device is in Portrait mode");
        return false;
    }

    /**
     * Helper method to determine child count for element. Can be used for counting elements in list
     */
    public int listViewSize(String packageName, String aResourceID) {
        String resourceID = computeResourceId(packageName, aResourceID);
        int childCount = 0;

        try {
            return findByResourceId(resourceID).getChildCount();
        } catch (UiObjectNotFoundException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * @param forceStopID id of force stop button
     * @return true if successful, otherwise false
     */
    protected boolean performForceStopAction(String forceStopID) {
        boolean result = clickOnItemWithID("com.android.settings", forceStopID, of(WAIT_FOR_SCREEN), of(UI_ACTION));
        UiObject uiAlertTitle = findByResourceId("android:id/alertTitle", Duration.of(WAIT_FOR_SCREEN));
        UiObject uiAlertMessage = findByResourceId("android:id/message", Duration.of(WAIT_FOR_SCREEN));

        try {
            if (result && (uiAlertMessage.getText().toLowerCase().contains("force stop") || uiAlertTitle.getText().toLowerCase().contains("force stop"))) {
                return clickOnItemWithID("android", "button1", of(WAIT_FOR_SCREEN), of(UI_ACTION));
            } else {
                Log.d(TAG, "Wrong action tried to be performed on UI");
                return false;
            }
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't find Force Stop text on UI");
            return false;
        }
    }

    /**
     * Helper method which launches the app specific settings page within Settings app (which allows for items such as force stop and permission changes)
     */
    public void launchAppUnderTestSettings() {
        launchAppSettings(getAppPackageName());
    }

    /**
     * Helper method which launches the app specific settings page within Settings app (which allows for items such as force stop and permission changes)
     *
     * @param appPackageName app package name
     */
    public void launchAppSettings(String appPackageName) {
        Context context = InstrumentationRegistry.getTargetContext();

        final Intent i = new Intent();
        i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        i.setData(Uri.parse("package:" + appPackageName));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        context.startActivity(i);

        uiDevice.waitForIdle(of(UI_WAIT));
    }

    /**
     * Helper method to determine if specific text is shown on screen
     *
     * @param aText text to be found on screen
     * @return true if successful, otherwise false
     */
    public boolean isTextShown(String aText) {
        return isTextShown(aText, of(UI_WAIT));
    }

    /**
     * Helper method to determine if specific text is shown on screen with default timeout
     *
     * @param aText      text to be found on screen
     * @param totalDelay wait for exist
     * @return true if successful, otherwise false
     */
    public boolean isTextShown(String aText, long totalDelay) {

        UiObject objectContainsText;
        objectContainsText = uiDevice.findObject(new UiSelector().textContains(aText));
        if (objectContainsText.waitForExists(totalDelay)) {
            Log.d(TAG, "Text was found: " + aText);
            return true;
        }
        Log.d(TAG, "Text wasn't found on this screen: " + aText);
        return false;
    }

    public boolean isTextShownMatchingRegex(String aRegex, int aTimeMilliseconds) {

        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        UiObject textObject = device.findObject(new UiSelector().textMatches(aRegex));

        textObject.waitForExists(aTimeMilliseconds);

        return textObject.exists();
    }

    /**
     * Helper method to determine if specific description is shown on screen
     *
     * @param aDesc description to be found on screen
     * @return true if successful, otherwise false
     */
    public boolean isDescriptionShown(String aDesc) {
        return isDescriptionShown(aDesc, of(UI_WAIT));
    }

    /**
     * Helper method to determine if specific description is shown on screen with default timeout
     *
     * @param aDesc      description to be found on screen
     * @param totalDelay wait for exist
     * @return true if successful, otherwise false
     */
    public boolean isDescriptionShown(String aDesc, long totalDelay) {

        UiObject objectContainsText;
        objectContainsText = uiDevice.findObject(new UiSelector().descriptionContains(aDesc));
        if (objectContainsText.waitForExists(totalDelay)) {
            Log.d(TAG, "Description was found: " + aDesc);
            return true;
        }
        Log.d(TAG, "Description wasn't found on this screen: " + aDesc);
        return false;
    }

    /**
     * Helper method to determine if specific resource ID is shown on screen
     *
     * @param packageName package name
     * @param aID         text to be found on screen
     * @return true if successful, otherwise false
     */
    public boolean isResourceWithIDShown(String packageName, String aID) {
        return isResourceWithIDShown(packageName, aID, 0);
    }

    /**
     * Helper method to determine if specific resource ID is shown on screen with default timeout
     *
     * @param packageName package name
     * @param aResourceID id of resource to be found on the screen
     * @param totalDelay  wait for exist
     * @return true if successful, otherwise false
     */
    public boolean isResourceWithIDShown(String packageName, String aResourceID, long totalDelay) {
        String resourceID = computeResourceId(packageName, aResourceID);
        UiObject objectContainsText;
        objectContainsText = uiDevice.findObject(new UiSelector().resourceId(resourceID));
        if (objectContainsText.waitForExists(totalDelay)) {
            Log.d(TAG, "Resource with ID was found: " + resourceID);
            return true;
        }
        Log.d(TAG, "Resource with ID wasn't found on this screen: " + resourceID);
        return false;
    }

    /**
     * Helper method to determine if specific text is shown on the screen
     *
     * @param appID     application ID
     * @param elementID ID of UI element
     * @param text      text to be searched
     * @return is text found on the screen
     */
    public boolean isElementWithIdContainsText(String appID, String elementID, String text) {
        try {
            UiObject uiObject = getUIObjectById(appID, elementID);
            if (uiObject != null) {
                return uiObject.getText().contains(text);
            }
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "UI object not found: " + e.getMessage());
        }
        return false;
    }

    /**
     * Helper method to determine if specific text is shown on screen and click on it without a delay
     *
     * @param aText text to be found on screen and clicked
     * @return true if successful, otherwise false
     */
    public boolean clickOnItemWithText(String aText) {
        return clickOnItemWithText(aText, 0);
    }

    /**
     * Helper method to determine if specific text is shown on screen with default timeout and click on it
     *
     * @param aText      text to be found on screen and clicked
     * @param totalDelay wait for exist
     * @return true if successful, otherwise false
     */
    public boolean clickOnItemWithText(String aText, long totalDelay) {

        UiObject objectContainsText;

        objectContainsText = uiDevice.findObject(new UiSelector().text(aText));

        if (objectContainsText.exists() || objectContainsText.waitForExists(totalDelay)) {
            try {
                objectContainsText.click();
                //wait till state will be changed
                if (totalDelay > 0) {
                    objectContainsText.waitUntilGone(of(UI_ACTION));
                }
                Log.d(TAG, "Click on text was performed: " + aText);
                return true;
            } catch (UiObjectNotFoundException e) {
                Log.d(TAG, "Text to be clicked wasn't found on this screen: " + e.getMessage());
                return false;
            }
        }

        Log.d(TAG, "Text to be clicked wasn't found on this screen: " + aText);
        return false;
    }

    /**
     * Helper method to determine if part of specific text is shown on screen and click on it without a delay
     *
     * @param aText text to be found on screen and clicked
     * @return true if successful, otherwise false
     */
    public boolean clickOnItemContainingText(String aText) {
        return clickOnItemContainingText(aText, 0);
    }

    /**
     * Helper method to determine if part of specific text is shown on screen with default timeout and click on it
     *
     * @param aText      text to be found on screen and clicked
     * @param totalDelay wait for exist
     * @return true if successful, otherwise false
     */
    public boolean clickOnItemContainingText(String aText, long totalDelay) {

        UiObject objectContainsText;

        objectContainsText = uiDevice.findObject(new UiSelector().textContains(aText));

        if (objectContainsText.exists() || objectContainsText.waitForExists(totalDelay)) {
            try {
                objectContainsText.click();
                //wait till state will be changed
                if (totalDelay > 0) {
                    objectContainsText.waitUntilGone(of(UI_ACTION));
                }
                Log.d(TAG, "Click on text was performed: " + aText);
                return true;
            } catch (UiObjectNotFoundException e) {
                Log.d(TAG, "Text to be clicked wasn't found on this screen: " + e.getMessage());
                return false;
            }
        }

        Log.d(TAG, "Text to be clicked wasn't found on this screen: " + aText);
        return false;
    }

    /**
     * Helper method for click on a element with specified content description
     *
     * @param text text to search in element's content description
     * @return true if successful, otherwise false
     */
    public boolean clickOnItemWithContentDescriptionText(String text) {
        return clickOnItemWithContentDescriptionText(text, of(WAIT_FOR_SCREEN));
    }

    /**
     * Helper method for click on a element with specified content description
     *
     * @param text       text to search in element's content description
     * @param totalDelay wait for exist
     * @return true if successful, otherwise false
     */
    public boolean clickOnItemWithContentDescriptionText(String text, long totalDelay) {
        UiObject uiObject = uiDevice.findObject(new UiSelector().descriptionContains(text));

        if (uiObject.waitForExists(totalDelay)) {
            try {
                uiObject.click();
                uiDevice.waitForIdle(of(UI_WAIT));
                Log.d(TAG, "Long click on text was performed: " + text);
                return true;
            } catch (UiObjectNotFoundException e) {
                Log.d(TAG, "Text wasn't found on this screen: " + e.getMessage());
                return false;
            }
        }
        Log.d(TAG, "Text wasn't found on this screen: " + text);
        return false;
    }

    /**
     * Helper method for long tap on a element with specified content description
     *
     * @param text       text to search in element's content description
     * @param totalDelay wait for exist
     * @return true if successful, otherwise false
     */
    public boolean longTapOnItemWithContentDescriptionText(String text, long totalDelay) {
        UiObject uiObject = uiDevice.findObject(new UiSelector().descriptionContains(text));

        if (uiObject.waitForExists(totalDelay)) {
            try {
                uiObject.longClick();
                uiDevice.waitForIdle(of(UI_WAIT));
                Log.d(TAG, "Long click on text was performed: " + text);
                return true;
            } catch (UiObjectNotFoundException e) {
                Log.d(TAG, "Text wasn't found on this screen: " + e.getMessage());
                return false;
            }
        }
        Log.d(TAG, "Text wasn't found on this screen: " + text);
        return false;
    }

    /**
     * Helper method for long tap on a element with specified id
     *
     * @param packageName app package name
     * @param aResourceID resource id to be clicked
     * @return true if successful, otherwise false
     */

    public boolean longTapOnItemWithID(String packageName, String aResourceID) {

        UiObject objectWithID = findByResourceId(packageName, aResourceID,
                Duration.of(UI_ACTION));
        if (objectWithID != null) {
            try {
                if (objectWithID.longClick()) {
                    Log.d(TAG, "Object with ID: " + aResourceID + " was long tapped");
                    return true;
                } else {
                    Log.d(TAG, "Object with ID: " + aResourceID + " cannot be long tapped");
                    return false;
                }
            } catch (UiObjectNotFoundException e) {
                Log.d(TAG, "UiObjectNotFoundException: " + e.getMessage());
                return false;
            }
        } else {
            Log.d(TAG, "Object with ID: " + aResourceID + " was not found");
            return false;
        }
    }

    /**
     * Helper method for long tap on a element containing specified text
     *
     * @param packageName app package name
     * @param aText       text of UI Object to be clicked
     * @return true if successful, otherwise false
     */

    public boolean longTapOnItemWithText(String packageName, String aText) {

        UiObject objectContainsText = uiDevice.findObject(new UiSelector().textContains(aText));

        if (objectContainsText != null) {
            try {
                if (objectContainsText.longClick()) {
                    Log.d(TAG, "Object containing text: " + aText + " was long tapped");
                    return true;
                } else {
                    Log.d(TAG, "Object containing text: " + aText + " cannot be long tapped");
                    return false;
                }
            } catch (UiObjectNotFoundException e) {
                Log.d(TAG, "UiObjectNotFoundException: " + e.getMessage());
                return false;
            }
        } else {
            Log.d(TAG, "Object containing text: " + aText + " was not found");
            return false;
        }
    }

    /**
     * Helper method to click on specific item, specified by resourceID, with a default timeout
     */
    public boolean clickOnItemWithID(String aResourceID) {
        return clickOnItemWithID(getAppPackageName(), aResourceID, Duration.of(UI_WAIT), Duration.of(UI_ACTION));
    }

    /**
     * Helper method to click on specific item, specified by package name and resourceID, with a default timeout
     */
    public boolean clickOnItemWithID(String packageName, String aResourceID) {
        return clickOnItemWithID(packageName, aResourceID, Duration.of(UI_WAIT), Duration.of(UI_ACTION));
    }

    /**
     * Helper method to click on specific item, specified by resourceID, with specified timeouts
     */
    public boolean clickOnItemWithID(String aResourceID, int aTimeMSWaitExists, int aTimeMSWaitGone) {
        return clickOnItemWithID(getAppPackageName(), aResourceID, aTimeMSWaitExists, aTimeMSWaitGone);
    }

    /**
     * Helper method to determine if an item with the specific id is shown on screen with default timeout and click on it
     *
     * @param packageName app package name
     * @param aResourceID resource id to be clicked
     * @return true if successful, otherwise false
     */
    public boolean clickOnItemWithID(String packageName, String aResourceID, long aTimeMSWaitExists,
                                     long... aTimeMSWaitGone) {
        String resourceID = computeResourceId(packageName, aResourceID);
        try {
            UiObject testItem = findByResourceId(resourceID);
            testItem.waitForExists(aTimeMSWaitExists);

            if (testItem.exists()) {
                // The result of this click is not returned because it is unreliable:
                // It often returns false despite the click itself was successful
                testItem.click();

                Log.d(TAG, "Click on resource with id: " + resourceID + " was performed");

                if (aTimeMSWaitGone.length > 0 && testItem.waitUntilGone(aTimeMSWaitGone[0])) {
                    Log.d(TAG, "Resource with id: " + resourceID + " was gone from screen");
                    return true;
                } else if (aTimeMSWaitGone.length == 0) {
                    Log.d(TAG, "Looks like click was performed.");
                    return true;
                }
                return true;
            } else {
                Log.d(TAG, "Resource with id: " + resourceID + " is not found on the screen");
            }
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "UiObjectNotFoundException. Resource with id: " + resourceID + " is not found on the screen");
            return false;
        }
        return false;
    }

    /**
     * Helper method that returns isChecked value of UI element
     *
     * @param packageName container package ID
     * @param id          id of UI element that could be Checked
     * @return isChecked value of UI element
     */
    public boolean isElementCheckedByID(String packageName, String id) {
        try {
            return getUIObjectById(packageName, id).isChecked();
        } catch (UiObjectNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Helper method that grants Runtime Permissions for Android API level 23+
     * For Android API level 22 and lower permissions are already granted
     *
     * @param permissions list of required permissions
     */
    public void grantPermissionsInRuntime(String[] permissions) {
        for (String permission : permissions) {
            getInstrumentation().getUiAutomation().executeShellCommand(
                    "pm grant " + getInstrumentation().getTargetContext().getPackageName() + " " + permission);
        }

    }

    /**
     * Helper method to toggle a permission switch in App Settings
     * View Hierarchy  can be determined in App Settings by using the UIAutomatorViewer tool in SDK/tools DIR
     */
    public boolean selectPermissionSwitchItemWithDescription(String aDescription) {

        UiScrollable permissionList = new UiScrollable(new UiSelector().className(RecyclerView.class));
        permissionList.waitForExists(Duration.of(UI_WAIT));

        try {
            UiObject object = permissionList.getChildByText(new UiSelector().className("android.widget.RelativeLayout"), aDescription);
            return object.click();
        } catch (UiObjectNotFoundException e) {
            return false;
        }
    }


    /**
     * Method to change the state of a permission swith in the App Settings.
     * Can be determined to navigate to Permission settings directly from the app UI or not
     *
     * @param appPackageName package name of the appUnderTest
     * @param aDescription title of the permission which state should be changed
     * @param fromAppUI initial place from where the state should be changed
     *                     true - if user have to change the permission from app UI by clicking on 'GO TO SETTINGS' button
     *                     false - if user should go to android app settings to change the permission
     */
    public boolean changePermissionsState(String appPackageName, String aDescription, boolean fromAppUI) {
        if (fromAppUI) {
            new BBDPermissionUI(appPackageName).clickAllow();
            Log.i(TAG, "Navigate to the app" + appPackageName
                    + " settings from app UI. Click on \"GO TO SETTINGS\" button");
        } else{
            launchAppSettings(appPackageName);
            Log.i(TAG, "Navigate to the app" + appPackageName + " Android settings");
        }
        clickOnItemContainingText("Permissions", Duration.of(Duration.ACCEPTING_PASSWORD));
        Log.i(TAG, "Navigate to Permission section of app settings");
        return clickOnItemContainingText(aDescription, Duration.of(Duration.ACCEPTING_PASSWORD));
    }


    /**
     * Toggles specified checkbox to specified state.
     *
     * @param packageName       package name of the radio
     * @param aResourceID       id of the radio
     * @param aTimeMSWaitExists timeout to exist
     * @param checkUnCheck      state to toggle checkbox into
     * @return true - if check was successful
     * false - otherwise
     */
    public boolean setCheckBox(String packageName, String aResourceID, long aTimeMSWaitExists,
                               boolean checkUnCheck) {

        String resourceID = computeResourceId(packageName, aResourceID);
        UiObject checkBox = findByResourceId(resourceID);
        checkBox.waitForExists(aTimeMSWaitExists);

        if (checkBox.exists()) {
            try {
                if (checkBox.isChecked() != checkUnCheck) {
                    checkBox.click();
                    if (checkBox.isChecked() == checkUnCheck) {
                        Log.d(TAG, "Checkbox with id: " + resourceID + " was checked");
                        return true;
                    } else {
                        Log.d(TAG, "Checkbox with id: " + resourceID + " was not checked");
                    }
                } else {
                    Log.d(TAG, "Checkbox is already " + (checkUnCheck ? "checked." : "unchecked."));
                    return true;
                }

            } catch (UiObjectNotFoundException e) {
                Log.d(TAG, "UiObjectNotFoundException. Resource with id: " + packageName + " is not found on the screen");
            }
        } else {
            Log.d(TAG, "Required checkbox (" + resourceID + ") does not exists on the screen.");
        }

        return false;
    }

    /**
     * Checks specified radio button.
     *
     * @param packageName       package name of the radio
     * @param aResourceID       id of the radio
     * @param aTimeMSWaitExists timeout to exist
     * @return true - if check was successful
     * false - otherwise
     */
    public boolean setRadio(String packageName, String aResourceID, long aTimeMSWaitExists) {

        String resourceID = computeResourceId(packageName, aResourceID);
        UiObject radio = findByResourceId(resourceID);
        radio.waitForExists(aTimeMSWaitExists);

        if (radio.exists()) {
            try {
                if (radio.isChecked()) {
                    Log.d(TAG, "Radio button with id: " + resourceID + " was already checked");
                    return true;
                } else {
                    radio.click();
                    if (radio.isChecked()) {
                        Log.d(TAG, "Radio button with id: " + resourceID + " was checked");
                        return true;
                    }
                }
            } catch (UiObjectNotFoundException e) {
                Log.d(TAG, "UiObjectNotFoundException. Resource with id: " + packageName + " is not found on the screen");
                return false;
            }
        } else {
            Log.d(TAG, "Required radio button (" + resourceID + ") does not exists on the screen.");
        }
        return false;
    }

    public boolean openRecentApps() {
        try {
            return uiDevice.pressRecentApps();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "Could not open recent apps");
        return false;
    }

    /**
     * Press key by its code
     *
     * @param keyCode - key code {@link KeyEvent}
     * @return true if operation successful, false otherwise
     */
    public boolean pressKeyCode(int keyCode) {
        return uiDevice.pressKeyCode(keyCode);
    }

    /**
     * Press key by its code with passed meta
     *
     * @param keyCode - key code {@link KeyEvent}
     * @return true if operation successful, false otherwise
     */
    public boolean pressKeyCode(int keyCode, int meta) {
        return uiDevice.pressKeyCode(keyCode, meta);
    }

    /**
     * Helper method which presses HOME
     */
    public void pressHome() {
        pressHome(of(UI_WAIT));
    }

    /**
     * Helper method which presses HOME
     *
     * @param delay delay after pressing Home
     */
    public void pressHome(long delay) {

        // Simulate a short press on the HOME button to ensure we always start from a known State
        uiDevice.pressHome();
        waitForUI(delay);
    }

    /**
     * Helper method which presses BACK
     */
    public boolean pressBack() {

        // Simulate a short press on the BACK button to ensure we always start from a known State
        boolean result = uiDevice.pressBack();
        waitForUI(of(UI_WAIT));
        if (result) {
            Log.d(TAG, "Press Back action was successfully performed");
            return true;
        }
        Log.d(TAG, "Press Back action was failed");
        return false;
    }

    /**
     * Helper method to get App Target API level
     */
    public int getAppTargetAPILevel() {
        return InstrumentationRegistry.getTargetContext().getApplicationInfo().targetSdkVersion;
    }

    /**
     * @param aText task with specific text, to be opened
     * @return true if action successfully performed, otherwise false
     */
    public boolean openTaskWithTextInRecentApps(String aText) {
        try {
            UiObject recentApp = findTaskWithTextInRecentApps(aText);
            return recentApp != null && recentApp.click();

        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "UiObjectNotFoundException. Recent app with text: " + aText + " is not found on the screen");
            return false;
        }
    }

    /**
     * Helper method to enter text into screen element which belongs to a certain class
     */
    public boolean enterTextScreenWithClass(String aClass, String aText) {

        UiObject textScreen = uiDevice.findObject(new UiSelector().className(aClass));
        textScreen.waitForExists(Duration.of(UI_WAIT));

        if (textScreen.exists()) {

            try {
                textScreen.setText(aText);
            } catch (UiObjectNotFoundException e) {
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Remove the task from the recent apps list
     *
     * @param aText task with specific text, to be removed
     * @return true if action successfully performed, otherwise false
     */
    public boolean swipeTaskWithTextInRecentApps(String aText) {
        try {
            UiObject recentApp = findTaskWithTextInRecentApps(aText);

            // The number of step is set to 10 after a few attempts
            // to balance swipe speed and distance. No better rationale for it.
            return recentApp != null && recentApp.swipeRight(10);

        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "UiObjectNotFoundException. Recent app with text: " + aText + " is not found on the screen");
            return false;
        }
    }

    /**
     * Helper method for GD Wearable App provisioning
     */
    public boolean provisionWearableGDApp() {

        boolean success = false;

        // Initial screen should prompt user to start activation
        if (isScreenShown("gd_button_start_activation")) {

            success = clickOnItemWithID("gd_button_start_activation");

            // After clicking on start activation button Activation should start, wait 30seconds for user validation
            if (success && isScreenShown(getAppPackageName(), "button1", 30000)) {

                success = clickOnItemWithID("button1");
                success = success && isScreenShown(getAppPackageName(), "gd_activation_complete", 30000);

            }
        }
        return success;
    }

    /**
     * Finds and returns the UiObject corresponding to the task matching the text
     *
     * @param aText task with specific text
     * @return the UiObject corresponding to the task matching the text
     * @throws UiObjectNotFoundException in case {@link UiObject} with specified text was not found
     */
    protected abstract UiObject findTaskWithTextInRecentApps(String aText) throws UiObjectNotFoundException;


    public String computeResourceId(String packageName, String aResourceID) {
        return packageName + ":id/" + aResourceID;
    }

    /**
     * Helper method which gets an object by resourceID
     *
     * @param resourceID id of view to match
     * @return matched object
     */
    public UiObject findByResourceId(String resourceID) {
        Log.d(TAG, "Finding UiObject with resourceID: " + resourceID);
        return uiDevice.findObject(new UiSelector().resourceId(resourceID));
    }

    /**
     * Helper method which gets an object by resourceID which was created in runtime
     *
     * @param resourceID id of view to match
     * @param delay      time to wait for object appearance
     * @return matched object
     */
    public UiObject findByResourceId(String resourceID, long delay) {
        UiObject uiObject = findByResourceId(resourceID);
        if (uiObject.waitForExists(delay)) {
            Log.d(TAG, "UiObject with resourceID: " + resourceID + " was found");
            return uiObject;
        }
        Log.d(TAG, "UiObject with resourceID: " + resourceID + " wasn't found");
        return null;
    }

    /**
     * Helper method which gets an object by resourceID which was created in .xml file
     *
     * @param packageName of app which object you need to get
     * @param resourceID  id of view to match
     * @param delay       time to wait for object appearance
     * @return matched object
     */
    public UiObject findByResourceId(String packageName, String resourceID, long delay) {
        return findByResourceId(computeResourceId(packageName, resourceID), delay);
    }

    /**
     * Helper method that returns the numeric id of a resource in the current application, provided the resource name.
     *
     * @param aResourceIDName the resource name, uniquely identifying the resource
     * @return the numeric id of the resource
     */
    public int getResourceID(String aResourceIDName) {
        return getResourceID(getAppPackageName(), aResourceIDName);
    }

    /**
     * Helper method that returns the numeric id of a resource in the package specified, provided the resource name.
     *
     * @param packageName     the package name where to look the resource up
     * @param aResourceIDName the resource name, uniquely identifying the resource in scope of the package name
     * @return the numeric id of the resource
     */
    public int getResourceID(String packageName, String aResourceIDName) {
        return InstrumentationRegistry.getTargetContext().getResources().getIdentifier(aResourceIDName, "id", packageName);
    }

    /**
     * Helper method which wakes up device if needed
     */
    public void wakeUpDeviceIfNeeded() {
        try {
            if (!uiDevice.isScreenOn()) {
                uiDevice.wakeUp();
            }
            uiDevice.pressKeyCode(KEYCODE_MENU);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param scrollableContainerId id of container to be scrolled
     * @param aText                 text to scroll to
     * @return true if text found, otherwise false
     */
    public boolean scrollToText(String scrollableContainerId, String aText) {
        String scrollableResId = scrollableContainerId;
        UiSelector scrollableSelector = new UiSelector().resourceId(scrollableResId);
        UiScrollable scrollable = new UiScrollable(scrollableSelector);
        try {
            UiSelector itemWithTextSelector = new UiSelector().text(aText);
            UiObject item = scrollable.getChildByText(itemWithTextSelector, aText, true);
            return item.waitForExists(of(UI_WAIT));
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "UiObjectNotFoundException: " + e.getMessage());
            return false;
        }
    }

    /**
     * Scrolls to an element with specified id.
     *
     * @param scrollableContainerId id of a scrollable element
     * @param elementId             id of an element to search for
     * @return true - if scroll was performed successfully / false - otherwise
     */
    public boolean scrollToTheElementWithId(String scrollableContainerId, String elementId) {
        UiSelector elementSelector = new UiSelector().resourceId(elementId);

        UiSelector scrollableSelector = new UiSelector().resourceId(scrollableContainerId);
        UiScrollable scrollable = new UiScrollable(scrollableSelector);

        try {
            return scrollable.scrollIntoView(elementSelector);
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "UiObjectNotFoundException: " + e.getMessage());
            return false;
        }
    }

    /**
     * Helper method which handles System Dialogues which may be showing and thus impact tests
     */
    public void acceptSystemDialogues() {
        acceptSystemWelcomeMessageIfShown();
        cancelSystemCrashDialogueIfShown();
    }

    /**
     * Helper method which cancels a system error message if shown (can be at start of emulator boot if part of system has crashed)
     */
    public void cancelSystemCrashDialogueIfShown() {
        // We don't wait for long duration, if it is showing then deal with it, otherwise move on
        if (isTextShown("has stopped", of(WAIT_FOR_SCREEN))) {
            clickOnItemWithText("OK", of(UI_WAIT));
        }

    }

    /**
     * Helper method which accepts and dismisses System Welcome message (can be at first boot of emulator or device)
     */
    public void acceptSystemWelcomeMessageIfShown() {

        // We don't wait for long duration, if it is showing then deal with it, otherwise move on
        if (isTextShown("Welcome", of(WAIT_FOR_SCREEN))) {
            clickOnItemWithText("GOT IT", of(UI_WAIT));
        }
    }

    /**
     * Helper method to get app under test Package Name
     */
    public String getAppPackageName() {
        return InstrumentationRegistry.getTargetContext().getPackageName();
    }

    /**
     * Helper method to get app Package Name that is shown in foreground
     */
    public String getAppPackageNameInForeground() {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).getCurrentPackageName();
    }

    /**
     * Helper method that checks whether any of the elements passed in input is displayed on screen.
     *
     * @param appID         package name of the app
     * @param uiElementsMap list of IDs of ui elements
     * @return the string id if the element is found, null otherwise
     */
    @Deprecated
    public String getDisplayedComponentOnTheScreen(String appID, List<String> uiElementsMap) {
        return getUiElementShown(appID, uiElementsMap);
    }

    /**
     * Helper method that checks whether any of the elements passed in input is displayed on screen.
     *
     * @param packageName package name of the app
     * @param uiElements  list of IDs of ui elements
     * @return the string id if the element is found, null otherwise
     */
    public String getUiElementShown(String packageName, List<String> uiElements) {
        UiObject ob;
        for (String res : uiElements) {
            ob = findByResourceId(computeResourceId(packageName, res));
            if (ob.exists()) {
                return res;
            }
        }
        return null;
    }

    /**
     * @param packageName package to work with
     */
    public void initialiseSettings(String packageName) {
        settings.initialize(InstrumentationRegistry.getContext(), packageName);
    }

    /**
     * @param packageName package to be provisioned
     * @return an email or userId that can be used for provision of an application after
     * installation or during unlock as String
     */
    public String getProvisionLogin(String packageName) {
        return settings.getAppProvisionEmail(packageName);
    }

    /**
     * @param packageName package to be provisioned
     * @return the access key that can be used for provision of an application after installation as String
     */
    public String getAccessKey(String packageName) {
        return settings.getAppProvisionAccessKey(packageName);
    }

    /**
     * @param packageName unlock password for app under test
     * @return the password for an application as String
     */
    public String getAppProvisionPassword(String packageName) {
        return settings.getAppProvisionPassword(packageName);
    }

    /**
     * @param packageName unlock key for app under test
     * @return the unlock key that can be used for provision the application if needed
     * after the initial provision as String
     */
    public String getAppUnlockKey(String packageName) {
        return GDTestSettings.getInstance().getAppUnlockKey(packageName);
    }

    /**
     * Wrapper method for uiautomator internal wait realisation
     *
     * @param delay waits for UI
     */
    public void waitForUI(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper method which checks that GD has sent the  GD Authorized callback (which needs to be sent before app code can run)
     */
    public boolean checkGDAuthorized() {

        boolean ret = GDSDKStateReceiver.getInstance().checkAuthorized();

        if (ret) {
            //If already authorized return immediately
            Log.d(TAG, "checkGDAuthorized: already TRUE");
            return true;
        }

        // If we aren't already authorized we wait up to 10secs for auth change to occur (i.e. if we have just logged in or finished activation)
        // We are explicitly  waiting for 10 seconds here, not the Duration.of(Duration.UI_WAIT)
        GDSDKStateReceiver.getInstance().waitForAuthorizedChange(of(AUTHORIZE_CALLBACK));

        ret = GDSDKStateReceiver.getInstance().checkAuthorized();
        Log.d(TAG, "checkGDAuthorized: finished waiting result = " + ret);

        // This time we return value we receive
        return ret;

    }

    /**
     * Helper method to get application version name
     *
     * @param packageName package name of application
     * @return application version name (e.g. 1.4.0.0)
     */
    public String getAppVersionName(String packageName) {
        String versionName = "";
        try {
            versionName = InstrumentationRegistry.getTargetContext().getPackageManager().getPackageInfo(packageName, PackageManager.GET_SERVICES).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Could not find specified package");
        }
        return versionName;
    }

    public boolean waitUntilTextGoneFormScreen(String sText, long timeout) {
        long i = timeout / of(UI_ACTION);
        for (long j = 0; j < i; j++) {
            waitForUI(of(UI_ACTION));
            if (!getUiDevice().findObject(new UiSelector().text(sText)).exists()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper method to determine if a screen (Either GD screen or App UI screen) containing specified ResourceID is shown (with default timeout and default current app)
     */
    public boolean isScreenShown(String aResourceID) {
        return isScreenShown(getAppPackageName(), aResourceID, Duration.of(UI_ACTION));
    }

    /**
     * Helper method to determine if a screen in the provided app (Either GD screen or App UI screen) containing specified ResourceID is shown (with default timeout)
     */
    public boolean isScreenShown(String packageName, String aResourceID) {
        return isScreenShown(packageName, aResourceID, Duration.of(UI_ACTION));
    }

    /**
     * Helper method to determine if a screen in the provided app (Either GD screen or App UI screen) containing specified ResourceID is shown waiting a certain timeout
     *
     * @param packageName package name of app under test
     * @param aResourceID id to be found on UI
     * @param delay       delay to find element on the screen
     */
    public boolean isScreenShown(String packageName, String aResourceID, long delay) {
        String resourceID = computeResourceId(packageName, aResourceID);
        Log.d(TAG, "Try to find resource with ID: " + resourceID);
        UiObject testScreen = findByResourceId(resourceID);

        if (testScreen.waitForExists(delay)) {
            Log.d(TAG, "Resource is found! ID: " + resourceID);
            return true;
        } else {
            Log.d(TAG, "Resource not found! ID: " + resourceID);
            return false;
        }
    }

    /**
     * Scrolls to the end of the scrollable.
     *
     * @param scrollableContainerID ID of a scrollable
     * @return true - if scrolled
     * false - otherwise
     */
    public boolean scrollToTheEnd(String scrollableContainerID) {
        String scrollableResId = scrollableContainerID;
        UiSelector scrollableSelector = new UiSelector().resourceId(scrollableResId);
        UiScrollable scrollable = new UiScrollable(scrollableSelector);

        try {
            return scrollable.scrollToEnd(scrollable.getMaxSearchSwipes());
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "UiObjectNotFoundException: " + e.getMessage());
            return false;
        }
    }

    /**
     * Scrolls to the beginning of the scrollable.
     *
     * @param scrollableContainerID ID of a scrollable
     * @return true - if scrolled
     * false - otherwise
     */
    public boolean scrollToTheBeginning(String scrollableContainerID) {
        String scrollableResId = scrollableContainerID;
        UiSelector scrollableSelector = new UiSelector().resourceId(scrollableResId);
        UiScrollable scrollable = new UiScrollable(scrollableSelector);

        try {
            return scrollable.scrollToBeginning(scrollable.getMaxSearchSwipes());
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "UiObjectNotFoundException: " + e.getMessage());
            return false;
        }
    }

    /**
     * Swipe on UI element from the top
     *
     * @param uiElementId UI element ID
     * @return true if swipe was successful, false otherwise
     */
    public boolean swipe(String uiElementId) {
        UiSelector uiElement = new UiSelector().resourceId(uiElementId);
        UiObject uiObject = uiDevice.findObject(uiElement);
        uiObject.waitForExists(of(UI_ACTION));
        try {
            Rect bound = uiObject.getBounds();
            return uiDevice.swipe(bound.centerX(), bound.top, bound.centerX(), bound.centerY(), 50);

        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "UiObjectNotFoundException: " + e.getMessage());
            return false;
        }
    }

    /**
     * Turns device screen off.
     * Works properly on API 23.
     * May mulfunction on higher API levels.
     */
    public void turnScreenOnOff() {
        uiDevice.pressKeyCode(KeyEvent.KEYCODE_POWER);
        waitForUI(of(WAIT_FOR_SCREEN));
    }

    /**
     * Helper method which locks device
     */
    public void lockDevice() {
        // Simulate a lock of the device
        uiDevice.pressKeyCode(KeyEvent.KEYCODE_SLEEP);
    }

    /**
     * Helper method which sets device password
     * Works only for pure Android UI
     */
    public boolean setDevicePassword(String devicePassword) {
        return setDevicePasswordOrPIN("Password", devicePassword);
    }

    /**
     * Helper method which sets device PIN
     * Works only for pure Android UI
     */
    public boolean setDevicePIN(String devicePIN) {
        return setDevicePasswordOrPIN("PIN", devicePIN);
    }

    /**
     * Helper method which sets device password/PIN
     * By default it configured to support 23 API level
     * Works only for pure Android UI
     */
    protected boolean setDevicePasswordOrPIN(String passwordPIN, String devicePasscode) {
        Log.d(TAG, "Default setting device PIN or Password");
        String setupPasswordPinText = "Choose your " + passwordPIN;
        String confirmYourPasswordPinText = "Confirm your " + passwordPIN;
        String completeToSetPasswordPINButton = "next_button";

        return setDevicePasswordOrPIN(passwordPIN, devicePasscode, setupPasswordPinText, confirmYourPasswordPinText, completeToSetPasswordPINButton);
    }

    /**
     * Works only for pure Android UI
     *
     * @param passwordPIN                    what should be selected PIN or Password
     * @param devicePasscode                 device password or PIN
     * @param setupPasswordPinText           helper text
     * @param confirmYourPasswordPinText     helper text
     * @param completeToSetPasswordPINButton complete setup PIN or Password
     * @return true if PIN or Password was successfully set, otherwise false
     */
    protected final boolean setDevicePasswordOrPIN(String passwordPIN, String devicePasscode, String setupPasswordPinText, String confirmYourPasswordPinText, String completeToSetPasswordPINButton) {

        openSecuritySettings();

        if (isDevicePasswordSet()) {
            return true;
        }

        if (!isTextShown("Screen lock", of(UI_WAIT))) {
            Log.d(TAG, "\"Screen lock\" screen is not shown");
            return false;
        }

        if (clickOnItemWithText("Screen lock", of(WAIT_FOR_SCREEN))) {
            if (clickOnItemWithText(passwordPIN, of(WAIT_FOR_SCREEN))) {
                if (clickOnItemWithID("com.android.settings", "encrypt_dont_require_password", of(WAIT_FOR_SCREEN), of(UI_WAIT))
                        && (clickOnItemWithID("com.android.settings", "next_button", of(WAIT_FOR_SCREEN), of(UI_WAIT)) || isTextShown(setupPasswordPinText))) {
                    if (enterTextToItemWithID("com.android.settings", "password_entry", devicePasscode) &&
                            (clickOnItemWithID("com.android.settings", "next_button", of(WAIT_FOR_SCREEN), of(UI_WAIT))
                                    || isTextShown(confirmYourPasswordPinText))) {
                        if (enterTextToItemWithID("com.android.settings", "password_entry", devicePasscode) &&
                                (clickOnItemWithID("com.android.settings", "next_button", of(WAIT_FOR_SCREEN), of(UI_WAIT))
                                        || isTextShown("Notifications"))) {
                            if (setRadio("com.android.settings", "show_all", of(WAIT_FOR_SCREEN)) &&
                                    clickOnItemWithID("com.android.settings", completeToSetPasswordPINButton, of(WAIT_FOR_SCREEN), of(UI_WAIT))) {
                                Log.d(TAG, "Password was successfully set: " + devicePasscode);
                                return true;
                            }
                            Log.d(TAG, "Couldn't complete to set of device PIN or Password. Was left default value");
                            return true;
                        }
                        Log.d(TAG, "\"Notifications\" screen is not shown. Couldn't enter text into field with id: com.android.settings:id/show_all");
                        return false;
                    }
                    Log.d(TAG, "\"Choose your PIN\" screen is not shown. Couldn't enter password into field with id: com.android.settings:id/password_entry");
                    return false;
                }
                Log.d(TAG, "encrypt_dont_require_password screen is not shown.");
                return false;
            }
            Log.d(TAG, "\"Security\" screen is not shown ");
            return false;
        }
        Log.d(TAG, "Probably \"Security\" screen is not shown. Couldn't click on \"Screen lock\" text");
        return false;
    }

    /**
     * Helper method which removes device PIN
     * By default it configured to support 23 API level and pure Android UI
     */
    public boolean removeDevicePIN(String devicePIN) {
        return removeDevicePasswordOrPIN(devicePIN);
    }

    /**
     * Helper method which removes device Password
     * By default it configured to support 23 API level and pure Android UI
     */
    public boolean removeDevicePassword(String devicePassword) {
        return removeDevicePasswordOrPIN(devicePassword);
    }

    /**
     * Helper method which removes device password/PIN
     * By default it configured to support 23 API level and pure Android UI
     */
    protected boolean removeDevicePasswordOrPIN(String devicePasscode) {
        if (!isDevicePasswordSet()) {
            return true;
        }

        openSecuritySettings();

        if (clickOnItemWithText("Screen lock", of(WAIT_FOR_SCREEN))) {
            if (enterTextToItemWithID("com.android.settings", "password_entry", devicePasscode) && clickKeyboardOk()) {
                if (clickOnItemWithText("None", of(WAIT_FOR_SCREEN)) && clickOnItemWithID("android", "button1", of(WAIT_FOR_SCREEN), of(UI_ACTION))) {
                    Log.d(TAG, "Device password was successfully removed");
                    return true;
                }
                Log.d(TAG, "\"Choose screen lock\" screen is not shown");
                return false;
            }
            Log.d(TAG, "Cannot enter device password");
            return false;
        }
        Log.d(TAG, "Probably \"Security\" screen is not shown. Couldn't click on \"Screen lock\" text");
        return false;
    }

    /**
     * @return true if click was performed successfully, otherwise false
     */
    public boolean clickKeyboardOk() {
        if (uiDevice.pressKeyCode(KeyEvent.KEYCODE_ENTER)) {
            uiDevice.waitForIdle(of(UI_WAIT));
            return true;
        }
        return false;
    }

    /**
     * @param packageName package name
     * @param aResourceID resource id
     * @param textToEnter text to be entered in specified resource id
     * @return true if text was entered, otherwise false
     */
    public boolean enterTextToItemWithID(String packageName, String aResourceID, String textToEnter) {
        try {
            hideKeyboardInLandscape();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        String resourceID = computeResourceId(packageName, aResourceID);
        UiObject testItem = findByResourceId(resourceID);
        try {
            if (testItem.waitForExists(of(UI_WAIT))) {
                testItem.legacySetText(textToEnter);
                Log.d(TAG, "Text: \"" + textToEnter + "\" was entered");
                if (testItem.getText() != null) {
                    Log.d(TAG, "Confirmation. Text: \"" + textToEnter + "\" was entered");
                    return true;
                } else {
                    Log.d(TAG, "Field is empty. ResourceID: " + resourceID);
                    return false;
                }
            } else {
                Log.d(TAG, "resourceID: " + resourceID + " doesn't exist");
                return false;
            }
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't enter text to UI element with ID: " + resourceID + " UiObjectNotFoundException: " + e.getMessage());
            return false;
        }
    }

    public boolean enterTextToItemWithText(String text, String textToEnter) {
        return enterTextToItemWithText(text, textToEnter, of(WAIT_FOR_SCREEN));
    }

    public boolean enterTextToItemWithText(String text, String textToEnter, long delay) {
        try {
            hideKeyboardInLandscape();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "Finding UiObject with text: " + text);

        UiObject uiObject = uiDevice.findObject(new UiSelector().text(text));

        if (uiObject.waitForExists(delay)) {
            Log.d(TAG, "UiObject with text: " + text + " was found");
            try {
                return uiObject.setText(textToEnter);
            } catch (UiObjectNotFoundException e) {
                e.printStackTrace();
                Log.d(TAG, "Text " + textToEnter + " wasn't entered to UiObject with text: " + text);
            }
        }
        Log.d(TAG, "UiObject with text: " + text + " wasn't found");
        return false;
    }

    public boolean enterTextToItemWithDescription(String textDescription, String textToEnter, long
            delay) {
        Log.d(TAG, "Finding UiObject with text: " + textDescription);

        UiObject uiObject = uiDevice.findObject(new UiSelector().descriptionContains(textDescription));

        if (uiObject.waitForExists(delay)) {
            Log.d(TAG, "UiObject with text: " + textDescription + " was found");
            try {
                return uiObject.setText(textToEnter);
            } catch (UiObjectNotFoundException e) {
                e.printStackTrace();
                Log.d(TAG, "Text " + textToEnter + " wasn't entered to UiObject with text: " + textDescription);
            }
        }
        Log.d(TAG, "UiObject with text: " + textDescription + " wasn't found");
        return false;
    }

    /**
     * @param packageName package name
     * @param aResourceID resource id
     * @return true if text was erased, otherwise false
     */
    public boolean eraseTextFieldWithID(String packageName, String aResourceID) {
        try {
            hideKeyboardInLandscape();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        String resourceID = computeResourceId(packageName, aResourceID);
        UiObject testItem = findByResourceId(resourceID);
        try {
            if (testItem.waitForExists(of(UI_WAIT))) {
                testItem.clearTextField();
                return true;
            } else {
                Log.d(TAG, "resourceID: " + resourceID + " doesn't exist");
                return false;
            }
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't erase text from UI element with ID: " + resourceID + " Error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Helper method which opens device security settings UI
     */
    protected void openSecuritySettings() {
        Context context = InstrumentationRegistry.getTargetContext();

        final Intent i = new Intent();
        i.setAction(Settings.ACTION_SECURITY_SETTINGS);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        context.startActivity(i);

        uiDevice.waitForIdle(of(UI_WAIT));
    }

    /**
     * Helper method which shows UI that asks to scan fingerprint
     * <p>
     * After calling this method, device/emulator will expect to scan your fingerprint
     * To simulate fingerprint tauch on emulator you have to execute command:
     * adb -e emu finger touch 11551155
     */
    public boolean getFingerprintScreen(String devicePass) {
        String findTheSensor = "Find the sensor";
        String fingerprintNextButton = "next_button";
        return getFingerprintScreen(devicePass, fingerprintNextButton, findTheSensor);
    }

    public final boolean isFingerprintSet() {
        openSecuritySettings();

        if (isFingerprintSupported() && isDevicePasswordSet()) {
            Log.d(TAG, "Fingerprint is supported by this hardware!");
            if (isTextShown("fingerprint set up")) {
                return true;
            } else {
                return scrollToText("com.android.settings:id/list", "fingerprint set up");
            }
        } else {
            Log.d(TAG, "Fingerprint is not supported by this hardware OR device PIN wasn't set");
            return false;
        }
    }

    public final boolean getFingerprintScreen(String devicePass, String fingerprintNextButton, String findTheSensor) {
        openSecuritySettings();

        if (isFingerprintSupported() && isDevicePasswordSet()) {
            Log.d(TAG, "Fingerprint is supported by this hardware!");
            if (isTextShown("Fingerprint")) {
                Log.d(TAG, "\"Fingerprint\" was found on Security screen");
            } else {
                Log.d(TAG, "Cannot find \"Fingerprint\" on Security screen. Try to scroll to it");
                if (scrollToText("com.android.settings:id/list", "Fingerprint")) {
                    Log.d(TAG, "\"Fingerprint\" was found on Security screen after scrolling to it");
                } else {
                    Log.d(TAG, "Cannot find \"Fingerprint\" on Security screen");
                    return false;
                }
            }

            if (clickOnItemWithText("Fingerprint", of(WAIT_FOR_SCREEN))) {
                if (clickOnItemWithID("com.android.settings", fingerprintNextButton, of(UI_WAIT), of(UI_WAIT))) {
                    if (enterTextToItemWithID("com.android.settings", "password_entry", devicePass) && clickKeyboardOk() && isTextShown(findTheSensor)) {
                        if (completeGettingOfFingerprintScan()) {
                            return true;
                        }
                        Log.d(TAG, "Cannot proceed to fingerprint setup");
                        return false;
                    }
                    Log.d(TAG, "Couldn't enter device password");
                    return false;
                }
                Log.d(TAG, "Couldn't click on next button");
                return false;
            }
            Log.d(TAG, "Cannot find \"Fingerprint\" on Security screen");
        } else {
            Log.d(TAG, "Fingerprint is not supported by this hardware");
        }
        return false;
    }

    /**
     * @return true if is proposed to scan your finger
     */
    protected boolean completeGettingOfFingerprintScan() {
        String fingerprintNextButton = "next_button";
        String fingerprintScrollViewId = "com.android.settings:id/suw_bottom_scroll_view";
        String scrollToTextForFingerprint = "NEXT";
        String scanYourFinger = "Put your finger on the sensor";

        return completeGettingOfFingerprintScan(fingerprintNextButton, fingerprintScrollViewId, scrollToTextForFingerprint, scanYourFinger);
    }

    /**
     * @return true if is proposed to scan your finger
     */
    protected final boolean completeGettingOfFingerprintScan(String fingerprintNextButton, String fingerprintScrollViewId, String scrollToTextForFingerprint, String scanYourFinger) {
        if (isResourceWithIDShown("com.android.settings", fingerprintNextButton)) {
            Log.d(TAG, fingerprintNextButton + " was found on the screen");
        } else {
            Log.d(TAG, "Cannot find " + fingerprintNextButton + " on the screen. Try to scroll to it");
            if (scrollToText(fingerprintScrollViewId, scrollToTextForFingerprint)) {
                Log.d(TAG, fingerprintNextButton + " was found on the screen");
            } else {
                Log.d(TAG, "Cannot find " + fingerprintNextButton + " button on the screen");
                return false;
            }
        }
        return clickOnItemWithID("com.android.settings", fingerprintNextButton, of(WAIT_FOR_SCREEN), of(UI_WAIT)) && isTextShown(scanYourFinger);
    }

    /**
     * @return true if fingerprint was accepted successfully
     */
    public boolean completeFingerprintSetup() {
        String fingerprintNextButton = "next_button";
        String completeFingerprintScanScrollViewId = "com.android.settings:id/suw_scroll_view";
        String scrollToTextToCompleteFingerprintScan = "DONE";
        return completeFingerprintSetup(fingerprintNextButton, completeFingerprintScanScrollViewId, scrollToTextToCompleteFingerprintScan);
    }

    public final boolean completeFingerprintSetup(String fingerprintNextButton, String completeFingerprintScanScrollViewId, String scrollToTextToCompleteFingerprintScan) {

        if (isResourceWithIDShown("com.android.settings", fingerprintNextButton, of(UI_WAIT))) {
            Log.d(TAG, fingerprintNextButton + " was found on the screen");
        } else {
            Log.d(TAG, "Cannot find " + fingerprintNextButton + " on the screen. Try to scroll to it");
            if (scrollToText(completeFingerprintScanScrollViewId, scrollToTextToCompleteFingerprintScan)) {
                Log.d(TAG, fingerprintNextButton + " was found on the screen");
            } else {
                Log.d(TAG, "Cannot find " + fingerprintNextButton + " button on the screen");
                return false;
            }
        }

        return clickOnItemWithID("com.android.settings", fingerprintNextButton, of(WAIT_FOR_SCREEN), of(UI_WAIT));
    }

    /**
     * @param fingerprintNameToRemove name of fingerprint to be removed
     * @param passwordPIN             password or PIN
     * @return true if fingerprint was removed otherwise false
     */
    public boolean removeFingerprint(String fingerprintNameToRemove, String passwordPIN) {
        openSecuritySettings();
        if (isFingerprintSupported() && isDevicePasswordSet()) {
            Log.d(TAG, "Fingerprint is supported by this hardware!");
            if (isTextShown("Fingerprint")) {
                Log.d(TAG, "\"Fingerprint\" was found on Security screen");
            } else {
                Log.d(TAG, "Cannot find \"Fingerprint\" on Security screen. Try to scroll to it");
                if (scrollToText("com.android.settings:id/list", "Fingerprint")) {
                    Log.d(TAG, "\"Fingerprint\" was found on Security screen after scrolling to it");
                } else {
                    Log.d(TAG, "Cannot find \"Fingerprint\" on Security screen");
                    return false;
                }
            }

            if (clickOnItemWithText("Fingerprint", of(WAIT_FOR_SCREEN))) {
                if (enterTextToItemWithID("com.android.settings", "password_entry", passwordPIN) && clickKeyboardOk() && isTextShown(fingerprintNameToRemove)) {
                    if (clickOnItemWithText(fingerprintNameToRemove, of(WAIT_FOR_SCREEN))) {
                        if (clickOnItemWithID("android", "button2", of(WAIT_FOR_SCREEN))) {
                            if (isTextShown("Remove all fingerprints") && clickOnItemWithID("android", "button1", of(WAIT_FOR_SCREEN))) {
                                Log.d(TAG, "The last one fingerprint was removed");
                                return true;
                            }
                            Log.d(TAG, "Specified fingerprint was removed. Looks like some fingerprints were left.");
                            return true;
                        }
                        Log.d(TAG, "Cannot remove specified fingerprint");
                        return false;
                    }
                }
                Log.d(TAG, "No access to fingerprint settings");
            }
            Log.d(TAG, "Fingerprint setting not found in Security list");
            return false;
        } else {
            Log.d(TAG, "Fingerprint in not supported by this hardware");
            return true;
        }
    }

    public boolean isDevicePasswordSet() {
        KeyguardManager keyguardManager = (KeyguardManager) InstrumentationRegistry.getContext().getSystemService(Context.KEYGUARD_SERVICE); //api 23+
        return keyguardManager != null && keyguardManager.isDeviceSecure();
    }

    @SuppressWarnings("MissingPermission")
    public boolean isFingerprintSupported() {
        //Fingerprint API only available on from Android 6.0 (M)
        FingerprintManager fingerprintManager = (FingerprintManager) InstrumentationRegistry.getTargetContext().getSystemService(Context.FINGERPRINT_SERVICE);
        return fingerprintManager != null && fingerprintManager.isHardwareDetected();
    }

    /**
     * Opens the notification shade.
     *
     * @return true if successful, false otherwise
     */
    public boolean openNotifications() {
        return uiDevice.openNotification();
    }

    /**
     * Disables WiFi on a device.
     *
     * @return true - if switch was successfull
     * false - otherwise
     * @throws UiObjectNotFoundException in case if switch was not found on the screen
     */
    public boolean disableWiFi() throws UiObjectNotFoundException {
        boolean result = false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            result = switchWiFi("on", "off");
        }
        pressHome();
        return result;
    }

    /**
     * Enables WiFi on the device.
     *
     * @return true - if switch was successful
     * false - otherwise
     * @throws UiObjectNotFoundException in case if switch was not found on the screen
     */
    public boolean enableWiFi() throws UiObjectNotFoundException {
        boolean result = false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            result = switchWiFi("off", "on");
        }
        if (!isTextShown("Connected", of(KNOWN_WIFI_CONNECTION))) {
            Log.d(TAG, "Did not connect to some known network.");
            return false;
        }
        pressHome();
        return result;

    }

    /**
     * Switches WiFi to On or Off state.
     *
     * @param fromState state of WiFi to switch from
     * @param toState   state of WiFi to switch to
     * @return true - if switch was successfull
     * false - otherwise
     * @throws UiObjectNotFoundException in case if switch was not found on the screen
     */
    private boolean switchWiFi(String fromState, String toState) throws UiObjectNotFoundException {

        String wifiStatus = getWiFiStatus();

        if (!TextUtils.isEmpty(wifiStatus)) {
            if (wifiStatus.equalsIgnoreCase(toState)) {

                Log.d(TAG, "WiFi is already " + toState);
                return true;
            } else {
                getWiFiSwitch().click();
                if (getWiFiSwitch().getText().equalsIgnoreCase(toState)) {

                    Log.d(TAG, "WiFi was switched from " + fromState + " to " + toState);
                    return true;
                } else {
                    Log.d(TAG, "Failed to switch WiFi form " + fromState + " to " + toState);
                }
            }
        } else {
            Log.d(TAG, "Couldn't get WiFi status (null was returned).");
        }
        return false;
    }

    /**
     * Opens WiFi settings and gets its status (whether it is turned on or not).
     *
     * @return the status of WiFi (On/Off)
     * @throws UiObjectNotFoundException if WiFi switch was not found
     */
    public String getWiFiStatus() throws UiObjectNotFoundException {

        if (!getWiFiSwitch().exists()) {
            launchActionSettings(ACTION_WIFI_SETTINGS);
            waitForUI(of(WAIT_FOR_SCREEN));
        }

        if (getWiFiSwitch().exists()) {
            return getWiFiSwitch().getText();
        } else {
            Log.d(TAG, "WiFi settings were not opened.");
            return null;
        }
    }

    /**
     * @param shouldBeEnabled true if Automatic Date & Time should be enabled, otherwise false
     * @param totalDelay      wait for UI changes
     * @param timeout         wait for existence of element
     */
    public void selectAutomaticDateTime(boolean shouldBeEnabled, long totalDelay, long timeout) {

        UiObject list;
        UiObject switcher;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                list = findByResourceId("com.android.settings:id/list");
                switcher = list.getChild(new UiSelector().index(0)).
                        getChild(new UiSelector().resourceId("android:id/widget_frame")).
                        getChild(new UiSelector().resourceId("android:id/switch_widget"));
            } else {
                list = findByResourceId("android:id/list");
                switcher = list.getChild(new UiSelector().index(0)).
                        getChild(new UiSelector().resourceId("android:id/switchWidget"));
            }

            switcher.waitForExists(timeout);

            if ((switcher.getText().contains("ON") && !shouldBeEnabled) || (switcher.getText().contains("OFF") && shouldBeEnabled)) {
                clickOnItemWithText("Automatic date & time");
            } else {
                Log.d(TAG, "No need to change Automatic date & time");
            }
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, e.toString());
        }
        Log.d(TAG, "Text wasn't found on this screen!");
    }

    /**
     * Increase date for specified number of days
     *
     * @param daysToAdd count of days to add to calendar
     * @param timeout   time to wait for existence of needed UI element
     */
    public void changeDateSettings(int daysToAdd, long timeout) {

        UiObject list;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            list = findByResourceId("com.android.settings:id/list");
        } else {
            list = findByResourceId("android:id/list");
        }

        try {
            UiObject setDate = list.getChild(new UiSelector().textMatches("Set date"));

            setDate.waitForExists(timeout);
            setDate.click();

            UiObject monthView = findByResourceId("android:id/month_view");
            int currentDate = Integer.parseInt(monthView.getChild(new UiSelector().checked(true)).getText());
            int daysInMonth = monthView.getChildCount();
            if (currentDate + daysToAdd > monthView.getChildCount()) {
                UiObject buttonNext = findByResourceId("android:id/next");
                buttonNext.click();
                monthView.getChild(new UiSelector().index(currentDate + daysToAdd - daysInMonth - 1)).click();

            } else {
                monthView.getChild(new UiSelector().index(currentDate + daysToAdd - 1)).click();
            }

            findByResourceId("android:id/button1").click();

        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Waiting for element");
        }
        Log.d(TAG, "Text wasn't found on this screen!");
    }

    /**
     * Increase or decrease date depends on input parameter
     *
     * @param increase true in case date should be increased, otherwise false
     * @param calendar Calendar object expected to apply
     * @param timeout  wait for existence of specific UI element
     * @return true if date was changed, otherwise false
     */
    public boolean changeDateSettings(boolean increase, Calendar calendar, long timeout) {
        UiObject list;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            list = findByResourceId("com.android.settings:id/list");
        } else {
            list = findByResourceId("android:id/list");
        }

        try {
            UiObject setDate = list.getChild(new UiSelector().textMatches("Set date"));

            setDate.waitForExists(timeout);
            setDate.click();

            if (selectExpectedDate(increase, calendar)) {
                return findByResourceId("android:id/button1").click();
            } else {
                Log.d(TAG, "Could not select expected date");
                return false;
            }
        } catch (UiObjectNotFoundException e) {
            Log.e(TAG, "Could not find element: " + e.getMessage());
        }
        Log.d(TAG, "Failed to set expected date");
        return false;
    }

    /**
     * Select or disable automatic Date&Time depends on input parameter
     *
     * @param increase true in case date should be increased, otherwise false
     * @param calendar Calendar object expected to apply
     * @return true if date was changed, otherwise false
     */
    private boolean selectExpectedDate(boolean increase, Calendar calendar) {
        UiObject nextMonth = null;
        if (increase) {
            nextMonth = findByResourceId("android:id/next");
        } else {
            nextMonth = findByResourceId("android:id/prev");
        }

        String textDateFormat = computeTextDateFormat(calendar);
        int indexToSelect = calendar.get(Calendar.DAY_OF_MONTH) - 1;
        Log.d(TAG, "Index: " + indexToSelect + " dateFormat: " + textDateFormat);
        try {
            UiObject uiDayOfMonth = findByResourceId("android:id/month_view").getChild(new UiSelector().index(indexToSelect));
            uiDayOfMonth.waitForExists(2000);
            if (uiDayOfMonth != null && uiDayOfMonth.getContentDescription().toLowerCase().contains(textDateFormat.toLowerCase())) {
                Log.d(TAG, "Expected data was selected!");
                return uiDayOfMonth.click();
            } else {
                Log.d(TAG, "Expected element was not found");
                if (nextMonth.click()) {
                    Log.d(TAG, "Move to next month");
                    return selectExpectedDate(increase, calendar);
                } else {
                    Log.d(TAG, "Could not move to the next month");
                    return false;
                }
            }
        } catch (UiObjectNotFoundException e) {
            Log.e(TAG, "Some element was not found on the screen: " + e.getMessage());
        }
        Log.d(TAG, "Could not select expected date");
        return false;
    }

    /**
     * @param calendar Calendar object expected to apply
     * @return expected date
     */
    private String computeTextDateFormat(Calendar calendar) {
        return calendar.get(Calendar.DAY_OF_MONTH) +
                " " + calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) +
                " " + calendar.get(Calendar.YEAR);
    }

    /**
     * Change time in 12 hours format
     *
     * @param calendar Calendar object expected to apply. Time format 12 hours
     * @return true if time changed successfully, otherwise false
     */
    public boolean changeTimeIn12HoursFormat(Calendar calendar) {

        UiObject list;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            list = findByResourceId("com.android.settings:id/list");
        } else {
            list = findByResourceId("android:id/list");
        }

        try {
            UiObject setTime = list.getChild(new UiSelector().textMatches("Set time"));

            setTime.waitForExists(of(WAIT_FOR_SCREEN));
            setTime.click();

            int indexOfHoursToSelect = calendar.get(Calendar.HOUR) - 1;
            Log.d(TAG, "Try to set " + (indexOfHoursToSelect + 1) + " hour in 12 hours format");
            UiObject radialTimePicker = findByResourceId("android:id/radial_picker").getChild(new UiSelector().index(indexOfHoursToSelect));
            Log.d(TAG, "Hours item was selected: " + radialTimePicker.click());

            int indexOfMinutesToSelect = calendar.get(Calendar.MINUTE) / 5;
            Log.d(TAG, "Try to set " + calendar.get(Calendar.MINUTE) + " minute in 12 hours format");
            radialTimePicker = findByResourceId("android:id/radial_picker",
                    Duration.of(WAIT_FOR_SCREEN)).
                    getChild(new UiSelector().index(indexOfMinutesToSelect));

            Log.d(TAG, "Minutes item was selected: " + radialTimePicker.click());

            String resultAmPm = "";
            if (calendar.get(Calendar.AM_PM) == 1) {
                resultAmPm = "pm_label";
            } else {
                resultAmPm = "am_label";
            }

            return findByResourceId("android:id/" + resultAmPm).click() && findByResourceId("android:id/button1").click();

        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Waiting for element");
        }
        Log.d(TAG, "Text wasn't found on this screen!");
        return false;
    }

    /**
     * Gets {@link UiObject} of a WiFi switch.
     *
     * @return {@link UiObject} of a WiFi switch
     */
    private UiObject getWiFiSwitch() {
        String settingsPackageId = "com.android.settings";
        String wifiSwitchResourceId = "switch_bar";

        return findByResourceId(computeResourceId(settingsPackageId, wifiSwitchResourceId));
    }

    public boolean isNaturalOrientation() throws RemoteException {
        return uiDevice.isNaturalOrientation();
    }

    public void rotateDeviceLeft() throws RemoteException {
        uiDevice.setOrientationLeft();
        waitForUI(Duration.of(SCREEN_ROTATION));
    }

    public void rotateDeviceRight() throws RemoteException {
        uiDevice.setOrientationRight();
        waitForUI(Duration.of(SCREEN_ROTATION));
    }

    public void rotateDeviceNatural() throws RemoteException {
        uiDevice.setOrientationNatural();
        waitForUI(Duration.of(SCREEN_ROTATION));
    }

    /**
     * Helper method to capture screenshot (requires app to have the write external storage permission)
     */
    public boolean captureScreenshot(String filename) {

        grantPermissionsInRuntime(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE});

        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        File dir = new File(Environment.getExternalStorageDirectory(), "Screenshots" + File.separator + stack[3].getClassName() + File.separator + stack[3].getMethodName());

        if (!dir.exists()) {
            Log.d(TAG, "Folder for screenshot does not exist. Try to create it.");
            if (!dir.mkdirs()) {

                //wait till permissions be granted
                waitForUI(of(WAIT_FOR_SCREEN));

                if (!dir.mkdirs()) {
                    Log.d(TAG, "Could not create hierarchy of folders for screenshot: " + dir.getAbsolutePath());
                    return false;
                }
            }
        }

        File file = new File(dir, filename + new Date().getTime() + ".png");

        boolean isCaptured = uiDevice.takeScreenshot(file);

        Log.d(TAG, "Capturing screenshot: " + isCaptured);

        return isCaptured;
    }

    /**
     * Enables airplane (flight) mode on the device by performing UI interaction with the device.
     * Networking will be completely disabled after this operation.
     *
     * @return whether the operation was performed successfully.
     */
    public boolean enableAirplaneMode() {
        return UiNetworkManagerFactory.getManager().enableAirplaneMode();
    }

    /**
     * Disables airplane (flight) mode on the device by performing UI interaction with the device.
     *
     * @return whether the operation was performed successfully.
     */
    public boolean disableAirplaneMode() {
        return UiNetworkManagerFactory.getManager().disableAirplaneMode();
    }

    /**
     * In simulation mode, a valid activation key is not required to open the application
     * because there is no direct communication with BlackBerry Dynamics servers at the enterprise;
     * and, in fact, no need for these servers to even be in place.
     * Communication with the BlackBerry Dynamics NOC, however, continues to take place.
     * In Enterprise Simulation mode your BlackBerry Dynamics applications are run on a device emulator.
     * To enable Enterprise Simulation mode, whether you use an IDE or an outside text editor,
     * you need change the following line in your application's settings.json file as shown below:
     * "GDLibraryMode":"GDEnterprise" -> "GDLibraryMode":"GDEnterpriseSimulation"
     * The settings.json file is located in the ../assets/ folder of the application and must remain there.
     * More information is here: https://community.good.com/docs/DOC-1351
     */
    public boolean activateGDAppInSimulationMode(String packageName, String email, String
            accessPin, String unlockPassword) {

        // When starting unprovisioned GD App will check NOC for Easy Activation options, if present will show screen prompting one can be installed
        // Otherwise will directly show the enter email and access key screen
        return BBDActivationHelper.loginOrActivateBuilder(UIAutomatorUtilsFactory.getUIAutomatorUtils(),
                packageName,
                email,
                accessPin)
                .setAppUnderTestPassword(unlockPassword)
                .doAction();
    }

    /**
     * Helper method to return GD Shared Preferences
     */
    @Deprecated
    public SharedPreferences getGDSharedPreferences(String aName, int aMode) {
        throw new RuntimeException("This method is deprecated. Please use GDAndroid.getInstance().getGDSharedPreferences(aName, aMode); in your code ");
    }

    @Deprecated
    public void registerGDStateReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        throw new RuntimeException("This method is deprecated. Please use GDAndroid.getInstance().registerReceiver(receiver, filter); in your code ");
    }

    @Deprecated
    public void unregisterGDStateReceiver(BroadcastReceiver receiver) {
        throw new RuntimeException("This method is deprecated. Please use GDAndroid.getInstance().unregisterReceiver(receiver); in your code ");
    }

    /**
     * Prints current screen hierarchy map to the logs.
     */
    public void printScreenMap() {

        Log.d(TAG, "Dumping current UI hierarchy map.");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            getUiDevice().dumpWindowHierarchy(os);
        } catch (IOException e) {
            Log.e(TAG, "Current window hierarchy dump failed!", e);
        }

        System.out.println(os);
    }

    /**
     * @param packageName package name
     * @param elementID   id of element expected to gone from screen
     * @param delay       total delay within which element has to be gone from screen
     * @return true if element gone from screen within specified time, otherwise false
     */
    public boolean waitUntilElementGoneFromUI(String packageName, String elementID, long delay) {
        for (int i = 0; i < delay / Duration.of(Duration.SHORT_UI_ACTION); i++) {
            if (isResourceWithIDShown(packageName, elementID)) {
                waitForUI(Duration.of(Duration.SHORT_UI_ACTION));
            } else {
                Log.d(TAG, "Element has gone from screen!");
                return true;
            }
        }
        return false;
    }


    public GDTestSettings getSettings() {
        return settings;
    }

    public void setSettings(GDTestSettings settings) {
        this.settings = settings;
    }
}
