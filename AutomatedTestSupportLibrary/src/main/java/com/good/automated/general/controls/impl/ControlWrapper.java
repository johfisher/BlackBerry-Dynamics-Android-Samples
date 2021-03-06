/*
 * (c) 2017 BlackBerry Limited. All rights reserved.
 */
package com.good.automated.general.controls.impl;

import static com.good.automated.general.utils.Duration.UI_WAIT;

import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import android.util.Log;

import com.good.automated.general.utils.Duration;

/**
 * ControlWrapper is a helper-class to interact with UI screen in order to create object with specific options
 */
public class ControlWrapper {

    private static final String TAG = ControlWrapper.class.getCanonicalName();
    private final UiDevice uiDevice;
    private UiObject uiObject;
    private String resourceID;

    public ControlWrapper() {
        this.uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    public ControlWrapper(UiObject uIObject) {
        this.uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        this.uiObject = uIObject;
    }

    public ControlWrapper getControlWrapperObject(String packageName, String id, long delay) {
        uiObject = getUIObjectById(packageName, id, delay);
        return this;
    }

    public ControlWrapper getControlWrapperObject(String packageName, String id) {
        uiObject = getUIObjectById(packageName, id, 0);
        return this;
    }

    public ControlWrapper getControlWrapperObject(String text, long delay) {
        uiObject = getUIObjectByText(text, delay);
        return this;
    }

    public ControlWrapper getControlWrapperObject(String text) {
        uiObject = getUIObjectByText(text, 0);
        return this;
    }

    public ControlWrapper getControlWrapperObjectByText(String packageID, String text, long delay) {
        uiObject = getUIObjectByText(packageID, text, delay);
        return this;
    }

    public ControlWrapper getControlWrapperObjectByText(String packageID, String text) {
        uiObject = getUIObjectByText(packageID, text, 0);
        return this;
    }

    private UiObject getUIObjectByText(String packageID, String text, long delay) {

        uiObject = uiDevice.findObject(new UiSelector().text(text));

        if (uiObject.waitForExists(delay)) {
            Log.d(TAG, "UiObject with text: " + text + " was found");
            return uiObject;
        }

        Log.d(TAG, "UiObject with text: " + text + " wasn't found");
        return null;
    }

    public ControlWrapper getChildElement(int i) {

        Log.d(TAG, "Finding child UiObject with index number: " + i);

        ControlWrapper control = new ControlWrapper(uiObject);
        try {
            control.uiObject = control.uiObject.getChild(new UiSelector().index(i));
            if (control.uiObject.waitForExists(Duration.of(UI_WAIT))) {
                Log.d(TAG, "Child UiObject with index number: " + i + " was found" + control.uiObject.getClassName() + " " + control.uiObject.getPackageName());
                return control;
            }
        } catch (UiObjectNotFoundException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "Child UiObject with index number: " + resourceID + " wasn't found");
        return null;
    }

    private UiObject getUIObjectById(String packageName, String id, long delay) {

        this.resourceID = packageName + ":id/" + id;

        Log.d(TAG, "Finding UiObject with resourceID: " + resourceID);

        uiObject = uiDevice.findObject(new UiSelector().resourceId(resourceID));

        if (uiObject.waitForExists(delay)) {
            Log.d(TAG, "UiObject with resourceID: " + resourceID + " was found");
            return uiObject;
        }

        Log.d(TAG, "UiObject with resourceID: " + resourceID + " wasn't found");
        return null;
    }


    private UiObject getUIObjectByText(String text, long delay) {

        Log.d(TAG, "Finding UiObject with text: " + text);

        uiObject = uiDevice.findObject(new UiSelector().text(text));

        if (uiObject.waitForExists(delay)) {
            Log.d(TAG, "UiObject with text: " + text + " was found");
            return uiObject;
        }

        Log.d(TAG, "UiObject with text: " + text + " wasn't found");
        return null;
    }

    public boolean click() {
        try {
            if (uiObject.waitForExists(Duration.of(UI_WAIT))) {
                uiObject.click();

                //Small wait after clicking on item (ensures state has changed correctly)
                uiObject.waitUntilGone(Duration.of(UI_WAIT));
                Log.d(TAG, "Click was performed successfully on UI element with ID: " + getResourceID());
                return true;
            }
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't click on UI element with ID: " + getResourceID() + " UiObjectNotFoundException: " + e.getMessage());
        }
        return false;
    }

    public int getChildCount() {
        try {
            return uiObject != null ? uiObject.getChildCount() : 0;
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't find UI element with ID: " + getResourceID() + " UiObjectNotFoundException: " + e.getMessage());
        }
        return 0;
    }

    public String getResourceID() {
        return resourceID;
    }

    public String getClassName() {
        try {
            return uiObject.getClassName();
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't find UI element with ID: " + getResourceID() + " UiObjectNotFoundException: " + e.getMessage());
        }
        return null;
    }

    public String getPackage() {
        return null;
    }

    public String getText() {
        try {
            Log.d(TAG, "getText() = " + uiObject.getText());
            return uiObject.getText();
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "UiObjectNotFoundException of element with resourceID: " + getResourceID() + e.getMessage());
        }
        return null;
    }

