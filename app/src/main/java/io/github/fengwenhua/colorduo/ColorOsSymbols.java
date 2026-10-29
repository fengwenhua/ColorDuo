/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import static io.github.fengwenhua.colorduo.CompatibilitySnapshot.Capability.*;

/** Read-only symbols for the tested ColorOS SystemUI family. Never infer other-device validation. */
final class ColorOsSymbols {
    static final String NAME_ICON_VIEW = "$iconView";
    static final String NAME_R8_CLASS_ID = "$r8$classId";
    static final String NAME_OPLUS_MOBILE_ICONS_VIEW_MODEL = "oplusMobileIconsViewModel";
    static final String NAME_VIEW_MODEL_FOR_SUB = "viewModelForSub";
    static final String NAME_GET_SUBSCRIPTION_ID = "getSubscriptionId";
    static final String NAME_GET_NETWORK_TYPE_ICON = "getNetworkTypeIcon";
    static final String NAME_GET_VALUE = "getValue";
    static final String CLASS_STATE_FLOW = "kotlinx.coroutines.flow.StateFlow";
    static final String NAME_OPLUS_STATUS_BAR_MOBILE_VIEW_BINDER = "OplusStatusBarMobileViewBinder";
    static final String NAME_OPLUS_STATUS_BAR_MOBILE_VIEW_BINDER_BIND_CUST_EX_1_2_1_2_1 = "OplusStatusBarMobileViewBinder$bindCustEx$1$2$1$2$1";
    static final String NAME_ADD_IGNORED_SLOT = "addIgnoredSlot";
    static final String ID_BATTERY_CHARGE_ICON = "battery_charge_icon";
    static final String ID_BATTERY_ICON_VIEW = "battery_icon_view";
    static final String ID_BATTERY_PERCENTAGE_VIEW = "battery_percentage_view";
    static final String NAME_BIND = "bind";
    static final String NAME_BIND_UPDATE_BATTERY_CONTENT_VIEW = "bind$updateBatteryContentView";
    static final String NAME_BIND_UPDATE_BATTERY_ICON_STYLE = "bind$updateBatteryIconStyle";
    static final String NAME_BIND_UPDATE_CHARGING_VIEW = "bind$updateChargingView";
    static final String NAME_BIND_CUST_EX = "bindCustEx";
    static final String NAME_BIND_CUST_EX_UPDATE_SIGNAL_ICON = "bindCustEx$updateSignalIcon";
    static final String NAME_BIND_VIEW_MODEL = "bindViewModel";
    static final String CLASS_BINDER_BATTERY_VIEW_BINDER = "binder.BatteryViewBinder";
    static final String ID_CLOCK = "clock";
    static final String CLASS_CHARGE_OPLUS_ADD_BATTERY_STATUS = "com.android.systemui.common.charge.OplusAddBatteryStatus";
    static final String CLASS_MODEL_ICON = "com.android.systemui.common.shared.model.Icon";
    static final String CLASS_MODEL_ICON_RESOURCE = "com.android.systemui.common.shared.model.Icon$Resource";
    static final String CLASS_BINDER_ICON_VIEW_BINDER = "com.android.systemui.common.ui.binder.IconViewBinder";
    static final String CLASS_PHONE_STATUS_ICON_CONTAINER = "com.android.systemui.statusbar.phone.StatusIconContainer";
    static final String CLASS_MODEL_SIGNAL_ICON_MODEL = "com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel";
    static final String CLASS_MODEL_SIGNAL_ICON_MODEL_CELLULAR = "com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel$Cellular";
    static final String CLASS_UI_MOBILE_VIEW_LOGGER = "com.android.systemui.statusbar.pipeline.mobile.ui.MobileViewLogger";
    static final String CLASS_VIEWMODEL_LOCATION_BASED_MOBILE_VIEW_MODEL = "com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.LocationBasedMobileViewModel";
    static final String CLASS_VIEW_MODERN_STATUS_BAR_VIEW = "com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView";
    static final String WIFI_PREFIX = "com.android.systemui.statusbar.pipeline.wifi.";
    static final String BATTERY_UI_PREFIX = "com.oplus.systemui.statusbar.pipeline.battery.ui.";
    static final String PREFIX_COM_OPLUS_SYSTEMUI_STATUSBAR_PIPELINE_MOBILE_UI_VIEW = "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.";
    static final String CLASS_WIDGET_STAT_CLOCK = "com.oplus.systemui.statusbar.widget.StatClock";
    static final String CLASS_BATTERY_OPLUS_BATTERY_CONTROLLER = "com.oplusos.systemui.common.battery.OplusBatteryController";
    static final String ID_DATA_INOUT = "data_inout";
    static final String NAME_DISABLE = "disable";
    static final String NAME_DUMP = "dump";
    static final String NAME_EMIT = "emit";
    static final String NAME_GET_BATTERY_ICON = "getBatteryIcon";
    static final String NAME_GET_BATTERY_ICON_STYLE = "getBatteryIconStyle";
    static final String NAME_GET_BATTERY_STYLE_DRAWABLE = "getBatteryStyleDrawable";
    static final String NAME_GET_CARRIER_NETWORK_CHANGE = "getCarrierNetworkChange";
    static final String NAME_GET_CONTENT_DESCRIPTION = "getContentDescription";
    static final String NAME_GET_ICON_STYLE = "getIconStyle";
    static final String NAME_GET_INSTANCE = "getInstance";
    static final String NAME_GET_LEVEL = "getLevel";
    static final String NAME_GET_MODULE_TAG = "getModuleTag";
    static final String NAME_GET_NUMBER_OF_LEVELS = "getNumberOfLevels";
    static final String NAME_GET_PHONE_ID = "getPhoneId";
    static final String NAME_GET_POWER_LEVEL = "getPowerLevel";
    static final String NAME_GET_RES = "getRes";
    static final String NAME_GET_INET_CONDITION = "getInetCondition";
    static final String NAME_GET_SLOT = "getSlot";
    static final String NAME_GET_TINT_COLOR = "getTintColor";
    static final String NAME_GET_VISIBLE_STATE = "getVisibleState";
    static final String NAME_HIDDEN = "hidden";
    static final String NAME_INVOKE = "invoke";
    static final String NAME_IS_CLOCK_FULL_CHARGE = "isClockFullCharge";
    static final String NAME_IS_ICON_BLOCKED = "isIconBlocked";
    static final String NAME_IS_ICON_VISIBLE = "isIconVisible";
    static final String NAME_IS_IN_SERVICE = "isInService";
    static final String NAME_IS_PLUGGIN = "isPluggin";
    static final String NAME_IS_REAL_CHARGING = "isRealCharging";
    static final String NAME_IS_SOFT_SIM_CARD = "isSoftSimCard";
    static final String NAME_IS_SUPER_VOOC_CHARGE = "isSuperVoocCharge";
    static final String NAME_IS_VALIDATED = "isValidated";
    static final String NAME_M_IGNORED_SLOTS = "mIgnoredSlots";
    static final String ID_MOBILE_GROUP = "mobile_group";
    static final String ID_MOBILE_SIGNAL = "mobile_signal";
    static final String ID_MOBILE_TYPE = "mobile_type";
    static final String CLASS_MODEL_BATTERY_ICON_STYLE = "model.BatteryIconStyle";
    static final String CLASS_MODEL_CHARGE_ICON = "model.ChargeIcon";
    static final String CLASS_MODEL_STAT_BATTERY_ICON = "model.StatBatteryIcon";
    static final String NAME_ON_ATTACHED_TO_WINDOW = "onAttachedToWindow";
    static final String NAME_ON_CONFIGURATION_CHANGED = "onConfigurationChanged";
    static final String NAME_ON_DARK_CHANGED = "onDarkChanged";
    static final String NAME_ON_DETACHED_FROM_WINDOW = "onDetachedFromWindow";
    static final String NAME_ON_HIDDEN_CHANGED = "onHiddenChanged";
    static final String NAME_ON_LAYOUT = "onLayout";
    static final String NAME_REGISTER_IBATTERY_STATE_CHANGED = "registerIBatteryStateChanged";
    static final String NAME_REMOVE_IGNORED_SLOT = "removeIgnoredSlot";
    static final String NAME_SET_VISIBILITY = "setVisibility";
    static final String CLASS_MODEL_WIFI_NETWORK_MODEL_ACTIVE = "shared.model.WifiNetworkModel$Active";
    static final String NAME_SHOULD_BE_VISIBLE = "shouldBeVisible";
    static final String NAME_STATUS = "status";
    static final String ID_STATUS_BAR_CONTENTS = "status_bar_contents";
    static final String ID_STATUS_BAR_DOT = "status_bar_dot";
    static final String CLASS_BINDER_WIFI_VIEW_BINDER = "ui.binder.WifiViewBinder";
    static final String CLASS_WIFI_VISIBILITY_COLLECTOR = "ui.binder.WifiViewBinder$bind$1$1$1$1";
    static final String NAME_GROUP_VIEW = "$groupView";
    static final String NAME_SET_VISIBLE_STATE = "setVisibleState";
    static final String ID_WIFI_GROUP = "wifi_group";
    static final String CLASS_MODEL_WIFI_ICON_VISIBLE = "ui.model.WifiIcon$Visible";
    static final String CLASS_VIEWMODEL_LOCATION_BASED_WIFI_VIEW_MODEL = "ui.viewmodel.LocationBasedWifiViewModel";
    static final String CLASS_VIEWMODEL_WIFI_VIEW_MODEL_WIFI_ICON_1 = "ui.viewmodel.WifiViewModel$wifiIcon$1";
    static final String NAME_UNREGISTER_IBATTERY_STATE_CHANGED = "unregisterIBatteryStateChanged";
    static final String NAME_UPDATE_CLOCK_VISIBILITY = "updateClockVisibility";
    static final String CLASS_VIEW_STAT_BATTERY_METER_VIEW = "view.StatBatteryMeterView";
    static final String CLASS_VIEWMODEL_LOCATION_BASED_BATTERY_VIEW_MODEL = "viewmodel.LocationBasedBatteryViewModel";