    public boolean legacySetText(String text) {
        try {
            if (uiObject != null) {
                uiObject.legacySetText(text);
                if (uiObject.getText() != null) {
                    Log.d(TAG, "Text: \"" + text + "\" was entered");
                    return true;
                }
                Log.d(TAG, "Field is empty. ResourceID: " + getResourceID());
                return false;
            } else {
                Log.d(TAG, "Failed entering text: \"" + text + "\" into field with resourceID: " + getResourceID());
                return false;
            }
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't enter text to UI element with ID: " + getResourceID() + " UiObjectNotFoundException: " + e.getMessage());
            return false;
        }
    }

    public boolean setText(String text) {
        Log.d(TAG, "Important! This method might not work properly in release mode");
        try {
            if (uiObject != null) {
                uiObject.setText(text);
                if (uiObject.getText() != null) {
                    Log.d(TAG, "Text: \"" + text + "\" was entered");
                    return true;
                }
                Log.d(TAG, "Field is empty. ResourceID: " + getResourceID());
                return false;
            } else {
                Log.d(TAG, "Failed entering text: \"" + text + "\" into field with resourceID: " + getResourceID());
                return false;
            }
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't enter text to UI element with ID: " + getResourceID() + " UiObjectNotFoundException: " + e.getMessage());
            return false;
        }
    }

    public boolean checkText(String text) {
        try {
            if (uiObject != null && uiObject.getText().compareTo(text) == 0) {
                Log.d(TAG, "Element: " + getResourceID() + " is displayed");
                return true;
            } else {
                Log.d(TAG, "Required control wasn't shown: " + getResourceID());
                return false;
            }
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't compare text with UI element with ID: " + getResourceID() + " UiObjectNotFoundException: " + e.getMessage());
            return false;
        }
    }

    public boolean isCheckable() {
        try {
            return this.uiObject.isCheckable();
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't get state of UI element with resourceID: " + getResourceID() + " Error message: " + e.getMessage());
        }
        return false;
    }

    public boolean isChecked() {
        try {
            return this.uiObject.isChecked();
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't get state of UI element with resourceID: " + getResourceID() + " Error message: " + e.getMessage());
        }
        return false;
    }

    public boolean isClickable() {
        try {
            return this.uiObject.isClickable();
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't get state of UI element with resourceID: " + getResourceID() + " Error message: " + e.getMessage());
        }
        return false;
    }

    public boolean isEnabled() {
        try {
            return this.uiObject.isEnabled();
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't get state of UI element with resourceID: " + getResourceID() + " Error message: " + e.getMessage());
        }
        return false;
    }

    public boolean isFocusable() {
        try {
            return this.uiObject.isFocusable();
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't get state of UI element with resourceID: " + getResourceID() + " Error message: " + e.getMessage());
        }
        return false;
    }

    public boolean isFocused() {
        try {
            return this.uiObject.isFocused();
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't get state of UI element with resourceID: " + getResourceID() + " Error message: " + e.getMessage());
        }
        return false;
    }

    public boolean isLongClickable() {
        try {
            return this.uiObject.isLongClickable();
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't get state of UI element with resourceID: " + getResourceID() + " Error message: " + e.getMessage());
        }
        return false;
    }

    public boolean isSelected() {
        try {
            return this.uiObject.isSelected();
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't get state of UI element with resourceID: " + getResourceID() + " Error message: " + e.getMessage());
        }
        return false;
    }

    public void clearData() {
        try {
            this.uiObject.clearTextField();
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't get state of UI element with resourceID: " + getResourceID() + " Error message: " + e.getMessage());
        }
    }

    public String getContentDescription() {
        try {
            this.uiObject.getContentDescription();
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "Couldn't get state of UI element with resourceID: " + getResourceID() + " Error message: " + e.getMessage());
        }
        return null;
    }
}