    private record Spec(String owner, String[] methods, String[] fields) {}
    static Map<CompatibilitySnapshot.Capability, CompatibilitySnapshot.Status> inspect(ClassLoader loader) {
        Map<CompatibilitySnapshot.Capability, CompatibilitySnapshot.Status> result = new EnumMap<>(CompatibilitySnapshot.Capability.class);
        result.put(BATTERY_SYMBOLS, probe(loader,
                new Spec(BATTERY_UI_PREFIX + CLASS_VIEW_STAT_BATTERY_METER_VIEW,
                        new String[]{NAME_GET_BATTERY_ICON, NAME_GET_BATTERY_ICON_STYLE, NAME_GET_BATTERY_STYLE_DRAWABLE},
                        new String[]{NAME_HIDDEN, NAME_DISABLE}),
                new Spec(BATTERY_UI_PREFIX + CLASS_MODEL_STAT_BATTERY_ICON,
                        new String[]{NAME_GET_POWER_LEVEL, NAME_GET_TINT_COLOR, NAME_GET_CONTENT_DESCRIPTION}, new String[]{}),
                new Spec(BATTERY_UI_PREFIX + CLASS_BINDER_BATTERY_VIEW_BINDER,
                        new String[]{NAME_BIND_UPDATE_BATTERY_ICON_STYLE, NAME_BIND_UPDATE_BATTERY_CONTENT_VIEW, NAME_BIND_UPDATE_CHARGING_VIEW}, new String[]{})));
        result.put(NETWORK_SYMBOLS, probe(loader,
                new Spec(WIFI_PREFIX + CLASS_MODEL_WIFI_NETWORK_MODEL_ACTIVE, new String[]{NAME_GET_LEVEL}, new String[]{NAME_IS_VALIDATED}),
                new Spec(CLASS_MODEL_SIGNAL_ICON_MODEL_CELLULAR, new String[]{NAME_GET_LEVEL, NAME_GET_NUMBER_OF_LEVELS,
                        NAME_GET_CARRIER_NETWORK_CHANGE, NAME_GET_INET_CONDITION, NAME_GET_PHONE_ID},
                        new String[]{NAME_IS_IN_SERVICE, NAME_IS_SOFT_SIM_CARD}),
                new Spec(CLASS_PHONE_STATUS_ICON_CONTAINER, new String[]{NAME_ADD_IGNORED_SLOT, NAME_REMOVE_IGNORED_SLOT}, new String[]{NAME_M_IGNORED_SLOTS})));
        result.put(POWER_SYMBOLS, probe(loader,
                new Spec(CLASS_BATTERY_OPLUS_BATTERY_CONTROLLER, new String[]{NAME_GET_INSTANCE, NAME_REGISTER_IBATTERY_STATE_CHANGED,
                        NAME_UNREGISTER_IBATTERY_STATE_CHANGED}, new String[]{}),
                new Spec(CLASS_CHARGE_OPLUS_ADD_BATTERY_STATUS, new String[]{NAME_IS_REAL_CHARGING, NAME_IS_SUPER_VOOC_CHARGE,
                        NAME_IS_PLUGGIN, NAME_IS_CLOCK_FULL_CHARGE}, new String[]{NAME_STATUS})));
        result.put(CLOCK_SYMBOLS, probe(loader, new Spec(CLASS_WIDGET_STAT_CLOCK,
                new String[]{NAME_UPDATE_CLOCK_VISIBILITY, NAME_SHOULD_BE_VISIBLE}, new String[]{})));
        return Map.copyOf(result);
    }
    static CompatibilitySnapshot.Status probe(ClassLoader loader, String owner, String method) {
        return probe(loader, new Spec(owner, new String[]{method}, new String[]{}));
    }
    private static CompatibilitySnapshot.Status probe(ClassLoader loader, Spec... specs) {
        try {
            for (Spec spec : specs) {
                Class<?> owner = Class.forName(spec.owner, false, loader);
                for (String name : spec.methods) {
                    boolean present = Arrays.stream(owner.getMethods()).anyMatch(m -> m.getName().equals(name))
                            || Arrays.stream(owner.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name));
                    if (!present) throw new NoSuchMethodException();
                }
                for (String field : spec.fields) owner.getDeclaredField(field);
            }
            return new CompatibilitySnapshot.Status(true, "None");
        } catch (Throwable failure) { return new CompatibilitySnapshot.Status(false, failure.getClass().getSimpleName()); }
    }
    private ColorOsSymbols() {}
}
